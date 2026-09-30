package com.c4g7.schemflow.we;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * Recalculates light over a pasted area and, off the main thread, waits until it is done.
 *
 * <p>FastAsyncWorldEdit relights a paste in the background and nothing tells the caller when that
 * finishes. A round world handed over right after the paste (Conduit unloads it and copies its folder
 * as the game map) is saved while that relight is still running, so the copy has blocks but no light:
 * players see it dark everywhere except right next to torches and lamps. This relights the pasted
 * chunks through the server's own light engine (Moonrise/Starlight on Paper 1.21+, reached by
 * reflection) and lets the paste wait for the result, so the world is only handed over fully lit.
 */
public final class PasteLighting {
    private static volatile boolean resolved;
    private static volatile Method relightChunks;   // ThreadedLevelLightEngine.starlight$serverRelightChunks
    private static volatile Constructor<?> chunkPos; // net.minecraft.world.level.ChunkPos(int, int)

    private PasteLighting() {}

    /** The block region a clipboard occupies once pasted at {@code to} (no rotation/flip). */
    public static Region pastedRegion(com.sk89q.worldedit.world.World world, Clipboard clipboard, BlockVector3 to) {
        BlockVector3 shift = to.subtract(clipboard.getOrigin());
        Region src = clipboard.getRegion();
        return new CuboidRegion(world, src.getMinimumPoint().add(shift), src.getMaximumPoint().add(shift));
    }

    /**
     * Relight {@code region} after a paste, per the {@code lighting.*} config. Off the main thread this
     * waits for the relight to finish (up to {@code lighting.maxWaitSeconds}), so a paste followed by this
     * call returns with the area lit. On the main thread it returns at once and the relight finishes in the
     * background: the chunk loads and tickets it needs are scheduled on the main thread, so blocking there
     * would deadlock. The light math itself always runs on the light engine's worker threads.
     * Failures are logged, never thrown.
     */
    public static void afterPaste(Plugin plugin, World world, Region region) {
        FileConfiguration cfg = plugin.getConfig();
        if (!isEnabled(cfg)) return;
        long start = System.nanoTime();
        CompletableFuture<Integer> done = relight(plugin, world, region);
        if (Bukkit.isPrimaryThread()) {
            done.whenComplete((chunks, err) -> {
                if (err != null) warn(plugin, world, err);
            });
            return;
        }
        int waitSeconds = maxWaitSeconds(cfg);
        try {
            int chunks = done.get(waitSeconds, TimeUnit.SECONDS);
            if (chunks >= 0) {
                plugin.getLogger().info("Relit " + chunks + " chunks in world '" + world.getName() + "' in "
                        + (System.nanoTime() - start) / 1_000_000 + " ms");
            }
        } catch (TimeoutException e) {
            plugin.getLogger().warning("Relight in world '" + world.getName() + "' still running after "
                    + waitSeconds + "s; continuing without it (raise lighting.maxWaitSeconds if maps come out dark)");
        } catch (ExecutionException e) {
            warn(plugin, world, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isEnabled(FileConfiguration cfg) {
        return cfg.getBoolean("lighting.relightAfterPaste", true);
    }

    public static int maxWaitSeconds(FileConfiguration cfg) {
        return Math.max(1, cfg.getInt("lighting.maxWaitSeconds", 60));
    }

    public static int chunksPerTick(FileConfiguration cfg) {
        return Math.max(1, cfg.getInt("lighting.chunksPerTick", 32));
    }

    /** True when the server's light engine exposes the relight entry point (Paper 1.21+). */
    public static boolean isAvailable() {
        resolve();
        return relightChunks != null;
    }

    /**
     * Relight every chunk of {@code region} plus a one-chunk border (light spills into neighbours).
     * Loads the chunks, holds them with plugin tickets while the light engine works, releases them after.
     * Safe from any thread.
     *
     * @return completes with the number of chunks relit, or -1 when this server has no relight API
     */
    public static CompletableFuture<Integer> relight(Plugin plugin, World world, Region region) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        if (!isAvailable()) {
            result.complete(-1);
            return result;
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = (min.getX() >> 4) - 1; cx <= (max.getX() >> 4) + 1; cx++) {
            for (int cz = (min.getZ() >> 4) - 1; cz <= (max.getZ() >> 4) + 1; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        int perTick = chunksPerTick(plugin.getConfig());
        List<int[]> held = new ArrayList<>();
        Runnable release = () -> {
            for (int[] c : held) {
                try { world.removePluginChunkTicket(c[0], c[1], plugin); } catch (Throwable ignore) {}
            }
            held.clear();
        };
        runOnMain(plugin, () -> loadBatch(plugin, world, chunks, 0, perTick, held, release, result));
        return result;
    }

    /*
     * The light engine skips chunks that aren't loaded, so every chunk is loaded first and then held with a
     * plugin ticket until the relight is done. A chunk finishing its load costs main-thread time (about half
     * a millisecond each; ~460 ms for a 960-chunk map loaded at once), so the loads go out perTick at a time,
     * one batch per tick. Runs on the main thread.
     */
    private static void loadBatch(Plugin plugin, World world, List<int[]> chunks, int from, int perTick,
                                  List<int[]> held, Runnable release, CompletableFuture<Integer> result) {
        try {
            if (from >= chunks.size()) {
                startRelight(plugin, world, chunks, release, result);
                return;
            }
            List<CompletableFuture<?>> loads = new ArrayList<>(perTick);
            for (int[] c : chunks.subList(from, Math.min(from + perTick, chunks.size()))) {
                loads.add(world.getChunkAtAsync(c[0], c[1]).thenAccept(ch -> {
                    world.addPluginChunkTicket(c[0], c[1], plugin);
                    held.add(c);
                }));
            }
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, err) -> {
                if (err != null) {
                    runOnMain(plugin, release);
                    result.completeExceptionally(err);
                    return;
                }
                try {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> loadBatch(plugin, world, chunks, from + perTick, perTick, held, release, result));
                } catch (Throwable t) {
                    runOnMain(plugin, release);
                    result.completeExceptionally(t);
                }
            });
        } catch (Throwable t) {
            release.run();
            result.completeExceptionally(t);
        }
    }

    /** Hand all (now loaded) chunks to the light engine; the relight itself runs on its worker threads. */
    private static void startRelight(Plugin plugin, World world, List<int[]> chunks, Runnable release,
                                     CompletableFuture<Integer> result) throws Exception {
        Object engine = lightEngine(world);
        Set<Object> positions = new LinkedHashSet<>();
        for (int[] c : chunks) positions.add(chunkPos.newInstance(c[0], c[1]));
        Consumer<Object> perChunk = pos -> {};
        IntConsumer onDone = relit -> runOnMain(plugin, () -> {
            release.run();
            result.complete(relit);
        });
        try {
            relightChunks.invoke(engine, positions, perChunk, onDone);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private static Object lightEngine(World world) throws Exception {
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object chunkSource = level.getClass().getMethod("getChunkSource").invoke(level);
        return chunkSource.getClass().getMethod("getLightEngine").invoke(chunkSource);
    }

    private static synchronized void resolve() {
        if (resolved) return;
        try {
            World any = Bukkit.getWorlds().get(0);
            Object engine = lightEngine(any);
            Method m = engine.getClass().getMethod("starlight$serverRelightChunks",
                    Collection.class, Consumer.class, IntConsumer.class);
            Class<?> pos = Class.forName("net.minecraft.world.level.ChunkPos", false, engine.getClass().getClassLoader());
            chunkPos = pos.getConstructor(int.class, int.class);
            relightChunks = m;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[SchemFlow] This server has no light-engine relight API (needs Paper 1.21+); "
                    + "pasted maps rely on WorldEdit's own lighting: " + t);
        }
        resolved = true;
    }

    private static void runOnMain(Plugin plugin, Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, r);
        } catch (Throwable t) {
            r.run(); // plugin disabling: best effort
        }
    }

    private static void warn(Plugin plugin, World world, Throwable t) {
        plugin.getLogger().log(Level.WARNING, "Relight after paste failed in world '" + world.getName() + "': " + t, t);
    }
}

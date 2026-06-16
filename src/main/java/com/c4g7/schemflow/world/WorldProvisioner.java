package com.c4g7.schemflow.world;

import com.c4g7.schemflow.S3Service;
import com.c4g7.schemflow.SchemFlowPlugin;
import com.c4g7.schemflow.util.SafeIO;
import com.c4g7.schemflow.util.ZipUtils;
import com.c4g7.schemflow.we.WorldEditUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WorldProvisioner {
    private final SchemFlowPlugin plugin;
    private final S3Service s3;

    public WorldProvisioner(SchemFlowPlugin plugin) {
        this.plugin = plugin;
        this.s3 = plugin.getS3Service();
    }

    public void provisionAllFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("provisionOnStartup", false)) return;
        List<?> worlds = cfg.getList("worlds");
        if (worlds == null) return;
        for (Object o : worlds) {
            if (o instanceof Map<?, ?> map) {
                String name = Objects.toString(map.get("name"), null);
                boolean enabled = Boolean.parseBoolean(Objects.toString(map.get("enabled"), "false"));
                if (name == null || !enabled) continue;
                @SuppressWarnings("unchecked") Map<String, Object> gr = (Map<String, Object>) map.get("gamerules");
                provisionWorld(
                        name,
                        Boolean.parseBoolean(Objects.toString(map.get("flat"), "true")),
                        Objects.toString(map.get("schem"), name),
                        Objects.toString(map.get("pasteAt"), "0,64,0"),
                        gr
                );
            }
        }
    }

    public void provisionWorld(String name, boolean flat, String schemName, String pasteAt, Map<String, Object> gamerules) {
        WorldCreator wc = new WorldCreator(name);
        if (flat) wc.generator(new EmptyChunkGenerator());
        wc.generateStructures(false);
        plugin.getLogger().info("Loading world " + name + "...");
        World world = Bukkit.createWorld(wc);
        if (world == null) {
            plugin.getLogger().severe("Failed to create world: " + name);
            return;
        }

        if (gamerules != null) applyGamerules(world, gamerules);

        String[] xyz = pasteAt.split(",");
        int x = Integer.parseInt(xyz[0]);
        int y = Integer.parseInt(xyz[1]);
        int z = Integer.parseInt(xyz[2]);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Path schm = s3.fetchSchm(schemName, plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics"));
                if (ZipUtils.isZip(schm)) {
                    Path outDir = schm.getParent().resolve(schemName);
                    SafeIO.ensureDir(outDir);
                    ZipUtils.unzip(schm, outDir);
                    Path schem;
                    try (java.util.stream.Stream<Path> st = Files.walk(outDir)) {
                        schem = st.filter(p -> p.toString().toLowerCase().endsWith(".schem")).findFirst().orElse(null);
                    }
                    if (schem != null) {
                        Path finalSchem = schem;
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            try {
                                WorldEditUtils.paste(new org.bukkit.Location(world, x, y, z), finalSchem, true);
                                plugin.getLogger().info("Pasted schematic into world " + name);
                            } catch (Exception ex) {
                                plugin.getLogger().severe("Paste failed for world " + name + ": " + ex.getMessage());
                            }
                        });
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("Provision failed for world " + name + ": " + ex.getMessage());
            }
        });
    }

    private void applyGamerules(World world, Map<String, Object> rules) {
        for (Map.Entry<String, Object> e : rules.entrySet()) {
            try {
                GameRule<?> gr = GameRule.getByName(e.getKey());
                if (gr == null) continue;
                Object val = e.getValue();
                if (val instanceof Boolean b && gr.getType() == Boolean.class) {
                    @SuppressWarnings("unchecked") GameRule<Boolean> g = (GameRule<Boolean>) gr;
                    world.setGameRule(g, b);
                } else if (val instanceof Number n && gr.getType() == Integer.class) {
                    @SuppressWarnings("unchecked") GameRule<Integer> g = (GameRule<Integer>) gr;
                    world.setGameRule(g, n.intValue());
                }
            } catch (Exception ignore) {}
        }
    }

    public void provisionByName(String worldName) {
        List<?> worlds = plugin.getConfig().getList("worlds");
        if (worlds == null) return;
        for (Object o : worlds) {
            if (o instanceof Map<?, ?> map) {
                String name = Objects.toString(map.get("name"), null);
                boolean enabled = Boolean.parseBoolean(Objects.toString(map.get("enabled"), "false"));
                if (name == null || !enabled || !name.equalsIgnoreCase(worldName)) continue;
                boolean flat = Boolean.parseBoolean(Objects.toString(map.get("flat"), "true"));
                String schem = Objects.toString(map.get("schem"), name);
                String pasteAt = Objects.toString(map.get("pasteAt"), "0,64,0");
                @SuppressWarnings("unchecked") Map<String, Object> gr = (Map<String, Object>) map.get("gamerules");
                provisionWorld(name, flat, schem, pasteAt, gr);
                return;
            }
        }
        plugin.getLogger().warning("No enabled world entry found for " + worldName);
    }

    // ===================================================================================
    //  On-demand API for external callers (e.g. Conduit's game host), invoked by REFLECTION:
    //    SchemFlowPlugin.getInstance().getProvisioner().provisionRoundWorld(...)
    //  Conduit keeps zero compile-time dependency on SchemFlow, so KEEP THE METHOD NAMES AND
    //  SIGNATURES BELOW STABLE once shipped.
    // ===================================================================================

    /**
     * Provision a per-round world from a schematic, ASYNC.
     *  - creates an EMPTY world named {@code worldName} (void, no structures, fixed time, no weather)
     *  - async-fetches {@code group/schematicName.schem} from S3 (reuses {@link S3Service#fetchSchm})
     *  - pastes it at its ORIGINAL absolute position when {@code pasteAtOrigin} (WorldEdit {@code -o});
     *    otherwise pastes the schematic's min corner at world (0,0,0)
     *  - applies {@code gamerules} (or sane game defaults when null)
     *  - completes the future with the loaded World on success / exceptionally on failure
     * Safe to call from ANY thread. World creation and future-completion run on the main thread; the
     * S3 fetch and the (FAWE) paste run OFF it, so a large paste never blocks the server tick loop.
     * With plain WorldEdit (no FAWE) the paste falls back to the main thread.
     * Idempotent: if {@code worldName} already exists, completes immediately with it.
     *
     * <p>NOTE: absolute placement only works for schematics saved with absolute origin (see
     * {@link #saveSelectionAsMap}); legacy maps must be re-exported.
     */
    public java.util.concurrent.CompletableFuture<World> provisionRoundWorld(
            String worldName,
            String group,
            String schematicName,
            boolean pasteAtOrigin,
            Map<String, Object> gamerules) {
        java.util.concurrent.CompletableFuture<World> future = new java.util.concurrent.CompletableFuture<>();
        if (worldName == null || worldName.isBlank()) {
            future.completeExceptionally(new IllegalArgumentException("worldName is required"));
            return future;
        }
        if (schematicName == null || schematicName.isBlank()) {
            future.completeExceptionally(new IllegalArgumentException("schematicName is required"));
            return future;
        }
        runOnMain(() -> {
            try {
                World existing = Bukkit.getWorld(worldName);
                if (existing != null) { future.complete(existing); return; } // idempotent
                World world = createEmptyWorld(worldName);
                if (world == null) {
                    future.completeExceptionally(new IllegalStateException("Failed to create world: " + worldName));
                    return;
                }
                applyGamerules(world, gamerules != null ? gamerules : defaultRoundGamerules());
                world.setTime(6000L);
                world.setStorm(false);
                world.setThundering(false);
                // S3 fetch + schematic decode + paste all run OFF the main thread. With FastAsyncWorldEdit
                // the paste is async-safe; running it on the main thread would block the tick loop and, for
                // a large map into a fresh world, trip Paper's watchdog. Plain WorldEdit has no async edits,
                // so it falls back to the main thread.
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Path schm = fetchRoundSchematic(schematicName, group);
                        com.sk89q.worldedit.extent.clipboard.Clipboard clipboard = WorldEditUtils.readClipboard(schm);
                        if (asyncPasteSupported()) {
                            pasteRoundMap(world, clipboard, pasteAtOrigin, worldName, group, schematicName);
                            // hop to the main thread only to complete the future, so the caller's
                            // whenComplete callback runs on the main thread (safe to teleport players).
                            plugin.getServer().getScheduler().runTask(plugin, () -> future.complete(world));
                        } else {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                try {
                                    pasteRoundMap(world, clipboard, pasteAtOrigin, worldName, group, schematicName);
                                    future.complete(world);
                                } catch (Exception ex) {
                                    future.completeExceptionally(ex);
                                }
                            });
                        }
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /** Unload {@code worldName} (no save) and delete its world folder. Safe to call from any thread. */
    public java.util.concurrent.CompletableFuture<Void> disposeWorld(String worldName) {
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        if (worldName == null || worldName.isBlank()) {
            future.completeExceptionally(new IllegalArgumentException("worldName is required"));
            return future;
        }
        runOnMain(() -> {
            try {
                World world = Bukkit.getWorld(worldName);
                java.io.File folder;
                if (world != null) {
                    folder = world.getWorldFolder();
                    if (!world.getPlayers().isEmpty() && !Bukkit.getWorlds().isEmpty()) {
                        org.bukkit.Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                        for (org.bukkit.entity.Player p : new java.util.ArrayList<>(world.getPlayers())) {
                            try { p.teleport(spawn); } catch (Exception ignore) {}
                        }
                    }
                    if (!Bukkit.unloadWorld(world, false)) {
                        future.completeExceptionally(new IllegalStateException("Failed to unload world: " + worldName));
                        return;
                    }
                } else {
                    folder = new java.io.File(Bukkit.getWorldContainer(), worldName);
                }
                final java.io.File toDelete = folder;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        if (toDelete != null && toDelete.exists()) SafeIO.deleteRecursively(toDelete.toPath());
                        future.complete(null);
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * ADD #2 (optional): a schematic's dimensions and authored minimum corner, so a caller that
     * pastes at a fixed point (NOT paste-at-origin) can map authored coords &rarr; pasted coords.
     * Does S3 + disk I/O — call this OFF the main thread.
     */
    public SchematicInfo inspect(String group, String schematicName) throws Exception {
        Path schm = fetchRoundSchematic(schematicName, group);
        com.sk89q.worldedit.extent.clipboard.Clipboard clipboard = WorldEditUtils.readClipboard(schm);
        com.sk89q.worldedit.math.BlockVector3 dim = clipboard.getDimensions();
        com.sk89q.worldedit.math.BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        return new SchematicInfo(dim.getX(), dim.getY(), dim.getZ(), min.getX(), min.getY(), min.getZ());
    }

    /**
     * ADD #3 (map builder): save {@code author}'s pos1/pos2 selection as {@code group/name.schem} to
     * S3, PRESERVING the absolute world origin so a later
     * {@link #provisionRoundWorld}{@code (..., pasteAtOrigin=true)} lands it at the same coordinates.
     * Overwrites any existing object of the same name (re-authoring a map is the normal flow).
     * Safe to call from any thread; the world read runs on the main thread, the S3 upload off it.
     */
    public java.util.concurrent.CompletableFuture<Void> saveSelectionAsMap(
            org.bukkit.entity.Player author, String group, String name) {
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        if (author == null) { future.completeExceptionally(new IllegalArgumentException("author is required")); return future; }
        if (name == null || name.isBlank()) { future.completeExceptionally(new IllegalArgumentException("name is required")); return future; }
        runOnMain(() -> {
            try {
                java.util.UUID id = author.getUniqueId();
                com.c4g7.schemflow.select.SelectionManager sel = plugin.getSelection();
                if (sel == null || !sel.hasBoth(id)) {
                    future.completeExceptionally(new IllegalStateException("Set pos1 and pos2 (same world) before saving a map"));
                    return;
                }
                org.bukkit.Location a = sel.getPos1(id);
                org.bukkit.Location b = sel.getPos2(id);
                final Path tmp = Files.createTempFile("schemflow-map-", s3.getExtension());
                WorldEditUtils.exportCuboidPreserveOrigin(a, b, tmp); // origin=(0,0,0) -> absolute coords preserved
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        s3.uploadSchmOverwrite(tmp, name, group);
                        plugin.refreshSchematicCacheAsync();
                        plugin.getLogger().info("Saved selection as map '"
                                + (group == null || group.isBlank() ? "" : group + "/") + name + "'");
                        future.complete(null);
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    } finally {
                        try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Save an explicit cuboid region (two corner Locations in a loaded world) as `group/name.schem` to
     * S3, PRESERVING the absolute world origin (so {@link #provisionRoundWorld}(…, pasteAtOrigin=true)
     * lands it back at the same absolute coords). Same as {@link #saveSelectionAsMap} but coord-driven —
     * no player/selection needed, so a converter (e.g. importing pre-built map worlds) can call it
     * headlessly for many maps. Both corners must be in the same loaded world. Overwrites any existing
     * object of that name. Safe to call from any thread; world read on main, S3 upload off-thread.
     */
    public java.util.concurrent.CompletableFuture<Void> saveRegionAsMap(
            org.bukkit.Location pos1, org.bukkit.Location pos2, String group, String name) {
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        if (name == null || name.isBlank()) { future.completeExceptionally(new IllegalArgumentException("name is required")); return future; }
        if (pos1 == null || pos2 == null || pos1.getWorld() == null || pos1.getWorld() != pos2.getWorld()) {
            future.completeExceptionally(new IllegalArgumentException("pos1 and pos2 must be set in the same loaded world"));
            return future;
        }
        runOnMain(() -> {
            try {
                final Path tmp = Files.createTempFile("schemflow-map-", s3.getExtension());
                WorldEditUtils.exportCuboidPreserveOrigin(pos1, pos2, tmp); // origin=(0,0,0) -> absolute coords preserved
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        s3.uploadSchmOverwrite(tmp, name, group);
                        plugin.refreshSchematicCacheAsync();
                        plugin.getLogger().info("Saved region as map '"
                                + (group == null || group.isBlank() ? "" : group + "/") + name + "'");
                        future.complete(null);
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    } finally {
                        try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    // --- internal helpers for the on-demand API ---

    private void runOnMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else plugin.getServer().getScheduler().runTask(plugin, r);
    }

    private World createEmptyWorld(String name) {
        WorldCreator wc = new WorldCreator(name);
        wc.generator(new EmptyChunkGenerator());
        wc.generateStructures(false);
        // NB: we deliberately do NOT call WorldCreator.keepSpawnInMemory(...)/keepSpawnLoaded(...) here —
        // those signatures drift across 1.21.x (keepSpawnInMemory(boolean) throws NoSuchMethodError on
        // 1.21.11). It isn't needed: EmptyChunkGenerator's fixed spawn + disabled generation already make
        // Paper's "Preparing spawn area" pass complete in ~1ms instead of multiple seconds.
        return Bukkit.createWorld(wc);
    }

    private Path fetchRoundSchematic(String schematicName, String group) throws Exception {
        Path ephemeral = plugin.getEphemeralCacheDir();
        String dest = ephemeral != null
                ? ephemeral.toString()
                : plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics");
        return s3.fetchSchm(schematicName, group, dest);
    }

    /**
     * Paste a round map. {@code ignoreAir = true}: a freshly provisioned world is all-void, so air blocks
     * are redundant; skipping them makes a typical (mostly hollow) map paste far cheaper.
     */
    private void pasteRoundMap(World world, com.sk89q.worldedit.extent.clipboard.Clipboard clipboard,
                               boolean pasteAtOrigin, String worldName, String group, String schematicName) throws Exception {
        WorldEditUtils.pasteClipboard(world, clipboard, pasteAtOrigin, true, true, true);
        plugin.getLogger().info("Provisioned round world '" + worldName + "' from "
                + (group == null || group.isBlank() ? "" : group + "/") + schematicName
                + (pasteAtOrigin ? " (paste-at-origin)" : " (min at 0,0,0)"));
    }

    /** True when FastAsyncWorldEdit is present, i.e. pastes can run safely off the main thread. */
    private boolean asyncPasteSupported() {
        var pm = plugin.getServer().getPluginManager();
        return pm.getPlugin("FastAsyncWorldEdit") != null || pm.getPlugin("FAWE") != null;
    }

    private Map<String, Object> defaultRoundGamerules() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("doMobSpawning", false);
        m.put("doDaylightCycle", false);
        m.put("doWeatherCycle", false);
        m.put("doFireTick", false);
        m.put("mobGriefing", false);
        m.put("doInsomnia", false);
        m.put("doTraderSpawning", false);
        m.put("doPatrolSpawning", false);
        m.put("disableRaids", true);
        m.put("announceAdvancements", false);
        m.put("randomTickSpeed", 0);
        return m;
    }

    // A ChunkGenerator with NO overrides falls through to VANILLA terrain generation on
    // modern Paper (1.18+). Round worlds must be pure VOID so the pasted schematic is the
    // only geometry — otherwise the map sits on top of random generated terrain. Disabling
    // every generation phase yields an empty (void) chunk.
    public static class EmptyChunkGenerator extends ChunkGenerator {
        // Provide a FIXED spawn so CraftBukkit skips the vanilla PlayerSpawnFinder search. In a void
        // world that search does a BLOCKING, synchronous chunk load on the main thread hunting for
        // solid ground that never exists — multi-second stall / watchdog risk on world creation.
        @Override
        public org.bukkit.Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new org.bukkit.Location(world, 0, 64, 0);
        }
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
}

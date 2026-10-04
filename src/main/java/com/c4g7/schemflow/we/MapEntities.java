package com.c4g7.schemflow.we;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.ByteTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.DoubleTag;
import com.sk89q.jnbt.FloatTag;
import com.sk89q.jnbt.IntArrayTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.LongArrayTag;
import com.sk89q.jnbt.LongTag;
import com.sk89q.jnbt.ShortTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Keeps entities that ride other entities (display-entity models) intact through an export and a paste.
 *
 * <p>Decoration models are often built from many block/item/text displays riding one root entity.
 * FastAsyncWorldEdit writes such a stack twice: the root keeps every passenger, complete, in its
 * {@code Passengers} list, and every passenger is also written as a top-level entity of its own, but
 * EMPTY: no UUID and no data (FAWE reads an entity with {@code Entity#save}, which writes nothing for an
 * entity that is riding). On paste FAWE loads the root without its {@code Passengers} and spawns the empty
 * copies as default entities: block displays of air, item displays without an item, blank text displays.
 * The model is gone from the map, and nothing reports it.
 *
 * <p>Exports drop the empty copies (the root still carries its passengers). Pastes take each root out of
 * the WorldEdit paste and spawn it, passengers mounted, from its own NBT through Paper's entity API once
 * the blocks are in; the copies are skipped. This works on schematics exported before the fix too, since
 * the root's {@code Passengers} list was always complete. Entities that ride nothing are left to (FA)WE.
 */
public final class MapEntities {
    /** How long a paste off the main thread waits for its riders to be spawned before giving up on them. */
    private static final int MAX_WAIT_SECONDS = 60;

    private MapEntities() {}

    public static boolean copyBiomes(FileConfiguration cfg) {
        return cfg.getBoolean("maps.copyBiomes", true);
    }

    public static boolean restorePassengers(FileConfiguration cfg) {
        return cfg.getBoolean("maps.restorePassengers", true);
    }

    public static int entitiesPerTick(FileConfiguration cfg) {
        return Math.max(1, cfg.getInt("maps.entitiesPerTick", 128));
    }

    /**
     * Export target: wraps the clipboard an export copies into, so the empty top-level copies of riding
     * entities are not stored. Their data lives on in their vehicle's {@code Passengers} list.
     */
    public static Extent exportTarget(Extent clipboard) {
        return new AbstractDelegateExtent(clipboard) {
            @Override
            public Entity createEntity(Location location, BaseEntity entity) {
                return entity != null && !hasUuid(nbt(entity)) ? null : super.createEntity(rebase(location, getExtent()), entity);
            }
        };
    }

    /** The location re-anchored on the wrapped extent: FAWE's clipboard casts a location's extent to itself. */
    private static Location rebase(Location location, Extent extent) {
        return new Location(extent, location.toVector(), location.getYaw(), location.getPitch());
    }

    /**
     * Plan the riders of one paste. Returns null when the clipboard has no riding entities and no empty
     * copies, so the paste then runs exactly as it always has.
     *
     * @param to the paste position handed to WorldEdit (the clipboard origin lands there)
     */
    public static Paste plan(Clipboard clipboard, BlockVector3 to) {
        List<? extends Entity> all = clipboard.getEntities();
        // passengers nested in a root, by type, at their position in the clipboard's frame (a root stores
        // its passengers at their source-world coordinates, the clipboard may be shifted from those)
        Map<String, List<double[]>> nested = new HashMap<>();
        int roots = 0;
        for (Entity e : all) {
            CompoundTag nbt = nbt(e.getState());
            if (!hasUuid(nbt) || !hasPassengers(nbt)) continue;
            roots++;
            double[] src = pos(nbt);
            Location at = e.getLocation();
            double[] shift = src == null ? new double[3]
                    : new double[]{at.getX() - src[0], at.getY() - src[1], at.getZ() - src[2]};
            collectPassengers(nbt, shift, nested);
        }
        Map<String, Integer> drop = new HashMap<>();
        for (Entity e : all) {
            BaseEntity state = e.getState();
            CompoundTag nbt = nbt(state);
            if (state == null || hasUuid(nbt)) continue;
            String type = state.getType().getId();
            Location at = e.getLocation();
            // an empty copy is useless either way; a copy WITH data is skipped only when it duplicates a
            // nested passenger, otherwise (vehicle outside the schematic) it is all that is left of the entity
            if (isEmpty(nbt) || takeMatch(nested.get(type), at.getX(), at.getY(), at.getZ())) {
                drop.merge(key(type, at.getX(), at.getY(), at.getZ()), 1, Integer::sum);
            }
        }
        if (roots == 0 && drop.isEmpty()) return null;
        return new Paste(to.subtract(clipboard.getOrigin()), drop);
    }

    /** One paste's riders: collected while WorldEdit pastes, spawned afterwards. */
    public static final class Paste {
        private final BlockVector3 shift;              // paste position - clipboard origin
        private final Map<String, Integer> drop;        // copies to skip, by type + clipboard position
        private final List<Root> roots = Collections.synchronizedList(new ArrayList<>());
        private volatile int skipped;

        private Paste(BlockVector3 shift, Map<String, Integer> drop) {
            this.shift = shift;
            this.drop = new HashMap<>(drop);
        }

        /** Paste target: keeps roots (spawned later by {@link #spawn}) and the skipped copies out of the paste. */
        public Extent wrap(Extent target) {
            return new AbstractDelegateExtent(target) {
                @Override
                public Entity createEntity(Location location, BaseEntity entity) {
                    return intercept(location, entity) ? null : super.createEntity(rebase(location, getExtent()), entity);
                }
            };
        }

        /** True when WorldEdit must not create this entity itself. Called from the paste thread. */
        private boolean intercept(Location location, BaseEntity entity) {
            if (entity == null) return false;
            CompoundTag nbt = nbt(entity);
            if (hasUuid(nbt)) {
                if (!hasPassengers(nbt)) return false;
                roots.add(new Root(entity.getType().getId(), nbt, location.getX(), location.getY(), location.getZ()));
                return true;
            }
            String key = key(entity.getType().getId(), location.getX() - shift.getX(),
                    location.getY() - shift.getY(), location.getZ() - shift.getZ());
            synchronized (drop) {
                Integer left = drop.get(key);
                if (left == null) return false;
                if (left <= 1) drop.remove(key); else drop.put(key, left - 1);
            }
            skipped++;
            return true;
        }

        /**
         * Spawn the collected roots with their passengers into {@code world}, a batch of
         * {@code maps.entitiesPerTick} entities per tick on the main thread. The NBT is turned into entity
         * snapshots beforehand on the calling thread (parsing is most of the cost and touches no world), so
         * the main thread only creates and adds the entities. Off the main thread this waits until they are
         * all in the world, so a world unloaded right after the paste saves them. Failures are logged, never
         * thrown.
         */
        public void spawn(Plugin plugin, World world) {
            if (roots.isEmpty()) {
                if (skipped > 0) plugin.getLogger().info("Skipped " + skipped + " duplicate passenger copies in world '" + world.getName() + "'");
                return;
            }
            long start = System.nanoTime();
            List<Job> jobs = new ArrayList<>(roots.size());
            for (Root r : roots) {
                try {
                    jobs.add(new Job(Bukkit.getEntityFactory().createEntitySnapshot(r.snbt()), r.x, r.y, r.z,
                            1 + countPassengers(r.nbt)));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Could not prepare riding " + r.type + " at "
                            + r.x + "," + r.y + "," + r.z + ": " + ex, ex);
                }
            }
            CompletableFuture<int[]> done = new CompletableFuture<>();
            Runnable begin = () -> spawnBatch(plugin, world, jobs, 0, entitiesPerTick(plugin.getConfig()),
                    new LinkedHashSet<>(), new int[3], done);
            if (Bukkit.isPrimaryThread()) {
                begin.run();
                done.whenComplete((r, err) -> report(plugin, world, r, err, start));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, begin);
            try {
                report(plugin, world, done.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS), null, start);
            } catch (TimeoutException e) {
                plugin.getLogger().warning("Riding entities in world '" + world.getName() + "' not all spawned after "
                        + MAX_WAIT_SECONDS + "s; continuing without waiting for the rest");
            } catch (ExecutionException e) {
                report(plugin, world, null, e.getCause(), start);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void report(Plugin plugin, World world, int[] r, Throwable err, long start) {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Restoring riding entities in world '" + world.getName() + "' failed: " + err, err);
                return;
            }
            plugin.getLogger().info("Restored " + r[0] + " entity stacks (" + r[1] + " entities, " + skipped
                    + " duplicate passenger copies skipped) in world '" + world.getName() + "' in "
                    + (System.nanoTime() - start) / 1_000_000 + " ms, " + r[2] / 1000 + " ms of it on the main thread");
        }
    }

    private record Root(String type, CompoundTag nbt, double x, double y, double z) {
        /**
         * The root as SNBT, moved to its paste position; its passengers move with it. UUIDs are left out, as
         * vanilla does when it places a structure: a second paste into the same world would otherwise be
         * refused as duplicate entities.
         */
        String snbt() {
            double[] src = pos(nbt);
            double[] d = src == null ? new double[3] : new double[]{x - src[0], y - src[1], z - src[2]};
            StringBuilder sb = new StringBuilder(4096);
            writeEntity(sb, nbt.getValue(), type, d, new double[]{x, y, z});
            return sb.toString();
        }
    }

    private record Job(EntitySnapshot snapshot, double x, double y, double z, int entities) {}

    /*
     * Runs on the main thread, one batch per tick (runTaskLater(1): a runTask scheduled from a running task
     * would still run in the same tick). The chunk under a root is loaded (async) and held with a plugin
     * ticket until every root is in, so nothing is added to an unloaded chunk; the tickets go once the last
     * batch is done and the world saves the entities like any other.
     */
    private static void spawnBatch(Plugin plugin, World world, List<Job> jobs, int from, int perTick,
                                   Set<Long> held, int[] stats, CompletableFuture<int[]> done) {
        try {
            if (from >= jobs.size()) {
                release(plugin, world, held);
                done.complete(stats);
                return;
            }
            int end = from, budget = 0;
            while (end < jobs.size() && (end == from || budget + jobs.get(end).entities() <= perTick)) {
                budget += jobs.get(end++).entities();
            }
            List<CompletableFuture<?>> loads = new ArrayList<>(end - from);
            for (Job job : jobs.subList(from, end)) {
                int cx = (int) Math.floor(job.x()) >> 4, cz = (int) Math.floor(job.z()) >> 4;
                loads.add(world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                    long t0 = System.nanoTime();
                    if (held.add(((long) cz << 32) | (cx & 0xffffffffL))) world.addPluginChunkTicket(cx, cz, plugin);
                    try {
                        org.bukkit.entity.Entity e = job.snapshot()
                                .createEntity(new org.bukkit.Location(world, job.x(), job.y(), job.z()));
                        stats[0]++;
                        stats[1] += 1 + countPassengers(e);
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.WARNING, "Could not spawn riding entity at " + job.x() + ","
                                + job.y() + "," + job.z() + ": " + ex, ex);
                    }
                    stats[2] += (int) ((System.nanoTime() - t0) / 1000);
                }));
            }
            final int next = end;
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, err) -> {
                if (err != null) {
                    release(plugin, world, held);
                    done.completeExceptionally(err);
                    return;
                }
                if (Bukkit.isPrimaryThread() && next >= jobs.size()) {
                    spawnBatch(plugin, world, jobs, next, perTick, held, stats, done);
                    return;
                }
                try {
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> spawnBatch(plugin, world, jobs, next, perTick, held, stats, done), 1L);
                } catch (Throwable t) { // plugin disabling
                    release(plugin, world, held);
                    done.completeExceptionally(t);
                }
            });
        } catch (Throwable t) {
            release(plugin, world, held);
            done.completeExceptionally(t);
        }
    }

    private static void release(Plugin plugin, World world, Set<Long> held) {
        for (long c : held) world.removePluginChunkTicket((int) c, (int) (c >> 32), plugin);
        held.clear();
    }

    // --- NBT helpers (jnbt: present in WorldEdit 7.2+ and FAWE alike) ---

    private static CompoundTag nbt(BaseEntity entity) {
        return entity == null ? null : entity.getNbtData();
    }

    private static boolean hasUuid(CompoundTag nbt) {
        if (nbt == null) return false;
        Map<String, Tag> m = nbt.getValue();
        return m.containsKey("UUID") || m.containsKey("UUIDMost");
    }

    private static boolean hasPassengers(CompoundTag nbt) {
        return nbt != null && nbt.getValue().get("Passengers") instanceof ListTag l && !l.getValue().isEmpty();
    }

    /** No data beyond what FAWE writes for any entry (where it is and which way it faces). */
    private static boolean isEmpty(CompoundTag nbt) {
        if (nbt == null) return true;
        for (String k : nbt.getValue().keySet()) {
            if (!k.equals("Rotation") && !k.equals("Pos") && !k.equals("id") && !k.equals("Id")) return false;
        }
        return true;
    }

    private static double[] pos(CompoundTag nbt) {
        if (nbt == null || !(nbt.getValue().get("Pos") instanceof ListTag l) || l.getValue().size() != 3) return null;
        double[] p = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(l.getValue().get(i) instanceof DoubleTag d)) return null;
            p[i] = (Double) ((Tag) d).getValue();
        }
        return p;
    }

    private static void collectPassengers(CompoundTag nbt, double[] shift, Map<String, List<double[]>> out) {
        if (!(nbt.getValue().get("Passengers") instanceof ListTag l)) return;
        for (Object o : l.getValue()) {
            if (!(o instanceof CompoundTag p)) continue;
            double[] at = pos(p);
            if (p.getValue().get("id") instanceof StringTag id && at != null) {
                out.computeIfAbsent((String) ((Tag) id).getValue(), k -> new ArrayList<>())
                        .add(new double[]{at[0] + shift[0], at[1] + shift[1], at[2] + shift[2]});
            }
            collectPassengers(p, shift, out);
        }
    }

    private static boolean takeMatch(List<double[]> candidates, double x, double y, double z) {
        if (candidates == null) return false;
        for (int i = 0; i < candidates.size(); i++) {
            double[] c = candidates.get(i);
            if (Math.abs(c[0] - x) <= 0.05 && Math.abs(c[1] - y) <= 0.05 && Math.abs(c[2] - z) <= 0.05) {
                candidates.remove(i);
                return true;
            }
        }
        return false;
    }

    private static String key(String type, double x, double y, double z) {
        return type + "@" + Math.round(x * 32) + "," + Math.round(y * 32) + "," + Math.round(z * 32);
    }

    private static int countPassengers(CompoundTag nbt) {
        int n = 0;
        if (nbt.getValue().get("Passengers") instanceof ListTag l) {
            for (Object o : l.getValue()) if (o instanceof CompoundTag p) n += 1 + countPassengers(p);
        }
        return n;
    }

    private static int countPassengers(org.bukkit.entity.Entity e) {
        int n = 0;
        for (org.bukkit.entity.Entity p : e.getPassengers()) n += 1 + countPassengers(p);
        return n;
    }

    // --- SNBT (the input Bukkit's EntityFactory takes) ---

    /**
     * An entity compound as SNBT with every {@code Pos} moved by {@code delta} (passengers included), the
     * root's set to {@code exact}, the root's id taken from its type (FAWE stores it beside the data), and
     * no UUIDs (the server hands out fresh ones).
     */
    private static void writeEntity(StringBuilder sb, Map<String, Tag> m, String id, double[] delta, double[] exact) {
        sb.append('{');
        boolean first = true;
        if (id != null) {
            sb.append("\"id\":");
            string(sb, id);
            first = false;
        }
        for (Map.Entry<String, Tag> e : m.entrySet()) {
            String k = e.getKey();
            if (id != null && (k.equals("id") || k.equals("Id"))) continue;
            if (k.equals("UUID") || k.equals("UUIDMost") || k.equals("UUIDLeast")) continue;
            if (!first) sb.append(',');
            first = false;
            string(sb, k);
            sb.append(':');
            Tag v = e.getValue();
            if (k.equals("Pos") && v instanceof ListTag l && l.getValue().size() == 3) {
                sb.append('[');
                for (int i = 0; i < 3; i++) {
                    double p = exact != null ? exact[i] : (Double) ((Tag) l.getValue().get(i)).getValue() + delta[i];
                    if (i > 0) sb.append(',');
                    sb.append(plain(p)).append('d');
                }
                sb.append(']');
            } else if (k.equals("Passengers") && v instanceof ListTag l) {
                sb.append('[');
                boolean firstP = true;
                for (Object o : l.getValue()) {
                    if (!(o instanceof CompoundTag p)) continue;
                    if (!firstP) sb.append(',');
                    firstP = false;
                    writeEntity(sb, p.getValue(), null, delta, null);
                }
                sb.append(']');
            } else {
                write(sb, v);
            }
        }
        sb.append('}');
    }

    private static void write(StringBuilder sb, Tag t) {
        Object v = t.getValue();
        if (t instanceof CompoundTag c) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Tag> e : c.getValue().entrySet()) {
                if (!first) sb.append(',');
                first = false;
                string(sb, e.getKey());
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (t instanceof ListTag l) {
            sb.append('[');
            boolean first = true;
            for (Object o : l.getValue()) {
                if (!first) sb.append(',');
                first = false;
                write(sb, (Tag) o);
            }
            sb.append(']');
        } else if (t instanceof StringTag) {
            string(sb, (String) v);
        } else if (t instanceof ByteTag) {
            sb.append(((Number) v).byteValue()).append('b');
        } else if (t instanceof ShortTag) {
            sb.append(((Number) v).shortValue()).append('s');
        } else if (t instanceof IntTag) {
            sb.append(((Number) v).intValue());
        } else if (t instanceof LongTag) {
            sb.append(((Number) v).longValue()).append('L');
        } else if (t instanceof FloatTag) {
            float f = ((Number) v).floatValue();
            sb.append(Float.isFinite(f) ? new BigDecimal(Float.toString(f)).toPlainString() : "0").append('f');
        } else if (t instanceof DoubleTag) {
            sb.append(plain(((Number) v).doubleValue())).append('d');
        } else if (t instanceof ByteArrayTag) {
            sb.append("[B;");
            byte[] a = (byte[]) v;
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]).append('b');
            sb.append(']');
        } else if (t instanceof IntArrayTag) {
            sb.append("[I;");
            int[] a = (int[]) v;
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]);
            sb.append(']');
        } else if (t instanceof LongArrayTag) {
            sb.append("[L;");
            long[] a = (long[]) v;
            for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i]).append('L');
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported NBT tag " + t.getClass().getSimpleName());
        }
    }

    /** Shortest exact decimal, never in exponent form (both SNBT grammars accept that). */
    private static String plain(double d) {
        return Double.isFinite(d) ? new BigDecimal(Double.toString(d)).toPlainString() : "0";
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
    }
}

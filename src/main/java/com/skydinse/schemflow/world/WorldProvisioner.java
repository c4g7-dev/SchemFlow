package com.skydinse.schemflow.world;

import com.skydinse.schemflow.S3Service;
import com.skydinse.schemflow.SchemFlowPlugin;
import com.skydinse.schemflow.util.SafeIO;
import com.skydinse.schemflow.util.ZipUtils;
import com.skydinse.schemflow.we.WorldEditUtils;
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

/**
 * WorldProvisioner — create/load worlds and paste schematics.
 * Author: c4g7
 */
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
                Path schm = s3.fetchSchm(schemName, plugin.getConfig().getString("downloadDir", "plugins/Skript/schematics"));
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

    public static class EmptyChunkGenerator extends ChunkGenerator { }
}
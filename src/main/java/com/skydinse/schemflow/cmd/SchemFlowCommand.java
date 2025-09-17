package com.skydinse.schemflow.cmd;

import com.skydinse.schemflow.S3Service;
import com.skydinse.schemflow.SchemFlowPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.List;

/**
 * SchemFlowCommand — main /SchemFlow executor.
 * Author: c4g7
 */
public class SchemFlowCommand implements CommandExecutor {
    private final SchemFlowPlugin plugin;

    public SchemFlowCommand(SchemFlowPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("schemflow.admin")) {
            sendMM(sender, prefix() + " <red>No permission.</red>");
            return true;
        }
        if (args.length == 0) {
            printHelp(sender, label);
            return true;
        }
        S3Service s3 = plugin.getS3Service();
        switch (args[0].toLowerCase()) {
            case "help" -> {
                printHelp(sender, label);
                return true;
            }
            case "reload" -> {
                boolean ok = plugin.reloadSchemFlowConfig();
                if (!ok) {
                    sendMM(sender, prefix() + " <red>Reload failed.</red>");
                    return true;
                }
                sendMM(sender, prefix() + " <green>Config reloaded.</green> <grey>Refreshing cache...</grey>");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        java.util.List<String> list = plugin.getS3Service().listSchm();
                        com.skydinse.schemflow.SchemFlowPlugin.getInstance().getSchematicCache().clear();
                        for (String n : list) com.skydinse.schemflow.SchemFlowPlugin.getInstance().getSchematicCache().add(n);
                        sendMM(sender, prefix() + " <green>Cache size:</green> <aqua>" + list.size() + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Cache refresh failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
                return true;
            }
            case "cache" -> {
                sendMM(sender, prefix() + " <grey>Refreshing schematic cache...</grey>");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        java.util.List<String> list = plugin.getS3Service().listSchm();
                        // update cache
                        com.skydinse.schemflow.SchemFlowPlugin.getInstance().getSchematicCache().clear();
                        for (String n : list) com.skydinse.schemflow.SchemFlowPlugin.getInstance().getSchematicCache().add(n);
                        sendMM(sender, prefix() + " <green>Cache refreshed:</green> <aqua>" + list.size() + "</aqua> <grey>schematics</grey>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Cache refresh failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
                return true;
            }
            case "list" -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<String> names = s3.listSchm();
                    if (names == null || names.isEmpty()) {
                        sendMM(sender, prefix() + " <grey>No schematics found.</grey>");
                    } else {
                        String joined = names.stream().map(n -> "<aqua>" + n + "</aqua>").reduce((a,b) -> a + ", " + b).orElse("");
                        sendMM(sender, prefix() + " <grey>Found:</grey> " + joined);
                    }
                    plugin.refreshSchematicCacheAsync();
                } catch (Exception e) {
                    sendMM(sender, prefix() + " <red>List failed:</red> <grey>" + e.getMessage() + "</grey>");
                }
            });
            case "fetch" -> {
                if (args.length < 2) {
                    sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow fetch</gradient> <white><i>name</i></white> <white><i>[destDir]</i></white>");
                    return true;
                }
                String name = args[1];
                String dest = args.length >= 3 ? args[2] : plugin.getConfig().getString("downloadDir", "plugins/Skript/schematics");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Path p = s3.fetchSchm(name, dest);
                        sendMM(sender, prefix() + " <green>Downloaded to </green><aqua>" + p + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Fetch failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "pos1" -> {
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                plugin.getSelection().setPos1(p.getUniqueId(), p.getLocation());
                sendMM(sender, prefix() + " <green>Set </green><aqua>pos1</aqua><green> at your location.</green>");
            }
            case "pos2" -> {
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                plugin.getSelection().setPos2(p.getUniqueId(), p.getLocation());
                sendMM(sender, prefix() + " <green>Set </green><aqua>pos2</aqua><green> at your location.</green>");
            }
            case "upload" -> {
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow upload</gradient> <white><i>id</i></white> <white><i>[-flags]</i></white>"); return true; }
                if (!plugin.getSelection().hasBoth(p.getUniqueId())) { sendMM(sender, prefix() + " <red>Set pos1 and pos2 first.</red>"); return true; }
                String id = args[1];
                final com.skydinse.schemflow.we.WeFlags weFlags = (args.length >= 3 && args[2].startsWith("-")) ? com.skydinse.schemflow.we.WeFlags.parse(args[2]) : null;
                Location a = plugin.getSelection().getPos1(p.getUniqueId());
                Location b = plugin.getSelection().getPos2(p.getUniqueId());
                try {
                    java.nio.file.Path workRoot = getPluginWorkDir();
                    java.nio.file.Path sessionDir = workRoot.resolve(id + "-" + System.nanoTime());
                    java.nio.file.Files.createDirectories(sessionDir);
                    Path schem = sessionDir.resolve(id + ".schem");
                    com.skydinse.schemflow.we.WorldEditUtils.exportCuboid(a, b, schem, weFlags);
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        Path schm = sessionDir.resolve(id + ".schm");
                        try {
                            zipSingle(schem, schm);
                            s3.uploadSchm(schm, id);
                            sendMM(sender, prefix() + " <green>Uploaded schematic </green><aqua>" + id + ".schm</aqua>");
                            plugin.refreshSchematicCacheAsync();
                        } catch (Exception ex) {
                            sendMM(sender, prefix() + " <red>Upload failed:</red> <grey>" + ex.getMessage() + "</grey>");
                        } finally {
                            try { java.nio.file.Files.deleteIfExists(schem); java.nio.file.Files.deleteIfExists(schm); java.nio.file.Files.delete(sessionDir); } catch (Exception ignore) {}
                        }
                    });
                } catch (Exception ex) {
                    sendMM(sender, prefix() + " <red>Export failed:</red> <grey>" + ex.getMessage() + "</grey>");
                }
            }
            case "paste" -> {
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow paste</gradient> <white><i>name</i></white> <white><i>[-flags]</i></white>"); return true; }
                String name = args[1];
                final com.skydinse.schemflow.we.WeFlags weFlags = (args.length >= 3 && args[2].startsWith("-")) ? com.skydinse.schemflow.we.WeFlags.parse(args[2]) : null;
                String dest = plugin.getConfig().getString("downloadDir", "plugins/Skript/schematics");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Path schm = s3.fetchSchm(name, dest);
                        if (com.skydinse.schemflow.util.ZipUtils.isZip(schm)) {
                            Path outDir = schm.getParent().resolve(name);
                            com.skydinse.schemflow.util.SafeIO.ensureDir(outDir);
                            com.skydinse.schemflow.util.ZipUtils.unzip(schm, outDir);
                            try (java.util.stream.Stream<Path> st = java.nio.file.Files.walk(outDir)) {
                                Path schem = st.filter(pth -> pth.toString().toLowerCase().endsWith(".schem")).findFirst().orElse(null);
                                if (schem != null) {
                                    org.bukkit.Location at = p.getLocation();
                                    final Path schemFile = schem;
                                    final com.skydinse.schemflow.we.WeFlags flagsFinal = weFlags;
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        try {
                                            boolean ents = flagsFinal != null && flagsFinal.entities; // default false unless -e
                                            boolean ignoreAir = flagsFinal != null && flagsFinal.ignoreAir; // -a
                                            boolean biomes = flagsFinal == null || flagsFinal.biomes; // default true unless -b omitted? keep true by default
                                            com.skydinse.schemflow.we.WorldEditUtils.paste(at, schemFile, ents, ignoreAir, biomes);
                                            sendMM(p, prefix() + " <green>Pasted schematic.</green>");
                                        } catch (Exception ignored) {
                                            sendMM(p, prefix() + " <red>Failed to paste schematic.</red>");
                                        }
                                    });
                                }
                            }
                        }
                        sendMM(sender, prefix() + " <green>Downloaded schematic </green><aqua>" + name + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Paste failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "delete" -> {
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow delete</gradient> <white><i>name</i></white>"); return true; }
                String name = args[1];
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        s3.deleteSchm(name);
                        plugin.refreshSchematicCacheAsync();
                        sendMM(sender, prefix() + " <yellow>Deleted schematic </yellow><aqua>" + name + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Delete failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "provision" -> {
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow provision</gradient> <white><i>world</i></white>"); return true; }
                String world = args[1];
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getProvisioner().provisionByName(world));
                sendMM(sender, prefix() + " <yellow>Provisioning started for </yellow><aqua>" + world + "</aqua>");
            }
            default -> printHelp(sender, label);
        }
        return true;
    }

    private void printHelp(CommandSender sender, String label) {
        String cmdName = "SchemFlow";
        String cmd = "/" + cmdName;
        var mm = com.skydinse.schemflow.SchemFlowPlugin.getInstance().getMiniMessage();
    String msg = "<dark_grey>--- <gradient:#ff77e9:#ff4fd8:#ff77e9>SchemFlow</gradient> <grey>Commands:</grey> ---</dark_grey>\n\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " list</gradient> <dark_grey>-</dark_grey> List schematics</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " fetch</gradient> <white><i>name</i></white> <white><i>[destDir]</i></white> <dark_grey>-</dark_grey> Download schematic</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " pos1</gradient> <dark_grey>-</dark_grey> Set first position</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " pos2</gradient> <dark_grey>-</dark_grey> Set second position</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " upload</gradient> <white><i>id</i></white> <dark_grey>-</dark_grey> Export selection and upload</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " paste</gradient> <white><i>name</i></white> <dark_grey>-</dark_grey> Fetch and paste at location</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " delete</gradient> <white><i>name</i></white> <dark_grey>-</dark_grey> Delete schematic from storage</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " cache</gradient> <dark_grey>-</dark_grey> Refresh schematic name cache now</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " reload</gradient> <dark_grey>-</dark_grey> Reload config and services</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " provision</gradient> <white><i>world</i></white> <dark_grey>-</dark_grey> Create/load and paste base</grey>";
        var adv = com.skydinse.schemflow.SchemFlowPlugin.getInstance().getAudiences();
        if (adv != null) adv.sender(sender).sendMessage(mm.deserialize(msg));
        else sender.sendMessage(mm.deserialize(msg));
        if (adv != null) adv.sender(sender).sendMessage(mm.deserialize("<dark_grey>------------------------------</dark_grey>"));
        else sender.sendMessage(mm.deserialize("<dark_grey>------------------------------</dark_grey>"));
    }

    private void zipSingle(Path input, Path zipOut) throws Exception {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(zipOut))) {
            java.util.zip.ZipEntry e = new java.util.zip.ZipEntry(input.getFileName().toString());
            zos.putNextEntry(e);
            java.nio.file.Files.copy(input, zos);
            zos.closeEntry();
        }
    }

    private java.nio.file.Path getPluginWorkDir() throws java.io.IOException {
        java.nio.file.Path work = plugin.getDataFolder().toPath().resolve("work");
        if (!java.nio.file.Files.exists(work)) {
            java.nio.file.Files.createDirectories(work);
        }
        return work;
    }

    private String prefix() {
        return "<dark_grey>[<gradient:#ff77e9:#ff4fd8:#ff77e9>SchemFlow</gradient>]</dark_grey>";
    }

    private void sendMM(CommandSender sender, String mini) {
        var mm = plugin.getMiniMessage();
        var adv = plugin.getAudiences();
        if (adv != null) adv.sender(sender).sendMessage(mm.deserialize(mini));
        else sender.sendMessage(mm.deserialize(mini));
    }
}
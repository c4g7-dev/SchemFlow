package com.c4g7.schemflow.cmd;

import com.c4g7.schemflow.S3Service;
import com.c4g7.schemflow.SchemFlowPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;

public class SchemFlowCommand implements CommandExecutor {
    private final SchemFlowPlugin plugin;

    public SchemFlowCommand(SchemFlowPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission("schemflow.help") || sender.hasPermission("schemflow.admin")) {
                printHelp(sender, label);
            } else {
                sendMM(sender, prefix() + " <red>No permission.</red>");
            }
            return true;
        }
        S3Service s3 = plugin.getS3Service();
        switch (args[0].toLowerCase()) {
            case "help" -> {
                if (!check(sender, "schemflow.help")) return true;
                printHelp(sender, label);
                return true;
            }
            case "reload" -> {
                if (!check(sender, "schemflow.reload")) return true;
                boolean ok = plugin.reloadSchemFlowConfig();
                if (!ok) {
                    sendMM(sender, prefix() + " <red>Reload failed.</red>");
                    return true;
                }
                sendMM(sender, prefix() + " <green>Config reloaded.</green> <grey>Refreshing cache...</grey>");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        var stats = refreshCacheAllGroups();
                        sendMM(sender, prefix() + " <green>Cache size:</green> <aqua>" + stats.total + "</aqua> <grey>from</grey> <aqua>" + stats.groupCount + "</aqua> <grey>groups</grey>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Cache refresh failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
                return true;
            }
            case "cache" -> {
                if (!check(sender, "schemflow.cache")) return true;
                sendMM(sender, prefix() + " <grey>Refreshing schematic cache...</grey>");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        var stats = refreshCacheAllGroups();
                        sendMM(sender, prefix() + " <green>Cache refreshed:</green> <aqua>" + stats.total + "</aqua> <grey>schematics from</grey> <aqua>" + stats.groupCount + "</aqua> <grey>groups</grey>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Cache refresh failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
                return true;
            }
            case "list" -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!sender.hasPermission("schemflow.list") && !sender.hasPermission("schemflow.admin")) { sendMM(sender, prefix() + " <red>No permission.</red>"); return; }
                try {
                    // Build grouped listing
                    java.util.List<String> groups = s3.listGroups();
                    java.util.Map<String, java.util.List<String>> grouped = new java.util.LinkedHashMap<>();
                    // Always include default first
                    grouped.put("Default", s3.listSchm(null));
                    for (String g : groups) {
                        if (g.equalsIgnoreCase("default")) continue;
                        grouped.put(g, s3.listSchm(g));
                    }
                    StringBuilder sb = new StringBuilder();
                    int total = 0;
                    for (var entry : grouped.entrySet()) {
                        java.util.List<String> vals = entry.getValue();
                        if (vals == null || vals.isEmpty()) continue;
                        total += vals.size();
                        sb.append("<grey>" + entry.getKey() + ":</grey>\n");
                        for (String n : vals) sb.append(" - <aqua>" + n + "</aqua>\n");
                    }
                    if (total == 0) sendMM(sender, prefix() + " <grey>No schematics found.</grey>");
                    else sendMM(sender, prefix() + " <grey>Schematics (" + total + "):</grey>\n" + sb);
                    plugin.refreshSchematicCacheAsync();
                } catch (Exception e) {
                    sendMM(sender, prefix() + " <red>List failed:</red> <grey>" + e.getMessage() + "</grey>");
                }
            });
            case "fetch" -> {
                if (!check(sender, "schemflow.fetch")) return true;
                if (args.length < 2) {
                    sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow fetch</gradient> <white><i>[group:]name</i></white> <white><i>[destDir]</i></white>");
                    return true;
                }
                String tmp = args[1];
                String g = null;
                int c = tmp.indexOf(':');
                if (c > 0) { g = tmp.substring(0, c); tmp = tmp.substring(c + 1); }
                final String name = tmp;
                final String group = (g != null && !g.isBlank()) ? g : getGroupFlag(args);
                String dest = args.length >= 3 ? args[2] : plugin.getConfig().getString("downloadDir", "plugins/FlowStack/schematics");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Path p = (group == null) ? s3.fetchSchm(name, dest) : s3.fetchSchm(name, group, dest);
                        sendMM(sender, prefix() + " <green>Downloaded to </green><aqua>" + p + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Fetch failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "pos1" -> {
                if (!check(sender, "schemflow.pos1")) return true;
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                plugin.getSelection().setPos1(p.getUniqueId(), p.getLocation());
                sendMM(sender, prefix() + " <green>Set </green><aqua>pos1</aqua><green> at your location.</green>");
            }
            case "pos2" -> {
                if (!check(sender, "schemflow.pos2")) return true;
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                plugin.getSelection().setPos2(p.getUniqueId(), p.getLocation());
                sendMM(sender, prefix() + " <green>Set </green><aqua>pos2</aqua><green> at your location.</green>");
            }
            case "upload" -> {
                if (!check(sender, "schemflow.upload")) return true;
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow upload</gradient> <white><i>id</i></white> <white><i>[-flags]</i></white> <grey>[-group <i>name</i>]</grey>"); return true; }
                if (!plugin.getSelection().hasBoth(p.getUniqueId())) { sendMM(sender, prefix() + " <red>Set pos1 and pos2 first.</red>"); return true; }
                String id = args[1];
                final com.c4g7.schemflow.we.WeFlags weFlags = (args.length >= 3 && args[2].startsWith("-")) ? com.c4g7.schemflow.we.WeFlags.parse(args[2]) : null;
                String group = getGroupFlag(args);
                Location a = plugin.getSelection().getPos1(p.getUniqueId());
                Location b = plugin.getSelection().getPos2(p.getUniqueId());
                try {
                    java.nio.file.Path workRoot = getPluginWorkDir();
                    java.nio.file.Path sessionDir = workRoot.resolve(id + "-" + System.nanoTime());
                    java.nio.file.Files.createDirectories(sessionDir);
                    Path schem = sessionDir.resolve(id + ".schem");
                    com.c4g7.schemflow.we.WorldEditUtils.exportCuboid(a, b, schem, weFlags);
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        Path schm = sessionDir.resolve(id + ".schm");
                        try {
                            zipSingle(schem, schm);
                            if (group == null) s3.uploadSchm(schm, id); else s3.uploadSchm(schm, id, group);
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
                if (!check(sender, "schemflow.paste")) return true;
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow paste</gradient> <white><i>[group:]name</i></white> <white><i>[-flags]</i></white>"); return true; }
                String tmpName = args[1];
                String tmpGroup = null;
                int colon = tmpName.indexOf(':');
                if (colon > 0) { tmpGroup = tmpName.substring(0, colon); tmpName = tmpName.substring(colon + 1); }
                final com.c4g7.schemflow.we.WeFlags weFlags = (args.length >= 3 && args[2].startsWith("-")) ? com.c4g7.schemflow.we.WeFlags.parse(args[2]) : null;
                String dest = plugin.getConfig().getString("downloadDir", "plugins/FlowStack/schematics");
                final String name = tmpName;
                final String group = tmpGroup;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Path schm = (group == null) ? s3.fetchSchm(name, dest) : s3.fetchSchm(name, group, dest);
                        if (com.c4g7.schemflow.util.ZipUtils.isZip(schm)) {
                            Path outDir = schm.getParent().resolve(name);
                            com.c4g7.schemflow.util.SafeIO.ensureDir(outDir);
                            com.c4g7.schemflow.util.ZipUtils.unzip(schm, outDir);
                            try (java.util.stream.Stream<Path> st = java.nio.file.Files.walk(outDir)) {
                                Path schem = st.filter(pth -> pth.toString().toLowerCase().endsWith(".schem")).findFirst().orElse(null);
                                if (schem != null) {
                                    org.bukkit.Location at = p.getLocation();
                                    final Path schemFile = schem;
                                    final com.c4g7.schemflow.we.WeFlags flagsFinal = weFlags;
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        try {
                                            boolean ents = flagsFinal != null && flagsFinal.entities;
                                            boolean ignoreAir = flagsFinal != null && flagsFinal.ignoreAir;
                                            boolean biomes = flagsFinal == null || flagsFinal.biomes;
                                            com.c4g7.schemflow.we.WorldEditUtils.paste(at, schemFile, ents, ignoreAir, biomes);
                                            if (p != null) {
                                                plugin.getUndoManager().record(new com.c4g7.schemflow.util.UndoManager.Action(
                                                        com.c4g7.schemflow.util.UndoManager.Action.Type.PASTE,
                                                        p.getUniqueId(), group, name, at, schemFile
                                                ));
                                            }
                                            sendMM(p, prefix() + " <green>Pasted schematic.</green>");
                                        } catch (Exception ignored) {
                                            sendMM(p, prefix() + " <red>Failed to paste schematic.</red>");
                                        }
                                    });
                                }
                            }
                        }
                        sendMM(sender, prefix() + " <green>Downloaded schematic </green><aqua>" + ((group != null) ? group + ":" : "") + name + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Paste failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "delete" -> {
                if (!check(sender, "schemflow.delete")) return true;
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow delete</gradient> <white><i>[group:]name</i></white>"); return true; }
                String dName = args[1];
                String dGroup = null;
                int colon = dName.indexOf(':'); if (colon > 0) { dGroup = dName.substring(0, colon); dName = dName.substring(colon + 1); }
                final String delName = dName;
                final String delGroup = dGroup;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        if (delGroup == null) s3.trashSchm(delName); else s3.trashSchm(delName, delGroup);
                        plugin.refreshSchematicCacheAsync();
                        sendMM(sender, prefix() + " <yellow>Deleted schematic </yellow><aqua>" + ((delGroup != null) ? delGroup + ":" : "") + delName + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Delete failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "undo" -> {
                if (!(sender instanceof Player)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                var act = plugin.getUndoManager().popUndo();
                if (act == null) { sendMM(sender, prefix() + " <grey>Nothing to undo.</grey>"); return true; }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        if (act.type == com.c4g7.schemflow.util.UndoManager.Action.Type.PASTE) {
                            // Re-paste air over region by reusing same schematic with ignoreAir=false to revert
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                try { com.c4g7.schemflow.we.WorldEditUtils.paste(act.at, act.schemFile, false, false, true); } catch (Exception ignored) {}
                            });
                        } else if (act.type == com.c4g7.schemflow.util.UndoManager.Action.Type.DELETE) {
                            plugin.getS3Service().restoreSchm(act.name, act.group);
                            plugin.refreshSchematicCacheAsync();
                        }
                        plugin.getUndoManager().pushRedo(act);
                        sendMM(sender, prefix() + " <green>Undo done.</green>");
                    } catch (Exception ex) {
                        sendMM(sender, prefix() + " <red>Undo failed:</red> <grey>" + ex.getMessage() + "</grey>");
                    }
                });
            }
            case "redo" -> {
                if (!(sender instanceof Player)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                var act = plugin.getUndoManager().popRedo();
                if (act == null) { sendMM(sender, prefix() + " <grey>Nothing to redo.</grey>"); return true; }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        if (act.type == com.c4g7.schemflow.util.UndoManager.Action.Type.PASTE) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                try { com.c4g7.schemflow.we.WorldEditUtils.paste(act.at, act.schemFile, true, false, true); } catch (Exception ignored) {}
                            });
                        } else if (act.type == com.c4g7.schemflow.util.UndoManager.Action.Type.DELETE) {
                            if (act.group == null) plugin.getS3Service().trashSchm(act.name); else plugin.getS3Service().trashSchm(act.name, act.group);
                            plugin.refreshSchematicCacheAsync();
                        }
                        sendMM(sender, prefix() + " <green>Redo done.</green>");
                    } catch (Exception ex) {
                        sendMM(sender, prefix() + " <red>Redo failed:</red> <grey>" + ex.getMessage() + "</grey>");
                    }
                });
            }
            case "groups" -> {
                if (!check(sender, "schemflow.groups")) return true;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        java.util.List<String> groups = s3.listGroups();
                        if (groups.isEmpty()) sendMM(sender, prefix() + " <grey>No groups found.</grey>");
                        else sendMM(sender, prefix() + " <grey>Groups:</grey> <aqua>" + String.join(", ", groups) + "</aqua>");
                    } catch (Exception ex) {
                        sendMM(sender, prefix() + " <red>Failed to list groups:</red> <grey>" + ex.getMessage() + "</grey>");
                    }
                });
            }
            case "group" -> {
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow group create</gradient> <white><i>name</i></white>"); return true; }
                if ("create".equalsIgnoreCase(args[1])) {
                    if (!check(sender, "schemflow.group.create")) return true;
                    if (args.length < 3) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow group create</gradient> <white><i>name</i></white>"); return true; }
                    String grp = args[2];
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            s3.createGroup(grp);
                            sendMM(sender, prefix() + " <green>Created group </green><aqua>" + grp + "</aqua>");
                        } catch (Exception ex) {
                            sendMM(sender, prefix() + " <red>Failed to create group:</red> <grey>" + ex.getMessage() + "</grey>");
                        }
                    });
                } else {
                    sendMM(sender, prefix() + " <grey>Unknown group subcommand.</grey>");
                }
            }
            case "provision" -> {
                if (!check(sender, "schemflow.provision")) return true;
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
        var mm = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getMiniMessage();
    String msg = "<dark_grey>--- <gradient:#ff77e9:#ff4fd8:#ff77e9>SchemFlow</gradient> <grey>Commands:</grey> ---</dark_grey>\n\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " list</gradient> <dark_grey>-</dark_grey> List schematics grouped by group</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " fetch</gradient> <white><i>[group:]name</i></white> <white><i>[destDir]</i></white> <dark_grey>-</dark_grey> Download schematic</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " pos1</gradient> <dark_grey>-</dark_grey> Set first position</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " pos2</gradient> <dark_grey>-</dark_grey> Set second position</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " upload</gradient> <white><i>id</i></white> <dark_grey>-</dark_grey> Export selection and upload</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " paste</gradient> <white><i>[group:]name</i></white> <dark_grey>-</dark_grey> Fetch and paste at location</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " delete</gradient> <white><i>[group:]name</i></white> <dark_grey>-</dark_grey> Delete schematic from storage</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " cache</gradient> <dark_grey>-</dark_grey> Refresh schematic name cache (all groups)</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " reload</gradient> <dark_grey>-</dark_grey> Reload config and services</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " provision</gradient> <white><i>world</i></white> <dark_grey>-</dark_grey> Create/load and paste base</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " groups</gradient> <dark_grey>-</dark_grey> List all groups</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " group create</gradient> <white><i>name</i></white> <dark_grey>-</dark_grey> Create group path</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " undo</gradient> <dark_grey>-</dark_grey> Undo last paste/delete</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " redo</gradient> <dark_grey>-</dark_grey> Redo last undo</grey>";
        var adv = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getAudiences();
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

    private boolean check(CommandSender sender, String node) {
        if (sender.hasPermission("schemflow.admin") || sender.hasPermission(node)) return true;
        sendMM(sender, prefix() + " <red>No permission.</red>");
        return false;
    }

    private String getGroupFlag(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-group".equalsIgnoreCase(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static class CacheStats { int total; int groupCount; }

    private CacheStats refreshCacheAllGroups() throws Exception {
        CacheStats cs = new CacheStats();
        var s3 = plugin.getS3Service();
        java.util.List<String> groups = s3.listGroups();
        java.util.List<String> all = new java.util.ArrayList<>();
        // default first
        java.util.List<String> def = s3.listSchm();
        all.addAll(def);
        cs.total += def.size();
        cs.groupCount = def.isEmpty() ? 0 : 1;
        for (String g : groups) {
            java.util.List<String> li = s3.listSchm(g);
            if (!li.isEmpty()) cs.groupCount++;
            // Add both plain and group-prefixed variants for discoverability
            all.addAll(li);
            all.addAll(prefixAll(g, li));
            cs.total += li.size();
        }
        // Store unprefixed names for backwards compatibility in cache for tabcomplete base matching
        plugin.getSchematicCache().clear();
        plugin.getSchematicCache().addAll(all);
        return cs;
    }

    private java.util.List<String> prefixAll(String group, java.util.List<String> names) {
        java.util.List<String> out = new java.util.ArrayList<>(names.size());
        for (String n : names) out.add(group + ":" + (n.endsWith(".schm") ? n.substring(0, n.length() - 5) : n));
        return out;
    }
}

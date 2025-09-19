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
                    sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow fetch</gradient> <white><i>[group:]name</i></white> <grey>or</grey> <white><i>/:path/to/name</i></white> <white><i>[destDir]</i></white>");
                    return true;
                }
                String tmp = args[1];
                String g = null;
                int c = tmp.indexOf(':');
                if (c > 0) { g = tmp.substring(0, c); tmp = tmp.substring(c + 1); }
                final String name = tmp;
                final String group = (g != null && !g.isBlank()) ? g : null;
                String dest = args.length >= 3 ? args[2] : plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Path p;
                        if (args[1].startsWith("/")) p = s3.fetchByPath(args[1].substring(1), dest);
                        else p = (group == null) ? s3.fetchSchm(name, dest) : s3.fetchSchm(name, group, dest);
                        sendMM(sender, prefix() + " <green>Downloaded to </green><aqua>" + p + "</aqua>");
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Fetch failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "local" -> {
                if (!check(sender, "schemflow.local")) return true;
                if (args.length > 1 && args[1].equalsIgnoreCase("delete")) {
                    // Handle local delete subcommand
                    if (args.length < 3) {
                        sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow local delete</gradient> <white><i>name</i></white> <grey>[--confirm]</grey>");
                        return true;
                    }
                    String fileName = args[2];
                    String downloadDir = plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics");
                    
                    if (!hasFlag(args, "--confirm")) {
                        sendMM(sender, prefix() + " <yellow>This will delete local schematic </yellow><aqua>" + fileName + "</aqua><yellow>. Use</yellow> <aqua>--confirm</aqua> <yellow>to proceed.</yellow>");
                        return true;
                    }
                    
                    try {
                        java.nio.file.Path downloadPath = java.nio.file.Paths.get(downloadDir);
                        java.nio.file.Path file = downloadPath.resolve(fileName);
                        
                        // Add extension if not present
                        if (!fileName.toLowerCase().endsWith(".schem") && !fileName.toLowerCase().endsWith(".schematic")) {
                            file = downloadPath.resolve(fileName + ".schem");
                        }
                        
                        if (java.nio.file.Files.exists(file)) {
                            java.nio.file.Files.delete(file);
                            sendMM(sender, prefix() + " <green>Deleted local schematic </green><aqua>" + fileName + "</aqua>");
                        } else {
                            sendMM(sender, prefix() + " <red>Local schematic not found:</red> <grey>" + fileName + "</grey>");
                        }
                    } catch (Exception ex) {
                        sendMM(sender, prefix() + " <red>Delete failed:</red> <grey>" + ex.getMessage() + "</grey>");
                    }
                } else {
                    // List local schematics
                    String downloadDir = plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics");
                    try {
                        java.nio.file.Path downloadPath = java.nio.file.Paths.get(downloadDir);
                        if (!java.nio.file.Files.exists(downloadPath)) {
                            sendMM(sender, prefix() + " <grey>No local schematics found. Directory does not exist:</grey> <aqua>" + downloadDir + "</aqua>");
                            return true;
                        }
                        
                        java.util.List<String> files = new java.util.ArrayList<>();
                        try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = java.nio.file.Files.newDirectoryStream(downloadPath, "*.{schem,schematic}")) {
                            for (java.nio.file.Path file : stream) {
                                files.add(file.getFileName().toString());
                            }
                        }
                        
                        if (files.isEmpty()) {
                            sendMM(sender, prefix() + " <grey>No local schematics found in:</grey> <aqua>" + downloadDir + "</aqua>");
                        } else {
                            files.sort(String.CASE_INSENSITIVE_ORDER);
                            StringBuilder sb = new StringBuilder();
                            for (String file : files) {
                                sb.append(" - <aqua>").append(file).append("</aqua>\n");
                            }
                            sendMM(sender, prefix() + " <grey>Local schematics (" + files.size() + "):</grey>\n" + sb.toString());
                        }
                    } catch (Exception ex) {
                        sendMM(sender, prefix() + " <red>Failed to list local schematics:</red> <grey>" + ex.getMessage() + "</grey>");
                    }
                }
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
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow upload</gradient> <white><i>id</i></white> <white><i>[-flags] [-update]</i></white> <grey>[-group <i>name</i>] [--confirm]</grey>"); return true; }
                if (!plugin.getSelection().hasBoth(p.getUniqueId())) { sendMM(sender, prefix() + " <red>Set pos1 and pos2 first.</red>"); return true; }
                String id = args[1];
                final com.c4g7.schemflow.we.WeFlags weFlags = com.c4g7.schemflow.we.WeFlags.parseArgs(args);
                String group = getGroupFlag(args);
                Location a = plugin.getSelection().getPos1(p.getUniqueId());
                Location b = plugin.getSelection().getPos2(p.getUniqueId());
                try {
                    java.nio.file.Path workRoot = getPluginWorkDir();
                    java.nio.file.Path sessionDir = workRoot.resolve(id + "-" + System.nanoTime());
                    java.nio.file.Files.createDirectories(sessionDir);
                    String ext = plugin.getS3Service().getExtension();
                    Path schem = sessionDir.resolve(id + ext);
                    com.c4g7.schemflow.we.WorldEditUtils.exportCuboid(a, b, schem, weFlags);
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            // Check if update flag is present and --confirm is provided
                            if (weFlags.update) {
                                if (!hasFlag(args, "--confirm")) {
                                    sendMM(sender, prefix() + " <yellow>This will overwrite existing schematic. Use</yellow> <aqua>--confirm</aqua> <yellow>to proceed.</yellow>");
                                    return;
                                }
                                // Use overwrite method for updates
                                if (group == null) s3.uploadSchmOverwrite(schem, id); 
                                else s3.uploadSchmOverwrite(schem, id, group);
                                sendMM(sender, prefix() + " <green>Updated schematic </green><aqua>" + id + ext + "</aqua>");
                            } else {
                                // Regular upload (will fail if exists)
                                if (group == null) s3.uploadSchm(schem, id); 
                                else s3.uploadSchm(schem, id, group);
                                sendMM(sender, prefix() + " <green>Uploaded schematic </green><aqua>" + id + ext + "</aqua>");
                            }
                            plugin.refreshSchematicCacheAsync();
                        } catch (Exception ex) {
                            sendMM(sender, prefix() + " <red>Upload failed:</red> <grey>" + ex.getMessage() + "</grey>");
                        } finally {
                            try { java.nio.file.Files.deleteIfExists(schem); java.nio.file.Files.delete(sessionDir); } catch (Exception ignore) {}
                        }
                    });
                } catch (Exception ex) {
                    sendMM(sender, prefix() + " <red>Export failed:</red> <grey>" + ex.getMessage() + "</grey>");
                }
            }
            case "update" -> {
                if (!check(sender, "schemflow.upload")) return true;
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow update</gradient> <white><i>[group:]name</i></white> <white><i>[-flags]</i></white> <grey>[--confirm]</grey>"); return true; }
                if (!plugin.getSelection().hasBoth(p.getUniqueId())) { sendMM(sender, prefix() + " <red>Set pos1 and pos2 first.</red>"); return true; }
                
                // Parse schematic name and group
                String tmpName = args[1];
                String tmpGroup = null;
                int colon = tmpName.indexOf(':');
                if (colon > 0) { tmpGroup = tmpName.substring(0, colon); tmpName = tmpName.substring(colon + 1); }
                final String name = tmpName;
                final String group = tmpGroup;
                
                // Check if --confirm is provided
                if (!hasFlag(args, "--confirm")) {
                    sendMM(sender, prefix() + " <yellow>This will overwrite existing schematic </yellow><aqua>" + (group != null ? group + ":" : "") + name + "</aqua><yellow>. Use</yellow> <aqua>--confirm</aqua> <yellow>to proceed.</yellow>");
                    return true;
                }
                
                final com.c4g7.schemflow.we.WeFlags weFlags = com.c4g7.schemflow.we.WeFlags.parseArgs(args);
                Location a = plugin.getSelection().getPos1(p.getUniqueId());
                Location b = plugin.getSelection().getPos2(p.getUniqueId());
                try {
                    java.nio.file.Path workRoot = getPluginWorkDir();
                    java.nio.file.Path sessionDir = workRoot.resolve(name + "-" + System.nanoTime());
                    java.nio.file.Files.createDirectories(sessionDir);
                    String ext = plugin.getS3Service().getExtension();
                    Path schem = sessionDir.resolve(name + ext);
                    com.c4g7.schemflow.we.WorldEditUtils.exportCuboid(a, b, schem, weFlags);
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            // Always use overwrite method for update command
                            if (group == null) s3.uploadSchmOverwrite(schem, name); 
                            else s3.uploadSchmOverwrite(schem, name, group);
                            sendMM(sender, prefix() + " <green>Updated schematic </green><aqua>" + (group != null ? group + ":" : "") + name + ext + "</aqua>");
                            plugin.refreshSchematicCacheAsync();
                        } catch (Exception ex) {
                            sendMM(sender, prefix() + " <red>Update failed:</red> <grey>" + ex.getMessage() + "</grey>");
                        } finally {
                            try { java.nio.file.Files.deleteIfExists(schem); java.nio.file.Files.delete(sessionDir); } catch (Exception ignore) {}
                        }
                    });
                } catch (Exception ex) {
                    sendMM(sender, prefix() + " <red>Export failed:</red> <grey>" + ex.getMessage() + "</grey>");
                }
            }
            case "paste" -> {
                if (!check(sender, "schemflow.paste")) return true;
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow paste</gradient> <white><i>[group:]name</i></white> <white><i>or</i></white> <white><i>local:name</i></white> <white><i>[-flags]</i></white>"); return true; }
                String tmpName = args[1];
                String tmpGroup = null;
                boolean useLocal = false;
                
                // Check if using local: prefix
                if (tmpName.startsWith("local:")) {
                    useLocal = true;
                    tmpName = tmpName.substring(6); // Remove "local:" prefix
                } else {
                    // Parse group:name format for server schematics
                    int colon = tmpName.indexOf(':');
                    if (colon > 0) { tmpGroup = tmpName.substring(0, colon); tmpName = tmpName.substring(colon + 1); }
                }
                
                final com.c4g7.schemflow.we.WeFlags weFlags = com.c4g7.schemflow.we.WeFlags.parseArgs(args);
                final String name = tmpName;
                final String group = tmpGroup;
                final boolean isLocal = useLocal;
                
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        final Path schemFile;
                        if (isLocal) {
                            // Use local directory
                            String downloadDir = plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics");
                            java.nio.file.Path downloadPath = java.nio.file.Paths.get(downloadDir);
                            Path tempFile = downloadPath.resolve(name);
                            
                            // Add extension if not present
                            if (!name.toLowerCase().endsWith(".schem") && !name.toLowerCase().endsWith(".schematic")) {
                                tempFile = downloadPath.resolve(name + ".schem");
                            }
                            schemFile = tempFile;
                            
                            if (!java.nio.file.Files.exists(schemFile)) {
                                sendMM(sender, prefix() + " <red>Local schematic not found:</red> <grey>" + name + "</grey>");
                                return;
                            }
                        } else {
                            // Fetch from server to ephemeral cache
                            java.nio.file.Path eph = plugin.getDataFolder().toPath().resolve("work").resolve("cache");
                            try { java.nio.file.Files.createDirectories(eph); } catch (Exception ignore) {}
                            String targetDir = eph.toString();
                            schemFile = (group == null) ? s3.fetchSchm(name, targetDir) : s3.fetchSchm(name, group, targetDir);
                        }
                        
                        org.bukkit.Location at = p.getLocation();

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            try {
                                boolean ents = weFlags.entities;
                                boolean ignoreAir = weFlags.ignoreAir;
                                boolean biomes = weFlags.biomes;
                                var we = com.sk89q.worldedit.WorldEdit.getInstance();
                                var wePlayer = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(p);
                                var local = we.getSessionManager().get(wePlayer);
                                try (var edit = local.createEditSession(wePlayer)) {
                                    edit.setReorderMode(com.sk89q.worldedit.EditSession.ReorderMode.MULTI_STAGE);
                                    var format = com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(schemFile.toFile());
                                    if (format == null) throw new IllegalStateException("Unknown schematic format: " + schemFile);
                                    try (var reader = format.getReader(java.nio.file.Files.newInputStream(schemFile))) {
                                        var clipboard = reader.read();
                                        var op = new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                                                .createPaste(edit)
                                                .to(com.sk89q.worldedit.math.BlockVector3.at(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
                                                .ignoreAirBlocks(ignoreAir)
                                                .copyEntities(ents)
                                                .copyBiomes(biomes)
                                                .build();
                                        com.sk89q.worldedit.function.operation.Operations.complete(op);
                                    }
                                    // Ensure WorldEdit history captures this edit for //undo
                                    local.remember(edit);
                                }
                                sendMM(p, prefix() + " <green>Pasted schematic.</green> <grey>Use</grey> <aqua>//undo</aqua> <grey>or</grey> <aqua>/SchemFlow undo</aqua> <grey>to revert.</grey>");
                            } catch (Exception ex) {
                                sendMM(p, prefix() + " <red>Paste failed:</red> <grey>" + ex.getMessage() + "</grey>");
                            } finally {
                                // Only delete file if using ephemeral cache (not local)
                                if (!isLocal) {
                                    try { java.nio.file.Files.deleteIfExists(schemFile); } catch (Exception ignore) {}
                                }
                            }
                        });
                        // No secondary cache message
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
            case "restore" -> {
                if (!check(sender, "schemflow.restore")) return true;
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow restore</gradient> <white><i>name</i></white> <grey>[-group <i>destGroup</i>]</grey>"); return true; }
                String rName = args[1]; String rGroup = getGroupFlag(args);
                final String name = rName; final String group = rGroup;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        if (group == null) s3.restoreSchm(name); else s3.restoreSchm(name, group);
                        plugin.refreshSchematicCacheAsync();
                        sendMM(sender, prefix() + " <green>Restored schematic </green><aqua>" + name + "</aqua>" + (group != null ? " <grey>to group</grey> <aqua>" + group + "</aqua>" : ""));
                    } catch (Exception e) {
                        sendMM(sender, prefix() + " <red>Restore failed:</red> <grey>" + e.getMessage() + "</grey>");
                    }
                });
            }
            case "trash" -> {
                if (args.length == 1) {
                    if (!check(sender, "schemflow.list")) return true;
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            java.util.List<String> items = s3.listTrash();
                            if (items.isEmpty()) sendMM(sender, prefix() + " <grey>Trash is empty.</grey>");
                            else sendMM(sender, prefix() + " <grey>Trashed schematics:</grey> <aqua>" + String.join(", ", items) + "</aqua>");
                        } catch (Exception ex) {
                            sendMM(sender, prefix() + " <red>Failed to list trash:</red> <grey>" + ex.getMessage() + "</grey>");
                        }
                    });
                } else if ("clear".equalsIgnoreCase(args[1])) {
                    if (!check(sender, "schemflow.trash.clear")) return true;
                    boolean confirmed = java.util.Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("--confirm"));
                    if (!confirmed) {
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                            try {
                                java.util.List<String> items = s3.listTrash();
                                sendMM(sender, prefix() + " <yellow>This will permanently delete </yellow><aqua>" + items.size() + "</aqua> <yellow>trashed schematics.</yellow> <grey>Re-run with</grey> <aqua>--confirm</aqua> <grey>to proceed.</grey>");
                            } catch (Exception ex) {
                                sendMM(sender, prefix() + " <red>Trash lookup failed:</red> <grey>" + ex.getMessage() + "</grey>");
                            }
                        });
                        return true;
                    }
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try { s3.clearTrash(); sendMM(sender, prefix() + " <green>Trash cleared.</green>"); }
                        catch (Exception ex) { sendMM(sender, prefix() + " <red>Trash clear failed:</red> <grey>" + ex.getMessage() + "</grey>"); }
                    });
                } else {
                    sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow trash clear</gradient> <grey>--confirm</grey>");
                }
            }
            case "undo" -> {
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                // First handle delete undo if any SchemFlow action present
                var act = plugin.getUndoManager().popUndo();
                if (act != null) {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            if (act.type == com.c4g7.schemflow.util.UndoManager.Action.Type.DELETE) {
                                plugin.getS3Service().restoreSchm(act.name, act.group);
                                plugin.refreshSchematicCacheAsync();
                            }
                            plugin.getUndoManager().pushRedo(act);
                            sendMM(sender, prefix() + " <green>Undo (delete) done.</green>");
                        } catch (Exception ex) {
                            sendMM(sender, prefix() + " <red>Undo failed:</red> <grey>" + ex.getMessage() + "</grey>");
                        }
                    });
                } else {
                    // Delegate directly; rely solely on WorldEdit output
                    if (!p.performCommand("undo")) {
                        p.performCommand("//undo");
                    }
                }
            }
            case "redo" -> {
                if (!(sender instanceof Player p)) { sendMM(sender, prefix() + " <red>Player only.</red>"); return true; }
                var act = plugin.getUndoManager().popRedo();
                if (act != null) {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            if (act.type == com.c4g7.schemflow.util.UndoManager.Action.Type.DELETE) {
                                if (act.group == null) plugin.getS3Service().trashSchm(act.name); else plugin.getS3Service().trashSchm(act.name, act.group);
                                plugin.refreshSchematicCacheAsync();
                            }
                            sendMM(sender, prefix() + " <green>Redo (delete) done.</green>");
                        } catch (Exception ex) {
                            sendMM(sender, prefix() + " <red>Redo failed:</red> <grey>" + ex.getMessage() + "</grey>");
                        }
                    });
                } else {
                    if (!p.performCommand("redo")) {
                        p.performCommand("//redo");
                    }
                }
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
                if (args.length < 2) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow group</gradient> <white><i>create|delete|rename</i></white> ..."); return true; }
                switch (args[1].toLowerCase()) {
                    case "create" -> {
                        if (!check(sender, "schemflow.group.create")) return true;
                        if (args.length < 3) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow group create</gradient> <white><i>name</i></white>"); return true; }
                        String grp = args[2];
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                            try { s3.createGroup(grp); sendMM(sender, prefix() + " <green>Created group </green><aqua>" + grp + "</aqua>"); }
                            catch (Exception ex) {
                                String msg = ex.getMessage();
                                if (msg != null && msg.toLowerCase().contains("exists")) {
                                    sendMM(sender, prefix() + " <yellow>Group already exists:</yellow> <aqua>" + grp + "</aqua>");
                                } else {
                                    sendMM(sender, prefix() + " <red>Create failed:</red> <grey>" + msg + "</grey>");
                                }
                            }
                        });
                    }
                    case "delete" -> {
                        if (!check(sender, "schemflow.group.delete")) return true;
                        if (args.length < 3) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow group delete</gradient> <white><i>name</i></white> [--confirm]"); return true; }
                        String grp = args[2];
                        boolean confirmed = java.util.Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("--confirm"));
                        if (!confirmed) {
                            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                                try {
                                    java.util.List<String> sch = s3.listSchm(grp);
                                    sendMM(sender, prefix() + " <yellow>This will delete group </yellow><aqua>" + grp + "</aqua> <yellow>and </yellow><aqua>" + sch.size() + "</aqua> <yellow>schematics.</yellow> <grey>Re-run with</grey> <aqua>--confirm</aqua> <grey>to proceed.</grey>");
                                } catch (Exception ex) {
                                    sendMM(sender, prefix() + " <red>Lookup failed:</red> <grey>" + ex.getMessage() + "</grey>");
                                }
                            });
                            return true;
                        }
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                            try { s3.deleteGroup(grp); sendMM(sender, prefix() + " <yellow>Deleted group </yellow><aqua>" + grp + "</aqua>"); }
                            catch (Exception ex) { sendMM(sender, prefix() + " <red>Delete failed:</red> <grey>" + ex.getMessage() + "</grey>"); }
                        });
                    }
                    case "rename" -> {
                        if (!check(sender, "schemflow.group.rename")) return true;
                        if (args.length < 4) { sendMM(sender, prefix() + " <grey>Usage:</grey> <gradient:#ff77e9:#ff4fd8:#ff77e9>/SchemFlow group rename</gradient> <white><i>old new</i></white>"); return true; }
                        String oldG = args[2]; String newG = args[3];
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                            try { s3.renameGroup(oldG, newG); sendMM(sender, prefix() + " <green>Renamed group </green><aqua>" + oldG + "</aqua><grey> -> </grey><aqua>" + newG + "</aqua>"); }
                            catch (Exception ex) { sendMM(sender, prefix() + " <red>Rename failed:</red> <grey>" + ex.getMessage() + "</grey>"); }
                        });
                    }
                    default -> sendMM(sender, prefix() + " <grey>Unknown group subcommand.</grey>");
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
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " update</gradient> <white><i>[group:]name</i></white> <grey>[--confirm]</grey> <dark_grey>-</dark_grey> Update existing schematic</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " paste</gradient> <white><i>[group:]name</i></white> <white><i>or</i></white> <white><i>local:name</i></white> <dark_grey>-</dark_grey> Fetch and paste schematic</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " delete</gradient> <white><i>[group:]name</i></white> <dark_grey>-</dark_grey> Delete schematic from storage</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " local</gradient> <dark_grey>-</dark_grey> List local schematics</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " local delete</gradient> <white><i>name</i></white> <grey>[--confirm]</grey> <dark_grey>-</dark_grey> Delete local schematic</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " cache</gradient> <dark_grey>-</dark_grey> Refresh schematic name cache (all groups)</grey>\n" +
        "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " reload</gradient> <dark_grey>-</dark_grey> Reload config and services</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " provision</gradient> <white><i>world</i></white> <dark_grey>-</dark_grey> Create/load and paste base</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " groups</gradient> <dark_grey>-</dark_grey> List all groups</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " group create</gradient> <white><i>name</i></white> <dark_grey>-</dark_grey> Create group</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " group delete</gradient> <white><i>name</i></white> <grey>[--confirm]</grey> <dark_grey>-</dark_grey> Delete group (shows count before confirm)</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " group rename</gradient> <white><i>old new</i></white> <dark_grey>-</dark_grey> Rename group (non-default)</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " restore</gradient> <white><i>name</i></white> <grey>[-group <i>dest</i>]</grey> <dark_grey>-</dark_grey> Restore a trashed schematic</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " trash</gradient> <dark_grey>-</dark_grey> List trashed schematics</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " trash clear</gradient> <grey>--confirm</grey> <dark_grey>-</dark_grey> Permanently clear trash (shows count)</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " undo</gradient> <dark_grey>-</dark_grey> Undo last paste/delete</grey>\n" +
                "<grey><gradient:#ff77e9:#ff4fd8:#ff77e9>" + cmd + " redo</gradient> <dark_grey>-</dark_grey> Redo last undo</grey>";
        var adv = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getAudiences();
        if (adv != null) adv.sender(sender).sendMessage(mm.deserialize(msg));
        else sender.sendMessage(mm.deserialize(msg));
        if (adv != null) adv.sender(sender).sendMessage(mm.deserialize("<dark_grey>------------------------------</dark_grey>"));
        else sender.sendMessage(mm.deserialize("<dark_grey>------------------------------</dark_grey>"));
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
        String ext = s3.getExtension();
        String defaultGroupName = plugin.getConfig().getString("storage.defaultGroup", "default");
        
        // Add default group schematics (without prefix)
        java.util.List<String> def = s3.listSchm();
        cs.total += def.size();
        cs.groupCount = def.isEmpty() ? 0 : 1;
        for (String n : def) {
            String base = n.toLowerCase().endsWith(ext) ? n.substring(0, n.length() - ext.length()) : n;
            all.add(base);
        }
        
        // Add non-default group schematics (with group: prefix)
        for (String g : groups) {
            // Skip default group to avoid duplicates
            if (g.equalsIgnoreCase(defaultGroupName)) continue;
            
            java.util.List<String> li = s3.listSchm(g);
            if (!li.isEmpty()) cs.groupCount++;
            cs.total += li.size();
            for (String n : li) {
                String base = n.toLowerCase().endsWith(ext) ? n.substring(0, n.length() - ext.length()) : n;
                all.add(g + ":" + base);
            }
        }
        
        // Store consistent format in cache
        plugin.getSchematicCache().clear();
        plugin.getSchematicCache().addAll(all);
        return cs;
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) return true;
        }
        return false;
    }
}

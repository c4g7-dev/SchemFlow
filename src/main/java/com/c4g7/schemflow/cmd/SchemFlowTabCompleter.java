package com.c4g7.schemflow.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SchemFlowTabCompleter implements TabCompleter {
    private static final List<String> ROOT = Arrays.asList("help", "list", "fetch", "pos1", "pos2", "upload", "update", "paste", "delete", "restore", "undo", "redo", "cache", "reload", "provision", "groups", "group", "trash", "local");
    private static final List<String> FLAG_COMBOS = Arrays.asList("-e", "-a", "-b", "-ea", "-eb", "-ab", "-eab");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!"SchemFlow".equalsIgnoreCase(command.getName())) return Collections.emptyList();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : ROOT) {
                if (!s.startsWith(p)) continue;
                if (switch (s) {
                    case "help" -> sender.hasPermission("schemflow.help") || sender.hasPermission("schemflow.admin");
                    case "list" -> sender.hasPermission("schemflow.list") || sender.hasPermission("schemflow.admin");
                    case "fetch" -> sender.hasPermission("schemflow.fetch") || sender.hasPermission("schemflow.admin");
                    case "pos1" -> sender.hasPermission("schemflow.pos1") || sender.hasPermission("schemflow.admin");
                    case "pos2" -> sender.hasPermission("schemflow.pos2") || sender.hasPermission("schemflow.admin");
                    case "upload" -> sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin");
                    case "update" -> sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin");
                    case "paste" -> sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin");
                    case "delete" -> sender.hasPermission("schemflow.delete") || sender.hasPermission("schemflow.admin");
                    case "restore" -> sender.hasPermission("schemflow.restore") || sender.hasPermission("schemflow.admin");
                    case "undo" -> sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin");
                    case "redo" -> sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin");
                    case "cache" -> sender.hasPermission("schemflow.cache") || sender.hasPermission("schemflow.admin");
                    case "reload" -> sender.hasPermission("schemflow.reload") || sender.hasPermission("schemflow.admin");
                    case "provision" -> sender.hasPermission("schemflow.provision") || sender.hasPermission("schemflow.admin");
                    case "groups" -> sender.hasPermission("schemflow.groups") || sender.hasPermission("schemflow.admin");
                    case "group" -> sender.hasPermission("schemflow.group.create") || sender.hasPermission("schemflow.admin");
                    case "trash" -> sender.hasPermission("schemflow.list") || sender.hasPermission("schemflow.trash.clear") || sender.hasPermission("schemflow.admin");
                    case "local" -> sender.hasPermission("schemflow.local") || sender.hasPermission("schemflow.admin");
                    default -> false;
                }) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && ("paste".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]) || "fetch".equalsIgnoreCase(args[0]) || "update".equalsIgnoreCase(args[0]))) {
            String root = args[0].toLowerCase();
            if ((root.equals("paste") && !(sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("delete") && !(sender.hasPermission("schemflow.delete") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("restore") && !(sender.hasPermission("schemflow.restore") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("fetch") && !(sender.hasPermission("schemflow.fetch") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("update") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin")))) {
                return Collections.emptyList();
            }
            String p = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            try {
                var plugin = com.c4g7.schemflow.SchemFlowPlugin.getInstance();
                
                if (root.equals("restore")) {
                    // Restore still needs live S3 call as trash is not cached
                    var s3 = plugin.getS3Service();
                    String ext = s3.getExtension();
                    int extLen = ext.length();
                    for (String n : s3.listTrash()) {
                        String base = n.toLowerCase().endsWith(ext) ? n.substring(0, n.length() - extLen) : n;
                        if (base.toLowerCase().startsWith(p)) out.add(base);
                    }
                } else {
                    // Use cached schematic data for better performance
                    var cachedSchematics = plugin.getSchematicCache();
                    String ext = plugin.getS3Service().getExtension();
                    int extLen = ext.length();
                    
                    for (String cached : cachedSchematics) {
                        String base;
                        if (cached.contains(":")) {
                            // This is a group:name format - use as-is
                            base = cached;
                        } else {
                            // This is a plain schematic name - remove extension
                            base = cached.toLowerCase().endsWith(ext) ? cached.substring(0, cached.length() - extLen) : cached;
                        }
                        if (base.toLowerCase().startsWith(p)) out.add(base);
                    }
                    
                    // Add local schematics for paste command
                    if (root.equals("paste")) {
                        try {
                            String downloadDir = plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics");
                            java.nio.file.Path downloadPath = java.nio.file.Paths.get(downloadDir);
                            if (java.nio.file.Files.exists(downloadPath)) {
                                try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = java.nio.file.Files.newDirectoryStream(downloadPath, "*.{schem,schematic}")) {
                                    for (java.nio.file.Path file : stream) {
                                        String fileName = file.getFileName().toString();
                                        String base = fileName.toLowerCase().endsWith(".schem") ? fileName.substring(0, fileName.length() - 6) :
                                                    fileName.toLowerCase().endsWith(".schematic") ? fileName.substring(0, fileName.length() - 10) : fileName;
                                        String localPref = "local:" + base;
                                        if (localPref.toLowerCase().startsWith(p)) out.add(localPref);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            return out;
        }
    if (args.length == 3 && ("upload".equalsIgnoreCase(args[0]) || "paste".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]) || "update".equalsIgnoreCase(args[0]))) {
            String root = args[0].toLowerCase();
            if ((root.equals("upload") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("paste") && !(sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("restore") && !(sender.hasPermission("schemflow.restore") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("update") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin")))) {
                return Collections.emptyList();
            }
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            if (!root.equals("restore")) {
                for (String s : FLAG_COMBOS) if (s.startsWith(p)) out.add(s);
            }
            if ((root.equals("upload") || root.equals("restore")) && "-group".startsWith(p)) out.add("-group");
            return out;
        }
        if (args.length == 4 && ("upload".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]) || "update".equalsIgnoreCase(args[0]))) {
            String root = args[0].toLowerCase();
            if ((root.equals("upload") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("restore") && !(sender.hasPermission("schemflow.restore") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("update") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin")))) {
                return Collections.emptyList();
            }
            if (!"-group".equalsIgnoreCase(args[2])) return Collections.emptyList();
            String p = args[3].toLowerCase();
            List<String> out = new ArrayList<>();
            try {
                // Extract group names from cached schematics instead of making S3 call
                var plugin = com.c4g7.schemflow.SchemFlowPlugin.getInstance();
                var cachedSchematics = plugin.getSchematicCache();
                java.util.Set<String> groups = new java.util.HashSet<>();
                
                for (String cached : cachedSchematics) {
                    if (cached.contains(":")) {
                        String groupName = cached.substring(0, cached.indexOf(":"));
                        groups.add(groupName);
                    }
                }
                
                for (String g : groups) {
                    if (g.toLowerCase().startsWith(p)) out.add(g);
                }
            } catch (Exception ignored) {}
            return out;
        }
        if (args.length == 2 && "fetch".equalsIgnoreCase(args[0]) && args[1].startsWith("/")) {
            String rel = args[1].substring(1);
            String p = rel.toLowerCase();
            List<String> out = new ArrayList<>();
            try {
                var s3 = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getS3Service();
                for (String d : s3.listDirectories(rel)) {
                    String s = "/" + (rel.isEmpty() ? "" : rel + "/") + d + "/";
                    if (s.toLowerCase().startsWith("/" + p)) out.add(s);
                }
                for (String f : s3.listFiles(rel)) {
                    String s = "/" + (rel.isEmpty() ? "" : rel + "/") + f;
                    if (s.toLowerCase().startsWith("/" + p)) out.add(s);
                }
            } catch (Exception ignored) {}
            return out;
        }
        if (args.length == 2 && "group".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<>();
            String p = args[1].toLowerCase();
            if ("create".startsWith(p) && (sender.hasPermission("schemflow.group.create") || sender.hasPermission("schemflow.admin"))) out.add("create");
            if ("delete".startsWith(p) && (sender.hasPermission("schemflow.group.delete") || sender.hasPermission("schemflow.admin"))) out.add("delete");
            if ("rename".startsWith(p) && (sender.hasPermission("schemflow.group.rename") || sender.hasPermission("schemflow.admin"))) out.add("rename");
            return out;
        }
        if (args.length == 2 && "local".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("schemflow.local") && !sender.hasPermission("schemflow.admin")) return Collections.emptyList();
            if ("delete".startsWith(args[1].toLowerCase())) return java.util.Collections.singletonList("delete");
            return Collections.emptyList();
        }
        if (args.length == 3 && "local".equalsIgnoreCase(args[0]) && "delete".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("schemflow.local") && !sender.hasPermission("schemflow.admin")) return Collections.emptyList();
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            try {
                var plugin = com.c4g7.schemflow.SchemFlowPlugin.getInstance();
                String downloadDir = plugin.getConfig().getString("downloadDir", "plugins/SchemFlow/schematics");
                java.nio.file.Path downloadPath = java.nio.file.Paths.get(downloadDir);
                if (java.nio.file.Files.exists(downloadPath)) {
                    try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = java.nio.file.Files.newDirectoryStream(downloadPath, "*.{schem,schematic}")) {
                        for (java.nio.file.Path file : stream) {
                            String fileName = file.getFileName().toString();
                            String base = fileName.toLowerCase().endsWith(".schem") ? fileName.substring(0, fileName.length() - 6) :
                                        fileName.toLowerCase().endsWith(".schematic") ? fileName.substring(0, fileName.length() - 10) : fileName;
                            if (base.toLowerCase().startsWith(p)) out.add(base);
                        }
                    }
                }
            } catch (Exception ignored) {}
            return out;
        }
        if (args.length == 2 && "trash".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("schemflow.trash.clear") && !sender.hasPermission("schemflow.admin")) return Collections.emptyList();
            if ("clear".startsWith(args[1].toLowerCase())) return java.util.Collections.singletonList("clear");
            return Collections.emptyList();
        }
        if (args.length == 3 && "group".equalsIgnoreCase(args[0]) && ("delete".equalsIgnoreCase(args[1]) || "rename".equalsIgnoreCase(args[1]))) {
            List<String> out = new ArrayList<>();
            String p = args[2].toLowerCase();
            try {
                // Extract group names from cached schematics instead of making S3 call
                var plugin = com.c4g7.schemflow.SchemFlowPlugin.getInstance();
                var cachedSchematics = plugin.getSchematicCache();
                java.util.Set<String> groups = new java.util.HashSet<>();
                
                for (String cached : cachedSchematics) {
                    if (cached.contains(":")) {
                        String groupName = cached.substring(0, cached.indexOf(":"));
                        groups.add(groupName);
                    }
                }
                
                for (String g : groups) {
                    if (g.toLowerCase().startsWith(p)) out.add(g);
                }
            } catch (Exception ignored) {}
            return out;
        }
        if (args.length == 4 && "group".equalsIgnoreCase(args[0]) && "rename".equalsIgnoreCase(args[1])) {
            // new group name freeform; no suggestions
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}

package com.c4g7.schemflow.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SchemFlowTabCompleter implements TabCompleter {
    private static final List<String> ROOT = Arrays.asList("help", "list", "fetch", "pos1", "pos2", "upload", "paste", "delete", "restore", "undo", "redo", "cache", "reload", "provision", "groups", "group", "trash");
    private static final List<String> FLAG_COMBOS = Arrays.asList("-e", "-a", "-b", "-l", "-ea", "-eb", "-ab", "-el", "-al", "-bl", "-eab", "-eal", "-ebl", "-abl", "-eabl");

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
                    default -> false;
                }) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && ("paste".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]) || "fetch".equalsIgnoreCase(args[0]))) {
            String root = args[0].toLowerCase();
            if ((root.equals("paste") && !(sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("delete") && !(sender.hasPermission("schemflow.delete") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("restore") && !(sender.hasPermission("schemflow.restore") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("fetch") && !(sender.hasPermission("schemflow.fetch") || sender.hasPermission("schemflow.admin")))) {
                return Collections.emptyList();
            }
            String p = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            try {
                var plugin = com.c4g7.schemflow.SchemFlowPlugin.getInstance();
                var s3 = plugin.getS3Service();
                String ext = s3.getExtension();
                int extLen = ext.length();
                if (root.equals("restore")) {
                    for (String n : s3.listTrash()) {
                        String base = n.toLowerCase().endsWith(ext) ? n.substring(0, n.length() - extLen) : n;
                        if (base.toLowerCase().startsWith(p)) out.add(base);
                    }
                } else {
                    for (String n : s3.listSchm()) {
                        String base = n.toLowerCase().endsWith(ext) ? n.substring(0, n.length() - extLen) : n;
                        if (base.toLowerCase().startsWith(p)) out.add(base);
                    }
                    for (String g : s3.listGroups()) {
                        for (String n : s3.listSchm(g)) {
                            String base = n.toLowerCase().endsWith(ext) ? n.substring(0, n.length() - extLen) : n;
                            String pref = g + ":" + base;
                            if (pref.toLowerCase().startsWith(p)) out.add(pref);
                        }
                    }
                }
            } catch (Exception ignored) {}
            return out;
        }
    if (args.length == 3 && ("upload".equalsIgnoreCase(args[0]) || "paste".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]) )) {
            String root = args[0].toLowerCase();
            if ((root.equals("upload") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("paste") && !(sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("restore") && !(sender.hasPermission("schemflow.restore") || sender.hasPermission("schemflow.admin")))) {
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
        if (args.length == 4 && ("upload".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]))) {
            String root = args[0].toLowerCase();
            if ((root.equals("upload") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("restore") && !(sender.hasPermission("schemflow.restore") || sender.hasPermission("schemflow.admin")))) {
                return Collections.emptyList();
            }
            if (!"-group".equalsIgnoreCase(args[2])) return Collections.emptyList();
            String p = args[3].toLowerCase();
            List<String> out = new ArrayList<>();
            try {
                java.util.List<String> groups = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getS3Service().listGroups();
                for (String g : groups) if (g.toLowerCase().startsWith(p)) out.add(g);
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
        if (args.length == 2 && "trash".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("schemflow.trash.clear") && !sender.hasPermission("schemflow.admin")) return Collections.emptyList();
            if ("clear".startsWith(args[1].toLowerCase())) return java.util.Collections.singletonList("clear");
            return Collections.emptyList();
        }
        if (args.length == 3 && "group".equalsIgnoreCase(args[0]) && ("delete".equalsIgnoreCase(args[1]) || "rename".equalsIgnoreCase(args[1]))) {
            List<String> out = new ArrayList<>();
            String p = args[2].toLowerCase();
            try {
                java.util.List<String> groups = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getS3Service().listGroups();
                for (String g : groups) if (g.toLowerCase().startsWith(p)) out.add(g);
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

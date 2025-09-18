package com.c4g7.schemflow.cmd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SchemFlowTabCompleter implements TabCompleter {
    private static final List<String> ROOT = Arrays.asList("help", "list", "fetch", "pos1", "pos2", "upload", "paste", "delete", "cache", "reload", "provision", "groups", "group");
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
                    case "paste" -> sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin");
                    case "delete" -> sender.hasPermission("schemflow.delete") || sender.hasPermission("schemflow.admin");
                    case "cache" -> sender.hasPermission("schemflow.cache") || sender.hasPermission("schemflow.admin");
                    case "reload" -> sender.hasPermission("schemflow.reload") || sender.hasPermission("schemflow.admin");
                    case "provision" -> sender.hasPermission("schemflow.provision") || sender.hasPermission("schemflow.admin");
                    case "groups" -> sender.hasPermission("schemflow.groups") || sender.hasPermission("schemflow.admin");
                    case "group" -> sender.hasPermission("schemflow.group.create") || sender.hasPermission("schemflow.admin");
                    default -> false;
                }) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && ("paste".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]))) {
            String root = args[0].toLowerCase();
            if ((root.equals("paste") && !(sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("delete") && !(sender.hasPermission("schemflow.delete") || sender.hasPermission("schemflow.admin")))) {
                return Collections.emptyList();
            }
            String p = args[1].toLowerCase();
            java.util.List<String> cache = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getSchematicCache();
            List<String> out = new ArrayList<>();
            for (String name : cache) {
                String base = name.endsWith(".schm") ? name.substring(0, name.length() - 5) : name;
                if (base.toLowerCase().startsWith(p)) out.add(base);
            }
            return out;
        }
        if (args.length == 3 && ("upload".equalsIgnoreCase(args[0]) || "paste".equalsIgnoreCase(args[0]))) {
            String root = args[0].toLowerCase();
            if ((root.equals("upload") && !(sender.hasPermission("schemflow.upload") || sender.hasPermission("schemflow.admin"))) ||
                (root.equals("paste") && !(sender.hasPermission("schemflow.paste") || sender.hasPermission("schemflow.admin")))) {
                return Collections.emptyList();
            }
            String p = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : FLAG_COMBOS) if (s.startsWith(p)) out.add(s);
            if ("-group".startsWith(p)) out.add("-group");
            return out;
        }
        if (args.length == 4 && ("upload".equalsIgnoreCase(args[0]) || "paste".equalsIgnoreCase(args[0]) || "fetch".equalsIgnoreCase(args[0]))) {
            if (!"-group".equalsIgnoreCase(args[2])) return Collections.emptyList();
            String p = args[3].toLowerCase();
            List<String> out = new ArrayList<>();
            try {
                java.util.List<String> groups = com.c4g7.schemflow.SchemFlowPlugin.getInstance().getS3Service().listGroups();
                for (String g : groups) if (g.toLowerCase().startsWith(p)) out.add(g);
            } catch (Exception ignored) {}
            return out;
        }
        if (args.length == 2 && "group".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<>();
            if ("create".startsWith(args[1].toLowerCase())) out.add("create");
            return out;
        }
        return Collections.emptyList();
    }
}

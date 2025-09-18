package com.c4g7.schemflow.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.c4g7.schemflow.S3Service;
import com.c4g7.schemflow.SchemFlowPlugin;
import org.bukkit.event.Event;

import java.nio.file.Path;
@SuppressWarnings({"unchecked"})

public class SkriptHook {
    public static void register() {
        if (!Skript.isAcceptRegistrations()) return;
    Skript.registerExpression(ExprSchemList.class, String.class, ExpressionType.SIMPLE, "schemflow schematics");
    Skript.registerEffect(EffFetchSchematic.class, "fetch schemflow schematic %string% [to %-string%]");
    Skript.registerEffect(EffPasteSchematic.class, "paste schemflow schematic %string% at %location%");
    }

    @Name("Paste SchemFlow Schematic")
    @Description("Fetches a .schm by name and pastes the contained .schem at a location.")
    @Since("0.1.0")
    public static class EffPasteSchematic extends Effect {
        private Expression<String> name;
        private Expression<org.bukkit.Location> at;
        @Override public String toString(Event e, boolean debug) { return "paste schemflow schematic"; }
        @Override
        public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            name = (Expression<String>) exprs[0];
            at = (Expression<org.bukkit.Location>) exprs[1];
            return true;
        }
        @Override
        protected void execute(Event e) {
            String n = name.getSingle(e);
            org.bukkit.Location loc = at.getSingle(e);
            if (n == null || loc == null) return;
            S3Service s3 = SchemFlowPlugin.getInstance().getS3Service();
            String dest = SchemFlowPlugin.getInstance().getConfig().getString("downloadDir", "plugins/FlowStack/schematics");
            SchemFlowPlugin.getInstance().getServer().getScheduler().runTaskAsynchronously(SchemFlowPlugin.getInstance(), () -> {
                try {
                    String group = null; String name = n;
                    int c = n.indexOf(':'); if (c > 0) { group = n.substring(0, c); name = n.substring(c + 1); }
                    java.nio.file.Path schm = (group == null) ? s3.fetchSchm(name, dest) : s3.fetchSchm(name, group, dest);
                    if (com.c4g7.schemflow.util.ZipUtils.isZip(schm)) {
                        java.nio.file.Path outDir = schm.getParent().resolve(name);
                        com.c4g7.schemflow.util.SafeIO.ensureDir(outDir);
                        com.c4g7.schemflow.util.ZipUtils.unzip(schm, outDir);
                        try (java.util.stream.Stream<java.nio.file.Path> st = java.nio.file.Files.walk(outDir)) {
                            java.nio.file.Path schem = st.filter(pth -> pth.toString().toLowerCase().endsWith(".schem")).findFirst().orElse(null);
                            if (schem != null) {
                                SchemFlowPlugin.getInstance().getServer().getScheduler().runTask(SchemFlowPlugin.getInstance(), () -> {
                                    try {
                                        com.c4g7.schemflow.we.WorldEditUtils.paste(loc, schem, true);
                                    } catch (Exception ignored) {}
                                });
                            }
                        }
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    @Name("SchemFlow Schematic Names")
    @Description("Lists available .schm object names from the configured bucket.")
    @Since("0.1.0")
    public static class ExprSchemList extends SimpleExpression<String> {
        @Override public boolean isSingle() { return false; }
        @Override public Class<? extends String> getReturnType() { return String.class; }
        @Override public String toString(Event e, boolean debug) { return "schemflow schematics"; }
        @Override public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) { return true; }
        @Override protected String[] get(Event e) {
            try {
                S3Service s3 = SchemFlowPlugin.getInstance().getS3Service();
                java.util.List<String> groups = s3.listGroups();
                java.util.List<String> out = new java.util.ArrayList<>();
                out.addAll(s3.listSchm());
                for (String g : groups) {
                    for (String n : s3.listSchm(g)) {
                        String base = n.endsWith(".schm") ? n.substring(0, n.length() - 5) : n;
                        out.add(g + ":" + base);
                    }
                }
                return out.toArray(String[]::new);
            } catch (Exception ex) {
                SchemFlowPlugin.getInstance().getLogger().warning("ExprSchemList failed: " + ex.getMessage());
                return new String[0];
            }
        }
    }

    @Name("Fetch SchemFlow Schematic")
    @Description("Downloads a .schm file by name to an optional directory.")
    @Examples({"fetch schemflow schematic \"map1\" to \"plugins/FlowStack/schematics\""})
    @Since("0.1.0")
    public static class EffFetchSchematic extends Effect {
        private Expression<String> name;
        private Expression<String> dir;
        @Override public String toString(Event e, boolean debug) { return "fetch schemflow schematic"; }
        @Override
        public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            name = (Expression<String>) exprs[0];
            dir = exprs.length > 1 ? (Expression<String>) exprs[1] : null;
            return true;
        }
        @Override
        protected void execute(Event e) {
            String n = name.getSingle(e);
            String d = dir == null ? SchemFlowPlugin.getInstance().getConfig().getString("downloadDir", "plugins/FlowStack/schematics") : dir.getSingle(e);
            if (n == null) return;
            S3Service s3 = SchemFlowPlugin.getInstance().getS3Service();
            SchemFlowPlugin.getInstance().getServer().getScheduler().runTaskAsynchronously(SchemFlowPlugin.getInstance(), () -> {
                try {
                    String group = null; String name = n;
                    int c = n.indexOf(':'); if (c > 0) { group = n.substring(0, c); name = n.substring(c + 1); }
                    Path p = (group == null) ? s3.fetchSchm(name, d) : s3.fetchSchm(name, group, d);
                    SchemFlowPlugin.getInstance().getLogger().info("Downloaded schematic: " + p);
                } catch (Exception ex) {
                    SchemFlowPlugin.getInstance().getLogger().severe("Fetch failed: " + ex.getMessage());
                }
            });
        }
    }
}

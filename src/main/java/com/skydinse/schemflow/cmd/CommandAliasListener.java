package com.skydinse.schemflow.cmd;

import com.skydinse.schemflow.SchemFlowPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * CommandAliasListener — supports hidden alias "/schem" mapped to "/SchemFlow".
 * Author: c4g7
 */
public class CommandAliasListener implements Listener {
    private final SchemFlowPlugin plugin;
    private static final String PRIMARY = "SchemFlow";
    private static final String HIDDEN = "schem";

    public CommandAliasListener(SchemFlowPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg == null || msg.length() < 2 || msg.charAt(0) != '/') return;
        String body = msg.substring(1);
        // Map "/schem ..." -> "/SchemFlow ..." (case-insensitive)
        if (body.regionMatches(true, 0, HIDDEN, 0, HIDDEN.length()) &&
            (body.length() == HIDDEN.length() || Character.isWhitespace(body.charAt(HIDDEN.length())))) {
            String rest = body.length() > HIDDEN.length() ? body.substring(HIDDEN.length()) : "";
            event.setMessage("/" + PRIMARY + rest);
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String cmd = event.getCommand();
        if (cmd == null) return;
        // Map "schem ..." -> "SchemFlow ..." (case-insensitive)
        if (cmd.regionMatches(true, 0, HIDDEN, 0, HIDDEN.length()) &&
            (cmd.length() == HIDDEN.length() || Character.isWhitespace(cmd.charAt(HIDDEN.length())))) {
            String rest = cmd.length() > HIDDEN.length() ? cmd.substring(HIDDEN.length()) : "";
            event.setCommand(PRIMARY + rest);
        }
    }
}
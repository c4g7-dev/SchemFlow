package com.c4g7.schemflow;

import com.c4g7.schemflow.cmd.SchemFlowCommand;
import com.c4g7.schemflow.select.SelectionManager;
import com.c4g7.schemflow.world.WorldProvisioner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class SchemFlowPlugin extends JavaPlugin {
    private static SchemFlowPlugin instance;
    private S3Service s3Service;
    private SelectionManager selection;
    private WorldProvisioner provisioner;
    private net.kyori.adventure.platform.bukkit.BukkitAudiences audiences;
    private net.kyori.adventure.text.minimessage.MiniMessage miniMessage;
    private java.util.concurrent.CopyOnWriteArrayList<String> schemCache = new java.util.concurrent.CopyOnWriteArrayList<>();
    private int cacheTaskId = -1;

    public static SchemFlowPlugin getInstance() { return instance; }
    public S3Service getS3Service() { return s3Service; }
    public SelectionManager getSelection() { return selection; }
    public WorldProvisioner getProvisioner() { return provisioner; }
    public net.kyori.adventure.platform.bukkit.BukkitAudiences getAudiences() { return audiences; }
    public net.kyori.adventure.text.minimessage.MiniMessage getMiniMessage() { return miniMessage; }
    public java.util.List<String> getSchematicCache() { return schemCache; }
    public void refreshSchematicCacheAsync() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                java.util.List<String> list = s3Service.listSchm();
                schemCache.clear();
                for (String n : list) schemCache.add(n);
            } catch (Exception ignored) {}
        });
    }

    public synchronized boolean reloadSchemFlowConfig() {
        try {
            sanitizeConfigTabs();
            sanitizeConfigQuotes();
            reloadConfig();
            FileConfiguration cfg = getConfig();
            S3Service newService = new S3Service(
                    cfg.getString("endpoint"),
                    cfg.getString("accessKey"),
                    cfg.getString("secretKey"),
                    cfg.getString("bucket"),
                    cfg.getBoolean("secure", true),
                    cfg.getString("extension", "schm")
            );
            if (this.s3Service != null) {
                try { this.s3Service.close(); } catch (Exception ignore) {}
            }
            this.s3Service = newService;

            if (cacheTaskId != -1) {
                getServer().getScheduler().cancelTask(cacheTaskId);
                cacheTaskId = -1;
            }
            int refreshSec = cfg.getInt("cacheRefreshSeconds", 60);
            if (refreshSec > 0) {
                long period = refreshSec * 20L;
                cacheTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, this::refreshSchematicCacheAsync, period, period).getTaskId();
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to reload config/services", e);
            return false;
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            boolean hasWE = getServer().getPluginManager().getPlugin("WorldEdit") != null;
            boolean hasFAWE = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null || getServer().getPluginManager().getPlugin("FAWE") != null;
            if (!hasWE && !hasFAWE) {
                getLogger().severe("WorldEdit or FastAsyncWorldEdit is required. Please install one of them.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } catch (Throwable ignored) {}
        saveDefaultConfig();
        sanitizeConfigTabs();
        sanitizeConfigQuotes();
        try { this.reloadConfig(); } catch (Throwable ignored) {}
        try {
            FileConfiguration c = getConfig();
            boolean updated = false;
            if (!c.contains("autoListOnStart")) { c.set("autoListOnStart", true); updated = true; }
            if (!c.contains("fetchOnStart")) { c.set("fetchOnStart", ""); updated = true; }
            if (!c.contains("cacheRefreshSeconds")) { c.set("cacheRefreshSeconds", 60); updated = true; }
            if (!c.contains("provisionOnStartup")) { c.set("provisionOnStartup", false); updated = true; }
            if (!c.contains("storage.rootDir")) { c.set("storage.rootDir", "FlowSuite/SchemFlow"); updated = true; }
            if (!c.contains("storage.defaultGroup")) { c.set("storage.defaultGroup", "default"); updated = true; }
            if (updated) saveConfig();
        } catch (Throwable ignored) {}
        this.audiences = net.kyori.adventure.platform.bukkit.BukkitAudiences.create(this);
        this.miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        try {
            // Initialize bStats metrics (plugin id 27301)
            new org.bstats.bukkit.Metrics(this, 27301);
        } catch (Throwable ignored) {}
        selection = new SelectionManager();
        FileConfiguration cfg = getConfig();
        try {
            s3Service = new S3Service(
                cfg.getString("endpoint"),
                cfg.getString("accessKey"),
                cfg.getString("secretKey"),
                cfg.getString("bucket"),
                cfg.getBoolean("secure", true),
                cfg.getString("extension", "schm"),
                cfg.getString("storage.rootDir", "FlowSuite/SchemFlow"),
                cfg.getString("storage.defaultGroup", "default")
            );
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to init MinIO S3 service", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        provisioner = new WorldProvisioner(this);
        int refreshSec = getConfig().getInt("cacheRefreshSeconds", 60);
        if (refreshSec > 0) {
            long period = refreshSec * 20L;
            cacheTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, this::refreshSchematicCacheAsync, period, period).getTaskId();
        }

        if (getCommand("SchemFlow") != null) {
            getCommand("SchemFlow").setExecutor(new SchemFlowCommand(this));
            getCommand("SchemFlow").setTabCompleter(new com.c4g7.schemflow.cmd.SchemFlowTabCompleter());
        }
        getServer().getPluginManager().registerEvents(new com.c4g7.schemflow.cmd.CommandAliasListener(this), this);

        try {
            com.c4g7.schemflow.skript.SkriptHook.register();
        } catch (Throwable t) {
            getLogger().info("Skript not present or registration failed; continuing without Skript hooks.");
        }

        if (cfg.getBoolean("autoListOnStart", true)) {
            try {
                java.util.List<String> list = s3Service.listSchm();
                schemCache.clear();
                schemCache.addAll(list);
                getLogger().info("[SchemFlow] Found " + list.size() + " .schm objects in bucket");
            } catch (Exception ex) {
                getLogger().log(Level.WARNING, "List on start failed", ex);
            }
        }

        String autoFetch = cfg.getString("fetchOnStart");
        if (autoFetch != null && !autoFetch.isBlank()) {
            final String name = autoFetch;
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    s3Service.fetchSchm(name, cfg.getString("downloadDir", "plugins/Skript/schematics"));
                } catch (Exception ex) {
                    getLogger().log(Level.SEVERE, "Fetch on start failed for " + name, ex);
                }
            });
        }

        if (cfg.getBoolean("provisionOnStartup", false)) {
            getServer().getScheduler().runTask(this, () -> provisioner.provisionAllFromConfig());
        }
    }

    @Override
    public void onDisable() {
        if (s3Service != null) s3Service.close();
        if (audiences != null) audiences.close();
        if (cacheTaskId != -1) getServer().getScheduler().cancelTask(cacheTaskId);
    }

    private void sanitizeConfigTabs() {
        try {
            java.io.File file = new java.io.File(getDataFolder(), "config.yml");
            if (!file.exists()) return;
            String content = java.nio.file.Files.readString(file.toPath());
            if (content.indexOf('\t') >= 0) {
                String fixed = content.replace("\t", "  ");
                java.nio.file.Files.writeString(file.toPath(), fixed, java.nio.charset.StandardCharsets.UTF_8);
                getLogger().info("Sanitized TABs in config.yml (replaced with spaces)");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to sanitize config.yml: " + e.getMessage());
        }
    }

    private void sanitizeConfigQuotes() {
        try {
            java.io.File file = new java.io.File(getDataFolder(), "config.yml");
            if (!file.exists()) return;
            java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
            boolean changed = false;
            java.util.regex.Pattern keyPattern = java.util.regex.Pattern.compile("^\\s*(endpoint|accessKey|secretKey|bucket|extension|downloadDir|fetchOnStart):\\s*\"[^\"]*$");
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;
                if (keyPattern.matcher(line).find()) {
                    lines.set(i, line + "\"");
                    changed = true;
                    continue;
                }
                long quotes = line.chars().filter(ch -> ch == '"').count();
                if (quotes % 2 == 1 && line.contains(":")) {
                    lines.set(i, line + "\"");
                    changed = true;
                }
            }
            if (changed) {
                java.nio.file.Files.write(file.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
                getLogger().info("Sanitized quotes in config.yml (added missing closing quote)");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to sanitize quotes in config.yml: " + e.getMessage());
        }
    }
}

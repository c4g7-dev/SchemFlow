package com.c4g7.schemflow.select;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public void setPos1(UUID uuid, Location loc) { pos1.put(uuid, loc.clone()); }
    public void setPos2(UUID uuid, Location loc) { pos2.put(uuid, loc.clone()); }
    public Location getPos1(UUID uuid) { return pos1.get(uuid); }
    public Location getPos2(UUID uuid) { return pos2.get(uuid); }
    public boolean hasBoth(UUID uuid) { return pos1.containsKey(uuid) && pos2.containsKey(uuid) && sameWorld(uuid); }
    private boolean sameWorld(UUID uuid) {
        Location a = pos1.get(uuid); Location b = pos2.get(uuid);
        return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }
}

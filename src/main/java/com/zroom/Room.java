package com.zroom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Room {
    private final String name;
    private Cuboid roomCuboid;
    private Cuboid doorCuboid;
    private final UUID[] slots = new UUID[2]; // MAX_PLAYERS = 2
    private final Location[] spawns = new Location[2];
    private final Map<Location, BlockData> originalDoorBlocks = new HashMap<>();

    private boolean inLootTime = false;
    private BukkitTask lootTimerTask = null;

    public Room(String name) { this.name = name; }

    public String getName() { return name; }
    public Cuboid getRoomCuboid() { return roomCuboid; }
    public void setRoomCuboid(Cuboid roomCuboid) { this.roomCuboid = roomCuboid; }
    public Cuboid getDoorCuboid() { return doorCuboid; }

    public void setDoorCuboid(Cuboid doorCuboid) {
        cancelLootTimer();
        boolean wasClosed = !originalDoorBlocks.isEmpty();
        openDoors();
        this.doorCuboid = doorCuboid;
        if (wasClosed) closeDoors();
    }

    public Location getSpawn(int slot) {
        return (slot >= 0 && slot < spawns.length) ? spawns[slot] : null;
    }

    public void setSpawn(int slot, Location loc) {
        if (slot >= 0 && slot < spawns.length) spawns[slot] = loc;
    }

    public boolean hasPlayer(UUID uuid) {
        for (UUID id : slots) if (uuid.equals(id)) return true;
        return false;
    }

    public boolean isFull() {
        for (UUID id : slots) if (id == null) return false;
        return true;
    }

    public List<UUID> getPlayers() {
        List<UUID> list = new ArrayList<>();
        for (UUID id : slots) if (id != null) list.add(id);
        return list;
    }

    public int addPlayer(UUID uuid) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = uuid;
                return i;
            }
        }
        return -1;
    }

    public void removePlayer(UUID uuid) {
        for (int i = 0; i < slots.length; i++) {
            if (uuid.equals(slots[i])) slots[i] = null;
        }
    }

    public void clearAllSlots() {
        Arrays.fill(slots, null);
    }

    public boolean isInLootTime() { return inLootTime; }

    public void startLootTimer(JavaPlugin plugin, int seconds, Runnable onEnd) {
        if (inLootTime) return;
        inLootTime = true;
        lootTimerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            inLootTime = false;
            lootTimerTask = null;
            if (onEnd != null) onEnd.run();
        }, 20L * seconds);
    }

    public void cancelLootTimer() {
        if (lootTimerTask != null) {
            lootTimerTask.cancel();
            lootTimerTask = null;
        }
        inLootTime = false;
    }

    public void closeDoors() {
        if (doorCuboid == null || !originalDoorBlocks.isEmpty()) return;
        for (Block block : doorCuboid.getAllBlocks()) {
            originalDoorBlocks.put(block.getLocation(), block.getBlockData());
            block.setType(Material.GLASS, false);
        }
    }

    public void openDoors() {
        if (originalDoorBlocks.isEmpty()) return;
        for (Map.Entry<Location, BlockData> entry : originalDoorBlocks.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) continue;
            loc.getBlock().setBlockData(entry.getValue(), false);
        }
        originalDoorBlocks.clear();
    }
}

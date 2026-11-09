package com.zroom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public class Cuboid {
    private final String worldName;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public Cuboid(Location l1, Location l2) {
        this.worldName = l1.getWorld().getName();
        this.minX = Math.min(l1.getBlockX(), l2.getBlockX());
        this.minY = Math.min(l1.getBlockY(), l2.getBlockY());
        this.minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        this.maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        this.maxY = Math.max(l1.getBlockY(), l2.getBlockY());
        this.maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());
    }

    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public long getVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public List<Block> getAllBlocks() {
        List<Block> list = new ArrayList<>();
        World w = Bukkit.getWorld(worldName);
        if (w == null) return list;
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    list.add(w.getBlockAt(x, y, z));
        return list;
    }
}

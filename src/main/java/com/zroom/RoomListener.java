package com.zroom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.UUID;

public class RoomListener implements Listener {

    private final ZRoom plugin;
    private static final String PERMISSION = "zroom.admin";

    public RoomListener(ZRoom plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION) || !ZRoom.isWand(event.getItem())) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        event.setCancelled(true);
        Location loc = clicked.getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            plugin.getPos1Map().put(player.getUniqueId(), loc);
            player.sendMessage(ChatColor.AQUA + "1. Köşe seçildi: " + ZRoom.formatLoc(loc));
        } else {
            plugin.getPos2Map().put(player.getUniqueId(), loc);
            player.sendMessage(ChatColor.AQUA + "2. Köşe seçildi: " + ZRoom.formatLoc(loc));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(PERMISSION) && ZRoom.isWand(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission(PERMISSION)) return;

        UUID uuid = player.getUniqueId();
        for (Room room : plugin.getRooms().values()) {
            if (room.hasPlayer(uuid)) {
                if (!room.isInLootTime()) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Odada komut kullanamazsınız!");
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        plugin.getForceSpawnOnRespawn().remove(victimId);

        Player killer = victim.getKiller();
        if (killer == null) return;

        UUID killerId = killer.getUniqueId();

        Room target = null;
        for (Room room : plugin.getRooms().values()) {
            if (room.hasPlayer(victimId) && room.hasPlayer(killerId)) {
                target = room;
                break;
            }
        }
        if (target == null) return;

        target.removePlayer(victimId);
        plugin.getForceSpawnOnRespawn().add(victimId);

        Room finalTarget = target;
        target.startLootTimer(plugin, ZRoom.LOOT_TIME_SECONDS, () -> handleLootTimerEnd(finalTarget));

        killer.sendMessage(ChatColor.GREEN + "Rakibini yendin! " + ZRoom.LOOT_TIME_SECONDS
                + " saniye ganimet süren var. Komutlar artık serbest.");
        victim.sendMessage(ChatColor.RED + "Odada öldün! Spawna gönderiliyorsun.");
    }

    private void handleLootTimerEnd(Room room) {
        Location spawn = ZRoom.getServerSpawn();
        List<UUID> players = room.getPlayers();
        room.clearAllSlots();

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(ChatColor.RED + "Ganimet süresi doldu! Spawna gönderildiniz.");
                if (spawn != null) plugin.safeTeleport(p, spawn);
            }
        }
        room.openDoors();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        checkPlayer(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.isInternalTeleport()) return;
        scheduleCheck(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getForceSpawnOnRespawn().remove(event.getPlayer().getUniqueId())) {
            Location spawn = ZRoom.getServerSpawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        }
        scheduleCheck(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleCheck(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getPos1Map().remove(uuid);
        plugin.getPos2Map().remove(uuid);

        for (Room room : plugin.getRooms().values()) {
            if (room.hasPlayer(uuid)) {
                leaveRoom(room, uuid);
            }
        }
    }

    private void scheduleCheck(UUID uuid) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) return;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                checkPlayer(player, player.getLocation());
            }
        });
    }

    private void checkPlayer(Player player, Location loc) {
        UUID uuid = player.getUniqueId();

        for (Room room : plugin.getRooms().values()) {
            Cuboid area = room.getRoomCuboid();
            boolean inside = area != null && area.contains(loc);
            boolean registered = room.hasPlayer(uuid);

            if (inside && !registered) {
                if (room.isInLootTime()) {
                    Location spawn = ZRoom.getServerSpawn();
                    if (spawn != null) {
                        player.sendMessage(ChatColor.RED + "Ganimet süresi boyunca odaya giremezsiniz!");
                        plugin.safeTeleport(player, spawn);
                    }
                    continue;
                }
                enterRoom(room, player);
            } else if (!inside && registered) {
                leaveRoom(room, uuid);
            }
        }
    }

    private void enterRoom(Room room, Player player) {
        if (room.isFull()) {
            plugin.sendToServerSpawn(player, "Oda dolu (Maksimum " + ZRoom.MAX_PLAYERS + " kişi)!");
            return;
        }

        for (UUID existingUUID : room.getPlayers()) {
            Player existing = Bukkit.getPlayer(existingUUID);
            if (existing != null && ZRoom.areInSameClan(player, existing)) {
                plugin.sendToServerSpawn(player, "Aynı klandan iki oyuncu aynı odaya giremez!");
                return;
            }
        }

        int slot = room.addPlayer(player.getUniqueId());
        Location spawn = room.getSpawn(slot);
        if (spawn != null) {
            plugin.safeTeleport(player, spawn);
        }

        if (room.isFull()) {
            room.closeDoors();
        }
    }

    private void leaveRoom(Room room, UUID uuid) {
        room.removePlayer(uuid);

        if (!room.isFull() && !room.isInLootTime()) {
            room.openDoors();
        }
    }
}

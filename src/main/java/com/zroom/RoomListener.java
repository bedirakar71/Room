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
import org.bukkit.inventory.ItemStack;

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
        UUID uuid = player.getUniqueId();
        for (Room room : plugin.getRooms().values()) {
            if (room.hasPlayer(uuid)) {
                if (room.isBattleStarted() && !room.isInLootTime()) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Savaş başladığı için odada komut kullanamazsınız!");
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

        Room target = null;
        for (Room room : plugin.getRooms().values()) {
            if (room.hasPlayer(victimId)) {
                target = room;
                break;
            }
        }
        if (target == null || !target.isBattleStarted()) return;

        target.removePlayer(victimId);
        plugin.getForceSpawnOnRespawn().add(victimId);

        Player killer = victim.getKiller();
        if (killer != null) {
            killer.sendMessage(ChatColor.GREEN + "Rakibini yendin! " + ZRoom.LOOT_TIME_SECONDS
                    + " saniye ganimet süren var. Komutlar artık serbest.");
        }
        victim.sendMessage(ChatColor.RED + "Odada öldün! Spawna gönderiliyorsun.");

        // Ölen oyuncu anında spawna atılır
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline() && victim.isDead()) {
                victim.spigot().respawn();
            }
            victim.performCommand("spawn");
        });

        // Öldüren için 15s ganimet timer başlatılır
        Room finalTarget = target;
        target.startLootTimer(plugin, ZRoom.LOOT_TIME_SECONDS, () -> handleLootTimerEnd(finalTarget));
    }

    private void handleLootTimerEnd(Room room) {
        List<UUID> players = room.getPlayers();
        room.clearAllSlots();

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(ChatColor.RED + "Ganimet süresi doldu! Spawna gönderildiniz.");
                p.performCommand("spawn");
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
            event.getPlayer().performCommand("spawn");
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
                handlePlayerEscapeOrQuit(room, uuid);
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
                    player.sendMessage(ChatColor.RED + "Ganimet süresi boyunca odaya giremezsiniz!");
                    player.performCommand("spawn");
                    continue;
                }
                enterRoom(room, player);
            } else if (!inside && registered) {
                if (room.isInLootTime()) {
                    continue;
                }
                if (room.isBattleStarted()) {
                    handlePlayerEscapeOrQuit(room, uuid);
                } else {
                    room.removePlayer(uuid);
                    player.sendMessage(ChatColor.YELLOW + "Odadan ayrıldınız.");
                }
            }
        }
    }

    private void handlePlayerEscapeOrQuit(Room room, UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        room.removePlayer(uuid);

        if (player != null) {
            Location dropLoc = player.getLocation();
            if (dropLoc.getWorld() != null) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        dropLoc.getWorld().dropItemNaturally(dropLoc, item);
                    }
                }
                player.getInventory().clear();
            }
            player.sendMessage(ChatColor.RED + "Odadan çıktığınız için yenik sayıldınız!");
            plugin.getForceSpawnOnRespawn().add(uuid);
            plugin.sendToServerSpawn(player, "Odadan kaçtınız!");
        }

        Room finalRoom = room;
        room.startLootTimer(plugin, ZRoom.LOOT_TIME_SECONDS, () -> handleLootTimerEnd(finalRoom));

        for (UUID remainingUuid : room.getPlayers()) {
            Player rem = Bukkit.getPlayer(remainingUuid);
            if (rem != null) {
                rem.sendMessage(ChatColor.GREEN + "Rakibiniz odadan ayrıldı! " + ZRoom.LOOT_TIME_SECONDS
                        + " saniye ganimet süreniz var.");
            }
        }
    }

    private void enterRoom(Room room, Player player) {
        // 3. Oyuncu veya fazlası girdiğinde anında spawna gönder
        if (room.isFull()) {
            plugin.sendToServerSpawn(player, "Oda dolu (Maksimum " + ZRoom.MAX_PLAYERS + " kişi)!");
            return;
        }

        // Odadaki mevcut oyuncu ile klan kontrolü yap
        for (UUID existingUUID : room.getPlayers()) {
            Player existing = Bukkit.getPlayer(existingUUID);
            if (existing != null && plugin.areInSameClan(player, existing)) {
                // Aynı klandan yeni girmeye çalışan oyuncuyu doğrudan spawna gönder
                plugin.sendToServerSpawn(player, "Aynı klandan iki oyuncu aynı odaya giremez!");
                return;
            }
        }

        room.addPlayer(player.getUniqueId());

        if (room.getPlayerCount() == 1) {
            player.sendMessage(ChatColor.YELLOW + "Diğer oyuncu bekleniyor...");
        } else if (room.isFull()) {
            room.setBattleStarted(true);

            List<UUID> players = room.getPlayers();
            for (int i = 0; i < players.size(); i++) {
                Player p = Bukkit.getPlayer(players.get(i));
                if (p != null) {
                    Location spawn = room.getSpawn(i);
                    if (spawn != null) {
                        plugin.safeTeleport(p, spawn);
                    }
                    p.sendTitle(ChatColor.RED + "Savaş Başladı!", "", 10, 40, 10);
                    p.sendMessage(ChatColor.RED + "Savaş Başladı!");
                }
            }
            room.closeDoors();
        }
    }
}

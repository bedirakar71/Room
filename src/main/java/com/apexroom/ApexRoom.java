package com.apexroom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ApexRoom extends JavaPlugin implements Listener, CommandExecutor {

    private static ApexRoom instance;
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("room") != null) {
            getCommand("room").setExecutor(this);
        }
        getLogger().info("ApexRoom 1.21.4 surumuyle basariyla baslatildi!");
    }

    @Override
    public void onDisable() {
        for (Room room : rooms.values()) {
            room.openDoor();
        }
    }

    public static ApexRoom getInstance() {
        return instance;
    }

    // --- KOMUT SİSTEMİ ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Bu komut sadece oyuncular tarafindan kullanilabilir.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Kullanim: /room <pos1|pos2|create|setdoor> <oda_ismi>");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String roomName = args[1];

        switch (subCommand) {
            case "pos1" -> {
                pos1Map.put(player.getUniqueId(), player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Room için 1. koordinat (Pos 1) kaydedildi.");
            }
            case "pos2" -> {
                pos2Map.put(player.getUniqueId(), player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Room için 2. koordinat (Pos 2) kaydedildi.");
            }
            case "create" -> {
                Location p1 = pos1Map.get(player.getUniqueId());
                Location p2 = pos2Map.get(player.getUniqueId());
                if (p1 == null || p2 == null) {
                    player.sendMessage(ChatColor.RED + "Önce /room pos1 ve /room pos2 komutlarını kullanmalısın!");
                    return true;
                }
                rooms.put(roomName.toLowerCase(), new Room(roomName, p1, p2));
                player.sendMessage(ChatColor.GREEN + "'" + roomName + "' isimli oda başarıyla oluşturuldu!");
            }
            case "setdoor" -> {
                Room room = rooms.get(roomName.toLowerCase());
                if (room == null) {
                    player.sendMessage(ChatColor.RED + "Böyle bir oda bulunamadı!");
                    return true;
                }
                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock == null) {
                    player.sendMessage(ChatColor.RED + "Lütfen kapı yapılacak bloğa bakarak komutu girin!");
                    return true;
                }
                room.getDoorBlocks().add(targetBlock.getLocation());
                player.sendMessage(ChatColor.GREEN + roomName + " odasına yeni kapı bloğu eklendi!");
            }
            default -> player.sendMessage(ChatColor.RED + "Geçersiz alt komut! Kullanım: /room <pos1|pos2|create|setdoor> <oda_ismi>");
        }
        return true;
    }

    // --- EVENTLER ---

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        Room currentRoom = getRoomByLocation(to);
        Room previousRoom = getRoomByLocation(from);

        if (previousRoom != null && previousRoom != currentRoom) {
            previousRoom.getInsidePlayers().remove(player.getUniqueId());
        }

        if (currentRoom != null && previousRoom != currentRoom) {
            Set<UUID> inside = currentRoom.getInsidePlayers();

            // 1. Klan Kontrolü (ApexClan)
            for (UUID inId : inside) {
                Player inPlayer = Bukkit.getPlayer(inId);
                if (inPlayer != null && areInSameClan(player, inPlayer)) {
                    player.teleport(player.getWorld().getSpawnLocation());
                    player.sendMessage(ChatColor.RED + "Aynı klandan bir oyuncu odada olduğu için giremezsiniz! Spawna gönderildiniz.");
                    return;
                }
            }

            // 2. Kapasite Kontrolü (Maksimum 2 kişi)
            if (inside.size() >= 2) {
                player.teleport(player.getWorld().getSpawnLocation());
                player.sendMessage(ChatColor.RED + "Oda dolu olduğu için Spawna gönderildiniz.");
                return;
            }

            inside.add(player.getUniqueId());

            // Tam 2 kişi girdiğinde kapıyı kapat ve dövüşü başlat
            if (inside.size() == 2) {
                currentRoom.closeDoor();
                currentRoom.setLockedForFight(true);
                for (UUID pId : inside) {
                    Player p = Bukkit.getPlayer(pId);
                    if (p != null) p.sendMessage(ChatColor.GOLD + "İkinci oyuncu da girdi! Kapılar kapandı, kapışma başladı!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Room room = getRoomByLocation(victim.getLocation());

        if (room != null && room.isLockedForFight()) {
            List<ItemStack> drops = new ArrayList<>(event.getDrops());
            event.getDrops().clear();

            Inventory lootMenu = Bukkit.createInventory(new DeathMenuHolder(), 54, ChatColor.DARK_RED + victim.getName() + " Eşyaları");
            for (ItemStack item : drops) {
                if (item != null && item.getType() != Material.AIR) {
                    lootMenu.addItem(item);
                }
            }

            room.getDeathLootGuis().put(victim.getUniqueId(), lootMenu);
            room.getInsidePlayers().remove(victim.getUniqueId());

            victim.sendMessage(ChatColor.YELLOW + "Öldünüz! 15 saniye sonra kapılar otomatik açılacaktır.");
            room.start15SecondLootTimer();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Room room = getRoomByLocation(player.getLocation());

        if (room != null && room.isLockedForFight() && !room.isFightEnded()) {
            Inventory lootMenu = Bukkit.createInventory(new DeathMenuHolder(), 54, ChatColor.DARK_RED + player.getName() + " (Çıktı) Eşyaları");
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    lootMenu.addItem(item);
                }
            }
            player.getInventory().clear();

            room.getDeathLootGuis().put(player.getUniqueId(), lootMenu);
            room.getInsidePlayers().remove(player.getUniqueId());

            for (UUID pId : room.getInsidePlayers()) {
                Player survivor = Bukkit.getPlayer(pId);
                if (survivor != null) {
                    survivor.sendMessage(ChatColor.RED + player.getName() + " savaş sırasında oyundan çıktığı için öldü sayıldı! 15 saniye loot süresi başladı.");
                }
            }

            room.start15SecondLootTimer();
        }
    }

    @EventHandler
    public void onLootMenuClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Room room = getRoomByLocation(player.getLocation());

        if (room != null && room.isFightEnded()) {
            if (!room.getDeathLootGuis().isEmpty() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Inventory gui = room.getDeathLootGuis().values().iterator().next();
                player.openInventory(gui);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandProcess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Room room = getRoomByLocation(player.getLocation());

        if (room != null && room.isLockedForFight() && !room.isFightEnded()) {
            if (!player.hasPermission("apexroom.admin")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Düello devam ederken oda içinde komut kullanamazsınız!");
            }
        }
    }

    // --- YARDIMCI METOTLAR ---
    private Room getRoomByLocation(Location loc) {
        for (Room room : rooms.values()) {
            if (room.isInside(loc)) return room;
        }
        return null;
    }

    private boolean areInSameClan(Player p1, Player p2) {
        Plugin apexClanPlugin = Bukkit.getPluginManager().getPlugin("ApexClan");
        if (apexClanPlugin == null || !apexClanPlugin.isEnabled()) return false;
        try {
            Object clan1 = apexClanPlugin.getClass().getMethod("getClan", Player.class).invoke(apexClanPlugin, p1);
            Object clan2 = apexClanPlugin.getClass().getMethod("getClan", Player.class).invoke(apexClanPlugin, p2);
            if (clan1 != null && clan2 != null) return clan1.equals(clan2);
        } catch (Exception ignored) {}
        return false;
    }

    // --- İÇ SINIFLAR ---
    public static class Room {
        private final String name;
        private final Location corner1;
        private final Location corner2;
        private final List<Location> doorBlocks = new ArrayList<>();
        private final Set<UUID> insidePlayers = new HashSet<>();
        private final Map<UUID, Inventory> deathLootGuis = new HashMap<>();
        private boolean doorClosed = false;
        private boolean lockedForFight = false;
        private boolean fightEnded = false;

        public Room(String name, Location c1, Location c2) {
            this.name = name;
            this.corner1 = c1;
            this.corner2 = c2;
        }

        public boolean isInside(Location loc) {
            if (corner1 == null || corner2 == null || loc.getWorld() == null) return false;
            if (!loc.getWorld().equals(corner1.getWorld())) return false;
            double minX = Math.min(corner1.getX(), corner2.getX());
            double maxX = Math.max(corner1.getX(), corner2.getX());
            double minY = Math.min(corner1.getY(), corner2.getY());
            double maxY = Math.max(corner1.getY(), corner2.getY());
            double minZ = Math.min(corner1.getZ(), corner2.getZ());
            double maxZ = Math.max(corner1.getZ(), corner2.getZ());
            return loc.getX() >= minX && loc.getX() <= maxX &&
                   loc.getY() >= minY && loc.getY() <= maxY &&
                   loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }

        public void closeDoor() {
            if (doorClosed) return;
            doorClosed = true;
            for (Location loc : doorBlocks) {
                loc.getBlock().setType(Material.BARRIER);
            }
        }

        public void openDoor() {
            doorClosed = false;
            lockedForFight = false;
            fightEnded = false;
            for (Location loc : doorBlocks) {
                loc.getBlock().setType(Material.AIR);
            }
        }

        public void start15SecondLootTimer() {
            this.fightEnded = true;
            Bukkit.getScheduler().runTaskLater(ApexRoom.getInstance(), () -> {
                openDoor();
                deathLootGuis.clear();
            }, 15 * 20L); // 15 Saniye sonra kapılar açılır
        }

        public List<Location> getDoorBlocks() { return doorBlocks; }
        public Set<UUID> getInsidePlayers() { return insidePlayers; }
        public Map<UUID, Inventory> getDeathLootGuis() { return deathLootGuis; }
        public boolean isLockedForFight() { return lockedForFight; }
        public void setLockedForFight(boolean locked) { this.lockedForFight = locked; }
        public boolean isFightEnded() { return fightEnded; }
    }

    public static class DeathMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }
}

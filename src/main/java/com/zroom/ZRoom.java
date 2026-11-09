package com.zroom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZRoom extends JavaPlugin implements CommandExecutor {

    private static final String PERMISSION = "zroom.admin";
    private static final String WAND_NAME = ChatColor.GOLD + "ZRoom Seçim Baltası";
    public static final int MAX_PLAYERS = 2;
    private static final long MAX_DOOR_VOLUME = 10_000L;
    public static final int LOOT_TIME_SECONDS = 15;

    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    private final Set<UUID> forceSpawnOnRespawn = new HashSet<>();

    private boolean internalTeleport = false;
    private boolean apexClansHooked = false;

    @Override
    public void onEnable() {
        PluginCommand roomCommand = getCommand("room");
        if (roomCommand == null) {
            getLogger().severe("plugin.yml içinde 'room' komutu tanımlı değil! Eklenti kapatılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        roomCommand.setExecutor(this);
        getServer().getPluginManager().registerEvents(new RoomListener(this), this);
        saveDefaultConfig();
        loadRooms();

        Plugin apexPlugin = Bukkit.getPluginManager().getPlugin("ApexClans");
        if (apexPlugin != null && apexPlugin.isEnabled()) {
            apexClansHooked = true;
            getLogger().info("ApexClans başarıyla bağlandı! Klan içi savaş engelleri aktif.");
        } else {
            getLogger().warning("ApexClans bulunamadı. Klan kontrolleri devre dışı kalacak.");
        }

        getLogger().info("ZRoom eklentisi aktif! " + rooms.size() + " oda yüklendi. Ganimet süresi: " + LOOT_TIME_SECONDS + " sn");
    }

    @Override
    public void onDisable() {
        saveRooms();
        for (Room room : rooms.values()) {
            room.cancelLootTimer();
            room.openDoors();
        }
        rooms.clear();
        pos1Map.clear();
        pos2Map.clear();
        forceSpawnOnRespawn.clear();
    }

    public Map<String, Room> getRooms() { return rooms; }
    public Map<UUID, Location> getPos1Map() { return pos1Map; }
    public Map<UUID, Location> getPos2Map() { return pos2Map; }
    public Set<UUID> getForceSpawnOnRespawn() { return forceSpawnOnRespawn; }
    public boolean isInternalTeleport() { return internalTeleport; }

    public void saveRooms() {
        FileConfiguration config = getConfig();
        config.set("rooms", null);

        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            String key = entry.getKey();
            Room room = entry.getValue();
            String base = "rooms." + key;

            config.set(base + ".name", room.getName());

            Cuboid area = room.getRoomCuboid();
            if (area != null) {
                config.set(base + ".room.world", area.getWorldName());
                config.set(base + ".room.min-x", area.getMinX());
                config.set(base + ".room.min-y", area.getMinY());
                config.set(base + ".room.min-z", area.getMinZ());
                config.set(base + ".room.max-x", area.getMaxX());
                config.set(base + ".room.max-y", area.getMaxY());
                config.set(base + ".room.max-z", area.getMaxZ());
            }

            Cuboid door = room.getDoorCuboid();
            if (door != null) {
                config.set(base + ".door.world", door.getWorldName());
                config.set(base + ".door.min-x", door.getMinX());
                config.set(base + ".door.min-y", door.getMinY());
                config.set(base + ".door.min-z", door.getMinZ());
                config.set(base + ".door.max-x", door.getMaxX());
                config.set(base + ".door.max-y", door.getMaxY());
                config.set(base + ".door.max-z", door.getMaxZ());
            }

            for (int i = 0; i < MAX_PLAYERS; i++) {
                Location spawn = room.getSpawn(i);
                if (spawn != null && spawn.getWorld() != null) {
                    String sp = base + ".spawn" + (i + 1);
                    config.set(sp + ".world", spawn.getWorld().getName());
                    config.set(sp + ".x", spawn.getX());
                    config.set(sp + ".y", spawn.getY());
                    config.set(sp + ".z", spawn.getZ());
                    config.set(sp + ".yaw", (double) spawn.getYaw());
                    config.set(sp + ".pitch", (double) spawn.getPitch());
                }
            }
        }
        saveConfig();
    }

    private void loadRooms() {
        rooms.clear();
        FileConfiguration config = getConfig();
        ConfigurationSection section = config.getConfigurationSection("rooms");
        if (section == null) {
            getLogger().info("config.yml içinde kayıtlı oda bulunamadı (ilk çalıştırma).");
            return;
        }

        int loaded = 0;
        for (String key : section.getKeys(false)) {
            String base = "rooms." + key;
            String name = config.getString(base + ".name", key);
            Room room = new Room(name);

            String roomWorld = config.getString(base + ".room.world");
            World rw = roomWorld != null ? Bukkit.getWorld(roomWorld) : null;
            if (rw != null) {
                Location min = new Location(rw,
                        config.getInt(base + ".room.min-x"),
                        config.getInt(base + ".room.min-y"),
                        config.getInt(base + ".room.min-z"));
                Location max = new Location(rw,
                        config.getInt(base + ".room.max-x"),
                        config.getInt(base + ".room.max-y"),
                        config.getInt(base + ".room.max-z"));
                room.setRoomCuboid(new Cuboid(min, max));
            } else if (roomWorld != null) {
                getLogger().warning("Oda '" + key + "' için dünya bulunamadı: " + roomWorld + " (atlandı)");
                continue;
            }

            String doorWorld = config.getString(base + ".door.world");
            World dw = doorWorld != null ? Bukkit.getWorld(doorWorld) : null;
            if (dw != null) {
                Location min = new Location(dw,
                        config.getInt(base + ".door.min-x"),
                        config.getInt(base + ".door.min-y"),
                        config.getInt(base + ".door.min-z"));
                Location max = new Location(dw,
                        config.getInt(base + ".door.max-x"),
                        config.getInt(base + ".door.max-y"),
                        config.getInt(base + ".door.max-z"));
                room.setDoorCuboid(new Cuboid(min, max));
            }

            for (int i = 0; i < MAX_PLAYERS; i++) {
                String sp = base + ".spawn" + (i + 1);
                String spWorld = config.getString(sp + ".world");
                World sw = spWorld != null ? Bukkit.getWorld(spWorld) : null;
                if (sw != null) {
                    Location loc = new Location(sw,
                            config.getDouble(sp + ".x"),
                            config.getDouble(sp + ".y"),
                            config.getDouble(sp + ".z"),
                            (float) config.getDouble(sp + ".yaw"),
                            (float) config.getDouble(sp + ".pitch"));
                    room.setSpawn(i, loc);
                }
            }

            rooms.put(key, room);
            loaded++;
        }
        getLogger().info(loaded + " oda config.yml'den yüklendi.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("room")) return false;

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Bu komutu kullanmak için yetkiniz yok!");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Bu komut sadece oyun içinden kullanılabilir.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("wand")) {
            giveWand(player);
            return true;
        }

        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        String roomName = args[1];
        String key = roomName.toLowerCase(Locale.ROOT);

        switch (action) {
            case "create":
                createRoom(player, roomName, key);
                break;
            case "setdoor":
                setDoor(player, key);
                break;
            case "setspawn1":
                setSpawn(player, key, 0);
                break;
            case "setspawn2":
                setSpawn(player, key, 1);
                break;
            case "delete":
                deleteRoom(player, key);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Geçersiz komut!");
                sendUsage(player);
                break;
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Kullanım: /room wand");
        player.sendMessage(ChatColor.YELLOW + "Kullanım: /room <create|setdoor|setspawn1|setspawn2|delete> <oda_adi>");
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(WAND_NAME);
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Sol tık: 1. Köşe", ChatColor.GRAY + "Sağ tık: 2. Köşe"));
            wand.setItemMeta(meta);
        }
        if (!player.getInventory().addItem(wand).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Envanteriniz dolu! Yer açıp tekrar deneyin.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "ZRoom seçim baltası verildi! (Sol/Sağ tık ile 2 köşe seçin)");
    }

    private Cuboid getSelection(Player player) {
        Location p1 = pos1Map.get(player.getUniqueId());
        Location p2 = pos2Map.get(player.getUniqueId());

        if (p1 == null || p2 == null) {
            player.sendMessage(ChatColor.RED + "Önce seçim baltası ile 2 köşeyi seçmelisiniz! (/room wand)");
            return null;
        }
        if (p1.getWorld() == null || p2.getWorld() == null
                || !p1.getWorld().getUID().equals(p2.getWorld().getUID())) {
            player.sendMessage(ChatColor.RED + "İki köşe de aynı dünyada olmalı!");
            return null;
        }
        return new Cuboid(p1, p2);
    }

    private void createRoom(Player player, String roomName, String key) {
        Cuboid area = getSelection(player);
        if (area == null) return;

        Room room = rooms.get(key);
        if (room == null) {
            room = new Room(roomName);
            room.setRoomCuboid(area);
            rooms.put(key, room);
            player.sendMessage(ChatColor.GREEN + "'" + roomName + "' odasının alanı başarıyla oluşturuldu!");
        } else {
            room.setRoomCuboid(area);
            player.sendMessage(ChatColor.GREEN + "'" + room.getName() + "' odasının alanı güncellendi!");
        }
        saveRooms();
    }

    private void setDoor(Player player, String key) {
        Room room = rooms.get(key);
        if (room == null) {
            player.sendMessage(ChatColor.RED + "Önce /room create <oda_adi> ile odayı oluşturmalısınız!");
            return;
        }

        Cuboid door = getSelection(player);
        if (door == null) return;

        Cuboid area = room.getRoomCuboid();
        if (area != null && !area.getWorldName().equals(door.getWorldName())) {
            player.sendMessage(ChatColor.RED + "Kapı alanı odayla aynı dünyada olmalı!");
            return;
        }
        if (door.getVolume() > MAX_DOOR_VOLUME) {
            player.sendMessage(ChatColor.RED + "Kapı alanı çok büyük! (En fazla " + MAX_DOOR_VOLUME + " blok)");
            return;
        }

        room.setDoorCuboid(door);
        player.sendMessage(ChatColor.GREEN + "'" + room.getName() + "' odasının kapı alanı ayarlandı!");
        saveRooms();
    }

    private void setSpawn(Player player, String key, int slot) {
        Room room = rooms.get(key);
        if (room == null) {
            player.sendMessage(ChatColor.RED + "Önce odayı oluşturmalısınız!");
            return;
        }

        Location loc = player.getLocation();
        Cuboid area = room.getRoomCuboid();
        if (area != null && !area.contains(loc)) {
            player.sendMessage(ChatColor.RED + "Spawn noktası odanın alanı içinde olmalı!");
            return;
        }

        room.setSpawn(slot, loc);
        player.sendMessage(ChatColor.GREEN + "'" + room.getName() + "' " + (slot + 1) + ". oyuncu spawn noktası ayarlandı!");
        saveRooms();
    }

    private void deleteRoom(Player player, String key) {
        Room room = rooms.remove(key);
        if (room == null) {
            player.sendMessage(ChatColor.RED + "Böyle bir oda yok!");
            return;
        }
        room.cancelLootTimer();
        room.openDoors();
        player.sendMessage(ChatColor.GREEN + "'" + room.getName() + "' odası silindi!");
        saveRooms();
    }

    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_AXE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && WAND_NAME.equals(meta.getDisplayName());
    }

    public static String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    public void safeTeleport(Player player, Location loc) {
        internalTeleport = true;
        try {
            player.teleport(loc);
        } finally {
            internalTeleport = false;
        }
    }

    public void sendToServerSpawn(Player player, String reason) {
        player.sendMessage(ChatColor.RED + reason + " Spawna gönderildiniz.");
        player.performCommand("spawn");
    }

    // ApexClans Entegrasyonu (Yansıma / Reflection ile hatasız kontrol)
    public boolean areInSameClan(Player p1, Player p2) {
        if (!apexClansHooked) return false;
        try {
            Plugin apexPlugin = Bukkit.getPluginManager().getPlugin("ApexClans");
            if (apexPlugin == null) return false;

            Object clanManager = apexPlugin.getClass().getMethod("getClanManager").invoke(apexPlugin);
            if (clanManager == null) return false;

            Method getClanMethod = clanManager.getClass().getMethod("getClan", Player.class);
            Object clan1 = getClanMethod.invoke(clanManager, p1);
            Object clan2 = getClanMethod.invoke(clanManager, p2);

            return clan1 != null && clan2 != null && clan1.equals(clan2);
        } catch (Exception e) {
            return false;
        }
    }
}

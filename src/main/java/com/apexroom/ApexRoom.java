package com.apexroom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class ApexRoom extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static ApexRoom i;
    private final Map<String, Room> r = new HashMap<>();
    private final Map<UUID, Location> p1 = new HashMap<>(), p2 = new HashMap<>();

    @Override public void onEnable() {
        i = this;
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("room").setExecutor(this);
        getCommand("room").setTabCompleter(this);
    }
    @Override public void onDisable() { r.values().forEach(Room::openDoor); }
    public static ApexRoom getInstance() { return i; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) return true;
        if (args.length == 0) return true;
        String sub = args[0].toLowerCase();
        
        if (sub.equals("wand")) {
            ItemStack w = new ItemStack(Material.WOODEN_AXE);
            ItemMeta m = w.getItemMeta(); m.setDisplayName(ChatColor.GOLD + "ApexRoom Baltası");
            w.setItemMeta(m); p.getInventory().addItem(w);
            p.sendMessage(ChatColor.GREEN + "Balta verildi!");
        } else if (sub.equals("pos1") || sub.equals("pos2")) {
            (sub.equals("pos1") ? p1 : p2).put(p.getUniqueId(), p.getLocation());
            p.sendMessage(ChatColor.GREEN + "Nokta kaydedildi.");
        } else if (sub.equals("create") && args.length > 1) {
            Location l1 = p1.get(p.getUniqueId()), l2 = p2.get(p.getUniqueId());
            if (l1 == null || l2 == null) { p.sendMessage(ChatColor.RED + "Önce 2 nokta seç!"); return true; }
            r.put(args[1].toLowerCase(), new Room(args[1], l1, l2));
            p.sendMessage(ChatColor.GREEN + "Oda oluşturuldu!");
        } else if ((sub.equals("setspawn1") || sub.equals("setspawn2")) && args.length > 1) {
            Room room = r.get(args[1].toLowerCase());
            if (room != null) {
                if (sub.equals("setspawn1")) room.s1 = p.getLocation(); else room.s2 = p.getLocation();
                p.sendMessage(ChatColor.GREEN + "Spawn ayarlandı!");
            }
        } else if (sub.equals("kapicreate") && args.length > 1) {
            Room room = r.get(args[1].toLowerCase());
            Location l1 = p1.get(p.getUniqueId()), l2 = p2.get(p.getUniqueId());
            if (room != null && l1 != null && l2 != null) {
                double minX = Math.min(l1.getX(), l2.getX()), maxX = Math.max(l1.getX(), l2.getX());
                double minY = Math.min(l1.getY(), l2.getY()), maxY = Math.max(l1.getY(), l2.getY());
                double minZ = Math.min(l1.getZ(), l2.getZ()), maxZ = Math.max(l1.getZ(), l2.getZ());
                for (double x = minX; x <= maxX; x++)
                    for (double y = minY; y <= maxY; y++)
                        for (double z = minZ; z <= maxZ; z++)
                            room.doors.add(new Location(l1.getWorld(), x, y, z));
                p.sendMessage(ChatColor.GREEN + "Kapı eklendi!");
            }
        } else if (sub.equals("remove") && args.length > 1) {
            Room rm = r.remove(args[1].toLowerCase());
            if (rm != null) rm.openDoor();
            p.sendMessage(ChatColor.GREEN + "İşlem tamam.");
        } else if (sub.equals("list")) {
            p.sendMessage(ChatColor.GOLD + "Odalar: " + String.join(", ", r.keySet()));
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String ali, String[] args) {
        if (args.length == 1) return List.of("wand", "pos1", "pos2", "create", "setspawn1", "setspawn2", "kapicreate", "remove", "list");
        if (args.length == 2 && List.of("create", "setspawn1", "setspawn2", "kapicreate", "remove").contains(args[0].toLowerCase()))
            return new ArrayList<>(r.keySet());
        return null;
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer(); ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName() || !item.getItemMeta().getDisplayName().contains("ApexRoom")) return;
        Block b = e.getClickedBlock(); if (b == null) return;
        e.setCancelled(true);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) p1.put(p.getUniqueId(), b.getLocation());
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) p2.put(p.getUniqueId(), b.getLocation());
        p.sendMessage(ChatColor.GREEN + "Konum seçildi.");
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) return;
        Player p = e.getPlayer();
        Room cur = r.values().stream().filter(room -> room.isInside(to)).findFirst().orElse(null);
        Room prev = r.values().stream().filter(room -> room.isInside(from)).findFirst().orElse(null);

        if (prev != null && prev != cur) prev.ins.remove(p.getUniqueId());
        if (cur != null && prev != cur) {
            for (UUID id : cur.ins) {
                Player ip = Bukkit.getPlayer(id);
                if (ip != null && sameClan(p, ip)) { p.teleport(p.getWorld().getSpawnLocation()); p.sendMessage(ChatColor.RED + "Aynı klan giremez!"); return; }
            }
            if (cur.ins.size() >= 2) { p.teleport(p.getWorld().getSpawnLocation()); p.sendMessage(ChatColor.RED + "Oda dolu!"); return; }
            cur.ins.add(p.getUniqueId());
            if (cur.ins.size() == 1) p.sendMessage(ChatColor.YELLOW + "Rooma giriş yaptın, diğer oyuncu bekleniyor...");
            else if (cur.ins.size() == 2) {
                cur.closeDoor(); cur.lock = true; cur.ended = false;
                Iterator<UUID> it = cur.ins.iterator();
                Player p1 = Bukkit.getPlayer(it.next()), p2 = Bukkit.getPlayer(it.next());
                if (p1 != null && cur.s1 != null) p1.teleport(cur.s1);
                if (p2 != null && cur.s2 != null) p2.teleport(cur.s2);
                cur.ins.forEach(id -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl != null) { pl.sendTitle(ChatColor.RED + "Savaş Başladı!", "", 10, 40, 10); pl.sendMessage(ChatColor.RED + "Savaş Başladı!"); }
                });

                // 10 Dakika (600 Saniye) Zaman Aşımı Zamanlayıcısı
                cur.maxTask = Bukkit.getScheduler().runTaskLater(ApexRoom.getInstance(), () -> {
                    if (cur.lock && !cur.ended) {
                        cur.ins.forEach(id -> {
                            Player pl = Bukkit.getPlayer(id);
                            if (pl != null) {
                                pl.teleport(pl.getWorld().getSpawnLocation());
                                pl.sendMessage(ChatColor.RED + "10 dakika boyunca kimse ölmediği için oda otomatik bitti ve spawna gönderildiniz!");
                            }
                        });
                        cur.openDoor();
                    }
                }, 10 * 60 * 20L);
            }
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player vic = e.getEntity();
        Room room = r.values().stream().filter(ro -> ro.isInside(vic.getLocation())).findFirst().orElse(null);
        if (room != null && room.lock) {
            room.ended = true; // Savaş bitti, komut kısıtlaması kalktı
            if (room.maxTask != null) room.maxTask.cancel();
            List<ItemStack> drops = new ArrayList<>(e.getDrops()); e.getDrops().clear();
            Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + vic.getName() + " Loot");
            drops.forEach(inv::addItem);
            room.gui.put(vic.getUniqueId(), inv); room.ins.remove(vic.getUniqueId());
            vic.sendMessage(ChatColor.YELLOW + "Öldün! 15 saniye sonra kapılar açılacak.");
            room.startTimer();
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Room room = r.values().stream().filter(ro -> ro.isInside(p.getLocation())).findFirst().orElse(null);
        if (room != null && room.lock && !room.ended) {
            room.ended = true; // Oyunda kalanlar için komut kısıtlaması kalktı
            if (room.maxTask != null) room.maxTask.cancel();
            Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + p.getName() + " Loot");
            for (ItemStack it : p.getInventory().getContents()) if (it != null) inv.addItem(it);
            p.getInventory().clear();
            room.gui.put(p.getUniqueId(), inv); room.ins.remove(p.getUniqueId());
            room.ins.forEach(id -> {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null) pl.sendMessage(ChatColor.RED + p.getName() + " çıktığı için öldü sayıldı!");
            });
            room.startTimer();
        }
    }

    @EventHandler public void onClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Room room = r.values().stream().filter(ro -> ro.isInside(p.getLocation())).findFirst().orElse(null);
        if (room != null && room.ended && !room.gui.isEmpty() && e.getAction() == Action.RIGHT_CLICK_BLOCK)
            p.openInventory(room.gui.values().iterator().next());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        Room room = r.values().stream().filter(ro -> ro.isInside(p.getLocation())).findFirst().orElse(null);
        // Savaş devam ederken ADMIN DÂHİL Herkese Komut Engellemesi
        if (room != null && room.lock && !room.ended) {
            e.setCancelled(true); 
            p.sendMessage(ChatColor.RED + "Savaş devam ederken komut yazamazsınız!");
        }
    }

    private boolean sameClan(Player p1, Player p2) {
        Plugin ac = Bukkit.getPluginManager().getPlugin("ApexClan");
        if (ac == null || !ac.isEnabled()) return false;
        try {
            Object c1 = ac.getClass().getMethod("getClan", Player.class).invoke(ac, p1);
            Object c2 = ac.getClass().getMethod("getClan", Player.class).invoke(ac, p2);
            return c1 != null && c1.equals(c2);
        } catch (Exception ignored) {}
        return false;
    }

    public static class Room {
        String name; Location c1, c2, s1, s2;
        List<Location> doors = new ArrayList<>();
        Set<UUID> ins = new HashSet<>();
        Map<UUID, Inventory> gui = new HashMap<>();
        boolean closed = false, lock = false, ended = false;
        BukkitTask maxTask;

        public Room(String name, Location c1, Location c2) { this.name = name; this.c1 = c1; this.c2 = c2; }
        public boolean isInside(Location l) {
            if (c1 == null || c2 == null || l.getWorld() == null || !l.getWorld().equals(c1.getWorld())) return false;
            return l.getX() >= Math.min(c1.getX(), c2.getX()) && l.getX() <= Math.max(c1.getX(), c2.getX()) &&
                   l.getY() >= Math.min(c1.getY(), c2.getY()) && l.getY() <= Math.max(c1.getY(), c2.getY()) &&
                   l.getZ() >= Math.min(c1.getZ(), c2.getZ()) && l.getZ() <= Math.max(c1.getZ(), c2.getZ());
        }
        public void closeDoor() {
            if (closed) return; closed = true;
            doors.forEach(l -> l.getBlock().setType(Material.BARRIER));
        }
        public void openDoor() {
            closed = false; lock = false; ended = false;
            if (maxTask != null) { maxTask.cancel(); maxTask = null; }
            doors.forEach(l -> l.getBlock().setType(Material.AIR));
        }
        public void startTimer() {
            Bukkit.getScheduler().runTaskLater(ApexRoom.getInstance(), () -> { openDoor(); gui.clear(); ins.clear(); }, 15 * 20L);
        }
    }
                }

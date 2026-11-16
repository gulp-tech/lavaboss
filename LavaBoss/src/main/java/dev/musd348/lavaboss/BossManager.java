package dev.musd348.lavaboss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class BossManager {

    private final LavaBoss plugin;
    private final Map<UUID, Boss>     activeBosses        = new HashMap<>();
    private final Map<UUID, Location> bossSpawnLocations  = new HashMap<>();
    private final Random random = new Random();

    private static final List<String> BOSSBAR_WORLDS = Arrays.asList(
            "world", "world2", "spawn2", "events", "test",
            "world2_nether", "world2_the_end",
            "world_nether",  "world_the_end"
    );

    public BossManager(LavaBoss plugin) {
        this.plugin = plugin;
        startTasks();
    }

    public boolean isBossAlive() {
        return !activeBosses.isEmpty();
    }

    public Boss getBossByEntity(LivingEntity entity) {
        if (entity == null) return null;
        return activeBosses.get(entity.getUniqueId());
    }

    public Collection<Boss> getActiveBosses() {
        return Collections.unmodifiableCollection(activeBosses.values());
    }

    public Boss spawnRandomBoss(Location location) {
        if (isBossAlive()) return null;
        List<BossType> possible = new ArrayList<>();
        for (BossType t : BossType.values()) {
            if (plugin.getConfig().getBoolean("bosses." + t.getConfigKey() + ".enabled", true)) {
                possible.add(t);
            }
        }
        if (possible.isEmpty()) return null;
        return spawnBoss(possible.get(random.nextInt(possible.size())), location);
    }

    public Boss spawnBoss(BossType type, Location location) {
        if (isBossAlive()) return null;
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("spawnBoss: локация null!");
            return null;
        }

        try {
            String path = "bosses." + type.getConfigKey();

            String name       = plugin.getConfig().getString(path + ".name", "Boss");
            String etStr      = plugin.getConfig().getString(path + ".entity_type", "ZOMBIE");
            double health     = plugin.getConfig().getDouble(path + ".health", 100);
            double damageMult = plugin.getConfig().getDouble(path + ".damage_multiplier", 1.0);
            int    points     = plugin.getConfig().getInt(path + ".points_reward", 0);
            String spawnMsg   = plugin.getConfig().getString(path + ".spawn_message", "");
            String deathMsg   = plugin.getConfig().getString(path + ".death_message", "");

            List<ItemStack> rewards = new ArrayList<>();
            for (String s : plugin.getConfig().getStringList(path + ".rewards")) {
                String[] p = s.split(" ");
                if (p.length >= 2) {
                    Material mat = Material.getMaterial(p[0].trim().toUpperCase());
                    if (mat != null) {
                        try { rewards.add(new ItemStack(mat, Integer.parseInt(p[1].trim()))); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }

            List<PotionEffect> abilities = new ArrayList<>();
            for (String s : plugin.getConfig().getStringList(path + ".abilities")) {
                PotionEffectType et = PotionEffectType.getByName(s.trim().toUpperCase());
                if (et != null) {
                    abilities.add(new PotionEffect(et, Integer.MAX_VALUE, 1, true, false));
                }
            }

            EntityType entityType;
            try {
                entityType = EntityType.valueOf(etStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный entity_type: " + etStr);
                return null;
            }

            Location spawnLoc = location.clone();
            for (int i = 0; i < 5; i++) {
                if (spawnLoc.getBlock().isPassable()) break;
                spawnLoc.add(0, 1, 0);
            }

            LivingEntity entity = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, entityType);
            if (entity == null) return null;

            Boss boss = new Boss(type, entity, name, health, damageMult,
                    rewards, abilities, spawnMsg, deathMsg, points);

            activeBosses.put(entity.getUniqueId(), boss);
            bossSpawnLocations.put(entity.getUniqueId(), spawnLoc.clone());

            if (entity instanceof Mob mob) {
                Player nearest = findNearestPlayer(mob, 100);
                if (nearest != null) mob.setTarget(nearest);
            }

            broadcastSpawnMessage(boss);

            plugin.getLogger().info("[Boss] Заспавнен: " + type.getConfigKey() +
                    " | HP: " + health);
            return boss;

        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка спавна босса: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void removeBoss(Boss boss) {
        if (boss == null) return;
        UUID id = boss.getEntity().getUniqueId();
        activeBosses.remove(id);
        bossSpawnLocations.remove(id);
        boss.remove();
    }

    public void removeAllBosses() {
        for (Boss b : new ArrayList<>(activeBosses.values())) b.remove();
        activeBosses.clear();
        bossSpawnLocations.clear();
    }

    private Player findNearestPlayer(Mob mob, double radius) {
        Player nearest = null;
        double minDist = radius * radius;
        for (Player p : mob.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                    p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double dist = p.getLocation().distanceSquared(mob.getLocation());
            if (dist < minDist) { minDist = dist; nearest = p; }
        }
        return nearest;
    }

    private void broadcastSpawnMessage(Boss boss) {
        Location loc = boss.getEntity().getLocation();
        List<String> lines = plugin.getConfig().getStringList("messages.spawn_broadcast");

        if (lines.isEmpty()) {
            lines = Arrays.asList(
                    "&d┏ %boss% &fпоявился на ПВП арене &#E0C912/warp pvp",
                    "&d┠ &fЗдоровье: &#FF5555%hp% ❤",
                    "&d┗ &fКоординаты: &#E0C912%x% %y% %z%"
            );
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            for (String line : lines) {
                String formatted = line
                        .replace("%boss%", boss.getName())
                        .replace("%hp%", String.valueOf((int) boss.getMaxHealth()))
                        .replace("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "")
                        .replace("%x%", String.valueOf(loc.getBlockX()))
                        .replace("%y%", String.valueOf(loc.getBlockY()))
                        .replace("%z%", String.valueOf(loc.getBlockZ()));

                p.sendMessage(ColorUtils.formatFor(p, formatted));
            }
        }
    }

    private void teleportToSpawn(Boss boss, Location spawnLoc) {
        LivingEntity entity = boss.getEntity();
        entity.teleport(spawnLoc);

        double newHp = Math.min(entity.getHealth() + boss.getMaxHealth() * 0.1, boss.getMaxHealth());
        entity.setHealth(newHp);

        if (entity instanceof Mob mob) mob.setTarget(null);

        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.5f);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!BOSSBAR_WORLDS.contains(p.getWorld().getName())) continue;
            p.sendActionBar(ColorUtils.formatFor(p,
                    "&d┠ &c" + boss.getName() + " &cвернулся на арену!"));
        }

        plugin.getLogger().info("[Boss] " + boss.getType().getConfigKey() +
                " телепортирован на арену.");
    }

    private void pullPlayers(Boss boss) {
        Location bossLoc   = boss.getEntity().getLocation();
        double pullRadius  = plugin.getConfig().getDouble(
                "bosses." + boss.getType().getConfigKey() + ".pull_radius", 30.0);

        boolean anyPulled = false;
        for (Player p : bossLoc.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                    p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double dist = p.getLocation().distanceSquared(bossLoc);
            if (dist > pullRadius * pullRadius || dist < 4) continue;

            Vector dir = bossLoc.toVector()
                    .subtract(p.getLocation().toVector())
                    .normalize().multiply(1.8).setY(0.5);
            p.setVelocity(dir);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            p.sendActionBar(ColorUtils.formatFor(p,
                    "&d┠ &c" + boss.getName() + " &fпритягивает вас!"));
            anyPulled = true;
        }
        if (anyPulled) {
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.3f);
        }
    }

    private void startTasks() {

        new BukkitRunnable() {
            @Override public void run() {
                activeBosses.entrySet().removeIf(e -> !e.getValue().isValid());
            }
        }.runTaskTimer(plugin, 40L, 40L);

        new BukkitRunnable() {
            private final Map<UUID, Location> lastLocations = new HashMap<>();
            private final Map<UUID, Integer>  stuckTicks    = new HashMap<>();

            @Override public void run() {
                for (Boss boss : new ArrayList<>(activeBosses.values())) {
                    if (!boss.isValid()) continue;

                    LivingEntity entity     = boss.getEntity();
                    Location     currentLoc = entity.getLocation();
                    UUID         id         = entity.getUniqueId();

                    boss.updateBossBar();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (BOSSBAR_WORLDS.contains(p.getWorld().getName())) {
                            boss.showBossBarTo(p);
                        } else {
                            boss.hideBossBarFrom(p);
                        }
                    }

                    if (entity instanceof Mob mob) {
                        org.bukkit.entity.Entity target = mob.getTarget();
                        if (target == null || target.isDead() || !target.isValid()) {
                            Player nearest = findNearestPlayer(mob, 100);
                            if (nearest != null) mob.setTarget(nearest);
                        }
                    }

                    Location lastLoc  = lastLocations.get(id);
                    Location spawnLoc = bossSpawnLocations.get(id);

                    if (lastLoc != null && spawnLoc != null) {
                        double moved         = currentLoc.distanceSquared(lastLoc);
                        double distFromSpawn = currentLoc.distanceSquared(spawnLoc);

                        boolean stuck  = moved < 0.25;
                        boolean tooFar = distFromSpawn > 2500;

                        if (stuck || tooFar) {
                            int s = stuckTicks.getOrDefault(id, 0) + 1;
                            stuckTicks.put(id, s);
                            if (s >= 10 || tooFar) {
                                stuckTicks.put(id, 0);
                                teleportToSpawn(boss, spawnLoc);
                            }
                        } else {
                            stuckTicks.put(id, 0);
                        }
                    }
                    lastLocations.put(id, currentLoc.clone());
                }
                lastLocations.entrySet().removeIf(e -> !activeBosses.containsKey(e.getKey()));
                stuckTicks.entrySet().removeIf(e -> !activeBosses.containsKey(e.getKey()));
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            private final Map<UUID, Integer> pullCount = new HashMap<>();

            @Override public void run() {
                for (Boss boss : new ArrayList<>(activeBosses.values())) {
                    if (!boss.isValid()) continue;
                    UUID id    = boss.getEntity().getUniqueId();
                    int  count = pullCount.getOrDefault(id, 0);
                    if (count >= 5) { pullCount.remove(id); continue; }
                    pullPlayers(boss);
                    pullCount.put(id, count + 1);
                }
                pullCount.entrySet().removeIf(e -> !activeBosses.containsKey(e.getKey()));
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }
}
package dev.musd348.lavaboss.listener;

import dev.musd348.lavaboss.Boss;
import dev.musd348.lavaboss.ColorUtils;
import dev.musd348.lavaboss.LavaBoss;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BossListener implements Listener {

    private final LavaBoss plugin;

    public BossListener(LavaBoss plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBossDamageOverride(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }

        boolean isVictimBoss = (victim instanceof LivingEntity living) && plugin.getBossManager().getBossByEntity(living) != null;
        boolean isDamagerBoss = (damager instanceof LivingEntity living) && plugin.getBossManager().getBossByEntity(living) != null;

        if (isVictimBoss || isDamagerBoss) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        Boss boss = plugin.getBossManager().getBossByEntity(living);
        if (boss == null) return;

        Player damager = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity d = byEntity.getDamager();
            if (d instanceof Player p) {
                damager = p;
            } else if (d instanceof Projectile proj
                    && proj.getShooter() instanceof Player p) {
                damager = p;
            }
        }

        if (damager != null) {
            double actualDmg = Math.min(event.getFinalDamage(), living.getHealth());
            boss.recordDamage(damager.getUniqueId(), actualDmg);
        }

        Bukkit.getScheduler().runTaskLater(plugin, boss::updateBossBar, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Boss boss = plugin.getBossManager().getBossByEntity(entity);
        if (boss == null) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        for (ItemStack item : boss.getRewards()) {
            if (item != null) event.getDrops().add(item);
        }

        List<Map.Entry<UUID, Double>> top = boss.getDamageTop();

        broadcastDeathMessage(boss, top);
        giveRewardsByPlace(top, boss);

        plugin.getBossManager().removeBoss(boss);
    }

    private void broadcastDeathMessage(Boss boss, List<Map.Entry<UUID, Double>> top) {
        List<String> template = plugin.getConfig().getStringList("messages.death_broadcast");

        if (template.isEmpty()) {
            template = Arrays.asList(
                    "&d┏ %boss% &aповержен!",
                    "&d┠ &fУчастников битвы: &#E0C912%players%",
                    "&d┠ &fТоп по урону:",
                    "%top_1%",
                    "%top_2%",
                    "%top_3%"
            );
        }

        String topFormat = plugin.getConfig().getString("messages.top_format",
                "&d┠ &f%medal% %player% &7— &#FF5555%damage% урона%reward%");
        String topNone = plugin.getConfig().getString("messages.top_none",
                "&d┠ &8— Отсутствует");

        String[] medals = {"🥇", "🥈", "🥉", "4.", "5."};
        String[] medalsBedrock = {"1.", "2.", "3.", "4.", "5."};

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean isBedrock = plugin.getBedrockManager().isBedrockPlayer(p);

            for (String line : template) {
                if (line.trim().startsWith("%top_")) {
                    int index = -1;
                    if (line.contains("%top_1%")) index = 0;
                    else if (line.contains("%top_2%")) index = 1;
                    else if (line.contains("%top_3%")) index = 2;
                    else if (line.contains("%top_4%")) index = 3;
                    else if (line.contains("%top_5%")) index = 4;

                    if (index >= 0 && index < top.size()) {
                        UUID uuid = top.get(index).getKey();
                        double dmg = top.get(index).getValue();
                        int reward = getPlaceReward(index + 1, boss);
                        Player pl = Bukkit.getPlayer(uuid);

                        String pName = (pl != null) ? pl.getName() : "Оффлайн";
                        String rwStr = reward > 0 ? " &#E0C912+" + reward + " ⭐" : "";
                        String medal = isBedrock ? medalsBedrock[index] : medals[index];

                        String topFormatted = topFormat
                                .replace("%medal%", medal)
                                .replace("%player%", pName)
                                .replace("%damage%", String.format("%.0f", dmg))
                                .replace("%reward%", rwStr);

                        p.sendMessage(ColorUtils.formatFor(p, topFormatted));
                    } else {
                        p.sendMessage(ColorUtils.formatFor(p, topNone));
                    }
                } else {
                    String formatted = line
                            .replace("%boss%", boss.getName())
                            .replace("%players%", String.valueOf(top.size()));

                    p.sendMessage(ColorUtils.formatFor(p, formatted));
                }
            }
        }
    }

    private void giveRewardsByPlace(List<Map.Entry<UUID, Double>> top, Boss boss) {
        int limit = Math.min(top.size(), getMaxPlaces(boss));
        for (int i = 0; i < limit; i++) {
            int    place  = i + 1;
            Player player = Bukkit.getPlayer(top.get(i).getKey());
            if (player == null) continue;
            int reward = getPlaceReward(place, boss);
            if (reward <= 0) continue;
            givePoints(player, reward);

            player.sendMessage(ColorUtils.formatFor(player,
                    "&d┠ &aВы заняли &f" + place + " место &aи получили &#E0C912" + reward + " осколков!"));
        }
    }

    private int getPlaceReward(int place, Boss boss) {
        String bossPath    = "bosses." + boss.getType().getConfigKey() +
                ".place_rewards." + place;
        String defaultPath = "place_rewards." + place;
        if (plugin.getConfig().contains(bossPath))
            return plugin.getConfig().getInt(bossPath, 0);
        return plugin.getConfig().getInt(defaultPath, 0);
    }

    private int getMaxPlaces(Boss boss) {
        var bossSection = plugin.getConfig().getConfigurationSection(
                "bosses." + boss.getType().getConfigKey() + ".place_rewards");
        var defSection  = plugin.getConfig().getConfigurationSection("place_rewards");
        int bossPlaces  = (bossSection != null) ? bossSection.getKeys(false).size() : 0;
        int defPlaces   = (defSection  != null) ? defSection.getKeys(false).size()  : 3;
        return bossPlaces > 0 ? bossPlaces : defPlaces;
    }

    private void givePoints(Player player, int points) {
        if (points <= 0) return;
        String cmd = plugin.getConfig()
                .getString("reward_command", "points give %player% %points%")
                .replace("%player%", player.getName())
                .replace("%points%", String.valueOf(points));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
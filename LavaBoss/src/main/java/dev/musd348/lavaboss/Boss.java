package dev.musd348.lavaboss;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class Boss {

    private final BossType type;
    private final LivingEntity entity;
    private final String name;
    private final double maxHealth;
    private final double damageMultiplier;
    private final List<ItemStack> rewards;
    private final String spawnMessage;
    private final String deathMessage;
    private final int pointsReward;
    private final BossBar bossBar;
    private final Map<UUID, Double> damageMap = new LinkedHashMap<>();

    public Boss(BossType type,
                LivingEntity entity,
                String name,
                double maxHealth,
                double damageMultiplier,
                List<ItemStack> rewards,
                List<PotionEffect> abilities,
                String spawnMessage,
                String deathMessage,
                int pointsReward) {

        this.type             = type;
        this.entity           = entity;
        this.name             = name;
        this.maxHealth        = maxHealth;
        this.damageMultiplier = damageMultiplier;
        this.rewards          = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.spawnMessage     = spawnMessage != null ? spawnMessage : "";
        this.deathMessage     = deathMessage != null ? deathMessage : "";
        this.pointsReward     = pointsReward;

        Component title = ColorUtils.colorize(name);
        this.bossBar = BossBar.bossBar(
                title,
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );

        setupBoss(abilities != null ? abilities : new ArrayList<>());
        
        // Инициализация TopManager
        LavaBoss.getInstance().getTopManager().clearTop();
    }

    private void setupBoss(List<PotionEffect> abilities) {
        entity.customName(ColorUtils.colorize(name));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        AttributeInstance healthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            try {
                healthAttr.setBaseValue(maxHealth);
            } catch (Exception e) {
                healthAttr.setBaseValue(Math.min(maxHealth, 2048.0));
                LavaBoss.getInstance().getLogger().warning(
                        "HP " + maxHealth + " превышает лимит, установлено 2048");
            }
        }

        Bukkit.getScheduler().runTaskLater(LavaBoss.getInstance(), () -> {
            if (entity.isValid() && !entity.isDead()) {
                entity.setHealth(Math.min(maxHealth,
                        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            }
        }, 1L);

        AttributeInstance dmgAttr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null) {
            new ArrayList<>(dmgAttr.getModifiers()).forEach(dmgAttr::removeModifier);
            dmgAttr.setBaseValue(dmgAttr.getBaseValue() * damageMultiplier);
        }

        AttributeInstance speedAttr = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.3);
        }

        AttributeInstance followAttr = entity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(64.0);
        }

        for (PotionEffect effect : abilities) {
            entity.addPotionEffect(effect);
        }
    }

    public void recordDamage(UUID playerUUID, double damage) {
        if (playerUUID == null || damage <= 0) return;
        damageMap.merge(playerUUID, damage, Double::sum);
        // Обновляем топ в реальном времени
        LavaBoss.getInstance().getTopManager().recordDamage(playerUUID, damage);
    }

    public List<Map.Entry<UUID, Double>> getDamageTop() {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(damageMap.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public void updateBossBar() {
        if (entity == null || !entity.isValid()) return;
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = (attr != null) ? attr.getValue() : maxHealth;
        double progress = entity.getHealth() / max;
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        bossBar.progress((float) progress);
        
        // Поддержка плейсхолдеров в босс-баре (если включено в конфиге)
        if (LavaBoss.getInstance().getConfig().getBoolean("bossbar.show_placeholders", true)) {
            // Можно добавить логику обновления заголовка с плейсхолдерами
        }
    }

    public void showBossBarTo(Player player) {
        bossBar.addViewer(player);
    }

    public void hideBossBarFrom(Player player) {
        bossBar.removeViewer(player);
    }

    public double       getMaxHealth()    { return maxHealth; }
    public BossBar      getBossBar()      { return bossBar; }
    public BossType     getType()         { return type; }
    public LivingEntity getEntity()       { return entity; }
    public String       getName()         { return name; }
    public String       getDeathMessage() { return deathMessage; }
    public String       getSpawnMessage() { return spawnMessage; }
    public List<ItemStack> getRewards()   { return new ArrayList<>(rewards); }
    public int          getPointsReward() { return pointsReward; }

    public boolean isValid() {
        return entity != null && !entity.isDead() && entity.isValid();
    }

    public void remove() {
        if (entity != null && entity.isValid()) entity.remove();
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.removeViewer(p);
        }
        
        // НОВЫЙ ФУНКЦИОНАЛ: Награждение игроков и топ (безопасно)
        LavaBoss plugin = LavaBoss.getInstance();
        if (plugin != null && plugin.getEconomyManager() != null && plugin.getTopManager() != null) {
            // Награды по местам
            plugin.getTopManager().rewardTopPlayers(this);
            
            // Индивидуальные награды (старый метод)
            for (Map.Entry<UUID, Double> entry : getDamageTop()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && entry.getValue() > 0) {
                    plugin.getEconomyManager().rewardPlayer(p, this);
                }
            }
        }
        
        // Уведомление об обновлении (если версия изменилась)
        checkUpdate();
    }

    private void checkUpdate() {
        // Логика проверки обновления (симуляция)
        String currentVersion = LavaBoss.getInstance().getDescription().getVersion();
        String newVersion = "1.5.0"; // Пример новой версии
        if (!currentVersion.equals(newVersion) && LavaBoss.getInstance().getConfig().getBoolean("update_notification.enabled", true)) {
            LavaBoss.getInstance().getLogger().info("[LavaBoss] Обновление доступно! Версия " + newVersion + " (текущая: " + currentVersion + ")");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) {
                    p.sendMessage(ColorUtils.formatFor(p,
                            LavaBoss.getInstance().getConfig().getString("update_notification.player_message", "&d┠ &cУважаемые ОП! Обновление LavaBoss до версии %new_version% доступно! &7Обновитесь для стабильной работы.")
                                    .replace("%new_version%", newVersion)));
                }
            }
        }
    }
}
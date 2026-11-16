package dev.musd348.lavaboss;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyManager implements Listener {

    private final LavaBoss plugin;
    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private final Map<UUID, Long> lastRewardTime = new HashMap<>();

    public EconomyManager(LavaBoss plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // --- НОВЫЙ ФУНКЦИОНАЛ: Система уровней и наград ---
    public void rewardPlayer(Player player, Boss boss) {
        if (player == null || boss == null) return;

        int points = boss.getPointsReward();
        if (points <= 0) return;

        // Защита от спама наград (баг-фикс: не давать награду дважды за один босс)
        long now = System.currentTimeMillis();
        long last = lastRewardTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 5000) return; // 5 секунд задержки
        lastRewardTime.put(player.getUniqueId(), now);

        // Добавление очков
        int current = playerPoints.getOrDefault(player.getUniqueId(), 0);
        playerPoints.put(player.getUniqueId(), current + points);

        // Интеграция с экономикой (Vault) - если есть
        try {
            net.milkbowl.vault.economy.Economy econ = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class).getProvider();
            if (econ != null) {
                econ.depositPlayer(player, points * 0.1); // 10% очков в деньги
            }
        } catch (Exception ignored) {}

        // Сообщение игроку
        player.sendMessage(ColorUtils.formatFor(player,
                "&d┠ &aВы получили &e" + points + " &aочков за победу над &c" + boss.getName() + "!"));

        // Уровень (новый функционал)
        int level = (int) Math.sqrt(current / 100.0);
        if (level > 0 && current % 100 == 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            player.sendMessage(ColorUtils.formatFor(player,
                    "&d┠ &bУровень &e" + level + " &bдостигнут! &aПоздравляем!"));
        }

        // Награда предметом (новый функционал: уникальный кристалл)
        if (Math.random() < 0.1) { // 10% шанс
            ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.colorize("&d&lКристалл Лавы"));
                meta.getPersistentDataContainer().set(plugin.getItemManager().sealKey, PersistentDataType.STRING, "lava_crystal");
                item.setItemMeta(meta);
                player.getInventory().addItem(item);
                player.sendMessage(ColorUtils.formatFor(player, "&d┠ &eВы получили &dКристалл Лавы!"));
            }
        }
    }

    // --- НОВЫЙ ФУНКЦИОНАЛ: Анонс при входе ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int level = (int) Math.sqrt(playerPoints.getOrDefault(player.getUniqueId(), 0) / 100.0);
        if (level > 0) {
            player.sendMessage(ColorUtils.formatFor(player,
                    "&d┏ &bДобро пожаловать обратно, &e" + player.getName() + " &b(Уровень " + level + ")!"));
            player.sendMessage(ColorUtils.formatFor(player,
                    "&d┗ &fВаши очки: &e" + playerPoints.getOrDefault(player.getUniqueId(), 0)));
        }

        // Проверка Bedrock при входе (улучшенная поддержка новых версий)
        if (BedrockUtils.isBedrockPlayer(player)) {
            player.sendMessage(ColorUtils.formatFor(player, "&d┠ &aДобро пожаловать, игрок Bedrock!"));
        }
    }

    public int getPlayerLevel(Player player) {
        return (int) Math.sqrt(playerPoints.getOrDefault(player.getUniqueId(), 0) / 100.0);
    }

    public int getPlayerPoints(Player player) {
        return playerPoints.getOrDefault(player.getUniqueId(), 0);
    }

    // --- НОВЫЙ ФУНКЦИОНАЛ: Телепорт к боссу (безопасный) ---
    public void teleportToBossSafe(Player player, Boss boss) {
        if (player == null || boss == null || !boss.isValid()) return;
        
        Location loc = boss.getEntity().getLocation();
        // Проверка на блокировку (баг-фикс: не телепортировать в стены)
        if (loc.getBlock().isPassable() || loc.clone().add(0, 1, 0).getBlock().isPassable()) {
            player.teleport(loc);
            player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            player.sendMessage(ColorUtils.formatFor(player, "&d┠ &aВы телепортированы к &c" + boss.getName() + "!"));
        } else {
            player.sendMessage(ColorUtils.formatFor(player, "&d┠ &cБосс заблокирован, телепорт невозможен."));
        }
    }
}
package dev.musd348.lavaboss;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public class TopManager {

    private final LavaBoss plugin;
    private final Map<UUID, Double> damageMap = new LinkedHashMap<>();
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public TopManager(LavaBoss plugin) {
        this.plugin = plugin;
    }

    public void recordDamage(UUID playerUUID, double damage) {
        if (playerUUID == null || damage <= 0) return;
        damageMap.merge(playerUUID, damage, Double::sum);
        updateScoreboard(playerUUID);
    }

    public List<Map.Entry<UUID, Double>> getDamageTop() {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(damageMap.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public void rewardTopPlayers(Boss boss) {
        List<Map.Entry<UUID, Double>> top = getDamageTop();
        if (top.isEmpty()) return;

        Map<String, String> placeholders = new HashMap<>();
        int place = 1;

        for (Map.Entry<UUID, Double> entry : top) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;

            double damage = entry.getValue();
            int rewardPoints = 0;
            String medal = "";

            // Получаем награду из конфига top_rewards.yml
            String rewardKey = String.valueOf(place);
            int baseReward = plugin.getConfig().getInt("place_rewards." + rewardKey, 0);
            if (baseReward == 0) {
                // По умолчанию из top_rewards.yml
                baseReward = plugin.getConfig().getInt("top_system.place_rewards." + rewardKey, 0);
            }
            
            rewardPoints = (int) (baseReward * plugin.getConfig().getDouble("top_system.reward_multiplier", 1.0));

            switch (place) {
                case 1 -> medal = "&e🥇 ";
                case 2 -> medal = "&a🥈 ";
                case 3 -> medal = "&6🥉 ";
                default -> medal = "&7" + place + ". ";
            }

            // Награда (PlayerPoints или внутренние очки)
            if (plugin.getConfig().getBoolean("top_system.playerpoints_integration", true)) {
                try {
                    // Псевдо-код для PlayerPoints (нужен плагин)
                    // PlayerPointsAPI.givePoints(p, rewardPoints);
                    plugin.getEconomyManager().rewardPlayer(p, boss); // Используем наш менеджер
                } catch (Exception ignored) {}
            } else {
                plugin.getEconomyManager().rewardPlayer(p, boss);
            }

            // Формируем строку для чата
            String rewardStr = rewardPoints > 0 ? " &e(+ " + rewardPoints + " баллов)" : "";
            String line = plugin.getConfig().getString("messages.top_format", "&d┠ &f%medal% %player% &7— &#FF5555%damage% урона%reward%")
                    .replace("%medal%", medal)
                    .replace("%player%", p.getName())
                    .replace("%damage%", String.format("%.1f", damage))
                    .replace("%reward%", rewardStr);

            placeholders.put("%top_" + place + "%", line);
            place++;

            // Обновляем скорборд игрока
            updateScoreboard(p.getUniqueId(), boss, place, damage);

            if (place > plugin.getConfig().getInt("top_system.max_places", 10)) break;
        }

        // Рассылка сообщения о смерти (в конфиге messages.death_broadcast)
        List<String> lines = plugin.getConfig().getStringList("messages.death_broadcast");
        if (lines.isEmpty()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Проверка ОП для уведомления об обновлении (в другом месте)
            for (String line : lines) {
                String formatted = line
                        .replace("%boss%", boss.getName())
                        .replace("%players%", String.valueOf(top.size()))
                        .replace("%top_1%", placeholders.getOrDefault("%top_1%", plugin.getConfig().getString("messages.top_none", "&d┠ &8— Отсутствует")))
                        .replace("%top_2%", placeholders.getOrDefault("%top_2%", plugin.getConfig().getString("messages.top_none", "&d┠ &8— Отсутствует")))
                        .replace("%top_3%", placeholders.getOrDefault("%top_3%", plugin.getConfig().getString("messages.top_none", "&d┠ &8— Отсутствует")));

                p.sendMessage(ColorUtils.formatFor(p, formatted));
            }
        }
    }

    private void updateScoreboard(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) return;

        Scoreboard sb = p.getScoreboard();
        if (sb == null) sb = Bukkit.getScoreboardManager().getNewScoreboard();
        
        Objective obj = sb.getObjective("lavaboss_top");
        if (obj == null) {
            obj = sb.registerNewObjective("lavaboss_top", "dummy", ChatColor.RED + "Топ урона");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Обновляем строки (пример)
        obj.getScore("Ваш урон").setScore((int) damageMap.getOrDefault(playerId, 0.0));
        
        p.setScoreboard(sb);
        playerScoreboards.put(playerId, sb);
    }

    public void updateScoreboard(UUID playerId, Boss boss, int place, double damage) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) return;
        // Логика обновления скорборда при смерти босса
        // (можно добавить плейсхолдеры для TAB/MVdW)
    }

    public void clearTop() {
        damageMap.clear();
        playerScoreboards.clear();
    }
}
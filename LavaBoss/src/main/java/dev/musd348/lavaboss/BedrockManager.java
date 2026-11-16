package dev.musd348.lavaboss;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BedrockManager {

    private final LavaBoss plugin;
    private final Set<UUID> bedrockPlayers = new HashSet<>();
    private final boolean supported;

    public BedrockManager(LavaBoss plugin) {
        this.plugin = plugin;

        Plugin geyser    = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        this.supported   = (geyser != null && geyser.isEnabled())
                || (floodgate != null && floodgate.isEnabled());

        if (supported) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (BedrockUtils.isBedrockPlayer(p)) {
                    bedrockPlayers.add(p.getUniqueId());
                }
            }
            startCacheTask();
        }
    }

    private void startCacheTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            bedrockPlayers.clear();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (BedrockUtils.isBedrockPlayer(p)) {
                    bedrockPlayers.add(p.getUniqueId());
                }
            }
        }, 0L, 1200L);
    }

    public boolean isBedrockPlayer(Player player) {
        if (!supported) return false;
        if (player == null) return false;
        if (bedrockPlayers.contains(player.getUniqueId())) return true;
        boolean result = BedrockUtils.isBedrockPlayer(player);
        if (result) bedrockPlayers.add(player.getUniqueId());
        return result;
    }

    public boolean isBedrockSupported() {
        return supported;
    }

    public Set<UUID> getBedrockPlayers() {
        return new HashSet<>(bedrockPlayers);
    }
}
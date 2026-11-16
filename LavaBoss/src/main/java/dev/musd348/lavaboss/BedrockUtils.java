package dev.musd348.lavaboss;

import org.bukkit.entity.Player;

public class BedrockUtils {

    private static Boolean floodgateAvailable = null;
    private static Boolean geyserAvailable = null;

    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;

        if (isFloodgateAvailable()) {
            try {
                return org.geysermc.floodgate.api.FloodgateApi.getInstance()
                        .isFloodgatePlayer(player.getUniqueId());
            } catch (Exception ignored) {}
        }

        if (isGeyserAvailable()) {
            try {
                return org.geysermc.geyser.api.GeyserApi.api()
                        .isBedrockPlayer(player.getUniqueId());
            } catch (Exception ignored) {}
        }

        return false;
    }

    private static boolean isFloodgateAvailable() {
        if (floodgateAvailable == null) {
            try {
                Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                org.geysermc.floodgate.api.FloodgateApi.getInstance();
                floodgateAvailable = true;
            } catch (Exception e) {
                floodgateAvailable = false;
            }
        }
        return floodgateAvailable;
    }

    private static boolean isGeyserAvailable() {
        if (geyserAvailable == null) {
            try {
                Class.forName("org.geysermc.geyser.api.GeyserApi");
                org.geysermc.geyser.api.GeyserApi.api();
                geyserAvailable = true;
            } catch (Exception e) {
                geyserAvailable = false;
            }
        }
        return geyserAvailable;
    }

    public static void reset() {
        floodgateAvailable = null;
        geyserAvailable = null;
    }
}
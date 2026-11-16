package dev.musd348.lavaboss;

public enum BossType {
    RAVAGER("ravager"),
    ICE_GIANT("ice_giant"),
    SHADOW_KNIGHT("shadow_knight"),
    EASY_BOSS("easy_boss");

    private final String configKey;

    BossType(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static BossType fromString(String str) {
        if (str == null) return null;
        for (BossType type : values()) {
            if (type.configKey.equalsIgnoreCase(str)) return type;
        }
        return null;
    }
}
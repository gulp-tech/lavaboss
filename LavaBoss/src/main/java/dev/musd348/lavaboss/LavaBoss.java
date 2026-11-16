package dev.musd348.lavaboss;

import dev.musd348.lavaboss.listener.AltarListener;
import dev.musd348.lavaboss.listener.BossListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class LavaBoss extends JavaPlugin {

    private static LavaBoss instance;
    private BossManager bossManager;
    private ItemManager itemManager;
    private AltarManager altarManager;
    private BedrockManager bedrockManager;

    private int autoSpawnTaskId = -1;
    private long autoSpawnIntervalTicks = 0L;
    private long ticksUntilNextSpawn = 0L;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.altarManager   = new AltarManager(this);
        this.bossManager    = new BossManager(this);
        this.itemManager    = new ItemManager(this);
        this.bedrockManager = new BedrockManager(this);

        getServer().getPluginManager().registerEvents(new AltarListener(this), this);
        getServer().getPluginManager().registerEvents(new BossListener(this), this);

        BossCommand cmd = new BossCommand(this);
        registerCommand("boss",   cmd);
        registerCommand("lavaboss", cmd);

        setupAutoSpawn();

        if (bedrockManager.isBedrockSupported()) {
            getLogger().info("Поддержка Bedrock (Geyser/Floodgate): АКТИВНА");
        } else {
            getLogger().info("Поддержка Bedrock: НЕ ОБНАРУЖЕНА (только Java)");
        }

        getLogger().info("LavaBoss v" + getDescription().getVersion() + " включён!");
    }

    private void registerCommand(String name, BossCommand cmd) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(cmd);
            c.setTabCompleter(cmd);
        }
    }

    @Override
    public void onDisable() {
        if (bossManager != null)   bossManager.removeAllBosses();
        if (autoSpawnTaskId != -1) Bukkit.getScheduler().cancelTask(autoSpawnTaskId);
        getLogger().info("LavaBoss выключён.");
    }

    public static LavaBoss getInstance() { return instance; }
    public BossManager    getBossManager()    { return bossManager; }
    public ItemManager    getItemManager()    { return itemManager; }
    public AltarManager   getAltarManager()   { return altarManager; }
    public BedrockManager getBedrockManager() { return bedrockManager; }

    public long    getTicksUntilNextSpawn() { return ticksUntilNextSpawn; }
    public boolean isAutoSpawnRunning()     { return autoSpawnTaskId != -1; }

    public void reloadAutoSpawn() {
        if (autoSpawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoSpawnTaskId);
            autoSpawnTaskId = -1;
        }
        BedrockUtils.reset();
        setupAutoSpawn();
    }

    private void setupAutoSpawn() {
        if (!getConfig().getBoolean("auto_spawn.enabled", false)) return;

        String    bossKey = getConfig().getString("auto_spawn.boss", "easy_boss");
        BossType  type    = BossType.fromString(bossKey);

        autoSpawnIntervalTicks = getConfig().getLong("auto_spawn.interval_ticks", 216000L);
        ticksUntilNextSpawn    = autoSpawnIntervalTicks;

        autoSpawnTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {

            if (ticksUntilNextSpawn > 0) ticksUntilNextSpawn -= 20;

            if (ticksUntilNextSpawn <= 0) {
                ticksUntilNextSpawn = autoSpawnIntervalTicks;

                if (bossManager.isBossAlive()) return;

                String wName = getConfig().getString("auto_spawn.world");
                if (wName == null) return;
                World w = Bukkit.getWorld(wName);
                if (w == null) return;

                Location loc = new Location(w,
                        getConfig().getDouble("auto_spawn.x"),
                        getConfig().getDouble("auto_spawn.y"),
                        getConfig().getDouble("auto_spawn.z"));

                if (type != null) bossManager.spawnBoss(type, loc);
            }

        }, 20L, 20L);
    }
}
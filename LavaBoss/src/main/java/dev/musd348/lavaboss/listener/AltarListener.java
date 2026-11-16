package dev.musd348.lavaboss.listener;

import dev.musd348.lavaboss.ColorUtils;
import dev.musd348.lavaboss.LavaBoss;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class AltarListener implements Listener {

    private final LavaBoss plugin;
    private final Map<Location, Integer> altarProgress = new HashMap<>();

    // Безопасное определение частиц для 1.21
    private final Particle enchantParticle = findParticle("ENCHANT", "ENCHANTMENT_TABLE");
    private final Particle explosionParticle = findParticle("EXPLOSION_EMITTER", "EXPLOSION_HUGE", "EXPLOSION");

    public AltarListener(LavaBoss plugin) {
        this.plugin = plugin;
        startParticles();
    }

    private Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (Exception ignored) {}
        }
        return Particle.FLAME; // Резервная частица
    }

    private void startParticles() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Location loc : plugin.getAltarManager().getAltars()) {
                if (loc.getWorld() == null) continue;
                Location center = loc.clone().add(0.5, 1.2, 0.5);
                loc.getWorld().spawnParticle(
                        enchantParticle, center, 3,
                        0.3, 0.1, 0.3, 0.05);
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getItemManager().isAltarItem(event.getItemInHand())) return;
        plugin.getAltarManager().addAltar(event.getBlock().getLocation());
        event.getPlayer().sendMessage(
                ColorUtils.formatFor(event.getPlayer(),
                        "&d┠ &aМагический алтарь установлен!"));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!plugin.getAltarManager().isAltar(loc)) return;

        plugin.getAltarManager().removeAltar(loc);
        altarProgress.remove(loc);
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(
                loc, plugin.getItemManager().getAltarBlock(1));
        event.getPlayer().sendMessage(
                ColorUtils.formatFor(event.getPlayer(), "&d┠ &cАлтарь разрушен."));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND)         return;
        if (event.getClickedBlock() == null)               return;

        Location blockLoc = event.getClickedBlock().getLocation();
        if (!plugin.getAltarManager().isAltar(blockLoc)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (plugin.getBossManager().isBossAlive()) {
            player.sendMessage(ColorUtils.formatFor(player,
                    plugin.getConfig().getString(
                            "messages.boss_already_alive", "&d┠ &cУже призван!")));
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !plugin.getItemManager().isSeal(item)) return;

        int newAmount = item.getAmount() - 1;
        if (newAmount > 0) {
            item.setAmount(newAmount);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        int current = altarProgress.getOrDefault(blockLoc, 0) + 1;

        try {
            Sound sound = Sound.valueOf(plugin.getConfig()
                    .getString("altar.sound_place", "BLOCK_END_PORTAL_FRAME_FILL"));
            blockLoc.getWorld().playSound(blockLoc, sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}

        blockLoc.getWorld().spawnParticle(
                Particle.FLAME,
                blockLoc.clone().add(0.5, 1, 0.5),
                8, 0.2, 0.2, 0.2, 0.02);

        if (current >= 3) {
            altarProgress.remove(blockLoc);
            activateAltar(blockLoc);
        } else {
            altarProgress.put(blockLoc, current);
            String msg = plugin.getConfig()
                    .getString("messages.altar_progress",
                            "&d┠ &eПечать установлена! &#00FFFF%current%&7/&#00FFFF3")
                    .replace("%current%", String.valueOf(current));
            player.sendActionBar(ColorUtils.formatFor(player, msg));
        }
    }

    private void activateAltar(Location altarLoc) {
        try {
            Sound sound = Sound.valueOf(plugin.getConfig()
                    .getString("altar.sound_summon", "ENTITY_WITHER_SPAWN"));
            altarLoc.getWorld().playSound(altarLoc, sound, 2.0f, 0.5f);
        } catch (Exception ignored) {}

        altarLoc.getWorld().spawnParticle(
                explosionParticle,
                altarLoc.clone().add(0.5, 1, 0.5),
                3, 0, 0, 0, 0);

        String titleMain = plugin.getConfig().getString(
                "messages.altar_title_main",
                "&#8B0000А&#9B1500Л&#AB2900Т&#BB3D00А&#CB5100Р&#DB6500Ь &#EB7900А&#FB8D00К&#FFAA00Т&#FFBB00И&#FFCC00В&#FFDD00И&#FFEE00Р&#FFFF00О&#FFEE00В&#FFDD00А&#FFCC00Н");
        String titleSub = plugin.getConfig().getString(
                "messages.altar_title_sub",
                "&eПрибытие на ПВП арену...");

        for (Player p : Bukkit.getOnlinePlayers()) {
            Title title = Title.title(
                    ColorUtils.formatFor(p, titleMain),
                    ColorUtils.formatFor(p, titleSub),
                    Title.Times.times(
                            Duration.ofMillis(300),
                            Duration.ofMillis(3000),
                            Duration.ofMillis(800))
            );
            p.showTitle(title);
        }

        Location spawnLoc = getSpawnLocation();
        if (spawnLoc == null) spawnLoc = altarLoc.clone().add(0.5, 3, 0.5);

        plugin.getBossManager().spawnRandomBoss(spawnLoc);
    }

    private Location getSpawnLocation() {
        String wName = plugin.getConfig().getString("auto_spawn.world");
        if (wName == null) return null;
        World w = Bukkit.getWorld(wName);
        if (w == null) return null;
        return new Location(w,
                plugin.getConfig().getDouble("auto_spawn.x"),
                plugin.getConfig().getDouble("auto_spawn.y"),
                plugin.getConfig().getDouble("auto_spawn.z"));
    }
}
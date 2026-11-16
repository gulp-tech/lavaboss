package dev.musd348.lavaboss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BossCommand implements CommandExecutor, TabCompleter {

    private final LavaBoss plugin;

    public BossCommand(LavaBoss plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        boolean isDelay = args[0].equalsIgnoreCase("delay");
        if (!isDelay && !sender.hasPermission("lavaboss.admin")) {
            sender.sendMessage(fmt(sender,
                    plugin.getConfig().getString(
                            "messages.no_permission", "&d┠ &cНет прав.")));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(fmt(sender, "&d┠ &cТолько для игроков."));
                    return true;
                }
                Location l = player.getLocation();
                plugin.getConfig().set("auto_spawn.world", l.getWorld().getName());
                plugin.getConfig().set("auto_spawn.x", l.getX());
                plugin.getConfig().set("auto_spawn.y", l.getY());
                plugin.getConfig().set("auto_spawn.z", l.getZ());
                plugin.saveConfig();
                plugin.reloadAutoSpawn();
                player.sendMessage(fmt(player,
                        "&d┠ &aТочка спавна боссов установлена!"));
            }

            case "spawn" -> {
                if (args.length < 2) {
                    sender.sendMessage(fmt(sender,
                            "&d┠ &cИспользование: /boss spawn <тип>"));
                    return true;
                }
                BossType type = BossType.fromString(args[1]);
                if (type == null) {
                    sender.sendMessage(fmt(sender,
                            plugin.getConfig().getString(
                                    "messages.invalid_boss", "&d┠ &cНеверный босс.")));
                    return true;
                }
                Location loc = getSpawnLocation();
                if (loc == null && sender instanceof Player p)
                    loc = p.getLocation();
                if (loc == null) {
                    sender.sendMessage(fmt(sender,
                            "&d┠ &cТочка спавна не установлена."));
                    return true;
                }
                Boss boss = plugin.getBossManager().spawnBoss(type, loc);
                sender.sendMessage(fmt(sender, boss != null
                        ? plugin.getConfig().getString(
                        "messages.boss_spawned", "&d┠ &aУспешно призван!")
                        : plugin.getConfig().getString(
                        "messages.boss_already_alive",
                        "&d┠ &cУже призван!")));
            }

            case "random" -> {
                Location loc = getSpawnLocation();
                if (loc == null && sender instanceof Player p) loc = p.getLocation();
                if (loc == null) {
                    sender.sendMessage(fmt(sender,
                            "&d┠ &cТочка спавна не установлена."));
                    return true;
                }
                Boss boss = plugin.getBossManager().spawnRandomBoss(loc);
                sender.sendMessage(fmt(sender, boss != null
                        ? "&d┠ &aСлучайный успешно призван!"
                        : plugin.getConfig().getString(
                        "messages.boss_already_alive",
                        "&d┠ &cУже призван!")));
            }

            case "items" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(fmt(sender, "&d┠ &cТолько для игроков."));
                    return true;
                }
                player.getInventory().addItem(
                        plugin.getItemManager().getAltarBlock(1));
                player.getInventory().addItem(
                        plugin.getItemManager().getSealItem(3));
                player.sendMessage(fmt(player,
                        plugin.getConfig().getString(
                                "messages.items_given", "&d┠ &aПредметы выданы.")));
            }

            case "reload" -> {
                plugin.reloadConfig();
                plugin.reloadAutoSpawn();
                sender.sendMessage(fmt(sender,
                        plugin.getConfig().getString(
                                "messages.config_reloaded",
                                "&d┠ &aПерезагружено.")));
            }

            case "list" -> {
                var bosses = plugin.getBossManager().getActiveBosses();
                if (bosses.isEmpty()) {
                    sender.sendMessage(fmt(sender,
                            plugin.getConfig().getString(
                                    "messages.no_active_bosses",
                                    "&d┠ &eАктивных нет.")));
                } else {
                    sender.sendMessage(fmt(sender,
                            plugin.getConfig().getString(
                                    "messages.active_bosses",
                                    "&d┠ &aЖивые:")));
                    for (Boss b : bosses) {
                        sender.sendMessage(
                                ColorUtils.colorize("  &7- " + b.getName()));
                    }
                }
            }

            case "remove" -> {
                plugin.getBossManager().removeAllBosses();
                sender.sendMessage(fmt(sender,
                        plugin.getConfig().getString(
                                "messages.boss_removed",
                                "&d┠ &aВсе боссы удалены.")));
            }

            case "delay" -> {
                if (!plugin.isAutoSpawnRunning()) {
                    sender.sendMessage(fmt(sender,
                            plugin.getConfig().getString(
                                    "messages.delay_not_running",
                                    "&d┠ &cАвто-спавн выключен.")));
                    return true;
                }
                long ticks    = plugin.getTicksUntilNextSpawn();
                long totalSec = ticks / 20;
                long h = totalSec / 3600;
                long m = (totalSec % 3600) / 60;
                long s = totalSec % 60;
                String time = h > 0 ? String.format("%dч %dм %dс", h, m, s)
                        : m > 0   ? String.format("%dм %dс", m, s)
                        :           String.format("%dс", s);
                String msg = plugin.getConfig()
                        .getString("messages.delay_info",
                                "&d┠ &aДо спавна: &#E0C912%time%")
                        .replace("%time%", time);
                sender.sendMessage(fmt(sender, msg));
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private net.kyori.adventure.text.Component fmt(CommandSender sender, String msg) {
        if (sender instanceof Player p)
            return ColorUtils.formatFor(p, msg);
        return ColorUtils.format(msg);
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(fmt(sender, "&#6253BF═══ Управление ═══"));
        String[][] cmds = {
                {"/boss setspawn",       "Точка спавна"},
                {"/boss spawn <type>",   "Призвать"},
                {"/boss random",         "Случайный"},
                {"/boss items",          "Выдать алтарь"},
                {"/boss list",           "Список"},
                {"/boss remove",         "Удалить"},
                {"/boss delay",          "Время до спавна"},
                {"/boss reload",         "Перезагрузка"},
        };
        for (String[] c : cmds) {
            sender.sendMessage(fmt(sender, "&e" + c[0] + " &7- " + c[1]));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn", "random", "setspawn", "items",
                            "list", "remove", "reload", "delay")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return Arrays.stream(BossType.values())
                    .map(BossType::getConfigKey)
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
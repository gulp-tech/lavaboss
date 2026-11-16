package dev.musd348.lavaboss;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private final LavaBoss plugin;
    public final NamespacedKey sealKey;
    public final NamespacedKey altarBlockKey;

    public ItemManager(LavaBoss plugin) {
        this.plugin = plugin;
        this.sealKey = new NamespacedKey(plugin, "ancient_seal");
        this.altarBlockKey = new NamespacedKey(plugin, "custom_altar_block");
    }

    public ItemStack getSealItem(int amount) {
        String matName = plugin.getConfig().getString("altar.seal_material", "NETHER_STAR");
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.NETHER_STAR;

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getConfig().getString("altar.seal_name", "&eДревняя Печать");
        meta.displayName(ColorUtils.colorize(name));

        List<String> loreConfig = plugin.getConfig().getStringList("altar.seal_lore");
        List<Component> lore = new ArrayList<>();
        for (String line : loreConfig) {
            lore.add(ColorUtils.colorize(line));
        }
        meta.lore(lore);

        meta.getPersistentDataContainer().set(sealKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getAltarBlock(int amount) {
        String matName = plugin.getConfig().getString("altar.block_material", "END_PORTAL_FRAME");
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.END_PORTAL_FRAME;

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(ColorUtils.colorize("&#55FF55М&#4AEE4Aа&#40DD40г&#35CC35и&#2BBB2Bч&#20AA20е&#159915с&#0A880Aк&#007700и&#008800й &#009900А&#00AA00л&#00BB00т&#00CC00а&#00DD00р&#00EE00ь"));

        List<Component> lore = new ArrayList<>();
        lore.add(ColorUtils.colorize("&7Поставьте на землю."));
        lore.add(ColorUtils.colorize("&7Используйте &#00FFFF3 Древних Печати &7для призыва."));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(altarBlockKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSeal(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(sealKey, PersistentDataType.STRING);
    }

    public boolean isAltarItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(altarBlockKey, PersistentDataType.STRING);
    }
}
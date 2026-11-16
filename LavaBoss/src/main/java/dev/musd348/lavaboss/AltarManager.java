package dev.musd348.lavaboss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AltarManager {

    private final LavaBoss plugin;
    private final Set<Location> altarLocations = new HashSet<>();
    private final File file;
    private final YamlConfiguration config;

    public AltarManager(LavaBoss plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "altars.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadAltars();
    }

    public void addAltar(Location loc) {
        altarLocations.add(loc);
        saveAltars();
    }

    public void removeAltar(Location loc) {
        altarLocations.remove(loc);
        saveAltars();
    }

    public boolean isAltar(Location loc) {
        return altarLocations.contains(loc);
    }

    public Set<Location> getAltars() {
        return altarLocations;
    }

    private void saveAltars() {
        List<String> locs = new ArrayList<>();
        for (Location loc : altarLocations) {
            if (loc.getWorld() != null) {
                locs.add(loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ());
            }
        }
        config.set("locations", locs);
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadAltars() {
        List<String> locs = config.getStringList("locations");
        for (String s : locs) {
            String[] parts = s.split(";");
            if (parts.length == 4) {
                World w = Bukkit.getWorld(parts[0]);
                if (w != null) {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    altarLocations.add(new Location(w, x, y, z));
                }
            }
        }
    }
}
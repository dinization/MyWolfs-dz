package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WolfDataManager {

    private static File file;
    private static FileConfiguration config;

    public static void setup() {
        file = new File(MyWolfs.getInstance().getDataFolder(), "wolves.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                MyWolfs.getInstance().getLogger().severe("Could not create wolves.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static void saveWolf(Wolf wolf, Player owner) {
        String uuid = wolf.getUniqueId().toString();
        String path = "wolves." + uuid;

        var pdc = wolf.getPersistentDataContainer();

        // Fetch all data from PDC
        String name = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
        int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        int xp = pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
        String rankHealth = pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "C");
        String rankDamage = pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "C");
        String rankSpeed = pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "C");
        String rankArmor = pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "C");

        // Save to YAML
        config.set(path + ".owner", owner.getUniqueId().toString());
        config.set(path + ".name", name);
        config.set(path + ".level", level);
        config.set(path + ".xp", xp);
        config.set(path + ".ranks.health", rankHealth);
        config.set(path + ".ranks.damage", rankDamage);
        config.set(path + ".ranks.speed", rankSpeed);
        config.set(path + ".ranks.armor", rankArmor);

        updateLocation(wolf, path);
        saveFile();
    }

    public static void updateWolf(Wolf wolf) {
        String uuid = wolf.getUniqueId().toString();
        String path = "wolves." + uuid;

        if (!config.contains(path)) {
            Player owner = (Player) wolf.getOwner();
            if (owner != null) saveWolf(wolf, owner);
            return;
        }

        var pdc = wolf.getPersistentDataContainer();

        // Update ALL data from PDC, including the name and ranks!
        config.set(path + ".name", pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf"));
        config.set(path + ".level", pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1));
        config.set(path + ".xp", pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0));
        config.set(path + ".ranks.health", pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "C"));
        config.set(path + ".ranks.damage", pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "C"));
        config.set(path + ".ranks.speed", pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "C"));
        config.set(path + ".ranks.armor", pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "C"));

        updateLocation(wolf, path);
        saveFile();
    }

    // Helper method to save chunk coordinates
    private static void updateLocation(Wolf wolf, String path) {
        config.set(path + ".world", wolf.getWorld().getName());
        // We save the chunk coordinates instead of exact X, Y, Z
        config.set(path + ".chunk_x", wolf.getLocation().getChunk().getX());
        config.set(path + ".chunk_z", wolf.getLocation().getChunk().getZ());
    }

    // Delete the wolf from the database
    public static void deleteWolf(Wolf wolf) {
        String uuid = wolf.getUniqueId().toString();
        String path = "wolves." + uuid;

        if (config.contains(path)) {
            config.set(path, null); // Setting to null deletes the section
            saveFile();
        }
    }

    // --- AUTOSAVE LOGIC ---

    // Gathers all loaded wolves safely on the main thread
    public static void saveAllWolvesSync() {
        for (World world : Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed()) {
                    // Update memory only (no disk I/O yet)
                    updateWolfNoSave(wolf);
                }
            }
        }
        // After updating all wolves in memory, trigger the async save once
        saveFileAsync();
    }

    // Helper method to update memory without saving to disk
    private static void updateWolfNoSave(Wolf wolf) {
        String uuid = wolf.getUniqueId().toString();
        String path = "wolves." + uuid;

        if (!config.contains(path)) {
            Player owner = (Player) wolf.getOwner();
            if (owner != null) saveWolf(wolf, owner); // Rare case, still saves
            return;
        }

        var pdc = wolf.getPersistentDataContainer();
        config.set(path + ".level", pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1));
        config.set(path + ".xp", pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0));
        updateLocation(wolf, path);
    }

    // Saves the file asynchronously to prevent server lag
    public static void saveFileAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(MyWolfs.getInstance(), () -> {
            // Synchronized block prevents two threads from writing to the file at the same time
            synchronized (config) {
                try {
                    config.save(file);
                } catch (IOException e) {
                    MyWolfs.getInstance().getLogger().severe("Could not save wolves.yml asynchronously!");
                }
            }
        });
    }

    // --- UTILS ---

    public static void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            MyWolfs.getInstance().getLogger().severe("Could not save wolves.yml!");
        }
    }

    public static FileConfiguration getConfig() {
        return config;
    }

    // Get a list of paths for all wolves owned by a player
    public static List<String> getPlayerWolves(Player player) {
        List<String> paths = new ArrayList<>();
        if (config.getConfigurationSection("wolves") == null) return paths;

        for (String key : config.getConfigurationSection("wolves").getKeys(false)) {
            String path = "wolves." + key;
            if (player.getUniqueId().toString().equals(config.getString(path + ".owner"))) {
                paths.add(path);
            }
        }
        return paths;
    }

    // Count how many RPG wolves (level > 0) a player has in the database
    public static int getRPGWolfCount(Player player) {
        int count = 0;
        if (config.getConfigurationSection("wolves") == null) return 0;

        for (String key : config.getConfigurationSection("wolves").getKeys(false)) {
            String path = "wolves." + key;
            if (player.getUniqueId().toString().equals(config.getString(path + ".owner"))) {
                int level = config.getInt(path + ".level", 1);
                if (level > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    // Check if a specific name is already taken by the player in the database
    public static boolean isNameTakenInDB(Player player, String name) {
        if (config.getConfigurationSection("wolves") == null) return false;

        for (String key : config.getConfigurationSection("wolves").getKeys(false)) {
            String path = "wolves." + key;
            if (player.getUniqueId().toString().equals(config.getString(path + ".owner"))) {
                String wolfName = config.getString(path + ".name", "");
                if (wolfName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Saves all wolves synchronously (used only when server is shutting down)
    public static void saveAllWolvesOnShutdown() {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed()) {
                    updateWolfNoSave(wolf);
                }
            }
        }
        // Save synchronously, not asynchronously
        try {
            config.save(file);
        } catch (IOException e) {
            MyWolfs.getInstance().getLogger().severe("Could not save wolves.yml on shutdown!");
        }
    }
}
package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FoxDataManager {

    private static File file;
    private static FileConfiguration config;

    //region SETUP & MIGRATION
    public static void setup() {
        file = new File(MyWolfs.getInstance().getDataFolder(), "foxes.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                MyWolfs.getInstance().getLogger().severe("Could not create wolves.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }
    //endregion

    //region SAVE & UPDATE
    public static void saveFox(Fox fox, Player owner) {
        String uuid = fox.getUniqueId().toString();
        String path = "foxes." + uuid;

        String name = fox.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_FOX_NAME, PersistentDataType.STRING, "Fox");

        // Save owner to PDC
        fox.getPersistentDataContainer().set(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        // Save to YAML
        config.set(path + ".owner", owner.getUniqueId().toString());
        updateLocation(fox, path);
        config.set(path + ".name", name);
        saveFile();
    }

    public static void updateFox(Fox fox) {
        String uuid = fox.getUniqueId().toString();
        String path = "foxes." + uuid;

        if (!config.contains(path)) return;

        String name = fox.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_FOX_NAME, PersistentDataType.STRING, "Fox");
        config.set(path + ".name", name);

        updateLocation(fox, path);
        saveFile();
    }

    private static void updateLocation(Fox fox, String path) {
        config.set(path + ".world", fox.getWorld().getName());
        config.set(path + ".chunk_x", fox.getLocation().getChunk().getX());
        config.set(path + ".chunk_z", fox.getLocation().getChunk().getZ());
    }
    //endregion

    //region DELETE
    public static void deleteFox(Fox fox) {
        String path = "foxes." + fox.getUniqueId().toString();
        if (config.contains(path)) {
            config.set(path, null);
            saveFile();
        }
    }

    public static void deleteFoxById(UUID foxUUID) {
        String path = "foxes." + foxUUID.toString();
        YamlConfiguration freshConfig = YamlConfiguration.loadConfiguration(file);

        if (freshConfig.contains(path)) {
            freshConfig.set(path, null);
            try {
                freshConfig.save(file);
                config = freshConfig;
            } catch (IOException e) {
                MyWolfs.getInstance().getLogger().severe("Error saving foxes.yml during deletion!");
            }
        }
    }
    //endregion

    //region FILE I/O
    public static void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            MyWolfs.getInstance().getLogger().severe("Could not save foxes.yml!");
        }
    }

    public static FileConfiguration getConfig() {
        return config;
    }
    //endregion

    //region DATABASE QUERIES
    public static List<String> getPlayerFoxes(Player player) {
        List<String> paths = new ArrayList<>();
        if (config.getConfigurationSection("foxes") == null) return paths;

        for (String key : config.getConfigurationSection("foxes").getKeys(false)) {
            String path = "foxes." + key;
            if (player.getUniqueId().toString().equals(config.getString(path + ".owner"))) {
                paths.add(path);
            }
        }
        return paths;
    }

    // Count how many foxes a player has in the database
    public static int getFoxCount(Player player) {
        int count = 0;
        if (config.getConfigurationSection("foxes") == null) return 0;

        for (String key : config.getConfigurationSection("foxes").getKeys(false)) {
            String path = "foxes." + key;
            if (player.getUniqueId().toString().equals(config.getString(path + ".owner"))) {
                count++;
            }
        }
        return count;
    }

    // Saves all foxes to DB
    public static void saveAllFoxesSync() {
        for (World world : Bukkit.getWorlds()) {
            for (Fox fox : world.getEntitiesByClass(Fox.class)) {
                String owner = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
                if (owner != null && !owner.isEmpty()) {
                    updateFox(fox);
                }
            }
        }
    }
    //endregion
}
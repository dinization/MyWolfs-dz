package me.diniz.myWolfs;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class DataMigrationManager {

    public static void runAllMigrations(MyWolfs plugin) {
        String currentVersion = plugin.getPluginMeta().getVersion();

        // 1. Migrate Config
        migrateConfig(plugin, currentVersion);

        // 2. Migrate Wolves DB (Only updates version number for now)
        migrateDatabase(plugin, "wolves.yml", currentVersion);

        // 3. Migrate Foxes DB (Only updates version number for now)
        migrateDatabase(plugin, "foxes.yml", currentVersion);
    }

    //region CONFIG MIGRATION
    private static void migrateConfig(MyWolfs plugin, String currentVersion) {

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        String configVersion = plugin.getConfig().getString("config_version", "1.0.0");
        if (configVersion.equals(currentVersion)) return;

        plugin.getLogger().info("Config version mismatch! Migrating from " + configVersion + " to " + currentVersion + "...");

        File oldConfigFile = new File(plugin.getDataFolder(), "config_old.yml");

        if (configFile.exists()) {
            if (oldConfigFile.exists()) oldConfigFile.delete();
            configFile.renameTo(oldConfigFile);
        }

        // Generate new config
        plugin.saveDefaultConfig();

        // Load both configs
        YamlConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile);
        FileConfiguration newConfig = plugin.getConfig();

        // Copy ALL keys from old config to new config, EXCEPT "messages" and "config_version"
        for (String key : oldConfig.getKeys(true)) {
            // Skip the messages section so new messages take effect
            if (key.startsWith("messages.")) continue;
            if (key.equals("config_version")) continue;

            // If the key is a leaf (not a section), we copy it
            if (!oldConfig.isConfigurationSection(key)) {
                newConfig.set(key, oldConfig.get(key));
            }
        }

        try {
            newConfig.save(configFile);
            oldConfigFile.delete();
            plugin.getLogger().info("Config migration complete!");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save the migrated config.yml!");
        }
    }
    //endregion

    //region DATABASE MIGRATION (Wolves & Foxes)
    private static void migrateDatabase(MyWolfs plugin, String fileName, String currentVersion) {
        File file = new File(plugin.getDataFolder(), fileName);
        // If the file doesn't exist, copy it from the .jar and stop here (nothing to migrate)
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String dbVersion = config.getString("database_version", "1.0.0");

        if (!dbVersion.equals(currentVersion)) {
            plugin.getLogger().info(fileName + " version mismatch! Migrating from " + dbVersion + " to " + currentVersion + "...");

            // FUTURE DATABASE MIGRATIONS GO HERE

            // Update version
            config.set("database_version", currentVersion);
            try {
                config.save(file);
                plugin.getLogger().info(fileName + " migration complete!");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + fileName + " during migration!");
            }
        }
    }
    //endregion
}
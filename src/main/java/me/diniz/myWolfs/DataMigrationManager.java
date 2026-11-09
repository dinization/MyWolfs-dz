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

        // Generate the new config file from the .jar (it has all the new comments and structure)
        plugin.saveDefaultConfig();

        // Copy the values from the old file to the new file manually
        try {
            java.util.List<String> oldLines = java.nio.file.Files.readAllLines(oldConfigFile.toPath());
            java.util.List<String> newLines = java.nio.file.Files.readAllLines(configFile.toPath());

            // Create a map of key -> value from the old file
            java.util.Map<String, String> oldValues = new java.util.HashMap<>();
            for (String line : oldLines) {
                line = line.trim();
                // Ignore comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Parse YAML lines (key: value)
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    // Ensure we only get the leaf nodes (not sections like 'settings:')
                    if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                        oldValues.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            // Reconstruct the new file
            java.util.List<String> finalLines = new java.util.ArrayList<>();
            boolean insideMessagesSection = false;

            for (String line : newLines) {
                String trimmed = line.trim();

                // 1. Keep comments and empty lines as is
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    finalLines.add(line);
                    continue;
                }

                // 2. Track if we are inside the messages section
                if (trimmed.startsWith("messages:")) {
                    insideMessagesSection = true;
                    finalLines.add(line);
                    continue;
                }
                // If we hit another top-level section, we are no longer in messages
                else if (!line.startsWith(" ") && !line.startsWith("\t") && trimmed.contains(":")) {
                    insideMessagesSection = false;
                }

                // 3. Handle key: value lines
                if (trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    String key = parts[0].trim();

                    // If we are inside messages, we ALWAYS want the new value from the .jar.
                    // For other sections, we copy the old value if it exists.
                    if (!insideMessagesSection && oldValues.containsKey(key) && !key.equals("config_version")) {
                        String indentation = line.substring(0, line.indexOf(key));
                        finalLines.add(indentation + key + ": " + oldValues.get(key));
                    } else {
                        // Keep the new default value (which includes all new messages)
                        finalLines.add(line);
                    }
                } else {
                    finalLines.add(line);
                }
            }

            // Write the final lines back to the config
            java.nio.file.Files.write(configFile.toPath(), finalLines);

            // Delete the old file
            oldConfigFile.delete();

            // Reload the config to load the new values into memory
            plugin.reloadConfig();

            plugin.getLogger().info("Config migration complete! Comments and structure preserved.");

        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not migrate config.yml!");
            e.printStackTrace();
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
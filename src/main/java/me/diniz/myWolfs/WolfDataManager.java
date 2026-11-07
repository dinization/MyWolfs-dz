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
import java.util.UUID;

public class WolfDataManager {

    private static File file;
    private static FileConfiguration config;

    public static class TopWolf {
        public final String name;
        public final String owner;
        public final String value;

        public TopWolf(String name, String owner, String value) {
            this.name = name;
            this.owner = owner;
            this.value = value;
        }
    }

    public static class TopList {
        public final List<TopWolf> wolves = new ArrayList<>();
    }

    //region SETUP & MIGRATION
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
    //endregion

    //region SAVE & UPDATE WOLVES
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
        config.set(path + ".kills", pdc.getOrDefault(MyWolfs.KEY_KILLS, PersistentDataType.INTEGER, 0));
        config.set(path + ".upgrade_item", pdc.getOrDefault(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, ""));

        // Save calculated stats to DB for quick access
        var attrHealth = wolf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        var attrDamage = wolf.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        var attrSpeed = wolf.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        var attrArmor = wolf.getAttribute(org.bukkit.attribute.Attribute.ARMOR);

        config.set(path + ".stats.health", attrHealth != null ? attrHealth.getValue() : 20.0);
        config.set(path + ".stats.damage", attrDamage != null ? attrDamage.getValue() : 4.0);
        config.set(path + ".stats.speed", attrSpeed != null ? attrSpeed.getValue() : 0.3);
        config.set(path + ".stats.armor", attrArmor != null ? attrArmor.getValue() : 0.0);

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
        config.set(path + ".kills", pdc.getOrDefault(MyWolfs.KEY_KILLS, PersistentDataType.INTEGER, 0));
        config.set(path + ".upgrade_item", pdc.getOrDefault(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, ""));

        // Save calculated stats to DB for quick access
        var attrHealth = wolf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        var attrDamage = wolf.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        var attrSpeed = wolf.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        var attrArmor = wolf.getAttribute(org.bukkit.attribute.Attribute.ARMOR);

        config.set(path + ".stats.health", attrHealth != null ? attrHealth.getValue() : 20.0);
        config.set(path + ".stats.damage", attrDamage != null ? attrDamage.getValue() : 4.0);
        config.set(path + ".stats.speed", attrSpeed != null ? attrSpeed.getValue() : 0.3);
        config.set(path + ".stats.armor", attrArmor != null ? attrArmor.getValue() : 0.0);

        updateLocation(wolf, path);
        saveFile();
    }

    // Helper method to save chunk coordinates
    private static void updateLocation(Wolf wolf, String path) {
        config.set(path + ".world", wolf.getWorld().getName());
        config.set(path + ".chunk_x", wolf.getLocation().getChunk().getX());
        config.set(path + ".chunk_z", wolf.getLocation().getChunk().getZ());
    }
    //endregion

    //region DELETE WOLVES
    public static void deleteWolf(Wolf wolf) {
        String uuid = wolf.getUniqueId().toString();
        String path = "wolves." + uuid;

        if (config.contains(path)) {
            config.set(path, null);
            saveFile();
        }
    }

    public static void deleteWolfById(UUID wolfUUID) {
        String path = "wolves." + wolfUUID.toString();

        // Load a FRESH copy of the file directly from disk
        YamlConfiguration freshConfig = YamlConfiguration.loadConfiguration(file);

        if (freshConfig.contains(path)) {
            freshConfig.set(path, null);
            try {
                freshConfig.save(file);
                config = freshConfig; // Update memory
            } catch (IOException e) {
                MyWolfs.getInstance().getLogger().severe("Error saving file during deletion!");
            }
        }
    }
    //endregion

    //region AUTOSAVE & SHUTDOWN
    public static void saveAllWolvesSync() {
        for (World world : Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed()) {
                    updateWolfNoSave(wolf);
                }
            }
        }
        saveFileAsync();
    }

    private static void updateWolfNoSave(Wolf wolf) {
        String uuid = wolf.getUniqueId().toString();
        String path = "wolves." + uuid;

        if (!config.contains(path)) {
            Player owner = (Player) wolf.getOwner();
            if (owner != null) saveWolf(wolf, owner);
            return;
        }

        var pdc = wolf.getPersistentDataContainer();

        // Save Level, XP, and KILLS
        config.set(path + ".level", pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1));
        config.set(path + ".xp", pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0));
        config.set(path + ".kills", pdc.getOrDefault(MyWolfs.KEY_KILLS, PersistentDataType.INTEGER, 0)); // <-- AQUI

        // Save calculated stats for the Scoreboard
        var attrHealth = wolf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        var attrDamage = wolf.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        var attrSpeed = wolf.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        var attrArmor = wolf.getAttribute(org.bukkit.attribute.Attribute.ARMOR);

        config.set(path + ".stats.health", attrHealth != null ? attrHealth.getValue() : 20.0);
        config.set(path + ".stats.damage", attrDamage != null ? attrDamage.getValue() : 4.0);
        config.set(path + ".stats.speed", attrSpeed != null ? attrSpeed.getValue() : 0.3);
        config.set(path + ".stats.armor", attrArmor != null ? attrArmor.getValue() : 0.0);

        updateLocation(wolf, path);
    }

    public static void saveAllWolvesOnShutdown() {
        for (World world : Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed()) {
                    // Use the full update to ensure stats are saved too
                    updateWolf(wolf);
                }
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            MyWolfs.getInstance().getLogger().severe("Could not save wolves.yml on shutdown!");
        }
    }

    public static void saveFileAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(MyWolfs.getInstance(), () -> {
            synchronized (config) {
                try {
                    config.save(file);
                } catch (IOException e) {
                    MyWolfs.getInstance().getLogger().severe("Could not save wolves.yml asynchronously!");
                }
            }
        });
    }

    public static void saveFile() {
        try {
            config.save(file);
        } catch (IOException e) {
            MyWolfs.getInstance().getLogger().severe("Could not save wolves.yml!");
        }
    }
    //endregion

    //region DATABASE QUERIES

    public static TopList getTopWolves(String category) {
        TopList topList = new TopList();
        List<Double> values = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> owners = new ArrayList<>();

        if (config.getConfigurationSection("wolves") == null) return topList;

        for (String key : config.getConfigurationSection("wolves").getKeys(false)) {
            String path = "wolves." + key;
            String name = config.getString(path + ".name", "Unknown");
            String ownerUUID = config.getString(path + ".owner", "");

            String ownerName = "Offline";
            if (ownerUUID != null && !ownerUUID.isEmpty()) {
                org.bukkit.OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID));
                if (p.getName() != null) ownerName = p.getName();
            }

            double value = 0;
            if (category.equalsIgnoreCase("level")) value = config.getInt(path + ".level", 1);
            else if (category.equalsIgnoreCase("kills")) value = config.getInt(path + ".kills", 0);
            else if (category.equalsIgnoreCase("damage")) value = config.getDouble(path + ".stats.damage", 4.0);

            values.add(value);
            names.add(name);
            owners.add(ownerName);
        }

        // Sort by highest value
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) indices.add(i);
        indices.sort((a, b) -> Double.compare(values.get(b), values.get(a)));

        // Get top 3
        for (int i = 0; i < Math.min(3, indices.size()); i++) {
            int idx = indices.get(i);
            double val = values.get(idx);

            // Format the value based on category
            String formattedValue = String.valueOf((int) val);
            if (category.equalsIgnoreCase("damage")) formattedValue = String.format("%.2f", val);

            topList.wolves.add(new TopWolf(names.get(idx), owners.get(idx), formattedValue));
        }

        return topList;
    }

    public static FileConfiguration getConfig() {
        return config;
    }

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
    //endregion
}
package me.diniz.myWolfs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyWolfs extends JavaPlugin {

    // Keys
    public static NamespacedKey KEY_LEVEL;
    public static NamespacedKey KEY_XP;
    public static NamespacedKey KEY_RANK_HEALTH;
    public static NamespacedKey KEY_RANK_DAMAGE;
    public static NamespacedKey KEY_RANK_SPEED;
    public static NamespacedKey KEY_RANK_ARMOR;
    public static NamespacedKey KEY_LAST_ATTACKER;
    public static NamespacedKey KEY_WOLF_NAME;
    public static NamespacedKey KEY_IS_BABY;
    public static NamespacedKey KEY_UPGRADE_ITEM;

    private static MyWolfs instance;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;

        // Creates config.yml
        saveDefaultConfig();
        // Creates wolves.yml
        saveResource("wolves.yml", false);

        //region MIGRATION LOGIC
        String currentVersion = getPluginMeta().getVersion();
        String configVersion = getConfig().getString("config_version", "1.0.0");

        if (!configVersion.equals(currentVersion)) {
            getLogger().info("Config version mismatch! Updating from " + configVersion + " to " + currentVersion + "...");
            getConfig().set("config_version", currentVersion);
            saveConfig();
        }

        //endregion

        WolfDataManager.setup();

        // Start the Mode Task (Aggressive Mode check)
        WolfModeManager.startTask(this);

        // Initializes keys
        KEY_WOLF_NAME = new NamespacedKey(this, "wolf_name");
        KEY_LEVEL = new NamespacedKey(this, "wolf_level");
        KEY_XP = new NamespacedKey(this, "wolf_xp");
        KEY_RANK_HEALTH = new NamespacedKey(this, "rank_health");
        KEY_RANK_DAMAGE = new NamespacedKey(this, "rank_damage");
        KEY_RANK_SPEED = new NamespacedKey(this, "rank_speed");
        KEY_RANK_ARMOR = new NamespacedKey(this, "rank_armor");
        KEY_LAST_ATTACKER = new NamespacedKey(this, "last_attacker");
        KEY_IS_BABY = new NamespacedKey(this, "is_baby");
        KEY_UPGRADE_ITEM = new NamespacedKey(this, "upgrade_item");

        // Listeners Reg
        getServer().getPluginManager().registerEvents(new DomesticationListener(this), this);
        getServer().getPluginManager().registerEvents(new LevelManager(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new BreedingListener(), this);
        getServer().getPluginManager().registerEvents(new WolfSafetyListener(), this);
        getServer().getPluginManager().registerEvents(new UpgradeListener(), this);

        // Command Reg
        this.getCommand("wolfs").setExecutor(new WolfsCommand());

        // --- AUTOSAVE SCHEDULER ---
        int intervalMinutes = getConfig().getInt("settings.autosave_interval_minutes", 5);
        long ticks = intervalMinutes * 60L * 20L; // Convert minutes to ticks (20 ticks = 1 second)

        // Run on the main thread to safely read entities,
        // but the actual file saving is handled asynchronously inside the manager.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            WolfDataManager.saveAllWolvesSync();
        }, ticks, ticks);

        getLogger().info("Autosave scheduled every " + intervalMinutes + " minutes.");
        getLogger().info("MyWolfs enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Save all wolves synchronously before the server shuts down
        WolfDataManager.saveAllWolvesOnShutdown();

        getLogger().info("MyWolfs disabled.");
    }

    // Method for other classes to access main instance
    public static MyWolfs getInstance() {
        return instance;
    }

    // Method for reading messages from config.yml with support for colors from minimessage
    public void sendConfigMessage(CommandSender sender, String path, String... replacements) {
        String message = getConfig().getString("messages." + path, "Message not found.");
        if (message == null) return;

        // overwrite variables like %level% for real values
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        sender.sendMessage(miniMessage.deserialize(message));
    }
}
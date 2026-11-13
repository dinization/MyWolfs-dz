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
    public static NamespacedKey KEY_KILLS;
    public static NamespacedKey KEY_RANK_HEALTH;
    public static NamespacedKey KEY_RANK_DAMAGE;
    public static NamespacedKey KEY_RANK_SPEED;
    public static NamespacedKey KEY_RANK_ARMOR;
    public static NamespacedKey KEY_LAST_ATTACKER;
    public static NamespacedKey KEY_WOLF_NAME;
    public static NamespacedKey KEY_IS_BABY;
    public static NamespacedKey KEY_UPGRADE_ITEM;
    public static NamespacedKey KEY_FOX_OWNER;
    public static NamespacedKey KEY_FOX_NAME;
    public static NamespacedKey KEY_STAT_HEALTH_MULT;
    public static NamespacedKey KEY_STAT_DAMAGE_MULT;
    public static NamespacedKey KEY_STAT_SPEED_MULT;
    public static NamespacedKey KEY_STAT_ARMOR_FLAT;


    private static MyWolfs instance;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;

        // Run data migrations.
        // This handles creating the default config, wolves.yml, and foxes.yml if they are missing,
        // and also updates them to the new version format if needed.
        DataMigrationManager.runAllMigrations(this);

        // Setup the data managers (loads files into memory)
        WolfDataManager.setup();
        FoxDataManager.setup();
        // Start the Mode Task (Aggressive Mode check)
        WolfModeManager.startTask(this);
        // Periodic cleanup of stale post-duel aggro locks
        DuelManager.startAggroLockCleanupTask();

        UpdateChecker updateChecker = new UpdateChecker(this, "mywolfs");

        // Initializes keys
        KEY_WOLF_NAME = new NamespacedKey(this, "wolf_name");
        KEY_LEVEL = new NamespacedKey(this, "wolf_level");
        KEY_XP = new NamespacedKey(this, "wolf_xp");
        KEY_KILLS = new NamespacedKey(this, "wolf_kills");
        KEY_RANK_HEALTH = new NamespacedKey(this, "rank_health");
        KEY_RANK_DAMAGE = new NamespacedKey(this, "rank_damage");
        KEY_RANK_SPEED = new NamespacedKey(this, "rank_speed");
        KEY_RANK_ARMOR = new NamespacedKey(this, "rank_armor");
        KEY_LAST_ATTACKER = new NamespacedKey(this, "last_attacker");
        KEY_IS_BABY = new NamespacedKey(this, "is_baby");
        KEY_UPGRADE_ITEM = new NamespacedKey(this, "upgrade_item");
        KEY_FOX_OWNER = new NamespacedKey(this, "fox_owner");
        KEY_FOX_NAME = new NamespacedKey(this, "fox_name");
        KEY_STAT_HEALTH_MULT = new NamespacedKey(this, "stat_health_mult");
        KEY_STAT_DAMAGE_MULT = new NamespacedKey(this, "stat_damage_mult");
        KEY_STAT_SPEED_MULT = new NamespacedKey(this, "stat_speed_mult");
        KEY_STAT_ARMOR_FLAT = new NamespacedKey(this, "stat_armor_flat");


        // Listeners Reg
        getServer().getPluginManager().registerEvents(new DomesticationListener(), this);
        getServer().getPluginManager().registerEvents(new LevelManager(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new BreedingListener(), this);
        getServer().getPluginManager().registerEvents(new WolfSafetyListener(), this);
        getServer().getPluginManager().registerEvents(new UpgradeListener(), this);
        getServer().getPluginManager().registerEvents(new FoxListener(), this);
        getServer().getPluginManager().registerEvents(new DuelListener(), this);
        getServer().getPluginManager().registerEvents(updateChecker, this);

        // Command Reg
        this.getCommand("wolfs").setExecutor(new WolfsCommand());

        // --- AUTOSAVE SCHEDULER ---
        int intervalMinutes = getConfig().getInt("settings.autosave_interval_minutes", 5);
        long ticks = intervalMinutes * 60L * 20L; // Convert minutes to ticks (20 ticks = 1 second)

        // Run on the main thread to safely read entities,
        // but the actual file saving is handled asynchronously inside the manager.
        ScoreboardManager.updateCache();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            WolfDataManager.saveAllWolvesSync();
            FoxDataManager.saveAllFoxesSync();

            ScoreboardManager.updateCache();
        }, ticks, ticks);

        getLogger().info("Autosave scheduled every " + intervalMinutes + " minutes.");

        // Starts companion buffs tasks
        CompanionBuffManager.startAllTasks(this);

        updateChecker.checkForUpdates();

        getLogger().info("MyWolfs enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Save all wolves synchronously before the server shuts down
        WolfDataManager.saveAllWolvesOnShutdown();
        FoxDataManager.saveAllFoxesSync();
        FoxDataManager.saveFile();
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
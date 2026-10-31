package me.diniz.myWolfs;

import net.kyori.adventure.text.minimessage.MiniMessage;
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

    private static MyWolfs instance;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;

        // Creates config.yml
        saveDefaultConfig();

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

        // Listeners Reg
        getServer().getPluginManager().registerEvents(new DomesticationListener(this), this);
        getServer().getPluginManager().registerEvents(new LevelManager(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new BreedingListener(), this);

        // Command Reg
        this.getCommand("wolfs").setExecutor(new WolfsCommand());

        getLogger().info("MyWolfs enabled successfully!");
    }

    @Override
    public void onDisable() {
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
package me.diniz.myWolfs;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

public class WolfStatsManager {

    // Roll multipliers
    public static double getMultiplier(String rank) {
        return MyWolfs.getInstance().getConfig().getDouble("ranks." + rank, 1.0);
    }

    // Main method to apply stats
    public static void applyStats(Wolf wolf) {
        MyWolfs plugin = MyWolfs.getInstance();
        var pdc = wolf.getPersistentDataContainer();

        // Get Level and Ranks from PDC
        int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        String rankHealth = pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "C");
        String rankDamage = pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "C");
        String rankSpeed = pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "C");
        String rankArmor = pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "C");

        // Get base gains from config.yml
        double baseHealthGain = plugin.getConfig().getDouble("stats.health_per_level", 0.5);
        double baseDamageGain = plugin.getConfig().getDouble("stats.damage_per_level", 0.1);
        double baseSpeedGain = plugin.getConfig().getDouble("stats.speed_per_level", 0.01);
        double baseArmorGain = plugin.getConfig().getDouble("stats.armor_per_level", 0.4);

        // Calculate extra stats (Defaults to 0)
        double extraHealth = 0;
        double extraDamage = 0;
        double extraSpeed = 0;
        double extraArmor = 0;

        // Only calculate bonuses if the wolf is Level 1 or higher
        // Level 0 (babies) will have 0 extra stats (vanilla)
        if (level > 0) {
            extraHealth = (level - 1) * (baseHealthGain * getMultiplier(rankHealth));
            extraDamage = (level - 1) * (baseDamageGain * getMultiplier(rankDamage));
            extraSpeed = (level - 1) * (baseSpeedGain * getMultiplier(rankSpeed));
            extraArmor = (level - 1) * (baseArmorGain * getMultiplier(rankArmor));
        }

        // --- APPLY ATTRIBUTES ---

        // Health
        AttributeInstance attrHealth = wolf.getAttribute(Attribute.MAX_HEALTH);
        if (attrHealth != null) {
            // 1. Remove vanilla modifiers (the hidden +20 HP the game applies on taming)
            for (AttributeModifier modifier : new ArrayList<>(attrHealth.getModifiers())) {
                attrHealth.removeModifier(modifier);
            }

            // 2. Set the base value to 20 (vanilla tamed wolf) + our RPG bonus
            attrHealth.setBaseValue(20.0 + extraHealth);

            // 3. Heal the wolf to the new max health
            wolf.setHealth(attrHealth.getValue());
        }

        // Damage
        AttributeInstance attrDamage = wolf.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attrDamage != null) {
            // Set base damage (4) + bonus
            attrDamage.setBaseValue(4.0 + extraDamage);
        }

        // Speed
        AttributeInstance attrSpeed = wolf.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attrSpeed != null) {
            // Set base speed (0.3) + bonus
            attrSpeed.setBaseValue(0.3 + extraSpeed);
        }

        // Armor
        AttributeInstance attrArmor = wolf.getAttribute(Attribute.ARMOR);
        if (attrArmor != null) {
            // Wolves have no vanilla armor, so we start from 0
            attrArmor.setBaseValue(extraArmor);
        }
    }
}
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

        // Get individual stat multipliers (Fallback to 1.0 if the keys don't exist, e.g., old wolves)
        double healthMult = pdc.getOrDefault(MyWolfs.KEY_STAT_HEALTH_MULT, PersistentDataType.DOUBLE, 1.0);
        double damageMult = pdc.getOrDefault(MyWolfs.KEY_STAT_DAMAGE_MULT, PersistentDataType.DOUBLE, 1.0);
        double speedMult = pdc.getOrDefault(MyWolfs.KEY_STAT_SPEED_MULT, PersistentDataType.DOUBLE, 1.0);
        double armorFlat = pdc.getOrDefault(MyWolfs.KEY_STAT_ARMOR_FLAT, PersistentDataType.DOUBLE, 0.0);

        // Get base gains from config.yml
        double baseHealthGain = plugin.getConfig().getDouble("stats.health_per_level", 0.5);
        double baseDamageGain = plugin.getConfig().getDouble("stats.damage_per_level", 0.1);
        double baseSpeedGain = plugin.getConfig().getDouble("stats.speed_per_level", 0.01);
        double baseArmorGain = plugin.getConfig().getDouble("stats.armor_per_level", 0.4);

        // Calculate level bonuses (Defaults to 0)
        double levelHealthBonus = 0;
        double levelDamageBonus = 0;
        double levelSpeedBonus = 0;
        double levelArmorBonus = 0;

        // Only calculate bonuses if the wolf is Level 1 or higher
        // Level 0 (babies/over limit) will have 0 extra stats (vanilla)
        if (level > 0) {
            levelHealthBonus = (level - 1) * (baseHealthGain * getMultiplier(rankHealth));
            levelDamageBonus = (level - 1) * (baseDamageGain * getMultiplier(rankDamage));
            levelSpeedBonus = (level - 1) * (baseSpeedGain * getMultiplier(rankSpeed));
            levelArmorBonus = (level - 1) * (baseArmorGain * getMultiplier(rankArmor));
        }

        // --- APPLY ATTRIBUTES ---

        // Health
        AttributeInstance attrHealth = wolf.getAttribute(Attribute.MAX_HEALTH);
        if (attrHealth != null) {
            // 1. Remove vanilla modifiers (the hidden +20 HP the game applies on taming)
            for (AttributeModifier modifier : new ArrayList<>(attrHealth.getModifiers())) {
                attrHealth.removeModifier(modifier);
            }

            // 2. Calculate final value: (Base 20 * Mult) + Level Bonus
            double finalHealth = (20.0 * healthMult) + levelHealthBonus;

            // 3. Round to 2 decimal places to avoid floating point drift
            finalHealth = Math.round(finalHealth * 100.0) / 100.0;

            // 4. Set the base value and heal the wolf
            attrHealth.setBaseValue(finalHealth);
            wolf.setHealth(attrHealth.getValue());
        }

        // Damage
        AttributeInstance attrDamage = wolf.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attrDamage != null) {
            // Base 4 * Mult + Level Bonus
            double finalDamage = (4.0 * damageMult) + levelDamageBonus;

            // Round to 2 decimal places
            finalDamage = Math.round(finalDamage * 100.0) / 100.0;

            attrDamage.setBaseValue(finalDamage);
        }

        // Speed
        AttributeInstance attrSpeed = wolf.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attrSpeed != null) {
            // Base 0.3 * Mult + Level Bonus
            double finalSpeed = (0.3 * speedMult) + levelSpeedBonus;

            // Round to 3 decimal places (speed is more sensitive)
            finalSpeed = Math.round(finalSpeed * 1000.0) / 1000.0;

            attrSpeed.setBaseValue(finalSpeed);
        }

        // Armor
        AttributeInstance attrArmor = wolf.getAttribute(Attribute.ARMOR);
        if (attrArmor != null) {
            // Base 0 + Flat Armor + Level Bonus
            double finalArmor = armorFlat + levelArmorBonus;

            // Round to 2 decimal places
            finalArmor = Math.round(finalArmor * 100.0) / 100.0;

            attrArmor.setBaseValue(finalArmor);
        }
    }
}
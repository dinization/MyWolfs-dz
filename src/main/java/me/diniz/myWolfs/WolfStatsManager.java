package me.diniz.myWolfs;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

public class WolfStatsManager {

    public static double getMultiplier(String rank) {
        return MyWolfs.getInstance().getConfig().getDouble("ranks." + rank, 1.0);
    }

    public static void applyStats(Wolf wolf) {
        MyWolfs plugin = MyWolfs.getInstance();
        var pdc = wolf.getPersistentDataContainer();

        // 1. Get Level and Ranks from PDC
        int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        String rankHealth = pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "C");
        String rankDamage = pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "C");
        String rankSpeed = pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "C");
        String rankArmor = pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "C");

        // 2. Get RNG multipliers from PDC (defaults to 1.0 for old wolves)
        double rngHealth = pdc.getOrDefault(MyWolfs.KEY_STAT_HEALTH_MULT, PersistentDataType.DOUBLE, 1.0);
        double rngDamage = pdc.getOrDefault(MyWolfs.KEY_STAT_DAMAGE_MULT, PersistentDataType.DOUBLE, 1.0);
        double rngSpeed = pdc.getOrDefault(MyWolfs.KEY_STAT_SPEED_MULT, PersistentDataType.DOUBLE, 1.0);
        double rngArmor = pdc.getOrDefault(MyWolfs.KEY_STAT_ARMOR_FLAT, PersistentDataType.DOUBLE, 0.0);

        // 3. Get Variant Bonuses and Level Scales from config
        String variantKey = wolf.getVariant().getKey().getKey();
        double flatHealth = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".health_flat", 0.0);
        double flatDamage = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".damage_flat", 0.0);
        double flatSpeed = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".speed_flat", 0.0);
        double flatArmor = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".armor_flat", 0.0);

        double scaleHealth = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".health_level_scale", 1.0);
        double scaleDamage = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".damage_level_scale", 1.0);
        double scaleSpeed = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".speed_level_scale", 1.0);
        double scaleArmor = plugin.getConfig().getDouble("rpg_stats_system.variants." + variantKey + ".armor_level_scale", 1.0);

        // 4. Get Base Gains from config
        double baseHealthGain = plugin.getConfig().getDouble("stats.health_per_level", 0.5);
        double baseDamageGain = plugin.getConfig().getDouble("stats.damage_per_level", 0.1);
        double baseSpeedGain = plugin.getConfig().getDouble("stats.speed_per_level", 0.01);
        double baseArmorGain = plugin.getConfig().getDouble("stats.armor_per_level", 0.4);

        // 5. Calculate Level Bonuses
        double levelHealthBonus = 0, levelDamageBonus = 0, levelSpeedBonus = 0, levelArmorBonus = 0;
        if (level > 0) {
            levelHealthBonus = (level - 1) * (baseHealthGain * getMultiplier(rankHealth) * scaleHealth);
            levelDamageBonus = (level - 1) * (baseDamageGain * getMultiplier(rankDamage) * scaleDamage);
            levelSpeedBonus = (level - 1) * (baseSpeedGain * getMultiplier(rankSpeed) * scaleSpeed);
            levelArmorBonus = (level - 1) * (baseArmorGain * getMultiplier(rankArmor) * scaleArmor);
        }

        // --- APPLY ATTRIBUTES ---

        // Health
        AttributeInstance attrHealth = wolf.getAttribute(Attribute.MAX_HEALTH);
        if (attrHealth != null) {
            for (AttributeModifier modifier : new ArrayList<>(attrHealth.getModifiers())) {
                attrHealth.removeModifier(modifier);
            }
            double finalHealth = (20.0 * rngHealth) + flatHealth + levelHealthBonus;
            finalHealth = Math.round(finalHealth * 100.0) / 100.0;
            attrHealth.setBaseValue(finalHealth);
            wolf.setHealth(attrHealth.getValue());
        }

        // Damage
        AttributeInstance attrDamage = wolf.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attrDamage != null) {
            double finalDamage = (4.0 * rngDamage) + flatDamage + levelDamageBonus;
            finalDamage = Math.round(finalDamage * 100.0) / 100.0;
            attrDamage.setBaseValue(finalDamage);
        }

        // Speed
        AttributeInstance attrSpeed = wolf.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attrSpeed != null) {
            double finalSpeed = (0.3 * rngSpeed) + flatSpeed + levelSpeedBonus;
            finalSpeed = Math.round(finalSpeed * 1000.0) / 1000.0;
            attrSpeed.setBaseValue(finalSpeed);
        }

        // Armor
        AttributeInstance attrArmor = wolf.getAttribute(Attribute.ARMOR);
        if (attrArmor != null) {
            double finalArmor = rngArmor + flatArmor + levelArmorBonus;
            finalArmor = Math.round(finalArmor * 100.0) / 100.0;
            attrArmor.setBaseValue(finalArmor);
        }
    }
}
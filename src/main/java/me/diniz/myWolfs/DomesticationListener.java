package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;

public class DomesticationListener implements Listener {

    private final Random random = new Random();

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf)) return;

        Bukkit.getScheduler().runTaskLater(MyWolfs.getInstance(), () -> {
            Player owner = (Player) wolf.getOwner();
            if (owner == null) return;
            setupTamedWolf(wolf, owner);
        }, 1L);
    }

    public static void setupTamedWolf(Wolf wolf, Player owner) {
        var pdc = wolf.getPersistentDataContainer();

        pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "TEMP_" + wolf.getUniqueId().toString().substring(0, 5));
        WolfDataManager.saveWolf(wolf, owner);

        int maxWolves = MyWolfs.getInstance().getConfig().getInt("max_wolves", 3);
        int currentWolfCount = WolfDataManager.getRPGWolfCount(owner);

        if (currentWolfCount > maxWolves) {
            pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 0);
            pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
            pdc.set(MyWolfs.KEY_IS_BABY, PersistentDataType.BOOLEAN, true);

            pdc.set(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, rollRank());
            pdc.set(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, rollRank());
            pdc.set(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, rollRank());
            pdc.set(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, rollRank());

            List<String> upgradeItems = MyWolfs.getInstance().getConfig().getStringList("upgrade_items");
            String requiredItem = "DIAMOND";
            if (!upgradeItems.isEmpty()) {
                requiredItem = upgradeItems.get(new Random().nextInt(upgradeItems.size()));
            }
            pdc.set(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, requiredItem);

            rollIndividualStats(wolf);

            String uniqueName = generateUniqueName(owner);
            pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);

            wolf.customName(net.kyori.adventure.text.Component.text(uniqueName + " (Over Limit - Needs " + requiredItem + ")"));
            wolf.setCustomNameVisible(true);

            WolfStatsManager.applyStats(wolf);
            WolfDataManager.updateWolf(wolf);

            MyWolfs.getInstance().sendConfigMessage(owner, "wolf_limit_reached", "%max%", String.valueOf(maxWolves));
            sendVariantMessage(wolf, owner);
            return;
        }

        // NORMAL RPG WOLF LOGIC
        pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
        pdc.set(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, rollRank());
        pdc.set(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, rollRank());
        pdc.set(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, rollRank());
        pdc.set(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, rollRank());

        rollIndividualStats(wolf);

        String uniqueName = generateUniqueName(owner);
        pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);

        wolf.customName(net.kyori.adventure.text.Component.text(uniqueName + " (Lv.1)"));
        wolf.setCustomNameVisible(true);

        WolfStatsManager.applyStats(wolf);
        WolfDataManager.updateWolf(wolf);

        sendVariantMessage(wolf, owner);
    }

    //region VARIANT MESSAGES
    private static void sendVariantMessage(Wolf wolf, Player owner) {
        String variant = wolf.getVariant().getKey().getKey(); // e.g., "pale", "woods"
        String messageKey = "variant_" + variant + "_tamed";

        // If the config doesn't have a message for this variant (e.g., unknown/future variant), fallback to pale.
        if (MyWolfs.getInstance().getConfig().getString("messages." + messageKey) == null) {
            messageKey = "variant_pale_tamed";
        }

        MyWolfs.getInstance().sendConfigMessage(owner, messageKey);
    }
    //endregion

    //region STAT ROLL LOGIC
    private static void rollIndividualStats(Wolf wolf) {
        var pdc = wolf.getPersistentDataContainer();
        var config = MyWolfs.getInstance().getConfig();
        Random rng = new Random();

        double globalVariance = config.getDouble("rpg_stats_system.global_variance", 0.20);
        double armorVariance = config.getDouble("rpg_stats_system.armor_variance", 1.0);

        double rngHealth = 1.0 + (rng.nextDouble() * 2 - 1) * globalVariance;
        double rngDamage = 1.0 + (rng.nextDouble() * 2 - 1) * globalVariance;
        double rngSpeed = 1.0 + (rng.nextDouble() * 2 - 1) * globalVariance;
        double rngArmor = rng.nextDouble() * armorVariance;

        pdc.set(MyWolfs.KEY_STAT_HEALTH_MULT, PersistentDataType.DOUBLE, rngHealth);
        pdc.set(MyWolfs.KEY_STAT_DAMAGE_MULT, PersistentDataType.DOUBLE, rngDamage);
        pdc.set(MyWolfs.KEY_STAT_SPEED_MULT, PersistentDataType.DOUBLE, rngSpeed);
        pdc.set(MyWolfs.KEY_STAT_ARMOR_FLAT, PersistentDataType.DOUBLE, rngArmor);
    }
    //endregion

    private static String generateUniqueName(Player owner) {
        if (!WolfDataManager.isNameTakenInDB(owner, "Wolf")) {
            return "Wolf";
        }
        int suffix = 2;
        while (WolfDataManager.isNameTakenInDB(owner, "Wolf-" + suffix)) {
            suffix++;
        }
        return "Wolf-" + suffix;
    }

    private static String rollRank() {
        int chance = new Random().nextInt(100);
        if (chance < 5) return "S";
        if (chance < 20) return "A";
        if (chance < 45) return "B";
        if (chance < 75) return "C";
        if (chance < 90) return "D";
        return "E";
    }
}
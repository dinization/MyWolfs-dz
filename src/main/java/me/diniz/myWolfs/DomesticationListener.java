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

    private final MyWolfs plugin;
    private final Random random = new Random();

    public DomesticationListener(MyWolfs plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player owner = (Player) wolf.getOwner();
            if (owner == null) return;

            var pdc = wolf.getPersistentDataContainer();

            // 1. Set a TEMPORARY name in the PDC so it doesn't occupy "Wolf" in the database
            pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "TEMP_" + wolf.getUniqueId().toString().substring(0, 5));

            // 2. Save the wolf to the database FIRST so it counts in our checks
            WolfDataManager.saveWolf(wolf, owner);

            // 3. Check the limit using the database
            int maxWolves = plugin.getConfig().getInt("max_wolves", 3);
            int currentWolfCount = WolfDataManager.getRPGWolfCount(owner);

            // If the limit is reached, make it a Level 0 wolf (Over Limit)
            if (currentWolfCount > maxWolves) {
                pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 0);
                pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
                pdc.set(MyWolfs.KEY_IS_BABY, PersistentDataType.BOOLEAN, true);

                // Roll ranks for the Over Limit wolf
                pdc.set(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, rollRank());
                pdc.set(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, rollRank());
                pdc.set(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, rollRank());
                pdc.set(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, rollRank());

                // NEW: Roll a random upgrade item from the config
                List<String> upgradeItems = plugin.getConfig().getStringList("upgrade_items");
                String requiredItem = "DIAMOND"; // Fallback
                if (!upgradeItems.isEmpty()) {
                    requiredItem = upgradeItems.get(random.nextInt(upgradeItems.size()));
                }
                pdc.set(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, requiredItem);

                // 4. Generate the REAL name now that the DB is safe
                String uniqueName = generateUniqueName(owner);
                pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);

                // Display the name with the required item
                wolf.setCustomName(uniqueName + " (Over Limit - Needs " + requiredItem + ")");
                wolf.setCustomNameVisible(true);

                WolfStatsManager.applyStats(wolf);
                WolfDataManager.updateWolf(wolf); // Update DB with the real name

                MyWolfs.getInstance().sendConfigMessage(owner, "wolf_limit_reached", "%max%", String.valueOf(maxWolves));
                return;
            }

            // --- NORMAL RPG WOLF LOGIC ---
            int initialLevel = 1;
            int initialXp = 0;

            String rankHealth = rollRank();
            String rankDamage = rollRank();
            String rankSpeed = rollRank();
            String rankArmor = rollRank();

            pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, initialLevel);
            pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, initialXp);
            pdc.set(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, rankHealth);
            pdc.set(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, rankDamage);
            pdc.set(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, rankSpeed);
            pdc.set(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, rankArmor);

            // 4. Generate the REAL name now that the DB is safe
            String uniqueName = generateUniqueName(owner);
            pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);

            wolf.setCustomName(uniqueName + " (Lv." + initialLevel + ")");
            wolf.setCustomNameVisible(true);

            WolfStatsManager.applyStats(wolf);

            // 5. Save to database again with the final level 1 data and real name
            WolfDataManager.updateWolf(wolf);

        }, 1L);
    }

    // Helper method to generate a unique name using the database
    private String generateUniqueName(Player owner) {
        if (!WolfDataManager.isNameTakenInDB(owner, "Wolf")) {
            return "Wolf";
        }

        int suffix = 2;
        while (WolfDataManager.isNameTakenInDB(owner, "Wolf-" + suffix)) {
            suffix++;
        }
        return "Wolf-" + suffix;
    }

    private String rollRank() {
        int chance = random.nextInt(100);
        if (chance < 5) return "S";
        if (chance < 20) return "A";
        if (chance < 45) return "B";
        if (chance < 75) return "C";
        if (chance < 90) return "D";
        return "E";
    }
}
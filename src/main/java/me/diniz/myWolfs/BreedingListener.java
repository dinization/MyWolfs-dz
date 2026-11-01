package me.diniz.myWolfs;

import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.persistence.PersistentDataType;

public class BreedingListener implements Listener {

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        // Check if the baby is a Wolf
        if (!(event.getEntity() instanceof Wolf babyWolf)) return;

        // Check if both parents are wolves (they should be)
        if (!(event.getMother() instanceof Wolf) || !(event.getFather() instanceof Wolf)) return;

        // Check if at least one parent is tamed by a player
        if (!babyWolf.isTamed() || !(babyWolf.getOwner() instanceof Player owner)) return;

        var pdc = babyWolf.getPersistentDataContainer();

        // 1. Set a TEMPORARY name to avoid occupying the real names in the database
        pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "TEMP_" + babyWolf.getUniqueId().toString().substring(0, 5));

        // 2. Save to database first so it exists (even though it's level 0)
        WolfDataManager.saveWolf(babyWolf, owner);

        // Mark as baby (Level 0 = cannot level up, doesn't count for limit)
        pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 0);
        pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
        pdc.set(MyWolfs.KEY_IS_BABY, PersistentDataType.BOOLEAN, true);

        // 3. Generate the REAL name now that the DB is safe
        String uniqueName = generateBabyName(owner);
        pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);

        babyWolf.setCustomName(uniqueName + " (Baby)");
        babyWolf.setCustomNameVisible(true);

        // Apply vanilla stats (base 20 HP, no RPG bonus) and fix the modifiers
        WolfStatsManager.applyStats(babyWolf);

        // 4. Save to database again with the final data and real name
        WolfDataManager.updateWolf(babyWolf);

        // Notify the owner
        MyWolfs.getInstance().sendConfigMessage(owner, "baby_born");
    }

    // Helper method using the database instead of scanning the world
    private String generateBabyName(Player owner) {
        int suffix = 1;
        while (true) {
            String testName = "Baby-Wolf" + (suffix == 1 ? "" : "-" + suffix);

            // Use the database to check if the name is taken
            if (!WolfDataManager.isNameTakenInDB(owner, testName)) {
                return testName;
            }
            suffix++;
        }
    }
}
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

        // Mark as baby (Level 0 = cannot level up)
        pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 0);
        pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
        pdc.set(MyWolfs.KEY_IS_BABY, PersistentDataType.BOOLEAN, true);

        // Give it a name (Wolf-Baby or similar, or just generate unique)
        String uniqueName = generateBabyName(babyWolf, owner);
        pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);
        babyWolf.setCustomName(uniqueName + " (Baby)");
        babyWolf.setCustomNameVisible(true);

        // Apply vanilla stats (base 20 HP, no RPG bonus) and fix the modifiers
        WolfStatsManager.applyStats(babyWolf);

        // Notify the owner that the baby won't level up
        MyWolfs.getInstance().sendConfigMessage(owner, "baby_born");
    }

    private String generateBabyName(Wolf babyWolf, Player owner) {
        int suffix = 1;
        while (true) {
            String testName = "Baby-Wolf" + (suffix == 1 ? "" : "-" + suffix);
            boolean taken = false;
            for (Wolf wolf : owner.getWorld().getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed() && owner.equals(wolf.getOwner()) && !wolf.equals(babyWolf)) {
                    String name = wolf.getPersistentDataContainer().getOrDefault(
                            MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, ""
                    );
                    if (name.equals(testName)) {
                        taken = true;
                        break;
                    }
                }
            }
            if (!taken) return testName;
            suffix++;
        }
    }
}
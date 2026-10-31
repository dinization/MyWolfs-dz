package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
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

        // Run the logic 1 tick later to ensure the game has fully registered the owner
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player owner = (Player) wolf.getOwner();
            if (owner == null) return;

            // --- Check the wolf limit across ALL worlds ---
            int maxWolves = plugin.getConfig().getInt("max_wolves", 3);
            int currentWolfCount = 0;

            // Iterate through all loaded worlds in the server
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Wolf w : world.getEntitiesByClass(Wolf.class)) {
                    if (w.isTamed() && owner.equals(w.getOwner()) && !w.equals(wolf)) {
                        // Only count wolves that are NOT level 0 (RPG wolves)
                        int lvl = w.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
                        if (lvl > 0) {
                            currentWolfCount++;
                        }
                    }
                }
            }

            var pdc = wolf.getPersistentDataContainer();

            // If the limit is reached, make it a Level 0 wolf (vanilla)
            if (currentWolfCount >= maxWolves) {
                pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 0);
                pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
                pdc.set(MyWolfs.KEY_IS_BABY, PersistentDataType.BOOLEAN, true);

                String uniqueName = generateUniqueName(wolf);
                pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);
                wolf.setCustomName(uniqueName + " (Over Limit)");
                wolf.setCustomNameVisible(true);

                WolfStatsManager.applyStats(wolf);

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

            String uniqueName = generateUniqueName(wolf);
            pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, uniqueName);

            wolf.setCustomName(uniqueName + " (Lv." + initialLevel + ")");
            wolf.setCustomNameVisible(true);

            WolfStatsManager.applyStats(wolf);
        }, 1L);
    }

    // Helper method to generate a unique name (Wolf, Wolf-2, Wolf-3...)
    private String generateUniqueName(Wolf tamedWolf) {
        Player owner = (Player) tamedWolf.getOwner();
        if (owner == null) return "Wolf";

        // 1. Get all wolves owned by the player across ALL worlds
        List<String> existingNames = new ArrayList<>();
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed() && owner.equals(wolf.getOwner()) && !wolf.equals(tamedWolf)) {
                    String name = wolf.getPersistentDataContainer().getOrDefault(
                            MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf"
                    );
                    existingNames.add(name);
                }
            }
        }

        // 2. If "Wolf" is free, use it
        if (!existingNames.contains("Wolf")) {
            return "Wolf";
        }

        // 3. Otherwise, try Wolf-2, Wolf-3, etc.
        int suffix = 2;
        while (existingNames.contains("Wolf-" + suffix)) {
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
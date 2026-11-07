package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class CompanionBuffManager {

    public static void startAllTasks(MyWolfs plugin) {
        startFoxBuffTask(plugin);

        // Future buffs will be started here:
    }

    //region FOX BUFF (Regeneration)

    private static void startFoxBuffTask(MyWolfs plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    applyFoxBuff(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 200L); // Runs every 10 seconds (200 ticks)
    }

    private static void applyFoxBuff(Player player) {
        boolean hasOwnedFox = false;
        Fox ownedFox = null;
        String playerUUID = player.getUniqueId().toString();

        // 1. Search for an owned fox within 16 blocks
        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
            if (entity instanceof Fox fox) {
                String foxOwner = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
                if (playerUUID.equals(foxOwner)) {
                    hasOwnedFox = true;
                    ownedFox = fox;
                    break;
                }
            }
        }

        // 2. If a fox is present, apply the effect to the fox itself
        if (hasOwnedFox && ownedFox != null) {
            applyRegenEffect(ownedFox);
        }

        // 3. Apply the effect to nearby owned wolves (24 blocks)
        if (hasOwnedFox) {
            for (Entity entity : player.getNearbyEntities(24, 24, 24)) {
                if (entity instanceof Wolf wolf) {
                    if (player.equals(wolf.getOwner())) {
                        applyRegenEffect(wolf);
                    }
                }
            }
        }
    }

    // Helper method to apply the exact same effect to any entity
    private static void applyRegenEffect(org.bukkit.entity.LivingEntity entity) {
        // Duration: 120 ticks (6 seconds), Amplifier: 0 (Regen I)
        // Particles: true, Ambient: false
        entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, false, true));
    }
    //endregion
}
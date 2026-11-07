package me.diniz.myWolfs;

import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class CombatListener implements Listener {

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        // Checks if attacker is a wolf
        if (event.getDamager() instanceof Wolf wolf) {
            // Checks if its tamed
            if (wolf.isTamed()) {
                // Saves wolf's UUID in the attacked mob's PDC
                // Only last wolf to hit is counted
                event.getEntity().getPersistentDataContainer().set(
                        MyWolfs.KEY_LAST_ATTACKER,
                        PersistentDataType.STRING,
                        wolf.getUniqueId().toString()
                );
            }
        }
    }

    @EventHandler
    public void onWolfFriendlyFire(EntityDamageByEntityEvent event) {
        // 1. Check if the setting is disabled in config
        if (MyWolfs.getInstance().getConfig().getBoolean("settings.wolf_friendly_fire", false)) {
            return;
        }

        // 2. Check if the attacker is a player
        if (!(event.getDamager() instanceof Player player)) return;

        // 3. Check if the damaged entity is a Wolf
        if (!(event.getEntity() instanceof Wolf wolf)) return;

        // 4. If the player is the owner of the wolf, cancel the damage
        if (player.equals(wolf.getOwner())) {
            event.setCancelled(true);
        }
    }
}
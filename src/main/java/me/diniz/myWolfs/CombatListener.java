package me.diniz.myWolfs;

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
}
package me.diniz.myWolfs;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class WolfSafetyListener implements Listener {

    // Saves the wolves and foxes exact position when the chunk is unloaded
    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Wolf wolf && wolf.isTamed()) {
            // ONLY save if the wolf is NOT dead.
            // If it's dead, let the EntityDeathEvent handle the deletion.
            if (wolf.getHealth() > 0) {
                WolfDataManager.updateWolf(wolf);
            }
        }

        if (event.getEntity() instanceof Fox fox) {

            String owner = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
            if (owner != null && !owner.isEmpty() && fox.getHealth() > 0) {
                FoxDataManager.updateFox(fox);
            }
        }
    }

    // Deletes wolf from DB when wolf dies
    @EventHandler
    public void onWolfDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wolf wolf && wolf.isTamed()) {
            UUID wolfId = wolf.getUniqueId();
            WolfDataManager.deleteWolfById(wolfId);
        }
    }
}
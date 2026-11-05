package me.diniz.myWolfs;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

public class WolfSafetyListener implements Listener {

    // Saves the wolf's exact position when the chunk is unloaded
    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Wolf wolf && wolf.isTamed()) {
            // ONLY save if the wolf is NOT dead.
            // If it's dead, let the EntityDeathEvent handle the deletion.
            if (wolf.getHealth() > 0) {
                WolfDataManager.updateWolf(wolf);
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
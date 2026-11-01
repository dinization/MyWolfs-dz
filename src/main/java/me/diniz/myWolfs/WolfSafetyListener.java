package me.diniz.myWolfs;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class WolfSafetyListener implements Listener {

    // Saves the wolf's exact position when the chunk is unloaded
    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Wolf wolf && wolf.isTamed()) {
            WolfDataManager.updateWolf(wolf);
        }
    }
}
package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class WolfModeManager {

    public enum Mode {
        NORMAL,
        AGGRESSIVE
    }

    // Store the current mode of a wolf by its UUID
    private static final HashMap<UUID, Mode> wolfModes = new HashMap<>();
    // Store the last time (in ms) the wolf found a target to avoid idling forever
    private static final HashMap<UUID, Long> lastTargetTime = new HashMap<>();

    public static void setMode(Wolf wolf, Mode mode) {
        wolfModes.put(wolf.getUniqueId(), mode);
        lastTargetTime.put(wolf.getUniqueId(), System.currentTimeMillis());
    }

    public static Mode getMode(Wolf wolf) {
        return wolfModes.getOrDefault(wolf.getUniqueId(), Mode.NORMAL);
    }

    public static void startTask(MyWolfs plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long timeoutMillis = 120 * 1000L; // 2 minutes timeout

                for (UUID uuid : new ArrayList<>(wolfModes.keySet())) {
                    Wolf wolf = (Wolf) Bukkit.getEntity(uuid);

                    // Cleanup if wolf is dead, despawned, or sitting
                    if (wolf == null || !wolf.isValid() || wolf.isSitting()) {
                        wolfModes.remove(uuid);
                        lastTargetTime.remove(uuid);
                        continue;
                    }

                    if (getMode(wolf) == Mode.AGGRESSIVE) {
                        // Check for timeout (no targets found for 2 minutes)
                        if (currentTime - lastTargetTime.getOrDefault(uuid, currentTime) > timeoutMillis) {
                            setMode(wolf, Mode.NORMAL);

                            // Notify the owner
                            if (wolf.getOwner() instanceof Player owner && owner.isOnline()) {
                                MyWolfs.getInstance().sendConfigMessage(owner, "wolf_bored");
                            }
                            continue;
                        }

                        // If the wolf has a valid target, reset the timer and skip
                        if (wolf.getTarget() != null && wolf.getTarget().isValid()) {
                            lastTargetTime.put(uuid, currentTime);
                            continue;
                        }

                        // Find a new target
                        LivingEntity target = findNearestTarget(wolf);
                        if (target != null) {
                            wolf.setTarget(target);
                            lastTargetTime.put(uuid, currentTime);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every 1 second
    }

    private static LivingEntity findNearestTarget(Wolf wolf) {
        for (Entity entity : wolf.getNearbyEntities(10, 10, 10)) {
            // Target ONLY hostile Monsters (Zombies, Skeletons, Spiders, etc.)
            // Creepers are technically Monsters, but we explicitly exclude them.
            if (entity instanceof Monster && !(entity instanceof org.bukkit.entity.Creeper)) {
                return (LivingEntity) entity;
            }

            // Villagers, Animals, and Players are naturally ignored because they are not Monsters.
        }
        return null;
    }
}
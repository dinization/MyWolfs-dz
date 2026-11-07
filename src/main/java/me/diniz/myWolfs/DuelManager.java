package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class DuelManager {

    //region DUEL SESSION
    public static class DuelSession {
        public final UUID player1;
        public final UUID player2;
        public final Location player1Loc;
        public final Location player2Loc;

        public final List<UUID> player1Wolves = new ArrayList<>();
        public final List<UUID> player2Wolves = new ArrayList<>();
        public final List<UUID> ghostWolves = new ArrayList<>();

        public final Map<UUID, Double> savedHealth = new HashMap<>();
        public boolean isActive = false;

        public DuelSession(Player p1, Player p2) {
            this.player1 = p1.getUniqueId();
            this.player2 = p2.getUniqueId();
            this.player1Loc = p1.getLocation();
            this.player2Loc = p2.getLocation();
        }
    }
    //endregion

    //region MANAGER STATE
    private static final Map<UUID, DuelSession> activeSessions = new HashMap<>();
    private static final Map<UUID, DuelSession> playerInDuel = new HashMap<>();
    public static final Set<UUID> frozenPlayers = new HashSet<>();

    public static boolean isInDuel(Player player) {
        return playerInDuel.containsKey(player.getUniqueId());
    }

    public static DuelSession getSession(Player player) {
        return playerInDuel.get(player.getUniqueId());
    }
    //endregion

    //region START DUEL
    public static void startDuel(Player p1, Player p2) {
        DuelSession session = new DuelSession(p1, p2);
        session.isActive = true;

        activeSessions.put(p1.getUniqueId(), session);
        playerInDuel.put(p1.getUniqueId(), session);
        playerInDuel.put(p2.getUniqueId(), session);
        frozenPlayers.add(p1.getUniqueId());
        frozenPlayers.add(p2.getUniqueId());

        int maxWolves = MyWolfs.getInstance().getConfig().getInt("settings.max_duel_wolves", 3);

        selectWolves(p1, maxWolves, session, session.player1Wolves);
        selectWolves(p2, maxWolves, session, session.player2Wolves);

        int prepTime = MyWolfs.getInstance().getConfig().getInt("settings.duel_preparation_seconds", 5);
        startCountdown(session, prepTime);
        for (UUID uuid : session.player1Wolves) {
            Wolf w = (Wolf) Bukkit.getEntity(uuid);
            if (w != null) WolfModeManager.setMode(w, WolfModeManager.Mode.AGGRESSIVE);
        }
        for (UUID uuid : session.player2Wolves) {
            Wolf w = (Wolf) Bukkit.getEntity(uuid);
            if (w != null) WolfModeManager.setMode(w, WolfModeManager.Mode.AGGRESSIVE);
        }
        startCombatTask(session);
    }

    public static void startCombatTask(DuelSession session) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!session.isActive) {
                    cancel();
                    return;
                }

                Player p1 = Bukkit.getPlayer(session.player1);
                Player p2 = Bukkit.getPlayer(session.player2);

                if (p1 == null || p2 == null) {
                    cancel();
                    return;
                }

                // Process Player 1's wolves
                for (UUID wolfId : session.player1Wolves) {
                    // Skip if the wolf is a ghost (defeated)
                    if (session.ghostWolves.contains(wolfId)) continue;

                    Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                    if (wolf != null && wolf.isValid() && wolf.getHealth() > 0) {
                        // If the wolf has no target or the target is dead
                        if (wolf.getTarget() == null || !wolf.getTarget().isValid()) {
                            // Find the nearest enemy (Player 2 or their wolves)
                            org.bukkit.entity.LivingEntity target = findNearestEnemy(wolf, p2);
                            if (target != null) {
                                wolf.setTarget(target);
                            }
                        }
                    }
                }

                // Process Player 2's wolves
                for (UUID wolfId : session.player2Wolves) {
                    // Skip if the wolf is a ghost (defeated)
                    if (session.ghostWolves.contains(wolfId)) continue;

                    Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                    if (wolf != null && wolf.isValid() && wolf.getHealth() > 0) {
                        if (wolf.getTarget() == null || !wolf.getTarget().isValid()) {
                            // Find the nearest enemy (Player 1 or their wolves)
                            org.bukkit.entity.LivingEntity target = findNearestEnemy(wolf, p1);
                            if (target != null) {
                                wolf.setTarget(target);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(MyWolfs.getInstance(), 0L, 20L); // Run every second
    }

    public static void defeatWolf(Wolf wolf, DuelSession session) {
        // 1. Play particles
        wolf.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, wolf.getLocation(), 10);
        wolf.getWorld().spawnParticle(org.bukkit.Particle.HEART, wolf.getLocation(), 5);

        // 2. Remove from fighting lists and add to ghosts
        session.ghostWolves.add(wolf.getUniqueId());

        // 3. Send to limbo (heal first so it doesn't die while flying up)
        wolf.setHealth(wolf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        sendToLimbo(wolf);

        // 4. Notify the owner
        if (wolf.getOwner() instanceof Player owner) {
            MyWolfs.getInstance().sendConfigMessage(owner, "duel_wolf_defeated", "%name%", wolf.getCustomName() != null ? wolf.getCustomName() : "Wolf");
        }
    }

    private static org.bukkit.entity.LivingEntity findNearestEnemy(Wolf wolf, Player enemyPlayer) {
        org.bukkit.entity.LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        // Check the enemy player
        double distToPlayer = wolf.getLocation().distanceSquared(enemyPlayer.getLocation());
        if (distToPlayer < closestDist) {
            closestDist = distToPlayer;
            closest = enemyPlayer;
        }

        // Check the enemy player's wolves
        for (Wolf enemyWolf : enemyPlayer.getWorld().getEntitiesByClass(Wolf.class)) {
            if (enemyPlayer.equals(enemyWolf.getOwner())) {
                double distToWolf = wolf.getLocation().distanceSquared(enemyWolf.getLocation());
                if (distToWolf < closestDist) {
                    closestDist = distToWolf;
                    closest = enemyWolf;
                }
            }
        }

        return closest;
    }

    private static void selectWolves(Player player, int maxWolves, DuelSession session, List<UUID> targetList) {
        List<Wolf> wolves = new ArrayList<>();
        for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
            if (player.equals(wolf.getOwner())) {
                wolves.add(wolf);
            }
        }

        wolves.sort(Comparator.comparingDouble(w -> w.getLocation().distanceSquared(player.getLocation())));

        for (int i = 0; i < wolves.size(); i++) {
            Wolf wolf = wolves.get(i);
            session.savedHealth.put(wolf.getUniqueId(), wolf.getHealth());

            if (i < maxWolves) {
                targetList.add(wolf.getUniqueId());
            } else {
                session.ghostWolves.add(wolf.getUniqueId());
                sendToLimbo(wolf);
            }
        }
    }

    private static void sendToLimbo(Wolf wolf) {
        wolf.setGravity(false);
        wolf.setSitting(true);
        Location loc = new Location(wolf.getWorld(), wolf.getX(), 250, wolf.getZ());
        wolf.teleport(loc);
        wolf.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        loc.getChunk().addPluginChunkTicket(MyWolfs.getInstance());
    }
    //endregion

    //region COUNTDOWN
    private static void startCountdown(DuelSession session, int seconds) {
        new BukkitRunnable() {
            int timeLeft = seconds;
            @Override
            public void run() {
                Player p1 = Bukkit.getPlayer(session.player1);
                Player p2 = Bukkit.getPlayer(session.player2);

                if (p1 == null || p2 == null || !session.isActive) {
                    cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    frozenPlayers.remove(p1.getUniqueId());
                    frozenPlayers.remove(p2.getUniqueId());
                    MyWolfs.getInstance().sendConfigMessage(p1, "duel_go");
                    MyWolfs.getInstance().sendConfigMessage(p2, "duel_go");
                    cancel();
                    return;
                }

                MyWolfs.getInstance().sendConfigMessage(p1, "duel_countdown", "%time%", String.valueOf(timeLeft));
                MyWolfs.getInstance().sendConfigMessage(p2, "duel_countdown", "%time%", String.valueOf(timeLeft));
                timeLeft--;
            }
        }.runTaskTimer(MyWolfs.getInstance(), 0L, 20L);
    }
    //endregion

    //region END DUEL & RESTORATION
    public static void endDuel(DuelSession session, Player winner, String endReason) {
        if (session == null || !session.isActive) return;
        session.isActive = false;

        Player p1 = Bukkit.getPlayer(session.player1);
        Player p2 = Bukkit.getPlayer(session.player2);

        // 1. Make everyone invulnerable immediately
        if (p1 != null && p1.isOnline()) p1.setInvulnerable(true);
        if (p2 != null && p2.isOnline()) p2.setInvulnerable(true);

        // 2. Send end messages
        String loserName = (winner != null && p1 != null && winner.equals(p1)) ? p2.getName() : p1.getName();
        if (winner != null && winner.isOnline()) {
            MyWolfs.getInstance().sendConfigMessage(winner, endReason, "%winner%", winner.getName(), "%loser%", loserName);
        }
        if (p1 != null && p2 != null) {
            MyWolfs.getInstance().sendConfigMessage(p1, "duel_teleport_warning");
            MyWolfs.getInstance().sendConfigMessage(p2, "duel_teleport_warning");
        }

        // 3. Delay for 2 seconds before teleporting
        new BukkitRunnable() {
            @Override
            public void run() {
                // Restore Wolves
                restoreWolves(session.player1Wolves, session);
                restoreWolves(session.player2Wolves, session);
                restoreWolves(session.ghostWolves, session);

                // Teleport Players
                if (p1 != null && p1.isOnline()) {
                    p1.teleport(session.player1Loc);
                    p1.setHealth(20.0);
                }
                if (p2 != null && p2.isOnline()) {
                    p2.teleport(session.player2Loc);
                    p2.setHealth(20.0);
                }

                // 4. Remove invulnerability after 3 more seconds and clean up
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p1 != null && p1.isOnline()) p1.setInvulnerable(false);
                        if (p2 != null && p2.isOnline()) p2.setInvulnerable(false);

                        // Cleanup memory
                        playerInDuel.remove(session.player1);
                        playerInDuel.remove(session.player2);
                        frozenPlayers.remove(session.player1);
                        frozenPlayers.remove(session.player2);
                    }
                }.runTaskLater(MyWolfs.getInstance(), 60L); // 3 seconds
            }
        }.runTaskLater(MyWolfs.getInstance(), 40L); // 2 seconds
    }

    private static void restoreWolves(List<UUID> wolfList, DuelSession session) {
        for (UUID uuid : wolfList) {
            Wolf wolf = (Wolf) Bukkit.getEntity(uuid);
            if (wolf != null && wolf.isValid()) {

                wolf.getLocation().getChunk().removePluginChunkTicket(MyWolfs.getInstance());
                resetWolfState(wolf);
                wolf.setGravity(true);
                wolf.setSitting(false);
                wolf.removePotionEffect(PotionEffectType.INVISIBILITY);

                // Reset mode to normal
                WolfModeManager.setMode(wolf, WolfModeManager.Mode.NORMAL);

                if (session.savedHealth.containsKey(uuid)) {
                    wolf.setHealth(session.savedHealth.get(uuid));
                }

                Player owner = (Player) wolf.getOwner();
                if (owner != null && owner.isOnline()) {
                    wolf.teleport(owner.getLocation());
                }
            }
        }
    }

    public static void resetWolfState(Wolf wolf) {
        wolf.setGravity(true);
        wolf.setSitting(false);
        wolf.removePotionEffect(PotionEffectType.INVISIBILITY);
        WolfModeManager.setMode(wolf, WolfModeManager.Mode.NORMAL);
        // Clear any target they might have left over
        wolf.setTarget(null);
    }

}
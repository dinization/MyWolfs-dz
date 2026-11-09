package me.diniz.myWolfs;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class DuelManager {

    public static final Set<UUID> allowedTeleports = new HashSet<>();

    //region DUEL SESSION
    public static class DuelSession {
        public final UUID player1;
        public final UUID player2;
        public final Location player1Loc;
        public final Location player2Loc;

        public final List<UUID> player1Wolves = new ArrayList<>();
        public final List<UUID> player2Wolves = new ArrayList<>();
        public final List<UUID> player1Supports = new ArrayList<>();
        public final List<UUID> player2Supports = new ArrayList<>();
        public final List<UUID> ghostWolves = new ArrayList<>();
        public final List<UUID> ghostSupports = new ArrayList<>();

        public final Map<UUID, Double> savedHealth = new HashMap<>();
        public boolean isActive = false;
        public boolean isFighting = false;

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

    /**
     * Wolf UUID -> set of player UUIDs the wolf must not re-target.
     * Populated when a duel ends; cleared when the wolf is unloaded,
     * deleted, or caught by the periodic cleanup task.
     */
    public static final Map<UUID, Set<UUID>> postDuelAggroLock = new HashMap<>();

    public static boolean isInDuel(Player player) {
        return playerInDuel.containsKey(player.getUniqueId());
    }

    public static DuelSession getSession(Player player) {
        return playerInDuel.get(player.getUniqueId());
    }

    public static void clearAggroLock(UUID wolfUUID) {
        postDuelAggroLock.remove(wolfUUID);
    }
    //endregion

    //region GLOW TEAMS
    private static final String TEAM_P1 = "duel_glow_p1_";
    private static final String TEAM_P2 = "duel_glow_p2_";

    /**
     * Registers two colored teams and assigns every wolf/support to its owner's team.
     * Players are intentionally NOT added, so only companion entities glow.
     */
    private static void createGlowTeams(DuelSession session) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String suffix = session.player1.toString().substring(0, 8);

        Team t1 = board.getTeam(TEAM_P1 + suffix);
        if (t1 != null) t1.unregister();
        t1 = board.registerNewTeam(TEAM_P1 + suffix);
        t1.color(NamedTextColor.RED);

        Team t2 = board.getTeam(TEAM_P2 + suffix);
        if (t2 != null) t2.unregister();
        t2 = board.registerNewTeam(TEAM_P2 + suffix);
        t2.color(NamedTextColor.BLUE);

        assignGlow(t1, session.player1Wolves);
        assignGlow(t1, session.player1Supports);
        assignGlow(t2, session.player2Wolves);
        assignGlow(t2, session.player2Supports);
    }

    private static void assignGlow(Team team, List<UUID> entities) {
        for (UUID uuid : entities) {
            if (Bukkit.getEntity(uuid) instanceof LivingEntity living && living.isValid()) {
                team.addEntry(living.getUniqueId().toString());
                living.setGlowing(true);
            }
        }
    }

    private static void cleanupGlowTeams(DuelSession session) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String suffix = session.player1.toString().substring(0, 8);

        Team t1 = board.getTeam(TEAM_P1 + suffix);
        if (t1 != null) t1.unregister();
        Team t2 = board.getTeam(TEAM_P2 + suffix);
        if (t2 != null) t2.unregister();

        // Clear glow from every entity that may have participated (alive, ghost, or support).
        Set<UUID> all = new HashSet<>();
        all.addAll(session.player1Wolves);
        all.addAll(session.player2Wolves);
        all.addAll(session.ghostWolves);
        all.addAll(session.player1Supports);
        all.addAll(session.player2Supports);
        all.addAll(session.ghostSupports);

        for (UUID uuid : all) {
            if (Bukkit.getEntity(uuid) instanceof LivingEntity living) {
                living.setGlowing(false);
            }
        }
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
        p1.setWalkSpeed(0f);
        p1.setFlySpeed(0f);
        frozenPlayers.add(p2.getUniqueId());
        p2.setWalkSpeed(0f);
        p2.setFlySpeed(0f);

        int maxWolves = MyWolfs.getInstance().getConfig().getInt("settings.max_duel_wolves", 3);

        selectWolves(p1, maxWolves, session, session.player1Wolves);
        selectSupports(p1, session, session.player1Supports);

        selectWolves(p2, maxWolves, session, session.player2Wolves);
        selectSupports(p2, session, session.player2Supports);

        // Color-coded glow so each side can tell whose companions are whose.
        createGlowTeams(session);

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

    private static void selectSupports(Player player, DuelSession session, List<UUID> targetList) {
        double searchRadius = MyWolfs.getInstance().getConfig().getInt("settings.duel_wolf_search_radius", 30);
        double radiusSquared = searchRadius * searchRadius;

        for (Fox fox : player.getWorld().getEntitiesByClass(Fox.class)) {
            String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
            if (player.getUniqueId().toString().equals(ownerUUID)) {
                if (fox.getLocation().distanceSquared(player.getLocation()) <= radiusSquared) {
                    targetList.add(fox.getUniqueId());
                    session.savedHealth.put(fox.getUniqueId(), fox.getHealth());
                }
            }
        }
    }

    public static void startCombatTask(DuelSession session) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!session.isActive || !session.isFighting) return;

                Player p1 = Bukkit.getPlayer(session.player1);
                Player p2 = Bukkit.getPlayer(session.player2);
                if (p1 == null || p2 == null) {
                    cancel();
                    return;
                }

                for (UUID wolfId : session.player1Wolves) {
                    if (session.ghostWolves.contains(wolfId)) continue;
                    Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                    if (wolf != null && wolf.isValid() && wolf.getHealth() > 0) {
                        if (wolf.getTarget() == null || !wolf.getTarget().isValid()) {
                            LivingEntity target = findNearestEnemy(wolf, p2, session.player2Supports);
                            if (target != null) wolf.setTarget(target);
                        }
                    }
                }

                for (UUID wolfId : session.player2Wolves) {
                    if (session.ghostWolves.contains(wolfId)) continue;
                    Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                    if (wolf != null && wolf.isValid() && wolf.getHealth() > 0) {
                        if (wolf.getTarget() == null || !wolf.getTarget().isValid()) {
                            LivingEntity target = findNearestEnemy(wolf, p1, session.player1Supports);
                            if (target != null) wolf.setTarget(target);
                        }
                    }
                }
            }
        }.runTaskTimer(MyWolfs.getInstance(), 0L, 20L);
    }

    private static LivingEntity findNearestEnemy(Wolf wolf, Player enemyPlayer, List<UUID> enemySupports) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        double distToPlayer = wolf.getLocation().distanceSquared(enemyPlayer.getLocation());
        if (distToPlayer < closestDist) {
            closestDist = distToPlayer;
            closest = enemyPlayer;
        }

        for (Wolf enemyWolf : enemyPlayer.getWorld().getEntitiesByClass(Wolf.class)) {
            if (enemyPlayer.equals(enemyWolf.getOwner())) {
                double distToWolf = wolf.getLocation().distanceSquared(enemyWolf.getLocation());
                if (distToWolf < closestDist) {
                    closestDist = distToWolf;
                    closest = enemyWolf;
                }
            }
        }

        for (UUID supportId : enemySupports) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(supportId);
            if (entity instanceof LivingEntity support && entity.isValid()) {
                double distToSupport = wolf.getLocation().distanceSquared(support.getLocation());
                if (distToSupport < closestDist) {
                    closestDist = distToSupport;
                    closest = support;
                }
            }
        }

        return closest;
    }

    public static void defeatWolf(Wolf wolf, DuelSession session) {
        wolf.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, wolf.getLocation(), 10);
        wolf.getWorld().spawnParticle(org.bukkit.Particle.HEART, wolf.getLocation(), 5);

        session.ghostWolves.add(wolf.getUniqueId());
        wolf.setHealth(wolf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        sendToLimbo(wolf);

        if (wolf.getOwner() instanceof Player owner) {
            String wolfName = "Wolf";
            if (wolf.customName() != null) {
                wolfName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(wolf.customName());
            }
            MyWolfs.getInstance().sendConfigMessage(owner, "duel_wolf_defeated", "%name%", wolfName);
        }
    }

    public static void defeatSupport(Fox fox, DuelSession session) {
        fox.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, fox.getLocation(), 10);
        fox.getWorld().spawnParticle(org.bukkit.Particle.HEART, fox.getLocation(), 5);

        session.player1Supports.remove(fox.getUniqueId());
        session.player2Supports.remove(fox.getUniqueId());
        session.ghostSupports.add(fox.getUniqueId());
        fox.setHealth(fox.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        sendToLimbo(fox);

        String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
        if (ownerUUID != null) {
            Player owner = Bukkit.getPlayer(UUID.fromString(ownerUUID));
            if (owner != null) {
                MyWolfs.getInstance().sendConfigMessage(owner, "duel_support_defeated");
            }
        }
    }

    private static void selectWolves(Player player, int maxWolves, DuelSession session, List<UUID> targetList) {
        List<Wolf> wolves = new ArrayList<>();
        double searchRadius = MyWolfs.getInstance().getConfig().getInt("settings.duel_wolf_search_radius", 30);
        double radiusSquared = searchRadius * searchRadius;

        for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
            if (player.equals(wolf.getOwner()) && wolf.getLocation().distanceSquared(player.getLocation()) <= radiusSquared) {
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

    private static void sendToLimbo(LivingEntity entity) {
        entity.setGravity(false);
        if (entity instanceof Wolf wolf) wolf.setSitting(true);
        if (entity instanceof Fox fox) fox.setSitting(true);

        Location loc = new Location(entity.getWorld(), entity.getX(), 250, entity.getZ());
        entity.teleport(loc);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
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
                    p1.setWalkSpeed(0.2f);
                    p1.setFlySpeed(0.1f);
                    frozenPlayers.remove(p2.getUniqueId());
                    p2.setWalkSpeed(0.2f);
                    p2.setFlySpeed(0.1f);
                    session.isFighting = true;
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
    public static void endDuel(DuelSession session, Player winner, String endReason, String... extraReplacements) {
        if (session == null || !session.isActive) return;
        session.isActive = false;

        // Drop glow immediately so the visual effect stops the moment the duel ends.
        cleanupGlowTeams(session);

        clearWolfTargets(session);

        Player p1 = Bukkit.getPlayer(session.player1);
        Player p2 = Bukkit.getPlayer(session.player2);

        if (p1 != null && p1.isOnline()) p1.setInvulnerable(true);
        if (p2 != null && p2.isOnline()) p2.setInvulnerable(true);

        if (p1 != null && p1.isOnline()) {
            p1.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 0, false, false));
        }
        if (p2 != null && p2.isOnline()) {
            p2.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 100, 0, false, false));
        }

        Player loser = (winner != null && winner.equals(p1)) ? p2 : p1;
        if (loser != null && loser.isOnline()) {
            org.bukkit.Particle.DustOptions bloodDust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.5f);

            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= 80 || loser == null || !loser.isOnline()) {
                        cancel();
                        return;
                    }
                    loser.getWorld().spawnParticle(org.bukkit.Particle.DUST, loser.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, bloodDust);
                    ticks += 10;
                }
            }.runTaskTimer(MyWolfs.getInstance(), 0L, 10L);
        }

        String loserName = (winner != null && p1 != null && winner.equals(p1)) ? p2.getName() : p1.getName();

        if (winner != null && winner.isOnline()) {
            String[] combinedReplacements = new String[4 + extraReplacements.length];
            combinedReplacements[0] = "%winner%"; combinedReplacements[1] = winner.getName();
            combinedReplacements[2] = "%loser%"; combinedReplacements[3] = loserName;
            System.arraycopy(extraReplacements, 0, combinedReplacements, 4, extraReplacements.length);

            MyWolfs.getInstance().sendConfigMessage(winner, endReason, combinedReplacements);
        }

        if (p1 != null && p2 != null) {
            MyWolfs.getInstance().sendConfigMessage(p1, "duel_teleport_warning");
            MyWolfs.getInstance().sendConfigMessage(p2, "duel_teleport_warning");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                playerInDuel.remove(session.player1);
                playerInDuel.remove(session.player2);

                if (p1 != null && p1.isOnline()) p1.removePotionEffect(PotionEffectType.LEVITATION);
                if (p2 != null && p2.isOnline()) p2.removePotionEffect(PotionEffectType.LEVITATION);

                if (p1 != null && p1.isOnline()) {
                    DuelManager.allowedTeleports.add(p1.getUniqueId());
                    p1.teleport(session.player1Loc);
                    p1.setHealth(20.0);
                }
                if (p2 != null && p2.isOnline()) {
                    DuelManager.allowedTeleports.add(p2.getUniqueId());
                    p2.teleport(session.player2Loc);
                    p2.setHealth(20.0);
                }

                restorePets(session.player1Wolves, session);
                restorePets(session.player2Wolves, session);
                restorePets(session.ghostWolves, session);
                restorePets(session.player1Supports, session);
                restorePets(session.player2Supports, session);
                restorePets(session.ghostSupports, session);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p1 != null && p1.isOnline()) p1.setInvulnerable(false);
                        if (p2 != null && p2.isOnline()) p2.setInvulnerable(false);

                        frozenPlayers.remove(session.player1);
                        frozenPlayers.remove(session.player2);
                    }
                }.runTaskLater(MyWolfs.getInstance(), 60L);
            }
        }.runTaskLater(MyWolfs.getInstance(), 80L);
    }

    private static void restorePets(List<UUID> petList, DuelSession session) {
        for (UUID uuid : petList) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity pet && pet.isValid()) {
                pet.getLocation().getChunk().removePluginChunkTicket(MyWolfs.getInstance());
                resetPetState(pet);

                if (session.savedHealth.containsKey(uuid)) {
                    pet.setHealth(session.savedHealth.get(uuid));
                }

                if (pet instanceof Wolf wolf && wolf.getOwner() instanceof Player owner && owner.isOnline()) {
                    // Lock this wolf against both duel participants to prevent vanilla aggro from resurfacing.
                    Set<UUID> lockedPlayers = new HashSet<>();
                    lockedPlayers.add(session.player1);
                    lockedPlayers.add(session.player2);
                    postDuelAggroLock.put(wolf.getUniqueId(), lockedPlayers);

                    wolf.setAngry(false);
                    wolf.setTarget(null);
                    wolf.teleport(owner.getLocation());
                    // Wolves return standing (no setSitting(true)).
                } else if (pet instanceof Fox fox) {
                    String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
                    if (ownerUUID != null) {
                        Player owner = Bukkit.getPlayer(UUID.fromString(ownerUUID));
                        if (owner != null && owner.isOnline()) {
                            fox.teleport(owner.getLocation());
                            fox.setSitting(true);
                        }
                    }
                }
            }
        }
    }

    public static void resetPetState(LivingEntity pet) {
        pet.setGravity(true);
        if (pet instanceof Wolf wolf) wolf.setSitting(false);
        if (pet instanceof Fox fox) fox.setSitting(false);
        pet.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (pet instanceof Wolf wolf) WolfModeManager.setMode(wolf, WolfModeManager.Mode.NORMAL);
        if (pet instanceof org.bukkit.entity.Mob mob) mob.setTarget(null);
    }

    private static void clearWolfTargets(DuelSession session) {
        List<UUID> allPets = new ArrayList<>();
        allPets.addAll(session.player1Wolves);
        allPets.addAll(session.player2Wolves);
        allPets.addAll(session.ghostWolves);
        allPets.addAll(session.player1Supports);
        allPets.addAll(session.player2Supports);

        for (UUID uuid : allPets) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity pet && pet.isValid()) {
                if (pet instanceof org.bukkit.entity.Mob mob) mob.setTarget(null);
                if (pet instanceof Wolf wolf) WolfModeManager.setMode(wolf, WolfModeManager.Mode.NORMAL);
            }
        }
    }
    //endregion

    //region AGGRO LOCK CLEANUP
    /**
     * Periodic sweep that drops lock entries for wolves whose entity no longer
     * exists (chunk unloaded, admin removal, death). The listener covers the
     * chunk-unload path; this task catches the residual cases.
     */
    public static void startAggroLockCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(MyWolfs.getInstance(), () -> {
            postDuelAggroLock.entrySet().removeIf(entry -> {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entry.getKey());
                return entity == null || !entity.isValid();
            });
        }, 1200L, 1200L); // every 60 seconds
    }
    //endregion
}
package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class LevelManager implements Listener {

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity deadMob = event.getEntity();
        Player killer = deadMob.getKiller();

        if (killer == null) return;

        String lastAttackerUUID = deadMob.getPersistentDataContainer().getOrDefault(
                MyWolfs.KEY_LAST_ATTACKER, PersistentDataType.STRING, "none"
        );

        if (lastAttackerUUID.equals("none")) return;

        Wolf killerWolf = null;
        try {
            UUID wolfId = UUID.fromString(lastAttackerUUID);
            Entity entity = Bukkit.getEntity(wolfId);
            if (entity instanceof Wolf wolf && wolf.isTamed() && killer.equals(wolf.getOwner())) {
                killerWolf = wolf;
            }
        } catch (IllegalArgumentException e) {
            return;
        }

        if (killerWolf == null) return;

        var pdc = killerWolf.getPersistentDataContainer();
        int currentXp = pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
        int currentLevel = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        if (currentLevel <= 0) return;

        // --- XP based on mob type ---
        String mobType = deadMob.getType().name();
        int xpGained = MyWolfs.getInstance().getConfig().getInt("mob_xp." + mobType,
                MyWolfs.getInstance().getConfig().getInt("mob_xp.DEFAULT", 10));

        int newXp = currentXp + xpGained;

        // --- XP curve ---
        double baseXpRequired = MyWolfs.getInstance().getConfig().getDouble("leveling.base_xp_required", 100);
        double exponent = MyWolfs.getInstance().getConfig().getDouble("leveling.exponent", 1.5);

        int xpRequiredForNextLevel = (int) (Math.pow(currentLevel, exponent) * baseXpRequired);

        if (newXp >= xpRequiredForNextLevel) {
            newXp -= xpRequiredForNextLevel;
            currentLevel++;

            pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, currentLevel);
            WolfStatsManager.applyStats(killerWolf);

            MyWolfs.getInstance().sendConfigMessage(killer, "level_up", "%level%", String.valueOf(currentLevel));

            String rankHealth = pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "?");
            String rankDamage = pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "?");
            String rankSpeed = pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "?");
            String rankArmor = pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "?");
            String officialName = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
            killerWolf.setCustomName(officialName + " (Lv." + currentLevel + ")");
        }

        pdc.set(MyWolfs.KEY_XP, PersistentDataType.INTEGER, newXp);
    }
}
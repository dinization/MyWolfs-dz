package me.diniz.myWolfs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ScoreboardManager {

    // Cache the Top 3 data to avoid reading the DB constantly
    private static WolfDataManager.TopList topLevel;
    private static WolfDataManager.TopList topDamage;
    private static WolfDataManager.TopList topKills;

    // Keep track of players who currently have the scoreboard active
    private static final Set<UUID> activePlayers = new HashSet<>();

    /**
     * Updates the cache with the latest top wolves and refreshes active scoreboards.
     */
    public static void updateCache() {
        topLevel = WolfDataManager.getTopWolves("level");
        topDamage = WolfDataManager.getTopWolves("damage");
        topKills = WolfDataManager.getTopWolves("kills");

        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                applyScoreboard(p);
            }
        }
    }

    /**
     * Shows the scoreboard to the player and schedules it to hide after 15 seconds.
     */
    public static void showScoreboard(Player player) {
        activePlayers.add(player.getUniqueId());
        applyScoreboard(player);
        MyWolfs.getInstance().sendConfigMessage(player, "scoreboard_shown");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (activePlayers.contains(player.getUniqueId())) {
                    activePlayers.remove(player.getUniqueId());
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    //MyWolfs.getInstance().sendConfigMessage(player, "scoreboard_hidden");
                }
            }
        }.runTaskLater(MyWolfs.getInstance(), 400L); // 400 ticks = 20 seconds
    }

    /**
     * Constructs and applies the sidebar scoreboard to the player.
     */
    public static void applyScoreboard(Player player) {
        if (topLevel == null) return; // Wait until the cache is populated

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective("mywolfs_top", Criteria.DUMMY,
                MiniMessage.miniMessage().deserialize("<gold><bold>SERVER'S BEST WOLVES"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int interval = MyWolfs.getInstance().getConfig().getInt("settings.autosave_interval_minutes", 5);

        // Master Wolf (Level)
        obj.getScore("§e§lMaster Wolf (Level)").setScore(14);
        addTop3ToScore(obj, topLevel, 13, "§e");
        obj.getScore("§7--------------------").setScore(10);

        // Strongest Wolf (Damage)
        obj.getScore("§c§lStrongest Wolf (Dmg)").setScore(9);
        addTop3ToScore(obj, topDamage, 8, "§c");
        obj.getScore("§7--------------------").setScore(5);

        // Killer Wolf (Kills)
        obj.getScore("§4§lKiller Wolf (Kills)").setScore(4);
        addTop3ToScore(obj, topKills, 3, "§4");

        obj.getScore("§8Updates every " + interval + "m").setScore(0);

        player.setScoreboard(board);
    }

    private static void addTop3ToScore(Objective obj, WolfDataManager.TopList list, int startScore, String color) {
        int score = startScore;
        for (int i = 0; i < 3; i++) {
            String text;
            if (i < list.wolves.size()) {
                WolfDataManager.TopWolf tw = list.wolves.get(i);
                text = "§f" + (i + 1) + ". " + color + tw.name + " §7(" + tw.owner + ") §f" + tw.value;
            } else {
                text = "§f" + (i + 1) + ". §7None";
            }
            obj.getScore(text).setScore(score);
            score--;
        }
    }
}
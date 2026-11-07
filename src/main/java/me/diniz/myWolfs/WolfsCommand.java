package me.diniz.myWolfs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WolfsCommand implements CommandExecutor {

    private final HashMap<UUID, Long> callCooldowns = new HashMap<>();
    private final HashMap<UUID, WolfDeletionRequest> pendingDeletions = new HashMap<>();
    private final HashMap<UUID, Long> foxCallCooldowns = new HashMap<>();

    private static class WolfDeletionRequest {
        UUID wolfUUID;
        long timestamp;
        public WolfDeletionRequest(UUID wolfUUID, long timestamp) {
            this.wolfUUID = wolfUUID;
            this.timestamp = timestamp;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        String subCommand = args.length > 0 ? args[0].toLowerCase() : "stats";

        //region HELP COMMAND
        if (subCommand.equals("help")) {
            MyWolfs.getInstance().sendConfigMessage(player, "help_header");
            MyWolfs.getInstance().sendConfigMessage(player, "help_stats");
            MyWolfs.getInstance().sendConfigMessage(player, "help_list");
            MyWolfs.getInstance().sendConfigMessage(player, "help_scoreboard");
            int cooldown = MyWolfs.getInstance().getConfig().getInt("settings.call_cooldown_seconds", 30);
            MyWolfs.getInstance().sendConfigMessage(player, "help_call", "%cooldown%", String.valueOf(cooldown));

            MyWolfs.getInstance().sendConfigMessage(player, "help_foxcall");
            MyWolfs.getInstance().sendConfigMessage(player, "help_foxlist");
            MyWolfs.getInstance().sendConfigMessage(player, "help_foxrename");

            MyWolfs.getInstance().sendConfigMessage(player, "help_mode");
            MyWolfs.getInstance().sendConfigMessage(player, "help_rename");
            MyWolfs.getInstance().sendConfigMessage(player, "help_remove");

            if (player.hasPermission("mywolfs.admin")) {
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_header");
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_setlevel");
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_forcerm");
            }

            MyWolfs.getInstance().sendConfigMessage(player, "help_upgrade_info");
            MyWolfs.getInstance().sendConfigMessage(player, "help_footer");
            return true;
        }
        //endregion

        //region SCOREBOARD COMMAND
        if (subCommand.equals("scoreboard")) {
            ScoreboardManager.showScoreboard(player);
            return true;
        }
        //endregion

        //region CALL COMMAND
        if (subCommand.equals("call")) {
            if (args.length < 2) {
                MyWolfs.getInstance().sendConfigMessage(player, "usage_call");
                return true;
            }

            String targetName = args[1];
            FileConfiguration db = WolfDataManager.getConfig();

            String wolfUUIDString = null;
            String wolfPath = null;
            for (String key : db.getConfigurationSection("wolves").getKeys(false)) {
                String path = "wolves." + key;
                if (targetName.equalsIgnoreCase(db.getString(path + ".name"))) {
                    if (db.getString(path + ".owner").equals(player.getUniqueId().toString())) {
                        wolfUUIDString = key;
                        wolfPath = path;
                        break;
                    }
                }
            }

            if (wolfUUIDString == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                return true;
            }

            int cooldownSeconds = MyWolfs.getInstance().getConfig().getInt("settings.call_cooldown_seconds", 30);
            long currentTime = System.currentTimeMillis();
            long lastUse = callCooldowns.getOrDefault(player.getUniqueId(), 0L);
            long timePassed = (currentTime - lastUse) / 1000;

            if (timePassed < cooldownSeconds) {
                long timeLeft = cooldownSeconds - timePassed;
                MyWolfs.getInstance().sendConfigMessage(player, "call_cooldown", "%time%", String.valueOf(timeLeft));
                return true;
            }

            UUID wolfUUID = UUID.fromString(wolfUUIDString);
            Wolf wolf = (Wolf) Bukkit.getEntity(wolfUUID);

            if (wolf != null && wolf.isValid()) {
                performTeleport(player, wolf, targetName, currentTime);
            } else {
                String worldName = db.getString(wolfPath + ".world");
                int chunkX = db.getInt(wolfPath + ".chunk_x");
                int chunkZ = db.getInt(wolfPath + ".chunk_z");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    MyWolfs.getInstance().sendConfigMessage(player, "world_not_loaded");
                    return true;
                }

                MyWolfs.getInstance().sendConfigMessage(player, "loading_wolf");

                final String finalWolfPath = wolfPath;
                CompletableFuture<Chunk> futureChunk = world.getChunkAtAsyncUrgently(chunkX, chunkZ);
                futureChunk.thenAccept(chunk -> {
                    Bukkit.getScheduler().runTask(MyWolfs.getInstance(), () -> {
                        Wolf loadedWolf = (Wolf) Bukkit.getEntity(wolfUUID);
                        chunk.removePluginChunkTicket(MyWolfs.getInstance());

                        if (loadedWolf != null && loadedWolf.isValid()) {
                            performTeleport(player, loadedWolf, targetName, currentTime);
                        } else {
                            MyWolfs.getInstance().sendConfigMessage(player, "call_not_found");
                            db.set(finalWolfPath, null);
                            WolfDataManager.saveFile();
                        }
                    });
                });
            }
            return true;
        }
        //endregion

        //region FOXCALL COMMAND
        if (subCommand.equals("foxcall")) {
            if (args.length < 2) {
                MyWolfs.getInstance().sendConfigMessage(player, "usage_foxcall");
                return true;
            }

            // Check Fox Cooldown
            int cooldownSeconds = MyWolfs.getInstance().getConfig().getInt("settings.fox_call_cooldown_seconds", 30);
            long currentTime = System.currentTimeMillis();
            long lastUse = foxCallCooldowns.getOrDefault(player.getUniqueId(), 0L);
            long timePassed = (currentTime - lastUse) / 1000;

            if (timePassed < cooldownSeconds) {
                long timeLeft = cooldownSeconds - timePassed;
                MyWolfs.getInstance().sendConfigMessage(player, "fox_call_cooldown", "%time%", String.valueOf(timeLeft));
                return true;
            }

            String targetName = args[1];
            String playerUUID = player.getUniqueId().toString();
            var db = FoxDataManager.getConfig();

            if (db.getConfigurationSection("foxes") == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "fox_not_found");
                return true;
            }

            String foxPath = null;
            String foxUUIDStr = null;

            for (String key : db.getConfigurationSection("foxes").getKeys(false)) {
                String path = "foxes." + key;
                if (playerUUID.equals(db.getString(path + ".owner")) && targetName.equalsIgnoreCase(db.getString(path + ".name"))) {
                    foxPath = path;
                    foxUUIDStr = key;
                    break;
                }
            }

            if (foxPath == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "fox_not_found");
                return true;
            }

            UUID foxUUID = UUID.fromString(foxUUIDStr);
            Fox fox = (Fox) Bukkit.getEntity(foxUUID);

            if (fox != null && fox.isValid()) {
                // Fox is loaded
                fox.teleport(player.getLocation());
                MyWolfs.getInstance().sendConfigMessage(player, "fox_call_success", "%name%", targetName);
                foxCallCooldowns.put(player.getUniqueId(), currentTime);
            } else {
                // Fox is unloaded, load chunk asynchronously
                String worldName = db.getString(foxPath + ".world");
                int chunkX = db.getInt(foxPath + ".chunk_x");
                int chunkZ = db.getInt(foxPath + ".chunk_z");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    MyWolfs.getInstance().sendConfigMessage(player, "world_not_loaded");
                    return true;
                }

                MyWolfs.getInstance().sendConfigMessage(player, "loading_fox");

                final String finalFoxPath = foxPath;
                CompletableFuture<Chunk> futureChunk = world.getChunkAtAsyncUrgently(chunkX, chunkZ);
                futureChunk.thenAccept(chunk -> {
                    Bukkit.getScheduler().runTask(MyWolfs.getInstance(), () -> {
                        Fox loadedFox = (Fox) Bukkit.getEntity(foxUUID);
                        chunk.removePluginChunkTicket(MyWolfs.getInstance());

                        if (loadedFox != null) {
                            loadedFox.teleport(player.getLocation());
                            MyWolfs.getInstance().sendConfigMessage(player, "fox_call_success", "%name%", targetName);
                            foxCallCooldowns.put(player.getUniqueId(), currentTime);
                        } else {
                            MyWolfs.getInstance().sendConfigMessage(player, "fox_not_found");
                            // Clean up DB if the fox is truly gone
                            db.set(finalFoxPath, null);
                            FoxDataManager.saveFile();
                        }
                    });
                });
            }
            return true;
        }
        //endregion

        //region FOXRENAME COMMAND
        if (subCommand.equals("foxrename")) {
            if (args.length < 3) {
                MyWolfs.getInstance().sendConfigMessage(player, "usage_foxrename");
                return true;
            }

            String oldName = args[1];
            String newName = args[2];
            String playerUUID = player.getUniqueId().toString();
            var db = FoxDataManager.getConfig();

            if (db.getConfigurationSection("foxes") == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "fox_not_found");
                return true;
            }

            String foxPath = null;
            String foxUUIDStr = null;

            for (String key : db.getConfigurationSection("foxes").getKeys(false)) {
                String path = "foxes." + key;
                if (playerUUID.equals(db.getString(path + ".owner")) && oldName.equalsIgnoreCase(db.getString(path + ".name"))) {
                    foxPath = path;
                    foxUUIDStr = key;
                    break;
                }
            }

            if (foxPath == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "fox_not_found");
                return true;
            }

            for (String key : db.getConfigurationSection("foxes").getKeys(false)) {
                String path = "foxes." + key;
                if (playerUUID.equals(db.getString(path + ".owner")) && newName.equalsIgnoreCase(db.getString(path + ".name"))) {
                    MyWolfs.getInstance().sendConfigMessage(player, "fox_name_in_use", "%name%", newName);
                    return true;
                }
            }

            db.set(foxPath + ".name", newName);
            FoxDataManager.saveFile();

            UUID foxUUID = UUID.fromString(foxUUIDStr);
            Fox fox = (Fox) Bukkit.getEntity(foxUUID);

            if (fox != null && fox.isValid()) {
                fox.customName(net.kyori.adventure.text.Component.text(newName));
            }

            MyWolfs.getInstance().sendConfigMessage(player, "fox_renamed", "%name%", newName);
            return true;
        }
        //endregion

        //region FOXLIST COMMAND
        if (subCommand.equals("foxlist")) {
            for (Fox fox : player.getWorld().getEntitiesByClass(Fox.class)) {
                String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
                if (player.getUniqueId().toString().equals(ownerUUID)) {
                    FoxDataManager.updateFox(fox);
                }
            }

            List<String> foxPaths = FoxDataManager.getPlayerFoxes(player);

            if (foxPaths.isEmpty()) {
                MyWolfs.getInstance().sendConfigMessage(player, "fox_list_empty");
                return true;
            }

            MyWolfs.getInstance().sendConfigMessage(player, "fox_list_header", "%count%", String.valueOf(foxPaths.size()));

            for (String path : foxPaths) {
                String name = FoxDataManager.getConfig().getString(path + ".name", "Unknown");

                String worldName = FoxDataManager.getConfig().getString(path + ".world", "world");
                if (worldName.equals("world")) worldName = "Overworld";
                else if (worldName.equals("world_nether")) worldName = "Nether";
                else if (worldName.equals("world_the_end")) worldName = "The End";

                String formattedItem = MyWolfs.getInstance().getConfig().getString("messages.fox_list_item", "<yellow>%name% - %world%");
                formattedItem = formattedItem.replace("%name%", name)
                        .replace("%world%", worldName);

                player.sendMessage(MiniMessage.miniMessage().deserialize(formattedItem));
            }
            return true;
        }
        //endregion

        //region ADMIN COMMANDS
        if (subCommand.equals("admin")) {
            if (!player.hasPermission("mywolfs.admin")) {
                MyWolfs.getInstance().sendConfigMessage(player, "no_permission");
                return true;
            }

            if (args.length < 2) {
                MyWolfs.getInstance().sendConfigMessage(player, "usage_admin");
                return true;
            }

            String adminAction = args[1].toLowerCase();

            if (adminAction.equals("setlevel")) {
                if (args.length < 4) {
                    MyWolfs.getInstance().sendConfigMessage(player, "usage_setlevel");
                    return true;
                }
                String targetName = args[2];
                int newLevel;
                try {
                    newLevel = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    MyWolfs.getInstance().sendConfigMessage(player, "level_not_number");
                    return true;
                }

                Wolf wolf = findWolfByName(player, targetName);
                if (wolf == null) {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                    return true;
                }

                var pdc = wolf.getPersistentDataContainer();
                pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, newLevel);
                WolfStatsManager.applyStats(wolf);
                WolfDataManager.updateWolf(wolf);

                String officialName = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                wolf.customName(net.kyori.adventure.text.Component.text(officialName + " (Lv." + newLevel + ")"));

                MyWolfs.getInstance().sendConfigMessage(player, "level_set", "%name%", officialName, "%level%", String.valueOf(newLevel));
                return true;
            }

            if (adminAction.equals("forcerm") || adminAction.equals("delete") || adminAction.equals("frm")) {
                Wolf wolf;
                if (args.length >= 3) {
                    wolf = findWolfByName(player, args[2]);
                } else {
                    wolf = getWolfInSight(player);
                }

                if (wolf == null) {
                    if (args.length >= 3 && WolfDataManager.isNameTakenInDB(player, args[2])) {
                        MyWolfs.getInstance().sendConfigMessage(player, "wolf_unloaded_remove", "%name%", args[2]);
                    } else {
                        MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                    }
                    return true;
                }

                String officialName = wolf.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                wolf.remove();
                WolfDataManager.deleteWolf(wolf);
                MyWolfs.getInstance().sendConfigMessage(player, "admin_removed", "%name%", officialName);
                return true;
            }

            MyWolfs.getInstance().sendConfigMessage(player, "unknown_admin_action");
            return true;
        }
        //endregion

        //region LIST COMMAND
        if (subCommand.equals("list")) {
            for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed() && player.equals(wolf.getOwner())) {
                    WolfDataManager.updateWolf(wolf);
                }
            }

            List<String> wolfPaths = WolfDataManager.getPlayerWolves(player);

            if (wolfPaths.isEmpty()) {
                MyWolfs.getInstance().sendConfigMessage(player, "list_empty");
                return true;
            }

            MyWolfs.getInstance().sendConfigMessage(player, "list_header", "%count%", String.valueOf(wolfPaths.size()));

            for (String path : wolfPaths) {
                String name = WolfDataManager.getConfig().getString(path + ".name", "Unknown");
                int level = WolfDataManager.getConfig().getInt(path + ".level", 1);

                String worldName = WolfDataManager.getConfig().getString(path + ".world", "world");
                if (worldName.equals("world")) worldName = "Overworld";
                else if (worldName.equals("world_nether")) worldName = "Nether";
                else if (worldName.equals("world_the_end")) worldName = "The End";

                String formattedItem = MyWolfs.getInstance().getConfig().getString("messages.list_item", "<yellow>%name% (Lv.%level%) - %world%");
                formattedItem = formattedItem.replace("%name%", name)
                        .replace("%level%", String.valueOf(level))
                        .replace("%world%", worldName);

                player.sendMessage(MiniMessage.miniMessage().deserialize(formattedItem));
            }
            return true;
        }
        //endregion

        //region REMOVE COMMAND
        if (subCommand.equals("remove") || subCommand.equals("delete")) {
            Wolf wolf = findWolf(player, args);
            if (wolf == null) {
                String nameToFind = args.length > 1 ? args[1] : null;
                if (nameToFind != null && WolfDataManager.isNameTakenInDB(player, nameToFind)) {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_unloaded_remove", "%name%", nameToFind);
                } else {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                }
                return true;
            }

            pendingDeletions.put(player.getUniqueId(), new WolfDeletionRequest(wolf.getUniqueId(), System.currentTimeMillis()));

            String wolfName = wolf.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
            MyWolfs.getInstance().sendConfigMessage(player, "confirm_remove", "%name%", wolfName);
            return true;
        }
        //endregion

        //region CONFIRM COMMAND
        if (subCommand.equals("confirm")) {
            WolfDeletionRequest request = pendingDeletions.get(player.getUniqueId());

            if (request == null || (System.currentTimeMillis() - request.timestamp) > 60000) {
                pendingDeletions.remove(player.getUniqueId());
                MyWolfs.getInstance().sendConfigMessage(player, "confirm_expired");
                return true;
            }

            pendingDeletions.remove(player.getUniqueId());
            Wolf wolf = (Wolf) Bukkit.getEntity(request.wolfUUID);

            if (wolf != null && wolf.isValid()) {
                String name = wolf.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                wolf.remove();
                WolfDataManager.deleteWolf(wolf);
                MyWolfs.getInstance().sendConfigMessage(player, "wolf_removed", "%name%", name);
            } else {
                MyWolfs.getInstance().sendConfigMessage(player, "confirm_unloaded");
            }
            return true;
        }
        //endregion

        //region RENAME COMMAND
        if (subCommand.equals("rename")) {
            if (args.length < 2) {
                MyWolfs.getInstance().sendConfigMessage(player, "usage_rename");
                return true;
            }

            Wolf wolf;
            String newName;

            if (args.length == 2) {
                wolf = getWolfInSight(player);
                newName = args[1];
            } else {
                wolf = findWolfByName(player, args[1]);
                newName = args[2];
            }

            if (wolf == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                return true;
            }

            if (isNameTaken(player, newName, wolf)) {
                MyWolfs.getInstance().sendConfigMessage(player, "name_already_in_use", "%name%", newName);
                return true;
            }

            var pdc = wolf.getPersistentDataContainer();
            pdc.set(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, newName);

            int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
            wolf.customName(net.kyori.adventure.text.Component.text(newName + " (Lv." + level + ")"));

            WolfDataManager.updateWolf(wolf);
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_renamed", "%name%", newName);
            return true;
        }
        //endregion

        //region MODE COMMAND
        if (subCommand.equals("mode")) {
            if (args.length < 2) {
                MyWolfs.getInstance().sendConfigMessage(player, "usage_mode");
                return true;
            }

            String modeArg = args[1].toLowerCase();
            WolfModeManager.Mode targetMode = null;

            if (modeArg.equals("agg") || modeArg.equals("aggressive")) {
                targetMode = WolfModeManager.Mode.AGGRESSIVE;
            } else if (modeArg.equals("normal")) {
                targetMode = WolfModeManager.Mode.NORMAL;
            } else {
                MyWolfs.getInstance().sendConfigMessage(player, "invalid_mode");
                return true;
            }

            // CHECK: Did the player specify a name?
            if (args.length >= 3) {
                // Specific wolf by name
                Wolf wolf = findWolfByName(player, args[2]);
                if (wolf == null) {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                    return true;
                }
                WolfModeManager.setMode(wolf, targetMode);
                MyWolfs.getInstance().sendConfigMessage(player, targetMode == WolfModeManager.Mode.AGGRESSIVE ? "mode_aggressive" : "mode_normal");
            } else {
                // AREA OF EFFECT: Apply to all nearby owned wolves
                int count = 0;
                for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                    if (entity instanceof Wolf wolf && player.equals(wolf.getOwner())) {
                        WolfModeManager.setMode(wolf, targetMode);
                        count++;
                    }
                }

                if (count == 0) {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                } else {
                    if (targetMode == WolfModeManager.Mode.AGGRESSIVE) {
                        MyWolfs.getInstance().sendConfigMessage(player, "mode_aggressive_area", "%count%", String.valueOf(count));
                    } else {
                        MyWolfs.getInstance().sendConfigMessage(player, "mode_normal_area", "%count%", String.valueOf(count));
                    }
                }
            }
            return true;
        }
        //endregion

        //region STATS COMMAND (Replaces old stats/ranks)
        if (!subCommand.equals("stats")) {
            MyWolfs.getInstance().sendConfigMessage(player, "unknown_subcommand");
            return true;
        }

        Wolf wolf = findWolf(player, args);

        // 1. If the wolf is loaded
        if (wolf != null) {
            var pdc = wolf.getPersistentDataContainer();
            int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
            int xp = pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
            int kills = pdc.getOrDefault(MyWolfs.KEY_KILLS, PersistentDataType.INTEGER, 0);
            String name = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");

            String h = pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "?");
            String d = pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "?");
            String s = pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "?");
            String a = pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "?");
            String requiredItem = pdc.getOrDefault(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, "DIAMOND");
            double maxHp = wolf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            double currentHp = wolf.getHealth();
            double dmg = wolf.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue();
            double spd = wolf.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getValue() * 100;
            double arm = wolf.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue();

            displayStats(player, name, level, xp, h, d, s, a, requiredItem, kills, currentHp, maxHp, dmg, spd, arm);
            return true;
        }

        // 2. If the wolf is unloaded (read from DB)
        if (args.length < 2) {
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
            return true;
        }

        String targetName = args[1];
        String wolfPath = null;
        var db = WolfDataManager.getConfig();

        if (db.getConfigurationSection("wolves") != null) {
            for (String key : db.getConfigurationSection("wolves").getKeys(false)) {
                String path = "wolves." + key;
                if (targetName.equalsIgnoreCase(db.getString(path + ".name")) && player.getUniqueId().toString().equals(db.getString(path + ".owner"))) {
                    wolfPath = path;
                    break;
                }
            }
        }

        if (wolfPath == null) {
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
            return true;
        }

        int dbKills = db.getInt(wolfPath + ".kills", 0);
        String dbRequiredItem = db.getString(wolfPath + ".upgrade_item", "DIAMOND");
        double dbMaxHp = db.getDouble(wolfPath + ".stats.health", 20.0);
        double dbDmg = db.getDouble(wolfPath + ".stats.damage", 4.0);
        double dbSpd = db.getDouble(wolfPath + ".stats.speed", 0.3) * 100;
        double dbArm = db.getDouble(wolfPath + ".stats.armor", 0.0);

        displayStats(player,
                db.getString(wolfPath + ".name", targetName),
                db.getInt(wolfPath + ".level", 1),
                db.getInt(wolfPath + ".xp", 0),
                db.getString(wolfPath + ".ranks.health", "?"),
                db.getString(wolfPath + ".ranks.damage", "?"),
                db.getString(wolfPath + ".ranks.speed", "?"),
                db.getString(wolfPath + ".ranks.armor", "?"),
                dbRequiredItem,
                dbKills,
                dbMaxHp, dbMaxHp, dbDmg, dbSpd, dbArm
        );
        return true;
        //endregion
    }

    //region HELPER METHODS
    private void displayStats(Player player, String name, int level, int xp, String h, String d, String s, String a,
                              String requiredItem, int kills, double currentHp, double maxHp, double dmg, double spd, double arm) {
        double baseXpRequired = MyWolfs.getInstance().getConfig().getDouble("leveling.base_xp_required", 100);
        double exponent = MyWolfs.getInstance().getConfig().getDouble("leveling.exponent", 1.5);
        int xpNeeded = (int) (Math.pow(level, exponent) * baseXpRequired);

        // Header
        MyWolfs.getInstance().sendConfigMessage(player, "stats_header", "%name%", name, "%level%", String.valueOf(level));

        // XP and Kills
        if (level > 0) {
            MyWolfs.getInstance().sendConfigMessage(player, "stats_xp", "%xp%", String.valueOf(xp), "%xp_needed%", String.valueOf(xpNeeded));
        }
        MyWolfs.getInstance().sendConfigMessage(player, "stats_kills", "%kills%", String.valueOf(kills));

        // Ranks in one line
        MyWolfs.getInstance().sendConfigMessage(player, "stats_ranks_line",
                "%h%", h, "%d%", d, "%s%", s, "%a%", a);

        // Numeric Attributes
        MyWolfs.getInstance().sendConfigMessage(player, "stats_numeric_header");
        MyWolfs.getInstance().sendConfigMessage(player, "stats_numeric_health",
                "%current%", String.format("%.2f", currentHp),
                "%max%", String.format("%.2f", maxHp));
        MyWolfs.getInstance().sendConfigMessage(player, "stats_numeric_damage", "%damage%", String.format("%.2f", dmg));
        MyWolfs.getInstance().sendConfigMessage(player, "stats_numeric_speed", "%speed%", String.format("%.2f", spd));
        MyWolfs.getInstance().sendConfigMessage(player, "stats_numeric_armor", "%armor%", String.format("%.2f", arm));

        // Required item (if Level 0)
        if (level == 0 && requiredItem != null && !requiredItem.isEmpty()) {
            MyWolfs.getInstance().sendConfigMessage(player, "stats_required_item", "%item%", requiredItem);
        }
    }

    private void performTeleport(Player player, Wolf wolf, String wolfName, long currentTime) {
        wolf.setSitting(false);
        wolf.teleport(player.getLocation());
        callCooldowns.put(player.getUniqueId(), currentTime);

        var pdc = wolf.getPersistentDataContainer();
        int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 0);

        if (level == 0) {
            String requiredItem = pdc.getOrDefault(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, "DIAMOND");
            MyWolfs.getInstance().sendConfigMessage(player, "call_success_overlimit",
                    "%name%", wolfName,
                    "%item%", requiredItem
            );
        } else {
            MyWolfs.getInstance().sendConfigMessage(player, "call_success", "%name%", wolfName);
        }
    }

    private Wolf findWolf(Player player, String[] args) {
        if (args.length > 1) {
            return findWolfByName(player, args[1]);
        }
        return getWolfInSight(player);
    }

    private Wolf getWolfInSight(Player player) {
        if (player.getTargetEntity(5) instanceof Wolf wolf && wolf.isTamed() && wolf.getOwner().equals(player)) {
            return wolf;
        }
        return null;
    }

    private Wolf findWolfByName(Player player, String name) {
        for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
            if (wolf.isTamed() && player.equals(wolf.getOwner())) {
                String wolfName = wolf.getPersistentDataContainer().getOrDefault(
                        MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf"
                );
                if (wolfName.equalsIgnoreCase(name)) {
                    return wolf;
                }
            }
        }
        return null;
    }

    private boolean isNameTaken(Player player, String name, Wolf excludeWolf) {
        for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
            if (wolf.isTamed() && player.equals(wolf.getOwner()) && !wolf.equals(excludeWolf)) {
                String wolfName = wolf.getPersistentDataContainer().getOrDefault(
                        MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf"
                );
                if (wolfName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
    //endregion
}
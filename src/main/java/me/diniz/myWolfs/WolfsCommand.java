package me.diniz.myWolfs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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

        String subCommand = args.length > 0 ? args[0].toLowerCase() : "ranks";

        //region HELP COMMAND
        if (subCommand.equals("help")) {
            MyWolfs.getInstance().sendConfigMessage(player, "help_header");
            MyWolfs.getInstance().sendConfigMessage(player, "help_ranks");
            MyWolfs.getInstance().sendConfigMessage(player, "help_stats");
            MyWolfs.getInstance().sendConfigMessage(player, "help_list");

            int cooldown = MyWolfs.getInstance().getConfig().getInt("settings.call_cooldown_seconds", 30);
            MyWolfs.getInstance().sendConfigMessage(player, "help_call", "%cooldown%", String.valueOf(cooldown));


            MyWolfs.getInstance().sendConfigMessage(player, "help_mode");
            MyWolfs.getInstance().sendConfigMessage(player, "help_rename");
            MyWolfs.getInstance().sendConfigMessage(player, "help_remove");

            if (player.hasPermission("mywolfs.admin")) {
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_header");
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_setlevel");
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_forcerm");
            }

            // footer tip explaining the upgrade system
            MyWolfs.getInstance().sendConfigMessage(player, "help_upgrade_info");
            MyWolfs.getInstance().sendConfigMessage(player, "help_footer");
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
                wolf.setCustomName(officialName + " (Lv." + newLevel + ")");

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
            wolf.setCustomName(newName + " (Lv." + level + ")");

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

            Wolf wolf;
            if (args.length >= 3) {
                wolf = findWolfByName(player, args[2]);
            } else {
                wolf = getWolfInSight(player);
            }

            if (wolf == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                return true;
            }

            String modeArg = args[1].toLowerCase();

            if (modeArg.equals("agg") || modeArg.equals("aggressive")) {
                if (WolfModeManager.getMode(wolf) == WolfModeManager.Mode.AGGRESSIVE) {
                    MyWolfs.getInstance().sendConfigMessage(player, "already_aggressive");
                } else {
                    WolfModeManager.setMode(wolf, WolfModeManager.Mode.AGGRESSIVE);
                    MyWolfs.getInstance().sendConfigMessage(player, "mode_aggressive");
                }
            } else if (modeArg.equals("normal")) {
                if (WolfModeManager.getMode(wolf) == WolfModeManager.Mode.NORMAL) {
                    MyWolfs.getInstance().sendConfigMessage(player, "already_normal");
                } else {
                    WolfModeManager.setMode(wolf, WolfModeManager.Mode.NORMAL);
                    MyWolfs.getInstance().sendConfigMessage(player, "mode_normal");
                }
            } else {
                MyWolfs.getInstance().sendConfigMessage(player, "invalid_mode");
            }
            return true;
        }
        //endregion

        //region RANKS / STATS COMMAND
        if (!subCommand.equals("ranks") && !subCommand.equals("stats")) {
            MyWolfs.getInstance().sendConfigMessage(player, "unknown_subcommand");
            return true;
        }

        Wolf wolf = findWolf(player, args);

        if (wolf != null) {
            var pdc = wolf.getPersistentDataContainer();
            int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
            int xp = pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
            String name = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");

            if (subCommand.equals("stats")) {
                double currentHp = wolf.getHealth();
                double maxHp = wolf.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                double dmg = wolf.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue();
                double spd = wolf.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getValue() * 100;
                double arm = wolf.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue();

                player.sendMessage("§6=== " + name + " (Live) ===");
                player.sendMessage(String.format("§cHealth: §f%.2f / %.2f", currentHp, maxHp));
                player.sendMessage(String.format("§4Damage: §f%.2f", dmg));
                player.sendMessage(String.format("§aSpeed: §f%.2f", spd));
                player.sendMessage(String.format("§7Armor: §f%.2f", arm));
            } else {
                String h = pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "?");
                String d = pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "?");
                String s = pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "?");
                String a = pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "?");
                String requiredItem = pdc.getOrDefault(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, "DIAMOND");

                displayRanks(player, name, level, xp, h, d, s, a, requiredItem);
            }
            return true;
        }

        // WOLF IS NOT LOADED: READ EVERYTHING FROM DB
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

        if (subCommand.equals("stats")) {
            player.sendMessage("§6=== " + targetName + " (From DB) ===");
            player.sendMessage(String.format("§cHealth: §f%.2f", db.getDouble(wolfPath + ".stats.health", 20.0)));
            player.sendMessage(String.format("§4Damage: §f%.2f", db.getDouble(wolfPath + ".stats.damage", 4.0)));
            player.sendMessage(String.format("§aSpeed: §f%.2f", db.getDouble(wolfPath + ".stats.speed", 0.3) * 100));
            player.sendMessage(String.format("§7Armor: §f%.2f", db.getDouble(wolfPath + ".stats.armor", 0.0)));
        } else {
            String requiredItem = db.getString(wolfPath + ".upgrade_item", "DIAMOND");

            displayRanks(player,
                    db.getString(wolfPath + ".name", targetName),
                    db.getInt(wolfPath + ".level", 1),
                    db.getInt(wolfPath + ".xp", 0),
                    db.getString(wolfPath + ".ranks.health", "?"),
                    db.getString(wolfPath + ".ranks.damage", "?"),
                    db.getString(wolfPath + ".ranks.speed", "?"),
                    db.getString(wolfPath + ".ranks.armor", "?"),
                    requiredItem
            );
        }
        return true;
        //endregion
    }

    //region HELPER METHODS
    private void displayRanks(Player player, String name, int level, int xp, String h, String d, String s, String a, String requiredItem) {
        double baseXpRequired = MyWolfs.getInstance().getConfig().getDouble("leveling.base_xp_required", 100);
        double exponent = MyWolfs.getInstance().getConfig().getDouble("leveling.exponent", 1.5);
        int xpNeeded = (int) (Math.pow(level, exponent) * baseXpRequired);

        player.sendMessage("§6=== " + name + " ===");
        player.sendMessage("§eLevel: §f" + level);

        if (level > 0) {
            player.sendMessage("§bXP: §f" + xp + " / " + xpNeeded);
        }

        player.sendMessage("§cHealth: §fRank " + h);
        player.sendMessage("§4Damage: §fRank " + d);
        player.sendMessage("§aSpeed: §fRank " + s);
        player.sendMessage("§7Armor: §fRank " + a);

        if (level == 0 && requiredItem != null && !requiredItem.isEmpty()) {
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_overlimit_info", "%item%", requiredItem);
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
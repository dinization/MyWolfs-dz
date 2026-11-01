package me.diniz.myWolfs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WolfsCommand implements CommandExecutor {

    private final HashMap<UUID, Long> callCooldowns = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        String subCommand = args.length > 0 ? args[0].toLowerCase() : "ranks";

        // --- HELP COMMAND ---
        if (subCommand.equals("help")) {
            MyWolfs.getInstance().sendConfigMessage(player, "help_header");
            MyWolfs.getInstance().sendConfigMessage(player, "help_ranks");
            MyWolfs.getInstance().sendConfigMessage(player, "help_stats");
            MyWolfs.getInstance().sendConfigMessage(player, "help_list");

            int cooldown = MyWolfs.getInstance().getConfig().getInt("settings.call_cooldown_seconds", 30);
            MyWolfs.getInstance().sendConfigMessage(player, "help_call", "%cooldown%", String.valueOf(cooldown));

            MyWolfs.getInstance().sendConfigMessage(player, "help_rename");
            MyWolfs.getInstance().sendConfigMessage(player, "help_remove");

            if (player.hasPermission("mywolfs.admin")) {
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_header");
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_setlevel");
                MyWolfs.getInstance().sendConfigMessage(player, "help_admin_forcerm");
            }

            MyWolfs.getInstance().sendConfigMessage(player, "help_footer");
            return true;
        }

        // --- CALL COMMAND ---
        if (subCommand.equals("call")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /wolfs call <name>");
                return true;
            }

            String targetName = args[1];
            FileConfiguration db = WolfDataManager.getConfig();

            // 1. Find the wolf UUID by name in the database
            String wolfUUIDString = null;
            String wolfPath = null;
            for (String key : db.getConfigurationSection("wolves").getKeys(false)) {
                String path = "wolves." + key;
                if (targetName.equalsIgnoreCase(db.getString(path + ".name"))) {
                    // Check if it belongs to this player
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

            // 2. Check Cooldown
            int cooldownSeconds = MyWolfs.getInstance().getConfig().getInt("settings.call_cooldown_seconds", 30);
            long currentTime = System.currentTimeMillis();
            long lastUse = callCooldowns.getOrDefault(player.getUniqueId(), 0L);
            long timePassed = (currentTime - lastUse) / 1000;

            if (timePassed < cooldownSeconds) {
                long timeLeft = cooldownSeconds - timePassed;
                MyWolfs.getInstance().sendConfigMessage(player, "call_cooldown", "%time%", String.valueOf(timeLeft));
                return true;
            }

            // 3. Try to find the entity in memory
            UUID wolfUUID = UUID.fromString(wolfUUIDString);
            Wolf wolf = (Wolf) Bukkit.getEntity(wolfUUID);

            if (wolf != null && wolf.isValid()) {
                // Wolf is loaded! Teleport immediately.
                performTeleport(player, wolf, targetName, currentTime);
            } else {
                // Wolf is NOT loaded. We need to load the chunk.
                String worldName = db.getString(wolfPath + ".world");
                int chunkX = db.getInt(wolfPath + ".chunk_x");
                int chunkZ = db.getInt(wolfPath + ".chunk_z");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    player.sendMessage("§cThe world where the wolf is located is not loaded!");
                    return true;
                }

                player.sendMessage("§7Loading your wolf from the world...");

                // Create a final copy of wolfPath to use inside the lambda
                final String finalWolfPath = wolfPath;
                // Load chunk asynchronously to avoid lag
                CompletableFuture<Chunk> futureChunk = world.getChunkAtAsyncUrgently(chunkX, chunkZ);
                futureChunk.thenAccept(chunk -> {
                    // Back to the main thread to do Bukkit stuff
                    Bukkit.getScheduler().runTask(MyWolfs.getInstance(), () -> {
                        Wolf loadedWolf = (Wolf) Bukkit.getEntity(wolfUUID);

                        // 1. Release the chunk first (so it can be unloaded later, even if we fail)
                        chunk.removePluginChunkTicket(MyWolfs.getInstance());

                        // 2. Then, check if the wolf was found
                        if (loadedWolf != null && loadedWolf.isValid()) {
                            performTeleport(player, loadedWolf, targetName, currentTime);
                        } else {
                            player.sendMessage("§cCould not find your wolf. It might have died or despawned.");
                            // Remove from database to clean up
                            db.set(finalWolfPath, null);
                            WolfDataManager.saveFile();
                        }
                    });
                });
            }
            return true;
        }

        // --- ADMIN COMMANDS ---
        if (subCommand.equals("admin")) {
            if (!player.hasPermission("mywolfs.admin")) {
                MyWolfs.getInstance().sendConfigMessage(player, "no_permission");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /wolfs admin <setlevel|forcerm> [args]");
                return true;
            }

            String adminAction = args[1].toLowerCase();

            if (adminAction.equals("setlevel")) {
                if (args.length < 4) {
                    player.sendMessage("§cUsage: /wolfs admin setlevel <name> <level>");
                    return true;
                }
                String targetName = args[2];
                int newLevel;
                try {
                    newLevel = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cLevel must be a number.");
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
                WolfDataManager.updateWolf(wolf); // Update database

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
                    // Check if the wolf is in the database but unloaded
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

            player.sendMessage("§cUnknown admin action. Use setlevel or forcerm.");
            return true;
        }

        // --- LIST COMMAND ---
        if (subCommand.equals("list")) {
            // SYNC: Update the DB for all loaded wolves first, so levels are up-to-date
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

                // Get the world name and format it nicely
                String worldName = WolfDataManager.getConfig().getString(path + ".world", "world");
                if (worldName.equals("world")) worldName = "Overworld";
                else if (worldName.equals("world_nether")) worldName = "Nether";
                else if (worldName.equals("world_the_end")) worldName = "The End";

                // Send message
                String formattedItem = MyWolfs.getInstance().getConfig().getString("messages.list_item", "<yellow>%name% (Lv.%level%) - %world%");
                formattedItem = formattedItem.replace("%name%", name)
                        .replace("%level%", String.valueOf(level))
                        .replace("%world%", worldName);

                player.sendMessage(MiniMessage.miniMessage().deserialize(formattedItem));
            }

            return true;
        }

        // --- REMOVE COMMAND ---
        if (subCommand.equals("remove") || subCommand.equals("delete")) {
            Wolf wolf = findWolf(player, args);
            if (wolf == null) {
                // Check if the wolf is in the database but unloaded
                String nameToFind = args.length > 1 ? args[1] : null;
                if (nameToFind != null && WolfDataManager.isNameTakenInDB(player, nameToFind)) {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_unloaded_remove", "%name%", nameToFind);
                } else {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                }
                return true;
            }

            if (args.length < 3 || !args[args.length - 1].equalsIgnoreCase("confirm")) {
                String wolfName = wolf.getPersistentDataContainer().getOrDefault(
                        MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                MyWolfs.getInstance().sendConfigMessage(player, "confirm_remove", "%name%", wolfName);
                return true;
            }

            wolf.remove();
            WolfDataManager.deleteWolf(wolf);
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_removed");
            return true;
        }

        // --- RENAME COMMAND ---
        if (subCommand.equals("rename")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /wolfs rename <oldName> <newName> OR /wolfs rename <newName> (while looking at wolf)");
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

            // Update database name
            WolfDataManager.updateWolf(wolf);

            MyWolfs.getInstance().sendConfigMessage(player, "wolf_renamed", "%name%", newName);
            return true;
        }

        // --- RANKS / STATS ---
        if (!subCommand.equals("ranks") && !subCommand.equals("stats")) {
            player.sendMessage("§cUnknown subcommand. Usage: /wolfs [ranks|stats|list|call|rename|remove|help|admin]");
            return true;
        }

        Wolf wolf = findWolf(player, args);
        if (wolf == null) {
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
            return true;
        }

        if (subCommand.equals("stats")) {
            player.sendMessage("§6=== Raw Wolf Stats ===");

            AttributeInstance healthAttr = wolf.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = healthAttr != null ? healthAttr.getValue() : 20.0;
            player.sendMessage(String.format("§cHealth: §f%.2f / %.2f", wolf.getHealth(), maxHealth));

            AttributeInstance damageAttr = wolf.getAttribute(Attribute.ATTACK_DAMAGE);
            double damage = damageAttr != null ? damageAttr.getValue() : 4.0;
            player.sendMessage(String.format("§4Damage: §f%.2f", damage));

            AttributeInstance speedAttr = wolf.getAttribute(Attribute.MOVEMENT_SPEED);
            double speed = speedAttr != null ? speedAttr.getValue() : 0.3;
            player.sendMessage(String.format("§aSpeed: §f%.2f", speed * 100));

            AttributeInstance armorAttr = wolf.getAttribute(Attribute.ARMOR);
            double armor = armorAttr != null ? armorAttr.getValue() : 0.0;
            player.sendMessage(String.format("§7Armor: §f%.2f", armor));
            return true;
        }

        // Standard Ranks
        var pdc = wolf.getPersistentDataContainer();
        int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        int xp = pdc.getOrDefault(MyWolfs.KEY_XP, PersistentDataType.INTEGER, 0);
        String wolfName = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");

        String rankHealth = pdc.getOrDefault(MyWolfs.KEY_RANK_HEALTH, PersistentDataType.STRING, "?");
        String rankDamage = pdc.getOrDefault(MyWolfs.KEY_RANK_DAMAGE, PersistentDataType.STRING, "?");
        String rankSpeed = pdc.getOrDefault(MyWolfs.KEY_RANK_SPEED, PersistentDataType.STRING, "?");
        String rankArmor = pdc.getOrDefault(MyWolfs.KEY_RANK_ARMOR, PersistentDataType.STRING, "?");

        double baseXpRequired = MyWolfs.getInstance().getConfig().getDouble("leveling.base_xp_required", 100);
        double exponent = MyWolfs.getInstance().getConfig().getDouble("leveling.exponent", 1.5);
        int xpNeeded = (int) (Math.pow(level, exponent) * baseXpRequired);

        player.sendMessage("§6=== " + wolfName + " ===");
        player.sendMessage("§eLevel: §f" + level);
        player.sendMessage("§bXP: §f" + xp + " / " + xpNeeded);
        player.sendMessage("§cHealth: §fRank " + rankHealth);
        player.sendMessage("§4Damage: §fRank " + rankDamage);
        player.sendMessage("§aSpeed: §fRank " + rankSpeed);
        player.sendMessage("§7Armor: §fRank " + rankArmor);

        return true;
    }

    // --- HELPER METHODS ---

    private void performTeleport(Player player, Wolf wolf, String wolfName, long currentTime) {
        // Make the wolf stand and teleport to the player
        wolf.setSitting(false);
        wolf.teleport(player.getLocation());

        // Set cooldown
        callCooldowns.put(player.getUniqueId(), currentTime);

        MyWolfs.getInstance().sendConfigMessage(player, "call_success", "%name%", wolfName);
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
}
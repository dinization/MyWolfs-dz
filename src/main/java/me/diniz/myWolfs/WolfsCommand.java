package me.diniz.myWolfs;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class WolfsCommand implements CommandExecutor {

    // Cooldown Map: Player UUID -> Last use timestamp in milliseconds
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

            // Dynamic cooldown replacement
            int cooldown = MyWolfs.getInstance().getConfig().getInt("settings.call_cooldown_seconds", 30);
            MyWolfs.getInstance().sendConfigMessage(player, "help_call", "%cooldown%", String.valueOf(cooldown));

            MyWolfs.getInstance().sendConfigMessage(player, "help_rename");
            MyWolfs.getInstance().sendConfigMessage(player, "help_remove");

            // Only show admin commands if the player has permission
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
            Wolf wolf = findWolf(player, args);
            if (wolf == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                return true;
            }

            if (wolf.getWorld().equals(player.getWorld()) && wolf.getLocation().distanceSquared(player.getLocation()) < 25) {
                String name = wolf.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                MyWolfs.getInstance().sendConfigMessage(player, "call_already_near", "%name%", name);
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

            wolf.setSitting(false);
            wolf.teleport(player.getLocation());
            callCooldowns.put(player.getUniqueId(), currentTime);

            String name = wolf.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
            MyWolfs.getInstance().sendConfigMessage(player, "call_success", "%name%", name);
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

                String officialName = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                wolf.setCustomName(officialName + " (Lv." + newLevel + ")");

                MyWolfs.getInstance().sendConfigMessage(player, "level_set", "%name%", officialName, "%level%", String.valueOf(newLevel));
                return true;
            }

            if (adminAction.equals("forcerm") || adminAction.equals("delete")) {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /wolfs admin forcerm <name>");
                    return true;
                }
                Wolf wolf = findWolfByName(player, args[2]);
                if (wolf == null) {
                    MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                    return true;
                }

                String officialName = wolf.getPersistentDataContainer().getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                wolf.remove();
                MyWolfs.getInstance().sendConfigMessage(player, "admin_removed", "%name%", officialName);
                return true;
            }

            player.sendMessage("§cUnknown admin action. Use setlevel or forcerm.");
            return true;
        }

        // --- LIST COMMAND ---
        if (subCommand.equals("list")) {
            List<Wolf> myWolves = new ArrayList<>();
            for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
                if (wolf.isTamed() && player.equals(wolf.getOwner())) {
                    myWolves.add(wolf);
                }
            }

            if (myWolves.isEmpty()) {
                MyWolfs.getInstance().sendConfigMessage(player, "list_empty");
                return true;
            }

            MyWolfs.getInstance().sendConfigMessage(player, "list_header", "%count%", String.valueOf(myWolves.size()));

            for (Wolf wolf : myWolves) {
                var pdc = wolf.getPersistentDataContainer();
                String name = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Unknown");
                int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);

                Location loc = wolf.getLocation();
                String locationStr;

                if (loc.getWorld() != null && loc.getWorld().equals(player.getWorld())) {
                    int distance = (int) loc.distance(player.getLocation());
                    locationStr = distance + "m";
                } else if (loc.getWorld() != null) {
                    locationStr = loc.getWorld().getName();
                } else {
                    locationStr = "Unloaded";
                }

                String formattedItem = MyWolfs.getInstance().getConfig().getString("messages.list_item", "<yellow>%name% - Lv.%level% (%location%)");
                formattedItem = formattedItem.replace("%name%", name)
                        .replace("%level%", String.valueOf(level))
                        .replace("%location%", locationStr);

                player.sendMessage(MiniMessage.miniMessage().deserialize(formattedItem));
            }

            MyWolfs.getInstance().sendConfigMessage(player, "list_world_only");
            return true;
        }

        // --- REMOVE COMMAND ---
        if (subCommand.equals("remove") || subCommand.equals("delete")) {
            Wolf wolf = findWolf(player, args);
            if (wolf == null) {
                MyWolfs.getInstance().sendConfigMessage(player, "wolf_not_found");
                return true;
            }

            if (args.length < 3 || !args[args.length - 1].equalsIgnoreCase("confirm")) {
                String wolfName = wolf.getPersistentDataContainer().getOrDefault(
                        MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
                MyWolfs.getInstance().sendConfigMessage(player, "confirm_remove", "%name%", wolfName);
                return true;
            }

            wolf.remove();
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
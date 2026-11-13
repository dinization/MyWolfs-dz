package me.diniz.myWolfs;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class UpgradeListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        // We only care about the main hand (ignores the off-hand)
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (!(event.getRightClicked() instanceof Wolf wolf)) return;
        Player player = event.getPlayer();

        // Check if the wolf belongs to the player
        if (!wolf.isTamed() || !player.equals(wolf.getOwner())) return;

        var pdc = wolf.getPersistentDataContainer();
        int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);

        // If the wolf is NOT level 0, we don't do anything
        if (level > 0) return;

        // --- NEW CHECK ---
        // If the wolf doesn't have the stat multiplier key, it's a vanilla baby (from Breeding).
        // Vanilla babies cannot be upgraded.
        if (!pdc.has(MyWolfs.KEY_STAT_HEALTH_MULT)) {
            // Optional: Send a message to the player
            // MyWolfs.getInstance().sendConfigMessage(player, "wolf_is_baby_cannot_upgrade");
            return;
        }

        // Check if the player is holding the correct item
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        String requiredItemStr = pdc.getOrDefault(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, "DIAMOND");

        Material requiredMaterial;
        try {
            requiredMaterial = Material.valueOf(requiredItemStr);
        } catch (IllegalArgumentException e) {
            // Fallback if the config has an invalid item name
            requiredMaterial = Material.DIAMOND;
        }

        if (itemInHand.getType() != requiredMaterial) {
            return;
        }

        // Check the limit (player must have room in their pack)
        int maxWolves = MyWolfs.getInstance().getConfig().getInt("max_wolves", 3);
        int currentWolfCount = WolfDataManager.getRPGWolfCount(player);

        if (currentWolfCount >= maxWolves) {
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_promotion_failed_limit");
            return;
        }

        // --- PROMOTION SUCCESSFUL ---
        // 1. Remove 1 item from the player's hand
        itemInHand.setAmount(itemInHand.getAmount() - 1);

        // 2. Change the level to 1
        pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);

        // 3. Remove the "is baby" flag (Over Limits are no longer considered babies once promoted)
        pdc.set(MyWolfs.KEY_IS_BABY, PersistentDataType.BOOLEAN, false);

        // 4. Apply RPG stats (this will recalculate HP, Damage, etc., using the multipliers already in the PDC)
        WolfStatsManager.applyStats(wolf);

        // 5. Update the visual name
        String uniqueName = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
        wolf.customName(net.kyori.adventure.text.Component.text(uniqueName + " (Lv.1)"));

        // 6. Update the database
        WolfDataManager.updateWolf(wolf);

        // 7. Notify the player
        MyWolfs.getInstance().sendConfigMessage(player, "wolf_promoted");
    }
}
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
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (!(event.getRightClicked() instanceof Wolf wolf)) return;
        Player player = event.getPlayer();

        if (!wolf.isTamed() || !player.equals(wolf.getOwner())) return;

        var pdc = wolf.getPersistentDataContainer();
        int level = pdc.getOrDefault(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);

        if (level > 0) return;

        // If the wolf doesn't have the stat multiplier key, it's a vanilla baby (from Breeding).
        if (!pdc.has(MyWolfs.KEY_STAT_HEALTH_MULT)) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        String requiredItemStr = pdc.getOrDefault(MyWolfs.KEY_UPGRADE_ITEM, PersistentDataType.STRING, "DIAMOND");

        Material requiredMaterial;
        try {
            requiredMaterial = Material.valueOf(requiredItemStr);
        } catch (IllegalArgumentException e) {
            requiredMaterial = Material.DIAMOND;
        }

        if (itemInHand.getType() != requiredMaterial) {
            return;
        }

        int maxWolves = MyWolfs.getInstance().getConfig().getInt("max_wolves", 3);
        int currentWolfCount = WolfDataManager.getRPGWolfCount(player);

        if (currentWolfCount >= maxWolves) {
            MyWolfs.getInstance().sendConfigMessage(player, "wolf_promotion_failed_limit");
            return;
        }

        // --- PROMOTION SUCCESSFUL ---
        itemInHand.setAmount(itemInHand.getAmount() - 1);
        pdc.set(MyWolfs.KEY_LEVEL, PersistentDataType.INTEGER, 1);
        pdc.set(MyWolfs.KEY_IS_BABY, PersistentDataType.BOOLEAN, false);

        WolfStatsManager.applyStats(wolf);

        String uniqueName = pdc.getOrDefault(MyWolfs.KEY_WOLF_NAME, PersistentDataType.STRING, "Wolf");
        wolf.customName(net.kyori.adventure.text.Component.text(uniqueName + " (Lv.1)"));

        WolfDataManager.updateWolf(wolf);
        MyWolfs.getInstance().sendConfigMessage(player, "wolf_promoted");
    }
}
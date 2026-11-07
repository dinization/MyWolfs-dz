package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class FoxListener implements Listener {

    // 1. Notify players when a baby fox is born
    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Fox babyFox)) return;

        // Wait 1 tick so the Minecraft vanilla finishes setting up the trust
        Bukkit.getScheduler().runTaskLater(MyWolfs.getInstance(), () -> {
            if (!babyFox.isValid()) return;

            // Search players in a 20-block radius
            for (Entity nearby : babyFox.getNearbyEntities(20, 20, 20)) {
                if (nearby instanceof Player player) {
                    // Check if the baby fox trusts this player
                    if (isFoxTrusted(babyFox, player)) {
                        MyWolfs.getInstance().sendConfigMessage(player, "fox_adoption_available");
                    }
                }
            }
        }, 1L);
    }

    // 2. Handle the adoption (Claiming)
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Fox fox)) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if the player is holding Raw Chicken
        if (itemInHand.getType() != Material.CHICKEN) return;

        // Check if the fox is already claimed
        String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
        if (ownerUUID != null && !ownerUUID.isEmpty()) {
            MyWolfs.getInstance().sendConfigMessage(player, "fox_already_claimed");
            return;
        }

        // Check if the player is trusted by the fox (vanilla requirement)
        if (!isFoxTrusted(fox, player)) {
            return; // Ignore silently if the player isn't trusted
        }

        // Check the fox limit
        int maxFoxes = MyWolfs.getInstance().getConfig().getInt("max_foxes", 3);
        int currentFoxCount = FoxDataManager.getFoxCount(player);

        if (currentFoxCount >= maxFoxes) {
            MyWolfs.getInstance().sendConfigMessage(player, "fox_limit_reached", "%max%", String.valueOf(maxFoxes));
            return; // Stop the adoption process
        }

        // --- ADOPTION SUCCESSFUL ---
        // 1. Remove 1 Raw Chicken from the player's hand
        itemInHand.setAmount(itemInHand.getAmount() - 1);

        // 2. Generate a unique name (Fox, Fox-2, etc.)
        String uniqueName = generateUniqueName(player);
        fox.getPersistentDataContainer().set(MyWolfs.KEY_FOX_NAME, PersistentDataType.STRING, uniqueName);

        // 3. Set visual CustomName
        fox.customName(net.kyori.adventure.text.Component.text(uniqueName));
        fox.setCustomNameVisible(true);

        // 4. Save the fox to the database (this also saves the owner to PDC)
        FoxDataManager.saveFox(fox, player);

        // 5. Notify the player
        MyWolfs.getInstance().sendConfigMessage(player, "fox_claimed");
    }

    //region Helper Methods

    // Clean up the database when a fox dies
    @EventHandler
    public void onFoxDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Fox fox) {
            // Check if the fox is in the database (has an owner in PDC)
            String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);

            if (ownerUUID != null && !ownerUUID.isEmpty()) {
                // Delete from database to clean up
                FoxDataManager.deleteFox(fox);
            }
        }
    }

    // Check if a player is trusted by the fox
    private boolean isFoxTrusted(Fox fox, Player player) {
        if (fox.getFirstTrustedPlayer() != null && fox.getFirstTrustedPlayer().getUniqueId().equals(player.getUniqueId())) {
            return true;
        }
        if (fox.getSecondTrustedPlayer() != null && fox.getSecondTrustedPlayer().getUniqueId().equals(player.getUniqueId())) {
            return true;
        }
        return false;
    }

    // Generates custom name
    private String generateUniqueName(Player owner) {
        var db = FoxDataManager.getConfig();
        java.util.List<String> existingNames = new java.util.ArrayList<>();

        if (db.getConfigurationSection("foxes") != null) {
            for (String key : db.getConfigurationSection("foxes").getKeys(false)) {
                String path = "foxes." + key;
                if (owner.getUniqueId().toString().equals(db.getString(path + ".owner"))) {
                    existingNames.add(db.getString(path + ".name", "Fox"));
                }
            }
        }

        if (!existingNames.contains("Fox")) {
            return "Fox";
        }

        int suffix = 2;
        while (existingNames.contains("Fox-" + suffix)) {
            suffix++;
        }
        return "Fox-" + suffix;
    }

    //endregion
}
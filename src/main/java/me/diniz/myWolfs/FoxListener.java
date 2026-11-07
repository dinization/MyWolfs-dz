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

        Bukkit.getScheduler().runTaskLater(MyWolfs.getInstance(), () -> {
            if (!babyFox.isValid()) return;

            for (Entity nearby : babyFox.getNearbyEntities(20, 20, 20)) {
                if (nearby instanceof Player player) {
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

        if (itemInHand.getType() != Material.CHICKEN) return;

        String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
        if (ownerUUID != null && !ownerUUID.isEmpty()) {
            MyWolfs.getInstance().sendConfigMessage(player, "fox_already_claimed");
            return;
        }

        if (!isFoxTrusted(fox, player)) return;

        int maxFoxes = MyWolfs.getInstance().getConfig().getInt("max_foxes", 3);
        int currentFoxCount = FoxDataManager.getFoxCount(player);

        if (currentFoxCount >= maxFoxes) {
            MyWolfs.getInstance().sendConfigMessage(player, "fox_limit_reached", "%max%", String.valueOf(maxFoxes));
            return;
        }

        // --- ADOPTION SUCCESSFUL ---
        itemInHand.setAmount(itemInHand.getAmount() - 1);
        setupAdoptedFox(fox, player);
        MyWolfs.getInstance().sendConfigMessage(player, "fox_claimed");
    }

    /**
     * Public method to setup a fox when it's adopted (or spawned by admin).
     * This handles generating the name, saving to PDC, and saving to the database.
     */
    public static void setupAdoptedFox(Fox fox, Player owner) {
        // Force the fox to trust the owner (vanilla requirement for spawned foxes)
        fox.setFirstTrustedPlayer(owner);
        // Generate a unique name (Fox, Fox-2, etc.)
        String uniqueName = generateUniqueName(owner);
        fox.getPersistentDataContainer().set(MyWolfs.KEY_FOX_NAME, PersistentDataType.STRING, uniqueName);

        // Set visual CustomName
        fox.customName(net.kyori.adventure.text.Component.text(uniqueName));
        fox.setCustomNameVisible(true);

        // Save the fox to the database (this also saves the owner to PDC)
        FoxDataManager.saveFox(fox, owner);
    }

    //region Helper Methods

    @EventHandler
    public void onFoxDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Fox fox) {
            String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
            if (ownerUUID != null && !ownerUUID.isEmpty()) {
                FoxDataManager.deleteFox(fox);
            }
        }
    }

    // Made static so it can be used in setupAdoptedFox
    private static boolean isFoxTrusted(Fox fox, Player player) {
        if (fox.getFirstTrustedPlayer() != null && fox.getFirstTrustedPlayer().getUniqueId().equals(player.getUniqueId())) {
            return true;
        }
        if (fox.getSecondTrustedPlayer() != null && fox.getSecondTrustedPlayer().getUniqueId().equals(player.getUniqueId())) {
            return true;
        }
        return false;
    }

    // Made static so it can be used in setupAdoptedFox
    private static String generateUniqueName(Player owner) {
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
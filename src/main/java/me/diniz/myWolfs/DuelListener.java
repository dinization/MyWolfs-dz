package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class DuelListener implements Listener {

    //region COMMAND BLOCKING
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        if (!DuelManager.isInDuel(player)) return;

        String command = event.getMessage().toLowerCase();

        if (command.startsWith("/wolfs duel forfeit")) {
            return;
        }

        event.setCancelled(true);
        MyWolfs.getInstance().sendConfigMessage(player, "duel_command_blocked");
    }
    //endregion

    //region TELEPORT PREVENTION
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (DuelManager.allowedTeleports.contains(player.getUniqueId())) {
            DuelManager.allowedTeleports.remove(player.getUniqueId());
            return;
        }

        if (!DuelManager.isInDuel(player)) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        event.setCancelled(true);
        MyWolfs.getInstance().sendConfigMessage(player, "duel_command_blocked");
    }
    //endregion

    //region FREEZE LOGIC
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (DuelManager.frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
            }
        }
    }
    //endregion

    //region DEATH PREVENTION
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!DuelManager.isInDuel(player)) return;

        event.setCancelled(true);
        player.setHealth(20.0);

        DuelManager.DuelSession session = DuelManager.getSession(player);
        if (session != null) {
            Player opponent = Bukkit.getPlayer(session.player1.equals(player.getUniqueId()) ? session.player2 : session.player1);
            DuelManager.endDuel(session, opponent, "duel_ended_winner");
        }
    }
    //endregion

    //region PET DEATH PREVENTION (Wolves & Foxes)
    @EventHandler
    public void onPetDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        // 1. Check if it's a Wolf
        if (event.getEntity() instanceof Wolf wolf) {
            Player owner = (Player) wolf.getOwner();
            if (owner == null || !DuelManager.isInDuel(owner)) return;

            DuelManager.DuelSession session = DuelManager.getSession(owner);
            if (session == null) return;

            if (!session.isActive) {
                event.setCancelled(true);
                return;
            }

            if (wolf.getHealth() - event.getFinalDamage() <= 0) {
                event.setCancelled(true);
                DuelManager.defeatWolf(wolf, session);
            }
        }
        // 2. Check if it's a Fox (Support)
        else if (event.getEntity() instanceof Fox fox) {
            String ownerUUID = fox.getPersistentDataContainer().get(MyWolfs.KEY_FOX_OWNER, PersistentDataType.STRING);
            if (ownerUUID == null) return;

            Player owner = Bukkit.getPlayer(UUID.fromString(ownerUUID));
            if (owner == null || !DuelManager.isInDuel(owner)) return;

            DuelManager.DuelSession session = DuelManager.getSession(owner);
            if (session == null) return;

            if (!session.isActive) {
                event.setCancelled(true);
                return;
            }

            if (fox.getHealth() - event.getFinalDamage() <= 0) {
                event.setCancelled(true);
                DuelManager.defeatSupport(fox, session);
            }
        }
    }
    //endregion

    //region DISCONNECT HANDLING
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!DuelManager.isInDuel(player)) return;

        DuelManager.DuelSession session = DuelManager.getSession(player);
        if (session != null) {
            Player opponent = Bukkit.getPlayer(session.player1.equals(player.getUniqueId()) ? session.player2 : session.player1);
            DuelManager.endDuel(session, opponent, "duel_ended_disconnect");
        }
    }
    //endregion
}
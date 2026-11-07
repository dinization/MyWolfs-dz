package me.diniz.myWolfs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class DuelListener implements Listener {

    //region COMMAND BLOCKING
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!DuelManager.isInDuel(player)) return;

        String command = event.getMessage().toLowerCase();

        // Allow only the forfeit command during a duel
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
        if (!DuelManager.isInDuel(player)) return;

        // Allow Ender Pearls
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        // Block external teleports
        event.setCancelled(true);
        MyWolfs.getInstance().sendConfigMessage(player, "duel_command_blocked");
    }
    //endregion

    //region FREEZE LOGIC
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (DuelManager.frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getY() != event.getTo().getY() ||
                    event.getFrom().getZ() != event.getTo().getZ()) {
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

    //region WOLF DEATH PREVENTION
    @EventHandler
    public void onWolfDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf)) return;

        Player owner = (Player) wolf.getOwner();
        if (owner == null || !DuelManager.isInDuel(owner)) return;

        DuelManager.DuelSession session = DuelManager.getSession(owner);

        // 1. If the session doesn't exist, ignore
        if (session == null) return;

        // 2. If the duel has ended and we are in the delay/invulnerability phase, block ALL damage
        if (!session.isActive) {
            event.setCancelled(true);
            return;
        }

        // 3. Check if the damage will kill the wolf during the active fight
        if (wolf.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            DuelManager.defeatWolf(wolf, session);
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
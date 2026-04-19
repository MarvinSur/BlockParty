package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.display.DisplayScoreboard;
import de.leonkoth.blockparty.event.PlayerLeaveArenaEvent;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.*;

public class PlayerLeaveArenaListener implements Listener {

    private BlockParty blockParty;

    public PlayerLeaveArenaListener(BlockParty blockParty) {
        this.blockParty = blockParty;

        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLeaveArena(PlayerLeaveArenaEvent event) {
        Arena arena = event.getArena();
        Player player = event.getPlayer();
        PlayerInfo playerInfo = event.getPlayerInfo();

        playerInfo.setPlayerState(PlayerState.DEFAULT);
        playerInfo.setCurrentArena(null);

        if (!blockParty.isBungee() && playerInfo.getPlayerData() != null) {
            playerInfo.getPlayerData().apply(player);
            playerInfo.setPlayerData(null);
        }

        this.blockParty.getPlayerInfoManager().savePlayerInfo(playerInfo);

        // Hitung sisa player SETELAH keluar (remove sudah dipanggil sebelum event ini, jadi pakai size langsung)
        int currentPlayers = arena.getPlayersInArena().size();
        int maxPlayers = arena.getMaxPlayers();
        String countTag = "&8[&e" + currentPlayers + "&7/&e" + maxPlayers + "&8]";

        // Broadcast leave ke ALL atau arena saja
        String leaveMsg = PREFIX.toString() + countTag + " &7" + player.getName() + " left &e" + arena.getName();
        if (blockParty.isBroadcastGlobalJoinLeave()) {
            Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', leaveMsg));
        } else {
            arena.broadcast(PREFIX, PLAYER_LEFT_GAME, false, playerInfo, "%PLAYER%", player.getName());
        }

        if (arena.getArenaState() == ArenaState.INGAME) {
            arena.eliminate(playerInfo);
        }

        if(arena.getArenaState() == ArenaState.LOBBY && !arena.getPhaseHandler().getLobbyPhase().isRunning())
        {
            this.blockParty.getDisplayScoreboard().setScoreboard(0,0,arena);
        }

        if (blockParty.isBungee()) {
            player.kickPlayer(LEFT_GAME.toString("%ARENA%", arena.getName()));
        } else {
            LEFT_GAME.message(PREFIX, player, "%ARENA%", arena.getName());
        }
    }

}

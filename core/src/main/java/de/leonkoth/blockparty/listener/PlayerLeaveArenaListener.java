package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
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

        // FIX: Simpan state SEBELUM di-reset ke DEFAULT,
        // supaya kita tahu apakah player ini perlu di-eliminate atau trigger checkForWin.
        PlayerState stateBeforeLeave = playerInfo.getPlayerState();

        playerInfo.setPlayerState(PlayerState.DEFAULT);
        playerInfo.setCurrentArena(null);

        if (!blockParty.isBungee() && playerInfo.getPlayerData() != null) {
            playerInfo.getPlayerData().apply(player);
            playerInfo.setPlayerData(null);
        }

        blockParty.getPlayerInfoManager().savePlayerInfo(playerInfo);

        // Broadcast leave
        int currentPlayers = arena.getPlayersInArena().size();
        int maxPlayers = arena.getMaxPlayers();
        String leaveMsg = PREFIX.toString()
                + "&8[&e" + currentPlayers + "&7/&e" + maxPlayers + "&8] "
                + "&7" + player.getName() + " left &e" + arena.getName();
        if (blockParty.isBroadcastGlobalJoinLeave()) {
            Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', leaveMsg));
        } else {
            arena.broadcast(PREFIX, PLAYER_LEFT_GAME, false, playerInfo, "%PLAYER%", player.getName());
        }

        // FIX: Kalau player INGAME leave/disconnect, langsung checkForWin
        // tanpa perlu memanggil eliminate() (yang akan gagal karena state sudah DEFAULT).
        if (arena.getArenaState() == ArenaState.INGAME
                && stateBeforeLeave == PlayerState.INGAME) {
            arena.getPhaseHandler().getGamePhase().checkForWin();
        }

        if (arena.getArenaState() == ArenaState.LOBBY
                && !arena.getPhaseHandler().getLobbyPhase().isRunning()) {
            blockParty.getDisplayScoreboard().setScoreboard(0, 0, arena);
        }

        if (blockParty.isBungee()) {
            player.kickPlayer(LEFT_GAME.toString("%ARENA%", arena.getName()));
        } else {
            LEFT_GAME.message(PREFIX, player, "%ARENA%", arena.getName());
        }
    }

}

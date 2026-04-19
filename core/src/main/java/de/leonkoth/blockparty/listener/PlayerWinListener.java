package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.arena.GameState;
import de.leonkoth.blockparty.event.PlayerWinEvent;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import de.pauhull.utils.image.ChatFace;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.*;

public class PlayerWinListener implements Listener {

    private BlockParty blockParty;
    private ChatFace chatFace;

    public PlayerWinListener(BlockParty blockParty) {
        this.blockParty = blockParty;
        this.chatFace = new ChatFace(blockParty.getExecutorService());

        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerWin(PlayerWinEvent event) {

        Arena arena = event.getArena();
        List<PlayerInfo> playerInfos = event.getPlayerInfo();

        arena.getPhaseHandler().cancelGamePhase();
        arena.getSongManager().stop(this.blockParty);
        arena.setGameState(GameState.WAIT);
        arena.setArenaState(ArenaState.ENDING);

        arena.getFloor().clearInventories();
        arena.getFloor().setEndFloor();

        for (PlayerInfo playerInfo : playerInfos) {
            playerInfo.setPlayerState(PlayerState.WINNER);
            Player player = playerInfo.asPlayer();
            if (player != null) {

                chatFace.getLinesAsync(player.getName(), lines -> {
                    // Kirim face ke arena player
                    for (PlayerInfo allPlayerInfo : arena.getPlayersInArena()) {
                        Player allPlayers = allPlayerInfo.asPlayer();
                        if (allPlayers != null) {
                            for (String line : lines) {
                                allPlayers.sendMessage(PREFIX + line);
                            }
                        }
                    }

                    // Winner announcement
                    String winnerMsg = PREFIX.toString() + "&7[&e" + arena.getName() + "&7] Player &e" + player.getName() + " &7won the game!";
                    String translated = ChatColor.translateAlternateColorCodes('&', winnerMsg);

                    if (blockParty.isBroadcastGlobalWinner()) {
                        Bukkit.broadcastMessage(translated);
                    } else {
                        arena.broadcast(PREFIX, WINNER_ANNOUNCE_ALL, false, playerInfo, "%PLAYER%", player.getName());
                    }

                    WINNER_ANNOUNCE_SELF.message(PREFIX, player);
                });

                player.teleport(arena.getGameSpawn());
            }

            playerInfo.addPoints(15);
            playerInfo.addWins(1);
            this.blockParty.getPlayerInfoManager().savePlayerInfo(playerInfo);
        }

        // FIX: pass winner list yang bener
        arena.getPhaseHandler().startWinningPhase(playerInfos);
    }

}

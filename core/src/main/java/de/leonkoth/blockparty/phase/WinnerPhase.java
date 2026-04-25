package de.leonkoth.blockparty.phase;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.event.GameEndEvent;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import de.pauhull.utils.misc.RandomFireworkGenerator;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * Created by Leon on 18.03.2018.
 * Project Blockparty2
 * © 2016 - Leon Koth
 */
public class WinnerPhase implements Runnable {

    private int countdown;
    private boolean ended = false;
    private BlockParty blockParty;
    private Arena arena;
    private List<PlayerInfo> winner;

    public WinnerPhase(BlockParty blockParty, Arena arena) {
        this(blockParty, arena, null);
    }

    public WinnerPhase(BlockParty blockParty, Arena arena, List<PlayerInfo> winner) {
        this.countdown = 10;
        this.blockParty = blockParty;
        this.arena = arena;
        this.winner = winner;
    }

    private void endGame() {
        if (ended) return;
        ended = true;
        GameEndEvent event = new GameEndEvent(arena);
        Bukkit.getPluginManager().callEvent(event);
    }

    @Override
    public void run() {
        if (countdown < 0) {
            // FIX: null check sebelum teleport — winner bisa offline selama 10 detik
            // Tanpa ini → NPE → endGame() tidak dipanggil → arena stuck di ENDING selamanya
            if (this.winner == null) {
                for (PlayerInfo playerInfo : arena.getPlayersInArena()) {
                    if (playerInfo.getPlayerState() == PlayerState.WINNER) {
                        Player p = playerInfo.asPlayer();
                        if (p != null) p.teleport(arena.getLobbySpawn());
                    }
                }
            } else {
                for (PlayerInfo playerInfo : this.winner) {
                    Player p = playerInfo.asPlayer();
                    if (p != null) p.teleport(arena.getLobbySpawn());
                }
            }
            endGame();
            return;
        }

        if (arena.isEnableFireworksOnWin() && winner != null) {
            for (PlayerInfo playerInfo : this.winner) {
                Player p = playerInfo.asPlayer();
                if (p != null) {
                    RandomFireworkGenerator.shootRandomFirework(p.getLocation(), 3);
                }
            }
        }

        countdown--;
    }

}

package de.leonkoth.blockparty.phase;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.player.PlayerInfo;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.List;

/**
 * Created by Leon on 16.03.2018.
 * Project Blockparty2
 * © 2016 - Leon Koth
 */
public class PhaseHandler {

    private int gamePhaseScheduler, winnerPhaseScheduler, lobbyPhaseScheduler;
    private BlockParty blockParty;
    private Arena arena;
    private BukkitScheduler scheduler;

    @Getter
    private LobbyPhase lobbyPhase;

    @Getter
    private GamePhase gamePhase;

    @Getter
    private WinnerPhase winnerPhase;

    public PhaseHandler(BlockParty blockParty, Arena arena) {
        this.blockParty = blockParty;
        this.arena = arena;
        this.lobbyPhase = new LobbyPhase(blockParty, arena);
        this.gamePhase = new GamePhase(blockParty, arena);
        this.winnerPhase = new WinnerPhase(blockParty, arena);
        scheduler = blockParty.getPlugin().getServer().getScheduler();
    }

    public boolean startLobbyPhase() {
        // FIX ROOT CAUSE: Harus cek ArenaState == LOBBY dulu.
        // Tanpa ini, setiap player join arena (termasuk saat INGAME/ENDING)
        // bisa trigger startLobbyPhase() → lobby countdown jalan di tengah game.
        if (arena.getArenaState() != ArenaState.LOBBY) {
            return false;
        }

        if (this.arena.getPlayersInArena().size() < arena.getMinPlayers()) {
            return false;
        }

        // Jangan start kalau lobby scheduler sudah jalan
        if (scheduler.isCurrentlyRunning(lobbyPhaseScheduler)
                || scheduler.isQueued(lobbyPhaseScheduler)) {
            return false;
        }

        this.lobbyPhase = new LobbyPhase(blockParty, arena.getName());
        this.lobbyPhase.initialize();
        this.lobbyPhaseScheduler = scheduler.scheduleSyncRepeatingTask(
                blockParty.getPlugin(), lobbyPhase, 0L, 20L);
        return true;
    }

    public boolean startGamePhase() {
        // Harus dari state LOBBY, bukan INGAME/ENDING
        if (arena.getArenaState() != ArenaState.LOBBY) {
            return false;
        }

        if (this.arena.getPlayersInArena().size() < arena.getMinPlayers()) {
            return false;
        }

        if (scheduler.isCurrentlyRunning(gamePhaseScheduler)
                || scheduler.isQueued(gamePhaseScheduler)) {
            return false;
        }

        arena.setArenaState(ArenaState.INGAME);
        this.gamePhase = new GamePhase(blockParty, arena.getName());
        this.gamePhase.initialize();
        this.gamePhaseScheduler = scheduler.scheduleSyncRepeatingTask(
                blockParty.getPlugin(), gamePhase, 0L, 2L);
        this.cancelLobbyPhase();
        return true;
    }

    public boolean startWinningPhase(List<PlayerInfo> winner) {
        // Hanya bisa mulai dari state INGAME
        if (arena.getArenaState() != ArenaState.INGAME
                && arena.getArenaState() != ArenaState.ENDING) {
            return false;
        }

        if (scheduler.isCurrentlyRunning(winnerPhaseScheduler)
                || scheduler.isQueued(winnerPhaseScheduler)) {
            return false;
        }

        // Tandai game phase selesai sebelum start winner
        gamePhase.cancel();

        arena.setArenaState(ArenaState.ENDING);
        this.winnerPhase = new WinnerPhase(blockParty, arena, winner);
        winnerPhaseScheduler = scheduler.scheduleSyncRepeatingTask(
                blockParty.getPlugin(), winnerPhase, 0L, 20L);
        return true;
    }

    public void cancelLobbyPhase() {
        Bukkit.getScheduler().cancelTask(lobbyPhaseScheduler);
    }

    public void cancelWinningPhase() {
        Bukkit.getScheduler().cancelTask(winnerPhaseScheduler);
    }

    public void cancelGamePhase() {
        gamePhase.cancel();
        Bukkit.getScheduler().cancelTask(gamePhaseScheduler);
    }

    public void cancelAll() {
        cancelLobbyPhase();
        cancelGamePhase();
        cancelWinningPhase();
    }

}

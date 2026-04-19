package de.leonkoth.blockparty.phase;

import cloud.timo.TimoCloud.api.TimoCloudAPI;
import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.GameState;
import de.leonkoth.blockparty.event.*;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import de.leonkoth.blockparty.util.ColorBlock;
import de.leonkoth.blockparty.util.Util;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

import java.util.ArrayList;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.ACTIONBAR_DANCE;
import static de.leonkoth.blockparty.locale.BlockPartyLocale.ACTIONBAR_STOP;

/**
 * Created by Leon on 15.03.2018.
 * Project Blockparty2
 * © 2016 - Leon Koth
 *
 * FIX: Replaced all double-based time tracking with integer tick counters
 * to eliminate floating point drift bugs on high-load servers (80+ players).
 * Scheduler runs every 2 ticks = 10 ticks/second.
 * All "time in seconds" values are multiplied by 10 for tick conversion.
 */
public class GamePhase implements Runnable {

    // Flag guard untuk setiap fase
    private boolean firstStopEnter = true, firstDanceEnter = true, firstPrepareEnter = true, firstEnter = true;

    // Semua waktu dalam satuan TICKS (10 tick = 1 detik, karena scheduler 2L / 20 tps)
    private int timeToSearchTicks, currentTimeToSearchTicks, currentTick;
    private int stopTimeTicks, preparingTimeTicks;

    private double timeReductionPerLevel, timeModifier;
    private int levelAmount, currentLevel;

    @Getter
    private int stopTime = 4; // tetap dalam detik untuk kompatibilitas getter

    private boolean cancelled = false;

    private BlockParty blockParty;
    private Arena arena;
    private ColorBlock colorBlock;

    @Deprecated
    public GamePhase(BlockParty blockParty, String name) {
        this(blockParty, Arena.getByName(name));
    }

    public GamePhase(BlockParty blockParty, Arena arena) {
        this.blockParty = blockParty;
        this.arena = arena;

        // Konversi semua ke ticks (×10 karena scheduler 2 tick = 0.1 detik)
        this.timeToSearchTicks = arena.getTimeToSearch() * 10;
        this.currentTimeToSearchTicks = timeToSearchTicks;
        this.timeReductionPerLevel = arena.getTimeReductionPerLevel();
        this.timeModifier = arena.getTimeModifier();
        this.levelAmount = arena.getLevelAmount();
        this.stopTimeTicks = stopTime * 10;       // 4 detik = 40 ticks
        this.preparingTimeTicks = 5 * 10;         // 5 detik = 50 ticks
        this.currentTick = 0;
    }

    private int getActivePlayerAmount() {
        int amount = 0;
        for (PlayerInfo playerInfo : arena.getPlayersInArena()) {
            if (playerInfo.getPlayerState() == PlayerState.INGAME) {
                amount++;
            }
        }
        return amount;
    }

    public void initialize() {
        this.firstDanceEnter = true;
        this.firstStopEnter = true;
        this.firstPrepareEnter = true;
        this.firstEnter = true;
        this.cancelled = false;

        GameStartEvent event = new GameStartEvent(arena);
        Bukkit.getPluginManager().callEvent(event);

        if (blockParty.isTimoCloud()) {
            TimoCloudAPI.getBukkitAPI().getThisServer().setState("INGAME");
        }
    }

    /**
     * Dipanggil saat game sudah selesai / di-cancel dari luar.
     * Mencegah run() melanjutkan eksekusi setelah arena di-reset.
     */
    public void cancel() {
        this.cancelled = true;
    }

    public void checkForWin() {
        if (this.getActivePlayerAmount() <= 1) {
            this.finishGame();
        }
    }

    public void finishGame() {
        if (cancelled) return;

        ArrayList<PlayerInfo> winners = new ArrayList<>();
        for (PlayerInfo playerInfo : arena.getPlayersInArena()) {
            if (playerInfo.getPlayerState() == PlayerState.INGAME) {
                winners.add(playerInfo);
            }
        }

        PlayerWinEvent event = new PlayerWinEvent(arena, winners);
        Bukkit.getPluginManager().callEvent(event);

        if (blockParty.isTimoCloud()) {
            TimoCloudAPI.getBukkitAPI().getThisServer().setState("RESTART");
        }
    }

    @Override
    public void run() {
        // FIX: Guard — jangan lanjut kalau udah di-cancel (arena reset / game ended)
        if (cancelled) return;

        if (arena.isEnableParticles()) {
            arena.getFloor().playParticles(5, 3, 10);
        }

        // Tick 0: place new floor (kecuali first enter)
        if (currentTick == 0) {
            if (firstEnter) {
                firstEnter = false;
            } else {
                FloorPlaceEvent event = new FloorPlaceEvent(arena, arena.getFloor());
                Bukkit.getPluginManager().callEvent(event);
            }
        }

        if (currentTick < preparingTimeTicks) {
            // --- FASE DANCE (lantai baru sudah diplace, suruh cari warna) ---
            if (firstPrepareEnter) {
                RoundStartEvent event = new RoundStartEvent(arena);
                Bukkit.getPluginManager().callEvent(event);
                firstPrepareEnter = false;
            }
            Util.showActionBar(ACTIONBAR_DANCE.toString(), arena, true);

        } else if (currentTick < (currentTimeToSearchTicks + preparingTimeTicks)) {
            // --- FASE SEARCH (countdown, player harus stand di blok yg bener) ---
            if (firstDanceEnter) {
                arena.getFloor().pickBlock();

                Block pickedBlock = arena.getFloor().getCurrentBlock();
                colorBlock = ColorBlock.get(pickedBlock);
                BlockPickEvent event = new BlockPickEvent(arena, pickedBlock, colorBlock);
                Bukkit.getPluginManager().callEvent(event);

                firstDanceEnter = false;
            }

            // Hitung sisa detik — pakai integer tick, no float drift
            int ticksLeft = (currentTimeToSearchTicks + preparingTimeTicks) - currentTick;
            int secondsLeft = (ticksLeft + 9) / 10; // ceil division biar ngga jump dari 8 ke 7 kelewat

            RoundPrepareEvent event = new RoundPrepareEvent(secondsLeft, arena, colorBlock);
            Bukkit.getPluginManager().callEvent(event);

            this.blockParty.getDisplayScoreboard().setScoreboard(secondsLeft, currentLevel + 1, arena);

        } else if (currentTick < (currentTimeToSearchTicks + preparingTimeTicks + stopTimeTicks)) {
            // --- FASE STOP (remove blok yg salah, player jatuh ke-eliminate) ---
            if (firstStopEnter) {
                arena.getSongManager().pause(this.blockParty);
                arena.setGameState(GameState.STOP);
                arena.getFloor().removeBlocks();
                firstStopEnter = false;
            }
            Util.showActionBar(ACTIONBAR_STOP.toString(), arena, true);

        } else {
            // --- TRANSISI KE LEVEL BERIKUTNYA ---
            if (currentLevel < levelAmount) {
                currentLevel++;
            } else {
                this.finishGame();
                return;
            }

            currentTick = -1; // akan jadi 0 setelah increment di akhir
            // Kurangi waktu search per level (konversi ke ticks)
            double newTimeToSearch = (currentTimeToSearchTicks / 10.0)
                    - (timeReductionPerLevel / (1 + timeModifier * currentLevel));
            // Pastikan minimal 1 detik
            currentTimeToSearchTicks = Math.max(10, (int) Math.round(newTimeToSearch * 10));

            firstStopEnter = true;
            firstDanceEnter = true;
            firstPrepareEnter = true;

            arena.getSongManager().continuePlay(this.blockParty);
            arena.setGameState(GameState.PLAY);
            arena.getFloor().clearInventories();
        }

        currentTick++;
    }

    /**
     * Sisa waktu dalam detik (untuk kompatibilitas kode lain).
     */
    public double getTimeRemaining() {
        int ticksLeft = (currentTimeToSearchTicks + preparingTimeTicks) - currentTick;
        return Math.max(0, ticksLeft / 10.0);
    }

}

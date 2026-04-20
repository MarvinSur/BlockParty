package de.leonkoth.blockparty.phase;

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
 * FIX: Removed direct TimoCloud import (cloud.timo.TimoCloud.api.TimoCloudAPI).
 * TimoCloud integration is now done via reflection inside BlockParty.setTimoCloudState()
 * so this class compiles without the TimoCloud JAR on the classpath.
 *
 * FIX: Replaced all double-based time tracking with integer tick counters
 * to eliminate floating point drift bugs on high-load servers (80+ players).
 */
public class GamePhase implements Runnable {

    private boolean firstStopEnter = true, firstDanceEnter = true, firstPrepareEnter = true, firstEnter = true;

    // Semua waktu dalam satuan TICKS (10 tick = 1 detik, scheduler jalan tiap 2L)
    private int timeToSearchTicks, currentTimeToSearchTicks, currentTick;
    private int stopTimeTicks, preparingTimeTicks;

    private double timeReductionPerLevel, timeModifier;
    private int levelAmount, currentLevel;

    @Getter
    private int stopTime = 4;

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

        this.timeToSearchTicks = arena.getTimeToSearch() * 10;
        this.currentTimeToSearchTicks = timeToSearchTicks;
        this.timeReductionPerLevel = arena.getTimeReductionPerLevel();
        this.timeModifier = arena.getTimeModifier();
        this.levelAmount = arena.getLevelAmount();
        this.stopTimeTicks = stopTime * 10;
        this.preparingTimeTicks = 5 * 10;
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

        // FIX: Pakai helper reflection — tidak perlu import TimoCloud langsung
        blockParty.setTimoCloudState("INGAME");
    }

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

        // FIX: Pakai helper reflection
        blockParty.setTimoCloudState("RESTART");
    }

    @Override
    public void run() {
        if (cancelled) return;

        if (arena.isEnableParticles()) {
            arena.getFloor().playParticles(5, 3, 10);
        }

        if (currentTick == 0) {
            if (firstEnter) {
                firstEnter = false;
            } else {
                FloorPlaceEvent event = new FloorPlaceEvent(arena, arena.getFloor());
                Bukkit.getPluginManager().callEvent(event);
            }
        }

        if (currentTick < preparingTimeTicks) {
            if (firstPrepareEnter) {
                RoundStartEvent event = new RoundStartEvent(arena);
                Bukkit.getPluginManager().callEvent(event);
                firstPrepareEnter = false;
            }
            Util.showActionBar(ACTIONBAR_DANCE.toString(), arena, true);

        } else if (currentTick < (currentTimeToSearchTicks + preparingTimeTicks)) {
            if (firstDanceEnter) {
                arena.getFloor().pickBlock();

                Block pickedBlock = arena.getFloor().getCurrentBlock();
                colorBlock = ColorBlock.get(pickedBlock);
                BlockPickEvent event = new BlockPickEvent(arena, pickedBlock, colorBlock);
                Bukkit.getPluginManager().callEvent(event);

                firstDanceEnter = false;
            }

            int ticksLeft = (currentTimeToSearchTicks + preparingTimeTicks) - currentTick;
            int secondsLeft = (ticksLeft + 9) / 10;

            RoundPrepareEvent event = new RoundPrepareEvent(secondsLeft, arena, colorBlock);
            Bukkit.getPluginManager().callEvent(event);

            this.blockParty.getDisplayScoreboard().setScoreboard(secondsLeft, currentLevel + 1, arena);

        } else if (currentTick < (currentTimeToSearchTicks + preparingTimeTicks + stopTimeTicks)) {
            if (firstStopEnter) {
                arena.getSongManager().pause(this.blockParty);
                arena.setGameState(GameState.STOP);
                arena.getFloor().removeBlocks();
                firstStopEnter = false;
            }
            Util.showActionBar(ACTIONBAR_STOP.toString(), arena, true);

        } else {
            if (currentLevel < levelAmount) {
                currentLevel++;
            } else {
                this.finishGame();
                return;
            }

            currentTick = -1;
            double newTimeToSearch = (currentTimeToSearchTicks / 10.0)
                    - (timeReductionPerLevel / (1 + timeModifier * currentLevel));
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

    public double getTimeRemaining() {
        int ticksLeft = (currentTimeToSearchTicks + preparingTimeTicks) - currentTick;
        return Math.max(0, ticksLeft / 10.0);
    }

}

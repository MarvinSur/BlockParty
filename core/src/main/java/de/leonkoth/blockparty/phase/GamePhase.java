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
 * FIX: Added Sudden Death mode.
 * When all levels are exhausted but >1 player remains,
 * the game enters Sudden Death: timeToSearch is locked at minimum (1 second),
 * preparingTime is reduced to 3 seconds, and rounds keep going until
 * only 1 player survives. The game will NEVER end with multiple winners.
 *
 * FIX: Replaced all double-based time tracking with integer tick counters.
 * FIX: Removed direct TimoCloud import — now uses reflection via BlockParty.setTimoCloudState().
 */
public class GamePhase implements Runnable {

    // Minimum search time: 1 detik = 10 ticks
    private static final int MIN_SEARCH_TICKS = 10;
    // Preparing time normal: 5 detik = 50 ticks
    private static final int NORMAL_PREPARE_TICKS = 50;
    // Preparing time saat sudden death: 3 detik = 30 ticks (lebih cepat, lebih tegang)
    private static final int SUDDEN_DEATH_PREPARE_TICKS = 30;

    private boolean firstStopEnter = true, firstDanceEnter = true, firstPrepareEnter = true, firstEnter = true;

    private int timeToSearchTicks, currentTimeToSearchTicks, currentTick;
    private int stopTimeTicks, preparingTimeTicks;

    private double timeReductionPerLevel, timeModifier;
    private int levelAmount, currentLevel;

    @Getter
    private int stopTime = 4;

    private boolean cancelled = false;

    // Sudden death flag — aktif kalau level habis tapi masih >1 player
    private boolean suddenDeath = false;
    private int suddenDeathRound = 0;

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
        this.preparingTimeTicks = NORMAL_PREPARE_TICKS;
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

        blockParty.setTimoCloudState("RESTART");
    }

    /**
     * Masuk ke mode Sudden Death.
     * Level dikunci minimum, prepare time dipercepat, dan game lanjut terus
     * sampai benar-benar tinggal 1 player.
     */
    private void enterSuddenDeath() {
        if (!suddenDeath) {
            suddenDeath = true;
            // Broadcast ke semua player di arena
            for (PlayerInfo playerInfo : arena.getPlayersInArena()) {
                org.bukkit.entity.Player p = playerInfo.asPlayer();
                if (p != null) {
                    p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                            "&c&l⚡ SUDDEN DEATH! &7Bertahan sampai akhir!"));
                    p.sendTitle(
                            org.bukkit.ChatColor.RED + "" + org.bukkit.ChatColor.BOLD + "SUDDEN DEATH",
                            org.bukkit.ChatColor.YELLOW + "Hanya 1 pemenang!",
                            5, 40, 10
                    );
                }
            }
        }
        suddenDeathRound++;
        // Sudden death: search time minimum, prepare time lebih pendek
        currentTimeToSearchTicks = MIN_SEARCH_TICKS;
        preparingTimeTicks = SUDDEN_DEATH_PREPARE_TICKS;
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

                // Broadcast info sudden death round di awal setiap round
                if (suddenDeath) {
                    for (PlayerInfo playerInfo : arena.getPlayersInArena()) {
                        org.bukkit.entity.Player p = playerInfo.asPlayer();
                        if (p != null) {
                            p.sendMessage(org.bukkit.ChatColor.RED + "⚡ Sudden Death Round " + suddenDeathRound
                                    + " | " + org.bukkit.ChatColor.YELLOW + getActivePlayerAmount() + " players left");
                        }
                    }
                }
            }
            if (suddenDeath) {
                Util.showActionBar("&c&l⚡ SUDDEN DEATH &7- Round " + suddenDeathRound, arena, true);
            } else {
                Util.showActionBar(ACTIONBAR_DANCE.toString(), arena, true);
            }

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
            // --- Akhir satu round ---

            // Cek dulu apakah ada yang perlu di-eliminate (yang berdiri di blok salah)
            // sebelum transisi level — checkForWin akan handle kalau tinggal 1 player
            if (getActivePlayerAmount() <= 1) {
                this.finishGame();
                return;
            }

            if (!suddenDeath && currentLevel < levelAmount) {
                // Normal level progression
                currentLevel++;
                double newTimeToSearch = (currentTimeToSearchTicks / 10.0)
                        - (timeReductionPerLevel / (1 + timeModifier * currentLevel));
                currentTimeToSearchTicks = Math.max(MIN_SEARCH_TICKS,
                        (int) Math.round(newTimeToSearch * 10));

            } else {
                // FIX: Level habis ATAU sudah sudden death — masuk/lanjut sudden death
                // Jangan finishGame() kalau masih >1 player
                enterSuddenDeath();
            }

            currentTick = -1;

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

    public boolean isSuddenDeath() {
        return suddenDeath;
    }

}

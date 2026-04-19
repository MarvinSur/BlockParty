package de.leonkoth.blockparty.phase;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.display.DisplayScoreboard;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.util.Util;
import de.pauhull.utils.misc.MinecraftVersion;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.*;
import static de.pauhull.utils.misc.MinecraftVersion.v1_13;

/**
 * Created by Leon on 15.03.2018.
 * Project Blockparty2
 * © 2016 - Leon Koth
 *
 * FIX + FEATURE: Countdown sekarang bisa di-broadcast ke semua player online
 * berdasarkan config Broadcast.GlobalCountdown.
 * Actionbar countdown juga bisa di-broadcast global via Broadcast.GlobalActionbar.
 */
public class LobbyPhase implements Runnable {

    private int countdown;
    private BlockParty blockParty;
    private Arena arena;
    private Sound sound;

    @Getter
    private boolean isRunning;

    public LobbyPhase(BlockParty blockParty, String name) {
        this(blockParty, Arena.getByName(name));
    }

    public LobbyPhase(BlockParty blockParty, Arena arena) {
        this.blockParty = blockParty;
        this.arena = arena;
        this.countdown = arena.getLobbyCountdown();

        try {
            if (MinecraftVersion.CURRENT_VERSION.isGreaterOrEquals(MinecraftVersion.v1_9)) {
                if (MinecraftVersion.CURRENT_VERSION.isLower(v1_13)) {
                    sound = Sound.valueOf("BLOCK_NOTE_HARP");
                } else {
                    sound = Sound.valueOf("BLOCK_NOTE_BLOCK_HARP");
                }
            } else {
                sound = Sound.valueOf("NOTE_PIANO");
            }
        } catch (IllegalArgumentException e) {
            Bukkit.getConsoleSender().sendMessage("§c" + e.getMessage());
        }

    }

    public void run() {

        if (countdown < 0) {
            arena.getPhaseHandler().cancelLobbyPhase();
            return;
        }

        if (countdown == 0) {
            arena.getPhaseHandler().startGamePhase();
            arena.getPhaseHandler().cancelLobbyPhase();
            return;
        }

        if (arena.getPlayersInArena().size() >= arena.getMinPlayers()) {
            List<Player> players = new ArrayList<>();
            for (PlayerInfo playerInfo : arena.getPlayersInArena()) {
                players.add(playerInfo.asPlayer());
            }

            boolean isCountdownTick = (countdown == 60 || countdown == 30 || countdown == 20
                    || countdown == 15 || countdown == 10 || countdown == 5
                    || countdown == 4 || countdown == 3 || countdown == 2 || countdown == 1);

            if (isCountdownTick) {
                for (Player player : players) {
                    player.playSound(player.getLocation(), sound, 1, 1);
                }

                // Countdown broadcast: ALL atau arena saja
                String countdownText = PREFIX.toString() + "&7[&e" + arena.getName() + "&7] Game starts in &e" + countdown + " &7seconds";
                String translated = ChatColor.translateAlternateColorCodes('&', countdownText);
                if (blockParty.isBroadcastGlobalCountdown()) {
                    Bukkit.broadcastMessage(translated);
                } else {
                    arena.broadcast(PREFIX, TIME_LEFT, false, (PlayerInfo[]) null, "%TIME%", Integer.toString(countdown));
                }
            }

            for (Player player : players) {
                player.setLevel(countdown);
                float exp = (float) countdown / (float) arena.getLobbyCountdown();
                player.setExp(exp);
            }

            // Actionbar countdown: broadcast ke semua atau arena saja
            String actionbarMsg = ACTIONBAR_COUNTDOWN.toString("%NUMBER%", Integer.toString(countdown));
            try {
                if (blockParty.isBroadcastGlobalActionbar()) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        Util.showActionBarToPlayer(actionbarMsg, online);
                    }
                } else {
                    Util.showActionBar(actionbarMsg, arena, false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            this.blockParty.getDisplayScoreboard().setScoreboard(countdown, 0, arena);
        } else {

            arena.broadcast(PREFIX, ERROR_START_ABORTED, false, (PlayerInfo[]) null);

            for(PlayerInfo info : arena.getPlayersInArena()) {
                Player player = info.asPlayer();
                if (player != null) {
                    player.setExp(0);
                    player.setLevel(0);
                }
            }

            countdown = -1;
        }

        countdown--;
    }

    public void initialize() {
        arena.getFloor().setStartFloor();
    }

    public void restartCountdown() {
        countdown = 60;
    }

}

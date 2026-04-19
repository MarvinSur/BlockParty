package de.leonkoth.blockparty.command;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import de.pauhull.utils.locale.storage.LocaleString;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.*;

public class BlockPartyDisableCommand extends SubCommand {

    public static String SYNTAX = "/bp disable <Arena>";

    @Getter
    private LocaleString description = COMMAND_DISABLE;

    public BlockPartyDisableCommand(BlockParty blockParty) {
        super(false, 2, "disable", "blockparty.admin.disable", blockParty);
    }

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {

        Arena arena = Arena.getByName(args[1]);
        if (arena == null) {
            ERROR_ARENA_NOT_EXIST.message(PREFIX, sender, "%ARENA%", args[1]);
            return false;
        }

        // FIX: Stop game / lobby dulu, kick semua player yang ada
        if (arena.getArenaState() == ArenaState.INGAME || arena.getArenaState() == ArenaState.ENDING) {
            // Cancel game phase
            arena.getPhaseHandler().cancelAll();
            arena.getSongManager().stop(blockParty);
        } else if (arena.getArenaState() == ArenaState.LOBBY) {
            arena.getPhaseHandler().cancelLobbyPhase();
        }

        // Kick semua player dari arena — copy dulu supaya ngga ConcurrentModificationException
        List<PlayerInfo> toKick = new ArrayList<>(arena.getPlayersInArena());
        for (PlayerInfo playerInfo : toKick) {
            Player player = playerInfo.asPlayer();
            if (player == null) continue;

            // Restore inventory & gamemode
            if (!blockParty.isBungee() && playerInfo.getPlayerData() != null) {
                playerInfo.getPlayerData().apply(player);
                playerInfo.setPlayerData(null);
            } else {
                player.getInventory().clear();
                player.setGameMode(GameMode.SURVIVAL);
            }

            playerInfo.setPlayerState(PlayerState.DEFAULT);
            playerInfo.setCurrentArena(null);
            blockParty.getPlayerInfoManager().savePlayerInfo(playerInfo);

            PREFIX.message(PREFIX, player);
            player.sendMessage(org.bukkit.ChatColor.RED + "Arena " + arena.getName() + " has been disabled. You have been removed.");

            if (blockParty.isBungee()) {
                player.kickPlayer(LEFT_GAME.toString("%ARENA%", arena.getName()));
            }
        }

        arena.getPlayersInArena().clear();

        // Reset state arena
        arena.setEnabled(false);

        // Broadcast ke server
        String disableMsg = PREFIX.toString() + "&cArena &e" + arena.getName() + " &chas been disabled by &e" + sender.getName();
        Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', disableMsg));

        SUCCESS_ARENA_DISABLE.message(PREFIX, sender, "%ARENA%", args[1]);

        return true;

    }

    @Override
    public String getSyntax() {
        return SYNTAX;
    }

}

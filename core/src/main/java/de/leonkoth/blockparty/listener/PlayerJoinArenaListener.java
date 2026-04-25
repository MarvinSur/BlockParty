package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.event.PlayerJoinArenaEvent;
import de.leonkoth.blockparty.player.PlayerData;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import de.leonkoth.blockparty.util.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.*;

public class PlayerJoinArenaListener implements Listener {

    private BlockParty blockParty;

    public PlayerJoinArenaListener(BlockParty blockParty) {
        this.blockParty = blockParty;
        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoinArena(PlayerJoinArenaEvent event) {
        Arena arena = event.getArena();
        Player player = event.getPlayer();
        PlayerInfo playerInfo = event.getPlayerInfo();

        // Tolak kalau player sudah di arena lain
        if (playerInfo.getPlayerState() != PlayerState.DEFAULT) {
            ERROR_INGAME.message(PREFIX, player);
            event.setCancelMessage(ERROR_INGAME.toString());
            event.setCancelled(true);
            return;
        }

        // Tolak kalau arena penuh
        if (arena.getPlayersInArena().size() >= arena.getMaxPlayers()) {
            ERROR_ARENA_FULL.message(PREFIX, player);
            event.setCancelMessage(ERROR_ARENA_FULL.toString());
            event.setCancelled(true);
            return;
        }

        ArenaState state = arena.getArenaState();

        if (state == ArenaState.LOBBY) {
            playerInfo.setPlayerState(PlayerState.INLOBBY);
        } else if (state == ArenaState.INGAME || state == ArenaState.ENDING) {
            // FIX: Saat INGAME/ENDING, player hanya bisa masuk sebagai spectator
            if (arena.isAllowJoinDuringGame()) {
                playerInfo.setPlayerState(PlayerState.SPECTATING);
                if (arena.isEnableSpectatorMode())
                    player.setGameMode(GameMode.SPECTATOR);
            } else {
                ERROR_IN_PROGRESS.message(PREFIX, player);
                event.setCancelMessage(ERROR_IN_PROGRESS.toString());
                event.setCancelled(true);
                return;
            }
        } else {
            // State tidak dikenal — tolak
            event.setCancelled(true);
            return;
        }

        if (!blockParty.isBungee()) {
            PlayerData data = PlayerData.create(player);
            playerInfo.setPlayerData(data);
        }

        player.getInventory().clear();
        player.teleport(arena.getLobbySpawn());
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.ADVENTURE);
        player.setLevel(0);
        player.setExp(0);
        playerInfo.setCurrentArena(arena);
        arena.getPlayersInArena().add(playerInfo);

        // Broadcast join dengan hitungan player
        int currentPlayers = arena.getPlayersInArena().size();
        int maxPlayers = arena.getMaxPlayers();
        String joinMsg = PREFIX.toString()
                + "&8[&e" + currentPlayers + "&7/&e" + maxPlayers + "&8] "
                + "&7" + player.getName() + " joined &e" + arena.getName();
        if (blockParty.isBroadcastGlobalJoinLeave()) {
            Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', joinMsg));
        } else {
            arena.broadcast(PREFIX, PLAYER_JOINED_GAME, false, playerInfo, "%PLAYER%", player.getName());
        }

        // Item hotbar hanya untuk player lobby, bukan spectator
        if (state == ArenaState.LOBBY) {
            player.getInventory().setItem(8, ItemType.LEAVEARENA.getItem());
            if (arena.isEnableVoteItem())
                player.getInventory().setItem(7, ItemType.VOTEFORASONG.getItem());
            player.updateInventory();
        }

        blockParty.getDisplayScoreboard().setScoreboard(0, 0, arena);

        if (arena.isEnableJoinMessage())
            JOINED_GAME.message(PREFIX, player, "%ARENA%", arena.getName());

        // FIX: startLobbyPhase() sekarang aman dipanggil di sini karena
        // PhaseHandler.startLobbyPhase() sudah punya guard ArenaState == LOBBY.
        // Kalau arena sedang INGAME/ENDING, method ini langsung return false tanpa efek.
        arena.getPhaseHandler().startLobbyPhase();
    }

}

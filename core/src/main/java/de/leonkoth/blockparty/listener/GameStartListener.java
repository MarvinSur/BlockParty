package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.arena.GameState;
import de.leonkoth.blockparty.event.GameStartEvent;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.GAME_STARTED;
import static de.leonkoth.blockparty.locale.BlockPartyLocale.PREFIX;

public class GameStartListener implements Listener {

    private BlockParty blockParty;

    public GameStartListener(BlockParty blockParty) {
        this.blockParty = blockParty;
        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameStart(GameStartEvent event) {
        Arena arena = event.getArena();

        arena.getSongManager().play(this.blockParty);
        arena.getFloor().setStartFloor();

        arena.setArenaState(ArenaState.INGAME);
        arena.setGameState(GameState.START);

        for (PlayerInfo playerInfo : arena.getPlayersInArena()) {
            Player player = playerInfo.asPlayer();
            if (player == null) continue;

            // FIX BUG 4: Hanya set INGAME untuk player yang memang di lobby.
            // Player SPECTATING (join-during-game) tidak ikut ronde ini — biarkan SPECTATING.
            // Sebelumnya semua player di-set INGAME termasuk yang baru join sebagai spectator
            // di tengah game sebelumnya → mereka ke-count di checkForWin() → winner tidak ada.
            if (playerInfo.getPlayerState() == PlayerState.INLOBBY) {
                playerInfo.setPlayerState(PlayerState.INGAME);
                playerInfo.addGamesPlayed(1);
            }

            player.teleport(arena.getGameSpawn());
            player.setGameMode(GameMode.SURVIVAL);
            player.setLevel(0);
            player.setExp(0);

            // FIX BUG 4b: clear() dulu baru updateInventory — remove(ItemStack) tidak reliable
            // karena membandingkan ItemStack by reference, bukan by type.
            // Di game pertama setelah restart, LEAVEARENA/VOTEFORASONG kadang masih di hotbar.
            player.getInventory().clear();
            player.updateInventory();
        }

        arena.broadcast(PREFIX, GAME_STARTED, false, (PlayerInfo[]) null);
    }

}

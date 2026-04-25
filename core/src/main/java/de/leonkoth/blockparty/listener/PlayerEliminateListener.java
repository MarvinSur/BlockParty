package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.event.PlayerEliminateEvent;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import static de.leonkoth.blockparty.locale.BlockPartyLocale.PLAYER_ELIMINATED;
import static de.leonkoth.blockparty.locale.BlockPartyLocale.PREFIX;

public class PlayerEliminateListener implements Listener {

    private BlockParty blockParty;

    public PlayerEliminateListener(BlockParty blockParty) {
        this.blockParty = blockParty;
        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerEliminate(PlayerEliminateEvent event) {

        Arena arena = event.getArena();
        Player player = event.getPlayer();
        PlayerInfo playerInfo = event.getPlayerInfo();

        // FIX BUG 1: Jangan proses eliminate kalau player sudah SPECTATING atau bukan INGAME.
        // Tanpa guard ini, player yang jatuh saat SPECTATING (misal join-during-game)
        // bisa trigger eliminate lagi → checkForWin() salah count → game tidak punya winner.
        if (playerInfo.getPlayerState() != PlayerState.INGAME) {
            return;
        }

        if (arena.isEnableLightnings()) {
            player.getWorld().strikeLightningEffect(player.getLocation());
        }

        // Hitung ingame players SEBELUM set state ke SPECTATING
        int ingameCount = 0;
        for (PlayerInfo pi : arena.getPlayersInArena()) {
            if (pi.getPlayerState() == PlayerState.INGAME) {
                ingameCount++;
            }
        }

        // Poin bonus berdasarkan sisa player
        int a = 10 - ingameCount;
        if (a > 0) {
            playerInfo.setPoints(playerInfo.getPoints() + a);
            this.blockParty.getPlayerInfoManager().savePlayerInfo(playerInfo);
        }

        playerInfo.setPlayerState(PlayerState.SPECTATING);
        if (arena.isEnableSpectatorMode())
            player.setGameMode(GameMode.SPECTATOR);
        player.teleport(arena.getLobbySpawn());
        player.getInventory().clear();
        player.updateInventory();
        arena.broadcast(PREFIX, PLAYER_ELIMINATED, false, (PlayerInfo[]) null, "%PLAYER%", playerInfo.getName());

        // FIX BUG 2: Cek win SETELAH state di-update ke SPECTATING.
        // Sebelumnya checkForWin() dipanggil sebelum setPlayerState, jadi saat
        // penghitungan, player yang baru di-eliminate masih dihitung INGAME.
        // Akibatnya: tinggal 1 player tapi checkForWin() hitung 2 → game tidak selesai.
        if (arena.getArenaState() == ArenaState.INGAME) {
            arena.getPhaseHandler().getGamePhase().checkForWin();
        }
    }

}

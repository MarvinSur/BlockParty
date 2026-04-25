package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.arena.ArenaState;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * FIX HIGH-LOAD: PlayerMoveEvent tidak reliable di server 60+ player karena
 * TPS drop bikin event skip. Ganti ke polling scheduler setiap 2 tick
 * yang jauh lebih konsisten untuk deteksi eliminasi via Y position.
 *
 * PlayerMoveEvent tetap dipertahankan sebagai secondary check untuk
 * deteksi yang lebih cepat saat server tidak lag.
 */
public class PlayerMoveListener implements Listener {

    private BlockParty blockParty;

    public PlayerMoveListener(BlockParty blockParty) {
        this.blockParty = blockParty;
        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
        startEliminationPoller();
    }

    /**
     * Scheduler polling setiap 2 tick (0.1 detik) — sama frekuensinya dengan GamePhase.
     * Jauh lebih reliable daripada PlayerMoveEvent di high-load server.
     */
    private void startEliminationPoller() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(blockParty.getPlugin(), () -> {
            for (Arena arena : blockParty.getArenas()) {
                if (arena.getArenaState() != ArenaState.INGAME) continue;

                int minY = arena.getFloor().getBounds().getA().getBlockY()
                        - arena.getDistanceToOutArea();

                // Snapshot list dulu untuk hindari ConcurrentModificationException
                // kalau eliminate() trigger event yang modify list
                PlayerInfo[] players = arena.getPlayersInArena()
                        .toArray(new PlayerInfo[0]);

                for (PlayerInfo playerInfo : players) {
                    if (playerInfo.getPlayerState() != PlayerState.INGAME) continue;
                    Player player = playerInfo.asPlayer();
                    if (player == null || !player.isOnline()) continue;

                    if (player.getLocation().getBlockY() <= minY) {
                        arena.eliminate(playerInfo);
                    }
                }
            }
        }, 2L, 2L);
    }

    /**
     * Secondary check via event — tetap berguna saat server sehat (TPS normal).
     * Di saat TPS drop, poller di atas yang jadi andalan.
     */
    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        // Optimasi: skip kalau Y tidak berubah — event ini fire SANGAT sering
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        PlayerInfo playerInfo = PlayerInfo.getFromPlayer(player);

        if (playerInfo == null) return;
        if (playerInfo.getPlayerState() != PlayerState.INGAME) return;

        Arena arena = playerInfo.getCurrentArena();
        if (arena == null) return;
        if (arena.getArenaState() != ArenaState.INGAME) return;

        int minY = arena.getFloor().getBounds().getA().getBlockY()
                - arena.getDistanceToOutArea();
        if (player.getLocation().getBlockY() <= minY) {
            arena.eliminate(playerInfo);
        }
    }

}

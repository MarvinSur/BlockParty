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

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX HIGH-LOAD: PlayerMoveEvent tidak reliable di server 60+ player karena
 * TPS drop bikin event skip. Pakai polling scheduler setiap 2 tick sebagai
 * primary check, PlayerMoveEvent sebagai secondary.
 *
 * FIX DOUBLE-ELIMINATE: eliminatingPlayers set mencegah player yang sama
 * di-eliminate dua kali dalam satu tick (dari poller + event secara bersamaan).
 */
public class PlayerMoveListener implements Listener {

    private BlockParty blockParty;

    // Set UUID player yang sedang dalam proses eliminate di tick ini.
    // Dibersihkan setiap tick oleh poller.
    private final Set<UUID> eliminatingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PlayerMoveListener(BlockParty blockParty) {
        this.blockParty = blockParty;
        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
        startEliminationPoller();
    }

    private void startEliminationPoller() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(blockParty.getPlugin(), () -> {

            // Bersihkan set di awal setiap tick
            eliminatingPlayers.clear();

            for (Arena arena : blockParty.getArenas()) {
                if (arena.getArenaState() != ArenaState.INGAME) continue;
                if (arena.getFloor() == null || arena.getFloor().getBounds() == null) continue;

                int minY = arena.getFloor().getBounds().getA().getBlockY()
                        - arena.getDistanceToOutArea();

                // Snapshot untuk hindari ConcurrentModificationException
                PlayerInfo[] players = arena.getPlayersInArena().toArray(new PlayerInfo[0]);

                for (PlayerInfo playerInfo : players) {
                    if (playerInfo.getPlayerState() != PlayerState.INGAME) continue;

                    Player player = playerInfo.asPlayer();
                    if (player == null || !player.isOnline()) continue;

                    if (player.getLocation().getBlockY() <= minY) {
                        // FIX DOUBLE-ELIMINATE: cek dan add ke set secara atomic
                        if (eliminatingPlayers.add(player.getUniqueId())) {
                            arena.eliminate(playerInfo);
                        }
                    }
                }
            }
        }, 2L, 2L);
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        // Skip kalau Y tidak berubah — optimasi penting, event ini fire sangat sering
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();

        // FIX DOUBLE-ELIMINATE: kalau poller sudah handle player ini di tick yang sama, skip
        if (eliminatingPlayers.contains(player.getUniqueId())) return;

        PlayerInfo playerInfo = PlayerInfo.getFromPlayer(player);
        if (playerInfo == null) return;
        if (playerInfo.getPlayerState() != PlayerState.INGAME) return;

        Arena arena = playerInfo.getCurrentArena();
        if (arena == null) return;
        if (arena.getArenaState() != ArenaState.INGAME) return;
        if (arena.getFloor() == null || arena.getFloor().getBounds() == null) return;

        int minY = arena.getFloor().getBounds().getA().getBlockY()
                - arena.getDistanceToOutArea();

        if (player.getLocation().getBlockY() <= minY) {
            if (eliminatingPlayers.add(player.getUniqueId())) {
                arena.eliminate(playerInfo);
            }
        }
    }

}

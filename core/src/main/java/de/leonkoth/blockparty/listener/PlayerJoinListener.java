package de.leonkoth.blockparty.listener;

import de.leonkoth.blockparty.BlockParty;
import de.leonkoth.blockparty.arena.Arena;
import de.leonkoth.blockparty.player.PlayerData;
import de.leonkoth.blockparty.player.PlayerInfo;
import de.leonkoth.blockparty.player.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;

/**
 * Created by Leon on 15.03.2018.
 * Project Blockparty2
 * © 2016 - Leon Koth
 *
 * FEATURE: AutoJoin — kalau AutoJoin.Enabled = true di config,
 * player yang join server otomatis masuk arena yang paling rame (atau PreferredArena).
 */
public class PlayerJoinListener implements Listener {

    private BlockParty blockParty;

    public PlayerJoinListener(BlockParty blockParty) {
        this.blockParty = blockParty;

        Bukkit.getPluginManager().registerEvents(this, blockParty.getPlugin());
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        PlayerInfo playerInfo = PlayerInfo.getFromPlayer(player);
        if (playerInfo == null) {
            blockParty.getPlayers().add((playerInfo = new PlayerInfo(player.getName(), player.getUniqueId(), 0, 0, 0)));
        } else {
            playerInfo.setPlayerState(PlayerState.DEFAULT);
            playerInfo.setCurrentArena(null);
        }
        blockParty.getPlayerInfoManager().createPlayerInfo(playerInfo);

        File file = new File(BlockParty.PLUGIN_FOLDER + "/PlayerData", player.getUniqueId().toString() + ".yml");
        if (file.exists()) {
            PlayerData data = PlayerData.getFromFile(file);

            if (data != null) {
                data.apply(player);
            }
        }

        // BungeeCord mode: langsung masuk default arena
        if (blockParty.isBungee()) {
            Arena arena = Arena.getByName(blockParty.getDefaultArena());
            if (arena != null) {
                String error = arena.addPlayer(player).getCancelMessage();
                if (error != null) {
                    player.kickPlayer(error);
                }
            }
            event.setJoinMessage(null);
            return;
        }

        // AutoJoin mode: delay 1 tick supaya player fully loaded dulu
        if (blockParty.isAutoJoinEnabled()) {
            final Player finalPlayer = player;
            Bukkit.getScheduler().runTaskLater(blockParty.getPlugin(), () -> {
                // Cek ulang apakah player masih online dan belum di arena
                if (!finalPlayer.isOnline()) return;
                PlayerInfo pi = PlayerInfo.getFromPlayer(finalPlayer);
                if (pi == null || pi.getPlayerState() != PlayerState.DEFAULT) return;

                Arena target = blockParty.findBestAutoJoinArena();
                if (target != null) {
                    String error = target.addPlayer(finalPlayer).getCancelMessage();
                    if (error != null) {
                        finalPlayer.sendMessage(org.bukkit.ChatColor.RED + "[BlockParty] Auto join failed: " + error);
                    }
                }
            }, 10L); // 10 tick = 0.5 detik
        }
    }

}

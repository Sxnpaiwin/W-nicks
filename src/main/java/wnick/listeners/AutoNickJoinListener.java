package wnick.listeners;

import wnick.NameTagPlugin;
import wnick.NickPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Re-applies a player's saved nick when they log in.
 *
 * Enabled by the {@code auto_apply_nick_on_join} config option (default: true).
 *
 * This is purely a cosmetic re-application — the player's saved nickname,
 * skin, and fake rank are read from storage and pushed back into the live
 * player profile / TAB display. Useful after a server restart or a player
 * reconnect where the in-memory cache has been lost.
 */
public class AutoNickJoinListener implements Listener {

   private final NameTagPlugin plugin;

   public AutoNickJoinListener(NameTagPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void on(PlayerJoinEvent event) {
      if (!plugin.config().getBoolean("auto_apply_nick_on_join", true)) {
         return;
      }
      Player player = event.getPlayer();

      // Run on the async thread — storage.loadPlayer may hit disk/network.
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
         try {
            plugin.getStorageManager().getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> {
               if (nameTagPlayer == null || !nameTagPlayer.hasNick()) {
                  // Nothing to restore.
                  if (nameTagPlayer == null) {
                     // First-time joiner: cache an empty NickPlayer so later
                     // operations don't repeatedly hit storage.
                     NickPlayer fresh = new NickPlayer(player.getUniqueId());
                     fresh.setOriginalName(player.getName());
                     plugin.getPlayerCache().put(player.getUniqueId(), fresh);
                  }
                  return;
               }

               // Cache the loaded data
               plugin.getPlayerCache().put(player.getUniqueId(), nameTagPlayer);

               // Re-apply on the main thread (PaperSkinManager requires it)
               Bukkit.getScheduler().runTask(plugin, () -> {
                  if (!player.isOnline()) return;

                  String nickname = nameTagPlayer.getNickname();
                  if (nickname != null && !nickname.isEmpty()) {
                     // Use setNickname to re-apply the player list name + display name + profile name.
                     // We deliberately DON'T call setSkinFromPlayer here because the saved
                     // skin data (texture + signature) is already on the cached NickPlayer;
                     // PaperSkinManager.refreshPlayer will re-broadcast it.
                     try {
                        plugin.setNickname(player, nickname);
                     } catch (Exception ex) {
                        plugin.getLogger().warning("Failed to re-apply nick for " + player.getName() + ": " + ex.getMessage());
                     }
                  }

                  // If TAB is hooked, refresh it too so the fake rank prefix/suffix shows up.
                  if (plugin.getServer().getPluginManager().isPluginEnabled("TAB")) {
                     plugin.attemptToUpdateTabPlayer(player);
                  }
               });
            });
         } catch (Exception ex) {
            plugin.getLogger().warning("AutoNickJoinListener failed for " + player.getName() + ": " + ex.getMessage());
         }
      });
   }
}

package gg.lode.nametag;

import gg.lode.nametagapi.api.NickPlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderManager extends PlaceholderExpansion {
   private final NameTagPlugin plugin;

   public PlaceholderManager(NameTagPlugin plugin) {
      this.plugin = plugin;
   }

   @NotNull
   public String getIdentifier() {
      return "wnick";
   }

   @NotNull
   public String getAuthor() {
      return "Joehe";
   }

   @NotNull
   public String getVersion() {
      return "v1.0.81-W-Nick";
   }

   @Nullable
   public String onPlaceholderRequest(Player player, @NotNull String params) {
      if (player == null) {
         return null;
      } else {
         NickPlayer nickPlayer = this.plugin.getPlayerCache().get(player.getUniqueId());

         return switch (params) {
            case "nickname" -> this.plugin.getNick(player);
            case "has_nick" -> String.valueOf(this.plugin.hasNick(player));
            case "original_name" -> nickPlayer != null ? nickPlayer.getOriginalName() : player.getName();
            case "skin_name" -> nickPlayer != null ? nickPlayer.getSkinName() : null;
            case "has_custom_skin" -> String.valueOf(nickPlayer != null && nickPlayer.getSkin() != null);
            case "has_fake_rank" -> String.valueOf(nickPlayer != null && nickPlayer.getFakeRankId() != null);
            case "fake_rank" -> nickPlayer != null ? nickPlayer.getFakeRankId() : null;
            default -> null;
         };
      }
   }
}

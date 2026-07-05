package wnick.listeners;

import com.github.retrooper.packetevents.nametag.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.nametag.event.PacketSendEvent;
import com.github.retrooper.packetevents.nametag.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.nametag.protocol.player.UserProfile;
import com.github.retrooper.packetevents.nametag.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import wnick.NameTagPlugin;
import wnick.NickPlayer;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class PlayerInfoPacketListener extends PacketListenerAbstract {
   private final NameTagPlugin plugin;

   public PlayerInfoPacketListener(NameTagPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   public void onPacketSend(PacketSendEvent event) {
      if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
         WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);

         for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : packet.getEntries()) {
            UserProfile profile = entry.getGameProfile();
            UUID uuid = profile.getUUID();
            NickPlayer nickPlayer = this.plugin.getPlayerCache().get(uuid);
            if (nickPlayer != null && nickPlayer.getNickname() != null) {
               String displayName = LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(nickPlayer.getNickname()));
               profile.setName(displayName);
            }
         }
      }
   }
}

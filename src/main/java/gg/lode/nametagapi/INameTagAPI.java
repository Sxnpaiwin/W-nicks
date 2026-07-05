package gg.lode.nametagapi;

import gg.lode.nametagapi.api.Skin;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public interface INameTagAPI {
   void setNickname(Player var1, String var2);

   void setNickname(Player var1, Component var2);

   void setSkinFromPlayer(Player var1, String var2);

   void setNickFromPlayer(Player var1, String var2);

   boolean setSkinFromMineskinId(Player var1, String var2);

   boolean setSkinFromMineskinUrl(Player var1, String var2);

   boolean setSkinFromTextureAndSignature(Player var1, String var2, String var3);

   @Nullable
   Skin getSkinFromMineskinId(String var1);

   @Nullable
   Skin getSkinFromMineskinUrl(String var1);

   void resetNickname(Player var1);

   void resetSkin(Player var1);

   void resetNick(Player var1);

   boolean hasNick(Player var1);

   @Nullable
   String getNick(Player var1);

   @Nullable
   String getNick(UUID var1);

   @Nullable
   Component getNickComponent(Player var1);

   @Nullable
   Component getNickComponent(UUID var1);

   @Nullable
   Skin getSkin(Player var1);

   @Nullable
   Skin getSkin(UUID var1);

   void randomNick(Player var1);

   void randomNick(Player var1, String var2);

   void setNickFromPlayer(Player var1, String var2, String var3);

   void setNickname(Player var1, String var2, String var3);

   void setFakeRank(Player var1, String var2);

   void clearFakeRank(Player var1);

   void resetAllNicks();

   void shouldChangeUniqueId(boolean var1);

   boolean shouldChangeUniqueId();
}

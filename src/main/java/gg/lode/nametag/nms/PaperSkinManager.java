package gg.lode.nametag.nms;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.util.MojangSkinFetcher;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperSkinManager {
   private static final String TEXTURES_PROPERTY = "textures";

   public GameProfile getProfile(Player player) {
      try {
         Method getProfileMethod = player.getClass().getMethod("getProfile");
         return (GameProfile)getProfileMethod.invoke(player);
      } catch (Exception var3) {
         throw new RuntimeException("Failed to fetch profile for " + player.getName(), var3);
      }
   }

   public void setSkin(Player player, String texture, String signature) {
      JavaPlugin plugin = NameTagPlugin.instance();

      try {
         if (NameTagPlugin.isPaper12004OrNewer()) {
            PlayerProfile playerProfile = player.getPlayerProfile();
            playerProfile.setProperty(new ProfileProperty("textures", texture, signature));
            player.setPlayerProfile(playerProfile);
         } else {
            GameProfile profile = this.getProfile(player);
            Method propertiesMethod = NameTagPlugin.isPaper1219OrNewer()
               ? GameProfile.class.getMethod("properties")
               : GameProfile.class.getMethod("getProperties");
            PropertyMap propertyMap = (PropertyMap)propertiesMethod.invoke(profile);
            propertyMap.removeAll("textures");
            Property skinProperty = signature != null ? new Property("textures", texture, signature) : new Property("textures", texture);
            propertyMap.put("textures", skinProperty);
            plugin.getLogger().info("Set skin for " + player.getName() + " - texture length: " + texture.length());
         }
      } catch (Exception var9) {
         plugin.getLogger().severe("Failed to set skin for " + player.getName() + ": " + var9.getMessage());
         var9.printStackTrace();
      }
   }

   public void setProfileName(Player player, String name) {
      String profileName = name.replaceAll("[^a-zA-Z0-9_]", "");
      if (profileName.length() > 16) {
         profileName = profileName.substring(0, 16);
      }

      try {
         if (!profileName.isEmpty()) {
            if (NameTagPlugin.isPaper12004OrNewer()) {
               PlayerProfile playerProfile = player.getPlayerProfile();
               playerProfile.setName(profileName);
               player.setPlayerProfile(playerProfile);
            } else {
               GameProfile profile = this.getProfile(player);
               Field nameField = GameProfile.class.getDeclaredField("name");
               nameField.setAccessible(true);
               nameField.set(profile, profileName);
            }
         }

         player.playerListName(MiniMessageHelper.deserialize(name));
         player.displayName(MiniMessageHelper.deserialize(name));
         this.forcePlayerUpdate(player);
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   public void setUniqueId(Player player, String name) {
      JavaPlugin plugin = NameTagPlugin.instance();
      Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
         try {
            UUID uuid = MojangSkinFetcher.fetchUUID(name);
            if (uuid == null) {
               uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
            }

            UUID resolvedUuid = uuid;
            Bukkit.getScheduler().runTask(plugin, () -> {
               if (player.isOnline()) {
                  this.setUniqueId(player, resolvedUuid);
                  plugin.getLogger().info("Set UUID for " + player.getName() + " to " + resolvedUuid);
               }
            });
         } catch (Exception var6) {
            plugin.getLogger().severe("Failed to fetch UUID for " + name + ": " + var6.getMessage());
            var6.printStackTrace();
         }
      });
   }

   public void setUniqueId(Player player, UUID uuid) {
      JavaPlugin plugin = NameTagPlugin.instance();

      try {
         for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != player) {
               online.hidePlayer(plugin, player);
            }
         }

         if (NameTagPlugin.isPaper12004OrNewer()) {
            PlayerProfile playerProfile = player.getPlayerProfile();
            playerProfile.setId(uuid);
            player.setPlayerProfile(playerProfile);
         } else {
            GameProfile profile = this.getProfile(player);
            Field uuidField = GameProfile.class.getDeclaredField("id");
            uuidField.setAccessible(true);
            uuidField.set(profile, uuid);
         }

         this.resetChatSession(player);
         Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
               for (Player onlinex : Bukkit.getOnlinePlayers()) {
                  if (onlinex != player) {
                     onlinex.showPlayer(plugin, player);
                  }
               }
            }
         }, 1L);
         plugin.getLogger().warning("Successfully changed " + player.getName() + "'s unique id.");
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   private void forcePlayerUpdate(Player player) {
      JavaPlugin plugin = NameTagPlugin.instance();
      Bukkit.getScheduler().runTask(plugin, () -> {
         if (player.isOnline()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
               try {
                  online.hidePlayer(plugin, player);
                  Bukkit.getScheduler().runTaskLater(plugin, () -> {
                     try {
                        if (player.isOnline()) {
                           online.showPlayer(plugin, player);
                        }
                     } catch (Exception var4) {
                        var4.printStackTrace();
                     }
                  }, 1L);
               } catch (Exception var5) {
                  var5.printStackTrace();
               }
            }
         }
      });
   }

   private void resetChatSession(Player player) {
      try {
         Method getHandle = player.getClass().getMethod("getHandle");
         Object nmsPlayer = getHandle.invoke(player);

         for (Field field : nmsPlayer.getClass().getDeclaredFields()) {
            if (field.getType().getSimpleName().contains("RemoteChatSession")) {
               field.setAccessible(true);
               field.set(nmsPlayer, null);
               return;
            }
         }
      } catch (Exception var8) {
      }
   }

   public void setSkinFromName(JavaPlugin plugin, Player player, String playerName) {
      if (!plugin.isEnabled()) {
         plugin.getLogger().severe("Cannot fetch skin - plugin is disabled!");
      } else {
         Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
               plugin.getLogger().info("Fetching skin for player: " + playerName);
               String[] skinData = MojangSkinFetcher.fetchSkinDataFromUsername(playerName);
               if (skinData != null && skinData.length >= 1) {
                  String textureValue = skinData[0];
                  String signature = skinData.length > 1 ? skinData[1] : null;
                  plugin.getLogger().info("Successfully fetched skin for " + playerName);
                  Task.later(plugin, () -> {
                     this.setSkin(player, textureValue, signature);
                     this.refreshPlayer(player);
                  }, 1L);
               } else {
                  plugin.getLogger().warning("Failed to fetch skin for " + playerName + " - player may not exist or have an API error");
                  plugin.getLogger().warning("skinData is " + (skinData == null ? "null" : "not null but length is " + skinData.length));
               }
            } catch (Exception var7) {
               plugin.getLogger().severe("Error fetching skin for " + playerName + ": " + var7.getMessage());
               var7.printStackTrace();
            }
         });
      }
   }

   public void refreshPlayer(Player player) {
      JavaPlugin plugin = NameTagPlugin.instance();
      Bukkit.getScheduler().runTask(plugin, () -> {
         if (player.isOnline()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
               try {
                  online.hidePlayer(plugin, player);
                  Bukkit.getScheduler().runTaskLater(plugin, () -> {
                     try {
                        if (player.isOnline()) {
                           online.showPlayer(plugin, player);
                        }
                     } catch (Exception var4) {
                        var4.printStackTrace();
                     }
                  }, 1L);
               } catch (Exception var5) {
                  var5.printStackTrace();
               }
            }
         }
      });
   }
}

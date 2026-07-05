package wnick;

import com.github.retrooper.packetevents.nametag.PacketEvents;
import com.github.retrooper.packetevents.nametag.settings.PacketEventsSettings;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.jorel.commandapi.nametag.CommandAPI;
import dev.jorel.commandapi.nametag.CommandAPIPaperConfig;
import wnick.util.Config;
import wnick.util.Tasks;
import wnick.util.MojangSkinFetcher;
import wnick.util.MiniMsg;
import wnick.command.NameTagCommand;
import wnick.command.NickCommand;
import wnick.command.RandomNickCommand;
import wnick.command.RealNameCommand;
import wnick.listeners.PlayerInfoPacketListener;
import wnick.nms.PaperSkinManager;
import wnick.storage.StorageManager;
import wnick.util.FakeRankManager;
import wnick.util.MojangSkinFetcher;
import wnick.util.SkinProvider;
import wnick.util.TabIntegration;
import wnick.util.UsernameGenerator;
import wnick.NickPlayer;
import wnick.Skin;
import io.github.retrooper.packetevents.nametag.factory.spigot.SpigotPacketEventsBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public final class NameTagPlugin extends JavaPlugin {
   public static final String VERSION = "v1.0.81-W-Nick";
   private final ConcurrentHashMap<UUID, NickPlayer> playerCache = new ConcurrentHashMap<>();
   private StorageManager storageManager;
   private PaperSkinManager paperSkinManager;
   @Nullable
   private FakeRankManager fakeRankManager;
   @Nullable
   private TabIntegration tabIntegration;
   private Config config;
   private static final int CONFIG_VERSION = 6;
   private static NameTagPlugin instance;

   @Nullable
   private static String[] fetchTextureAndSignatureFromMineskinId(String skinId) {
      try {
         String apiEndpoint = "https://api.mineskin.org/v2/skins/" + skinId;
         URL url = new URL(apiEndpoint);
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         JSONParser parser = new JSONParser();
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            return null;
         } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line);
            }

            reader.close();
            String jsonResponse = response.toString();
            JSONObject object = (JSONObject)parser.parse(jsonResponse);
            String[] outputToReturn = new String[]{
               (String)((JSONObject)((JSONObject)((JSONObject)object.get("skin")).get("texture")).get("data")).get("value"),
               (String)((JSONObject)((JSONObject)((JSONObject)object.get("skin")).get("texture")).get("data")).get("signature")
            };
            reader.close();
            return outputToReturn;
         }
      } catch (Exception var12) {
         var12.printStackTrace();
         return null;
      }
   }

   public static boolean isPaper12004OrNewer() {
      String version = Bukkit.getMinecraftVersion();
      return isVersionAtLeast(version, "1.20.4");
   }

   public static boolean isPaper1219OrNewer() {
      String version = Bukkit.getMinecraftVersion();
      return isVersionAtLeast(version, "1.21.9");
   }

   private static boolean isVersionAtLeast(String current, String required) {
      String[] a = current.split("\\.");
      String[] b = required.split("\\.");

      for (int i = 0; i < Math.max(a.length, b.length); i++) {
         int ai = i < a.length ? Integer.parseInt(a[i]) : 0;
         int bi = i < b.length ? Integer.parseInt(b[i]) : 0;
         if (ai != bi) {
            return ai > bi;
         }
      }

      return true;
   }

   public void onLoad() {
      // [W-Nick] bStats metrics removed for privacy. No telemetry is sent.
      PacketEvents.setAPI(
         SpigotPacketEventsBuilder.build(
            this, new PacketEventsSettings().checkForUpdates(false).fullStackTrace(true).kickIfTerminated(false).kickOnPacketException(false)
         )
      );
      PacketEvents.getAPI().load();
      CommandAPI.onLoad(new CommandAPIPaperConfig(this).setNamespace("minecraft").fallbackToLatestNMS(true));
      this.storageManager = new StorageManager(this);
      this.storageManager.initialize().whenComplete((result, throwable) -> {
         if (throwable != null) {
            this.getLogger().severe("Failed to initialize storage system: " + throwable.getMessage());
            throwable.printStackTrace();
         } else {
            this.getLogger().info("Storage system initialized successfully");
         }
      });
      this.config = new Config(this, "config.yml");
      this.config.initialize();
      this.updateConfigToLatest();
   }

   private void updateConfigToLatest() {
      // W-Nick migration: v5 (original) -> v6 (W-Nick fork).
      // Adds: message_prefix, auto_apply_nick_on_join, auto_assign_random_rank_on_random_nick.
      if (this.config.getInt("version") < 6) {
         this.getLogger().info("Updating configuration to the latest version...");
         int current = this.config.getInt("version");
         switch (current) {
            case 1:
               this.config.set("change_uuids", false);
               // fall through to next migration step
            case 2:
               this.config.set("can_use_existing_players", false);
               this.config.set("change_uuids", null);
               // fall through
            case 3:
            case 4:
               this.config.set("can_use_existing_players", true);
               this.config.set("should_spoof_uuid", false);
               // [W-Nick] allow_cloud_nicking removed — no phone-home. Drop the key.
               this.config.set("allow_cloud_nicking", null);
               // fall through
            case 5:
               // [W-Nick] new keys added by this fork.
               if (this.config.getString("message_prefix", null) == null) {
                  this.config.set("message_prefix", "<gold>[W-Nick]</gold> ");
               }
               if (this.config.getString("auto_apply_nick_on_join", null) == null) {
                  this.config.set("auto_apply_nick_on_join", true);
               }
               if (this.config.getString("auto_assign_random_rank_on_random_nick", null) == null) {
                  this.config.set("auto_assign_random_rank_on_random_nick", true);
               }
               // [W-Nick] Drop the now-unused allow_cloud_nicking key if it exists.
               this.config.set("allow_cloud_nicking", null);
               break;
            default:
               // Unknown version — initialize all W-Nick keys defensively.
               this.config.set("message_prefix", "<gold>[W-Nick]</gold> ");
               this.config.set("auto_apply_nick_on_join", true);
               this.config.set("auto_assign_random_rank_on_random_nick", true);
               this.config.set("allow_cloud_nicking", null);
         }

         this.config.set("version", current + 1);
         if (this.config.getInt("version") < 6) {
            this.updateConfigToLatest();
         } else {
            this.config.save();
            this.getLogger().info("Config updated to W-Nick v6 successfully.");
         }
      }
   }

   public void onEnable() {
      PacketEvents.getAPI().init();
      PacketEvents.getAPI().getEventManager().registerListener(new PlayerInfoPacketListener(this));
      CommandAPI.onEnable();
      instance = this;
      this.paperSkinManager = new PaperSkinManager();
      // [W-Nick] CloudNickService removed — no phone-home, no IP leak.
      // Random /nick generation now uses the local UsernameGenerator exclusively.

      // [W-Nick] Auto-apply saved nick on join
      this.getServer().getPluginManager().registerEvents(
         new wnick.listeners.AutoNickJoinListener(this), this);

      for (Player player : this.getServer().getOnlinePlayers()) {
         Bukkit.getScheduler()
            .runTaskAsynchronously(this, () -> this.storageManager.getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> {
                  if (nameTagPlayer == null) {
                     nameTagPlayer = new NickPlayer(player.getUniqueId());
                     nameTagPlayer.setOriginalName(player.getName());

                     try {
                        GameProfile profile = this.paperSkinManager.getProfile(player);
                        Method propertiesMethod = isPaper1219OrNewer()
                           ? GameProfile.class.getMethod("properties")
                           : GameProfile.class.getMethod("getProperties");
                        PropertyMap propertyMap = (PropertyMap)propertiesMethod.invoke(profile);
                        Collection<Property> textures = propertyMap.get("textures");
                        if (textures != null && !textures.isEmpty()) {
                           Property skinProperty = textures.iterator().next();
                           nameTagPlayer.setOriginalTexture(skinProperty.value());
                           nameTagPlayer.setOriginalSignature(skinProperty.signature());
                           this.getLogger().info("Stored original skin for " + player.getName());
                        }
                     } catch (Exception var8) {
                        this.getLogger().warning("Could not fetch original skin for " + player.getName() + ": " + var8.getMessage());
                     }

                     this.storageManager.getStorage().savePlayer(nameTagPlayer);
                  }

                  this.playerCache.put(player.getUniqueId(), nameTagPlayer);
               }));
      }

      this.getLogger().info("W-Nick UserHandler initialized for " + this.getServer().getOnlinePlayers().size() + " online players");
      // [W-Nick] VersionUpdater removed — no phone-home to third-party servers.
      this.registerCommands();
      // [W-Nick] TAB integration via pure reflection so we survive any TAB
      // API version mismatch (notably EventBus.register changed signature
      // between (Class, Consumer) and (Class, EventHandler) across versions).
      if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
         this.tabIntegration = new TabIntegration(this);
         if (this.tabIntegration.hook()) {
            this.tabIntegration.registerPlayerLoadListener();
         } else {
            this.tabIntegration = null;
         }
      }

      if (this.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
         try {
            LuckPerms luckPerms = (LuckPerms)this.getServer().getServicesManager().load(LuckPerms.class);
            if (luckPerms != null) {
               this.fakeRankManager = new FakeRankManager(luckPerms);
               this.getLogger().info("Hooked into LuckPerms! Fake rank support enabled. Use /nickrank to manage ranks.");
            } else {
               this.getLogger().warning("LuckPerms is installed but its service is not registered. Fake rank support disabled.");
            }
         } catch (NoClassDefFoundError classErr) {
            // The PaperPluginClassLoader may isolate LuckPerms's classes
            // even when the plugin is enabled — this happens when
            // paper-plugin.yml has join-classpath: false for LuckPerms.
            // Fall back to no-LuckPerms mode instead of crashing onEnable.
            this.getLogger().warning("LuckPerms is installed but its API classes are not visible to W-Nick's classloader. Fake rank support disabled.");
            this.getLogger().warning("If you are using paper-plugin.yml, make sure 'join-classpath: true' is set for LuckPerms.");
            this.getLogger().warning("Underlying error: " + classErr.getMessage());
         } catch (Exception ex) {
            this.getLogger().warning("Failed to hook into LuckPerms: " + ex.getMessage());
            ex.printStackTrace();
         }
      }

      if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
         new PlaceholderManager(this).register();
         this.getLogger().info("Hooked into PlaceholderAPI!");
      }
   }

   public PaperSkinManager getPaperSkinManager() {
      return this.paperSkinManager;
   }

   /**
    * @deprecated Use {@link #attemptToUpdateTabPlayer(Player)} instead. The
    * old method took a TAB {@code TabPlayer} directly, but W-Nick now talks
    * to TAB via reflection, so there is no compile-time {@code TabPlayer} type.
    */
   @Deprecated(since = "1.0.81-W-Nick", forRemoval = true)
   public void updateTabPlayer(Object ignoredTabPlayer) {
      // no-op — the reflection-based TabIntegration.refreshPlayer() handles this now.
   }

   public StorageManager getStorageManager() {
      return this.storageManager;
   }

   public static NameTagPlugin instance() {
      return instance;
   }

   public ConcurrentHashMap<UUID, NickPlayer> getPlayerCache() {
      return this.playerCache;
   }

   @Nullable
   public FakeRankManager getFakeRankManager() {
      return this.fakeRankManager;
   }

   private <T> void executeStorageOperation(CompletableFuture<T> future, String operation) {
      this.executeStorageOperation(future, operation, () -> {
      });
   }

   private <T> void executeStorageOperation(CompletableFuture<T> future, String operation, Runnable callback) {
      future.whenComplete((result, throwable) -> {
         if (throwable != null) {
            this.getLogger().severe("Storage operation failed (" + operation + "): " + throwable.getMessage());
            throwable.printStackTrace();
         } else {
            this.getLogger().warning("Successfully executed operation (" + operation + ")");
            callback.run();
         }
      });
   }

   private void registerCommands() {
      new NickCommand(this).register();
      new RandomNickCommand(this).register();
      new NameTagCommand(this).register();
      new RealNameCommand(this).register();
      // [LuckPerms extension] Dedicated UI for choosing / previewing the fake rank
      // that gets spoofed alongside a nickname. See NickRankCommand for details.
      new wnick.command.NickRankCommand(this).register();
      // [W-Nick] Master command for help, info, version, reload
      new wnick.command.WNickCommand(this).register();
   }

   public Config config() {
      return this.config;
   }

   /**
    * Wrap a MiniMessage string with the configurable W-Nick prefix.
    *
    * The prefix is read from {@code config.yml} under {@code message_prefix}
    * and defaults to {@code "<gold>[W-Nick]</gold> "}. Set it to an empty
    * string to disable the prefix entirely.
    */
   public net.kyori.adventure.text.Component prefixedMessage(String miniMessage) {
      String prefix = this.config != null ? this.config.getString("message_prefix", "<gold>[W-Nick]</gold> ") : "<gold>[W-Nick]</gold> ";
      String full = prefix + miniMessage;
      return MiniMsg.deserialize(full);
   }

   public void onDisable() {
      PacketEvents.getAPI().terminate();
      CommandAPI.onDisable();
      if (this.storageManager != null) {
         try {
            this.storageManager.close().join();
            this.getLogger().info("Storage system closed successfully");
         } catch (Exception var2) {
            this.getLogger().severe("Failed to close storage system: " + var2.getMessage());
            var2.printStackTrace();
         }
      }
   }

   public boolean hasNick(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached != null) {
         return cached.hasNick();
      } else {
         NickPlayer nickPlayer = this.storageManager.getStorage().loadPlayer(player.getUniqueId()).join();
         return nickPlayer != null && nickPlayer.hasNick();
      }
   }

   @Nullable
   public String getNick(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached != null) {
         return cached.getNickname();
      } else {
         NickPlayer nickPlayer = this.storageManager.getStorage().loadPlayer(player.getUniqueId()).join();
         if (nickPlayer != null) {
            this.playerCache.put(player.getUniqueId(), nickPlayer);
         }

         return nickPlayer != null ? nickPlayer.getNickedName() : null;
      }
   }

   @Nullable
   public String getNick(UUID uniqueId) {
      NickPlayer cached = this.playerCache.get(uniqueId);
      if (cached != null) {
         return cached.getNickname();
      } else {
         NickPlayer nickPlayer = this.storageManager.getStorage().loadPlayer(uniqueId).join();
         if (nickPlayer != null) {
            this.playerCache.put(uniqueId, nickPlayer);
         }

         return nickPlayer != null ? nickPlayer.getNickname() : null;
      }
   }

   @Nullable
   public Component getNickComponent(Player player) {
      String nickname = this.getNick(player);
      return nickname != null ? MiniMsg.deserialize(nickname) : null;
   }

   @Nullable
   public Component getNickComponent(UUID uniqueId) {
      String nickname = this.getNick(uniqueId);
      return nickname != null ? MiniMsg.deserialize(nickname) : null;
   }

   @Nullable
   public Skin getSkin(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached != null) {
         return cached.getSkin();
      } else {
         NickPlayer nickPlayer = this.storageManager.getStorage().loadPlayer(player.getUniqueId()).join();
         if (nickPlayer != null) {
            this.playerCache.put(player.getUniqueId(), nickPlayer);
         }

         return nickPlayer != null ? nickPlayer.getSkin() : null;
      }
   }

   @Nullable
   public Skin getSkin(UUID uniqueId) {
      NickPlayer cached = this.playerCache.get(uniqueId);
      if (cached != null) {
         return cached.getSkin();
      } else {
         NickPlayer nickPlayer = this.storageManager.getStorage().loadPlayer(uniqueId).join();
         if (nickPlayer != null) {
            this.playerCache.put(uniqueId, nickPlayer);
         }

         return nickPlayer != null ? nickPlayer.getSkin() : null;
      }
   }

   public void resetAllNicks() {
      int[] onlineResetCount = new int[]{0};

      for (Player player : this.getServer().getOnlinePlayers()) {
         if (this.hasNick(player)) {
            this.resetNick(player);
            onlineResetCount[0]++;
         }
      }

      this.storageManager
         .getStorage()
         .getAllPlayers()
         .whenComplete(
            (storedPlayers, throwable) -> {
               if (throwable != null) {
                  this.getLogger().severe("Failed to reset stored data: " + throwable.getMessage());
                  throwable.printStackTrace();
               } else if (storedPlayers != null && !storedPlayers.isEmpty()) {
                  List<CompletableFuture<Void>> deleteFutures = new ArrayList<>();

                  for (NickPlayer storedPlayer : storedPlayers) {
                     deleteFutures.add(this.storageManager.getStorage().deletePlayer(storedPlayer.getUuid()));
                  }

                  CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0]))
                     .whenComplete(
                        (result, deleteThrowable) -> {
                           if (deleteThrowable != null) {
                              this.getLogger().severe("Failed to delete some stored data: " + deleteThrowable.getMessage());
                              deleteThrowable.printStackTrace();
                           } else {
                              this.playerCache.clear();
                              this.getLogger()
                                 .info(
                                    "Successfully reset "
                                       + onlineResetCount[0]
                                       + " online players and "
                                       + storedPlayers.size()
                                       + " stored players. All nickname data has been cleared."
                                 );
                           }
                        }
                     );
               } else {
                  this.getLogger().info("Successfully reset " + onlineResetCount[0] + " online players. No stored data found.");
               }
            }
         );
   }

   public void shouldChangeUniqueId(boolean shouldChange) {
      this.config.set("should_spoof_uuid", shouldChange);
   }

   public boolean shouldChangeUniqueId() {
      return this.config.getBoolean("should_spoof_uuid");
   }

   public void saveAllPlayersData() {
      this.getLogger().info("Starting sequential save of all online players' data...");
      List<Player> onlinePlayers = new ArrayList<>(this.getServer().getOnlinePlayers());
      int totalPlayers = onlinePlayers.size();
      int savedCount = 0;

      for (Player player : onlinePlayers) {
         NickPlayer cached = this.playerCache.get(player.getUniqueId());
         if (cached != null && cached.hasNick()) {
            try {
               this.storageManager.getStorage().savePlayer(cached).join();
               this.getLogger().info("Saved data for " + player.getName() + " (" + ++savedCount + "/" + totalPlayers + ")");
            } catch (Exception var8) {
               this.getLogger().severe("Failed to save data for " + player.getName() + ": " + var8.getMessage());
            }
         }
      }

      this.getLogger().info("Completed saving data for " + savedCount + " players out of " + totalPlayers + " online players.");
   }

   public void saveAllCachedPlayersData() {
      this.getLogger().info("Starting sequential save of all cached players' data...");
      List<NickPlayer> cachedPlayers = new ArrayList<>(this.playerCache.values());
      int totalPlayers = cachedPlayers.size();
      int savedCount = 0;

      for (NickPlayer cachedPlayer : cachedPlayers) {
         if (cachedPlayer.hasNick()) {
            try {
               this.storageManager.getStorage().savePlayer(cachedPlayer).join();
               this.getLogger().info("Saved cached data for " + cachedPlayer.getUuid() + " (" + ++savedCount + "/" + totalPlayers + ")");
            } catch (Exception var7) {
               this.getLogger().severe("Failed to save cached data for " + cachedPlayer.getUuid() + ": " + var7.getMessage());
            }
         }
      }

      this.getLogger().info("Completed saving data for " + savedCount + " cached players out of " + totalPlayers + " total cached players.");
   }

   public boolean hasNick(UUID uniqueId) {
      NickPlayer cached = this.playerCache.get(uniqueId);
      return cached != null ? cached.hasNick() : this.storageManager.getStorage().hasPlayer(uniqueId).join();
   }

   public void setNickname(Player player, String name) {
      this.setNickname(player, MiniMsg.deserialize(MiniMsg.convertAmpersandToMiniMessage(name)));
   }

   public void setNickname(Player player, Component component) {
      String rawString = (String)MiniMessage.miniMessage().serialize(component);
      String translatedText = LegacyComponentSerializer.legacySection().serialize(component);
      this.paperSkinManager.setProfileName(player, translatedText);
      if (this.config().getBoolean("should_spoof_uuid")) {
         this.paperSkinManager.setUniqueId(player, translatedText);
      } else {
         this.paperSkinManager.refreshPlayer(player);
      }

      player.playerListName(component);
      player.displayName(component);
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      CompletableFuture<NickPlayer> dataFuture;
      if (cached != null) {
         dataFuture = CompletableFuture.completedFuture(cached);
      } else {
         dataFuture = this.storageManager.getStorage().loadPlayer(player.getUniqueId());
      }

      dataFuture.thenAcceptAsync(nameTagPlayer -> {
         NickPlayer playerData = nameTagPlayer != null ? nameTagPlayer : new NickPlayer(player.getUniqueId());
         if (playerData.getOriginalName() == null) {
            String[] mojangProfile = MojangSkinFetcher.fetchMojangProfile(player.getUniqueId());
            if (mojangProfile != null) {
               playerData.setOriginalName(mojangProfile[0]);
               if (mojangProfile[1] != null && playerData.getOriginalTexture() == null) {
                  playerData.setOriginalTexture(mojangProfile[1]);
                  playerData.setOriginalSignature(mojangProfile[2]);
               }

               this.getLogger().info("Captured original profile from Mojang for " + mojangProfile[0]);
            } else {
               this.getLogger().warning("Could not capture original profile from Mojang for UUID " + player.getUniqueId());
            }
         }

         playerData.setNickname(rawString);
         this.playerCache.put(player.getUniqueId(), playerData);
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(playerData), "save nickname for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }).whenComplete((result, throwable) -> {
         if (throwable != null) {
            this.getLogger().severe("Failed to load player data for nickname update: " + throwable.getMessage());
            throwable.printStackTrace();
         }
      });
   }

   public void attemptToUpdateTabPlayer(Player player) {
      // [W-Nick] All TAB interaction is now done via reflection in TabIntegration,
      // so we don't crash on TAB API version mismatches (NoSuchMethodError etc).
      if (this.tabIntegration != null) {
         this.tabIntegration.refreshPlayer(player);
      }
   }

   public void setSkinFromPlayer(Player player, String playerName) {
      this.paperSkinManager.setSkinFromName(this, player, playerName);
      this.storageManager.getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> {
         NickPlayer playerData = nameTagPlayer != null ? nameTagPlayer : new NickPlayer(player.getUniqueId());
         playerData.setSkinName(playerName);
         playerData.setTexture(null);
         playerData.setSignature(null);
         this.playerCache.put(player.getUniqueId(), playerData);
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(playerData), "save skin for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }).whenComplete((result, throwable) -> {
         if (throwable != null) {
            this.getLogger().severe("Failed to load player data for skin update: " + throwable.getMessage());
            throwable.printStackTrace();
         }
      });
   }

   public void setNickFromPlayer(Player player, String playerName) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      CompletableFuture<NickPlayer> dataFuture;
      if (cached != null) {
         dataFuture = CompletableFuture.completedFuture(cached);
      } else {
         dataFuture = this.storageManager.getStorage().loadPlayer(player.getUniqueId());
      }

      dataFuture.thenAcceptAsync(nameTagPlayer -> {
         NickPlayer playerData = nameTagPlayer != null ? nameTagPlayer : new NickPlayer(player.getUniqueId());
         String originalName = playerData.getOriginalName();
         if (originalName == null) {
            String[] mojangProfile = MojangSkinFetcher.fetchMojangProfile(player.getUniqueId());
            if (mojangProfile != null) {
               originalName = mojangProfile[0];
               playerData.setOriginalName(originalName);
               if (mojangProfile[1] != null) {
                  playerData.setOriginalTexture(mojangProfile[1]);
                  playerData.setOriginalSignature(mojangProfile[2]);
               }

               this.getLogger().info("Stored original profile from Mojang for " + originalName);
            } else {
               originalName = player.getName();
               playerData.setOriginalName(originalName);
               this.getLogger().warning("Mojang API unavailable, using player.getName() for " + originalName);

               try {
                  GameProfile profile = this.paperSkinManager.getProfile(player);
                  Method propertiesMethod = isPaper1219OrNewer() ? GameProfile.class.getMethod("properties") : GameProfile.class.getMethod("getProperties");
                  PropertyMap propertyMap = (PropertyMap)propertiesMethod.invoke(profile);
                  Collection<Property> textures = propertyMap.get("textures");
                  if (textures != null && !textures.isEmpty()) {
                     Property skinProperty = textures.iterator().next();
                     playerData.setOriginalTexture(skinProperty.value());
                     playerData.setOriginalSignature(skinProperty.signature());
                  }
               } catch (Exception var12) {
                  this.getLogger().warning("Could not fetch original skin for " + originalName + ": " + var12.getMessage());
               }
            }
         }

         Tasks.run(this, () -> {
            this.paperSkinManager.setProfileName(player, playerName);
            this.paperSkinManager.setSkinFromName(this, player, playerName);
            playerData.setNickname(playerName);
            playerData.setSkinName(playerName);
            playerData.setTexture(null);
            playerData.setSignature(null);
            this.playerCache.put(player.getUniqueId(), playerData);
            this.executeStorageOperation(this.storageManager.getStorage().savePlayer(playerData), "save nick from player for " + player.getName(), () -> {
               if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
                  this.attemptToUpdateTabPlayer(player);
               }
            });
            Tasks.runAsync(this, () -> {
               if (this.config().getBoolean("should_spoof_uuid")) {
                  try {
                     if (MojangSkinFetcher.fetchUUID(playerName) == null) {
                        this.paperSkinManager.setUniqueId(player, UUID.randomUUID());
                     } else {
                        this.paperSkinManager.setUniqueId(player, playerName);
                     }
                  } catch (Exception var4x) {
                     this.paperSkinManager.setUniqueId(player, playerName);
                  }
               }
            });
         });
      });
   }

   public void randomNick(Player player) {
      this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
         // [W-Nick] Cloud nicking removed — uses local UsernameGenerator exclusively.
         String generatedUsername = this.findAvailableUsername();

         String finalUsername = generatedUsername;
         if (finalUsername == null) {
            this.getLogger().warning("Failed to generate a random nickname for " + player.getName());
         } else {
            String randomSkinPlayer = SkinProvider.getRandomPlayerName(finalUsername);
            this.getServer().getScheduler().runTask(this, () -> {
               if (player.isOnline()) {
                  this.setNickWithSkin(player, finalUsername, randomSkinPlayer, null);
               }
            });
         }
      });
   }

   public void randomNick(Player player, String groupName) {
      this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
         // [W-Nick] Cloud nicking removed — uses local UsernameGenerator exclusively.
         String generatedUsername = this.findAvailableUsername();

         String finalUsername = generatedUsername;
         if (finalUsername == null) {
            this.getLogger().warning("Failed to generate a random nickname for " + player.getName());
         } else {
            String randomSkinPlayer = SkinProvider.getRandomPlayerName(finalUsername);
            this.getServer().getScheduler().runTask(this, () -> {
               if (player.isOnline()) {
                  this.setNickWithSkin(player, finalUsername, randomSkinPlayer, groupName);
               }
            });
         }
      });
   }

   public void setNickFromPlayer(Player player, String playerName, String groupName) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      CompletableFuture<NickPlayer> dataFuture = cached != null
         ? CompletableFuture.completedFuture(cached)
         : this.storageManager.getStorage().loadPlayer(player.getUniqueId());
      dataFuture.thenAcceptAsync(
         nameTagPlayer -> {
            NickPlayer playerData = nameTagPlayer != null ? nameTagPlayer : new NickPlayer(player.getUniqueId());
            String originalName = playerData.getOriginalName();
            if (originalName == null) {
               String[] mojangProfile = MojangSkinFetcher.fetchMojangProfile(player.getUniqueId());
               if (mojangProfile != null) {
                  playerData.setOriginalName(mojangProfile[0]);
                  if (mojangProfile[1] != null) {
                     playerData.setOriginalTexture(mojangProfile[1]);
                     playerData.setOriginalSignature(mojangProfile[2]);
                  }
               } else {
                  playerData.setOriginalName(player.getName());
               }
            }

            Tasks.run(
               this,
               () -> {
                  this.paperSkinManager.setProfileName(player, playerName);
                  this.paperSkinManager.setSkinFromName(this, player, playerName);
                  playerData.setNickname(playerName);
                  playerData.setSkinName(playerName);
                  playerData.setTexture(null);
                  playerData.setSignature(null);
                  if (this.fakeRankManager != null && this.fakeRankManager.getRank(groupName) != null) {
                     playerData.setFakeRankId(groupName);
                  }

                  this.playerCache.put(player.getUniqueId(), playerData);
                  this.executeStorageOperation(
                     this.storageManager.getStorage().savePlayer(playerData), "save nick from player (with rank) for " + player.getName(), () -> {
                        if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
                           this.attemptToUpdateTabPlayer(player);
                        }
                     }
                  );
                  Tasks.runAsync(this, () -> {
                     if (this.config().getBoolean("should_spoof_uuid")) {
                        try {
                           if (MojangSkinFetcher.fetchUUID(playerName) == null) {
                              this.paperSkinManager.setUniqueId(player, UUID.randomUUID());
                           } else {
                              this.paperSkinManager.setUniqueId(player, playerName);
                           }
                        } catch (Exception var4x) {
                           this.paperSkinManager.setUniqueId(player, playerName);
                        }
                     }
                  });
               }
            );
         }
      );
   }

   public void setNickname(Player player, String name, String groupName) {
      this.setNickname(player, name);
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached != null && this.fakeRankManager != null && this.fakeRankManager.getRank(groupName) != null) {
         cached.setFakeRankId(groupName);
         this.playerCache.put(player.getUniqueId(), cached);
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(cached), "set fake rank via setNickname for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }
   }

   public void setFakeRank(Player player, String groupName) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached != null) {
         if (this.fakeRankManager != null && this.fakeRankManager.getRank(groupName) != null) {
            cached.setFakeRankId(groupName);
            this.playerCache.put(player.getUniqueId(), cached);
            this.executeStorageOperation(this.storageManager.getStorage().savePlayer(cached), "set fake rank for " + player.getName(), () -> {
               if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
                  this.attemptToUpdateTabPlayer(player);
               }
            });
         }
      }
   }

   public void clearFakeRank(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached != null) {
         cached.setFakeRankId(null);
         this.playerCache.put(player.getUniqueId(), cached);
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(cached), "clear fake rank for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }
   }

   private void setNickWithSkin(Player player, String nickName, String skinName, @Nullable String rankIdOverride) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      CompletableFuture<NickPlayer> dataFuture;
      if (cached != null) {
         dataFuture = CompletableFuture.completedFuture(cached);
      } else {
         dataFuture = this.storageManager.getStorage().loadPlayer(player.getUniqueId());
      }

      dataFuture.thenAcceptAsync(nameTagPlayer -> {
         NickPlayer playerData = nameTagPlayer != null ? nameTagPlayer : new NickPlayer(player.getUniqueId());
         String originalName = playerData.getOriginalName();
         if (originalName == null) {
            String[] mojangProfile = MojangSkinFetcher.fetchMojangProfile(player.getUniqueId());
            if (mojangProfile != null) {
               originalName = mojangProfile[0];
               playerData.setOriginalName(originalName);
               if (mojangProfile[1] != null) {
                  playerData.setOriginalTexture(mojangProfile[1]);
                  playerData.setOriginalSignature(mojangProfile[2]);
               }

               this.getLogger().info("Stored original profile from Mojang for " + originalName);
            } else {
               originalName = player.getName();
               playerData.setOriginalName(originalName);
               this.getLogger().warning("Mojang API unavailable, using player.getName() for " + originalName);

               try {
                  GameProfile profile = this.paperSkinManager.getProfile(player);
                  Method propertiesMethod = isPaper1219OrNewer() ? GameProfile.class.getMethod("properties") : GameProfile.class.getMethod("getProperties");
                  PropertyMap propertyMap = (PropertyMap)propertiesMethod.invoke(profile);
                  Collection<Property> textures = propertyMap.get("textures");
                  if (textures != null && !textures.isEmpty()) {
                     Property skinProperty = textures.iterator().next();
                     playerData.setOriginalTexture(skinProperty.value());
                     playerData.setOriginalSignature(skinProperty.signature());
                  }
               } catch (Exception var13) {
                  this.getLogger().warning("Could not fetch original skin for " + originalName + ": " + var13.getMessage());
               }
            }
         }

         Tasks.run(this, () -> {
            this.paperSkinManager.setProfileName(player, nickName);
            this.paperSkinManager.setSkinFromName(this, player, skinName);
            playerData.setNickname(nickName);
            playerData.setSkinName(skinName);
            playerData.setTexture(null);
            playerData.setSignature(null);
            if (this.fakeRankManager != null) {
               // [LuckPerms extension] Honor an explicit rank override (e.g. from
               // /nick random -r <rank> or randomNick(player, groupName)) when
               // it resolves to a real LuckPerms group. Otherwise fall back to
               // the legacy behaviour of picking a random assignable rank, but
               // only when the player doesn't already have a fake rank set
               // (so /nickrank set <rank> persists across /nick random).
               String overrideId = rankIdOverride != null
                  ? this.fakeRankManager.resolveRankIdCaseInsensitive(rankIdOverride)
                  : null;
               if (overrideId != null) {
                  playerData.setFakeRankId(overrideId);
               } else if (playerData.getFakeRankId() == null
                       && this.config().getBoolean("auto_assign_random_rank_on_random_nick", true)) {
                  FakeRankManager.FakeRank rank = this.fakeRankManager.getRandomRank();
                  playerData.setFakeRankId(rank != null ? rank.id() : null);
               }
            }

            this.playerCache.put(player.getUniqueId(), playerData);
            this.executeStorageOperation(this.storageManager.getStorage().savePlayer(playerData), "save random nick for " + player.getName(), () -> {
               if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
                  this.attemptToUpdateTabPlayer(player);
               }
            });
            Tasks.runAsync(this, () -> {
               if (this.config().getBoolean("should_spoof_uuid")) {
                  try {
                     if (MojangSkinFetcher.fetchUUID(nickName) == null) {
                        this.paperSkinManager.setUniqueId(player, UUID.randomUUID());
                     } else {
                        this.paperSkinManager.setUniqueId(player, nickName);
                     }
                  } catch (Exception var4x) {
                     this.paperSkinManager.setUniqueId(player, nickName);
                  }
               }
            });
         });
      });
   }

   private String findAvailableUsername() {
      for (int i = 0; i < 50; i++) {
         String username = UsernameGenerator.generateUsername();
         if (username.length() >= 3 && username.length() <= 16) {
            try {
               if (MojangSkinFetcher.fetchUUID(username) == null) {
                  return username;
               }
            } catch (Exception var4) {
               this.getLogger().warning("Failed to check username availability for '" + username + "': " + var4.getMessage());
               return username;
            }
         }
      }

      return null;
   }

   public boolean setSkinFromTextureAndSignature(Player player, String texture, String signature) {
      this.paperSkinManager.setSkin(player, texture, signature);
      this.paperSkinManager.refreshPlayer(player);
      this.storageManager.getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> {
         NickPlayer playerData = nameTagPlayer != null ? nameTagPlayer : new NickPlayer(player.getUniqueId());
         playerData.setTexture(texture);
         playerData.setSignature(signature);
         playerData.setSkinName(null);
         this.playerCache.put(player.getUniqueId(), playerData);
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(playerData), "save mineskin for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }).whenComplete((result, throwable) -> {
         if (throwable != null) {
            this.getLogger().severe("Failed to load player data for mineskin update: " + throwable.getMessage());
            throwable.printStackTrace();
         }
      });
      return true;
   }

   public boolean setSkinFromMineskinId(Player player, String id) {
      String[] textureAndSignature = fetchTextureAndSignatureFromMineskinId(id);
      return textureAndSignature == null ? false : this.setSkinFromTextureAndSignature(player, textureAndSignature[0], textureAndSignature[1]);
   }

   public boolean setSkinFromMineskinUrl(Player player, String url) {
      String skinId = url.replaceAll(".*/", "");
      return this.setSkinFromMineskinId(player, skinId);
   }

   @Nullable
   public Skin getSkinFromMineskinId(String id) {
      String[] textureAndSignature = fetchTextureAndSignatureFromMineskinId(id);
      return textureAndSignature == null ? null : new Skin(textureAndSignature[0], textureAndSignature[1]);
   }

   @Nullable
   public Skin getSkinFromMineskinUrl(String url) {
      String skinId = url.replaceAll(".*/", "");
      return this.getSkinFromMineskinId(skinId);
   }

   public void resetNickname(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached == null) {
         this.storageManager.getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> this.resetNicknameSync(player, nameTagPlayer));
      } else {
         this.resetNicknameSync(player, cached);
      }
   }

   private void resetNicknameSync(Player player, NickPlayer nickPlayer) {
      if (nickPlayer != null && nickPlayer.getOriginalName() != null) {
         this.paperSkinManager.setProfileName(player, nickPlayer.getOriginalName());
         if (nickPlayer.getOriginalTexture() != null && nickPlayer.getOriginalSignature() != null) {
            this.paperSkinManager.setSkin(player, nickPlayer.getOriginalTexture(), nickPlayer.getOriginalSignature());
         }

         this.paperSkinManager.refreshPlayer(player);
         nickPlayer.setNickname(null);
         nickPlayer.setNickedName(nickPlayer.getOriginalName());
         nickPlayer.setFakeRankId(null);
         this.playerCache.put(player.getUniqueId(), nickPlayer);
         String originalName = nickPlayer.getOriginalName();
         player.playerListName(MiniMsg.deserialize(originalName));
         player.displayName(MiniMsg.deserialize(originalName));
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(nickPlayer), "reset nickname for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }
   }

   public void resetSkin(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached == null) {
         this.storageManager.getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> this.resetSkinSync(player, nameTagPlayer));
      } else {
         this.resetSkinSync(player, cached);
      }
   }

   private void resetSkinSync(Player player, NickPlayer nickPlayer) {
      if (nickPlayer != null && nickPlayer.getOriginalTexture() != null) {
         this.paperSkinManager.setSkin(player, nickPlayer.getOriginalTexture(), nickPlayer.getOriginalSignature());
         this.paperSkinManager.refreshPlayer(player);
         nickPlayer.setTexture(null);
         nickPlayer.setSignature(null);
         nickPlayer.setSkinName(null);
         this.playerCache.put(player.getUniqueId(), nickPlayer);
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(nickPlayer), "reset skin for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }
   }

   public void resetNick(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached == null) {
         this.storageManager.getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> {
            if (nameTagPlayer != null) {
               this.ensureOriginalData(player, nameTagPlayer, () -> Tasks.run(this, () -> this.resetNickSync(player, nameTagPlayer)));
            }
         });
      } else {
         this.ensureOriginalData(player, cached, () -> Tasks.run(this, () -> this.resetNickSync(player, cached)));
      }
   }

   private void ensureOriginalData(Player player, NickPlayer nickPlayer, Runnable callback) {
      if (nickPlayer.getOriginalName() != null && nickPlayer.getOriginalTexture() != null) {
         callback.run();
      } else {
         Tasks.runAsync(this, () -> {
            if (nickPlayer.getOriginalName() == null || nickPlayer.getOriginalTexture() == null) {
               String[] mojangProfile = MojangSkinFetcher.fetchMojangProfile(player.getUniqueId());
               if (mojangProfile != null) {
                  if (nickPlayer.getOriginalName() == null) {
                     nickPlayer.setOriginalName(mojangProfile[0]);
                     this.getLogger().info("Fetched original name from Mojang: " + mojangProfile[0]);
                  }

                  if (nickPlayer.getOriginalTexture() == null && mojangProfile[1] != null) {
                     nickPlayer.setOriginalTexture(mojangProfile[1]);
                     nickPlayer.setOriginalSignature(mojangProfile[2]);
                     this.getLogger().info("Fetched original skin from Mojang for " + nickPlayer.getOriginalName());
                  }
               } else {
                  this.getLogger().warning("Failed to fetch Mojang profile for UUID " + player.getUniqueId());
               }
            }

            callback.run();
         });
      }
   }

   private void resetNickSync(Player player, NickPlayer nickPlayer) {
      if (nickPlayer != null && nickPlayer.getOriginalName() != null) {
         String originalName = nickPlayer.getOriginalName();
         String originalTexture = nickPlayer.getOriginalTexture();
         String originalSignature = nickPlayer.getOriginalSignature();

         // If we don't have the original texture stored, try to fetch it
         // from Mojang before resetting. This fixes the bug where /nick reset
         // doesn't restore the original skin.
         if (originalTexture == null) {
            this.getLogger().warning("No original texture stored for " + originalName + " — fetching from Mojang...");
            String[] mojangProfile = MojangSkinFetcher.fetchMojangProfile(player.getUniqueId());
            if (mojangProfile != null && mojangProfile[1] != null) {
               originalTexture = mojangProfile[1];
               originalSignature = mojangProfile[2];
               nickPlayer.setOriginalTexture(originalTexture);
               nickPlayer.setOriginalSignature(originalSignature);
               this.getLogger().info("Fetched original skin from Mojang for " + originalName);
            } else {
               this.getLogger().warning("Could not fetch original skin from Mojang for " + originalName);
            }
         }

         this.getLogger().info("Resetting nick for " + player.getName() + " back to " + originalName);

         // Set the original skin FIRST, then the name. This order matters:
         // setProfileName calls forcePlayerUpdate (hide/show) which broadcasts
         // the profile to other players. If we set the name first, other players
         // briefly see the nicked skin with the original name. By setting the
         // skin first, the profile is already correct when the name changes.
         if (originalTexture != null) {
            this.getLogger().info("Restoring original skin...");
            this.paperSkinManager.setSkin(player, originalTexture, originalSignature);
         } else {
            this.getLogger().warning("No original texture to restore for " + originalName + " — skin will remain as-is");
         }

         this.paperSkinManager.setProfileName(player, originalName);
         this.paperSkinManager.refreshPlayer(player);
         player.playerListName(MiniMsg.deserialize(originalName));
         player.displayName(MiniMsg.deserialize(originalName));
         nickPlayer.setNickname(null);
         nickPlayer.setSkinName(null);
         nickPlayer.setTexture(null);
         nickPlayer.setSignature(null);
         nickPlayer.setNickedName(null);
         nickPlayer.setFakeRankId(null);
         this.playerCache.put(player.getUniqueId(), nickPlayer);
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(nickPlayer), "reset nick for " + originalName, () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }
   }
}

package gg.lode.nametag;

import com.github.retrooper.packetevents.nametag.PacketEvents;
import com.github.retrooper.packetevents.nametag.settings.PacketEventsSettings;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.jorel.commandapi.nametag.CommandAPI;
import dev.jorel.commandapi.nametag.CommandAPIPaperConfig;
import gg.lode.bookshelfapi.api.Configuration;
import gg.lode.bookshelfapi.api.Task;
import gg.lode.bookshelfapi.api.VersionUpdater;
import gg.lode.bookshelfapi.api.mojang.MojangProfile;
import gg.lode.bookshelfapi.api.util.Metrics;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;
import gg.lode.nametag.command.NameTagCommand;
import gg.lode.nametag.command.NickCommand;
import gg.lode.nametag.command.RandomNickCommand;
import gg.lode.nametag.command.RealNameCommand;
import gg.lode.nametag.listeners.PlayerInfoPacketListener;
import gg.lode.nametag.nms.PaperSkinManager;
import gg.lode.nametag.storage.StorageManager;
import gg.lode.nametag.util.CloudNickService;
import gg.lode.nametag.util.FakeRankManager;
import gg.lode.nametag.util.MojangSkinFetcher;
import gg.lode.nametag.util.SkinProvider;
import gg.lode.nametag.util.UsernameGenerator;
import gg.lode.nametagapi.INameTagAPI;
import gg.lode.nametagapi.NameTagAPI;
import gg.lode.nametagapi.api.NickPlayer;
import gg.lode.nametagapi.api.Skin;
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
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
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

public final class NameTagPlugin extends JavaPlugin implements INameTagAPI {
   public static final String VERSION = "v1.0.81-W-Nick";
   private final ConcurrentHashMap<UUID, NickPlayer> playerCache = new ConcurrentHashMap<>();
   private StorageManager storageManager;
   private PaperSkinManager paperSkinManager;
   private CloudNickService cloudNickService;
   @Nullable
   private FakeRankManager fakeRankManager;
   private Configuration config;
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
      new Metrics(this, 24781);
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
      this.config = new Configuration(this, "config.yml");
      this.config.initialize();
      this.updateConfigToLatest();
      NameTagAPI.setApi(this);
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
               this.config.set("allow_cloud_nicking", true);
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
               break;
            default:
               // Unknown version — initialize all W-Nick keys defensively.
               this.config.set("message_prefix", "<gold>[W-Nick]</gold> ");
               this.config.set("auto_apply_nick_on_join", true);
               this.config.set("auto_assign_random_rank_on_random_nick", true);
         }

         this.config.set("version", current + 1);
         if (this.config.getInt("version") < 6) {
            this.updateConfigToLatest();
         } else {
            this.config.save();
            this.getLogger().info("Configuration updated to W-Nick v6 successfully.");
         }
      }
   }

   public void onEnable() {
      PacketEvents.getAPI().init();
      PacketEvents.getAPI().getEventManager().registerListener(new PlayerInfoPacketListener(this));
      CommandAPI.onEnable();
      instance = this;
      this.paperSkinManager = new PaperSkinManager();
      this.cloudNickService = new CloudNickService(this.getLogger(), this.getServer().getPort());

      // [W-Nick] Auto-apply saved nick on join
      this.getServer().getPluginManager().registerEvents(
         new gg.lode.nametag.listeners.AutoNickJoinListener(this), this);

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

      this.getLogger().info("NickAPI UserHandler initialized for " + this.getServer().getOnlinePlayers().size() + " online players");
      this.getServer()
         .getPluginManager()
         .registerEvents(new VersionUpdater(this, "W-Nick", "https://lode.gg/plugin/nametag", "https://lode.gg/api/plugins/nametag/version", VERSION), this);
      this.registerCommands();
      if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
         try {
            Class<?> tabClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Method getInstanceMethod = tabClass.getMethod("getInstance");
            TabAPI tabApiInstance = (TabAPI)getInstanceMethod.invoke(null);
            Objects.requireNonNull(tabApiInstance.getEventBus()).register(PlayerLoadEvent.class, event -> this.updateTabPlayer(event.getPlayer()));
            this.getLogger().warning("==========================================");
            this.getLogger().warning("Hooked into TAB!");
            this.getLogger().warning("Name Tag will now use TAB to display nicknames.");
            this.getLogger().warning("==========================================");
         } catch (Exception var4) {
            var4.printStackTrace();
         }
      }

      if (this.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
         LuckPerms luckPerms = (LuckPerms)this.getServer().getServicesManager().load(LuckPerms.class);
         if (luckPerms != null) {
            this.fakeRankManager = new FakeRankManager(luckPerms);
            this.getLogger().info("Hooked into LuckPerms! Fake rank support enabled. Use /nickrank to manage ranks.");
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

   public CloudNickService getCloudNickService() {
      return this.cloudNickService;
   }

   public void updateTabPlayer(TabPlayer player) {
      boolean nicked = this.hasNick(player.getUniqueId());
      String name = nicked ? this.getNick(player.getUniqueId()) : player.getName();
      String fakePrefix = null;
      String fakeSuffix = null;
      if (nicked && this.fakeRankManager != null) {
         NickPlayer nickData = this.playerCache.get(player.getUniqueId());
         if (nickData != null && nickData.getFakeRankId() != null) {
            FakeRankManager.FakeRank rank = this.fakeRankManager.getRank(nickData.getFakeRankId());
            if (rank != null) {
               fakePrefix = rank.prefix().isEmpty() ? null : rank.prefix();
               fakeSuffix = rank.suffix().isEmpty() ? null : rank.suffix();
            }
         }
      }

      try {
         TabAPI tabApiInstance = TabAPI.getInstance();
         if (tabApiInstance.getTabListFormatManager() != null) {
            tabApiInstance.getTabListFormatManager().setName(player, name);
            tabApiInstance.getTabListFormatManager().setPrefix(player, fakePrefix);
            tabApiInstance.getTabListFormatManager().setSuffix(player, fakeSuffix);
         }

         if (tabApiInstance.getNameTagManager() != null) {
            tabApiInstance.getNameTagManager().setPrefix(player, fakePrefix);
            tabApiInstance.getNameTagManager().setSuffix(player, fakeSuffix);
         }

         this.getLogger().info("[TAB] Updated " + player.getName() + " to " + name);
      } catch (Exception var8) {
         this.getLogger().warning("Failed to update player " + player.getName() + " in TAB: " + var8.getMessage());
      }
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
      new gg.lode.nametag.command.NickRankCommand(this).register();
      // [W-Nick] Master command for help, info, version, reload
      new gg.lode.nametag.command.WNickCommand(this).register();
   }

   public Configuration config() {
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
      return MiniMessageHelper.deserialize(full);
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

   @Override
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
   @Override
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
   @Override
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
   @Override
   public Component getNickComponent(Player player) {
      String nickname = this.getNick(player);
      return nickname != null ? MiniMessageHelper.deserialize(nickname) : null;
   }

   @Nullable
   @Override
   public Component getNickComponent(UUID uniqueId) {
      String nickname = this.getNick(uniqueId);
      return nickname != null ? MiniMessageHelper.deserialize(nickname) : null;
   }

   @Nullable
   @Override
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
   @Override
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

   @Override
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

   @Override
   public void shouldChangeUniqueId(boolean shouldChange) {
      this.config.set("should_spoof_uuid", shouldChange);
   }

   @Override
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

   @Override
   public void setNickname(Player player, String name) {
      this.setNickname(player, MiniMessageHelper.deserialize(MiniMessageHelper.convertAmpersandToMiniMessage(name)));
   }

   @Override
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
      try {
         Class<?> tabClass = Class.forName("me.neznamy.tab.api.TabAPI");
         Method getInstanceMethod = tabClass.getMethod("getInstance");
         TabAPI tabApiInstance = (TabAPI)getInstanceMethod.invoke(null);
         TabPlayer tabPlayer = tabApiInstance.getPlayer(player.getUniqueId());
         if (tabPlayer == null) {
            throw new Exception("fucking tab player cant be found");
         }

         this.updateTabPlayer(tabPlayer);
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   @Override
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

   @Override
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

         Task.run(this, () -> {
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
            Task.runAsync(this, () -> {
               if (this.config().getBoolean("should_spoof_uuid")) {
                  try {
                     if (MojangProfile.getMojangProfile(playerName) == null) {
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

   @Override
   public void randomNick(Player player) {
      this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
         String generatedUsername = null;
         if (this.config().getBoolean("allow_cloud_nicking")) {
            try {
               generatedUsername = this.cloudNickService.getRandomNick();
               if (generatedUsername != null) {
                  this.getLogger().info("[Cloud Nick] Received nick from cloud: " + generatedUsername);
               }
            } catch (Exception var5) {
               this.getLogger().warning("[Cloud Nick] Cloud service unavailable, falling back to legacy: " + var5.getMessage());
            }
         }

         if (generatedUsername == null) {
            generatedUsername = this.findAvailableUsername();
         }

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

   @Override
   public void randomNick(Player player, String groupName) {
      this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
         String generatedUsername = null;
         if (this.config().getBoolean("allow_cloud_nicking")) {
            try {
               generatedUsername = this.cloudNickService.getRandomNick();
            } catch (Exception var6) {
               this.getLogger().warning("[Cloud Nick] Cloud service unavailable, falling back to legacy: " + var6.getMessage());
            }
         }

         if (generatedUsername == null) {
            generatedUsername = this.findAvailableUsername();
         }

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

   @Override
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

            Task.run(
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
                  Task.runAsync(this, () -> {
                     if (this.config().getBoolean("should_spoof_uuid")) {
                        try {
                           if (MojangProfile.getMojangProfile(playerName) == null) {
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

   @Override
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

   @Override
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

   @Override
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

         Task.run(this, () -> {
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
            Task.runAsync(this, () -> {
               if (this.config().getBoolean("should_spoof_uuid")) {
                  try {
                     if (MojangProfile.getMojangProfile(nickName) == null) {
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
               if (MojangProfile.getMojangProfile(username) == null) {
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

   @Override
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

   @Override
   public boolean setSkinFromMineskinId(Player player, String id) {
      String[] textureAndSignature = fetchTextureAndSignatureFromMineskinId(id);
      return textureAndSignature == null ? false : this.setSkinFromTextureAndSignature(player, textureAndSignature[0], textureAndSignature[1]);
   }

   @Override
   public boolean setSkinFromMineskinUrl(Player player, String url) {
      String skinId = url.replaceAll(".*/", "");
      return this.setSkinFromMineskinId(player, skinId);
   }

   @Nullable
   @Override
   public Skin getSkinFromMineskinId(String id) {
      String[] textureAndSignature = fetchTextureAndSignatureFromMineskinId(id);
      return textureAndSignature == null ? null : new Skin(textureAndSignature[0], textureAndSignature[1]);
   }

   @Nullable
   @Override
   public Skin getSkinFromMineskinUrl(String url) {
      String skinId = url.replaceAll(".*/", "");
      return this.getSkinFromMineskinId(skinId);
   }

   @Override
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
         player.playerListName(MiniMessageHelper.deserialize(originalName));
         player.displayName(MiniMessageHelper.deserialize(originalName));
         this.executeStorageOperation(this.storageManager.getStorage().savePlayer(nickPlayer), "reset nickname for " + player.getName(), () -> {
            if (this.getServer().getPluginManager().isPluginEnabled("TAB")) {
               this.attemptToUpdateTabPlayer(player);
            }
         });
      }
   }

   @Override
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

   @Override
   public void resetNick(Player player) {
      NickPlayer cached = this.playerCache.get(player.getUniqueId());
      if (cached == null) {
         this.storageManager.getStorage().loadPlayer(player.getUniqueId()).thenAccept(nameTagPlayer -> {
            if (nameTagPlayer != null) {
               this.ensureOriginalData(player, nameTagPlayer, () -> Task.run(this, () -> this.resetNickSync(player, nameTagPlayer)));
            }
         });
      } else {
         this.ensureOriginalData(player, cached, () -> Task.run(this, () -> this.resetNickSync(player, cached)));
      }
   }

   private void ensureOriginalData(Player player, NickPlayer nickPlayer, Runnable callback) {
      if (nickPlayer.getOriginalName() != null && nickPlayer.getOriginalTexture() != null) {
         callback.run();
      } else {
         Task.runAsync(this, () -> {
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
         this.getLogger().info("Resetting nick for " + player.getName() + " back to " + originalName);
         this.paperSkinManager.setProfileName(player, originalName);
         if (originalTexture != null) {
            this.getLogger().info("Restoring original skin...");
            this.paperSkinManager.setSkin(player, originalTexture, originalSignature);
         } else {
            this.getLogger().warning("No original texture to restore for " + originalName);
         }

         this.paperSkinManager.refreshPlayer(player);
         player.playerListName(MiniMessageHelper.deserialize(originalName));
         player.displayName(MiniMessageHelper.deserialize(originalName));
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

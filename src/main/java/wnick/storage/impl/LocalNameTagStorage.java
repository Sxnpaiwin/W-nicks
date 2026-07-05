package wnick.storage.impl;

import wnick.NameTagPlugin;
import wnick.storage.NameTagStorage;
import wnick.NickPlayer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class LocalNameTagStorage implements NameTagStorage {
   private final NameTagPlugin plugin;
   private final File dataFile;
   private final ConcurrentLinkedQueue<LocalNameTagStorage.SaveOperation> saveQueue = new ConcurrentLinkedQueue<>();
   private final AtomicBoolean isProcessing = new AtomicBoolean(false);

   public LocalNameTagStorage(NameTagPlugin plugin) {
      this.plugin = plugin;
      this.dataFile = new File(plugin.getDataFolder(), "data.yml");
   }

   private void processSaveQueue() {
      if (this.isProcessing.compareAndSet(false, true)) {
         CompletableFuture.runAsync(() -> {
            try {
               while (!this.saveQueue.isEmpty()) {
                  LocalNameTagStorage.SaveOperation operation = this.saveQueue.poll();
                  if (operation != null) {
                     try {
                        this.performSave(operation.player);
                        operation.future.complete(null);
                     } catch (Exception var6) {
                        operation.future.completeExceptionally(var6);
                     }
                  }
               }
            } finally {
               this.isProcessing.set(false);
               if (!this.saveQueue.isEmpty()) {
                  this.processSaveQueue();
               }
            }
         });
      }
   }

   private void performSave(NickPlayer player) {
      if (player != null && player.getUuid() != null) {
         FileConfiguration config = this.loadConfigurationSafely();
         String uuidStr = player.getUuid().toString();
         String nickname = player.getNickname();
         String skinName = player.getSkinName();
         String texture = player.getTexture();
         String signature = player.getSignature();
         config.set(uuidStr + ".name", nickname);
         config.set(uuidStr + ".skin_name", skinName);
         config.set(uuidStr + ".texture", texture);
         config.set(uuidStr + ".signature", signature);
         config.set(uuidStr + ".original_name", player.getOriginalName());
         config.set(uuidStr + ".original_texture", player.getOriginalTexture());
         config.set(uuidStr + ".original_signature", player.getOriginalSignature());
         config.set(uuidStr + ".fake_rank_id", player.getFakeRankId());

         try {
            config.save(this.dataFile);
            this.plugin.getLogger().fine("Successfully saved player data for " + uuidStr);
         } catch (IOException var9) {
            this.plugin.getLogger().severe("Failed to save NameTag player data: " + var9.getMessage());
            throw new RuntimeException(var9);
         }
      } else {
         this.plugin.getLogger().warning("Attempted to save null player or player with null UUID");
      }
   }

   @Override
   public CompletableFuture<Void> initialize() {
      return CompletableFuture.runAsync(() -> {
         if (!this.dataFile.exists()) {
            try {
               this.dataFile.getParentFile().mkdirs();
               this.dataFile.createNewFile();
            } catch (IOException var2) {
               this.plugin.getLogger().severe("Failed to create NameTag data file: " + var2.getMessage());
               throw new RuntimeException(var2);
            }
         } else {
            this.loadConfigurationSafely(true);
         }
      });
   }

   private FileConfiguration loadConfigurationSafely() {
      return this.loadConfigurationSafely(false);
   }

   private FileConfiguration loadConfigurationSafely(boolean validate) {
      try {
         YamlConfiguration config = new YamlConfiguration();
         config.load(this.dataFile);
         if (validate) {
            this.validateConfiguration(config);
         }

         return config;
      } catch (Exception var6) {
         this.plugin.getLogger().warning("Failed to load data.yml, creating backup and starting fresh: " + var6.getMessage());
         if (this.dataFile.exists()) {
            try {
               File backupFile = new File(this.dataFile.getParentFile(), "data.yml.backup." + System.currentTimeMillis());
               Files.copy(this.dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               this.plugin.getLogger().info("Created backup of corrupted data.yml: " + backupFile.getName());
            } catch (IOException var5) {
               this.plugin.getLogger().warning("Failed to create backup of corrupted data.yml: " + var5.getMessage());
            }
         }

         try {
            this.dataFile.delete();
            this.dataFile.createNewFile();
            this.plugin.getLogger().info("Created new data.yml file");
         } catch (IOException var4) {
            this.plugin.getLogger().severe("Failed to create new data.yml file: " + var4.getMessage());
            throw new RuntimeException(var4);
         }

         return new YamlConfiguration();
      }
   }

   private void validateConfiguration(FileConfiguration config) {
      boolean hasInvalidEntries = false;

      for (String key : config.getKeys(false)) {
         try {
            UUID.fromString(key);
         } catch (IllegalArgumentException var7) {
            this.plugin.getLogger().warning("Found invalid UUID key in data.yml: " + key + " - removing it");
            config.set(key, null);
            hasInvalidEntries = true;
            continue;
         }

         if (config.isConfigurationSection(key)) {
            if (!config.contains(key + ".name")
               && !config.contains(key + ".skin_name")
               && !config.contains(key + ".texture")
               && !config.contains(key + ".signature")
               && !config.contains(key + ".original_name")
               && !config.contains(key + ".original_texture")
               && !config.contains(key + ".original_signature")
               && !config.contains(key + ".fake_rank_id")) {
               this.plugin.getLogger().info("Removing completely empty UUID section: " + key);
               config.set(key, null);
               hasInvalidEntries = true;
            }
         } else {
            this.plugin.getLogger().warning("Found invalid entry in data.yml: " + key + " - removing it");
            config.set(key, null);
            hasInvalidEntries = true;
         }
      }

      if (hasInvalidEntries) {
         try {
            config.save(this.dataFile);
            this.plugin.getLogger().info("Cleaned data.yml file by removing invalid entries");
         } catch (IOException var6) {
            this.plugin.getLogger().warning("Failed to save cleaned data.yml: " + var6.getMessage());
         }
      }
   }

   @Override
   public CompletableFuture<Void> savePlayer(NickPlayer player) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      this.saveQueue.offer(new LocalNameTagStorage.SaveOperation(player, future));
      this.processSaveQueue();
      return future;
   }

   @Override
   public CompletableFuture<NickPlayer> loadPlayer(UUID uuid) {
      return CompletableFuture.supplyAsync(() -> {
         FileConfiguration config = this.loadConfigurationSafely();
         String uuidStr = uuid.toString();
         if (!config.contains(uuidStr)) {
            return null;
         } else {
            NickPlayer player = new NickPlayer(uuid);
            player.setNickname(config.getString(uuidStr + ".name"));
            player.setSkinName(config.getString(uuidStr + ".skin_name"));
            player.setTexture(config.getString(uuidStr + ".texture"));
            player.setSignature(config.getString(uuidStr + ".signature"));
            player.setOriginalName(config.getString(uuidStr + ".original_name"));
            player.setOriginalTexture(config.getString(uuidStr + ".original_texture"));
            player.setOriginalSignature(config.getString(uuidStr + ".original_signature"));
            player.setFakeRankId(config.getString(uuidStr + ".fake_rank_id"));
            return player;
         }
      });
   }

   @Override
   public CompletableFuture<Void> deletePlayer(UUID uuid) {
      return CompletableFuture.runAsync(() -> {
         FileConfiguration config = this.loadConfigurationSafely();
         config.set(uuid.toString(), null);

         try {
            config.save(this.dataFile);
         } catch (IOException var4) {
            this.plugin.getLogger().severe("Failed to delete NameTag player data: " + var4.getMessage());
            throw new RuntimeException(var4);
         }
      });
   }

   @Override
   public CompletableFuture<Boolean> hasPlayer(UUID uuid) {
      return CompletableFuture.supplyAsync(
         () -> {
            FileConfiguration config = this.loadConfigurationSafely();
            String uuidStr = uuid.toString();
            return config.contains(uuidStr + ".name")
               || config.contains(uuidStr + ".skin_name")
               || config.contains(uuidStr + ".texture")
               || config.contains(uuidStr + ".signature")
               || config.contains(uuidStr + ".original_name")
               || config.contains(uuidStr + ".original_texture")
               || config.contains(uuidStr + ".original_signature");
         }
      );
   }

   @Override
   public CompletableFuture<List<NickPlayer>> getAllPlayers() {
      return CompletableFuture.supplyAsync(
         () -> {
            FileConfiguration config = this.loadConfigurationSafely();
            List<NickPlayer> players = new ArrayList<>();

            for (String key : config.getKeys(false)) {
               try {
                  UUID uuid = UUID.fromString(key);
                  if (config.contains(key + ".name")
                     || config.contains(key + ".skin_name")
                     || config.contains(key + ".texture")
                     || config.contains(key + ".signature")
                     || config.contains(key + ".original_name")
                     || config.contains(key + ".original_texture")
                     || config.contains(key + ".original_signature")) {
                     NickPlayer player = new NickPlayer(uuid);
                     player.setNickname(config.getString(key + ".name"));
                     player.setSkinName(config.getString(key + ".skin_name"));
                     player.setTexture(config.getString(key + ".texture"));
                     player.setSignature(config.getString(key + ".signature"));
                     player.setOriginalName(config.getString(key + ".original_name"));
                     player.setOriginalTexture(config.getString(key + ".original_texture"));
                     player.setOriginalSignature(config.getString(key + ".original_signature"));
                     player.setFakeRankId(config.getString(key + ".fake_rank_id"));
                     players.add(player);
                  }
               } catch (IllegalArgumentException var7) {
                  this.plugin.getLogger().warning("Found invalid UUID in data file: " + key);
               }
            }

            return players;
         }
      );
   }

   @Override
   public CompletableFuture<Void> close() {
      return CompletableFuture.runAsync(() -> {
         while (!this.saveQueue.isEmpty() || this.isProcessing.get()) {
            try {
               Thread.sleep(100L);
            } catch (InterruptedException var2) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      });
   }

   private static record SaveOperation(NickPlayer player, CompletableFuture<Void> future) {
   }
}

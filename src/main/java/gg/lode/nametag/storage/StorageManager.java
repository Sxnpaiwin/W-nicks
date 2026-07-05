package gg.lode.nametag.storage;

import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.storage.impl.LocalNameTagStorage;
import gg.lode.nametag.storage.impl.MongoDBNameTagStorage;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.file.FileConfiguration;

public class StorageManager {
   private final NameTagPlugin plugin;
   private NameTagStorage storage;

   public StorageManager(NameTagPlugin plugin) {
      this.plugin = plugin;
   }

   public CompletableFuture<Void> initialize() {
      return CompletableFuture.runAsync(() -> {
         try {
            FileConfiguration config = this.plugin.getConfig();
            String storageType = config.getString("storage.type", "LOCAL").toUpperCase();
            switch (storageType) {
               case "LOCAL":
                  this.storage = new LocalNameTagStorage(this.plugin);
                  this.plugin.getLogger().info("Using LOCAL storage");
                  break;
               case "MONGODB":
                  this.storage = new MongoDBNameTagStorage(this.plugin);
                  this.plugin.getLogger().info("Using MONGODB storage");
                  break;
               default:
                  this.plugin.getLogger().warning("Unknown storage type: " + storageType + ". Falling back to LOCAL");
                  this.storage = new LocalNameTagStorage(this.plugin);
            }

            this.storage.initialize().join();
            this.plugin.getLogger().info("Storage system initialized successfully");
         } catch (Exception var5) {
            this.plugin.getLogger().severe("Failed to initialize storage system: " + var5.getMessage());
            var5.printStackTrace();
            throw new RuntimeException("Storage initialization failed", var5);
         }
      });
   }

   public NameTagStorage getStorage() {
      return this.storage;
   }

   public CompletableFuture<Void> close() {
      return this.storage != null ? this.storage.close() : CompletableFuture.completedFuture(null);
   }
}

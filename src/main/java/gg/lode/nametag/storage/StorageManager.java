package gg.lode.nametag.storage;

import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.storage.impl.LocalNameTagStorage;
import java.lang.reflect.Constructor;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Storage factory. Reads {@code storage.type} from config.yml and instantiates
 * either {@link LocalNameTagStorage} (default, always available) or the
 * MongoDB-backed storage.
 *
 * MongoDB storage is loaded via reflection so that the MongoDB driver
 * (com.mongodb.*, org.bson.*, etc. — ~9 MB) does NOT need to be bundled in
 * the JAR. Users who want MongoDB storage install the MongoDB driver as a
 * separate library on the server's classpath (or use a Paper plugin loader
 * that pulls it in via maven). If the driver is missing, the plugin falls
 * back to LOCAL storage with a clear error in the log.
 */
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
                  this.storage = loadMongoStorage();
                  if (this.storage != null) {
                     this.plugin.getLogger().info("Using MONGODB storage");
                  } else {
                     // loadMongoStorage already logged the error; fall back to LOCAL.
                     this.storage = new LocalNameTagStorage(this.plugin);
                     this.plugin.getLogger().warning("Falling back to LOCAL storage.");
                  }
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

   /**
    * Reflectively load {@code gg.lode.nametag.storage.impl.MongoDBNameTagStorage}.
    *
    * Returns null (with a logged error) if the MongoDB driver is not on the
    * classpath. This lets the main JAR ship without bundling the ~9 MB
    * MongoDB driver + BSON + slf4j.
    */
   private NameTagStorage loadMongoStorage() {
      try {
         // First check the MongoDB driver is actually present.
         Class.forName("com.mongodb.MongoClient", false, this.getClass().getClassLoader());

         // Then load our MongoDB storage class reflectively.
         Class<?> mongoStorageClass = Class.forName(
            "gg.lode.nametag.storage.impl.MongoDBNameTagStorage",
            true,
            this.getClass().getClassLoader()
         );
         Constructor<?> ctor = mongoStorageClass.getConstructor(NameTagPlugin.class);
         return (NameTagStorage) ctor.newInstance(this.plugin);
      } catch (ClassNotFoundException ex) {
         String missing = ex.getMessage();
         this.plugin.getLogger().severe("==============================================");
         this.plugin.getLogger().severe("MongoDB storage selected but the MongoDB driver is not installed.");
         this.plugin.getLogger().severe("Missing class: " + missing);
         this.plugin.getLogger().severe("To use MongoDB storage, install the MongoDB driver as a server library");
         this.plugin.getLogger().severe("(e.g. drop mongodb-driver-sync-*.jar into your server's libs/ folder)");
         this.plugin.getLogger().severe("or change 'storage.type' to 'LOCAL' in plugins/W-Nick/config.yml.");
         this.plugin.getLogger().severe("==============================================");
         return null;
      } catch (Throwable t) {
         this.plugin.getLogger().severe("Failed to load MongoDB storage: " + t.getClass().getSimpleName() + ": " + t.getMessage());
         t.printStackTrace();
         return null;
      }
   }

   public NameTagStorage getStorage() {
      return this.storage;
   }

   public CompletableFuture<Void> close() {
      return this.storage != null ? this.storage.close() : CompletableFuture.completedFuture(null);
   }
}

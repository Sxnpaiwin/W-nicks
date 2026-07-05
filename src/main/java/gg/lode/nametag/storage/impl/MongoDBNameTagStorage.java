package gg.lode.nametag.storage.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import gg.lode.nametag.NameTagPlugin;
import gg.lode.nametag.storage.NameTagStorage;
import gg.lode.nametagapi.api.NickPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;

public class MongoDBNameTagStorage implements NameTagStorage {
   private final NameTagPlugin plugin;
   private MongoClient mongoClient;
   private MongoCollection<Document> collection;

   public MongoDBNameTagStorage(NameTagPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   public CompletableFuture<Void> initialize() {
      return CompletableFuture.runAsync(() -> {
         try {
            FileConfiguration config = this.plugin.getConfig();
            String uri = config.getString("storage.mongodb.uri");
            String database = config.getString("storage.mongodb.database");
            String collectionName = config.getString("storage.mongodb.collection");
            if (uri != null && database != null && collectionName != null) {
               this.mongoClient = MongoClients.create(uri);
               MongoDatabase mongoDatabase = this.mongoClient.getDatabase(database);
               this.collection = mongoDatabase.getCollection(collectionName);
               this.collection.createIndex(new Document("uuid", 1));
               this.plugin.getLogger().info("MongoDB storage initialized successfully");
            } else {
               throw new RuntimeException("MongoDB configuration is incomplete. Please check your config.yml");
            }
         } catch (Exception var6) {
            this.plugin.getLogger().severe("Failed to initialize MongoDB storage: " + var6.getMessage());
            throw new RuntimeException(var6);
         }
      });
   }

   @Override
   public CompletableFuture<Void> savePlayer(NickPlayer player) {
      return CompletableFuture.runAsync(
         () -> {
            try {
               Document doc = new Document()
                  .append("uuid", player.getUuid().toString())
                  .append("name", player.getNickname())
                  .append("skin_name", player.getSkinName())
                  .append("texture", player.getTexture())
                  .append("signature", player.getSignature())
                  .append("original_name", player.getOriginalName())
                  .append("original_texture", player.getOriginalTexture())
                  .append("original_signature", player.getOriginalSignature())
                  .append("fake_rank_id", player.getFakeRankId());
               this.collection.replaceOne(Filters.eq("uuid", player.getUuid().toString()), doc, new ReplaceOptions().upsert(true));
            } catch (Exception var3) {
               this.plugin.getLogger().severe("Failed to save NameTag player: " + var3.getMessage());
               throw new RuntimeException(var3);
            }
         }
      );
   }

   @Override
   public CompletableFuture<NickPlayer> loadPlayer(UUID uuid) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            Document doc = this.collection.find(Filters.eq("uuid", uuid.toString())).first();
            if (doc == null) {
               return null;
            } else {
               NickPlayer player = new NickPlayer(uuid);
               player.setNickname(doc.getString("name"));
               player.setSkinName(doc.getString("skin_name"));
               player.setTexture(doc.getString("texture"));
               player.setSignature(doc.getString("signature"));
               player.setOriginalName(doc.getString("original_name"));
               player.setOriginalTexture(doc.getString("original_texture"));
               player.setOriginalSignature(doc.getString("original_signature"));
               player.setFakeRankId(doc.getString("fake_rank_id"));
               return player;
            }
         } catch (Exception var4) {
            this.plugin.getLogger().severe("Failed to load NameTag player: " + var4.getMessage());
            throw new RuntimeException(var4);
         }
      });
   }

   @Override
   public CompletableFuture<Void> deletePlayer(UUID uuid) {
      return CompletableFuture.runAsync(() -> {
         try {
            this.collection.deleteOne(Filters.eq("uuid", uuid.toString()));
         } catch (Exception var3) {
            this.plugin.getLogger().severe("Failed to delete NameTag player: " + var3.getMessage());
            throw new RuntimeException(var3);
         }
      });
   }

   @Override
   public CompletableFuture<Boolean> hasPlayer(UUID uuid) {
      return CompletableFuture.supplyAsync(
         () -> {
            try {
               Document doc = this.collection.find(Filters.eq("uuid", uuid.toString())).first();
               return doc == null
                  ? false
                  : doc.get("name") != null
                     || doc.get("skin_name") != null
                     || doc.get("texture") != null
                     || doc.get("signature") != null
                     || doc.get("original_name") != null
                     || doc.get("original_texture") != null
                     || doc.get("original_signature") != null;
            } catch (Exception var3) {
               this.plugin.getLogger().severe("Failed to check if NameTag player exists: " + var3.getMessage());
               throw new RuntimeException(var3);
            }
         }
      );
   }

   @Override
   public CompletableFuture<List<NickPlayer>> getAllPlayers() {
      return CompletableFuture.supplyAsync(
         () -> {
            try {
               List<NickPlayer> players = new ArrayList<>();

               for (Document doc : this.collection.find()) {
                  try {
                     String uuidStr = doc.getString("uuid");
                     if (uuidStr != null) {
                        UUID uuid = UUID.fromString(uuidStr);
                        if (doc.get("name") != null
                           || doc.get("skin_name") != null
                           || doc.get("texture") != null
                           || doc.get("signature") != null
                           || doc.get("original_name") != null
                           || doc.get("original_texture") != null
                           || doc.get("original_signature") != null) {
                           NickPlayer player = new NickPlayer(uuid);
                           player.setNickname(doc.getString("name"));
                           player.setSkinName(doc.getString("skin_name"));
                           player.setTexture(doc.getString("texture"));
                           player.setSignature(doc.getString("signature"));
                           player.setOriginalName(doc.getString("original_name"));
                           player.setOriginalTexture(doc.getString("original_texture"));
                           player.setOriginalSignature(doc.getString("original_signature"));
                           player.setFakeRankId(doc.getString("fake_rank_id"));
                           players.add(player);
                        }
                     }
                  } catch (IllegalArgumentException var7) {
                     this.plugin.getLogger().warning("Found invalid UUID in MongoDB: " + doc.getString("uuid"));
                  }
               }

               return players;
            } catch (Exception var8) {
               this.plugin.getLogger().severe("Failed to get all NameTag players: " + var8.getMessage());
               throw new RuntimeException(var8);
            }
         }
      );
   }

   @Override
   public CompletableFuture<Void> close() {
      if (this.mongoClient != null) {
         this.mongoClient.close();
      }

      return CompletableFuture.completedFuture(null);
   }
}

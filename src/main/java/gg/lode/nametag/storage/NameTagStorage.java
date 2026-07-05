package gg.lode.nametag.storage;

import gg.lode.nametagapi.api.NickPlayer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NameTagStorage {
   CompletableFuture<Void> savePlayer(NickPlayer var1);

   CompletableFuture<NickPlayer> loadPlayer(UUID var1);

   CompletableFuture<Void> deletePlayer(UUID var1);

   CompletableFuture<Boolean> hasPlayer(UUID var1);

   CompletableFuture<List<NickPlayer>> getAllPlayers();

   CompletableFuture<Void> initialize();

   CompletableFuture<Void> close();
}

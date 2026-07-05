package wnick;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class NickPlayer {
   private final UUID uuid;
   private final UUID originalUniqueId;
   private String nickname;
   private String skinName;
   private String texture;
   private String signature;
   private String originalName;
   private String originalTexture;
   private String originalSignature;
   private String nickedName;
   private String nickedTexture;
   private String nickedSignature;
   private UUID nickedUniqueId;
   private String fakeRankId;

   public NickPlayer(UUID uuid) {
      this.uuid = uuid;
      this.originalUniqueId = uuid;
      this.nickedUniqueId = uuid;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   @Nullable
   public String getNickname() {
      return this.nickname;
   }

   public void setNickname(@Nullable String nickname) {
      this.nickname = nickname;
      this.nickedName = nickname;
   }

   @Nullable
   public String getSkinName() {
      return this.skinName;
   }

   public void setSkinName(@Nullable String skinName) {
      this.skinName = skinName;
   }

   @Nullable
   public String getTexture() {
      return this.texture;
   }

   public void setTexture(@Nullable String texture) {
      this.texture = texture;
      this.nickedTexture = texture;
   }

   @Nullable
   public String getSignature() {
      return this.signature;
   }

   public void setSignature(@Nullable String signature) {
      this.signature = signature;
      this.nickedSignature = signature;
   }

   @Nullable
   public Skin getSkin() {
      return this.texture != null && this.signature != null ? new Skin(this.texture, this.signature) : null;
   }

   public void setSkin(@Nullable Skin skin) {
      if (skin != null) {
         this.texture = skin.texture();
         this.signature = skin.signature();
         this.nickedTexture = skin.texture();
         this.nickedSignature = skin.signature();
      } else {
         this.texture = null;
         this.signature = null;
         this.nickedTexture = null;
         this.nickedSignature = null;
      }
   }

   public boolean hasNick() {
      return this.nickname != null || this.skinName != null || this.texture != null || this.signature != null;
   }

   public void reset() {
      this.nickname = null;
      this.skinName = null;
      this.texture = null;
      this.signature = null;
      this.nickedName = null;
      this.nickedTexture = null;
      this.nickedSignature = null;
      this.nickedUniqueId = this.originalUniqueId;
      this.fakeRankId = null;
   }

   public String getOriginalName() {
      return this.originalName;
   }

   public void setOriginalName(String originalName) {
      this.originalName = originalName;
      if (this.nickedName == null) {
         this.nickedName = originalName;
      }
   }

   public String getOriginalTexture() {
      return this.originalTexture;
   }

   public void setOriginalTexture(String originalTexture) {
      this.originalTexture = originalTexture;
      if (this.nickedTexture == null) {
         this.nickedTexture = originalTexture;
      }
   }

   public String getOriginalSignature() {
      return this.originalSignature;
   }

   public void setOriginalSignature(String originalSignature) {
      this.originalSignature = originalSignature;
      if (this.nickedSignature == null) {
         this.nickedSignature = originalSignature;
      }
   }

   public UUID getOriginalUniqueId() {
      return this.originalUniqueId;
   }

   public UUID getNickedUniqueId() {
      return this.nickedUniqueId;
   }

   public void setNickedUniqueId(UUID nickedUniqueId) {
      this.nickedUniqueId = nickedUniqueId;
   }

   public String getNickedName() {
      return this.nickedName;
   }

   public void setNickedName(String nickedName) {
      this.nickedName = nickedName;
      this.nickname = nickedName;
   }

   public String getNickedTexture() {
      return this.nickedTexture;
   }

   public String getNickedSignature() {
      return this.nickedSignature;
   }

   public boolean isCurrentlyNicked() {
      return this.hasNick();
   }

   @Nullable
   public String getFakeRankId() {
      return this.fakeRankId;
   }

   public void setFakeRankId(@Nullable String fakeRankId) {
      this.fakeRankId = fakeRankId;
   }
}

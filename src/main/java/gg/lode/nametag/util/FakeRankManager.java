package gg.lode.nametag.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.query.QueryOptions;
import org.jetbrains.annotations.Nullable;

public class FakeRankManager {
   private static final String ASSIGNABLE_PERMISSION = "lodestone.nametag.randomly_assignable";
   private final LuckPerms luckPerms;

   public FakeRankManager(LuckPerms luckPerms) {
      this.luckPerms = luckPerms;
   }

   /**
    * Returns the list of LuckPerms groups flagged as randomly assignable.
    * A group is assignable when it has the permission
    * {@code lodestone.nametag.randomly_assignable} set to true.
    */
   public List<FakeRankManager.FakeRank> getAssignableRanks() {
      return this.luckPerms
         .getGroupManager()
         .getLoadedGroups()
         .stream()
         .filter(g -> g.getNodes(NodeType.PERMISSION).stream().anyMatch(n -> n.getKey().equals(ASSIGNABLE_PERMISSION) && n.getValue()))
         .map(this::toFakeRank)
         .toList();
   }

   /**
    * Returns ALL loaded LuckPerms groups as FakeRank entries.
    * This is used by the {@code /nickrank list} command so the user can
    * pick any group as a fake rank, not only the randomly-assignable ones.
    */
   public List<FakeRankManager.FakeRank> getAllRanks() {
      return this.luckpermsGroups();
   }

   /**
    * Returns only groups that contain a non-empty prefix OR a non-empty suffix.
    * Useful for nicer tab-completion in {@code /nickrank set}.
    */
   public List<FakeRankManager.FakeRank> getRanksWithFormatting() {
      List<FakeRankManager.FakeRank> all = this.luckpermsGroups();
      List<FakeRankManager.FakeRank> filtered = new ArrayList<>();
      for (FakeRankManager.FakeRank r : all) {
         if (!r.prefix().isEmpty() || !r.suffix().isEmpty()) {
            filtered.add(r);
         }
      }
      return filtered;
   }

   private List<FakeRankManager.FakeRank> luckpermsGroups() {
      return this.luckPerms
         .getGroupManager()
         .getLoadedGroups()
         .stream()
         .map(this::toFakeRank)
         .toList();
   }

   @Nullable
   public FakeRankManager.FakeRank getRank(String groupName) {
      if (groupName == null || groupName.isBlank()) {
         return null;
      }
      Group group = this.luckPerms.getGroupManager().getGroup(groupName);
      return group != null ? this.toFakeRank(group) : null;
   }

   /**
    * Case-insensitive lookup, returns the actual group id (as registered in LuckPerms)
    * when a match is found. Useful when the user types a rank name with the wrong case.
    */
   @Nullable
   public String resolveRankIdCaseInsensitive(String input) {
      if (input == null || input.isBlank()) {
         return null;
      }
      // Try exact match first
      if (this.luckPerms.getGroupManager().getGroup(input) != null) {
         return input;
      }
      // Fall back to case-insensitive search
      for (Group g : this.luckPerms.getGroupManager().getLoadedGroups()) {
         if (g.getName().equalsIgnoreCase(input)) {
            return g.getName();
         }
      }
      return null;
   }

   @Nullable
   public FakeRankManager.FakeRank getRandomRank() {
      List<FakeRankManager.FakeRank> assignable = this.getAssignableRanks();
      return assignable.isEmpty() ? null : assignable.get(ThreadLocalRandom.current().nextInt(assignable.size()));
   }

   private FakeRankManager.FakeRank toFakeRank(Group group) {
      CachedMetaData meta = group.getCachedData().getMetaData(QueryOptions.nonContextual());
      String prefix = meta.getPrefix() != null ? meta.getPrefix() : "";
      String suffix = meta.getSuffix() != null ? meta.getSuffix() : "";
      return new FakeRankManager.FakeRank(group.getName(), prefix, suffix);
   }

   public static record FakeRank(String id, String prefix, String suffix) {
   }
}

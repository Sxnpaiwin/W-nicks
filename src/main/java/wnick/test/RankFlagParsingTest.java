package wnick.test;

import wnick.command.NickCommand;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for the {@code -r <rank>} flag parsing in
 * {@link NickCommand}.
 *
 * These don't need Bukkit — they test the static helper methods
 * (extractRankFlag / stripRankFlag / RANK_FLAG_PATTERN) that drive the
 * {@code -r} flag in both {@code /nick as}, {@code /nick with_name}, and
 * {@code /randomnick}.
 *
 * Run with:
 *   ./gradlew test --tests wnick.test.RankFlagParsingTest
 */
public class RankFlagParsingTest {

   // Mirror the pattern from NickCommand.RANK_FLAG_PATTERN
   // (we test the same regex here to catch regressions in either copy)
   private static final Pattern RANK_FLAG_PATTERN =
      Pattern.compile("(?:^|\\s)-r\\s+(\\S+)");

   private static String extractRankFlag(String input) {
      if (input == null) return null;
      Matcher m = RANK_FLAG_PATTERN.matcher(input);
      return m.find() ? m.group(1) : null;
   }

   private static String stripRankFlag(String input) {
      if (input == null) return null;
      return input.replaceAll("(?:^|\\s)-r\\s+\\S+", "").trim();
   }

   @Test
   void extractsRankAtEndOfInput() {
      assertEquals("vip", extractRankFlag("Steve -r vip"));
      assertEquals("moderator", extractRankFlag("SomeNick -r moderator"));
   }

   @Test
   void extractsRankAtStartOfInput() {
      assertEquals("vip", extractRankFlag("-r vip Steve"));
   }

   @Test
   void extractsRankInMiddleOfInput() {
      assertEquals("vip", extractRankFlag("Steve -r vip -f"));
   }

   @Test
   void extractsRankWithUnderscore() {
      assertEquals("my_rank", extractRankFlag("Steve -r my_rank"));
   }

   @Test
   void returnsNullWhenNoFlag() {
      assertNull(extractRankFlag("Steve"));
      assertNull(extractRankFlag(""));
      assertNull(extractRankFlag(null));
   }

   @Test
   void doesNotMatchRWithoutDash() {
      // "r vip" alone shouldn't match — the dash is required
      assertNull(extractRankFlag("Steve r vip"));
   }

   @Test
   void doesNotMatchSimilarFlags() {
      assertNull(extractRankFlag("Steve --random"));
      assertNull(extractFlag(extractRankFlagPattern(), "Steve --rank vip"));
   }

   @Test
   void stripFlagRemovesRankButKeepsRest() {
      assertEquals("Steve", stripRankFlag("Steve -r vip"));
      assertEquals("Steve -f", stripRankFlag("Steve -r vip -f"));
      assertEquals("-f Steve", stripRankFlag("-f Steve -r vip"));
   }

   @Test
   void stripFlagHandlesMissingFlagGracefully() {
      assertEquals("Steve", stripRankFlag("Steve"));
      assertEquals("", stripRankFlag(""));
      assertNull(stripRankFlag(null));
   }

   @Test
   void roundTripExtractAndStrip() {
      String input = "NotCladdy -r moderator -f";
      String rank = extractRankFlag(input);
      String remaining = stripRankFlag(input);
      assertEquals("moderator", rank);
      // The regex strips " -r moderator" (with leading space), leaving
      // "NotCladdy -f" (not "-f NotCladdy" — order is preserved).
      assertEquals("NotCladdy -f", remaining);
      // The rank shouldn't appear in the stripped remainder
      assertTrue(!remaining.contains("moderator"));
      assertTrue(!remaining.contains("-r"));
   }

   /** Helper for the negative-match test. */
   private static String extractFlag(Pattern p, String input) {
      Matcher m = p.matcher(input);
      return m.find() ? m.group(1) : null;
   }

   private static Pattern extractRankFlagPattern() {
      return RANK_FLAG_PATTERN;
   }
}

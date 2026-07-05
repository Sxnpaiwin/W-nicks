package wnick.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Command-line flag parsing utility — replaces
 * wnick.util.Flags's static methods.
 *
 * Supports two kinds of flags:
 *   - Single-character flags: -f, -r (parsed as -X)
 *   - Word flags: --skip, --force (parsed as --word)
 *
 * The original FlagArgument was also a CommandAPI Argument type; we've
 * replaced that usage with GreedyStringArgument + these static helpers.
 */
public final class Flags {

   private Flags() {}

   /** Check if the input contains a single-char flag like -f. */
   public static boolean hasFlags(String input, String flag) {
      if (input == null || flag == null || flag.isEmpty()) return false;
      Pattern p = Pattern.compile("(?:^|\\s)-" + Pattern.quote(flag) + "(?:\\s|$)");
      return p.matcher(input).find();
   }

   /** Check if the input contains a word flag like --skip. */
   public static boolean hasWordFlag(String input, String flag) {
      if (input == null || flag == null || flag.isEmpty()) return false;
      Pattern p = Pattern.compile("(?:^|\\s)--" + Pattern.quote(flag) + "(?:\\s|$)");
      return p.matcher(input).find();
   }

   /**
    * Remove single-char flags from the input.
    * @param input the raw input string
    * @param charFlags set of single-char flags to strip (e.g. Set.of('f'))
    */
   public static String sanitizeInput(String input, Set<Character> charFlags) {
      return sanitizeInput(input, charFlags, Set.of());
   }

   /**
    * Remove both single-char and word flags from the input.
    * @param input the raw input string
    * @param charFlags set of single-char flags to strip (e.g. Set.of('f'))
    * @param wordFlags set of word flags to strip (e.g. Set.of("skip"))
    */
   public static String sanitizeInput(String input, Set<Character> charFlags, Set<String> wordFlags) {
      if (input == null) return null;
      String result = input;
      // Strip word flags first (--skip)
      for (String wf : wordFlags) {
         result = result.replaceAll("(?:^|\\s)--" + Pattern.quote(wf) + "(?:\\s|$)", " ");
      }
      // Strip single-char flags (-f)
      for (char cf : charFlags) {
         result = result.replaceAll("(?:^|\\s)-" + Pattern.quote(String.valueOf(cf)) + "(?:\\s|$)", " ");
      }
      return result.trim().replaceAll("\\s+", " ");
   }
}

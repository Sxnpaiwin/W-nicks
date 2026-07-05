package wnick.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage utility — replaces wnick.util.MiniMsg.
 *
 * Provides three static methods used throughout W-Nick:
 *   - deserialize(String)  → Component
 *   - serialize(Component) → String
 *   - convertAmpersandToMiniMessage(String) → String
 */
public final class MiniMsg {
   private static final MiniMessage MINI = MiniMessage.miniMessage();
   private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

   public static Component deserialize(String str) {
      return MINI.deserialize(str);
   }

   public static Component deserialize(Object str) {
      return MINI.deserialize(String.valueOf(str));
   }

   public static String serialize(Component component) {
      String str = PLAIN.serialize(component);
      return str.replace("\\<", "<");
   }

   /**
    * Convert legacy ampersand color codes (&c, &l, etc.) to MiniMessage
    * tags (<red>, <bold>, etc.).
    */
   public static String convertAmpersandToMiniMessage(String input) {
      if (input == null) return null;
      return input
         .replace("&0", "<black>")
         .replace("&1", "<dark_blue>")
         .replace("&2", "<dark_green>")
         .replace("&3", "<dark_aqua>")
         .replace("&4", "<dark_red>")
         .replace("&5", "<dark_purple>")
         .replace("&6", "<gold>")
         .replace("&7", "<gray>")
         .replace("&8", "<dark_gray>")
         .replace("&9", "<blue>")
         .replace("&a", "<green>")
         .replace("&b", "<aqua>")
         .replace("&c", "<red>")
         .replace("&d", "<light_purple>")
         .replace("&e", "<yellow>")
         .replace("&f", "<white>")
         .replace("&k", "<obfuscated>")
         .replace("&l", "<bold>")
         .replace("&m", "<strikethrough>")
         .replace("&n", "<underlined>")
         .replace("&o", "<italic>")
         .replace("&r", "<reset>");
   }
}

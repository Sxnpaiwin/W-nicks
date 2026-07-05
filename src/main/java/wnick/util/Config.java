package wnick.util;

import wnick.NameTagPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Thin wrapper around Bukkit's FileConfiguration that mimics the
 * upstream bookshelfapi Config API.
 *
 * Replaces wnick.util.Config.
 */
public final class Config {
   private final NameTagPlugin plugin;

   public Config(NameTagPlugin plugin, String fileName) {
      this.plugin = plugin;
      // Extract default config from the JAR if not present on disk.
      plugin.saveDefaultConfig();
   }

   /** Reload config from disk. */
   public void initialize() {
      plugin.reloadConfig();
   }

   /** Save config to disk. */
   public void save() {
      plugin.saveConfig();
   }

   public String getString(String key) {
      return plugin.getConfig().getString(key);
   }

   public String getString(String key, String def) {
      return plugin.getConfig().getString(key, def);
   }

   public boolean getBoolean(String key) {
      return plugin.getConfig().getBoolean(key);
   }

   public boolean getBoolean(String key, boolean def) {
      return plugin.getConfig().getBoolean(key, def);
   }

   public int getInt(String key) {
      return plugin.getConfig().getInt(key);
   }

   public int getInt(String key, int def) {
      return plugin.getConfig().getInt(key, def);
   }

   public void set(String key, Object value) {
      plugin.getConfig().set(key, value);
   }

   public FileConfiguration raw() {
      return plugin.getConfig();
   }
}

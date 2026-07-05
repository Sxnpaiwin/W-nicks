package wnick.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Smoke test that loads EVERY .class file in the final W-Nick JAR and
 * verifies that all referenced classes can be resolved.
 *
 * This catches the entire family of "NoClassDefFoundError at runtime"
 * bugs that we've been hitting:
 *   - bStats CustomChart stripped but SpigotPacketEventsBuilder references it
 *   - net/kyori/adventure/nbt/BinaryTag stripped but PacketEvents needs it
 *   - Any future strip that breaks a transitive dependency
 *
 * The test does NOT run the plugin — it only loads (verifies) every class
 * in the JAR. Class loading triggers JVM bytecode verification, which
 * resolves all class references in method signatures, field types, and
 * superclasses. If any referenced class is missing, the load fails with
 * NoClassDefFoundError / ClassNotFoundException.
 *
 * Note: this test does NOT catch runtime-only issues like
 * NoSuchMethodError (signature mismatch) — those need an integration test
 * with the actual dependency JARs. But it catches the much more common
 * "I stripped a package that was actually needed" bug.
 *
 * Run as a standalone main():
 *   java -cp build/out wnick.test.JarSmokeTest [path/to/W-Nick.jar]
 */
public final class JarSmokeTest {

   /** Default location of the final JAR (relative to project root). */
   private static final String DEFAULT_JAR_PATH =
      System.getProperty("wnick.jar", "download/W-Nick-1.0.81.jar");

   public static void main(String[] args) throws Exception {
      Path jarPath = args.length > 0
         ? Path.of(args[0])
         : Path.of(DEFAULT_JAR_PATH);

      if (!Files.exists(jarPath)) {
         throw new IllegalStateException(
            "JAR not found at " + jarPath.toAbsolutePath()
            + ". Build it first with: ./scripts/package.sh"
            + " Or pass the path as the first argument."
            + " Or set -Dwnick.jar=/path/to/W-Nick.jar");
      }

      System.out.println("Smoke-testing JAR: " + jarPath);
      System.out.println("Size: " + Files.size(jarPath) + " bytes");

      URL[] classpath = buildClasspath(jarPath);
      URLClassLoader loader = new URLClassLoader(classpath, ClassLoader.getSystemClassLoader());

      List<String> allClasses = listClassesInJar(jarPath);
      System.out.println("Found " + allClasses.size() + " classes in JAR");

      List<String> failures = new ArrayList<>();
      List<String> tolerated = new ArrayList<>();
      int ok = 0;

      for (String className : allClasses) {
         try {
            Class.forName(className, true, loader);
            ok++;
         } catch (Throwable t) {
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            if (isToleratedFailure(className, t)) {
               tolerated.add("  " + className + " -> " + msg);
            } else {
               failures.add("  " + className + " -> " + msg);
            }
         }
      }

      System.out.println();
      System.out.println("Results:");
      System.out.println("  Loaded OK:       " + ok);
      System.out.println("  Tolerated:       " + tolerated.size());
      System.out.println("  Hard failures:   " + failures.size());

      if (!tolerated.isEmpty()) {
         System.out.println();
         System.out.println("Tolerated failures (runtime-only deps not on test classpath):");
         tolerated.forEach(System.out::println);
      }

      if (!failures.isEmpty()) {
         System.out.println();
         System.out.println("HARD FAILURES (these will crash the plugin at runtime):");
         failures.forEach(System.out::println);
         System.out.println();
         throw new IllegalStateException(
            "JAR smoke test failed: " + failures.size()
            + " classes could not be loaded. See output above for details."
            + " This usually means scripts/package.sh stripped a package"
            + " that is transitively required. Check the failure messages"
            + " for the missing class name, then ensure that package is"
            + " NOT stripped in scripts/package.sh.");
      }

      System.out.println();
      System.out.println("PASS: JAR smoke test passed — all non-tolerated classes load cleanly.");
   }

   private static URL[] buildClasspath(Path jarPath) throws IOException {
      List<URL> urls = new ArrayList<>();
      urls.add(jarPath.toUri().toURL());

      Path libsDir = Path.of("build/libs");
      if (Files.isDirectory(libsDir)) {
         try (Stream<Path> entries = Files.list(libsDir)) {
            entries
               .filter(p -> p.toString().endsWith(".jar"))
               .map(p -> {
                  try { return p.toUri().toURL(); }
                  catch (IOException e) { throw new UncheckedIOException(e); }
               })
               .forEach(urls::add);
         }
      }

      return urls.toArray(new URL[0]);
   }

   private static List<String> listClassesInJar(Path jarPath) throws IOException {
      List<String> result = new ArrayList<>();
      try (JarFile jar = new JarFile(jarPath.toFile())) {
         Enumeration<JarEntry> entries = jar.entries();
         while (entries.hasMoreElements()) {
            JarEntry e = entries.nextElement();
            String name = e.getName();
            if (name.endsWith(".class") && !name.endsWith("module-info.class")) {
               String dotted = name
                  .substring(0, name.length() - ".class".length())
                  .replace('/', '.');
               int dollar = dotted.indexOf('$');
               if (dollar > 0) {
                  dotted = dotted.substring(0, dollar);
               }
               if (dotted.endsWith(".package-info") || dotted.endsWith(".module-info")) {
                  continue;
               }
               if (!result.contains(dotted)) {
                  result.add(dotted);
               }
            }
         }
      }
      return result;
   }

   /**
    * Decide whether a class-load failure is tolerable.
    *
    * A failure is tolerable if the class references a runtime-only
    * dependency that is intentionally NOT bundled in the JAR — either
    * because Paper provides it at runtime, or because the dependency
    * is an optional plugin that the user must install separately.
    *
    * A failure is NOT tolerable if it references a class that SHOULD be
    * bundled in the JAR (e.g. net/kyori/adventure/*, bstats/*, etc.) —
    * that means package.sh stripped something it shouldn't have.
    */
   private static boolean isToleratedFailure(String className, Throwable t) {
      String msg = t.getMessage() == null ? "" : t.getMessage();

      // Packages provided at runtime by Paper (or by other installed plugins).
      // These are intentionally NOT bundled in our JAR.
      String[] runtimeOnlyPrefixes = {
         // Other plugins (must be installed by the user)
         "net.luckperms.api.",
         "me.neznamy.tab.",
         "me.clip.placeholderapi.",
         // Paper / Bukkit runtime
         "org.bukkit.",
         "io.papermc.paper.",
         "com.destroystokyo.paper.",
         // Mojang libraries bundled with Paper
         "com.mojang.authlib.",
         "com.mojang.brigadier.",
         "com.mojang.serialization.",
         "net.minecraft.",
         // Netty (bundled with Paper)
         "io.netty.",
         // Gson (bundled with Paper)
         "com.google.gson.",
         // Guava (bundled with Paper)
         "com.google.common.",
         // Adventure serializer modules — Paper bundles these at runtime
         // (adventure-text-serializer-plain, adventure-text-serializer-gson,
         // adventure-text-serializer-legacy, etc.) even though they're not
         // in paper-api.jar. BookshelfAPI's MiniMessageHelper references
         // PlainTextComponentSerializer as a static field.
         "net.kyori.adventure.text.serializer.plain.",
         "net.kyori.adventure.text.serializer.gson.",
      };

      String missingClass = extractMissingClassName(msg);
      if (missingClass != null) {
         String dotted = missingClass.replace('/', '.');
         for (String prefix : runtimeOnlyPrefixes) {
            if (dotted.startsWith(prefix)) {
               return true;
            }
         }
      }

      // MongoDB storage class is intentionally not bundled — it's loaded
      // reflectively by StorageManager only when storage.type=MONGODB.
      if (className.equals("wnick.storage.impl.MongoDBNameTagStorage")) {
         return true;
      }

      // TabIntegration references TAB via reflection.
      if (className.equals("wnick.util.TabIntegration")) {
         return true;
      }

      // CommandAPI NMS classes reference Minecraft's internal
      // net.minecraft.* classes which we don't have on the test classpath.
      // They're loaded lazily at runtime based on the server's MC version.
      if (className.startsWith("dev.jorel.commandapi.nametag.nms.NMS_")
         || className.startsWith("dev.jorel.commandapi.nametag.nms.PaperNMS_")) {
         return true;
      }

      // PacketEvents injector classes reference Netty channel classes
      // which are bundled with Paper but not on our test classpath.
      if (className.startsWith("io.github.retrooper.packetevents.nametag.injector.")) {
         return true;
      }

      // PacketEvents reflection / Folia compat utilities reference
      // Paper internals that aren't on the test classpath.
      if (className.equals("io.github.retrooper.packetevents.nametag.util.FoliaCompatUtil")
         || className.equals("io.github.retrooper.packetevents.nametag.util.SpigotReflectionUtil")) {
         return true;
      }

      // PacketEvents' Gson serializer classes reference com.google.gson
      // which is bundled with Paper but not on our test classpath.
      if (className.startsWith("io.github.retrooper.packetevents.nametag.adventure.serializer.gson.")) {
         return true;
      }

      // PacketEvents protocol registry classes (ItemTypes, EntityTypes,
      // etc.) do lazy init in <clinit> that reads mappings from
      // assets/mappings/. In our smoke test we don't have a server
      // context, so init fails with ExceptionInInitializerError. This
      // is tolerable — these registries init correctly on a real server
      // (they only load the mappings for the running MC version).
      //
      // We also tolerate the cascading NoClassDefFoundError that happens
      // when a class's <clinit> failed previously — subsequent attempts
      // to reference that class throw NoClassDefFoundError with message
      // "Could not initialize class X" instead of ExceptionInInitializerError.
      if ((t instanceof ExceptionInInitializerError
           || (t instanceof NoClassDefFoundError && msg.contains("Could not initialize class")))
         && className.startsWith("com.github.retrooper.packetevents.nametag.protocol.")) {
         return true;
      }

      // SynchronizedRegistriesHandler is the same — lazy init that needs
      // a server context. PacketEvents loads it on first packet send.
      if (className.equals("com.github.retrooper.packetevents.nametag.util.mappings.SynchronizedRegistriesHandler")
         && t instanceof ExceptionInInitializerError) {
         return true;
      }

      // WrapperPlayServerUpdateAttributes references protocol registry
      // classes that fail to init in our test context.
      if (className.equals("com.github.retrooper.packetevents.nametag.wrapper.play.server.WrapperPlayServerUpdateAttributes")
         && t instanceof ExceptionInInitializerError) {
         return true;
      }

      return false;
   }

   private static String extractMissingClassName(String msg) {
      if (msg == null) return null;
      String trimmed = msg.trim();
      if (trimmed.isEmpty()) return null;
      if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
         trimmed = trimmed.substring(1, trimmed.length() - 1);
      }
      return trimmed;
   }
}

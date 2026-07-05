# Architecture

This document explains how W-Nick is structured for contributors who want to
understand or extend the codebase.

## Background

W-Nick is a **fork** of [Name-Tag v1.0.81](https://lode.gg/plugin/nametag)
by Apollo30 / Lodestone. The original plugin is closed-source (only the
public API JAR is open), so W-Nick's sources are **decompiled with
[Vineflower](https://github.com/Vineflower/vineflower) 1.10.1** and then
patched. Decompiled code may have minor oddities (variable names like
`var9`, `forcex`/`textNoRankx` etc.) — these are Vineflower's naming for
shadowed variables in switch cases and are left as-is where they don't
cause bugs.

## Package layout

```
gg.lode.nametag                  — main plugin package
  ├── NameTagPlugin              — main class, implements INameTagAPI
  ├── PlaceholderManager         — PlaceholderAPI hook
  ├── command/
  │   ├── NickCommand            — /nick command + listener for chat/death/etc.
  │   ├── RandomNickCommand      — /randomnick (W-Nick: added -r flag)
  │   ├── NickRankCommand        — /nickrank (NEW in W-Nick)
  │   ├── WNickCommand           — /wnick master command (NEW in W-Nick)
  │   ├── NameTagCommand         — /nametag admin command
  │   └── RealNameCommand        — /realname
  ├── listeners/
  │   ├── PlayerInfoPacketListener  — packetevents hook for player info packets
  │   └── AutoNickJoinListener      — re-applies saved nick on join (NEW in W-Nick)
  ├── nms/
  │   └── PaperSkinManager       — Paper profile / skin manipulation
  ├── storage/
  │   ├── NameTagStorage         — storage interface
  │   ├── StorageManager         — storage factory + lifecycle
  │   └── impl/
  │       ├── LocalNameTagStorage   — YAML file storage
  │       └── MongoDBNameTagStorage — MongoDB storage
  └── util/
      ├── FakeRankManager        — LuckPerms group -> FakeRank mapping
      │                           (W-Nick: added getAllRanks, resolveCaseInsensitive)
      ├── CloudNickService       — random username cloud service
      ├── MojangSkinFetcher      — Mojang API client
      ├── SkinProvider           — random skin selection
      └── UsernameGenerator      — legacy random username generator

gg.lode.nametagapi               — public API (already bundled in main JAR)
  ├── INameTagAPI                — API interface
  ├── NameTagAPI                 — static accessor
  └── api/
      ├── NickPlayer             — per-player nick data record
      └── Skin                   — skin texture + signature record
```

## Key flows

### Setting a nickname
1. Player runs `/nick with_name Steve` (or `/nick as Steve`, `/randomnick`).
2. `NickCommand` parses arguments, applies the `-r <rank>` flag if present,
   validates the rank via `FakeRankManager.getRank(...)`.
3. The command calls one of:
   - `NameTagPlugin.setNickname(...)` — just the name
   - `NameTagPlugin.setNickFromPlayer(...)` — name + skin from another player
   - `NameTagPlugin.setNickWithSkin(...)` — internal, used by `/randomnick`
4. The chosen method updates the cached `NickPlayer`, persists it via the
   `NameTagStorage` backend (LOCAL or MONGODB), and (if TAB is installed)
   calls `attemptToUpdateTabPlayer(...)` to push the new name + prefix +
   suffix to TAB's tab-list and nametag managers.
5. `PaperSkinManager` mutates the player's GameProfile (name, skin
   properties, optionally UUID) and calls `refreshPlayer(...)` to
   re-broadcast the change to other players via PacketEvents.

### Spoofing a LuckPerms rank
1. On plugin enable, `NameTagPlugin` checks if LuckPerms is loaded and, if
   so, constructs a `FakeRankManager` with the `LuckPerms` service.
2. The player picks a rank via `/nickrank set <rank>` (or `-r <rank>` on
   `/nick` / `/randomnick`). The rank id is stored on the `NickPlayer` as
   `fakeRankId`.
3. When `updateTabPlayer(...)` is called, it looks up the rank via
   `FakeRankManager.getRank(fakeRankId)`, reads the LuckPerms group's
   cached prefix and suffix, and pushes them to TAB via
   `NameTagManager.setPrefix(...)` and `setSuffix(...)`.
4. The rank is **also** stored separately from the nick, so it survives
   `/nick reset` followed by a new `/nick` — only `/nickrank clear` or
   `/nick reset` will clear it.

### Auto-apply on join
1. `AutoNickJoinListener` listens for `PlayerJoinEvent` (MONITOR priority).
2. If `auto_apply_nick_on_join` is `true`, it asynchronously loads the
   player's `NickPlayer` from storage.
3. If a saved nick exists, it synchronously calls `setNickname(...)` to
   re-apply the player list name + display name + profile name, then
   refreshes TAB if installed.

## Build process

Because the upstream plugin bundles several closed-source libraries
(PacketEvents, CommandAPI, BookshelfAPI, MongoDB driver, bStats, etc.),
W-Nick is built by **repackaging** the original JAR:

1. Decompile the original `Name-Tag-Paper-1.0.81.jar` with Vineflower.
2. Patch the decompiled sources in `src/main/java/`.
3. Compile the patched sources with JDK 21 against the original JAR's
   bundled classes plus Paper-API, LuckPerms-API, PlaceholderAPI, etc.
4. Use `jar uf` to **replace** the patched `.class` files in a copy of the
   original JAR (preserving all bundled libs).
5. Replace `plugin.yml` and `config.yml` with the patched versions.

See `scripts/package.sh` for the exact commands.

## Key files to know

| File | What it does | W-Nick changes |
|------|--------------|----------------|
| `NameTagPlugin.java` | Main plugin class, all nick/skin/rank operations | Added `prefixedMessage()`, registered `AutoNickJoinListener` and `WNickCommand`, fixed `setNickWithSkin` to honor `rankIdOverride`, config v5→v6 migration |
| `FakeRankManager.java` | LuckPerms group → FakeRank mapping | Added `getAllRanks()`, `getRanksWithFormatting()`, `resolveRankIdCaseInsensitive()` |
| `NickCommand.java` | `/nick` command | Fixed `as` branch typos (`rankIdx`/`rankId`, `sanitizedTextx`/`sanitizedText`) |
| `RandomNickCommand.java` | `/randomnick` command | Added `-r <rank>` flag |
| `NickRankCommand.java` | `/nickrank` command | **New file.** |
| `WNickCommand.java` | `/wnick` command | **New file.** |
| `AutoNickJoinListener.java` | Re-apply saved nick on join | **New file.** |
| `plugin.yml` | Plugin metadata | Renamed to `W-Nick`, author `Joehe`, registered new commands + permissions |
| `config.yml` | Default config | Added `message_prefix`, `auto_apply_nick_on_join`, `auto_assign_random_rank_on_random_nick` |

## Extending W-Nick

### Adding a new LuckPerms-aware feature
Use `plugin.getFakeRankManager()` to access the LuckPerms integration. If
it returns `null`, LuckPerms isn't installed — handle that case gracefully.

### Adding a new command
1. Create a class extending `dev.jorel.commandapi.nametag.CommandAPICommand`.
2. In the constructor, define subcommands, arguments, permissions, and
   executors.
3. Register it in `NameTagPlugin.registerCommands()`.
4. Add the permission to `plugin.yml`.

### Adding a new listener
1. Create a class implementing `org.bukkit.event.Listener`.
2. Register it in `NameTagPlugin.onEnable()` via
   `getServer().getPluginManager().registerEvents(...)`.

### Adding a new config option
1. Add the key + default to `src/main/resources/config.yml`.
2. Bump `CONFIG_VERSION` in `NameTagPlugin.java` and add a migration case
   in `updateConfigToLatest()`.
3. Read it via `plugin.config().getString(...)` / `getBoolean(...)` / etc.

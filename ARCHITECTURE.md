# Architecture

This document explains how W-Nick is structured for contributors who want to
understand or extend the codebase.

## Package layout

```
gg.lode.nametag                  — main plugin package
  ├── NameTagPlugin              — main class, implements INameTagAPI
  ├── PlaceholderManager         — PlaceholderAPI hook (identifier: "wnick")
  ├── command/
  │   ├── NickCommand            — /nick command + listener for chat/death/etc.
  │   ├── RandomNickCommand      — /randomnick (added -r flag)
  │   ├── NickRankCommand        — /nickrank
  │   ├── WNickCommand           — /wnick master command
  │   ├── NameTagCommand         — /nametag admin command
  │   └── RealNameCommand        — /realname
  ├── listeners/
  │   ├── PlayerInfoPacketListener  — packetevents hook for player info packets
  │   └── AutoNickJoinListener      — re-applies saved nick on join
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
      ├── MojangSkinFetcher      — Mojang API client (official APIs only)
      ├── SkinProvider           — random skin selection
      └── UsernameGenerator      — local random username generator

gg.lode.nametagapi               — public API (already bundled in main JAR)
  ├── INameTagAPI                — API interface
  ├── NameTagAPI                 — static accessor
  └── api/
      ├── NickPlayer             — per-player nick data record
      └── Skin                   — skin texture + signature record
```

## Privacy audit

W-Nick ships **no telemetry and no phone-home calls**. The following
upstream features were removed in this fork:

| Removed              | What it did                                                                                                    | Replacement                          |
|----------------------|----------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `CloudNickService`   | Resolved the server's public IP via `api.ipify.org`, then uploaded every joining player's UUID + username + the server's IP + port to a third-party endpoint on every join. Also sent the IP + port as HTTP headers when fetching random usernames. | Deleted entirely. Random username generation uses the local `UsernameGenerator`. |
| `Metrics(this, 24781)` | bStats analytics — sent server info (player count, version, etc.) to bstats.org using the upstream plugin's ID. | Removed. No telemetry is sent.      |
| `VersionUpdater`     | On every server start, fetched `https://<third-party>/api/plugins/nametag/version` to check for updates.       | Removed. Use `/wnick version` instead. |
| `allow_cloud_nicking` config | Gated the upstream phone-home.                                                                                  | Removed. The migration nulls out the key. |
| `NickCommand.PlayerJoinEvent` phone-home | Called `cloudNickService.registerPlayer()` on every player join, uploading UUID + username + server IP + port. | Removed. The rest of the listener (loading saved nick from storage) is preserved. |

The only outbound network calls remaining are:

| Endpoint                                | When it's called                                  | Purpose                              |
|-----------------------------------------|---------------------------------------------------|--------------------------------------|
| `api.mojang.com/users/profiles/minecraft/<name>` | `/nick as <name>`, `/nick with_name <name>` (when `can_use_existing_players=false`) | Check if a username is taken by a real Mojang account. |
| `sessionserver.mojang.com/session/minecraft/profile/<uuid>` | First time a player nicks, to capture their original skin | Fetch the player's original skin texture + signature so it can be restored on `/nick reset`. |
| `api.mineskin.org/v2/skins/<id>`        | `/nick from_url <url>` (player-initiated only)    | Fetch a Mineskin skin by ID/URL.     |

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

W-Nick is built by repackaging the original JAR (which bundles several
closed-source libraries like PacketEvents, CommandAPI, BookshelfAPI, MongoDB
driver, etc.):

1. Patch the Java sources in `src/main/java/`.
2. Compile the patched sources with JDK 21 against the original JAR's
   bundled classes plus Paper-API, LuckPerms-API, PlaceholderAPI, etc.
3. Use `jar uf` to **replace** the patched `.class` files in a copy of the
   original JAR (preserving all bundled libs).
4. Replace `plugin.yml` and `config.yml` with the patched versions.

See `scripts/package.sh` for the exact commands.

## Key files to know

| File | What it does |
|------|--------------|
| `NameTagPlugin.java` | Main plugin class, all nick/skin/rank operations. Removed `Metrics`, `VersionUpdater`, `CloudNickService`. Added `prefixedMessage()`, `AutoNickJoinListener`, `WNickCommand`. Fixed `setNickWithSkin` to honor `rankIdOverride`. Config v5→v6 migration. |
| `FakeRankManager.java` | LuckPerms group → FakeRank mapping. Added `getAllRanks()`, `getRanksWithFormatting()`, `resolveRankIdCaseInsensitive()`. Renamed assignable permission to `wnick.rank.assignable`. |
| `NickCommand.java` | `/nick` command. Fixed `as` branch typos. Removed `PlayerJoinEvent` phone-home call. Renamed all permissions. |
| `RandomNickCommand.java` | `/randomnick` command. Added `-r <rank>` flag. Renamed permissions. |
| `NickRankCommand.java` | `/nickrank` command. New file. |
| `WNickCommand.java` | `/wnick` command. New file. |
| `AutoNickJoinListener.java` | Re-apply saved nick on join. New file. |
| `PlaceholderManager.java` | PlaceholderAPI hook. Identifier changed to `wnick`, author to `Joehe`. |
| `plugin.yml` | Plugin metadata. Author `Joehe`, all permissions under `wnick.*`. |
| `config.yml` | Default config. Removed `allow_cloud_nicking`, added `message_prefix` / `auto_apply_nick_on_join` / `auto_assign_random_rank_on_random_nick`. |

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

### Privacy review for new code
Before merging any new feature, verify that:
- It does not make any HTTP requests to third-party servers (only Mojang
  and mineskin.org are acceptable, and only for legitimate skin/username
  lookups).
- It does not upload player data (UUIDs, usernames, IPs) anywhere.
- It does not include any analytics / metrics calls.
- It does not register a `PlayerJoinEvent` listener that exfiltrates data.

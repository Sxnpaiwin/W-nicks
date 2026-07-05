# W-Nick

A LuckPerms-aware nick plugin for Paper / Folia Minecraft servers.

**W-Nick** is a fork of [Name-Tag](https://lode.gg/plugin/nametag) v1.0.81 with
extended LuckPerms integration. It lets players nick themselves with a fake
name, fake skin, AND a fake rank (prefix + suffix pulled from any LuckPerms
group), so they can look like a VIP, Moderator, or any other rank while
nicked.

> **One JAR, no separate API plugin needed.** The `Name-Tag-API` classes are
> already bundled inside `W-Nick-1.0.81.jar` — just drop the single file into
> your `plugins/` folder and you're done.

---

## Features

### Nicking
- `/nick reset` — clear your nick
- `/nick as <player>` — nick as an existing Minecraft player (skin + name)
- `/nick with_name <name>` — set a custom nickname
- `/nick with_skin <player>` — copy another player's skin
- `/nick from_url <url>` — apply a Mineskin skin
- `/randomnick [selector] [-r <rank>] [--skip]` — generate a random nick,
  optionally with a specific LuckPerms rank

### LuckPerms rank spoofing (W-Nick's headline feature)
- `/nickrank list [all|assignable]` — preview every LuckPerms group with
  its prefix + suffix rendered live
- `/nickrank set <rank> [targets]` — set your fake rank (tab-completes all
  group names, case-insensitive)
- `/nickrank clear [targets]` — clear your fake rank
- `/nickrank random [targets]` — pick a random assignable rank
- `/nickrank current [target]` — show your current rank + preview
- Works whether or not you currently have a nick set — the rank will be
  applied automatically the next time you `/nick`

You can also pass `-r <rank>` directly to `/nick` and `/randomnick`:
```
/nick with_name Steve -r vip
/randomnick -r moderator
```

### W-Nick extras
- `/wnick` — friendly help screen showing every command
- `/wnick info [player]` — full debug view (UUID, original name, current
  nick, skin, fake rank, rank preview)
- `/wnick version` / `/wnick reload`
- Configurable message prefix (`message_prefix` in `config.yml`)
- Auto-apply saved nick on join (`auto_apply_nick_on_join` in `config.yml`)
  — players who reconnect after a restart get their nick back automatically
- Config file auto-migrates from the original Name-Tag v5 to W-Nick v6

### Other integrations
- **TAB** (optional) — when installed, W-Nick pushes the spoofed prefix and
  suffix to TAB's tab-list and above-head nametag managers
- **PlaceholderAPI** (optional) — exposes `%nametag_nickname%`,
  `%nametag_has_nick%`, `%nametag_fake_rank%`, `%nametag_original_name%`,
  `%nametag_skin_name%`, etc.
- **Folia** supported

---

## Quick start

1. Install [LuckPerms](https://luckperms.net/) (required for rank spoofing).
2. (Optional) Install [TAB](https://github.com/NEZNAMY/TAB) and
   [PlaceholderAPI](https://github.com/HelpChat/PlaceholderAPI) for full
   feature parity.
3. Download `W-Nick-1.0.81.jar` from the [releases page](../../releases) and
   drop it into your server's `plugins/` folder.
4. Restart your server.
5. In LuckPerms, tag a group as randomly assignable:
   ```
   /lp group vip permission set lodestone.nametag.randomly_assignable true
   ```
6. In-game, run `/nickrank list` to see available ranks, then
   `/nickrank set vip` to pick one, then `/nick random` to apply a random
   nick with that rank.

---

## Permissions

| Permission                                          | Default | Description                              |
|-----------------------------------------------------|---------|------------------------------------------|
| `lodestone.nametag.commands.nick`                   | op      | Access to `/nick`                        |
| `lodestone.nametag.commands.nick.others`            | op      | Nick other players                       |
| `lodestone.nametag.commands.randomnick`             | op      | Access to `/randomnick`                  |
| `lodestone.nametag.commands.randomnick.others`      | op      | Random-nick other players                |
| `lodestone.nametag.commands.realname`               | true    | Access to `/realname`                    |
| `lodestone.nametag.commands.nickrank`               | op      | Access to `/nickrank`                    |
| `lodestone.nametag.commands.nickrank.list`          | op      | List ranks                               |
| `lodestone.nametag.commands.nickrank.set`           | op      | Set a fake rank on yourself              |
| `lodestone.nametag.commands.nickrank.clear`         | op      | Clear your fake rank                     |
| `lodestone.nametag.commands.nickrank.random`        | op      | Pick a random assignable rank            |
| `lodestone.nametag.commands.nickrank.current`       | op      | View your current fake rank              |
| `lodestone.nametag.commands.nickrank.others`        | op      | Act on other players' fake rank          |
| `lodestone.nametag.commands.wnick`                  | op      | Access to `/wnick`                       |
| `lodestone.nametag.commands.wnick.info`             | op      | View your own nick info                  |
| `lodestone.nametag.commands.wnick.info.others`      | op      | View other players' nick info            |
| `lodestone.commands.nametag.reload`                 | op      | `/wnick reload` and `/nametag reload`    |
| `lodestone.commands.nametag.refresh_player`         | op      | `/nametag refresh_player`                |
| `lodestone.commands.nametag.debug`                  | op      | `/nametag debug`                         |

---

## Configuration

`plugins/W-Nick/config.yml`:

```yaml
version: 6
can_use_existing_players: true
should_spoof_uuid: false
allow_cloud_nicking: true

# Prefix prepended to most user-facing plugin messages.
# Supports MiniMessage formatting. Set to "" to disable.
message_prefix: "<gold>[W-Nick]</gold> "

# Re-apply a player's saved nick automatically when they log in.
auto_apply_nick_on_join: true

# When true, /nick random automatically picks a random assignable rank
# from LuckPerms (only if the player doesn't already have one set).
auto_assign_random_rank_on_random_nick: true

storage:
  type: "LOCAL"        # or MONGODB
  mongodb:
    uri: "mongodb://localhost:27017"
    database: "nametag"
    collection: "players"
    pool-size: 10
```

---

## What's different from upstream Name-Tag?

This is a **fork** of [Apollo30's Name-Tag](https://lode.gg/plugin/nametag)
v1.0.81 with the following changes:

### Bug fixes
- `/nick as <name> -r <rank>` was broken in the original build (variable
  typos `rankIdx`/`rankId`, `sanitizedTextx`/`sanitizedText`). Now actually
  applies the rank.
- `setNickWithSkin(...)` ignored its `rankIdOverride` parameter and always
  overwrote the player's rank with a random one. Now honors the override and
  preserves any rank set via `/nickrank set`.

### New features
- `/nickrank` command — dedicated UI for managing the LuckPerms fake rank
- `/wnick` master command — help, info, version, reload
- `/randomnick -r <rank>` flag
- Auto-apply saved nick on join
- Configurable message prefix
- `FakeRankManager.getAllRanks()` and `resolveRankIdCaseInsensitive()`
- New config keys with automatic migration from v5 → v6

### Branding
- Plugin name: `W-Nick` (was `NameTag`)
- Author: `Joehe` (upstream author `Apollo30` still credited)
- Internal version: `v1.0.81-W-Nick`

---

## Building from source

This repository ships the patched Java sources under `src/`. The original
JAR's bundled dependencies (PacketEvents, CommandAPI, BookshelfAPI, MongoDB
driver, bStats, etc.) are NOT open source and are NOT included — to build,
you need the original `Name-Tag-Paper-1.0.81.jar` from upstream, which
serves as both the source of bundled libs and the base for the repackaged
JAR.

Quick build:
```bash
# Requires JDK 21 + the original Name-Tag-Paper JAR
./scripts/package.sh    # produces dist/W-Nick-1.0.81.jar
```

Decompiled with [Vineflower](https://github.com/Vineflower/vineflower) 1.10.1.

---

## License

The original Name-Tag plugin is © Apollo30 / Lodestone. The source code in
this repository (the W-Nick patches, new commands, and integration code) is
released under the MIT License — see [LICENSE](LICENSE). Bundled third-party
libraries retain their original licenses.

---

## Credits

- **Apollo30 / Lodestone** — original Name-Tag plugin
- **Joehe** — W-Nick fork, LuckPerms integration, bug fixes, new commands

# W-Nick

> A lightweight, privacy-respecting nick plugin for Paper 1.21.8+ with LuckPerms rank spoofing and Paper Dialog API integration.

[![GitHub release](https://img.shields.io/github/v/release/Sxnpaiwin/W-nicks?style=flat-square)](https://github.com/Sxnpaiwin/W-nicks/releases)
[![JAR size](https://img.shields.io/badge/JAR-5.3_MB-blue?style=flat-square)](https://github.com/Sxnpaiwin/W-nicks/releases)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)
[![Paper](https://img.shields.io/badge/Paper-1.21.8%2B-blue?style=flat-square)](https://papermc.io)

**Author:** Joehe · **Repo:** [github.com/Sxnpaiwin/W-nicks](https://github.com/Sxnpaiwin/W-nicks)

---

## Overview

W-Nick lets players nick themselves with a fake name, fake skin, AND a fake LuckPerms rank (prefix + suffix). Players can look like a VIP, Moderator, or any other rank while nicked — perfect for streamers, events, or just hiding from the crowd.

**One JAR, no separate API plugin needed.** The API classes are already bundled inside — just drop `W-Nick-1.0.81.jar` into `plugins/` and restart.

---

## Features

### Nicking
| Command | Description |
|---------|-------------|
| `/nick reset` | Clear your nick |
| `/nick as <player>` | Nick as an existing Minecraft player (skin + name) |
| `/nick with_name <name>` | Set a custom nickname |
| `/nick with_skin <player>` | Copy another player's skin |
| `/nick from_url <url>` | Apply a Mineskin skin |
| `/randomnick [selector] [-r <rank>] [--skip]` | Generate a random nick |

### LuckPerms Rank Spoofing
| Command | Description |
|---------|-------------|
| `/nickrank list [all\|assignable]` | Preview every LuckPerms group with prefix + suffix rendered live |
| `/nickrank set <rank> [targets]` | Set your fake rank (tab-completes, case-insensitive) |
| `/nickrank clear [targets]` | Clear your fake rank |
| `/nickrank random [targets]` | Pick a random assignable rank |
| `/nickrank current [target]` | Show your current rank + preview |

You can also pass `-r <rank>` directly to `/nick` and `/randomnick`:
```
/nick with_name Steve -r vip
/randomnick -r moderator
```

### W-Nick Extras
- **`/wnick guide`** — interactive Paper Dialog with clickable action buttons
- **`/wnick info [player]`** — full debug view (UUID, nick, skin, fake rank, preview)
- **`/wnick version` / `/wnick reload`** — version info and config reload
- **Auto-apply saved nick on join** — players who reconnect get their nick back automatically
- **Configurable message prefix** — `message_prefix` in `config.yml`

### Integrations
- **LuckPerms** (required for rank spoofing) — hook into any LuckPerms group
- **TAB** (optional) — pushes spoofed prefix/suffix to TAB's tab-list and nametag managers
- **PlaceholderAPI** (optional) — exposes `%wnick_nickname%`, `%wnick_fake_rank%`, etc.
- **Paper Dialog API** (1.21.8+) — `/wnick guide` opens an in-game dialog
- **Folia** — supported

---

## Quick Start

1. **Install [Paper 1.21.8+](https://papermc.io)** (required for the Dialog API)
2. **Install [LuckPerms](https://luckperms.net/)** (required for rank spoofing)
3. *(Optional)* Install [TAB](https://github.com/NEZNAMY/TAB) and [PlaceholderAPI](https://github.com/HelpChat/PlaceholderAPI)
4. **Download** `W-Nick-1.0.81.jar` from the [releases page](../../releases) and drop it into `plugins/`
5. **Restart** your server
6. **Tag a group as randomly assignable:**
   ```
   /lp group vip permission set wnick.rank.assignable true
   ```
7. **In-game:** Run `/wnick guide` to open the interactive dialog, or:
   ```
   /nickrank list        → see available ranks
   /nickrank set vip     → pick a rank
   /nick random          → apply a random nick
   ```

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `wnick.commands.nick` | op | Access to `/nick` |
| `wnick.commands.nick.others` | op | Nick other players |
| `wnick.commands.nick.reset` | op | `/nick reset` |
| `wnick.commands.nick.reset_all` | op | `/nick reset_all` |
| `wnick.commands.nick.save_all` | op | `/nick save_all` |
| `wnick.commands.nick.save_cached` | op | `/nick save_cached` |
| `wnick.commands.randomnick` | op | Access to `/randomnick` |
| `wnick.commands.randomnick.others` | op | Random-nick other players |
| `wnick.commands.realname` | true | Access to `/realname` |
| `wnick.commands.nickrank` | op | Access to `/nickrank` |
| `wnick.commands.nickrank.list` | op | List ranks |
| `wnick.commands.nickrank.set` | op | Set a fake rank on yourself |
| `wnick.commands.nickrank.clear` | op | Clear your fake rank |
| `wnick.commands.nickrank.random` | op | Pick a random assignable rank |
| `wnick.commands.nickrank.current` | op | View your current fake rank |
| `wnick.commands.nickrank.others` | op | Act on other players' fake rank |
| `wnick.commands.wnick` | op | Access to `/wnick` |
| `wnick.commands.wnick.guide` | op | Open the Paper Dialog guide |
| `wnick.commands.wnick.info` | op | View your own nick info |
| `wnick.commands.wnick.info.others` | op | View other players' nick info |
| `wnick.admin.reload` | op | `/wnick reload` and `/nametag reload` |
| `wnick.admin.refresh_player` | op | `/nametag refresh_player` |
| `wnick.admin.debug` | op | `/nametag debug` |
| `wnick.rank.assignable` | false | Tags a LuckPerms group as randomly assignable |

---

## Configuration

`plugins/W-Nick/config.yml`:

```yaml
version: 6
can_use_existing_players: true
should_spoof_uuid: false

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

## Storage

**LOCAL** (default) — saves nick data to a YAML file in `plugins/W-Nick/data.yml`. No extra setup needed.

**MONGODB** — saves nick data to a MongoDB database. The MongoDB driver is **not bundled** in the lightweight JAR (~5 MB). To use MongoDB storage:
1. Install the [MongoDB driver](https://mongodb.github.io/mongo-java-driver/) as a server library, OR
2. Change `storage.type` back to `LOCAL` (the plugin falls back to LOCAL automatically if the driver is missing)

---

## Privacy

W-Nick ships **no telemetry and no phone-home calls**:
- No bStats / metrics
- No version-checker that hits third-party servers
- No "cloud nick" service that uploads your server IP or player UUIDs

The only outbound network calls are to **Mojang's official APIs** (username + skin lookups) and **mineskin.org** (only when a player explicitly runs `/nick from_url <url>`).

---

## Building from Source

This repository ships the patched Java sources under `src/`. The bundled dependencies (PacketEvents, CommandAPI, BookshelfAPI, etc.) are not open source and are not included — to build, you need the original upstream JAR which serves as the base for repackaging.

```bash
# Requires JDK 21 + the original Name-Tag-Paper-1.0.81.jar
./scripts/test.sh       # run the test suite
./scripts/package.sh    # build dist/W-Nick-1.0.81.jar
```

---

## Testing

W-Nick includes an automated test suite to catch runtime crashes before they reach users:

- **`JarSmokeTest`** — loads every `.class` in the final JAR and verifies all dependencies resolve. Catches "I stripped a package that was actually needed" bugs.
- **`RankFlagParsingTest`** — 7 JUnit tests for the `-r <rank>` flag parsing.
- **`UsernameGeneratorTest`** — verifies the random username generator produces valid Minecraft usernames.

Run locally:
```bash
./scripts/test.sh
```

---

## License

MIT — see [LICENSE](LICENSE).

---

## Documentation

📖 **[Wiki](https://github.com/Sxnpaiwin/W-nicks/wiki)** — full documentation:

- [Commands](https://github.com/Sxnpaiwin/W-nicks/wiki/Commands) — full command reference
- [Permissions](https://github.com/Sxnpaiwin/W-nicks/wiki/Permissions) — complete permission list
- [Configuration](https://github.com/Sxnpaiwin/W-nicks/wiki/Configuration) — `config.yml` docs
- [LuckPerms Integration](https://github.com/Sxnpaiwin/W-nicks/wiki/LuckPerms-Integration) — how rank spoofing works
- [Paper Dialog API](https://github.com/Sxnpaiwin/W-nicks/wiki/Paper-Dialog-API) — how `/wnick guide` works
- [Privacy](https://github.com/Sxnpaiwin/W-nicks/wiki/Privacy) — what backdoors were removed
- [Building from Source](https://github.com/Sxnpaiwin/W-nicks/wiki/Building-from-Source) — build & test instructions
- [Troubleshooting](https://github.com/Sxnpaiwin/W-nicks/wiki/Troubleshooting) — common issues and fixes

---

## Credits

W-Nick is authored and maintained by **Joehe**.

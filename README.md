# VillagerReset

VillagerReset is a dual-platform Minecraft server project that provides villager trade cycling features for:

- Paper 1.21.x plugin
- Fabric 1.21.11+ (Mojmap) server mod

![1](https://i.imgur.com/Qwh4Hfm.png)
![2](https://i.imgur.com/fViNBcA.png)

## Features

- Adds two special villager trade entries (before villager is locked by a normal purchase):
- `Cycle Trades` option (barrier icon): rerolls villager trades for emerald cost.
- `Profession Swap` option (head icon): swaps to a random profession for emerald cost/chance.
- Special options are virtual actions (no barrier/head item goes to player inventory).
- Configurable max special uses before sold out; restocks like normal villager offers.
- Optional unemployed handling: pay a higher first-time cost to assign profession.
- Optional cure discount spread to villagers in configurable radius.
- OP/admin command for runtime config edits, status listing, enable/disable, and debug mode.

## Commands

- `/villagerreset status`
- `/villagerreset list`
- `/villagerreset set <setting> <value>`
- `/villagerreset enable`
- `/villagerreset disable`
- `/villagerreset reload`
- `/villagerreset debug <on|off|status>`

Alias: `/vr`

Permission: `villagerreset.admin` (default: op)

## Example Config

```
enabled: true
debug: false
cycle-cost-emeralds: 1
profession-swap-cost-emeralds: 2
unemployed-initial-profession:
  enabled: true
  cost-emeralds: 10
cycle-max-uses: 8
profession-swap-success-chance: 1.0
cure-discount:
  enabled: true
  radius-blocks: 500
  bonus-level: 20
```

## Build

From project root:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

Artifacts:

- Paper: `paper/build/libs/VillageReset-v1.0.0-Paper.jar`
- Fabric: `fabric/build/libs/VillageReset-v1.0.0-Fabric.jar`

## Fabric Metadata

- Mod id: `villagerreset`
- Config file: `config/villagerreset.json`

## License

Licensed under `GNU General Public License v3.0` (GPL-3.0-only). See [LICENSE](LICENSE).

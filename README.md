# MyWolfs

An RPG plugin for Paper that turns tamed wolves into levelable companions with unique stats (Ranks) and progression.

## Features
- Wolves receive random ranks (S to E) for Health, Damage, Speed, and Armor upon taming.
- Wolves gain XP and level up by killing mobs.
- Unique naming system (Wolf, Wolf-2, etc.).
- Limit on the number of wolves per player, wolves tamed above the limit cannot level up (configurable).
- Baby wolves born from breeding cannot level up (vanilla balance).
- Full command suite for players and admins.

## Requirements
- Paper 26.3 or higher
- Java 25

## Commands
- `/wolfs ranks [name]` - Shows the RPG ranks of your wolf.
- `/wolfs stats [name]` - Shows the raw numerical stats.
- `/wolfs list` - Lists all your wolves in the current world.
- `/wolfs call [name]` - Teleports your wolf to you.
- `/wolfs rename <oldName> <newName>` - Renames a wolf.
- `/wolfs remove [name] confirm` - Safely deletes a wolf.
- `/wolfs help` - Shows the help menu.

## Permissions
- `mywolfs.admin` - Allows access to admin commands (setlevel, force remove).

## Configuration
The `config.yml` allows you to tweak:
- Max wolves per player (`max_wolves`).
- Stat gains per level.
- Rank multipliers.
- Mob XP values.
- Call cooldown.

## Roadmap (Planned Features)

Here is what I plan to add to the plugin in the future. (This list is not final and may change).

- [ ] **Regeneration & Death Mechanic:** Wolves will slowly regenerate health out of combat, and dying might have a penalty (or a way to revive them).
- [ ] **Special Abilities (Perks):** At certain levels, wolves will unlock special powers (e.g., fire resistance, critical strike chance).
- [ ] **GUI Menu:** Instead of chat commands, players will be able to right-click a wolf with a specific item to open an inventory menu showing all stats.
- [ ] **Genetics:** Wolves from different biomes might have different ranks and maybe breeding different wolves can mix good or bad ranks
- [ ] **Multi-language Support:** Add a system to easily translate the plugin to other languages.
- [ ] **Multiplayer Features:** a scoreboard showing off the strongest wolves to the whole server

*(If you have a suggestion, feel free to open an issue on GitHub!)*
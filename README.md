# MyWolfs

An RPG plugin for Paper that turns tamed wolves into levelable companions with unique stats (Ranks) and progression.

MyWolfs intends to give players a real reason to tame and use Minecraft wolves.

When a player tames a wolf, random ranks (S to E) are rolled for its stats. As the wolf levels up by killing mobs, its stats increase — and higher ranks mean those stats will scale much more effectively.

For balancing reasons, once a player tames a certain number of wolves (configurable), their next tames will result in Level 0 wolves, which cannot level up. The player can inspect the new wolf's ranks, and if they like it more than their current wolves, they can remove one of their old wolves to make room. Then, by feeding the Level 0 wolf its desired item (which is randomized from a configurable list), they can evolve it into a normal Level 1 wolf.

## Features
- **Rank System:** Wolves receive random ranks (S to E) for Health, Damage, Speed, and Armor upon taming.
- **Leveling:** Wolves gain XP and level up by killing mobs, increasing their stats based on their Ranks.
- **Async Calling:** `/wolfs call` can teleport wolves from other worlds or unloaded chunks without causing server lag.
- **Aggressive Mode:** Toggle your wolf into aggressive mode to automatically attack nearby hostile monsters.
- **Over-Limit Evolution:** Tame a wolf when your pack is full? It becomes a Level 0. Inspect its Ranks, remove an old wolf, and feed it its required item to promote it to Level 1!
- **Vanilla Breeding:** Baby wolves born from breeding cannot level up, preserving vanilla balance.

## Requirements
- Paper 26.3 or higher
- Java 25

## Commands
- `/wolfs` or `/wolfs ranks [name]` - Shows the RPG ranks, level, and XP of your wolf (also shows the required item for Level 0 wolves).
- `/wolfs stats [name]` - Shows the raw numerical stats (HP, Damage, etc.).
- `/wolfs list` - Lists all your wolves and the worlds they are currently in.
- `/wolfs call <name>` - Teleports your wolf to you, even if it's in an unloaded chunk. (Has a cooldown).
- `/wolfs mode <agg|normal> [name]` - Changes the wolf's combat behavior (Aggressive or Normal).
- `/wolfs rename <oldName> <newName>` - Renames a wolf.
- `/wolfs remove [name]` - Safely deletes a wolf (requires `/wolfs confirm`).
- `/wolfs confirm` - Confirms the deletion of a wolf.
- `/wolfs help` - Shows the help menu.

### Admin Commands
*(Requires `mywolfs.admin` permission)*
- `/wolfs admin setlevel <name> <level>` - Sets a wolf's level.
- `/wolfs admin forcerm <name>` (or `frm`) - Instantly deletes a wolf.

## Permissions
- `mywolfs.admin` - Allows access to admin commands (setlevel, force remove).

## Configuration
The `config.yml` allows you to tweak:
- `max_wolves`: Max RPG wolves per player.
- `stats`: Stat gains per level.
- `ranks`: Rank multipliers.
- `mob_xp`: XP values for each mob.
- `upgrade_items`: The list of items that can be rolled to promote Level 0 wolves.
- `settings`: Call cooldown and autosave interval.

## Roadmap (Planned Features)

Here is what I plan to add to the plugin in the future. (This list is not final and may change).

- [ ] **Regeneration & Death Mechanic:** Wolves will slowly regenerate health out of combat, and dying might have a penalty (or a way to revive them).
- [ ] **Special Abilities (Perks):** At certain levels, wolves will unlock special powers (e.g., fire resistance, critical strike chance).
- [ ] **GUI Menu:** Instead of chat commands, players will be able to right-click a wolf with a specific item to open an inventory menu showing all stats.
- [ ] **Genetics:** Wolves from different biomes might have different ranks, and breeding different wolves can mix good or bad ranks.
- [ ] **Multi-language Support:** Add a system to easily translate the plugin to other languages.
- [ ] **Multiplayer Features:** A scoreboard showing off the strongest wolves to the whole server.

*(If you have a suggestion, feel free to open an issue on GitHub!)*
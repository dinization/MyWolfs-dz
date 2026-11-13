# MyWolfs

An RPG plugin for Paper that turns tamed wolves and foxes into levelable companions with unique stats (Ranks) and progression.

MyWolfs intends to give players a real reason to tame and use Minecraft's companion mobs.

When a player tames a wolf, random ranks (S to E) are rolled for its stats. As the wolf levels up by killing mobs, its stats increase — and higher ranks mean those stats will scale much more effectively.

For balancing reasons, once a player tames a certain number of wolves (configurable), their next tames will result in Level 0 wolves. The player can inspect the ranks and evolve the wolf by feeding it a specific item.

Additionally, players can adopt Foxes as "Support Pets". They provide passive buffs (like Regeneration) to your wolves, and they can be brought into battle!

Players can now challenge each other to a duel to fight alongside their pets without risking them,
the duel ends with no deaths to the player or pets, have fun!

## Features
- **Wolf Rank System:** Wolves receive random ranks (S to E) for Health, Damage, Speed, and Armor upon taming.
- **Wolf Variants & Genetics:** A wolf's base stats are influenced by its vanilla variant (Pale, Woods, Black, etc.). Upon taming, wolves also roll a unique individual RNG variation, making every companion slightly different.
- **Leveling & Kills:** Wolves gain XP and level up by killing mobs. The plugin also tracks how many mobs each wolf has killed.
- **Fox Adoption & Support:** Baby foxes can be adopted using Raw Chicken. They provide a Regeneration aura to nearby wolves.
- **Async Calling:** `/wolfs call` and `/wolfs foxcall` can teleport your pets from other worlds or unloaded chunks without causing server lag.
- **Aggressive Mode:** Set your wolf to aggressive mode to automatically attack nearby hostile monsters.
- **PvP Duels:** Challenge other players to a duel! Your pets will fight alongside you, and deaths are cancelled for a safe, competitive experience.
- **Leaderboards (Scoreboard):** A fully interactive scoreboard showing the Top 3 Wolves in Level, Damage, and Kills on the server.
- **Friendly Fire Protection:** Configure whether players can accidentally hit their own wolves.
- **Vanilla Breeding:** Baby wolves born from breeding remain 100% vanilla, cannot level up, and cannot be evolved, preserving vanilla balance.
- **Update Checker:** The plugin automatically checks for new versions upon server startup and notifies administrators.

## Requirements
- Paper 26.3 or higher
- Java 25

## Commands
- `/wolfs` or `/wolfs stats [name]` - Shows the full stats and ranks of your wolf.
- `/wolfs list` - Lists all your wolves and the worlds they are currently in.
- `/wolfs call <name>` - Teleports your wolf to you.
- `/wolfs mode <agg|normal> [name]` - Changes the wolf's combat behavior (applies to nearby wolves if no name is given).
- `/wolfs rename <oldName> <newName>` - Renames a wolf.
- `/wolfs remove [name]` - Safely deletes a wolf (requires confirmation).
- `/wolfs confirm` - Confirms the deletion of a wolf.
- `/wolfs scoreboard` - Shows the Top 3 Wolves scoreboard for 15 seconds.
- `/wolfs duel <player>` - Challenges a player to a pet duel.
- `/wolfs duel <accept|decline|forfeit>` - Manages your current duel.
- `/wolfs help [page]` - Shows the help menu (pages 1-3).

### Fox Commands
- `/wolfs foxlist` - Lists all your adopted foxes.
- `/wolfs foxcall <name>` - Teleports your fox to you.
- `/wolfs foxrename <oldName> <newName>` - Renames a fox.

### Admin Commands
*(Requires `mywolfs.admin` permission)*
- `/wolfs setlevel <name> <level>` - Sets a wolf's level.
- `/wolfs forcerm <name>` (or `frm`) - Instantly deletes a wolf.
- `/wolfs spawn <wolf|fox>` - Spawns a tamed/configured pet for testing.

## Permissions
- `mywolfs.admin` - Allows access to admin commands.

## Configuration
The `config.yml` allows you to tweak:
- `max_wolves` & `max_foxes`: Max RPG pets per player.
- `stats`: Stat gains per level.
- `stats_roll`: RNG margins and variant multipliers for base stats.
- `ranks`: Rank multipliers.
- `mob_xp`: XP values for each mob.
- `upgrade_items`: The list of items for Level 0 wolves.
- `settings`: Call cooldowns, autosave intervals, friendly fire options, and duel system toggles
(enable/disable, max distance).
- `variant multipliers`: The stats multiplier for each wolf variant

## Roadmap (Planned Features)
- [ ] **Special Abilities (Perks):** At certain levels, wolves will unlock special powers.
- [ ] **GUI Menu:** Instead of chat commands, players will be able to right-click a pet to open an inventory menu.
- [ ] **Genetics:** Breeding different wolves can mix good or bad ranks.
- [ ] **Multi-language Support:** Add a system to easily translate the plugin.
- [ ] **Advanced Duel Targeting:** Allow players to use an item to command their wolves to focus a specific target during a duel.

*(If you have a suggestion, feel free to open an issue on GitHub!)*
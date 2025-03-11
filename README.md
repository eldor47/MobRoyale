Below is an example README.md file you can include in your project. It explains how to set up the configuration files, and how to compile, install, and package your plugin with Maven to generate the jar file.

---

# MobRoyale

MobRoyale is a custom Minecraft (Spigot/Paper) plugin that tracks mob kills, manages sessions and leaderboards, spawns waves of mobs, and provides a configurable chest loot system with random rarity tiers.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Configuration Files Setup](#configuration-files-setup)
- [Building and Packaging](#building-and-packaging)
- [Installation and Deployment](#installation-and-deployment)
- [Usage](#usage)
- [Troubleshooting](#troubleshooting)
- [License](#license)

## Features

- **Mob Kill Tracking:** Earn points for killing mobs with configurable point values.
- **Session and Leaderboard Management:** Track and display scores on a leaderboard.
- **Wave Spawner:** Spawn mobs in waves with configurable settings.
- **Lobby System:** Manage game start, stop, and player lobby with equipment setup.
- **Chest Loot System:** Randomly fill chests with loot based on weighted rarity tiers.
- **Commands:** Several commands including `/leaderboard`, `/startgame`, `/startwaves`, `/reloadloot`, `/resetleaderboard`, etc.

## Requirements

- Java Development Kit (JDK)
- Maven
- A Spigot or Paper Minecraft server (version 1.21.4+ recommended)
- (Optional) EssentialsX, Multiverse, and any other dependencies if required by your server setup

## Configuration Files Setup

Your plugin uses several configuration files. When the plugin is first run, default versions are generated in the plugin's data folder. You may customize these files as needed.

### config.yml

Contains general plugin settings such as the tracked world for mob kills. Example entry:

```yaml
tracked-world: world
```

### config_spawn.yml

Configures mob spawn settings, loot tables for mobs, and wave spawner settings.

### config_chest.yml

Configures the chest loot system:
- **chest.world:** The world where loot chests reside (e.g., "PVE").
- **chest.tier-chances:** Weighted chance values for tiers (common, uncommon, rare, legendary).
- **chest.tiers:** For each tier, define a fixed loot list along with `min-items`, `max-items`, and loot details.

Example:

```yaml
chest:
  world: PVE
  tier-chances:
    common: 50
    uncommon: 30
    rare: 15
    legendary: 5
  tiers:
    common:
      min-items: 1
      max-items: 2
      loot:
        - item: diamond_sword
          weight: 5
          amount: 1
          enchantments:
            sharpness: 3
        - item: splash_potion{Potion:healing1}
          weight: 10
          amount: 1
    # Additional tiers...
```

### config_lobby.yml (if used)

Configure the lobby system (world, spawn locations, equipment, excluded players).

## Building and Packaging

This project uses Maven for compilation and packaging.

1. **Clone the Repository:**  

2. **Compile the Plugin:**  
   Run the following Maven command to compile your code:
   ```bash
   mvn clean compile
   ```

3. **Package the Plugin:**  
   To create the jar file, run:
   ```bash
   mvn clean package
   ```
   The jar file will be generated in the `target/` folder (for example, `cozycraftpve-1.0-SNAPSHOT.jar`).

4. **Installation:**  
   Copy the generated jar file into your Minecraft server’s `plugins/` directory.

## Installation and Deployment

1. **Place the Jar:**  
   Copy `cozycraftpve-1.0-SNAPSHOT.jar` (or the built jar) to your server’s `plugins/` folder.

2. **Configure:**  
   Start your server once to generate default config files. Then, edit `config.yml`, `config_spawn.yml`, `config_chest.yml`, and (if applicable) `config_lobby.yml` as needed.

3. **Restart the Server:**  
   Restart your server to load the updated configuration.

## Usage

- **Mob Kill Tracking:**  
  Kills in the configured tracked world earn points. Use `/checkkills` to view your score.

- **Sessions and Leaderboard:**  
  Use `/startsession <name>`, `/viewsession <name>`, `/listsessions`, and `/leaderboard` to manage and view leaderboards.  
  Use `/resetleaderboard` to reset the scores.

- **Wave Spawner:**  
  Use `/startwaves` and `/stopwaves` to control mob waves.

- **Lobby System:**  
  Use `/startgame` to send players to the lobby and `/stopgame` to reset the lobby.

- **Chest Loot System:**  
  Chests in the designated chest world with the custom name `"chest_loot"` will be filled with loot when reloaded. Use `/reloadloot` and `/reloadchestconfig` to update chest loot.

## Troubleshooting

- **Empty Chests:**  
  Ensure that your chests in the specified world have the custom name `"chest_loot"` and that the chunks are loaded.  
- **Configuration Issues:**  
  Verify that your config files are correctly formatted and that loot entries are valid.  
- **Scoreboard Not Updating:**  
  Check that no conflicting plugins override the scoreboard API.

## License

This project is licensed under the MIT License.

```

---

You can adjust the details as needed for your project. Save this as `README.md` in your project root. This file will help users set up the configuration files, compile the project using Maven, and install the resulting jar on their server.

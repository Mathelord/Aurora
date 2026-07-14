# Aurora

[![GitHub Downloads](https://img.shields.io/github/downloads/Mathelord/Aurora/total?style=for-the-badge&label=Downloads)](https://github.com/Mathelord/Aurora/releases)
> [!WARNING]
> Silentaura and Aim assist are getting detected on matrix anticheat i dont know how to fix it yet

> [!WARNING]
> This project is still in progress.
> If something breaks/doesnt work, open an issue.
> Supported Minecraft versions are Fabric 1.21.4 and 1.21.11.
> This entire project is also vibecoded (fully ai generated but somehow good)
> If u want a module/feature open an issue
> Other Minecraft versions are rejected by the launcher.

Aurora is a Minecraft 1.21.4 fabric Java Agent injection client with a external gui.

Make sure to check out [Mathelord/Eclipse](https://github.com/Mathelord/Eclipse).

## Flagged Modules

- Knockback Delay (Grim Anticheat)
- Silentaura (Matrix)
- Aim assist (Matrix)

## Versions

Aurora supports Fabric 1.21.4 and 1.21.11. Version 1.21.4 is the primary target.
If you find bugs for 1.21.11 open an issue

## Modules

### Combat

- Aim Assist
- Auto Anchor
- Hit Swap
- Silent Aura
- Trigger Bot

### Movement

- Instant Stop
- Jump Reset
- No Jump Delay

### Player

- AutoTool
- FastPlace
- Reach

### Network

- BackTrack
- Blink
- Knockback Delay
- Network Delay (if set to 2000ms i had a 29~ block reach)

### Render

- Anti Debuff
- ESP
- Free Look
- Freecam
- Fullbright
- Nametags
- Target Ring
- Text GUI
- Trail
- Tracers
- Trajectories

### Misc

- Panic

## Configuration

Aurora saves module states, keybinds, and settings in `config.properties`. Its location depends on
the operating system and how Minecraft was launched:

| Environment | Configuration file |
| --- | --- |
| Linux | `~/.config/aurora/config.properties` |
| Linux with `XDG_CONFIG_HOME` | `$XDG_CONFIG_HOME/aurora/config.properties` |
| Windows | `%APPDATA%\Aurora\config.properties` |
| Flatpak launcher | `~/.var/app/<launcher-app-id>/config/aurora/config.properties` |
| Prismlauncher Flatpak | `~/.var/app/org.prismlauncher.PrismLauncher/config/aurora/config.properties` |

## Profiles

- does what it does

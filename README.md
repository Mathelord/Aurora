# Aurora

[![GitHub Downloads](https://img.shields.io/github/downloads/Mathelord/Aurora/total?style=for-the-badge&label=Downloads)](https://github.com/Mathelord/Aurora/releases)
> [!WARNING]
> Silentaura and Aim assist is getting detected on matrix anticheat i dont know how to fix it yet

> [!WARNING]
> This project is still in progress.
> If something breaks/doesnt work, open an issue.
> It can only inject Fabric 1.21.4 so far i will add more version support later
> This entire project is also vibecoded (fully ai generated but somehow good)
> If u want a module/feature open an issue

Aurora is a Minecraft 1.21.4 fabric injection client with a external gui.

Make sure to check out [Mathelord/Eclipse](https://github.com/Mathelord/Eclipse).

## Modules

- Reach
- AimAssist
- SilentAura
- TargetRing
- TriggerBot
- JumpReset
- NoJumpDelay
- HitSwap
- Trail
- Trajectories
- ESP
- Tracers
- Fullbright
- AutoTool
- FastPlace
- TextGui

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

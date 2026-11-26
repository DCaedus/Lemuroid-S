# Lemuroid-S

**A free, open-source retro game emulator for Android, built on [Lemuroid](https://github.com/Swordfish90/Lemuroid) and libretro.**
Lemuroid's simplicity, with features modeled on [RetroArch](https://github.com/libretro/RetroArch): one folder for all your games,
saves and covers, RetroArch-compatible saves, up-to-date emulator cores, and more systems. Light on battery and on older phones.
No ads, no tracking, no "All files access".

> Lemuroid-S is an **unofficial fork** of Lemuroid by [Swordfish90](https://github.com/Swordfish90) and is not affiliated with or
> endorsed by the original project. All credit for Lemuroid goes to its author and contributors. It uses its own app ID, so it can be
> installed next to the official Lemuroid.

## Download

- **GitHub:** [Releases](https://github.com/DCaedus/Lemuroid-S/releases) (coming with version 1.2)
- **Google Play:** coming soon

## Features

- **One Lemuroid-S folder** for ROMs (optional), BIOS files, saves, save states and covers, sorted by system with the same folder
  names as ES-DE. Your saves stay in your storage, survive reinstalls and are easy to back up.
- **Games load straight from your folders**, zipped or not, without copying them first (Android's folder access, like RetroArch).
- **RetroArch-compatible saves** (`.srm`) and **Import Saves** from RetroArch, another emulator's folder or a ZIP file
  (e.g. Pizza Boy's "Export saves").
- **Covers like RetroArch:** box art, screenshot or title screen, per console, downloaded from the libretro thumbnails or imported
  from RetroArch.
- **Autosave:** on exit (and continue where you left off) and every few minutes while playing, in an Autosave slot next to 4 save
  slots.
- **Fast-forward** up to 8x, with lower or muted audio and an on-screen button you can show or hide per console.
- **Display filters:** CRT, LCD, Scale2x, xBR, SABR, MMPX and CRT (zfast), all light single-pass GPU shaders, plus HD mode.
- **Up-to-date cores**, one per system: the most popular ones are built into the app, the others download the first time you need
  them.
- Gamepad support, local multiplayer, customizable touch controls, tilt input, quick save/load, Google Drive save sync (Google Play
  version).

## Supported systems

| System | Core |
|---|---|
| Nintendo (NES) | [FCEUmm](https://docs.libretro.com/library/fceumm/) |
| Super Nintendo (SNES) | [Snes9x](https://docs.libretro.com/library/snes9x/) |
| Nintendo 64 | [Mupen64Plus-Next](https://docs.libretro.com/library/mupen64plus/) |
| Game Boy, Game Boy Color | [Gambatte](https://docs.libretro.com/library/gambatte/) |
| Game Boy Advance | [mGBA](https://docs.libretro.com/library/mgba/) |
| Nintendo DS | [melonDS DS](https://github.com/JesseTG/melonds-ds) |
| Nintendo 3DS (64-bit ARM devices) | [Azahar](https://github.com/azahar-emu/azahar) |
| Virtual Boy | [Beetle VB](https://docs.libretro.com/library/beetle_vb/) |
| Pokémon Mini | [PokeMini](https://docs.libretro.com/library/pokemini/) |
| Sega Master System, Game Gear, Genesis / Mega Drive, Sega CD, SG-1000 | [Genesis Plus GX](https://docs.libretro.com/library/genesis_plus_gx/) |
| Sega 32X | [PicoDrive](https://docs.libretro.com/library/picodrive/) |
| Sega Dreamcast | [Flycast](https://docs.libretro.com/library/flycast/) |
| PlayStation | [PCSX ReARMed](https://docs.libretro.com/library/pcsx_rearmed/) |
| PlayStation Portable | [PPSSPP](https://docs.libretro.com/library/ppsspp/) |
| Arcade | [FinalBurn Neo](https://docs.libretro.com/library/fbneo/), [MAME 2003-Plus](https://docs.libretro.com/library/mame2003_plus/) for games FBNeo doesn't run |
| Neo Geo CD | [NeoCD](https://docs.libretro.com/library/neocd/) |
| Neo Geo Pocket, Neo Geo Pocket Color | [Beetle NeoPop](https://docs.libretro.com/library/beetle_neopop/) |
| PC Engine / TurboGrafx-16 | [Beetle PCE Fast](https://docs.libretro.com/library/beetle_pce_fast/) |
| WonderSwan, WonderSwan Color | [Beetle Cygne](https://docs.libretro.com/library/beetle_cygne/) |
| Atari 2600 | [Stella](https://docs.libretro.com/library/stella/) |
| Atari 5200 | [a5200](https://docs.libretro.com/library/atari5200/) |
| Atari 7800 | [ProSystem](https://docs.libretro.com/library/prosystem/) |
| Atari Lynx | [Handy](https://docs.libretro.com/library/handy/) |
| ColecoVision | [Gearcoleco](https://github.com/drhelius/Gearcoleco) |
| Intellivision | [FreeIntv](https://docs.libretro.com/library/freeintv/) |
| Vectrex | [vecx](https://docs.libretro.com/library/vecx/) |
| DOS | [DOSBox Pure](https://github.com/schellingb/dosbox-pure) |

Not every device can run every system: Nintendo DS, 3DS, PSP and Dreamcast need a fast phone.

Lemuroid-S does not include any games. Use your own legally obtained game files.

## Credits

- [Lemuroid](https://github.com/Swordfish90/Lemuroid) and [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) by
  Swordfish90 (GPL-3.0). Lemuroid-S includes a modified copy of LibretroDroid in the `libretrodroid` folder.
- [RetroArch](https://github.com/libretro/RetroArch) and the [libretro](https://www.libretro.com/) project, for the cores, the
  thumbnails and the Android folder-access layer (MIT) used to load games.
- The authors of every emulator core listed above. Cores are unmodified libretro builds (Azahar built from its source) and keep
  their own licenses.
- Display filters ported from [libretro's GLSL shaders](https://github.com/libretro/glsl-shaders): MMPX (Morgan McGuire and
  Mara Gagiu, MIT; GLSL by hunterk) and zfast-CRT (Greg Hogan, GPL-2.0+).

## License

Lemuroid-S is licensed under [GPL-3.0](COPYING), like Lemuroid. See the [privacy policy](PRIVACY.md).

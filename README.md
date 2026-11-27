# Lemuroid-S

A free, open-source retro game emulator for Android, based on [Lemuroid](https://github.com/Swordfish90/Lemuroid) and
libretro. Lemuroid's simplicity, with features inspired by [RetroArch](https://github.com/libretro/RetroArch).
No ads, no tracking, no "All files access".

> Unofficial fork of Lemuroid by [Swordfish90](https://github.com/Swordfish90), not affiliated with the original project.
> It has its own app ID, so it installs next to Lemuroid.

**Download:** [GitHub Releases](https://github.com/DCaedus/Lemuroid-S/releases) (from version 1.2) · Google Play (coming soon)

On GitHub, `lemuroid-s-<version>.apk` downloads each emulator core the first time a game needs it. The **full**
APKs have every core inside and work offline, one per processor type: `-full-arm64-v8a.apk` for most phones and
TVs, `-full-armeabi-v7a.apk` for older 32-bit devices, `-full-x86_64.apk` for Chromebooks and emulators.

## New in Lemuroid-S

- **One Lemuroid-S folder** for ROMs, BIOS, saves, states and covers, sorted by system (ES-DE folder names)
- **Games load straight from your folders**, zipped or not, without copying them first
- **RetroArch-compatible saves** and **Import Saves** from RetroArch, other emulators or a ZIP file
- **Covers like RetroArch:** box art, screenshot or title screen per console, or import RetroArch's
- **Autosave slot**, filled on exit and every few minutes while playing
- **Fast-forward** up to 8x, with lower or muted audio
- **New display filters:** Scale2x, xBR, SABR, MMPX, CRT (zfast)
- **Up-to-date cores and 10 new systems**, including Dreamcast, 32X and Neo Geo CD; melonDS DS and Azahar for DS and 3DS

## From Lemuroid

- Android TV support
- Automatically save and restore game states.
- ROMs scanning and indexing
- Optimized touch controls
- Quick save/load
- Support for Zipped ROMs
- Display simulation (LCD/CRT)
- Gamepad support
- Local multiplayer
- Tilt input
- Customizable touch controls (size and position)
- Cloud save sync
- HD mode

## Supported systems

- Atari 2600 (A26) ([stella](https://docs.libretro.com/library/stella/))
- Atari 5200 (A52) ([a5200](https://docs.libretro.com/library/atari5200/))
- Atari 7800 (A78) ([prosystem](https://docs.libretro.com/library/prosystem/))
- Atari Lynx (Lynx) ([handy](https://docs.libretro.com/library/handy/))
- Nintendo (NES) ([fceumm](https://docs.libretro.com/library/fceumm/))
- Super Nintendo (SNES) ([snes9x](https://docs.libretro.com/library/snes9x/))
- Game Boy (GB) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Color (GBC) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Advance (GBA) ([mgba](https://docs.libretro.com/library/mgba/))
- Nintendo 64 (N64) ([mupen64plus](https://docs.libretro.com/library/mupen64plus/))
- Nintendo DS (NDS) ([melonDS DS](https://github.com/JesseTG/melonds-ds))
- Nintendo 3DS (3DS) ([azahar](https://github.com/azahar-emu/azahar)) (64-bit ARM devices)
- Virtual Boy (VB) ([beetle_vb](https://docs.libretro.com/library/beetle_vb/))
- Pokémon Mini (PM) ([pokemini](https://docs.libretro.com/library/pokemini/))
- Sega Genesis (aka Megadrive) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega CD (aka Mega CD) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Master System (SMS) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Game Gear (GG) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega SG-1000 (SG-1000) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega 32X (32X) ([picodrive](https://docs.libretro.com/library/picodrive/))
- Sega Dreamcast (DC) ([flycast](https://docs.libretro.com/library/flycast/))
- PlayStation (PSX) ([PCSX-ReARMed](https://docs.libretro.com/library/pcsx_rearmed/))
- PlayStation Portable (PSP) ([ppsspp](https://docs.libretro.com/library/ppsspp/))
- FinalBurn Neo (Arcade) ([fbneo](https://github.com/libretro/FBNeo/))
- MAME 2003-Plus (Arcade) ([mame2003_plus](https://docs.libretro.com/library/mame2003_plus/))
- Neo Geo CD (NGCD) ([neocd](https://docs.libretro.com/library/neocd/))
- Neo Geo Pocket (NGP) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- Neo Geo Pocket Color (NGC) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- NEC PC Engine (PCE) ([beetle_pce_fast](https://docs.libretro.com/library/beetle_pce_fast/))
- WonderSwan (WS) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- WonderSwan Color (WSC) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- ColecoVision (CV) ([gearcoleco](https://github.com/drhelius/Gearcoleco))
- Intellivision (INTV) ([freeintv](https://docs.libretro.com/library/freeintv/))
- Vectrex (VEC) ([vecx](https://docs.libretro.com/library/vecx/))
- DOS ([dosbox_pure](https://github.com/schellingb/dosbox-pure))

Lemuroid-S doesn't include any games: use your own legally obtained game files.

## Credits

[Lemuroid](https://github.com/Swordfish90/Lemuroid) and [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) by
Swordfish90, [RetroArch](https://github.com/libretro/RetroArch) and the [libretro](https://www.libretro.com/) project, and the
authors of every emulator core and display filter. Each core keeps its own license.

## License

[GPL-3.0](COPYING) · [Privacy policy](PRIVACY.md)

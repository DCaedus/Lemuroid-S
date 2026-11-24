# Lemuroid-S cores

Emulator cores that the GitHub build of Lemuroid-S downloads when a system is first used. Each folder is one tested snapshot; the app reads the folder named in `CoresRelease.VERSION`.

**Snapshot:** libretro nightly Android builds from 2026-11-24 (32-bit x86 builds of the new cores from 2026-11-23) ([buildbot.libretro.com](https://buildbot.libretro.com/nightly/android/)), unmodified and zipped. File names: `<abi>-lib<core>_libretro_android.so.zip`.

Azahar isn't built by libretro: it was built here from Azahar's tag `2127.0` with its official libretro build settings (NDK 29, `-DENABLE_LIBRETRO=ON`, stripped).

`ppsspp-assets.zip` holds PPSSPP's support files (libretro's `PPSSPP.zip` from the same date).

| Core | Systems | Source | License |
|---|---|---|---|
| Stella | Atari 2600 | [stella-emu/stella](https://github.com/stella-emu/stella) | GPL-2.0 |
| ProSystem | Atari 7800 | [libretro/prosystem-libretro](https://github.com/libretro/prosystem-libretro) | GPL-2.0 |
| Handy | Atari Lynx | [libretro/libretro-handy](https://github.com/libretro/libretro-handy) | zlib |
| Genesis Plus GX | Genesis/Mega Drive, Sega CD, Master System, Game Gear, SG-1000 | [libretro/Genesis-Plus-GX](https://github.com/libretro/Genesis-Plus-GX) | Non-commercial |
| Mupen64Plus-Next | Nintendo 64 | [libretro/mupen64plus-libretro-nx](https://github.com/libretro/mupen64plus-libretro-nx) | GPL-2.0 |
| PPSSPP | PSP | [hrydgard/ppsspp](https://github.com/hrydgard/ppsspp) | GPL-2.0 |
| FinalBurn Neo | Arcade | [libretro/FBNeo](https://github.com/libretro/FBNeo) | Non-commercial |
| MAME 2003-Plus | Arcade | [libretro/mame2003-plus-libretro](https://github.com/libretro/mame2003-plus-libretro) | MAME (non-commercial) |
| melonDS DS | Nintendo DS (no 32-bit x86) | [JesseTG/melonds-ds](https://github.com/JesseTG/melonds-ds) | GPL-3.0 |
| Beetle PCE Fast | PC Engine | [libretro/beetle-pce-fast-libretro](https://github.com/libretro/beetle-pce-fast-libretro) | GPL-2.0 |
| Beetle NeoPop | Neo Geo Pocket (Color) | [libretro/beetle-ngp-libretro](https://github.com/libretro/beetle-ngp-libretro) | GPL-2.0 |
| Beetle Cygne | WonderSwan (Color) | [libretro/beetle-wswan-libretro](https://github.com/libretro/beetle-wswan-libretro) | GPL-2.0 |
| Azahar 2127.0 | Nintendo 3DS (arm64 only) | [azahar-emu/azahar](https://github.com/azahar-emu/azahar) | GPL-2.0 |
| PicoDrive | Sega 32X | [libretro/picodrive](https://github.com/libretro/picodrive) | Non-commercial |
| a5200 | Atari 5200 | [libretro/a5200](https://github.com/libretro/a5200) | GPL-2.0 |
| Flycast | Dreamcast | [flyinghead/flycast](https://github.com/flyinghead/flycast) | GPL-2.0 |
| NeoCD | Neo Geo CD | [libretro/neocd_libretro](https://github.com/libretro/neocd_libretro) | LGPL-3.0 |
| Beetle VB | Virtual Boy | [libretro/beetle-vb-libretro](https://github.com/libretro/beetle-vb-libretro) | GPL-2.0 |
| PokeMini | Pokémon Mini | [libretro/PokeMini](https://github.com/libretro/PokeMini) | GPL-3.0 |
| vecx | Vectrex | [libretro/libretro-vecx](https://github.com/libretro/libretro-vecx) | GPL-3.0 |
| Gearcoleco | ColecoVision | [drhelius/Gearcoleco](https://github.com/drhelius/Gearcoleco) | GPL-3.0 |
| FreeIntv | Intellivision | [libretro/FreeIntv](https://github.com/libretro/FreeIntv) | GPL-3.0 |
| DOSBox Pure | DOS | [schellingb/dosbox-pure](https://github.com/schellingb/dosbox-pure) | GPL-2.0 |

Thanks to the authors of these emulators and to the libretro project, which builds them.

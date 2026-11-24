# Lemuroid-S cores

Emulator cores that the GitHub build of Lemuroid-S downloads when a system is first used. Each folder is one tested snapshot; the app reads the folder named in `CoresRelease.VERSION`.

**Snapshot:** libretro nightly Android builds from 2026-11-24 ([buildbot.libretro.com](https://buildbot.libretro.com/nightly/android/)), unmodified and zipped. File names: `<abi>-lib<core>_libretro_android.so.zip`.

**Exception:** melonDS is Lemuroid's own melonDS build from [Swordfish90/LemuroidCores](https://github.com/Swordfish90/LemuroidCores) (until Lemuroid-S switches to melonDS DS).

`ppsspp-assets.zip` holds PPSSPP's support files (libretro's `PPSSPP.zip` from the same date).

| Core | Systems | Source | License |
|---|---|---|---|
| Stella | Atari 2600 | [stella-emu/stella](https://github.com/stella-emu/stella) | GPL-2.0 |
| ProSystem | Atari 7800 | [libretro/prosystem-libretro](https://github.com/libretro/prosystem-libretro) | GPL-2.0 |
| Handy | Atari Lynx | [libretro/libretro-handy](https://github.com/libretro/libretro-handy) | zlib |
| Genesis Plus GX | Genesis/Mega Drive, Sega CD, Master System, Game Gear | [libretro/Genesis-Plus-GX](https://github.com/libretro/Genesis-Plus-GX) | Non-commercial |
| Mupen64Plus-Next | Nintendo 64 | [libretro/mupen64plus-libretro-nx](https://github.com/libretro/mupen64plus-libretro-nx) | GPL-2.0 |
| PPSSPP | PSP | [hrydgard/ppsspp](https://github.com/hrydgard/ppsspp) | GPL-2.0 |
| FinalBurn Neo | Arcade | [libretro/FBNeo](https://github.com/libretro/FBNeo) | Non-commercial |
| MAME 2003-Plus | Arcade | [libretro/mame2003-plus-libretro](https://github.com/libretro/mame2003-plus-libretro) | MAME (non-commercial) |
| melonDS | Nintendo DS | [libretro/melonDS](https://github.com/libretro/melonDS) | GPL-3.0 |
| Beetle PCE Fast | PC Engine | [libretro/beetle-pce-fast-libretro](https://github.com/libretro/beetle-pce-fast-libretro) | GPL-2.0 |
| Beetle NeoPop | Neo Geo Pocket (Color) | [libretro/beetle-ngp-libretro](https://github.com/libretro/beetle-ngp-libretro) | GPL-2.0 |
| Beetle Cygne | WonderSwan (Color) | [libretro/beetle-wswan-libretro](https://github.com/libretro/beetle-wswan-libretro) | GPL-2.0 |
| Citra | Nintendo 3DS (64-bit only) | [libretro/citra](https://github.com/libretro/citra) | GPL-2.0 |
| DOSBox Pure | DOS | [schellingb/dosbox-pure](https://github.com/schellingb/dosbox-pure) | GPL-2.0 |

Thanks to the authors of these emulators and to the libretro project, which builds them.

# Included cores

Emulator cores shipped inside Lemuroid-S (all other cores are downloaded when first needed):

| Core | Systems | License |
|---|---|---|
| FCEUmm | NES | GPL-2.0 |
| Snes9x | SNES | Snes9x license (non-commercial) |
| Gambatte | Game Boy, Game Boy Color | GPL-2.0 |
| mGBA | Game Boy Advance | MPL-2.0 |
| PCSX ReARMed | PlayStation | GPL-2.0 |
| Genesis Plus GX | Mega Drive/Genesis, Master System, Game Gear, Sega CD, SG-1000 | Non-commercial (see core) |
| melonDS DS | Nintendo DS | GPL-3.0 |
| Mupen64Plus-Next | Nintendo 64 | GPL-2.0 |
| Mednafen NGP | Neo Geo Pocket / Color | GPL-2.0 |
| Handy | Atari Lynx | zlib |
| Beetle VB | Virtual Boy | GPL-2.0 |
| Beetle Cygne | WonderSwan / Color | GPL-2.0 |
| a5200 | Atari 5200 | GPL-2.0 |
| PokeMini | Pokémon mini | GPL-3.0 |
| ProSystem | Atari 7800 | GPL-2.0 |
| vecx | Vectrex | GPL-3.0 |

The first five `.so` files are in `src/main/jniLibs`: unmodified libretro nightly builds from
[buildbot.libretro.com](https://buildbot.libretro.com/nightly/android/) (2026-11-24), built by the libretro
project from each core's public source code. The 64-bit builds use 16 KB page alignment (required by Google Play).
The others aren't stored in git: the build downloads them from this repository's `cores` branch
(`CoresRelease.VERSION`), checked against its SHA256SUMS, like the Play modules (see `buildSrc/PlayCores.kt`).
All other cores are downloaded on demand: in the APK build from this repository's `cores` branch
(`CoresRelease.VERSION`), in the Play build as Play modules built from the same files (`modules/play-cores/`).

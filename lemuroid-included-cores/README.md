# Included cores

Emulator cores shipped inside Lemuroid-S (all other cores are downloaded when first needed):

| Core | Systems | License |
|---|---|---|
| FCEUmm | NES | GPL-2.0 |
| Snes9x | SNES | Snes9x license (non-commercial) |
| Gambatte | Game Boy, Game Boy Color | GPL-2.0 |
| mGBA | Game Boy Advance | MPL-2.0 |
| PCSX ReARMed | PlayStation | GPL-2.0 |

The `.so` files are unmodified libretro nightly builds from
[buildbot.libretro.com](https://buildbot.libretro.com/nightly/android/) (2026-11-24), built by the libretro
project from each core's public source code. The 64-bit builds use 16 KB page alignment (required by Google Play).
On-demand cores still come from Swordfish90/LemuroidCores tag `1.17.0` (`CORES_VERSION` in the core updaters).

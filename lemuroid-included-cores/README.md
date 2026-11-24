# Included cores

Emulator cores shipped inside Lemuroid-S (all other cores are downloaded when first needed):

| Core | Systems | License |
|---|---|---|
| FCEUmm | NES | GPL-2.0 |
| Snes9x | SNES | Snes9x license (non-commercial) |
| Gambatte | Game Boy, Game Boy Color | GPL-2.0 |
| mGBA | Game Boy Advance | MPL-2.0 |
| PCSX ReARMed | PlayStation | GPL-2.0 |

The `.so` files are unmodified builds from [Swordfish90/LemuroidCores](https://github.com/Swordfish90/LemuroidCores)
at tag `1.17.0`, built from the libretro cores' public source code.
When the cores version changes (`CORES_VERSION` in the core updaters), update these files too.

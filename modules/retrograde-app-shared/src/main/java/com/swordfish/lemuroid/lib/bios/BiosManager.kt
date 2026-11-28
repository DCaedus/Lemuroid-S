package com.swordfish.lemuroid.lib.bios

import com.swordfish.lemuroid.common.files.safeDelete
import com.swordfish.lemuroid.common.kotlin.associateByNotNull
import com.swordfish.lemuroid.common.kotlin.writeToFile
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream

class BiosManager(private val directoriesManager: DirectoriesManager) {
    private val crcLookup = SUPPORTED_BIOS.associateByNotNull { it.externalCRC32 }
    private val nameLookup = SUPPORTED_BIOS.associateByNotNull { it.externalName }

    fun getMissingBiosFiles(
        coreConfig: SystemCoreConfig,
        game: Game,
    ): List<String> {
        val regionalBiosFiles = coreConfig.regionalBIOSFiles

        val gameLabels =
            Regex("\\([A-Za-z]+\\)")
                .findAll(game.title)
                .map { it.value.drop(1).dropLast(1) }
                .filter { it.isNotBlank() }
                .toSet()

        Timber.d("Found game labels: $gameLabels")

        val requiredRegionalFiles =
            gameLabels.intersect(regionalBiosFiles.keys)
                .ifEmpty { regionalBiosFiles.keys }
                .mapNotNull { regionalBiosFiles[it] }

        Timber.d("Required regional files for game: $requiredRegionalFiles")

        return (coreConfig.requiredBIOSFiles + requiredRegionalFiles)
            .filter { !File(directoriesManager.getSystemDirectory(), it).exists() }
    }

    fun deleteBiosBefore(timestampMs: Long) {
        Timber.i("Pruning old bios files")
        SUPPORTED_BIOS
            .map { File(directoriesManager.getSystemDirectory(), it.libretroFileName) }
            .filter { it.lastModified() < normalizeTimestamp(timestampMs) }
            .forEach {
                Timber.d("Pruning old bios file: ${it.path}")
                it.safeDelete()
            }
    }

    @Deprecated("Use the suspend variant")
    fun getBiosInfo(): BiosInfo {
        // Lemuroid-S: arcade BIOS sets (neogeo.zip...) are game files kept with the ROMs, as in
        // Lemuroid; they're still picked up during scans but not listed with the other BIOS files.
        val bios =
            SUPPORTED_BIOS.filter { it.systemID !in ARCADE_SYSTEMS }.groupBy {
                File(directoriesManager.getSystemDirectory(), it.libretroFileName).exists()
            }.withDefault { listOf() }

        return BiosInfo(bios.getValue(true), bios.getValue(false))
    }

    suspend fun getBiosInfoAsync(): BiosInfo =
        withContext(Dispatchers.IO) {
            getBiosInfo()
        }

    fun tryAddBiosAfter(
        storageFile: StorageFile,
        inputStream: InputStream,
        timestampMs: Long,
    ): Boolean {
        val bios = findByCRC(storageFile) ?: findByName(storageFile) ?: return false

        Timber.i("Importing bios file: $bios")

        val biosFile = File(directoriesManager.getSystemDirectory(), bios.libretroFileName)
        if (biosFile.exists() && biosFile.setLastModified(normalizeTimestamp(timestampMs))) {
            Timber.d("Bios file already present. Updated last modification date.")
        } else {
            Timber.d("Bios file not available. Copying new file.")
            // Some cores keep their BIOS in a subfolder (e.g. neocd/, dc/).
            biosFile.parentFile?.mkdirs()
            inputStream.writeToFile(biosFile)
        }
        return true
    }

    private fun findByCRC(storageFile: StorageFile): Bios? {
        return crcLookup[storageFile.crc]
    }

    private fun findByName(storageFile: StorageFile): Bios? {
        return nameLookup[storageFile.name]
    }

    private fun normalizeTimestamp(timestamp: Long) = (timestamp / 1000) * 1000

    data class BiosInfo(val detected: List<Bios>, val notDetected: List<Bios>)

    companion object {
        private val ARCADE_SYSTEMS = setOf(SystemID.FBNEO, SystemID.MAME2003PLUS)

        private val SUPPORTED_BIOS =
            listOf(
                Bios(
                    "scph101.bin",
                    "6E3735FF4C7DC899EE98981385F6F3D0",
                    "PS One 4.5 NTSC-U/C",
                    SystemID.PSX,
                    "171BDCEC",
                ),
                Bios(
                    "scph7001.bin",
                    "1E68C231D0896B7EADCAD1D7D8E76129",
                    "PS Original 4.1 NTSC-U/C",
                    SystemID.PSX,
                    "502224B6",
                ),
                Bios(
                    "scph5501.bin",
                    "490F666E1AFB15B7362B406ED1CEA246",
                    "PS Original 3.0 NTSC-U/C",
                    SystemID.PSX,
                    "8D8CB7E4",
                ),
                Bios(
                    "scph1001.bin",
                    "924E392ED05558FFDB115408C263DCCF",
                    "PS Original 2.2 NTSC-U/C",
                    SystemID.PSX,
                    "37157331",
                ),
                Bios(
                    "lynxboot.img",
                    "FCD403DB69F54290B51035D82F835E7B",
                    "Lynx Boot Image",
                    SystemID.LYNX,
                    "0D973C9D",
                ),
                Bios(
                    "bios_CD_E.bin",
                    "E66FA1DC5820D254611FDCDBA0662372",
                    "Sega CD E",
                    SystemID.SEGACD,
                    "529AC15A",
                ),
                Bios(
                    "bios_CD_J.bin",
                    "278A9397D192149E84E820AC621A8EDD",
                    "Sega CD J",
                    SystemID.SEGACD,
                    "9D2DA8F2",
                ),
                Bios(
                    "bios_CD_U.bin",
                    "2EFD74E3232FF260E371B99F84024F7F",
                    "Sega CD U",
                    SystemID.SEGACD,
                    "C6D10268",
                ),
                Bios(
                    "bios7.bin",
                    "DF692A80A5B1BC90728BC3DFC76CD948",
                    "Nintendo DS ARM7",
                    SystemID.NDS,
                    "1280F0D5",
                ),
                Bios(
                    "bios9.bin",
                    "A392174EB3E572FED6447E956BDE4B25",
                    "Nintendo DS ARM9",
                    SystemID.NDS,
                    "2AB23573",
                ),
                Bios(
                    "firmware.bin",
                    "E45033D9B0FA6B0DE071292BBA7C9D13",
                    "Nintendo DS Firmware",
                    SystemID.NDS,
                    "945F9DC9",
                    "nds_firmware.bin",
                ),
                // Arcade BIOS sets: FBNeo also looks in system/fbneo/, which works however the game
                // itself is loaded. Matched by name (zip checksums depend on how the set was packed).
                Bios(
                    "fbneo/neogeo.zip",
                    "",
                    "Neo Geo (FBNeo)",
                    SystemID.FBNEO,
                    null,
                    "neogeo.zip",
                ),
                Bios(
                    "fbneo/pgm.zip",
                    "",
                    "PGM (FBNeo)",
                    SystemID.FBNEO,
                    null,
                    "pgm.zip",
                ),
                Bios(
                    "5200.rom",
                    "281F20EA4320404EC820FB7EC0693B38",
                    "Atari 5200",
                    SystemID.ATARI5200,
                    "4248D3E3",
                    "5200.rom",
                ),
                Bios(
                    "colecovision.rom",
                    "2C66F5911E5B42B8EBE113403548EEE7",
                    "ColecoVision",
                    SystemID.COLECOVISION,
                    "3AA93EF3",
                    "colecovision.rom",
                ),
                Bios(
                    "exec.bin",
                    "62E761035CB657903761800F4437B8AF",
                    "Intellivision Executive",
                    SystemID.INTELLIVISION,
                    "CBCE86F7",
                    "exec.bin",
                ),
                Bios(
                    "grom.bin",
                    "0CD5946C6473E42E8E4C2137785E427F",
                    "Intellivision GROM",
                    SystemID.INTELLIVISION,
                    "683A4158",
                    "grom.bin",
                ),
                Bios(
                    "bios.min",
                    "1E4FB124A3A886865ACB574F388C803D",
                    "Pokémon Mini",
                    SystemID.POKEMINI,
                    "AED3C14D",
                    "bios.min",
                ),
                Bios(
                    "dc/dc_boot.bin",
                    "E10C53C2F8B90BAB96EAD2D368858623",
                    "Dreamcast",
                    SystemID.DREAMCAST,
                    "89F2B1A1",
                    "dc_boot.bin",
                ),
                Bios(
                    "neocd/000-lo.lo",
                    "FC7599F3F871578FE9A0453662D1C966",
                    "Neo Geo CD Y-Zoom",
                    SystemID.NEOGEOCD,
                    "5A86CFF2",
                    "000-lo.lo",
                ),
                Bios(
                    "neocd/ng-lo.rom",
                    "E255264D85D5765013B1B2FA8109DD53",
                    "Neo Geo CD Y-Zoom",
                    SystemID.NEOGEOCD,
                    "E09E253C",
                    "ng-lo.rom",
                ),
                Bios(
                    "neocd/neocd_f.rom",
                    "8834880C33164CCBE6476B559F3E37DE",
                    "Neo Geo CD Front Loader",
                    SystemID.NEOGEOCD,
                    "6FD2A520",
                    "neocd_f.rom",
                ),
                Bios(
                    "neocd/neocd_sf.rom",
                    "043D76D5F0EF836500700C34FAEF774D",
                    "Neo Geo CD Front Loader (SMKDAN)",
                    SystemID.NEOGEOCD,
                    "1101F313",
                    "neocd_sf.rom",
                ),
                Bios(
                    "neocd/front-sp1.bin",
                    "5C2366F25FF92D71788468CA492EBECA",
                    "Neo Geo CD Front Loader (MAME)",
                    SystemID.NEOGEOCD,
                    "CAC62307",
                    "front-sp1.bin",
                ),
                Bios(
                    "neocd/neocd_t.rom",
                    "DE3CF45D227AD44645B22AA83B49F450",
                    "Neo Geo CD Top Loader",
                    SystemID.NEOGEOCD,
                    "56C9ECBA",
                    "neocd_t.rom",
                ),
                Bios(
                    "neocd/neocd_st.rom",
                    "F6325A33C6D63EA4B9162A3FA8C32727",
                    "Neo Geo CD Top Loader (SMKDAN)",
                    SystemID.NEOGEOCD,
                    "01C299CC",
                    "neocd_st.rom",
                ),
                Bios(
                    "neocd/top-sp1.bin",
                    "122AEE210324C72E8A11116E6EF9C0D0",
                    "Neo Geo CD Top Loader (MAME)",
                    SystemID.NEOGEOCD,
                    "C36A47C0",
                    "top-sp1.bin",
                ),
                Bios(
                    "neocd/neocd_z.rom",
                    "11526D58D4C524DAEF7D5D677DC6B004",
                    "Neo Geo CDZ",
                    SystemID.NEOGEOCD,
                    "33697892",
                    "neocd_z.rom",
                ),
                Bios(
                    "neocd/neocd_sz.rom",
                    "971EE8A36FB72DA57AED01758F0A37F5",
                    "Neo Geo CDZ (SMKDAN)",
                    SystemID.NEOGEOCD,
                    "42F6B6C4",
                    "neocd_sz.rom",
                ),
                Bios(
                    "neocd/neocd.bin",
                    "F39572AF7584CB5B3F70AE8CC848ABA2",
                    "Neo Geo CDZ (MAME)",
                    SystemID.NEOGEOCD,
                    "DF9DE490",
                    "neocd.bin",
                ),
                Bios(
                    "neocd/uni-bioscd.rom",
                    "08CA8B2DBA6662E8024F9E789711C6FC",
                    "Neo Geo CD Universe BIOS",
                    SystemID.NEOGEOCD,
                    "FF3ABC59",
                    "uni-bioscd.rom",
                ),
            )
    }
}

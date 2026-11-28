import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Lemuroid-S: the Google Play build delivers cores as on-demand Play modules (modules/play-cores/lemuroid_core_<core>).
 * Their libraries aren't stored in git: each module downloads them at build time from the same release the
 * APK build uses (the "cores" branch, folder CoresRelease.VERSION), checks them against its SHA256SUMS and
 * unpacks them for packaging.
 */
object PlayCores {
    const val FOLDER = "modules/play-cores"
    private const val REPOSITORY_URL = "https://github.com/DCaedus/Lemuroid-S/raw/cores/"

    /** Every module must cover the same ABIs as the app (Play rule); missing ones get a placeholder library. */
    private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    /** Names of the Play core modules ("lemuroid_core_fbneo", ...), from the play-cores folder. */
    fun moduleNames(rootDir: File): List<String> =
        File(rootDir, FOLDER).listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("lemuroid_core_") && File(it, "build.gradle.kts").exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    /** The cores release (CoresRelease.VERSION), read from the app's code so it's defined once. */
    fun coresVersion(rootDir: File): String {
        val source =
            File(rootDir, "modules/retrograde-app-shared/src/main/java/com/swordfish/lemuroid/lib/core/CoresRelease.kt")
                .readText()
        return Regex("""const val VERSION = "([^"]+)"""").find(source)?.groupValues?.get(1)
            ?: error("CoresRelease.VERSION not found")
    }

    /** Folder the module's libraries are unpacked to (<abi>/lib<core>_libretro_android.so). */
    fun libsDir(project: Project): File = project.layout.buildDirectory.dir("coreLibs").get().asFile

    /** Core names (e.g. "fbneo") of all on-demand cores, from the Play module names. */
    fun coreNames(rootDir: File): List<String> = moduleNames(rootDir).map { it.removePrefix("lemuroid_core_") }

    /**
     * Registers the download of [libraryName] (e.g. "fbneo") into [outputDir] and runs it before native
     * libraries are merged. Play modules add [placeholders] for ABIs the core isn't built for.
     */
    fun registerFetch(
        project: Project,
        libraryName: String,
        taskName: String = "fetchCoreLibraries",
        outputDir: File = libsDir(project),
        placeholders: Boolean = true,
    ) {
        val fetch =
            project.tasks.register(taskName, FetchCoreLibraries::class.java) {
                library.set(libraryName)
                version.set(coresVersion(project.rootDir))
                this.outputDir.set(outputDir)
                addPlaceholders.set(placeholders)
                placeholderDir.set(File(project.rootDir, "$FOLDER/placeholder"))
            }
        project.tasks.configureEach {
            if ((name.startsWith("merge") && name.endsWith("JniLibFolders")) || name.contains("lint", ignoreCase = true)) dependsOn(fetch)
        }
    }

    /** Registers the download of a release file (e.g. "ppsspp-assets.zip") into [outputDir], before assets are merged. */
    fun registerFetchFile(
        project: Project,
        fileName: String,
        outputDir: File,
    ) {
        val fetch =
            project.tasks.register("fetchCoreFile_" + fileName.replace(Regex("[^A-Za-z0-9]"), "_"), FetchCoreFile::class.java) {
                file.set(fileName)
                version.set(coresVersion(project.rootDir))
                this.outputDir.set(outputDir)
            }
        project.tasks.configureEach {
            if ((name.startsWith("merge") && name.endsWith("Assets")) || name.contains("lint", ignoreCase = true)) dependsOn(fetch)
        }
    }

    abstract class FetchCoreFile : DefaultTask() {
        @get:Input
        abstract val file: Property<String>

        @get:Input
        abstract val version: Property<String>

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @TaskAction
        fun fetch() {
            val output = outputDir.get().asFile
            output.deleteRecursively()
            output.mkdirs()
            val data = URI("$REPOSITORY_URL${version.get()}/${file.get()}").toURL().openStream().use { it.readBytes() }
            File(output, file.get()).writeBytes(data)
        }
    }

    abstract class FetchCoreLibraries : DefaultTask() {
        @get:Input
        abstract val library: Property<String>

        @get:Input
        abstract val version: Property<String>

        @get:InputDirectory
        abstract val placeholderDir: DirectoryProperty

        @get:Input
        abstract val addPlaceholders: Property<Boolean>

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @TaskAction
        fun fetch() {
            val baseUrl = "$REPOSITORY_URL${version.get()}/"
            val zipSuffix = "-lib${library.get()}_libretro_android.so.zip"
            val checksums =
                download(baseUrl + "SHA256SUMS").toString(Charsets.UTF_8).lines()
                    .mapNotNull { line -> line.trim().split(Regex("\\s+")).takeIf { it.size == 2 } }
                    .associate { (hash, name) -> name.removePrefix("*") to hash }
                    .filterKeys { it.endsWith(zipSuffix) }
            check(checksums.isNotEmpty()) { "No ${library.get()} libraries in cores release ${version.get()}" }

            val output = outputDir.get().asFile
            output.deleteRecursively()
            checksums.forEach { (zipName, expectedHash) ->
                val abi = zipName.removeSuffix(zipSuffix)
                val data = download(baseUrl + zipName)
                val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
                check(hash == expectedHash) { "Checksum mismatch for $zipName" }
                unzip(data, File(output, abi))
                logger.lifecycle("Core ${library.get()}: $abi")
            }

            // See modules/play-cores/placeholder/README.md.
            if (!addPlaceholders.get()) return
            ABIS.filter { !File(output, it).isDirectory }.forEach { abi ->
                val placeholder = File(placeholderDir.get().asFile, "$abi/libcore_placeholder.so")
                placeholder.copyTo(File(output, "$abi/libplaceholder_${library.get()}.so"))
                logger.lifecycle("Core ${library.get()}: $abi not available, placeholder added")
            }
        }

        private fun download(url: String): ByteArray {
            var lastError: Exception? = null
            repeat(4) { attempt ->
                try {
                    return URI(url).toURL().openStream().use { it.readBytes() }
                } catch (e: Exception) {
                    lastError = e
                    Thread.sleep(2000L * (attempt + 1))
                }
            }
            throw IllegalStateException("Could not download $url", lastError)
        }

        private fun unzip(
            data: ByteArray,
            target: File,
        ) {
            target.mkdirs()
            ZipInputStream(data.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }
                    .filter { !it.isDirectory && it.name.endsWith(".so") }
                    .forEach { entry ->
                        // The release zips hold "<core>_libretro_android.so"; Android only unpacks native
                        // libraries named "lib*.so", and the app looks for "lib<core>_libretro_android.so".
                        val name = File(entry.name).name.let { if (it.startsWith("lib")) it else "lib$it" }
                        File(target, name).outputStream().use { zip.copyTo(it) }
                    }
            }
        }
    }
}

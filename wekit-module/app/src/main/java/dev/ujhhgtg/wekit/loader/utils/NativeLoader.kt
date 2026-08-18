package dev.ujhhgtg.wekit.loader.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Process
import com.tencent.mmkv.MMKV
import dev.ujhhgtg.wekit.loader.startup.StartupInfo
import dev.ujhhgtg.wekit.loader.utils.NativeLoader.init
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.fs.createDirsSafe
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.div
import kotlin.io.path.exists

object NativeLoader {

    private const val TAG = "NativeLoader"

    private data class ZygiskPayload(
        val apk: File,
        val dataDir: File,
    )

    private val nativeLoadLock = Any()
    private var zygiskPayload: ZygiskPayload? = null
    private var zygiskNativeLibraries: Map<String, File> = emptyMap()
    private var xposedNativeLibraries: Map<String, File> = emptyMap()
    private var nativeLibrariesLoaded = false

    /**
     * Configures native loading for the copied APK that the FunBox-style
     * bootstrap placed in the target app's data directory. This must run before
     * module startup reaches [init].
     */
    @JvmStatic
    fun configureZygiskPayload(apkPath: String, dataDir: String) = synchronized(nativeLoadLock) {
        check(!nativeLibrariesLoaded) { "native libraries were already loaded" }
        val apk = File(apkPath)
        require(apk.isFile && apk.canRead()) { "Zygisk payload APK is unreadable: $apkPath" }
        val appDataDir = File(dataDir)
        require(appDataDir.isDirectory) { "Zygisk app data directory is unavailable: $dataDir" }
        zygiskPayload = ZygiskPayload(apk, appDataDir)
    }

    fun init(hostCtx: Context) {
        ensureNativeLibrariesLoaded(hostCtx)
        val mmkvDir = hostCtx.filesDir.toPath() / "mmkv"
        if (!mmkvDir.exists()) {
            mmkvDir.createDirsSafe()
        }

        val libLoader = when {
            zygiskPayload != null -> zygiskMmkvLibLoader()
            xposedNativeLibraries.isNotEmpty() -> xposedMmkvLibLoader()
            else -> null
        }
        if (libLoader == null) {
            MMKV.initialize(hostCtx, mmkvDir.toString())
        } else {
            MMKV.initialize(hostCtx, mmkvDir.toString(), libLoader)
        }

        MMKV.mmkvWithID(WePrefs.PREFS_NAME, MMKV.MULTI_PROCESS_MODE)
    }

    private fun ensureNativeLibrariesLoaded(hostCtx: Context) {
        synchronized(nativeLoadLock) {
            if (nativeLibrariesLoaded) {
                return@synchronized
            }
            val payload = zygiskPayload
            if (payload == null) {
                loadXposedLibraries(hostCtx)
            } else {
                loadZygiskLibraries(payload)
            }
            nativeLibrariesLoaded = true
        }
    }

    // ── Xposed / LSPosed native loading ──────────────────────────────────────

    /**
     * In LSPosed/Traditional Xposed, System.loadLibrary may fail because the
     * module classloader's native library path is not always set up correctly.
     * We try the standard path first, then fall back to locating the .so next
     * to the module APK, and finally extract from the APK itself.
     */
    private fun loadXposedLibraries(hostCtx: Context) {
        val libraries = listOf("dexkit", "wekit_native")
        val resolved = mutableMapOf<String, File>()

        for (libName in libraries) {
            val loaded = runCatching {
                System.loadLibrary(libName)
                true
            }.getOrElse { t ->
                WeLogger.w(TAG, "System.loadLibrary($libName) failed: ${t.message}")
                false
            }

            if (!loaded) {
                val soFile = findOrExtractLibrary(hostCtx, libName)
                    ?: error("could not locate native library lib$libName.so for Xposed module")
                System.load(soFile.absolutePath)
                resolved[libName] = soFile
                WeLogger.i(TAG, "loaded $libName from ${soFile.absolutePath}")
            }
        }

        // Also resolve mmkv for the custom LibLoader
        if (resolved.isNotEmpty()) {
            findOrExtractLibrary(hostCtx, "mmkv")?.let { resolved["mmkv"] = it }
            findOrExtractLibrary(hostCtx, "androidx.graphics.path")?.let {
                resolved["androidx.graphics.path"] = it
            }
        }

        xposedNativeLibraries = resolved
    }

    /**
     * Locate a native library for the current ABI:
     * 1. In the module APK's sibling lib/ directory (standard install layout).
     * 2. Extracted from the module APK into hostCtx.filesDir.
     */
    private fun findOrExtractLibrary(hostCtx: Context, libName: String): File? {
        val fileName = "lib$libName.so"
        val abi = currentProcessAbi()

        // 1) Try the module APK's native library directory
        runCatching {
            val moduleApk = File(StartupInfo.modulePath)
            if (moduleApk.exists()) {
                // Typical layout: /data/app/~~xxx/base.apk → /data/app/~~xxx/lib/arm64/
                val apkDir = moduleApk.parentFile
                val candidates = listOf(
                    File(apkDir, "lib/$abi/$fileName"),
                    File(apkDir, "lib/${abi.replace("-v", "/")}/$fileName"),
                    File(apkDir.parentFile, "lib/$abi/$fileName"),
                )
                for (c in candidates) {
                    if (c.exists() && c.length() > 0) {
                        c.setExecutable(true, false)
                        return c
                    }
                }

                // 2) Extract from the APK
                ZipFile(moduleApk).use { zip ->
                    val entry = zip.getEntry("lib/$abi/$fileName")
                    if (entry != null) {
                        val libDir = File(hostCtx.filesDir, ".wekit-native/$abi").apply { mkdirs() }
                        val outFile = File(libDir, fileName)
                        if (!outFile.exists() || outFile.length() != entry.size) {
                            zip.getInputStream(entry).use { input ->
                                outFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        outFile.setReadable(true, false)
                        outFile.setExecutable(true, false)
                        return outFile
                    }
                }
            }
        }.onFailure { WeLogger.w(TAG, "findOrExtractLibrary($libName) failed: ${it.message}") }

        return null
    }

    private fun currentProcessAbi(): String {
        val supported = if (Process.is64Bit()) {
            Build.SUPPORTED_64_BIT_ABIS
        } else {
            Build.SUPPORTED_32_BIT_ABIS
        }
        // Prefer the ABIs we ship
        val shipped = listOf("arm64-v8a", "armeabi-v7a")
        return supported.firstOrNull { it in shipped } ?: supported.firstOrNull() ?: "arm64-v8a"
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun xposedMmkvLibLoader(): MMKV.LibLoader = MMKV.LibLoader { libraryName ->
        val library = xposedNativeLibraries[libraryName]
        if (library != null) {
            System.load(library.absolutePath)
        } else {
            System.loadLibrary(libraryName)
        }
    }

    // ── Zygisk native loading ────────────────────────────────────────────────

    /**
     * InMemoryDexClassLoader has no native-library directory on API 28. Match
     * FunBox's workaround: extract packaged libraries into app data, then use
     * absolute System.load paths from this module ClassLoader.
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadZygiskLibraries(payload: ZygiskPayload) {
        val abi = currentProcessAbi(payload.apk)
        val libraryDir = File(payload.dataDir, ".wekit-native-$abi")
        if (!libraryDir.exists() && !libraryDir.mkdirs()) {
            error("cannot create Zygisk native-library directory: $libraryDir")
        }
        require(libraryDir.isDirectory) { "Zygisk native-library path is not a directory: $libraryDir" }

        val libraries = mutableMapOf<String, File>()
        ZipFile(payload.apk).use { archive ->
            val names = listOf(
                "androidx.graphics.path" to "libandroidx.graphics.path.so",
                "dexkit" to "libdexkit.so",
                "mmkv" to "libmmkv.so",
                "wekit_native" to "libwekit_native.so",
            )
            for (name in names) {
                val (libraryName, fileName) = name
                val entry = archive.getEntry("lib/$abi/$fileName") ?: continue
                val extracted = extractLibrary(archive, entry.name, libraryDir, fileName)
                libraries[libraryName] = extracted
                if (libraryName != "mmkv") {
                    System.load(extracted.absolutePath)
                }
            }
            require(archive.getEntry("lib/$abi/libdexkit.so") != null) {
                "Zygisk payload is missing libdexkit.so for $abi"
            }
            require(archive.getEntry("lib/$abi/libwekit_native.so") != null) {
                "Zygisk payload is missing libwekit_native.so for $abi"
            }
        }
        zygiskNativeLibraries = libraries
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun zygiskMmkvLibLoader(): MMKV.LibLoader = MMKV.LibLoader { libraryName ->
        val library = zygiskNativeLibraries[libraryName]
        if (library != null) {
            System.load(library.absolutePath)
        } else {
            System.loadLibrary(libraryName)
        }
    }

    private fun currentProcessAbi(apk: File): String {
        val candidates = if (Process.is64Bit()) {
            Build.SUPPORTED_64_BIT_ABIS.asList()
        } else {
            Build.SUPPORTED_32_BIT_ABIS.asList()
        }
        ZipFile(apk).use { archive ->
            return candidates.firstOrNull { abi ->
                archive.getEntry("lib/$abi/libwekit_native.so") != null
            } ?: error("Zygisk payload has no native library for this process ABI")
        }
    }

    private fun extractLibrary(
        archive: ZipFile,
        entryName: String,
        destinationDir: File,
        libraryName: String,
    ): File {
        val destination = File(destinationDir, libraryName)
        val temporary = File(destinationDir, "$libraryName.${Process.myPid()}.tmp")
        temporary.delete()
        archive.getInputStream(archive.getEntry(entryName)).use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        temporary.setReadable(true, true)
        temporary.setExecutable(true, true)
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("cannot publish Zygisk native library: $destination")
        }
        return destination
    }
}

package dev.ujhhgtg.wekit.features.items.chat.panel

import dev.ujhhgtg.wekit.utils.fs.KnownPaths
import dev.ujhhgtg.wekit.utils.fs.createDirsSafe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.isRegularFile

object PanelPaths {
    val stickerPanelDir: Path by lazy { (KnownPaths.moduleData / "sticker_panel").createDirsSafe() }
    val voicePanelDir: Path by lazy { (KnownPaths.moduleData / "voice_panel").createDirsSafe() }
    val cloneVoiceDir: Path by lazy { (voicePanelDir / "clone_voices").createDirsSafe() }
    val panelCacheDir: Path by lazy { (KnownPaths.moduleCache / "panels").createDirsSafe() }
    val telegramStickerImportDir: Path by lazy {
        (stickerPanelDir / ".telegram_import").createDirsSafe()
    }

    fun cleanupStalePanelCache() {
        val cutoff = FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS))
        runCatching {
            Files.list(panelCacheDir).use { paths ->
                paths.filter { it.isRegularFile() && Files.getLastModifiedTime(it) < cutoff }
                    .forEach(Path::deleteIfExists)
            }
        }
    }
}

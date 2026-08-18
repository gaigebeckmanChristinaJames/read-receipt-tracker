import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

abstract class GenerateMethodHashesTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val namespace: Property<String>

    @TaskAction
    fun generate() {
        val srcDir = sourceDir.get().asFile
        val outDir = outputDir.get().asFile
        val outputFile = outDir.resolve("${namespace.get().replace(".", "/")}/dexkit/cache/GeneratedMethodHashes.kt")

        val hashMap = mutableMapOf<String, String>()

        // Pre-filter files containing the token to save time, then strictly validate inside
        srcDir.walk().filter { it.isFile && it.extension == "kt" && it.readText().contains("IResolveDex") }.forEach { file ->
            val content = file.readText()

            // Strip comments to avoid matching class/object keywords in KDOC or line comments.
            // Lexer-based on purpose — see stripCommentsPreservingStrings for why regexes cannot
            // do this correctly.
            val clean = stripCommentsPreservingStrings(content)

            val packageName = clean.findCode(Regex("""package\s+([\w.]+)"""))?.groupValues?.get(1)

            // A file may declare helpers before its feature. Select the declaration that actually
            // implements IResolveDex instead of assuming the first class/object is the feature.
            val classRegex = Regex("""\b(?:class|object)\s+(\w+)\b""")
            val declarations = clean.findAllCode(classRegex)
            val resolveDexDeclaration = declarations.withIndex().firstNotNullOfOrNull { (index, match) ->
                val braceIndex = clean.indexOfCode('{', match.range.first)
                val closingBraceIndex = clean.indexOfCode('}', match.range.first)
                val nextDeclarationIndex = declarations.getOrNull(index + 1)?.range?.first ?: clean.length
                if (
                    braceIndex == -1 ||
                    braceIndex >= nextDeclarationIndex ||
                    (closingBraceIndex != -1 && braceIndex >= closingBraceIndex)
                ) {
                    return@firstNotNullOfOrNull null
                }

                val signature = clean.substring(match.range.first, braceIndex)
                if (signature.contains(":") && Regex("""\bIResolveDex\b""").containsMatchIn(signature)) {
                    match
                } else {
                    null
                }
            } ?: return@forEach // Skip files that only import or reference the interface internally
            val className = resolveDexDeclaration.groupValues[1]

            val fullClassName = if (packageName != null) "$packageName.$className" else className
            val blocks = mutableListOf<String>()

            // 1. Extract resolveDex method body if it exists
            val resolveDexMatch = clean.findCode(Regex("""override\s+fun\s+resolveDex\s*\("""))
            if (resolveDexMatch != null) {
                val start = clean.indexOfCode('{', resolveDexMatch.range.last)
                if (start != -1) {
                    val end = clean.findBlockEnd(start)
                    if (end != -1) {
                        blocks.add(clean.substring(start, end + 1))
                    }
                }
            }

            // 2. Extract inline search blocks if they exist (by dexClass, by dexMethod, by dexConstructor)
            val inlineKeywordRegex = Regex("""\bby\s+dex(?:Class|Method|Constructor)\b""")
            val separatorRegex = Regex("""\b(val|fun|private|public|internal|class|object|override)\b""")

            clean.findAllCode(inlineKeywordRegex).forEach { match ->
                val startScan = match.range.last + 1
                val nextOpenBrace = clean.indexOfCode('{', startScan)
                if (nextOpenBrace != -1) {
                    if (!clean.containsCodeMatch(separatorRegex, startScan, nextOpenBrace)) {
                        val end = clean.findBlockEnd(nextOpenBrace)
                        if (end != -1) {
                            blocks.add(clean.substring(nextOpenBrace, end + 1))
                        }
                    }
                }
            }

            // Guardrail check only applies to actual implementations now
            if (blocks.isEmpty()) {
                error("Class $fullClassName implements IResolveDex but has neither a resolveDex() body nor any inline dex blocks.")
            }

            val combinedBody = blocks.joinToString(separator = "\n")
            val hash = MessageDigest.getInstance("MD5").digest(combinedBody.toByteArray()).joinToString("") { "%02x".format(it) }
            hashMap[fullClassName] = hash
        }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package ${namespace.get()}.dexkit.cache

            object GeneratedMethodHashes {
                val HASHES = mapOf(${hashMap.entries.sortedBy { it.key }.joinToString(", \n") { "\"${it.key}\" to \"${it.value}\"" }})
            }
        """.trimIndent()
        )
    }
}

/**
 * Comment-free Kotlin source plus a parallel mask telling which characters are real code, i.e.
 * sit outside every string/char literal. All lookups below consult the mask, so a `{`, `}` or
 * keyword that only appears inside a literal is never mistaken for syntax.
 */
private class ScannedSource(val text: String, private val codeMask: BooleanArray) {
    val length: Int get() = text.length

    fun substring(startIndex: Int, endIndex: Int): String = text.substring(startIndex, endIndex)

    /** Index of the next [char] that is real code at or after [startIndex], or -1. */
    fun indexOfCode(char: Char, startIndex: Int): Int {
        for (i in startIndex.coerceAtLeast(0) until text.length) {
            if (text[i] == char && codeMask[i]) return i
        }
        return -1
    }

    /** Index of the `}` closing the block opened at [openBraceIndex], or -1 when unbalanced. */
    fun findBlockEnd(openBraceIndex: Int): Int {
        var depth = 0
        for (i in openBraceIndex until text.length) {
            if (!codeMask[i]) continue
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return -1
    }

    /** First [regex] match that starts at a code position. */
    fun findCode(regex: Regex): MatchResult? = regex.findAll(text).firstOrNull { codeMask[it.range.first] }

    /** All [regex] matches that start at a code position, in source order. */
    fun findAllCode(regex: Regex): List<MatchResult> =
        regex.findAll(text).filter { codeMask[it.range.first] }.toList()

    /** Whether [regex] matches real code somewhere in `[startIndex, endIndex)`. */
    fun containsCodeMatch(regex: Regex, startIndex: Int, endIndex: Int): Boolean =
        regex.findAll(text, startIndex.coerceIn(0, text.length))
            .takeWhile { it.range.first < endIndex }
            .any { codeMask[it.range.first] }
}

/** Lexer state of [stripCommentsPreservingStrings]. */
private sealed class LexContext {
    /** Real code — the file body itself, or the inside of a `${...}` template interpolation. */
    class Code(val isTemplate: Boolean) : LexContext() {
        var braceDepth = 0
    }

    object NormalString : LexContext()
    object RawString : LexContext()
    object CharLiteral : LexContext()
}

/**
 * Strip Kotlin comments from [source] while keeping every string literal byte-identical, and
 * record which characters of the result are real code.
 *
 * Regexes cannot do this, and getting it wrong is silent:
 * - `Regex("//[^\n]*")` eats from a `//` that lives *inside* a string literal to end of line, so
 *   e.g. `usingEqStrings("weixin://voip/callagain/?username=")` never reaches the hash. Editing
 *   the query then yields an identical MD5, `DexCacheManager.isItemCacheValid` returns true, and
 *   the hook silently installs on the stale, wrong method.
 * - counting bare `{`/`}` characters trips over braces inside literals, e.g.
 *   `usingStrings("ILinkMember{memberId=")`, so the extracted "block" runs far past its real end
 *   and swallows unrelated declarations (spurious re-resolves on unrelated edits).
 *
 * A single pass tracks lexer state instead: normal `"..."` strings (with `\` escapes), raw
 * `"""..."""` strings (no escapes; a run of >3 quotes ends with its last three), char literals,
 * line comments and block comments (which nest in Kotlin). `${...}` interpolations are re-entered
 * as code, so braces inside them are matched normally.
 *
 * Recovery: an unterminated single-quoted string or char literal is closed at the newline, which
 * keeps a malformed file from poisoning the rest of the scan.
 */
private fun stripCommentsPreservingStrings(source: String): ScannedSource {
    val text = StringBuilder(source.length)
    val codeMask = BooleanArray(source.length)

    fun emit(char: Char, isCode: Boolean) {
        codeMask[text.length] = isCode
        text.append(char)
    }

    val stack = mutableListOf<LexContext>(LexContext.Code(isTemplate = false))
    fun pop() = stack.removeAt(stack.size - 1)

    var i = 0
    while (i < source.length) {
        val char = source[i]
        when (val context = stack.last()) {
            is LexContext.Code -> when {
                source.startsWith("//", i) -> {
                    // Line comment: drop it, but keep the terminating newline.
                    while (i < source.length && source[i] != '\n') i++
                }

                source.startsWith("/*", i) -> {
                    // Block comments (and KDOC) nest in Kotlin, so count them.
                    var depth = 0
                    while (i < source.length) {
                        if (source.startsWith("/*", i)) {
                            depth++
                            i += 2
                        } else if (source.startsWith("*/", i)) {
                            depth--
                            i += 2
                            if (depth == 0) break
                        } else {
                            i++
                        }
                    }
                }

                source.startsWith("\"\"\"", i) -> {
                    repeat(3) { emit(source[i + it], false) }
                    i += 3
                    stack.add(LexContext.RawString)
                }

                char == '"' -> {
                    emit(char, false)
                    i++
                    stack.add(LexContext.NormalString)
                }

                char == '\'' -> {
                    emit(char, false)
                    i++
                    stack.add(LexContext.CharLiteral)
                }

                char == '{' -> {
                    context.braceDepth++
                    emit(char, true)
                    i++
                }

                char == '}' -> {
                    if (context.isTemplate && context.braceDepth == 0) {
                        // Closes the interpolation itself, not a code block.
                        pop()
                        emit(char, false)
                    } else {
                        context.braceDepth--
                        emit(char, true)
                    }
                    i++
                }

                else -> {
                    emit(char, true)
                    i++
                }
            }

            LexContext.NormalString -> when {
                char == '\\' && i + 1 < source.length -> {
                    emit(char, false)
                    emit(source[i + 1], false)
                    i += 2
                }

                char == '"' -> {
                    emit(char, false)
                    i++
                    pop()
                }

                char == '$' && i + 1 < source.length && source[i + 1] == '{' -> {
                    emit(char, false)
                    emit('{', false)
                    i += 2
                    stack.add(LexContext.Code(isTemplate = true))
                }

                char == '\n' -> { // unterminated literal — recover at the line break
                    emit(char, false)
                    i++
                    pop()
                }

                else -> {
                    emit(char, false)
                    i++
                }
            }

            LexContext.RawString -> when {
                source.startsWith("\"\"\"", i) -> {
                    // In a run of quotes the LAST three terminate; any extras are content.
                    var run = 0
                    while (i + run < source.length && source[i + run] == '"') run++
                    repeat(run) { emit(source[i + it], false) }
                    i += run
                    pop()
                }

                char == '$' && i + 1 < source.length && source[i + 1] == '{' -> {
                    emit(char, false)
                    emit('{', false)
                    i += 2
                    stack.add(LexContext.Code(isTemplate = true))
                }

                else -> {
                    emit(char, false)
                    i++
                }
            }

            LexContext.CharLiteral -> when {
                char == '\\' && i + 1 < source.length -> {
                    emit(char, false)
                    emit(source[i + 1], false)
                    i += 2
                }

                char == '\'' || char == '\n' -> {
                    emit(char, false)
                    i++
                    pop()
                }

                else -> {
                    emit(char, false)
                    i++
                }
            }
        }
    }

    return ScannedSource(text.toString(), codeMask.copyOf(text.length))
}

package dev.lector.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Convierte en prueba las reglas "que nunca se rompen" de CLAUDE.md y PLAN §4–§5. */
class ProjectRulesTest {
    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CLAUDE.md").isFile }
    private val main = File(repoRoot, "app/src/main")
    private val sources: List<File> = File(main, "java").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun code(file: File): String = file.readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines().joinToString("\n") { line -> line.substringBefore("//").let { if (line.trimStart().startsWith("//")) "" else line } }

    private fun offenders(pattern: Regex, include: (File) -> Boolean = { true }): List<String> =
        sources.filter(include).filter { pattern.containsMatchIn(code(it)) }.map { it.relativeTo(main).path }

    private fun inUi(file: File) = file.path.contains("/ui/")
    private fun inScreens(file: File) = inUi(file) && !file.path.contains("/ui/common/") && !file.path.contains("/ui/theme/")

    @Test fun noNotNullAssertions() = assertEquals(emptyList<String>(), offenders(Regex("""[A-Za-z0-9_)\]]!!""")))

    @Test fun noRunBlockingOrGlobalScope() = assertEquals(emptyList<String>(), offenders(Regex("""\brunBlocking\b|\bGlobalScope\b""")))

    @Test fun noJavascriptInterface() = assertEquals(emptyList<String>(), offenders(Regex("""addJavascriptInterface""")))

    @Test fun webViewsAreCreatedOnlyInTheSecureFactory() =
        assertEquals(listOf("dev/lector/app/data/office/SecureWebViewFactory.kt"), offenders(Regex("""(?<![A-Za-z.])WebView\("""))
            .map { it.removePrefix("java/") })

    @Test fun pdfRendererIsUsedOnlyInsideTheFrameworkEngine() =
        assertEquals(listOf("data/pdf/FrameworkPdfEngine.kt"), offenders(Regex("""\bPdfRenderer\b""")) { it.name != "AppDispatchers.kt" }
            .map { it.removePrefix("java/dev/lector/app/") })

    @Test fun noLateinitInViewModels() =
        assertEquals(emptyList<String>(), offenders(Regex("""\blateinit\b""")) { it.name.endsWith("ViewModel.kt") })

    @Test fun noPendingIntents() = assertEquals(emptyList<String>(), offenders(Regex("""\bPendingIntent\b""")))

    @Test fun screensUseOnlyThemeTokensForColorsAndSizes() {
        assertEquals("colores literales en pantallas", emptyList<String>(), offenders(Regex("""Color\(0x|Color\.(White|Black|Red|Green|Blue|Gray)\b|Color\(red"""), ::inScreens))
        assertEquals("tamaños literales en pantallas", emptyList<String>(), offenders(Regex("""\b\d+(\.\d+)?\.(dp|sp)\b"""), ::inScreens))
        assertEquals("radios literales en pantallas", emptyList<String>(), offenders(Regex("""RoundedCornerShape\("""), ::inScreens))
    }

    @Test fun manifestDeclaresNoPermissionsAndOneExportedActivity() {
        val manifest = File(main, "AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val permissions = Regex("""<uses-permission[^>]*>""", RegexOption.DOT_MATCHES_ALL).findAll(manifest).map { it.value }.toList()
        assertTrue("todo uses-permission debe llevar tools:node=\"remove\": $permissions", permissions.all { it.contains("tools:node=\"remove\"") })
        assertTrue(permissions.any { it.contains("android.permission.INTERNET") })
        assertEquals(1, Regex("""android:exported="true"""").findAll(manifest).count())
        assertTrue(manifest.contains("android:allowBackup=\"false\"") && manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:scheme=\"content\"") && !manifest.contains("android:scheme=\"file\""))
    }

    @Test fun cspInIndexHtmlMatchesTheHeaderTheInterceptorSends() {
        val html = File(main, "assets/viewer/index.html").readText()
        val meta = Regex("""http-equiv="Content-Security-Policy" content="([^"]+)"""").find(html)!!.groupValues[1]
        assertEquals(dev.lector.app.data.office.ViewerRequestInterceptor.CSP, meta)
    }

    @Test fun viewerScriptsHaveNoEvalNorInlineScriptsNorExternalUrls() {
        val viewer = File(main, "assets/viewer")
        val own = listOf("viewer.js", "index.html", "viewer.css").map { name ->
            File(viewer, name).readText()
                .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                .lines().joinToString("\n") { it.replace(Regex("""^\s*//.*$"""), "") }
        }
        own.forEach { text ->
            assertTrue(!Regex("""\beval\s*\(|new Function\(|innerHTML""").containsMatchIn(text))
            assertTrue(!Regex("""https?://(?!appassets)""").containsMatchIn(text.replace(Regex("""\bhttps?://www\.w3\.org[^\s"']*"""), "")))
        }
        val html = own[1]
        assertEquals("index.html no debe llevar scripts en línea", 0, Regex("""<script(?![^>]*\bsrc=)[^>]*>""").findAll(html).count())
    }

    @Test fun releaseIsMinifiedAndKeysStayOutOfGit() {
        val gradle = File(repoRoot, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("isMinifyEnabled = true") && gradle.contains("isShrinkResources = true") && gradle.contains("isDebuggable = false"))
        assertTrue(gradle.contains("abortOnError = true") && gradle.contains("warningsAsErrors = true"))
        val ignore = File(repoRoot, ".gitignore").readText()
        listOf("*.jks", "*.keystore", "keystore.properties", "local.properties").forEach { assertTrue("$it en .gitignore", ignore.contains(it)) }
        assertTrue(File(repoRoot, "gradle/verification-metadata.xml").isFile)
    }
}

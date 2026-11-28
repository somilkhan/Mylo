package com.music.bitchord

import com.music.bitchord.ui.components.SUPPORTED_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every offered language must translate the base catalogue, including scrobble
 * resources split into their own files. Missing keys silently fall back to
 * English, so completed translations must not stay on an exemption list.
 */
@RunWith(Parameterized::class)
class LocaleStringsTest(private val tag: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" }

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/main/res").exists()) {
                File(projectRoot, "app/src/main/res")
            } else {
                File(projectRoot, "src/main/res")
            }
        }

        private fun parse(file: File): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

        private fun resourceFiles(qualifier: String): List<File> =
            File(resDir, qualifier).listFiles { file -> file.extension == "xml" }
                ?.sortedBy { it.name }.orEmpty()

        /** Keys that need translating: translatable="false" entries are excluded. */
        private fun translatableKeys(doc: Document): Set<String> {
            val keys = mutableSetOf<String>()
            for (tagName in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    val name = element.getAttribute("name")
                    if (name.isNotEmpty()) keys.add(name)
                }
            }
            return keys
        }

        // Unrelated attribution copy is still awaiting translation. Scrobble
        // keys are intentionally not exempt: their locale coverage is complete.
        internal val AWAITING_TRANSLATION = setOf("about_gpl_fork_credit")

        private val baseKeys: Set<String> by lazy {
            resourceFiles("values")
                .filter { it.name == "strings.xml" || it.name.startsWith("scrobble_") }
                .flatMap { translatableKeys(parse(it)) }
                .toSet()
        }

        private fun scrobbleStrings(qualifier: String): Map<String, String> = buildMap {
            for (file in resourceFiles(qualifier)) {
                val nodes = parse(file).getElementsByTagName("string")
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    val key = element.getAttribute("name")
                    if (key.startsWith("scrobble_") && element.getAttribute("translatable") != "false") {
                        assertTrue("Duplicate string $key in $qualifier", !containsKey(key))
                        put(key, element.textContent)
                    }
                }
            }
        }

        private val formatToken = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]")

        /** Match BCP-47 app language tags to Android resource qualifiers. */
        internal fun tagToQualifier(tag: String): String = when (tag) {
            "zh-CN" -> "zh"
            "zh-TW" -> "zh-rTW"
            else -> tag
        }
    }

    @Test
    fun strings_containsAllBaseKeys() {
        val qualifier = "values-${tagToQualifier(tag)}"
        assertTrue("$qualifier/strings.xml must exist (language $tag)", File(resDir, "$qualifier/strings.xml").exists())
        val localeKeys = resourceFiles(qualifier).flatMap { translatableKeys(parse(it)) }.toSet()
        val missingKeys = baseKeys - localeKeys - AWAITING_TRANSLATION
        assertTrue("$qualifier is missing ${missingKeys.size} base keys: $missingKeys", missingKeys.isEmpty())
    }

    @Test
    fun scrobble_translationsPreserveFormatArguments() {
        val base = scrobbleStrings("values")
        val qualifier = "values-${tagToQualifier(tag)}"
        val translated = scrobbleStrings(qualifier)
        assertTrue("$qualifier is missing scrobble keys: ${base.keys - translated.keys}", translated.keys.containsAll(base.keys))
        for ((key, original) in base) {
            val localized = translated.getValue(key)
            assertTrue("$qualifier/$key must not be blank", localized.isNotBlank())
            assertEquals(
                "$qualifier/$key must preserve printf argument numbers and types",
                formatToken.findAll(original).map { it.value }.sorted().toList(),
                formatToken.findAll(localized).map { it.value }.sorted().toList(),
            )
        }
    }
}

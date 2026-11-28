package com.music.bitchord

import com.music.bitchord.ui.components.SUPPORTED_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every offered language and the Traditional Chinese fallback catalogues must
 * translate the base catalogue, including resources split into their own files.
 * Missing keys silently fall back to English; scrobble keys are never exempt.
 */
@RunWith(Parameterized::class)
class LocaleStringsTest(private val tag: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = (
            SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" } +
                listOf("zh-HK", "b+zh+Hant")
            ).distinct()

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/main/res").exists()) {
                File(projectRoot, "app/src/main/res")
            } else {
                File(projectRoot, "src/main/res")
            }
        }

        private data class ResourceKey(val type: String, val name: String) {
            override fun toString(): String = "$type/$name"
        }

        private fun resourceFiles(qualifier: String): List<File> =
            File(resDir, qualifier).listFiles { file -> file.extension == "xml" }
                ?.sortedBy { it.name }.orEmpty()

        /** Android arrays share one resource type, regardless of the XML tag. */
        private fun resourceType(element: Element): String? = when (
            val type = if (element.tagName == "item") element.getAttribute("type") else element.tagName
        ) {
            "string", "plurals" -> type
            "string-array", "integer-array", "array" -> "array"
            else -> null
        }

        private fun resources(qualifier: String): Map<ResourceKey, Element> = buildMap {
            val origins = mutableMapOf<ResourceKey, String>()
            for (file in resourceFiles(qualifier)) {
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val nodes = doc.documentElement.childNodes
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as? Element ?: continue
                    val type = resourceType(element) ?: continue
                    val name = element.getAttribute("name")
                    if (name.isEmpty()) continue
                    val key = ResourceKey(type, name)
                    assertTrue(
                        "Duplicate $key in $qualifier: ${origins[key]} and ${file.name}",
                        !containsKey(key),
                    )
                    origins[key] = file.name
                    put(key, element)
                }
            }
        }

        // Existing, narrowly scoped attribution exception; no scrobble exemption.
        internal val AWAITING_TRANSLATION = setOf("about_gpl_fork_credit")

        private val baseResources: Map<ResourceKey, Element> by lazy { resources("values") }

        /** Collect every translatable base string/plural/string-array across XML files. */
        private val baseKeys: Set<ResourceKey> by lazy {
            baseResources.filter { (key, element) ->
                element.getAttribute("translatable") != "false" &&
                    (key.type != "array" || element.tagName == "string-array") &&
                    !(key.type == "string" && key.name in AWAITING_TRANSLATION)
            }.keys
        }

        private fun scrobbleResources(catalogue: Map<ResourceKey, Element>): Map<ResourceKey, Element> =
            catalogue.filterKeys { it.name.startsWith("scrobble_") && it.type in setOf("string", "plurals") }

        private val formatToken = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?(?:[tT][a-zA-Z]|[a-zA-Z%])")

        private fun assertFormatArguments(context: String, original: String, localized: String) {
            assertTrue("$context base text must not be blank", original.isNotBlank())
            assertTrue("$context must not be blank", localized.isNotBlank())
            assertEquals(
                "$context must preserve printf argument numbers and types",
                formatToken.findAll(original).map { it.value }.sorted().toList(),
                formatToken.findAll(localized).map { it.value }.sorted().toList(),
            )
        }

        private fun pluralItems(element: Element, context: String): Map<String, String> = buildMap {
            val nodes = element.getElementsByTagName("item")
            for (i in 0 until nodes.length) {
                val item = nodes.item(i) as Element
                val quantity = item.getAttribute("quantity")
                assertTrue(
                    "$context has invalid plural quantity '$quantity'",
                    quantity in setOf("zero", "one", "two", "few", "many", "other"),
                )
                assertTrue("$context has duplicate quantity '$quantity'", !containsKey(quantity))
                assertTrue("$context[$quantity] must not be blank", item.textContent.isNotBlank())
                put(quantity, item.textContent)
            }
            assertTrue("$context must define plural quantity 'other'", containsKey("other"))
        }

        /** Match BCP-47 app language tags without losing the script-qualified folder. */
        internal fun tagToQualifier(tag: String): String = when (tag) {
            "zh-CN" -> "zh"
            "zh-TW" -> "zh-rTW"
            "zh-HK" -> "zh-rHK"
            else -> tag
        }
    }

    private val qualifier = "values-${tagToQualifier(tag)}"
    private val localeResources: Map<ResourceKey, Element> by lazy { resources(qualifier) }

    @Test
    fun resources_haveNoDuplicateTypeAndName() {
        assertTrue("Base resource catalogue must exist", baseResources.isNotEmpty())
        assertTrue("$qualifier resource catalogue must exist (language $tag)", localeResources.isNotEmpty())
    }

    @Test
    fun strings_containsAllBaseKeys() {
        assertTrue("$qualifier resource catalogue must exist (language $tag)", localeResources.isNotEmpty())
        val localeKeys = localeResources.filterValues { it.getAttribute("translatable") != "false" }.keys
        val missingKeys = baseKeys - localeKeys
        assertTrue("$qualifier is missing ${missingKeys.size} base keys: $missingKeys", missingKeys.isEmpty())
    }

    @Test
    fun scrobble_translationsPreserveFormatArguments() {
        val base = scrobbleResources(baseResources)
        val translated = scrobbleResources(localeResources)
        assertTrue(
            "$qualifier is missing scrobble keys: ${base.keys - translated.keys}",
            translated.keys.containsAll(base.keys),
        )
        for ((key, original) in base) {
            val localized = translated.getValue(key)
            val context = "$qualifier/$key"
            assertTrue("$context must remain translatable", localized.getAttribute("translatable") != "false")
            if (key.type == "string") {
                assertFormatArguments(context, original.textContent, localized.textContent)
            } else {
                val baseItems = pluralItems(original, "values/$key")
                val localizedItems = pluralItems(localized, context)
                for ((quantity, text) in localizedItems) {
                    // Languages may add/remove grammatical quantities; extra forms
                    // must retain the base 'other' arguments, not English categories.
                    val baseText = baseItems[quantity] ?: baseItems.getValue("other")
                    assertFormatArguments("$context[$quantity]", baseText, text)
                }
            }
        }
    }
}

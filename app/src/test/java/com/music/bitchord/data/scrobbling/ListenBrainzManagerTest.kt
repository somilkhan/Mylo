package com.music.bitchord.data.scrobbling

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenBrainzManagerTest {

    @Test
    fun escapedStringsParseBackToTheSameText() {
        val tricky = "quote\" back\\slash tab\t cr\r lf\n bell\u0007 nul\u0000 ff\u000C bs\b unicode ünï ✓"
        val wrapped = "{\"v\":\"${ListenBrainzManager.escapeJson(tricky)}\"}"

        val parsed = Json.parseToJsonElement(wrapped).jsonObject["v"]!!.jsonPrimitive.content

        assertEquals(tricky, parsed)
    }

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("Hello World", ListenBrainzManager.escapeJson("Hello World"))
    }
}

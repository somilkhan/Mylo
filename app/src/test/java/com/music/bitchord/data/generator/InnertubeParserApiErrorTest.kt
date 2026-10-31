package com.music.bitchord.data.generator

import com.music.bitchord.data.innertube.InnertubeParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class InnertubeParserApiErrorTest {
    @Test
    fun homeApiAuthenticationErrorIsNotParsedAsAnEmptyFeed() {
        val response = Json.parseToJsonElement(
            """{"error":{"code":401,"status":"UNAUTHENTICATED","message":"Invalid credentials"}}""",
        ).let { it as kotlinx.serialization.json.JsonObject }

        val failure = runCatching { InnertubeParser.parseHome(response) }.exceptionOrNull()

        assertTrue("API errors must remain failures instead of empty shelves", failure is IllegalStateException)
        assertTrue(failure!!.message.orEmpty().contains("UNAUTHENTICATED"))
        assertTrue(failure.message.orEmpty().contains("401"))
    }

    @Test
    fun homeApiLoginRequiredStatusIsRecognizableAsAuthenticationFailure() {
        val response = Json.parseToJsonElement(
            """{"error":{"code":400,"status":"LOGIN_REQUIRED","message":"Sign in to continue"}}""",
        ).let { it as kotlinx.serialization.json.JsonObject }

        val failure = runCatching { InnertubeParser.parseHome(response) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.isYouTubeAuthenticationFailure())
    }
}

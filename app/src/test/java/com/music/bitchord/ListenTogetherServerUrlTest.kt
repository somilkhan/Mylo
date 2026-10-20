package com.music.bitchord

import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.ServerUrlError
import com.music.bitchord.data.listentogether.ServerUrlValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenTogetherServerUrlTest {

    @Test
    fun `blank server selects the build default`() {
        assertEquals(
            ServerUrlValidationResult.Valid(""),
            ListenTogether.parseAndNormalizeServerUrl("   "),
        )
    }

    @Test
    fun `hostname without scheme defaults to HTTPS and normalizes host casing`() {
        assertEquals(
            ServerUrlValidationResult.Valid("https://party.example.com"),
            ListenTogether.parseAndNormalizeServerUrl(" PARTY.Example.COM "),
        )
    }

    @Test
    fun `localhost HTTP endpoint and reverse proxy path are preserved`() {
        assertEquals(
            ServerUrlValidationResult.Valid("http://localhost:8080/jam"),
            ListenTogether.parseAndNormalizeServerUrl("http://LOCALHOST:8080/jam"),
        )
    }

    @Test
    fun `non HTTP schemes are rejected`() {
        assertEquals(
            ServerUrlValidationResult.Invalid(ServerUrlError.InvalidScheme),
            ListenTogether.parseAndNormalizeServerUrl("ftp://party.example.com"),
        )
    }

    @Test
    fun `query and fragment are rejected`() {
        assertEquals(
            ServerUrlValidationResult.Invalid(ServerUrlError.HasQuery),
            ListenTogether.parseAndNormalizeServerUrl("https://party.example.com?token=secret"),
        )
        assertEquals(
            ServerUrlValidationResult.Invalid(ServerUrlError.HasFragment),
            ListenTogether.parseAndNormalizeServerUrl("https://party.example.com#section"),
        )
    }

    @Test
    fun `invalid host port whitespace and dot segments are rejected`() {
        assertEquals(
            ServerUrlValidationResult.Invalid(ServerUrlError.InvalidHost),
            ListenTogether.parseAndNormalizeServerUrl("party"),
        )
        assertEquals(
            ServerUrlValidationResult.Invalid(ServerUrlError.InvalidPort),
            ListenTogether.parseAndNormalizeServerUrl("https://party.example.com:70000"),
        )
        assertEquals(
            ServerUrlValidationResult.Invalid(ServerUrlError.Whitespace),
            ListenTogether.parseAndNormalizeServerUrl("https://party example.com"),
        )
        assertEquals(
            ServerUrlValidationResult.Invalid(ServerUrlError.InvalidPath),
            ListenTogether.parseAndNormalizeServerUrl("https://party.example.com/../api"),
        )
    }
}

package com.hazia.medicall.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CryptoTest {

    private val key = ByteArray(32) { it.toByte() }
    private val cipher = LineCipher(mapOf("k1" to key), "k1")

    @Test
    fun pbkdf2MatchesRfc7914Vector() {
        // RFC 7914, section 11: PBKDF2-HMAC-SHA256, P = "passwd", S = "salt", c = 1, dkLen = 64.
        val expected = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
            "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
        val derived = KeyDerivation.deriveBytes("passwd".toCharArray(), "salt".encodeToByteArray(), iterations = 1, length = 64)
        assertEquals(expected, derived.toHex())
        assertEquals(32, KeyDerivation.derive("passwd".toCharArray(), "salt".encodeToByteArray(), iterations = 1).size)
    }

    @Test
    fun sealedLineRoundTrips() {
        val plain = "{\"name\":\"Profile A\"}".encodeToByteArray()
        assertContentEquals(plain, cipher.open(cipher.seal(plain)))
    }

    @Test
    fun sameInputSealsDifferently() {
        val plain = "same".encodeToByteArray()
        assertNotEquals(cipher.seal(plain), cipher.seal(plain))
    }

    @Test
    fun tamperedLineOpensToNull() {
        val line = cipher.seal("Profile A".encodeToByteArray())
        val i = line.length - 5
        val flipped = line.substring(0, i) + (if (line[i] == 'A') 'B' else 'A') + line.substring(i + 1)
        assertNull(cipher.open(flipped))
        assertNull(cipher.open("k1.not-base64!"))
        assertNull(cipher.open("no dot at all"))
    }

    @Test
    fun unknownKeyIdOpensToNull() {
        val other = LineCipher(mapOf("k2" to key), "k2")
        assertNull(cipher.open(other.seal("x".encodeToByteArray())))
    }

    // SPEC:R16 every line in the sync folder is encrypted; the key id is bound into the tag.
    @Test
    fun relabelledLineOpensToNull_SPEC_R16() {
        val twoIds = LineCipher(mapOf("k1" to key, "k2" to key), "k1")
        val line = twoIds.seal("Profile A".encodeToByteArray())
        assertNull(twoIds.open("k2" + line.removePrefix("k1")), "same key bytes, another id: the tag refuses it")
    }

    @Test
    fun sealedLineHasNoNewline() {
        val line = cipher.seal(ByteArray(10_000) { 10 })
        assertFalse('\n' in line || '\r' in line)
        assertEquals("k1.", line.take(3))
    }

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

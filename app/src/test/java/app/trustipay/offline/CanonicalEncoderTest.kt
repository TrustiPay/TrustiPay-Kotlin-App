package app.trustipay.offline

import app.trustipay.offline.protocol.CanonicalEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalEncoderTest {
    @Test
    fun encode_ordersMapKeysDeterministically() {
        val first = linkedMapOf(
            "z" to 3,
            "a" to "one",
            "nested" to mapOf("b" to true, "a" to false),
        )
        val second = linkedMapOf(
            "nested" to mapOf("a" to false, "b" to true),
            "a" to "one",
            "z" to 3,
        )

        assertEquals(
            CanonicalEncoder.encodeToString(first),
            CanonicalEncoder.encodeToString(second),
        )
        assertEquals(
            """{"a":"one","nested":{"a":false,"b":true},"z":3}""",
            CanonicalEncoder.encodeToString(first),
        )
    }
}

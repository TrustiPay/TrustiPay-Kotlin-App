package app.trustipay.offline.protocol

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

object CanonicalEncoder {
    fun encode(value: Any?): ByteArray = encodeToString(value).toByteArray(StandardCharsets.UTF_8)

    fun encodeToString(value: Any?): String = buildString {
        appendCanonical(value)
    }

    private fun StringBuilder.appendCanonical(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendQuoted(value)
            is Boolean -> append(if (value) "true" else "false")
            is Byte, is Short, is Int, is Long -> append(value.toString())
            is Instant -> appendQuoted(value.toString())
            is Enum<*> -> appendQuoted(value.name)
            is ByteArray -> appendQuoted(Base64.getUrlEncoder().withoutPadding().encodeToString(value))
            is Map<*, *> -> appendMap(value)
            is Iterable<*> -> appendIterable(value)
            else -> error("Unsupported canonical value type: ${value::class.qualifiedName}")
        }
    }

    private fun StringBuilder.appendMap(value: Map<*, *>) {
        append('{')
        value.entries
            .map { (key, entryValue) ->
                require(key is String) { "Canonical map keys must be strings." }
                key to entryValue
            }
            .sortedBy { it.first }
            .forEachIndexed { index, (key, entryValue) ->
                if (index > 0) append(',')
                appendQuoted(key)
                append(':')
                appendCanonical(entryValue)
            }
        append('}')
    }

    private fun StringBuilder.appendIterable(value: Iterable<*>) {
        append('[')
        value.forEachIndexed { index, item ->
            if (index > 0) append(',')
            appendCanonical(item)
        }
        append(']')
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}

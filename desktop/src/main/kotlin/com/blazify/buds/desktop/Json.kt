package com.blazify.buds.desktop

/**
 * Just enough JSON for the command line and Home Assistant: writing maps, lists, strings, numbers and booleans, and
 * reading the app's own answers back. Not a general parser; it reads what [write] makes and ordinary JSON like it.
 */
object Json {
    fun write(value: Any?): String = StringBuilder().also { put(it, value) }.toString()

    private fun put(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> string(out, value)
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Double -> out.append(if (value % 1.0 == 0.0) value.toLong().toString() else value.toString())
            is Number -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    string(out, k.toString())
                    out.append(':')
                    put(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, v -> if (i > 0) out.append(','); put(out, v) }
                out.append(']')
            }
            else -> string(out, value.toString())
        }
    }

    private fun string(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
        out.append('"')
    }

    /** Maps come back as Map<String, Any?>, lists as List<Any?>, numbers as Long or Double. */
    fun read(text: String): Any? = Reader(text).run { value().also { space(); require(at == text.length) { "Extra text after JSON" } } }

    private class Reader(val s: String) {
        var at = 0

        fun space() { while (at < s.length && s[at].isWhitespace()) at++ }

        fun value(): Any? {
            space()
            require(at < s.length) { "JSON ended early" }
            return when (val c = s[at]) {
                '{' -> obj()
                '[' -> list()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw IllegalArgumentException("Unexpected '$c' in JSON")
            }
        }

        fun word(w: String, v: Any?): Any? {
            require(s.startsWith(w, at)) { "Bad JSON word" }
            at += w.length
            return v
        }

        fun number(): Any {
            val start = at
            while (at < s.length && (s[at].isDigit() || s[at] in "+-.eE")) at++
            val t = s.substring(start, at)
            return t.toLongOrNull() ?: t.toDouble()
        }

        fun str(): String {
            at++
            val out = StringBuilder()
            while (true) {
                require(at < s.length) { "Unclosed string in JSON" }
                when (val c = s[at++]) {
                    '"' -> return out.toString()
                    '\\' -> when (val e = s[at++]) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'u' -> { out.append(s.substring(at, at + 4).toInt(16).toChar()); at += 4 }
                        else -> out.append(e)
                    }
                    else -> out.append(c)
                }
            }
        }

        fun list(): List<Any?> {
            at++
            val out = mutableListOf<Any?>()
            space()
            if (s[at] == ']') { at++; return out }
            while (true) {
                out += value()
                space()
                when (s[at++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw IllegalArgumentException("Expected , or ] in JSON")
                }
            }
        }

        fun obj(): Map<String, Any?> {
            at++
            val out = linkedMapOf<String, Any?>()
            space()
            if (s[at] == '}') { at++; return out }
            while (true) {
                space()
                val key = str()
                space()
                require(s[at++] == ':') { "Expected : in JSON" }
                out[key] = value()
                space()
                when (s[at++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw IllegalArgumentException("Expected , or } in JSON")
                }
            }
        }
    }
}

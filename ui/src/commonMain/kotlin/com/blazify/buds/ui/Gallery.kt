package com.blazify.buds.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** A sound someone shared for the gallery: its name, a line about it, who made it, and its bands and gains. */
data class SharedSound(val name: String, val about: String, val by: String, val bands: List<Int>, val gains: List<Double>) {
    fun code() = SavedSound(name, gains).code()
}

/**
 * Sounds by others: a list kept in Blazbuds' repository (community/sounds.json), read only when the person opens the
 * gallery. People send theirs through the "Share a sound" issue form.
 */
object Gallery {
    const val URL = "https://raw.githubusercontent.com/$REPO/main/community/sounds.json"
    const val SHARE_FORM = "share-a-sound.md"

    /** The shared sounds, or null when the list can't be reached or read. */
    suspend fun load(): List<SharedSound>? = withContext(Dispatchers.IO) { fetchText(URL)?.let { parse(it) } }

    /** Only the sounds made for these bands, so each plays as its maker heard it. */
    fun fitting(sounds: List<SharedSound>, bands: List<Int>): List<SharedSound> = sounds.filter { it.bands == bands && it.gains.size == bands.size }

    /** The list from its JSON: {"sounds": [{"name", "about", "by", "bands", "gains"}, …]}; null when it isn't that. */
    fun parse(json: String): List<SharedSound>? = runCatching {
        val root = Json(json).value() as? Map<*, *> ?: return null
        val list = root["sounds"] as? List<*> ?: return null
        list.mapNotNull { item ->
            val o = item as? Map<*, *> ?: return@mapNotNull null
            val name = (o["name"] as? String)?.trim()?.take(40)?.ifEmpty { null } ?: return@mapNotNull null
            val bands = (o["bands"] as? List<*>)?.map { (it as? Double)?.toInt() ?: return@mapNotNull null } ?: return@mapNotNull null
            val gains = (o["gains"] as? List<*>)?.map { ((it as? Double) ?: return@mapNotNull null).coerceIn(-12.0, 12.0) } ?: return@mapNotNull null
            if (bands.isEmpty() || bands.size != gains.size) return@mapNotNull null
            SharedSound(name, (o["about"] as? String).orEmpty().take(120), (o["by"] as? String).orEmpty().take(40), bands, gains)
        }
    }.getOrNull()

    /** Just enough JSON for the list: objects, arrays, strings, numbers, true, false and null. */
    private class Json(private val s: String) {
        private var i = 0

        fun value(): Any? {
            space()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) number() else error("unexpected $c")
            }
        }

        private fun space() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun word(w: String, v: Any?): Any? {
            check(s.startsWith(w, i)); i += w.length; return v
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }

        private fun string(): String {
            i++
            val out = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> { out.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> out.append(e)
                    }
                } else out.append(s[i])
                i++
            }
            i++
            return out.toString()
        }

        private fun array(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            space()
            if (s[i] == ']') { i++; return out }
            while (true) {
                out += value()
                space()
                if (s[i] == ',') { i++; continue }
                check(s[i] == ']'); i++
                return out
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            space()
            if (s[i] == '}') { i++; return out }
            while (true) {
                space()
                val key = string()
                space()
                check(s[i] == ':'); i++
                out[key] = value()
                space()
                if (s[i] == ',') { i++; continue }
                check(s[i] == '}'); i++
                return out
            }
        }
    }
}

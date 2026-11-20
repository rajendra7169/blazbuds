package com.blazify.buds.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Where Blazbuds is published, and the page for anything else from its maker. */
const val REPO = "rajendra7169/blazbuds"
const val REPO_URL = "https://github.com/$REPO"
const val PROFILE_URL = "https://github.com/rajendra7169"

/** One published version: its number, what changed, its page, and the files it offers by name. */
data class Release(val version: String, val notes: String, val page: String, val files: Map<String, String>) {
    /** This platform's file: the APK on Android, the .deb on Linux; none for the rest, which get the page. */
    fun fileFor(look: SystemLook): String? = when (look) {
        SystemLook.Android -> files.entries.firstOrNull { it.key.endsWith(".apk") }?.value
        SystemLook.Linux -> files.entries.firstOrNull { it.key.endsWith(".deb") }?.value
        else -> null
    }
}

/** Reads a web page as text; null when offline or anything goes wrong. */
expect suspend fun fetchText(url: String): String?

object Updates {
    /** The newest published version, or null when GitHub can't be reached. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        fetchText("https://api.github.com/repos/$REPO/releases/latest")?.let { json -> runCatching { parse(json) }.getOrNull() }
    }

    /** True when [other] is a later version than [mine]: 1.10.0 is after 1.9.2, and a leading "v" is ignored. */
    fun isNewer(other: String, mine: String = VERSION): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(other)
        val b = parts(mine)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * The few fields of GitHub's answer the app needs, read without a JSON library: the tag, the notes, the page and
     * each file's name and link. Assets come in order, each with its name before its download link.
     */
    fun parse(json: String): Release? {
        val tag = field(json, "tag_name") ?: return null
        val page = field(json, "html_url") ?: "$REPO_URL/releases"
        val notes = field(json, "body").orEmpty()
        val names = strings(json, "name")
        val links = strings(json, "browser_download_url")
        // The release's own name comes first among the names; the assets' names follow in the same order as links.
        val assetNames = names.takeLast(links.size)
        return Release(tag.removePrefix("v"), notes, page, assetNames.zip(links).toMap())
    }

    private fun field(json: String, key: String): String? = strings(json, key).firstOrNull()

    /**
     * Every string value of [key]. The pattern reads runs of plain characters possessively and only steps aside for an
     * escape, so long release notes don't nest the regex engine once per character and overflow the stack.
     */
    private fun strings(json: String, key: String): List<String> =
        Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"([^\"\\\\]*+(?:\\\\.[^\"\\\\]*+)*+)\"").findAll(json).map { unescape(it.groupValues[1]) }.toList()

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { out.append(c); i++; continue }
            when (val n = s[i + 1]) {
                'n' -> out.append('\n')
                'r' -> {}
                't' -> out.append('\t')
                'u' -> {
                    s.substring(i + 2, minOf(s.length, i + 6)).toIntOrNull(16)?.let { out.append(it.toChar()) }
                    i += 4
                }
                else -> out.append(n)
            }
            i += 2
        }
        return out.toString()
    }
}

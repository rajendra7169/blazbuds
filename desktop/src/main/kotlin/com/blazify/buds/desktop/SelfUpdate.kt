package com.blazify.buds.desktop

import com.blazify.buds.ui.Release
import com.blazify.buds.ui.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

/**
 * The new version put in by Blazbuds itself, on a computer where it was installed from the .deb.
 *
 * The package is fetched with its progress shown, then Blazbuds closes and a small script it leaves behind waits for
 * it to be gone and hands the package to the system's own installer, which asks for the password in its usual window.
 * The result is exactly what installing by hand would leave, and Blazbuds opens again afterwards — the old version if
 * the password was refused. What the script says goes in a log beside the download.
 */
object SelfUpdate : UpdateInstaller {
    private val linux = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)

    /** The program that started this copy. */
    private val launcher: String? by lazy {
        System.getProperty("jpackage.app-path") ?: ProcessHandle.current().info().command().orElse(null)
    }

    private val fromDeb: Boolean by lazy {
        linux && launcher?.startsWith("/opt/blazbuds/") == true && listOf("/usr/bin/pkexec", "/bin/pkexec").any { File(it).exists() }
    }

    override fun canInstall(release: Release): Boolean = fromDeb && deb(release) != null

    private fun deb(release: Release) = release.files.entries.firstOrNull { it.key.endsWith(".deb", ignoreCase = true) }

    override suspend fun install(release: Release, progress: (Float?) -> Unit): String {
        val (name, url) = deb(release) ?: return "This version has no package for this computer."
        val file = withContext(Dispatchers.IO) { runCatching { fetch(url, name, release.version, progress) } }
            .getOrElse { return "The download stopped: ${it.message ?: "no reason given"}. Try again." }
        return runCatching { closeAndInstall(file) }
            .exceptionOrNull()?.let { "Couldn't start the installer: ${it.message ?: "no reason given"}." }
            ?: "Couldn't start the installer."
    }

    private fun folder(version: String): File {
        val cache = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "/.cache")
        return File(cache, "blazbuds/updates/$version").apply { mkdirs() }
    }

    /** Fetches the package, carrying on from what is already there after a dropped connection. */
    internal fun fetch(url: String, name: String, version: String, progress: (Float?) -> Unit): File {
        val target = File(folder(version), name)
        val part = File(target.parentFile, "$name.part")
        var last: Exception? = null
        repeat(3) {
            try {
                val have = part.length()
                val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Blazbuds")
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                try {
                    val code = connection.responseCode
                    if (code != 200 && code != 206) error("the server answered $code")
                    val append = code == 206 && have > 0
                    val length = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    val total = length?.let { if (append) it + have else it }
                    connection.inputStream.use { input ->
                        FileOutputStream(part, append).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var got = if (append) have else 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                got += read
                                progress(total?.takeIf { it > 0 }?.let { (got.toFloat() / it).coerceIn(0f, 1f) })
                            }
                        }
                    }
                    if (total != null && part.length() != total) error("it arrived incomplete")
                    target.delete()
                    if (!part.renameTo(target)) error("couldn't put the download in place")
                    return target
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no answer")
    }

    /** Leaves the script behind, starts it in a session of its own so it outlives this one, and closes Blazbuds. */
    private fun closeAndInstall(file: File): Nothing {
        val pid = ProcessHandle.current().pid()
        val app = launcher ?: "/opt/blazbuds/bin/Blazbuds"
        val script = File(file.parentFile, "install.sh")
        script.writeText(
            "#!/bin/sh\n" +
                "while kill -0 $pid 2>/dev/null; do sleep 0.3; done\n" +
                "pkexec env DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-downgrades ${quote(file.absolutePath)}\n" +
                "exec ${quote(app)}\n",
        )
        script.setExecutable(true)
        val shell = if (File("/usr/bin/setsid").exists()) listOf("/usr/bin/setsid", "/bin/sh") else listOf("/bin/sh")
        ProcessBuilder(shell + script.absolutePath)
            .redirectOutput(File(file.parentFile, "install.log"))
            .redirectErrorStream(true)
            .start()
        Thread.sleep(300)
        exitProcess(0)
    }

    private fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"
}

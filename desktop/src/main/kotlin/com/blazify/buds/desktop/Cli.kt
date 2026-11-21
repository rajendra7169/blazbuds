package com.blazify.buds.desktop

import java.io.PrintStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * `blazbuds battery`, `blazbuds mode anc` and the rest: the earbuds from a terminal. Only one program can hold the
 * earbuds' control link, so these ask the running app over its local port instead of opening the earbuds themselves.
 * Runs before any window code, and starts nothing when the app isn't running.
 */
object Cli {
    private val COMMANDS = setOf("status", "battery", "mode", "scene", "scenes", "preset", "presets", "help", "--help", "-h")

    /** Whether these arguments are a command for the terminal rather than options for the app. */
    fun wants(args: Array<String>): Boolean = args.firstOrNull()?.lowercase() in COMMANDS

    const val NOT_RUNNING = 2

    fun run(args: Array<String>, out: PrintStream = System.out, err: PrintStream = System.err, port: Int = ControlPort.number): Int {
        val json = args.any { it == "--json" }
        val words = args.filter { it != "--json" }
        val command = words.first().lowercase()
        val rest = words.drop(1).joinToString(" ").trim()
        val line = when (command) {
            "status", "battery" -> "get state"
            "mode" -> if (rest.isEmpty()) "get state" else {
                if (ModeWords.parse(rest) == null) {
                    err.println("Pick a mode: anc, transparency or normal.")
                    return 1
                }
                "set mode $rest"
            }
            "scene", "scenes" -> if (rest.isEmpty() || command == "scenes") "get state" else "scene $rest"
            "preset", "presets" -> if (rest.isEmpty() || command == "presets") "get state" else "preset $rest"
            else -> {
                out.print(HELP)
                return 0
            }
        }
        val answer = when (val got = ask(line, port)) {
            is Asked.Answer -> got.json
            is Asked.Failed -> {
                err.println(got.text)
                return got.code
            }
        }
        if (answer["ok"] != true) {
            if (json) out.println(Json.write(answer)) else err.println(answer["error"] ?: "Blazbuds couldn't do that.")
            return 1
        }
        if (json) {
            out.println(Json.write(if (command == "battery") batteryPart(answer) else answer))
            return 0
        }
        out.println(
            when (command) {
                "battery" -> if (answer["connected"] == true) batteryText(answer) else "${answer["name"]}  ${linkText(answer)}"
                "mode" -> (answer["mode_label"] as String?) ?: "These earbuds have no listening modes."
                "scene", "scenes" -> if (rest.isNotEmpty() && command == "scene") statusLine(answer) else listText(answer["scenes"], answer["scene"])
                "preset", "presets" -> if (rest.isNotEmpty() && command == "preset") statusLine(answer) else listText(answer["presets"], (answer["preset"] as Map<*, *>?)?.get("id"))
                else -> statusLine(answer)
            },
        )
        return 0
    }

    sealed interface Asked {
        data class Answer(val json: Map<String, Any?>) : Asked
        data class Failed(val text: String, val code: Int) : Asked
    }

    /** Sends one line to the running app and reads its JSON answer. */
    fun ask(line: String, port: Int = ControlPort.number): Asked = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_000)
            socket.soTimeout = 15_000
            socket.getOutputStream().write("$line\n".toByteArray())
            socket.getOutputStream().flush()
            val reader = socket.getInputStream().bufferedReader()
            if (reader.readLine() != ControlPort.HELLO) {
                Asked.Failed("Another program is using Blazbuds' port $port, so Blazbuds can't be reached.", NOT_RUNNING)
            } else {
                val text = reader.readLine()
                if (text == null) {
                    Asked.Failed("The Blazbuds that's running is too old for commands. Update it, then try again.", 1)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    Asked.Answer(Json.read(text) as Map<String, Any?>)
                }
            }
        }
    } catch (_: ConnectException) {
        Asked.Failed("Blazbuds isn't running. Open it (sitting in the tray is enough), then try again.", NOT_RUNNING)
    } catch (_: SocketTimeoutException) {
        Asked.Failed("Blazbuds didn't answer in time. Try again.", 1)
    }

    private fun battery(answer: Map<String, Any?>, which: String): Map<*, *>? = (answer["battery"] as Map<*, *>?)?.get(which) as Map<*, *>?

    private fun level(b: Map<*, *>): String = buildString {
        append("${b["percent"]}%")
        if (b["charging"] == true) append(" charging")
        (b["seen_at"] as Number?)?.let { append(" (${com.blazify.buds.ui.ago(it.toLong())})") }
    }

    /** "L 80%  R 60%  case 100% charging", or the one level a pair gives when that is all there is. */
    fun batteryText(answer: Map<String, Any?>): String {
        val parts = listOfNotNull(
            battery(answer, "left")?.let { "L ${level(it)}" },
            battery(answer, "right")?.let { "R ${level(it)}" },
            battery(answer, "case")?.let { "case ${level(it)}" },
        ).ifEmpty { listOfNotNull(battery(answer, "overall")?.let { "battery ${level(it)}" }) }
        return parts.joinToString("  ").ifEmpty { "battery unknown" }
    }

    private fun linkText(answer: Map<String, Any?>) = when (answer["link"]) {
        "busy" -> "in use by another app"
        "connecting" -> "connecting"
        else -> "not connected"
    }

    /** "Liberty 4 NC  L 80%  R 80%  case 60%  Noise cancelling". */
    fun statusLine(answer: Map<String, Any?>): String {
        val name = answer["name"].toString()
        if (answer["connected"] != true) return "$name  ${linkText(answer)}"
        return listOfNotNull(name, batteryText(answer), answer["mode_label"] as String?).joinToString("  ")
    }

    private fun listText(items: Any?, current: Any?): String {
        val list = (items as List<*>?)?.filterIsInstance<Map<*, *>>().orEmpty()
        if (list.isEmpty()) return "None for these earbuds."
        return list.joinToString("\n") { item ->
            val on = item["id"] == current || item["id"].toString() == current?.toString()
            val id = item["id"].toString()
            val name = item["name"].toString()
            "${if (on) "*" else " "} ${if (id.equals(name, true)) name else "$id  $name"}"
        }
    }

    private fun batteryPart(answer: Map<String, Any?>): Map<String, Any?> = linkedMapOf(
        "name" to answer["name"],
        "connected" to answer["connected"],
    ) + ((answer["battery"] as Map<*, *>?)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap())

    /**
     * Makes `blazbuds` a terminal command on Linux. The package installs the app in /opt, off the PATH, so the
     * installed app links itself into ~/.local/bin when that folder is on the PATH and no other `blazbuds` is.
     * A link it made before is pointed at the app again after a move; nothing else there is touched.
     */
    fun linkIntoPath(
        // Set by the installed app's own launcher only; a run from Gradle has none.
        app: String? = System.getProperty("jpackage.app-path"),
        home: String = System.getProperty("user.home"),
        pathVariable: String = System.getenv("PATH").orEmpty(),
    ) = runCatching {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows")) return@runCatching
        val launcher = app?.let { java.io.File(it) }?.takeIf { it.canExecute() } ?: return@runCatching
        val bin = java.io.File(home, ".local/bin").absoluteFile
        val path = pathVariable.split(java.io.File.pathSeparator).filter { it.isNotBlank() }.map { java.io.File(it).absoluteFile }
        if (!bin.isDirectory || bin !in path) return@runCatching
        val link = bin.toPath().resolve("blazbuds")
        if (java.nio.file.Files.isSymbolicLink(link)) {
            val target = java.nio.file.Files.readSymbolicLink(link)
            if (target == launcher.toPath()) return@runCatching
            if (target.fileName?.toString() != launcher.name) return@runCatching
            java.nio.file.Files.delete(link)
        } else if (java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return@runCatching
        } else if (path.any { it != bin && java.io.File(it, "blazbuds").canExecute() }) {
            return@runCatching
        }
        java.nio.file.Files.createSymbolicLink(link, launcher.toPath())
    }

    val HELP = """
        |Blazbuds from the terminal. The app has to be running; sitting in the tray is enough.
        |
        |  blazbuds status [--json]          name, batteries and listening mode
        |  blazbuds battery [--json]         left, right and case battery
        |  blazbuds mode [anc|transparency|normal]
        |                                    show the listening mode, or change it
        |  blazbuds scene [<name>]           list the scenes, or put one on (focus, commute, flight, talk, relax)
        |  blazbuds preset [<name or number>]
        |                                    list the sound presets, or pick one
        |
        |Exit codes: 0 done, 1 not done (the reason is printed), 2 Blazbuds isn't running.
        |
    """.trimMargin()
}

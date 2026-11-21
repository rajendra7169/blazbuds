package com.blazify.buds.desktop

import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The command line against the app's real port handling, with demo earbuds in place of real ones. */
class CliTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var buds = DemoBuds()
    private val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
    private var shown = 0

    init {
        val control = Control({ buds }, scope)
        Thread {
            while (!server.isClosed) {
                val peer = runCatching { server.accept() }.getOrNull() ?: continue
                Thread { peer.use { ControlPort.serve(it, control) { shown++ } } }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    @AfterTest
    fun close() {
        server.close()
        scope.cancel()
    }

    private fun cli(vararg args: String): Triple<Int, String, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = Cli.run(arrayOf(*args), PrintStream(out), PrintStream(err), server.localPort)
        return Triple(code, out.toString().trim(), err.toString().trim())
    }

    @Test
    fun statusReadsLikeASentence() {
        val (code, out) = cli("status")
        assertEquals(0, code)
        assertEquals("Liberty 4 NC  L 80%  R 60%  case 100% charging  Noise cancelling", out)
    }

    @Test
    fun batteryAsJson() {
        val (code, out) = cli("battery", "--json")
        assertEquals(0, code)
        val json = Json.read(out) as Map<*, *>
        assertEquals("Liberty 4 NC", json["name"])
        assertEquals(80L, (json["left"] as Map<*, *>)["percent"])
        assertEquals(true, (json["case"] as Map<*, *>)["charging"])
    }

    @Test
    fun modeShowsAndChanges() {
        assertEquals("Noise cancelling", cli("mode").second)
        val (code, out) = cli("mode", "transparency")
        assertEquals(0, code)
        assertEquals("Transparency", out)
        assertEquals(ListeningMode.Transparency, buds.state.value.sound?.mode)
        assertEquals(1, cli("mode", "loud").first)
    }

    @Test
    fun scenesListAndApply() {
        val list = cli("scene").second
        assertTrue(list.lines().first() == "* Focus", list)
        val (code, out) = cli("scene", "talk")
        assertEquals(0, code, out)
        assertEquals(ListeningMode.Transparency, buds.state.value.sound?.mode)
        assertTrue(cli("scenes").second.lines().any { it.startsWith("* Talk") })
        assertEquals(1, cli("scene", "beach").first)
    }

    @Test
    fun presetsByNameOrNumber() {
        assertEquals(0, cli("preset", "bass", "booster").first)
        assertEquals(2, buds.state.value.equalizer?.presetId)
        assertEquals(0, cli("preset", "5").first)
        assertEquals(5, buds.state.value.equalizer?.presetId)
        assertTrue(cli("presets").second.lines().any { it.startsWith("* 5  Vocal") })
    }

    @Test
    fun earbudsAwaySayWhy() {
        buds = DemoBuds(connected = false)
        assertEquals("Liberty 4 NC  not connected", cli("status").second)
        val (code, _, err) = cli("mode", "normal")
        assertEquals(1, code)
        assertEquals("Liberty 4 NC isn't connected.", err)
        val json = Json.read(cli("status", "--json").second) as Map<*, *>
        assertEquals(Link.Lost.name.lowercase(), json["link"])
    }

    @Test
    fun secondStartStillJustShows() {
        java.net.Socket(InetAddress.getLoopbackAddress(), server.localPort).use { s ->
            s.getOutputStream().write("show\n".toByteArray())
            assertEquals("blazbuds", s.getInputStream().bufferedReader().readLine())
        }
        Thread.sleep(100)
        assertEquals(1, shown)
    }

    @Test
    fun appNotRunning() {
        val free = ServerSocket(0).use { it.localPort }
        val err = ByteArrayOutputStream()
        val code = Cli.run(arrayOf("battery"), PrintStream(ByteArrayOutputStream()), PrintStream(err), free)
        assertEquals(Cli.NOT_RUNNING, code)
        assertTrue("isn't running" in err.toString())
    }

    @Test
    fun onlyCommandsGoToTheTerminal() {
        assertTrue(Cli.wants(arrayOf("battery")))
        assertTrue(Cli.wants(arrayOf("--help")))
        assertTrue(!Cli.wants(arrayOf("--hidden")))
        assertTrue(!Cli.wants(arrayOf()))
    }
}

/** The installed app's link that makes `blazbuds` a terminal command on Linux. */
class TerminalLinkTest {
    private val root = kotlin.io.path.createTempDirectory("blazbuds-link").toFile()
    private val app = java.io.File(root, "opt/blazbuds/bin/Blazbuds").apply { parentFile.mkdirs(); writeText("#!/bin/sh\n"); setExecutable(true) }
    private val home = java.io.File(root, "home").apply { mkdirs() }
    private val bin = java.io.File(home, ".local/bin")
    private val link = bin.toPath().resolve("blazbuds")

    @AfterTest
    fun clean() { root.deleteRecursively() }

    @Test
    fun linksWhenTheFolderIsOnThePath() {
        bin.mkdirs()
        Cli.linkIntoPath(app.path, home.path, "/usr/bin:${bin.path}")
        assertEquals(app.toPath(), java.nio.file.Files.readSymbolicLink(link))
    }

    @Test
    fun leavesThingsAloneOtherwise() {
        Cli.linkIntoPath(app.path, home.path, "/usr/bin:${bin.path}")
        assertTrue(!bin.exists(), "made no folder")
        bin.mkdirs()
        Cli.linkIntoPath(app.path, home.path, "/usr/bin")
        assertTrue(!java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS), "off the PATH")
        Cli.linkIntoPath(null, home.path, bin.path)
        assertTrue(!java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS), "run from Gradle")
        java.io.File(bin, "blazbuds").writeText("mine")
        Cli.linkIntoPath(app.path, home.path, bin.path)
        assertEquals("mine", java.io.File(bin, "blazbuds").readText())
    }

    @Test
    fun followsTheAppWhenItMoves() {
        bin.mkdirs()
        java.nio.file.Files.createSymbolicLink(link, java.io.File(root, "old/bin/Blazbuds").toPath())
        Cli.linkIntoPath(app.path, home.path, bin.path)
        assertEquals(app.toPath(), java.nio.file.Files.readSymbolicLink(link))
    }
}

package com.blazify.buds.desktop

import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The few MQTT 3.1.1 packets Blazbuds needs to talk to a Home Assistant broker, as bytes: connect with a last will and
 * a login, publish at QoS 0, subscribe, ping and disconnect. Kept apart from the socket so it can be tested byte by byte.
 */
object Mqtt {
    /** What the broker publishes for us if we vanish without saying goodbye. */
    class Will(val topic: String, val payload: ByteArray, val retain: Boolean)

    sealed interface Packet {
        data class ConnAck(val sessionPresent: Boolean, val code: Int) : Packet
        class Publish(val topic: String, val payload: ByteArray, val retain: Boolean, val qos: Int, val id: Int?) : Packet {
            val text: String get() = payload.decodeToString()
        }
        data class SubAck(val id: Int, val codes: List<Int>) : Packet
        data object PingResp : Packet
        data class Other(val type: Int) : Packet
    }

    const val CONNECT = 1
    const val CONNACK = 2
    const val PUBLISH = 3
    const val PUBACK = 4
    const val SUBSCRIBE = 8
    const val SUBACK = 9
    const val PINGREQ = 12
    const val PINGRESP = 13
    const val DISCONNECT = 14

    /** The largest remaining length four length bytes can carry. */
    const val MAX_LENGTH = 268_435_455

    /** The remaining length: seven bits a byte, lowest first, the top bit set while more follow. */
    fun length(n: Int): ByteArray {
        require(n in 0..MAX_LENGTH) { "MQTT packet too long: $n" }
        val out = ByteArrayOutputStream(4)
        var left = n
        do {
            var byte = left % 128
            left /= 128
            if (left > 0) byte = byte or 0x80
            out.write(byte)
        } while (left > 0)
        return out.toByteArray()
    }

    fun readLength(input: InputStream): Int {
        var value = 0
        var factor = 1
        repeat(4) {
            val byte = input.read()
            if (byte < 0) throw EOFException("The broker closed the connection")
            value += (byte and 0x7F) * factor
            if (byte and 0x80 == 0) return value
            factor *= 128
        }
        throw IOException("Malformed remaining length")
    }

    private fun ByteArrayOutputStream.text(s: String) = bytes(s.encodeToByteArray())

    private fun ByteArrayOutputStream.bytes(b: ByteArray) {
        require(b.size <= 0xFFFF) { "MQTT field too long" }
        write(b.size shr 8)
        write(b.size and 0xFF)
        write(b)
    }

    private fun ByteArrayOutputStream.short(n: Int) {
        write((n shr 8) and 0xFF)
        write(n and 0xFF)
    }

    private fun packet(first: Int, body: ByteArray): ByteArray = ByteArrayOutputStream(body.size + 5).apply {
        write(first)
        write(length(body.size))
        write(body)
    }.toByteArray()

    fun connect(clientId: String, username: String?, password: String?, keepAlive: Int, will: Will?, clean: Boolean = true): ByteArray {
        val body = ByteArrayOutputStream()
        body.text("MQTT")
        body.write(4) // 3.1.1
        var flags = 0
        if (!username.isNullOrEmpty()) flags = flags or 0x80
        if (!username.isNullOrEmpty() && password != null) flags = flags or 0x40
        if (will != null) flags = flags or 0x04 or (if (will.retain) 0x20 else 0)
        if (clean) flags = flags or 0x02
        body.write(flags)
        body.short(keepAlive)
        body.text(clientId)
        if (will != null) {
            body.text(will.topic)
            body.bytes(will.payload)
        }
        if (!username.isNullOrEmpty()) {
            body.text(username)
            if (password != null) body.bytes(password.encodeToByteArray())
        }
        return packet(CONNECT shl 4, body.toByteArray())
    }

    /** A QoS 0 message: no packet id, no reply. */
    fun publish(topic: String, payload: ByteArray, retain: Boolean): ByteArray {
        val body = ByteArrayOutputStream(topic.length + payload.size + 2)
        body.text(topic)
        body.write(payload)
        return packet((PUBLISH shl 4) or (if (retain) 1 else 0), body.toByteArray())
    }

    /** Asks for each topic at QoS 0. */
    fun subscribe(id: Int, topics: List<String>): ByteArray {
        val body = ByteArrayOutputStream()
        body.short(id)
        topics.forEach { body.text(it); body.write(0) }
        return packet((SUBSCRIBE shl 4) or 0x02, body.toByteArray())
    }

    fun pubAck(id: Int): ByteArray = byteArrayOf((PUBACK shl 4).toByte(), 2, (id shr 8).toByte(), id.toByte())

    val pingReq: ByteArray get() = byteArrayOf((PINGREQ shl 4).toByte(), 0)

    val disconnect: ByteArray get() = byteArrayOf((DISCONNECT shl 4).toByte(), 0)

    /** Reads one packet from the broker, waiting for it. */
    fun read(input: InputStream): Packet {
        val first = input.read()
        if (first < 0) throw EOFException("The broker closed the connection")
        val size = readLength(input)
        val body = input.readNBytes(size)
        if (body.size < size) throw EOFException("The broker closed the connection")
        val type = first ushr 4
        fun short(at: Int) = ((body[at].toInt() and 0xFF) shl 8) or (body[at + 1].toInt() and 0xFF)
        return when (type) {
            CONNACK -> Packet.ConnAck(body[0].toInt() and 1 == 1, body[1].toInt() and 0xFF)
            PUBLISH -> {
                val qos = (first shr 1) and 3
                val topicLength = short(0)
                val topic = body.decodeToString(2, 2 + topicLength)
                var at = 2 + topicLength
                val id = if (qos > 0) short(at).also { at += 2 } else null
                Packet.Publish(topic, body.copyOfRange(at, body.size), first and 1 == 1, qos, id)
            }
            SUBACK -> Packet.SubAck(short(0), (2 until body.size).map { body[it].toInt() and 0xFF })
            PINGRESP -> Packet.PingResp
            else -> Packet.Other(type)
        }
    }

    /** Why a broker turned the connection down, in plain words. */
    fun refusal(code: Int): String = when (code) {
        1 -> "The broker doesn't speak MQTT 3.1.1."
        2 -> "The broker turned down this app's client name."
        3 -> "The broker isn't taking connections right now."
        4 -> "The broker turned down the username or password."
        5 -> "The broker says this login may not connect."
        else -> "The broker turned the connection down (code $code)."
    }
}

class MqttRefused(val code: Int) : IOException(Mqtt.refusal(code))

/**
 * One connection to a broker over a plain socket. Every call blocks, so it is used from Dispatchers.IO; messages
 * arrive on its own reader thread through [onMessage]. [closed] completes when the connection ends, with the reason.
 */
class MqttClient(
    private val host: String,
    private val port: Int,
    private val clientId: String,
    private val username: String?,
    private val password: String?,
    private val will: Mqtt.Will?,
    private val keepAlive: Int = 30,
    private val onMessage: (topic: String, payload: String) -> Unit,
) {
    private val socket = Socket()
    private lateinit var output: OutputStream
    private var nextId = 1
    val closed = CompletableDeferred<Throwable?>()

    fun connect() {
        socket.connect(InetSocketAddress(host, port), 5_000)
        socket.tcpNoDelay = true
        socket.soTimeout = 5_000
        output = socket.getOutputStream().buffered()
        val input = socket.getInputStream().buffered()
        send(Mqtt.connect(clientId, username, password, keepAlive, will))
        when (val reply = Mqtt.read(input)) {
            is Mqtt.Packet.ConnAck -> if (reply.code != 0) { socket.close(); throw MqttRefused(reply.code) }
            else -> { socket.close(); throw IOException("The broker answered with something other than a connect reply") }
        }
        // We ping twice per keep-alive, so silence for one and a half of them means the link is gone.
        socket.soTimeout = keepAlive * 1_500
        Thread({
            val why = runCatching {
                while (true) {
                    when (val packet = Mqtt.read(input)) {
                        is Mqtt.Packet.Publish -> {
                            if (packet.qos == 1 && packet.id != null) send(Mqtt.pubAck(packet.id))
                            runCatching { onMessage(packet.topic, packet.text) }
                        }
                        else -> {}
                    }
                }
            }.exceptionOrNull()
            runCatching { socket.close() }
            closed.complete(why)
        }, "mqtt-reader").apply { isDaemon = true }.start()
    }

    private fun send(bytes: ByteArray) = synchronized(this) {
        output.write(bytes)
        output.flush()
    }

    fun publish(topic: String, payload: String, retain: Boolean) = send(Mqtt.publish(topic, payload.encodeToByteArray(), retain))

    fun subscribe(topics: List<String>) = send(Mqtt.subscribe(synchronized(this) { nextId++ }, topics))

    fun ping() = send(Mqtt.pingReq)

    /** Says goodbye first when [graceful], so the broker doesn't publish the last will. */
    fun close(graceful: Boolean) {
        if (graceful) runCatching { send(Mqtt.disconnect) }
        runCatching { socket.close() }
        closed.complete(null)
    }
}

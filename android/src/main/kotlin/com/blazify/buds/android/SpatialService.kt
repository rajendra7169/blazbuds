package com.blazify.buds.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.DynamicsProcessing
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import com.blazify.buds.R
import com.blazify.buds.core.spatial.SpatialMode
import com.blazify.buds.core.spatial.Spatializer
import com.blazify.buds.ui.SpatialState
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.FileInputStream

/**
 * 3D for whatever is playing. Android lets an app hear the sound other apps play (the way screen recorders do), so
 * this service takes it, turns it into 3D and plays that to the earbuds. The apps' own sound would then be heard
 * twice, so each app it can hear is quietened with an effect on its own sound: Android hands the recording the sound
 * from before such effects, so the 3D copy is untouched. Apps that don't let themselves be heard are left alone.
 * Finding each app's sound needs the system's audio report, which a phone shares only after a one-time "dump"
 * permission from a computer.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SpatialService : Service() {
    private var projection: MediaProjection? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val muted = mutableMapOf<Int, DynamicsProcessing>()
    private val mutedUid = mutableMapOf<Int, Int>()
    private val main = Handler(Looper.getMainLooper())
    private var audio: AudioManager? = null
    private val playbackChanged = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            quieten()
        }
    }
    private val sweep = object : Runnable {
        override fun run() {
            if (!running) return
            quieten()
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.getStringExtra(MODE)?.let { mode = SpatialMode.valueOf(it) }
        if (running) return START_NOT_STICKY
        val code = intent?.getIntExtra(CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(DATA, Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra(DATA)
        showNotice()
        if (data == null) {
            fail("Android didn't allow capturing what's playing")
            return START_NOT_STICKY
        }
        if (report() == null) {
            fail("Needs a one-time setup from a computer first")
            return START_NOT_STICKY
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = runCatching { manager.getMediaProjection(code, data) }.getOrNull()
        if (granted == null) {
            fail("Android didn't allow capturing what's playing")
            return START_NOT_STICKY
        }
        projection = granted
        granted.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopSelf() }
            }
        }, main)
        running = true
        audio = getSystemService(AudioManager::class.java)
        audio?.registerAudioPlaybackCallback(playbackChanged, main)
        quieten()
        main.post(sweep)
        worker = Thread({ pump(granted) }, "blazbuds-3d").also { it.priority = Thread.MAX_PRIORITY; it.start() }
        return START_NOT_STICKY
    }

    /** Hears, turns to 3D, plays; until switched off. */
    private fun pump(projection: MediaProjection) {
        val rate = 48_000
        val frames = 480
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()
        val inSize = maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT), frames * 8) * 2
        val record = try {
            @Suppress("MissingPermission")
            AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(inSize).setAudioPlaybackCaptureConfig(capture).build()
        } catch (e: Exception) {
            Log.w(TAG, "No capture", e)
            main.post { fail("Couldn't capture what's playing") }
            return
        }
        val outSize = maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT), frames * 8) * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE).build(),
            )
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(outSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val spatial = Spatializer(rate)
        val input = FloatArray(frames * 2)
        val output = FloatArray(frames * 2)
        var silentFor = 0
        try {
            record.startRecording()
            track.play()
            main.post { state.value = SpatialState.On(mode, muted.size) }
            while (running) {
                val got = record.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
                if (got <= 0) continue
                // Nothing heard while an app is quietened means that app won't be heard: give it its sound back.
                val loud = (0 until got).maxOf { kotlin.math.abs(input[it]) }
                silentFor = if (loud < 1e-5f && muted.isNotEmpty()) silentFor + got / 2 else 0
                if (silentFor > rate * 5 / 2) {
                    silentFor = 0
                    main.post { giveBack() }
                }
                spatial.mode = mode
                spatial.process(input, output, got / 2)
                track.write(output, 0, got, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: Exception) {
            Log.w(TAG, "3D stopped", e)
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { track.stop() }
            track.release()
        }
    }

    /**
     * Quietens every app playing now whose sound we can hear, and lets go of ones that have finished. Reads the
     * system's list of players for each one's sound session.
     */
    private fun quieten() {
        if (!running) return
        val report = report() ?: return
        val me = Process.myUid()
        val playing = mutableSetOf<Int>()
        for (line in report.lineSequence()) {
            if (!line.contains("AudioPlaybackConfiguration") || line.trimStart().startsWith("(")) continue
            val session = SESSION.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val uid = UID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (session <= 0 || uid == me || uid < Process.FIRST_APPLICATION_UID) continue
            val usage = USAGE.find(line)?.groupValues?.get(1) ?: continue
            if (usage !in HEARD) continue
            // An app that asked not to be recorded can't be heard, so it keeps its own sound.
            val flags = FLAGS.find(line)?.groupValues?.get(1)?.toIntOrNull(16) ?: 0
            if (flags and (NO_MEDIA_PROJECTION or NO_SYSTEM_CAPTURE) != 0) continue
            if (!allowsCapture(uid)) continue
            playing += session
            if (session !in muted) {
                runCatching {
                    DynamicsProcessing(Int.MAX_VALUE, session, null).apply {
                        setInputGainAllChannelsTo(-200f)
                        enabled = true
                    }
                }.onSuccess {
                    muted[session] = it
                    mutedUid[session] = uid
                    Log.i(TAG, "Quietened session $session of uid $uid")
                }.onFailure { Log.w(TAG, "Couldn't quieten session $session", it) }
            }
        }
        (muted.keys - playing).toList().forEach { session ->
            mutedUid.remove(session)
            muted.remove(session)?.let { runCatching { it.release() } }
        }
        val now = state.value
        if (now is SpatialState.On && now.apps != muted.size) state.value = now.copy(apps = muted.size)
    }

    /** Gives every quietened app its sound back and leaves those apps alone from now on. */
    private fun giveBack() {
        Log.i(TAG, "Nothing heard; giving ${muted.size} app(s) their sound back")
        mutedUid.values.forEach { captureAllowed[it] = false }
        mutedUid.clear()
        muted.values.forEach { runCatching { it.release() } }
        muted.clear()
        val now = state.value
        if (now is SpatialState.On) state.value = now.copy(apps = 0)
    }

    private val captureAllowed = mutableMapOf<Int, Boolean>()

    /** Apps made for Android 9 or older, or that say so in their manifest, can't be recorded. */
    private fun allowsCapture(uid: Int): Boolean = captureAllowed.getOrPut(uid) {
        val pm = packageManager
        // If the app can't be looked up, try it: the check for silence above gives its sound back if it can't be heard.
        val names = pm.getPackagesForUid(uid) ?: return@getOrPut true
        names.all { name ->
            val info = runCatching { pm.getApplicationInfo(name, 0) }.getOrNull() ?: return@all true
            if (info.targetSdkVersion < Build.VERSION_CODES.Q) return@all false
            // ApplicationInfo keeps the manifest's allowAudioPlaybackCapture in a field apps can't read directly.
            runCatching {
                ApplicationInfo::class.java.getMethod("isAudioPlaybackCaptureAllowed").invoke(info) as Boolean
            }.getOrDefault(true)
        }
    }

    /** The system's audio report, or null without the one-time permission. */
    private fun report(): String? {
        if (checkSelfPermission(android.Manifest.permission.DUMP) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "audio") as IBinder
            val (read, write) = ParcelFileDescriptor.createPipe()
            binder.dumpAsync(write.fileDescriptor, arrayOf())
            write.close()
            FileInputStream(read.fileDescriptor).bufferedReader().use { it.readText() }.also { read.close() }
        }.onFailure { Log.w(TAG, "No audio report", it) }.getOrNull()
    }

    private fun showNotice() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "3D sound", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, SpatialService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("3D sound is on")
            .setContentText("Everything playing goes round you")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTICE, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTICE, notice)
        }
    }

    private fun fail(why: String) {
        state.value = SpatialState.Blocked(why)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(sweep)
        audio?.unregisterAudioPlaybackCallback(playbackChanged)
        worker?.join(500)
        // Give every app its own sound back.
        muted.values.forEach { runCatching { it.release() } }
        muted.clear()
        projection?.stop()
        projection = null
        if (state.value !is SpatialState.Blocked) state.value = SpatialState.Off
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Blazbuds3D"
        const val CODE = "code"
        const val DATA = "data"
        const val MODE = "mode"
        const val STOP = "com.blazify.buds.STOP_3D"
        private const val CHANNEL = "spatial"
        private const val NOTICE = 7
        private const val NO_MEDIA_PROJECTION = 0x400
        private const val NO_SYSTEM_CAPTURE = 0x1000
        private val HEARD = setOf("USAGE_MEDIA", "USAGE_GAME", "USAGE_UNKNOWN")
        private val SESSION = Regex("""sessionId:(\d+)""")
        private val UID = Regex("""u/pid:(\d+)/""")
        private val USAGE = Regex("""usage=(\w+)""")
        private val FLAGS = Regex("""flags=0x([0-9A-Fa-f]+)""")

        val state = MutableStateFlow<SpatialState>(SpatialState.Off)

        @Volatile var mode: SpatialMode = SpatialMode.Speakers
    }
}

private typealias ApplicationInfo = android.content.pm.ApplicationInfo

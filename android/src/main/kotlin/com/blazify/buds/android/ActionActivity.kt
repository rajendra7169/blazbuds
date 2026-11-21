package com.blazify.buds.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Scene
import com.blazify.buds.ui.Action
import com.blazify.buds.ui.words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The door for everything outside the app that wants to change the earbuds: the app icon's shortcuts, "Hey Google",
 * and automation apps such as Tasker or MacroDroid, through links like `blazbuds://mode/noise-cancelling`,
 * `blazbuds://scene/focus`, `blazbuds://battery` or `blazbuds://find`. Nothing is drawn; it makes the change, says
 * so in a short message, and is gone.
 */
class ActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.data ?: featureLink(intent)
        android.util.Log.i(TAG, "link: $link")
        if (link != null) handle(applicationContext, link) { open -> startActivity(open) }
        finish()
    }

    companion object {
        private const val TAG = "BlazbudsAction"

        /** "Hey Google, open noise cancelling on Blazbuds" arrives as a feature name rather than a link. */
        private fun featureLink(intent: Intent?): Uri? {
            val feature = intent?.getStringExtra("feature")?.lowercase()?.trim() ?: return null
            val path = when {
                "find" in feature -> "find"
                "battery" in feature -> "battery"
                "transparen" in feature || "ambient" in feature || "aware" in feature -> "mode/transparency"
                "normal" in feature || "off" in feature -> "mode/normal"
                "cancel" in feature || "anc" in feature || "quiet" in feature -> "mode/noise-cancelling"
                else -> Scene.DEFAULTS.firstOrNull { it.name.lowercase() in feature }?.let { "scene/${it.id}" } ?: return null
            }
            return Uri.parse("blazbuds://$path")
        }

        fun handle(context: Context, link: Uri, open: (Intent) -> Unit) {
            val parts = listOfNotNull(link.host) + link.pathSegments
            val what = parts.getOrNull(0)
            val which = parts.getOrNull(1)?.lowercase()
            when (what) {
                "mode" -> {
                    val mode = when (which) {
                        "noise-cancelling", "anc", "cancelling", "noisecancelling" -> ListeningMode.NoiseCancelling
                        "transparency", "ambient", "aware" -> ListeningMode.Transparency
                        "normal", "off" -> ListeningMode.Normal
                        "next" -> null
                        else -> return say(context, "Unknown mode: $which")
                    }
                    work(context) {
                        if (mode == null) {
                            Remote.cycle(context)?.let { say(context, it.words()) } ?: say(context, "Couldn't reach your earbuds")
                        } else {
                            say(context, if (Remote.setMode(context, mode)) mode.words() else "Couldn't reach your earbuds")
                        }
                    }
                }
                "scene" -> {
                    val scene = Scene.DEFAULTS.firstOrNull { it.id == which || it.name.equals(which, ignoreCase = true) }
                        ?: return say(context, "Unknown scene: $which")
                    work(context) {
                        val done = Remote.applyAll(context, Action.PutScene(scene.id).changes())
                        say(context, if (done) "${scene.name} scene" else "Couldn't reach your earbuds")
                    }
                }
                "battery" -> work(context) {
                    val state = Remote.battery(context)
                    if (state == null) {
                        say(context, "Couldn't reach your earbuds")
                    } else {
                        val parts = listOfNotNull(
                            state.left?.percent?.takeIf { it >= 0 }?.let { "L $it%" },
                            state.right?.percent?.takeIf { it >= 0 }?.let { "R $it%" },
                            state.overall?.takeIf { state.left == null && state.right == null }?.percent?.let { "$it%" },
                            state.case?.percent?.takeIf { it > 0 }?.let { "case $it%" },
                        )
                        say(context, "${state.name}: ${parts.joinToString("  ")}")
                    }
                }
                "find" -> open(Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN, MainActivity.OPEN_FIND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> open(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        private fun work(context: Context, block: suspend () -> Unit) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { block() } }
        }

        private fun say(context: Context, text: String) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
        }
    }
}

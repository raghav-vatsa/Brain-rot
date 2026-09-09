package com.brainrot.detector.overlay

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.brainrot.detector.R
import com.brainrot.detector.data.formatDuration

/**
 * The nag itself: a full-screen window, drawn over whatever app the user is in,
 * showing an animated crying brain GIF and how far past the limit they are.
 */
class CryingBrainOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)

    private var root: View? = null
    private var gif: Drawable? = null

    /** Package the overlay is currently nagging about, or null when hidden. */
    var showingFor: String? = null
        private set

    fun show(
        packageName: String,
        appLabel: String,
        usedMs: Long,
        limitMs: Long,
        snoozeMs: Long,
        onSnooze: () -> Unit,
        onQuit: () -> Unit,
    ) {
        if (showingFor == packageName) {
            return
        }
        hide()

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_crying_brain, null)

        view.findViewById<TextView>(R.id.overlay_message).text = context.getString(
            R.string.overlay_message,
            appLabel,
            formatDuration(usedMs),
            formatDuration(limitMs),
        )

        val image = view.findViewById<ImageView>(R.id.brain_gif)
        gif = loadCryingBrain()?.also { drawable ->
            image.setImageDrawable(drawable)
            if (drawable is AnimatedImageDrawable) {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                drawable.start()
            }
        }

        val snooze = view.findViewById<Button>(R.id.btn_snooze)
        snooze.text = context.getString(R.string.overlay_snooze, formatDuration(snoozeMs))
        snooze.setOnClickListener {
            hide()
            onSnooze()
        }
        view.findViewById<Button>(R.id.btn_quit).setOnClickListener {
            hide()
            onQuit()
        }
        // Back dismisses it the same way "5 more minutes" does, so the overlay can
        // never trap the user.
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                hide()
                onSnooze()
                true
            } else {
                false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            dimAmount = 0.7f
            gravity = Gravity.CENTER
        }

        val added = runCatching { windowManager?.addView(view, params) }.isSuccess
        if (added) {
            root = view
            showingFor = packageName
            view.requestFocus()
        }
    }

    fun hide() {
        val view = root ?: return
        (gif as? AnimatedImageDrawable)?.stop()
        runCatching { windowManager?.removeView(view) }
        root = null
        gif = null
        showingFor = null
    }

    private fun loadCryingBrain(): Drawable? = runCatching {
        ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(context.resources, R.raw.crying_brain)
        )
    }.getOrNull()
}

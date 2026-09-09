package com.brainrot.detector.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.brainrot.detector.R
import com.brainrot.detector.data.InstalledApps
import com.brainrot.detector.data.Permissions
import com.brainrot.detector.data.SettingsStore
import com.brainrot.detector.data.WatchSettings
import com.brainrot.detector.overlay.CryingBrainOverlay
import com.brainrot.detector.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The watcher. Polls the foreground app a few times a second-ish, keeps a
 * per-app timer, and puts the crying brain on screen once a watched app has
 * been used past its limit.
 *
 * It runs as a foreground service because Android will not let a background
 * process poll usage stats or add windows for long.
 */
class UsageMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var settings: SettingsStore
    private lateinit var detector: ForegroundAppDetector
    private lateinit var overlay: CryingBrainOverlay
    private val tracker = UsageSessionTracker()

    /** Package -> monotonic time before which we will not nag again. */
    private val mutedUntil = HashMap<String, Long>()

    private var screenOn = true
    private var running = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    detector.clear()
                    tracker.update(null, SystemClock.elapsedRealtime())
                    overlay.hide()
                }

                Intent.ACTION_SCREEN_ON -> screenOn = true
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore.get(this)
        detector = ForegroundAppDetector(this)
        overlay = CryingBrainOverlay(this)

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            settings.state.collectLatest { state ->
                if (!state.monitoring) {
                    stopSelf()
                } else {
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.setMonitoring(false)
            stopSelf()
            return START_NOT_STICKY
        }

        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(settings.current),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(settings.current))
            }
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            handler.post(pollRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        runCatching { unregisterReceiver(screenReceiver) }
        overlay.hide()
        scope.cancel()
        running = false
        super.onDestroy()
    }

    private fun tick() {
        val state = settings.current
        if (!state.monitoring) {
            stopSelf()
            return
        }
        if (!screenOn || !Permissions.hasUsageAccess(this)) return

        val nowElapsed = SystemClock.elapsedRealtime()
        val foreground = detector.poll(System.currentTimeMillis())
        val wasReset = tracker.update(foreground, nowElapsed)
        if (foreground != null && wasReset) mutedUntil.remove(foreground)

        // The nag belongs to one app; leaving that app takes it away.
        overlay.showingFor?.let { shown ->
            if (shown != foreground) overlay.hide()
        }

        if (foreground == null || foreground == packageName) return
        if (!state.isWatched(foreground)) return

        val used = tracker.usedMs(foreground)
        val limit = state.limitFor(foreground)
        val muted = mutedUntil[foreground] ?: 0L
        if (used < limit || nowElapsed < muted) return

        showNag(foreground, used, limit, state, nowElapsed)
    }

    private fun showNag(
        watchedPackage: String,
        usedMs: Long,
        limitMs: Long,
        state: WatchSettings,
        nowElapsed: Long,
    ) {
        if (!Permissions.canDrawOverlay(this)) return

        overlay.show(
            packageName = watchedPackage,
            appLabel = InstalledApps.labelFor(this, watchedPackage),
            usedMs = usedMs,
            limitMs = limitMs,
            snoozeMs = state.snoozeMs,
            onSnooze = {
                mutedUntil[watchedPackage] = SystemClock.elapsedRealtime() + state.snoozeMs
            },
            onQuit = {
                // Timer starts over, and we stay quiet long enough for them to
                // actually get out of the app.
                tracker.reset(watchedPackage)
                mutedUntil[watchedPackage] = SystemClock.elapsedRealtime() + state.snoozeMs
                goHome()
            },
        )
        // Keep quiet until snooze elapses even if the user just ignores the card.
        mutedUntil[watchedPackage] = nowElapsed + state.snoozeMs
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun buildNotification(state: WatchSettings): Notification {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = getString(R.string.channel_description) }
            )
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, UsageMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (state.watched.isEmpty()) {
            getString(R.string.notification_text_none)
        } else {
            getString(R.string.notification_text, state.watched.size)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification),
                    getString(R.string.notification_stop),
                    stop,
                ).build()
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "usage_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 2_000L
        const val ACTION_STOP = "com.brainrot.detector.action.STOP"

        fun start(context: Context) {
            // Can be refused if the app is in the background under a restricted
            // start; the user can always turn it back on from the app.
            runCatching {
                context.startForegroundService(Intent(context, UsageMonitorService::class.java))
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, UsageMonitorService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

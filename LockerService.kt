package com.spectral.phonelocker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class LockerService : Service() {
    @Volatile private var running = true
    private var overlay: android.view.View? = null
    private var windowManager: WindowManager? = null
    private var unlockedForCurrentSession = false
    private var currentTarget = ""

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Thread { watchLoop() }.start()
    }

    private fun watchLoop() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var lastEventTime = System.currentTimeMillis()
        while (running) {
            try {
                val end = System.currentTimeMillis()
                val events = usm.queryEvents(lastEventTime, end)
                val event = UsageEvents.Event()
                var foreground = ""
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)) {
                        foreground = event.packageName ?: ""
                    } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                        (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_PAUSED)) {
                        if (event.packageName == currentTarget) unlockedForCurrentSession = false
                    }
                }
                lastEventTime = end
                val target = getSharedPreferences("locker", MODE_PRIVATE).getString("target", "") ?: ""
                if (target.isNotEmpty() && foreground.isNotEmpty()) {
                    if (foreground != target) {
                        if (currentTarget == target) unlockedForCurrentSession = false
                        removeOverlay()
                    } else if (!unlockedForCurrentSession && overlay == null && Settings.canDrawOverlays(this)) {
                        currentTarget = target
                        showOverlay()
                    }
                }
                Thread.sleep(500)
            } catch (_: Exception) {
                Thread.sleep(1000)
            }
        }
    }

    private fun showOverlay() {
        val prefs = getSharedPreferences("locker", MODE_PRIVATE)
        val correctPin = prefs.getString("pin", "") ?: ""
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(0xFF101018.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "PhoneLocker\nВведите PIN-код"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        val input = EditText(this).apply {
            hint = "PIN-код"
            inputType = 2
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(input)
        val button = Button(this).apply {
            text = "Разблокировать"
            setOnClickListener {
                if (input.text.toString() == correctPin) {
                    unlockedForCurrentSession = true
                    removeOverlay()
                } else {
                    input.setText("")
                    Toast.makeText(this@LockerService, "Неверный PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(button)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        try {
            windowManager?.addView(layout, params)
            overlay = layout
        } catch (_: Exception) { }
    }

    private fun removeOverlay() {
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        overlay = null
    }

    private fun buildNotification(): Notification {
        val channelId = "phonelocker"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "PhoneLocker", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("PhoneLocker работает")
                .setContentText("Защита выбранного приложения активна")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("PhoneLocker работает")
                .setContentText("Защита выбранного приложения активна")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        }
    }

    override fun onDestroy() {
        running = false
        removeOverlay()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

package com.spectral.phonelocker

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("locker", MODE_PRIVATE)
        val pad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        pad.addView(TextView(this).apply { text = "PhoneLocker"; textSize = 28f })
        pad.addView(TextView(this).apply {
            text = "Укажи package name приложения, которое нужно защищать, и PIN-код."
            textSize = 16f
        })
        val pkg = EditText(this).apply {
            hint = "Например: com.example.app"
            setText(prefs.getString("target", ""))
        }
        val pin = EditText(this).apply {
            hint = "PIN-код (не менее 4 цифр)"
            inputType = 2
            setText(prefs.getString("pin", ""))
        }
        pad.addView(pkg, ViewGroup.LayoutParams(-1, -2))
        pad.addView(pin, ViewGroup.LayoutParams(-1, -2))
        val overlay = Button(this).apply {
            text = "Разрешить отображение поверх приложений"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
        }
        val usage = Button(this).apply {
            text = "Разрешить доступ к статистике использования"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        val start = Button(this).apply {
            text = "Сохранить и включить блокировку"
            setOnClickListener {
                val p = pkg.text.toString().trim()
                val code = pin.text.toString()
                if (p.isBlank() || code.length < 4 || !code.all { it.isDigit() }) {
                    Toast.makeText(this@MainActivity, "Введи package name и PIN минимум из 4 цифр", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("target", p).putString("pin", code).apply()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Сначала разреши отображение поверх приложений", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!hasUsageAccess()) {
                    Toast.makeText(this@MainActivity, "Сначала разреши доступ к статистике использования", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                startService(Intent(this@MainActivity, LockerService::class.java))
                Toast.makeText(this@MainActivity, "PhoneLocker включён", Toast.LENGTH_SHORT).show()
            }
        }
        pad.addView(overlay); pad.addView(usage); pad.addView(start)
        setContentView(pad)
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow("android:get_usage_stats",
            android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

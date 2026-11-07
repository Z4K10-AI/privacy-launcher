package com.privacylauncher.ui.components

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privacylauncher.data.GlanceInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Battery level and next alarm, read straight from the system.
 *
 * Neither needs a permission. The battery broadcast is sticky, so the first
 * registerReceiver returns the current value immediately rather than waiting
 * for a change.
 */
@Composable
fun rememberGlance(enabled: Boolean): GlanceInfo {
    val context = LocalContext.current
    var info by remember { mutableStateOf(GlanceInfo()) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(enabled, context, lifecycleOwner) {
        if (!enabled) {
            info = GlanceInfo()
            return@DisposableEffect onDispose { }
        }

        fun readNextAlarm(): String? {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val next = am?.nextAlarmClock ?: return null
            val time = Instant.ofEpochMilli(next.triggerTime).atZone(ZoneId.systemDefault())
            return time.format(ALARM_FORMAT)
        }

        fun readBattery(intent: Intent?): Pair<Int?, Boolean> {
            val i = intent ?: return null to false
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return percent to charging
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val (percent, charging) = readBattery(intent)
                        info = info.copy(batteryPercent = percent, charging = charging)
                    }
                    AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                        info = info.copy(nextAlarm = readNextAlarm())
                    }
                }
            }
        }

        // Sticky broadcast: this call returns the current battery state.
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val (percent, charging) = readBattery(sticky)
        info = GlanceInfo(
            nextAlarm = readNextAlarm(),
            batteryPercent = percent,
            charging = charging,
        )

        // The alarm can be set or cleared in another app while we are in the
        // background, and ACTION_NEXT_ALARM_CLOCK_CHANGED is not guaranteed
        // to reach a stopped process, so re-read on resume as well.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                info = info.copy(nextAlarm = readNextAlarm())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return info
}

private val ALARM_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm")

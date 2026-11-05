package com.yamone.cafevisit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object Schedule {
    private fun pending(c: Context) = PendingIntent.getBroadcast(c, 700,
        Intent(c, RefreshReceiver::class.java).setAction("com.yamone.cafevisit.TICK"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun exact(c: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun cancel(c: Context) { c.getSystemService(AlarmManager::class.java).cancel(pending(c)) }
    fun arm(c: Context, elapsed: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val at = maxOf(SystemClock.elapsedRealtime() + 100L, elapsed)
        try {
            if (exact(c)) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c))
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c))
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c))
        }
    }
}

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Store.prefs(context)
        if (!p.getBoolean("running", false) || p.getBoolean("paused", false)) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (!p.getBoolean("resume_boot", false)) {
                p.edit().putBoolean("running", false).putLong("next_elapsed", 0).putLong("next_wall", 0)
                    .putString("result", "휴대폰 재시작 · 직접 시작해 주세요").apply()
                return
            }
            val wait = maxOf(0, p.getLong("next_wall", 0) - System.currentTimeMillis())
            p.edit().putLong("next_elapsed", SystemClock.elapsedRealtime() + wait).apply()
        }
        try {
            context.startForegroundService(Intent(context, RefreshService::class.java).setAction(RefreshService.RESTORE))
        } catch (_: RuntimeException) {
            // Do not evade OS launch restrictions or launch an Activity from the background.
            p.edit().putBoolean("paused", true).putString("result", "시스템이 실행을 제한했습니다. 앱에서 재개해 주세요.").apply()
            Schedule.cancel(context)
            Store.log(context, "시스템 시작 제한 · 앱에서 재개 필요", "error")
        }
    }
}

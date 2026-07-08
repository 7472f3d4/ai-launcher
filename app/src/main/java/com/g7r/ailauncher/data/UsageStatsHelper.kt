package com.g7r.ailauncher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/** System-wide usage signal (optional, requires the Usage Access special permission). */
data class UsageSignal(val lastTimeUsed: Long, val totalForegroundMs: Long)

/**
 * Best-effort bridge to [UsageStatsManager]. When the user grants Usage Access, this gives the
 * recommender real day-one signal; otherwise the launcher relies on its own [AppStat] tracking.
 */
object UsageStatsHelper {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun loadUsage(context: Context): Map<String, UsageSignal> {
        if (!hasPermission(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val end = System.currentTimeMillis()
        val begin = end - 30L * 24 * 60 * 60 * 1000
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end) ?: return emptyMap()
        val result = HashMap<String, UsageSignal>()
        for (s in stats) {
            if (s.totalTimeInForeground <= 0 && s.lastTimeUsed <= 0) continue
            val existing = result[s.packageName]
            result[s.packageName] = UsageSignal(
                lastTimeUsed = maxOf(existing?.lastTimeUsed ?: 0L, s.lastTimeUsed),
                totalForegroundMs = (existing?.totalForegroundMs ?: 0L) + s.totalTimeInForeground
            )
        }
        return result
    }
}

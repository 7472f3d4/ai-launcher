package com.g7r.ailauncher.data

import java.util.Calendar
import kotlin.math.exp
import kotlin.math.ln

/**
 * Scoring-based recommender (no ML for v1, but a clean seam to swap in Gemini/AI later).
 *
 * score = w1·frequency + w2·recency + w3·hour-of-day match + w4·day-of-week match + favorite bonus
 *
 * Self-tracked [AppStat] is primary; system [UsageSignal] backfills apps we haven't seen launched
 * yet (good cold-start when Usage Access is granted). When there is no signal at all, we fall back
 * to favorites → recently installed → alphabetical so the panel is never empty.
 */
object Recommender {

    private const val W_FREQUENCY = 1.0
    private const val W_RECENCY = 1.3
    private const val W_HOUR = 0.9
    private const val W_DOW = 0.5
    private const val W_FAVORITE = 2.0

    /** Recency decay constant (~3 days). */
    private const val RECENCY_TAU_MS = 3.0 * 24 * 60 * 60 * 1000

    fun recommend(
        apps: List<AppInfo>,
        stats: Map<String, AppStat>,
        favorites: Set<String>,
        usage: Map<String, UsageSignal>,
        now: Long,
        count: Int = 8
    ): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()

        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY(1)..SATURDAY(7) -> 0..6

        val scored = apps.map { app ->
            var score = 0.0
            val stat = stats[app.packageName]
            if (stat != null && stat.count > 0) {
                score += W_FREQUENCY * ln(1.0 + stat.count)

                val age = (now - stat.lastLaunch).coerceAtLeast(0).toDouble()
                score += W_RECENCY * exp(-age / RECENCY_TAU_MS)

                val hourTotal = stat.hours.sum()
                if (hourTotal > 0) {
                    // current hour plus the two adjacent hours (±1) as a soft window
                    val window = stat.hours[hour] +
                        stat.hours[(hour + 23) % 24] +
                        stat.hours[(hour + 1) % 24]
                    score += W_HOUR * (window.toDouble() / hourTotal)
                }

                val dowTotal = stat.daysOfWeek.sum()
                if (dowTotal > 0) {
                    score += W_DOW * (stat.daysOfWeek[dow].toDouble() / dowTotal)
                }
            } else {
                // Backfill from system usage stats when we have no first-party data yet.
                val u = usage[app.packageName]
                if (u != null) {
                    if (u.totalForegroundMs > 0) {
                        score += W_FREQUENCY * ln(1.0 + u.totalForegroundMs / 60_000.0)
                    }
                    if (u.lastTimeUsed > 0) {
                        val age = (now - u.lastTimeUsed).coerceAtLeast(0).toDouble()
                        score += W_RECENCY * exp(-age / RECENCY_TAU_MS)
                    }
                }
            }
            if (favorites.contains(app.packageName)) score += W_FAVORITE
            app to score
        }

        val hasSignal = scored.any { it.second > 0.0 }
        return if (hasSignal) {
            scored.sortedByDescending { it.second }.take(count).map { it.first }
        } else {
            apps.sortedWith(
                compareByDescending<AppInfo> { favorites.contains(it.packageName) }
                    .thenByDescending { it.firstInstallTime }
                    .thenBy { it.label.lowercase() }
            ).take(count)
        }
    }
}

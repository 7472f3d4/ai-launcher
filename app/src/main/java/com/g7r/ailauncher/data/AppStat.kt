package com.g7r.ailauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-package usage signal tracked by the launcher itself.
 * Histograms over hour-of-day (0..23) and day-of-week (0..6) feed the recommender.
 */
class AppStat(
    var count: Int = 0,
    var lastLaunch: Long = 0L,
    val hours: IntArray = IntArray(24),
    val daysOfWeek: IntArray = IntArray(7)
) {
    fun record(timestamp: Long, hour: Int, dow: Int) {
        count++
        lastLaunch = timestamp
        if (hour in 0..23) hours[hour]++
        if (dow in 0..6) daysOfWeek[dow]++
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("count", count)
        put("lastLaunch", lastLaunch)
        put("hours", JSONArray(hours.toList()))
        put("dows", JSONArray(daysOfWeek.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject): AppStat {
            val hours = IntArray(24)
            val dows = IntArray(7)
            o.optJSONArray("hours")?.let { arr ->
                for (i in 0 until minOf(24, arr.length())) hours[i] = arr.optInt(i)
            }
            o.optJSONArray("dows")?.let { arr ->
                for (i in 0 until minOf(7, arr.length())) dows[i] = arr.optInt(i)
            }
            return AppStat(
                count = o.optInt("count"),
                lastLaunch = o.optLong("lastLaunch"),
                hours = hours,
                daysOfWeek = dows
            )
        }
    }
}

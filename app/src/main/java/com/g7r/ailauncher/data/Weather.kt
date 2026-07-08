package com.g7r.ailauncher.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** UI state for the home weather chip. */
sealed interface WeatherState {
    data object NoPermission : WeatherState
    data object Loading : WeatherState
    data class Loaded(val placeName: String?, val temperatureC: Int, val code: Int) : WeatherState
    data class Error(val message: String) : WeatherState
}

data class WeatherData(val temperatureC: Int, val code: Int)

/** Open-Meteo current weather (no API key required). */
object WeatherRepository {
    fun fetch(lat: Double, lon: Double): WeatherData? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        conn.inputStream.use { stream ->
            val text = stream.readBytes().decodeToString()
            val current = JSONObject(text).getJSONObject("current")
            WeatherData(
                temperatureC = current.getDouble("temperature_2m").roundToInt(),
                code = current.getInt("weather_code")
            )
        }
    }.getOrNull()
}

/** WMO weather code → short Japanese description. */
object WeatherCodes {
    fun description(code: Int): String = when (code) {
        0 -> "快晴"
        1 -> "晴れ"
        2 -> "一部曇り"
        3 -> "曇り"
        in 45..48 -> "霧"
        in 51..57 -> "霧雨"
        in 61..67 -> "雨"
        in 71..77 -> "雪"
        in 80..82 -> "にわか雨"
        in 85..86 -> "にわか雪"
        in 95..99 -> "雷雨"
        else -> "—"
    }
}

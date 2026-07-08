package com.g7r.ailauncher.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A Todoist task shown on the home やること card and fed to the AI message. */
data class TodoTask(
    val id: String,
    val title: String,
    val dueMillis: Long?,
    val hasTime: Boolean,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
    /** Todoist API priority 1..4 — 4 is the highest (P1). */
    val priority: Int
)

sealed interface TodoistState {
    data object NoToken : TodoistState
    data object Loading : TodoistState
    data class Loaded(val tasks: List<TodoTask>) : TodoistState
    data class Error(val message: String) : TodoistState
}

/**
 * Todoist API クライアント。
 *
 * 2025 年に Todoist は統合 API v1 (`api.todoist.com/api/v1`) へ移行し、旧 REST v2 は
 * 廃止された。ここでは v1 を第一候補、v2 をフォールバックとして両対応する。
 * すべてのリクエスト結果は logcat (tag=TodoistRepo) に出力される。
 *
 * トークンは Todoist の 設定 → 連携機能 → 開発者 タブの個人 API トークン。
 * AI(Gemini等)への供給は [AiMessageContext.tasks] 経由。
 */
object TodoistRepository {

    private const val TAG = "TodoistRepo"
    private const val V1 = "https://api.todoist.com/api/v1"
    private const val V2 = "https://api.todoist.com/rest/v2"

    sealed interface FetchResult {
        data class Ok(val tasks: List<TodoTask>) : FetchResult
        data class Fail(val message: String) : FetchResult
    }

    /**
     * All active (incomplete) tasks — sorted so overdue/today surface first.
     * フィルタで絞ると「将来日付のみ」「期限なしのみ」のアカウントで 0 件になるため全件取得する。
     */
    fun fetch(token: String): FetchResult {
        val attempts = listOf(
            "$V1/tasks?limit=200", // 新API v1
            "$V2/tasks"            // 旧REST v2 フォールバック
        )
        var lastError = "取得に失敗しました"
        for (url in attempts) {
            try {
                val conn = open("GET", url, token)
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.use { it.readBytes().decodeToString() }
                    Log.i(TAG, "GET $url -> 200 (${body.length} bytes)")
                    return try {
                        val tasks = parseTasks(body)
                        Log.i(TAG, "parsed ${tasks.size} tasks")
                        FetchResult.Ok(tasks)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse error: ${body.take(300)}", e)
                        FetchResult.Fail("応答の解析に失敗しました")
                    }
                }
                val errorBody = runCatching {
                    conn.errorStream?.use { it.readBytes().decodeToString() }
                }.getOrNull()
                Log.w(TAG, "GET $url -> $code body=${errorBody?.take(300)}")
                when (code) {
                    401, 403 -> return FetchResult.Fail("トークンが無効です")
                    else -> lastError = "取得に失敗しました ($code)" // 404/410 等 → 次のエンドポイントへ
                }
            } catch (e: Exception) {
                Log.e(TAG, "GET $url network failure", e)
                lastError = "ネットワークエラー"
            }
        }
        return FetchResult.Fail(lastError)
    }

    /** Marks a task complete. */
    fun close(token: String, id: String): Boolean {
        for (url in listOf("$V1/tasks/$id/close", "$V2/tasks/$id/close")) {
            try {
                val conn = open("POST", url, token)
                val code = conn.responseCode
                Log.i(TAG, "POST $url -> $code")
                if (code in 200..299) return true
                if (code == 401 || code == 403) return false
            } catch (e: Exception) {
                Log.e(TAG, "POST $url network failure", e)
            }
        }
        return false
    }

    /** Adds a task due today. */
    fun add(token: String, content: String): Boolean {
        val body = JSONObject()
            .put("content", content)
            .put("due_string", "today")
            .put("due_lang", "ja")
            .toString()
        for (url in listOf("$V1/tasks", "$V2/tasks")) {
            try {
                val conn = open("POST", url, token).apply {
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                Log.i(TAG, "POST $url -> $code")
                if (code in 200..299) return true
                if (code == 401 || code == 403) return false
            } catch (e: Exception) {
                Log.e(TAG, "POST $url network failure", e)
            }
        }
        return false
    }

    private fun open(method: String, url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

    /** v1 は `{"results":[...]}`、v2 は素の配列 — 両方の形を受ける。 */
    private fun parseTasks(body: String): List<TodoTask> {
        val trimmed = body.trimStart()
        val arr: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(body)
        } else {
            JSONObject(body).optJSONArray("results") ?: JSONArray()
        }

        val now = System.currentTimeMillis()
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = dayFmt.format(now)
        val tasks = ArrayList<TodoTask>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("checked", false) || o.optBoolean("is_completed", false)) continue
            val content = o.optString("content")
            if (content.isBlank()) continue
            val due = o.optJSONObject("due")
            val dueDate = due?.optString("date").orEmpty()
            val dueDateTime = due?.optString("datetime").orEmpty()
            // v1 では due.date 自体が "2026-07-02T10:00:00" 形式になることがある
            val effectiveDateTime = when {
                dueDateTime.isNotBlank() && dueDateTime != "null" -> dueDateTime
                dueDate.contains("T") -> dueDate
                else -> ""
            }
            val dayPart = dueDate.substringBefore("T")
            val hasTime = effectiveDateTime.isNotBlank()
            val dueMillis = when {
                hasTime -> parseDateTime(effectiveDateTime)
                dayPart.isNotBlank() -> runCatching { dayFmt.parse(dayPart)?.time }.getOrNull()
                else -> null
            }
            val isDueToday = dayPart == today
            val isOverdue = when {
                dayPart.isNotBlank() && dayPart < today -> true
                hasTime && dueMillis != null -> dueMillis < now
                else -> false
            }
            tasks.add(
                TodoTask(
                    id = o.optString("id"),
                    title = content,
                    dueMillis = dueMillis,
                    hasTime = hasTime,
                    isOverdue = isOverdue,
                    isDueToday = isDueToday,
                    priority = o.optInt("priority", 1)
                )
            )
        }
        // 期限切れ → 今日 → 将来（日付順） → 期限なし。同順位は優先度の高い順。
        return tasks.sortedWith(
            compareBy(
                { !it.isOverdue },
                { !it.isDueToday },
                { it.dueMillis ?: Long.MAX_VALUE },
                { -it.priority }
            )
        )
    }

    private fun parseDateTime(raw: String): Long? {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for ((index, p) in patterns.withIndex()) {
            val fmt = SimpleDateFormat(p, Locale.US)
            if (index == 1) fmt.timeZone = TimeZone.getTimeZone("UTC")
            val t = runCatching { fmt.parse(raw)?.time }.getOrNull()
            if (t != null) return t
        }
        return null
    }
}

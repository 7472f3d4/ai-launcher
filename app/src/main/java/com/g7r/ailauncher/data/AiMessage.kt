package com.g7r.ailauncher.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the message generator may consider — Todoist tasks and Google Calendar
 * events combined. Extend as new signals appear (location, Bluetooth…).
 */
data class AiMessageContext(
    val now: Long,
    val hour: Int,
    val todayEvents: List<CalEvent>,
    val weather: WeatherState,
    val topAppLabel: String?,
    val tasks: List<TodoTask> = emptyList()
)

/**
 * Pluggable one-line assistant message shown on the Discover page.
 *
 * v1 is rule-based ([RuleBasedAiMessage]). To switch to Gemini later, implement this
 * interface with an API-backed provider (pass the same [AiMessageContext] — tasks,
 * events, weather — as the prompt context) and assign it to
 * [com.g7r.ailauncher.LauncherViewModel.aiMessageProvider].
 */
fun interface AiMessageProvider {
    fun generate(ctx: AiMessageContext): String
}

object RuleBasedAiMessage : AiMessageProvider {

    private val timeFmt = SimpleDateFormat("H:mm", Locale.JAPAN)

    override fun generate(ctx: AiMessageContext): String {
        val next = ctx.todayEvents.firstOrNull { !it.allDay && it.begin > ctx.now }

        // 1. An event is imminent.
        if (next != null) {
            val mins = ((next.begin - ctx.now) / 60_000L).toInt()
            if (mins in 1..60) return "あと${mins}分で「${next.title}」です。"
        }

        // 2. Free gap before the next event → suggest progressing a task.
        if (next != null && ctx.tasks.isNotEmpty()) {
            val gapMin = ((next.begin - ctx.now) / 60_000L).toInt()
            if (gapMin >= 60) {
                val gapText = if (gapMin >= 120) "約${gapMin / 60}時間" else "約1時間"
                val top = ctx.tasks.first()
                return "次の予定まで${gapText}あるので、「${top.title}」を進められます。"
            }
        }

        // 3. Overdue tasks.
        val overdue = ctx.tasks.count { it.isOverdue }
        if (overdue > 0) return "期限切れのタスクが${overdue}件あります。先に片付けましょう。"

        // 4. Tasks due today.
        val dueToday = ctx.tasks.count { it.isDueToday }
        if (dueToday > 0) return "今日が期限のタスクが${dueToday}件あります。"

        // 5. High-priority tasks (Todoist P1/P2 = API priority 4/3).
        if (ctx.tasks.any { it.priority >= 3 }) {
            return "優先度の高いタスクを先に終わらせることをおすすめします。"
        }

        // 6. Umbrella-worthy weather.
        val w = ctx.weather
        if (w is WeatherState.Loaded && w.code in 51..99) {
            return "${WeatherCodes.description(w.code)}の予報です。傘を忘れずに！"
        }

        // 7. Today's schedule summary.
        if (ctx.todayEvents.isNotEmpty()) {
            return if (next != null) {
                "今日の予定は${ctx.todayEvents.size}件。次は${timeFmt.format(Date(next.begin))}「${next.title}」です。"
            } else {
                "今日の予定は${ctx.todayEvents.size}件あります。"
            }
        }

        // 8. Usage pattern hint.
        if (ctx.topAppLabel != null && ctx.hour in 12..18) {
            return "この時間は「${ctx.topAppLabel}」をよく使っています。"
        }

        // 9. Time-of-day fallback.
        return when (ctx.hour) {
            in 5..10 -> "今日も一日頑張りましょう！"
            in 11..17 -> "午後も良いペースで行きましょう。"
            in 18..22 -> "今日もおつかれさまでした。"
            else -> "そろそろ休む時間です。おやすみなさい。"
        }
    }
}

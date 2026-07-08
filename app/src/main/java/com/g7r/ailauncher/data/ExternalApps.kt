package com.g7r.ailauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock

/** Launch helpers for the external apps that home-screen elements open when tapped. */
object ExternalApps {

    /** 時刻タップ → Google 時計。 */
    fun openClock(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
        if (launch != null) {
            runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching {
            context.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 天気タップ → 天気情報 (Pixel Weather)。無ければ Google 検索へフォールバック。 */
    fun openWeather(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.weather")
        if (launch != null) {
            runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=天気"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** やることカードタップ → Todoist アプリ。 */
    fun openTodoist(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.todoist")
        if (launch != null) {
            runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://app.todoist.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** タスクタップ → Todoist アプリの該当タスク画面（deep link → web → アプリ本体の順）。 */
    fun openTodoistTask(context: Context, taskId: String) {
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("todoist://task?id=$taskId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(deepLink) }.isSuccess) return
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://app.todoist.com/app/task/$taskId"))
            .setPackage("com.todoist")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(web) }.isSuccess) return
        openTodoist(context)
    }

    /** 編集メニュー「壁紙」。 */
    fun openWallpaperPicker(context: Context) {
        runCatching {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "壁紙を選択")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

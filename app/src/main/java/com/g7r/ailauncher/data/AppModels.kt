package com.g7r.ailauncher.data

import android.graphics.drawable.Drawable

/** The 15 categories shown in the app list (iOS App-Library style). */
enum class AppCategory(val displayName: String) {
    RECENTLY_ADDED("最近追加"),
    SNS("SNS"),
    ENTERTAINMENT("エンターテインメント"),
    PRODUCTIVITY_FINANCE("仕事効率化・ファイナンス"),
    UTILITIES("ユーティリティ"),
    TRAVEL("旅行"),
    SHOPPING("ショッピング"),
    LIFESTYLE("ライフスタイル"),
    HEALTH_FITNESS("健康・フィットネス"),
    EDUCATION("教育"),
    NEWS_READING("情報・読書"),
    GAMES("ゲーム"),
    PHOTO_VIDEO("写真・ビデオ"),
    MUSIC("ミュージック"),
    CREATIVITY("クリエイティビティ");
}

/** A launchable application. [category] is the resolved primary category (after overrides). */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
    val category: AppCategory,
    val firstInstallTime: Long,
    val isSystem: Boolean
) {
    val key: String get() = "$packageName/$activityName"
}

/** A category and the apps belonging to it, used to render the app-list cards. */
data class CategorySection(
    val category: AppCategory,
    val apps: List<AppInfo>
)

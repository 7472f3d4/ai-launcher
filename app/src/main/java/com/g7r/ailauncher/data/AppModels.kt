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
    val isSystem: Boolean,
    val customCategoryId: String? = null
) {
    val key: String get() = "$packageName/$activityName"
}

/** A user-created category shown alongside the built-in app categories. */
data class CustomAppCategory(
    val id: String,
    val name: String
)

/** A user-pinned Android shortcut, identified by the publishing app and shortcut ID. */
data class PinnedHomeShortcut(
    val instanceId: String,
    val packageName: String,
    val shortcutId: String,
    val label: String
)

/** A shortcut saved inside a dock folder. A null shortcutId launches the app itself. */
data class DockShortcutEntry(
    val id: String,
    val packageName: String,
    val shortcutId: String?,
    val label: String
)

/** A category and the apps belonging to it, used to render the app-list cards. */
data class CategorySection(
    val id: String,
    val displayName: String,
    val apps: List<AppInfo>
)

/** Persisted configuration for one of the four home-screen dock slots. */
sealed interface DockEntry {
    data object Empty : DockEntry

    data class App(val packageName: String) : DockEntry

    data class Folder(
        val id: String,
        val name: String,
        val packageNames: List<String>,
        val shortcuts: List<DockShortcutEntry> = emptyList()
    ) : DockEntry
}

/** A dock entry resolved against the apps currently installed on the device. */
sealed interface DockItem {
    data class App(val app: AppInfo) : DockItem

    data class Folder(
        val id: String,
        val name: String,
        val apps: List<AppInfo>,
        val shortcuts: List<DockShortcutEntry> = emptyList()
    ) : DockItem
}

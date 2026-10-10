package com.g7r.ailauncher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight persistence (SharedPreferences + JSON) for launcher settings and state. */
class Storage(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ai_launcher", Context.MODE_PRIVATE)

    // ---- launch stats (keyed by package name) ----
    fun loadStats(): MutableMap<String, AppStat> {
        val raw = prefs.getString(KEY_STATS, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, AppStat>()
            for (key in obj.keys()) result[key] = AppStat.fromJson(obj.getJSONObject(key))
            result
        }.getOrDefault(mutableMapOf())
    }

    fun saveStats(stats: Map<String, AppStat>) {
        val obj = JSONObject()
        for ((k, v) in stats) obj.put(k, v.toJson())
        prefs.edit().putString(KEY_STATS, obj.toString()).apply()
    }

    // ---- favorites (ordered, unique package names; order = pin order for the dock) ----
    fun loadFavorites(): MutableList<String> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) out.add(arr.getString(i))
            out
        }.getOrDefault(mutableListOf())
    }

    fun saveFavorites(favorites: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, JSONArray(favorites).toString()).apply()
    }

    // ---- home dock (exactly four app/folder slots) ----
    fun loadDock(): MutableList<DockEntry>? {
        val raw = prefs.getString(KEY_DOCK, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<DockEntry>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                when (item.optString("type")) {
                    "app" -> out.add(DockEntry.App(item.getString("package")))
                    "folder" -> {
                        val packages = item.optJSONArray("packages") ?: JSONArray()
                        val packageNames = buildList {
                            for (j in 0 until packages.length()) add(packages.getString(j))
                        }
                        val shortcuts = item.optJSONArray("shortcuts") ?: JSONArray()
                        val shortcutEntries = buildList {
                            for (j in 0 until shortcuts.length()) {
                                val shortcut = shortcuts.optJSONObject(j) ?: continue
                                val id = shortcut.optString("id").trim()
                                val packageName = shortcut.optString("packageName").trim()
                                val label = shortcut.optString("label").trim()
                                val shortcutId = shortcut.optString("shortcutId")
                                    .trim()
                                    .takeIf { it.isNotEmpty() }
                                if (id.isNotEmpty() && packageName.isNotEmpty()) {
                                    add(DockShortcutEntry(id, packageName, shortcutId, label))
                                }
                            }
                        }
                        out.add(
                            DockEntry.Folder(
                                id = item.getString("id"),
                                name = item.optString("name").ifBlank { "フォルダ" },
                                packageNames = packageNames.distinct(),
                                shortcuts = shortcutEntries.distinctBy { it.id }
                            )
                        )
                    }
                    else -> out.add(DockEntry.Empty)
                }
            }
            while (out.size < DOCK_SLOT_COUNT) out.add(DockEntry.Empty)
            out.take(DOCK_SLOT_COUNT).toMutableList()
        }.getOrNull()
    }

    fun saveDock(entries: List<DockEntry>) {
        val arr = JSONArray()
        entries.take(DOCK_SLOT_COUNT).forEach { entry ->
            val item = JSONObject()
            when (entry) {
                DockEntry.Empty -> item.put("type", "empty")
                is DockEntry.App -> item
                    .put("type", "app")
                    .put("package", entry.packageName)
                is DockEntry.Folder -> item
                    .put("type", "folder")
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("packages", JSONArray(entry.packageNames))
                    .put("shortcuts", JSONArray().apply {
                        entry.shortcuts.forEach { shortcut ->
                            put(
                                JSONObject()
                                    .put("id", shortcut.id)
                                    .put("packageName", shortcut.packageName)
                                    .put("shortcutId", shortcut.shortcutId)
                                    .put("label", shortcut.label)
                            )
                        }
                    })
            }
            arr.put(item)
        }
        prefs.edit().putString(KEY_DOCK, arr.toString()).apply()
    }

    // ---- Todoist API token ----
    fun loadTodoistToken(): String? = prefs.getString(KEY_TODOIST_TOKEN, null)

    fun saveTodoistToken(token: String) {
        prefs.edit().putString(KEY_TODOIST_TOKEN, token.trim()).apply()
    }

    // ---- settings ----
    fun loadNewsEnabled(): Boolean = prefs.getBoolean(KEY_NEWS, true)

    fun saveNewsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEWS, enabled).apply()
    }

    // ---- hosted App Widgets on the home screen (grid cells) ----
    fun loadWidgets(): MutableList<WidgetEntry> {
        val raw = prefs.getString(KEY_WIDGETS, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<WidgetEntry>()
            var legacyRow = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.has("c")) {
                    out.add(
                        WidgetEntry(
                            id = o.getInt("id"),
                            col = o.getInt("c"),
                            row = o.getInt("r"),
                            spanW = o.getInt("w"),
                            spanH = o.getInt("h")
                        )
                    )
                } else {
                    // Pre-grid format: full-width stack entries with a pixel height in "h".
                    val spanH = Math.round(o.getInt("h") / WidgetGrid.TARGET_CELL_HEIGHT_DP.toFloat())
                        .coerceIn(1, 4)
                    out.add(WidgetEntry(o.getInt("id"), 0, legacyRow, WidgetGrid.COLUMNS, spanH))
                    legacyRow += spanH
                }
            }
            out
        }.getOrDefault(mutableListOf())
    }

    fun saveWidgets(widgets: List<WidgetEntry>) {
        val arr = JSONArray()
        for (w in widgets) {
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("c", w.col)
                    .put("r", w.row)
                    .put("w", w.spanW)
                    .put("h", w.spanH)
            )
        }
        prefs.edit().putString(KEY_WIDGETS, arr.toString()).apply()
    }

    // ---- category overrides (package name -> AppCategory.name) ----
    fun loadOverrides(): MutableMap<String, String> {
        val raw = prefs.getString(KEY_OVERRIDES, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            for (key in obj.keys()) result[key] = obj.getString(key)
            result
        }.getOrDefault(mutableMapOf())
    }

    fun saveOverrides(overrides: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in overrides) obj.put(k, v)
        prefs.edit().putString(KEY_OVERRIDES, obj.toString()).apply()
    }

    // ---- user-created app categories and their package assignments ----
    fun loadCustomCategories(): MutableList<CustomAppCategory> {
        val raw = prefs.getString(KEY_CUSTOM_CATEGORIES, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    if (id.isNotEmpty() && name.isNotEmpty()) add(CustomAppCategory(id, name))
                }
            }.distinctBy { it.id }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun saveCustomCategories(categories: List<CustomAppCategory>) {
        val arr = JSONArray()
        categories.forEach { category ->
            arr.put(JSONObject().put("id", category.id).put("name", category.name))
        }
        prefs.edit().putString(KEY_CUSTOM_CATEGORIES, arr.toString()).apply()
    }

    fun loadCustomCategoryAssignments(): MutableMap<String, String> =
        loadStringMap(KEY_CUSTOM_CATEGORY_ASSIGNMENTS)

    fun saveCustomCategoryAssignments(assignments: Map<String, String>) {
        saveStringMap(KEY_CUSTOM_CATEGORY_ASSIGNMENTS, assignments)
    }

    // ---- app shortcuts on the home screen ----
    fun loadHomeShortcuts(): MutableList<String> {
        val raw = prefs.getString(KEY_HOME_SHORTCUTS, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
                .distinct()
                .toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun saveHomeShortcuts(packageNames: List<String>) {
        prefs.edit().putString(KEY_HOME_SHORTCUTS, JSONArray(packageNames.distinct()).toString()).apply()
    }

    fun loadPinnedHomeShortcuts(): MutableList<PinnedHomeShortcut> {
        val raw = prefs.getString(KEY_PINNED_HOME_SHORTCUTS, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val instanceId = item.optString("instanceId").trim()
                    val packageName = item.optString("packageName").trim()
                    val shortcutId = item.optString("shortcutId").trim()
                    val label = item.optString("label").trim()
                    if (instanceId.isNotEmpty() && packageName.isNotEmpty() && shortcutId.isNotEmpty()) {
                        add(PinnedHomeShortcut(instanceId, packageName, shortcutId, label))
                    }
                }
            }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun savePinnedHomeShortcuts(shortcuts: List<PinnedHomeShortcut>) {
        val arr = JSONArray()
        shortcuts.forEach { shortcut ->
            arr.put(
                JSONObject()
                    .put("instanceId", shortcut.instanceId)
                    .put("packageName", shortcut.packageName)
                    .put("shortcutId", shortcut.shortcutId)
                    .put("label", shortcut.label)
            )
        }
        prefs.edit().putString(KEY_PINNED_HOME_SHORTCUTS, arr.toString()).apply()
    }

    private fun loadStringMap(key: String): MutableMap<String, String> {
        val raw = prefs.getString(key, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                for (name in obj.keys()) put(name, obj.getString(name))
            }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun saveStringMap(key: String, values: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in values) obj.put(k, v)
        prefs.edit().putString(key, obj.toString()).apply()
    }

    companion object {
        private const val KEY_STATS = "stats"
        private const val KEY_FAVORITES = "favorites_ordered"
        private const val KEY_DOCK = "home_dock"
        private const val KEY_OVERRIDES = "overrides"
        private const val KEY_CUSTOM_CATEGORIES = "custom_categories"
        private const val KEY_CUSTOM_CATEGORY_ASSIGNMENTS = "custom_category_assignments"
        private const val KEY_HOME_SHORTCUTS = "home_shortcuts"
        private const val KEY_PINNED_HOME_SHORTCUTS = "pinned_home_shortcuts"
        private const val KEY_NEWS = "news_enabled"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_TODOIST_TOKEN = "todoist_token"
        private const val DOCK_SLOT_COUNT = 4
    }
}

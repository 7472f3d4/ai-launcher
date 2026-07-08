package com.g7r.ailauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.g7r.ailauncher.data.AiMessageContext
import com.g7r.ailauncher.data.AiMessageProvider
import com.g7r.ailauncher.data.AppCategory
import com.g7r.ailauncher.data.AppInfo
import com.g7r.ailauncher.data.AppRepository
import com.g7r.ailauncher.data.AppStat
import com.g7r.ailauncher.data.CalEvent
import com.g7r.ailauncher.data.CalendarRepository
import com.g7r.ailauncher.data.CategorySection
import com.g7r.ailauncher.data.LocationRepository
import com.g7r.ailauncher.data.NewsItem
import com.g7r.ailauncher.data.NewsRepository
import com.g7r.ailauncher.data.Recommender
import com.g7r.ailauncher.data.RuleBasedAiMessage
import com.g7r.ailauncher.data.Storage
import com.g7r.ailauncher.data.TodoTask
import com.g7r.ailauncher.data.TodoistRepository
import com.g7r.ailauncher.data.TodoistState
import com.g7r.ailauncher.data.UsageSignal
import com.g7r.ailauncher.data.UsageStatsHelper
import com.g7r.ailauncher.data.WeatherRepository
import com.g7r.ailauncher.data.WeatherState
import com.g7r.ailauncher.data.WidgetEntry
import com.g7r.ailauncher.data.WidgetGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app.applicationContext
    private val repo = AppRepository(appContext)
    private val storage = Storage(appContext)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _recommendations = MutableStateFlow<List<AppInfo>>(emptyList())
    val recommendations = _recommendations.asStateFlow()

    private val _sections = MutableStateFlow<List<CategorySection>>(emptyList())
    val sections = _sections.asStateFlow()

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites = _favorites.asStateFlow()

    private val _dock = MutableStateFlow<List<AppInfo>>(emptyList())
    val dock = _dock.asStateFlow()

    private val _recentlyUsed = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentlyUsed = _recentlyUsed.asStateFlow()

    private val _recentlyInstalled = MutableStateFlow<List<AppInfo>>(emptyList())
    val recentlyInstalled = _recentlyInstalled.asStateFlow()

    private val _usageAccess = MutableStateFlow(false)
    val usageAccess = _usageAccess.asStateFlow()

    private val _newsEnabled = MutableStateFlow(true)
    val newsEnabled = _newsEnabled.asStateFlow()

    private val _news = MutableStateFlow<List<NewsItem>>(emptyList())
    val news = _news.asStateFlow()

    private val _weather = MutableStateFlow<WeatherState>(WeatherState.NoPermission)
    val weather = _weather.asStateFlow()

    private val _todayEvents = MutableStateFlow<List<CalEvent>>(emptyList())
    val todayEvents = _todayEvents.asStateFlow()

    private val _calendarPermission = MutableStateFlow(false)
    val calendarPermission = _calendarPermission.asStateFlow()

    private val _widgets = MutableStateFlow<List<WidgetEntry>>(emptyList())
    val widgets = _widgets.asStateFlow()

    private val _todoist = MutableStateFlow<TodoistState>(TodoistState.NoToken)
    val todoist = _todoist.asStateFlow()

    private val _aiMessage = MutableStateFlow("")
    val aiMessage = _aiMessage.asStateFlow()

    /** Swap point for a Gemini-backed provider later. */
    var aiMessageProvider: AiMessageProvider = RuleBasedAiMessage

    private val _homeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeEvent = _homeEvent.asSharedFlow()

    private var stats: MutableMap<String, AppStat> = mutableMapOf()
    private var overrides: MutableMap<String, String> = mutableMapOf()
    private var usage: Map<String, UsageSignal> = emptyMap()
    private var dockDefaults: List<String> = emptyList()
    private var lastNews: Long = 0L
    private var lastWeather: Long = 0L
    private var lastTodoist: Long = 0L

    init {
        _widgets.value = storage.loadWidgets()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            overrides = storage.loadOverrides()
            stats = storage.loadStats()
            val favs = storage.loadFavorites()
            usage = UsageStatsHelper.loadUsage(appContext)
            _usageAccess.value = UsageStatsHelper.hasPermission(appContext)
            _newsEnabled.value = storage.loadNewsEnabled()
            dockDefaults = repo.defaultDockPackages()
            val loaded = repo.loadApps(overrides)
            _apps.value = loaded
            _favorites.value = favs
            recompute(loaded, favs)
        }
        loadNews()
        refreshWeather()
        refreshCalendar()
        refreshTodoist()
    }

    // ---- Todoist (公式 REST API) ----
    fun refreshTodoist(force: Boolean = false) {
        val token = storage.loadTodoistToken()
        if (token.isNullOrBlank()) {
            _todoist.value = TodoistState.NoToken
            updateAiMessage()
            return
        }
        val now = System.currentTimeMillis()
        if (!force && _todoist.value is TodoistState.Loaded && now - lastTodoist < TODOIST_TTL_MS) return
        if (_todoist.value !is TodoistState.Loaded) _todoist.value = TodoistState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = TodoistRepository.fetch(token)) {
                is TodoistRepository.FetchResult.Ok -> {
                    _todoist.value = TodoistState.Loaded(result.tasks)
                    lastTodoist = System.currentTimeMillis()
                }
                is TodoistRepository.FetchResult.Fail ->
                    _todoist.value = TodoistState.Error(result.message)
            }
            updateAiMessage()
        }
    }

    fun setTodoistToken(token: String) {
        if (token.isBlank()) return
        storage.saveTodoistToken(token)
        refreshTodoist(force = true)
    }

    fun completeTodoistTask(task: TodoTask) {
        val current = _todoist.value
        if (current is TodoistState.Loaded) {
            _todoist.value = TodoistState.Loaded(current.tasks.filterNot { it.id == task.id })
        }
        viewModelScope.launch(Dispatchers.IO) {
            val token = storage.loadTodoistToken() ?: return@launch
            if (!TodoistRepository.close(token, task.id)) {
                refreshTodoist(force = true)
            } else {
                updateAiMessage()
            }
        }
    }

    fun addTodoistTask(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val token = storage.loadTodoistToken() ?: return@launch
            TodoistRepository.add(token, content.trim())
            refreshTodoist(force = true)
        }
    }

    // ---- AI message (Discover header) ----
    private fun updateAiMessage() {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        _aiMessage.value = aiMessageProvider.generate(
            AiMessageContext(
                now = now,
                hour = hour,
                todayEvents = _todayEvents.value,
                weather = _weather.value,
                topAppLabel = _recommendations.value.firstOrNull()?.label,
                tasks = (_todoist.value as? TodoistState.Loaded)?.tasks ?: emptyList()
            )
        )
    }

    // ---- hosted App Widgets (grid placement) ----
    fun addWidget(id: Int, spanW: Int, spanH: Int) {
        val w = spanW.coerceIn(1, WidgetGrid.COLUMNS)
        val h = spanH.coerceAtLeast(1)
        val (col, row) = WidgetGrid.findFreeCell(_widgets.value, w, h)
        val list = _widgets.value + WidgetEntry(id, col, row, w, h)
        _widgets.value = list
        viewModelScope.launch(Dispatchers.IO) { storage.saveWidgets(list) }
    }

    fun removeWidget(id: Int) {
        val list = _widgets.value.filterNot { it.id == id }
        _widgets.value = list
        viewModelScope.launch(Dispatchers.IO) { storage.saveWidgets(list) }
    }

    fun moveWidget(id: Int, col: Int, row: Int) {
        val list = _widgets.value.map {
            if (it.id == id) {
                it.copy(
                    col = col.coerceIn(0, WidgetGrid.COLUMNS - it.spanW),
                    row = row.coerceAtLeast(0)
                )
            } else it
        }
        _widgets.value = list
        viewModelScope.launch(Dispatchers.IO) { storage.saveWidgets(list) }
    }

    fun resizeWidget(id: Int, col: Int, row: Int, spanW: Int, spanH: Int) {
        val list = _widgets.value.map {
            if (it.id == id) {
                val w = spanW.coerceIn(1, WidgetGrid.COLUMNS)
                it.copy(
                    col = col.coerceIn(0, WidgetGrid.COLUMNS - w),
                    row = row.coerceAtLeast(0),
                    spanW = w,
                    spanH = spanH.coerceAtLeast(1)
                )
            } else it
        }
        _widgets.value = list
        viewModelScope.launch(Dispatchers.IO) { storage.saveWidgets(list) }
    }

    // ---- weather ----
    fun refreshWeather(force: Boolean = false) {
        if (!LocationRepository.hasPermission(appContext)) {
            _weather.value = WeatherState.NoPermission
            return
        }
        val now = System.currentTimeMillis()
        val current = _weather.value
        if (!force && current is WeatherState.Loaded && now - lastWeather < WEATHER_TTL_MS) return
        _weather.value = WeatherState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val point = LocationRepository.current(appContext)
            if (point == null) {
                _weather.value = WeatherState.Error("現在地を取得できません")
                return@launch
            }
            val place = LocationRepository.placeName(appContext, point)
            val data = WeatherRepository.fetch(point.lat, point.lon)
            _weather.value = if (data != null) {
                lastWeather = System.currentTimeMillis()
                WeatherState.Loaded(place, data.temperatureC, data.code)
            } else {
                WeatherState.Error("天気を取得できません")
            }
            updateAiMessage()
        }
    }

    // ---- calendar ----
    fun refreshCalendar() {
        _calendarPermission.value = CalendarRepository.hasPermission(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            _todayEvents.value = CalendarRepository.todayEvents(appContext)
            updateAiMessage()
        }
    }

    fun openCalendarApp() = CalendarRepository.openCalendarApp(appContext)

    fun loadNews(force: Boolean = false) {
        if (!storage.loadNewsEnabled()) {
            _news.value = emptyList()
            return
        }
        val now = System.currentTimeMillis()
        if (!force && _news.value.isNotEmpty() && now - lastNews < NEWS_TTL_MS) return
        viewModelScope.launch(Dispatchers.IO) {
            val items = NewsRepository.fetchHeadlines()
            if (items.isNotEmpty()) {
                _news.value = items
                lastNews = System.currentTimeMillis()
            }
        }
    }

    fun setNewsEnabled(enabled: Boolean) {
        _newsEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) { storage.saveNewsEnabled(enabled) }
        if (enabled) loadNews(force = true) else _news.value = emptyList()
    }

    private fun recompute(loaded: List<AppInfo>, favs: List<String>) {
        val now = System.currentTimeMillis()
        _recommendations.value = Recommender.recommend(loaded, stats, favs.toSet(), usage, now)
        _sections.value = buildSections(loaded, now)
        _dock.value = buildDock(loaded, favs)
        _recentlyUsed.value = loaded
            .map { it to lastUsed(it) }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(12)
            .map { it.first }
        _recentlyInstalled.value = loaded
            .filter { it.firstInstallTime > 0L }
            .sortedByDescending { it.firstInstallTime }
            .take(12)
        updateAiMessage()
    }

    private fun buildDock(loaded: List<AppInfo>, favs: List<String>): List<AppInfo> {
        val byPkg = loaded.associateBy { it.packageName }
        val defaults = dockDefaults.mapNotNull { byPkg[it] }
        val defaultPkgs = defaults.map { it.packageName }.toSet()
        val favApps = favs.mapNotNull { byPkg[it] }.filter { it.packageName !in defaultPkgs }
        return (defaults + favApps).distinctBy { it.packageName }.take(6)
    }

    private fun lastUsed(app: AppInfo): Long =
        maxOf(stats[app.packageName]?.lastLaunch ?: 0L, usage[app.packageName]?.lastTimeUsed ?: 0L)

    private fun buildSections(loaded: List<AppInfo>, now: Long): List<CategorySection> {
        val recentWindow = now - RECENTLY_ADDED_DAYS * 24L * 60 * 60 * 1000
        val out = ArrayList<CategorySection>()
        val recent = loaded
            .filter { it.firstInstallTime in (recentWindow + 1)..now }
            .sortedByDescending { it.firstInstallTime }
        if (recent.isNotEmpty()) out.add(CategorySection(AppCategory.RECENTLY_ADDED, recent))
        val byCat = loaded.groupBy { it.category }
        for (cat in AppCategory.entries) {
            if (cat == AppCategory.RECENTLY_ADDED) continue
            val items = byCat[cat]?.sortedBy { it.label.lowercase() }
            if (!items.isNullOrEmpty()) out.add(CategorySection(cat, items))
        }
        return out
    }

    fun onLaunch(app: AppInfo) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val stat = stats.getOrPut(app.packageName) { AppStat() }
        stat.record(now, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.DAY_OF_WEEK) - 1)
        viewModelScope.launch(Dispatchers.IO) { storage.saveStats(stats) }
        _recommendations.value =
            Recommender.recommend(_apps.value, stats, _favorites.value.toSet(), usage, now)
        repo.launch(app)
    }

    fun toggleFavorite(packageName: String) {
        val updated = _favorites.value.toMutableList()
        if (!updated.remove(packageName)) updated.add(packageName)
        _favorites.value = updated
        viewModelScope.launch(Dispatchers.IO) { storage.saveFavorites(updated) }
        recompute(_apps.value, updated)
    }

    fun isFavorite(packageName: String): Boolean = _favorites.value.contains(packageName)

    fun setCategory(packageName: String, category: AppCategory) {
        overrides[packageName] = category.name
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveOverrides(overrides)
            val loaded = repo.loadApps(overrides)
            _apps.value = loaded
            recompute(loaded, _favorites.value)
        }
    }

    fun openAppInfo(packageName: String) = repo.openAppInfo(packageName)

    fun requestUsageAccess() = UsageStatsHelper.requestPermission(appContext)

    fun requestHome() {
        _homeEvent.tryEmit(Unit)
    }

    companion object {
        private const val RECENTLY_ADDED_DAYS = 7
        private const val NEWS_TTL_MS = 15L * 60 * 1000
        private const val WEATHER_TTL_MS = 10L * 60 * 1000
        private const val TODOIST_TTL_MS = 5L * 60 * 1000
    }
}

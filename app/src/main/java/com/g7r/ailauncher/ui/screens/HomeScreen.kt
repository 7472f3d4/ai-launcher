package com.g7r.ailauncher.ui.screens

import android.Manifest
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.g7r.ailauncher.LauncherViewModel
import com.g7r.ailauncher.data.AppInfo
import com.g7r.ailauncher.data.CalEvent
import com.g7r.ailauncher.data.ExternalApps
import com.g7r.ailauncher.data.WeatherCodes
import com.g7r.ailauncher.data.WeatherState
import com.g7r.ailauncher.data.WidgetHostManager
import com.g7r.ailauncher.ui.components.AppGridItem
import com.g7r.ailauncher.ui.components.EditableDock
import com.g7r.ailauncher.ui.components.WidgetCanvas
import com.g7r.ailauncher.ui.components.WidgetPickerSheet
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    widgetHost: WidgetHostManager,
    onAddWidget: (AppWidgetProviderInfo) -> Unit
) {
    val context = LocalContext.current
    val recommendations by viewModel.recommendations.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()
    val dock by viewModel.dock.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val todayEvents by viewModel.todayEvents.collectAsState()
    val calendarPermission by viewModel.calendarPermission.collectAsState()
    val widgets by viewModel.widgets.collectAsState()
    val newsEnabled by viewModel.newsEnabled.collectAsState()
    val favorites = favoritesList.toSet()

    var showEditMenu by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showHomeSettings by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var selectedWidget by remember { mutableStateOf<Int?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.refreshWeather(force = true) }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.refreshCalendar() }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { selectedWidget = null },
                        onLongPress = {
                            selectedWidget = null
                            showEditMenu = true
                        }
                    )
                }
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            HeaderRow(
                weather = weather,
                onOpenClock = { ExternalApps.openClock(context) },
                onOpenWeather = { ExternalApps.openWeather(context) },
                onRequestLocation = {
                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            )
            Spacer(Modifier.height(16.dp))
            CalendarCard(
                events = todayEvents,
                hasPermission = calendarPermission,
                onRequestPermission = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
                onOpenCalendar = viewModel::openCalendarApp,
                modifier = Modifier.fillMaxWidth()
            )
            WidgetCanvas(
                widgets = widgets,
                host = widgetHost,
                selectedId = selectedWidget,
                onSelect = { selectedWidget = it },
                onLongPressEmptySpace = {
                    selectedWidget = null
                    showEditMenu = true
                },
                onMove = viewModel::moveWidget,
                onResize = viewModel::resizeWidget,
                onRemove = { id ->
                    widgetHost.deleteId(id)
                    viewModel.removeWidget(id)
                    selectedWidget = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp, bottom = 8.dp)
            )
            RecommendedSection(
                apps = recommendations,
                favorites = favorites,
                onLaunch = viewModel::onLaunch,
                onToggleFavorite = viewModel::toggleFavorite,
                onAppInfo = viewModel::openAppInfo
            )
            Spacer(Modifier.height(18.dp))
            EditableDock(
                dock = dock,
                apps = apps,
                onLaunch = viewModel::onLaunch,
                onSetApp = viewModel::setDockApp,
                onSetFolder = viewModel::setDockFolder,
                onClear = viewModel::clearDockSlot,
                onMove = viewModel::moveDockItem,
                onMerge = viewModel::mergeDockItems,
                onAppInfo = viewModel::openAppInfo
            )
            Spacer(Modifier.height(10.dp))
        }

        // Pixel 風の編集モード（長押し → 下部メニュー）
        EditMenuOverlay(
            visible = showEditMenu,
            onDismiss = { showEditMenu = false },
            onWallpaper = {
                showEditMenu = false
                ExternalApps.openWallpaperPicker(context)
            },
            onWidgets = { showEditMenu = false; showWidgetPicker = true },
            onSettings = { showEditMenu = false; showHomeSettings = true }
        )
    }

    if (showWidgetPicker) {
        WidgetPickerSheet(
            manager = widgetHost.manager,
            onPick = { info ->
                showWidgetPicker = false
                onAddWidget(info)
            },
            onDismiss = { showWidgetPicker = false }
        )
    }
    if (showHomeSettings) {
        HomeSettingsDialog(
            newsEnabled = newsEnabled,
            onNewsChange = viewModel::setNewsEnabled,
            onTodoistToken = { showHomeSettings = false; showTokenDialog = true },
            onUsageAccess = { viewModel.requestUsageAccess() },
            onDismiss = { showHomeSettings = false }
        )
    }
    if (showTokenDialog) {
        TodoistTokenDialog(
            onSave = { viewModel.setTodoistToken(it); showTokenDialog = false },
            onDismiss = { showTokenDialog = false }
        )
    }
}

// ---------- header: date + live clock (left) / weather (right) ----------

@Composable
private fun HeaderRow(
    weather: WeatherState,
    onOpenClock: () -> Unit,
    onOpenWeather: () -> Unit,
    onRequestLocation: () -> Unit
) {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val year = cal.get(Calendar.YEAR)
    val reiwaYear = year - 2018
    val dateLine = "%d(R%d)年%d月%d日(%s)".format(
        year,
        reiwaYear,
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        japaneseWeekday(cal.get(Calendar.DAY_OF_WEEK))
    )
    val timeLine = "%02d:%02d:%02d".format(
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND)
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = dateLine,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = timeLine,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenClock
                )
            )
        }
        Spacer(Modifier.width(8.dp))
        WeatherColumn(weather, onOpenWeather, onRequestLocation)
    }
}

@Composable
private fun WeatherColumn(
    weather: WeatherState,
    onOpenWeather: () -> Unit,
    onRequestLocation: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val titleStyle = MaterialTheme.typography.titleLarge
    val smallStyle = MaterialTheme.typography.bodyMedium
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (weather) {
            is WeatherState.NoPermission -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onRequestLocation)
            ) {
                Icon(Icons.Rounded.LocationOn, null, tint = primary, modifier = Modifier.size(30.dp))
                Text(
                    "位置情報を\n許可",
                    style = smallStyle,
                    color = primary,
                    textAlign = TextAlign.Center
                )
            }

            is WeatherState.Loading -> Text(
                "天気\n取得中…",
                style = smallStyle,
                color = secondary,
                textAlign = TextAlign.Center
            )

            is WeatherState.Loaded -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenWeather
                )
            ) {
                Icon(
                    weatherIcon(weather.code), null,
                    tint = primary,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "${weather.temperatureC}℃ ${WeatherCodes.description(weather.code)}",
                    style = titleStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = primary
                )
                Text(
                    weather.placeName ?: "現在地",
                    style = smallStyle,
                    color = secondary
                )
            }

            is WeatherState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onRequestLocation)
            ) {
                Icon(Icons.Rounded.LocationOn, null, tint = secondary, modifier = Modifier.size(26.dp))
                Text(
                    "再試行",
                    style = smallStyle,
                    color = secondary
                )
            }
        }
    }
}

// ---------- calendar card ----------

@Composable
private fun CalendarCard(
    events: List<CalEvent>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFmt = remember { SimpleDateFormat("H:mm", Locale.JAPAN) }
    val now = System.currentTimeMillis()
    val next = events.firstOrNull { !it.allDay && it.begin > now }
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 3.dp,
        onClick = onOpenCalendar,
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "カレンダー",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            when {
                !hasPermission -> TextButton(
                    onClick = onRequestPermission,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("予定を表示", style = MaterialTheme.typography.titleSmall) }

                events.isEmpty() -> Text(
                    "今日の予定はありません",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (next != null) {
                        Text(
                            next.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "今日",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                timeFmt.format(Date(next.begin)),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            "次の予定はありません",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (events.size > (if (next != null) 1 else 0)) {
                        Text(
                            "今日の予定 ${events.size}件",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoistTokenDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(token) }, enabled = token.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text("Todoistと連携") },
        text = {
            Column {
                Text(
                    "Todoist の 設定 → 連携機能 → 開発者 タブにある APIトークンを貼り付けてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    placeholder = { Text("APIトークン") }
                )
            }
        }
    )
}

// ---------- Pixel-style edit mode (long-press) ----------

@Composable
private fun EditMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 56.dp, start = 8.dp, end = 8.dp)
            ) {
                EditMenuChip(Icons.Rounded.Wallpaper, "壁紙", onWallpaper)
                EditMenuChip(Icons.Rounded.Widgets, "ウィジェット", onWidgets)
                EditMenuChip(Icons.Rounded.Settings, "ホーム設定", onSettings)
            }
        }
    }
}

@Composable
private fun EditMenuChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        onClick = onClick
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HomeSettingsDialog(
    newsEnabled: Boolean,
    onNewsChange: (Boolean) -> Unit,
    onTodoistToken: () -> Unit,
    onUsageAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        title = { Text("ホームの設定") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Discoverのニュース", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = newsEnabled, onCheckedChange = onNewsChange)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onTodoistToken, contentPadding = PaddingValues(0.dp)) {
                    Text("Todoist APIトークンを設定")
                }
                TextButton(onClick = onUsageAccess, contentPadding = PaddingValues(0.dp)) {
                    Text("使用状況アクセスを許可（おすすめ精度向上）")
                }
            }
        }
    )
}

// ---------- AI recommendations + dock ----------

@Composable
private fun RecommendedSection(
    apps: List<AppInfo>,
    favorites: Set<String>,
    onLaunch: (AppInfo) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAppInfo: (String) -> Unit
) {
    val labelStyle = MaterialTheme.typography.labelMedium
    val onBackground = MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "AIおすすめアプリ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = onBackground
            )
        }
        Spacer(Modifier.height(14.dp))
        val items = apps.take(8)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (row in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 4) {
                        val app = items.getOrNull(row * 4 + col)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (app != null) {
                                AppGridItem(
                                    app = app,
                                    isFavorite = app.packageName in favorites,
                                    onLaunch = onLaunch,
                                    onToggleFavorite = onToggleFavorite,
                                    onAppInfo = onAppInfo,
                                    iconSize = 56.dp,
                                    labelColor = onBackground,
                                    labelStyle = labelStyle
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun weatherIcon(code: Int): ImageVector = when (code) {
    0, 1 -> Icons.Rounded.WbSunny
    2, 3, in 45..48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.Grain
    in 71..77, in 85..86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Bolt
    else -> Icons.Rounded.WbSunny
}

internal fun japaneseWeekday(calendarDow: Int): String = when (calendarDow) {
    Calendar.MONDAY -> "月"
    Calendar.TUESDAY -> "火"
    Calendar.WEDNESDAY -> "水"
    Calendar.THURSDAY -> "木"
    Calendar.FRIDAY -> "金"
    Calendar.SATURDAY -> "土"
    else -> "日"
}

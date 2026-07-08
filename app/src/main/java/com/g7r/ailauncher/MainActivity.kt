package com.g7r.ailauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.g7r.ailauncher.data.WidgetGrid
import com.g7r.ailauncher.data.WidgetHostManager
import com.g7r.ailauncher.ui.LauncherRoot
import com.g7r.ailauncher.ui.theme.AILauncherTheme
import kotlin.math.ceil

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var widgetHost: WidgetHostManager
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** Step 3 (optional): the widget's own configuration activity finished. */
    private val configureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) commitWidget(id) else widgetHost.deleteId(id)
    }

    /** Step 2 (optional): the system "allow this launcher to bind widgets" dialog. */
    private val bindLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            configureOrCommit(id)
        } else {
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            widgetHost.deleteId(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark-only launcher: pin the system bars to light icons on a transparent
        // scrim so they stay visible on our black background even when the device
        // itself is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        widgetHost = WidgetHostManager(this)
        setContent {
            AILauncherTheme {
                LauncherRoot(viewModel, widgetHost, onAddWidget = ::addWidget)
            }
        }
        viewModel.refresh()
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** Home button pressed while we're already the foreground launcher → snap back to center page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.requestHome()
    }

    /** Step 1: picker sheet selected a provider — bind (asking permission if needed). */
    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateId()
        pendingWidgetId = id
        val bound = runCatching {
            widgetHost.manager.bindAppWidgetIdIfAllowed(id, info.provider)
        }.getOrDefault(false)
        if (bound) {
            configureOrCommit(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            runCatching { bindLauncher.launch(intent) }.onFailure {
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                widgetHost.deleteId(id)
            }
        }
    }

    private fun configureOrCommit(id: Int) {
        val info = widgetHost.manager.getAppWidgetInfo(id)
        val configure = info?.configure
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            runCatching { configureLauncher.launch(intent) }.onFailure {
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                commitWidget(id)
            }
        } else {
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            commitWidget(id)
        }
    }

    private fun commitWidget(id: Int) {
        val info = widgetHost.manager.getAppWidgetInfo(id)
        if (info == null) {
            widgetHost.deleteId(id)
            return
        }
        // Default spans the way Launcher3 does: the provider's target cells when
        // declared, otherwise the min sizes rounded up to grid cells.
        val dm = resources.displayMetrics
        val cellWPx = (dm.widthPixels - 32 * dm.density) / WidgetGrid.COLUMNS
        val cellHPx = WidgetGrid.TARGET_CELL_HEIGHT_DP * dm.density
        val spanW = (if (info.targetCellWidth > 0) info.targetCellWidth
            else ceil(info.minWidth / cellWPx).toInt())
            .coerceIn(1, WidgetGrid.COLUMNS)
        val spanH = (if (info.targetCellHeight > 0) info.targetCellHeight
            else ceil(info.minHeight / cellHPx).toInt())
            .coerceIn(1, 4)
        viewModel.addWidget(id, spanW, spanH)
    }
}

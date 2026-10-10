package com.g7r.ailauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.g7r.ailauncher.data.WidgetGrid
import com.g7r.ailauncher.data.WidgetHostManager
import com.g7r.ailauncher.ui.components.AppIconImage
import com.g7r.ailauncher.ui.LauncherRoot
import com.g7r.ailauncher.ui.theme.AILauncherTheme
import kotlin.math.ceil

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var widgetHost: WidgetHostManager
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingPinConfirmation by mutableStateOf<PendingPinConfirmation?>(null)

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
                pendingPinConfirmation?.let { pending ->
                    val launcherApps = getSystemService(LauncherApps::class.java)
                    val icon = remember(pending.info) {
                        runCatching {
                            launcherApps?.getShortcutIconDrawable(
                                pending.info,
                                resources.displayMetrics.densityDpi
                            )
                        }.getOrNull()
                    }
                    val label = pending.info.shortLabel?.toString()?.takeIf(String::isNotBlank)
                        ?: pending.info.longLabel?.toString()?.takeIf(String::isNotBlank)
                        ?: pending.info.`package`
                    AlertDialog(
                        onDismissRequest = { pendingPinConfirmation = null },
                        title = { Text("ホーム画面に追加") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (icon != null) AppIconImage(icon, 48.dp)
                                Spacer(Modifier.height(8.dp))
                                Text("「$label」をホーム画面に追加しますか？")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { acceptPinnedShortcut(pending) }) {
                                Text("追加")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingPinConfirmation = null }) {
                                Text("キャンセル")
                            }
                        }
                    )
                }
            }
        }
        handlePinShortcutIntent(intent)
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
        if (!handlePinShortcutIntent(intent)) viewModel.requestHome()
    }

    private fun handlePinShortcutIntent(intent: Intent): Boolean {
        if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) return false
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return true
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull() ?: return true
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return true
        val info = runCatching { request.shortcutInfo }.getOrNull() ?: return true
        pendingPinConfirmation = PendingPinConfirmation(request, info)
        return true
    }

    private fun acceptPinnedShortcut(pending: PendingPinConfirmation) {
        val accepted = runCatching { pending.request.accept() }.getOrDefault(false)
        if (accepted) {
            val info = pending.info
            val label = info.shortLabel?.toString()?.takeIf(String::isNotBlank)
                ?: info.longLabel?.toString()?.takeIf(String::isNotBlank)
                ?: info.`package`
            viewModel.addPinnedHomeShortcut(info.`package`, info.id, label)
            viewModel.requestShortcutsPage()
        }
        pendingPinConfirmation = null
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

    private data class PendingPinConfirmation(
        val request: LauncherApps.PinItemRequest,
        val info: ShortcutInfo
    )
}

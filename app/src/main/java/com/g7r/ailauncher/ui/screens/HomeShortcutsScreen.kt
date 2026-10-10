package com.g7r.ailauncher.ui.screens

import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.g7r.ailauncher.LauncherViewModel
import com.g7r.ailauncher.data.DockItem
import com.g7r.ailauncher.ui.components.HomeShortcutGrid
import com.g7r.ailauncher.ui.components.HomeShortcutTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeShortcutsScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsState()
    val appShortcutPackages by viewModel.homeShortcuts.collectAsState()
    val pinnedShortcuts by viewModel.pinnedHomeShortcuts.collectAsState()
    val dockItems by viewModel.dock.collectAsState()
    val folderTargets = dockItems.filterIsInstance<DockItem.Folder>()
    val launcherApps = remember(context) { context.getSystemService(LauncherApps::class.java) }

    val pinnedShortcutIcons by produceState(
        initialValue = emptyMap<String, Drawable>(),
        pinnedShortcuts,
        apps,
        context
    ) {
        val launcher = launcherApps
        value = if (launcher == null) {
            emptyMap()
        } else {
            withContext(Dispatchers.IO) {
                pinnedShortcuts.mapNotNull { shortcut ->
                    val info = runCatching {
                        launcher.getShortcuts(
                            LauncherApps.ShortcutQuery()
                                .setPackage(shortcut.packageName)
                                .setShortcutIds(listOf(shortcut.shortcutId))
                                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
                            Process.myUserHandle()
                        )?.firstOrNull()
                    }.getOrNull()
                    val icon = info?.let {
                        runCatching {
                            launcher.getShortcutBadgedIconDrawable(
                                it,
                                context.resources.displayMetrics.densityDpi
                            )
                        }.getOrNull()
                    } ?: apps.firstOrNull { it.packageName == shortcut.packageName }?.icon
                        ?: runCatching {
                            launcher.getApplicationInfo(shortcut.packageName, 0, Process.myUserHandle())
                                .loadIcon(context.packageManager)
                        }.getOrNull()
                    icon?.let { shortcut.instanceId to it }
                }.toMap()
            }
        }
    }

    val tiles = buildList {
        appShortcutPackages.forEach { packageName ->
            val app = apps.firstOrNull { it.packageName == packageName } ?: return@forEach
            add(
                HomeShortcutTile(
                    id = "app:${app.packageName}",
                    label = app.label,
                    icon = app.icon,
                    onLaunch = { viewModel.onLaunch(app) },
                    onRemove = { viewModel.removeHomeShortcut(app.packageName) },
                    onMoveToFolder = { folderId ->
                        viewModel.moveHomeShortcutIntoFolder(app.packageName, folderId)
                    }
                )
            )
        }
        pinnedShortcuts.forEach { shortcut ->
            add(
                HomeShortcutTile(
                    id = "pin:${shortcut.instanceId}",
                    label = shortcut.label.ifBlank { shortcut.packageName },
                    icon = pinnedShortcutIcons[shortcut.instanceId],
                    onLaunch = {
                        val result = runCatching {
                            launcherApps?.startShortcut(
                                shortcut.packageName,
                                shortcut.shortcutId,
                                null,
                                null,
                                Process.myUserHandle()
                            ) ?: error("LauncherApps is unavailable")
                        }
                        if (result.isFailure) {
                            Toast.makeText(context, "ショートカットを開けませんでした", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemove = { viewModel.removePinnedHomeShortcut(shortcut.instanceId) },
                    onMoveToFolder = { folderId ->
                        viewModel.movePinnedShortcutIntoFolder(shortcut.instanceId, folderId)
                    }
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "ショートカットフォルダ",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "右上のハンドルをドラッグして既存フォルダへ追加できます。長押しで削除できます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (tiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ここに置けます",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "右のアプリ一覧でアプリを長押しするか、Chromeのメニューからホーム画面に追加すると表示されます。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            HomeShortcutGrid(
                items = tiles,
                folders = folderTargets,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

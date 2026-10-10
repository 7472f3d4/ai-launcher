package com.g7r.ailauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.g7r.ailauncher.LauncherViewModel
import com.g7r.ailauncher.data.WidgetHostManager
import com.g7r.ailauncher.ui.screens.AppListScreen
import com.g7r.ailauncher.ui.screens.DiscoverScreen
import com.g7r.ailauncher.ui.screens.HomeScreen
import com.g7r.ailauncher.ui.screens.HomeShortcutsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * [Discover] ← [Home] → [App list], with [Shortcuts] inserted when shortcuts exist.
 * The system Home button snaps back to the center page.
 */
@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel,
    widgetHost: WidgetHostManager,
    onAddWidget: (AppWidgetProviderInfo) -> Unit
) {
    val appShortcutPackages by viewModel.homeShortcuts.collectAsState()
    val installedApps by viewModel.apps.collectAsState()
    val pinnedShortcuts by viewModel.pinnedHomeShortcuts.collectAsState()
    val hasShortcutPage = appShortcutPackages.any { packageName ->
        installedApps.any { it.packageName == packageName }
    } || pinnedShortcuts.isNotEmpty()
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { if (hasShortcutPage) 4 else 3 }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(hasShortcutPage) {
        if (!hasShortcutPage && pagerState.currentPage >= 2) {
            pagerState.animateScrollToPage(1)
        }
    }

    LaunchedEffect(Unit) {
        launch {
            viewModel.homeEvent.collect {
                pagerState.animateScrollToPage(1)
            }
        }
        launch {
            viewModel.shortcutsPageEvent.collect {
                snapshotFlow { pagerState.pageCount }.first { it >= 4 }
                pagerState.animateScrollToPage(2)
            }
        }
    }

    // Launcher-controlled base background: pure white (light) / pure black (dark).
    // Every page draws over this, so the home experience never depends on the
    // device wallpaper — the theme alone drives the light/dark base.
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> DiscoverScreen(viewModel)
                1 -> HomeScreen(
                    viewModel = viewModel,
                    widgetHost = widgetHost,
                    onAddWidget = onAddWidget
                )
                2 -> if (hasShortcutPage) {
                    HomeShortcutsScreen(viewModel)
                } else {
                    AppListScreen(
                        viewModel = viewModel,
                        onClose = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                }
                else -> AppListScreen(
                    viewModel = viewModel,
                    onClose = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
            }
        }
    }
}

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.g7r.ailauncher.LauncherViewModel
import com.g7r.ailauncher.data.WidgetHostManager
import com.g7r.ailauncher.ui.screens.AppListScreen
import com.g7r.ailauncher.ui.screens.DiscoverScreen
import com.g7r.ailauncher.ui.screens.HomeScreen
import kotlinx.coroutines.launch

/**
 * Three-page launcher: [Discover] ← [Home] → [App list].
 * The system Home button snaps back to the center page.
 */
@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel,
    widgetHost: WidgetHostManager,
    onAddWidget: (AppWidgetProviderInfo) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.homeEvent.collect {
            scope.launch { pagerState.animateScrollToPage(1) }
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
                else -> AppListScreen(
                    viewModel = viewModel,
                    onClose = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
            }
        }
    }
}

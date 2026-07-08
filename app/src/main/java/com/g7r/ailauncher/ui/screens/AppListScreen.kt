package com.g7r.ailauncher.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.g7r.ailauncher.LauncherViewModel
import com.g7r.ailauncher.data.AppCategory
import com.g7r.ailauncher.data.AppInfo
import com.g7r.ailauncher.data.CategorySection
import com.g7r.ailauncher.data.SuggestRepository
import com.g7r.ailauncher.ui.components.AppGridItem
import com.g7r.ailauncher.ui.components.AppIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@Composable
fun AppListScreen(viewModel: LauncherViewModel, onClose: () -> Unit) {
    val sections by viewModel.sections.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()
    val favorites = favoritesList.toSet()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var categoryDialogApp by remember { mutableStateOf<AppInfo?>(null) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    // Launch resets the drawer to its initial state so the next open starts fresh.
    val launchApp: (AppInfo) -> Unit = { app ->
        viewModel.onLaunch(app)
        selectedCategory = null
        query = ""
        onClose()
    }
    val openWeb: (String) -> Unit = { q ->
        openWebSearch(context, q)
        selectedCategory = null
        query = ""
        onClose()
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
        } else {
            delay(250)
            suggestions = withContext(Dispatchers.IO) { SuggestRepository.suggest(query) }
        }
    }

    BackHandler {
        when {
            selectedCategory != null -> selectedCategory = null
            query.isNotEmpty() -> query = ""
            else -> onClose()
        }
    }

    val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    // Dark-only launcher: opaque black base.
    val bgAlpha = 1f
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = bgAlpha))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset)
                .padding(horizontal = 16.dp)
        ) {
            val active = selectedCategory
            if (active == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "アプリ一覧",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                SearchField(query) { query = it }
                Spacer(Modifier.height(12.dp))
                if (query.isBlank()) {
                    CategoryGrid(
                        sections = sections,
                        bottomInset = bottomInset,
                        onSelect = { selectedCategory = it }
                    )
                } else {
                    val q = query.filterNot { it.isWhitespace() }
                    val matches = apps.filter { app ->
                        app.label.filterNot { it.isWhitespace() }.contains(q, ignoreCase = true)
                    }.take(20)
                    SearchResults(
                        apps = matches,
                        query = query,
                        suggestions = suggestions,
                        favorites = favorites,
                        viewModel = viewModel,
                        bottomInset = bottomInset,
                        onChangeCategory = { categoryDialogApp = it },
                        onLaunch = launchApp,
                        onWeb = openWeb
                    )
                }
            } else {
                val section = sections.firstOrNull { it.category == active }
                CategoryDetail(
                    section = section,
                    favorites = favorites,
                    viewModel = viewModel,
                    bottomInset = bottomInset,
                    onBack = { selectedCategory = null },
                    onChangeCategory = { categoryDialogApp = it },
                    onLaunch = launchApp
                )
            }
        }
    }

    categoryDialogApp?.let { app ->
        CategoryPickerDialog(
            app = app,
            onDismiss = { categoryDialogApp = null },
            onPick = { category ->
                viewModel.setCategory(app.packageName, category)
                categoryDialogApp = null
            }
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        placeholder = { Text("アプリを検索") },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CategoryGrid(
    sections: List<CategorySection>,
    bottomInset: androidx.compose.ui.unit.Dp,
    onSelect: (AppCategory) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = bottomInset + 16.dp)
    ) {
        items(sections, key = { it.category.name }) { section ->
            CategoryCard(section = section, onClick = { onSelect(section.category) })
        }
    }
}

@Composable
private fun CategoryCard(section: CategorySection, onClick: () -> Unit) {
    // 全カテゴリで同一サイズ（アプリ数に依存しない固定高さ）
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = lightCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                section.category.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${section.apps.size}個",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            // 2 large icons (slot height fixed)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(44.dp)
            ) {
                section.apps.take(2).forEach { app ->
                    AppIconImage(app.icon, 44.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            // 4 small icons (slot height fixed)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(24.dp)
            ) {
                section.apps.drop(2).take(4).forEach { app ->
                    AppIconImage(app.icon, 24.dp)
                }
            }
        }
    }
}

@Composable
private fun CategoryDetail(
    section: CategorySection?,
    favorites: Set<String>,
    viewModel: LauncherViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onChangeCategory: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                section?.category?.displayName ?: "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(8.dp))
        AppFlowGrid(
            apps = section?.apps.orEmpty(),
            favorites = favorites,
            viewModel = viewModel,
            bottomInset = bottomInset,
            onChangeCategory = onChangeCategory,
            onLaunch = onLaunch
        )
    }
}

@Composable
private fun AppFlowGrid(
    apps: List<AppInfo>,
    favorites: Set<String>,
    viewModel: LauncherViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onChangeCategory: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 76.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = bottomInset + 16.dp)
    ) {
        items(apps, key = { it.key }) { app ->
            AppGridItem(
                app = app,
                isFavorite = app.packageName in favorites,
                onLaunch = onLaunch,
                onToggleFavorite = viewModel::toggleFavorite,
                onAppInfo = viewModel::openAppInfo,
                iconSize = 52.dp,
                onChangeCategory = onChangeCategory
            )
        }
    }
}

/** App matches plus Google web suggestions, Pixel-drawer style. */
@Composable
private fun SearchResults(
    apps: List<AppInfo>,
    query: String,
    suggestions: List<String>,
    favorites: Set<String>,
    viewModel: LauncherViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    onChangeCategory: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onWeb: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 76.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = bottomInset + 16.dp)
    ) {
        items(apps, key = { it.key }) { app ->
            AppGridItem(
                app = app,
                isFavorite = app.packageName in favorites,
                onLaunch = onLaunch,
                onToggleFavorite = viewModel::toggleFavorite,
                onAppInfo = viewModel::openAppInfo,
                iconSize = 52.dp,
                onChangeCategory = onChangeCategory
            )
        }
        if (suggestions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "ウェブ検索",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            suggestions.forEach { s ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WebSuggestRow(text = s, onClick = { onWeb(s) })
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            WebSuggestRow(text = "「$query」を Google で検索", onClick = { onWeb(query) })
        }
    }
}

@Composable
private fun WebSuggestRow(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun openWebSearch(context: Context, q: String) {
    val url = "https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8")
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun CategoryPickerDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onPick: (AppCategory) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text("${app.label} のカテゴリ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppCategory.entries
                    .filter { it != AppCategory.RECENTLY_ADDED }
                    .forEach { category ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(category) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = category == app.category,
                                onClick = { onPick(category) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(category.displayName)
                        }
                    }
            }
        }
    )
}

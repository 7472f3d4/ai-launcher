package com.g7r.ailauncher.ui.components

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

private data class WidgetGroup(val appLabel: String, val providers: List<AppWidgetProviderInfo>)

/** Pixel 風の M3 ウィジェット選択シート。タップで配置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    manager: AppWidgetManager,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val groups = remember {
        val pm = context.packageManager
        manager.installedProviders
            .groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                WidgetGroup(
                    appLabel = label,
                    providers = list.sortedBy {
                        runCatching { it.loadLabel(pm) }.getOrNull().orEmpty()
                    }
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "ウィジェット",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            groups.forEach { group ->
                item(key = "h_${group.appLabel}") {
                    Text(
                        "${group.appLabel}（${group.providers.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp)
                    )
                }
                items(
                    count = group.providers.size,
                    key = { i -> group.providers[i].provider.flattenToString() }
                ) { i ->
                    WidgetRow(context, group.providers[i], onPick)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun WidgetRow(
    context: Context,
    info: AppWidgetProviderInfo,
    onPick: (AppWidgetProviderInfo) -> Unit
) {
    val pm = context.packageManager
    val label = remember(info) { runCatching { info.loadLabel(pm) }.getOrNull() ?: "ウィジェット" }
    val preview = remember(info) {
        val drawable = runCatching { info.loadPreviewImage(context, 0) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(info.provider.packageName) }.getOrNull()
        drawable?.let {
            val w = it.intrinsicWidth.coerceIn(48, 480)
            val h = it.intrinsicHeight.coerceIn(48, 480)
            runCatching { it.toBitmap(w, h).asImageBitmap() }.getOrNull()
        }
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onPick(info) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier.size(width = 96.dp, height = 56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(max = 96.dp)
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

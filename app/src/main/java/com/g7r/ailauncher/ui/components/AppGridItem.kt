package com.g7r.ailauncher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.g7r.ailauncher.data.AppInfo

/** Icon + label tile with tap-to-launch and a long-press menu (favorite / category / app info). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridItem(
    app: AppInfo,
    isFavorite: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelStyle: TextStyle? = null,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onChangeCategory: ((AppInfo) -> Unit)? = null,
    onAddToHome: ((AppInfo) -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier
            .combinedClickable(
                onClick = { onLaunch(app) },
                onLongClick = { menuOpen = true }
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        AppIconImage(app.icon, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = labelColor,
                style = labelStyle ?: MaterialTheme.typography.labelSmall
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "お気に入りから外す" else "お気に入りに追加") },
                leadingIcon = {
                    Icon(if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null)
                },
                onClick = { onToggleFavorite(app.packageName); menuOpen = false }
            )
            if (onChangeCategory != null) {
                DropdownMenuItem(
                    text = { Text("カテゴリを変更") },
                    leadingIcon = { Icon(Icons.Rounded.Category, null) },
                    onClick = { onChangeCategory(app); menuOpen = false }
                )
            }
            if (onAddToHome != null) {
                DropdownMenuItem(
                    text = { Text("ホーム画面に追加") },
                    leadingIcon = { Icon(Icons.Rounded.Home, null) },
                    onClick = { onAddToHome(app); menuOpen = false }
                )
            }
            DropdownMenuItem(
                text = { Text("アプリ情報") },
                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                onClick = { onAppInfo(app.packageName); menuOpen = false }
            )
        }
    }
}

package com.g7r.ailauncher.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.g7r.ailauncher.data.DockItem
import com.g7r.ailauncher.data.DockShortcutEntry
import kotlin.math.roundToInt

data class HomeShortcutTile(
    val id: String,
    val label: String,
    val icon: Drawable?,
    val onLaunch: () -> Unit,
    val onRemove: () -> Unit,
    val onMoveToFolder: (folderId: String) -> Unit
)

private data class ShortcutDragState(
    val item: HomeShortcutTile,
    val pointerInRoot: Offset
)

@Composable
fun HomeShortcutGrid(
    items: List<HomeShortcutTile>,
    folders: List<DockItem.Folder>,
    modifier: Modifier = Modifier
) {
    val targetBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragState by remember { mutableStateOf<ShortcutDragState?>(null) }
    var gridRootPosition by remember { mutableStateOf(Offset.Zero) }
    val dragStateNow = dragState
    val hoveredFolderId = dragStateNow?.let { drag ->
        targetBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(drag.pointerInRoot) }?.key
    }

    Box(
        modifier = modifier.onGloballyPositioned { gridRootPosition = it.positionInRoot() }
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (dragStateNow != null) 104.dp else 0.dp),
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(items, key = { it.id }) { item ->
                ShortcutGridItem(
                    item = item,
                    canDragToFolder = folders.isNotEmpty(),
                    isDragging = dragStateNow?.item?.id == item.id,
                    onDragStart = { pointer -> dragState = ShortcutDragState(item, pointer) },
                    onDrag = { delta ->
                        dragStateNow?.let { dragState = it.copy(pointerInRoot = it.pointerInRoot + delta) }
                    },
                    onDragEnd = {
                        val current = dragState
                        val folderId = current?.let { state ->
                            targetBounds.entries.firstOrNull { (_, bounds) ->
                                bounds.contains(state.pointerInRoot)
                            }?.key
                        }
                        if (folderId != null) {
                            current?.item?.onMoveToFolder(folderId)
                        }
                        dragState = null
                    }
                )
            }
        }

        if (dragStateNow != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .zIndex(3f)
            ) {
                Text(
                    "既存フォルダへドラッグして追加",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 2.dp)
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    folders.forEach { folder ->
                        val isHovered = hoveredFolderId == folder.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .background(
                                    color = if (isHovered) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    targetBounds[folder.id] = Rect(
                                        position.x,
                                        position.y,
                                        position.x + coordinates.size.width,
                                        position.y + coordinates.size.height
                                    )
                                }
                                .padding(horizontal = 4.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = if (isHovered) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                folder.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isHovered) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            val dragIconOffset = 28.dp
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dragStateNow.pointerInRoot.x - gridRootPosition.x).roundToInt() - dragIconOffset.roundToPx(),
                            y = (dragStateNow.pointerInRoot.y - gridRootPosition.y).roundToInt() - dragIconOffset.roundToPx()
                        )
                    }
                    .size(56.dp)
                    .zIndex(5f)
            ) {
                ShortcutPreviewIcon(dragStateNow.item.label, dragStateNow.item.icon)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutGridItem(
    item: HomeShortcutTile,
    canDragToFolder: Boolean,
    isDragging: Boolean,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var menuOpen by remember(item.id) { mutableStateOf(false) }
    var dragHandlePosition by remember(item.id) { mutableStateOf(Offset.Zero) }
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isDragging) 0.28f else 1f }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = item.onLaunch,
                    onLongClick = { menuOpen = true }
                )
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            ShortcutPreviewIcon(item.label, item.icon)
            Spacer(Modifier.height(6.dp))
            Text(
                item.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelMedium
            )
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("ホームから削除") },
                    onClick = {
                        item.onRemove()
                        menuOpen = false
                    }
                )
            }
        }

        if (canDragToFolder) {
            val interactionSource = remember(item.id) { MutableInteractionSource() }
            Icon(
                imageVector = Icons.Rounded.DragIndicator,
                contentDescription = "フォルダへドラッグ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 1.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .onGloballyPositioned { dragHandlePosition = it.positionInRoot() }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {}
                    )
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = { offset -> latestDragStart(dragHandlePosition + offset) },
                            onDrag = { change, amount ->
                                change.consume()
                                latestDrag(amount)
                            },
                            onDragEnd = latestDragEnd,
                            onDragCancel = latestDragEnd
                        )
                    }
            )
        }
    }
}

@Composable
private fun ShortcutPreviewIcon(label: String, icon: Drawable?) {
    if (icon != null) {
        AppIconImage(icon, 52.dp)
    } else {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

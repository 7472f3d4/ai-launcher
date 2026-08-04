package com.g7r.ailauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import com.g7r.ailauncher.data.AppInfo
import com.g7r.ailauncher.data.DockItem
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Four editable Pixel-style dock slots, including app folders. */
@Composable
fun EditableDock(
    dock: List<DockItem?>,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onSetApp: (slot: Int, app: AppInfo) -> Unit,
    onSetFolder: (slot: Int, name: String, packageNames: List<String>) -> Unit,
    onClear: (slot: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onMerge: (from: Int, to: Int) -> Unit,
    onAppInfo: (String) -> Unit
) {
    var menuSlot by remember { mutableStateOf<Int?>(null) }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }
    var folderEditorSlot by remember { mutableStateOf<Int?>(null) }
    var openFolderSlot by remember { mutableStateOf<Int?>(null) }
    var dockWidthPx by remember { mutableStateOf(0f) }
    var dockTopInRootPx by remember { mutableStateOf(0f) }
    var dragState by remember { mutableStateOf<DockDragState?>(null) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val deleteTargetTopPx = with(density) { DELETE_TARGET_TOP_DP.dp.toPx() }
    val deleteTargetCenterPx = with(density) { DELETE_TARGET_CENTER_DP.dp.toPx() }
    val dockIconCenterPx = with(density) { 28.dp.toPx() }
    val minimumDeleteTravelPx = with(density) { 72.dp.toPx() }
    val deleteThresholdPx = (dockTopInRootPx + dockIconCenterPx - deleteTargetCenterPx)
        .coerceAtLeast(minimumDeleteTravelPx)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { dockWidthPx = it.width.toFloat() }
            .onGloballyPositioned { dockTopInRootPx = it.positionInRoot().y }
    ) {
        val activeDrag = dragState
        if (activeDrag != null) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (activeDrag.delete) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (deleteTargetTopPx - dockTopInRootPx).roundToInt()
                        )
                    }
                    .zIndex(4f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (activeDrag.delete) "離して削除" else "上へ移動して削除")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(DOCK_SLOT_COUNT) { slot ->
                val item = dock.getOrNull(slot)
                val slotWidth = dockWidthPx / DOCK_SLOT_COUNT
                val isDragged = activeDrag?.from == slot
                val isMergeTarget = activeDrag?.merge == true && activeDrag.target == slot
                val previewShift = previewShiftFor(slot, activeDrag, slotWidth)
                val animatedShift by animateFloatAsState(
                    targetValue = previewShift,
                    label = "dock-slot-shift"
                )
                val slotModifier = Modifier
                    .weight(1f)
                    .zIndex(if (isDragged) 3f else if (isMergeTarget) 2f else 0f)
                    .graphicsLayer {
                        translationX = if (isDragged) activeDrag?.offset?.x ?: 0f else animatedShift
                        translationY = if (isDragged) activeDrag?.offset?.y ?: 0f else 0f
                        val scale = when {
                            isDragged -> 1.1f
                            isMergeTarget -> 1.08f
                            else -> 1f
                        }
                        scaleX = scale
                        scaleY = scale
                        alpha = if (activeDrag?.delete == true && isDragged) 0.65f else 1f
                    }

                Box(slotModifier, contentAlignment = Alignment.TopCenter) {
                    when (item) {
                        null -> EmptyDockSlot(onClick = { pickerSlot = slot })
                        is DockItem.App -> DockAppTile(
                            app = item.app,
                            modifier = Modifier
                                .clickable { onLaunch(item.app) }
                                .pointerInput(slot, dockWidthPx, item) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragState = DockDragState(from = slot)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val current = dragState ?: return@detectDragGesturesAfterLongPress
                                            val offset = current.offset + amount
                                            dragState = calculateDragState(
                                                from = slot,
                                                offset = offset,
                                                dock = dock,
                                                dockWidthPx = dockWidthPx,
                                                deleteThresholdPx = deleteThresholdPx
                                            )
                                        },
                                        onDragEnd = {
                                            finishDockDrag(
                                                state = dragState,
                                                openMenu = { menuSlot = it },
                                                onClear = onClear,
                                                onMove = onMove,
                                                onMerge = onMerge
                                            )
                                            dragState = null
                                        },
                                        onDragCancel = { dragState = null }
                                    )
                                }
                        )
                        is DockItem.Folder -> DockFolderTile(
                            folder = item,
                            modifier = Modifier
                                .clickable { openFolderSlot = slot }
                                .pointerInput(slot, dockWidthPx, item) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragState = DockDragState(from = slot)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val current = dragState ?: return@detectDragGesturesAfterLongPress
                                            dragState = calculateDragState(
                                                from = slot,
                                                offset = current.offset + amount,
                                                dock = dock,
                                                dockWidthPx = dockWidthPx,
                                                deleteThresholdPx = deleteThresholdPx
                                            )
                                        },
                                        onDragEnd = {
                                            finishDockDrag(
                                                state = dragState,
                                                openMenu = { menuSlot = it },
                                                onClear = onClear,
                                                onMove = onMove,
                                                onMerge = onMerge
                                            )
                                            dragState = null
                                        },
                                        onDragCancel = { dragState = null }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }

    menuSlot?.let { slot ->
        val item = dock.getOrNull(slot)
        DockSlotMenu(
            item = item,
            onDismiss = { menuSlot = null },
            onReplace = {
                menuSlot = null
                pickerSlot = slot
            },
            onEditFolder = {
                menuSlot = null
                folderEditorSlot = slot
            },
            onRemove = {
                menuSlot = null
                onClear(slot)
            },
            onAppInfo = {
                val app = (item as? DockItem.App)?.app ?: return@DockSlotMenu
                menuSlot = null
                onAppInfo(app.packageName)
            }
        )
    }

    pickerSlot?.let { slot ->
        AppPickerDialog(
            apps = apps,
            onPick = { app ->
                onSetApp(slot, app)
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null }
        )
    }

    folderEditorSlot?.let { slot ->
        val item = dock.getOrNull(slot)
        val initialApps = when (item) {
            is DockItem.App -> listOf(item.app.packageName)
            is DockItem.Folder -> item.apps.map { it.packageName }
            null -> emptyList()
        }
        FolderEditorDialog(
            apps = apps,
            isExistingFolder = item is DockItem.Folder,
            initialName = (item as? DockItem.Folder)?.name.orEmpty(),
            initialPackages = initialApps,
            onSave = { name, packages ->
                onSetFolder(slot, name, packages)
                folderEditorSlot = null
            },
            onDismiss = { folderEditorSlot = null }
        )
    }

    openFolderSlot?.let { slot ->
        val folder = dock.getOrNull(slot) as? DockItem.Folder
        if (folder != null) {
            FolderPopup(
                folder = folder,
                onEdit = {
                    openFolderSlot = null
                    folderEditorSlot = slot
                },
                onLaunch = { app ->
                    openFolderSlot = null
                    onLaunch(app)
                },
                onDismiss = { openFolderSlot = null }
            )
        }
    }
}

private data class DockDragState(
    val from: Int,
    val offset: Offset = Offset.Zero,
    val target: Int = from,
    val merge: Boolean = false,
    val delete: Boolean = false
)

private fun calculateDragState(
    from: Int,
    offset: Offset,
    dock: List<DockItem?>,
    dockWidthPx: Float,
    deleteThresholdPx: Float
): DockDragState {
    if (dockWidthPx <= 0f) return DockDragState(from = from, offset = offset)
    val slotWidth = dockWidthPx / DOCK_SLOT_COUNT
    val centerX = (from + 0.5f) * slotWidth + offset.x
    val target = floor(centerX / slotWidth).toInt().coerceIn(0, DOCK_SLOT_COUNT - 1)
    val delete = offset.y < -deleteThresholdPx
    val targetCenterX = (target + 0.5f) * slotWidth
    val centeredOnTarget = abs(centerX - targetCenterX) < slotWidth * MERGE_TARGET_RADIUS
    val source = dock.getOrNull(from)
    val targetItem = dock.getOrNull(target)
    val merge = !delete &&
        target != from &&
        centeredOnTarget &&
        source is DockItem.App &&
        (targetItem is DockItem.App || targetItem is DockItem.Folder)
    return DockDragState(
        from = from,
        offset = offset,
        target = target,
        merge = merge,
        delete = delete
    )
}

private fun previewShiftFor(slot: Int, drag: DockDragState?, slotWidth: Float): Float {
    if (drag == null || drag.merge || drag.delete || slot == drag.from || drag.target == drag.from) {
        return 0f
    }
    return when {
        drag.target > drag.from && slot in (drag.from + 1)..drag.target -> -slotWidth
        drag.target < drag.from && slot in drag.target until drag.from -> slotWidth
        else -> 0f
    }
}

private fun finishDockDrag(
    state: DockDragState?,
    openMenu: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMerge: (Int, Int) -> Unit
) {
    state ?: return
    when {
        state.delete -> onClear(state.from)
        state.target != state.from && state.merge -> onMerge(state.from, state.target)
        state.target != state.from -> onMove(state.from, state.target)
        hypot(state.offset.x.toDouble(), state.offset.y.toDouble()) < LONG_PRESS_MENU_SLOP_PX ->
            openMenu(state.from)
    }
}

@Composable
private fun DockAppTile(app: AppInfo, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        AppIconImage(app.icon, 56.dp)
        Spacer(Modifier.height(5.dp))
        DockLabel(app.label)
    }
}

@Composable
private fun DockFolderTile(
    folder: DockItem.Folder,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        FolderIcon(folder.apps)
        Spacer(Modifier.height(5.dp))
        DockLabel(folder.name)
    }
}

@Composable
private fun EmptyDockSlot(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "ドックにアプリを追加",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        DockLabel("追加")
    }
}

@Composable
private fun DockLabel(label: String) {
    Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FolderIcon(apps: List<AppInfo>, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.size(56.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(7.dp)
        ) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { col ->
                        val app = apps.getOrNull(row * 2 + col)
                        if (app != null) {
                            AppIconImage(app.icon, 19.dp)
                        } else {
                            Box(Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockSlotMenu(
    item: DockItem?,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onEditFolder: () -> Unit,
    onRemove: () -> Unit,
    onAppInfo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text(if (item is DockItem.Folder) item.name else "ドックを編集") },
        text = {
            Column {
                TextButton(onClick = onReplace, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("アプリを変更", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                }
                TextButton(onClick = onEditFolder, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (item is DockItem.Folder) "フォルダを編集" else "フォルダを作成",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
                if (item is DockItem.App) {
                    TextButton(onClick = onAppInfo, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("アプリ情報", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("ドックから削除", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                }
            }
        }
    )
}

@Composable
private fun AppPickerDialog(
    apps: List<AppInfo>,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val normalized = query.filterNot(Char::isWhitespace)
        if (normalized.isBlank()) apps else apps.filter {
            it.label.filterNot(Char::isWhitespace).contains(normalized, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text("ドックに置くアプリ") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("アプリを検索") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 430.dp)
                ) {
                    items(filtered, key = { it.key }) { app ->
                        PickerAppTile(app = app, onClick = { onPick(app) })
                    }
                }
            }
        }
    )
}

@Composable
private fun FolderEditorDialog(
    apps: List<AppInfo>,
    isExistingFolder: Boolean,
    initialName: String,
    initialPackages: List<String>,
    onSave: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initialPackages.toSet()) }
    val filtered = remember(apps, query) {
        val normalized = query.filterNot(Char::isWhitespace)
        if (normalized.isBlank()) apps else apps.filter {
            it.label.filterNot(Char::isWhitespace).contains(normalized, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(name, apps.map { it.packageName }.filter { it in selected }) },
                enabled = selected.isNotEmpty()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        title = { Text(if (isExistingFolder) "フォルダを編集" else "フォルダを作成") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("フォルダ名") },
                    placeholder = { Text("フォルダ") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("アプリを検索") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${selected.size}個のアプリ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(filtered.size, key = { filtered[it].key }) { index ->
                        val app = filtered[index]
                        val checked = app.packageName in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - app.packageName
                                    else selected + app.packageName
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            AppIconImage(app.icon, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                app.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selected = if (checked) selected - app.packageName
                                    else selected + app.packageName
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun FolderPopup(
    folder: DockItem.Folder,
    onEdit: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onEdit)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("編集")
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (folder.apps.isEmpty()) {
                    Text(
                        "アプリがありません。上の名前をタップして編集できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.heightIn(max = 430.dp)
                    ) {
                        items(folder.apps, key = { it.key }) { app ->
                            PickerAppTile(app = app, onClick = { onLaunch(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerAppTile(app: AppInfo, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        AppIconImage(app.icon, 48.dp)
        Spacer(Modifier.height(5.dp))
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val DOCK_SLOT_COUNT = 4
private const val MERGE_TARGET_RADIUS = 0.24f
private const val LONG_PRESS_MENU_SLOP_PX = 24f
private const val DELETE_TARGET_TOP_DP = 56
private const val DELETE_TARGET_CENTER_DP = 78

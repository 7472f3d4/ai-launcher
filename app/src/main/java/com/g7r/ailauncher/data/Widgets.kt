package com.g7r.ailauncher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** A hosted App Widget on the home grid. Position and spans are in grid cells. */
data class WidgetEntry(
    val id: Int,
    val col: Int,
    val row: Int,
    val spanW: Int,
    val spanH: Int
)

/** Home-screen widget grid geometry and placement rules (Pixel-style cells). */
object WidgetGrid {
    const val COLUMNS = 5
    const val TARGET_CELL_HEIGHT_DP = 84
    private const val MAX_SEARCH_ROWS = 32

    fun overlaps(e: WidgetEntry, col: Int, row: Int, spanW: Int, spanH: Int): Boolean =
        e.col < col + spanW && col < e.col + e.spanW &&
            e.row < row + spanH && row < e.row + e.spanH

    /** True when the area is inside the grid and free of other widgets. */
    fun fits(
        entries: List<WidgetEntry>,
        ignoreId: Int,
        col: Int,
        row: Int,
        spanW: Int,
        spanH: Int,
        rows: Int = Int.MAX_VALUE
    ): Boolean {
        if (col < 0 || row < 0 || col + spanW > COLUMNS || row + spanH > rows) return false
        return entries.none { it.id != ignoreId && overlaps(it, col, row, spanW, spanH) }
    }

    /** First free top-left cell scanning left-to-right, top-to-bottom. */
    fun findFreeCell(entries: List<WidgetEntry>, spanW: Int, spanH: Int): Pair<Int, Int> {
        val w = spanW.coerceIn(1, COLUMNS)
        for (row in 0..MAX_SEARCH_ROWS) {
            for (col in 0..(COLUMNS - w)) {
                if (fits(entries, ignoreId = -1, col, row, w, spanH)) return col to row
            }
        }
        return 0 to 0
    }
}

/**
 * Thin wrapper around [AppWidgetHost] so the launcher can embed standard Android
 * App Widgets (Google Clock, Calendar, Todoist, etc.) on the home screen.
 */
class WidgetHostManager(context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
    private val host = LauncherWidgetHost(context.applicationContext, HOST_ID)

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    /** Returns null when the provider has been uninstalled. */
    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    fun info(id: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun label(context: Context, id: Int): String? =
        runCatching { manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager) }.getOrNull()

    companion object {
        private const val HOST_ID = 0x4A17
    }
}

private class LauncherWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView = LauncherWidgetHostView(context)
}

/**
 * [AppWidgetHostView] that reports long-press + drag gestures to the launcher
 * (the same trick Launcher3 uses) while leaving taps and scrolls to the widget.
 *
 * A long press lifts the widget for dragging; the finger keeps the gesture until
 * ACTION_UP. Children receive ACTION_CANCEL the moment the drag starts, so a
 * long-press never also fires the widget's own click.
 */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {

    interface DragListener {
        fun onDragStart()
        fun onDragMove(dxPx: Float, dyPx: Float)
        fun onDragEnd(cancelled: Boolean)
    }

    var dragListener: DragListener? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pendingLongPress: Runnable? = null

    private fun scheduleLongPress() {
        cancelScheduledLongPress()
        val r = Runnable { if (isAttachedToWindow) startDrag() }
        pendingLongPress = r
        postDelayed(r, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelScheduledLongPress() {
        pendingLongPress?.let { removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun startDrag() {
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        cancelLongPress()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        dragListener?.onDragStart()
    }

    private fun endDrag(cancelled: Boolean) {
        if (!dragging) return
        dragging = false
        dragListener?.onDragEnd(cancelled)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                downX = ev.rawX
                downY = ev.rawY
                scheduleLongPress()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging &&
                    (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)
                ) {
                    cancelScheduledLongPress()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelScheduledLongPress()
                // A child owns the gesture and the finger lifted right after the
                // long press: the UP is consumed as the child's CANCEL, so close
                // the drag here or it would never end.
                if (dragging) {
                    endDrag(ev.actionMasked == MotionEvent.ACTION_CANCEL)
                    return true
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    dragListener?.onDragMove(ev.rawX - downX, ev.rawY - downY)
                } else if (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop) {
                    cancelScheduledLongPress()
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelScheduledLongPress()
                endDrag(cancelled = false)
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelScheduledLongPress()
                endDrag(cancelled = true)
            }
        }
        // Always claim the gesture so the long-press timer keeps running even
        // when no child inside the widget is clickable.
        return true
    }

    override fun onDetachedFromWindow() {
        cancelScheduledLongPress()
        endDrag(cancelled = true)
        super.onDetachedFromWindow()
    }
}

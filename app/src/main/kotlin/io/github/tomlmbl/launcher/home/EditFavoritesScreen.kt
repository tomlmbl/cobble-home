package io.github.tomlmbl.launcher.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.ui.AppName
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.PrimaryButton
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.ui.TonalButton
import io.github.tomlmbl.launcher.ui.UndoBar
import io.github.tomlmbl.launcher.ui.rememberDot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Favorites editor: on Home's axis, the list ends at the height of Home's favorites; "×" removes, the handle
 * reorders. Below the list, "Add app" and the counter; at the maximum, "Add app" is disabled and the counter gives
 * the reason. A removal can be undone for 10 s with the "Undo" bar, next to "Done".
 * Every change is saved at once; "Done" and Back close the screen.
 */
@Composable
fun EditFavoritesScreen(
    slots: List<FavoriteSlot<App>>,
    capacity: Int,
    color: suspend (App) -> Int?,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Favorite) -> Unit,
    onRestore: (index: Int, Favorite) -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local order during a drag: the write only happens on drop.
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragTo by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableFloatStateOf(1f) }
    // Dropped order, kept until the (asynchronous) write returns: without it, the old order came back for one frame.
    var dropped by remember { mutableStateOf<List<String>?>(null) }
    val ids = slots.map { it.favorite.appId }
    val currentIds by rememberUpdatedState(ids)
    val order = dropped
    val shown = when {
        dragFrom in slots.indices && dragTo in slots.indices -> slots.toMutableList().apply { add(dragTo, removeAt(dragFrom)) }
        order != null && order != ids && order.toSet() == ids.toSet() -> order.map { id -> slots.first { it.favorite.appId == id } }
        else -> slots
    }
    LaunchedEffect(ids) { if (dropped != null) dropped = null }
    // Dropped row: it starts from its offset to the finger and settles with a spring, instead of jumping into place.
    // Started without waiting for the next frame, so the offset is in place as soon as the drag stops.
    var settling by remember { mutableStateOf<String?>(null) }
    val settle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun settleFrom(id: String, offset: Float) {
        settling = id
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(offset)
            settle.animateTo(0f, Tokens.spatialDefault())
            if (settling == id) settling = null
        }
    }
    // Last removal, undoable (one at a time, forgotten when leaving the screen).
    var removed by remember { mutableStateOf<Pair<Int, Favorite>?>(null) }
    LaunchedEffect(removed) { if (removed != null) { delay(Tokens.undoMs); removed = null } }

    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val removeLabel = stringResource(R.string.remove)
    val notOnHome = stringResource(R.string.not_on_home)
    val full = slots.size >= capacity

    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(top = Tokens.Space.m).height(Tokens.topBarHeight).padding(horizontal = Tokens.screenMargin), contentAlignment = Alignment.CenterStart) {
            BasicText(stringResource(R.string.favorites), style = Tokens.screenTitle, modifier = Modifier.semantics { heading() })
        }
        // List aligned to the bottom: it ends where Home's favorites end. Animated rows: neighbors slide into place, a
        // removal collapses, an addition or "Undo" expands; the held row follows the finger without its own slide.
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            itemsIndexed(shown, key = { _, slot -> slot.favorite.appId }) { index, slot ->
                val id = slot.favorite.appId
                val currentIndex by rememberUpdatedState(index)
                val dragged = dragFrom >= 0 && dragTo == index
                val app = (slot as? FavoriteSlot.Installed)?.app
                val dot = rememberDot(app, color)
                val label = app?.label ?: slot.favorite.label
                val hidden = index >= capacity
                Row(
                    Modifier
                        .animateItem(
                            fadeInSpec = tween(Tokens.favoriteRowInMs, Tokens.favoriteRowInDelayMs, Tokens.fadeInEasing),
                            placementSpec = if (dragged) null else Tokens.spatialDefault(),
                            fadeOutSpec = Tokens.effectsFast(),
                        )
                        .fillMaxWidth()
                        .zIndex(if (dragged || settling == id) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) dragOffset else if (settling == id) settle.value else 0f }
                        .onSizeChanged { rowPx = it.height.toFloat().coerceAtLeast(1f) }
                        // Home rows: grouped surface, 2 dp gaps, 24/6 corners;
                        // the dot stays at the screen margin.
                        .then(
                            Tokens.favoriteShape(first = index == 0, last = index == shown.lastIndex).let { shape ->
                                Modifier
                                    .padding(horizontal = Tokens.favoritesGroupInset, vertical = Tokens.favoritesGroupGap / 2)
                                    .background(Tokens.favoriteSurface, shape)
                                    .background(if (dragged) Tokens.highlight else Color.Transparent, shape)
                                    .heightIn(min = Tokens.favoriteRowHeight - Tokens.favoritesGroupGap)
                                    .padding(start = Tokens.screenMargin - Tokens.favoritesGroupInset, end = Tokens.Space.xl - Tokens.favoritesGroupInset)
                            }
                        )
                        .semantics(mergeDescendants = true) {
                            if (app?.otherProfile == true) contentDescription = app.description
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { onMove(currentIndex, currentIndex - 1); true })
                                if (index < slots.lastIndex) add(CustomAccessibilityAction(moveDown) { onMove(currentIndex, currentIndex + 1); true })
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(Tokens.dotSize).background(dot ?: Color.Transparent, CircleShape))
                    Spacer(Modifier.width(Tokens.dotLabel))
                    Column(Modifier.weight(1f).then(if (app == null) Modifier.alpha(Tokens.missingAlpha) else Modifier)) {
                        if (app == null) BasicText(stringResource(R.string.uninstalled, label), style = Tokens.favorite)
                        else AppName(label, app.otherProfile, Tokens.favorite)
                        if (hidden) BasicText(notOnHome, style = Tokens.section)
                    }
                    Box(
                        Modifier
                            .size(Tokens.touchTarget)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { removed = currentIndex to slot.favorite; onRemove(slot.favorite) }
                            .semantics { contentDescription = "$removeLabel $label" },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(R.drawable.ic_close, Tokens.glyphSize, Tokens.onWallpaperMuted) }
                    Box(
                        Modifier
                            .size(Tokens.touchTarget)
                            .clearAndSetSemantics {}
                            .pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = { settling = null; dragFrom = currentIndex; dragTo = currentIndex; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val steps = (dragOffset / rowPx).roundToInt()
                                        val target = (dragTo + steps).coerceIn(0, currentIds.lastIndex)
                                        if (target != dragTo) {
                                            dragOffset -= (target - dragTo) * rowPx
                                            dragTo = target
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragFrom != dragTo) {
                                            dropped = currentIds.toMutableList().apply { add(dragTo, removeAt(dragFrom)) }
                                            onMove(dragFrom, dragTo)
                                        }
                                        settleFrom(id, dragOffset)
                                        dragFrom = -1; dragTo = -1; dragOffset = 0f
                                    },
                                    onDragCancel = { settleFrom(id, dragOffset); dragFrom = -1; dragTo = -1; dragOffset = 0f },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(R.drawable.ic_drag_handle, Tokens.glyphSize, Tokens.onWallpaperMuted) }
                }
            }
        }
        // In the room of the dock and its gap on Home: "Add app", counter, then "Undo" and "Done".
        Column(
            Modifier.fillMaxWidth().heightIn(min = Tokens.dockReserve + Tokens.favoritesToDock).padding(horizontal = Tokens.screenMargin),
            verticalArrangement = Arrangement.Bottom,
        ) {
            TonalButton(stringResource(R.string.add_app), R.drawable.ic_add, onClick = onAdd, enabled = !full, modifier = Modifier.fillMaxWidth())
            BasicText(
                favoritesCounter(slots.size, capacity),
                style = Tokens.hint, modifier = Modifier.padding(top = Tokens.Space.m, bottom = Tokens.Space.xxs),
            )
            Row(Modifier.fillMaxWidth().padding(bottom = Tokens.footerBottom), verticalAlignment = Alignment.CenterVertically) {
                // "Undo" bar: rises as it appears, fades as it leaves; the last removal stays shown while it fades.
                // Its room stays reserved: "Done" never leaves the right side.
                var shown by remember { mutableStateOf(removed) }
                removed?.let { shown = it }
                Box(Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = removed != null,
                        modifier = Modifier.fillMaxWidth(),
                        enter = slideInVertically(Tokens.spatialDefault()) { it } + fadeIn(Tokens.effectsDefault()),
                        exit = fadeOut(Tokens.effectsFast()),
                    ) {
                        shown?.let { (index, favorite) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UndoBar(
                                    stringResource(R.string.favorite_removed, favorite.label), stringResource(R.string.undo),
                                    onUndo = { removed = null; onRestore(index, favorite) },
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(Tokens.Space.ml))
                            }
                        }
                    }
                }
                PrimaryButton(stringResource(R.string.done), onClick = onDone)
            }
        }
    }
}

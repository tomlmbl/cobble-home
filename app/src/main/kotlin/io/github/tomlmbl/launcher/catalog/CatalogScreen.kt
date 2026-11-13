package io.github.tomlmbl.launcher.catalog

import android.animation.ValueAnimator
import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import io.github.tomlmbl.launcher.home.windowRect
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.ui.AppName
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.ui.listEdges
import io.github.tomlmbl.launcher.ui.remainingAbove
import io.github.tomlmbl.launcher.ui.remainingBelow
import io.github.tomlmbl.launcher.ui.pressScale
import io.github.tomlmbl.launcher.ui.pressedIn
import io.github.tomlmbl.launcher.ui.rememberDot
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface Entry {
    data class Header(val key: String) : Entry
    data class Item(val app: App) : Entry
}

/** Letter under the finger on the rail, and its vertical position in the window to place the bubble. */
private data class RailTouch(val key: String, val y: Float)

/**
 * Catalog: sectioned list and # A–Z rail, search field at the bottom, keyboard closed on opening.
 * While searching: flat list stacked from the bottom, best result at the bottom, highlighted and launched by
 * Enter. Colored dots, on the same axis as Home.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(
    apps: List<App>,
    alphabet: Alphabet,
    query: String,
    onQueryChange: (String) -> Unit,
    color: suspend (App) -> Int?,
    onLaunch: (App, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    /** Long press: app menu. */
    onLongPress: ((App) -> Unit)? = null,
    /** List position, held by the caller while the catalog stays composed. */
    listState: LazyListState = rememberLazyListState(),
    /**
     * Swipe down to return Home, following the finger: offset in px, then velocity in px/s on release;
     * [dismissing]: true while the catalog is not settled in place.
     */
    onDismissDrag: ((Float) -> Unit)? = null,
    onDismissRelease: ((Float) -> Unit)? = null,
    dismissing: () -> Boolean = { false },
    /** Search: [searchRequested] focuses the field and opens the keyboard. */
    searchRequested: Boolean = false,
    onSearchRequestHandled: () -> Unit = {},
    /**
     * Search only, opened by swiping up. Empty query: the field alone, no list or rail; leaving the search (swipe
     * down, keyboard closed with no query) calls [onExitSearch] and returns Home.
     */
    searchOnly: Boolean = false,
    onExitSearch: () -> Unit = {},
) {
    val sections = remember(apps, alphabet) { alphabet.sections(apps) { it.label } }
    val rows = remember(sections) {
        sections.flatMap { s -> listOf<Entry>(Entry.Header(s.key)) + s.items.map { Entry.Item(it) } }
    }
    // Section of each row, to know which one is at the top of the list.
    val rowSection = remember(sections) { sections.flatMap { s -> List(s.items.size + 1) { s.key } } }
    val headerIndex = remember(rows) {
        rows.withIndex().filter { it.value is Entry.Header }.associate { (it.value as Entry.Header).key to it.index }
    }
    val results = remember(apps, query, alphabet) { alphabet.search(apps, query) { it.label } }
    // Section of the row at mid-height of the list, where the rail brings the target section.
    val currentSection = remember(rowSection) {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val row = info.visibleItemsInfo.lastOrNull { it.offset <= middle }?.index ?: listState.firstVisibleItemIndex
            rowSection.getOrNull(row)
        }
    }

    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val softKeyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            searchFocus.requestFocus()
            softKeyboard?.show()
            onSearchRequestHandled()
        }
    }
    // Keyboard open, even with no query: searching (the catalog opens with the keyboard up; swiping down closes it
    // to browse the list).
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val searching by rememberUpdatedState(query.isNotBlank() || imeOpen)
    val exitScope = rememberCoroutineScope()
    val exitToHome by rememberUpdatedState(if (searchOnly) onExitSearch else null)
    /** Leaving the search, shared by keyboard closed, swipe and tap on empty space. */
    val leaveSearch by rememberUpdatedState {
        focusManager.clearFocus()
        softKeyboard?.hide()
        exitToHome?.invoke()
        Unit
    }
    // Search only: keyboard closed (Back) with no query leaves an empty screen with no use, so return Home. Decided as
    // soon as the keyboard starts going down (zero target), so the exit goes along with its closing instead of after it.
    val imeClosing = imeOpen && WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) == 0
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(imeOpen, imeClosing, searchOnly, query.isBlank()) {
        if (imeOpen && !imeClosing) imeSeen = true
        else if (imeSeen && (imeClosing || !imeOpen)) {
            imeSeen = false
            if (searchOnly && query.isBlank()) leaveSearch()
        }
    }
    val dismiss = remember(onDismissDrag, onDismissRelease) {
        if (onDismissDrag == null || onDismissRelease == null) null
        else DismissGesture(onDismissDrag, onDismissRelease, dismissing, { searching }) {
            // Leaving the search: query cleared, keyboard closed, full list at the top.
            onQueryChange("")
            exitScope.launch { listState.scrollToItem(0) }
            leaveSearch()
        }
    }
    val dismissModifier = if (dismiss == null) Modifier else Modifier
        // Each new touch starts a new gesture, even if the previous one's list disappeared along the way.
        .pointerInput(dismiss) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); dismiss.start() } }
        .nestedScroll(dismiss.nestedScroll)
        .draggable(
            rememberDraggableState { dismiss.leftover(it) },
            Orientation.Vertical,
            onDragStopped = { dismiss.end(it) },
        )

    // Edge to edge: the list runs under the status bar, the field and the gesture bar; with the keyboard
    // open, it stops at the keyboard. [underField]: distance from the top of the field to the bottom of the list.
    val density = LocalDensity.current
    val safe = WindowInsets.safeDrawing
    var fieldBlock by remember { mutableIntStateOf(0) }
    val statusBar = with(density) { safe.getTop(this).toDp() }
    val underField = with(density) { ((safe.getBottom(this) - WindowInsets.ime.getBottom(this)).coerceAtLeast(0) + fieldBlock).toDp() }
    val fieldNow by rememberUpdatedState(underField)

    Box(modifier.fillMaxSize().windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal)).then(dismissModifier)) {
        Box(Modifier.fillMaxSize().imePadding()) {
            if (query.isNotBlank()) {
                if (results.isEmpty()) {
                    NoResults(query, Modifier.padding(top = statusBar, bottom = underField)) { onQueryChange("") }
                } else {
                    // Stacked from the bottom: the best result closest to the field and the thumb.
                    val resultsState = rememberLazyListState()
                    // Each keystroke brings the best result back against the field: otherwise the list keeps its old first
                    // row (same key) and the highlighted result, the one Enter launches, slides under the field.
                    LaunchedEffect(query) { resultsState.requestScrollToItem(0) }
                    LazyColumn(
                        Modifier.fillMaxSize().listEdges(resultsState::remainingAbove, resultsState::remainingBelow, statusBar) { fieldNow },
                        state = resultsState,
                        contentPadding = PaddingValues(top = statusBar, bottom = underField + Tokens.Space.l),
                        reverseLayout = true,
                    ) {
                        itemsIndexed(results, key = { _, app -> app.id }) { i, app ->
                            AppRow(app, color, onLaunch, onLongPress, highlighted = i == 0)
                        }
                    }
                }
            } else if (searchOnly) {
                // Search only, nothing to show before the first letter; a tap on empty space returns Home.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { leaveSearch() }
                })
            } else {
                var touch by remember { mutableStateOf<RailTouch?>(null) }
                var top by remember { mutableFloatStateOf(0f) }
                // Start of the list within thumb reach, a quarter of the screen height down, keyboard excluded. The
                // pickers, shown in a sheet, keep their own start.
                val imePx = WindowInsets.ime.getBottom(density)
                val imeHeight = with(density) { imePx.toDp() }
                // Keyboard open or field focused, so searching: the rail fades out and stops responding, the list does not
                // move (only clipped by the keyboard). The rail fades back in when the keyboard closes. When the catalog
                // opens and closes, the rail follows the list (no abrupt appearance or disappearance).
                val railHidden = imePx > 0 || fieldFocused
                val railAlpha by animateFloatAsState(if (railHidden) 0f else 1f, tween(Tokens.railMagnifierFadeMs), label = "railHidden")
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val listTop = maxOf(statusBar, (maxHeight + imeHeight) * Tokens.catalogTopReach)
                    Row(Modifier.fillMaxSize().padding(end = Tokens.railEnd).onGloballyPositioned { top = it.positionInRoot().y }) {
                        LazyColumn(
                            Modifier.weight(1f).fillMaxHeight().listEdges(listState::remainingAbove, listState::remainingBelow, statusBar) { fieldNow },
                            state = listState,
                            contentPadding = PaddingValues(top = listTop, bottom = underField + Tokens.Space.l),
                        ) {
                            items(rows, key = { row ->
                                when (row) {
                                    is Entry.Header -> "h:${row.key}"
                                    is Entry.Item -> row.app.id
                                }
                            }) { row ->
                                when (row) {
                                    is Entry.Header -> BasicText(
                                        row.key, style = Tokens.section,
                                        modifier = Modifier
                                            .heightIn(min = Tokens.sectionHeaderHeight)
                                            .padding(start = Tokens.screenMargin, top = Tokens.Space.ml)
                                            .semantics { heading() },
                                    )
                                    is Entry.Item -> AppRow(row.app, color, onLaunch, onLongPress)
                                }
                            }
                        }
                        AlphabetRail(
                            headerIndex, listState, touch?.key, currentSection,
                            Modifier
                                .padding(top = statusBar, bottom = underField)
                                .alpha(railAlpha)
                                .then(
                                    if (!railHidden) Modifier
                                    else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                        }
                                    }
                                ),
                        ) { touch = it }
                    }
                }
                touch?.let { LetterBubble(it.copy(y = it.y - top), Modifier.align(Alignment.TopEnd)) }
            }
        }
        // At rest, the lists' bottom padding keeps the 16 dp gap above the field.
        SearchField(
            query, onQueryChange,
            onSearch = { results.firstOrNull()?.let { onLaunch(it, null) } },
            gapAbove = false,
            focusRequester = searchFocus,
            onFocusChanged = { fieldFocused = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(safe.only(WindowInsetsSides.Bottom))
                .onSizeChanged { fieldBlock = it.height },
        )
    }
}

/**
 * Swipe down in the catalog, as in Launcher3: from the list once it cannot scroll further up (it gets the rest of
 * the drag), or from the field and areas without a list; never from the rail, which keeps its own gesture. While
 * searching, the swipe leaves the search ([exitSearch]) and stops there; otherwise it brings Home back under the
 * finger, keyboard included. Once closing has started, the whole drag drives it, in both directions.
 */
private class DismissGesture(
    private val drag: (Float) -> Unit,
    private val release: (Float) -> Unit,
    private val dismissing: () -> Boolean,
    private val searching: () -> Boolean,
    private val exitSearch: () -> Unit,
) {
    // Drag already used to leave the search: the rest is ignored until the next touch.
    private var spent = false

    fun start() { spent = false }

    /** Vertical offset the list did not consume; true if the gesture takes it. */
    fun leftover(dy: Float): Boolean {
        if (spent) return true
        if (dismissing()) { drag(dy); return true }
        if (dy <= 0f) return false
        if (searching()) { spent = true; exitSearch(); return true }
        drag(dy)
        return true
    }

    /** End of the drag; true if the gesture had kept it. */
    fun end(velocity: Float): Boolean {
        val held = spent || dismissing()
        spent = false
        if (dismissing()) release(velocity)
        return held
    }

    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && (spent || dismissing()) && leftover(available.y)) Offset(0f, available.y)
            else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y != 0f && leftover(available.y)) Offset(0f, available.y)
            else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity = if (end(available.y)) available else Velocity.Zero
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    margin: Dp = Tokens.screenMargin,
    /** False when the list above carries the gap itself, in its bottom padding. */
    gapAbove: Boolean = true,
    hint: String = stringResource(R.string.search_hint),
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val clear = stringResource(R.string.clear_search)
    // Keyboard closed (Back): the field gives up focus, otherwise the blinking cursor redraws the screen every
    // 500 ms while the catalog stays open. A keyboard never seen in the insets triggers nothing.
    val focusManager = LocalFocusManager.current
    val keyboard = WindowInsets.isImeVisible
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(keyboard) { if (!keyboard && focused) focusManager.clearFocus() }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = Tokens.label,
        cursorBrush = SolidColor(Tokens.onWallpaper),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; onFocusChanged(it.isFocused) }
            .padding(horizontal = margin)
            .padding(top = if (gapAbove) Tokens.Space.l else Dp.Hairline, bottom = Tokens.Space.l)
            .background(Tokens.fieldBackground, Tokens.pillShape)
            .padding(start = Tokens.Space.xl, end = Tokens.Space.xs)
            .semantics { contentDescription = hint },
        decorationBox = { inner ->
            Row(Modifier.heightIn(min = Tokens.fieldHeight), verticalAlignment = Alignment.CenterVertically) {
                Glyph(R.drawable.ic_search, Tokens.glyphSize, if (query.isEmpty()) Tokens.onWallpaperMuted else Tokens.onWallpaper)
                Spacer(Modifier.width(Tokens.Space.ml))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) BasicText(hint, style = Tokens.label.copy(color = Tokens.onWallpaperMuted, shadow = null))
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(Tokens.touchTarget)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") }
                            .semantics { contentDescription = clear },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(R.drawable.ic_close, Tokens.glyphSize, Tokens.onWallpaper) }
                }
            }
        },
    )
}

@Composable
private fun NoResults(query: String, modifier: Modifier, onClear: () -> Unit) {
    // Just above the field, like the results.
    Column(
        modifier.fillMaxSize().padding(horizontal = Tokens.screenMargin).padding(bottom = Tokens.Space.m + Tokens.Space.l),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.m, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(stringResource(R.string.no_results_for, query.trim()), style = Tokens.label)
        // 40 dp button, 48 dp touch target.
        val interaction = remember { MutableInteractionSource() }
        Box(Modifier.heightIn(min = Tokens.touchTarget).clickable(interaction, indication = null, onClick = onClear), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .heightIn(min = Tokens.chipHeight)
                    .widthIn(min = Tokens.chipMinWidth)
                    .background(Tokens.fieldBackground, Tokens.pillShape)
                    .pressedIn(interaction, Tokens.pillShape)
                    .padding(horizontal = Tokens.Space.xl),
                contentAlignment = Alignment.Center,
            ) { BasicText(stringResource(R.string.clear_search), style = Tokens.enterHint) }
        }
    }
}
@Composable
private fun AppRow(
    app: App,
    color: suspend (App) -> Int?,
    onLaunch: (App, Rect?) -> Unit,
    onLongPress: ((App) -> Unit)?,
    highlighted: Boolean = false,
) {
    val dot = rememberDot(app, color)
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val paused = stringResource(R.string.paused)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.rowMinHeight)
            .padding(horizontal = Tokens.rowInset)
            .clip(Tokens.highlightShape)
            .background(if (highlighted) Tokens.highlight else Color.Transparent)
            .onGloballyPositioned {
                bounds = it.windowRect()
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = onLongPress?.let { { it(app) } },
            ) { onLaunch(app, bounds) }
            .padding(start = Tokens.screenMargin - Tokens.rowInset, end = Tokens.Space.m, top = Tokens.Space.s, bottom = Tokens.Space.s)
            // Press shrink from the dot. In a list, Compose already delays the press: a scroll cancels it before it
            // shows.
            .pressScale(interaction, Offset(0f, 0.5f))
            .alpha(if (!app.paused) 1f else Tokens.disabledAlpha)
            .then(if (app.paused) Modifier.semantics { stateDescription = paused } else Modifier)
            .then(if (app.otherProfile) Modifier.semantics { contentDescription = app.description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Tokens.dotSize).background(dot ?: Color.Transparent, CircleShape))
        Spacer(Modifier.width(Tokens.dotLabel))
        AppName(app.label, app.otherProfile, Tokens.label, Modifier.weight(1f))
        // Visual cue only: Enter launches the highlighted result.
        if (highlighted) {
            Row(
                Modifier
                    .padding(start = Tokens.enterHintStart, end = Tokens.Space.ml, top = Tokens.Space.xs, bottom = Tokens.Space.xs)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(R.drawable.ic_keyboard_return, Tokens.enterGlyphSize, Tokens.onWallpaper)
                BasicText(stringResource(R.string.open), style = Tokens.enterHint)
            }
        }
    }
}

/** Bubble showing the current letter, left of the finger, at finger height. */
@Composable
private fun LetterBubble(touch: RailTouch, modifier: Modifier) {
    Box(
        modifier
            .offset {
                val size = Tokens.bubbleSize.roundToPx()
                IntOffset(-(Tokens.railEnd + Tokens.railScrubWidth + Tokens.Space.xl).roundToPx(), (touch.y - size / 2).roundToInt().coerceAtLeast(0))
            }
            .size(Tokens.bubbleSize)
            .background(Tokens.bubbleBackground, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) { OnSurface { BasicText(touch.key, style = Tokens.bubble) } }
}

/**
 * # A–Z rail: tap or drag scrolls to the section. Letters without an app look like the others and are skipped;
 * light haptic feedback on every letter change.
 * Anchored at the bottom, above the search field, in the thumb zone: the gesture area is limited to it.
 * Compact and dimmed at rest, current section opaque; while dragging, an in-place magnifier.
 */
@Composable
private fun AlphabetRail(
    headerIndex: Map<String, Int>,
    listState: LazyListState,
    active: String?,
    current: State<String?>,
    modifier: Modifier,
    onTouch: (RailTouch?) -> Unit,
) {
    val keys = Alphabet.RAIL_KEYS
    val window = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    // Never less than the room for the 27 letters, which grows with the font size.
    val minimum = with(density) { (Tokens.railLetter.fontSize * 1.4f).toPx() } * keys.size
    BoxWithConstraints(modifier.width(Tokens.railWidth).fillMaxHeight().padding(bottom = Tokens.railBottom + Tokens.Space.l)) {
        val available = constraints.maxHeight.toFloat()
        val railPx = maxOf(window * Tokens.railHeightFraction, minimum).coerceAtMost(available)
        RailLetters(
            keys, headerIndex, listState, active, current, onTouch,
            Modifier.align(Alignment.BottomCenter).height(with(density) { railPx.toDp() }),
        )
    }
}

@Composable
private fun RailLetters(
    keys: List<String>,
    headerIndex: Map<String, Int>,
    listState: LazyListState,
    active: String?,
    current: State<String?>,
    onTouch: (RailTouch?) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val run = with(LocalDensity.current) { Tokens.railJumpRun.toPx() }
    var height by remember { mutableIntStateOf(1) }
    var top by remember { mutableFloatStateOf(0f) }
    // Finger position in rest-rail cells; kept on release so the magnifier recedes in place.
    var finger by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    // Drag in progress: the press, whose release arrives at the drag threshold, must not release.
    var dragging by remember { mutableStateOf(false) }
    val intensity by animateFloatAsState(if (pressed) 1f else 0f, tween(Tokens.railMagnifierFadeMs), label = "magnifier")
    // Gestures survive recompositions: they always read the current value.
    val currentActive by rememberUpdatedState(active)
    val currentIndex by rememberUpdatedState(headerIndex)
    fun touchAt(y: Float, animate: Boolean) {
        pressed = true
        finger = (y / height * keys.size).coerceIn(0f, keys.size.toFloat())
        val key = keys[finger.toInt().coerceIn(0, keys.lastIndex)]
        val index = currentIndex[key] ?: return
        if (currentActive != key) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            scope.launch { listState.scrollToSection(index, if (animate) run else 0f) }
        }
        onTouch(RailTouch(key, top + y))
    }
    fun release() {
        pressed = false
        onTouch(null)
    }

    Column(
        modifier
            .width(Tokens.railWidth)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .onGloballyPositioned { top = it.positionInRoot().y }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { touchAt(it.y, animate = true); tryAwaitRelease(); if (!dragging) release() })
            }
            .pointerInput(Unit) {
                // Drag tracked on both axes, height only read: a horizontal thumb movement, below the Z in
                // particular, would otherwise stay free and close the catalog.
                detectDragGestures(
                    onDragStart = { dragging = true; touchAt(it.y, animate = false) },
                    onDragEnd = { dragging = false; release() },
                    onDragCancel = { dragging = false; release() },
                    onDrag = { change, _ -> touchAt(change.position.y, animate = false) },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val jump = stringResource(R.string.jump_to_section)
        keys.forEachIndexed { i, key ->
            // Visual effect only: read at draw time, without recomposition or relayout.
            val magnified = Modifier.graphicsLayer {
                // Enlarged toward the list: right edge fixed.
                transformOrigin = TransformOrigin(1f, 0.5f)
                // At rest: letters dimmed, except the one of the section at the top of the list.
                val rest = if (current.value == key) 1f else Tokens.railIdleAlpha
                val t = intensity
                alpha = rest
                if (t > 0f) {
                    val m = Tokens.railMagnifier
                    val distance = i + 0.5f - finger
                    val slot = height.toFloat() / keys.size
                    val s = 1f + (m.scale(distance) - 1f) * t
                    scaleX = s
                    scaleY = s
                    translationY = (m.center(i, finger) - (i + 0.5f)) * slot * t
                    alpha = rest + (m.alpha(distance) - rest) * t
                }
            }
            // Equal cells: the letter under the finger is computed on these cells (touchAt).
            Box(Modifier.weight(1f).fillMaxWidth().padding(end = Tokens.railLetterEnd), contentAlignment = Alignment.CenterEnd) {
                val index = headerIndex[key]
                if (index == null) {
                    // Letter without an app: same style as the others, hidden from TalkBack.
                    BasicText(key, style = Tokens.railLetter, modifier = magnified.clearAndSetSemantics {})
                } else {
                    // For TalkBack: each letter is an actionable element leading to its section.
                    BasicText(
                        key, style = Tokens.railLetter,
                        modifier = magnified
                            .semantics { contentDescription = "$jump $key" }
                            // No ripple: the magnifier is the rail's visual feedback.
                            .clickable(interactionSource = null, indication = null) { scope.launch { listState.scrollToSection(index, run) } },
                    )
                }
            }
        }
    }
}

/**
 * Brings section [index] to mid-height of the list, under the thumb; bounded by the list's ends.
 * On a letter tap, jumps to [run] short of the target, then a short scroll in the jump's direction. While dragging
 * on the rail ([run] zero), an instant jump: chained, jump and scroll read as jerks.
 * With animations off: instant.
 */
private suspend fun LazyListState.scrollToSection(index: Int, run: Float) {
    // Offset counted from the list's top padding, not from the top of the screen.
    val offset = layoutInfo.beforeContentPadding - layoutInfo.viewportSize.height / 2
    if (run == 0f || !ValueAnimator.areAnimatorsEnabled()) return scrollToItem(index, offset)
    val spec = tween<Float>(Tokens.railJumpMs, easing = Tokens.fadeInEasing)
    // Section already on screen: actual distance to mid-height, scrolled alone if it fits within the run.
    val distance = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { it.offset + offset }
    if (distance != null && abs(distance) <= run) {
        animateScrollBy(distance.toFloat(), spec)
        return
    }
    val sign = if (distance?.let { it > 0 } ?: (index > firstVisibleItemIndex)) 1 else -1
    scrollToItem(index, offset - sign * run.roundToInt())
    animateScrollBy(sign * run, spec)
}

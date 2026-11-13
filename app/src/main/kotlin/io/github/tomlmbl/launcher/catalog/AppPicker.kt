package io.github.tomlmbl.launcher.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.ui.AppName
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.ui.pressedIn
import io.github.tomlmbl.launcher.ui.rememberDot

/**
 * App picker (onboarding, dock slot): title, [header], search, optional suggestions, then every app in
 * alphabetical order. The search field is anchored at the bottom and its results stacked above it, the best one
 * closest to the field; a short list then aligns with the field. Nothing is preselected: [mark] only reflects the
 * actual state (place in the dock, selection order, current app). With [ordered], the mark is a numbered disc and an
 * unselected app shows a ring; with [full], unselected apps are grayed out. An excluded app stays visible, grayed
 * out, with the reason given by [excluded]. [headline] replaces the title and subtitle.
 */
@Composable
fun AppPicker(
    title: String,
    apps: List<App>,
    suggested: List<App>,
    suggestedTitle: String?,
    alphabet: Alphabet,
    query: String,
    onQueryChange: (String) -> Unit,
    color: suspend (App) -> Int?,
    mark: (App) -> String?,
    excluded: (App) -> String?,
    onPick: (App) -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = Tokens.screenTitle,
    margin: Dp = Tokens.screenMargin,
    markStyle: TextStyle = Tokens.label,
    ordered: Boolean = false,
    full: Boolean = false,
    headline: (@Composable ColumnScope.() -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val sorted = remember(apps, alphabet) { alphabet.sort(apps) { it.label } }
    val results = remember(apps, query, alphabet) { alphabet.search(apps, query) { it.label } }
    val row: @Composable (App) -> Unit = { PickerRow(it, color, mark(it), markStyle, excluded(it), margin, ordered, full, onPick) }
    val search: @Composable () -> Unit = {
        SearchField(query, onQueryChange, onSearch = { results.firstOrNull()?.takeIf { excluded(it) == null }?.let(onPick) }, margin = margin)
    }
    Column(modifier.fillMaxSize()) {
        if (headline != null) headline() else {
            BasicText(title, style = titleStyle, modifier = Modifier.padding(horizontal = margin, vertical = Tokens.Space.m).semantics { heading() })
        }
        header()
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = query.isNotBlank(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (query.isNotBlank()) {
                items(results, key = { "r:" + it.id }) { row(it) }
            } else {
                if (suggested.isNotEmpty() && suggestedTitle != null) {
                    item(key = "h:suggested") { Header(suggestedTitle, margin) }
                    items(suggested, key = { "s:" + it.id }) { row(it) }
                    item(key = "h:all") { Header(stringResource(R.string.all_apps), margin) }
                }
                items(sorted, key = { "a:" + it.id }) { row(it) }
            }
        }
        search()
        footer()
    }
}

@Composable
private fun Header(text: String, margin: Dp) {
    BasicText(text, style = Tokens.section, modifier = Modifier.padding(start = margin, top = Tokens.Space.ml, bottom = Tokens.Space.xs).semantics { heading() })
}

@Composable
private fun PickerRow(
    app: App,
    color: suspend (App) -> Int?,
    mark: String?,
    markStyle: TextStyle,
    excluded: String?,
    margin: Dp,
    ordered: Boolean,
    full: Boolean,
    onPick: (App) -> Unit,
) {
    val dot = rememberDot(app, color)
    val enabled = excluded == null && (mark != null || !full)
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, enabled = enabled) { onPick(app) }
            .heightIn(min = if (ordered) Tokens.favoriteRowHeight else Tokens.rowMinHeight)
            .pressedIn(interaction, Tokens.highlightShape, Tokens.rowInset)
            .padding(horizontal = margin - Tokens.rowInset)
            .alpha(if (enabled) 1f else Tokens.disabledAlpha)
            .semantics {
                selected = mark != null
                if (mark != null) stateDescription = mark
                if (excluded != null) contentDescription = "${app.description}, $excluded"
                else if (app.otherProfile) contentDescription = app.description
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Tokens.dotSize).background(dot ?: Color.Transparent, CircleShape))
        // Axis of the app names in app lists.
        Spacer(Modifier.width(Tokens.dotLabel))
        AppName(app.label, app.otherProfile, Tokens.label, Modifier.weight(1f))
        when {
            excluded != null -> BasicText(excluded, style = Tokens.section, modifier = Modifier.clearAndSetSemantics {})
            ordered -> OrderMark(mark)
            mark != null -> BasicText(mark, style = markStyle, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Order mark: numbered filled disc if the app is selected, a ring otherwise; decorative. */
@Composable
private fun OrderMark(mark: String?) {
    Box(
        Modifier
            .size(Tokens.orderMarkSize)
            .then(
                if (mark != null) Modifier.background(Tokens.onWallpaper, CircleShape)
                else Modifier.border(Tokens.emptySlotBorder, Tokens.onWallpaperMuted, CircleShape)
            )
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) { if (mark != null) BasicText(mark, style = Tokens.orderMark) }
}

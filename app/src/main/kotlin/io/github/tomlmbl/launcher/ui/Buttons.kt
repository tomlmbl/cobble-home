package io.github.tomlmbl.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Main button on the background: filled, inverse neutral; grayed out if [enabled] is false. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, minWidth: Dp = 0.dp) {
    Box(
        modifier
            .heightIn(min = Tokens.buttonHeight)
            .widthIn(min = minWidth)
            .alpha(if (enabled) 1f else Tokens.disabledAlpha)
            .clip(Tokens.pillShape)
            .background(Tokens.onWallpaper)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Tokens.Space.xxl),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Tokens.buttonLabel) }
}

/** Text button on the background ("Skip", "Not now"). */
@Composable
fun PlainButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = Tokens.buttonHeight)
            .widthIn(min = Tokens.buttonMinWidth)
            .clip(Tokens.pillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Tokens.Space.xxs),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Tokens.buttonText) }
}

/** Tonal button on the background ("Add app"): search field background, icon and label centered. */
@Composable
fun TonalButton(label: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier
            .heightIn(min = Tokens.tonalButtonHeight)
            .alpha(if (enabled) 1f else Tokens.disabledAlpha)
            .clip(Tokens.pillShape)
            .background(Tokens.fieldBackground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = Tokens.Space.l, end = Tokens.Space.xxl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, Tokens.glyphSize, Tokens.onWallpaper)
        Spacer(Modifier.width(Tokens.Space.m))
        BasicText(label, style = Tokens.buttonText)
    }
}

/**
 * "Undo" bar (`Undo bar`): message and action, on `inverseSurface`. Placed next to the other buttons, never over
 * them; announced by TalkBack when it appears.
 */
@Composable
fun UndoBar(message: String, action: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = Tokens.buttonHeight)
            .clip(Tokens.undoBarShape)
            .background(Tokens.undoBackground)
            .padding(start = Tokens.Space.xl, end = Tokens.Space.m)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(message, style = Tokens.undoText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = Tokens.touchTarget)
                .widthIn(min = Tokens.undoActionWidth)
                .clip(Tokens.pillShape)
                .clickable(role = Role.Button, onClick = onUndo),
            contentAlignment = Alignment.Center,
        ) { BasicText(action, style = Tokens.undoText) }
    }
}

package io.github.tomlmbl.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * Sheet at the bottom of the screen (profiles, app menu, full dock). A tap above closes it; Back and the Home button
 * are handled by the activity, which also sets the veil, the wallpaper blur and their fade. Surface and texts from
 * the dynamic theme.
 */
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SheetSurface(Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Sheet surface (`Sheet`): blur of the current screen and the wallpaper under a translucent surface, opaque when the
 * system turns blur off; design handle, padding and corner. Absorbs touches. [fill]: takes the height given by
 * [modifier] (slot picker) rather than its content's.
 */
@Composable
fun SheetSurface(modifier: Modifier, fill: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val blur = rememberCrossWindowBlurEnabled()
    // Inside a [SheetHost]: appearance, drag and predictive back.
    val motion = LocalSheetMotion.current
    BackdropBlur(
        if (blur) LocalBackdrop.current else null, Tokens.sheetShape, Tokens.windowBlur, modifier.sheetMotion(motion),
        scale = motion?.let { it::backScale },
    ) {
        // Veil under the sheet in the design, hence under its surface; useless under the opaque surface.
        if (blur) Box(Modifier.matchParentSize().background(Tokens.sheetVeil, Tokens.sheetShape))
        Column(
            Modifier
                .then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                // Content not distorted by the shrink: its height follows the width (like M3).
                .then(if (motion == null) Modifier else Modifier.graphicsLayer {
                    val factor = motion.backScale(size)
                    scaleY = factor.scaleX / factor.scaleY
                    transformOrigin = TransformOrigin(0.5f, 0f)
                })
                .background(if (blur) Tokens.sheetBlurredBackground else Tokens.sheetBackground, Tokens.sheetShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(top = Tokens.sheetTop, bottom = Tokens.sheetBottom),
        ) {
            SheetHandle()
            OnSurface { content() }
        }
    }
}

/** Handle (`Sheet handle`): decorative. */
@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().height(Tokens.handleArea), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = Tokens.handleWidth, height = Tokens.handleHeight).background(Tokens.onSheetMuted, Tokens.handleShape))
    }
}

@Composable
fun SheetTitle(text: String, subtitle: String? = null) {
    Column(
        Modifier.padding(horizontal = Tokens.sheetMargin, vertical = Tokens.Space.m),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs),
    ) {
        BasicText(text, style = Tokens.sheetTitle, modifier = Modifier.semantics { heading() })
        if (subtitle != null) BasicText(subtitle, style = Tokens.sheetBody.copy(color = Tokens.onSheetMuted))
    }
}

@Composable
fun SheetSection(text: String) {
    BasicText(text, style = Tokens.sheetSection, modifier = Modifier.padding(start = Tokens.sheetMargin, end = Tokens.sheetMargin, top = Tokens.Space.ml, bottom = Tokens.Space.xs).semantics { heading() })
}

/** Tappable sheet row; grayed out and inactive if [enabled] is false. Elements spaced by [spacing]. */
@Composable
fun SheetRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role? = null,
    spacing: Dp = Tokens.Space.l,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
            .heightIn(min = Tokens.rowMinHeight)
            .pressedIn(interaction, Tokens.highlightShape, Tokens.rowInset)
            .padding(horizontal = Tokens.sheetMargin - Tokens.rowInset)
            .alpha(if (enabled) 1f else Tokens.disabledAlpha),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Material Symbols icon of a row, decorative. */
@Composable
fun SheetIcon(@DrawableRes id: Int, tint: Color = Tokens.onSheetMuted) {
    Glyph(id, Tokens.glyphSize, tint)
}

/** Radio button of `Sheet row` (Type Profile): outline, filled mark if selected; decorative, the row carries the state. */
@Composable
fun SheetRadio(selected: Boolean) {
    Box(
        Modifier
            .size(Tokens.radioSize)
            .border(Tokens.targetBorder, if (selected) Tokens.onSheet else Tokens.onSheetMuted, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(Tokens.radioMark).background(Tokens.onSheet, CircleShape))
    }
}

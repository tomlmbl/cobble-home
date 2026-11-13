package io.github.tomlmbl.launcher.home

import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import io.github.tomlmbl.launcher.ui.LocalSheetMotion
import io.github.tomlmbl.launcher.ui.ScreenTransitions
import io.github.tomlmbl.launcher.ui.contactDisc
import io.github.tomlmbl.launcher.ui.contactProgress
import io.github.tomlmbl.launcher.ui.deferredPress
import io.github.tomlmbl.launcher.ui.pressScale
import io.github.tomlmbl.launcher.ui.pressedIn
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.ui.Sheet
import io.github.tomlmbl.launcher.ui.SheetRadio
import io.github.tomlmbl.launcher.ui.SheetRow
import io.github.tomlmbl.launcher.ui.SheetSurface
import io.github.tomlmbl.launcher.ui.SheetTitle
import io.github.tomlmbl.launcher.ui.Tokens

@Composable
fun profileLabel(profile: Profile): String = stringResource(
    when (profile) {
        Profile.WORK -> R.string.profile_work
        Profile.HOME -> R.string.profile_home
        Profile.OUT -> R.string.profile_out
        Profile.WORKOUT -> R.string.profile_workout
        Profile.TRAVEL -> R.string.profile_travel
    }
)

/** Themed icon loader: app, size in pixels, colors of the glyph, the disc and its outline. */
typealias ThemedIcons = suspend (App, Int, Int, Int, Int) -> ImageBitmap

/** Themed icon of [app] in the current theme colors, reloaded when they change; null while loading. */
@Composable
fun themedIcon(app: App?, icon: ThemedIcons, size: Dp = Tokens.dockIconSize): ImageBitmap? {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val glyph = Tokens.themedIcon.toArgb()
    val veil = Tokens.dockIconVeil.toArgb()
    val outline = Tokens.dockIconOutline.toArgb()
    // Keyed on the app, not its identity: an app update (new icon) reloads it.
    return produceState<ImageBitmap?>(null, app, px, glyph, veil, outline) { value = app?.let { icon(it, px, glyph, veil, outline) } }.value
}

/** Content of an empty slot in the dock slot picker's preview. */
private object EmptySlot

/** Empty dock slot: thin circle and "+" (`Dock slot`, Empty state). */
@Composable
private fun EmptySlotIcon() {
    Box(
        Modifier.size(Tokens.dockIconSize).border(Tokens.emptySlotBorder, Tokens.onWallpaperMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) { BasicText("+", style = Tokens.favorite.copy(shadow = null), modifier = Modifier.clearAndSetSemantics {}) }
}

/** Dock being chosen during onboarding: selected apps in their slots, "+" elsewhere; decorative. */
@Composable
fun DockChoicePreview(apps: List<App>, icon: ThemedIcons, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Tokens.dockSpacing)) {
        repeat(Dock.SIZE) { index ->
            val app = apps.getOrNull(index)
            val image = themedIcon(app, icon)
            Box(Modifier.size(Tokens.dockSlotSize), contentAlignment = Alignment.Center) {
                when {
                    image != null -> Image(image, contentDescription = null, modifier = Modifier.size(Tokens.dockIconSize))
                    app == null -> EmptySlotIcon()
                }
            }
        }
    }
}

/**
 * Dock of the active profile: three fixed slots, themed icons. An empty slot, or one whose app is gone, shows "+".
 * Tapping "+" or long-pressing a slot opens the picker.
 */
@Composable
fun DockRow(
    profile: Profile,
    dock: Dock,
    installed: Map<String, App>,
    icon: ThemedIcons,
    onLaunch: (App, Rect?) -> Unit,
    onSlot: (Int) -> Unit,
    slotModifier: (Int) -> Modifier = { Modifier },
) {
    val name = profileLabel(profile)
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.dockSpacing)) {
        dock.slots.forEachIndexed { index, slot ->
            val app = slot?.let { installed[it.appId] }
            Box(slotModifier(index)) { if (app == null) {
                val add = stringResource(R.string.add_to_dock_slot, name, index + 1)
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(Tokens.dockSlotSize)
                        // Long press consumed here: it must not open the Home menu.
                        .combinedClickable(interaction, indication = null, onClickLabel = add, onLongClick = { onSlot(index) }) { onSlot(index) }
                        .pressedIn(deferredPress(interaction), CircleShape)
                        .pressScale(interaction, Offset(0.5f, 0.5f))
                        .semantics { contentDescription = add },
                    contentAlignment = Alignment.Center,
                ) { EmptySlotIcon() }
            } else {
                DockIcon(app, icon, onLaunch, onReplace = { onSlot(index) })
            } }
        }
    }
}

@Composable
private fun DockIcon(app: App, icon: ThemedIcons, onLaunch: (App, Rect?) -> Unit, onReplace: () -> Unit) {
    val image = themedIcon(app, icon)
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val replace = stringResource(R.string.replace)
    val paused = stringResource(R.string.paused)
    val interaction = remember { MutableInteractionSource() }
    val progress = contactProgress(deferredPress(interaction))
    val veil = Tokens.dockIconVeil
    Box(
        Modifier
            .size(Tokens.dockSlotSize)
            .onGloballyPositioned { c ->
                bounds = c.windowRect()
            }
            .combinedClickable(interaction, indication = null, onLongClickLabel = replace, onLongClick = onReplace) { onLaunch(app, bounds) }
            // Touch feedback: the disc becomes a rounded square, instead of a ripple and a shrink.
            .contactDisc({ progress.value }, { veil }, Tokens.dockIconSize)
            .semantics {
                contentDescription = app.label
                if (app.paused) stateDescription = paused
                customActions = listOf(CustomAccessibilityAction(replace) { onReplace(); true })
            }
            .alpha(if (app.paused) Tokens.disabledAlpha else 1f),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.size(Tokens.dockIconSize).returnTarget(app.id, ReturnLook.Icon(it))) }
    }
}

/**
 * Discreet active profile indicator: thin outline and profile name, which carries the information, drawn in Home
 * like the rest (no blurred window). 48 dp touch target extended upward only, up to the dock slots. Tap: profile
 * sheet.
 */
@Composable
fun ProfileIndicator(profile: Profile, onClick: () -> Unit) {
    val name = profileLabel(profile)
    val description = stringResource(R.string.profile_indicator, name)
    // Ripple inside the pill; touch target unchanged.
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = Tokens.touchTarget)
            .widthIn(min = Tokens.touchTarget)
            .combinedClickable(interaction, indication = null, role = Role.Button, onLongClick = onClick, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.pressScale(interaction, Offset(0.5f, 0.5f))) {
            IndicatorPill(name, Modifier.pressedIn(deferredPress(interaction), Tokens.pillShape))
        }
    }
}

/** Non-interactive indicator, above the dock slot picker sheet (`Dock`, Pressed state). */
@Composable
private fun IndicatorPreview(profile: Profile, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = Tokens.touchTarget), contentAlignment = Alignment.BottomCenter) {
        IndicatorPill(profileLabel(profile), Modifier)
    }
}

/** Thin outline and profile name. */
@Composable
private fun IndicatorPill(name: String, modifier: Modifier) {
    Box(
        Modifier
            .border(Tokens.hairline, Tokens.indicatorOutline, Tokens.pillShape)
            .then(modifier)
            .heightIn(min = Tokens.indicatorHeight)
            .padding(horizontal = Tokens.Space.l),
        contentAlignment = Alignment.Center,
    ) {
        // Profile name crossfades, the pill following its width.
        AnimatedContent(name, transitionSpec = { ScreenTransitions.profileSwap() }, label = "profile") {
            BasicText(it, style = Tokens.indicatorLabel, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Profile sheet: the three chosen profiles, the active one checked, each with its dock's three icons, without added
 * color; a tap applies at once.
 */
@Composable
fun ProfileSheet(
    selected: List<Profile>,
    active: Profile?,
    dock: (Profile) -> Dock,
    installed: Map<String, App>,
    icon: ThemedIcons,
    onChoose: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        SheetTitle(stringResource(R.string.profile))
        selected.forEach { profile ->
            SheetRow(onClick = { onChoose(profile) }, role = Role.RadioButton, modifier = Modifier.semantics { this.selected = profile == active }) {
                SheetRadio(profile == active)
                BasicText(profileLabel(profile), style = Tokens.menuLabel, modifier = Modifier.weight(1f))
                DockPreview(dock(profile), installed, icon)
            }
        }
    }
}

/** A dock's three slots, small; decorative. */
@Composable
private fun DockPreview(dock: Dock, installed: Map<String, App>, icon: ThemedIcons) {
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Tokens.dockPreviewSpacing)) {
        dock.slots.forEach { slot ->
            val image = themedIcon(slot?.let { installed[it.appId] }, icon, Tokens.dockPreviewIcon)
            Box(Modifier.size(Tokens.dockPreviewIcon), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(Tokens.dockPreviewIcon))
                else Box(Modifier.size(Tokens.dockPreviewIcon).border(Tokens.hairline, Tokens.onSheetMuted, CircleShape))
            }
        }
    }
}

/**
 * Position of Home's dock, vertical center of its icons in the window: the slot picker's preview leaves from and
 * returns to it with the sheet. [lifted]: the preview is composed (exit included); Home's dock is then hidden,
 * otherwise both would overlap while Home comes back.
 */
class DockAnchor {
    var centerY by mutableFloatStateOf(Float.NaN)
    var lifted by mutableStateOf(false)
}

/** Records the element's vertical center in [anchor]. */
fun Modifier.dockAnchor(anchor: DockAnchor): Modifier = onGloballyPositioned { anchor.centerY = it.boundsInWindow().center.y }

/**
 * Replacing a slot: the rest of Home fades out; the profile's dock and its indicator sit just above the sheet, the
 * target slot ringed with the accent. The sheet holds the picker. Tapping a slot in the preview targets it, sheet
 * open ([onSlot]); a tap above, outside the dock row, cancels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DockSlotSheet(
    profile: Profile,
    dock: Dock,
    index: Int,
    installed: Map<String, App>,
    icon: ThemedIcons,
    anchor: DockAnchor,
    onSlot: (Int) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Sheet progress (0 off screen, 1 in place): the dock slides from its place on Home to above the sheet, and the
    // target slot takes its outline, in step with the sheet (opening, drag, closing).
    val motion = LocalSheetMotion.current
    val progress = { motion?.let { if (it.height > 0f) 1f - it.shown / it.height else 0f } ?: 1f }
    var ownCenter by remember { mutableFloatStateOf(Float.NaN) }
    // Icons loaded before hiding Home's dock: otherwise one frame without icons on opening.
    val apps = dock.slots.map { slot -> slot?.let { installed[it.appId] } }
    val images = apps.map { themedIcon(it, icon) }
    val ready = apps.indices.all { apps[it] == null || images[it] != null }
    // Once lifted, Home's dock stays hidden until the end: a chosen app whose icon is still loading does not make it
    // reappear under the descending preview.
    SideEffect { if (ready) anchor.lifted = true }
    DisposableEffect(anchor) { onDispose { anchor.lifted = false } }
    val accent = Tokens.accent
    // Keyboard opening: the dock preview gives way to the results in step with the keyboard, the sheet rising with it;
    // it comes back the same way on closing. 0 without keyboard, 1 keyboard open.
    val density = LocalDensity.current
    val imeNow = WindowInsets.ime.getBottom(density)
    val imeFull = maxOf(WindowInsets.imeAnimationSource.getBottom(density), WindowInsets.imeAnimationTarget.getBottom(density))
    val typing = if (imeFull > 0) (imeNow.toFloat() / imeFull).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .imePadding(),
    ) {
        if (typing < 1f) Column(
            Modifier
                .weight(1f - typing)
                .fillMaxWidth()
                // Faded out in the first half of the collapse, while its room still fits its content: no clipping,
                // which would also hide it on its way from Home's dock.
                .graphicsLayer { alpha = (1f - 2 * typing).coerceAtLeast(0f) }
                // Preview only: TalkBack reads the sheet title, which names the profile and the slot.
                .clearAndSetSemantics {},
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .onGloballyPositioned { ownCenter = it.boundsInWindow().center.y }
                    // A tap between two slots does not close the sheet.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .graphicsLayer {
                        val from = anchor.centerY - ownCenter
                        if (!from.isNaN()) translationY = from * (1f - progress())
                    },
                horizontalArrangement = Arrangement.spacedBy(Tokens.dockSpacing),
            ) {
                apps.forEachIndexed { i, app ->
                    val image = images[i]
                    // The outline crossfades from one slot to another when another is targeted.
                    val target by animateFloatAsState(if (i == index) 1f else 0f, Tokens.effectsDefault(), label = "target")
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(Tokens.dockSlotSize)
                            .clickable(interaction, indication = null) { onSlot(i) }
                            .pressScale(interaction, Offset(0.5f, 0.5f))
                            .drawWithContent {
                                drawContent()
                                val w = Tokens.targetBorder.toPx()
                                val alpha = progress().coerceIn(0f, 1f) * target
                                if (alpha > 0f) drawCircle(accent, radius = (size.minDimension - w) / 2, alpha = alpha, style = Stroke(w))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Slot emptied or filled with the sheet open: the old content gives way to the new one.
                        AnimatedContent(if (app == null) EmptySlot else image, transitionSpec = { ScreenTransitions.slotSwap() }, label = "slot") {
                            when (it) {
                                EmptySlot -> EmptySlotIcon()
                                is ImageBitmap -> Image(it, contentDescription = null, modifier = Modifier.size(Tokens.dockIconSize))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Tokens.dockToIndicator - (Tokens.touchTarget - Tokens.indicatorHeight)))
            IndicatorPreview(profile, Modifier.graphicsLayer {
                val from = anchor.centerY - ownCenter
                if (!from.isNaN()) translationY = from * (1f - progress())
            })
            // Design: dock bottom margin (`space/indicatorBottom`) and 4 dp to the sheet.
            Spacer(Modifier.height(Tokens.indicatorBottom + Tokens.Space.xs))
        }
        SheetSurface(Modifier.weight(Tokens.dockSlotSheetWeight).fillMaxWidth(), fill = true, content = content)
    }
}

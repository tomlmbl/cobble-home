package io.github.tomlmbl.launcher.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.IntOffset

/**
 * Screen transitions. [slide]: slide distance in pixels (`Tokens.sharedAxisSlide`). None delays an app launch: they
 * only apply to the launcher's screens. With reduced animations, Compose makes them instant.
 */
object ScreenTransitions {
    private const val outMs = (Tokens.screenTransitionMs * Tokens.fadeThroughThreshold).toInt()
    private const val inMs = Tokens.screenTransitionMs - outMs

    /** The leaving screen fades over the first 35 % of the duration, the entering one appears afterwards (`FadeThroughProvider`). */
    private fun crossFade() =
        fadeIn(tween(inMs, delayMillis = outMs, easing = Tokens.emphasizedEasing)) togetherWith
            fadeOut(tween(outMs, easing = Tokens.emphasizedEasing))

    /**
     * Opacity of a shared-axis or fade-through screen, in step with its fade, to paint consistently what it reveals
     * below it ("Clock style" preview).
     */
    @Composable
    fun Transition<EnterExitState>.fadeThroughAlpha(): State<Float> = animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(inMs, delayMillis = outMs, easing = Tokens.emphasizedEasing)
            else tween(outMs, easing = Tokens.emphasizedEasing)
        },
        label = "fadeThroughAlpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }

    /** Horizontal shared axis: onboarding steps, settings subpages; direction reversed on Back. */
    fun sharedAxisX(forward: Boolean, slide: Int): ContentTransform {
        val spec = tween<IntOffset>(Tokens.screenTransitionMs, easing = Tokens.emphasizedEasing)
        val sign = if (forward) 1 else -1
        val fade = crossFade()
        return ContentTransform(
            fade.targetContentEnter + slideInHorizontally(spec) { sign * slide },
            fade.initialContentExit + slideOutHorizontally(spec) { -sign * slide },
            sizeTransform = null,
        )
    }

    /** Fade through: the entering screen grows from 92 % (Home ↔ favorites editor, settings, picker). */
    fun fadeThrough(): ContentTransform {
        val fade = crossFade()
        return ContentTransform(
            fade.targetContentEnter +
                scaleIn(tween(Tokens.screenTransitionMs, easing = Tokens.emphasizedEasing), initialScale = Tokens.fadeThroughScale),
            fade.initialContentExit,
            sizeTransform = null,
        )
    }

    /**
     * Profile change, manual or signal-driven: nothing at the whole-dock level; each slot carries its own transition
     * ([profileSlotEnter], [profileSlotExit]), and the leaving dock stays composed until the last one ends.
     */
    fun profileSwitch() = ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

    /** Slot [index] of the entering dock: fade and slide from the left, after the slots to its left. */
    fun profileSlotEnter(index: Int, slide: Int) =
        fadeIn(tween(Tokens.profileSwitchSlotMs, index * Tokens.profileSwitchStaggerMs, Tokens.emphasizedEasing)) +
            slideInHorizontally(tween(Tokens.profileSwitchSlotMs, index * Tokens.profileSwitchStaggerMs, Tokens.emphasizedEasing)) { -slide }

    /** Slot [index] of the leaving dock: fade and slide to the right, at the same pace. */
    fun profileSlotExit(index: Int, slide: Int) =
        fadeOut(tween(Tokens.profileSwitchSlotMs, index * Tokens.profileSwitchStaggerMs, Tokens.emphasizedEasing)) +
            slideOutHorizontally(tween(Tokens.profileSwitchSlotMs, index * Tokens.profileSwitchStaggerMs, Tokens.emphasizedEasing)) { slide }

    /**
     * Profile name in the indicator: short crossfade; a changing size follows on the fast spatial spring.
     */
    fun profileSwap() = ContentTransform(
        fadeIn(Tokens.effectsDefault()),
        fadeOut(Tokens.effectsFast()),
        sizeTransform = SizeTransform(clip = false) { _, _ -> Tokens.spatialFast() },
    )

    /**
     * Switching from one sheet to the next without closing it: app menu → full-dock or full-favorites choice, and from
     * the latter back to the menu. The surface stays in place; the old content fades out, the new one fades in and the
     * height follows on the default spatial spring, content clipped during the size change.
     */
    fun sheetSwap() = ContentTransform(
        fadeIn(Tokens.effectsDefault()),
        fadeOut(Tokens.effectsFast()),
        sizeTransform = SizeTransform(clip = true) { _, _ -> Tokens.spatialDefault() },
    )

    /**
     * Changing content of a slot in the slot picker's preview: the old one shrinks and fades, the new one grows from
     * the menu's scale, on M3 Expressive springs. Values on trial.
     */
    fun slotSwap() = ContentTransform(
        fadeIn(Tokens.effectsDefault()) + scaleIn(Tokens.spatialFast(), initialScale = Tokens.menuClosedScale),
        fadeOut(Tokens.effectsFast()) + scaleOut(Tokens.effectsFast(), targetScale = Tokens.menuClosedScale),
        sizeTransform = null,
    )
}

/** Catalog entry, which sets its choreography ([CatalogMotion]); [Search]: search only. */
enum class CatalogEntry { Up, Search }

/**
 * Home ↔ catalog: a single progress, 0 on Home, 1 on the catalog. Both stay composed; the finger drives the progress
 * over [Tokens.catalogCourse] ([CatalogDeck]), Back and the accessibility action animate it.
 * Vertical shared axis: Home fades out rising up to the fade-through threshold, then the catalog appears rising;
 * never both overlapping. The content travels the course in step with the finger.
 */
object CatalogMotion {
    private const val split = Tokens.fadeThroughThreshold

    private fun out(p: Float) = (p / split).coerceIn(0f, 1f)
    private fun enter(p: Float) = ((p - split) / (1f - split)).coerceIn(0f, 1f)
    private fun ramp(p: Float, from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)

    /**
     * Search only: Home fades over most of the course instead of the first third (a faster fade felt too abrupt), the
     * field overlaps at the end of the course. At p = 1 and p = 0, same values as [CatalogEntry.Up].
     */
    fun homeAlpha(p: Float, e: CatalogEntry = CatalogEntry.Up) = when (e) {
        CatalogEntry.Up -> 1f - out(p)
        CatalogEntry.Search -> 1f - ramp(p, 0f, Tokens.searchHomeFadeEnd)
    }
    fun homeOffset(p: Float, course: Float, e: CatalogEntry = CatalogEntry.Up) = when (e) {
        CatalogEntry.Up -> -course * p.coerceIn(0f, 1f)
        CatalogEntry.Search -> -course * Tokens.searchHomeRise * p.coerceIn(0f, 1f)
    }
    fun catalogAlpha(p: Float, e: CatalogEntry = CatalogEntry.Up) = when (e) {
        CatalogEntry.Up -> enter(p)
        CatalogEntry.Search -> ramp(p, Tokens.searchFieldFrom, 1f)
    }
    /** Vertical over [course]; zero in search, the field only moves with the keyboard. */
    fun catalogOffset(p: Float, course: Float, e: CatalogEntry = CatalogEntry.Up) =
        if (e == CatalogEntry.Search) 0f else course * (1f - p.coerceIn(0f, 1f))
    /** Catalog veil: full once Home is gone, taking over from Home's flat veil. */
    fun veil(p: Float, e: CatalogEntry = CatalogEntry.Up) = 1f - homeAlpha(p, e)
    /**
     * Blur radius: [Tokens.catalogBlurEasing] curve up to [Tokens.catalogBlurEnd] of the progress, so it still changes
     * while the catalog appears (Launcher3: full at mid-course).
     */
    fun blur(p: Float) = Tokens.catalogBlurEasing.transform((p / Tokens.catalogBlurEnd).coerceIn(0f, 1f))
}

/** Home ↔ catalog progress ([CatalogMotion]) and release velocity, consumed by the following animation. */
class CatalogDeck {
    val progress = Animatable(0f)
    private var releaseVelocity: Float? = null

    fun release(velocity: Float) { releaseVelocity = velocity }
    fun takeVelocity(): Float? = releaseVelocity.also { releaseVelocity = null }
}

package io.github.tomlmbl.launcher.context

/**
 * What Home exchanges with automatic switching, without depending on the signal sources: it reports when it is
 * visible and idle, and which profile the user picks.
 */
interface AutoSwitch {
    /** Whether Home is visible (from `onStart` to `onStop`). */
    fun visible(visible: Boolean)

    /** Home is idle: no catalog, no sheet, no favorites editor, no onboarding, no finger down. */
    fun atRest(rest: Boolean)

    /** Manual choice of [profile]: any pending change is dropped; counted in the local measure ([Measure]). */
    fun chosen(profile: Profile)
}

package io.github.tomlmbl.launcher.ui

import io.github.tomlmbl.launcher.BuildConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Status bar time of SystemUI demo mode, reused by the `screenshots` build. */
private val SCREENSHOT_TIME = LocalTime.of(16, 0)

/**
 * Time shown by Home and settings: the real time, except in the `screenshots` build, where it is frozen at 16:00 of
 * the current day.
 */
fun shownNow(): Long {
    if (!BuildConfig.SCREENSHOTS) return System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone).atTime(SCREENSHOT_TIME).atZone(zone).toInstant().toEpochMilli()
}

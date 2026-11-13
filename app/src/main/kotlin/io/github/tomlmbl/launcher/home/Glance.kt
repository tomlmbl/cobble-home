package io.github.tomlmbl.launcher.home

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Home weather, prepared by the `weather` package: Home does not depend on it. */
data class WeatherGlance(val hours: List<GlanceHour>, val fetchedAt: Long) {
    /** Hour containing [now], null if the forecast no longer covers it. */
    fun at(now: Long) = hours.hourAt(now) { it.time }
}

/** Forecast hour containing [now], shared by Home and the panel; [time]: start of the hour. */
fun <T> List<T>.hourAt(now: Long, time: (T) -> Long): T? = filter { time(it) <= now && now - time(it) < HOUR_MS }.maxByOrNull(time)

/** One forecast hour, already formatted; [time]: start of the hour. */
data class GlanceHour(val time: Long, @param:DrawableRes val icon: Int, val temperature: String)

/** Shown as is, dimmed, or hidden. */
enum class Freshness { FRESH, AGED, HIDDEN }

/**
 * Weather dimmed after 4 h (a request every 3 h, plus one hour of margin), hidden after 12 h. A reading dated in the
 * future: unknown age, never fresh; except by less than a minute, since Home's clock only ticks every minute
 * (refreshed right after).
 */
fun WeatherGlance.freshness(now: Long): Freshness = freshness(fetchedAt, now)

/** Same rule for the panel. */
fun freshness(fetchedAt: Long, now: Long): Freshness {
    val age = now - fetchedAt
    return when {
        age < -MINUTE_MS -> Freshness.AGED
        age <= AGED_AFTER_MS -> Freshness.FRESH
        age <= HIDDEN_AFTER_MS -> Freshness.AGED
        else -> Freshness.HIDDEN
    }
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val AGED_AFTER_MS = 4 * HOUR_MS
private const val HIDDEN_AFTER_MS = 12 * HOUR_MS

/** The only weather interface Home sees; [visible]: Home in the foreground (refresh). */
interface WeatherFeed {
    val glance: StateFlow<WeatherGlance?>
    fun visible(visible: Boolean)
}

/** Home event, picked by the `calendar` package; never stored. */
data class EventGlance(val title: String, val begin: Long, val end: Long)

/** Time shown after the title, computed again at each minute of Home's clock. */
sealed interface EventTime {
    /** Ongoing, until [end]. */
    data class Until(val end: Long) : EventTime
    /** Starts in less than an hour; [minutes] rounded up. */
    data class InMinutes(val minutes: Int) : EventTime
    data class Today(val begin: Long) : EventTime
    data class Tomorrow(val begin: Long) : EventTime
    data class Later(val day: DayOfWeek, val begin: Long) : EventTime
}

/** null once the event is over: the line disappears until the next calendar read. */
fun EventGlance.time(now: Long, zone: ZoneId): EventTime? {
    if (end <= now) return null
    if (begin <= now) return EventTime.Until(end)
    val wait = begin - now
    if (wait < HOUR_MS) return EventTime.InMinutes(((wait + MINUTE_MS - 1) / MINUTE_MS).toInt())
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
    return when (day) {
        today -> EventTime.Today(begin)
        today.plusDays(1) -> EventTime.Tomorrow(begin)
        else -> EventTime.Later(day.dayOfWeek, begin)
    }
}

/**
 * The only calendar interface Home sees; [next] is null without permission, without an event or on failure (line
 * hidden). [visible]: Home in the foreground (read on display and every minute).
 */
interface EventFeed {
    val next: StateFlow<EventGlance?>
    /** Calendar allowed: the event line then counts toward the favorites maximum. */
    val allowed: StateFlow<Boolean>
    fun visible(visible: Boolean)
}

/**
 * Weather and calendar panel, provided by the `panel` package: Home only knows this interface.
 */
interface GlancePanel {
    @Composable
    fun Sheet(now: Long, onDismiss: () -> Unit)
}

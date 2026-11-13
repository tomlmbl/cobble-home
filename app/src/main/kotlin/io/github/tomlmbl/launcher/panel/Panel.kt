package io.github.tomlmbl.launcher.panel

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.collectAsState
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.calendar.CalendarRepository
import io.github.tomlmbl.launcher.calendar.NextEvent
import io.github.tomlmbl.launcher.calendar.Occurrence
import io.github.tomlmbl.launcher.home.Freshness
import io.github.tomlmbl.launcher.home.appDetailsIntent
import io.github.tomlmbl.launcher.home.startQuietly
import io.github.tomlmbl.launcher.home.GlancePanel
import io.github.tomlmbl.launcher.home.formatTime
import io.github.tomlmbl.launcher.home.freshness
import io.github.tomlmbl.launcher.settings.SettingsActivity
import io.github.tomlmbl.launcher.ui.PrimaryButton
import io.github.tomlmbl.launcher.ui.Sheet
import io.github.tomlmbl.launcher.ui.SheetSection
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.ui.WeatherPicture
import io.github.tomlmbl.launcher.ui.pressedIn
import io.github.tomlmbl.launcher.weather.Forecast
import io.github.tomlmbl.launcher.weather.Hour
import io.github.tomlmbl.launcher.weather.Sky
import io.github.tomlmbl.launcher.weather.TemperatureUnit
import io.github.tomlmbl.launcher.weather.Weather
import io.github.tomlmbl.launcher.weather.WeatherData
import io.github.tomlmbl.launcher.weather.WeatherRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date


/**
 * Weather and calendar panel: current conditions and the next 12 hours, then the calendar over [NextEvent.DAYS]
 * days, grouped by day, the next event highlighted. An event opens in the system calendar; calendar access is only
 * requested from the panel's button.
 */
class Panel(private val weather: WeatherRepository, private val calendar: CalendarRepository) : GlancePanel {

    @Composable
    override fun Sheet(now: Long, onDismiss: () -> Unit) {
        val data by weather.data.collectAsState()
        val online by weather.online.collectAsState()
        val unit by weather.unit.collectAsState()
        val events by calendar.events.collectAsState()
        val allowed by calendar.allowed.collectAsState()
        val context = LocalContext.current
        val requestCalendar = rememberCalendarRequest(onGranted = { calendar.refresh("granted") })
        // Calendar read again at every opening: a recent edit or sync shows without waiting for the minute.
        LaunchedEffect(Unit) { calendar.refresh("panel") }
        // Sheet capped below the status bar; its content scrolls (85 % of the window).
        val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * MAX_HEIGHT).toDp() }
        Sheet(onDismiss) {
            Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                val figures = weatherBlock(data ?: WeatherData(), now, online, unit, onSetCity = {
                    startQuietly(context, SettingsActivity.weatherIntent(context))
                    onDismiss()
                })
                Box(Modifier.padding(horizontal = Tokens.sheetMargin, vertical = Tokens.Space.m).fillMaxWidth().height(Tokens.hairline).background(Tokens.panelDivider))
                if (!allowed) {
                    SheetSection(stringResource(R.string.panel_agenda))
                    Body(stringResource(R.string.show_events_here))
                    ButtonWrap { PrimaryButton(stringResource(R.string.allow_calendar), requestCalendar) }
                } else {
                    Agenda(events, now, onOpen = { openEvent(context, it) })
                }
                // Small attribution under the calendar, only if weather figures are shown.
                if (figures) BasicText(
                    stringResource(R.string.weather_by_open_meteo), style = Tokens.attribution,
                    modifier = Modifier.fillMaxWidth().padding(start = Tokens.sheetMargin, end = Tokens.sheetMargin, top = Tokens.Space.l, bottom = Tokens.Space.m),
                )
            }
        }
    }

    private companion object {
        const val MAX_HEIGHT = 0.85f
    }
}

/**
 * Panel weather: up to date, stale (dimmed, its age on the second line), no city, or nothing received or older than
 * 12 h. True if figures are shown.
 */
@Composable
private fun weatherBlock(data: WeatherData, now: Long, online: Boolean, unit: TemperatureUnit, onSetCity: () -> Unit): Boolean {
    if (data.city == null) {
        SheetSection(stringResource(R.string.weather))
        Body(stringResource(R.string.choose_city_to_see_weather))
        ButtonWrap { PrimaryButton(stringResource(R.string.set_a_city), onSetCity) }
        return false
    }
    val forecast = data.current
    val freshness = forecast?.let { freshness(it.fetchedAt, now) }
    val current = forecast?.now(now)
    val offline = if (online) "" else " · " + stringResource(R.string.offline_short)
    if (forecast == null || current == null || freshness == Freshness.HIDDEN) {
        // No figures and no attribution without displayed data.
        val reason = when {
            forecast != null -> updatedAgo(forecast, now) + offline
            online -> stringResource(R.string.loading)
            else -> stringResource(R.string.loads_once_online)
        }
        Now(R.drawable.ic_cloud_off, stringResource(if (forecast == null) R.string.no_weather_yet else R.string.weather_out_of_date), reason, dimmed = true)
        return false
    }
    val aged = freshness == Freshness.AGED
    val sky = Sky.of(current.code)
    val day = forecast.day(now)
    val range = if (!aged && day != null) unit.format(day.high) to unit.format(day.low) else null
    val rain = if (aged) null else forecast.rainFrom(now)
    // Two lines: as the title, what matters for the day, likely rain, otherwise the current sky; the current
    // temperature is in the "Now" cell of the hourly strip, the current sky in the picture.
    val title = when {
        rain == null -> stringResource(sky.label)
        rain <= now -> stringResource(R.string.rain_likely_now)
        else -> stringResource(R.string.rain_likely_from, formatTime(rain))
    }
    Now(
        sky.picture(current.isDay), title,
        if (aged) updatedAgo(forecast, now) + offline else null, dimmed = aged, picture = true,
    ) {
        when {
            range != null -> HighLow(range.first, range.second, data.city.name)
            // Without the day's high and low: the city alone, dimmed.
            !aged -> BasicText(
                data.city.name, style = Tokens.sheetBody.copy(color = Tokens.onSheetMuted), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Hours(current, forecast, now, unit, Modifier.alpha(if (aged) Tokens.staleWeatherAlpha else 1f))
    return true
}

@Composable
private fun updatedAgo(forecast: Forecast, now: Long): String {
    val hours = ((now - forecast.fetchedAt) / 3_600_000L).toInt().coerceAtLeast(1)
    return pluralStringResource(R.plurals.updated_hours_ago, hours, hours)
}

/**
 * "Now" row: colored condition picture ([picture], 64 dp) or a tinted 40 dp glyph, conditions, then the data's age
 * ([body]) or high, low and city ([extra], [HighLow]); icon and title dimmed if [dimmed].
 */
@Composable
private fun Now(
    @DrawableRes icon: Int, title: String, body: String?, dimmed: Boolean, picture: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    Row(
        Modifier.padding(horizontal = Tokens.sheetMargin, vertical = Tokens.Space.m).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (dimmed) Tokens.staleWeatherAlpha else 1f
        if (picture) WeatherPicture(icon, Tokens.panelPicture, Modifier.alpha(alpha))
        else Image(painterResource(icon), null, Modifier.size(Tokens.panelGlyph).alpha(alpha), colorFilter = ColorFilter.tint(Tokens.onSheet))
        // 4 dp between the title and the next line: at 2, high and low touched the title.
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
            BasicText(title, style = Tokens.sheetTitle, modifier = Modifier.alpha(alpha))
            if (body != null) BasicText(body, style = Tokens.sheetBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            extra()
        }
    }
}

/**
 * High and low with Material Symbols arrows, more legible than the font's; glyph sized in `sp` to follow the text
 * size. Then a dimmed "· city", like the event time on Home; the city is truncated first.
 * TalkBack reads "High …, low …".
 */
@Composable
private fun HighLow(high: String, low: String, place: String) {
    val style = Tokens.sheetBody
    val glyph = with(LocalDensity.current) { Tokens.rangeGlyph.toDp() }
    val spoken = stringResource(R.string.high_low_description, high, low)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
        Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_arrow_upward, glyph, style.color)
            BasicText(high, style = style, maxLines = 1)
            Spacer(Modifier.width(Tokens.Space.xs))
            Glyph(R.drawable.ic_arrow_downward, glyph, style.color)
            BasicText(low, style = style, maxLines = 1)
        }
        BasicText(
            stringResource(R.string.then_place, place), style = style.copy(color = Tokens.onSheetMuted), maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Now, then the next 12 hours, scrolling sideways. */
@Composable
private fun Hours(current: Hour, forecast: Forecast, now: Long, unit: TemperatureUnit, modifier: Modifier) {
    val hours = listOf(current.copy(time = now)) + Weather.nextHours(forecast.hours, now)
    Row(modifier.horizontalScroll(rememberScrollState()).padding(start = Tokens.Space.xl, end = Tokens.Space.xl, top = Tokens.Space.xs, bottom = Tokens.Space.xs)) {
        hours.forEachIndexed { index, hour -> HourCell(if (index == 0) stringResource(R.string.hour_now) else formatTime(hour.time), hour, unit) }
    }
}

@Composable
private fun HourCell(label: String, hour: Hour, unit: TemperatureUnit) {
    val temperature = unit.format(hour.temperature)
    Column(
        Modifier.width(Tokens.hourCellWidth).padding(vertical = Tokens.Space.m).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(label, style = Tokens.supporting, maxLines = 1)
        val sky = Sky.of(hour.code)
        val condition = stringResource(sky.label)
        WeatherPicture(sky.picture(hour.isDay), Tokens.hourPicture, Modifier.semantics { contentDescription = condition })
        BasicText(temperature, style = Tokens.sheetBody, maxLines = 1)
    }
}

/** Calendar: days with events, the next one highlighted; "No upcoming events" when there are none. */
@Composable
private fun Agenda(events: List<Occurrence>, now: Long, onOpen: (Occurrence) -> Unit) {
    val zone = remember(now) { ZoneId.systemDefault() }
    val days = remember(events, now) { NextEvent.days(events, now, zone) }
    if (days.isEmpty()) {
        SheetSection(stringResource(R.string.panel_agenda))
        Body(stringResource(R.string.no_events_this_week))
        return
    }
    val next = remember(events, now) { NextEvent.pick(events, now) }
    val today = remember(now) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
    days.forEach { day ->
        SheetSection(dayLabel(day.date, today, zone))
        day.events.forEach { event -> EventRow(event, next = event == next, onClick = { onOpen(event) }) }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate, zone: ZoneId): String = when (date) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    else -> {
        val locale = LocalConfiguration.current.locales[0]
        val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), Date(millis)).toString()
    }
}

/** `Event row`: neutral band, or accent under a veil for the next event; time, then title. */
@Composable
private fun EventRow(event: Occurrence, next: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val time = if (event.allDay) stringResource(R.string.all_day)
    else stringResource(R.string.time_range, formatTime(event.begin), formatTime(event.end))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.sheetMargin - Tokens.rowInset)
            .then(if (next) Modifier.background(Tokens.nextEventVeil, Tokens.highlightShape) else Modifier)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressedIn(interaction, Tokens.highlightShape)
            .padding(horizontal = Tokens.rowInset, vertical = Tokens.Space.m),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Tokens.eventStripeWidth, Tokens.eventStripeHeight)
                .background(if (next) Tokens.nextEventStripe else Tokens.eventStripe, RoundedCornerShape(Tokens.eventStripeWidth / 2))
                .clearAndSetSemantics {}
        )
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.xxs)) {
            BasicText(time, style = Tokens.supporting)
            BasicText(
                event.title.ifBlank { stringResource(R.string.untitled_event) }, style = Tokens.sheetBody,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    BasicText(
        text, style = Tokens.sheetBody.copy(color = Tokens.onSheetMuted),
        modifier = Modifier.padding(start = Tokens.sheetMargin, end = Tokens.sheetMargin, top = Tokens.Space.xs, bottom = Tokens.Space.m),
    )
}

@Composable
private fun ButtonWrap(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = Tokens.sheetMargin, vertical = Tokens.Space.m)) { content() }
}

/**
 * `READ_CALENDAR` request. Permanent denial: the system no longer shows a dialog; a tap that did not open one (no
 * rationale offered before or after) opens the app's settings. A denial just given in the dialog does not send the
 * user there.
 */
@Composable
private fun rememberCalendarRequest(onGranted: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val before = remember { booleanArrayOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted()
        else if (activity != null && !before[0] && !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            openAppSettings(activity)
        }
    }
    return {
        before[0] = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == true
        launcher.launch(Manifest.permission.READ_CALENDAR)
    }
}

private fun openAppSettings(activity: Activity) = startQuietly(activity, appDetailsIntent(activity.packageName))

/** Opens the event in the system calendar; nothing if no app handles it. */
private fun openEvent(context: Context, event: Occurrence) = startQuietly(
    context,
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end),
)

package io.github.tomlmbl.launcher.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import io.github.tomlmbl.launcher.home.EventFeed
import io.github.tomlmbl.launcher.home.EventGlance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Calendar: read when Home is shown, every minute while it stays visible, on every change reported by the
 * calendar provider (a DAVx5 sync, an edit in the calendar app) and when the panel opens; never stored.
 * Without `READ_CALENDAR`, nothing is read; the permission is only requested from the panel, which reads again
 * right away. The `Calendar` log holds only states and counts ([Tally]), never a title, a time or a calendar name.
 */
class CalendarRepository(private val context: Context, private val scope: CoroutineScope) : EventFeed {

    private val _next = MutableStateFlow<EventGlance?>(null)
    override val next: StateFlow<EventGlance?> = _next.asStateFlow()

    private val _events = MutableStateFlow<List<Occurrence>>(emptyList())
    /** Occurrences read for the panel, in memory only; empty without the permission. */
    val events: StateFlow<List<Occurrence>> = _events.asStateFlow()

    private val _allowed = MutableStateFlow(hasPermission())
    override val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private var shown = 0
    private var reading: Job? = null
    private var observing = false
    private var lastTally: Tally? = null

    /** Change in the calendar provider: read again after a short delay, so the notices of one sync are batched. */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        private var pending: Job? = null
        override fun onChange(selfChange: Boolean) {
            pending?.cancel()
            pending = scope.launch(Dispatchers.IO) {
                delay(CHANGE_DELAY_MS)
                read("change")
            }
        }
    }

    override fun visible(visible: Boolean) {
        if (visible) {
            if (shown++ == 0) {
                observe(true)
                reading = scope.launch(Dispatchers.IO) {
                    var reason = "shown"
                    while (isActive) {
                        read(reason)
                        reason = "tick"
                        delay(NextEvent.readDelay(_next.value?.end, System.currentTimeMillis()))
                    }
                }
            }
        } else if (shown > 0 && --shown == 0) {
            reading?.cancel()
            reading = null
            observe(false)
        }
    }

    /** Immediate read: permission granted from the panel, panel opened. */
    fun refresh(reason: String = "refresh") {
        if (shown > 0 && !observing) observe(true)
        scope.launch(Dispatchers.IO) { read(reason) }
    }

    /** Observes the provider while Home is visible and the permission is granted (otherwise `SecurityException`). */
    private fun observe(on: Boolean) {
        if (on && !observing && hasPermission()) {
            observing = runCatching {
                context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            }.isSuccess
        } else if (!on && observing) {
            context.contentResolver.unregisterContentObserver(observer)
            observing = false
        }
    }

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    private fun read(reason: String) {
        val now = System.currentTimeMillis()
        _allowed.value = hasPermission()
        val events = if (_allowed.value) {
            runCatching { query(now, reason) }.onFailure { Log.i(TAG, "read failed ($reason): ${it.javaClass.simpleName}") }.getOrDefault(emptyList())
        } else emptyList()
        _events.value = events
        _next.value = NextEvent.pick(events, now)?.let { EventGlance(it.title, it.begin, it.end) }
    }

    /**
     * Occurrences of the visible calendars, recurrences included. The range extends one day on each side: all-day
     * events are stored in UTC; [NextEvent] sorts afterwards. Every occurrence in the range is read, hidden calendars
     * included, so that [Tally] counts what is left out and why; the selection stays that of `Instances.query`
     * (visible calendars, canceled occurrences left out).
     */
    private fun query(now: Long, reason: String): List<Occurrence> {
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.SELF_ATTENDEE_STATUS,
            Instances.STATUS, Instances.TITLE, Instances.VISIBLE,
        )
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - DAY_MS)
            ContentUris.appendId(it, now + (NextEvent.DAYS + 1) * DAY_MS)
        }.build()
        val cursor = context.contentResolver.query(uri, projection, null, null, null) ?: return emptyList()
        val rows = cursor.use { c ->
            buildList {
                while (c.moveToNext()) add(InstanceRow(
                    occurrence = Occurrence(
                        id = c.getLong(0),
                        begin = c.getLong(1),
                        end = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                        declined = !c.isNull(4) && c.getInt(4) == Attendees.ATTENDEE_STATUS_DECLINED,
                        title = c.getString(6).orEmpty(),
                    ),
                    visible = c.isNull(7) || c.getInt(7) == 1,
                    canceled = !c.isNull(5) && c.getInt(5) == Events.STATUS_CANCELED,
                ))
            }
        }
        val (kept, tally) = Tally.of(rows, now)
        if (tally != lastTally) {
            Log.i(TAG, "read ($reason): $tally")
            lastTally = tally
        }
        return kept
    }

    private companion object {
        const val TAG = "Calendar"
        const val CHANGE_DELAY_MS = 500L
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24 * 60 * MINUTE_MS
    }
}

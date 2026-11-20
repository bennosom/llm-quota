package work.becode.llmquota

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss")
private val DATE_TIME_SHORT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)
private val DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH)

/** "Today, 16:27:15" for today, "20 Nov 2026, 16:27:15" for another day. */
fun formatStamp(at: Instant, now: Instant, zone: ZoneId): String {
    val local = at.atZone(zone)
    return if (local.toLocalDate() == now.atZone(zone).toLocalDate()) {
        "Today, ${TIME_SECONDS.format(local)}"
    } else {
        DATE_TIME_SECONDS.format(local)
    }
}

/** "Today, 21:18" for today, "24 Nov, 12:58" for another day. */
fun formatAbsolute(at: Instant, now: Instant, zone: ZoneId): String {
    val local = at.atZone(zone)
    return if (local.toLocalDate() == now.atZone(zone).toLocalDate()) {
        "Today, ${TIME.format(local)}"
    } else {
        DATE_TIME_SHORT.format(local)
    }
}

/** The time until [at] in its highest unit only: "in 4 days", "in 3 h", "in 12 min"; "now" once reached. */
fun formatRelative(at: Instant, now: Instant): String {
    val left = Duration.between(now, at)
    return when {
        left.isNegative || left.isZero -> "now"
        left.toDays() >= 1 -> left.toDays().let { if (it == 1L) "in 1 day" else "in $it days" }
        left.toHours() >= 1 -> "in ${left.toHours()} h"
        left.toMinutes() >= 1 -> "in ${left.toMinutes()} min"
        else -> "in <1 min"
    }
}

/** Absolute reset time and the time left, for a window row. */
fun formatReset(epochSec: Long?, now: Instant, zone: ZoneId): String {
    if (epochSec == null) return "Reset –"
    val at = Instant.ofEpochSecond(epochSec)
    return "Reset ${formatAbsolute(at, now, zone)} · ${formatRelative(at, now)}"
}

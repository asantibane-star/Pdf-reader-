package dev.lector.app.domain.usecase

import dev.lector.app.domain.model.RecentDocument
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class RecentGroupKind { Today, Week, Month, Older }

data class RecentGroup(val kind: RecentGroupKind, val items: List<RecentDocument>)

/** Agrupa los recientes (ya ordenados del más nuevo al más antiguo) en Hoy / Esta semana / Este mes / Antes. */
fun groupRecents(items: List<RecentDocument>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<RecentGroup> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val buckets = RecentGroupKind.entries.associateWith { ArrayList<RecentDocument>() }
    for (item in items) {
        val date = Instant.ofEpochMilli(item.lastOpenedAt).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        val kind = when {
            days <= 0 -> RecentGroupKind.Today
            days <= WEEK_DAYS -> RecentGroupKind.Week
            days <= MONTH_DAYS -> RecentGroupKind.Month
            else -> RecentGroupKind.Older
        }
        buckets.getValue(kind) += item
    }
    return RecentGroupKind.entries.mapNotNull { kind -> buckets.getValue(kind).takeIf { it.isNotEmpty() }?.let { RecentGroup(kind, it) } }
}

private const val WEEK_DAYS = 6L
private const val MONTH_DAYS = 30L

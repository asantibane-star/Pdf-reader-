package dev.lector.app.domain

import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.domain.model.RecentDocument
import dev.lector.app.domain.usecase.RecentGroupKind
import dev.lector.app.domain.usecase.groupRecents
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupRecentsTest {
    private val zone = ZoneId.of("America/Santiago")
    private val now = ZonedDateTime.of(2026, 3, 18, 15, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun recent(id: Long, daysAgo: Long, hourOfDay: Int = 10) = RecentDocument(
        id = id, uri = "content://x/$id", displayName = "Doc $id", kind = DocumentKind.Pdf, sizeBytes = 1,
        lastOpenedAt = ZonedDateTime.of(2026, 3, 18, hourOfDay, 0, 0, 0, zone).minusDays(daysAgo).toInstant().toEpochMilli(),
        lastPage = 0, pageCount = 1,
    )

    @Test fun groupsByCalendarDistance() {
        val groups = groupRecents(
            listOf(recent(1, 0), recent(2, 1), recent(3, 6), recent(4, 7), recent(5, 30), recent(6, 31)),
            now, zone,
        )
        assertEquals(
            listOf(RecentGroupKind.Today, RecentGroupKind.Week, RecentGroupKind.Month, RecentGroupKind.Older),
            groups.map { it.kind },
        )
        assertEquals(listOf(1L), groups[0].items.map { it.id })
        assertEquals(listOf(2L, 3L), groups[1].items.map { it.id })
        assertEquals(listOf(4L, 5L), groups[2].items.map { it.id })
        assertEquals(listOf(6L), groups[3].items.map { it.id })
    }

    @Test fun omitsEmptyGroupsAndKeepsOrder() {
        val groups = groupRecents(listOf(recent(1, 0, 14), recent(2, 0, 9)), now, zone)
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.single().items.map { it.id })
    }

    @Test fun emptyInputGivesNoGroups() {
        assertEquals(emptyList<Any>(), groupRecents(emptyList(), now, zone))
    }
}

package com.sportall.az.domain

import com.sportall.az.domain.usecases.GetStatisticsUseCase
import com.sportall.az.domain.usecases.TimeFilter
import com.sportall.az.models.Category
import com.sportall.az.models.HistoryRecord
import com.sportall.az.repositories.DefaultDrillRepository
import com.sportall.az.repositories.FakeLocalDataSource
import com.sportall.az.repositories.HistoryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

private class InMemoryHistoryRepository(records: List<HistoryRecord> = emptyList()) : HistoryRepository {
    private val items = records.toMutableList()
    override fun add(record: HistoryRecord) { items.add(record) }
    override fun getAll(): List<HistoryRecord> = items.toList()
    override fun clear() { items.clear() }
}

class GetStatisticsUseCaseTest {

    private val drills = DefaultDrillRepository(FakeLocalDataSource())
    private val now = Clock.System.now().toEpochMilliseconds()
    private val daysAgo = { days: Int -> now - days.days.inWholeMilliseconds }

    private fun useCase(vararg records: HistoryRecord) =
        GetStatisticsUseCase(InMemoryHistoryRepository(records.toList()), drills)

    @Test
    fun emptyHistory_givesEmptyStatistics() = runTest {
        val result = useCase()()

        assertEquals(0, result.total)
        assertNull(result.avgStars)
        assertEquals(emptyMap(), result.byCategory)
        assertEquals(emptyList(), result.mostUsed)
    }

    @Test
    fun averageRating_ignoresRecordsWithoutStars() = runTest {
        val result = useCase(
            HistoryRecord(drillId = 1, date = now, stars = 4),
            HistoryRecord(drillId = 1, date = now, stars = 2),
            HistoryRecord(drillId = 2, date = now, stars = null)
        )()

        assertEquals(3, result.total)
        assertEquals(3.0, result.avgStars)
    }

    @Test
    fun byCategory_countsRecordsPerCategory() = runTest {
        val result = useCase(
            HistoryRecord(1, now), HistoryRecord(4, now), HistoryRecord(2, now)
        )()

        assertEquals(2, result.byCategory[Category.WarmUp])
        assertEquals(1, result.byCategory[Category.Rondo])
    }

    @Test
    fun mostUsed_isSortedByUsageAndCarriesAverageRating() = runTest {
        val result = useCase(
            HistoryRecord(2, now, stars = 5),
            HistoryRecord(1, now, stars = 3),
            HistoryRecord(1, now, stars = 5)
        )()

        assertEquals(listOf(1, 2), result.mostUsed.map { it.drillId })
        assertEquals(2, result.mostUsed.first().usageCount)
        assertEquals(4.0, result.mostUsed.first().avgRating)
    }

    @Test
    fun sevenDayFilter_dropsOlderRecords() = runTest {
        val result = useCase(
            HistoryRecord(1, daysAgo(1)),
            HistoryRecord(1, daysAgo(3)),
            HistoryRecord(1, daysAgo(40))
        )(TimeFilter.SEVEN_DAYS)

        assertEquals(2, result.total)
    }

    @Test
    fun thirtyDayFilter_keepsRecordsInsideTheWindow() = runTest {
        val result = useCase(
            HistoryRecord(1, daysAgo(10)),
            HistoryRecord(1, daysAgo(29)),
            HistoryRecord(1, daysAgo(60))
        )(TimeFilter.THIRTY_DAYS)

        assertEquals(2, result.total)
    }

    @Test
    fun allTimeFilter_keepsEverything() = runTest {
        val result = useCase(
            HistoryRecord(1, daysAgo(1)),
            HistoryRecord(1, daysAgo(500))
        )(TimeFilter.ALL_TIME)

        assertEquals(2, result.total)
    }
}

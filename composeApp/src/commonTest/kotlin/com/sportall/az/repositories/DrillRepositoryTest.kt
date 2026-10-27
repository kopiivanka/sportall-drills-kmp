package com.sportall.az.repositories

import com.sportall.az.models.Category
import com.sportall.az.models.Difficulty
import com.sportall.az.sources.LocalDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val DRILLS_JSON = """
[
  {"id": 1, "name": "Circle Mobility", "category": "WarmUp", "difficulty": "Easy", "estimatedTime": 5,
   "setup": [], "execution": [], "coachingPoints": [], "visualDescription": "",
   "steps": ["Pass the ball around the cones"]},
  {"id": 2, "name": "Rondo 4v2", "category": "Rondo", "difficulty": "Medium", "estimatedTime": 10,
   "setup": [], "execution": [], "coachingPoints": [], "visualDescription": "",
   "steps": ["Keep possession under pressure"]},
  {"id": 3, "name": "Finishing Under Pressure", "category": "Shooting", "difficulty": "Hard", "estimatedTime": 15,
   "setup": [], "execution": [], "coachingPoints": [], "visualDescription": "",
   "steps": ["Shoot first time"], "isExclusive": true},
  {"id": 4, "name": "Dynamic Warm-up Line", "category": "WarmUp", "difficulty": "Easy", "estimatedTime": 5,
   "setup": [], "execution": [], "coachingPoints": [], "visualDescription": "",
   "steps": ["Jog and stretch"]}
]
"""

internal class FakeLocalDataSource(private val text: String = DRILLS_JSON) : LocalDataSource {
    var reads = 0
        private set

    override suspend fun readText(path: String): String {
        reads++
        return text
    }
}

class DrillRepositoryTest {

    private val dataSource = FakeLocalDataSource()
    private val repository = DefaultDrillRepository(dataSource)

    @Test
    fun getAll_parsesAllDrillsFromJson() = runTest {
        assertEquals(4, repository.getAll().size)
    }

    @Test
    fun getById_returnsDrillWhenItExists() = runTest {
        val drill = repository.getById(2)
        assertNotNull(drill)
        assertEquals("Rondo 4v2", drill.name)
        assertEquals(Category.Rondo, drill.category)
    }

    @Test
    fun getById_returnsNullForUnknownId() = runTest {
        assertNull(repository.getById(999))
    }

    @Test
    fun search_matchesNameIgnoringCase() = runTest {
        assertEquals(listOf(2), repository.search("RONDO").map { it.id })
    }

    @Test
    fun search_matchesTextInSteps() = runTest {
        assertEquals(listOf(3), repository.search("first time").map { it.id })
    }

    @Test
    fun search_blankQueryReturnsEverything() = runTest {
        assertEquals(4, repository.search("   ").size)
    }

    @Test
    fun search_noMatchReturnsEmptyList() = runTest {
        assertEquals(emptyList(), repository.search("goalkeeper gloves"))
    }

    @Test
    fun filterByCategory_returnsOnlyThatCategory() = runTest {
        assertEquals(listOf(1, 4), repository.filterByCategory(Category.WarmUp).map { it.id })
    }

    @Test
    fun filterByDifficulty_returnsOnlyThatLevel() = runTest {
        assertEquals(listOf(3), repository.filterByDifficulty(Difficulty.Hard).map { it.id })
    }

    @Test
    fun getCategories_returnsDistinctCategories() = runTest {
        assertEquals(
            listOf(Category.WarmUp, Category.Rondo, Category.Shooting),
            repository.getCategories()
        )
    }

    @Test
    fun isExclusive_reflectsDrillFlag() = runTest {
        assertEquals(true, repository.isExclusive(repository.getById(3)!!))
        assertEquals(false, repository.isExclusive(repository.getById(1)!!))
    }

    @Test
    fun jsonIsReadOnlyOnceAndThenServedFromCache() = runTest {
        repository.getAll()
        repository.getById(1)
        repository.search("rondo")
        assertEquals(1, dataSource.reads)
    }
}

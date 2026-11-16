package dev.lector.app.data.recents

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.testing.TestDispatchers
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecentsRepositoryTest {
    private lateinit var db: LectorDatabase
    private lateinit var thumbs: ThumbnailStore
    private val dir = Files.createTempDirectory("thumbs").toFile()

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LectorDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun repo(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): RoomRecentsRepository {
        val dispatchers = TestDispatchers(StandardTestDispatcher(scheduler))
        thumbs = ThumbnailStore(dir, dispatchers)
        return RoomRecentsRepository(db.recentDao(), thumbs, dispatchers)
    }

    private fun ref(n: Int, name: String = "Doc $n") = DocumentRef(
        id = "id$n", cachePath = "/tmp/$n", kind = DocumentKind.Pdf, displayName = name, sourceUri = "content://x/$n", sizeBytes = 100L * n,
    )

    @Test fun upsertKeepsOneRowPerUriAndPreservesProgress() = runTest {
        val repo = repo(testScheduler)
        val first = (repo.record(ref(1), now = 1_000) as Outcome.Success).value
        repo.updateProgress(first.id, page = 11, pageCount = 48)
        val again = (repo.record(ref(1, name = "Nombre nuevo"), now = 9_000) as Outcome.Success).value
        assertEquals(first.id, again.id)
        assertEquals(11, again.lastPage)
        assertEquals(48, again.pageCount)
        val all = repo.observe().first()
        assertEquals(1, all.size)
        assertEquals("Nombre nuevo", all.single().displayName)
        assertEquals(9_000, all.single().lastOpenedAt)
    }

    @Test fun listIsNewestFirst() = runTest {
        val repo = repo(testScheduler)
        repo.record(ref(1), now = 1_000)
        repo.record(ref(2), now = 3_000)
        repo.record(ref(3), now = 2_000)
        assertEquals(listOf("Doc 2", "Doc 3", "Doc 1"), repo.observe().first().map { it.displayName })
    }

    @Test fun purgesToOneHundredRowsAndTheirThumbnails() = runTest {
        val repo = repo(testScheduler)
        for (n in 1..105) repo.record(ref(n), now = n * 1_000L)
        val all = repo.observe().first()
        assertEquals(RoomRecentsRepository.MAX_ROWS, all.size)
        assertEquals("Doc 105", all.first().displayName)
        assertEquals("Doc 6", all.last().displayName)
        assertFalse(all.any { it.displayName == "Doc 5" })
    }

    @Test fun removeRestoreAndClear() = runTest {
        val repo = repo(testScheduler)
        val row = (repo.record(ref(1), now = 1_000) as Outcome.Success).value
        repo.remove(row.id)
        assertTrue(repo.observe().first().isEmpty())
        repo.restore(row)
        assertEquals(row.id, repo.observe().first().single().id)
        repo.clear()
        assertTrue(repo.observe().first().isEmpty())
    }
}

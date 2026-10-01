package me.aliahad.audioplayer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Room DAO (the JVM property tests only model its contract with in-memory lists). */
@RunWith(AndroidJUnit4::class)
class TimestampDaoTest {

    private lateinit var database: TimestampDatabase
    private lateinit var dao: TimestampDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TimestampDatabase::class.java
        ).build()
        dao = database.timestampDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bookmarksAreReturnedInPlaybackOrder() = runBlocking {
        listOf(90_000L, 5_000L, 42_000L).forEach { position ->
            dao.insert(bookmark(TRACK_A, FOLDER_1, position))
        }

        val positions = dao.getBookmarksForTrack(TRACK_A, FOLDER_1).first().map { it.positionMs }

        assertEquals(listOf(5_000L, 42_000L, 90_000L), positions)
    }

    @Test
    fun bookmarksAreScopedToTrackAndFolder() = runBlocking {
        dao.insert(bookmark(TRACK_A, FOLDER_1, 1_000L))
        dao.insert(bookmark(TRACK_B, FOLDER_1, 2_000L))
        dao.insert(bookmark(TRACK_A, FOLDER_2, 3_000L))

        assertEquals(listOf(1_000L), dao.getBookmarksForTrack(TRACK_A, FOLDER_1).first().map { it.positionMs })
        assertEquals(listOf(2_000L), dao.getBookmarksForTrack(TRACK_B, FOLDER_1).first().map { it.positionMs })
        assertEquals(listOf(3_000L), dao.getBookmarksForTrack(TRACK_A, FOLDER_2).first().map { it.positionMs })
    }

    @Test
    fun deleteByIdRemovesOnlyThatBookmarkAndKeepsNotes() = runBlocking {
        dao.insert(bookmark(TRACK_A, FOLDER_1, 1_000L, note = "intro"))
        dao.insert(bookmark(TRACK_A, FOLDER_1, 2_000L, note = null))
        val first = dao.getBookmarksForTrack(TRACK_A, FOLDER_1).first().first()

        dao.deleteById(first.id)

        val remaining = dao.getBookmarksForTrack(TRACK_A, FOLDER_1).first()
        assertEquals(listOf(2_000L), remaining.map { it.positionMs })
        assertEquals(listOf<String?>(null), remaining.map { it.note })
    }

    private fun bookmark(track: String, folder: String, positionMs: Long, note: String? = null) =
        TimestampBookmark(audioFileUri = track, folderUri = folder, positionMs = positionMs, note = note)

    private companion object {
        const val FOLDER_1 = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FA"
        const val FOLDER_2 = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FB"
        const val TRACK_A = "$FOLDER_1/document/primary%3AMusic%2FA%2Fsong.mp3"
        const val TRACK_B = "$FOLDER_1/document/primary%3AMusic%2FA%2Fother.mp3"
    }
}

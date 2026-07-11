package io.cloudcauldron.bocan.playback.lyrics

import io.cloudcauldron.bocan.persistence.daos.LyricsDao
import io.cloudcauldron.bocan.persistence.entities.LyricsCacheEntity
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.NoopLog
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsRepositoryTests {
    private class FakeLyricsDao(seed: LyricsCacheEntity? = null) : LyricsDao {
        private val rows = HashMap<Long, LyricsCacheEntity>()
        init {
            seed?.let { rows[it.trackId] = it }
        }
        override suspend fun get(trackId: Long): LyricsCacheEntity? = rows[trackId]
        override suspend fun upsert(entity: LyricsCacheEntity) {
            rows[entity.trackId] = entity
        }
    }

    private class FakeFetcher(private val result: FetchResult) : LyricsFetcher {
        var fetches = 0
            private set
        override suspend fun fetch(trackId: Long): FetchResult {
            fetches++
            return result
        }
    }

    private fun repo(dao: LyricsDao, fetcher: LyricsFetcher): LyricsRepository {
        val d = UnconfinedTestDispatcher()
        return LyricsRepository(dao, fetcher, CoroutineDispatchers(io = d, default = d, main = d), NoopLog)
    }

    @Test
    fun `a cache hit by hash serves without fetching`() = runTest {
        val dao = FakeLyricsDao(LyricsCacheEntity(1, "h1", LyricsKind.Synced, "[00:01.00]a", Instant.EPOCH))
        val fetcher = FakeFetcher(FetchResult.Unreachable)
        val result = repo(dao, fetcher).lyricsFor(trackId = 1, lyricsHash = "h1")
        val loaded = assertIs<LyricsResult.Loaded>(result)
        assertIs<LyricsDoc.Synced>(loaded.doc)
        assertEquals(0, fetcher.fetches)
    }

    @Test
    fun `a changed hash refetches and caches`() = runTest {
        val dao = FakeLyricsDao(LyricsCacheEntity(1, "old", LyricsKind.Synced, "stale", Instant.EPOCH))
        val fetcher = FakeFetcher(FetchResult.Found(LyricsKind.Unsynced, "fresh words"))
        val result = repo(dao, fetcher).lyricsFor(trackId = 1, lyricsHash = "new")
        val loaded = assertIs<LyricsResult.Loaded>(result)
        assertEquals(LyricsDoc.Unsynced("fresh words"), loaded.doc)
        assertEquals(1, fetcher.fetches)
        assertEquals("new", dao.get(1)?.lyricsHash)
    }

    @Test
    fun `a null lyrics hash is none without fetching`() = runTest {
        val fetcher = FakeFetcher(FetchResult.Found(LyricsKind.Unsynced, "x"))
        assertEquals(LyricsResult.None, repo(FakeLyricsDao(), fetcher).lyricsFor(1, null))
        assertEquals(0, fetcher.fetches)
    }

    @Test
    fun `offline with no cache is none`() = runTest {
        val fetcher = FakeFetcher(FetchResult.Unreachable)
        assertEquals(LyricsResult.None, repo(FakeLyricsDao(), fetcher).lyricsFor(1, "h1"))
    }

    @Test
    fun `offline with a stale cache serves the stale copy`() = runTest {
        val dao = FakeLyricsDao(LyricsCacheEntity(1, "old", LyricsKind.Unsynced, "old words", Instant.EPOCH))
        val result = repo(dao, FakeFetcher(FetchResult.Unreachable)).lyricsFor(1, "new")
        assertEquals(LyricsResult.Loaded(LyricsDoc.Unsynced("old words")), result)
    }

    @Test
    fun `a 404 is none and is remembered for the session`() = runTest {
        val fetcher = FakeFetcher(FetchResult.NotFound)
        val repository = repo(FakeLyricsDao(), fetcher)
        assertEquals(LyricsResult.None, repository.lyricsFor(1, "h1"))
        assertEquals(LyricsResult.None, repository.lyricsFor(1, "h1"))
        assertEquals(1, fetcher.fetches)
    }
}

package io.cloudcauldron.bocan.playback.podcast

import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.NoopLog
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChaptersRepositoryTests {
    private class FakeFetcher(private val body: String?) : ChaptersFetcher {
        var fetches = 0
            private set
        override suspend fun fetch(episodeId: String): String? {
            fetches++
            return body
        }
    }

    private fun repo(fetcher: ChaptersFetcher): ChaptersRepository {
        val d = UnconfinedTestDispatcher()
        return ChaptersRepository(fetcher, CoroutineDispatchers(io = d, default = d, main = d), NoopLog)
    }

    @Test
    fun `an episode without chapters is not fetched`() = runTest {
        val fetcher = FakeFetcher("""{"chapters":[{"startTime":0,"title":"x"}]}""")
        assertTrue(repo(fetcher).chaptersFor("ep", hasChapters = false).isEmpty())
        assertEquals(0, fetcher.fetches)
    }

    @Test
    fun `chapters are fetched, parsed, and cached`() = runTest {
        val fetcher = FakeFetcher("""{"chapters":[{"startTime":0,"title":"Intro"},{"startTime":60,"title":"Main"}]}""")
        val repository = repo(fetcher)
        val first = repository.chaptersFor("ep", hasChapters = true)
        assertEquals(listOf("Intro", "Main"), first.map { it.title })
        repository.chaptersFor("ep", hasChapters = true)
        assertEquals(1, fetcher.fetches)
    }

    @Test
    fun `an unreachable fetch yields no chapters`() = runTest {
        assertTrue(repo(FakeFetcher(null)).chaptersFor("ep", hasChapters = true).isEmpty())
    }
}

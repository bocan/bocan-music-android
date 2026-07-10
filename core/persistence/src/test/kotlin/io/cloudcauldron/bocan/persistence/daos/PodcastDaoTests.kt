package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.Manifests
import io.cloudcauldron.bocan.persistence.firstList
import io.cloudcauldron.bocan.persistence.fixedClockApplier
import io.cloudcauldron.bocan.persistence.fixtureManifest
import io.cloudcauldron.bocan.persistence.runDbTest
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PodcastDaoTests {
    private val threeEpisodes = Manifests.manifest(
        podcasts = listOf(Manifests.podcast(4)),
        episodes = listOf(
            Manifests.episode("ep-a", publishedAt = "2026-06-01T09:00:00Z"),
            Manifests.episode("ep-b", publishedAt = "2026-06-08T09:00:00Z"),
            Manifests.episode("ep-c", publishedAt = "2026-06-15T09:00:00Z")
        )
    )

    @Test
    fun `shows come back sorted by title`() = runDbTest { db ->
        fixedClockApplier(db).apply(fixtureManifest())
        assertEquals(listOf("Some Show"), db.podcastDao().observeShows().firstList().map { it.title })
    }

    @Test
    fun `episode order flips with the sort flag`() = runDbTest { db ->
        fixedClockApplier(db).apply(threeEpisodes)
        val dao = db.podcastDao()
        assertEquals(
            listOf("ep-c", "ep-b", "ep-a"),
            dao.observeEpisodes(4L, sortNewestFirst = true).firstList().map { it.id }
        )
        assertEquals(
            listOf("ep-a", "ep-b", "ep-c"),
            dao.observeEpisodes(4L, sortNewestFirst = false).firstList().map { it.id }
        )
    }

    @Test
    fun `continue listening orders by recency and drops completed episodes`() = runDbTest { db ->
        fixedClockApplier(db).apply(threeEpisodes)
        val states = db.episodeStateDao()
        states.updatePosition("ep-a", 1000, at = FIXED_NOW.minus(2, ChronoUnit.HOURS))
        states.updatePosition("ep-b", 2000, at = FIXED_NOW.minus(1, ChronoUnit.HOURS))
        states.updatePosition("ep-c", 3000, at = FIXED_NOW.minus(3, ChronoUnit.HOURS))
        states.markPlayed("ep-c", at = FIXED_NOW)

        val items = db.podcastDao().observeContinueListening().firstList()
        assertEquals(listOf("ep-b", "ep-a"), items.map { it.episode.id })
        assertEquals(listOf(2000L, 1000L), items.map { it.playPositionMs })
    }

    @Test
    fun `continue listening emits reactively as progress changes`() = runDbTest { db ->
        fixedClockApplier(db).apply(threeEpisodes)
        db.podcastDao().observeContinueListening().test {
            assertTrue(awaitItem().isEmpty())

            db.episodeStateDao().updatePosition("ep-a", 500, at = FIXED_NOW)

            assertEquals(listOf("ep-a"), awaitItem().map { it.episode.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

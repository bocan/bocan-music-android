package io.cloudcauldron.bocan.persistence

import android.content.Context
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.cloudcauldron.bocan.persistence.daos.BrowseDao
import io.cloudcauldron.bocan.persistence.daos.EpisodeStateDao
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.daos.LyricsDao
import io.cloudcauldron.bocan.persistence.daos.PlayStatsDao
import io.cloudcauldron.bocan.persistence.daos.PlaylistDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.persistence.daos.ScrobbleDao
import io.cloudcauldron.bocan.persistence.daos.SearchDao
import io.cloudcauldron.bocan.persistence.daos.SyncDao
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.entities.LyricsCacheEntity
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistTrackEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.ScrobbleQueueEntity
import io.cloudcauldron.bocan.persistence.entities.SyncServerEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.entities.TrackFtsEntity
import kotlin.coroutines.CoroutineContext

/**
 * The one Room database. Always built on BundledSQLiteDriver: Room only
 * guarantees FTS5 with the bundled SQLite, never the platform one.
 */
@Database(
    entities = [
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PodcastEntity::class,
        EpisodeEntity::class,
        TrackFtsEntity::class,
        PlayStatsEntity::class,
        EpisodeStateEntity::class,
        LyricsCacheEntity::class,
        SyncServerEntity::class,
        ScrobbleQueueEntity::class
    ],
    version = 1
)
@ColumnTypeConverters(Converters::class)
abstract class BocanDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun podcastDao(): PodcastDao

    abstract fun searchDao(): SearchDao

    abstract fun playStatsDao(): PlayStatsDao

    abstract fun lyricsDao(): LyricsDao

    abstract fun episodeStateDao(): EpisodeStateDao

    abstract fun syncDao(): SyncDao

    abstract fun scrobbleDao(): ScrobbleDao

    abstract fun browseDao(): BrowseDao

    companion object {
        const val FILE_NAME = "bocan.db"

        /** The production database: bundled driver, WAL journal, app-private file. */
        fun create(context: Context, queryContext: CoroutineContext): BocanDatabase =
            Room.databaseBuilder(context, BocanDatabase::class.java, FILE_NAME)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(queryContext)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        /** An in-memory database on the same driver, for tests. */
        fun createInMemory(context: Context, queryContext: CoroutineContext): BocanDatabase =
            Room.inMemoryDatabaseBuilder(context, BocanDatabase::class.java)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(queryContext)
                .build()
    }
}

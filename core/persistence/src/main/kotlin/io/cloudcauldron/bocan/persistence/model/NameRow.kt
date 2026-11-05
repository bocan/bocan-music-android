package io.cloudcauldron.bocan.persistence.model

/**
 * The light (id, name) projection of an artist or album. The car tree groups these into
 * letter buckets in Kotlin, because SQLite cannot fold accents, then reads the members of
 * one bucket back by id.
 */
data class NameRow(val id: Long, val name: String)

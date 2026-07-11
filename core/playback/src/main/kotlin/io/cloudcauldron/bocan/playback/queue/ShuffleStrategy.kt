package io.cloudcauldron.bocan.playback.queue

import kotlin.random.Random

/**
 * One track's inputs to a shuffle, decoupled from any Room or Media3 type so the
 * shuffle stays pure and testable. [skipCount] comes from play stats,
 * [playedRecently] flags tracks heard earlier in this listening session (recency
 * damping), and [excludedFromShuffle] drops a track from the shuffle pool entirely.
 */
data class ShuffleItem(
    val mediaId: String,
    val skipCount: Long = 0,
    val playedRecently: Boolean = false,
    val excludedFromShuffle: Boolean = false
)

/**
 * Produces a shuffled order of media ids. Shuffling reorders the actual queue
 * (deterministic and testable) rather than delegating to ExoPlayer's opaque
 * built-in shuffle order. A [Random] is injected so tests can seed it.
 */
sealed interface ShuffleStrategy {
    /**
     * The shuffled order of [items]' media ids. Items with [ShuffleItem.excludedFromShuffle]
     * are removed first, so they never appear in a shuffled run.
     */
    fun order(items: List<ShuffleItem>, random: Random): List<String>

    /** A uniform random permutation of the eligible items (in-place Fisher-Yates). */
    data object FisherYates : ShuffleStrategy {
        override fun order(items: List<ShuffleItem>, random: Random): List<String> {
            val pool = items.filterNot { it.excludedFromShuffle }.map { it.mediaId }.toMutableList()
            for (i in pool.indices.reversed()) {
                val j = random.nextInt(i + 1)
                val tmp = pool[i]
                pool[i] = pool[j]
                pool[j] = tmp
            }
            return pool
        }
    }

    /**
     * Weighted shuffle that favours tracks the listener does not skip and that were
     * not heard recently. Each track's weight is `(1 / (1 + skipCount)) * recencyDamp`,
     * where recently played tracks are damped by [RECENCY_DAMP]. Implemented as
     * weighted sampling without replacement, so high-weight tracks tend to come
     * first while every eligible track still appears exactly once.
     */
    data class SmartShuffle(val recencyDamp: Double = RECENCY_DAMP) : ShuffleStrategy {
        override fun order(items: List<ShuffleItem>, random: Random): List<String> {
            val pool = items.filterNot { it.excludedFromShuffle }.toMutableList()
            val result = ArrayList<String>(pool.size)
            while (pool.isNotEmpty()) {
                val weights = pool.map { weightOf(it) }
                val pickedIndex = sampleIndex(weights, random)
                result += pool.removeAt(pickedIndex).mediaId
            }
            return result
        }

        private fun weightOf(item: ShuffleItem): Double {
            val base = 1.0 / (1.0 + item.skipCount)
            return if (item.playedRecently) base * recencyDamp else base
        }

        /** Pick an index in proportion to [weights] (roulette-wheel selection). */
        private fun sampleIndex(weights: List<Double>, random: Random): Int {
            val total = weights.sum()
            if (total <= 0.0) return random.nextInt(weights.size)
            var threshold = random.nextDouble() * total
            return weights.indices.firstOrNull { index ->
                threshold -= weights[index]
                threshold <= 0.0
            } ?: weights.lastIndex
        }
    }

    companion object {
        /** Recently played tracks get a quarter of their normal weight. */
        const val RECENCY_DAMP = 0.25
    }
}

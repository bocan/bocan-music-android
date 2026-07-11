package io.cloudcauldron.bocan.playback.queue

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ShuffleStrategyTests {
    private fun items(count: Int) = (1..count).map { ShuffleItem(mediaId = "track:$it") }

    @Test
    fun `fisher yates is a permutation with no losses or duplicates`() {
        val input = items(50)
        val out = ShuffleStrategy.FisherYates.order(input, Random(1))
        assertEquals(input.map { it.mediaId }.sorted(), out.sorted())
        assertEquals(input.size, out.toSet().size)
    }

    @Test
    fun `fisher yates drops excluded items`() {
        val input = listOf(
            ShuffleItem("track:1"),
            ShuffleItem("track:2", excludedFromShuffle = true),
            ShuffleItem("track:3")
        )
        val out = ShuffleStrategy.FisherYates.order(input, Random(1))
        assertEquals(setOf("track:1", "track:3"), out.toSet())
    }

    @Test
    fun `fisher yates is roughly uniform across positions`() {
        val labels = listOf("a", "b", "c", "d")
        val input = labels.map { ShuffleItem(it) }
        val runs = 10_000
        // counts[element][position]
        val counts = labels.associateWith { IntArray(labels.size) }
        val random = Random(4242)
        repeat(runs) {
            val order = ShuffleStrategy.FisherYates.order(input, random)
            order.forEachIndexed { position, label -> counts.getValue(label)[position]++ }
        }
        val expected = runs / labels.size
        val lower = (expected * 0.85).toInt()
        val upper = (expected * 1.15).toInt()
        counts.forEach { (label, positions) ->
            positions.forEachIndexed { position, observed ->
                assertTrue(
                    observed in lower..upper,
                    "element $label at position $position had $observed, expected near $expected"
                )
            }
        }
    }

    @Test
    fun `smart shuffle sinks high skip tracks later on average`() {
        val lowSkip = listOf("track:1", "track:2", "track:3")
        val highSkip = "track:99"
        val input = lowSkip.map { ShuffleItem(it, skipCount = 0) } +
            ShuffleItem(highSkip, skipCount = 100)
        val runs = 3_000
        val random = Random(7)
        var highSkipPositionSum = 0.0
        val lowSkipPositionSum = mutableMapOf<String, Double>()
        repeat(runs) {
            val order = ShuffleStrategy.SmartShuffle().order(input, random)
            highSkipPositionSum += order.indexOf(highSkip)
            lowSkip.forEach { lowSkipPositionSum.merge(it, order.indexOf(it).toDouble(), Double::plus) }
        }
        val highAvg = highSkipPositionSum / runs
        val lowAvg = lowSkipPositionSum.values.sum() / (runs * lowSkip.size)
        assertTrue(highAvg > lowAvg, "high-skip avg position $highAvg should exceed low-skip avg $lowAvg")
    }

    @Test
    fun `smart shuffle still returns every eligible track exactly once`() {
        val input = (1..20).map { ShuffleItem("track:$it", skipCount = it.toLong()) }
        val out = ShuffleStrategy.SmartShuffle().order(input, Random(3))
        assertEquals(input.map { it.mediaId }.sorted(), out.sorted())
    }

    @Test
    fun `smart shuffle damps recently played tracks`() {
        val recent = "track:recent"
        val fresh = listOf("track:a", "track:b", "track:c")
        val input = fresh.map { ShuffleItem(it, playedRecently = false) } +
            ShuffleItem(recent, playedRecently = true)
        val runs = 3_000
        val random = Random(11)
        var recentPositionSum = 0.0
        repeat(runs) {
            val order = ShuffleStrategy.SmartShuffle().order(input, random)
            recentPositionSum += order.indexOf(recent)
        }
        // With four items, the mean position is 1.5; a damped track should sit later.
        assertTrue(recentPositionSum / runs > 1.5)
    }
}

package io.cloudcauldron.bocan.app.fixtures

/**
 * The library states the end-to-end suite starts from (phase 17).
 *
 * - [UnpairedDemo]: a fresh unpaired install; the app seeds the phase 14 demo itself on first Home.
 * - [PairedLibrary]: a paired (unreachable) Mac plus the full fixture library, so every paired-only control renders.
 * - [EmptyPaired]: a paired (unreachable) Mac and an empty library.
 * - [Unpaired]: no Mac, an empty library, and the demo's one automatic seed already spent, so the
 *   only library that can appear is the one a sync brings (slice 6's loopback fake Mac).
 */
enum class FixtureProfile { UnpairedDemo, PairedLibrary, EmptyPaired, Unpaired }

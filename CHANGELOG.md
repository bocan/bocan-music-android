# Changelog

## [0.2.0](https://github.com/bocan/bocan-music-android/compare/v0.1.0...v0.2.0) (2026-07-13)


### Added

* **app:** About shows the license line and both project repos ([f83c3dc](https://github.com/bocan/bocan-music-android/commit/f83c3dcadca4f50d2557224b4be88bed5144470c))
* **app:** accessibility audit fixes across every screen ([cbdee72](https://github.com/bocan/bocan-music-android/commit/cbdee72c5175e4f0cdb247e2c58baff78a9f4960))
* **app:** add a quick-actions row to Now Playing ([9b72978](https://github.com/bocan/bocan-music-android/commit/9b72978339ce54dffd13f29ac02c055630eaa017))
* **app:** add pairing UI and wire the sync graph ([3a8638d](https://github.com/bocan/bocan-music-android/commit/3a8638d9c54e48aff6511928a3966cf0b314ca01))
* **app:** add Shuffle All to the library, artist, and genre screens ([9efdb37](https://github.com/bocan/bocan-music-android/commit/9efdb379d3aefea8e589cea078b7b13869121315))
* **app:** add the equalizer screen and effect settings ([dd08ab0](https://github.com/bocan/bocan-music-android/commit/dd08ab0205c171baea425f0be3d33684aa53b2c7))
* **app:** add the home-screen widget, app shortcuts, and Auto manifest wiring ([f274507](https://github.com/bocan/bocan-music-android/commit/f2745076e154f1bb889122d4102bae407dafbf9b))
* **app:** add the podcasts tab, show detail, and podcast now playing ([24b33b8](https://github.com/bocan/bocan-music-android/commit/24b33b819d3fb00add937da44953c94e5da2cc6f))
* **app:** add the scrobble settings screen and wiring ([8bdca72](https://github.com/bocan/bocan-music-android/commit/8bdca724e174b83752d92e39e31a9bda2baf161c))
* **app:** add the sync status UI and wire the sync graph ([0cde74f](https://github.com/bocan/bocan-music-android/commit/0cde74f20b39d7566980d790b311298861113070))
* **app:** appearance settings with theme mode, dynamic color, and pure black ([09dd62c](https://github.com/bocan/bocan-music-android/commit/09dd62c4703c685d16b8c46d43ded1016e6be73c))
* **app:** build Now Playing, the queue sheet, lyrics, and the sleep timer ([ed9c867](https://github.com/bocan/bocan-music-android/commit/ed9c86736b52f2253ecb2a9f6b1718c6cc6812a2))
* **app:** build the adaptive launcher icon from the brand assets ([944f39d](https://github.com/bocan/bocan-music-android/commit/944f39d62037f88e7a5c21a251bcac11cfb2ca73))
* **app:** build the library UI, navigation shell, and mini player ([b90e309](https://github.com/bocan/bocan-music-android/commit/b90e3095bb3d467d41c54c312ff134256fe60819))
* **app:** expose the database and sync applier from AppGraph ([f2e87ce](https://github.com/bocan/bocan-music-android/commit/f2e87ce373d8757a68791b2a4be23c455dddc8f7))
* **app:** launch a themed empty Compose shell ([1714afa](https://github.com/bocan/bocan-music-android/commit/1714afa3a92c9fba19c930414b56f4b062f2f90d))
* **app:** map every user-reachable error to a human string ([05cd55b](https://github.com/bocan/bocan-music-android/commit/05cd55bb34b86728aa68dbaa1e366d1753a7fab8))
* **app:** Now Playing gestures and read-only song details sheet ([13f8682](https://github.com/bocan/bocan-music-android/commit/13f8682b651270a35b8569c04f6dc07f293b5a29))
* **app:** rainbow oscilloscope on Now Playing, replacing the docked mini player ([8c4a7c4](https://github.com/bocan/bocan-music-android/commit/8c4a7c457994ca3613399d7b80d728fec87a2557))
* **app:** reorder library tabs to Songs, Albums, Artists ([bcc9817](https://github.com/bocan/bocan-music-android/commit/bcc9817819678e67799964eddb6234e7ce983110))
* **app:** route launcher shortcuts and deep links, add resume-on-reconnect ([4213687](https://github.com/bocan/bocan-music-android/commit/42136873cc2c94f303319554d93279af029da3e9))
* **app:** settings hub with sections, about screen, and first-run onboarding ([fd15ae4](https://github.com/bocan/bocan-music-android/commit/fd15ae46e78bad81a6435f9485d39472b026c828))
* **app:** show song counts on artist rows and album cells ([42c31cb](https://github.com/bocan/bocan-music-android/commit/42c31cbf2eec4ca8e8488605de34ad17e00c14e3))
* **app:** sync settings surface with unpair and remove-all-media ([82fd74c](https://github.com/bocan/bocan-music-android/commit/82fd74c79afca89e4e5903dd830eff96027c5e3a))
* **observability:** add the AppLog facade over Timber ([492c5f2](https://github.com/bocan/bocan-music-android/commit/492c5f2a38c27ea95e7027d1b050c2413ef06082))
* **persistence:** add a downloaded-track-ids query for the shuffle shortcut ([cfb0e36](https://github.com/bocan/bocan-music-android/commit/cfb0e36f48fdc10f36af69d4495dd7110b831ca8))
* **persistence:** add podcast progress and unplayed-count reads ([8f72e57](https://github.com/bocan/bocan-music-android/commit/8f72e577850036edf8b5c9708f1b94bb3ddfd8df))
* **persistence:** add scrobble queue read queries ([26bcdfb](https://github.com/bocan/bocan-music-android/commit/26bcdfbb73babdd5dcb28945b462ac82d9ac252e))
* **persistence:** add SyncApplier, the one write path for manifests ([22b9a99](https://github.com/bocan/bocan-music-android/commit/22b9a998b302658ee93f95dc9a09deb94feb4238))
* **persistence:** add the browse DAO for the Auto media tree ([9940cd1](https://github.com/bocan/bocan-music-android/commit/9940cd14c87578161bfa78c63f9b862558487ff5))
* **persistence:** add the DAOs and the Room database ([6297d62](https://github.com/bocan/bocan-music-android/commit/6297d6246a57840d6d2e9578f4aafb2a439cb642))
* **persistence:** add the entities and column converters ([8ff5089](https://github.com/bocan/bocan-music-android/commit/8ff50895c18d9e0429b7b842a24c572873c96fb9))
* **persistence:** add the lyrics cache DAO ([d2eedcd](https://github.com/bocan/bocan-music-android/commit/d2eedcdaacd53d563d78e0926e12501988c85f7f))
* **persistence:** add the manifest DTOs and the shared golden fixture ([443a0c7](https://github.com/bocan/bocan-music-android/commit/443a0c7027608d7577999ab05be0314b6bb6c395))
* **persistence:** bulk download-state resets for remove-all-synced-media ([9390894](https://github.com/bocan/bocan-music-android/commit/9390894c3d0575f552264f0f7a09514b4fb39e93))
* **persistence:** wire Room 3, KSP, and kotlinx.serialization into the module ([1627abc](https://github.com/bocan/bocan-music-android/commit/1627abccf33d30b14f34e0bf3f9dd486f508a770))
* **playback:** add episode resume, chapters, and podcast media rules ([e9e21f2](https://github.com/bocan/bocan-music-android/commit/e9e21f29a582d6de31ec4c1d9e77a6fede802697))
* **playback:** add the Auto browse tree, session commands, and notification actions ([217eb27](https://github.com/bocan/bocan-music-android/commit/217eb270375606e7ca6af7b5dea1cc84b6af590d))
* **playback:** add the EQ, bass boost, limiter, and fade effects chain ([6717508](https://github.com/bocan/bocan-music-android/commit/6717508766fcd2862e5e947b69512a820b6827f5))
* **playback:** add the LRC parser, lyrics repository, sleep timer, and transport interface ([ccce98a](https://github.com/bocan/bocan-music-android/commit/ccce98a9ffa1b0f049a259f4d5dd7fa89d3b8bde))
* **playback:** GET_AUDIO_FORMAT session command for the live decoder pipeline ([78c1dc5](https://github.com/bocan/bocan-music-android/commit/78c1dc505357c0dcc5cb4a4c1897444d83f4bf9a))
* **playback:** implement the Media3 playback engine ([aa4ee2d](https://github.com/bocan/bocan-music-android/commit/aa4ee2d0e1144c19e23c345a69c3cf2a11754aa7))
* **playback:** show episode skip buttons in the notification and Auto layout ([a17ed61](https://github.com/bocan/bocan-music-android/commit/a17ed61c7698cdd53a92a1e78689fd264dd5e5d6))
* **playback:** tap the output waveform for the visualizer ([ee07098](https://github.com/bocan/bocan-music-android/commit/ee07098127301c72c29a9e4d27f21137d91c5c79))
* **scrobble:** implement the providers, offline queue, and service ([dc24ebd](https://github.com/bocan/bocan-music-android/commit/dc24ebd39a6ae73a45a98c127950a7d88b645126))
* **sync:** add the lyrics endpoint client call ([5f8144f](https://github.com/bocan/bocan-music-android/commit/5f8144f72e47cce27d8926f19c7c7f00f9725121))
* **sync:** add the paired chapters endpoint ([213b43c](https://github.com/bocan/bocan-music-android/commit/213b43cf36a7b648ecc777bd4fe54bcfcfb25526))
* **sync:** implement discovery, identity, trust, and pairing ([6ce24c0](https://github.com/bocan/bocan-music-android/commit/6ce24c0bc372d0e5699189a07713f45b36c1bf8b))
* **sync:** implement the one-way sync engine ([48970d8](https://github.com/bocan/bocan-music-android/commit/48970d87fb385623a88e1f0265dc14beb5659d1b))


### Fixed

* **app:** apply system bar insets to the onboarding flow ([dde95cc](https://github.com/bocan/bocan-music-android/commit/dde95cc660a742cef543da8600c18b875fd6005c))
* **app:** calm the oscilloscope, drive halfway back toward the flat trace ([9fe961a](https://github.com/bocan/bocan-music-android/commit/9fe961a7479b6285293e1f26eb8e2a7caa69e5dc))
* **app:** clearer, live pairing instructions with the real Mac path ([15887a5](https://github.com/bocan/bocan-music-android/commit/15887a57964dbf72d235b1980411e89a2a5e7e33))
* **app:** default wallpaper colors off ([8dd5423](https://github.com/bocan/bocan-music-android/commit/8dd54231c62cf8b5ba426e5741fe238befa4b802))
* **app:** dismiss Now Playing reliably on a fast downward flick ([17e5e0a](https://github.com/bocan/bocan-music-android/commit/17e5e0a83064230b60dd24856fab04d026e44910))
* **app:** drive the oscilloscope amplitude for a bolder trace ([25d1b9f](https://github.com/bocan/bocan-music-android/commit/25d1b9f2588a9f1975ac677f78aecec8a1760209))
* **app:** flip Now Playing swipe direction and make swipe-up reachable ([d9e0ad3](https://github.com/bocan/bocan-music-android/commit/d9e0ad3f8e7a45a5b06469bee8b95ef894b54d1f))
* **app:** hand off to the library and start syncing when pairing completes ([58f2bba](https://github.com/bocan/bocan-music-android/commit/58f2bba34fd840e8dc51b6714c72448446a34e3a))
* **app:** keep Room database constructors so the R8 build launches ([7acb6d9](https://github.com/bocan/bocan-music-android/commit/7acb6d9183cd51bc2c2cd204aec59d1a89e3903d))
* **app:** localization audit fixes: plurals, signs, and preset names ([7b4d6a4](https://github.com/bocan/bocan-music-android/commit/7b4d6a408723ff5737a5c2a7afa60b4a427b33d0))
* **app:** make Now Playing a full-screen overlay ([eef26fa](https://github.com/bocan/bocan-music-android/commit/eef26fa1d4961f07d564685b36996ca8f315b139))
* **app:** make the oscilloscope background truly transparent ([6d527c6](https://github.com/bocan/bocan-music-android/commit/6d527c65d857203088b695d0f1def8502f29af50))
* **app:** open the song details sheet fully expanded ([0dc5cea](https://github.com/bocan/bocan-music-android/commit/0dc5cea185b32ba353b6a9d8ad924a04293ed9fe))
* **app:** serve album art as a content URI so Android Auto shows it ([b017b72](https://github.com/bocan/bocan-music-android/commit/b017b7209ba774d591eb6e6f1a83cc736ccdefab))
* **app:** show live transfer progress in the sync counts line ([b09f140](https://github.com/bocan/bocan-music-android/commit/b09f140d5ffbfadfce63b5b5a7ae9a36fd995834))
* **app:** stop the doubled top inset on every screen ([edfd8a8](https://github.com/bocan/bocan-music-android/commit/edfd8a8f2ed558de9a8259b12c18b9eb472d3464))
* **app:** stop the songs list duration overlapping the alphabet rail ([cfbfcb5](https://github.com/bocan/bocan-music-android/commit/cfbfcb53fc219e5a919af54de426bc778d03f630))
* **app:** theming audit fixes for the widget, show notes, and data-driven color ([278d8af](https://github.com/bocan/bocan-music-android/commit/278d8aff1d3a9599c0675821e4ff599751573793))
* **persistence:** tolerate contract-optional manifest fields ([451e711](https://github.com/bocan/bocan-music-android/commit/451e711ea4095f06b802baeb029b030d495cb373))
* **playback:** peak-pick each waveform window so transients survive ([6c07ea1](https://github.com/bocan/bocan-music-android/commit/6c07ea123ad1cacde82ff7c7c639d68035d6656e))
* **playback:** show the podcast cover on episode now playing and mini player ([9837b81](https://github.com/bocan/bocan-music-android/commit/9837b812ca374a44877304d83127d463fe6127eb))
* **playback:** tolerate a failed session connection in QueueController ([71f7bf1](https://github.com/bocan/bocan-music-android/commit/71f7bf1e4bf987b1221eecfe488fa5a9d19b2854))
* **podcasts:** show artwork on continue-listening cards ([edf200c](https://github.com/bocan/bocan-music-android/commit/edf200cc39815072e942c0404c656af5b726f31d))
* **sync:** allow raw ECDSA signing so mutual-TLS pairing completes ([b61c294](https://github.com/bocan/bocan-music-android/commit/b61c294c83895faa3549c848006c5031691d92a2))
* **sync:** give a manual sync the same discovery window as the scheduled one ([fc0cc90](https://github.com/bocan/bocan-music-android/commit/fc0cc905ea948867a7b6d376dfdf20719e1cbd56))
* **sync:** hold wifi and wake locks for the duration of a sync ([50bfe84](https://github.com/bocan/bocan-music-android/commit/50bfe84c47cc00a6c0f003bbb0553e63000e2086))
* **sync:** let the pairing confirm wait out the Trust click ([d30dbd0](https://github.com/bocan/bocan-music-android/commit/d30dbd00b9c5f611b2777cbcffac47343ce035c8))
* **sync:** re-resolve discovered Macs so a pairing-mode flip is seen ([9358234](https://github.com/bocan/bocan-music-android/commit/9358234f45c0435fdd871eaa443e908124d01ffe))
* **sync:** recover a .part the server can no longer resume ([9f2e5d4](https://github.com/bocan/bocan-music-android/commit/9f2e5d49ecaa9a76d4ff071efc7bef2415fdf6d9))
* **sync:** retry pending downloads and re-fetch artwork missing from disk ([66437bd](https://github.com/bocan/bocan-music-android/commit/66437bdda22ff24b49617e88d71101ebae5eff93))

## Changelog

All notable changes to Bòcan Music for Android are recorded here. This file is
maintained by release-please from Conventional Commit messages; do not edit it by hand.

The versioning baseline is 0.1.0. The first merged release PR after release engineering
landed will be the first published release, with its version derived from the commits
since this baseline.

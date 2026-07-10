# Phase 08: EQ and Effects

> Depends on: phase-04-playback-engine.md, phase-06-now-playing-queue.md
> Read docs/design-spec/_standards.md first.
> Provides: the audio effects chain in `:core:playback` and the Equalizer screen in `:app`.

## Goal

The Mac app's core listening enhancements on Android: a 10-band graphic EQ with the same bands and presets, bass boost, ReplayGain mode selection with preamp, skip silence, and crossfade to the extent Android's player architecture honestly allows.

## Non-goals

- No binaural crossfeed or stereo expander in v1 (Mac-only for now; revisit if users ask).
- No per-track or per-album DSP assignments (Mac feature; phone applies one global chain).
- No visualizer taps.

## Outcome shape

```
core/playback/src/main/kotlin/io/cloudcauldron/bocan/playback/audio/
  EffectsChain.kt         // owns processor instances + settings application
  EqSettings.kt           // bands, presets, persistence model
  EqProcessor.kt          // 10-band peaking-filter AudioProcessor
  BassBoostProcessor.kt   // low-shelf filter
  Crossfader.kt           // see design note
  SkipSilence.kt          // thin wrapper over ExoPlayer's skipSilenceEnabled
app/src/main/kotlin/io/cloudcauldron/bocan/app/effects/
  EqualizerScreen.kt EqualizerViewModel.kt BandSlider.kt PresetPicker.kt
```

## Definitions and contracts

- **Bands** (match the Mac): 31.25, 62.5, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 Hz; gain range -12 to +12 dB in 0.5 dB steps.
- **Implementation choice: custom `AudioProcessor`s in the ExoPlayer audio sink**, not the platform `android.media.audiofx.Equalizer`. Rationale (record in code docs): the platform effect is device-dependent (5 bands on many devices, unreliable on some OEMs), while a processor in the sink is deterministic, works with the FFmpeg renderer output, and stacks cleanly with the phase 04 ReplayGain processor. Implement each band as a biquad peaking filter (RBJ audio EQ cookbook coefficients), cascaded; bass boost as a low-shelf biquad at 80 Hz, 0 to +9 dB.
- **Chain order** (fixed): decoder output -> EQ -> bass boost -> ReplayGain gain -> limiter guard -> sink. Limiter guard: a simple soft-knee peak limiter (lookahead-free tanh-style clamp) protecting against EQ-induced clipping; always on when any band is positive.
- **Presets**: the Mac's built-in preset names and curves (Flat, Rock, Pop, Jazz, Classical, Electronic, Hip-Hop, Vocal, Bass Boost, Treble Boost; copy exact curves from `bocan-music/Modules/AudioEngine/Sources/AudioEngine/DSP/` when implementing, or approximate with documented values if unreadable from here) plus user presets (name + 10 gains) stored in DataStore as JSON.
- **Crossfade design note (binding)**: ExoPlayer's single-player pipeline cannot crossfade two arbitrary tracks natively. V1 implements "fade on manual skip" only: a 300 ms fade-out via volume ramp before a user-initiated skip, plus configurable end-of-track fade-out/fade-in (0 to 12 s) implemented with volume ramps around transitions. True overlapping crossfade requires a second player instance and manual mixing; it is explicitly deferred, and the settings UI must label the feature "Fade between tracks" not "Crossfade" to stay honest. Gapless remains the default and fades default to off.
- **Skip silence**: expose ExoPlayer's `skipSilenceEnabled` as a toggle (great for podcasts); per-context defaults: off for music, user-choice for podcasts.

## Implementation plan

1. Biquad math in a small pure `Biquad` class (coefficients from the RBJ cookbook; double precision state, float I/O).
2. `EqProcessor` and `BassBoostProcessor` as allocation-free `AudioProcessor`s; settings swaps via `@Volatile` coefficient arrays recomputed off the audio thread.
3. Limiter guard processor.
4. `EffectsChain` wiring into the phase 04 `PlayerFactory` seam; settings from DataStore applied on start and on change.
5. `Crossfader`: volume-ramp scheduler listening to position (start fade `fadeSeconds` before item end) and to manual skip calls.
6. Equalizer screen: vertical band sliders with dB labels and hertz captions (TalkBack: "1 kilohertz, plus 3 decibels"), preset picker with save-as, enable/disable master switch, bass boost slider, ReplayGain section (Off/Track/Album + preamp -6 to +6 dB), skip silence toggle, fade duration slider. Entry points: Now Playing overflow and Settings.
7. A/B honesty: toggling the master switch applies within one buffer, enabling instant comparison.

## Context7 lookups

- use context7: Media3 AudioProcessor lifecycle onConfigure onFlush queueInput semantics
- use context7: DataStore preferences JSON serialization patterns

## Dependencies

None new.

## Test plan

### Biquad and processors (JVM, signal tests)
- Feed sine sweeps through `EqProcessor` at +6 dB on one band: measure ~+6 dB at center frequency, ~0 dB two octaves away (FFT or RMS windowed assertion, tolerances documented).
- Flat settings: output bit-similar to input within float epsilon.
- Coefficient swap mid-stream: no clicks (no discontinuity beyond a threshold across the swap boundary).
- Limiter: a +12 dB all-bands square wave does not exceed 1.0 sample magnitude.
- Bass boost raises 60 Hz, leaves 2 kHz alone.

### Crossfader (virtual time)
- Fade starts at `end - fadeSeconds`, reaches 0 by transition, next item ramps up.
- Manual skip triggers the short fade then skips.
- Disabled = volume untouched (gapless preserved).

### Settings
- Preset apply/save/delete round-trips; DataStore migration-safe defaults.

## Acceptance criteria

- [ ] EQ audibly and measurably shapes output for both platform-decoded and FFmpeg-decoded sources.
- [ ] Presets match the Mac's names; curves documented next to the code.
- [ ] No clipping with hot EQ settings (limiter proven by test).
- [ ] ReplayGain modes and preamp adjustable from the screen; interaction with EQ chain order fixed as specified.
- [ ] Fades honest: off by default, labelled "Fade between tracks", gapless intact when off.
- [ ] Skip silence toggle works on a spoken-word file.
- [ ] Master toggle A/Bs instantly.
- [ ] EQ screen fully accessible (slider values read as text).

## Gotchas

- **Never do coefficient math on the audio thread.** Recompute on settings change, publish arrays via volatile swap, apply per-buffer.
- **AudioProcessor flush semantics**: seeks call `onFlush`; biquad state must reset or you smear stale samples across seeks.
- **The platform `Equalizer` effect is a trap** on OEM devices; the custom chain exists precisely to avoid it. Do not "simplify" back to it.
- **Fades interact with ReplayGain and the sleep timer**: all three manipulate volume. The sleep timer owns `player.volume`; fades and ReplayGain live inside the processor chain (gain stage), so they compose instead of fighting. Keep it that way.

## Handoff

Phase 10 needs nothing from here. Phase 11's settings surface links to the Equalizer screen. The chain seams are final; later DSP additions (crossfeed, expander) slot in as new processors without re-architecture.

package io.cloudcauldron.bocan.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long until the timer fires: a fixed duration, or the end of the current track. */
sealed interface SleepDuration {
    data class Fixed(val durationMs: Long) : SleepDuration
    data object EndOfTrack : SleepDuration
}

/** The observable state of the sleep timer for the UI (a moon indicator and countdown). */
sealed interface SleepTimerState {
    data object Idle : SleepTimerState
    data class Counting(val remainingMs: Long) : SleepTimerState
    data object WaitingForTrackEnd : SleepTimerState
    data object Fading : SleepTimerState
}

/**
 * The minimal player surface the timer drives: read and set volume (0..1) and pause.
 * A boundary so the timer is tested with a fake and never touches a MediaController.
 */
interface PlayerVolume {
    suspend fun getVolume(): Float
    suspend fun setVolume(volume: Float)
    suspend fun pause()
}

/**
 * Fades the music out and pauses when the timer expires, rather than cutting abruptly.
 * A fixed timer counts down then ramps volume to zero over ten seconds; end-of-track
 * mode waits for the next item transition then fades fast. The original volume is
 * always restored in a finally, so the next play is never left silent, and cancelling
 * mid-fade restores immediately.
 */
class SleepTimer(private val volume: PlayerVolume, private val trackTransitions: Flow<Unit>, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val internalState = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
    val state: StateFlow<SleepTimerState> = internalState.asStateFlow()

    @Volatile
    private var remainingMs: Long = 0
    private var job: Job? = null

    /** Arm the timer, replacing any running one. */
    fun start(duration: SleepDuration) {
        job?.cancel()
        job = scope.launch {
            when (duration) {
                is SleepDuration.Fixed -> {
                    remainingMs = duration.durationMs
                    countdown()
                    fade(FIXED_FADE_MS)
                }
                SleepDuration.EndOfTrack -> {
                    internalState.value = SleepTimerState.WaitingForTrackEnd
                    trackTransitions.first()
                    fade(END_OF_TRACK_FADE_MS)
                }
            }
        }
    }

    /** Add [minutes] to a running fixed countdown; a no-op unless counting. */
    fun extend(minutes: Int) {
        if (internalState.value is SleepTimerState.Counting) {
            remainingMs += minutes * MS_PER_MINUTE
        }
    }

    /** Cancel the timer; a fade in progress restores the volume via its finally. */
    fun cancel() {
        job?.cancel()
        job = null
        internalState.value = SleepTimerState.Idle
    }

    private suspend fun countdown() {
        while (remainingMs > 0) {
            internalState.value = SleepTimerState.Counting(remainingMs)
            delay(TICK_MS)
            remainingMs -= TICK_MS
        }
    }

    private suspend fun fade(fadeMs: Long) {
        internalState.value = SleepTimerState.Fading
        val original = volume.getVolume()
        try {
            val steps = (fadeMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                volume.setVolume(original * (1f - step.toFloat() / steps))
                delay(FADE_STEP_MS)
            }
            volume.pause()
        } finally {
            // Restore even when cancelled mid-fade, or the next play starts silent.
            withContext(NonCancellable) {
                volume.setVolume(original)
                internalState.value = SleepTimerState.Idle
            }
        }
    }

    fun dispose() = scope.cancel()

    private companion object {
        const val TICK_MS = 1_000L
        const val FIXED_FADE_MS = 10_000L
        const val END_OF_TRACK_FADE_MS = 2_000L
        const val FADE_STEP_MS = 100L
        const val MS_PER_MINUTE = 60_000L
    }
}

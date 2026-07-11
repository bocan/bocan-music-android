package io.cloudcauldron.bocan.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the widget in step with playback while the process lives: it maps [PlayerUiState]
 * to a [WidgetState], persists it (so a cold widget still renders), and asks Glance to
 * recompose. Updates are debounced by [distinctUntilChanged] so an unchanged snapshot does
 * not thrash the launcher.
 */
@UnstableApi
class WidgetUpdater(
    private val context: Context,
    private val playerState: Flow<PlayerUiState>,
    private val store: WidgetStateStore,
    private val scope: CoroutineScope
) {
    private val log = AppLog.forCategory(LogCategory.Ui)

    fun start() {
        scope.launch {
            playerState.map(WidgetState::fromPlayer).distinctUntilChanged().collect { state ->
                // Updating the widget is best effort: a headless or test environment (no
                // widget host) must never crash the app, so failures are logged, not thrown.
                runCatching {
                    store.write(state)
                    BocanWidget().updateAll(context)
                }.onFailure { log.warning("widget.update.failed", mapOf("error" to it.toString())) }
            }
        }
    }
}

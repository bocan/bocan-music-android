package io.cloudcauldron.bocan.app.player

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.ArtworkImage
import kotlin.math.roundToInt

private val ROW_HEIGHT = 64.dp

/**
 * The queue sheet: an Up Next header with count and remaining time, the queue with the
 * current item highlighted, swipe-to-remove, long-press drag-to-reorder (committed on
 * drop so it does not fight the live queue), and a confirmed Clear. These are session
 * queue edits, which are allowed; only the library is read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: QueueUiState, onMove: (Int, Int) -> Unit, onRemove: (Int) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.queue_up_next,
                    state.upNextCount,
                    DateUtils.formatElapsedTime(state.upNextRemainingMs / 1000)
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.queue_clear)) }
        }
        QueueList(state, onMove, onRemove)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.queue_clear_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text(stringResource(R.string.queue_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueList(state: QueueUiState, onMove: (Int, Int) -> Unit, onRemove: (Int) -> Unit) {
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    LazyColumn {
        itemsIndexed(state.items, key = { _, item -> item.mediaId + item.index }) { index, item ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onRemove(index)
                        true
                    } else {
                        false
                    }
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = { Box(Modifier.fillMaxWidth().height(ROW_HEIGHT)) }
            ) {
                QueueRow(
                    item = item,
                    dragOffset = if (draggingIndex == index) dragOffset else 0f,
                    onDragStart = {
                        draggingIndex = index
                        dragOffset = 0f
                    },
                    onDrag = { dragOffset += it },
                    onDragEnd = {
                        val target = (index + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, state.items.lastIndex)
                        if (target != index) onMove(index, target)
                        draggingIndex = -1
                        dragOffset = 0f
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItemUi, dragOffset: Float, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val background = if (item.isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .graphicsLayer { translationY = dragOffset }
            .background(background)
            .padding(horizontal = 16.dp)
    ) {
        ArtworkImage(artworkHash = null, modifier = Modifier.size(44.dp))
        Text(
            text = item.title,
            style = if (item.isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (item.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.queue_reorder_handle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .pointerInput(item.mediaId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        }
                    )
                }
        )
    }
}

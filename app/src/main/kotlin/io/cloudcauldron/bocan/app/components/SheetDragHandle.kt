package io.cloudcauldron.bocan.app.components

import android.content.res.Configuration
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import io.cloudcauldron.bocan.app.e2e.E2ETags

/**
 * Material's bottom sheet drag handle with its end-to-end tag. The handle is tappable (it
 * expands, collapses, or dismisses the sheet), so it needs a tag like any other control;
 * every `ModalBottomSheet` passes this as its `dragHandle`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetDragHandle() {
    BottomSheetDefaults.DragHandle(modifier = Modifier.testTag(E2ETags.Common.SHEET_HANDLE))
}

/**
 * The state for a sheet holding a list (the queue, chapters, show notes): the half-height stop
 * in portrait, where half the screen shows several rows and the player stays in view behind it,
 * but fully open in landscape, where half the height showed a single row. Short sheets of a few
 * actions skip the half-height stop in both orientations instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberListSheetState(): SheetState =
    rememberModalBottomSheetState(skipPartiallyExpanded = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE)

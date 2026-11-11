package io.cloudcauldron.bocan.app.components

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

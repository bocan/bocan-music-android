package io.cloudcauldron.bocan.app.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.sync.localNetworkAccessGranted

/**
 * A recovery notice shown only while the Android 17 local network permission is
 * denied, on the screens that stop working without it (pairing, sync status).
 * Discovery and sync silently see an empty network in that state, so the notice
 * is the one place the user learns why and can fix it. The button re-requests
 * the permission; when the system suppresses the dialog after repeated denials,
 * it opens the app's system settings page instead.
 */
@Composable
fun LocalNetworkPermissionNotice(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
    val context = LocalContext.current
    var missing by remember { mutableStateOf(localNetworkPermissionMissing(context)) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        missing = localNetworkPermissionMissing(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        missing = !granted
        // A denial with no rationale expected means the system no longer shows the
        // dialog at all; the app settings page is the only remaining path to a grant.
        if (!granted && context.activity()?.let { rationaleExpected(it) } == false) {
            settingsLauncher.launch(appDetailsIntent(context))
        }
    }
    if (!missing) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.local_network_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) {
            Text(stringResource(R.string.local_network_allow))
        }
    }
}

private fun localNetworkPermissionMissing(context: Context): Boolean = !localNetworkAccessGranted(context)

private fun rationaleExpected(activity: Activity): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_LOCAL_NETWORK)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

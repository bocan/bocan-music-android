package io.cloudcauldron.bocan.app.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * True once local network traffic is allowed: always below Android 17, and after
 * the ACCESS_LOCAL_NETWORK runtime grant on 17+. Discovery must not start before
 * this holds, or the system answers the unpermissioned mDNS pass with its
 * per-connection device picker.
 */
fun localNetworkAccessGranted(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
    PackageManager.PERMISSION_GRANTED

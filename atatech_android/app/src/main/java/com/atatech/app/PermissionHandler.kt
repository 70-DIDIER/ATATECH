package com.atatech.app

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberPermissionState(
    permission: String,
    onResult: (Boolean) -> Unit
): PermissionState {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    val isGranted = ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED

    return remember(isGranted) {
        PermissionState(isGranted) { launcher.launch(permission) }
    }
}

data class PermissionState(val isGranted: Boolean, val request: () -> Unit)

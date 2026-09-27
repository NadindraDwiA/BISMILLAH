package com.example.bismillah.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object PermissionUtils {
    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun overlayPermissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
}

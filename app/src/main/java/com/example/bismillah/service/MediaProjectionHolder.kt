package com.example.bismillah.service

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object MediaProjectionHolder {
    var mediaProjection: MediaProjection? = null
        private set
    var resultCode: Int = Activity.RESULT_CANCELED
        private set
    var resultData: android.content.Intent? = null
        private set

    fun set(resultCode: Int, data: android.content.Intent?, context: Context) {
        this.resultCode = resultCode
        this.resultData = data?.let { android.content.Intent(it) }
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = if (resultCode == Activity.RESULT_OK && data != null) {
            try { mpm.getMediaProjection(resultCode, data) } catch (_: Exception) { null }
        } else null
    }

    fun clear() {
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        resultData = null
    }
}

package com.example.bismillah.data

import android.content.Context
import com.example.bismillah.util.ModelDownloader
import java.io.File

/** Single source of truth for on-device model state (PaddleOCR pack only). */
class ModelRepository(private val appContext: Context) {

    data class PaddleStatus(
        val ready: Boolean,
        val files: List<Pair<String, Boolean>>,
        val dictReady: Boolean
    )

    fun paddleStatus(): PaddleStatus {
        val files = ModelDownloader.paddleFiles(appContext)
        val names = listOf("det.onnx", "rec.onnx", "cls.onnx")
        return PaddleStatus(
            ready = ModelDownloader.hasPaddle(appContext),
            files = files.mapIndexed { i, f -> names[i] to (f.exists() && f.length() > 0) },
            dictReady = ModelDownloader.dictFile(appContext).let { it.exists() && it.length() > 0 }
        )
    }

    fun paddleFiles(): List<File> = ModelDownloader.paddleFiles(appContext)

    suspend fun ensurePaddle(): Boolean =
        ModelDownloader.ensurePaddleFromAssets(appContext)
}

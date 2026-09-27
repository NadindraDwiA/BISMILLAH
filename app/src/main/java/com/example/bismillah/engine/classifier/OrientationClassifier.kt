package com.example.bismillah.engine.classifier

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.bismillah.App
import com.example.bismillah.data.model.BoundingBox
import com.example.bismillah.engine.ocr.ImagePreprocessor
import com.example.bismillah.util.BitmapUtils
import com.example.bismillah.util.ModelDownloader
import java.util.Collections

class OrientationClassifier(private val context: Context? = null) {
    private val preprocessor = ImagePreprocessor()

    fun isVertical(box: BoundingBox, clsUpsideDown: Boolean = false): Boolean {
        return box.ratioHW > 1.35f
    }

    /** Opsional: jalankan ch_ppocr_mobile_cls.onnx pada crop untuk 0/180. Return true jika 180. */
    fun classifyUpsideDown(crop: Bitmap): Boolean {
        try {
            val ctx = context ?: return false
            val env: OrtEnvironment =
                (ctx.applicationContext as? App)?.ortEnv ?: return false
            val modelFile = ModelDownloader.paddleFiles(ctx).getOrNull(2)?.takeIf { it.exists() }
                ?: return false
            env.createSession(modelFile.absolutePath, OrtSession.SessionOptions()).use { session ->
                val resized = Bitmap.createScaledBitmap(crop, 48, 48, true)
                return try {
                    val (buf, shape) = preprocessor.toChwFloat(resized)
                    OnnxTensor.createTensor(env, buf, shape).use { input ->
                        session.run(Collections.singletonMap(session.inputNames.first(), input)).use { res ->
                            parseCls(res[0].value)
                        }
                    }
                } finally {
                    BitmapUtils.safeRecycle(resized)
                }
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun parseCls(raw: Any?): Boolean {
        return try {
            val arr = when (raw) {
                is Array<*> -> (raw[0] as? FloatArray)
                is FloatArray -> raw
                else -> null
            } ?: return false
            // cls 2 kelas: [0]=0deg, [1]=180deg
            arr.size >= 2 && arr[1] > arr[0]
        } catch (_: Exception) { false }
    }
}

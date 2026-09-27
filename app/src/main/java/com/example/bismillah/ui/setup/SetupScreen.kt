package com.example.bismillah.ui.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bismillah.R
import com.example.bismillah.data.SetupPrefs
import com.example.bismillah.service.MediaProjectionHolder
import com.example.bismillah.util.ModelDownloader
import com.example.bismillah.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private enum class SetupStep { CHECK, OVERLAY, NOTIF, CAPTURE, PADDLE, APIKEY, DONE }

@Composable
fun SetupScreen(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val app = appCtx as android.app.Application
    val vm: SetupViewModel = viewModel(factory = SetupViewModel.Factory(app))
    val activity = ctx as? Activity

    var step by remember { mutableStateOf(SetupStep.CHECK) }
    var status by remember { mutableStateOf("") }
    var autoTried by remember { mutableStateOf(setOf<SetupStep>()) }

    fun markDone() = vm.markDone(appCtx) { onFinished() }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionUtils.canDrawOverlays(ctx)) step = SetupStep.NOTIF
        else status = "Izin overlay ditolak — ketuk Lanjut untuk coba lagi."
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        step = SetupStep.CAPTURE
    }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            MediaProjectionHolder.set(res.resultCode, res.data, appCtx)
            step = SetupStep.PADDLE
            status = "Menyalin model OCR kecil..."
        } else {
            status = "Izin capture ditolak — ketuk Lanjut untuk coba lagi."
        }
    }

    fun requestOverlay() {
        try {
            overlayLauncher.launch(PermissionUtils.overlayPermissionIntent(ctx))
        } catch (e: Exception) {
            status = "Gagal membuka izin overlay: ${e.message}"
        }
    }

    fun requestCapture() {
        try {
            val mpm = appCtx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            captureLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (e: Exception) {
            status = "Gagal meminta capture: ${e.message}"
        }
    }

    // Mesin langkah otomatis: maju sendiri, hanya berhenti bila butuh aksi user / gagal.
    LaunchedEffect(step) {
        when (step) {
            SetupStep.CHECK -> {
                val overlayOk = PermissionUtils.canDrawOverlays(ctx)
                val paddleOk = withContext(Dispatchers.IO) { ModelDownloader.hasPaddle(appCtx) }
                val backendOk = vm.hasTranslatorBackend(appCtx)
                val done = try {
                    SetupPrefs.isDone(appCtx).first()
                } catch (_: Exception) { false }
                if (overlayOk && paddleOk && (backendOk || done)) {
                    markDone()
                } else if (!overlayOk) {
                    step = SetupStep.OVERLAY
                } else if (!paddleOk) {
                    step = SetupStep.PADDLE
                } else {
                    step = SetupStep.NOTIF
                }
            }
            SetupStep.OVERLAY -> {
                status = "Butuh izin bubble agar bisa melayang di atas aplikasi lain."
                if (SetupStep.OVERLAY !in autoTried) {
                    autoTried = autoTried + SetupStep.OVERLAY
                    requestOverlay()
                }
            }
            SetupStep.NOTIF -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        step = SetupStep.CAPTURE
                    } else if (SetupStep.NOTIF !in autoTried) {
                        autoTried = autoTried + SetupStep.NOTIF
                        status = "Meminta izin notifikasi untuk service stabil..."
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        step = SetupStep.CAPTURE
                    }
                } else {
                    step = SetupStep.CAPTURE
                }
            }
            SetupStep.CAPTURE -> {
                status = "Butuh izin tangkap layar sekali untuk MediaProjection."
                if (MediaProjectionHolder.mediaProjection != null) {
                    step = SetupStep.PADDLE
                } else if (SetupStep.CAPTURE !in autoTried) {
                    autoTried = autoTried + SetupStep.CAPTURE
                    requestCapture()
                }
            }
            SetupStep.PADDLE -> {
                status = "Menyalin model OCR kecil dari aplikasi..."
                val ok = withContext(Dispatchers.IO) { vm.ensurePaddle(appCtx) }
                if (ok) {
                    step = SetupStep.APIKEY
                } else {
                    status = "Model OCR (det/rec) belum ada di assets/paddle/. " +
                        "Letakkan .onnx di sana lalu ketuk Coba lagi."
                }
            }
            SetupStep.APIKEY -> {
                if (vm.hasTranslatorBackend(appCtx)) {
                    step = SetupStep.DONE
                    markDone()
                } else {
                    status = "Translator cloud belum terkonfigurasi di build ini. " +
                        "Isi gemini.api.key lalu rebuild, atau lewati dulu."
                }
            }
            SetupStep.DONE -> markDone()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
        Text(status, style = MaterialTheme.typography.bodyMedium)

        when (step) {
            SetupStep.OVERLAY -> {
                Button(onClick = { requestOverlay() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Izinkan Bubble (otomatis terbuka)")
                }
            }
            SetupStep.CAPTURE -> {
                Button(
                    onClick = { requestCapture() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = activity != null
                ) {
                    Text("Izinkan Tangkap Layar")
                }
            }
            SetupStep.PADDLE -> {
                OutlinedButton(
                    onClick = { step = SetupStep.CHECK },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Coba lagi") }
            }
            SetupStep.APIKEY -> {
                OutlinedButton(onClick = { markDone() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_skip))
                }
            }
            else -> {}
        }

        if (step != SetupStep.APIKEY && step != SetupStep.CHECK) {
            OutlinedButton(
                onClick = {
                    step = when (step) {
                        SetupStep.OVERLAY -> SetupStep.NOTIF
                        SetupStep.NOTIF -> SetupStep.CAPTURE
                        SetupStep.CAPTURE -> SetupStep.PADDLE
                        SetupStep.PADDLE -> SetupStep.APIKEY
                        else -> step
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Lanjut") }
        }
    }
}

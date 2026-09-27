package com.example.bismillah.feature.screentranslator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bismillah.R
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.example.bismillah.data.preference.AppPreferences
import com.example.bismillah.service.MediaProjectionHolder
import com.example.bismillah.service.ScreenTranslatorService
import com.example.bismillah.util.PermissionUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorSetupScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as android.app.Application
    val vm: TranslatorViewModel = viewModel(factory = TranslatorViewModel.Factory(app))
    val status by vm.status.collectAsState()
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()

    var recLang by remember { mutableStateOf(AppPreferences.REC_AUTO) }
    var tgtLang by remember { mutableStateOf(AppPreferences.TGT_ID) }
    LaunchedEffect(Unit) {
        try {
            val s = AppPreferences.observe(ctx.applicationContext).first()
            recLang = s.recognitionLang
            tgtLang = s.targetLang
        } catch (_: Exception) {}
    }

    val hasOverlay = PermissionUtils.canDrawOverlays(ctx)

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            MediaProjectionHolder.set(res.resultCode, res.data, ctx.applicationContext)
            ctx.startForegroundService(Intent(ctx, ScreenTranslatorService::class.java))
            vm.start()
        } else {
            Toast.makeText(ctx, "Izin capture ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (PermissionUtils.canDrawOverlays(ctx)) {
            requestProjection(activity, ctx, projectionLauncher)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Live Card
        LiveStatusHeaderCard(status = status, hasOverlay = hasOverlay)

        // Step 1: Overlay Permission
        SetupStepCard(
            stepNumber = "1",
            title = "Izin Display Over Apps (Overlay)",
            description = "Dibutuhkan agar bubble melayang bisa tampil di atas game atau aplikasi lain.",
            isComplete = hasOverlay,
            icon = Icons.Rounded.Layers
        ) {
            if (!hasOverlay) {
                Button(
                    onClick = { overlayLauncher.launch(PermissionUtils.overlayPermissionIntent(ctx)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Berikan Izin Overlay", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Izin overlay telah diberikan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Step 2: Controls Hub Card (tombol nonaktif saat bubble sudah aktif — anti bubble ganda)
        val isActive = status.contains("aktif", ignoreCase = true) || status.contains("berjalan", ignoreCase = true)
        SetupStepCard(
            stepNumber = "2",
            title = "Kontrol Bubble Screen Translator",
            description = "Aktifkan bubble melayang, lakukan pengujian instant, atau hentikan layanan.",
            isComplete = isActive,
            icon = Icons.Rounded.Translate
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (!PermissionUtils.canDrawOverlays(ctx)) {
                            overlayLauncher.launch(PermissionUtils.overlayPermissionIntent(ctx))
                        } else {
                            requestProjection(activity, ctx, projectionLauncher)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Text("Aktifkan Bubble Screen Translator", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.runOnce() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tes Capture Teks", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedButton(
                        onClick = { vm.stop() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Matikan", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // V4 Common Settings: recognition + target language
        val recOptions = listOf(
            AppPreferences.REC_AUTO to "Auto-Detect (ML Kit)",
            AppPreferences.REC_JA to "Japanese",
            AppPreferences.REC_ZH_HANS to "Chinese (Simplified)",
            AppPreferences.REC_ZH_HANT to "Chinese (Traditional)",
            AppPreferences.REC_KO to "Korean"
        )
        val tgtOptions = listOf(
            AppPreferences.TGT_ID to "Indonesian (ind_Latn)",
            AppPreferences.TGT_EN to "English (eng_Latn)"
        )
        SetupStepCard(
            stepNumber = "3",
            title = "Bahasa Sumber & Target",
            description = "Auto memakai ML Kit (<5ms). Target Indonesia default; Inggris bila model mendukung.",
            isComplete = true,
            icon = Icons.Rounded.Translate
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LangDropdown(
                    label = "Recognition Language",
                    options = recOptions,
                    selected = recLang,
                    onSelect = {
                        recLang = it
                        scope.launch { AppPreferences.setRecognitionLang(ctx.applicationContext, it) }
                    }
                )
                LangDropdown(
                    label = "Target Language",
                    options = tgtOptions,
                    selected = tgtLang,
                    onSelect = {
                        tgtLang = it
                        scope.launch { AppPreferences.setTargetLang(ctx.applicationContext, it) }
                    }
                )
            }
        }

        // How to Use Guide Card
        GuideCard()

        // Open Settings Button
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Settings, contentDescription = null)
                Text("Buka Pengaturan & Izin Detail")
            }
        }
    }
}

@Composable
private fun LiveStatusHeaderCard(status: String, hasOverlay: Boolean) {
    val isActive = status.contains("aktif", ignoreCase = true) || status.contains("berjalan", ignoreCase = true)
    val statusColor = when {
        isActive -> MaterialTheme.colorScheme.tertiary
        !hasOverlay -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = statusColor,
                shape = CircleShape,
                modifier = Modifier.size(16.dp)
            ) {}

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Status Layanan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isComplete: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isComplete) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Langkah $stepNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LangDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: selected
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun GuideCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Cara Pakai Saat Bermain Game",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            val steps = listOf(
                "1. Jalankan bubble translator via tombol di atas.",
                "2. Buka game atau app dengan bahasa asing (CJK/English).",
                "3. Ketuk bubble melayang untuk mengambil screenshot layar.",
                "4. Teks akan terdeteksi & terjemahan muncul di atas layar."
            )

            steps.forEach { stepText ->
                Text(
                    text = stepText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun requestProjection(
    activity: Activity?,
    ctx: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    try {
        val mpm = ctx.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (activity != null) {
            launcher.launch(mpm.createScreenCaptureIntent())
        } else {
            Toast.makeText(ctx, "Buka dari Activity untuk minta izin capture", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(ctx, "Gagal minta izin capture: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

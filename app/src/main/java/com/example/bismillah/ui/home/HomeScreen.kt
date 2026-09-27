package com.example.bismillah.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bismillah.R
import com.example.bismillah.core.navigation.FeatureItem
import com.example.bismillah.core.navigation.FeatureStatus

@Composable
fun HomeScreen(
    features: List<FeatureItem>,
    onClick: (FeatureItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner Span
        item(span = { GridItemSpan(2) }) {
            HeroBannerCard(features = features, onNavigate = onClick)
        }

        // Section Title
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_modules),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${features.size} ${stringResource(R.string.home_features)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Features Grid
        items(features) { feature ->
            FeatureCardItem(feature = feature, onClick = { onClick(feature) })
        }

        // Privacy & On-Device Security Info Card
        item(span = { GridItemSpan(2) }) {
            PrivacyFooterCard()
        }
    }
}

@Composable
private fun HeroBannerCard(
    features: List<FeatureItem>,
    onNavigate: (FeatureItem) -> Unit
) {
    val translatorFeature = features.find { it.id == "screen_translator" }
    val isReady = translatorFeature?.status == FeatureStatus.READY

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "BISMILLAH AI TRANSLATOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Terjemahkan Layar Game & App Tanpa Internet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "PaddleOCR CJK + Gemini Flash Translation langsung di layar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(Modifier.height(4.dp))

                if (translatorFeature != null) {
                    Button(
                        onClick = { onNavigate(translatorFeature) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isReady) "Mulai Screen Translator" else "Konfigurasi Translator",
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FeatureStyle(val icon: ImageVector, val iconTint: Color)

@Composable
private fun FeatureCardItem(
    feature: FeatureItem,
    onClick: () -> Unit
) {
    val isEnabled = feature.status != FeatureStatus.SOON

    val style = when (feature.id) {
        "screen_translator" -> FeatureStyle(Icons.Rounded.Translate, MaterialTheme.colorScheme.primary)
        "model_manager" -> FeatureStyle(Icons.Rounded.Memory, MaterialTheme.colorScheme.secondary)
        "history" -> FeatureStyle(Icons.Rounded.History, MaterialTheme.colorScheme.tertiary)
        "settings" -> FeatureStyle(Icons.Rounded.Settings, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> FeatureStyle(Icons.Rounded.Translate, MaterialTheme.colorScheme.primary)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = style.iconTint.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = feature.title,
                            tint = style.iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                StatusBadge(status = feature.status)
            }

            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = feature.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

private data class StatusStyleInfo(
    val bgColor: Color,
    val contentColor: Color,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun StatusBadge(status: FeatureStatus) {
    val info = when (status) {
        FeatureStatus.READY -> StatusStyleInfo(
            bgColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.tertiary,
            label = stringResource(R.string.status_ready),
            icon = Icons.Rounded.CheckCircle
        )
        FeatureStatus.NEED_PERMISSION -> StatusStyleInfo(
            bgColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.error,
            label = stringResource(R.string.status_permission),
            icon = Icons.Rounded.Warning
        )
        FeatureStatus.NEED_MODEL -> StatusStyleInfo(
            bgColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.secondary,
            label = stringResource(R.string.status_model),
            icon = Icons.Rounded.DownloadForOffline
        )
        FeatureStatus.SOON -> StatusStyleInfo(
            bgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            label = stringResource(R.string.status_soon),
            icon = Icons.Rounded.HourglassTop
        )
    }

    Surface(
        color = info.bgColor,
        shape = CircleShape,
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = info.contentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelSmall,
                color = info.contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PrivacyFooterCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = "Privasi",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "100% On-Device & Privat",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Proses OCR dan Translasi berjalan penuh di HP Anda tanpa mengunggah data ke server luar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

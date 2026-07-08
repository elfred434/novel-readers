package com.novelreader.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onExtensionsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = OnSurfaceDark) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === CARTE THÈME ===
            SettingsCard {
                Column {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Palette, null, tint = Primary, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Thème", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(AppTheme.entries.getOrElse(uiState.themeType) { AppTheme.DARK }.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AppTheme.entries.forEachIndexed { index, theme ->
                            val selected = uiState.themeType == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Brush.horizontalGradient(listOf(Primary, PrimaryVariant)) else SurfaceDarkElevated)
                                    .clickable { viewModel.setThemeType(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    theme.displayName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else OnSurfaceDarkSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // === FILE D'ATTENTE ===
            SettingsCard {
                Row(Modifier.fillMaxWidth().clickable { onDownloadsClick() }, Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Download, null, tint = Primary, modifier = Modifier.size(22.dp))
                        Column {
                            Text("File d'attente", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            val parts = mutableListOf<String>()
                            if (uiState.activeDownloads > 0) parts.add("${uiState.activeDownloads} actif(s)")
                            if (uiState.failedDownloads > 0) parts.add("${uiState.failedDownloads} échec(s)")
                            Text(parts.joinToString(" · ").ifEmpty { "Aucun téléchargement" }, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDarkTertiary)
                }
            }

            // === EXTENSIONS ===
            SettingsCard {
                Row(Modifier.fillMaxWidth().clickable { onExtensionsClick() }, Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Extension, null, tint = Secondary, modifier = Modifier.size(22.dp))
                        Column {
                            Text("Extensions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("${uiState.extensionCount} source(s)", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDarkTertiary)
                }
            }

            // === VÉRIFICATION ===
            SettingsCard {
                Column {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Schedule, null, tint = Primary, modifier = Modifier.size(22.dp))
                            Column { Text("Vérification auto", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Text("Toutes les ${uiState.updateIntervalHours}h", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(value = uiState.updateIntervalHours.toFloat(), onValueChange = { viewModel.setUpdateInterval(it.toInt()) }, valueRange = 4f..48f, steps = 10,
                        colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceDarkElevated))
                }
            }

            // === NOTIFICATIONS ===
            SettingsCard {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Notifications, null, tint = Tertiary, modifier = Modifier.size(22.dp))
                        Column { Text("Notifications", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Text("Alerte nouveaux chapitres", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary) }
                    }
                    Switch(checked = uiState.notificationsEnabled, onCheckedChange = { viewModel.toggleNotifications() }, colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.3f)))
                }
            }

            // === TÉLÉCHARGEMENTS ===
            SettingsCard {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Download, null, tint = Primary, modifier = Modifier.size(22.dp))
                        Text("Téléchargements", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Simultané", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary); Text("${uiState.downloadMaxConcurrent}", style = MaterialTheme.typography.bodySmall) }
                    Slider(value = uiState.downloadMaxConcurrent.toFloat(), onValueChange = { viewModel.setDownloadMaxConcurrent(it.toInt()) }, valueRange = 1f..5f, steps = 3,
                        colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary, inactiveTrackColor = SurfaceDarkElevated))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Wifi, null, modifier = Modifier.size(16.dp), tint = OnSurfaceDarkSecondary)
                            Text("Wi-Fi uniquement", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary)
                        }
                        Switch(checked = uiState.downloadOnWifiOnly, onCheckedChange = { viewModel.setDownloadOnWifiOnly(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.3f)))
                    }
                }
            }

            // === CACHE ===
            SettingsCard {
                Column {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Storage, null, tint = OnSurfaceDarkSecondary, modifier = Modifier.size(22.dp))
                            Column { Text("Cache", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Text("${uiState.cachedChapterCount} chapitre(s)", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::clearCache, enabled = uiState.cachedChapterCount > 0 && !uiState.clearingCache,
                        colors = ButtonDefaults.buttonColors(containerColor = Error),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.padding(end = 8.dp))
                        Text(if (uiState.clearingCache) "Suppression…" else "Vider le cache")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("NovelReader v1.0.0", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkTertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

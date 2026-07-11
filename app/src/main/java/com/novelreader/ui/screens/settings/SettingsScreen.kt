package com.novelreader.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit, onExtensionsClick: () -> Unit = {}, onDownloadsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.setSafUri(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Theme
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Thème", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(AppTheme.entries.getOrElse(uiState.themeType) { AppTheme.DARK }.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        AppTheme.entries.forEachIndexed { i, t ->
                            val sel = uiState.themeType == i
                            Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.setThemeType(i) }, contentAlignment = Alignment.Center) {
                                Text(t.displayName, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant))
                            }
                        }
                    }
                }
            }

            // Queue
            SectionCard {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { onDownloadsClick() }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("File d'attente", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            val parts = mutableListOf<String>()
                            if (uiState.activeDownloads > 0) parts.add("${uiState.activeDownloads} actif(s)")
                            if (uiState.failedDownloads > 0) parts.add("${uiState.failedDownloads} échec(s)")
                            Text(parts.joinToString(" · ").ifEmpty { "Aucun téléchargement" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uiState.failedDownloads > 0) {
                        TextButton(
                            onClick = viewModel::retryFailedDownloads,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.Replay, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Réessayer les ${uiState.failedDownloads} échec(s)")
                        }
                    }
                }
            }

            // Extensions
            SectionCard {
                Row(Modifier.fillMaxWidth().clickable { onExtensionsClick() }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) { Text("Extensions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Text("${uiState.extensionCount} source(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Update interval
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) { Text("Vérification auto", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Text("Toutes les ${uiState.updateIntervalHours}h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Slider(value = uiState.updateIntervalHours.toFloat(), onValueChange = { viewModel.setUpdateInterval(it.toInt()) }, valueRange = 4f..48f, steps = 10, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant))
                }
            }

            // Notifications
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) { Text("Notifications", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Text("Alerte nouveaux chapitres", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = uiState.notificationsEnabled, onCheckedChange = { viewModel.toggleNotifications() }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                }
            }

            // === STOCKAGE SAF — UNIQUE EMPLACEMENT ===
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Dossier de stockage", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            if (uiState.hasStorageLocation) {
                                Text("${uiState.downloadCountOnDisk} chapitre(s) · ${uiState.storageUsed}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Aucun dossier choisi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Button(
                        onClick = { safLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.hasStorageLocation) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.padding(end = 6.dp))
                        Text(if (uiState.hasStorageLocation) "Changer de dossier" else "Choisir un dossier")
                    }
                    if (uiState.hasStorageLocation) {
                        Button(
                            onClick = { viewModel.refreshStorageInfo() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                        ) { Icon(Icons.Default.Refresh, null, modifier = Modifier.padding(end = 6.dp)); Text("Actualiser") }
                    }
                }
            }

            // Download settings
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        Text("Téléchargements", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Simultané", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${uiState.downloadMaxConcurrent}") }
                    Slider(value = uiState.downloadMaxConcurrent.toFloat(), onValueChange = { viewModel.setDownloadMaxConcurrent(it.toInt()) }, valueRange = 1f..5f, steps = 3, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Wi-Fi uniquement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = uiState.downloadOnWifiOnly, onCheckedChange = { viewModel.setDownloadOnWifiOnly(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                    }
                }
            }

            // High speed mode
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Wifi, null, tint = if (uiState.isOnWifi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mode Haute Vitesse", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                            val status = when {
                                !uiState.isOnline -> "Hors-ligne"
                                uiState.isOnWifi && uiState.wifiHighDataMode -> "Wi-Fi · Turbo activé"
                                uiState.isOnWifi && !uiState.wifiHighDataMode -> "Wi-Fi · Mode économique"
                                else -> "Données mobiles"
                            }
                            Text(status, style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.isOnWifi && uiState.wifiHighDataMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.wifiHighDataMode, onCheckedChange = { viewModel.toggleWifiHighDataMode() },
                            enabled = uiState.isOnline,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(
                            if (uiState.isOnWifi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                        Text(
                            if (uiState.wifiHighDataMode && uiState.isOnWifi) "Jusqu'à 5 téléchargements simultanés · préchargement des chapitres"
                            else if (uiState.isOnWifi) "${uiState.downloadMaxConcurrent} téléchargement(s) simultané(s)"
                            else "Actif uniquement sur Wi-Fi",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Cache
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) { Text("Cache", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)); Text("${uiState.cachedChapterCount} chapitre(s) en cache", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Button(onClick = viewModel::clearCache, enabled = uiState.cachedChapterCount > 0 && !uiState.clearingCache,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.padding(end = 6.dp)); Text(if (uiState.clearingCache) "Suppression…" else "Vider le cache")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("NovelReader v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Box(Modifier.padding(16.dp)) { content() } }
}

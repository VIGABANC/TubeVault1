package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.DownloadedVideo
import com.example.ui.TubeVaultViewModel
import com.example.ui.theme.TubeAccent
import com.example.ui.theme.TubeAccentDim
import com.example.ui.theme.TubeBorder
import com.example.ui.theme.TubeOledDark
import com.example.ui.theme.TubePrimary
import com.example.ui.theme.TubeSurfaceVariant
import com.example.ui.theme.TubeTextLight
import com.example.ui.theme.TubeTextMuted

@Composable
fun SettingsScreen(
    videos: List<DownloadedVideo>,
    viewModel: TubeVaultViewModel? = null,
    modifier: Modifier = Modifier
) {
    val totalBytes = videos.sumOf { it.fileSizeBytes }
    val totalMb = totalBytes / (1024f * 1024f)

    val browserSettings = viewModel?.browserSettings?.collectAsState()?.value
    val downloadSettings = viewModel?.downloadSettings?.collectAsState()?.value
    val aiSettings = viewModel?.aiSettings?.collectAsState()?.value

    val isKeyConfigured = BuildConfig.DOWNLOAD_API_KEY.isNotBlank() &&
            !BuildConfig.DOWNLOAD_API_KEY.startsWith("YOUR_") &&
            !BuildConfig.DOWNLOAD_API_KEY.contains("PLACEHOLDER", ignoreCase = true)

    val isHostConfigured = BuildConfig.DOWNLOAD_API_HOST.isNotBlank() &&
            !BuildConfig.DOWNLOAD_API_HOST.startsWith("YOUR_") &&
            !BuildConfig.DOWNLOAD_API_HOST.contains("PLACEHOLDER", ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Réglages",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configuration, moteur Turbo & apparence TubeVault",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Appearance / Theme Card (Phase 4 Premium Theme Switcher)
        if (viewModel != null && browserSettings != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Thème de l'application",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sélectionnez le mode d'affichage préféré pour l'interface :",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("System", "Light", "Dark", "OLED").forEach { themeMode ->
                            FilterChip(
                                selected = browserSettings.theme == themeMode,
                                onClick = { viewModel.setTheme(themeMode) },
                                label = { Text(themeMode) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("theme_chip_$themeMode")
                            )
                        }
                    }
                }
            }
        }

        // Turbo Download & Media Grabber Card (Phase 2.6)
        if (viewModel != null && downloadSettings != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Turbo Download & Smart Media Grabber",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Turbo Download Toggle
                    BrowserSettingSwitchRow(
                        title = "Téléchargement multi-segments Turbo",
                        description = "Découpe les fichiers en segments parallèles via HTTP Range pour maximiser le débit (style ADM / 1DM)",
                        checked = downloadSettings.turboDownloadEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateDownloadSettings { it.copy(turboPartsMode = if (enabled) "Auto" else "1") }
                        },
                        testTag = "switch_turbo_download"
                    )

                    if (downloadSettings.turboDownloadEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Nombre de segments parallèles :",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 4, 8).forEach { segs ->
                                FilterChip(
                                    selected = downloadSettings.segmentsCount == segs,
                                    onClick = { viewModel.updateDownloadSettings { it.copy(turboPartsMode = "$segs") } },
                                    label = { Text("$segs flux") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    // Quick Download Toggle
                    BrowserSettingSwitchRow(
                        title = "Téléchargement rapide en un clic",
                        description = "Télécharge automatiquement dans la meilleure qualité sans ouvrir le sélecteur de format",
                        checked = downloadSettings.quickDownloadEnabled,
                        onCheckedChange = { viewModel.updateDownloadSettings { it.copy(quickDownloadEnabled = it.quickDownloadEnabled.not()) } },
                        testTag = "switch_quick_download"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    // Auto Detect in Browser Toggle
                    BrowserSettingSwitchRow(
                        title = "Détection intelligente automatique",
                        description = "Analyse automatiquement le DOM HTML5 et les flux réseau des pages visitées",
                        checked = downloadSettings.browserAutoDetect,
                        onCheckedChange = { viewModel.updateDownloadSettings { it.copy(browserAutoDetect = it.browserAutoDetect.not()) } },
                        testTag = "switch_browser_autodetect"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    BrowserSettingSwitchRow(
                        title = "Télécharger via Wi-Fi uniquement",
                        description = "Met en pause les téléchargements si vous êtes sur réseau mobile",
                        checked = downloadSettings.wifiOnly,
                        onCheckedChange = { viewModel.updateDownloadSettings { it.copy(wifiOnly = it.wifiOnly.not()) } },
                        testTag = "switch_wifi_only"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    BrowserSettingSwitchRow(
                        title = "Reprise automatique",
                        description = "Tente de relancer automatiquement un téléchargement échoué",
                        checked = downloadSettings.autoRetry,
                        onCheckedChange = { viewModel.updateDownloadSettings { it.copy(autoRetry = it.autoRetry.not()) } },
                        testTag = "switch_auto_retry"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    // Public Download Folder display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dossier de stockage public",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Stockage interne / Download / TubeVault",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Les vidéos normales y sont enregistrées et visibles dans la Galerie, VLC et les gestionnaires de fichiers. Le Coffre-fort (Vault) reste chiffré et privé.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Browser & WebView Settings Card (Phase 2.5)
        if (viewModel != null && browserSettings != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Navigateur intégré (SnapTube)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Moteur de recherche par défaut
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Moteur de recherche par défaut",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Utilisé lors de la saisie de texte dans la barre d'adresse",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Google", "YouTube").forEach { engine ->
                                FilterChip(
                                    selected = browserSettings.searchEngine.equals(engine, ignoreCase = true),
                                    onClick = { viewModel.setSearchEngine(engine) },
                                    label = { Text(if (engine == "Google") "Google (Défaut)" else "YouTube") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    // Switch: Bloquer les popups
                    BrowserSettingSwitchRow(
                        title = "Bloquer les popups et fenêtres intempestives",
                        description = "Interdit l'ouverture non sollicitée de fenêtres ou popups automatiques",
                        checked = browserSettings.blockPopups,
                        onCheckedChange = { viewModel.setBrowserBlockPopups(it) },
                        testTag = "switch_block_popups"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    // Switch: Bloquer les redirections pub
                    BrowserSettingSwitchRow(
                        title = "Bloquer les redirections publicitaires",
                        description = "Empêche les redirections forcées vers des régies publicitaires externes",
                        checked = browserSettings.blockAdRedirects,
                        onCheckedChange = { viewModel.setBrowserBlockAdRedirects(it) },
                        testTag = "switch_block_ad_redirects"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    // Switch: JavaScript
                    BrowserSettingSwitchRow(
                        title = "Activer JavaScript",
                        description = "Requis pour YouTube, TikTok et Instagram. Désactivable pour une navigation restreinte.",
                        checked = browserSettings.javascriptEnabled,
                        onCheckedChange = { viewModel.setBrowserJavascriptEnabled(it) },
                        testTag = "switch_javascript_enabled"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // API Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Statut de l'API de téléchargement",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // API Key line
                ApiStatusRow(
                    label = "DOWNLOAD_API_KEY",
                    isConfigured = isKeyConfigured,
                    displayVal = if (isKeyConfigured) "••••••••" + BuildConfig.DOWNLOAD_API_KEY.takeLast(4) else "Non configurée (Placeholder)"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // API Host line
                ApiStatusRow(
                    label = "DOWNLOAD_API_HOST",
                    isConfigured = isHostConfigured,
                    displayVal = if (isHostConfigured) BuildConfig.DOWNLOAD_API_HOST else "Non configuré"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pour mettre à jour ces valeurs, utilisez le panneau Secrets d'AI Studio. Elles sont automatiquement injectées dans le build sans exposer vos clés.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        if (viewModel != null && aiSettings != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enrichissement & Intelligence Artificielle",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    BrowserSettingSwitchRow(
                        title = "Activer les fonctionnalités IA",
                        description = "Génère des résumés, tags de recherche et chapitres automatiques",
                        checked = aiSettings.aiEnabled,
                        onCheckedChange = { viewModel.setAiEnabled(it) },
                        testTag = "switch_ai_master"
                    )

                    androidx.compose.animation.AnimatedVisibility(visible = aiSettings.aiEnabled) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )

                            BrowserSettingSwitchRow(
                                title = "Wi-Fi uniquement pour l'IA",
                                description = "Empêche l'utilisation de vos données mobiles pour les requêtes Gemini",
                                checked = aiSettings.wifiOnlyForAi,
                                onCheckedChange = { viewModel.setWifiOnlyForAi(it) },
                                testTag = "switch_ai_wifi_only"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )

                            BrowserSettingSwitchRow(
                                title = "Générer les tags après téléchargement",
                                description = "Analyse et indexe automatiquement chaque nouveau média",
                                checked = aiSettings.autoTagAfterDownload,
                                onCheckedChange = { viewModel.setAutoTagAfterDownload(it) },
                                testTag = "switch_ai_auto_tag"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )

                            BrowserSettingSwitchRow(
                                title = "Générer le résumé après téléchargement",
                                description = "Produit une synthèse descriptive dès la fin du flux réseau",
                                checked = aiSettings.autoSummaryAfterDownload,
                                onCheckedChange = { viewModel.setAutoSummaryAfterDownload(it) },
                                testTag = "switch_ai_auto_summary"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )

                            BrowserSettingSwitchRow(
                                title = "Autoriser le cloud pour le Vault",
                                description = "Autorise le traitement cloud des contenus chiffrés (désactivé par défaut)",
                                checked = aiSettings.allowCloudAiForPrivateContent,
                                onCheckedChange = { viewModel.setAllowCloudAiForPrivateContent(it) },
                                testTag = "switch_ai_allow_private"
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Consentement de traitement cloud", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (aiSettings.cloudAiDisclosureAccepted) "Accepté" else "Non accepté",
                                        color = if (aiSettings.cloudAiDisclosureAccepted) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                                        fontSize = 11.sp
                                    )
                                }
                                if (aiSettings.cloudAiDisclosureAccepted) {
                                    TextButton(onClick = { viewModel.setCloudAiDisclosureAccepted(false) }) {
                                        Text("Réinitialiser", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Usage Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stockage local (Scoped Storage)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Vidéos enregistrées :", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text("${videos.size}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Espace utilisé :", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(String.format("%.2f Mo", totalMb), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Information
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "À propos de TubeVault",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Version : 2.6 (Smart Media Grabber & Turbo Download)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Moteur : Extraction distante + Détection multi-niveaux (A/B/C)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Téléchargement : Multi-segments Turbo avec reprise sur coupure réseau",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Avertissement Légal :",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Download content you own or are authorized to save. 100% local privacy is not guaranteed when cloud AI features are enabled. Guaranteed availability of third-party extractor providers is not provided.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun BrowserSettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ApiStatusRow(label: String, isConfigured: Boolean, displayVal: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(displayVal, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Surface(
            color = if (isConfigured) MaterialTheme.colorScheme.primaryContainer else Color(0xFF332014),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConfigured) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isConfigured) "Actif" else "En attente",
                    color = if (isConfigured) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


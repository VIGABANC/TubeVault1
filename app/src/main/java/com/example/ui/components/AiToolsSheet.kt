package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadedVideo
import com.example.ui.AiToolState
import com.example.ui.TubeVaultViewModel
import com.example.ui.theme.TubeAccent
import com.example.ui.theme.TubeBorder
import com.example.ui.theme.TubeOledDark
import com.example.ui.theme.TubePrimary
import com.example.ui.theme.TubeSurfaceVariant
import com.example.ui.theme.TubeTextLight
import com.example.ui.theme.TubeTextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsSheet(
    video: DownloadedVideo,
    viewModel: TubeVaultViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.aiSettings.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TubePrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.testTag("ai_tools_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = TubeAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Outils d'enrichissement IA",
                        style = MaterialTheme.typography.titleLarge,
                        color = TubeTextLight,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = TubeTextMuted
                    )
                }
            }

            Divider(color = TubeBorder, modifier = Modifier.padding(vertical = 12.dp))

            // AI Master Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activer les fonctionnalités IA",
                        color = TubeTextLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Générez des résumés, tags, chapitres et plus.",
                        color = TubeTextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.aiEnabled,
                    onCheckedChange = { viewModel.setAiEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TubeAccent,
                        checkedTrackColor = TubeAccent.copy(alpha = 0.4f),
                        uncheckedThumbColor = TubeTextMuted,
                        uncheckedTrackColor = TubeSurfaceVariant
                    ),
                    modifier = Modifier.testTag("ai_master_switch")
                )
            }

            AnimatedVisibility(visible = settings.aiEnabled) {
                Column {
                    // Cloud disclosure check
                    if (!settings.cloudAiDisclosureAccepted) {
                        CloudAiDisclosureCard(
                            onAccept = { viewModel.setCloudAiDisclosureAccepted(true) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Network and Cost parameters
                    Text(
                        text = "CONTRÔLE RÉSEAU & CONFIDENTIALITÉ",
                        color = TubeAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )

                    // Wifi only switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, null, tint = TubeTextMuted, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Wi-Fi uniquement pour l'IA", color = TubeTextLight, fontSize = 14.sp)
                                Text("Évite d'utiliser vos données mobiles", color = TubeTextMuted, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = settings.wifiOnlyForAi,
                            onCheckedChange = { viewModel.setWifiOnlyForAi(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TubeAccent)
                        )
                    }

                    // Cloud AI for Vault private content
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = TubeTextMuted, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Autoriser le cloud pour le Vault", color = TubeTextLight, fontSize = 14.sp)
                                Text("Désactivé par défaut pour la vie privée", color = TubeTextMuted, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = settings.allowCloudAiForPrivateContent,
                            onCheckedChange = { viewModel.setAllowCloudAiForPrivateContent(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TubeAccent)
                        )
                    }

                    // Auto process parameters
                    Text(
                        text = "TRAITEMENT POST-TÉLÉCHARGEMENT",
                        color = TubeAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    )

                    // Auto Tag Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Générer automatiquement des tags", color = TubeTextLight, fontSize = 14.sp)
                            Text("Exécute après chaque téléchargement", color = TubeTextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = settings.autoTagAfterDownload,
                            onCheckedChange = { viewModel.setAutoTagAfterDownload(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TubeAccent)
                        )
                    }

                    // Auto Summary Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Générer automatiquement un résumé", color = TubeTextLight, fontSize = 14.sp)
                            Text("Utilise la longueur préférée", color = TubeTextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = settings.autoSummaryAfterDownload,
                            onCheckedChange = { viewModel.setAutoSummaryAfterDownload(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TubeAccent)
                        )
                    }

                    // Preferred Summary Length
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Longueur du résumé", color = TubeTextLight, fontSize = 14.sp)
                            Text("Format de génération par défaut", color = TubeTextMuted, fontSize = 11.sp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isShort = settings.preferredSummaryLength == "short"
                            Button(
                                onClick = { viewModel.setPreferredSummaryLength("short") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isShort) TubeAccent else TubeSurfaceVariant,
                                    contentColor = if (isShort) TubeOledDark else TubeTextLight
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Court", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.setPreferredSummaryLength("detailed") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isShort) TubeAccent else TubeSurfaceVariant,
                                    contentColor = if (!isShort) TubeOledDark else TubeTextLight
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Détaillé", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(color = TubeBorder, modifier = Modifier.padding(vertical = 12.dp))

                    // Engine info
                    val aiEngine = remember { viewModel.getAiEngine() }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TubeSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (aiEngine.providerSource == com.example.data.ai.AiProviderSource.LOCAL_HEURISTIC) Icons.Default.NetworkCheck else Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = TubeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Moteur actif : ${aiEngine.modelIdentifier}",
                                    color = TubeTextLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (aiEngine.providerSource == com.example.data.ai.AiProviderSource.LOCAL_HEURISTIC) 
                                        "Mode heuristique local sur l'appareil (hors ligne et sans clé cloud)" 
                                    else "Appels cloud directs vers l'API Gemini",
                                    color = TubeTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cache clearing row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.clearAiCache()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Vider le cache d'intelligence artificielle", color = Color(0xFFDC2626), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Force la régénération de toutes les requêtes", color = TubeTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudAiDisclosureCard(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B16)), // Warm brown-dark alert tint
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAB308)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Divulgation de traitement cloud",
                    color = Color(0xFFEAB308),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pour générer de riches résumés, tags ou structures de chapitres, le contexte textuel du média est envoyé de manière sécurisée aux serveurs de traitement Google Gemini. Vos fichiers vidéo locaux NE SONT JAMAIS envoyés. Vous pouvez refuser ou désactiver ce service à tout moment.",
                color = TubeTextLight,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onAccept,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEAB308))
                ) {
                    Text("J'accepte, activer les outils cloud", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.detector.MediaCandidate
import com.example.data.model.Platform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDrawerSheet(
    candidates: List<MediaCandidate>,
    onDismiss: () -> Unit,
    onDownloadSingle: (MediaCandidate) -> Unit,
    onDownloadBatch: (List<MediaCandidate>) -> Unit,
    onScanPage: () -> Unit,
    onClearCandidates: () -> Unit,
    sheetState: SheetState
) {
    var selectedFilter by remember { mutableStateOf("all") } // "all", "video", "audio"

    val selectedCandidateIds = remember { mutableStateMapOf<String, Boolean>() }
    // Initialize all as selected by default for easy batch downloading
    remember(candidates) {
        candidates.forEach { if (!selectedCandidateIds.containsKey(it.id)) selectedCandidateIds[it.id] = true }
    }

    val filteredCandidates = remember(candidates, selectedFilter) {
        when (selectedFilter) {
            "video" -> candidates.filter { it.type == "video" }
            "audio" -> candidates.filter { it.type == "audio" }
            else -> candidates
        }
    }

    val selectedCount = filteredCandidates.count { selectedCandidateIds[it.id] == true }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            // Title Header with Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Médias détectés",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "${candidates.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onScanPage, modifier = Modifier.testTag("scan_page_drawer_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Analyser la page", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onClearCandidates) {
                        Icon(Icons.Default.Delete, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter chips & batch action toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedFilter == "all",
                        onClick = { selectedFilter = "all" },
                        label = { Text("Tous (${candidates.size})") }
                    )
                    val videoCount = candidates.count { it.type == "video" }
                    if (videoCount > 0) {
                        FilterChip(
                            selected = selectedFilter == "video",
                            onClick = { selectedFilter = "video" },
                            label = { Text("Vidéos ($videoCount)") }
                        )
                    }
                    val audioCount = candidates.count { it.type == "audio" }
                    if (audioCount > 0) {
                        FilterChip(
                            selected = selectedFilter == "audio",
                            onClick = { selectedFilter = "audio" },
                            label = { Text("Audio ($audioCount)") }
                        )
                    }
                }

                // Invert / Toggle all button
                Text(
                    text = if (selectedCount == filteredCandidates.size) "Désélectionner" else "Tout",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val newSelectState = selectedCount != filteredCandidates.size
                            filteredCandidates.forEach { selectedCandidateIds[it.id] = newSelectState }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Candidates list
            if (filteredCandidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aucun média détecté pour ce filtre",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = onScanPage) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analyser la page")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCandidates, key = { it.id }) { candidate ->
                        val isChecked = selectedCandidateIds[candidate.id] ?: false

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedCandidateIds[candidate.id] = !isChecked },
                            color = if (isChecked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = if (isChecked) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { selectedCandidateIds[candidate.id] = it },
                                    modifier = Modifier.size(28.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Thumbnail or Icon
                                if (!candidate.thumbnail.isNullOrBlank()) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                            .data(candidate.thumbnail)
                                            .size(150, 100) // approx for 72x48 dp
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Miniature",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 64.dp, height = 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (candidate.type == "audio") Icons.Default.Audiotrack else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title ?: "Média détecté (${candidate.extension.uppercase()})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (candidate.platform != Platform.OTHER) {
                                            PlatformBadge(platform = candidate.platform, compact = true)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        // Available formats or source tags
                                        if (candidate.availableFormats.isNotEmpty()) {
                                            Text(
                                                text = candidate.availableFormats.joinToString(" • ") { it.quality },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = candidate.extension.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Individual download button
                                IconButton(
                                    onClick = { onDownloadSingle(candidate) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Télécharger",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDownloadBatch(filteredCandidates)
                    },
                    enabled = filteredCandidates.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tout télécharger (${filteredCandidates.size})")
                }

                Button(
                    onClick = {
                        val selectedList = filteredCandidates.filter { selectedCandidateIds[it.id] == true }
                        onDownloadBatch(selectedList)
                    },
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_selected_batch_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sélection ($selectedCount)")
                }
            }
        }
    }
}

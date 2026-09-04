package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.DownloadState
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadedVideo
import com.example.data.model.Platform
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import com.example.ui.TubeVaultViewModel
import com.example.ui.components.PlatformBadge
import com.example.ui.theme.TubeAccent
import com.example.ui.theme.TubeAccentDim
import com.example.ui.theme.TubeBorder
import com.example.ui.theme.TubeOledDark
import com.example.ui.theme.TubePrimary
import com.example.ui.theme.TubeSurfaceVariant
import com.example.ui.theme.TubeTextLight
import com.example.ui.theme.TubeTextMuted

@Composable
fun HomeScreen(
    viewModel: TubeVaultViewModel,
    downloadState: DownloadState,
    onNavigateToLibrary: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var urlInput by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val detectedPlatform = remember(urlInput) {
        if (urlInput.isNotBlank()) Platform.detect(urlInput) else null
    }

    val duplicateWarning by viewModel.duplicateWarningVideo.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val activeTasksCount = downloadTasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val downloadSettings by viewModel.downloadSettings.collectAsState()

    // Shared incoming URL from Android Share Sheet
    val sharedIncomingUrl by viewModel.sharedIncomingUrl.collectAsState()
    LaunchedEffect(sharedIncomingUrl) {
        sharedIncomingUrl?.let { shared ->
            urlInput = shared
            viewModel.clearSharedIncomingUrl()
            viewModel.fetchMediaInfo(shared)
        }
    }

    // Discreet clipboard video URL detection
    var ignoredClipboardUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var detectedClipboardUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(downloadSettings.detectClipboardLinks) {
        if (downloadSettings.detectClipboardLinks) {
            val clipText = clipboardManager.getText()?.text?.trim()
            if (!clipText.isNullOrBlank() && clipText != ignoredClipboardUrl && clipText != urlInput) {
                val detected = com.example.util.VideoUrlDetector.detectVideo(clipText)
                if (detected != null) {
                    detectedClipboardUrl = clipText
                }
            }
        }
    }

    // Duplicate detection alert dialog
    if (duplicateWarning != null) {
        val dup = duplicateWarning!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateWarning() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vidéo déjà présente", fontWeight = FontWeight.Bold, color = TubeTextLight)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Cette vidéo est déjà enregistrée dans votre bibliothèque locale :",
                        color = TubeTextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "« ${dup.title} »",
                        color = TubeTextLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Qualité : ${dup.resolution}",
                        color = TubeAccent,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Voulez-vous la retélécharger quand même ?",
                        color = TubeTextMuted,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.enqueueCurrentDownload(forceIgnoreDuplicate = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark)
                ) {
                    Text("Retélécharger")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.dismissDuplicateWarning()
                        onNavigateToLibrary()
                    }) {
                        Text("Voir en bibliothèque", color = TubeAccent)
                    }
                    TextButton(onClick = { viewModel.dismissDuplicateWarning() }) {
                        Text("Annuler", color = TubeTextMuted)
                    }
                }
            },
            containerColor = TubePrimary,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Hero Branding
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "TubeVault",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "YouTube • TikTok • Instagram • Twitter/X",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Multi-Plateformes",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active downloads banner if queue is running
        if (activeTasksCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDownloads() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$activeTasksCount téléchargement${if (activeTasksCount > 1) "s" else ""} en cours",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gérer dans la file d'attente",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Ouvrir",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Discreet clipboard video URL prompt banner
        if (detectedClipboardUrl != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lien vidéo détecté",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = detectedClipboardUrl ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = {
                            ignoredClipboardUrl = detectedClipboardUrl
                            detectedClipboardUrl = null
                        }
                    ) {
                        Text("Ignorer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val url = detectedClipboardUrl ?: ""
                            urlInput = url
                            detectedClipboardUrl = null
                            viewModel.fetchMediaInfo(url)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Télécharger", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // URL Input Card (Premium Design styled as Quick Capture)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Quick Capture",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Paste a link. We'll handle the rest.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Live platform preview badge
                    if (detectedPlatform != null && detectedPlatform != Platform.OTHER) {
                        PlatformBadge(platform = detectedPlatform, compact = true)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_url_input"),
                    placeholder = {
                        Text("Paste YouTube URL here...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (urlInput.isNotBlank()) {
                                viewModel.fetchMediaInfo(urlInput)
                            }
                        }
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                            if (urlInput.isNotBlank()) {
                                IconButton(
                                    onClick = { urlInput = "" },
                                    modifier = Modifier.testTag("btn_clear_url")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        urlInput = clip.trim()
                                    }
                                },
                                modifier = Modifier.testTag("btn_paste_url"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("PASTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tip: YouTube, Shorts, Playlists & more",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action button: RESOLVE & DOWNLOAD
                val isFetching = downloadState is DownloadState.FetchingMetadata
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.fetchMediaInfo(urlInput)
                    },
                    enabled = urlInput.isNotBlank() && !isFetching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_fetch_metadata"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Analyzing link...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESOLVE & DOWNLOAD",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Dynamic horizontal workflow stepper (Phase 4 Premium UI)
        val activeStep = when (downloadState) {
            is DownloadState.Idle -> if (urlInput.isBlank()) 1 else 2
            is DownloadState.FetchingMetadata -> 2
            is DownloadState.MetadataLoaded -> 3
            is DownloadState.Downloading -> 4
            is DownloadState.Success -> 4
            is DownloadState.Error -> 2
        }
        Spacer(modifier = Modifier.height(14.dp))
        TvWorkflowStepper(activeStep = activeStep)

        // Error message banner
        AnimatedVisibility(
            visible = downloadState is DownloadState.Error,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (downloadState is DownloadState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Échec de récupération",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = downloadState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.resetDownloadState() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Réessayer",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Result Card: Metadata Ready, Downloading, or Success
        when (downloadState) {
            is DownloadState.MetadataLoaded -> {
                Spacer(modifier = Modifier.height(20.dp))
                MediaInfoCard(
                    metadata = downloadState.metadata,
                    selectedFormat = downloadState.selectedFormat,
                    onSelectFormat = { viewModel.selectFormat(it) },
                    onStartDownload = {
                        viewModel.enqueueCurrentDownload()
                    },
                    isDownloading = false
                )
            }
            is DownloadState.Downloading -> {
                Spacer(modifier = Modifier.height(20.dp))
                VideoDownloadingCard(
                    metadata = downloadState.metadata,
                    selectedFormat = downloadState.selectedFormat,
                    progress = downloadState.progress,
                    bytesDownloaded = downloadState.bytesDownloaded,
                    totalBytes = downloadState.totalBytes
                )
            }
            is DownloadState.Success -> {
                Spacer(modifier = Modifier.height(20.dp))
                VideoSuccessCard(
                    video = downloadState.video,
                    onPlay = { viewModel.openPlayer(downloadState.video) },
                    onGoToLibrary = onNavigateToLibrary,
                    onNewDownload = {
                        urlInput = ""
                        viewModel.resetDownloadState()
                    }
                )
            }
            else -> {}
        }

        // Browse Web contextual card
        if (downloadState is DownloadState.Idle) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBrowser() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Browse & Search Web",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Don't have a URL? Search or browse videos in our integrated browser.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Platform Support Information
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Plateformes prises en charge",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlatformBadge(Platform.YOUTUBE, compact = true)
                    PlatformBadge(Platform.TIKTOK, compact = true)
                    PlatformBadge(Platform.INSTAGRAM, compact = true)
                    PlatformBadge(Platform.TWITTER, compact = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Les téléchargements sont exécutés en arrière-plan avec notification de progression continue et détection automatique des doublons.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}


@Composable
fun MediaInfoCard(
    metadata: MediaInfo,
    selectedFormat: MediaFormat,
    onSelectFormat: (MediaFormat) -> Unit,
    onStartDownload: () -> Unit,
    isDownloading: Boolean
) {
    var formatDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_metadata_card"),
        colors = CardDefaults.cardColors(containerColor = TubePrimary),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TubeBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Thumbnail with platform badge & duration tag overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TubeSurfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(metadata.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = metadata.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Top left: Platform badge
                Box(modifier = Modifier.padding(8.dp).align(Alignment.TopStart)) {
                    PlatformBadge(platform = metadata.platform)
                }

                // Bottom right: Duration badge
                if (metadata.durationText.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = metadata.durationText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Video Title
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleMedium,
                color = TubeTextLight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!metadata.author.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metadata.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TubeTextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Resolution dropdown selector
            Text(
                text = "Qualité / Format disponible",
                style = MaterialTheme.typography.labelLarge,
                color = TubeTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { formatDropdownExpanded = true }
                        .testTag("format_selector_dropdown"),
                    color = TubeSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TubeBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = selectedFormat.quality,
                                color = TubeAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (!selectedFormat.approximateSize.isNullOrBlank()) {
                                Text(
                                    text = selectedFormat.approximateSize,
                                    color = TubeTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Ouvrir les résolutions",
                            tint = TubeAccent
                        )
                    }
                }

                DropdownMenu(
                    expanded = formatDropdownExpanded,
                    onDismissRequest = { formatDropdownExpanded = false },
                    modifier = Modifier
                        .background(TubePrimary)
                        .border(1.dp, TubeBorder, RoundedCornerShape(8.dp))
                ) {
                    metadata.formats.forEach { format ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = format.quality,
                                        color = if (format == selectedFormat) TubeAccent else TubeTextLight,
                                        fontWeight = if (format == selectedFormat) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (!format.approximateSize.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = format.approximateSize,
                                            color = TubeTextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectFormat(format)
                                formatDropdownExpanded = false
                            },
                            modifier = Modifier.testTag("dropdown_item_${format.quality.replace(" ", "_")}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Download Action Button
            Button(
                onClick = onStartDownload,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_start_download"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TubeAccent,
                    contentColor = TubeOledDark
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Télécharger en arrière-plan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun VideoDownloadingCard(
    metadata: MediaInfo,
    selectedFormat: MediaFormat,
    progress: Float,
    bytesDownloaded: Long,
    totalBytes: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_downloading_card"),
        colors = CardDefaults.cardColors(containerColor = TubePrimary),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TubeAccent.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(36.dp),
                    color = TubeAccent,
                    trackColor = TubeSurfaceVariant,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Téléchargement en cours...",
                        color = TubeTextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = metadata.title,
                        color = TubeTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = TubeAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("download_progress_bar"),
                color = TubeAccent,
                trackColor = TubeSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val downloadedMb = bytesDownloaded / (1024f * 1024f)
                val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f

                Text(
                    text = if (totalMb > 0) {
                        String.format("%.1f Mo / %.1f Mo", downloadedMb, totalMb)
                    } else {
                        String.format("%.1f Mo", downloadedMb)
                    },
                    color = TubeTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = selectedFormat.quality,
                    color = TubeAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun VideoSuccessCard(
    video: DownloadedVideo,
    onPlay: () -> Unit,
    onGoToLibrary: () -> Unit,
    onNewDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_success_card"),
        colors = CardDefaults.cardColors(containerColor = TubePrimary),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TubeAccent)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(TubeAccentDim, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TubeAccent,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Téléchargement terminé !",
                style = MaterialTheme.typography.titleMedium,
                color = TubeTextLight,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = video.title,
                color = TubeTextMuted,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("btn_play_downloaded_now"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TubeAccent,
                        contentColor = TubeOledDark
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lire", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onGoToLibrary,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("btn_view_in_library"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TubeSurfaceVariant,
                        contentColor = TubeTextLight
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bibliothèque", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onNewDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = TubeTextMuted
                )
            ) {
                Text("Télécharger un autre lien", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TvWorkflowStepper(activeStep: Int) {
    val steps = listOf(
        "Paste URL" to 1,
        "Resolve" to 2,
        "Format" to 3,
        "Download" to 4
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (label, step) ->
            val isActive = activeStep >= step
            val isCurrent = activeStep == step

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive && activeStep > step) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Fait",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = step.toString(),
                            color = if (isActive) MaterialTheme.colorScheme.onPrimary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(0.5f)
                        .background(
                            if (activeStep > step) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}


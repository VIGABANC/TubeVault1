package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.AiChapter
import com.example.data.model.AiTranscript
import com.example.data.model.DownloadedVideo
import com.example.ui.AiToolState
import com.example.ui.TubeVaultViewModel
import com.example.ui.components.AiToolsSheet
import com.example.ui.theme.TubeAccent
import com.example.ui.theme.TubeBorder
import com.example.ui.theme.TubeOledDark
import com.example.ui.theme.TubePrimary
import com.example.ui.theme.TubeSurfaceVariant
import com.example.ui.theme.TubeTextLight
import com.example.ui.theme.TubeTextMuted
import com.example.util.TranscriptFixture
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    video: DownloadedVideo,
    viewModel: TubeVaultViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect video updates dynamically from view model libraryVideos flow to avoid stale properties
    val videos by viewModel.libraryVideos.collectAsState()
    val currentVideo = remember(videos, video) {
        videos.find { it.id == video.id } ?: video
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    BackHandler {
        onBack()
    }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }

    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderValue by remember { mutableFloatStateOf(0f) }

    // Bottom sheet state for AI Tools
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAiToolsSheet by remember { mutableStateOf(false) }

    // Active tab state
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Résumé", "Chapitres", "Transcription")

    // Auto-hide controls after 3 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    // Progress ticker
    LaunchedEffect(isPlaying, isSeeking) {
        while (isPlaying && !isSeeking) {
            videoViewRef?.let { vv ->
                currentPositionMs = vv.currentPosition
                val dur = vv.duration
                if (dur > 0) {
                    durationMs = dur
                }
            }
            delay(300)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    Scaffold(
        containerColor = TubeOledDark,
        floatingActionButton = {
            if (!isLandscape && !showAiToolsSheet) {
                FloatingActionButton(
                    onClick = { showAiToolsSheet = true },
                    containerColor = TubeAccent,
                    contentColor = TubeOledDark,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 4.dp)
                        .testTag("btn_fab_ai_tools")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Outils IA"
                    )
                }
            }
        }
    ) { paddingValues ->
        if (showAiToolsSheet) {
            AiToolsSheet(
                video = currentVideo,
                viewModel = viewModel,
                sheetState = sheetState,
                onDismiss = { showAiToolsSheet = false }
            )
        }

        if (isLandscape) {
            // FULLSCREEN PLAYER LANDSCAPE LAYOUT
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PlayerVideoView(
                    video = currentVideo,
                    onPrepared = { vv, mp ->
                        isBuffering = false
                        durationMs = mp.duration.coerceAtLeast(1)
                        vv.start()
                        isPlaying = true
                    },
                    onCompletion = {
                        isPlaying = false
                        showControls = true
                    },
                    onError = {
                        isBuffering = false
                    },
                    onViewCreated = { videoViewRef = it }
                )

                PlayerControlsOverlay(
                    video = currentVideo,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    showControls = showControls,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    isSeeking = isSeeking,
                    seekSliderValue = seekSliderValue,
                    onToggleControls = { showControls = !showControls },
                    onBack = onBack,
                    onPlayPause = {
                        videoViewRef?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    },
                    onSeekBack = {
                        videoViewRef?.let { vv ->
                            val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                            vv.seekTo(target)
                            currentPositionMs = target
                        }
                    },
                    onSeekForward = {
                        videoViewRef?.let { vv ->
                            val target = (vv.currentPosition + 10000).coerceAtMost(durationMs)
                            vv.seekTo(target)
                            currentPositionMs = target
                        }
                    },
                    onSliderChange = {
                        isSeeking = true
                        seekSliderValue = it
                    },
                    onSliderFinished = {
                        val targetMs = (seekSliderValue * durationMs).toInt()
                        videoViewRef?.seekTo(targetMs)
                        currentPositionMs = targetMs
                        isSeeking = false
                    }
                )
            }
        } else {
            // PORTRAIT SPLIT SCREEN PLAY + DETAILS DETAILS TABS
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(TubeOledDark)
            ) {
                // UPPER BOX: 16:9 Video view + Overlay Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    PlayerVideoView(
                        video = currentVideo,
                        onPrepared = { vv, mp ->
                            isBuffering = false
                            durationMs = mp.duration.coerceAtLeast(1)
                            vv.start()
                            isPlaying = true
                        },
                        onCompletion = {
                            isPlaying = false
                            showControls = true
                        },
                        onError = {
                            isBuffering = false
                        },
                        onViewCreated = { videoViewRef = it }
                    )

                    PlayerControlsOverlay(
                        video = currentVideo,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        showControls = showControls,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isSeeking = isSeeking,
                        seekSliderValue = seekSliderValue,
                        onToggleControls = { showControls = !showControls },
                        onBack = onBack,
                        onPlayPause = {
                            videoViewRef?.let { vv ->
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        },
                        onSeekBack = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                                vv.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        onSeekForward = {
                            videoViewRef?.let { vv ->
                                val target = (vv.currentPosition + 10000).coerceAtMost(durationMs)
                                vv.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        onSliderChange = {
                            isSeeking = true
                            seekSliderValue = it
                        },
                        onSliderFinished = {
                            val targetMs = (seekSliderValue * durationMs).toInt()
                            videoViewRef?.seekTo(targetMs)
                            currentPositionMs = targetMs
                            isSeeking = false
                        }
                    )
                }

                // LOWER BOX: Tab titles
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = TubePrimary,
                    contentColor = TubeTextLight,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = TubeAccent
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                            selectedContentColor = TubeAccent,
                            unselectedContentColor = TubeTextMuted
                        )
                    }
                }

                // AI Active Operation Loader Banner
                val opState by viewModel.currentAiOperationState.collectAsState()
                val opMessage by viewModel.currentAiOperationMessage.collectAsState()
                val opType by viewModel.currentAiOperationType.collectAsState()

                if (opState != AiToolState.IDLE) {
                    Surface(
                        color = when (opState) {
                            AiToolState.ERROR -> Color(0xFFDC2626)
                            AiToolState.COMPLETED -> Color(0xFF16A34A)
                            else -> TubeSurfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (opState == AiToolState.PREPARING_CONTEXT || opState == AiToolState.GENERATING) {
                                    CircularProgressIndicator(
                                        color = TubeAccent,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = opMessage ?: "Opération en cours...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearAiOperationState() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Active Tab Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> SummaryTabContent(
                            video = currentVideo,
                            viewModel = viewModel,
                            onTriggerOperation = { viewModel.runAiOperation(currentVideo, it) }
                        )
                        1 -> ChaptersTabContent(
                            video = currentVideo,
                            viewModel = viewModel,
                            onSeekToSeconds = { secs ->
                                videoViewRef?.seekTo((secs * 1000).toInt())
                                currentPositionMs = (secs * 1000).toInt()
                            },
                            onTriggerOperation = { viewModel.runAiOperation(currentVideo, "chapters") }
                        )
                        2 -> TranscriptTabContent(
                            video = currentVideo,
                            viewModel = viewModel,
                            onSeekToSeconds = { secs ->
                                videoViewRef?.seekTo((secs * 1000).toInt())
                                currentPositionMs = (secs * 1000).toInt()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerVideoView(
    video: DownloadedVideo,
    onPrepared: (VideoView, MediaPlayer) -> Unit,
    onCompletion: (VideoView) -> Unit,
    onError: (VideoView) -> Unit,
    onViewCreated: (VideoView) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                val file = File(video.filePath)
                if (file.exists()) {
                    setVideoURI(Uri.fromFile(file))
                } else if (video.sourceUrl.isNotBlank()) {
                    setVideoPath(video.sourceUrl)
                }

                setOnPreparedListener { mp ->
                    onPrepared(this, mp)
                }

                setOnCompletionListener {
                    onCompletion(this)
                }

                setOnErrorListener { _, _, _ ->
                    onError(this)
                    false
                }

                onViewCreated(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PlayerControlsOverlay(
    video: DownloadedVideo,
    isPlaying: Boolean,
    isBuffering: Boolean,
    showControls: Boolean,
    currentPositionMs: Int,
    durationMs: Int,
    isSeeking: Boolean,
    seekSliderValue: Float,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggleControls() }
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center),
                color = TubeAccent,
                strokeWidth = 4.dp
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_player_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Qualité : ${video.resolution}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TubeAccent
                        )
                    }
                }

                // Center controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(
                        onClick = onSeekBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Reculer de 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(TubeAccent, CircleShape)
                            .clickable { onPlayPause() }
                            .testTag("btn_player_play_pause"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = TubeOledDark,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = onSeekForward,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Avancer de 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Seekbar and time display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Slider(
                        value = if (isSeeking) seekSliderValue else (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = onSliderChange,
                        onValueChangeFinished = onSliderFinished,
                        colors = SliderDefaults.colors(
                            thumbColor = TubeAccent,
                            activeTrackColor = TubeAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_seek_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayCurrent = if (isSeeking) {
                            (seekSliderValue * durationMs).toInt()
                        } else {
                            currentPositionMs
                        }

                        Text(
                            text = formatTime(displayCurrent),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryTabContent(
    video: DownloadedVideo,
    viewModel: TubeVaultViewModel,
    onTriggerOperation: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var manualTagText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TubeOledDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Vault privacy card toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = TubePrimary),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TubeBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (video.isPrivate) Icons.Default.Lock else Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (video.isPrivate) TubeAccent else TubeTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (video.isPrivate) "Média crypté dans le Vault" else "Média public",
                            color = TubeTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (video.isPrivate) "IA cloud restreinte par défaut" else "Eligible aux enrichissements IA",
                            color = TubeTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Button(
                    onClick = { viewModel.setPrivateMode(video, !video.isPrivate) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (video.isPrivate) TubeAccent else TubeSurfaceVariant,
                        contentColor = if (video.isPrivate) TubeOledDark else TubeTextLight
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (video.isPrivate) "Rendre public" else "Chiffrer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Suggested Title Alert Banner
        if (video.suggestedTitle != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1D1A)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TubeAccent.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Titre suggéré par l'IA :", color = TubeAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(video.suggestedTitle, color = TubeTextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.rejectSuggestedTitle(video) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Ignorer", color = TubeTextMuted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { viewModel.applySuggestedTitle(video) },
                            colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Appliquer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Suggested category topics display
        if (video.primaryCategory != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = TubeAccent,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = video.primaryCategory,
                        color = TubeOledDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val topicsList = video.topics?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    topicsList.forEach { topic ->
                        Surface(
                            color = TubeSurfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#$topic",
                                color = TubeTextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Tags concept separate display
        Text(
            text = "TAGS ET THÉMATIQUES",
            color = TubeAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Manual approved tags row
        val approvedTags = video.userApprovedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (approvedTags.isEmpty()) {
                Text("Aucun tag approuvé", color = TubeTextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                approvedTags.forEach { tag ->
                    Surface(
                        color = TubeAccent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, TubeAccent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(tag, color = TubeAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Supprimer",
                                tint = TubeAccent,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { viewModel.removeApprovedTag(video, tag) }
                            )
                        }
                    }
                }
            }
        }

        // Suggested Tags to Approve/Reject
        val suggestedTags = video.aiSuggestedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        if (suggestedTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = TubePrimary),
                border = BorderStroke(1.dp, TubeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Suggérés par l'IA (cliquez pour approuver) :", color = TubeTextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { viewModel.approveAllTags(video) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Tout approuver", color = TubeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestedTags.forEach { tag ->
                            Surface(
                                color = TubeSurfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { viewModel.approveTag(video, tag) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, tint = TubeTextMuted, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(tag, color = TubeTextLight, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Rejeter",
                                        tint = TubeTextMuted,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { viewModel.removeSuggestedTag(video, tag) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add custom manual tag input bar
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualTagText,
                onValueChange = { manualTagText = it },
                placeholder = { Text("Ajouter un tag personnalisé...", fontSize = 12.sp, color = TubeTextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (manualTagText.isNotBlank()) {
                        viewModel.addManualTag(video, manualTagText)
                        manualTagText = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TubeAccent,
                    unfocusedBorderColor = TubeBorder,
                    focusedTextColor = TubeTextLight,
                    unfocusedTextColor = TubeTextLight,
                    focusedContainerColor = TubeSurfaceVariant,
                    unfocusedContainerColor = TubeSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (manualTagText.isNotBlank()) {
                        viewModel.addManualTag(video, manualTagText)
                        manualTagText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AI Summary section
        Text(
            text = "RÉSUMÉ MÉDIA",
            color = TubeAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val summaryText = video.detailedSummary ?: video.shortSummary
        if (summaryText != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TubePrimary),
                border = BorderStroke(1.dp, TubeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = summaryText,
                        color = TubeTextLight,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Généré à partir des : ${if (video.summarySource == "transcript") "Sous-titres réels" else "Métadonnées textuelles"}",
                        color = TubeTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onTriggerOperation("summary_short") }) {
                    Text("Version courte", color = TubeAccent, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                TextButton(onClick = { onTriggerOperation("summary_detailed") }) {
                    Text("Version détaillée", color = TubeAccent, fontSize = 12.sp)
                }
            }
        } else {
            // Dashboard placeholder when no summary exists
            Card(
                colors = CardDefaults.cardColors(containerColor = TubePrimary),
                border = BorderStroke(1.dp, TubeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = TubeTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucun résumé disponible", color = TubeTextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Enrichissez ce fichier avec l'intelligence artificielle pour générer un résumé synthétique de sa thématique.",
                        color = TubeTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { onTriggerOperation("summary_short") },
                            colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Générer résumé", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onTriggerOperation("tags") },
                            colors = ButtonDefaults.buttonColors(containerColor = TubeSurfaceVariant, contentColor = TubeTextLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Générer tags", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onTriggerOperation("classify") }) {
                        Text("Classer et catégoriser", color = TubeAccent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChaptersTabContent(
    video: DownloadedVideo,
    viewModel: TubeVaultViewModel,
    onSeekToSeconds: (Long) -> Unit,
    onTriggerOperation: () -> Unit
) {
    val chapters = remember(video.aiChaptersJson) {
        AiChapter.listFromJsonString(video.aiChaptersJson)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TubeOledDark)
            .padding(16.dp)
    ) {
        Text(
            text = "CHAPITRES DE LECTURE INTELLIGENTS",
            color = TubeAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (chapters.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chapters) { chapter ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TubePrimary),
                        border = BorderStroke(1.dp, TubeBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Badge Timestamp button to seek
                            Button(
                                onClick = { onSeekToSeconds(chapter.startTimestamp) },
                                colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                val mins = chapter.startTimestamp / 60
                                val secs = chapter.startTimestamp % 60
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapter.title,
                                    color = TubeTextLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = chapter.shortDescription,
                                    color = TubeTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Placeholder when empty
            Card(
                colors = CardDefaults.cardColors(containerColor = TubePrimary),
                border = BorderStroke(1.dp, TubeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListBulleted,
                        contentDescription = null,
                        tint = TubeTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucun chapitre structuré", color = TubeTextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "L'intelligence artificielle peut segmenter ce média en chapitres logiques basés sur le contexte.",
                        color = TubeTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onTriggerOperation,
                        colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Générer les chapitres", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptTabContent(
    video: DownloadedVideo,
    viewModel: TubeVaultViewModel,
    onSeekToSeconds: (Double) -> Unit
) {
    val transcript = remember(video.transcriptJson) {
        AiTranscript.fromJsonString(video.transcriptJson)
    }

    var transcriptSearchText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TubeOledDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRANSCRIPTION ET SOUS-TITRES RÉELS",
                color = TubeAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            // Dynamic test loader
            if (transcript == null) {
                TextButton(
                    onClick = {
                        viewModel.importTranscript(video, TranscriptFixture.SRT_FIXTURE, "fr")
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Charger la démo", color = TubeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (transcript != null) {
            // Search inside transcript text field
            OutlinedTextField(
                value = transcriptSearchText,
                onValueChange = { transcriptSearchText = it },
                placeholder = { Text("Rechercher dans les sous-titres...", fontSize = 12.sp, color = TubeTextMuted) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = TubeTextMuted, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (transcriptSearchText.isNotBlank()) {
                        IconButton(onClick = { transcriptSearchText = "" }) {
                            Icon(Icons.Default.Clear, null, tint = TubeTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TubeAccent,
                    unfocusedBorderColor = TubeBorder,
                    focusedTextColor = TubeTextLight,
                    unfocusedTextColor = TubeTextLight,
                    focusedContainerColor = TubeSurfaceVariant,
                    unfocusedContainerColor = TubeSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val filteredSegments = remember(transcript, transcriptSearchText) {
                if (transcriptSearchText.isBlank()) {
                    transcript.segments
                } else {
                    val q = transcriptSearchText.lowercase()
                    transcript.segments.filter { it.text.lowercase().contains(q) }
                }
            }

            if (filteredSegments.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSegments) { segment ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TubePrimary),
                            border = BorderStroke(1.dp, TubeBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekToSeconds(segment.startTime) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Clickable small timestamp button
                                Surface(
                                    color = TubeSurfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, TubeBorder),
                                    modifier = Modifier.clickable { onSeekToSeconds(segment.startTime) }
                                ) {
                                    val mins = (segment.startTime / 60).toInt()
                                    val secs = (segment.startTime % 60).toInt()
                                    Text(
                                        text = String.format("%02d:%02d", mins, secs),
                                        color = TubeAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = segment.text,
                                    color = TubeTextLight,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun segment ne correspond à votre recherche.", color = TubeTextMuted, fontSize = 12.sp)
                }
            }
        } else {
            // Placeholder when empty
            Card(
                colors = CardDefaults.cardColors(containerColor = TubePrimary),
                border = BorderStroke(1.dp, TubeBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = TubeTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aucun sous-titre importé", color = TubeTextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Pour afficher la transcription complète avec recherche textuelle et accès rapide aux passages, importez un fichier de sous-titre SRT ou WebVTT.",
                        color = TubeTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    var importText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("Coller le contenu SRT ou WebVTT ici...", fontSize = 11.sp, color = TubeTextMuted) },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TubeAccent,
                            unfocusedBorderColor = TubeBorder,
                            focusedTextColor = TubeTextLight,
                            unfocusedTextColor = TubeTextLight
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (importText.isNotBlank()) {
                                viewModel.importTranscript(video, importText, "fr")
                                importText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Importer la transcription", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

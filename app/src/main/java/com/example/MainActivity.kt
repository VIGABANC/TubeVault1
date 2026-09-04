package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import com.example.data.model.DownloadedVideo
import com.example.ui.screens.VaultLockScreen
import com.example.ui.screens.VaultScreen
import java.io.File
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.DownloadStatus
import com.example.ui.TubeVaultViewModel
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.TubeAccent
import com.example.ui.theme.TubeOledDark
import com.example.ui.theme.TubePrimary
import com.example.ui.theme.TubeTextMuted
import com.example.ui.theme.TubeVaultTheme

enum class TubeVaultNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    ACCUEIL(
        title = "Accueil",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_accueil"
    ),
    EXPLORER(
        title = "Explorer",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_tab_explorer"
    ),
    TELECHARGEMENTS(
        title = "Téléchargements",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        testTag = "nav_tab_telechargements"
    ),
    BIBLIOTHEQUE(
        title = "Bibliothèque",
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_tab_bibliotheque"
    ),
    REGLAGES(
        title = "Réglages",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_reglages"
    ),
    VAULT(
        title = "Vault",
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock,
        testTag = "nav_tab_vault"
    )
}

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: TubeVaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            val browserSettings by viewModel.browserSettings.collectAsStateWithLifecycle()
            val currentTheme = browserSettings.theme
            TubeVaultTheme(themeMode = currentTheme) {
                TubeVaultApp(viewModel = viewModel, activity = this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.vaultSessionManager.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.vaultSessionManager.checkTimeout()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleIncomingShare(sharedText)
            }
        }
    }
}

@Composable
fun TubeVaultApp(
    viewModel: TubeVaultViewModel = viewModel(),
    activity: androidx.fragment.app.FragmentActivity? = null
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(TubeVaultNavTab.ACCUEIL) }

    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()

    // FLAG_SECURE handling when Vault is selected/unlocked
    LaunchedEffect(currentTab) {
        if (currentTab == TubeVaultNavTab.VAULT) {
            activity?.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val sharedIncomingUrl by viewModel.sharedIncomingUrl.collectAsStateWithLifecycle()
    LaunchedEffect(sharedIncomingUrl) {
        if (sharedIncomingUrl != null) {
            currentTab = TubeVaultNavTab.ACCUEIL
        }
    }

    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val libraryVideos by viewModel.libraryVideos.collectAsStateWithLifecycle()
    val currentPlayingVideo by viewModel.currentPlayingVideo.collectAsStateWithLifecycle()

    val activeCount = downloadTasks.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    // Runtime permission for Android 13+ (Tiramisu) notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Fullscreen Video Player Mode
    if (currentPlayingVideo != null) {
        VideoPlayerScreen(
            video = currentPlayingVideo!!,
            viewModel = viewModel,
            onBack = { viewModel.closePlayer() }
        )
        return
    }

    // Standard Tab Navigation Mode
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("tubevault_bottom_nav")
            ) {
                listOf(
                    TubeVaultNavTab.ACCUEIL,
                    TubeVaultNavTab.EXPLORER,
                    TubeVaultNavTab.TELECHARGEMENTS,
                    TubeVaultNavTab.BIBLIOTHEQUE,
                    TubeVaultNavTab.VAULT,
                    TubeVaultNavTab.REGLAGES
                ).forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == TubeVaultNavTab.TELECHARGEMENTS && activeCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(text = activeCount.toString(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = when (tab) {
                                    TubeVaultNavTab.ACCUEIL -> "Home"
                                    TubeVaultNavTab.EXPLORER -> "Explorer"
                                    TubeVaultNavTab.TELECHARGEMENTS -> "Downloads"
                                    TubeVaultNavTab.BIBLIOTHEQUE -> "Library"
                                    TubeVaultNavTab.VAULT -> "Vault"
                                    TubeVaultNavTab.REGLAGES -> "Settings"
                                },
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            label = "tab_crossfade"
        ) { tab ->
            when (tab) {
                TubeVaultNavTab.ACCUEIL -> {
                    HomeScreen(
                        viewModel = viewModel,
                        downloadState = downloadState,
                        onNavigateToLibrary = { currentTab = TubeVaultNavTab.BIBLIOTHEQUE },
                        onNavigateToDownloads = { currentTab = TubeVaultNavTab.TELECHARGEMENTS },
                        onNavigateToBrowser = { currentTab = TubeVaultNavTab.EXPLORER }
                    )
                }
                TubeVaultNavTab.EXPLORER -> {
                    BrowserScreen(
                        viewModel = viewModel
                    )
                }
                TubeVaultNavTab.TELECHARGEMENTS -> {
                    DownloadsScreen(
                        viewModel = viewModel,
                        onPlayVideo = { video -> viewModel.openPlayer(video) },
                        onNavigateToHome = { currentTab = TubeVaultNavTab.ACCUEIL }
                    )
                }
                TubeVaultNavTab.BIBLIOTHEQUE -> {
                    LibraryScreen(
                        viewModel = viewModel,
                        videos = libraryVideos,
                        onNavigateToHome = { currentTab = TubeVaultNavTab.ACCUEIL }
                    )
                }
                TubeVaultNavTab.REGLAGES -> {
                    SettingsScreen(
                        videos = libraryVideos,
                        viewModel = viewModel
                    )
                }
                TubeVaultNavTab.VAULT -> {
                    if (!isVaultUnlocked) {
                        VaultLockScreen(
                            onUnlockClicked = {
                                if (activity != null) {
                                    com.example.ui.components.BiometricAuthHelper.authenticate(
                                        activity = activity,
                                        onSuccess = { viewModel.unlockVault() },
                                        onError = { err -> android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show() },
                                        onFailed = {}
                                    )
                                } else {
                                    viewModel.unlockVault()
                                }
                            }
                        )
                    } else {
                        VaultScreen(
                            viewModel = viewModel,
                            vaultItems = vaultItems,
                            onLockClicked = { viewModel.lockVault() },
                            onPlayVaultVideo = { vaultItem ->
                                viewModel.getDecryptedPlaybackFile(vaultItem) { playbackFile ->
                                    if (playbackFile != null) {
                                        val meta = viewModel.getVaultDisplayMetadata(vaultItem)
                                        if (meta != null) {
                                            val video = DownloadedVideo(
                                                title = meta.title,
                                                thumbnailUrl = meta.thumbnailUrl,
                                                durationText = meta.durationText,
                                                resolution = meta.resolution,
                                                filePath = playbackFile.absolutePath,
                                                fileSizeBytes = playbackFile.length(),
                                                sourceUrl = meta.sourceUrl,
                                                platform = meta.platform
                                            )
                                            viewModel.openPlayer(video)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


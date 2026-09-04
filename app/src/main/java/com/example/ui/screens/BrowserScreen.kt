package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.detector.MediaCandidate
import com.example.data.model.DownloadState
import com.example.data.model.Platform
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import com.example.ui.TubeVaultViewModel
import com.example.ui.components.BatchDownloadSheet
import com.example.ui.components.MediaDrawerSheet
import com.example.ui.components.PlatformBadge
import com.example.ui.components.QualityPickerSheet
import com.example.ui.theme.TubeAccent
import com.example.ui.theme.TubeAccentDim
import com.example.ui.theme.TubeBorder
import com.example.ui.theme.TubeOledDark
import com.example.ui.theme.TubePrimary
import com.example.ui.theme.TubeSurfaceVariant
import com.example.ui.theme.TubeTextLight
import com.example.ui.theme.TubeTextMuted
import com.example.util.DetectedVideo
import com.example.util.VideoUrlDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class BrowserShortcut(
    val title: String,
    val subtitle: String,
    val url: String,
    val platform: Platform,
    val accentColor: Color
)

private val BROWSER_SHORTCUTS = listOf(
    BrowserShortcut(
        title = "YouTube",
        subtitle = "Vidéos, Clips & Shorts",
        url = "https://m.youtube.com",
        platform = Platform.YOUTUBE,
        accentColor = Color(0xFFFF0000)
    ),
    BrowserShortcut(
        title = "TikTok",
        subtitle = "Vidéos virales & tendances",
        url = "https://www.tiktok.com",
        platform = Platform.TIKTOK,
        accentColor = Color(0xFF00F2FE)
    ),
    BrowserShortcut(
        title = "Instagram",
        subtitle = "Reels & publications",
        url = "https://www.instagram.com",
        platform = Platform.INSTAGRAM,
        accentColor = Color(0xFFE1306C)
    ),
    BrowserShortcut(
        title = "Twitter / X",
        subtitle = "Fils d'actualités & clips",
        url = "https://x.com",
        platform = Platform.TWITTER,
        accentColor = Color(0xFF1DA1F2)
    )
)

fun resolveBrowserUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return "https://m.youtube.com"

    // Block dangerous local and script schemes
    val lower = trimmed.lowercase()
    if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("content:") || lower.startsWith("data:")) {
        return try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            "https://m.youtube.com/results?search_query=$encoded"
        } catch (_: Exception) {
            "https://m.youtube.com"
        }
    }

    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }

    val domainRegex = Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(/.*)?$")
    if (domainRegex.matches(trimmed)) {
        return "https://$trimmed"
    }

    return try {
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        "https://m.youtube.com/results?search_query=$encoded"
    } catch (_: Exception) {
        "https://m.youtube.com"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: TubeVaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val browserSettings by viewModel.browserSettings.collectAsState()
    val downloadSettings by viewModel.downloadSettings.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val duplicateWarning by viewModel.duplicateWarningVideo.collectAsState()
    val detectedCandidates by viewModel.detectedCandidates.collectAsState()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webViewRecreateKey by remember { mutableIntStateOf(0) }
    var currentUrl by rememberSaveable { mutableStateOf("") }
    var urlInputText by rememberSaveable { mutableStateOf("") }
    var showStartPage by rememberSaveable { mutableStateOf(true) }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Detected video for current active URL
    var detectedVideo by remember { mutableStateOf<DetectedVideo?>(null) }

    // Sheets management
    var showMediaDrawer by rememberSaveable { mutableStateOf(false) }
    var showBatchSheet by rememberSaveable { mutableStateOf(false) }
    var batchSelectedCandidates by remember { mutableStateOf<List<MediaCandidate>>(emptyList()) }
    var showQualityPickerForMeta by remember { mutableStateOf<MediaInfo?>(null) }

    val drawerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val batchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val qualitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Advanced WebView State Handlers
    var webError by remember { mutableStateOf<String?>(null) }
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fileCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            fileCallback?.onReceiveValue(uris)
        } else {
            fileCallback?.onReceiveValue(null)
        }
        fileCallback = null
    }

    // Trigger page scan function
    fun triggerScanPage() {
        webViewInstance?.let { webView ->
            webView.evaluateJavascript(viewModel.mediaDetector.html5ScannerScript) { result ->
                viewModel.mediaDetector.onHtml5ScanResult(currentUrl, result)
            }
            webView.evaluateJavascript(viewModel.mediaDetector.inlineButtonInjectorScript, null)
            Toast.makeText(context, "Analyse de la page effectuée", Toast.LENGTH_SHORT).show()
        }
    }

    // Hide fullscreen custom view cleanly
    fun hideCustomVideo() {
        if (customView == null) return
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    // Physical Back Handler supporting Fullscreen video and internal history
    BackHandler(enabled = customView != null || (!showStartPage && canGoBack)) {
        if (customView != null) {
            hideCustomVideo()
        } else {
            webViewInstance?.goBack()
        }
    }

    // Synchronize address bar
    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            urlInputText = currentUrl
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                try {
                    evaluateJavascript("try { if (window._tubevaultObserver) { window._tubevaultObserver.disconnect(); window._tubevaultObserver = null; } } catch(e) {}", null)
                    stopLoading()
                    loadUrl("about:blank")
                    clearHistory()
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                } catch (_: Exception) {}
            }
            webViewInstance = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TubeOledDark)
    ) {
        // --- TOP CONTROLS & ADDRESS BAR ---
        BrowserAddressBar(
            urlInput = urlInputText,
            onUrlInputChange = { urlInputText = it },
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            isLoading = isLoading,
            detectedMediaCount = detectedCandidates.size,
            onGoBack = { webViewInstance?.goBack() },
            onGoForward = { webViewInstance?.goForward() },
            onRefresh = { 
                webError = null
                webViewInstance?.reload() 
            },
            onStop = { webViewInstance?.stopLoading() },
            onGoHome = {
                showStartPage = true
                currentUrl = ""
                urlInputText = ""
                detectedVideo = null
                webError = null
                viewModel.mediaDetector.clear()
                webViewInstance?.loadUrl("about:blank")
            },
            onOpenMediaDrawer = { showMediaDrawer = true },
            onScanPage = { triggerScanPage() },
            onClearMedia = { viewModel.mediaDetector.clear() },
            onSubmit = { input ->
                keyboardController?.hide()
                val targetUrl = resolveBrowserUrl(input)
                showStartPage = false
                currentUrl = targetUrl
                urlInputText = targetUrl
                detectedVideo = VideoUrlDetector.detectVideo(targetUrl)
                webError = null
                viewModel.mediaDetector.clear()
                viewModel.mediaDetector.onPageUrlChanged(targetUrl)
                webViewInstance?.loadUrl(targetUrl)
            },
            context = context,
            webViewInstance = webViewInstance
        )

        // Loading Progress Bar
        if (isLoading && !showStartPage) {
            LinearProgressIndicator(
                progress = { pageProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = TubeAccent,
                trackColor = TubeBorder
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TubeBorder)
            )
        }

        if (!browserSettings.javascriptEnabled && !showStartPage) {
            Surface(
                color = Color(0xFF7F1D1D), // Dark Red/Brown Warning
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "JavaScript désactivé. Certains sites peuvent ne pas fonctionner.",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // --- BROWSER VIEWPORT ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (showStartPage) {
                BrowserStartPage(
                    onSelectShortcut = { shortcutUrl ->
                        showStartPage = false
                        currentUrl = shortcutUrl
                        urlInputText = shortcutUrl
                        detectedVideo = VideoUrlDetector.detectVideo(shortcutUrl)
                        viewModel.mediaDetector.clear()
                        viewModel.mediaDetector.onPageUrlChanged(shortcutUrl)
                        webViewInstance?.loadUrl(shortcutUrl)
                    },
                    onSearchQuery = { query ->
                        val targetUrl = resolveBrowserUrl(query)
                        showStartPage = false
                        currentUrl = targetUrl
                        urlInputText = targetUrl
                        detectedVideo = VideoUrlDetector.detectVideo(targetUrl)
                        viewModel.mediaDetector.clear()
                        viewModel.mediaDetector.onPageUrlChanged(targetUrl)
                        webViewInstance?.loadUrl(targetUrl)
                    }
                )
            }

            // Android WebView
            if (!showStartPage) {
                key(webViewRecreateKey) {
                    AndroidView(
                    factory = { ctx ->
                        try {
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                // Enable standard cookie handling
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = browserSettings.javascriptEnabled
                                    domStorageEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false

                                    setSupportMultipleWindows(!browserSettings.blockPopups)
                                    javaScriptCanOpenWindowsAutomatically = !browserSettings.blockPopups
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    userAgentString = settings.userAgentString.replace("; wv", "")
                                }

                                // Intercept direct file downloads from webpage
                                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                    viewModel.mediaDetector.onNetworkRequest(currentUrl, url, mimetype)
                                }

                                // Bridge for Inline HTML5 Download Badge
                                addJavascriptInterface(
                                    object {
                                        @android.webkit.JavascriptInterface
                                        fun onVideoDownloadClicked(src: String?, poster: String?, title: String?, pageUrl: String?) {
                                            post {
                                                val targetPage = pageUrl ?: currentUrl
                                                val detected = VideoUrlDetector.detectVideo(targetPage)
                                                coroutineScope.launch {
                                                    if (detected != null) {
                                                        // For YouTube/TikTok/Instagram/Twitter: prioritize canonical URL & extractor engine
                                                        val result = withContext(Dispatchers.IO) {
                                                            viewModel.extractorEngine.resolve(detected.originalUrl)
                                                        }
                                                        if (result.isSuccess) {
                                                            val meta = result.getOrThrow()
                                                            if (downloadSettings.quickDownloadEnabled) {
                                                                viewModel.quickDownload(meta)
                                                                Toast.makeText(context, "Téléchargement rapide lancé !", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                showQualityPickerForMeta = meta
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Erreur de résolution", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        // HTML5 direct media
                                                        val mediaUrl = if (!src.isNullOrBlank()) src else targetPage
                                                        val ext = mediaUrl.substringAfterLast(".", "mp4").substringBefore("?").lowercase()
                                                        val directFormat = MediaFormat(
                                                            quality = "HTML5 Video (${ext.uppercase()})",
                                                            downloadUrl = mediaUrl,
                                                            extension = ext,
                                                            container = ext.uppercase()
                                                        )
                                                        val meta = MediaInfo(
                                                            title = if (!title.isNullOrBlank()) title else "Vidéo_${System.currentTimeMillis()}",
                                                            thumbnailUrl = poster ?: "",
                                                            durationText = "--:--",
                                                            sourceUrl = targetPage,
                                                            platform = Platform.OTHER,
                                                            formats = listOf(directFormat)
                                                        )
                                                        if (downloadSettings.quickDownloadEnabled) {
                                                            viewModel.quickDownload(meta)
                                                            Toast.makeText(context, "Téléchargement rapide lancé !", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            showQualityPickerForMeta = meta
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    "TubeVaultBridge"
                                )

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        webError = null
                                        url?.let {
                                            currentUrl = it
                                            detectedVideo = VideoUrlDetector.detectVideo(it)
                                            viewModel.mediaDetector.onPageUrlChanged(it)
                                        }
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        url?.let {
                                            currentUrl = it
                                            detectedVideo = VideoUrlDetector.detectVideo(it)
                                            viewModel.mediaDetector.onPageUrlChanged(it)
                                        }
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true

                                        // Automatic HTML5 DOM scan and inline download badge injection
                                        if (downloadSettings.browserAutoDetect && url != null && url != "about:blank") {
                                            view?.evaluateJavascript(viewModel.mediaDetector.html5ScannerScript) { result ->
                                                viewModel.mediaDetector.onHtml5ScanResult(url, result)
                                            }
                                            view?.evaluateJavascript(viewModel.mediaDetector.inlineButtonInjectorScript, null)
                                        }
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString()
                                        if (reqUrl != null && currentUrl.isNotBlank()) {
                                            viewModel.mediaDetector.onNetworkRequest(currentUrl, reqUrl)
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val uri = request?.url ?: return false
                                        val url = uri.toString()
                                        val scheme = uri.scheme?.lowercase() ?: ""

                                        // 1. Handle non-http/https external schemes safely (preventing crashes and unauthorized command execution)
                                        if (scheme != "http" && scheme != "https" && scheme != "about") {
                                            // Block non-user-gesture external redirects
                                            if (browserSettings.blockAdRedirects && !request.hasGesture()) {
                                                return true
                                            }

                                            try {
                                                val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                                                intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                                                intent.component = null
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                                    intent.selector = null
                                                }

                                                val resolvedActivity = intent.resolveActivity(context.packageManager)
                                                if (resolvedActivity != null) {
                                                    context.startActivity(intent)
                                                } else {
                                                    if (scheme == "mailto" || scheme == "tel" || scheme == "sms") {
                                                        val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                        context.startActivity(fallbackIntent)
                                                    } else {
                                                        Toast.makeText(context, "Aucune application disponible pour ce lien", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (_: Exception) {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Lien non supporté", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            return true // Handled
                                        }

                                        // 2. Redirect/Ad Blocking for HTTP/HTTPS URLs
                                        if (browserSettings.blockAdRedirects) {
                                            val host = uri.host?.lowercase() ?: ""
                                            if (host.contains("adservice") ||
                                                host.contains("doubleclick") ||
                                                host.contains("popads") ||
                                                host.contains("propellerads") ||
                                                host.contains("onclickads") ||
                                                host.contains("adsterra") ||
                                                host.contains("exoclick")
                                            ) {
                                                return true // Block ad hosts
                                            }

                                            // Block unwanted external redirects if hasGesture is false and isRedirect is true
                                            if (!request.hasGesture() && request.isRedirect) {
                                                val currentHost = Uri.parse(currentUrl).host?.lowercase() ?: ""
                                                val newHost = host
                                                if (currentHost.isNotEmpty() && newHost.isNotEmpty() && 
                                                    !newHost.contains(currentHost) && !currentHost.contains(newHost) &&
                                                    !newHost.contains("google.com") && !newHost.contains("facebook.com") && !newHost.contains("apple.com")
                                                ) {
                                                    return true // Block non-user-gesture cross-domain redirects
                                                }
                                            }
                                        }
                                        return false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            webError = error?.description?.toString() ?: "Erreur de connexion"
                                            isLoading = false
                                        }
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: android.webkit.SslErrorHandler?,
                                        error: android.net.http.SslError?
                                    ) {
                                        // Security audit rule: always cancel invalid SSL certificates
                                        handler?.cancel()
                                        isLoading = false
                                        Toast.makeText(context, "Erreur de certificat SSL sécurisé", Toast.LENGTH_LONG).show()
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: android.webkit.RenderProcessGoneDetail?
                                    ): Boolean {
                                        val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            detail?.didCrash() == true
                                        } else {
                                            false
                                        }
                                        android.util.Log.e("TubeVaultBrowser", "Renderer process exited (didCrash=$didCrash). Cleaning up and recreating WebView...")

                                        try {
                                            view?.stopLoading()
                                            (view?.parent as? ViewGroup)?.removeView(view)
                                            view?.destroy()
                                        } catch (_: Exception) {}

                                        if (webViewInstance == view) {
                                            webViewInstance = null
                                        }

                                        isLoading = false
                                        webError = "Le moteur de rendu web a rencontré un problème et a été réinitialisé."
                                        webViewRecreateKey++
                                        return true
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress / 100f
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }

                                    override fun onShowCustomView(view: android.view.View?, callback: WebChromeClient.CustomViewCallback?) {
                                        super.onShowCustomView(view, callback)
                                        if (customView != null) {
                                            callback?.onCustomViewHidden()
                                            return
                                        }
                                        customView = view
                                        customViewCallback = callback
                                    }

                                    override fun onHideCustomView() {
                                        super.onHideCustomView()
                                        hideCustomVideo()
                                    }

                                    override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                        // Security audit: deny website access to sensors, camera, mic by default
                                        request?.deny()
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: android.os.Message?
                                    ): Boolean {
                                        if (isUserGesture || !browserSettings.blockPopups) {
                                            val contextForPopup = view?.context ?: context
                                            val newWebView = WebView(contextForPopup)
                                            newWebView.webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                    val url = request?.url?.toString()
                                                    if (url != null) {
                                                        webViewInstance?.loadUrl(url)
                                                    }
                                                    return true
                                                }
                                            }
                                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                                            transport?.webView = newWebView
                                            resultMsg?.sendToTarget()
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                                        fileChooserParams: WebChromeClient.FileChooserParams?
                                    ): Boolean {
                                        fileCallback = filePathCallback
                                        try {
                                            val intent = fileChooserParams?.createIntent()
                                            if (intent != null) {
                                                filePickerLauncher.launch(intent)
                                            }
                                        } catch (_: Exception) {
                                            fileCallback?.onReceiveValue(null)
                                            fileCallback = null
                                        }
                                        return true
                                    }
                                }

                                webViewInstance = this
                                if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
                                    loadUrl(currentUrl)
                                }
                            }
                        } catch (e: Exception) {
                            // Handle missing classes.dex gracefully in emulators
                            android.widget.TextView(ctx).apply {
                                text = "WebView non supporté ou introuvable sur cet appareil."
                                setTextColor(android.graphics.Color.WHITE)
                                gravity = android.view.Gravity.CENTER
                            }
                        }
                    },
                    update = { view ->
                        if (view is WebView) {
                            view.settings.javaScriptEnabled = browserSettings.javascriptEnabled
                            view.settings.setSupportMultipleWindows(!browserSettings.blockPopups)
                            view.settings.javaScriptCanOpenWindowsAutomatically = !browserSettings.blockPopups
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                }
            }

            // HTML5 Fullscreen Video Playback Overlay
            if (customView != null) {
                AndroidView(
                    factory = { _ ->
                        android.widget.FrameLayout(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0xFF000000.toInt())
                            (customView?.parent as? ViewGroup)?.removeView(customView)
                            addView(customView, ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Browser Connection Error State Screen with Retry action
            if (webError != null && !showStartPage) {
                Surface(
                    color = TubeOledDark,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Impossible de charger la page",
                            color = TubeTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = webError ?: "Veuillez vérifier votre connexion internet.",
                            color = TubeTextMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                webError = null
                                isLoading = true
                                if (webViewInstance != null) {
                                    webViewInstance?.reload()
                                } else {
                                    webViewRecreateKey++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Réessayer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- FLOATING SMART MEDIA BUTTON (SnapTube Style) ---
            val showFloatingButton = !showStartPage && (detectedVideo != null || detectedCandidates.isNotEmpty())

            androidx.compose.animation.AnimatedVisibility(
                visible = showFloatingButton,
                enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
            ) {
                // If multiple media detected, pill offers drawer view; if single detected video, offers download
                val mediaCount = detectedCandidates.size

                ExtendedFloatingActionButton(
                    onClick = {
                        if (mediaCount > 1) {
                            showMediaDrawer = true
                        } else {
                            val targetUrl = detectedVideo?.originalUrl ?: detectedCandidates.firstOrNull()?.canonicalUrl ?: currentUrl
                            coroutineScope.launch(Dispatchers.IO) {
                                val result = viewModel.extractorEngine.resolve(targetUrl)
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        val meta = result.getOrThrow()
                                        if (downloadSettings.quickDownloadEnabled) {
                                            viewModel.quickDownload(meta)
                                            Toast.makeText(context, "Téléchargement rapide lancé !", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showQualityPickerForMeta = meta
                                        }
                                    } else {
                                        Toast.makeText(context, "Erreur de résolution", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    containerColor = TubeAccent,
                    contentColor = TubeOledDark,
                    shape = RoundedCornerShape(28.dp),
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(8.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (mediaCount > 1) "$mediaCount médias trouvés" else "Télécharger cette vidéo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            detectedVideo?.let { v ->
                                Spacer(modifier = Modifier.width(8.dp))
                                PlatformBadge(platform = v.platform, compact = true)
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_browser_download_video")
                )
            }
        }
    }

    // --- MODAL 1: MEDIA DRAWER (List of detected media on current page) ---
    if (showMediaDrawer) {
        MediaDrawerSheet(
            candidates = detectedCandidates,
            onDismiss = { showMediaDrawer = false },
            onDownloadSingle = { candidate ->
                coroutineScope.launch(Dispatchers.IO) {
                    val targetUrl = candidate.canonicalUrl.ifBlank { candidate.pageUrl }
                    val result = viewModel.extractorEngine.resolve(targetUrl)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val meta = result.getOrThrow()
                            if (downloadSettings.quickDownloadEnabled) {
                                viewModel.quickDownload(meta)
                                Toast.makeText(context, "Téléchargement rapide lancé !", Toast.LENGTH_SHORT).show()
                            } else {
                                showQualityPickerForMeta = meta
                            }
                        } else {
                            // Fallback direct format download
                            val fallbackFormat = candidate.availableFormats.firstOrNull() ?: MediaFormat(
                                quality = "Direct",
                                downloadUrl = candidate.mediaUrl ?: targetUrl
                            )
                            val fallbackMeta = MediaInfo(
                                title = candidate.title ?: "Media_${System.currentTimeMillis()}",
                                thumbnailUrl = candidate.thumbnail ?: "",
                                durationText = candidate.duration ?: "--:--",
                                sourceUrl = targetUrl,
                                platform = candidate.platform,
                                formats = listOf(fallbackFormat)
                            )
                            viewModel.downloadManager.enqueue(fallbackMeta, fallbackFormat)
                            Toast.makeText(context, "Téléchargement direct ajouté !", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDownloadBatch = { selectedList ->
                batchSelectedCandidates = selectedList
                showMediaDrawer = false
                showBatchSheet = true
            },
            onScanPage = { triggerScanPage() },
            onClearCandidates = { viewModel.mediaDetector.clear() },
            sheetState = drawerSheetState
        )
    }

    // --- MODAL 2: BATCH DOWNLOAD SHEET (Storage Check & Global Quality Selector) ---
    if (showBatchSheet) {
        BatchDownloadSheet(
            candidates = batchSelectedCandidates,
            freeStorageBytes = viewModel.getFreeStorageBytes(),
            onDismiss = { showBatchSheet = false },
            onConfirmBatch = { qualityPreference, wifiOnly ->
                coroutineScope.launch(Dispatchers.IO) {
                    val batchItems = mutableListOf<Pair<MediaInfo, MediaFormat>>()

                    for (candidate in batchSelectedCandidates) {
                        val targetUrl = candidate.canonicalUrl.ifBlank { candidate.pageUrl }
                        val resolveResult = viewModel.extractorEngine.resolve(targetUrl)

                        if (resolveResult.isSuccess) {
                            val meta = resolveResult.getOrThrow()
                            val chosenFormat = viewModel.pickFormatForQuality(meta.formats, qualityPreference)
                                ?: meta.formats.firstOrNull()
                                ?: MediaFormat(quality = "Standard", downloadUrl = targetUrl)
                            batchItems.add(Pair(meta, chosenFormat))
                        } else {
                            val fallbackFormat = candidate.availableFormats.firstOrNull() ?: MediaFormat(
                                quality = "Direct",
                                downloadUrl = candidate.mediaUrl ?: targetUrl
                            )
                            val fallbackMeta = MediaInfo(
                                title = candidate.title ?: "Video_${System.currentTimeMillis()}",
                                thumbnailUrl = candidate.thumbnail ?: "",
                                durationText = "--:--",
                                sourceUrl = targetUrl,
                                platform = candidate.platform,
                                formats = listOf(fallbackFormat)
                            )
                            batchItems.add(Pair(fallbackMeta, fallbackFormat))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (batchItems.isNotEmpty()) {
                            viewModel.enqueueBatch(batchItems)
                            Toast.makeText(
                                context,
                                "${batchItems.size} téléchargements ajoutés à la file !",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            sheetState = batchSheetState
        )
    }

    // --- MODAL 3: QUALITY PICKER PRO SHEET ---
    showQualityPickerForMeta?.let { meta ->
        QualityPickerSheet(
            metadata = meta,
            onDismiss = { showQualityPickerForMeta = null },
            onFormatSelected = { format ->
                viewModel.downloadManager.enqueue(meta, format)
                Toast.makeText(context, "Téléchargement lancé (${format.quality}) !", Toast.LENGTH_SHORT).show()
            },
            sheetState = qualitySheetState
        )
    }

    // --- DUPLICATE ALERT DIALOG ---
    if (duplicateWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateWarning() },
            containerColor = TubePrimary,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Vidéo déjà dans votre bibliothèque",
                    color = TubeTextLight,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Cette vidéo « ${duplicateWarning?.title} » a déjà été téléchargée et sauvegardée hors-ligne.",
                        color = TubeTextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Voulez-vous quand même la retélécharger ?",
                        color = TubeTextLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.enqueueCurrentDownload(forceIgnoreDuplicate = true)
                        Toast.makeText(context, "Téléchargement forcé ajouté !", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TubeAccent, contentColor = TubeOledDark)
                ) {
                    Text("Retélécharger")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicateWarning() }) {
                    Text("Annuler", color = TubeTextMuted)
                }
            }
        )
    }
}

/**
 * Top Address Bar with Media Badge button and Dropdown actions.
 */
@Composable
private fun BrowserAddressBar(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    detectedMediaCount: Int,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onGoHome: () -> Unit,
    onOpenMediaDrawer: () -> Unit,
    onScanPage: () -> Unit,
    onClearMedia: () -> Unit,
    onSubmit: (String) -> Unit,
    context: android.content.Context,
    webViewInstance: WebView?
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = TubePrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onGoHome,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("browser_btn_home")
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Accueil Explorer",
                    tint = TubeAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onGoBack,
                enabled = canGoBack,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("browser_btn_back")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Précédent",
                    tint = if (canGoBack) TubeTextLight else TubeTextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onGoForward,
                enabled = canGoForward,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("browser_btn_forward")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Suivant",
                    tint = if (canGoForward) TubeTextLight else TubeTextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { if (isLoading) onStop() else onRefresh() },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("browser_btn_refresh")
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Clear else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "Arrêter" else "Actualiser",
                    tint = TubeTextLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            // URL input
            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlInputChange,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("browser_address_input"),
                placeholder = {
                    Text(
                        text = "Rechercher ou URL...",
                        color = TubeTextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (urlInput.startsWith("https://")) Icons.Default.Lock else Icons.Default.Search,
                        contentDescription = null,
                        tint = if (urlInput.startsWith("https://")) TubeAccent else TubeTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (urlInput.isNotBlank()) {
                        IconButton(
                            onClick = { onUrlInputChange("") },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Effacer",
                                tint = TubeTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSubmit(urlInput) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TubeAccent,
                    unfocusedBorderColor = TubeBorder,
                    focusedTextColor = TubeTextLight,
                    unfocusedTextColor = TubeTextLight,
                    focusedContainerColor = TubeSurfaceVariant,
                    unfocusedContainerColor = TubeSurfaceVariant,
                    cursorColor = TubeAccent
                ),
                shape = RoundedCornerShape(22.dp)
            )

            // Media Badge action
            IconButton(
                onClick = onOpenMediaDrawer,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("browser_btn_media_badge")
            ) {
                BadgedBox(
                    badge = {
                        if (detectedMediaCount > 0) {
                            Badge(
                                containerColor = TubeAccent,
                                contentColor = TubeOledDark
                            ) {
                                  Text("$detectedMediaCount", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Médias détectés",
                        tint = if (detectedMediaCount > 0) TubeAccent else TubeTextLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // More Options Dropdown
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TubeTextLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(TubePrimary)
                ) {
                    DropdownMenuItem(
                        text = { Text("Analyser cette page", color = TubeTextLight) },
                        onClick = {
                            showMenu = false
                            onScanPage()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Actualiser", color = TubeTextLight) },
                        onClick = {
                            showMenu = false
                            onRefresh()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copier le lien", color = TubeTextLight) },
                        onClick = {
                            showMenu = false
                            val targetUrl = if (urlInput.isNotBlank()) urlInput else "https://m.youtube.com"
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Lien vidéo", targetUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Lien copié !", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Partager la page", color = TubeTextLight) },
                        onClick = {
                            showMenu = false
                            val targetUrl = if (urlInput.isNotBlank()) urlInput else "https://m.youtube.com"
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, targetUrl)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, webViewInstance?.title ?: "Lien TubeVault")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Partager via"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ouvrir dans le navigateur", color = TubeTextLight) },
                        onClick = {
                            showMenu = false
                            val targetUrl = if (urlInput.isNotBlank()) urlInput else "https://m.youtube.com"
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Aucun navigateur disponible", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    if (detectedMediaCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Effacer les médias", color = TubeTextLight) },
                            onClick = {
                                showMenu = false
                                onClearMedia()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Start page with quick shortcuts.
 */
@Composable
private fun BrowserStartPage(
    onSelectShortcut: (String) -> Unit,
    onSearchQuery: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = TubeAccentDim,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TubeAccent.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = TubeAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Smart Media Grabber",
                    color = TubeAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Explorez et Téléchargez",
            style = MaterialTheme.typography.headlineSmall,
            color = TubeTextLight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Naviguez sur vos réseaux et sites vidéo. TubeVault détecte automatiquement tous les médias d'une page pour les télécharger en qualité optimale.",
            style = MaterialTheme.typography.bodyMedium,
            color = TubeTextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Raccourcis rapides",
                style = MaterialTheme.typography.titleMedium,
                color = TubeTextLight,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(BROWSER_SHORTCUTS) { shortcut ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectShortcut(shortcut.url) }
                        .testTag("shortcut_${shortcut.title.lowercase().replace(" ", "_")}"),
                    colors = CardDefaults.cardColors(containerColor = TubePrimary),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TubeBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(shortcut.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                PlatformBadge(platform = shortcut.platform, compact = true)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = shortcut.title,
                            color = TubeTextLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = shortcut.subtitle,
                            color = TubeTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Suggestions de recherche",
            style = MaterialTheme.typography.labelLarge,
            color = TubeTextMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        val suggestions = listOf("Lofi chill", "Shorts tendances", "Techno mix", "Tutoriels", "Gaming")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            suggestions.take(3).forEach { tag ->
                Surface(
                    color = TubeSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TubeBorder),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSearchQuery(tag) }
                ) {
                    Text(
                        text = tag,
                        color = TubeTextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

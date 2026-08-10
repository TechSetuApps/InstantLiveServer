package app.techsetuapps.instantweb

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.SharedPreferences
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError

// Companion-constant imports for extension functions

// ================================================================
//  MainActivityActions — User Actions — file/folder pick, hosting, AdMob, output, viewer auto-try
// ================================================================

fun MainActivity.onFilePicked(uri: Uri) {
    val name = getFileName(uri)
    if (!name.endsWith(".html", true) && !name.endsWith(".htm", true)) {
        toast("Please select an .html file"); return
    }
    selectedUri = uri
    // Show selected file with green check indicator
    tvSelectedFile.text = name
    tvSelectedFile.setTextColor(0xFF059669.toInt())
    tvSelectedFile.setTypeface(Typeface.DEFAULT_BOLD)
    tvSelectedFile.background = drawRoundRect(0xFFECFDF5.toInt(), 8.dp().toFloat())
    // Folder mode exit — manual HTML pick
    isFolderMode = false
    selectedFolderUri = null
    if (isTvFolderInfoReady) tvFolderInfo.visibility = View.GONE
    // Recompute relative paths for existing extra files
    if (extraFileUris.isNotEmpty()) {
        extraRelativePaths.clear()
        for (extraUri in extraFileUris) {
            val rel = getRelativePath(extraUri, uri)
            if (rel != null) extraRelativePaths[extraUri] = rel
        }
    }
    refreshBtn()
    // No auto-host on file select — only auto-host when folder is selected or more files added
}

fun MainActivity.onFolderPicked(treeUri: Uri) {
    // Take persistable permission — survives activity recreation
    try {
        contentResolver.takePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) {}

    isFolderMode = true
    selectedFolderUri = treeUri

    // Show scanning indicator
    if (isTvFolderInfoReady) {
        tvFolderInfo.text = "Scanning folder..."
        tvFolderInfo.setTextColor(0xFFF59E0B.toInt())
        tvFolderInfo.visibility = View.VISIBLE
    }

    Thread {
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val files = scanFolder(treeUri, docId)

            // Auto-detect HTML file
            var htmlFile: Pair<Uri, String>? = null
            // Prefer index.html, then index.htm, then first HTML
            for (f in files) {
                val name = f.second.lowercase()
                if (name == "index.html") { htmlFile = f; break }
            }
            if (htmlFile == null) {
                for (f in files) {
                    val name = f.second.lowercase()
                    if (name == "index.htm") { htmlFile = f; break }
                }
            }
            if (htmlFile == null) {
                for (f in files) {
                    val name = f.second.lowercase()
                    if (name.endsWith(".html") || name.endsWith(".htm")) { htmlFile = f; break }
                }
            }

            // Set selected HTML
            if (htmlFile != null) {
                runOnUiThread {
                    this@onFolderPicked.selectedUri = htmlFile.first
                    val htmlName = htmlFile.second.substringAfterLast("/")
                    this@onFolderPicked.tvSelectedFile.text = htmlName
                    this@onFolderPicked.tvSelectedFile.setTextColor(0xFF111827.toInt())
                }
            }

            // Populate extraFileUris + relativePaths (all non-HTML files)
            val extras = files.filter { it != htmlFile }
            runOnUiThread {
                this@onFolderPicked.extraFileUris.clear()
                this@onFolderPicked.extraRelativePaths.clear()
                for ((fileUri, relPath) in extras) {
                    this@onFolderPicked.extraFileUris.add(fileUri)
                    this@onFolderPicked.extraRelativePaths[fileUri] = relPath
                }
                this@onFolderPicked.folderFileCount = files.size

                // Folder name from URI
                val folderName = try {
                    val segments = treeUri.pathSegments
                    segments.lastOrNull()?.substringAfterLast("/") ?: "Folder"
                } catch (_: Exception) { "Folder" }

                if (this@onFolderPicked.isTvFolderInfoReady) {
                    this@onFolderPicked.tvFolderInfo.text = "$folderName — $folderFileCount files found"
                    this@onFolderPicked.tvFolderInfo.setTextColor(0xFF059669.toInt())
                    this@onFolderPicked.tvFolderInfo.visibility = View.VISIBLE
                }
                // Hide manual extra files display
                if (this@onFolderPicked.isTvExtraFilesReady) this@onFolderPicked.tvExtraFiles.visibility = View.GONE

                this@onFolderPicked.refreshBtn()
                if (htmlFile != null) {
                    this@onFolderPicked.toast("Folder selected! HTML auto-detected.")
                    // Auto-trigger hosting when folder with HTML is selected
                    if (!this@onFolderPicked.isHosting) this@onFolderPicked.btnHost.performClick()
                } else {
                    this@onFolderPicked.toast("Folder selected — please also select an .html file")
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                if (this@onFolderPicked.isTvFolderInfoReady) {
                    this@onFolderPicked.tvFolderInfo.text = "Folder scan failed"
                    this@onFolderPicked.tvFolderInfo.setTextColor(0xFFDC2626.toInt())
                }
                this@onFolderPicked.toast("Error scanning folder: ${e.message}")
            }
        }
    }.start()
}

fun MainActivity.scanFolder(treeUri: Uri, parentDocId: String, basePath: String = ""): List<Pair<Uri, String>> {
    val results = mutableListOf<Pair<Uri, String>>()
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

    try {
        contentResolver.query(childrenUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        ), null, null, null)?.use { cursor ->
            val colId    = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val colName  = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val colMime  = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val colSize  = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(colId) ?: continue
                val name  = cursor.getString(colName) ?: continue
                val mime  = cursor.getString(colMime) ?: continue
                val size  = if (colSize >= 0) cursor.getLong(colSize) else 0L

                // Skip hidden files/folders
                if (name.startsWith(".")) continue
                // Skip common dev folders that can be huge
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                    name in listOf("node_modules", "build", "dist", "__pycache__")) continue
                // Skip very large files (>15MB) — avoid OOM in VFS
                if (size > 15L * 1024L * 1024L) continue

                val relPath = if (basePath.isEmpty()) name else "$basePath/$name"

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    // Recurse into subdirectory
                    results.addAll(scanFolder(treeUri, docId, relPath))
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    results.add(Pair(fileUri, relPath))
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("IW_FOLDER", "Scan error at $basePath: ${e.message}")
    }
    return results
}

fun MainActivity.refreshBtn() {
    if (adLoading) return  // Ad is loading/showing — do not override button state
    val ready = selectedUri != null
    btnHost.isEnabled = ready
    btnHost.background = drawRoundRect(
        if (ready) 0xFF2563EB.toInt() else 0xFFD1D5DB.toInt(), 14.dp().toFloat()
    )
}

fun MainActivity.loadInterstitialAd() {
    val req = AdRequest.Builder().build()
    InterstitialAd.load(this, AD_UNIT_ID, req, object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
            this@loadInterstitialAd.interstitialAd = ad
            this@loadInterstitialAd.adLoaded = true
            // Host after ad is dismissed
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    this@loadInterstitialAd.interstitialAd = null
                    this@loadInterstitialAd.adLoaded = false
                    this@loadInterstitialAd.adLoading = false  // refreshBtn can now update button state
                    this@loadInterstitialAd.loadInterstitialAd()
                    if (this@loadInterstitialAd.isBtnHostReady) {
                        this@loadInterstitialAd.btnHost.isEnabled = true
                    }
                    this@loadInterstitialAd.startGracePeriod()
                    this@loadInterstitialAd.startHosting()
                }
                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    this@loadInterstitialAd.interstitialAd = null
                    this@loadInterstitialAd.adLoaded = false
                    this@loadInterstitialAd.adLoading = false
                    this@loadInterstitialAd.loadInterstitialAd()
                    if (this@loadInterstitialAd.isBtnHostReady) {
                        this@loadInterstitialAd.btnHost.text = "Start Hosting"
                        this@loadInterstitialAd.btnHost.isEnabled = true
                        this@loadInterstitialAd.btnHost.background = this@loadInterstitialAd.drawRoundRect(0xFF2563EB.toInt(), 14.dp().toFloat())
                    }
                    this@loadInterstitialAd.startHosting()
                }
            }
        }
        override fun onAdFailedToLoad(e: LoadAdError) {
            this@loadInterstitialAd.interstitialAd = null
            this@loadInterstitialAd.adLoaded = false
        }
    })
}

fun MainActivity.showAdThenHost() {
    adLoading = true  // refreshBtn will not override until ad flow completes
    val ad = interstitialAd
    if (ad != null && adLoaded) {
        if (isBtnHostReady) {
            btnHost.text = "Loading ad..."
            btnHost.isEnabled = false
            btnHost.background = drawRoundRect(0xFF6B7280.toInt(), 14.dp().toFloat())
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val currentAd = this@showAdThenHost.interstitialAd
            if (currentAd != null && this@showAdThenHost.adLoaded) {
                currentAd.show(this@showAdThenHost)
                // adLoading will be set to false when ad is dismissed
            } else {
                this@showAdThenHost.adLoading = false
                if (this@showAdThenHost.isBtnHostReady) {
                    this@showAdThenHost.btnHost.text = "Start Hosting"
                    this@showAdThenHost.btnHost.isEnabled = true
                    this@showAdThenHost.btnHost.background = this@showAdThenHost.drawRoundRect(0xFF2563EB.toInt(), 14.dp().toFloat())
                }
                this@showAdThenHost.startHosting()
                this@showAdThenHost.loadInterstitialAd()
            }
        }, 1500)
    } else {
        // Ad not loaded yet — wait up to 8 seconds
        if (isBtnHostReady) {
            btnHost.text = "Loading ad..."
            btnHost.isEnabled = false
            btnHost.background = drawRoundRect(0xFF6B7280.toInt(), 14.dp().toFloat())
        }
        loadInterstitialAd()
        var waited = 0
        val checkHandler = Handler(Looper.getMainLooper())
        val checkRunnable = object : Runnable {
            override fun run() {
                waited += 500
                if (this@showAdThenHost.adLoaded && this@showAdThenHost.interstitialAd != null) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        val currentAd = this@showAdThenHost.interstitialAd
                        if (currentAd != null && this@showAdThenHost.adLoaded) {
                            currentAd.show(this@showAdThenHost)
                        } else {
                            this@showAdThenHost.adLoading = false
                            if (this@showAdThenHost.isBtnHostReady) {
                                this@showAdThenHost.btnHost.text = "Start Hosting"
                                this@showAdThenHost.btnHost.isEnabled = true
                                this@showAdThenHost.btnHost.background = this@showAdThenHost.drawRoundRect(0xFF2563EB.toInt(), 14.dp().toFloat())
                            }
                            this@showAdThenHost.startHosting()
                        }
                    }, 1500)
                } else if (waited >= 8000) {
                    this@showAdThenHost.adLoading = false
                    if (this@showAdThenHost.isBtnHostReady) {
                        this@showAdThenHost.btnHost.text = "Start Hosting"
                        this@showAdThenHost.btnHost.isEnabled = true
                        this@showAdThenHost.btnHost.background = this@showAdThenHost.drawRoundRect(0xFF2563EB.toInt(), 14.dp().toFloat())
                    }
                    this@showAdThenHost.startHosting()
                } else {
                    checkHandler.postDelayed(this, 500)
                }
            }
        }
        checkHandler.postDelayed(checkRunnable, 500)
    }
}

fun MainActivity.startGracePeriod() {
    // Cancel any previous grace timer
    adGraceRunnable?.let { adGraceHandler.removeCallbacks(it) }
    // Save grace start time — timestamp-based, survives app restart
    val graceStartTime = System.currentTimeMillis()
    prefs.edit()
        .putBoolean("ad_grace_active", true)
        .putLong("ad_grace_start_time", graceStartTime)
        .apply()
    // Set false after exactly 2 minutes — MainThread handler, background-kill safe
    adGraceRunnable = Runnable {
        prefs.edit()
            .putBoolean("ad_grace_active", false)
            .remove("ad_grace_start_time")
            .apply()
        adGraceRunnable = null
    }.also { adGraceHandler.postDelayed(it, AD_GRACE_MS) }
}

fun MainActivity.startHosting() {
    val uri = selectedUri ?: run { toast("Please select an .html file first"); return }
    try {
        val htmlName = getFileName(uri)

        // ── Populate Virtual File System (RAM) ──────────────
        // Clear previous VFS contents first
        VirtualFS.clear()
        // Clean old cache files (migration from previous version)
        try { File(cacheDir, "iw_serve_dir").deleteRecursively() } catch (_: Exception) {}

        // Load main HTML file into VFS
        val htmlBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: run { toast("Cannot read HTML file"); return }
        VirtualFS.put(htmlName, htmlBytes)

        // Load extra CSS/JS/image files into VFS with relative paths
        // Recompute relative paths (main URI may have changed)
        extraRelativePaths.clear()
        extraFileUris.forEach { extraUri ->
            try {
                val relPath = getRelativePath(extraUri, uri) ?: getFileName(extraUri)
                extraRelativePaths[extraUri] = relPath
                val extraBytes = contentResolver.openInputStream(extraUri)?.use { it.readBytes() }
                if (extraBytes != null && extraBytes.isNotEmpty()) {
                    VirtualFS.put(relPath, extraBytes)
                }
            } catch (_: Exception) {}
        }

        android.util.Log.i("IW_VFS", "VFS ready: ${VirtualFS.size()} files, ${VirtualFS.totalSize()} bytes")
        android.util.Log.i("IW_VFS", "Paths: ${VirtualFS.paths()}")

        isHosting = true
        lastFileModified = System.currentTimeMillis()
        val svcIntent = Intent(this, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_HTML_NAME, htmlName)
            putExtra(ServerService.EXTRA_PORT, getPort())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svcIntent)
        else
            startService(svcIntent)

        // Build URLs
        val localUrl = "http://127.0.0.1:${getPort()}/?__iw_app=1"

        val hotspotIp = getHostIP().let { if (it == "localhost") null else it }
        val bestUrl   = if (hotspotIp != null) "http://$hotspotIp:${getPort()}"
                        else "http://127.0.0.1:${getPort()}"

        // Store hostUrl for title bar display + long-press copy
        hostUrl = bestUrl

        // Server card — show port only (IP is in WebView title bar now)
        tvServerUrl.text = "localhost :${getPort()}"
        cardUrl.visibility = View.VISIBLE
        btnHost.text = "Stop Hosting"
        btnHost.background = drawRoundRect(0xFFDC2626.toInt(), 14.dp().toFloat())
        showNotif(bestUrl)
        // bestUrl already uses localhost fallback — notification shows correct URL

        // Show IP URL in title bar instead of file name
        tvPageTitle.text = hostUrl

        toast("Server started!")

        // Auto-switch to OUTPUT panel and load live preview
        if (currentPanel != 1) switchPanel(1)
        // Small delay so server is ready, then load output
        Handler(Looper.getMainLooper()).postDelayed({
            loadOutput()
        }, 400)

    } catch (e: Exception) {
        isHosting = false
        toast("Failed to start server: ${e.message}")
    }
}

fun MainActivity.stopHosting() {
    // Do NOT cancel grace period — re-host within 2 minutes = no ad
    // Grace is cancelled only when the 2-min timer expires (set in startGracePeriod)
    // Stop the foreground service — this stops the server
    val svcIntent = Intent(this, ServerService::class.java).apply {
        action = ServerService.ACTION_STOP
    }
    startService(svcIntent)
    lastFileModified = 0L
    isHosting  = false
    hostUrl = ""
    cancelNotif()
    // Clear VFS — free RAM (ServerService also clears, but this is a safety net)
    VirtualFS.clear()
    // Clean old cache files (leftover from previous versions)
    try { File(cacheDir, "iw_serve_dir").deleteRecursively() } catch (_: Exception) {}
    // Clear WebView cache as well
    if (isWebOutputReady) webOutput.clearCache(true)
    runOnUiThread {
        if (this@stopHosting.isCardUrlReady)  this@stopHosting.cardUrl.visibility = View.GONE
        if (this@stopHosting.isBtnHostReady) {
            this@stopHosting.btnHost.text = "Start Hosting"
            this@stopHosting.refreshBtn()
        }
        if (this@stopHosting.isWebOutputReady)
            this@stopHosting.webOutput.loadData(this@stopHosting.htmlPlaceholder(), "text/html", "UTF-8")
        if (this@stopHosting.isTvPageTitleReady)
            this@stopHosting.tvPageTitle.text = "Live Preview"
    }
}

fun MainActivity.startViewerAutoTry() {
    if (isHosting) return          // Skip on host device
    if (viewerAutoTryRunning) return // Already running
    if (viewerVerifiedIP != null) return // Already found
    if (!isWifiOn()) return         // WiFi off — skip

    viewerAutoTryRunning = true
    var attempt = 0
    val maxAttempts = 5

    val tryRunnable = object : Runnable {
        override fun run() {
            if (this@startViewerAutoTry.viewerVerifiedIP != null || !this@startViewerAutoTry.viewerAutoTryRunning || attempt >= maxAttempts) {
                this@startViewerAutoTry.viewerAutoTryRunning = false
                // Max attempts reached without finding host — show neutral message
                if (attempt >= maxAttempts && this@startViewerAutoTry.viewerVerifiedIP == null && this@startViewerAutoTry.isTvConnStatusReady) {
                    this@startViewerAutoTry.tvConnStatus.text = "No host found yet — tap View Live Output"
                    this@startViewerAutoTry.tvConnStatus.setTextColor(0xFF9CA3AF.toInt())
                }
                return
            }
            attempt++
            this@startViewerAutoTry.loadOutput()
            this@startViewerAutoTry.viewerTryHandler.postDelayed(this, 800)
        }
    }
    // First attempt after 500ms (allow WiFi to settle)
    viewerTryHandler.postDelayed(tryRunnable, 500)
}

fun MainActivity.registerWifiChangeListener() {
    try { wifiChangeReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
    wifiChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
            if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                val info = intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (info?.isConnected == true) {
                    // New WiFi connected — reset verified IP and restart auto-try
                    this@registerWifiChangeListener.viewerVerifiedIP = null
                    this@registerWifiChangeListener.viewerAutoTryRunning = false
                    this@registerWifiChangeListener.startViewerAutoTry()
                }
            }
        }
    }
    val filter = android.content.IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
    registerReceiver(wifiChangeReceiver, filter)
}

fun MainActivity.loadOutput() {
    val url: String

    if (isHosting) {
        // This device is the host — localhost works directly
        url = "http://127.0.0.1:${getPort()}/?__iw_app=1"
    } else {
        // This device is a viewer — gateway = host IP
        if (!isWifiOn()) {
            webOutput.loadData(
                htmlError("Please turn on WiFi and connect to the hosting device's hotspot."),
                "text/html", "UTF-8"
            )
            return
        }
        val gw = getGatewayIP()
        if (gw == null) {
            webOutput.loadData(
                htmlError("Could not find the hosting device.<br>Make sure you are connected to the host's Mobile Hotspot."),
                "text/html", "UTF-8"
            )
            return
        }
        url = "http://$gw:${getPort()}/?__iw_app=1"
    }

    tvPageTitle.text = if (isHosting && hostUrl.isNotEmpty()) hostUrl else "Loading..."
    webOutput.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
    webOutput.loadUrl(url)
}

fun MainActivity.enterFullscreen() {
    val url = webOutput.url
    if (!url.isNullOrEmpty() && url.startsWith("http")) webFs.loadUrl(url)
    else webFs.loadData(htmlPlaceholder(), "text/html", "UTF-8")
    if (isTvFsTitleReady && isTvPageTitleReady)
        tvFsTitle.text = tvPageTitle.text
    overlayFs.visibility = View.VISIBLE
    consoleVisible = false
    // adjustResize — keyboard pushes console panel up automatically
    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
}

fun MainActivity.exitFullscreen() {
    overlayFs.visibility = View.GONE
    consoleVisible = false
    if (isFsConsolePanelReady) fsConsolePanel.visibility = View.GONE
    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
}

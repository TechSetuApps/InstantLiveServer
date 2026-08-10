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


// ================================================================
//  InstantLive Server — Live HTML Preview Sharing App
//  NanoHTTPD v2.3.1 | BSD-3-Clause License
//  Binds to 0.0.0.0 — accessible via localhost AND network IP
// ================================================================

// ── Top-level constants (accessible from all split files in same package) ──
internal val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
internal val WRAP  = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
internal const val CHANNEL_ID  = "instantweb_ch1"
internal const val NOTIF_ID    = 1001
internal const val PORT        = 7090
internal const val ACTION_STOP = "app.techsetuapps.instantweb.ACTION_STOP"
internal const val PERM_REQ    = 101
internal const val AD_UNIT_ID  = "ca-app-pub-3940256099942544/1033178617"  // Google test ad unit ID for open-source build
internal const val AD_GRACE_MS = 2 * 60 * 1000L
const val UPDATE_JSON_URL      = "https://raw.githubusercontent.com/TechSetuApps/instantweb-update/main/instantweb_update.json"
const val PREF_UPDATE_AVAILABLE  = "update_available"
const val PREF_UPDATE_LINK       = "update_link"
const val PREF_UPDATE_FOUND_TIME = "update_found_time"
const val OFFLINE_GRACE_DAYS     = 7L

class MainActivity : AppCompatActivity() {


    // ── State ────────────────────────────────────────────────────
    internal var currentPanel    = 0
    internal lateinit var prefs: SharedPreferences
    internal var extraFileUris   = mutableListOf<android.net.Uri>()
    internal lateinit var tvExtraFiles: TextView
    internal var isFirstResume = true

    // ── Folder Mode ─────────────────────────────────────────────
    // "Select a Folder" feature — auto-host all files in a folder
    internal var selectedFolderUri: Uri? = null
    internal var isFolderMode = false
    internal var folderFileCount = 0
    internal lateinit var tvFolderInfo: TextView

    // Virtual folder — relative path map for each extra file URI
    // Computed when files are picked, used when populating VFS
    internal var extraRelativePaths = mutableMapOf<android.net.Uri, String>()

    // Dynamic port — reads from saved prefs
    internal fun getPort(): Int = prefs.getInt("host_port", PORT)

    // ── AdMob ────────────────────────────────────────────────────
    internal var interstitialAd: InterstitialAd? = null
    internal var adLoaded = false
    internal var adLoading = false  // TRUE while ad is loading/showing — prevents refreshBtn override
    // Grace period handler — runs on main thread, safe from background kills
    internal val adGraceHandler = Handler(Looper.getMainLooper())
    internal var adGraceRunnable: Runnable? = null

    internal var drawerOverlay: android.widget.FrameLayout? = null
    internal var fullScreenOverlay: android.widget.FrameLayout? = null
    internal var fullScreenCancelable: Boolean = true
    internal var fullScreenOverlayTag: String? = null  // "update", "first_launch", "legal", "help", "support", "settings"

    // ── Viewer auto-try ──────────────────────────────────────────
    // On app open or WiFi change, try 4-5 times in background
    // Once page loads, show verified IP and stop
    internal var viewerAutoTryRunning = false
    internal var viewerVerifiedIP: String? = null  // Set after WebView genuinely loads the page
    internal val viewerTryHandler = Handler(Looper.getMainLooper())
    internal var wifiChangeReceiver: android.content.BroadcastReceiver? = null

    // Server runs in ServerService (foreground service)
    internal var serverBroadcastReceiver: android.content.BroadcastReceiver? = null
    internal var selectedUri: Uri? = null
    internal var isHosting       = false
    internal var hostUrl = ""   // Best URL when hosting (hotspot IP or localhost)
    internal var serverReachable  = false
    internal var lastFileModified = 0L   // For live reload detection

    // ── UI — Host Panel ──────────────────────────────────────────
    internal lateinit var tvHotspotStatus: TextView
    internal lateinit var dotHotspot: View
    internal lateinit var tvSelectedFile: TextView
    internal lateinit var btnHost: TextView
    internal lateinit var cardUrl: LinearLayout
    internal lateinit var tvServerUrl: TextView

    // ── UI — Output Panel ────────────────────────────────────────
    internal lateinit var tvWifiStatus: TextView
    internal lateinit var dotWifi: View
    internal lateinit var tvConnStatus: TextView
    internal lateinit var dotConn: View
    internal lateinit var tvPageTitle: TextView    // Page title bar (read-only)
    internal lateinit var webOutput: WebView

    // ── Fullscreen ───────────────────────────────────────────────
    internal lateinit var overlayFs: FrameLayout
    internal lateinit var webFs: WebView
    internal lateinit var tvFsTitle: TextView
    internal lateinit var consolePanel: LinearLayout
    internal lateinit var tvConsoleLog: TextView
    internal lateinit var tvConsoleToggle: TextView
    internal val consoleLogs = mutableListOf<String>()
    internal var consoleVisible = false
    // Fullscreen console
    internal lateinit var fsConsolePanel: LinearLayout
    internal lateinit var tvFsConsoleLog: TextView
    internal lateinit var etFsConsoleInput: android.widget.EditText
    internal var fsConsoleVisible = false

    // ── Tabs ─────────────────────────────────────────────────────
    internal lateinit var tabHost: TextView
    internal lateinit var tabOutput: TextView
    internal lateinit var tabIndicator: View
    internal lateinit var panelHost: View
    internal lateinit var panelOutput: View

    internal val density get() = resources.displayMetrics.density
    internal fun Int.dp() = (this * density + 0.5f).toInt()

    internal val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onFilePicked(it) }
    }

    // Folder picker — Select a Folder feature
    internal val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onFolderPicked(it) }
    }

    // Multiple files picker — manual mode (secondary option)
    // Virtual Folder: compute relative paths so that
    // HTML references like src="css/style.css" work correctly
    internal val multiFilePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        // Filter out HTML files — only extra files needed
        val filtered = uris.filter { uri ->
            val name = getFileName(uri).lowercase()
            !name.endsWith(".html") && !name.endsWith(".htm")
        }
        if (filtered.isEmpty()) {
            toast("Please select HTML files using the 'Select .html File' button above")
            return@registerForActivityResult
        }
        extraFileUris.clear()
        extraFileUris.addAll(filtered)
        // Folder mode exit — manual file picking
        isFolderMode = false
        selectedFolderUri = null
        if (::tvFolderInfo.isInitialized) tvFolderInfo.visibility = View.GONE
        // Compute relative paths for each extra file
        extraRelativePaths.clear()
        val mainUri = selectedUri
        if (mainUri != null) {
            for (extraUri in filtered) {
                val rel = getRelativePath(extraUri, mainUri)
                if (rel != null) extraRelativePaths[extraUri] = rel
            }
        }
        // Display — show relative path if available, otherwise filename only
        val names = filtered.map { uri ->
            extraRelativePaths[uri] ?: getFileName(uri)
        }.joinToString(", ")
        if (::tvExtraFiles.isInitialized) {
            tvExtraFiles.text = "Added: $names"
            tvExtraFiles.visibility = View.VISIBLE
        }
        // Auto-host when more files are added (if not already hosting)
        if (selectedUri != null && !isHosting) {
            refreshBtn()
            btnHost.performClick()
        }
    }

    // ================================================================
    //  LIFECYCLE
    // ================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setupWindow()
        createNotifChannel()
        registerServerReceiver()
        prefs = getSharedPreferences("iw_prefs", MODE_PRIVATE)
        buildUI()
        askPermissions()
        startPolling()
        // Initialize AdMob on background thread
        MobileAds.initialize(this) { loadInterstitialAd() }
        // Clear stale cache (leftover from crashes/force stops)
        try { File(cacheDir, "iw_serve_dir").deleteRecursively() } catch (_: Exception) {}
        if (!prefs.getBoolean("terms_accepted", false)) {
            showFirstLaunchDialog()
        } else {
            // Terms already accepted — start viewer auto-try (4-5 background attempts)
            if (!isHosting) startViewerAutoTry()
        }
        // Check for updates in background on every app open
        checkForUpdate()
        // Detect WiFi changes — auto-try when connecting to a new hotspot
        registerWifiChangeListener()
        if (intent?.action == ACTION_STOP) stopHosting()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == ACTION_STOP) stopHosting()
    }

    override fun onResume() {
        super.onResume()
        // Skip first resume (onCreate already handles it)
        // Only re-check when user returns to app from background
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        // Sync hosting state — verify server is still running when returning from background
        syncHostingState()
        if (::prefs.isInitialized && prefs.getBoolean("terms_accepted", false)) {
            checkForUpdate()
            // Viewer: when returning from background, start auto-try
            if (!isHosting) startViewerAutoTry()
        }
    }

    override fun onDestroy() {
        stopHosting()
        // Cancel only the grace runnable — SharedPrefs grace data is retained
        adGraceRunnable?.let { adGraceHandler.removeCallbacks(it) }
        // Note: ad_grace_active persists in SharedPrefs — 2-min check survives app restart
        // Viewer auto-try cleanup
        viewerTryHandler.removeCallbacksAndMessages(null)
        try { wifiChangeReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        wifiChangeReceiver = null
        val rcv = serverBroadcastReceiver
        if (rcv != null) {
            try { unregisterReceiver(rcv) } catch (_: Exception) {}
            serverBroadcastReceiver = null
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            fullScreenOverlay != null && fullScreenCancelable -> closeFullScreenOverlay()
            drawerOverlay != null -> closeRightDrawer()
            ::overlayFs.isInitialized && overlayFs.visibility == View.VISIBLE -> exitFullscreen()
            else -> super.onBackPressed()
        }
    }

    // ── Safe accessors for lateinit vars (extension functions can't access backing field) ──
    internal val isTvConnStatusReady: Boolean get() = ::tvConnStatus.isInitialized
    internal val isDotConnReady: Boolean get() = ::dotConn.isInitialized
    internal val isBtnHostReady: Boolean get() = ::btnHost.isInitialized
    internal val isCardUrlReady: Boolean get() = ::cardUrl.isInitialized
    internal val isTvPageTitleReady: Boolean get() = ::tvPageTitle.isInitialized
    internal val isTvFolderInfoReady: Boolean get() = ::tvFolderInfo.isInitialized
    internal val isTvExtraFilesReady: Boolean get() = ::tvExtraFiles.isInitialized
    internal val isTvFsTitleReady: Boolean get() = ::tvFsTitle.isInitialized
    internal val isWebOutputReady: Boolean get() = ::webOutput.isInitialized
    internal val isOverlayFsReady: Boolean get() = ::overlayFs.isInitialized
    internal val isFsConsolePanelReady: Boolean get() = ::fsConsolePanel.isInitialized
    internal val isConsolePanelReady: Boolean get() = ::consolePanel.isInitialized
    internal val isTvConsoleLogReady: Boolean get() = ::tvConsoleLog.isInitialized
    internal val isTvHotspotStatusReady: Boolean get() = ::tvHotspotStatus.isInitialized
    internal val isTvWifiStatusReady: Boolean get() = ::tvWifiStatus.isInitialized
    internal val isWebFsReady: Boolean get() = ::webFs.isInitialized
}

// ================================================================
//  SERVER RECEIVER
// ================================================================

fun MainActivity.registerServerReceiver() {
    serverBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra(ServerService.EXTRA_RUNNING, false) ?: false
            this@registerServerReceiver.isHosting = running
            if (!running) this@registerServerReceiver.hostUrl = ""
            runOnUiThread {
                if (this@registerServerReceiver.isTvConnStatusReady) {
                    this@registerServerReceiver.tvConnStatus.text = if (running)
                        "127.0.0.1:${this@registerServerReceiver.getPort()} — Server running"
                    else
                        "Server stopped"
                    this@registerServerReceiver.tvConnStatus.setTextColor(
                        if (running) 0xFF16A34A.toInt() else 0xFF9CA3AF.toInt()
                    )
                    if (this@registerServerReceiver.isDotConnReady)
                        this@registerServerReceiver.dotConn.background = this@registerServerReceiver.drawCircle(
                            if (running) 0xFF16A34A.toInt() else 0xFFD1D5DB.toInt()
                        )
                }
                // Sync UI with server state (fixes notification-bar stop not updating UI)
                if (!running) {
                    if (this@registerServerReceiver.isBtnHostReady) {
                        this@registerServerReceiver.btnHost.text = "Start Hosting"
                        this@registerServerReceiver.refreshBtn()
                    }
                    if (this@registerServerReceiver.isCardUrlReady) this@registerServerReceiver.cardUrl.visibility = View.GONE
                    if (this@registerServerReceiver.isTvPageTitleReady) this@registerServerReceiver.tvPageTitle.text = "Live Preview"
                } else {
                    if (this@registerServerReceiver.isBtnHostReady) {
                        this@registerServerReceiver.btnHost.text = "Stop Hosting"
                        this@registerServerReceiver.btnHost.background = this@registerServerReceiver.drawRoundRect(0xFFDC2626.toInt(), 14.dp().toFloat())
                        this@registerServerReceiver.btnHost.isEnabled = true
                    }
                    if (this@registerServerReceiver.isCardUrlReady) this@registerServerReceiver.cardUrl.visibility = View.VISIBLE
                }
            }
        }
    }
    val filter = android.content.IntentFilter(ServerService.BROADCAST_STATUS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        registerReceiver(serverBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
    else
        registerReceiver(serverBroadcastReceiver, filter)
}

// ================================================================
//  WINDOW SETUP
// ================================================================

fun MainActivity.setupWindow() {
    requestWindowFeature(Window.FEATURE_NO_TITLE)

    // Edge-to-edge — prevents system from adding padding
    // Ensures consistent behavior on Android 14 and 16
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
}

// ================================================================
//  NOTIFICATION
// ================================================================

fun MainActivity.createNotifChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(CHANNEL_ID, "InstantLive Server Hosting",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Active while a file is being hosted"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

fun MainActivity.showNotif(url: String) {
    val stopPi = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).apply {
            action = ACTION_STOP; flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val openPi = PendingIntent.getActivity(this, 1,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notif = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.iw_launcher)
        .setContentTitle("InstantLive Server — Hosting Active")
        .setContentText(url)
        .setOngoing(true).setSilent(true)
        .setContentIntent(openPi)
        .addAction(android.R.drawable.ic_delete, "Stop Hosting", stopPi)
        .build()
    val ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPerm(Manifest.permission.POST_NOTIFICATIONS)
    if (ok) NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
}

fun MainActivity.cancelNotif() = NotificationManagerCompat.from(this).cancel(NOTIF_ID)

// ================================================================
//  PERMISSIONS
// ================================================================

fun MainActivity.askPermissions() {
    val needed = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (!hasPerm(Manifest.permission.POST_NOTIFICATIONS))
            needed += Manifest.permission.POST_NOTIFICATIONS
        if (!hasPerm(Manifest.permission.READ_MEDIA_IMAGES))
            needed += Manifest.permission.READ_MEDIA_IMAGES
    } else {
        if (!hasPerm(Manifest.permission.READ_EXTERNAL_STORAGE))
            needed += Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (needed.isNotEmpty())
        ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERM_REQ)
}

fun MainActivity.hasPerm(p: String) =
    ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

// ================================================================
//  BUILD UI
// ================================================================

fun MainActivity.buildUI() {
    android.webkit.WebStorage.getInstance().deleteAllData()
    val root = FrameLayout(this).apply { setBackgroundColor(0xFFF5F5F5.toInt()) }
    val main = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        // Nav bar bottom padding only — status bar handled by AppBar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }
    main.addView(buildAppBar())
    main.addView(buildTabBar())

    val frame = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
    }
    panelHost   = buildHostPanel()
    panelOutput = buildOutputPanel()
    panelOutput.visibility = View.GONE
    frame.addView(panelHost,   FrameLayout.LayoutParams(MATCH, MATCH))
    frame.addView(panelOutput, FrameLayout.LayoutParams(MATCH, MATCH))
    main.addView(frame)
    root.addView(main)

    overlayFs = buildFsOverlay()
    root.addView(overlayFs)
    overlayFs.visibility = View.GONE
    setContentView(root)
}

// ================================================================
//  SYNC HOSTING STATE
// ================================================================

fun MainActivity.syncHostingState() {
    if (!isHosting) return  // Already not hosting, nothing to sync
    Thread {
        val ok = try {
            Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", getPort()), 1500)
                true
            }
        } catch (_: Exception) { false }
        if (!ok && isHosting) {
            // Server died but UI still shows "Stop Hosting" — fix it
            runOnUiThread {
                this@syncHostingState.isHosting = false
                this@syncHostingState.hostUrl = ""
                if (this@syncHostingState.isBtnHostReady) {
                    this@syncHostingState.btnHost.text = "Start Hosting"
                    this@syncHostingState.refreshBtn()
                }
                if (this@syncHostingState.isCardUrlReady) this@syncHostingState.cardUrl.visibility = View.GONE
                if (this@syncHostingState.isTvPageTitleReady) this@syncHostingState.tvPageTitle.text = "Live Preview"
                if (this@syncHostingState.isTvConnStatusReady) {
                    this@syncHostingState.tvConnStatus.text = "Server stopped"
                    this@syncHostingState.tvConnStatus.setTextColor(0xFF9CA3AF.toInt())
                }
                if (this@syncHostingState.isDotConnReady)
                    this@syncHostingState.dotConn.background = this@syncHostingState.drawCircle(0xFFD1D5DB.toInt())
                this@syncHostingState.cancelNotif()
            }
        }
    }.start()
}

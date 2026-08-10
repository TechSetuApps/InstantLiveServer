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
//  MainActivityUI — UI Building — all build functions, panels, tabs, console, fullscreen overlay
// ================================================================

fun MainActivity.buildAppBar() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    setBackgroundColor(Color.WHITE)
    setPadding(20.dp(), 0, 20.dp(), 0); elevation = 4.dp().toFloat()

    // Status bar height set dynamically — works on all Android versions
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val statusBarH = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(20.dp(), statusBarH, 20.dp(), 0)
        v.minimumHeight = statusBarH + 62.dp()
        insets
    }

    addView(View(this@buildAppBar).apply {
        val s = 42.dp()
        layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 14.dp() }
        background = drawLogo()
    })
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        addView(TextView(context).apply {
            text = "InstantLive Server"; setTextColor(0xFF111827.toInt())
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        })
        // Subtitle removed
    })
    // Hamburger menu icon — triggers bottom sheet menu
    addView(View(this@buildAppBar).apply {
        layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
        background = drawIconHamburger(0xFF6B7280.toInt())
        setOnClickListener { showRightDrawer() }
    })
}

fun MainActivity.buildTabBar() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    tabHost = TextView(this@buildTabBar).apply {
        text = "HOST MODE"; gravity = Gravity.CENTER
        textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF334155.toInt()); setPadding(0, 16.dp(), 0, 16.dp())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        // Subtle active background
        background = drawRoundRect(0x0A111111.toInt(), 0f)  // Subtle neutral tint
        setOnClickListener { switchPanel(0) }
    }
    tabOutput = TextView(this@buildTabBar).apply {
        text = "OUTPUT"; gravity = Gravity.CENTER
        textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF9CA3AF.toInt()); setPadding(0, 16.dp(), 0, 16.dp())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        setOnClickListener { switchPanel(1) }
    }
    row.addView(tabHost); row.addView(tabOutput); addView(row)
    val sw = resources.displayMetrics.widthPixels
    tabIndicator = View(this@buildTabBar).apply {
        setBackgroundColor(0xFF2563EB.toInt())
        layoutParams = LinearLayout.LayoutParams(sw / 2, 3.dp())
    }
    addView(tabIndicator)
    addView(View(this@buildTabBar).apply {
        setBackgroundColor(0xFFE5E7EB.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
    })
}

fun MainActivity.buildHostPanel(): ScrollView {
    val scroll  = ScrollView(this)
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp(), 20.dp(), 16.dp(), 50.dp())
    }

    content.addView(mkSectionLabel("Step 1 — Mobile Hotspot"))

    val hsCard = mkCard()
    val hsRow  = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    hsRow.addView(mkIcon(28.dp(), drawHotspot(0xFFFF9500.toInt())).apply {
        (layoutParams as LinearLayout.LayoutParams).rightMargin = 14.dp()
    })
    val hsCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    hsCol.addView(TextView(this).apply {
        text = "Mobile Hotspot"
        setTextColor(0xFF111827.toInt()); textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD
    })
    tvHotspotStatus = TextView(this).apply {
        text = "Checking..."; setTextColor(0xFF9CA3AF.toInt()); textSize = 12f
    }
    hsCol.addView(tvHotspotStatus)
    dotHotspot = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(14.dp(), 14.dp())
        background = drawCircle(0xFFD1D5DB.toInt())
    }
    hsRow.addView(hsCol); hsRow.addView(dotHotspot)
    hsCard.addView(hsRow); content.addView(hsCard)

    content.addView(mkRowBtn("Open Hotspot Settings", drawHotspot(0xFF007AFF.toInt())) {
        try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
        catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }
    })

    content.addView(mkSectionLabel("Step 2 — Select HTML File"))
    content.addView(mkOutlineBtn("Select .html File", drawFile()) { filePicker.launch("*/*") })
    tvSelectedFile = TextView(this).apply {
        text = "No file selected"
        setTextColor(0xFF9CA3AF.toInt())
        textSize = 12f
        setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
        // Subtle rounded background when file is selected
        background = drawRoundRect(0x00000000.toInt(), 8.dp().toFloat())
    }
    content.addView(tvSelectedFile)

    // ── Select a Folder — auto-host all files ────────────────
    content.addView(mkOutlineBtn("Select a Folder", drawFolder()) {
        folderPicker.launch(null)
    })
    tvFolderInfo = TextView(this).apply {
        text = ""
        setTextColor(0xFF059669.toInt())
        textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        visibility = View.GONE
        setPadding(8.dp(), 6.dp(), 8.dp(), 8.dp())
        // Green background pill when folder is selected
        background = drawRoundRect(0xFFECFDF5.toInt(), 8.dp().toFloat())
    }
    content.addView(tvFolderInfo)

    // Manual file picker — secondary option (styled as a subtle link)
    content.addView(TextView(this).apply {
        text = "or add files manually"
        setTextColor(0xFF007AFF.toInt())
        textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        setPadding(4.dp(), 6.dp(), 4.dp(), 2.dp())
        setOnClickListener { multiFilePicker.launch(arrayOf("*/*")) }
        isClickable = true; isFocusable = true
        // Subtle underline effect via paint flags
        paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
    })
    tvExtraFiles = TextView(this).apply {
        text = ""
        setTextColor(0xFF059669.toInt())
        textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        visibility = View.GONE
        setPadding(8.dp(), 6.dp(), 8.dp(), 8.dp())
        background = drawRoundRect(0xFFECFDF5.toInt(), 8.dp().toFloat())
    }
    content.addView(tvExtraFiles)

    content.addView(mkSectionLabel("Step 3 — Start Hosting · Watch Ad"))

    // Info box — styled with icon and accent
    content.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
        background  = drawRoundRect(0xFFF8FAFC.toInt(), 12.dp().toFloat())
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 12.dp() }
        // Info icon — properly centered circle with "i"
        addView(View(context).apply {
            val s = 20.dp()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 10.dp() }
            background = drawInfoIcon(0xFF475569.toInt())
        })
        addView(TextView(context).apply {
            text = "Hotspot is optional for hosting. File will always be accessible at localhost:${getPort()} on this device. Turn on Hotspot to share with other devices.\n\nA short ad will play before hosting starts. This helps support free development of InstantLive Server. Once an ad is watched, you can re-host within 2 minutes without seeing another ad."
            setTextColor(0xFF475569.toInt()); textSize = 11.5f; setLineSpacing(0f, 1.4f)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
    })

    btnHost = TextView(this).apply {
        text = "Start Hosting"; gravity = Gravity.CENTER
        textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE); isEnabled = false
        background = drawRoundRect(0xFFD1D5DB.toInt(), 14.dp().toFloat())
        setPadding(0, 18.dp(), 0, 18.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        // Touch feedback for hosting button
        setOnTouchListener { v, event ->
            if (!v.isEnabled) return@setOnTouchListener false
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Slightly darken the current color
                    alpha = 0.85f
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    alpha = 1f
                }
            }
            false
        }
        setOnClickListener {
            if (isHosting) {
                stopHosting()
            } else {
                // Grace period check — verify via timestamp, valid for 2 minutes only
                val graceActive = prefs.getBoolean("ad_grace_active", false)
                val graceStart = prefs.getLong("ad_grace_start_time", 0L)
                val elapsed = System.currentTimeMillis() - graceStart
                val graceValid = graceActive && graceStart > 0L && elapsed < AD_GRACE_MS
                if (graceValid) {
                    startHosting()
                } else {
                    // Grace expired or never set — clean stale data
                    if (!graceValid && graceActive) {
                        prefs.edit().putBoolean("ad_grace_active", false).remove("ad_grace_start_time").apply()
                    }
                    showAdThenHost()
                }
            }
        }
    }
    content.addView(btnHost)

    cardUrl = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background  = drawRoundRect(0xFFECFDF5.toInt(), 14.dp().toFloat())
        setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
        visibility  = View.GONE
        elevation   = 3.dp().toFloat()
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = 14.dp() }
    }
    cardUrl.addView(LinearLayout(this).apply {
        // Green accent line on left + "HOSTING ACTIVE" badge
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 8.dp() }
        addView(View(context).apply {
            // Green accent bar (4dp wide, 18dp tall)
            setBackgroundColor(0xFF059669.toInt())
            layoutParams = LinearLayout.LayoutParams(4.dp(), 18.dp()).apply { rightMargin = 10.dp() }
        })
        addView(TextView(context).apply {
            text = "HOSTING ACTIVE"
            setTextColor(0xFF059669.toInt()); textSize = 10f
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
        })
    })
    tvServerUrl = TextView(this).apply {
        setTextColor(0xFF111827.toInt()); textSize = 15f
        typeface = Typeface.MONOSPACE; setPadding(0, 8.dp(), 0, 0)
        setLineSpacing(0f, 1.5f)
    }
    cardUrl.addView(tvServerUrl)
    // Extra info text removed — card is clean now
    content.addView(cardUrl)
    scroll.addView(content)
    return scroll
}

fun MainActivity.buildOutputPanel(): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
    }

    // ── Status + controls ──
    val top = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
        elevation = 3.dp().toFloat()
    }

    // WiFi row
    val wifiRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 8.dp() }
    }
    wifiRow.addView(mkIcon(22.dp(), drawWifi(0xFF007AFF.toInt())).apply {
        (layoutParams as LinearLayout.LayoutParams).rightMargin = 10.dp()
    })
    val wCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    wCol.addView(TextView(this).apply {
        text = "WiFi"
        setTextColor(0xFF111827.toInt()); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
    })
    tvWifiStatus = TextView(this).apply {
        text = "Checking..."; setTextColor(0xFF9CA3AF.toInt()); textSize = 11f
    }
    wCol.addView(tvWifiStatus)
    dotWifi = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(14.dp(), 14.dp())
        background = drawCircle(0xFFD1D5DB.toInt())
    }
    wifiRow.addView(wCol); wifiRow.addView(dotWifi); top.addView(wifiRow)

    // Hosting device row
    val connRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 10.dp() }
    }
    connRow.addView(mkIcon(22.dp(), drawServer()).apply {
        (layoutParams as LinearLayout.LayoutParams).rightMargin = 10.dp()
    })
    val cCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    }
    cCol.addView(TextView(this).apply {
        text = "Server"
        setTextColor(0xFF111827.toInt()); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
    })
    tvConnStatus = TextView(this).apply {
        text = "Not connected"; setTextColor(0xFF9CA3AF.toInt()); textSize = 11f
    }
    cCol.addView(tvConnStatus)
    dotConn = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(14.dp(), 14.dp())
        background = drawCircle(0xFFD1D5DB.toInt())
    }
    connRow.addView(cCol); connRow.addView(dotConn); top.addView(connRow)

    // WiFi settings link — styled as a subtle action row
    top.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 4.dp(), 0, 10.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        isClickable = true; isFocusable = true
        setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        addView(mkIcon(18.dp(), drawWifi(0xFF475569.toInt())).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = 8.dp()
        })
        addView(TextView(this@buildOutputPanel).apply {
            text = "Open WiFi Settings"; setTextColor(0xFF475569.toInt()); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        addView(mkIcon(14.dp(), drawChevronRight(0xFF9CA3AF.toInt())).apply {
            (layoutParams as LinearLayout.LayoutParams).leftMargin = 4.dp()
        })
    })

    // Divider
    top.addView(View(this).apply {
        setBackgroundColor(0xFFE5E7EB.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, 1).apply { bottomMargin = 10.dp() }
    })

    // View button — prominent action
    top.addView(TextView(this).apply {
        text = "View Live Output"; gravity = Gravity.CENTER
        textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        background = drawRoundRect(0xFF2563EB.toInt(), 13.dp().toFloat())
        setPadding(0, 14.dp(), 0, 14.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        // Touch feedback
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { v.alpha = 0.85f }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { v.alpha = 1f }
            }
            false
        }
        setOnClickListener { loadOutput() }
    })
    root.addView(top)

    // ── Title bar — fixed, no margin, right below top panel ──
    val toolbar = LinearLayout(this).apply {
        orientation   = LinearLayout.HORIZONTAL
        gravity       = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF1A1A1A.toInt())
        setPadding(10.dp(), 6.dp(), 8.dp(), 6.dp())
        minimumHeight = 48.dp()
        // No margins — fixed position, no sliding
        layoutParams  = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    // W logo icon (small, rounded square)
    toolbar.addView(View(this).apply {
        val s = 34.dp()
        layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 8.dp() }
        background = drawLogo()
    })

    // Rounded pill title container
    val titlePill = FrameLayout(this).apply {
        layoutParams  = LinearLayout.LayoutParams(0, 36.dp(), 1f)
        background    = drawRoundRect(0xFF2C2C2C.toInt(), 18.dp().toFloat())
    }
    tvPageTitle = TextView(this).apply {
        text         = "Live Preview"
        setTextColor(0xFFE0E0E0.toInt())
        textSize     = 13f
        isSingleLine = true
        ellipsize    = android.text.TextUtils.TruncateAt.END
        gravity      = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 0, 14.dp(), 0)
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        isFocusable  = false
    }
    titlePill.addView(tvPageTitle)
    // Long-press on title text → copy URL popup
    tvPageTitle.isClickable = true
    tvPageTitle.isLongClickable = true
    tvPageTitle.setOnLongClickListener {
        val url = hostUrl.ifEmpty { webOutput.url ?: "" }
        if (url.startsWith("http")) {
            val popup = android.widget.PopupMenu(this@buildOutputPanel, tvPageTitle)
            popup.menu.add("Copy URL")
            popup.setOnMenuItemClickListener { _ ->
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("URL", url))
                toast("URL copied!")
                true
            }
            popup.show()
        }
        true
    }
    toolbar.addView(titlePill)

    // Reload button — small text symbol
    toolbar.addView(TextView(this).apply {
        text     = "↺"
        textSize = 22f
        setTextColor(0xFF9CA3AF.toInt())
        gravity  = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply {
            leftMargin = 4.dp()
        }
        setOnClickListener { consoleLogs.clear(); updateConsoleView(); webOutput.reload() }
    })

    // Fullscreen button
    toolbar.addView(mkIcon(16.dp(), drawExpand()).apply {
        layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp())
        setOnClickListener { enterFullscreen() }
    })

    // Console toggle — hidden, state tracking only (console is fullscreen-only)
    tvConsoleToggle = TextView(this).apply {
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(0, 0)
    }
    toolbar.addView(tvConsoleToggle)

    // 3-dot removed — menu moved to main header

    root.addView(toolbar)

    // 1px separator — no margin
    root.addView(View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(0xFF333333.toInt())
    })

    // ── Console panel (hidden by default, slides in below toolbar) ──
    consolePanel = LinearLayout(this).apply {
        orientation  = LinearLayout.VERTICAL
        visibility   = View.GONE
        setBackgroundColor(0xFF1E1E1E.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, 220.dp()).apply {
            setMargins(10.dp(), 0, 10.dp(), 0)
        }
    }
    // Console header
    val consoleHeader = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity     = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF252525.toInt())
        setPadding(16.dp(), 0, 8.dp(), 0)
        minimumHeight = 36.dp()
    }
    consoleHeader.addView(TextView(this).apply {
        text     = "Console"
        textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFCCCCCC.toInt())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    })
    consoleHeader.addView(TextView(this).apply {
        text     = "Clear"
        textSize = 11f
        setTextColor(0xFF888888.toInt())
        setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
        setOnClickListener { consoleLogs.clear(); updateConsoleView() }
    })
    consolePanel.addView(consoleHeader)
    // Divider
    consolePanel.addView(View(this).apply {
        setBackgroundColor(0xFF333333.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
    })
    // Log scroll area
    val consoleScroll = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
    }
    tvConsoleLog = TextView(this).apply {
        text     = ">> "
        setTextColor(0xFF888888.toInt())
        textSize = 11f; typeface = Typeface.MONOSPACE
        setLineSpacing(0f, 1.5f)
    }
    consoleScroll.addView(tvConsoleLog)
    consolePanel.addView(consoleScroll)
    root.addView(consolePanel)

    // ── WebView — fills all remaining space below toolbar ──
    val webFrame = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        // Subtle border around WebView
        background = object : android.graphics.drawable.Drawable() {
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = android.graphics.Paint.Style.FILL
            }
            val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE5E7EB.toInt()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            override fun draw(c: android.graphics.Canvas) {
                val r = android.graphics.RectF(bounds)
                c.drawRect(r, bgPaint)
                // Top border only — bottom and sides free
                c.drawLine(r.left, r.top, r.right, r.top, borderPaint)
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(f: android.graphics.ColorFilter?) {}
            override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
        }
        clipToOutline = false
    }
    webOutput = makeWebView()
    webOutput.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
    webOutput.loadData(htmlPlaceholder(), "text/html", "UTF-8")
    webFrame.addView(webOutput)
    root.addView(webFrame)

            return root
}

fun MainActivity.toggleConsole() {
    consoleVisible = !consoleVisible
    if (isConsolePanelReady)
        consolePanel.visibility = if (consoleVisible) View.VISIBLE else View.GONE
}

fun MainActivity.toggleFsConsole() {
    if (!isFsConsolePanelReady) return
    consoleVisible = !consoleVisible
    fsConsolePanel.visibility = if (consoleVisible) View.VISIBLE else View.GONE
    if (consoleVisible) updateFsConsoleLog()
}

fun MainActivity.updateFsConsoleLog() {
    if (!isFsConsolePanelReady) return
    val scrollView = (fsConsolePanel.getChildAt(2) as? android.widget.ScrollView) ?: return
    val logView = scrollView.getChildAt(0) as? TextView ?: return
    if (consoleLogs.isEmpty()) {
        logView.text = ">"; logView.setTextColor(0xFF666666.toInt()); return
    }
    val sb = android.text.SpannableStringBuilder()
    consoleLogs.takeLast(200).forEach { line ->
        val col = when {
            line.startsWith("[ERR]")  -> 0xFFEF4444.toInt()
            line.startsWith("[WARN]") -> 0xFFF59E0B.toInt()
            line.startsWith("[RUN]")  -> 0xFF4ADE80.toInt()
            line.startsWith("  =>")   -> 0xFF60A5FA.toInt()
            line.startsWith("[DBG]")  -> 0xFF60A5FA.toInt()
            else                      -> 0xFFE5E7EB.toInt()
        }
        val start = sb.length
        sb.append(line).append("\n")
        sb.setSpan(android.text.style.ForegroundColorSpan(col), start, sb.length-1,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    logView.text = sb
}

fun MainActivity.updateConsoleView() {
    if (!isTvConsoleLogReady) return
    if (consoleLogs.isEmpty()) {
        tvConsoleLog.text = "// No console output"
        tvConsoleLog.setTextColor(0xFF4B5563.toInt())
        return
    }
    // Color-coded spans: ERR=red, WARN=yellow, LOG=white, DBG=blue
    val sb = android.text.SpannableStringBuilder()
    consoleLogs.takeLast(200).forEach { line ->
        val color = when {
            line.startsWith("[ERR]")  -> 0xFFEF4444.toInt()
            line.startsWith("[WARN]") -> 0xFFF59E0B.toInt()
            line.startsWith("[DBG]")  -> 0xFF60A5FA.toInt()
            else                      -> 0xFFE5E7EB.toInt()
        }
        val start = sb.length
        sb.append(line).append("\n")
        sb.setSpan(android.text.style.ForegroundColorSpan(color),
            start, sb.length - 1,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    tvConsoleLog.text = sb
}

fun MainActivity.showDevMenu(anchor: android.view.View) {
    val popup = android.widget.PopupMenu(this, anchor)
    popup.menu.apply {
        add(0, 1, 0, "Fullscreen")
    }
    popup.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            1 -> enterFullscreen()
        }
        true
    }
    popup.show()
}

fun MainActivity.buildFsOverlay() = FrameLayout(this).apply {
    setBackgroundColor(0xFF1A1A1A.toInt())
    layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)

    // Root LinearLayout — title bar on top, webview below
    // Ensures no layout shift on any Android version
    val fsRoot = LinearLayout(this@buildFsOverlay).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
    }

    // Title bar — WRAP_CONTENT height, status bar padding dynamic
    val fsBar = LinearLayout(this@buildFsOverlay).apply {
        orientation  = LinearLayout.HORIZONTAL
        gravity      = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF1A1A1A.toInt())
        setPadding(10.dp(), 4.dp(), 8.dp(), 8.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        elevation    = 8.dp().toFloat()
    }
    // Status bar padding — directly on title bar, not on webview
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(fsBar) { v, insets ->
        val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(10.dp(), top + 4.dp(), 8.dp(), 8.dp())
        insets
    }

    // W logo
    fsBar.addView(View(this@buildFsOverlay).apply {
        val s = 34.dp()
        layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 8.dp() }
        background = drawLogo()
    })

    // Rounded pill title
    val fsPill = FrameLayout(this@buildFsOverlay).apply {
        layoutParams = LinearLayout.LayoutParams(0, 36.dp(), 1f)
        background   = drawRoundRect(0xFF2C2C2C.toInt(), 18.dp().toFloat())
    }
    tvFsTitle = TextView(this@buildFsOverlay).apply {
        text         = "Live Preview"
        setTextColor(0xFFE0E0E0.toInt())
        textSize     = 13f
        isSingleLine = true
        ellipsize    = android.text.TextUtils.TruncateAt.END
        gravity      = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 0, 14.dp(), 0)
        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        isFocusable  = false
        isClickable  = false
    }
    fsPill.addView(tvFsTitle)
    fsBar.addView(fsPill)

    // Reload button
    fsBar.addView(mkIcon(16.dp(), drawReload()).apply {
        layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp()).apply { leftMargin = 2.dp() }
        setOnClickListener { webFs.reload() }
    })

    // 3-dot menu
    fsBar.addView(mkIcon(16.dp(), drawMenuDots()).apply {
        layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp())
        setOnClickListener { v ->
            val popup = android.widget.PopupMenu(this@buildFsOverlay, v)
            popup.menu.apply {
                add(0, 1, 0, "Console")
                add(0, 2, 0, "Reload")
                add(0, 3, 0, "Exit Fullscreen")
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> toggleFsConsole()
                    2 -> webFs.reload()
                    3 -> exitFullscreen()
                }
                true
            }
            popup.show()
        }
    })

    // 1px separator — clean division between bar and webview
    val separator = View(this@buildFsOverlay).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(0xFF333333.toInt())
    }

    // WebView — weight=1f fills all remaining space below title bar
    // No topMargin needed — LinearLayout handles this automatically
    webFs = makeWebView()
    webFs.layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)

    // Add in order: bar → separator → webview
    fsRoot.addView(fsBar)
    fsRoot.addView(separator)
    fsRoot.addView(webFs)

    // ── JS console panel — slides up from bottom ─────────────
    fsConsolePanel = LinearLayout(this@buildFsOverlay).apply {
        orientation = LinearLayout.VERTICAL
        visibility  = View.GONE
        setBackgroundColor(0xFF1A1A1A.toInt())
        layoutParams = FrameLayout.LayoutParams(MATCH, 300.dp()).apply {
            gravity = Gravity.BOTTOM
        }
        elevation = 10.dp().toFloat()
        // Insets handled by parent FrameLayout listener below
    }
    // Console header row
    val fsCHeader = LinearLayout(this@buildFsOverlay).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF222222.toInt())
        setPadding(14.dp(), 0, 8.dp(), 0); minimumHeight = 36.dp()
    }
    fsCHeader.addView(TextView(this@buildFsOverlay).apply {
        text = "Console"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFCCCCCC.toInt())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    })
    fsCHeader.addView(TextView(this@buildFsOverlay).apply {
        text = "Clear"; textSize = 11f; setTextColor(0xFF666666.toInt())
        setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
        setOnClickListener { consoleLogs.clear(); updateFsConsoleLog() }
    })
    fsConsolePanel.addView(fsCHeader)
    fsConsolePanel.addView(View(this@buildFsOverlay).apply {
        setBackgroundColor(0xFF333333.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
    })
    // Log output area
    val fsCScroll = android.widget.ScrollView(this@buildFsOverlay).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        setPadding(14.dp(), 8.dp(), 14.dp(), 4.dp())
    }
    val fsCLogView = TextView(this@buildFsOverlay).apply {
        text = ">"; setTextColor(0xFF666666.toInt())
        textSize = 11f; typeface = Typeface.MONOSPACE
        setLineSpacing(0f, 1.5f)
        tag = "fsCLog"
    }
    fsCScroll.addView(fsCLogView)
    fsConsolePanel.addView(fsCScroll)
    // Divider
    fsConsolePanel.addView(View(this@buildFsOverlay).apply {
        setBackgroundColor(0xFF333333.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
    })
    // JS input row
    val jsRow = LinearLayout(this@buildFsOverlay).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFF111111.toInt())
        setPadding(10.dp(), 4.dp(), 4.dp(), 4.dp())
    }
    jsRow.addView(TextView(this@buildFsOverlay).apply {
        text = ">"; textSize = 13f; typeface = Typeface.MONOSPACE
        setTextColor(0xFF4ADE80.toInt())
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = 8.dp() }
    })
    val jsInput = android.widget.EditText(this@buildFsOverlay).apply {
        hint = "console.log('hello')"; textSize = 11f; typeface = Typeface.MONOSPACE
        setTextColor(0xFFE0E0E0.toInt()); setHintTextColor(0xFF444444.toInt())
        background = null
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        maxLines = 1
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
    }
    jsRow.addView(jsInput)
    jsRow.addView(TextView(this@buildFsOverlay).apply {
        text = "Run"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF007AFF.toInt())
        setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        setOnClickListener {
            val js = jsInput.text.toString().trim()
            if (js.isNotEmpty()) {
                consoleLogs.add("[RUN] $js")
                webFs.evaluateJavascript(js) { result ->
                    runOnUiThread {
                        if (!result.isNullOrEmpty() && result != "null")
                            consoleLogs.add("  =>  $result")
                        updateFsConsoleLog()
                        jsInput.text.clear()
                    }
                }
            }
        }
    })
    fsConsolePanel.addView(jsRow)
    // Add fsRoot (titlebar + webview) first, console panel floats on top
    addView(fsRoot)
    addView(fsConsolePanel)

    // Keyboard + nav bar — use WindowInsetsCompat for all Android versions
    // Correctly moves console above keyboard and nav bar
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
        val imeH   = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
        // imeH includes nav bar when keyboard is open, so use max
        val bottomOffset = maxOf(navBar, imeH)
        if (this@buildFsOverlay.isFsConsolePanelReady) {
            val lp = this@buildFsOverlay.fsConsolePanel.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = bottomOffset
            this@buildFsOverlay.fsConsolePanel.layoutParams = lp
        }
        insets
    }
}

fun MainActivity.switchPanel(idx: Int) {
    if (currentPanel == idx) return
    val w   = resources.displayMetrics.widthPixels.toFloat()
    val dir = if (idx > currentPanel) 1f else -1f
    val inc = if (idx == 0) panelHost else panelOutput
    val out = if (idx == 0) panelOutput else panelHost
    inc.apply {
        visibility = View.VISIBLE; translationX = dir * w
        animate().translationX(0f).setDuration(270)
            .setInterpolator(DecelerateInterpolator()).start()
    }
    out.animate().translationX(-dir * w).setDuration(270)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction { out.visibility = View.GONE }.start()
    currentPanel = idx
    tabHost.setTextColor(if (idx == 0) 0xFF334155.toInt() else 0xFF9CA3AF.toInt())
    tabOutput.setTextColor(if (idx == 1) 0xFF334155.toInt() else 0xFF9CA3AF.toInt())
    // Active tab gets subtle slate tint, inactive is transparent
    tabHost.background = drawRoundRect(if (idx == 0) 0x0A111111.toInt() else 0x00000000, 0f)
    tabOutput.background = drawRoundRect(if (idx == 1) 0x0A111111.toInt() else 0x00000000, 0f)
    tabIndicator.animate().translationX(idx * w / 2f).setDuration(270)
        .setInterpolator(DecelerateInterpolator()).start()
}

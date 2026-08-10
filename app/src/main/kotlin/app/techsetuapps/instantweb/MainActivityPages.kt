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
//  MainActivityPages — Dialogs & Pages — update system, menu/drawer, legal pages, settings, help, support
// ================================================================

fun MainActivity.checkForUpdate() {
    // First show from SharedPrefs immediately (instant feedback)
    checkOfflineUpdate()
    // Then check fresh in background
    Thread {
        var retries = 0
        while (retries < 3) {
            try {
                val myVersion = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                    else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0).versionCode
                    }
                } catch (_: Exception) { 1 }

                // Add timestamp to URL to bypass any caching
                val url = "$UPDATE_JSON_URL?t=${System.currentTimeMillis()}"
                val conn = java.net.URL(url)
                    .openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout    = 8000
                conn.requestMethod  = "GET"
                conn.useCaches      = false
                conn.setRequestProperty("Cache-Control", "no-cache, no-store")
                conn.setRequestProperty("Pragma", "no-cache")
                conn.connect()

                if (conn.responseCode != 200) {
                    conn.disconnect()
                    retries++
                    Thread.sleep(1000)
                    continue
                }

                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                android.util.Log.d("IW_UPDATE", "JSON: $json")

                // Robust parsing — use regex to extract values safely
                val remoteVersion = try {
                    val match = Regex("\"app_version\"\\s*:\\s*(\\d+)").find(json)
                    match?.groupValues?.get(1)?.toIntOrNull()
                } catch (_: Exception) { null }

                val updateLink = try {
                    val match = Regex("\"new_version_link\"\\s*:\\s*\"([^\"]+)\"").find(json)
                    match?.groupValues?.get(1)?.trim()
                } catch (_: Exception) { null }

                if (remoteVersion == null || updateLink.isNullOrBlank()) {
                    android.util.Log.w("IW_UPDATE", "Invalid JSON — missing app_version or new_version_link")
                    break
                }

                android.util.Log.d("IW_UPDATE", "remote=$remoteVersion mine=$myVersion link=$updateLink")

                if (remoteVersion > myVersion) {
                    prefs.edit()
                        .putBoolean(PREF_UPDATE_AVAILABLE, true)
                        .putString(PREF_UPDATE_LINK, updateLink)
                        .putLong(PREF_UPDATE_FOUND_TIME, System.currentTimeMillis())
                        .apply()
                    runOnUiThread { showUpdateDialog(updateLink, hardBlock = true) }
                } else {
                    prefs.edit()
                        .remove(PREF_UPDATE_AVAILABLE)
                        .remove(PREF_UPDATE_LINK)
                        .remove(PREF_UPDATE_FOUND_TIME)
                        .apply()
                    runOnUiThread { closeFullScreenOverlay(tag = "update") }
                }
                break // Success — exit retry loop

            } catch (e: Exception) {
                android.util.Log.e("IW_UPDATE", "Attempt ${retries+1} failed: ${e.message}")
                retries++
                if (retries < 3) Thread.sleep(1000) // 1 second wait before retry
            }
        }
        // All retries failed — use SharedPrefs fallback
        if (retries >= 3) {
            runOnUiThread { checkOfflineUpdate() }
        }
    }.start()
}

fun MainActivity.checkOfflineUpdate() {
    val updateAvailable = prefs.getBoolean(PREF_UPDATE_AVAILABLE, false)
    if (!updateAvailable) return

    val updateLink  = prefs.getString(PREF_UPDATE_LINK, "") ?: ""
    val foundTime   = prefs.getLong(PREF_UPDATE_FOUND_TIME, 0L)
    val daysPassed  = (System.currentTimeMillis() - foundTime) /
                      (1000L * 60 * 60 * 24)

    // Offline for 7+ days → soft banner only, no hard block
    val isHardBlock = daysPassed < OFFLINE_GRACE_DAYS
    showUpdateDialog(updateLink, hardBlock = isHardBlock)
}

fun MainActivity.showUpdateDialog(updateLink: String, hardBlock: Boolean) {
    val overlayBg = android.widget.FrameLayout(this).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
        setBackgroundColor(0xFF0F172A.toInt())
    }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity     = Gravity.CENTER
        setPadding(32.dp(), 0, 32.dp(), 0)
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
    }

    // Edge-to-edge: add system bar padding to root
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        v.setPadding(32.dp() + sb.left, sb.top, 32.dp() + sb.right, sb.bottom)
        insets
    }

    // Icon
    root.addView(View(this).apply {
        val s = 72.dp()
        layoutParams = LinearLayout.LayoutParams(s, s).apply { bottomMargin = 24.dp() }
        background = drawLogo()
    })

    // Title
    root.addView(TextView(this).apply {
        text = "Update Required"
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        gravity  = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 12.dp() }
    })

    // Message
    root.addView(TextView(this).apply {
        text = "A new version of InstantLive Server is available.\nPlease update to continue using the app."
        setTextColor(0xFF94A3B8.toInt())
        textSize = 14f
        gravity  = Gravity.CENTER
        setLineSpacing(0f, 1.5f)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 32.dp() }
    })

    // Update button
    root.addView(TextView(this).apply {
        text = "Update Now"
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity  = Gravity.CENTER
        background = drawRoundRect(0xFF2563EB.toInt(), 14.dp().toFloat())
        setPadding(0, 16.dp(), 0, 16.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 12.dp() }
        setOnClickListener {
            if (updateLink.startsWith("http://") || updateLink.startsWith("https://")) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse(updateLink)))
                } catch (_: Exception) {
                    toast("Could not open link. Please update manually.")
                }
            } else {
                toast("Invalid update link. Please update manually.")
            }
        }
    })

    // Soft block only — show "Maybe Later" option
    if (!hardBlock) {
        root.addView(TextView(this).apply {
            text = "Maybe Later"
            setTextColor(0xFF64748B.toInt())
            textSize = 14f
            gravity  = Gravity.CENTER
            setPadding(0, 12.dp(), 0, 12.dp())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setOnClickListener { closeFullScreenOverlay() }
        })
    }

    overlayBg.addView(root)
    showFullScreenOverlay(overlayBg, cancelable = false, tag = "update")
}

fun MainActivity.showMainMenu(anchor: android.view.View) {
    showRightDrawer()
}

fun MainActivity.showRightDrawer() {
    drawerOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
    val root = window.decorView.findViewById<android.widget.FrameLayout>(android.R.id.content)

    val overlay = android.widget.FrameLayout(this).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
        setBackgroundColor(0x60000000.toInt())
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()
    }

    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM)
        elevation = 32.dp().toFloat()
        translationY = resources.displayMetrics.heightPixels.toFloat()
        background = object : android.graphics.drawable.Drawable() {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt() }
            override fun draw(c: android.graphics.Canvas) {
                val r = 20.dp().toFloat()
                c.drawRoundRect(android.graphics.RectF(bounds), r, r, p)
                c.drawRect(android.graphics.RectF(bounds.left.toFloat(), bounds.top + r, bounds.right.toFloat(), bounds.bottom.toFloat()), p)
            }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(f: android.graphics.ColorFilter?) {}
            override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
        }
    }

    // Handle bar
    panel.addView(View(this).apply {
        val lp = LinearLayout.LayoutParams(44.dp(), 4.dp())
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = 10.dp()
        lp.bottomMargin = 6.dp()
        layoutParams = lp
        background = drawRoundRect(0xFF555555.toInt(), 2.dp().toFloat())
    })

    data class MenuItem(val label: String, val iconRes: Int?, val action: () -> Unit)
    val menuItems = listOf(
        MenuItem("Privacy Policy", R.drawable.ic_privacy)    { showLegalPage("Privacy Policy", htmlPrivacyPolicy()) },
        MenuItem("Terms of Use",   R.drawable.ic_terms)      { showLegalPage("Terms of Use", htmlTermsOfUse()) },
        MenuItem("Disclaimer",     R.drawable.ic_disclaimer) { showLegalPage("Disclaimer", htmlDisclaimer()) },
        MenuItem("Credits",        R.drawable.ic_credits)    { showLegalPage("Credits", htmlCredits()) },
        MenuItem("Help",           null)                     { showHelpPage() },  // custom drawn
        MenuItem("Support Us",     R.drawable.ic_support)    { showSupportPage() },
        MenuItem("Settings",       R.drawable.ic_settings)   { showSettingsPage() },
        MenuItem("Exit",           R.drawable.ic_exit)       { showExitConfirmation() }
    )

    val scrollView = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        isVerticalScrollBarEnabled = false
    }
    val itemsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    menuItems.forEachIndexed { i, mi ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 20.dp(), 0)
            minimumHeight = 60.dp()
            isClickable = true; isFocusable = true
            setBackgroundResource(android.util.TypedValue().also { tv ->
                theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            }.resourceId)
            setOnClickListener { closeRightDrawer(); mi.action() }
        }

        // Icon container — white background
        val iconBg = FrameLayout(this).apply {
            val s = 44.dp()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 16.dp() }
            background = drawRoundRect(0xFFFFFFFF.toInt(), 10.dp().toFloat())
        }

        if (mi.iconRes != null) {
            // PNG icon — no color filter, original colors shown
            iconBg.addView(android.widget.ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                val p = 7.dp()
                setPadding(p, p, p, p)
                setImageResource(mi.iconRes)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            })
        } else {
            // Help — custom drawn "?" icon
            iconBg.addView(View(this).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                background = object : android.graphics.drawable.Drawable() {
                    override fun draw(c: android.graphics.Canvas) {
                        val b = bounds
                        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF0891B2.toInt()
                            textSize = b.height() * 0.55f
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val x = b.exactCenterX()
                        val y = b.exactCenterY() - (p.descent() + p.ascent()) / 2
                        c.drawText("?", x, y, p)
                    }
                    override fun setAlpha(a: Int) {}
                    override fun setColorFilter(f: android.graphics.ColorFilter?) {}
                    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
                }
            })
        }
        row.addView(iconBg)

        row.addView(TextView(this).apply {
            text = mi.label
            setTextColor(0xFFEEEEEE.toInt())
            textSize = 15.5f
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        // Drawn chevron arrow instead of "›" text
        row.addView(mkIcon(16.dp(), drawChevronRight(0xFF555555.toInt())))

        itemsLayout.addView(row)

        // Full-width divider
        if (i < menuItems.size - 1) {
            itemsLayout.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH, 1)
                setBackgroundColor(0xFF2E2E2E.toInt())
            })
        }
    }

    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(itemsLayout) { v, insets ->
        val nb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
        v.setPadding(0, 0, 0, nb + 8.dp())
        insets
    }

    scrollView.addView(itemsLayout)
    panel.addView(scrollView)
    overlay.addView(panel)
    root.addView(overlay)
    drawerOverlay = overlay

    panel.animate().translationY(0f).setDuration(260)
        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()

    overlay.setOnClickListener { closeRightDrawer() }
    panel.setOnClickListener { }
}

fun MainActivity.closeRightDrawer() {
    val overlay = drawerOverlay ?: return
    val panel = overlay.getChildAt(0) as? LinearLayout ?: return
    val h = resources.displayMetrics.heightPixels.toFloat()
    panel.animate().translationY(h).setDuration(220)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .withEndAction {
            overlay.animate().alpha(0f).setDuration(150)
                .withEndAction {
                    (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
                    drawerOverlay = null
                }.start()
        }.start()
}

fun MainActivity.showFullScreenOverlay(contentView: android.view.View, cancelable: Boolean = true, tag: String? = null) {
    // Remove any existing overlay first
    fullScreenOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }

    val root = window.decorView.findViewById<android.widget.FrameLayout>(android.R.id.content)

    val overlay = android.widget.FrameLayout(this).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
    }
    overlay.addView(contentView)
    root.addView(overlay)

    fullScreenOverlay = overlay
    fullScreenCancelable = cancelable
    fullScreenOverlayTag = tag
}

fun MainActivity.closeFullScreenOverlay(tag: String? = null) {
    if (tag != null && fullScreenOverlayTag != tag) return  // Different overlay — do not close
    val overlay = fullScreenOverlay ?: return
    overlay.animate().alpha(0f).setDuration(150)
        .withEndAction {
            (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
            fullScreenOverlay = null
            fullScreenOverlayTag = null
        }.start()
}

fun MainActivity.showLegalPage(title: String, html: String) {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
    }
    // Header bar — white background, title left, X right
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(16.dp(), 22.dp(), 8.dp(), 8.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        elevation = 2.dp().toFloat()
    }
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
        val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(16.dp(), top + 22.dp(), 8.dp(), 8.dp())
        insets
    }
    header.addView(TextView(this).apply {
        text = title
        setTextColor(0xFF111827.toInt())
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    })
    header.addView(android.widget.ImageView(this).apply {
        setImageDrawable(drawIconClose(0xFF6B7280.toInt()))
        setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
        layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp())
        setOnClickListener { closeFullScreenOverlay() }
    })
    root.addView(header)
    // WebView for rich HTML content
    val wv = WebView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        settings.javaScriptEnabled = false
        settings.domStorageEnabled = false
        // Enable mailto: links
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.startsWith("mailto:")) {
                    startActivity(Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse(url)
                    })
                    true
                } else false
            }
        }
    }
    root.addView(wv)
    wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    showFullScreenOverlay(root, cancelable = true, tag = "legal")
}

fun MainActivity.showFirstLaunchDialog() {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
    }
    // Header
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(0xFF1A1A1A.toInt())
        setPadding(24.dp(), 28.dp(), 24.dp(), 20.dp())
    }
    // Edge-to-edge: add status bar padding to header
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
        val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(24.dp(), top + 28.dp(), 24.dp(), 20.dp())
        insets
    }
    header.addView(View(this).apply {
        val s = 56.dp()
        layoutParams = LinearLayout.LayoutParams(s, s).apply { bottomMargin = 14.dp() }
        background = drawLogo()
    })
    header.addView(TextView(this).apply {
        text = "Welcome to InstantLive Server"
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    })
    header.addView(TextView(this).apply {
        text = "Please read and accept our terms before continuing"
        setTextColor(0xFF9CA3AF.toInt())
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, 8.dp(), 0, 0)
    })
    root.addView(header)

    // Combined scroll content
    val wv = WebView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        settings.javaScriptEnabled = false
    }
    root.addView(wv)
    wv.loadDataWithBaseURL(null, htmlFirstLaunch(), "text/html", "UTF-8", null)

    // Buttons row — add nav bar height so buttons are not hidden
    val btnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(20.dp(), 16.dp(), 20.dp(), 24.dp())
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(20.dp(), 16.dp(), 20.dp(), 24.dp() + nb)
            insets
        }
        setBackgroundColor(0xFFF9FAFB.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    // Deny button
    btnRow.addView(TextView(this).apply {
        text = "Deny & Exit"
        setTextColor(0xFFDC2626.toInt())
        textSize = 15f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = drawRoundRect(0xFFFFE4E4.toInt(), 12.dp().toFloat())
        setPadding(24.dp(), 14.dp(), 24.dp(), 14.dp())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = 10.dp() }
        setOnClickListener {
            closeFullScreenOverlay()
            finishAffinity()
        }
    })
    // Accept button
    btnRow.addView(TextView(this).apply {
        text = "Accept & Continue"
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 15f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = drawRoundRect(0xFF2563EB.toInt(), 12.dp().toFloat())
        setPadding(24.dp(), 14.dp(), 24.dp(), 14.dp())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = 10.dp() }
        setOnClickListener {
            prefs.edit().putBoolean("terms_accepted", true).apply()
            closeFullScreenOverlay()
            checkForUpdate()
        }
    })
    root.addView(btnRow)
    showFullScreenOverlay(root, cancelable = false, tag = "first_launch")
}

fun MainActivity.htmlFirstLaunch() = """<!DOCTYPE html><html><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#fff;color:#111827;font-size:14px;line-height:1.7;padding:20px 18px 80px;}
h2{font-size:16px;font-weight:700;color:#1e40af;margin:20px 0 6px;}
h2:first-child{margin-top:0;}
p{color:#374151;margin-bottom:10px;}
.warn{background:#FEF3C7;border-left:4px solid #F59E0B;padding:10px 14px;border-radius:6px;margin:10px 0;color:#92400E;font-size:13px;display:flex;align-items:center;gap:6px;}
.info{background:#EFF6FF;border-left:4px solid #2563EB;padding:10px 14px;border-radius:6px;margin:10px 0;color:#1e40af;font-size:13px;}
ul{padding-left:18px;margin-bottom:10px;}
li{color:#374151;margin-bottom:4px;}
</style></head><body>
<h2>Privacy Policy</h2>
<p>InstantLive Server does not collect or transmit your personal data. All files are served locally. No analytics, no tracking, no cloud uploads.</p>
<div class="info"><svg style='width:14px;height:14px;vertical-align:middle;margin-right:4px' viewBox='0 0 24 24'><path fill='#1e40af' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z'/></svg>This app shows ads via <b>Google AdMob</b> before each hosting session. AdMob may collect device identifiers to serve relevant ads. See our full Privacy Policy for details.</div>
<h2>Terms of Use</h2>
<ul>
<li>You must be at least <b>13 years old</b> to use this app.</li>
<li>You may use InstantLive Server only for lawful purposes.</li>
<li>Decompiling, reverse engineering, or modifying this app's code is strictly prohibited.</li>
<li>Redistribution of this app or its code without permission is not allowed.</li>
<li>You agree not to use this app to host illegal, harmful, or infringing content.</li>
<li>Do not artificially click or interact with ads — this violates AdMob policy.</li>
</ul>
<h2>Disclaimer</h2>
<p>InstantLive Server is provided "as is" without any warranties. The developer is not responsible for:</p>
<ul>
<li>Any data loss or damage caused by using this app.</li>
<li>Misuse of this app for hacking, phishing, or any illegal activity.</li>
<li>Security breaches resulting from content hosted by the user.</li>
<li>Any content or products shown in third-party advertisements.</li>
</ul>
<div class="warn"><svg style='width:14px;height:14px;vertical-align:middle;margin-right:4px' viewBox='0 0 24 24'><path fill='#92400E' d='M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z'/></svg>Do not host sensitive, confidential, or private information. You are solely responsible for the content you serve.</div>
<h2>What's New in v1.4.0</h2>
<ul>
<li><b>Virtual File System (VFS):</b> All hosted files now live in RAM — zero disk writes, zero file residue after hosting ends.</li>
<li><b>Relative Path Support:</b> Your HTML can now reference assets with relative paths like <code>src="css/style.css"</code> or <code>src="images/logo.png"</code> — they just work!</li>
<li><b>Improved IP Detection:</b> Auto-discovery of live servers on your network for faster viewer connections.</li>
<li><b>Robust Update System:</b> Safer JSON parsing and URL validation for the force update check.</li>
</ul>
<h2>Credits</h2>
<p>This app uses <b>NanoHTTPD</b> (BSD-3-Clause), <b>Eruda</b> (MIT — © 2017 liriliri), and <b>Google AdMob SDK</b> (© Google LLC). All other code is original work by <b>TechSetuApps</b>.</p>
<p><svg style='width:14px;height:14px;vertical-align:middle;margin-right:4px' viewBox='0 0 24 24'><path fill='#374151' d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z'/></svg>Special thanks to <b>Android Code Studio (ACS)</b> team. Visit: <b>androidide.com</b></p>
</body></html>"""

fun MainActivity.htmlPrivacyPolicy() = """<!DOCTYPE html><html><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#fff;color:#111827;font-size:14px;line-height:1.8;padding:24px 18px 80px;}
h1{font-size:20px;font-weight:700;color:#111827;margin-bottom:4px;}
.sub{color:#6B7280;font-size:12px;margin-bottom:24px;}
h2{font-size:15px;font-weight:700;color:#1e40af;margin:20px 0 8px;padding-bottom:4px;border-bottom:1px solid #E5E7EB;}
p{color:#374151;margin-bottom:10px;}
ul{padding-left:18px;margin-bottom:10px;}
li{color:#374151;margin-bottom:5px;}
.chip{display:inline-block;background:#ECFDF5;color:#059669;padding:2px 10px;border-radius:20px;font-size:12px;font-weight:700;}
.adchip{display:inline-block;background:#FEF3C7;color:#92400E;padding:2px 10px;border-radius:20px;font-size:12px;font-weight:700;}
.note{background:#F0F9FF;border-left:4px solid #0EA5E9;padding:10px 14px;border-radius:6px;margin:10px 0;color:#0C4A6E;font-size:13px;display:flex;align-items:flex-start;gap:6px;}
.warn{background:#FFFBEB;border-left:4px solid #F59E0B;padding:10px 14px;border-radius:6px;margin:10px 0;color:#92400E;font-size:13px;display:flex;align-items:flex-start;gap:6px;}
.chip svg,.adchip svg{vertical-align:text-bottom;position:relative;top:0px;}
</style></head><body>
<h1>Privacy Policy</h1>
<p class="sub">Last updated: August 2026 &nbsp;|&nbsp; <span class="chip"><svg style='width:12px;height:12px;vertical-align:middle;margin-right:2px' viewBox='0 0 24 24'><path fill='#059669' d='M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z'/></svg>No personal data collected</span> &nbsp;<span class="adchip"><svg style='width:12px;height:12px;vertical-align:middle;margin-right:2px' viewBox='0 0 24 24'><path fill='#92400E' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z'/></svg>Contains Ads</span></p>
<h2>Overview</h2>
<p>InstantLive Server ("we", "our", "the app") is a local HTML file hosting tool that lets you serve web pages from your Android device. We are committed to protecting your privacy. This policy explains what data we access and why.</p>
<div class="note"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#0C4A6E' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z'/></svg><span style="flex:1;min-width:0;">This app is <b>open source</b> under the MIT License. Source code available at <a href="https://github.com/TechSetuApps/InstantLiveServer" style="color:#1e40af;font-weight:700;">github.com/TechSetuApps/InstantLiveServer</a> for full audit.</span></div>
<h2>Data We Do NOT Collect</h2>
<ul>
<li>No personal information is collected or stored remotely by us.</li>
<li>No usage analytics or crash reports are sent to any server owned by us.</li>
<li>Files you serve are never uploaded to any cloud or third-party server by us.</li>
<li>No file paths, folder names, or directory structures are transmitted externally.</li>
</ul>
<h2>Advertising (Google AdMob)</h2>
<p>InstantLive Server displays advertisements powered by <b>Google AdMob</b> to support free development. AdMob may collect and use data to serve personalized or non-personalized ads. This includes:</p>
<ul>
<li>Device identifiers (Advertising ID)</li>
<li>IP address and approximate location</li>
<li>App usage data for ad relevance</li>
</ul>
<div class="note"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#0C4A6E' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z'/></svg><span style="flex:1;min-width:0;">Google's data collection is governed by Google's Privacy Policy: <b>policies.google.com/privacy</b>. We do not control or access the data collected by AdMob.</span></div>
<p>Ads are shown before starting a hosting session. A 2-minute grace period applies — no ad is shown if you re-host within 2 minutes of the previous ad.</p>
<h2>Local Storage &amp; Virtual File System</h2>
<p>InstantLive Server serves selected files directly from RAM using a Virtual File System. No file data is written to persistent storage at any point. When the server stops, all file data is immediately freed from memory.</p>
<p>When you select files for hosting, the app loads them into a Virtual File System (VFS) stored entirely in RAM. This virtual structure allows relative paths in your HTML (e.g., <code>src="css/style.css"</code>) to work correctly. The VFS exists only in RAM while the server is running and is completely discarded when the server stops. <b>No copies of your files are ever written to persistent storage.</b> This means zero file residue after hosting ends.</p>
<h2>Network Access</h2>
<p>The app creates a local HTTP server on your device (port 7090 by default). This server is accessible only by devices on the same local network (e.g., hotspot). When a folder is selected, all files within it become accessible through the server's URL structure, preserving relative paths so that assets like CSS, JavaScript, and images load correctly.</p>
<h2>App Update &amp; Force Update</h2>
<p>On startup, InstantLive Server checks for updates by fetching a small version file from GitHub. <b>No personal data is sent</b> — only the app version number is compared.</p>
<div class="note"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#0C4A6E' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z'/></svg><span style="flex:1;min-width:0;">A <b>force update</b> system is in place for critical bug fixes and major security updates. When triggered, it cannot be dismissed — the user must update to continue using the app.</span></div>
<h2>Permissions Used</h2>
<ul>
<li><b>INTERNET:</b> Required to run the local HTTP server and load ads.</li>
<li><b>ACCESS_WIFI_STATE / ACCESS_NETWORK_STATE:</b> To detect your hotspot IP address and network changes.</li>
<li><b>CHANGE_WIFI_STATE:</b> To detect WiFi/hotspot connection changes for automatic server discovery.</li>
<li><b>READ_EXTERNAL_STORAGE / READ_MEDIA:</b> To let you select files and folders from your device.</li>
<li><b>POST_NOTIFICATIONS:</b> To show the "Hosting Active" persistent notification.</li>
<li><b>FOREGROUND_SERVICE:</b> To keep the server running in the background.</li>
<li><b>FOREGROUND_SERVICE_DATA_SYNC:</b> Required for serving files over the local network.</li>
<li><b>WAKE_LOCK:</b> To prevent Android from killing the server due to battery optimization.</li>
</ul>
<h2>Contact</h2>
<p>For privacy-related queries, contact:<br>
<a href="mailto:techsetuapps@gmail.com" style="color:#2563EB;font-weight:700;">techsetuapps@gmail.com</a></p>
<div style="height:40px;"></div>
</body></html>"""

fun MainActivity.htmlTermsOfUse() = """<!DOCTYPE html><html><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#fff;color:#111827;font-size:14px;line-height:1.8;padding:24px 18px 80px;}
h1{font-size:20px;font-weight:700;color:#111827;margin-bottom:4px;}
.sub{color:#6B7280;font-size:12px;margin-bottom:24px;}
h2{font-size:15px;font-weight:700;color:#1e40af;margin:20px 0 8px;padding-bottom:4px;border-bottom:1px solid #E5E7EB;}
p{color:#374151;margin-bottom:10px;}
ul{padding-left:18px;margin-bottom:10px;}
li{color:#374151;margin-bottom:5px;}
.warn{background:#FEF3C7;border-left:4px solid #F59E0B;padding:12px 16px;border-radius:8px;margin:14px 0;color:#92400E;display:flex;align-items:flex-start;gap:6px;}.warn svg{flex-shrink:0;}
.red{background:#FEF2F2;border-left:4px solid #DC2626;padding:12px 16px;border-radius:8px;margin:14px 0;color:#991B1B;display:flex;align-items:flex-start;gap:6px;}
</style></head><body>
<h1>Terms of Use</h1>
<p class="sub">Last updated: August 2026 &nbsp;|&nbsp; Effective immediately upon acceptance.</p>
<h2>1. Acceptance</h2>
<p>By using InstantLive Server, you agree to these Terms. If you do not agree, you must not use this application.</p>
<h2>2. Age Requirement</h2>
<p>You must be at least <b>13 years of age</b> to use InstantLive Server. If you are under 13, you may not use this app. By using this app, you confirm that you meet this age requirement.</p>
<h2>3. Permitted Use</h2>
<ul>
<li>Personal, educational, or professional web development and preview purposes.</li>
<li>Sharing HTML files and web projects with authorized devices on a private local network.</li>
<li>Testing web pages, front-end code, and multi-file projects locally.</li>
<li>Using the folder hosting feature to serve entire web project directories with relative path support.</li>
</ul>
<h2>4. Prohibited Use</h2>
<div class="red"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#991B1B' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 3.58-8 8-8s8 3.58 8 8-3.58 8-8 8-8-3.58-8-8zm3-1h10v2H7z'/></svg><span style="flex:1;min-width:0;">The following are strictly prohibited:</span></div>
<ul>
<li>Using the app to host illegal, harmful, defamatory, or infringing content.</li>
<li>Using the app for phishing, hacking, network attacks, or any malicious activity.</li>
<li>Hosting sensitive personal data, passwords, financial information, or private records over unsecured networks.</li>
<li>Bypassing or attempting to bypass any security mechanism within the app.</li>
<li>Using any method to block, bypass, or interfere with the display of advertisements shown in the app.</li>
</ul>
<h2>5. Advertising</h2>
<p>InstantLive Server displays ads via Google AdMob to support free development. A short interstitial ad is shown before each hosting session. A 2-minute grace period applies — re-hosting within 2 minutes does not trigger another ad.</p>
<p>You agree not to artificially interact with ads (e.g., repeatedly clicking ads) as this violates Google AdMob policies and may result in account suspension.</p>
<h2>6. Open Source &amp; Intellectual Property</h2>
<p>InstantLive Server is open-source software released under the <b>MIT License</b>. You are free to use, modify, and distribute the code under the terms of the MIT License. Source code: <a href="https://github.com/TechSetuApps/InstantLiveServer" style="color:#2563EB;font-weight:700;">github.com/TechSetuApps/InstantLiveServer</a></p>
<div class="note"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#0C4A6E' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z'/></svg><span style="flex:1;min-width:0;">The UI design, concept, and application logic are by TechSetuApps. Code was written with AI assistance. The author is currently learning Kotlin.</span></div>
<div class="warn"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#92400E' d='M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z'/></svg><span style="flex:1;min-width:0;">This app uses NanoHTTPD (BSD-3-Clause), Eruda (MIT), and Google AdMob SDK (Apache 2.0). See Credits for full attribution.</span></div>
<h2>7. App Updates &amp; Force Update</h2>
<p>InstantLive Server checks for updates on startup by fetching a version file from GitHub. <b>No personal data is sent</b> — only the version number is compared locally.</p>
<div class="note"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#0C4A6E' d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z'/></svg><span style="flex:1;min-width:0;">A <b>force update</b> system is in place for critical bug fixes and major security updates. When a force update is triggered, it cannot be dismissed and the user must update to continue.</span></div>
<h2>8. Termination</h2>
<p>We reserve the right to terminate your right to use this application if you violate these terms.</p>
<h2>9. Governing Law</h2>
<p>These terms shall be governed by applicable laws. By using this app, you agree to resolve any disputes in good faith directly with the developer.</p>
</body></html>"""

fun MainActivity.htmlDisclaimer() = """<!DOCTYPE html><html><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#fff;color:#111827;font-size:14px;line-height:1.8;padding:24px 18px 80px;}
h1{font-size:20px;font-weight:700;color:#111827;margin-bottom:4px;}
.sub{color:#6B7280;font-size:12px;margin-bottom:24px;}
h2{font-size:15px;font-weight:700;color:#1e40af;margin:20px 0 8px;padding-bottom:4px;border-bottom:1px solid #E5E7EB;}
p{color:#374151;margin-bottom:10px;}
ul{padding-left:18px;margin-bottom:10px;}
li{color:#374151;margin-bottom:5px;}
.box{background:#F3F4F6;border-radius:10px;padding:14px 16px;margin:12px 0;}
.red{background:#FEF2F2;border-left:4px solid #DC2626;padding:12px 16px;border-radius:8px;margin:14px 0;color:#991B1B;display:flex;align-items:flex-start;gap:6px;}.red svg{flex-shrink:0;}
</style></head><body>
<h1>Disclaimer</h1>
<p class="sub">Last updated: August 2026 &nbsp;|&nbsp; Read carefully before use.</p>
<h2>No Warranty</h2>
<p>InstantLive Server is provided <b>"as is"</b> and <b>"as available"</b> without any warranties of any kind, either express or implied, including but not limited to fitness for a particular purpose.</p>
<h2>Limitation of Liability</h2>
<p>The developer of InstantLive Server shall not be held liable for:</p>
<ul>
<li>Any loss of data, files, or content arising from use of this app.</li>
<li>Any unauthorized access to content you serve through this app.</li>
<li>Any damage caused by malware, viruses, or third-party interference.</li>
<li>Financial, reputational, or other losses resulting from using this app.</li>
<li>Any content, products, or services advertised by third-party ads shown in the app.</li>
</ul>
<h2>Third-Party Advertising</h2>
<div class="box">InstantLive Server uses Google AdMob to display ads. We do not control the content of ads shown. We are not responsible for any claims, losses, or damages arising from third-party ad content. Ad content is governed by Google's advertising policies.</div>
<h2>Security Responsibility</h2>
<div class="red"><svg style='width:14px;height:14px;flex-shrink:0;margin-top:2px' viewBox='0 0 24 24'><path fill='#991B1B' d='M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z'/></svg><span style="flex:1;min-width:0;">You are solely responsible for the security of content you host. Do NOT host confidential, sensitive, or private information over public or unsecured networks.</span></div>
<h2>Misuse & Illegal Activity</h2>
<div class="box">If a user uses InstantLive Server for hacking, phishing, unauthorized network access, or any illegal purpose — the developer bears <b>zero responsibility</b> for any resulting harm. All legal liability rests entirely with the user.</div>
<h2>Third-Party Networks</h2>
<p>When sharing via hotspot or local network, the developer is not responsible for the security of that network or devices connected to it.</p>
<h2>Sensitive Projects</h2>
<p>Users working on sensitive, confidential, or high-security projects use this app at their own risk. We strongly advise against sharing such content over local networks using this or any similar tool.</p>
</body></html>"""

fun MainActivity.htmlCredits() = """<!DOCTYPE html><html><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#fff;color:#111827;font-size:14px;line-height:1.8;padding:24px 18px 80px;}
h1{font-size:20px;font-weight:700;color:#111827;margin-bottom:4px;}
.sub{color:#6B7280;font-size:12px;margin-bottom:24px;}
.card{background:#F9FAFB;border:1px solid #E5E7EB;border-radius:12px;padding:16px 18px;margin-bottom:14px;}
.card h3{font-size:15px;font-weight:700;color:#1e40af;margin-bottom:4px;}
.card p{color:#374151;font-size:13px;margin-bottom:6px;}
.badge{display:inline-block;background:#DBEAFE;color:#1e40af;padding:2px 10px;border-radius:20px;font-size:11px;font-weight:700;margin-bottom:8px;}
.badge-g{display:inline-block;background:#FEF9C3;color:#854D0E;padding:2px 10px;border-radius:20px;font-size:11px;font-weight:700;margin-bottom:8px;}
.dev{background:linear-gradient(135deg,#1A1A1A,#2C2C2C);border-radius:14px;padding:20px;margin-bottom:14px;color:#fff;text-align:center;}
.dev h2{font-size:18px;font-weight:700;color:#fff;margin-bottom:4px;}
.dev p{color:#9CA3AF;font-size:13px;margin:0;}
.dev .loc{color:#60A5FA;font-size:12px;margin-top:6px;}
</style></head><body>
<h1>Credits</h1>
<p class="sub">Open source libraries used in InstantLive Server v1.4.0</p>
<div class="dev">
<div style="width:48px;height:48px;background:#2563EB;border-radius:12px;display:flex;align-items:center;justify-content:center;margin:0 auto 12px;font-size:22px;font-weight:900;color:white;">W</div>
<h2>TechSetuApps</h2>
<p>Developer & Designer</p>
<p class="loc"><svg style='width:12px;height:12px;vertical-align:middle;margin-right:2px' viewBox='0 0 24 24'><path fill='#60A5FA' d='M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z'/></svg>Darbhanga, Bihar, India</p>
</div>
<div class="card">
<span class="badge">BSD-3-Clause License</span>
<h3>NanoHTTPD</h3>
<p>A light-weight HTTP server designed for embedding in applications, written in Java.</p>
<p style="color:#6B7280;font-size:12px;">© nanohttpd.org contributors &nbsp;|&nbsp; github.com/NanoHttpd/nanohttpd</p>
</div>
<div class="card">
<span class="badge">MIT License</span>
<h3>Eruda</h3>
<p>A powerful JavaScript debugging console for mobile browsers — used to provide in-browser DevTools in the live preview.</p>
<p>Copyright &copy; 2017 liriliri</p>
<p style="color:#6B7280;font-size:12px;">github.com/liriliri/eruda &nbsp;|&nbsp; <a href="https://opensource.org/licenses/MIT" style="color:#1e40af;">Full MIT License</a></p>
</div>
<div class="card">
<span class="badge-g">Google Play Services</span>
<h3>Google AdMob SDK</h3>
<p>Advertisement platform by Google used to display interstitial ads — supports free development of InstantLive Server.</p>
<p style="color:#6B7280;font-size:12px;">© Google LLC &nbsp;|&nbsp; policies.google.com/privacy</p>
</div>
<div class="card">
<h3><svg style='width:14px;height:14px;vertical-align:middle;margin-right:4px' viewBox='0 0 24 24'><path fill='#374151' d='M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z'/></svg>Built with Android Code Studio (ACS)</h3>
<p>A special thank you to the <b>ACS team</b> for building such an amazing Android IDE that runs directly on Android devices. InstantLive Server was entirely built using ACS.</p>
<p style="color:#6B7280;font-size:12px;">Visit: <b>androidide.com</b> &nbsp;|&nbsp; Author: AndroidIDE Team</p>
</div>
<div class="card">
<span class="badge">Apache 2.0 License</span>
<h3>AndroidX / Jetpack Libraries</h3>
<p>Core, AppCompat, Activity — standard Android support libraries by Google.</p>
<p style="color:#6B7280;font-size:12px;">© The Android Open Source Project</p>
</div>
<div class="card">
<span class="badge">Apache 2.0 License</span>
<h3>Material Components for Android</h3>
<p>UI components following Material Design guidelines.</p>
<p style="color:#6B7280;font-size:12px;">© Google LLC</p>
</div>
<p style="color:#9CA3AF;font-size:12px;text-align:center;margin-top:16px;">InstantLive Server v1.4.0 (Build 5) &nbsp;•&nbsp; All original code © TechSetuApps 2026</p>
</body></html>"""

fun MainActivity.showHelpPage() {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
    }
    // Header
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(16.dp(), 22.dp(), 16.dp(), 8.dp())
        elevation = 2.dp().toFloat()
    }
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
        val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(16.dp(), top + 22.dp(), 16.dp(), 8.dp())
        insets
    }
    header.addView(TextView(this).apply {
        text = "Help"
        setTextColor(0xFF111827.toInt())
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    })
    header.addView(android.widget.ImageView(this).apply {
        setImageDrawable(drawIconClose(0xFF6B7280.toInt()))
        setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
        layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp())
        setOnClickListener { closeFullScreenOverlay() }
    })
    root.addView(header)

    val wv = android.webkit.WebView(this)
    wv.settings.javaScriptEnabled = true
    wv.layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
    wv.loadDataWithBaseURL(null, htmlHelp(), "text/html", "UTF-8", null)
    root.addView(wv)
    showFullScreenOverlay(root, cancelable = true, tag = "help")
}

fun MainActivity.showSupportPage() {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(16.dp(), 28.dp(), 12.dp(), 14.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        elevation = 2.dp().toFloat()
    }
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
        val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(16.dp(), top + 28.dp(), 12.dp(), 14.dp())
        insets
    }
    header.addView(TextView(this).apply {
        text = "Support Us"
        setTextColor(0xFF111827.toInt())
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    })
    header.addView(android.widget.ImageView(this).apply {
        setImageDrawable(drawIconClose(0xFF6B7280.toInt()))
        setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
        layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp())
        setOnClickListener { closeFullScreenOverlay() }
    })
    root.addView(header)

    val wv = android.webkit.WebView(this)
    wv.settings.javaScriptEnabled = true
    wv.layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
    wv.webViewClient = object : android.webkit.WebViewClient() {
        override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            return true
        }
    }
    wv.loadDataWithBaseURL(null, htmlSupport(), "text/html", "UTF-8", null)
    root.addView(wv)
    showFullScreenOverlay(root, cancelable = true, tag = "support")
}

fun MainActivity.showSettingsPage() {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFF9FAFB.toInt())
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
    }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(16.dp(), 28.dp(), 12.dp(), 14.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        elevation = 2.dp().toFloat()
    }
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
        val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(16.dp(), top + 28.dp(), 12.dp(), 14.dp())
        insets
    }
    header.addView(TextView(this).apply {
        text = "Settings"
        setTextColor(0xFF111827.toInt())
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
    })
    header.addView(android.widget.ImageView(this).apply {
        setImageDrawable(drawIconClose(0xFF6B7280.toInt()))
        setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
        layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp())
        setOnClickListener { closeFullScreenOverlay() }
    })
    root.addView(header)

    val scroll = android.widget.ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.dp(), 16.dp(), 20.dp(), 40.dp())
    }

    // ── Server Section ──
    content.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 12.dp() }
        addView(View(context).apply {
            val s = 20.dp()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 8.dp() }
            background = drawRoundRect(0xFF007AFF.toInt(), 10.dp().toFloat())
        })
        addView(TextView(context).apply {
            text = "SERVER"; setTextColor(0xFF6B7280.toInt())
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
        })
    })

    val portCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = drawRoundRect(0xFFFFFFFF.toInt(), 14.dp().toFloat())
        setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        elevation = 2.dp().toFloat()
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 24.dp() }
    }
    portCard.addView(TextView(this).apply {
        text = "Host Port"
        setTextColor(0xFF111827.toInt())
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, 4.dp())
    })
    portCard.addView(TextView(this).apply {
        text = "Port on which InstantLive Server serves your file. Default: 7090"
        setTextColor(0xFF6B7280.toInt())
        textSize = 12f
        setPadding(0, 0, 0, 14.dp())
    })

    val portRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    val portEdit = android.widget.EditText(this).apply {
        setText(prefs.getInt("host_port", PORT).toString())
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(android.text.InputFilter.LengthFilter(5))
        textSize = 16f; typeface = Typeface.MONOSPACE
        setTextColor(0xFF111827.toInt())
        background = drawRoundRect(0xFFF3F4F6.toInt(), 10.dp().toFloat())
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = 12.dp() }
    }
    portRow.addView(portEdit)
    portRow.addView(TextView(this).apply {
        text = "Save"
        setTextColor(0xFFFFFFFF.toInt())
        background = drawRoundRect(0xFF2563EB.toInt(), 10.dp().toFloat())
        setPadding(22.dp(), 12.dp(), 22.dp(), 12.dp())
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isClickable = true
        isFocusable = true
        setOnClickListener {
            val p = portEdit.text.toString().trim().toIntOrNull()
            if (p != null && p in 1024..65535) {
                prefs.edit().putInt("host_port", p).apply()
                toast("Port saved! Restart hosting to apply.")
                closeFullScreenOverlay()
            } else {
                toast("Please enter a valid port (1024-65535)")
            }
        }
    })
    portCard.addView(portRow)
    content.addView(portCard)

    // ── Console Section ──
    content.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 12.dp() }
        addView(View(context).apply {
            val s = 20.dp()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 8.dp() }
            background = drawRoundRect(0xFF059669.toInt(), 10.dp().toFloat())
        })
        addView(TextView(context).apply {
            text = "CONSOLE"; setTextColor(0xFF6B7280.toInt())
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
        })
    })

    val savedConsole = prefs.getString("console_type", "legacy") ?: "legacy"
    val consoleCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = drawRoundRect(0xFFFFFFFF.toInt(), 14.dp().toFloat())
        setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        elevation = 2.dp().toFloat()
    }
    consoleCard.addView(TextView(this).apply {
        text = "Console Type"
        setTextColor(0xFF111827.toInt())
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, 4.dp())
    })
    consoleCard.addView(TextView(this).apply {
        text = "Choose which console is injected into your live preview."
        setTextColor(0xFF6B7280.toInt())
        textSize = 12f
        setPadding(0, 0, 0, 16.dp())
    })

    val rg = android.widget.RadioGroup(this).apply {
        orientation = android.widget.RadioGroup.VERTICAL
    }
    var checkedId = -1
    listOf("legacy" to "Legacy Console (Default)", "eruda" to "Eruda Console").forEachIndexed { index, (value, label) ->
        val rb = android.widget.RadioButton(this).apply {
            id = index + 1  // IDs: 1 and 2
            text = label
            tag = value
            isChecked = (savedConsole == value)
            if (isChecked) checkedId = id
            textSize = 14f
            setTextColor(0xFF374151.toInt())
            setPadding(8.dp(), 10.dp(), 8.dp(), 10.dp())
        }
        rg.addView(rb)
    }
    if (checkedId != -1) rg.check(checkedId)
    rg.setOnCheckedChangeListener { group, id ->
        val rb = group.findViewById<android.widget.RadioButton>(id)
        val v = rb?.tag?.toString() ?: "legacy"
        prefs.edit().putString("console_type", v).apply()
    }
    consoleCard.addView(rg)
    content.addView(consoleCard)

    scroll.addView(content)
    root.addView(scroll)
    showFullScreenOverlay(root, cancelable = true, tag = "settings")
}

fun MainActivity.showExitConfirmation() {
    val overlayBg = android.widget.FrameLayout(this).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(MATCH, MATCH)
        setBackgroundColor(0x99000000.toInt()) // 60% black scrim
    }
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        background = drawRoundRect(0xFFFFFFFF.toInt(), 20.dp().toFloat())
        setPadding(28.dp(), 28.dp(), 28.dp(), 20.dp())
        elevation = 24.dp().toFloat()
        layoutParams = android.widget.FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.82).toInt(), WRAP
        ).apply { gravity = Gravity.CENTER }
    }
    // Title
    card.addView(TextView(this).apply {
        text = "Exit InstantLive Server?"
        setTextColor(0xFF111827.toInt())
        textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 8.dp() }
    })
    // Message
    card.addView(TextView(this).apply {
        text = "Hosting will be stopped and the app will close."
        setTextColor(0xFF6B7280.toInt())
        textSize = 13.5f; gravity = Gravity.CENTER
        setLineSpacing(0f, 1.5f)
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 24.dp() }
    })
    // Buttons row
    val btnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }
    btnRow.addView(TextView(this).apply {
        text = "Cancel"
        setTextColor(0xFF374151.toInt()); textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = drawRoundRect(0xFFF3F4F6.toInt(), 12.dp().toFloat())
        setPadding(24.dp(), 13.dp(), 24.dp(), 13.dp())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = 10.dp() }
        isClickable = true; isFocusable = true
        setOnClickListener { closeFullScreenOverlay() }
    })
    btnRow.addView(TextView(this).apply {
        text = "Exit"
        setTextColor(0xFFFFFFFF.toInt()); textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = drawRoundRect(0xFFDC2626.toInt(), 12.dp().toFloat())
        setPadding(24.dp(), 13.dp(), 24.dp(), 13.dp())
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = 10.dp() }
        isClickable = true; isFocusable = true
        setOnClickListener { closeFullScreenOverlay(); stopHosting(); finishAffinity() }
    })
    card.addView(btnRow)
    overlayBg.addView(card)
    // Nav bar inset for card
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(overlayBg) { v, insets ->
        val sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
        insets
    }
    showFullScreenOverlay(overlayBg, cancelable = true, tag = "exit")
}

fun MainActivity.htmlHelp() = """<!DOCTYPE html><html><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#F9FAFB;color:#111827;font-size:14px;line-height:1.7;padding:20px 16px 60px;}
h1{font-size:20px;font-weight:800;color:#111827;margin-bottom:4px;}
.sub{color:#6B7280;font-size:12px;margin-bottom:24px;}
.section{margin-bottom:16px;}
.section-title{font-size:13px;font-weight:700;color:#6B7280;letter-spacing:.06em;text-transform:uppercase;margin-bottom:10px;padding-left:4px;}
.card{background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.07);}
.faq-item{border-bottom:1px solid #F3F4F6;}
.faq-item:last-child{border-bottom:none;}
.faq-q{padding:16px;font-size:14px;font-weight:600;color:#111827;cursor:pointer;display:flex;justify-content:space-between;align-items:center;user-select:none;}
.faq-a{padding:0 16px 16px;font-size:13px;color:#374151;line-height:1.7;display:none;}
.faq-a.open{display:block;}
.arrow{color:#9CA3AF;font-size:18px;transition:transform .2s;}
.arrow.open{transform:rotate(180deg);}
.bug-card{background:#fff;border-radius:12px;padding:20px;box-shadow:0 1px 3px rgba(0,0,0,0.07);}
.bug-card p{color:#374151;font-size:13px;margin-bottom:16px;line-height:1.7;}
.btn{display:block;background:#2563EB;color:#fff;text-align:center;padding:13px;border-radius:10px;font-weight:700;font-size:14px;text-decoration:none;}
</style></head><body>
<h1>Help Center</h1>
<p class="sub">Frequently Asked Questions & Bug Reports</p>

<div class="section">
<div class="section-title">Frequently Asked Questions</div>
<div class="card">
  <div class="faq-item">
    <div class="faq-q" onclick="toggle(this)">Will hosting my file cause copyright issues? <span class="arrow">&#8964;</span></div>
    <div class="faq-a">Absolutely not. InstantLive Server is simply a tool that serves your HTML file on your own device. You remain the sole owner of all your code and content. InstantLive Server does not upload, share, or distribute anything.</div>
  </div>
  <div class="faq-item">
    <div class="faq-q" onclick="toggle(this)">Does InstantLive Server inject any code into my files? <span class="arrow">&#8964;</span></div>
    <div class="faq-a">Yes — InstantLive Server temporarily injects a small live-reload script and optionally a console script (Eruda or Legacy) into the served output. This injection only happens in the virtual RAM-based copy and does NOT modify your original files on disk. Your source files remain completely untouched.</div>
  </div>
  <div class="faq-item">
    <div class="faq-q" onclick="toggle(this)">Why is the preview not loading? <span class="arrow">&#8964;</span></div>
    <div class="faq-a">Make sure you have selected a file and tapped "Start Hosting". If using the ACode plugin, ensure InstantLive Server is running and the correct port is set in the plugin.</div>
  </div>
  <div class="faq-item">
    <div class="faq-q" onclick="toggle(this)">Can I use a custom port? <span class="arrow">&#8964;</span></div>
    <div class="faq-a">Yes! Go to Menu → Settings → Host Port and enter your preferred port number (1024–65535). The default port is 7090.</div>
  </div>
  <div class="faq-item">
    <div class="faq-q" onclick="toggle(this)">What is the difference between Legacy and Eruda console? <span class="arrow">&#8964;</span></div>
    <div class="faq-a">Legacy Console is the built-in basic console available in InstantLive Server's output panel. Eruda is a powerful mobile browser debugging tool with advanced features. Both are available only in InstantLive Server's output panel — not in external browsers.</div>
  </div>
  <div class="faq-item">
    <div class="faq-q" onclick="toggle(this)">Is my data safe? <span class="arrow">&#8964;</span></div>
    <div class="faq-a">Yes. InstantLive Server does not collect or transmit any personal data. All file serving happens locally on your device. See our Privacy Policy for full details.</div>
  </div>
</div>
</div>

<div class="section">
<div class="section-title">Report Troubleshooting / Bugs</div>
<div class="bug-card">
  <p>If you have encountered a bug or are facing any issue with InstantLive Server, please let us know. Tap the button below and describe the problem — we will work on a fix as soon as possible.</p>
  <p><strong>Thank you for helping us improve InstantLive Server!</strong></p>
  <a class="btn" href="mailto:techsetuapps@gmail.com?subject=Troubleshooting%2FBug%20Report%20-%20InstantLive Server"><svg width='16' height='16' viewBox='0 0 24 24' fill='white'><path d='M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z'/></svg> Report a Bug</a>
</div>
</div>

<script>
function toggle(el){
  var a=el.nextElementSibling;
  var arrow=el.querySelector('.arrow');
  if(a.classList.contains('open')){a.classList.remove('open');arrow.classList.remove('open');}
  else{a.classList.add('open');arrow.classList.add('open');}
}
</script>
</body></html>"""

fun MainActivity.htmlSupport() = """<!DOCTYPE html><html><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#F9FAFB;color:#111827;font-size:14px;line-height:1.7;padding:20px 16px 60px;}
h1{font-size:20px;font-weight:800;color:#111827;margin-bottom:4px;}
.sub{color:#6B7280;font-size:12px;margin-bottom:24px;}
.card{background:#fff;border-radius:12px;padding:20px;box-shadow:0 1px 3px rgba(0,0,0,0.07);margin-bottom:16px;}
.card h2{font-size:15px;font-weight:700;color:#111827;margin-bottom:8px;}
.card p{color:#374151;font-size:13px;margin-bottom:14px;line-height:1.7;}
.btn{display:flex;align-items:center;justify-content:center;gap:10px;background:#2563EB;color:#fff;text-align:center;padding:13px;border-radius:10px;font-weight:700;font-size:14px;text-decoration:none;margin-bottom:10px;}
.btn-gh{background:#24292E;}
.btn-yt{background:#FF0000;}
.section-title{font-size:13px;font-weight:700;color:#6B7280;letter-spacing:.06em;text-transform:uppercase;margin-bottom:10px;margin-top:24px;padding-left:4px;}
</style></head><body>
<h1>Support Us</h1>
<p class="sub">Your support helps us keep improving InstantLive Server</p>

<div class="card">
  <h2><svg style='width:16px;height:16px;vertical-align:middle;margin-right:4px' viewBox='0 0 24 24'><path fill='#111827' d='M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z'/></svg>Send Feedback</h2>
  <p>Have a suggestion or feature request? We would love to hear from you. Tap below to send us your feedback directly.</p>
  <a class="btn" href="mailto:techsetuapps@gmail.com?subject=InstantLive Server_Feedback"><svg width='16' height='16' viewBox='0 0 24 24' fill='white'><path d='M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z'/></svg> Send Feedback</a>
</div>

<div class="section-title">Connect With Us</div>
<a class="btn btn-gh" href="https://github.com/TechSetuApps">
  <svg width="20" height="20" viewBox="0 0 24 24" fill="white"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.3 3.44 9.8 8.2 11.38.6.1.82-.26.82-.58v-2.03c-3.34.72-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.09-.75.08-.73.08-.73 1.2.08 1.84 1.24 1.84 1.24 1.07 1.83 2.8 1.3 3.49 1 .1-.78.42-1.3.76-1.6-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.14-.3-.54-1.52.1-3.18 0 0 1.01-.32 3.3 1.23a11.5 11.5 0 0 1 3-.4c1.02.005 2.04.14 3 .4 2.28-1.55 3.29-1.23 3.29-1.23.64 1.66.24 2.88.12 3.18.77.84 1.23 1.91 1.23 3.22 0 4.61-2.8 5.63-5.48 5.92.43.37.81 1.1.81 2.22v3.29c0 .32.22.69.83.57C20.57 21.8 24 17.3 24 12c0-6.63-5.37-12-12-12z"/></svg>
  GitHub — TechSetuApps
</a>
<a class="btn btn-yt" href="https://youtube.com/@techsetuappsofficial">
  <svg width="20" height="20" viewBox="0 0 24 24" fill="white"><path d="M23.5 6.2s-.2-1.6-1-2.3c-.9-1-1.9-1-2.4-1C17.1 2.8 12 2.8 12 2.8s-5.1 0-8.1.1c-.5.1-1.5.1-2.4 1-.7.7-1 2.3-1 2.3S.4 8 .4 9.8v1.7c0 1.8.1 3.6.1 3.6s.3 1.6 1 2.3c.9 1 2.1.9 2.6 1C5.6 18.6 12 18.6 12 18.6s5.1 0 8.1-.2c.5-.1 1.5-.1 2.4-1 .7-.7 1-2.3 1-2.3s.1-1.8.1-3.6V9.8c0-1.8-.1-3.6-.1-3.6zM9.7 14.5V8.1l6.6 3.2-6.6 3.2z"/></svg>
  YouTube — TechSetuApps
</a>

</body></html>"""

fun MainActivity.htmlPlaceholder() = """
<!DOCTYPE html><html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;
  background:#F7F8FA;min-height:100vh;
  display:flex;align-items:center;justify-content:center;}
.box{text-align:center;padding:32px 24px;max-width:320px;}
.ring{width:80px;height:80px;border-radius:50%;
  border:3px solid #E5E7EB;background:#fff;
  margin:0 auto 20px;display:flex;align-items:center;justify-content:center;
  box-shadow:0 2px 12px rgba(0,0,0,0.07);}
h3{font-size:18px;font-weight:700;color:#374151;margin-bottom:10px;}
p{font-size:13px;color:#9CA3AF;line-height:1.7;margin-bottom:16px;}
.card{background:#fff;border-radius:14px;padding:14px 16px;margin-bottom:10px;
  text-align:left;box-shadow:0 1px 4px rgba(0,0,0,0.07);}
.card .role{font-size:10px;font-weight:700;color:#007AFF;letter-spacing:.06em;margin-bottom:4px;}
.card .desc{font-size:12px;color:#374151;line-height:1.5;}
</style></head>
<body><div class="box">
<div class="ring">
<svg width="34" height="34" viewBox="0 0 34 34" fill="none">
  <polyline points="4,11 9,29 17,14 25,29 30,11"
    stroke="#D1D5DB" stroke-width="3.5"
    stroke-linecap="round" stroke-linejoin="round"/>
</svg>
</div>
<h3>Live Preview Window</h3>
<p>Your hosted HTML will appear here</p>
<div class="card">
  <div class="role">HOST DEVICE</div>
  <div class="desc">Select file → Start Hosting → OUTPUT tab opens preview automatically</div>
</div>
<div class="card">
  <div class="role">VIEWER DEVICE</div>
  <div class="desc">Connect to host's Hotspot → tap <b>View Live Output</b></div>
</div>
</div></body></html>""".trimIndent()

fun MainActivity.htmlError(msg: String) = """
<!DOCTYPE html><html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:-apple-system,sans-serif;background:#FEF2F2;min-height:100vh;
  display:flex;align-items:center;justify-content:center;}
.box{text-align:center;padding:36px 24px;max-width:300px;}
.ic{width:64px;height:64px;border-radius:50%;background:#FEE2E2;
  margin:0 auto 16px;display:flex;align-items:center;justify-content:center;}
h3{font-size:16px;font-weight:700;color:#B91C1C;margin-bottom:10px;}
p{font-size:12.5px;color:#6B7280;line-height:1.7;text-align:left;}
</style></head>
<body><div class="box">
<div class="ic">
<svg width="30" height="30" viewBox="0 0 30 30" fill="none">
  <circle cx="15" cy="15" r="13" stroke="#EF4444" stroke-width="2.5"/>
  <line x1="15" y1="8" x2="15" y2="17" stroke="#EF4444" stroke-width="2.5" stroke-linecap="round"/>
  <circle cx="15" cy="22" r="1.8" fill="#EF4444"/>
</svg>
</div>
<h3>Connection Error</h3>
<p>$msg</p>
</div></body></html>""".trimIndent()

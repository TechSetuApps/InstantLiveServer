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
//  MainActivityDraw — Drawables & Helpers — all drawXxx functions, UI helpers, WebView factory
// ================================================================

@SuppressLint("SetJavaScriptEnabled")
fun MainActivity.makeWebView() = WebView(this).apply {
    settings.apply {
        javaScriptEnabled    = true
        domStorageEnabled    = true
        allowFileAccess      = true
        useWideViewPort      = true
        loadWithOverviewMode = true
        textZoom             = 100
        @Suppress("DEPRECATION")
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
    // Capture JS console logs
    webChromeClient = object : android.webkit.WebChromeClient() {
        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
            val level = when (msg.messageLevel()) {
                android.webkit.ConsoleMessage.MessageLevel.ERROR   -> "[ERR]"
                android.webkit.ConsoleMessage.MessageLevel.WARNING -> "[WARN]"
                android.webkit.ConsoleMessage.MessageLevel.LOG     -> "[LOG]"
                android.webkit.ConsoleMessage.MessageLevel.DEBUG   -> "[DBG]"
                else                                               -> "[LOG]"
            }
            val line = "$level ${msg.message()}  (${msg.sourceId().substringAfterLast("/")}:${msg.lineNumber()})"
            this@makeWebView.consoleLogs.add(line)
            runOnUiThread { this@makeWebView.updateConsoleView() }
            return true
        }
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (this@makeWebView.isTvPageTitleReady && newProgress < 100) {
                if (this@makeWebView.isHosting && this@makeWebView.hostUrl.isNotEmpty()) this@makeWebView.tvPageTitle.text = this@makeWebView.hostUrl
                else this@makeWebView.tvPageTitle.text = "Loading… $newProgress%"
            }
        }
    }
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // Show "Live Preview" for data: URLs and about:blank
            val isRealPage = !url.isNullOrEmpty()
                && !url.startsWith("data:")
                && !url.startsWith("about:")
            val displayTitle = if (this@makeWebView.isHosting && this@makeWebView.hostUrl.isNotEmpty()) {
                this@makeWebView.hostUrl
            } else if (isRealPage) {
                view?.title?.takeIf { it.isNotBlank() } ?: "Live Preview"
            } else {
                "Live Preview"
            }
            if (this@makeWebView.isTvPageTitleReady) this@makeWebView.tvPageTitle.text = displayTitle
            if (this@makeWebView.isTvFsTitleReady)   this@makeWebView.tvFsTitle.text   = displayTitle

            // Viewer: if page genuinely loaded (real HTTP, not data/error page)
            // extract IP from URL and set as verified IP
            if (isRealPage && !this@makeWebView.isHosting && url!!.startsWith("http")) {
                try {
                    val host = java.net.URL(url).host
                    if (host != "127.0.0.1" && host != "localhost") {
                        this@makeWebView.viewerVerifiedIP = host
                        this@makeWebView.viewerAutoTryRunning = false  // Found — stop auto-try
                        if (this@makeWebView.isTvConnStatusReady) {
                            this@makeWebView.tvConnStatus.text = "Host found at $host"
                            this@makeWebView.tvConnStatus.setTextColor(0xFF16A34A.toInt())
                            this@makeWebView.dotConn.background = this@makeWebView.drawCircle(0xFF16A34A.toInt())
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        override fun onReceivedError(
            v: WebView?, req: WebResourceRequest?, err: WebResourceError?
        ) {
            // Only show error for main frame, skip sub-resources
            if (req?.isForMainFrame == true) {
                v?.loadData(
                    this@makeWebView.htmlError(
                        "Cannot reach the server.<br><br>" +
                        "• Is hosting active on the host device?<br>" +
                        "• If viewing: are you connected to the host's hotspot?<br>" +
                        "• Tap <b>View Live Output</b> again."
                    ),
                    "text/html", "UTF-8"
                )
            }
        }
    }
}

fun MainActivity.drawIconQuestion(color: Int): android.graphics.drawable.Drawable {
    val bg = color and 0x1AFFFFFF or ((color ushr 24) * 0x1A / 0xFF shl 24)
    return object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: Canvas) {
            val r = bounds
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // Background circle
            p.color = color and 0x00FFFFFF or 0x1A000000
            canvas.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), 10f, 10f, p)
            // Question mark
            p.color = color
            p.textSize = r.height() * 0.52f
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            val x = r.exactCenterX()
            val y = r.exactCenterY() - (p.descent() + p.ascent()) / 2
            canvas.drawText("?", x, y, p)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(f: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

fun MainActivity.drawIconHeart(color: Int): android.graphics.drawable.Drawable {
    return object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: Canvas) {
            val r = bounds
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = color and 0x00FFFFFF or 0x1A000000
            canvas.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), 10f, 10f, p)
            // Draw heart shape using path
            p.color = color
            p.style = Paint.Style.FILL
            val cx = r.exactCenterX(); val cy = r.exactCenterY()
            val s = r.height() * 0.20f
            val path = Path()
            path.moveTo(cx, cy + s * 1.4f)
            path.cubicTo(cx - s * 2.2f, cy + s * 0.2f, cx - s * 2.2f, cy - s * 1.4f, cx, cy - s * 0.4f)
            path.cubicTo(cx + s * 2.2f, cy - s * 1.4f, cx + s * 2.2f, cy + s * 0.2f, cx, cy + s * 1.4f)
            path.close()
            canvas.drawPath(path, p)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(f: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

fun MainActivity.drawIconGear(color: Int): android.graphics.drawable.Drawable {
    return object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: Canvas) {
            val r = bounds
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = color and 0x00FFFFFF or 0x1A000000
            canvas.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), 10f, 10f, p)
            // Draw gear shape using path
            p.color = color
            p.style = Paint.Style.FILL
            val cx = r.exactCenterX(); val cy = r.exactCenterY()
            val outerR = r.height() * 0.22f
            val innerR = r.height() * 0.12f
            val teeth = 6
            val path = Path()
            for (i in 0 until teeth) {
                val a1 = Math.toRadians((360.0 / teeth) * i - 90.0)
                val a2 = Math.toRadians((360.0 / teeth) * (i + 0.5) - 90.0)
                val a3 = Math.toRadians((360.0 / teeth) * (i + 1.0) - 90.0)
                if (i == 0) path.moveTo(cx + outerR * Math.cos(a1).toFloat(), cy + outerR * Math.sin(a1).toFloat())
                path.lineTo(cx + outerR * Math.cos(a2).toFloat(), cy + outerR * Math.sin(a2).toFloat())
                path.lineTo(cx + innerR * Math.cos(a2).toFloat(), cy + innerR * Math.sin(a2).toFloat())
                path.lineTo(cx + innerR * Math.cos(a3).toFloat(), cy + innerR * Math.sin(a3).toFloat())
                path.lineTo(cx + outerR * Math.cos(a3).toFloat(), cy + outerR * Math.sin(a3).toFloat())
            }
            path.close()
            canvas.drawPath(path, p)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(f: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

fun MainActivity.drawIconDoor(color: Int): android.graphics.drawable.Drawable {
    return object : android.graphics.drawable.Drawable() {
        override fun draw(canvas: Canvas) {
            val r = bounds
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = color and 0x00FFFFFF or 0x1A000000
            canvas.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), 10f, 10f, p)
            // Draw exit arrow
            p.color = color
            p.strokeWidth = r.height() * 0.1f
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            val cx = r.exactCenterX(); val cy = r.exactCenterY()
            val s = r.height() * 0.25f
            // Arrow line
            canvas.drawLine(cx - s, cy, cx + s * 0.6f, cy, p)
            // Arrow head
            canvas.drawLine(cx, cy - s * 0.6f, cx + s * 0.6f, cy, p)
            canvas.drawLine(cx, cy + s * 0.6f, cx + s * 0.6f, cy, p)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(f: android.graphics.ColorFilter?) {}
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}

fun MainActivity.mkCard() = LinearLayout(this).apply {
    orientation  = LinearLayout.VERTICAL
    background   = drawRoundRect(Color.WHITE, 16.dp().toFloat())
    setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
    elevation    = 2.dp().toFloat()
    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 10.dp() }
}

fun MainActivity.mkSectionLabel(text: String) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    setPadding(0, 16.dp(), 0, 6.dp())
    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    // Extract step number if present (e.g., "Step 1 — ...")
    val stepMatch = Regex("^Step (\\d+)").find(text)
    if (stepMatch != null) {
        val num = stepMatch.groupValues[1]
        addView(TextView(context).apply {
            this.text = num; gravity = Gravity.CENTER
            textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            background = drawRoundRect(0xFF2563EB.toInt(), 10.dp().toFloat())
            val s = 20.dp()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = 8.dp() }
        })
    }
    addView(TextView(context).apply {
        // Remove "Step X — " prefix if numbered, keep rest
        val displayText = if (stepMatch != null) {
            text.replaceFirst(Regex("^Step \\d+\\s*[-—]\\s*"), "")
        } else text
        this.text = displayText.uppercase()
        textSize = 10f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF6B7280.toInt()); letterSpacing = 0.06f
    })
}

fun MainActivity.mkRowBtn(label: String, icon: Drawable, action: () -> Unit) =
    LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background   = drawRoundRect(Color.WHITE, 16.dp().toFloat())
        setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp()); elevation = 2.dp().toFloat()
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 10.dp() }
        isClickable = true; isFocusable = true
        // Touch feedback
        val origBg = background
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.background = drawRoundRect(0xFFF3F4F6.toInt(), 16.dp().toFloat())
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.background = origBg
                }
            }
            false
        }
        addView(mkIcon(26.dp(), icon).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = 12.dp()
        })
        addView(TextView(context).apply {
            text = label; setTextColor(0xFF007AFF.toInt()); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        addView(mkIcon(16.dp(), drawChevronRight(0xFFC7C7CC.toInt())))
        setOnClickListener { action() }
    }

fun MainActivity.mkOutlineBtn(label: String, icon: Drawable, action: () -> Unit) =
    LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        background   = drawOutlineRect(0xFF007AFF.toInt(), 13.dp().toFloat())
        setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = 8.dp() }
        isClickable = true; isFocusable = true
        // Ripple feedback
        val origBg = background
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.background = drawRoundRect(0x0D007AFF.toInt(), 13.dp().toFloat())
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.background = origBg
                }
            }
            false
        }
        addView(mkIcon(22.dp(), icon).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = 10.dp()
        })
        addView(TextView(context).apply {
            text = label; setTextColor(0xFF007AFF.toInt())
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        })
        setOnClickListener { action() }
    }

fun MainActivity.mkIcon(size: Int, d: Drawable) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(size, size); background = d
}

fun MainActivity.drawRoundRect(color: Int, r: Float): Drawable = object : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    override fun draw(c: Canvas) = c.drawRoundRect(RectF(bounds), r, r, p)
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawOutlineRect(color: Int, r: Float): Drawable = object : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    override fun draw(c: Canvas) = c.drawRoundRect(
        RectF(bounds.left+1f, bounds.top+1f, bounds.right-1f, bounds.bottom-1f), r, r, p)
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawCircle(color: Int): Drawable = object : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        alpha = 80
    }
    private val isActive = (color == 0xFF16A34A.toInt()) // green = active

    override fun draw(c: Canvas) {
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        val r = minOf(bounds.width(), bounds.height()) / 2f

        if (isActive) {
            // Outer subtle ring
            outerRingPaint.color = color
            c.drawCircle(cx, cy, r - 0.5f, outerRingPaint)
            // Inner fill (smaller)
            c.drawCircle(cx, cy, r - 3f, fillPaint)
            // White border between outer ring and inner dot
            c.drawCircle(cx, cy, r - 2.5f, strokePaint)
        } else {
            // Inactive — plain circle
            c.drawCircle(cx, cy, r, fillPaint)
        }
    }
    override fun setAlpha(a: Int) { fillPaint.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { fillPaint.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawLogo(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRoundRect(RectF(b), b.width()*0.22f, b.width()*0.22f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF007AFF.toInt() })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE
            strokeWidth = b.width()*0.10f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val l=b.left+b.width()*0.20f; val r=b.right-b.width()*0.20f
        val t=b.top+b.height()*0.24f; val bt=b.bottom-b.height()*0.22f
        val mid=b.centerX().toFloat()
        val path=Path()
        path.moveTo(l,t); path.lineTo(l+(mid-l)*0.44f,bt)
        path.lineTo(mid,t+(bt-t)*0.38f); path.lineTo(r-(r-mid)*0.44f,bt); path.lineTo(r,t)
        c.drawPath(path,p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawHotspot(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b=bounds; val cx=b.centerX().toFloat(); val cy=b.centerY().toFloat()+b.height()*0.06f
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=color; style=Paint.Style.STROKE
            strokeWidth=b.width()*0.10f; strokeCap=Paint.Cap.ROUND
        }
        for(mul in listOf(0.35f,0.58f,0.82f)) {
            val rad=b.width()*mul/2f
            c.drawArc(RectF(cx-rad,cy-rad,cx+rad,cy+rad),207f,126f,false,p)
        }
        p.style=Paint.Style.FILL
        c.drawCircle(cx,cy+b.height()*0.24f,b.width()*0.08f,p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawWifi(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b=bounds; val cx=b.centerX().toFloat(); val cy=b.centerY().toFloat()+b.height()*0.10f
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=color; style=Paint.Style.STROKE
            strokeWidth=b.width()*0.10f; strokeCap=Paint.Cap.ROUND
        }
        for(mul in listOf(0.40f,0.65f,0.90f))
            c.drawArc(RectF(cx-b.width()*mul/2f,cy-b.width()*mul/2f,
                cx+b.width()*mul/2f,cy+b.width()*mul/2f),210f,120f,false,p)
        p.style=Paint.Style.FILL
        c.drawCircle(cx,cy+b.height()*0.20f,b.width()*0.085f,p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawServer(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b=bounds
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=0xFF007AFF.toInt(); style=Paint.Style.STROKE
            strokeWidth=b.width()*0.09f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
        }
        val l=b.left+b.width()*0.12f; val r=b.right-b.width()*0.12f
        val t=b.top+b.height()*0.08f; val bt=b.bottom-b.height()*0.08f; val mid=(t+bt)/2f
        c.drawRoundRect(RectF(l,t,r,mid-b.height()*0.04f),b.width()*0.08f,b.width()*0.08f,p)
        c.drawRoundRect(RectF(l,mid+b.height()*0.04f,r,bt),b.width()*0.08f,b.width()*0.08f,p)
        p.style=Paint.Style.FILL
        c.drawCircle(r-b.width()*0.14f,(t+mid-b.height()*0.04f)/2f,b.width()*0.07f,p)
        c.drawCircle(r-b.width()*0.14f,(mid+b.height()*0.04f+bt)/2f,b.width()*0.07f,p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawFile(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b=bounds
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=0xFF007AFF.toInt(); style=Paint.Style.STROKE
            strokeWidth=b.width()*0.09f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
        }
        val l=b.left+b.width()*0.14f; val r=b.right-b.width()*0.14f
        val t=b.top+b.height()*0.06f; val bt=b.bottom-b.height()*0.06f
        val fold=b.width()*0.28f
        val body=Path()
        body.moveTo(l,bt); body.lineTo(l,t+fold); body.lineTo(l+fold,t)
        body.lineTo(r,t); body.lineTo(r,bt); body.close()
        c.drawPath(body,p)
        val corner=Path()
        corner.moveTo(l,t+fold); corner.lineTo(l+fold,t+fold); corner.lineTo(l+fold,t)
        c.drawPath(corner,p)
        val gap=(bt-t-fold)/4f
        val lx=l+b.width()*0.10f; val rx=r-b.width()*0.08f
        for(i in 1..3) c.drawLine(lx,t+fold+gap*i,rx,t+fold+gap*i,p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawIconClose(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE
            strokeWidth = b.width() * 0.10f; strokeCap = Paint.Cap.ROUND
        }
        val pad = b.width() * 0.28f
        c.drawLine(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad, p)
        c.drawLine(b.right - pad, b.top + pad, b.left + pad, b.bottom - pad, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawFolder(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF007AFF.toInt(); style = Paint.Style.STROKE
            strokeWidth = b.width() * 0.09f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val l = b.left + b.width() * 0.08f; val r = b.right - b.width() * 0.08f
        val t = b.top + b.height() * 0.14f; val bt = b.bottom - b.height() * 0.08f
        val tabW = b.width() * 0.32f
        // Tab (back panel of folder)
        val tab = Path()
        tab.moveTo(l, t + b.height() * 0.08f)
        tab.lineTo(l + b.width() * 0.04f, t)
        tab.lineTo(l + tabW, t)
        tab.lineTo(l + tabW + b.width() * 0.06f, t + b.height() * 0.08f)
        c.drawPath(tab, p)
        // Body (front panel of folder)
        val body = Path()
        body.moveTo(l, t + b.height() * 0.08f)
        body.lineTo(r, t + b.height() * 0.08f)
        body.lineTo(r, bt)
        body.lineTo(l, bt)
        body.close()
        c.drawPath(body, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawRoundRectTop(color: Int, r: Float): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val b = RectF(bounds)
        c.drawRoundRect(b, r, r, p)
        c.drawRect(b.left, b.bottom - r, b.right, b.bottom, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawThinReload(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b  = bounds
        val cx = b.centerX().toFloat(); val cy = b.centerY().toFloat()
        val r  = b.width() * 0.40f
        val p  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9CA3AF.toInt(); style = Paint.Style.STROKE
            strokeWidth = b.width() * 0.10f; strokeCap = Paint.Cap.ROUND
        }
        // 300-degree arc
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 300f, false, p)
        // Small arrowhead at end of arc
        p.style = Paint.Style.FILL; p.strokeWidth = 0f
        val angle = Math.toRadians(210.0)
        val ax = cx + r * Math.cos(angle).toFloat()
        val ay = cy + r * Math.sin(angle).toFloat()
        val aw = b.width() * 0.10f
        val path = android.graphics.Path()
        path.moveTo(ax, ay - aw)
        path.lineTo(ax + aw * 1.4f, ay + aw)
        path.lineTo(ax - aw * 1.4f, ay + aw)
        path.close()
        c.drawPath(path, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawReload(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b  = bounds
        val cx = b.centerX().toFloat(); val cy = b.centerY().toFloat()
        val r  = b.width() * 0.38f
        val p  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9CA3AF.toInt(); style = Paint.Style.STROKE
            strokeWidth = b.width() * 0.13f; strokeCap = Paint.Cap.ROUND
        }
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -60f, 280f, false, p)
        p.style = Paint.Style.FILL
        val ax = cx + r * Math.cos(Math.toRadians(220.0)).toFloat()
        val ay = cy + r * Math.sin(Math.toRadians(220.0)).toFloat()
        c.drawCircle(ax, ay, b.width() * 0.12f, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawMenuDots(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds; val cx = b.centerX().toFloat(); val cy = b.centerY().toFloat()
        val r = b.width() * 0.09f; val gap = b.width() * 0.28f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9CA3AF.toInt() }
        c.drawCircle(cx - gap, cy, r, p); c.drawCircle(cx, cy, r, p); c.drawCircle(cx + gap, cy, r, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawLock(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b  = bounds
        val cx = b.centerX().toFloat()
        val w  = b.width().toFloat()
        val h  = b.height().toFloat()
        val p  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9CA3AF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = w * 0.11f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // Shackle (arc on top)
        val shackleL = b.left + w * 0.28f
        val shackleR = b.right - w * 0.28f
        val shackleB = b.top + h * 0.50f
        val shackleT = b.top + h * 0.08f
        c.drawArc(RectF(shackleL, shackleT, shackleR, shackleB), 180f, 180f, false, p)
        // Body (rounded rect bottom half)
        p.style = Paint.Style.FILL
        p.color = 0xFF9CA3AF.toInt()
        val bodyL = b.left + w * 0.15f
        val bodyT = b.top  + h * 0.44f
        val bodyR = b.right - w * 0.15f
        val bodyB = b.bottom - h * 0.08f
        val rad   = w * 0.12f
        c.drawRoundRect(RectF(bodyL, bodyT, bodyR, bodyB), rad, rad, p)
        // Keyhole dot
        p.color = 0xFF1C1C1E.toInt()
        c.drawCircle(cx, b.top + h * 0.65f, w * 0.09f, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawExpand(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b=bounds
        c.drawRoundRect(RectF(b),b.width()*0.22f,b.width()*0.22f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xCC000000.toInt() })
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.WHITE; style=Paint.Style.STROKE
            strokeWidth=b.width()*0.13f; strokeCap=Paint.Cap.ROUND
        }
        val m=b.width()*0.25f; val s=b.width()*0.15f
        val bL=b.left.toFloat(); val bT=b.top.toFloat()
        val bR=b.right.toFloat(); val bB=b.bottom.toFloat()
        c.drawLine(bL+m,bT+m,bL+m+s,bT+m,p); c.drawLine(bL+m,bT+m,bL+m,bT+m+s,p)
        c.drawLine(bR-m,bT+m,bR-m-s,bT+m,p); c.drawLine(bR-m,bT+m,bR-m,bT+m+s,p)
        c.drawLine(bL+m,bB-m,bL+m+s,bB-m,p); c.drawLine(bL+m,bB-m,bL+m,bB-m-s,p)
        c.drawLine(bR-m,bB-m,bR-m-s,bB-m,p); c.drawLine(bR-m,bB-m,bR-m,bB-m-s,p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawClose(): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b=bounds
        c.drawCircle(b.centerX().toFloat(),b.centerY().toFloat(),b.width()/2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xCC000000.toInt() })
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.WHITE; style=Paint.Style.STROKE
            strokeWidth=b.width()*0.13f; strokeCap=Paint.Cap.ROUND
        }
        val m=b.width()*0.30f
        c.drawLine(b.left+m,b.top+m,b.right-m,b.bottom-m,p)
        c.drawLine(b.right-m,b.top+m,b.left+m,b.bottom-m,p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawIconShield(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.centerX().toFloat()
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val l = b.left + w*0.20f; val r = b.right - w*0.20f
        val t = b.top + h*0.10f;  val bot = b.bottom - h*0.10f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(cx, t)
            lineTo(r, t + h*0.18f)
            lineTo(r, t + h*0.52f)
            quadTo(r, bot, cx, bot)
            quadTo(l, bot, l, t + h*0.52f)
            lineTo(l, t + h*0.18f)
            close()
        }
        c.drawPath(path, p)
        // Check mark inside
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.STROKE
            strokeWidth = w*0.11f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val checkPath = Path().apply {
            moveTo(cx - w*0.14f, b.centerY() + h*0.06f)
            lineTo(cx,           b.centerY() + h*0.20f)
            lineTo(cx + w*0.18f, b.centerY() - h*0.10f)
        }
        c.drawPath(checkPath, tp)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawIconDoc(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val l = b.left + w*0.18f; val r = b.right - w*0.18f
        val t = b.top + h*0.08f;  val bot = b.bottom - h*0.08f
        val fold = w*0.22f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        // Paper body
        val path = Path().apply {
            moveTo(l, t); lineTo(r - fold, t); lineTo(r, t + fold)
            lineTo(r, bot); lineTo(l, bot); close()
        }
        c.drawPath(path, p)
        // Fold triangle (lighter)
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; alpha = 80; style = Paint.Style.FILL
        }
        val foldPath = Path().apply {
            moveTo(r - fold, t); lineTo(r, t + fold); lineTo(r - fold, t + fold); close()
        }
        c.drawPath(foldPath, fp)
        // Lines on paper
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; alpha = 200; style = Paint.Style.STROKE
            strokeWidth = w*0.08f; strokeCap = Paint.Cap.ROUND
        }
        val lx1 = l + w*0.12f; val lx2 = r - w*0.18f
        val ly1 = t + h*0.42f; val ly2 = t + h*0.58f; val ly3 = t + h*0.73f
        c.drawLine(lx1, ly1, lx2, ly1, lp)
        c.drawLine(lx1, ly2, lx2, ly2, lp)
        c.drawLine(lx1, ly3, lx2 - w*0.10f, ly3, lp)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawIconWarning(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.centerX().toFloat()
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val t = b.top + h*0.08f; val bot = b.bottom - h*0.08f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val path = Path().apply {
            moveTo(cx, t)
            lineTo(b.right - w*0.10f, bot)
            quadTo(b.right - w*0.05f, bot, b.right - w*0.15f, bot)
            lineTo(b.left + w*0.15f, bot)
            quadTo(b.left + w*0.05f, bot, b.left + w*0.10f, bot)
            close()
        }
        // Rounded triangle
        val rPath = Path().apply {
            val rr = w*0.10f
            moveTo(cx, t + rr)
            lineTo(b.right - w*0.18f, bot - rr)
            lineTo(b.left + w*0.18f, bot - rr)
            close()
        }
        c.drawPath(rPath, p)
        // ! mark
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.FILL
        }
        val bx = w*0.08f; val by = h*0.10f
        c.drawRoundRect(RectF(cx-bx/2, b.top+h*0.38f, cx+bx/2, b.top+h*0.68f), bx/2, bx/2, ep)
        c.drawCircle(cx, b.top+h*0.78f, bx/2, ep)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawIconStar(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.centerX().toFloat(); val cy = b.centerY().toFloat()
        val outer = b.width() * 0.42f; val inner = b.width() * 0.18f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val path = Path()
        val points = 5
        for (i in 0 until points * 2) {
            val angle = (Math.PI * i / points - Math.PI / 2).toFloat()
            val r = if (i % 2 == 0) outer else inner
            val x = cx + r * Math.cos(angle.toDouble()).toFloat()
            val y = cy + r * Math.sin(angle.toDouble()).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawIconHamburger(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE
            strokeWidth = b.height() * 0.11f; strokeCap = Paint.Cap.ROUND
        }
        val lx = b.left + b.width() * 0.20f
        val rx = b.right - b.width() * 0.20f
        c.drawLine(lx, b.top  + b.height()*0.30f, rx, b.top  + b.height()*0.30f, p)
        c.drawLine(lx, b.centerY().toFloat(),      rx, b.centerY().toFloat(),      p)
        c.drawLine(lx, b.bottom - b.height()*0.30f, rx, b.bottom - b.height()*0.30f, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawChevronRight(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE
            strokeWidth = b.width() * 0.14f; strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val lx = b.left + b.width() * 0.30f
        val rx = b.right - b.width() * 0.30f
        val cy = b.centerY().toFloat()
        val halfH = b.height() * 0.28f
        c.drawLine(lx, cy - halfH, rx, cy, p)
        c.drawLine(lx, cy + halfH, rx, cy, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawCheckmark(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE
            strokeWidth = b.width() * 0.14f; strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        path.moveTo(b.left + b.width()*0.20f, b.centerY().toFloat())
        path.lineTo(b.left + b.width()*0.42f, b.bottom - b.height()*0.28f)
        path.lineTo(b.right - b.width()*0.20f, b.top + b.height()*0.28f)
        c.drawPath(path, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawInfoIcon(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.centerX().toFloat(); val cy = b.centerY().toFloat()
        val r = b.width() * 0.42f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        // Circle
        c.drawCircle(cx, cy, r, p)
        // "i" character
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.FILL
            textSize = b.height() * 0.52f; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val y = cy - (tp.descent() + tp.ascent()) / 2
        c.drawText("i", cx, y, tp)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun MainActivity.drawGearIcon(color: Int): Drawable = object : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.centerX().toFloat(); val cy = b.centerY().toFloat()
        val outerR = b.width() * 0.40f; val innerR = b.width() * 0.22f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.FILL
        }
        // 6-tooth gear
        val teeth = 6
        val path = android.graphics.Path()
        for (i in 0 until teeth) {
            val a0 = Math.toRadians(i * 360.0 / teeth - 90.0).toFloat()
            val a1 = Math.toRadians((i + 0.35) * 360.0 / teeth - 90.0).toFloat()
            val a2 = Math.toRadians((i + 0.65) * 360.0 / teeth - 90.0).toFloat()
            val a3 = Math.toRadians((i + 1.0) * 360.0 / teeth - 90.0).toFloat()
            if (i == 0) path.moveTo(cx + outerR * kotlin.math.cos(a0), cy + outerR * kotlin.math.sin(a0))
            path.lineTo(cx + outerR * kotlin.math.cos(a1), cy + outerR * kotlin.math.sin(a1))
            path.lineTo(cx + innerR * kotlin.math.cos(a1), cy + innerR * kotlin.math.sin(a1))
            path.lineTo(cx + innerR * kotlin.math.cos(a2), cy + innerR * kotlin.math.sin(a2))
            path.lineTo(cx + outerR * kotlin.math.cos(a2), cy + outerR * kotlin.math.sin(a2))
            path.lineTo(cx + outerR * kotlin.math.cos(a3), cy + outerR * kotlin.math.sin(a3))
        }
        path.close()
        c.drawPath(path, p)
        // Center hole
        c.drawCircle(cx, cy, innerR * 0.45f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; style = Paint.Style.FILL
        })
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

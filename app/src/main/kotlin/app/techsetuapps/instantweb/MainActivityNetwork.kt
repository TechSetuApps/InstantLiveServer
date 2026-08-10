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
//  MainActivityNetwork — Network & Polling — hotspot/WiFi/gateway IP, live reload, reachability, utils
// ================================================================

fun MainActivity.isHotspotOn(): Boolean = try {
    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val m  = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
    m.isAccessible = true; m.invoke(wm) as Boolean
} catch (_: Exception) { false }

fun MainActivity.isWifiOn(): Boolean =
    (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled

/**
 * Returns the best available hotspot/WiFi IP.
 * Excludes cellular interfaces (rmnet, ccmni, etc.).
 * Hotspot IP ranges: 192.168.x.x (classic), 172.x.x.x (newer Android)
 * NOTE: 10.x.x.x EXCLUDED — most common private range globally,
 * office/home WiFi routers also use it, could show wrong IP.
 */
fun MainActivity.getHostIP(): String {
    return try {
        // If both hotspot and WiFi are off — searching for IP is pointless
        if (!isHotspotOn() && !isWifiOn()) return "localhost"

        val allIPs = mutableListOf<Pair<String, String>>() // (ifaceName, ip)

        // Interfaces that are clearly cellular — exclude
        val cellularPrefixes = listOf(
            "rmnet", "ccmni", "v4-rmnet", "v6-rmnet",
            "tunl", "tun", "ppp", "dummy", "sit",
            "ip6tnl", "pdp", "lte", "data", "qmi",
            "svnet", "clat", "usb", "radio", "ifb"
        )
        // Interfaces that are hotspot/WiFi — prefer
        val hotspotIfacePrefixes = listOf(
            "wlan", "ap", "swlan", "wifi", "p2p", "rndis",
            "softap", "wl0", "wl1", "ap0", "vap"
        )

        val ifaces = NetworkInterface.getNetworkInterfaces()
        while (ifaces.hasMoreElements()) {
            val iface = ifaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val n = iface.name.lowercase()
            if (cellularPrefixes.any { n.startsWith(it) || n.contains(it) }) continue
            val addrs = iface.inetAddresses
            while (addrs.hasMoreElements()) {
                val a = addrs.nextElement()
                if (a.isLoopbackAddress || a !is Inet4Address) continue
                val ip = a.hostAddress ?: continue
                allIPs.add(Pair(n, ip))
            }
        }

        // Priority 1: Hotspot interface by name — any IP range
        val fromHotspotIface = allIPs.firstOrNull { (n, _) ->
            hotspotIfacePrefixes.any { n.startsWith(it) }
        }?.second

        // Priority 2: 192.168.43.x — classic Android hotspot
        val from43 = allIPs.firstOrNull { (_, ip) -> ip.startsWith("192.168.43.") }?.second

        // Priority 3: 192.168.x.x — common hotspot range
        val from192 = allIPs.firstOrNull { (_, ip) -> ip.startsWith("192.168.") }?.second

        // Priority 4: 172.x.x.x — newer Android hotspot
        val from172 = allIPs.firstOrNull { (_, ip) -> ip.startsWith("172.") }?.second

        // 10.x.x.x INTENTIONALLY EXCLUDED — highly ambiguous (office WiFi, VPN, etc.)

        val result = fromHotspotIface ?: from43 ?: from192 ?: from172

        result ?: "localhost"
    } catch (_: Exception) { "localhost" }
}

fun MainActivity.getGatewayIP(): String? {
    return try {
        val wm  = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val srv = wm.dhcpInfo?.serverAddress ?: return null
        if (srv == 0) return null
        val bytes = byteArrayOf(
            (srv and 0xFF).toByte(),
            (srv shr 8  and 0xFF).toByte(),
            (srv shr 16 and 0xFF).toByte(),
            (srv shr 24 and 0xFF).toByte()
        )
        InetAddress.getByAddress(bytes).hostAddress
    } catch (_: Exception) { null }
}

fun MainActivity.checkLiveReload() {
    if (!isHosting) return
    val uri = selectedUri ?: return
    val htmlName = getFileName(uri)

    Thread {
        try {
            // ── Folder mode: rescan entire folder for changes ──────
            if (isFolderMode && selectedFolderUri != null) {
                val treeUri = selectedFolderUri!!
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val files = scanFolder(treeUri, docId)

                // Find HTML file
                var htmlFile: Pair<Uri, String>? = null
                for (f in files) {
                    val name = f.second.lowercase()
                    if (name == "index.html") { htmlFile = f; break }
                }
                if (htmlFile == null) {
                    for (f in files) {
                        val name = f.second.lowercase()
                        if (name.endsWith(".html") || name.endsWith(".htm")) { htmlFile = f; break }
                    }
                }

                var changed = false

                // Check/update all files in VFS
                for ((fileUri, relPath) in files) {
                    try {
                        val newBytes = contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                        if (newBytes == null || newBytes.isEmpty()) continue
                        val oldBytes = VirtualFS.get(relPath)
                        if (oldBytes == null) {
                            // New file detected
                            VirtualFS.put(relPath, newBytes)
                            changed = true
                            android.util.Log.i("IW_FOLDER", "New file detected: $relPath")
                        } else if (!newBytes.contentEquals(oldBytes)) {
                            // Existing file changed
                            VirtualFS.put(relPath, newBytes)
                            changed = true
                        }
                    } catch (_: Exception) {}
                }

                // Remove files from VFS that no longer exist in folder
                val currentPaths = files.map { it.second }.toSet()
                val htmlKeyName = htmlFile?.second ?: htmlName
                for (vfsPath in VirtualFS.paths().toList()) {
                    if (vfsPath != htmlKeyName && vfsPath !in currentPaths) {
                        VirtualFS.remove(vfsPath)
                        changed = true
                        android.util.Log.i("IW_FOLDER", "File removed: $vfsPath")
                    }
                }

                // Update file count display
                if (files.size != folderFileCount) {
                    folderFileCount = files.size
                    runOnUiThread {
                        if (this@checkLiveReload.isTvFolderInfoReady) {
                            val folderName = try {
                                treeUri.pathSegments.lastOrNull()?.substringAfterLast("/") ?: "Folder"
                            } catch (_: Exception) { "Folder" }
                            this@checkLiveReload.tvFolderInfo.text = "$folderName — $folderFileCount files"
                        }
                    }
                }

                if (changed) {
                    runOnUiThread {
                        if (this@checkLiveReload.isTvPageTitleReady) this@checkLiveReload.tvPageTitle.text = "↻ Reloading..."
                        if (this@checkLiveReload.isTvFsTitleReady &&
                            this@checkLiveReload.isOverlayFsReady &&
                            this@checkLiveReload.overlayFs.visibility == View.VISIBLE)
                            this@checkLiveReload.tvFsTitle.text = "↻ Reloading..."
                    }
                    Thread.sleep(1200)
                    runOnUiThread {
                        if (this@checkLiveReload.isTvPageTitleReady) this@checkLiveReload.tvPageTitle.text = if (this@checkLiveReload.isHosting && this@checkLiveReload.hostUrl.isNotEmpty()) this@checkLiveReload.hostUrl else htmlName
                    }
                }
                return@Thread
            }

            // ── Manual mode: check individual URIs ─────────────────
            // Main HTML — check for changes
            val newContent = contentResolver.openInputStream(uri)
                ?.use { it.readBytes() } ?: return@Thread
            if (newContent.isEmpty()) return@Thread

            val oldContent = VirtualFS.get(htmlName)
            var changed = if (oldContent != null) !newContent.contentEquals(oldContent) else true

            if (changed) {
                VirtualFS.put(htmlName, newContent)
            }

            // Check extra CSS/JS/image files too — update VFS
            extraFileUris.forEach { extraUri ->
                try {
                    val relPath = extraRelativePaths[extraUri] ?: getFileName(extraUri)
                    val newExtra = contentResolver.openInputStream(extraUri)
                        ?.use { it.readBytes() } ?: return@forEach
                    val oldExtra = VirtualFS.get(relPath)
                    if (oldExtra == null || !newExtra.contentEquals(oldExtra)) {
                        VirtualFS.put(relPath, newExtra)
                        changed = true
                    }
                } catch (_: Exception) {}
            }

            if (changed) {
                runOnUiThread {
                    if (this@checkLiveReload.isTvPageTitleReady) this@checkLiveReload.tvPageTitle.text = "↻ Reloading..."
                    if (this@checkLiveReload.isTvFsTitleReady &&
                        this@checkLiveReload.isOverlayFsReady &&
                        this@checkLiveReload.overlayFs.visibility == View.VISIBLE)
                        this@checkLiveReload.tvFsTitle.text = "↻ Reloading..."
                }
                Thread.sleep(1200)
                runOnUiThread {
                    if (this@checkLiveReload.isTvPageTitleReady) this@checkLiveReload.tvPageTitle.text = if (this@checkLiveReload.isHosting && this@checkLiveReload.hostUrl.isNotEmpty()) this@checkLiveReload.hostUrl else htmlName
                }
            }
        } catch (_: Exception) {}
    }.start()
}

fun MainActivity.checkReachability() {
    if (!isHosting) return   // Only check on host device
    Thread {
        val ok = try {
            Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", this@checkReachability.getPort()), 1500)
                true
            }
        } catch (_: Exception) { false }
        this@checkReachability.serverReachable = ok
        runOnUiThread {
            if (!this@checkReachability.isTvConnStatusReady) return@runOnUiThread
            this@checkReachability.tvConnStatus.text = when {
                ok -> "127.0.0.1:${this@checkReachability.getPort()} — Server running"
                else -> "Server starting..."
            }
            this@checkReachability.tvConnStatus.setTextColor(
                if (ok) 0xFF16A34A.toInt() else 0xFFF59E0B.toInt()
            )
            this@checkReachability.dotConn.background = this@checkReachability.drawCircle(
                if (ok) 0xFF16A34A.toInt() else 0xFFF59E0B.toInt()
            )
        }
    }.start()
}

fun MainActivity.startPolling() {
    val h = Handler(Looper.getMainLooper())
    h.post(object : Runnable {
        override fun run() {
            // Host panel status
            if (this@startPolling.isTvHotspotStatusReady) {
                val on = this@startPolling.isHotspotOn()
                this@startPolling.tvHotspotStatus.text =
                    if (on) "Hotspot ON — other devices can connect"
                    else    "Hotspot OFF (optional — localhost still works)"
                this@startPolling.tvHotspotStatus.setTextColor(
                    if (on) 0xFF16A34A.toInt() else 0xFF6B7280.toInt()
                )
                this@startPolling.dotHotspot.background = this@startPolling.drawCircle(
                    if (on) 0xFF16A34A.toInt() else 0xFFD1D5DB.toInt()
                )
                this@startPolling.refreshBtn()
            }
            // Output panel status
            if (this@startPolling.isTvWifiStatusReady) {
                if (this@startPolling.isHosting) {
                    this@startPolling.tvWifiStatus.text = "Using localhost — no WiFi needed"
                    this@startPolling.tvWifiStatus.setTextColor(0xFF16A34A.toInt())
                    this@startPolling.dotWifi.background = this@startPolling.drawCircle(0xFF16A34A.toInt())
                } else {
                    val on = this@startPolling.isWifiOn()
                    this@startPolling.tvWifiStatus.text =
                        if (on) "WiFi ON — ready to receive"
                        else    "WiFi OFF — turn on to view from host"
                    this@startPolling.tvWifiStatus.setTextColor(if (on) 0xFF16A34A.toInt() else 0xFFDC2626.toInt())
                    this@startPolling.dotWifi.background = this@startPolling.drawCircle(if (on) 0xFF16A34A.toInt() else 0xFFDC2626.toInt())
                }
            }
            if (this@startPolling.isTvConnStatusReady && !this@startPolling.isHosting) {
                // Show only VERIFIED IP (genuinely loaded in WebView)
                // Previously showed "Host found" on wrong gateway — now fixed
                val vip = this@startPolling.viewerVerifiedIP
                if (vip != null) {
                    this@startPolling.tvConnStatus.text = "Host found at $vip"
                    this@startPolling.tvConnStatus.setTextColor(0xFF16A34A.toInt())
                    this@startPolling.dotConn.background = this@startPolling.drawCircle(0xFF16A34A.toInt())
                } else {
                    val gw = this@startPolling.getGatewayIP()
                    if (gw != null) {
                        this@startPolling.tvConnStatus.text = "Searching host at $gw..."
                        this@startPolling.tvConnStatus.setTextColor(0xFFF59E0B.toInt())
                        this@startPolling.dotConn.background = this@startPolling.drawCircle(0xFFF59E0B.toInt())
                    } else {
                        this@startPolling.tvConnStatus.text = "Not connected to host's hotspot"
                        this@startPolling.tvConnStatus.setTextColor(0xFF9CA3AF.toInt())
                        this@startPolling.dotConn.background = this@startPolling.drawCircle(0xFFD1D5DB.toInt())
                    }
                }
            }
            this@startPolling.checkReachability()
            this@startPolling.checkLiveReload()
            h.postDelayed(this, 500)
        }
    })
}

fun MainActivity.getFileName(uri: Uri): String {
    var name = "file.html"
    contentResolver.query(uri, null, null, null, null)?.use {
        val col = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && col >= 0) name = it.getString(col) ?: name
    }
    return name
}

fun MainActivity.extractSafPath(uri: Uri): String? {
    try {
        // SAF external storage documents URI
        // content://com.android.externalstorage.documents/document/primary%3APath%2FTo%2FFile
        if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
            val docId = DocumentsContract.getDocumentId(uri)
            // docId format: "primary:Projects/MyWebsite/css/style.css"
            val separatorIndex = docId.indexOf(':')
            if (separatorIndex >= 0) {
                return docId.substring(separatorIndex + 1)
            }
            return docId
        }
        // file:// URI — direct filesystem path
        if (uri.scheme == "file") {
            return uri.path
        }
    } catch (_: Exception) {}
    return null
}

fun MainActivity.getRelativePath(fileUri: Uri, mainHtmlUri: Uri): String? {
    val filePath = extractSafPath(fileUri) ?: return null
    val mainPath = extractSafPath(mainHtmlUri) ?: return null

    // Get the main HTML's parent directory
    val mainDir = mainPath.substringBeforeLast("/", "")
    if (mainDir.isEmpty()) return null

    // Check if extra file is inside the same project tree
    if (!filePath.startsWith(mainDir)) return null

    // Compute relative path
    val relative = filePath.substring(mainDir.length).trimStart('/')
    return if (relative.isNotEmpty()) relative else null
}

fun MainActivity.toast(m: String) {
    // Custom styled toast — rounded dark pill with white text
    // Android 11+ (API 30) deprecated custom Toast views — fallback gracefully
    try {
        val tv = TextView(this).apply {
            text = m; setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            background = drawRoundRect(0xE6374151.toInt(), 28.dp().toFloat())
            setPadding(20.dp(), 10.dp(), 20.dp(), 10.dp())
            gravity = Gravity.CENTER; elevation = 6.dp().toFloat()
        }
        val container = android.widget.FrameLayout(this).apply { addView(tv) }
        val toastObj = Toast(this).apply {
            duration = Toast.LENGTH_SHORT
            view = container
            setGravity(android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
                0, 80.dp())
        }
        toastObj.show()
    } catch (_: Exception) {
        // Fallback for Android 11+ where custom view is not allowed
        Toast.makeText(this@toast, m, Toast.LENGTH_SHORT).show()
    }
}

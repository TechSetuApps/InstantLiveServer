package app.techsetuapps.instantweb

import fi.iki.elonen.NanoHTTPD
import android.content.Context

// ================================================================
//  LocalHttpServer — Serves files from VirtualFS (RAM-based)
//  Main HTML is served with injected live-reload + console scripts.
//  All other files (CSS, JS, images, fonts, etc.) are served
//  from VirtualFS using their relative paths — so HTML can
//  reference assets like src="css/style.css" or src="images/logo.png"
// ================================================================

class LocalHttpServer(
    private val mainHtmlName: String,
    port: Int,
    private val context: Context
) : NanoHTTPD("0.0.0.0", port) {

    private val prefs = context.getSharedPreferences("iw_prefs", Context.MODE_PRIVATE)

    // ── Injected Scripts ────────────────────────────────────────

    private fun reloadScript() = """<script>
(function(){
  var ts=null;
  var base=window.location.protocol+'//'+window.location.host;
  function poll(){
    var x=new XMLHttpRequest();
    x.open('GET',base+'/__iw_ping?_='+Date.now(),true);
    x.onload=function(){
      var t=x.responseText;
      if(ts===null){ts=t;}
      else if(t!==ts){ts=t;location.reload(true);}
    };
    x.onerror=function(){};
    x.send();
  }
  setInterval(poll,500);
})();
</script>"""

    private fun consoleScript(): String {
        val consoleType = prefs.getString("console_type", "legacy") ?: "legacy"
        return if (consoleType == "eruda") {
            """<script src="/eruda.js"></script><script>eruda.init();</script>"""
        } else {
            ""
        }
    }

    private val viewport = """<meta name="viewport" content="width=device-width, initial-scale=1.0">"""

    private fun injectViewport(html: String): String {
        if (html.contains("viewport", ignoreCase = true)) return html
        return when {
            html.contains("<head>", ignoreCase = true) ->
                html.replaceFirst("<head>", "<head>\n$viewport")
            html.contains("<HEAD>", ignoreCase = true) ->
                html.replaceFirst("<HEAD>", "<HEAD>\n$viewport")
            html.contains("<html>", ignoreCase = true) ->
                html.replaceFirst("<html>", "<html>\n<head>\n$viewport\n</head>")
            else -> "<head>\n$viewport\n</head>\n$html"
        }
    }

    // ── MIME Type ───────────────────────────────────────────────

    private fun getMimeType(fileName: String): String = when {
        fileName.endsWith(".css", true)   -> "text/css"
        fileName.endsWith(".js", true)    -> "application/javascript"
        fileName.endsWith(".json", true)  -> "application/json"
        fileName.endsWith(".txt", true)   -> "text/plain"
        fileName.endsWith(".xml", true)   -> "text/xml"
        fileName.endsWith(".csv", true)   -> "text/csv"
        fileName.endsWith(".md", true)    -> "text/markdown"
        fileName.endsWith(".png", true)   -> "image/png"
        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
        fileName.endsWith(".gif", true)   -> "image/gif"
        fileName.endsWith(".svg", true)   -> "image/svg+xml"
        fileName.endsWith(".ico", true)   -> "image/x-icon"
        fileName.endsWith(".webp", true)  -> "image/webp"
        fileName.endsWith(".woff", true)  -> "font/woff"
        fileName.endsWith(".woff2", true) -> "font/woff2"
        fileName.endsWith(".ttf", true)   -> "font/ttf"
        fileName.endsWith(".otf", true)   -> "font/otf"
        fileName.endsWith(".mp3", true)   -> "audio/mpeg"
        fileName.endsWith(".wav", true)   -> "audio/wav"
        fileName.endsWith(".mp4", true)   -> "video/mp4"
        fileName.endsWith(".webm", true)  -> "video/webm"
        fileName.endsWith(".pdf", true)   -> "application/pdf"
        else -> "application/octet-stream"
    }

    // ── Serve ──────────────────────────────────────────────────

    override fun serve(session: IHTTPSession?): Response {
        return try {
            val uri = session?.uri ?: "/"
            val params = session?.parameters ?: emptyMap()

            // Ping endpoint — VFS fingerprint for change detection
            if (uri.startsWith("/__iw_ping")) {
                val ts = VirtualFS.fingerprint()
                val r = newFixedLengthResponse(Response.Status.OK, "text/plain", ts)
                r.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                r.addHeader("Access-Control-Allow-Origin", "*")
                return r
            }

            // Eruda endpoint — served from assets (not VFS)
            if (uri == "/eruda.js") {
                return try {
                    val input = context.assets.open("eruda.js")
                    val content = input.bufferedReader().readText()
                    input.close()
                    val r = newFixedLengthResponse(Response.Status.OK, "application/javascript", content)
                    r.addHeader("Cache-Control", "max-age=86400")
                    r.addHeader("Access-Control-Allow-Origin", "*")
                    r
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "eruda.js not found")
                }
            }

            // ── Serve from Virtual File System ──────────────────

            val requestPath = uri.trimStart('/')

            // Non-main files: CSS, JS, images, fonts, etc.
            // requestPath is like "css/style.css" or "images/logo.png"
            if (requestPath.isNotEmpty() && requestPath != mainHtmlName && VirtualFS.contains(requestPath)) {
                val bytes = VirtualFS.get(requestPath)!!
                val mime = getMimeType(requestPath)
                val isText = mime.startsWith("text/") ||
                             mime == "application/javascript" ||
                             mime == "application/json" ||
                             mime == "image/svg+xml" ||
                             mime == "text/markdown"
                val r = if (isText) {
                    newFixedLengthResponse(Response.Status.OK, mime, String(bytes, Charsets.UTF_8))
                } else {
                    newFixedLengthResponse(Response.Status.OK, mime,
                        bytes.inputStream(), bytes.size.toLong())
                }
                r.addHeader("Cache-Control", "no-cache")
                r.addHeader("Access-Control-Allow-Origin", "*")
                return r
            }

            // ── Main HTML file ──────────────────────────────────

            val htmlBytes = VirtualFS.get(mainHtmlName)
            if (htmlBytes == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No file selected")
            }

            val rawHtml = String(htmlBytes, Charsets.UTF_8)
            if (rawHtml.isBlank()) {
                val blank = "<html><body style='background:#fff;'></body></html>"
                val r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", blank)
                r.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                return r
            }

            var html = rawHtml
            html = injectViewport(html)

            val isAppWebView = params["__iw_app"]?.firstOrNull() == "1"
            val isAcodePlugin = params["__iw_acode"]?.firstOrNull() == "1"
            val scripts = if (isAppWebView || isAcodePlugin) consoleScript() + "\n" + reloadScript()
                          else reloadScript()

            val tag = when {
                html.contains("</body>", ignoreCase = true) -> "</body>"
                html.contains("</html>", ignoreCase = true) -> "</html>"
                else -> ""
            }
            html = if (tag.isNotEmpty())
                html.replaceFirst(tag, scripts + "\n" + tag)
            else
                html + "\n" + scripts

            val resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            resp.addHeader("Pragma", "no-cache")
            resp.addHeader("Expires", "0")
            resp
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server error: ${e.message}")
        }
    }
}

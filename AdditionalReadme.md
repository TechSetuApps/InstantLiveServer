# InstantLive Server
## Complete Feature Reference

---

## Contents

- [ <span style="color: #1a43f8;"> One-tap hosting </span>](#one-tap-hosting)
- [ <span style="color: #1a43f8;"> Folder mode </span>](#folder-mode)
- [ <span style="color: #1a43f8;"> Live preview </span>](#live-preview)
- [ <span style="color: #1a43f8;"> Fullscreen Eruda console </span>](#fullscreen-eruda-console)
- [ <span style="color: #1a43f8;"> Network sharing </span>](#network-sharing)
- [ <span style="color: #1a43f8;"> Virtual filesystem </span>](#virtual-filesystem)
- [ <span style="color: #1a43f8;"> Ad-supported </span>](#ad-supported)
- [ <span style="color: #1a43f8;"> Update checker </span>](#update-checker)
- [ <span style="color: #1a43f8;"> Force update </span>](#force-update)
- [ <span style="color: #1a43f8;"> Portrait-locked </span>](#portrait-locked)

---

## One-tap hosting

> One-tap hosting is the core promise of InstantLive Server. You select an HTML file or a folder, tap the host button once, and the server is live on your local network within milliseconds. There is no setup wizard, no configuration file to edit, no terminal to open, and no root permission required. The app internally handles everything — picking a free port if the default is busy, starting the foreground service, taking the wake lock, building the notification, and finally broadcasting the URL to the UI.

> The server binds to `0.0.0.0` by design instead of `127.0.0.1`, which is what makes one-tap hosting genuinely useful. A bind to localhost would only let your phone browse the content; a bind to `0.0.0.0` lets every device on the same WiFi or hotspot reach it. So when you tap that single button, you are not just starting a server — you are publishing your work to every browser on the network.

> A single tap also handles teardown. The same button toggles between Start and Stop, the persistent notification has its own Stop action, and the foreground service uses `START_NOT_STICKY` so a killed app does not leave a zombie notification behind. The whole flow is built around the idea that hosting should be as easy as opening a file.

---

## Folder mode

> Folder mode lets you host an entire project tree in one go. Instead of picking individual files, you tap the folder button, choose a directory from the system file picker, and the app recursively scans every file inside it. The structure — subdirectories, nested assets, relative paths — is preserved exactly as it sits on disk. An HTML reference like `<link rel="stylesheet" href="css/style.css">` or `<img src="images/logo.png">` resolves correctly without any rewriting on your part.

> The scanner is intelligent about what to skip. It ignores hidden files starting with a dot, skips common dependency folders that would balloon memory (`node_modules`, `build`, `dist`, `__pycache__`), and refuses to load any single file larger than 15 MB to avoid running out of RAM on lower-end phones. It also auto-detects the main HTML file: it prefers `index.html`, falls back to `index.htm`, and finally picks the first HTML file it finds in the tree.

> Folder mode is also live. Once hosting is active, every polling cycle rescans the folder and compares file timestamps and sizes against the in-memory virtual filesystem. If you edit a file in your editor, add a new asset, or delete one, the change is reflected in the served content within a fraction of a second — no restart needed. This makes it ideal for active development where you are iterating quickly.

---

## Live preview

> Live preview means you save code in your editor and the rendered output updates on its own — no manual refresh, no F5, no switching apps. The mechanism is a small JavaScript snippet that the server injects into every served HTML page. This script polls a special `/__iw_ping` endpoint every 500 milliseconds and asks the server for a fingerprint of the current virtual filesystem. The fingerprint is a sorted, deterministic string built from every file's path, size, and last-modified timestamp.

> When the fingerprint changes between two polls, the script triggers `location.reload(true)` on the page. Because the fingerprint only changes when an actual file has been edited, added, or removed, the page does not flicker on idle polls. The reload is a hard refresh, so cached CSS and JavaScript are also pulled fresh — important when you are iterating on a stylesheet or a script.

> Live preview works in two contexts. Inside the app itself, the Output tab has an embedded WebView that loads the served URL and benefits from the same polling script. Outside the app, anyone on the same network can open the URL in their own browser and get the same live behavior. So you can edit on your phone and watch the result update on a laptop browser in real time, or vice versa.

---

## Fullscreen Eruda console

> The fullscreen Eruda console brings desktop-class browser DevTools to your phone. Eruda is an open-source mobile debugging library that injects a draggable console button into the page; tapping it opens a full panel covering Elements, Console, Network, Resources, and Sources. You get DOM inspection with on-the-fly editing, JavaScript console output with errors and warnings, a network request inspector with headers and timing, and access to localStorage, sessionStorage, and cookies — all without ever leaving the phone.

> Inside InstantLive Server, the console is invoked through a fullscreen overlay. When you tap the fullscreen icon on the Output panel, the page is reloaded into a larger immersive WebView with system bars hidden, and the Eruda script is injected ahead of the live-reload script. The console button then sits on top of your served content, ready to be expanded whenever you need to debug. A separate Legacy mode is available in settings if you want a lighter preview without the console overhead.

> This is genuinely useful for mobile-first development. If you are building a responsive layout, testing a touch interaction, or chasing a JavaScript bug that only reproduces on a phone, you no longer need to plug into Chrome DevTools over USB. You write the code, host it, tap fullscreen, and the full debugging surface is right there in your hand.

---

## Network sharing

> Network sharing is what turns a personal preview tool into a presentation tool. Because the server binds to `0.0.0.0`, every device on the same local network can reach your hosted content. A laptop on the same WiFi can browse to your phone's IP and see the live site. A second phone can do the same. A smart TV with a browser, a tablet, a Chromebook — anything that can open a URL and speak HTTP can connect.

> The app does the network discovery work for you. On the host side, it enumerates every network interface, filters out cellular interfaces like `rmnet` and `ccmni`, and prefers hotspot and WiFi ranges (192.168.x.x and 172.x.x.x). It deliberately skips 10.x.x.x because that range is too ambiguous — it is used by office WiFi, VPNs, and corporate networks, and surfacing it would often show the wrong IP. The detected IP is shown directly on the host panel, so you do not have to dig through Android settings to find it.

> On the viewer side, the app does the reverse. When you open it on a second device connected to the host's hotspot, it reads the gateway IP from the DHCP info and tries to load the page from there. A small auto-try routine pings the host a few times in the background, and once the WebView genuinely loads the page, the verified IP is shown in the status panel. So sharing and joining a hosted session both take a single tap.

---

## Virtual filesystem

> The virtual filesystem — VirtualFS — is the in-memory layer that makes everything else possible. It is a Kotlin `object` backed by a `ConcurrentHashMap<String, Pair<ByteArray, Long>>`, where the key is the normalized relative path and the value is the file's byte content plus a timestamp. Every file you host lives entirely in RAM; nothing is ever written to disk. Stop hosting and the map is cleared in one call — no leftover files, no cleanup chores, no traces on your storage.

> The choice of RAM over disk is deliberate. Disk I/O on Android is slow and unpredictable, especially with scoped storage and the Storage Access Framework. Reading from a SAF Uri requires a ContentResolver query on every access, which would make the server sluggish under load. By reading the file once into the VFS at host-start time and serving all subsequent requests directly from RAM, the server stays fast even with many concurrent viewers.

> The VFS exposes a fingerprint method that returns a sorted, deterministic string built from every entry's path, timestamp, and size. This fingerprint is what the live-preview script polls. Because the map is sorted and the timestamp updates on every write, even a one-byte change in a single file produces a different fingerprint and triggers a reload. The VFS is thread-safe, so the polling thread and the HTTP serving thread can read and write concurrently without locks in your code.

---

## Ad-supported

> InstantLive Server is ad-supported, which keeps it free for everyone. An interstitial ad from Google AdMob plays once before each hosting session starts. When you tap Start Hosting, the app checks if a grace period is active; if not, it loads and shows the interstitial. Once you dismiss the ad, the actual hosting begins. There is no banner ad cluttering the UI, no native ad wedged into a list — just one interstitial per session, and that is it.

> The grace period is what keeps the experience from getting annoying. After an ad is dismissed, a two-minute window begins. If you stop and restart hosting within those two minutes — for example, because you wanted to switch files or fix a typo — no new ad is shown. The grace state is stored as a timestamp in SharedPreferences, not just in memory, so it survives app restarts. The system was designed to feel fair: ads fund development, but they should not punish you for iterating.

> The open-source build uses Google's official test ad unit IDs, so anyone building from source sees sample ads instead of real ones. The production build on the Amazon Appstore uses the real IDs. AdMob itself may collect advertising ID, IP address, and app interaction data — this is governed by Google's privacy policy, not by InstantLive Server, and the app adds no tracking of its own on top.

---

## Update checker

> The update checker keeps your installed copy of the app in sync with the latest release on GitHub. On every app launch, and again on every resume from background, a background thread fetches a small JSON file from a public GitHub raw URL. The JSON contains the latest version code, version name, release notes, and download link. The thread parses it defensively — missing fields, malformed JSON, and unexpected types are all handled silently, so a broken update file can never crash the app.

> If the latest version code is higher than the installed one, an update overlay appears with the version name, release notes, and a download button. The button opens the release page in the system browser, where you can grab the new APK. URL validation rejects anything that is not HTTPS and not from a trusted domain, so a compromised update file cannot redirect users to an arbitrary download location.

> When the device is offline, the check is simply skipped. The app remembers the last successful check time and lets you keep using it normally for up to seven days without a successful update check. This offline grace means a developer on a flight, in a basement, or anywhere without signal can still host and preview their work without the app holding them hostage.

---

## Force update

> Force update is the safety net that lets critical security fixes or breaking-change releases reach every user, even those who never read update prompts. If the update JSON marks a release as mandatory, or if the app has not been able to reach the update server for more than seven days, a full-screen update overlay appears on launch and blocks further use of the app until you update.

> This is a deliberate tradeoff. Most apps let users ignore updates forever, which means security vulnerabilities sit unpatched on millions of devices. InstantLive Server is small, single-purpose, and ships no user data — so requiring an update in rare cases is acceptable. The seven-day offline grace window ensures that travelers, developers in low-signal areas, and people who simply forgot to update are not punished the moment they lose connectivity.

> The force-update overlay is intentionally minimal. It shows the version you are on, the version you should be on, the release notes for the new version, and a single Download button. There is no dismiss button and no skip button. Tapping the button opens the release page in the system browser. Once you install the new APK, the next launch passes the version check and the app is usable again.

---

## Portrait-locked

> The entire UI is locked to portrait orientation. This is a deliberate choice for a tool that is meant to be used one-handed, on a phone, in short bursts. You select a file with your thumb, tap host, glance at the URL, and hand the phone to a friend — all without the screen rotating mid-action because you tilted the device a little too far. The orientation lock is enforced at the activity level via `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT` in `onCreate`.

> Portrait lock also keeps the codebase simpler. With a single orientation to design for, the programmatic UI does not need separate landscape layouts, no re-measuring of panels on rotation, and no state save and restore on configuration changes. The host panel, output panel, fullscreen overlay, drawer, and update sheet all assume one shape, which makes the layout code shorter and easier to follow.

> The tradeoff is that landscape users — for example, someone testing a responsive website in landscape mode — have to use a different device to actually view the served content in landscape. But that is the right tradeoff for a hosting tool. The app itself is just a control surface; the actual rendered preview happens in any browser you point at the server, and those browsers happily rotate as they please. The control surface stays portrait, the preview surface stays flexible.

---

## Summary

| Feature | Purpose |
|---------|---------|
| One-tap hosting | Start a local server with a single tap, no setup |
| Folder mode | Host an entire project tree with relative paths intact |
| Live preview | Auto-refresh the page when files change on disk |
| Fullscreen Eruda console | Mobile DevTools: Elements, Console, Network, Resources |
| Network sharing | Reach the server from any device on the same WiFi or hotspot |
| Virtual filesystem | RAM-only file storage, no disk writes, instant cleanup |
| Ad-supported | One interstitial per session, with a 2-minute grace period |
| Update checker | Background check against GitHub releases on every launch |
| Force update | Mandatory update screen for critical releases or 7-day offline |
| Portrait-locked | One-handed, predictable control surface for the UI |

---

> **Version** 1.4.0 (Build 5)  
> **Updated** August 2026  
> **License** MIT  
> **Developer** TechSetuApps — Darbhanga, Bihar, India

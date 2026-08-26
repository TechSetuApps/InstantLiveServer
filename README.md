# InstantLive Server
![InstantLiveServer Feature Graphic](https://raw.githubusercontent.com/TechSetuApps/InstantLiveServer/refs/heads/main/FeatureGraphic/FeatureGraphic_Banner.jpg)
<p align="center">
  <b>Turn your phone into a live web server</b>
</p>

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat)](LICENSE)
[![Platform](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/v1.4.0-0097A7?style=flat)](https://github.com/TechSetuApps/InstantLiveServer/releases)

## • Overview

Welcome to InstantLive Server — a lightweight, </br> on-device web server for instant HTML preview </br> and live sharing. Built for developers and learners.

Select any HTML file or folder, and share it over your local network — no cloud, no upload, no signup. </br> The built-in WebView provides live preview with </br> auto-reload, and a fullscreen Eruda console for </br> real-time debugging.

## • Features

- [ <span style="color: #1a43f8;"> One-tap hosting </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#one-tap-hosting)
- [ <span style="color: #1a43f8;"> Folder mode </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#folder-mode)
- [ <span style="color: #1a43f8;"> Live preview </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#live-preview)
- [ <span style="color: #1a43f8;"> Fullscreen Eruda console </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#fullscreen-eruda-console)
- [ <span style="color: #1a43f8;">  Network sharing </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#network-sharing)
- [ <span style="color: #1a43f8;"> Virtual filesystem </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#virtual-filesystem)
- [ <span style="color: #1a43f8;"> Ad-supported </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#ad-supported)
- [ <span style="color: #1a43f8;"> Update checker </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#update-checker)
- [ <span style="color: #1a43f8;"> Force update </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#force-update)
- [ <span style="color: #1a43f8;"> Portrait-locked </span>](https://github.com/TechSetuApps/InstantLiveServer/blob/main/FeatureGraphic/FeaturesRD.md#portrait-locked)

## • How I Made It

This project started as a simple idea: *what if my phone could host a website?*

> I designed the entire UI layout and application logic myself.

> AI assisted with code generation, debugging, and refactoring.

> I am learning Kotlin — the concept, design decisions, feature set, and architecture are entirely mine. AI helped translate my ideas into working code.

**Tech Stack:** Kotlin / NanoHTTPD v2.3.1 / Eruda / Google AdMob / Android Foreground Service

## • Transparency

> **Code origin** — UI design and logic conceived by the author. Implementation with AI assistance. No code copied from other projects.
 
> **AI disclosure** — AI (LLM) used for code generation, debugging, compilation fixes, and refactoring. Author is learning Kotlin — AI is a development accelerator.

> **No hidden tracking** — app does not collect, store, or transmit any user data beyond what Google AdMob SDK collects.
 
> **No backdoors** — server binds `0.0.0.0` by design for local network access. No remote access, telemetry, or analytics beyond AdMob.
 
> **Open source** — full source available for audit. Report security concerns via [GitHub Issues](https://github.com/TechSetuApps/InstantLiveServer/issues).

## • Project Structure

<pre>
app/src/main/
├── kotlin/app/techsetuapps/instantweb/
│   ├── MainActivity.kt            — Lifecycle, UI scaffold, notification
│   ├── MainActivityActions.kt     — File pick, hosting start/stop, AdMob
│   ├── MainActivityUI.kt          — App bar, tabs, host panel, output panel
│   ├── MainActivityDraw.kt        — Circles, rounded rects, gradients
│   ├── MainActivityNetwork.kt     — WiFi info, IP detection, update checker
│   ├── MainActivityPages.kt       — Help, privacy, terms, credits, settings
│   ├── ServerService.kt           — Foreground service, NanoHTTPD, notification
│   ├── LocalHttpServer.kt         — Serves VirtualFS, live reload, Eruda
│   └── VirtualFS.kt               — In-memory virtual filesystem (RAM)
│
├── AndroidManifest.xml            — Permissions, service, AdMob app ID
├── res/                           — Icons, values, themes
├── assets/                        — eruda.js (embedded DevTools)
├── build.gradle.kts               — App-level dependencies & SDK
└── proguard-rules.pro             — R8/ProGuard rules
</pre>

**Root-level:**

<pre>
├── build.gradle.kts       — Project-level Gradle config
├── settings.gradle.kts    — Module includes
├── gradle.properties      — JVM & AndroidX properties
├── LICENSE                — MIT License
└── README.md              — This file
</pre>

## • Privacy Policy

> **No personal data collection.** The app does not collect, store, or transmit any personal information.

> **AdMob.** Google AdMob displays interstitial ads. It may collect device advertising ID, IP address, and app interaction data for ad targeting. Governed by [Google's Privacy Policy](https://policies.google.com/privacy).

> **Local server.** HTTP server runs on your device, accessible only on your local network (WiFi/hotspot). No data leaves your network through this server.

> **File access.** The app reads only HTML/CSS/JS files you explicitly select. It does not scan or access other files.

> **No analytics.** No Firebase, no Google Analytics, no third-party telemetry beyond AdMob.

Privacy concerns? [Open an Issue](https://github.com/TechSetuApps/InstantLiveServer/issues)

## • Terms of Use

> **Local use only.** This tool is for local development and testing. You are responsible for what you serve.

> **No illegal content.** Do not serve or distribute illegal, harmful, or copyrighted content without permission.

> **Network exposure.** When hosting, your content is accessible to all devices on the same network. Secure your hotspot/WiFi.

> **Ads.** Ads displayed via Google AdMob. Do not artificially click ads — this violates AdMob policies.

> **As-is.** Software provided "as is" without warranty. See the MIT License for full terms.

> **Ethical use.** This is a developer tool. Use it responsibly.

## • Disclaimer

> This app binds an HTTP server on `0.0.0.0:7090` (configurable). Anyone on your local network can access served content. Use on trusted networks only.

> The server uses NanoHTTPD, a lightweight Java HTTP server. It is not hardened for production use — do not expose it to the public internet.

> No TLS/HTTPS. All traffic is served over plain HTTP. Do not transmit sensitive data through this server.

> The app is designed for development and learning purposes. It is not a production web server.

> The author is learning Kotlin. The code may not follow all Kotlin best practices. Improvements and suggestions are welcome.

## • Credits

> **NanoHTTPD v2.3.1** — [nanohttpd/nanohttpd](https://github.com/nanohttpd/nanohttpd) — BSD-3-Clause

> **Eruda** — [liriliri/eruda](https://github.com/liriliri/eruda) — MIT
 
> **Google AdMob SDK** — Google LLC — Apache 2.0
>
> **AndroidX / Jetpack** — Google LLC — Apache 2.0
>
> **Kotlin Stdlib** — JetBrains — Apache 2.0

All other code is original work by **TechSetuApps**.

## • Security

This project is open to security review. If you are an ethical hacker, security researcher, or developer and find security flaws, vulnerabilities, or bugs:

> **Do not exploit them.**  
> **Report via [GitHub Issues](https://github.com/TechSetuApps/InstantLiveServer/issues)** — include steps to reproduce, impact, and suggested fix.
> 
> I am learning security auditing — your reports help me improve both the app and my skills.

**Known considerations:**

> Server binds `0.0.0.0` by design (local network access required for sharing)
> No TLS/HTTPS — plain HTTP only
> 
> No authentication on served content
> Foreground service uses WakeLock (max 4 hours)

## • Author

**TechSetuApps** — Independent developer & ethical hacking enthusiast

- GitHub: [TechSetuApps](https://github.com/TechSetuApps)
- Email: [techsetuapps@gmail.com](mailto:techsetuapps@gmail.com)
- License: MIT
- Version: 1.4.0 (Stable)

*Concept and design by TechSetuApps. Code written with AI assistance while learning Kotlin. Learning security auditing — reports and suggestions welcome.*

---

<p align="center">
  <sub>Built with curiosity. Open-sourced with transparency.</sub>
</p>

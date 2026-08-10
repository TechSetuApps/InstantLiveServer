# ================================================================
#  InstantLive Server — ProGuard Rules
# ================================================================

# ── Keep app entry points ────────────────────────────────────────
-keep class app.techsetuapps.instantweb.** { *; }
-keep class app.techsetuapps.instantweb.MainActivity { *; }

# ── NanoHTTPD ────────────────────────────────────────────────────
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ── Android core ─────────────────────────────────────────────────
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# ── WebView JavaScript bridge ────────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# ── Native methods ───────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Kotlin ───────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ── Remove all logs (hides info from hackers) ────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ── Obfuscation ──────────────────────────────────────────────────
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'iw'
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# ── AndroidX ─────────────────────────────────────────────────────
-keep class androidx.** { *; }
-dontwarn androidx.**
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ── FIX: Missing javax / errorprone classes (R8 error) ──────────
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.**

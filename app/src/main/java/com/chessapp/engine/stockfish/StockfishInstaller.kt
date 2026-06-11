package com.chessapp.engine.stockfish

import android.content.Context
import java.io.File

/**
 * Resolves the bundled Stockfish binary. The binary is packaged as a fake shared
 * library at src/main/jniLibs/<abi>/libstockfish.so, because on Android 10+
 * (API 29) the W^X policy forbids exec() from any app-writable location — the
 * ONLY place an app may execute a bundled binary is its read-only
 * nativeLibraryDir. Requires jniLibs.useLegacyPackaging=true so the lib is
 * extracted to disk at install time.
 *
 * The binary is fetched at CI build time from the official Stockfish release
 * (see .github/workflows/build.yml); if it was not bundled for this ABI —
 * e.g. x86_64 emulators, or armv7 devices — [path] returns null and callers
 * fall back to the built-in engine.
 */
object StockfishInstaller {

    fun path(context: Context): String? {
        val f = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        // Empty placeholder .so files exist for non-arm64 ABIs purely so the APK
        // installs everywhere; a real engine is megabytes, a placeholder is 0 bytes.
        return if (f.exists() && f.canExecute() && f.length() > 1_000_000) f.absolutePath else null
    }

    fun available(context: Context): Boolean = path(context) != null
}

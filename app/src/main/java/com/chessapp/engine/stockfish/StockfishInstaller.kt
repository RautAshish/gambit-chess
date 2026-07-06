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
        // The bundled ELF is built for API 29 (Android 10). On arm64 devices
        // running API 24-28 it could exec and then fail in the loader, so we
        // gate here and fall back to the built-in engine below API 29.
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val f = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        // Non-arm64 ABIs carry a 0-byte placeholder; the >1MB guard treats it as
        // absent so the loader never touches an empty file.
        return if (f.exists() && f.canExecute() && f.length() > 1_000_000) f.absolutePath else null
    }

    fun available(context: Context): Boolean = path(context) != null
}

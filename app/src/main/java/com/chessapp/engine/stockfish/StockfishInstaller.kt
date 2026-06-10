package com.chessapp.engine.stockfish

import android.content.Context
import java.io.File

/**
 * Extracts the bundled Stockfish binary for the device ABI from assets to the app's
 * private files dir and marks it executable. Bundle binaries at:
 *   src/main/assets/stockfish/<abi>/stockfish
 * where <abi> is e.g. arm64-v8a, armeabi-v7a, x86_64.
 *
 * Returns the absolute path to the runnable binary.
 */
object StockfishInstaller {

    fun ensure(context: Context): String {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val assetPath = "stockfish/$abi/stockfish"
        val outFile = File(context.filesDir, "stockfish-$abi")

        if (!outFile.exists() || outFile.length() == 0L) {
            context.assets.open(assetPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!outFile.canExecute()) outFile.setExecutable(true, true)
        return outFile.absolutePath
    }
}

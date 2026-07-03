package com.chessapp.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessapp.data.prefs.Settings
import com.chessapp.data.prefs.SettingsRepository
import com.chessapp.domain.ai.ChessAI
import kotlinx.coroutines.launch

private val BG = UiColor(0xFF1C1F17)
private val PANEL = UiColor(0xFF272B20)
private val BONE = UiColor(0xFFEFE6D2)
private val BRASS = UiColor(0xFFE4B02A)
private val MUTED = UiColor(0xFF8A8B7E)

@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val settings by repo.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).statusBarsPadding()) {
        TopRow("Settings", onBack)

        Section("Board theme")
        ThemePicker(settings.boardTheme) { scope.launch { repo.setBoardTheme(it) } }

        Section("Gameplay")
        ToggleRow("Show legal moves", settings.showLegalMoves) {
            scope.launch { repo.setShowLegalMoves(it) }
        }
        ToggleRow("Flip board for black", settings.boardFlipped) {
            scope.launch { repo.setFlipped(it) }
        }

        Section("Sound & haptics")
        ToggleRow("Sound effects", settings.soundEnabled) {
            scope.launch { repo.setSound(it) }
        }
        ToggleRow("Haptics", settings.hapticsEnabled) {
            scope.launch { repo.setHaptics(it) }
        }

        Section("Engine")
        val sfAvailable = com.chessapp.engine.stockfish.StockfishInstaller
            .available(androidx.compose.ui.platform.LocalContext.current)
        ToggleRow(
            if (sfAvailable) "Use Stockfish (strong engine)" else "Use Stockfish (not bundled on this device)",
            settings.useStockfish && sfAvailable
        ) { if (sfAvailable) scope.launch { repo.setUseStockfish(it) } }
        Text(
            if (sfAvailable)
                "Stockfish 18 \u00B7 applies from your next game. \u00A9 the Stockfish team, GPLv3 \u2014 see THIRD_PARTY_LICENSES in the source repo."
            else
                "This build has no Stockfish binary for this device's CPU; the built-in engine is used.",
            color = MUTED, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Section("Clock")
        ClockPicker(settings.clockMinutes, settings.clockIncrementSeconds) { m, inc ->
            scope.launch { repo.setClock(m, inc) }
        }
        Text(
            clockDescription(settings.clockMinutes, settings.clockIncrementSeconds),
            color = MUTED, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Section("Online play")
        // Store builds ship a baked server: hide the plumbing behind an
        // explicit advanced disclosure so players never wonder whether setup
        // is required (it isn't).
        val hasBuiltInServer = com.chessapp.BuildConfig.DEFAULT_ONLINE_PROJECT_ID.isNotBlank()
        var showCustomServer by remember { mutableStateOf(false) }
        if (hasBuiltInServer && !showCustomServer) {
            Text(
                "Multiplayer is ready \u2014 this build includes the Emersion server. Nothing to set up.",
                color = MUTED, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            TextButton(onClick = { showCustomServer = true },
                modifier = Modifier.padding(horizontal = 10.dp)) {
                Text("Use a custom server (advanced)", color = BRASS)
            }
        } else {
        var pid by remember(settings.onlineProjectId) { mutableStateOf(settings.onlineProjectId) }
        var key by remember(settings.onlineApiKey) { mutableStateOf(settings.onlineApiKey) }
        OutlinedTextField(
            value = pid, onValueChange = { pid = it },
            label = { Text("Firebase project ID", color = MUTED) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Web API key", color = MUTED) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        TextButton(onClick = { scope.launch { repo.setOnlineConfig(pid, key) } },
            modifier = Modifier.padding(horizontal = 10.dp)) {
            Text("Save online settings", color = BRASS)
        }
        Text(
            "Optional \u2014 leave blank to use this build's default server. " +
            "To host your own free server instead (\u22485 min), see SERVER_SETUP.md in the source repo.",
            color = MUTED, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp)
        )
        }

        Section("About")
        Text(
            "Emersion Chess v${com.chessapp.BuildConfig.VERSION_NAME}\nBuilt-in engine + optional Stockfish 18 (\u00A9 the Stockfish team, GPLv3).\nSource & licenses: github.com/emersionplay/emersion-chess",
            color = MUTED, fontSize = 12.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TopRow(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("\u2039 Home", color = BRASS) }
        Spacer(Modifier.weight(1f))
        Text(title, color = BONE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Section(name: String) {
    Text(name.uppercase(), color = BRASS, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BONE, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BG, checkedTrackColor = BRASS,
                uncheckedThumbColor = MUTED, uncheckedTrackColor = PANEL
            )
        )
    }
}


@Composable
private fun ClockPicker(minutes: Int, inc: Int, onPick: (Int, Int) -> Unit) {
    val presets = listOf(Triple("Bullet", 1, 0), Triple("Blitz", 5, 3),
        Triple("Rapid", 10, 5), Triple("Classical", 30, 0), Triple("None", 0, 0))
    Column(Modifier.padding(horizontal = 16.dp)) {
        presets.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, m, i) ->
                    val sel = m == minutes && i == inc
                    Button(
                        onClick = { onPick(m, i) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) BRASS else PANEL,
                            contentColor = if (sel) BG else BONE
                        )
                    ) {
                        Text(if (m == 0) name else "$name $m+$i", fontSize = 11.sp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemePicker(current: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, label) in listOf("CLASSIC" to "Classic", "WALNUT" to "Walnut", "FOREST" to "Forest")) {
            val sel = current == key
            OutlinedButton(
                onClick = { onPick(key) },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, if (sel) BRASS else MUTED)
            ) { Text(label, color = if (sel) BRASS else MUTED, fontSize = 13.sp) }
        }
    }
}

/** Plain-language explanation of the selected time control (#7). */
fun clockDescription(min: Int, inc: Int): String = when {
    min <= 0 -> "No clocks \u2014 think as long as you like."
    min == 1 && inc == 0 -> "Bullet: 1 minute each for the whole game. Lightning fast \u2014 expect chaos."
    min == 5 -> "Blitz: 5 minutes each, +${inc}s added after every move you make."
    min == 10 -> "Rapid: 10 minutes each, +${inc}s per move. A comfortable casual pace."
    min == 30 -> "Classical: 30 minutes each. Tournament-style thinking time."
    else -> "$min minutes per side" + if (inc > 0) ", +${inc}s added per move." else "."
}

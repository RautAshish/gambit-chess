package com.chessapp.ui.settings

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
private val BRASS = UiColor(0xFFC9A227)
private val MUTED = UiColor(0xFF8A8B7E)

@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val settings by repo.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState())) {
        TopRow("Settings", onBack)

        Section("Gameplay")
        ToggleRow("Show legal moves", settings.showLegalMoves) {
            scope.launch { repo.setShowLegalMoves(it) }
        }
        ToggleRow("Flip board for black", settings.boardFlipped) {
            scope.launch { repo.setFlipped(it) }
        }

        Section("Feedback")
        ToggleRow("Sound effects", settings.soundEnabled) {
            scope.launch { repo.setSound(it) }
        }
        ToggleRow("Haptics", settings.hapticsEnabled) {
            scope.launch { repo.setHaptics(it) }
        }

        Section("Difficulty")
        DifficultyPicker(settings.difficulty) {
            scope.launch { repo.setDifficulty(it) }
        }

        Section("Engine")
        ToggleRow("Use Stockfish (strong)", settings.useStockfish) {
            scope.launch { repo.setUseStockfish(it) }
        }
        Text(
            "Requires bundling Stockfish binaries. Falls back to the built-in engine if unavailable.",
            color = MUTED, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp)
        )

        Section("Clock")
        ClockPicker(settings.clockMinutes, settings.clockIncrementSeconds) { m, inc ->
            scope.launch { repo.setClock(m, inc) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TopRow(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("Back", color = BRASS) }
        Spacer(Modifier.width(8.dp))
        Text(title, color = BONE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
private fun DifficultyPicker(current: ChessAI.Difficulty, onPick: (ChessAI.Difficulty) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in ChessAI.Difficulty.entries) {
            val sel = d == current
            Button(
                onClick = { onPick(d) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sel) BRASS else PANEL,
                    contentColor = if (sel) BG else BONE
                )
            ) { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
        }
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

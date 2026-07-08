package com.chessapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessapp.domain.ai.ChessAI
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.PieceType
import com.chessapp.ui.board.PieceRenderer

private val BG = UiColor(0xFF1C1F17)
private val PANEL = UiColor(0xFF272B20)
private val BONE = UiColor(0xFFEFE6D2)
private val BRASS = UiColor(0xFFE4B02A)
private val MUTED = UiColor(0xFF8A8B7E)

/**
 * Landing screen. The signature element is a knight rendered large in brass — the
 * app's own vector piece, not a stock icon — anchoring the identity. Everything
 * else stays quiet so the one mark carries the screen.
 */
@Composable
fun HomeScreen(
    selectedDifficulty: ChessAI.Difficulty,
    onSelectDifficulty: (ChessAI.Difficulty) -> Unit,
    selectedColor: Color,
    onSelectColor: (Color) -> Unit,
    onPlayAi: (ChessAI.Difficulty, Color) -> Unit,
    onPlayLocal: () -> Unit,
    onPuzzles: () -> Unit,
    puzzlesSolved: Int = 0,
    onSavedGames: () -> Unit,
    onSettings: () -> Unit,
    onPlayOnline: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(BG)
            .statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.size(72.dp)) {
            PieceRenderer.draw(
                this, PieceType.KNIGHT, Color.WHITE, Offset.Zero, size.minDimension,
                fillBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFFFFD86A),
                        androidx.compose.ui.graphics.Color(0xFFE4B02A),
                        androidx.compose.ui.graphics.Color(0xFFB98508)
                    )
                ),
                lineOverride = androidx.compose.ui.graphics.Color(0xFFEFE6D2)
            )
        }
        Text("EMERSION", color = BONE, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp)
        Text("play. learn. repeat.", color = MUTED, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(20.dp))

        // Local-immediate selection with persistence as write-through: starting a
        // game right after tapping a chip must use the tapped value, not wait for
        // the DataStore round-trip (Round-3 caught this race on device). Fresh
        // Home compositions initialise from the persisted values.
        var playAs by remember { mutableStateOf(selectedColor) }
        var diff by remember { mutableStateOf(selectedDifficulty) }

        // One visual group (#15): the card is the ACTION, the rows below are its
        // options — difficulty chips select (highlighted), they don't launch (#20).
        Surface(color = PANEL, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                PrimaryTile(
                    "Play vs Computer",
                    "${diff.name.lowercase().replaceFirstChar { it.uppercase() }} \u00B7 as ${playAs.name.lowercase().replaceFirstChar { it.uppercase() }}"
                ) { onPlayAi(diff, playAs) }
                Spacer(Modifier.height(10.dp))
                PlayAsRow(playAs) { playAs = it; onSelectColor(it) }
                Spacer(Modifier.height(8.dp))
                DifficultySelector(diff) { d -> diff = d; onSelectDifficulty(d) }
            }
        }
        Spacer(Modifier.height(20.dp))

        SecondaryTile("Pass & Play") { onPlayLocal() }
        SecondaryTile("Play Online") { onPlayOnline() }
        SecondaryTile("Puzzles",
            subtitle = if (puzzlesSolved > 0) "$puzzlesSolved solved" else null
        ) { onPuzzles() }
        SecondaryTile("Saved Games") { onSavedGames() }
        SecondaryTile("Settings") { onSettings() }
    }
}

@Composable
private fun PlayAsRow(selected: Color, onPick: (Color) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Play as", color = MUTED, fontSize = 13.sp)
        for (c in listOf(Color.WHITE, Color.BLACK)) {
            val label = c.name.lowercase().replaceFirstChar { it.uppercase() }
            OutlinedButton(
                onClick = { onPick(c) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp),
                border = BorderStroke(1.dp, if (selected == c) BRASS else MUTED)
            ) {
                Text(label, color = if (selected == c) BRASS else MUTED, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PrimaryTile(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BRASS
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = BG, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = UiColor(0xCC1C1F17), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DifficultySelector(selected: ChessAI.Difficulty, onSelect: (ChessAI.Difficulty) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in ChessAI.Difficulty.entries) {
            val sel = d == selected
            OutlinedButton(
                onClick = { onSelect(d) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
                border = BorderStroke(1.dp, if (sel) BRASS else MUTED)
            ) {
                Text(d.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (sel) BRASS else MUTED, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SecondaryTile(title: String, comingSoon: Boolean = false, subtitle: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !comingSoon,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        color = PANEL
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (comingSoon) MUTED else BONE, fontSize = 17.sp)
            if (subtitle != null) {
                Spacer(Modifier.weight(1f))
                Text(subtitle, color = BRASS, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            if (comingSoon) Text("Coming soon", color = MUTED, fontSize = 11.sp)
            else Text("\u203A", color = MUTED, fontSize = 17.sp)
        }
    }
}

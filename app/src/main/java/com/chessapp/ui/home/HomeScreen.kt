package com.chessapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
private val BRASS = UiColor(0xFFC9A227)
private val MUTED = UiColor(0xFF8A8B7E)

/**
 * Landing screen. The signature element is a knight rendered large in brass — the
 * app's own vector piece, not a stock icon — anchoring the identity. Everything
 * else stays quiet so the one mark carries the screen.
 */
@Composable
fun HomeScreen(
    onPlayAi: (ChessAI.Difficulty, Color) -> Unit,
    onPlayLocal: () -> Unit,
    onPuzzles: () -> Unit,
    onSavedGames: () -> Unit,
    onSettings: () -> Unit,
    onPlayOnline: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(BG).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Canvas(Modifier.size(96.dp)) {
            PieceRenderer.draw(this, PieceType.KNIGHT, Color.WHITE, Offset.Zero, size.minDimension)
        }
        Text("GAMBIT", color = BONE, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp)
        Text("play. learn. repeat.", color = MUTED, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(40.dp))

        PrimaryTile("Play vs Computer", "Four difficulty levels") {
            onPlayAi(ChessAI.Difficulty.MEDIUM, Color.WHITE)
        }
        Spacer(Modifier.height(12.dp))
        DifficultyRow(onPlayAi)
        Spacer(Modifier.height(20.dp))

        SecondaryTile("Pass & Play") { onPlayLocal() }
        SecondaryTile("Play Online") { onPlayOnline() }
        SecondaryTile("Puzzles") { onPuzzles() }
        SecondaryTile("Saved Games") { onSavedGames() }
        SecondaryTile("Settings") { onSettings() }
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
private fun DifficultyRow(onPlayAi: (ChessAI.Difficulty, Color) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in ChessAI.Difficulty.entries) {
            OutlinedButton(
                onClick = { onPlayAi(d, Color.WHITE) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)
            ) {
                Text(d.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp, color = BONE)
            }
        }
    }
}

@Composable
private fun SecondaryTile(title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = PANEL
    ) {
        Box(Modifier.padding(18.dp)) {
            Text(title, color = BONE, fontSize = 16.sp)
        }
    }
}

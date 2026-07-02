package com.chessapp.ui.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessapp.domain.model.Color
import com.chessapp.ui.board.BoardCanvas
import com.chessapp.ui.board.paletteFor

private val BG = UiColor(0xFF14140F)
private val BONE = UiColor(0xFFEFE6D2)
private val BRASS = UiColor(0xFFE4B02A)
private val MUTED = UiColor(0xFF8A8F7E)
private val GOOD = UiColor(0xFF7FA869)
private val BAD = UiColor(0xFFB46A55)

@Composable
fun PuzzleScreen(vm: PuzzleViewModel, boardTheme: String, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().background(BG).statusBarsPadding()
            .padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2039 Home", color = BRASS) }
            Spacer(Modifier.weight(1f))
            Text("Puzzle ${s.index + 1}/${s.total}", color = MUTED, fontSize = 13.sp)
            Spacer(Modifier.width(10.dp))
            Text("Solved ${s.solvedCount}", color = BRASS, fontSize = 13.sp,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        val sideName = if (s.sideToMove == Color.WHITE) "White" else "Black"
        val goal = when {
            "mateIn1" in s.themes -> "mate in 1"
            "mateIn2" in s.themes -> "mate in 2"
            else -> "the best move"
        }
        Text(
            when {
                s.solved -> "Solved!"
                s.wrong -> s.message
                s.message.isNotBlank() -> s.message
                else -> "$sideName to move \u00B7 find $goal"
            },
            color = when { s.solved -> GOOD; s.wrong -> BAD; else -> BONE },
            fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        // Side to move plays from the bottom of the board.
        BoardCanvas(
            state = s.board,
            flipped = s.sideToMove == Color.BLACK,
            pal = paletteFor(boardTheme)
        ) { vm.onSquareTapped(it) }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.hint() }, enabled = !s.solved) {
                Text("Hint", color = if (s.solved) MUTED else BONE)
            }
            OutlinedButton(onClick = { vm.retry() }) { Text("Retry", color = BONE) }
            Button(
                onClick = { vm.next() },
                colors = ButtonDefaults.buttonColors(containerColor = BRASS, contentColor = BG)
            ) { Text(if (s.solved) "Next puzzle" else "Skip") }
        }
    }
}

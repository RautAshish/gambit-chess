package com.chessapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessapp.data.db.GameRepository
import com.chessapp.data.db.SavedGame
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BG = UiColor(0xFF1C1F17)
private val PANEL = UiColor(0xFF272B20)
private val BONE = UiColor(0xFFEFE6D2)
private val BRASS = UiColor(0xFFC9A227)
private val MUTED = UiColor(0xFF8A8B7E)

@Composable
fun SavedGamesScreen(
    repo: GameRepository,
    onResume: (SavedGame) -> Unit,
    onBack: () -> Unit
) {
    val games by repo.observeSavedGames().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().background(BG).statusBarsPadding()) {
        var confirmDeleteAll by remember { mutableStateOf(false) }
        val headerScope = rememberCoroutineScope()
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = BRASS) }
            Spacer(Modifier.width(8.dp))
            Text("Saved Games", color = BONE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (games.isNotEmpty()) {
                TextButton(onClick = { confirmDeleteAll = true }) { Text("Delete all", color = MUTED) }
            }
        }
        if (confirmDeleteAll) {
            AlertDialog(
                onDismissRequest = { confirmDeleteAll = false },
                containerColor = PANEL,
                title = { Text("Delete all saved games?", color = BONE, fontWeight = FontWeight.Bold) },
                text = { Text("This removes every saved game and can't be undone.", color = BONE) },
                confirmButton = {
                    TextButton(onClick = { confirmDeleteAll = false; headerScope.launch { repo.deleteAll() } }) {
                        Text("Delete all", color = BRASS)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel", color = MUTED) }
                }
            )
        }

        if (games.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved games yet.\nFinish or pause a game and it'll appear here.",
                    color = MUTED, fontSize = 15.sp)
            }
        } else {
            val scope = rememberCoroutineScope()
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(games, key = { it.id }) { g ->
                    SavedRow(g, onResume, onDelete = { scope.launch { repo.delete(g.id) } })
                }
            }
        }
    }
}

@Composable
private fun SavedRow(g: SavedGame, onResume: (SavedGame) -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp)).testTag("savedRow").clickable { onResume(g) },
        color = PANEL
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (g.vsAi) "vs Computer (${g.difficulty ?: "?"})" else "Pass & Play",
                    color = BONE, fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
                Text(fmt.format(Date(g.updatedAt)), color = MUTED, fontSize = 12.sp)
                if (g.pgn.isNotBlank()) {
                    Text(g.pgn.take(40) + if (g.pgn.length > 40) "\u2026" else "",
                        color = MUTED, fontSize = 12.sp, maxLines = 1)
                }
            }
            ResultBadge(g.result)
            TextButton(onClick = onDelete) { Text("Delete", color = MUTED, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ResultBadge(result: String) {
    val label = when (result) {
        "1-0" -> "1\u20130"; "0-1" -> "0\u20131"; "1/2-1/2" -> "\u00BD\u2013\u00BD"; else -> "live"
    }
    val color = if (result == "*") BRASS else MUTED
    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

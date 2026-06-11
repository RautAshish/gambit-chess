package com.chessapp.ui.online

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessapp.domain.model.Color
import com.chessapp.ui.board.BoardCanvas
import com.chessapp.ui.board.paletteFor

private val BG = UiColor(0xFF14140F)
private val PANEL = UiColor(0xFF1E1F17)
private val BONE = UiColor(0xFFEFE6D2)
private val BRASS = UiColor(0xFFC9A227)
private val MUTED = UiColor(0xFF8A8F7E)
private val BAD = UiColor(0xFFB46A55)

@Composable
fun OnlineScreen(
    vm: OnlineViewModel,
    boardTheme: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val s by vm.state.collectAsState()
    // Cues only while visible: poll-adopted opponent moves stay silent in the
    // background — the same rule the local game follows.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> vm.onAppPaused()
                Lifecycle.Event.ON_RESUME -> vm.onAppResumed()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        Modifier.fillMaxSize().background(BG).statusBarsPadding()
            .padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (s.phase == OnlineUiState.Phase.LOBBY) onBack() else vm.backToLobby()
            }) { Text("\u2039 Back", color = BRASS) }
            Spacer(Modifier.weight(1f))
            Text("Play Online", color = BONE, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }

        if (!s.configured) { SetupCard(onOpenSettings); return }

        when (s.phase) {
            OnlineUiState.Phase.LOBBY -> Lobby(s, vm)
            OnlineUiState.Phase.WAITING -> Waiting(s)
            OnlineUiState.Phase.PLAYING -> Game(s, vm, boardTheme)
        }
        if (s.error.isNotBlank()) {
            Text(s.error, color = BAD, fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Surface(color = PANEL, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("One-time setup needed", color = BONE, fontSize = 17.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Online play runs on your own free Firebase project, so your games " +
                "stay yours. It takes about five minutes: create a project, enable " +
                "Anonymous sign-in and Firestore, then paste the Project ID and Web " +
                "API key into Settings. Full steps: SERVER_SETUP.md in the source repo.",
                color = MUTED, fontSize = 14.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = BRASS, contentColor = BG)
            ) { Text("Open Settings") }
        }
    }
}

@Composable
private fun Lobby(s: OnlineUiState, vm: OnlineViewModel) {
    var code by remember { mutableStateOf("") }
    Spacer(Modifier.height(20.dp))
    Surface(color = PANEL, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Start a game with a friend", color = BONE, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("You'll get a 6-letter code to share. You play White.",
                color = MUTED, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.createGame() }, enabled = !s.busy,
                colors = ButtonDefaults.buttonColors(containerColor = BRASS, contentColor = BG)
            ) { Text(if (s.busy) "Working\u2026" else "Create game") }
        }
    }
    Spacer(Modifier.height(14.dp))
    Surface(color = PANEL, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("Join with a code", color = BONE, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(6) },
                label = { Text("Game code", color = MUTED) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.joinGame(code) }, enabled = !s.busy && code.length == 6) {
                Text("Join game", color = if (code.length == 6) BONE else MUTED)
            }
        }
    }
}

@Composable
private fun Waiting(s: OnlineUiState) {
    Spacer(Modifier.height(36.dp))
    Text("Game code", color = MUTED, fontSize = 13.sp)
    Text(s.code, color = BRASS, fontSize = 44.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 6.sp)
    Spacer(Modifier.height(10.dp))
    Text(s.statusLine, color = BONE, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    Text("Your friend taps Play Online \u2192 Join with a code.", color = MUTED, fontSize = 13.sp)
}

@Composable
private fun Game(s: OnlineUiState, vm: OnlineViewModel, boardTheme: String) {
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Code ${s.code}", color = MUTED, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("You are ${if (s.myColor == Color.BLACK) "Black" else "White"}",
            color = BONE, fontSize = 13.sp)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        if (s.gameOver) s.resultLine else s.statusLine,
        color = if (s.gameOver) BRASS else BONE,
        fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    BoardCanvas(
        state = s.board,
        flipped = s.myColor == Color.BLACK,
        pal = paletteFor(boardTheme)
    ) { vm.onSquareTapped(it) }
    Spacer(Modifier.height(12.dp))
    if (!s.gameOver) {
        OutlinedButton(onClick = { vm.resign() }, enabled = !s.busy) {
            Text("Resign", color = BONE)
        }
    }
    if (s.moveList.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Surface(color = PANEL, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Moves", color = BRASS, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(s.moveList, color = BONE, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
    }
}

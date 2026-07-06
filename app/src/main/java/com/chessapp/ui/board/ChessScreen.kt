package com.chessapp.ui.board

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessapp.domain.model.*

private val BG = UiColor(0xFF1C1F17)
private val PANEL = UiColor(0xFF272B20)
private val BONE = UiColor(0xFFEFE6D2)
// Board themes (#3): CLASSIC reads as black-and-white without glare; WALNUT is
// the tournament-wood look; FOREST is the original green.
data class BoardPalette(val light: UiColor, val dark: UiColor)
fun paletteFor(theme: String): BoardPalette = when (theme) {
    "WALNUT" -> BoardPalette(UiColor(0xFFE3C9A2), UiColor(0xFF8B5E3C))
    "FOREST" -> BoardPalette(UiColor(0xFFD9CFB4), UiColor(0xFF6E7E55))
    else -> BoardPalette(UiColor(0xFFEDEAE2), UiColor(0xFF75756B))   // CLASSIC
}
private val BRASS = UiColor(0xFFE4B02A)
private val MUTED = UiColor(0xFF8A8F7E)
private val SELECT = UiColor(0x803C8C5F)
private val TARGET = UiColor(0x55201C16)
private val LAST = UiColor(0x55C9A227)
private val CHECK = UiColor(0x99C0392B)

@Composable
fun ChessScreen(vm: ChessViewModel, flipped: Boolean = false, onBack: () -> Unit = {}, boardTheme: String = "CLASSIC") {
    val state by vm.state.collectAsState()

    // Pause the clock when the app goes to the background; resume on return.
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

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("\u2039 Home", color = BRASS) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.toggleSound() }) {
                    Text(if (state.soundOn) "Mute" else "Unmute", color = MUTED)
                }
            }
            CapturedTray(state.capturedByBlack, label = "Black captured",
                balance = -state.materialBalance)
            ClockRow(Color.BLACK, state.blackClock,
                active = state.useClock && state.board.sideToMove == Color.BLACK)
            Spacer(Modifier.height(8.dp))
            StatusBar(state)
            Spacer(Modifier.height(8.dp))
            BoardCanvas(state, flipped, paletteFor(boardTheme)) { vm.onSquareTapped(it) }
            Spacer(Modifier.height(8.dp))
            ClockRow(Color.WHITE, state.whiteClock,
                active = state.useClock && state.board.sideToMove == Color.WHITE)
            CapturedTray(state.capturedByWhite, label = "White captured",
                balance = state.materialBalance)
            Spacer(Modifier.height(12.dp))
            Controls(state, vm)
            Spacer(Modifier.height(12.dp))
            MoveList(state.moveList)
        }

        if (state.pendingPromotion != null) {
            PromotionDialog(state.board.sideToMove, { vm.choosePromotion(it) }, { vm.cancelPromotion() })
        }
        if (state.drawOfferPending) {
            DrawOfferDialog(onAccept = { vm.acceptDraw() }, onDecline = { vm.declineDraw() })
        }
        if (state.drawDeclined) {
            AlertDialog(
                onDismissRequest = { vm.clearDrawDeclined() },
                containerColor = PANEL,
                title = { Text("Draw declined", color = BONE, fontWeight = FontWeight.Bold) },
                text = { Text("Your opponent declined the draw offer.", color = BONE) },
                confirmButton = {
                    TextButton(onClick = { vm.clearDrawDeclined() }) { Text("OK", color = BRASS) }
                }
            )
        }
        var resultDismissed by remember(state.result) { mutableStateOf(false) }
        // Let the final move's slide and sound land before covering the board.
        var resultRevealed by remember(state.result) { mutableStateOf(false) }
        LaunchedEffect(state.gameOver, state.result) {
            if (state.gameOver) { kotlinx.coroutines.delay(700); resultRevealed = true }
        }
        if (state.gameOver && resultRevealed && !resultDismissed) {
            GameOverDialog(state, onViewBoard = { resultDismissed = true }) { vm.newGame() }
        }
    }
}

@Composable
private fun ClockRow(color: Color, time: String, active: Boolean) {
    if (time.isBlank()) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (active) PANEL else BG)
            .border(1.dp, if (active) BRASS else PANEL, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (color == Color.WHITE) "White" else "Black", color = BONE, fontSize = 14.sp)
        Text(time, color = if (active) BRASS else BONE, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CapturedTray(captured: List<Piece>, label: String, balance: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).height(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (captured.isNotEmpty()) {
            // Fixed light chip behind the row: verbatim-black captured pieces
            // were invisible on the near-black page (same lesson as promo tiles).
            Box(
                Modifier.height(22.dp).width((captured.size * 16 + 8).dp)
                    .background(UiColor(0xFFEDEAE2), RoundedCornerShape(4.dp))
            ) {
                Canvas(Modifier.fillMaxHeight().fillMaxWidth().padding(horizontal = 4.dp)) {
                    val s = 20.dp.toPx()
                    captured.forEachIndexed { i, p ->
                        PieceRenderer.draw(this, p.type, p.color, Offset(i * s * 0.8f, 0f), s)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (balance > 0) Text("+$balance", color = BRASS, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBar(state: BoardUiState) {
    // Once the game has a result (incl. resignation/timeout/agreement, which the
    // board status can't express), the status line carries it — so the outcome
    // stays visible after the dialog is dismissed to inspect the final position.
    if (state.gameOver) {
        Text(
            "${state.resultHeadline} \u00B7 ${state.resultDetail}",
            color = BRASS, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        return
    }
    val text = when (state.status) {
        GameStatus.CHECKMATE -> "Checkmate"
        GameStatus.STALEMATE -> "Stalemate \u2014 draw"
        GameStatus.DRAW_FIFTY_MOVE -> "Draw \u2014 fifty-move rule"
        GameStatus.DRAW_REPETITION -> "Draw \u2014 repetition"
        GameStatus.DRAW_INSUFFICIENT_MATERIAL -> "Draw \u2014 insufficient material"
        GameStatus.CHECK -> "Check"
        GameStatus.ONGOING ->
            if (state.thinking) "Thinking\u2026"
            else "${state.board.sideToMove.name.lowercase().replaceFirstChar { it.uppercase() }} to move"
    }
    Text(text, color = BONE, fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun Controls(state: BoardUiState, vm: ChessViewModel) {
    var confirmResign by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.undo() }, enabled = state.canUndo) { Text("Undo") }
            OutlinedButton(onClick = { vm.redo() }, enabled = state.canRedo) { Text("Redo") }
            Button(
                onClick = { vm.newGame() },
                colors = ButtonDefaults.buttonColors(containerColor = BRASS, contentColor = BG)
            ) { Text("New game") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmResign = true }, enabled = state.canResign) {
                Text("Resign")
            }
            OutlinedButton(onClick = { vm.offerDraw() }, enabled = state.canOfferDraw) {
                Text("Offer draw")
            }
        }
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            containerColor = PANEL,
            title = { Text("Resign?", color = BONE, fontWeight = FontWeight.Bold) },
            text = { Text("This ends the game as a loss.", color = BONE) },
            confirmButton = {
                Button(
                    onClick = { confirmResign = false; vm.resign() },
                    colors = ButtonDefaults.buttonColors(containerColor = CHECK, contentColor = BONE)
                ) { Text("Resign") }
            },
            dismissButton = {
                TextButton(onClick = { confirmResign = false }) { Text("Cancel", color = BRASS) }
            }
        )
    }
}

@Composable
private fun DrawOfferDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = PANEL,
        title = { Text("Draw offered", color = BONE, fontWeight = FontWeight.Bold) },
        text = { Text("Your opponent offers a draw.", color = BONE) },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = BRASS, contentColor = BG)
            ) { Text("Accept") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Decline", color = BRASS) }
        }
    )
}

@Composable
private fun MoveList(pgn: String) {
    if (pgn.isBlank()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PANEL).padding(12.dp)) {
        Text("Moves", color = BRASS, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(pgn, color = BONE, fontSize = 14.sp)
    }
}

@Composable
private fun PromotionDialog(color: Color, onPick: (PieceType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        containerColor = PANEL,
        title = { Text("Promote to", color = BONE) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val squares = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
                squares.forEachIndexed { i, t ->
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(UiColor(0xFFEDEAE2))  // fixed light tile:
                            .testTag("promote-${t.name}")     // promo pieces must pop on any theme
                            .semantics { contentDescription = "Promote to ${t.name.lowercase()}" }
                            .pointerInput(t) { detectTapGestures { onPick(t) } }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            PieceRenderer.draw(this, t, color, Offset.Zero, size.minDimension)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun GameOverDialog(state: BoardUiState, onViewBoard: () -> Unit, onNewGame: () -> Unit) {
    val title = state.resultHeadline.ifBlank { "Game over" }
    val detail = state.resultDetail
    AlertDialog(
        onDismissRequest = onViewBoard,
        containerColor = PANEL,
        title = { Text(title, color = BRASS, fontWeight = FontWeight.Bold) },
        text = { Text(detail, color = BONE) },
        confirmButton = {
            Button(
                onClick = onNewGame,
                colors = ButtonDefaults.buttonColors(containerColor = BRASS, contentColor = BG)
            ) { Text("New game") }
        },
        dismissButton = {
            TextButton(onClick = onViewBoard) { Text("View board", color = BRASS) }
        }
    )
}

@Composable
fun BoardCanvas(state: BoardUiState, flipped: Boolean, pal: BoardPalette, onTap: (Square) -> Unit) {
    val measurer = rememberTextMeasurer()
    // Animation progress 0..1 for the sliding piece.
    val anim = state.animating
    val progress by animateFloatAsState(
        targetValue = if (anim != null) 1f else 0f,
        animationSpec = tween(durationMillis = ChessViewModel.ANIM_MS.toInt(), easing = LinearEasing),
        label = "slide"
    )

    BoxWithConstraints {
        val side = maxWidth
        Canvas(
            Modifier.size(side).testTag("board")
                .semantics { this.contentDescription = "Chess board" }.clip(RoundedCornerShape(6.dp)).pointerInput(flipped) {
                detectTapGestures { offset ->
                    val cell = size.width / 8f
                    val col = (offset.x / cell).toInt().coerceIn(0, 7)
                    val row = (offset.y / cell).toInt().coerceIn(0, 7)
                    val file = if (flipped) 7 - col else col
                    val rank = if (flipped) row else 7 - row
                    onTap(Square.of(file, rank))
                }
            }
        ) {
            val cell = size.width / 8f
            fun screenPos(sq: Square): Offset {
                val c = if (flipped) 7 - sq.file else sq.file
                val r = if (flipped) sq.rank else 7 - sq.rank
                return Offset(c * cell, r * cell)
            }

            for (r in 0..7) for (c in 0..7) {
                val file = if (flipped) 7 - c else c
                val rank = if (flipped) r else 7 - r
                val sq = Square.of(file, rank)
                val light = (file + rank) % 2 == 1
                val tl = Offset(c * cell, r * cell)
                val sz = Size(cell, cell)
                drawRect(if (light) pal.light else pal.dark, tl, sz)

                if (sq == state.lastMove?.from || sq == state.lastMove?.to) drawRect(LAST, tl, sz)
                if (sq == state.selected) drawRect(SELECT, tl, sz)
                if (sq == state.checkSquare) drawRect(CHECK, tl, sz)
                if (sq in state.legalTargets) {
                    val center = Offset(c * cell + cell / 2, r * cell + cell / 2)
                    if (state.board.pieceAt(sq) != null)
                        drawCircle(TARGET, cell * 0.46f, center, style = Stroke(width = cell * 0.08f))
                    else drawCircle(TARGET, cell * 0.16f, center)
                }

                // Coordinate labels on the edges.
                if (state.showCoordinates) {
                    val labelColor = if (light) pal.dark else pal.light
                    if (c == 0) {
                        val lay = measurer.measure((rank + 1).toString(),
                            TextStyle(color = labelColor, fontSize = (cell * 0.16f / 2.625f).sp,
                                fontWeight = FontWeight.Bold))
                        drawText(lay, topLeft = Offset(tl.x + cell * 0.04f, tl.y + cell * 0.03f))
                    }
                    if (r == 7) {
                        val lay = measurer.measure(('a' + file).toString(),
                            TextStyle(color = labelColor, fontSize = (cell * 0.16f / 2.625f).sp,
                                fontWeight = FontWeight.Bold))
                        drawText(lay, topLeft = Offset(tl.x + cell * 0.78f, tl.y + cell * 0.78f))
                    }
                }
            }

            // Draw pieces. Skip the piece on the animation's ORIGIN square (it is
            // still on state.board because the move applies only after the slide);
            // the sliding copy is drawn separately at its interpolated position.
            for ((sq, piece) in state.board.allPieces()) {
                if (anim != null && sq == anim.from) continue
                drawPiece(piece, screenPos(sq), cell)
            }
            if (anim != null) {
                val from = screenPos(anim.from)
                val to = screenPos(anim.to)
                val pos = Offset(
                    from.x + (to.x - from.x) * progress,
                    from.y + (to.y - from.y) * progress
                )
                drawPiece(anim.piece, pos, cell)
            }
        }
    }
}

private fun DrawScope.drawPiece(piece: Piece, topLeft: Offset, cell: Float) {
    PieceRenderer.draw(this, piece.type, piece.color, topLeft, cell)
}

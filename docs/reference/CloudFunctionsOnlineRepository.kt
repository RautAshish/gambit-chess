package com.chessapp.data.online

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Multiplayer client. Reads game state live from Firestore, but performs all
 * MUTATIONS through Cloud Functions (submitMove / findMatch / resign). Firestore
 * security rules make /games read-only to clients, so the server is the sole
 * authority — a tampered client literally cannot write an illegal move.
 *
 * The local [OnlineGameValidator] is still useful for OPTIMISTIC UI: apply the move
 * locally for instant feedback, then reconcile when the server's update streams
 * back via [observeGame]. Because the server runs the identical validation logic
 * (verified byte-for-byte against this engine), the optimistic result and the
 * authoritative result agree except when the client is cheating or stale.
 */
class OnlineRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    private val games get() = db.collection("games")

    /** Calls the findMatch function; returns a gameId if paired, else null (queued). */
    suspend fun findMatch(): String? {
        val result = functions.getHttpsCallable("findMatch")
            .call().await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return null
        return data["gameId"] as? String
    }

    /** Live stream of a game's state. */
    fun observeGame(gameId: String): Flow<OnlineGame> = callbackFlow {
        val reg = games.document(gameId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            snap?.toOnlineGame()?.let { trySend(it) }
        }
        awaitClose { reg.remove() }
    }

    /**
     * Submits a move via the authoritative Cloud Function. Throws on rejection
     * (not-your-turn, illegal, game-over) so the caller can roll back optimistic UI.
     */
    suspend fun submitMove(gameId: String, uci: String): SubmitResult {
        val result = functions.getHttpsCallable("submitMove")
            .call(mapOf("gameId" to gameId, "uci" to uci)).await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: return SubmitResult("ONGOING", null)
        return SubmitResult(
            status = data["status"] as? String ?: "ONGOING",
            fen = data["fen"] as? String
        )
    }

    suspend fun resign(gameId: String) {
        functions.getHttpsCallable("resign")
            .call(mapOf("gameId" to gameId)).await()
    }

    private fun DocumentSnapshot.toOnlineGame(): OnlineGame? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        return OnlineGame(
            id = id,
            whiteUid = getString("whiteUid") ?: "",
            blackUid = getString("blackUid") ?: "",
            moves = (get("moves") as? List<String>) ?: emptyList(),
            fen = getString("fen") ?: com.chessapp.domain.model.Board.START_FEN,
            status = getString("status") ?: "ONGOING",
            winnerUid = getString("winnerUid"),
            whiteTimeMillis = getLong("whiteTimeMillis") ?: 0,
            blackTimeMillis = getLong("blackTimeMillis") ?: 0,
            updatedAt = getLong("updatedAt") ?: 0
        )
    }
}

/** Server's authoritative verdict after a move. */
data class SubmitResult(val status: String, val fen: String?)

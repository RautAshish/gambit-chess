import { initializeApp } from "firebase-admin/app";
import {
  getFirestore,
  FieldValue,
  Transaction,
  DocumentReference,
} from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { Board } from "./board";
import { applyMove, OnlineGame } from "./engine";

initializeApp();
const db = getFirestore();

/** Read a game doc inside a transaction into our typed shape. */
async function readGame(
  tx: Transaction,
  ref: DocumentReference
): Promise<OnlineGame | null> {
  const snap = await tx.get(ref);
  if (!snap.exists) return null;
  const d = snap.data()!;
  return {
    id: ref.id,
    whiteUid: d.whiteUid ?? "",
    blackUid: d.blackUid ?? "",
    moves: (d.moves as string[]) ?? [],
    fen: d.fen ?? Board.START_FEN,
    status: d.status ?? "ONGOING",
    winnerUid: d.winnerUid ?? null,
    updatedAt: d.updatedAt ?? 0,
  };
}

/**
 * submitMove — the authority. The client calls this instead of writing the game
 * doc directly. We re-derive the board from the stored move list and validate with
 * the SAME logic the client uses (proven byte-identical to the Kotlin engine), so
 * a tampered client cannot push an illegal or out-of-turn move.
 *
 * Lock down Firestore so clients CANNOT write /games/{id} directly — only this
 * function (running with admin privileges) may. See firestore.rules.
 */
export const submitMove = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to play.");

  const gameId = request.data?.gameId as string | undefined;
  const uci = request.data?.uci as string | undefined;
  if (!gameId || !uci) {
    throw new HttpsError("invalid-argument", "gameId and uci are required.");
  }

  const ref = db.collection("games").doc(gameId);

  return db.runTransaction(async (tx) => {
    const game = await readGame(tx, ref);
    if (!game) throw new HttpsError("not-found", "Game not found.");

    const outcome = applyMove(game, uid, uci);
    switch (outcome.kind) {
      case "Applied":
        tx.set(ref, { ...outcome.game, updatedAt: FieldValue.serverTimestamp() });
        return { status: outcome.game.status, fen: outcome.game.fen };
      case "NotYourTurn":
        throw new HttpsError("failed-precondition", "Not your turn.");
      case "IllegalMove":
        throw new HttpsError("failed-precondition", "Illegal move.");
      case "GameOver":
        throw new HttpsError("failed-precondition", "Game is already over.");
    }
  });
});

/**
 * findMatch — atomic matchmaking. Claims a waiting opponent or enqueues the caller.
 * Returns { gameId } when paired, or { waiting: true } if now in the queue.
 */
export const findMatch = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to play.");

  const queue = db.collection("matchmaking");
  const games = db.collection("games");

  return db.runTransaction(async (tx) => {
    // Find one waiting opponent that isn't us.
    const waitingSnap = await tx.get(
      queue.where("status", "==", "waiting").limit(5)
    );
    const opponent = waitingSnap.docs.find((d) => d.get("uid") !== uid);

    if (opponent) {
      const gameRef = games.doc();
      const newGame: OnlineGame = {
        id: gameRef.id,
        whiteUid: opponent.get("uid"),
        blackUid: uid,
        moves: [],
        fen: Board.START_FEN,
        status: "ONGOING",
        winnerUid: null,
        updatedAt: 0,
      };
      tx.set(gameRef, { ...newGame, updatedAt: FieldValue.serverTimestamp() });
      tx.update(opponent.ref, { status: "matched", gameId: gameRef.id });
      tx.set(queue.doc(uid), { uid, status: "matched", gameId: gameRef.id });
      return { gameId: gameRef.id };
    }

    // No one waiting — enqueue ourselves.
    tx.set(queue.doc(uid), { uid, status: "waiting" });
    return { waiting: true };
  });
});

/** resign — ends the game, awarding the win to the opponent. */
export const resign = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to play.");
  const gameId = request.data?.gameId as string | undefined;
  if (!gameId) throw new HttpsError("invalid-argument", "gameId is required.");

  const ref = db.collection("games").doc(gameId);
  return db.runTransaction(async (tx) => {
    const game = await readGame(tx, ref);
    if (!game) throw new HttpsError("not-found", "Game not found.");
    if (uid !== game.whiteUid && uid !== game.blackUid) {
      throw new HttpsError("permission-denied", "You are not in this game.");
    }
    const winner = uid === game.whiteUid ? game.blackUid : game.whiteUid;
    tx.update(ref, {
      status: "RESIGNED",
      winnerUid: winner,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { status: "RESIGNED", winnerUid: winner };
  });
});

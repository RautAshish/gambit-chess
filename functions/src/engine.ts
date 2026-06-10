import { Board, Move, GameStatus, moveFromUci, opposite } from "./board";
import { legalMoves, isSquareAttacked } from "./movegen";

/**
 * Game state with status detection — port of the Kotlin GameEngine. Tracks the
 * position history so threefold repetition and the fifty-move rule work.
 */
export class GameEngine {
  private history: Board[];
  private moves: Move[] = [];

  constructor(start: Board = Board.initial()) {
    this.history = [start];
  }

  get board(): Board {
    return this.history[this.history.length - 1];
  }

  legalMoves(): Move[] {
    return legalMoves(this.board);
  }

  makeMove(move: Move): boolean {
    const legal = this.legalMoves().find(
      (m) =>
        m.from === move.from &&
        m.to === move.to &&
        (m.promotion ?? undefined) === (move.promotion ?? undefined)
    );
    if (!legal) return false;
    this.history.push(this.board.apply(legal));
    this.moves.push(legal);
    return true;
  }

  inCheck(color = this.board.sideToMove): boolean {
    const king = this.board.kingSquare(color);
    if (king === null) return false;
    return isSquareAttacked(this.board, king, opposite(color));
  }

  status(): GameStatus {
    const moves = this.legalMoves();
    const check = this.inCheck();
    if (moves.length === 0) return check ? "CHECKMATE" : "STALEMATE";
    if (this.board.halfmoveClock >= 100) return "DRAW_FIFTY_MOVE";
    if (this.isThreefoldRepetition()) return "DRAW_REPETITION";
    if (this.isInsufficientMaterial()) return "DRAW_INSUFFICIENT_MATERIAL";
    return check ? "CHECK" : "ONGOING";
  }

  private positionKey(b: Board): string {
    return b.toFen().split(" ").slice(0, 4).join(" ");
  }

  private isThreefoldRepetition(): boolean {
    const key = this.positionKey(this.board);
    return this.history.filter((b) => this.positionKey(b) === key).length >= 3;
  }

  private isInsufficientMaterial(): boolean {
    const nonKings = this.board.allPieces()
      .map((x) => x.piece)
      .filter((p) => p.type !== "k");
    if (nonKings.length === 0) return true;
    if (nonKings.length === 1 && (nonKings[0].type === "b" || nonKings[0].type === "n"))
      return true;
    return false;
  }
}

/** Mirrors Kotlin OnlineGame. */
export interface OnlineGame {
  id: string;
  whiteUid: string;
  blackUid: string;
  moves: string[];
  fen: string;
  status: string;
  winnerUid: string | null;
  updatedAt: number;
}

export type MoveOutcome =
  | { kind: "Applied"; game: OnlineGame }
  | { kind: "NotYourTurn" }
  | { kind: "IllegalMove" }
  | { kind: "GameOver" };

/**
 * Server-authoritative validation — a 1:1 port of Kotlin OnlineGameValidator.
 * Rebuilds the board from the stored move list (never trusting a client-supplied
 * position), checks turn and legality, then returns the updated game.
 */
export function applyMove(
  game: OnlineGame,
  byUid: string,
  uci: string
): MoveOutcome {
  if (game.status !== "ONGOING" && game.status !== "CHECK") {
    return { kind: "GameOver" };
  }

  const engine = new GameEngine();
  for (const m of game.moves) {
    let parsed: Move;
    try {
      parsed = moveFromUci(m);
    } catch {
      return { kind: "IllegalMove" };
    }
    if (!engine.makeMove(parsed)) return { kind: "IllegalMove" };
  }

  const mover =
    byUid === game.whiteUid ? "w" : byUid === game.blackUid ? "b" : null;
  if (mover === null) return { kind: "NotYourTurn" };
  if (engine.board.sideToMove !== mover) return { kind: "NotYourTurn" };

  let move: Move;
  try {
    move = moveFromUci(uci);
  } catch {
    return { kind: "IllegalMove" };
  }
  if (!engine.makeMove(move)) return { kind: "IllegalMove" };

  const status = engine.status();
  const winner = status === "CHECKMATE" ? byUid : null;

  return {
    kind: "Applied",
    game: {
      ...game,
      moves: [...game.moves, uci],
      fen: engine.board.toFen(),
      status,
      winnerUid: winner,
      updatedAt: Date.now(),
    },
  };
}

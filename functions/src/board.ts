/**
 * Minimal chess rules engine in TypeScript — a faithful port of the Kotlin
 * domain layer (Board, MoveGenerator, GameEngine). Its ONLY job is server-side
 * move validation, so it mirrors the Kotlin logic exactly. Equivalence between the
 * two is enforced by a shared test-vector suite (see test/equivalence.test.ts):
 * both engines must agree on legality, FEN, and Perft for the same positions.
 *
 * Square index 0..63: a1=0, h1=7, a8=56, h8=63. file = idx & 7, rank = idx >> 3.
 */

export type Color = "w" | "b";
export type PieceType = "p" | "n" | "b" | "r" | "q" | "k";

export interface Piece {
  type: PieceType;
  color: Color;
}

export interface Move {
  from: number;
  to: number;
  promotion?: PieceType;
  isCastle?: boolean;
  isEnPassant?: boolean;
  isDoublePush?: boolean;
}

export interface CastlingRights {
  wk: boolean; wq: boolean; bk: boolean; bq: boolean;
}

export type GameStatus =
  | "ONGOING" | "CHECK" | "CHECKMATE" | "STALEMATE"
  | "DRAW_FIFTY_MOVE" | "DRAW_REPETITION" | "DRAW_INSUFFICIENT_MATERIAL";

export const opposite = (c: Color): Color => (c === "w" ? "b" : "w");
export const fileOf = (sq: number): number => sq & 7;
export const rankOf = (sq: number): number => sq >> 3;
export const squareOf = (file: number, rank: number): number => rank * 8 + file;

export function parseSquare(s: string): number {
  if (s.length !== 2) throw new Error(`bad square: ${s}`);
  const file = s.charCodeAt(0) - 97; // 'a'
  const rank = s.charCodeAt(1) - 49; // '1'
  if (file < 0 || file > 7 || rank < 0 || rank > 7) throw new Error(`square out of range: ${s}`);
  return squareOf(file, rank);
}

export function squareToString(sq: number): string {
  return String.fromCharCode(97 + fileOf(sq)) + String(rankOf(sq) + 1);
}

export function moveToUci(m: Move): string {
  return squareToString(m.from) + squareToString(m.to) + (m.promotion ?? "");
}

export function moveFromUci(s: string): Move {
  if (s.length !== 4 && s.length !== 5) throw new Error(`bad UCI move: ${s}`);
  const from = parseSquare(s.slice(0, 2));
  const to = parseSquare(s.slice(2, 4));
  let promotion: PieceType | undefined;
  if (s.length === 5) {
    const p = s[4].toLowerCase();
    if (p !== "q" && p !== "r" && p !== "b" && p !== "n") {
      throw new Error(`bad promotion in UCI: ${s}`);
    }
    promotion = p as PieceType;
  }
  return { from, to, promotion };
}

function pieceFromFenChar(c: string): Piece {
  const color: Color = c === c.toUpperCase() ? "w" : "b";
  return { type: c.toLowerCase() as PieceType, color };
}

function pieceFenChar(p: Piece): string {
  return p.color === "w" ? p.type.toUpperCase() : p.type;
}

export class Board {
  static readonly START_FEN =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  constructor(
    readonly squares: (Piece | null)[], // length 64
    readonly sideToMove: Color,
    readonly castling: CastlingRights,
    readonly enPassant: number | null,
    readonly halfmoveClock: number,
    readonly fullmoveNumber: number
  ) {}

  pieceAt(sq: number): Piece | null {
    return this.squares[sq];
  }

  kingSquare(color: Color): number | null {
    for (let i = 0; i < 64; i++) {
      const p = this.squares[i];
      if (p && p.type === "k" && p.color === color) return i;
    }
    return null;
  }

  allPieces(): Array<{ sq: number; piece: Piece }> {
    const out: Array<{ sq: number; piece: Piece }> = [];
    for (let i = 0; i < 64; i++) {
      const p = this.squares[i];
      if (p) out.push({ sq: i, piece: p });
    }
    return out;
  }

  apply(move: Move): Board {
    const next = this.squares.slice();
    const moving = next[move.from]!;

    const isCapture = next[move.to] !== null || !!move.isEnPassant;
    const isPawn = moving.type === "p";
    const newHalfmove = isCapture || isPawn ? 0 : this.halfmoveClock + 1;

    next[move.from] = null;
    next[move.to] = move.promotion
      ? { type: move.promotion, color: moving.color }
      : moving;

    if (move.isEnPassant) {
      const capturedRank = rankOf(move.from);
      next[squareOf(fileOf(move.to), capturedRank)] = null;
    }

    if (move.isCastle) {
      const rank = rankOf(move.from);
      if (fileOf(move.to) === 6) {
        next[squareOf(5, rank)] = next[squareOf(7, rank)];
        next[squareOf(7, rank)] = null;
      } else if (fileOf(move.to) === 2) {
        next[squareOf(3, rank)] = next[squareOf(0, rank)];
        next[squareOf(0, rank)] = null;
      }
    }

    const c = { ...this.castling };
    if (moving.type === "k") {
      if (moving.color === "w") { c.wk = false; c.wq = false; }
      else { c.bk = false; c.bq = false; }
    }
    const touch = (sq: number) => {
      if (sq === parseSquare("a1")) c.wq = false;
      else if (sq === parseSquare("h1")) c.wk = false;
      else if (sq === parseSquare("a8")) c.bq = false;
      else if (sq === parseSquare("h8")) c.bk = false;
    };
    touch(move.from);
    touch(move.to);

    const newEp = move.isDoublePush
      ? squareOf(fileOf(move.from), (rankOf(move.from) + rankOf(move.to)) / 2)
      : null;

    return new Board(
      next,
      opposite(this.sideToMove),
      c,
      newEp,
      newHalfmove,
      this.sideToMove === "b" ? this.fullmoveNumber + 1 : this.fullmoveNumber
    );
  }

  toFen(): string {
    let out = "";
    for (let rank = 7; rank >= 0; rank--) {
      let empty = 0;
      for (let file = 0; file < 8; file++) {
        const p = this.squares[squareOf(file, rank)];
        if (!p) empty++;
        else {
          if (empty > 0) { out += empty; empty = 0; }
          out += pieceFenChar(p);
        }
      }
      if (empty > 0) out += empty;
      if (rank > 0) out += "/";
    }
    out += " " + this.sideToMove + " ";
    let cr = "";
    if (this.castling.wk) cr += "K";
    if (this.castling.wq) cr += "Q";
    if (this.castling.bk) cr += "k";
    if (this.castling.bq) cr += "q";
    out += cr === "" ? "-" : cr;
    out += " " + (this.enPassant === null ? "-" : squareToString(this.enPassant));
    out += " " + this.halfmoveClock + " " + this.fullmoveNumber;
    return out;
  }

  static initial(): Board {
    return Board.fromFen(Board.START_FEN);
  }

  static fromFen(fen: string): Board {
    const parts = fen.trim().split(" ");
    const squares: (Piece | null)[] = new Array(64).fill(null);
    const rows = parts[0].split("/");
    for (let rowIdx = 0; rowIdx < 8; rowIdx++) {
      const rank = 7 - rowIdx;
      let file = 0;
      for (const ch of rows[rowIdx]) {
        if (ch >= "1" && ch <= "9") file += ch.charCodeAt(0) - 48;
        else { squares[squareOf(file, rank)] = pieceFromFenChar(ch); file++; }
      }
    }
    const side = parts[1] === "w" ? "w" : "b";
    const cr = parts[2];
    const castling: CastlingRights = {
      wk: cr.includes("K"), wq: cr.includes("Q"),
      bk: cr.includes("k"), bq: cr.includes("q"),
    };
    const ep = parts[3] === "-" ? null : parseSquare(parts[3]);
    const half = parts[4] ? parseInt(parts[4], 10) : 0;
    const full = parts[5] ? parseInt(parts[5], 10) : 1;
    return new Board(squares, side, castling, ep, half, full);
  }
}

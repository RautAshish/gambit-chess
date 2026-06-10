import {
  Board, Color, Move, Piece, PieceType,
  fileOf, rankOf, squareOf, opposite,
} from "./board";

/**
 * Legal move generation, ported 1:1 from the Kotlin MoveGenerator. Same strategy:
 * generate pseudo-legal moves, then reject any that leave the mover's king in check.
 */

const KNIGHT_OFFSETS = [-17, -15, -10, -6, 6, 10, 15, 17];
const KING_OFFSETS = [-9, -8, -7, -1, 1, 7, 8, 9];
const ROOK_DIRS: Array<[number, number]> = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const BISHOP_DIRS: Array<[number, number]> = [[1, 1], [1, -1], [-1, 1], [-1, -1]];

export function legalMoves(board: Board): Move[] {
  const side = board.sideToMove;
  return pseudoLegalMoves(board).filter((move) => {
    const after = board.apply(move);
    const king = after.kingSquare(side);
    if (king === null) return false;
    return !isSquareAttacked(after, king, opposite(side));
  });
}

export function pseudoLegalMoves(board: Board): Move[] {
  const moves: Move[] = [];
  const side = board.sideToMove;
  for (const { sq, piece } of board.allPieces()) {
    if (piece.color !== side) continue;
    switch (piece.type) {
      case "p": pawnMoves(board, sq, piece, moves); break;
      case "n": stepMoves(board, sq, piece, KNIGHT_OFFSETS, moves, 2); break;
      case "k":
        stepMoves(board, sq, piece, KING_OFFSETS, moves, 1);
        castleMoves(board, sq, piece, moves);
        break;
      case "r": slideMoves(board, sq, piece, ROOK_DIRS, moves); break;
      case "b": slideMoves(board, sq, piece, BISHOP_DIRS, moves); break;
      case "q": slideMoves(board, sq, piece, [...ROOK_DIRS, ...BISHOP_DIRS], moves); break;
    }
  }
  return moves;
}

function stepMoves(
  board: Board, from: number, piece: Piece,
  offsets: number[], out: Move[], maxFileJump: number
): void {
  for (const off of offsets) {
    const to = from + off;
    if (to < 0 || to > 63) continue;
    if (Math.abs(fileOf(to) - fileOf(from)) > maxFileJump) continue;
    const occ = board.pieceAt(to);
    if (occ === null || occ.color !== piece.color) out.push({ from, to });
  }
}

function slideMoves(
  board: Board, from: number, piece: Piece,
  dirs: Array<[number, number]>, out: Move[]
): void {
  for (const [df, dr] of dirs) {
    let f = fileOf(from) + df;
    let r = rankOf(from) + dr;
    while (f >= 0 && f < 8 && r >= 0 && r < 8) {
      const to = squareOf(f, r);
      const occ = board.pieceAt(to);
      if (occ === null) out.push({ from, to });
      else {
        if (occ.color !== piece.color) out.push({ from, to });
        break;
      }
      f += df; r += dr;
    }
  }
}

function pawnMoves(board: Board, from: number, piece: Piece, out: Move[]): void {
  const dir = piece.color === "w" ? 1 : -1;
  const startRank = piece.color === "w" ? 1 : 6;
  const promoRank = piece.color === "w" ? 7 : 0;

  const oneRank = rankOf(from) + dir;
  if (oneRank >= 0 && oneRank <= 7) {
    const one = squareOf(fileOf(from), oneRank);
    if (board.pieceAt(one) === null) {
      addPawnMove(from, one, oneRank === promoRank, false, out);
      if (rankOf(from) === startRank) {
        const two = squareOf(fileOf(from), rankOf(from) + 2 * dir);
        if (board.pieceAt(two) === null) {
          out.push({ from, to: two, isDoublePush: true });
        }
      }
    }
  }
  for (const df of [-1, 1]) {
    const cf = fileOf(from) + df;
    const cr = rankOf(from) + dir;
    if (cf < 0 || cf > 7 || cr < 0 || cr > 7) continue;
    const to = squareOf(cf, cr);
    const occ = board.pieceAt(to);
    if (occ !== null && occ.color !== piece.color) {
      addPawnMove(from, to, cr === promoRank, false, out);
    } else if (to === board.enPassant) {
      out.push({ from, to, isEnPassant: true });
    }
  }
}

function addPawnMove(
  from: number, to: number, promotion: boolean, ep: boolean, out: Move[]
): void {
  if (promotion) {
    for (const t of ["q", "r", "b", "n"] as PieceType[]) {
      out.push({ from, to, promotion: t });
    }
  } else {
    out.push({ from, to, isEnPassant: ep });
  }
}

function castleMoves(board: Board, from: number, king: Piece, out: Move[]): void {
  const rank = king.color === "w" ? 0 : 7;
  if (from !== squareOf(4, rank)) return;
  const enemy = opposite(king.color);
  if (isSquareAttacked(board, from, enemy)) return;

  const c = board.castling;
  const kingSide = king.color === "w" ? c.wk : c.bk;
  const queenSide = king.color === "w" ? c.wq : c.bq;

  if (
    kingSide &&
    board.pieceAt(squareOf(5, rank)) === null &&
    board.pieceAt(squareOf(6, rank)) === null &&
    !isSquareAttacked(board, squareOf(5, rank), enemy) &&
    !isSquareAttacked(board, squareOf(6, rank), enemy)
  ) {
    out.push({ from, to: squareOf(6, rank), isCastle: true });
  }

  if (
    queenSide &&
    board.pieceAt(squareOf(3, rank)) === null &&
    board.pieceAt(squareOf(2, rank)) === null &&
    board.pieceAt(squareOf(1, rank)) === null &&
    !isSquareAttacked(board, squareOf(3, rank), enemy) &&
    !isSquareAttacked(board, squareOf(2, rank), enemy)
  ) {
    out.push({ from, to: squareOf(2, rank), isCastle: true });
  }
}

export function isSquareAttacked(
  board: Board, target: number, byColor: Color
): boolean {
  const pawnDir = byColor === "w" ? 1 : -1;
  for (const df of [-1, 1]) {
    const f = fileOf(target) + df;
    const r = rankOf(target) - pawnDir;
    if (f >= 0 && f < 8 && r >= 0 && r < 8) {
      const p = board.pieceAt(squareOf(f, r));
      if (p && p.color === byColor && p.type === "p") return true;
    }
  }
  for (const off of KNIGHT_OFFSETS) {
    const to = target + off;
    if (to < 0 || to > 63) continue;
    if (Math.abs(fileOf(to) - fileOf(target)) > 2) continue;
    const p = board.pieceAt(to);
    if (p && p.color === byColor && p.type === "n") return true;
  }
  for (const off of KING_OFFSETS) {
    const to = target + off;
    if (to < 0 || to > 63) continue;
    if (Math.abs(fileOf(to) - fileOf(target)) > 1) continue;
    const p = board.pieceAt(to);
    if (p && p.color === byColor && p.type === "k") return true;
  }
  if (slideAttack(board, target, ROOK_DIRS, byColor, "r")) return true;
  if (slideAttack(board, target, BISHOP_DIRS, byColor, "b")) return true;
  return false;
}

function slideAttack(
  board: Board, target: number, dirs: Array<[number, number]>,
  byColor: Color, straight: PieceType
): boolean {
  for (const [df, dr] of dirs) {
    let f = fileOf(target) + df;
    let r = rankOf(target) + dr;
    while (f >= 0 && f < 8 && r >= 0 && r < 8) {
      const p = board.pieceAt(squareOf(f, r));
      if (p) {
        if (p.color === byColor && (p.type === straight || p.type === "q")) return true;
        break;
      }
      f += df; r += dr;
    }
  }
  return false;
}

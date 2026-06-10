import { Board, moveFromUci } from "../src/board";
import { legalMoves } from "../src/movegen";
import { GameEngine, applyMove, OnlineGame, MoveOutcome } from "../src/engine";

let fails = 0;
function ok(name: string, cond: boolean): void {
  console.log((cond ? "PASS " : "FAIL ") + name);
  if (!cond) fails++;
}
function eq(name: string, got: unknown, want: unknown): void {
  const cond = got === want;
  console.log((cond ? "PASS " : "FAIL ") + `${name}: got ${got}` + (cond ? "" : ` want ${want}`));
  if (!cond) fails++;
}

function perft(board: Board, depth: number): number {
  if (depth === 0) return 1;
  const moves = legalMoves(board);
  if (depth === 1) return moves.length;
  let nodes = 0;
  for (const m of moves) nodes += perft(board.apply(m), depth - 1);
  return nodes;
}

console.log("=== TS PERFT: starting position ===");
const start = Board.initial();
eq("perft(1)", perft(start, 1), 20);
eq("perft(2)", perft(start, 2), 400);
eq("perft(3)", perft(start, 3), 8902);
eq("perft(4)", perft(start, 4), 197281);

console.log("\n=== TS PERFT: Kiwipete ===");
const kiwi = Board.fromFen(
  "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
);
eq("kiwipete(1)", perft(kiwi, 1), 48);
eq("kiwipete(2)", perft(kiwi, 2), 2039);
eq("kiwipete(3)", perft(kiwi, 3), 97862);

console.log("\n=== TS PERFT: Position 3 (en passant) ===");
const pos3 = Board.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
eq("pos3(1)", perft(pos3, 1), 14);
eq("pos3(2)", perft(pos3, 2), 191);
eq("pos3(3)", perft(pos3, 3), 2812);
eq("pos3(4)", perft(pos3, 4), 43238);
eq("pos3(5)", perft(pos3, 5), 674624);

console.log("\n=== TS PERFT: Position 4 (castling/promo) ===");
const pos4 = Board.fromFen(
  "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
);
eq("pos4(1)", perft(pos4, 1), 6);
eq("pos4(2)", perft(pos4, 2), 264);
eq("pos4(3)", perft(pos4, 3), 9467);

console.log("\n=== TS FEN round-trip ===");
const fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
eq("fen roundtrip", Board.fromFen(fen).toFen(), fen);

console.log("\n=== TS PGN-line checkmate (Scholar's mate status) ===");
const ge = new GameEngine();
for (const u of ["e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7"]) {
  ge.makeMove(moveFromUci(u));
}
eq("scholar status", ge.status(), "CHECKMATE");

console.log("\n=== TS Online validator (mirror of Kotlin) ===");
const w = "uidW", b = "uidB";
let g: OnlineGame = {
  id: "g", whiteUid: w, blackUid: b, moves: [], fen: Board.START_FEN,
  status: "ONGOING", winnerUid: null, updatedAt: 0,
};
const step = (game: OnlineGame, uid: string, uci: string): OnlineGame => {
  const r = applyMove(game, uid, uci);
  if (r.kind !== "Applied") throw new Error(`expected Applied, got ${r.kind} for ${uci}`);
  return r.game;
};
g = step(g, w, "e2e4");
ok("white twice rejected", applyMove(g, w, "d2d4").kind === "NotYourTurn");
ok("stranger rejected", applyMove(g, "x", "e7e5").kind === "NotYourTurn");
ok("illegal rejected", applyMove(g, b, "e2e4").kind === "IllegalMove");
g = step(g, b, "e7e5");
g = step(g, w, "f1c4"); g = step(g, b, "b8c6");
g = step(g, w, "d1h5"); g = step(g, b, "g8f6");
const fin = applyMove(g, w, "h5f7");
ok("checkmate detected", fin.kind === "Applied" && fin.game.status === "CHECKMATE");
ok("winner is white", fin.kind === "Applied" && fin.game.winnerUid === w);
ok("post-mate blocked", fin.kind === "Applied" && applyMove(fin.game, b, "e8e7").kind === "GameOver");

console.log(fails === 0 ? "\nALL TS TESTS PASSED" : `\n${fails} TS FAILURES`);
if (fails > 0) process.exit(1);

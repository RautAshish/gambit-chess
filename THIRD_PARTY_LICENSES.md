# Third-party licenses

## Stockfish
This app bundles the Stockfish chess engine (https://stockfishchess.org),
release sf_18, as an optional strong engine, communicating over UCI.

Stockfish is Copyright (C) the Stockfish developers and is licensed under the
GNU General Public License v3 (https://www.gnu.org/licenses/gpl-3.0.html).
Stockfish source code: https://github.com/official-stockfish/Stockfish

In accordance with the GPL, the complete source of this application —
including the integration code — is available at this repository.
The Stockfish binary is fetched unmodified from the official release at
build time (see .github/workflows/build.yml).

## Chess piece artwork (Cburnett set)
Piece designs are the classic set by Colin M.L. Burnett — the pieces familiar
from Wikipedia and Lichess — used under the GPLv2-or-later license, obtained
from https://github.com/lichess-org/lila (public/piece/cburnett).
Copyright (C) Colin M.L. Burnett.
Geometry is sampled from the original 45x45 SVGs into the polylines embedded in
app/src/main/java/com/chessapp/ui/board/PieceRenderer.kt; colors are adapted to
this app's palette. To keep licensing coherent (this artwork + the bundled
Stockfish engine), the application as a whole is distributed under GPLv3 — see
LICENSE.

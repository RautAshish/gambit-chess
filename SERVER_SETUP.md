# Online play — 5-minute free setup

Emersion's online mode runs on YOUR free Firebase project (Spark tier, no card).

1. Go to console.firebase.google.com → **Add project** (any name, Analytics off).
2. **Security → Authentication → Get started → Sign-in method → Anonymous →
   Enable.** (Older console layout: Build → Authentication. Doing this first
   also provisions the Web API key.)
3. **Databases and storage → Firestore Database → Create database**
   (Standard edition if asked, production mode, any region).
   (Older layout: Build → Firestore Database.) Skip "Add app" and all Gemini
   cards — this integration registers no app and needs no google-services.json.
4. Firestore → **Rules** tab → replace contents with `firebase/firestore.rules`
   from this repo → Publish.
5. **Project settings (gear) → General**: copy **Project ID** and **Web API
   Key** (an `AIzaSy…` string) from the "Your project" card — on phones the
   card truncates, so scroll within it or toggle Desktop site. If the key is
   blank, Authentication hasn't finished provisioning: confirm Anonymous shows
   Enabled, wait ~30s, refresh. Guaranteed fallback: console.cloud.google.com
   → same project → APIs & Services → Credentials → "Browser key (auto created
   by Firebase)" — that IS the Web API key.
6. In the app: **Settings → Online play** → paste both → Save.

Both players do steps 5–6 with the SAME project values (share them with your
friend). Then: Play Online → Create game → share the 6-letter code.

Trust model: games are private friendly matches. Both clients validate every
move with the same engine; Firestore rules restrict writes to the two players.
For a public/ranked mode, deploy the server-authoritative Cloud Functions in
`functions/` instead (requires the Blaze plan).

## Shipping to app-store users (zero setup for them)
Do the steps above once yourself, then put the two values into repo secrets
EMERSION_ONLINE_PROJECT_ID and EMERSION_ONLINE_API_KEY. CI bakes them into every
build as the default server: players just tap Play Online. (These values are
public identifiers by design — security lives in firestore.rules — the secrets
are for convenience, not confidentiality.) The in-app Settings fields remain
as an advanced override for self-hosters. Free-tier headroom: polling costs
roughly 1.5K reads per player-hour against the 50K/day Spark quota — fine for
a small community; upgrade to Blaze (pennies) if it ever grows past that.

## Rules v2 (REQUIRED re-publish)
The original rules let a seated player forge status/winnerUid and write
multi-move updates (found in external review). Rules v2 rewrites updates as
explicit transitions (join / one-move / resign) diff-locked to the client's
exact field masks, adds turn enforcement by ply parity, outcome-consistency
(CHECKMATE winner = mover; draws/stalemate = no winner; RESIGNED = other
seat), size caps, and hides filled games from non-participants. Re-publish:
Firestore -> Rules -> replace all -> Publish. Then smoke: create a game on
one device, join by code on another, play a few moves, resign — all should
behave exactly as before; only forged writes are newly rejected.

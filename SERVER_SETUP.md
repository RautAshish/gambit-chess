# Online play — 5-minute free setup

Emersion's online mode runs on YOUR free Firebase project (Spark tier, no card).

1. Go to console.firebase.google.com → **Add project** (any name, Analytics off).
2. **Build → Authentication → Get started → Anonymous → Enable.**
3. **Build → Firestore Database → Create database** (production mode, any region).
4. Firestore → **Rules** tab → replace contents with `firebase/firestore.rules`
   from this repo → Publish.
5. **Project settings (gear) → General**: copy **Project ID** and **Web API Key**.
6. In the app: **Settings → Online play** → paste both → Save.

Both players do steps 5–6 with the SAME project values (share them with your
friend). Then: Play Online → Create game → share the 6-letter code.

Trust model: games are private friendly matches. Both clients validate every
move with the same engine; Firestore rules restrict writes to the two players.
For a public/ranked mode, deploy the server-authoritative Cloud Functions in
`functions/` instead (requires the Blaze plan).

## Shipping to app-store users (zero setup for them)
Do the steps above once yourself, then put the two values into repo secrets
GAMBIT_ONLINE_PROJECT_ID and GAMBIT_ONLINE_API_KEY. CI bakes them into every
build as the default server: players just tap Play Online. (These values are
public identifiers by design — security lives in firestore.rules — the secrets
are for convenience, not confidentiality.) The in-app Settings fields remain
as an advanced override for self-hosters. Free-tier headroom: polling costs
roughly 1.5K reads per player-hour against the 50K/day Spark quota — fine for
a small community; upgrade to Blaze (pennies) if it ever grows past that.

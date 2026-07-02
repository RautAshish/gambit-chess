# Emersion Chess — brand spec (locked)

## The mark
The knight silhouette is a LOCKED ASSET (Cburnett-derived; GPLv2+ attribution
in THIRD_PARTY_LICENSES.md). Never reshape, rotate, re-proportion, or add
details. All renders — flat (in-app) or hero (store) — use this geometry.

## Hero rendering (store/launcher art)
- Gold gradient: #FFD86A (top) -> #E4B02A (mid) -> #B98508 (bottom)
- Outline: thin clean bone (#EFE6D2 family); soft warm glow; subtle drop shadow
- Background: matte #111111 with 5–8% opacity dark chessboard texture
- Tone: luxury chess set / tournament branding; never cartoonish, never bright

## Platform corrections (learned the hard way — do NOT regress)
- NO baked borders/frames/rounded corners on launcher or Play icons: Android
  launchers and Play apply their OWN masks; baked frames clip into ring
  artifacts. Frames are for marketing shots only.
- Adaptive icon: hero art ships as the BACKGROUND layer, foreground
  transparent, knight inside the 66/108 safe zone (feathered margin).
- The monochrome layer (flat white silhouette) is mandatory for Android 13+
  themed icons and is separate from hero art.

## Flat rendering (in-app board + header)
PieceRenderer palette: bone #F7F3E8 / charcoal #2B2B28 bodies with contrast
outlines — readability on any square outranks gloss inside the game.

## Regeneration prompt
The archived generator prompt (owner's, via GPT) lives with this spec: keep
its palette/material/lighting language; drop its "gold border" instruction
per the platform corrections above.

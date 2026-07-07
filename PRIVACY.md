# Emersion Chess — Privacy Policy

Emersion Chess is built to work offline and to collect as little as possible.

## On your device
Games, puzzle progress, and settings live only on your device. There are no
ads, no analytics, no trackers, and no third-party SDKs. Deleting the app
deletes all local data.

## Online play (optional)
If you use Play Online, the app connects to a game server operated by the
developer (a Google Firebase project). To make a match work, the following is
stored there: a randomly generated anonymous player identifier (created by
Firebase Anonymous Authentication — no name, email, or account), the game's
room code, the list of chess moves, game status, and timestamps. This data
exists solely to run the game between its two participants and is readable
only by them. It is never sold, shared, or used for any other purpose.
Finished games are retained so they remain viewable by their players; you can
request deletion of your games via the source repository's issue tracker.

Advanced users may instead point the app at their own Firebase project in
Settings, in which case online data is stored under their control and never
reaches the developer's server.

## Permissions
INTERNET is used only for optional online play. VIBRATE powers move haptics.

## Contact
Questions or deletion requests: the issue tracker at the source repository.

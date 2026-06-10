# Keep Room entities and DAOs (reflection-based)
-keep class com.chessapp.data.db.** { *; }
# Firebase / Firestore model mapping uses reflection on field names
-keep class com.chessapp.data.online.OnlineGame { *; }
-keepattributes Signature
-keepattributes *Annotation*
# Kotlin coroutines debug metadata
-dontwarn kotlinx.coroutines.**

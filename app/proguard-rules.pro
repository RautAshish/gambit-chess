# Keep Room entities and DAOs (reflection-based)
-keep class com.chessapp.data.db.** { *; }
# Firebase / Firestore model mapping uses reflection on field names
-keep class com.chessapp.data.online.OnlineGame { *; }
-keepattributes Signature
-keepattributes *Annotation*
# Kotlin coroutines debug metadata
-dontwarn kotlinx.coroutines.**

# ViewModels are constructed reflectively by the default viewModel() factory;
# without these keeps the RELEASE build crashes opening Puzzles/Online.
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ProGuard / R8 rules for t1dTracker

# Keep Room entities, DAOs and generated DB classes
-keep class com.hardrivetech.t1dtracker.data.** { *; }
-keep class androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Preserve Kotlin metadata
-keepclassmembers class kotlin.Metadata { *; }

# SQLCipher native bindings
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep JSON / org.json usages
-dontwarn org.json.**

# Keep reflection entry points if any (adjust as needed)
-keepclassmembers class * {
    java.lang.Class class$(java.lang.String);
}

# Somnus Proguard / R8 Rules

# Room keep rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Data local classes
-keep class com.example.somnus.data.local.** { *; }

# Keep Health Connect rationale and client activities
-keep class com.example.somnus.MainActivity { *; }

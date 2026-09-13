# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlinx coroutines
-dontwarn kotlinx.coroutines.**

# Keep data/entity classes used by Room + JSON (field names matter)
-keep class com.reelbot.mobile.data.model.** { *; }
-keep class com.reelbot.mobile.data.db.entity.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }

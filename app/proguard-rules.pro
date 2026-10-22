# kotlinx.serialization a Retrofit si pravidla přinášejí v knihovnách (consumer rules).

# Glance používá WorkManager, jeho Room databázi (WorkDatabase_Impl) vytváří reflexí – R8 ji jinak odstraní
# a aplikace spadne už při startu (InitializationProvider).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# WorkManager vytváří workery (i Glance SessionWorker) a InputMerger reflexí podle jména třídy.
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class * extends androidx.work.InputMerger { public <init>(); }

# WorkManager recreates workers by class name after process death, including
# jobs persisted by older APKs. Preserve these names across every release.
-keep class fr.swucollection.app.CatalogSyncWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class fr.swucollection.app.UpdateCheckWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
# SDK consumer rules and Android's optimized defaults handle other components.

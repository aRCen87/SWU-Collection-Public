package fr.swucollection.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Free, visible connection; only started from a visible activity or an explicit user action. */
internal object CommunityLiveConnection {
    @Volatile var activityVisible=false
    private fun prefs(context:Context)=context.getSharedPreferences("community_live_connection",Context.MODE_PRIVATE)
    fun enabled(context:Context)=prefs(context).getBoolean("enabled",true)
    fun setEnabled(context:Context,enabled:Boolean){prefs(context).edit().putBoolean("enabled",enabled).apply();if(enabled)refresh(context) else context.stopService(intent(context))}
    private fun intent(context:Context)=Intent().setClassName(context.packageName,"fr.swucollection.app.RealtimeMessagingService")
    fun refresh(context:Context){
        if(context.packageName!=AppChannel.TEST_PACKAGE || !activityVisible || !enabled(context))return
        if(CommunityService.create(context)?.auth?.currentUser?.isEmailVerified!=true)return
        if(!NotificationManagerCompat.from(context).areNotificationsEnabled() || (Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED))return
        runCatching{ContextCompat.startForegroundService(context,intent(context))}
    }
}

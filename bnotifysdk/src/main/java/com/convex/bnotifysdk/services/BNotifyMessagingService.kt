package com.convex.bnotifysdk.services

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.convex.bnotifysdk.BNotifyApp
import com.convex.bnotifysdk.model.NotificationModel
import kotlin.jvm.java
import kotlin.let
import kotlin.onFailure
import kotlin.runCatching

abstract class BNotifyMessagingService : Service()  {

    /** Consumer must return the Activity to open when a notification is clicked. */
    protected abstract fun activityToOpenOnClick(): Class<out Activity>

    abstract fun onMessageReceived(remoteMessage: NotificationModel?)

    override fun onCreate() {
        super.onCreate()
        // 1) Prefer the overridden activity
        runCatching {
            BNotifyApp.setActivityToOpenOnClick(activityToOpenOnClick())
        }.onFailure {
            Log.w("BerryBaseService", "activityToOpenOnClick() failed: ${it.message}")
        }

        // 2) Fallback: try manifest <meta-data> if override wasn’t provided or failed
        if (runCatching { BNotifyApp.getActivityToOpenOnClick(this) }.isFailure) {
            resolveActivityFromManifest()?.let { BNotifyApp.setActivityToOpenOnClick(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BerryBaseService", "Service started")
        // Example: initialize notification handling
        intent?.let {
            BNotifyApp.setNotificationListener(object : BNotifyApp.OnNotificationListener{
                override fun onMessageReceive(remoteMessage: NotificationModel?) {
                    onMessageReceived(remoteMessage)
                }
            })
            BNotifyApp.notificationInitializer(this, it)
        }

        // Example: fake message reception simulation
//        simulateIncomingMessage()

        // Keep service alive unless explicitly stopped
        return START_STICKY
    }

    private fun simulateIncomingMessage() {
        // Here you would connect to your socket / backend / push system
        // and trigger onMessageReceived when real data arrives
        val fakeMessage = "Hello from Berry Service!"
//        onMessageReceived(fakeMessage)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not binding, just a background service
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BerryBaseService", "Service destroyed")
    }

    /** Optional fallback via manifest meta-data: <meta-data android:name="berry.activity_on_click" android:value="com.pkg.MainActivity"/> */
    private fun resolveActivityFromManifest(): Class<out Activity>? {
        return try {
            val si = packageManager.getServiceInfo(
                ComponentName(this, this::class.java),
                PackageManager.GET_META_DATA
            )
            val fqcn = si.metaData?.getString("berry.activity_on_click") ?: return null
            @Suppress("UNCHECKED_CAST")
            Class.forName(fqcn) as Class<out Activity>
        } catch (e: Exception) {
            Log.w("BerryBaseService", "No manifest activity meta-data: ${e.message}")
            null
        }
    }
}
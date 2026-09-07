package com.convex.bnotifysdk.inappmessaging.tracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.convex.bnotifysdk.inappmessaging.InAppMessenger
import com.convex.bnotifysdk.socket.SocketManager
import org.json.JSONObject

@SuppressLint("StaticFieldLeak")
object ActivityTracker : Application.ActivityLifecycleCallbacks {
    private var startedCount = 0
    private var currentActivity: Activity? = null

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    fun getCurrentActivity(): Activity? = currentActivity

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.i("FIAM.Display", "onActivityCreated: ${activity::class.java.simpleName}")

        SocketManager.setSocketConnectListener(object : SocketManager.OnSocketConnectListener{
            override fun onConnected() {
                Log.d("FIAM", "SocketManager: socket connected, sending in-app impression")
                val json = JSONObject().apply {
                    put("trigger", "on_foreground")
                }
                SocketManager.handleInAppMessagesImpression("inapp:fetch", json)
            }
        })
    }

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        currentActivity = activity
        Log.i("FIAM.Display", "onActivityStarted: ${activity::class.java.simpleName}")
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        if (startedCount == 1) {
            Log.i("FIAM.Headless", "went foreground: ${activity::class.java.simpleName}")
        }
        Log.i("FIAM.Display", "onActivityResumed: ${activity::class.java.simpleName}")
        Log.i("FIAM.Display", "Binding to activity: ${activity::class.java.simpleName}")
        Log.i("FIAM.Headless", "Setting display event component")

        InAppMessenger.flushPendingQueueIfAny()
    }

    override fun onActivityPaused(activity: Activity) {
        Log.i("FIAM.Display", "onActivityPaused: ${activity::class.java.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
        if (startedCount <= 0) {
            Log.i("FIAM.Headless", "went background: ${activity::class.java.simpleName}")
            currentActivity = null
        }
        Log.i("FIAM.Display", "onActivityStopped: ${activity::class.java.simpleName}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.i("FIAM.Display", "onActivitySaveInstanceState: ${activity::class.java.simpleName}")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.i("FIAM.Display", "onActivityDestroyed: ${activity::class.java.simpleName}")
    }
}

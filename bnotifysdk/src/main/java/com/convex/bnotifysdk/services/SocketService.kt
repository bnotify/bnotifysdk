package com.convex.bnotifysdk.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import com.convex.bnotifysdk.socket.SocketManager
import kotlin.let

internal class SocketService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val logRunnable = object : Runnable {
        override fun run() {
            Log.i("Bnotify_SocketService", "✅ Still running (pid=${Process.myPid()})")
            handler.postDelayed(this, 60_000) // log every 60s
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SocketManager.initialize(applicationContext)
        SocketManager.connect()
        Log.i("Bnotify_SocketService", "Service CREATED")
        handler.post(logRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {

            Log.d("Notification_SocketIO", "Notification Click: event: SocketService Class")
            SocketManager.handleNotificationClickedIntent(it)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        SocketManager.disconnect()
        Log.w("Bnotify_SocketService", "Service DESTROYED")
        handler.removeCallbacks(logRunnable)
        super.onDestroy()
    }
}
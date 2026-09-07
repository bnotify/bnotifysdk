package com.convex.bnotifysdk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.convex.bnotifysdk.socket.SocketManager
import com.convex.bnotifysdk.utils.NotifyConstants

internal class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.extras?.get("notification_id")
        val action = intent.getStringExtra("action")
        val click = intent.getBooleanExtra("click", false)

        // Handle the dismiss action here
        // You can log it, send to analytics, etc.
        Log.d("Notification_SocketIO", "Notification ID: $notificationId ACTION: $action CLICKED: $click")
//        SocketManager.handleNotificationIntent(intent)
        if (action == NotifyConstants.ClickedEvent) {
            // Do something when notification is dismissed

            Log.d("Notification_SocketIO", "Notification Click: event: NotificationDismissReceiver Class")
            SocketManager.handleNotificationClickedIntent(intent)
            Log.d("Notification_SocketIO", "Notification CLICKED")
        }else if (action == NotifyConstants.DismissedEvent) {
            // Do something when notification is dismissed
            SocketManager.handleNotificationDismissedIntent(intent)
            Log.d("Notification_SocketIO", "Notification DISMISSED")
        }
    }
}
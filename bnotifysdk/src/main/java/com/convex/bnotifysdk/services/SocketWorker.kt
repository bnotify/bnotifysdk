package com.convex.bnotifysdk.services

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.convex.bnotifysdk.socket.SocketManager

class SocketWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val action = inputData.getString("action")
        val type = inputData.getString("type")
        val notificationId = inputData.getString("notification_id")

        Log.i("SocketWorker", "Running work: action=$action, type=$type, id=$notificationId")

        return try {
            // Do your reconnect or handling
            SocketManager.initialize(applicationContext)
            SocketManager.connect()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry() // retry if failed
        }
    }
}
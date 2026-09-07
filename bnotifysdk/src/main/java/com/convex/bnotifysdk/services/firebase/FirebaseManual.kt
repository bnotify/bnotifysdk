package com.convex.bnotifysdk.services.firebase

import android.content.Context
import android.util.Log
import com.convex.bnotifysdk.model.BnotifyConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlin.collections.any

internal object FirebaseManual {

//    data class FirebaseConfig(
//        val apiKey: String = "AIzaSyAV1JDWg79Cnb2-XIXGF_LVQRKjMe0GEjs",
//        val appId: String = "1:1098181252460:android:a2646cbe3da0482beb0f49", // This is Firebase ApplicationId (format 1:NNNN:android:HASH)
//        val projectId: String = "convex-testing",
//        val senderId: String = "1098181252460",
//        // Optional/use on server only:
//        val serverKey: String = """<Replace this text with the FIREBASE SERVER KEY or upload a file>"""
//    )

    fun initialize(context: Context, cfg: BnotifyConfig) {
        if (FirebaseApp.getApps(context).any()) return

        val options = FirebaseOptions.Builder()
            .setApiKey(cfg.fcmApiKey)
            .setApplicationId(cfg.fcmAppId)
            .setProjectId(cfg.fcmProjectId)
            .setGcmSenderId(cfg.fcmSenderId)
            .build()

        FirebaseApp.initializeApp(context, options)
        Log.i("CUSTOM_FIREBASE","Firebase initialized manually (no google-services.json)")
    }
}
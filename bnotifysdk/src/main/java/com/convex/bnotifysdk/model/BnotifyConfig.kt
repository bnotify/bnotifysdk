package com.convex.bnotifysdk.model

data class BnotifyConfig(
    val projectId: String,
    val packageName: String,
    val apiKey: String,
    val authDomain: String? = null,
    val databaseURL: String? = null,
    val storageBucket: String? = null,
    val messagingSenderId: String? = null,
    val appId: String,
    val measurementId: String? = null,
    val fcmAppId: String,
    val fcmProjectId: String,
    val fcmApiKey: String,
    val fcmSenderId: String,
)

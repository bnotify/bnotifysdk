package com.convex.bnotifyproject.Bnotify.inappmessaging.utils

import android.app.Application
import android.content.Context

object ContextUtils {
    fun getApplication(context: Context): Application {
        val appContext = context.applicationContext
        return when (appContext) {
            is Application -> appContext
            else -> throw IllegalStateException(
                "Provided context is not tied to an Application. " +
                        "Pass Application or a Context from your own app."
            )
        }
    }
}

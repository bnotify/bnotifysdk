package com.convex.bnotifyproject.Bnotify.inappmessaging.fetcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.convex.bnotifyproject.Bnotify.inappmessaging.model.CampaignModel
import com.convex.bnotifyproject.Bnotify.inappmessaging.storage.CampaignStorage
import com.google.gson.Gson
import kotlin.concurrent.thread

object CampaignFetcher {
    fun fetch(context: Context, callback: (CampaignModel?) -> Unit) {
        thread {
            try {
                Log.i("FIAM.Headless", "Fetching campaigns from service.")
                val response = CampaignStorage.getCachedCampaigns(context)!!
                Log.d("FIAM.Headless", "Fetch response: ${response}")
                Log.d("FIAM.Headless", "Fetch response length: ${response.length}")
                val campaigns = Gson().fromJson(response, CampaignModel::class.java)
                Handler(Looper.getMainLooper()).post { callback(campaigns) }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("FIAM.Headless", "Fetch failed: ${e.message}")
                Handler(Looper.getMainLooper()).post { callback(null) }
            }
        }
    }

//    fun fetch(url: String, callback: (List<CampaignModel>, String) -> Unit) {
//        thread {
//            try {
//                val response = URL(url).readText()
//                Log.d("InAppMessenger", "Response: $response")
//                val campaigns = parseJson(response)
//                Handler(Looper.getMainLooper()).post { callback(campaigns, response) }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                Log.e("FIAM.Headless", "Fetch failed: ${e.message}")
//                Handler(Looper.getMainLooper()).post { callback(emptyList(), "") }
//            }
//        }
//    }
}
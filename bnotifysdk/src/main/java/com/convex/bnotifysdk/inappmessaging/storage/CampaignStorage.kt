package com.convex.bnotifysdk.inappmessaging.storage

import android.content.Context
import android.util.Log
import com.convex.bnotifysdk.inappmessaging.model.CampaignModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object CampaignStorage {
    private const val PREFS = "inapp_campaigns"
    private const val KEY_CACHE = "campaigns_json"

    fun canShow(context: Context, campaign: CampaignModel): Boolean {
        val now = System.currentTimeMillis()

        val start = parseIsoDate(campaign.limits.startAt)
        val end = parseIsoDate(campaign.limits.endAt)

        if (now < start || now > end) {
            Log.d("FIAM.Headless", "Campaign ${campaign.id} outside of valid range → start=$start, end=$end")
            return false
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(campaign.id, 0)
        return count < campaign.limits.maxPerDay
    }

    fun markShown(context: Context, campaign: CampaignModel) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(campaign.id, 0) + 1
        Log.d("FIAM.Headless", "DIALOG SHOWING COUNT: $count")
        prefs.edit().putInt(campaign.id, count).apply()
    }

    /** Save campaigns raw JSON to disk */
    fun saveCachedCampaigns(context: Context, rawJson: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CACHE, rawJson).apply()
    }

    /** Load cached campaigns JSON */
    fun getCachedCampaigns(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CACHE, null)
    }

    /** Parse ISO8601 dates safely (supports with/without milliseconds) */
    private fun parseIsoDate(date: String?): Long {
        if (date.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(date)?.time ?: 0L
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(date)?.time ?: 0L
            } catch (e2: Exception) {
                Log.e("FIAM.Storage", "Failed to parse date: $date", e2)
                0L
            }
        }
    }
}
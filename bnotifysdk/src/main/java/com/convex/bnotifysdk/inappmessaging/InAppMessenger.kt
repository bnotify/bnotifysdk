package com.convex.bnotifysdk.inappmessaging

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.convex.bnotifysdk.inappmessaging.fetcher.CampaignFetcher
import com.convex.bnotifysdk.inappmessaging.fragments.BaseMessageFragment
import com.convex.bnotifysdk.inappmessaging.matcher.CampaignMatcher
import com.convex.bnotifysdk.inappmessaging.model.CTA
import com.convex.bnotifysdk.inappmessaging.model.CTASecondary
import com.convex.bnotifysdk.inappmessaging.model.CampaignModel
import com.convex.bnotifysdk.inappmessaging.storage.CampaignStorage
import com.convex.bnotifysdk.inappmessaging.tracker.ActivityTracker
import com.convex.bnotifysdk.inappmessaging.ui.MessageRenderer
import com.convex.bnotifysdk.inappmessaging.utils.ContextUtils
import com.convex.bnotifysdk.socket.SocketManager
import com.google.gson.Gson
import org.json.JSONObject
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

object InAppMessenger {

    private var campaigns: CampaignModel? = null
    internal lateinit var application: Application

    private var installationId: String = ""
    private val pendingQueue: Queue<CampaignModel> = LinkedList()
    private var isFreshInstall: Boolean = false
    private var isTestDevice: Boolean = false

    /**
     * Opt-in. When false, in-app UI behaves exactly as before.
     * The Flutter plugin sets this so a campaign can be queued off a disallowed route
     * without incrementing [CampaignStorage.markShown].
     */
    @Volatile
    private var presentationGateConfigured: Boolean = false

    @Volatile
    private var presentationEnabled: Boolean = true

    fun init(context: Context, response: String) {
        CampaignStorage.saveCachedCampaigns(context, response)
        val app = ContextUtils.getApplication(context)
        application = app
        ActivityTracker.register(app)

        val prefs = app.getSharedPreferences("inapp_prefs", Context.MODE_PRIVATE)
        installationId = prefs.getString("installation_id", null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("installation_id", it).apply()
            }
        Log.i("FIAM.Headless", "Starting InAppMessaging runtime with Installation ID $installationId")

        isFreshInstall = !prefs.getBoolean("installed_before", false)
        if (isFreshInstall) prefs.edit().putBoolean("installed_before", true).apply()

        isTestDevice = try {
            val debug = javaClass.getClassLoader()
                ?.loadClass("${application.packageName}.BuildConfig")
                ?.getDeclaredField("DEBUG")?.getBoolean(null) ?: false
            debug
        } catch (e: Exception) {
            false
        }

        Log.i(
            "FIAM.Headless",
            "Forcing fetch from service rather than cache. Test Device: $isTestDevice | App Fresh Install: $isFreshInstall"
        )

        val cachedJson = CampaignStorage.getCachedCampaigns(app)
        if (!isFreshInstall && !isTestDevice && !cachedJson.isNullOrEmpty()) {
            Log.i("FIAM.Headless", "Using cached campaigns")
        }

        CampaignFetcher.fetch(context) { fetched ->
            campaigns = fetched
            logEvent(campaigns?.trigger?.event!!)
            Log.i("FIAM.Headless", "Successfully fetched messages from backend")
        }
    }

    fun logEvent(eventName: String) {
        val response = CampaignStorage.getCachedCampaigns(application)!!
        campaigns = Gson().fromJson(response, CampaignModel::class.java)
        Log.d("FIAM.Headless", "logEvent: $eventName")
        val campaign = CampaignMatcher.match(campaigns!!, eventName)
        campaign?.let {

            Log.d("FIAM.Show", "Can Show: ${CampaignStorage.canShow(application, it)}")
            if (CampaignStorage.canShow(application, it)) {
                val activity = ActivityTracker.getCurrentActivity()
//                Log.d("FIAM.Headless", "Current Active Activity Name: ${activity!!::class.java.simpleName}")
                if (activity == null || activity.isFinishing) {
                    Log.d("FIAM.Headless", "No activity now; enqueuing campaign: ${it.id}")
                    enqueueCampaign(it)
                } else {
                    showCampaign(it)
                }
            } else {
                Log.d("FIAM.Headless", "Campaign ${it.id} cannot be shown due to frequency limits")
            }
        } ?: run {
            Log.d("FIAM.Headless", "No campaign matched for event: $eventName")
        }
    }

    /**
     * When [configured] is false, presentation stays unrestricted.
     * When [configured] is true and [enabled] is false, campaigns are queued and any
     * visible in-app dialog is dismissed. [CampaignStorage.markShown] runs only inside
     * [MessageRenderer.show], so a queued campaign does not consume `maxPerDay`.
     */
    fun setPresentationGate(configured: Boolean, enabled: Boolean) {
        val wasAllowed = presentationAllowed()
        presentationGateConfigured = configured
        presentationEnabled = enabled
        if (presentationAllowed()) {
            if (!wasAllowed) {
                flushPendingQueueIfAny()
            }
        } else {
            dismissVisibleMessage()
        }
    }

    private fun presentationAllowed(): Boolean {
        return !presentationGateConfigured || presentationEnabled
    }

    internal fun showCampaign(campaign: CampaignModel) {
        if (!presentationAllowed()) {
            Log.d("FIAM.Headless", "Presentation gated; enqueuing campaign: ${campaign.id}")
            enqueueCampaign(campaign)
            return
        }
        MessageRenderer.show(campaign)
    }

    fun enqueueCampaign(campaign: CampaignModel) {
        synchronized(pendingQueue) {
            if (pendingQueue.none { it.id == campaign.id }) {
                pendingQueue.offer(campaign)
            }
        }
    }

    internal fun flushPendingQueueIfAny() {
        if (!presentationAllowed()) return
        synchronized(pendingQueue) {
            if (pendingQueue.isEmpty()) return
            val next = pendingQueue.poll()
            next?.let {
                Log.d("FIAM.Headless", "Flushing queued campaign: ${it.id}")
                showCampaign(it)
            }
        }
    }

    /**
     * Closes a message that was already presented. Does not enqueue it again:
     * that presentation already counted toward `maxPerDay`.
     */
    private fun dismissVisibleMessage() {
        val activity = ActivityTracker.getCurrentActivity() as? FragmentActivity ?: return
        val fragment = activity.supportFragmentManager.findFragmentByTag("InAppMessage") as? BaseMessageFragment
            ?: return
        fragment.setDismissListener { }
        fragment.dismissAllowingStateLoss()
        MessageRenderer.clear()
    }

    fun onMessageDismissed(campaign: CampaignModel) {
        Log.i("FIAM.Analytics", "Dismiss logged")


        val json = JSONObject().apply {
            put("campaignId", campaign.id)
            put("type", "dismissed")
        }
        SocketManager.handleInAppMessagesImpression("inapp:event", json)


        synchronized(pendingQueue) {
            val next = pendingQueue.poll()
            if (next != null) {
                Log.d("FIAM.Headless", "Showing next queued campaign: ${next.id}")
                showCampaign(next)
            } else {
                Log.d("FIAM.Headless", "No more queued campaigns")
            }
        }
    }

    /** 🔹 Analytics hooks */
    fun onMessageImpression(campaign: CampaignModel) {
        Log.i("FIAM.Analytics", "Impression logged for: ${campaign.id}")
        val json = JSONObject().apply {
            put("campaignId", campaign.id)
            put("type", "impression")
        }
        SocketManager.handleInAppMessagesImpression("inapp:event", json)
    }

    fun onMessageClick(campaign: CampaignModel, cta: CTA) {
        Log.i("FIAM.Analytics", "Click logged for: ${campaign.id}, action=${cta.action}, url=${cta.url}")

        val json = JSONObject().apply {
            put("campaignId", campaign.id)
            put("type", "click_primary")
        }
        SocketManager.handleInAppMessagesImpression("inapp:event", json)


        when (cta.action) {
            "open_url" -> {
                cta.url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    application.startActivity(intent)
                }
            }
            "deeplink" -> {
                cta.url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    application.startActivity(intent)
                }
            }
            "custom_event" -> {
                logEvent(cta.text) // trigger another campaign via event
            }
        }
    }

    fun onMessageSecondaryClick(campaign: CampaignModel, cta: CTASecondary) {
        Log.i("FIAM.Analytics", "Click logged for: ${campaign.id}, action=${cta.action}, url=${cta.url}")

        val json = JSONObject().apply {
            put("campaignId", campaign.id)
            put("type", "click_secondary")
        }
        SocketManager.handleInAppMessagesImpression("inapp:event", json)

        when (cta.action) {
            "open_url" -> {
                cta.url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    application.startActivity(intent)
                }
            }
            "deeplink" -> {
                cta.url?.let {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    application.startActivity(intent)
                }
            }
            "custom_event" -> {
                logEvent(cta.text) // trigger another campaign via event
            }
        }
    }
}

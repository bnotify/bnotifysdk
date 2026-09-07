package com.convex.bnotifyproject.Bnotify.inappmessaging.ui

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.convex.bnotifyproject.Bnotify.inappmessaging.InAppMessenger
import com.convex.bnotifyproject.Bnotify.inappmessaging.fragments.*
import com.convex.bnotifyproject.Bnotify.inappmessaging.model.CampaignModel
import com.convex.bnotifyproject.Bnotify.inappmessaging.storage.CampaignStorage
import com.convex.bnotifyproject.Bnotify.inappmessaging.tracker.ActivityTracker

object MessageRenderer {

    private var currentCampaignId: String? = null

    fun show(campaign: CampaignModel) {
        val activity = ActivityTracker.getCurrentActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.d("FIAM.Display", "No valid activity; campaign queued: ${campaign.id}")
            InAppMessenger.enqueueCampaign(campaign)
            return
        }

        // skip if activity not in stable state (e.g., permission/system dialog on top)
        if (!activity.hasWindowFocus()) {
            Log.d("FIAM.Display", "Activity not in focus; re-queue campaign: ${campaign.id}")
            InAppMessenger.enqueueCampaign(campaign)
            return
        }

        if (currentCampaignId == campaign.id) {
            Log.d("FIAM.Display", "Campaign ${campaign.id} already showing; skip")
            return
        }
        currentCampaignId = campaign.id

        val fm = (activity as? AppCompatActivity)?.supportFragmentManager ?: return
        var fragment: BaseMessageFragment? = null
        /*var fragment: BaseMessageFragment = when (campaign.content.type.lowercase()) {
            "card" -> {
                CardMessageFragment()
            }
            "modal" -> {
                ModalMessageFragment()
            }
            "image" -> {
                ImageMessageFragment()
            }
            "top_banner" -> {
                TopBannerMessageFragment()
            }
            "fullscreen" -> {
                FullscreenMessageFragment()
            }
            else -> {
                Log.e("FIAM.Display", "Unknown message type: ${campaign.content.type}, defaulting to modal")
                ModalMessageFragment()
            }
        }*/

        if(campaign.content.type.lowercase() == "card") {
            fragment =  CardMessageFragment()
        } else if(campaign.content.type.lowercase() == "modal") {
            fragment =  ModalMessageFragment()
        } else if(campaign.content.type.lowercase() == "image") {
            fragment =  ImageMessageFragment()
        } else if(campaign.content.type.lowercase() == "top_banner") {
            fragment =  TopBannerMessageFragment()
        }else if(campaign.content.type.lowercase() == "fullscreen") {
            fragment =  FullscreenMessageFragment()
        } else {
            Log.e("FIAM.Display", "Unknown message type: ${campaign.content.type}, defaulting to modal")
        }

        fragment!!.bindCampaign(campaign)

        fragment.setDismissListener {
            if (activity.hasWindowFocus()) {
                Log.d("FIAM.Display", "Dialog dismissed normally for: ${campaign.id}")
                clear()
            } else {
                if (!fragment.isCTAClick()){
                    InAppMessenger.onMessageDismissed(campaign)
                }
                Log.d("FIAM.Display", "Dialog dismissed due to lost focus; re-queueing: ${campaign.id}")
                clear()
                InAppMessenger.enqueueCampaign(campaign)
            }
        }

        fragment.show(fm, "InAppMessage")
        CampaignStorage.markShown(activity, campaign)
    }

    fun clear() {
        currentCampaignId = null
    }
}


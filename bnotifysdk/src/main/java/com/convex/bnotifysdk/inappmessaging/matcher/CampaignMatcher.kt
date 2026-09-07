package com.convex.bnotifyproject.Bnotify.inappmessaging.matcher

import com.convex.bnotifyproject.Bnotify.inappmessaging.model.CampaignModel

object CampaignMatcher {
    fun match(campaigns: CampaignModel, event: String): CampaignModel? {
        return if (campaigns.trigger.event == event) campaigns else null
    }
}
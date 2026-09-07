package com.convex.bnotifysdk.inappmessaging.matcher

import com.convex.bnotifysdk.inappmessaging.model.CampaignModel

object CampaignMatcher {
    fun match(campaigns: CampaignModel, event: String): CampaignModel? {
        return if (campaigns.trigger.event == event) campaigns else null
    }
}
package com.convex.bnotifysdk.inappmessaging.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CampaignModel(
    val inapp: Boolean,
    val id: String,
    val deviceToken: String,
    val projectId: String,
    val appId: String,
    val campaignId: String,
    val trigger: Trigger,
    val content: Content,
    val limits: Limits,
    val platform: String,
) : Parcelable

@Parcelize
data class Trigger(val event: String) : Parcelable

@Parcelize
data class Content(
    val type: String,
    val title: String,
    val message: String,
    val imageUrl: String,
    val cta: CTA,
    val ctaSecondary: CTASecondary,
    val bgColor: String,
    val textColor: String,
    val images: Images,
//    val customData: Map<String, Any>,
) : Parcelable


@Parcelize
data class CTA(
    val text: String,
    val action: String,
    val url: String?,
    val color: String,
    val backgroundColor: String,
    val buttonVisibility: Boolean,
) : Parcelable

@Parcelize
data class CTASecondary(
    val text: String,
    val action: String,
    val url: String?,
    val color: String,
    val backgroundColor: String,
    val buttonVisibility: Boolean,
) : Parcelable

@Parcelize
data class Limits(
    val maxPerDay: Long,
    val expireAt: String,
    val startAt: String,
    val endAt: String,
) : Parcelable

@Parcelize
data class Images(
    val portrait: String,
    val landscape: String,
) : Parcelable

//@Parcelize
//data class ButtonData(
//    val text: String? = null,
//    val action: String? = null,
//    val url: String? = null,
//    val buttonVisibility: Boolean? = null
//) : Parcelable
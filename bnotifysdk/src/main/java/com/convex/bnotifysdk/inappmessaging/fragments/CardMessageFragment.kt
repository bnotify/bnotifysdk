package com.convex.bnotifyproject.Bnotify.inappmessaging.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.convex.bnotifyproject.R

class CardMessageFragment : BaseMessageFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.inapp_card_message, container, false)
        container?.setBackgroundColor(ResourcesCompat.getColor(resources,R.color.black, requireContext().theme))
        val title = view.findViewById<TextView>(R.id.title)
        val message = view.findViewById<TextView>(R.id.message)
        val cta = view.findViewById<Button>(R.id.cta)
        val ctaSecondary = view.findViewById<Button>(R.id.cta_secondary)
        val image = view.findViewById<ImageView>(R.id.image)

        Glide.with(requireContext()).load(campaign.content.imageUrl).into(image)

        title.text = campaign.content.title
        message.text = campaign.content.message

        setupCTA(cta, campaign,campaign.content.cta)
        setupSecondaryCTA(ctaSecondary, campaign,campaign.content.ctaSecondary)

        applyStyles(view, view, title, message, cta, ctaSecondary)
        return view
    }
}
package com.convex.bnotifysdk.inappmessaging.fragments

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.convex.bnotifysdk.R

class ModalMessageFragment : BaseMessageFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.inapp_modal_message, container, false)

        val title = view.findViewById<TextView>(R.id.title)
        val message = view.findViewById<TextView>(R.id.message)
        val cta = view.findViewById<Button>(R.id.cta)
        val ctaSecondary = view.findViewById<Button>(R.id.cta_secondary)
        val close = view.findViewById<ImageView>(R.id.close)
        val image = view.findViewById<ImageView>(R.id.image)

        Glide.with(requireContext()).load(campaign.content.imageUrl).into(image)

        title.text = campaign.content.title
        message.text = campaign.content.message
        close.imageTintList = ColorStateList.valueOf(Color.parseColor(campaign.content.cta.backgroundColor))

        setupCTA(cta, campaign,campaign.content.cta)
        setupSecondaryCTA(ctaSecondary, campaign,campaign.content.ctaSecondary)


        close.setOnClickListener { dismissAndReport(campaign) }

        applyStyles(view, view, title, message, cta, ctaSecondary)
        return view
    }
}

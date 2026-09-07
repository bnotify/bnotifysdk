package com.convex.bnotifyproject.Bnotify.inappmessaging.fragments

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.convex.bnotifyproject.R

class ImageMessageFragment : BaseMessageFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.inapp_image_only, container, false)

        val image = view.findViewById<ImageView>(R.id.image)
        val close = view.findViewById<ImageView>(R.id.close)

        close.imageTintList = ColorStateList.valueOf(Color.parseColor(campaign.content.cta.backgroundColor))

        // TODO: Load real image from campaign (Glide/Picasso/Coil)
         Glide.with(requireContext()).load(campaign.content.imageUrl).into(image)

        close.setOnClickListener { dismissAndReport(campaign) }

        setupImageCTA(image, campaign,campaign.content.cta)

        return view
    }
}

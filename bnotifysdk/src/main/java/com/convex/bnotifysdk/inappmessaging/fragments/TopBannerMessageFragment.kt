package com.convex.bnotifysdk.inappmessaging.fragments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.convex.bnotifysdk.R

class TopBannerMessageFragment : BaseMessageFragment() {
    override val noDimBackground: Boolean = true // no dim for banners
    private val AUTO_DISMISS_MS = 10000L // 5 seconds
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        if (isVisible) {
            Log.i("FIAM.Display", "Auto-dismiss banner: ${campaign.id}")
            dismissAndReport(campaign)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.inapp_banner_top, container, false)

        val title = view.findViewById<TextView>(R.id.title)
        val message = view.findViewById<TextView>(R.id.message)
        val ctaImage = view.findViewById<ImageView>(R.id.cta)

        Glide.with(requireContext()).load(campaign.content.imageUrl).into(ctaImage)
        title.text = campaign.content.title
        message.text = campaign.content.message

        setupImageCTA(ctaImage, campaign,campaign.content.cta)
        setupViewCTA(view, campaign,campaign.content.cta)
        applyStyles(view, view, null, message, null, null)

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Stick to top
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.TOP)

            // Slide-down animation
            attributes = attributes.apply {
                windowAnimations = R.style.TopBannerAnimation
            }
        }

        // Start auto-dismiss countdown
        autoDismissHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
    }
}

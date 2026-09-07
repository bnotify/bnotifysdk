package com.convex.bnotifysdk.inappmessaging.fragments

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.convex.bnotifysdk.inappmessaging.model.CampaignModel
import androidx.core.graphics.drawable.toDrawable
import com.convex.bnotifysdk.inappmessaging.InAppMessenger
import com.convex.bnotifysdk.inappmessaging.model.CTA
import com.convex.bnotifysdk.inappmessaging.model.CTASecondary
import com.convex.bnotifysdk.inappmessaging.ui.MessageRenderer
import java.util.concurrent.TimeUnit

abstract class BaseMessageFragment : DialogFragment() {
    protected lateinit var campaign: CampaignModel
    private var impressionHandler: Handler? = null
    private var impressionRunnable: Runnable? = null
    private val IMPRESSION_MS = TimeUnit.SECONDS.toMillis(1)
    private var dismissListener: (() -> Unit)? = null
    protected open val noDimBackground: Boolean = false

    private var isCTAClick = false

    fun bindCampaign(campaign: CampaignModel) {
        arguments = Bundle().apply {
            putParcelable("campaign", campaign)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        campaign = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable("campaign", CampaignModel::class.java)
                ?: throw IllegalStateException("Campaign must be provided")
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable<CampaignModel>("campaign")
                ?: throw IllegalStateException("Campaign must be provided")
        }

        setIsCTAClick(false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Remove default dialog rectangle background
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

            // Layout matches parent width but wraps height
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )

            if (noDimBackground) {
                // 🔹 Remove dim if requested by subclass
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startImpressionTimer()
    }

    override fun onPause() {
        super.onPause()
        cancelImpressionTimer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelImpressionTimer()
    }

    fun setDismissListener(listener: () -> Unit) {
        dismissListener = listener
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        dismissListener?.invoke()
    }

    private fun startImpressionTimer() {
        cancelImpressionTimer()
        impressionHandler = Handler(Looper.getMainLooper())
        impressionRunnable = Runnable {
            Log.i("FIAM.Display", "Impression timer onFinish for: ${campaign.id}")
            InAppMessenger.onMessageImpression(campaign)
        }
        impressionHandler?.postDelayed(impressionRunnable!!, IMPRESSION_MS)
    }

    private fun cancelImpressionTimer() {
        impressionHandler?.removeCallbacks(impressionRunnable ?: return)
        impressionHandler = null
        impressionRunnable = null
    }

    protected fun setupCTA(button: Button?, campaign: CampaignModel, cta: CTA?) {
        if (button == null) return
        if (cta != null) {
            button.text = cta.text
            button.visibility = if (cta.buttonVisibility) View.VISIBLE else View.GONE
            button.setOnClickListener {
                Log.i("FIAM.Display", "CTA clicked: ${campaign.id} → ${cta.action}")
                setIsCTAClick(true)
                InAppMessenger.onMessageClick(campaign, cta)
                dismissAndReport(campaign)
            }
        } else {
            button.visibility = View.GONE
        }
    }

    protected fun setupSecondaryCTA(button: Button?, campaign: CampaignModel, cta: CTASecondary?) {
        if (button == null) return
        if (cta != null) {
            button.text = cta.text
            button.visibility = if (cta.buttonVisibility) View.VISIBLE else View.GONE
            button.setOnClickListener {
                Log.i("FIAM.Display", "CTA clicked: ${campaign.id} → ${cta.action}")
                setIsCTAClick(true)
                InAppMessenger.onMessageSecondaryClick(campaign, cta)
                dismissAndReport(campaign)
            }
        } else {
            button.visibility = View.GONE
        }
    }

    protected fun setupImageCTA(image: ImageView?, campaign: CampaignModel, cta: CTA?) {
        if (image == null) return
        if (cta != null) {
            image.visibility = View.VISIBLE
            image.setOnClickListener {
                Log.i("FIAM.Display", "CTA clicked: ${campaign.id} → ${cta.action}")
                setIsCTAClick(true)
                InAppMessenger.onMessageClick(campaign, cta)
                dismissAndReport(campaign)
            }
        } else {
            image.visibility = View.GONE
        }
    }

    protected fun setupViewCTA(view: View?, campaign: CampaignModel, cta: CTA?) {
        if (view == null) return
        if (cta != null) {
            view.visibility = View.VISIBLE
            view.setOnClickListener {
                Log.i("FIAM.Display", "CTA clicked: ${campaign.id} → ${cta.action}")
                setIsCTAClick(true)
                InAppMessenger.onMessageClick(campaign, cta)
                dismissAndReport(campaign)
            }
        } else {
            view.visibility = View.GONE
        }
    }

    protected fun dismissAndReport(campaign: CampaignModel) {
//        setIsCTAClick(false)
        cancelImpressionTimer()
        dismiss()
//        InAppMessenger.onMessageDismissed(campaign)
        MessageRenderer.clear()
    }

    protected fun applyStyles(
        root: View,
        bgView: View,
        title: TextView?,
        message: TextView?,
        cta: Button?,
        ctaSecondary: Button?
    ) {
        // 🔹 Background color (if bgView uses a shape drawable like inapp_card_bg.xml)
        campaign.content.bgColor.let { hex ->
            try {
                val drawable = bgView.background
                if (drawable is GradientDrawable) {
                    drawable.setColor(Color.parseColor(hex)) // change solid fill
                } else {
                    bgView.setBackgroundColor(Color.parseColor(hex)) // fallback
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 🔹 Text color for title + message
        campaign.content.textColor.let { hex ->
            val c = Color.parseColor(hex)
            title?.setTextColor(c)
            message?.setTextColor(c)
        }

        // 🔹 CTA button background color
        campaign.content.cta.backgroundColor.let { hex ->
            val bg = Color.parseColor(hex)
            cta?.background?.setTint(bg)
            ctaSecondary?.background?.setTint(bg)
        }

        // 🔹 CTA button text color
        campaign.content.cta.color.let { hex ->
            val c = Color.parseColor(hex)
            cta?.setTextColor(c)
            ctaSecondary?.setTextColor(c)
        }
    }

    internal fun isCTAClick(): Boolean {
        return isCTAClick
    }

    internal fun setIsCTAClick(value: Boolean) {
        isCTAClick = value
    }
}



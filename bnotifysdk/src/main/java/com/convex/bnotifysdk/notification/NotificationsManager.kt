package com.convex.bnotifysdk.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.NOTIFICATION_SERVICE
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.convex.bnotifysdk.BNotifyApp
import com.convex.bnotifysdk.model.NotificationModel
import com.convex.bnotifysdk.receiver.NotificationDismissReceiver
import com.convex.bnotifysdk.socket.SocketManager
import com.convex.bnotifysdk.utils.NotifyConstants
import java.io.File
import java.nio.ByteBuffer
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.apply
import kotlin.jvm.java
import kotlin.takeIf
import kotlin.text.endsWith
import kotlin.text.isNullOrBlank
import kotlin.text.isNullOrEmpty
import kotlin.text.startsWith
import kotlin.text.toIntOrNull

internal object NotificationsManager {
    private lateinit var notificationManager: NotificationManager
    private var isInitialized = false
    private const val CHANNEL_ID = "bnotify_channel"
    private const val EXTRA_TRAY_NOTIFICATION_ID = "tray_notification_id"
    private const val GIF_MAX_FRAMES = 48
    private const val GIF_MAX_EDGE_PX = 720
    private const val GIF_MIN_FRAME_DELAY_MS = 70

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gifFrameRunnables = ConcurrentHashMap<Int, Runnable>()
    private val dismissedNotificationIds = ConcurrentHashMap.newKeySet<Int>()

    fun init(context: Context) {
        if (!isInitialized) {
            notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel(context)
            isInitialized = true
        }
    }

    /** Called from deleteIntent — stops GIF updates so the notification cannot be re-posted. */
    fun onNotificationDismissed(trayNotificationId: Int) {
        if (trayNotificationId < 0) return
        dismissedNotificationIds.add(trayNotificationId)
        stopGifAnimation(trayNotificationId)
        // Cover race where a GIF frame notify runs after the swipe.
        if (::notificationManager.isInitialized) {
            notificationManager.cancel(trayNotificationId)
        }
        Log.d("NotificationsManager", "GIF stopped after dismiss id=$trayNotificationId")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appName = context.getConsumerAppName()
            val channel = NotificationChannel(
                CHANNEL_ID,
                appName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "App notifications"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun handleNow(model: NotificationModel, context: Context) {
        try {
            if (!isInitialized) init(context)

            val notificationId = model.notificationId?.toIntOrNull() ?: Random().nextInt(10000)
            dismissedNotificationIds.remove(notificationId)
            stopGifAnimation(notificationId)

            val notificationBuilder = buildBaseNotification(model, context, notificationId)

            // Alert once immediately (sound/vibrate). Image/GIF updates below are silent.
            showNotification(notificationBuilder.build(), notificationId, model, trackReceived = true)
            muteNotificationAlerts(notificationBuilder)

            if (!model.imageUrl.isNullOrBlank()) {
                val isURLValid = isValidImageUrl(model.imageUrl)
                if (isURLValid) {
                    loadImageForNotification(
                        model,
                        notificationBuilder,
                        notificationId,
                        model.isExpanded == true,
                        context
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("Bnotify", "${e.printStackTrace()}")
        }
    }

    private fun muteNotificationAlerts(builder: NotificationCompat.Builder) {
        builder.setOnlyAlertOnce(true)
            .setSilent(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
    }


    /*private fun buildBaseNotification(
        model: NotificationModel,
        context: Context,
        notificationId: Int
    ): NotificationCompat.Builder {

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//            Log.i("Notification_SocketIO","PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE ${PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE}")
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
//            Log.i("Notification_SocketIO","FLAG_ONE_SHOT ${PendingIntent.FLAG_ONE_SHOT}")
        }

//        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
//        } else {
//            PendingIntent.FLAG_ONE_SHOT
//        }

//        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        } else {
//            PendingIntent.FLAG_UPDATE_CURRENT
//        }
        // For the main click action (when notification is clicked)
        val clickIntent = Intent(context, BNotifyApp.getActivityToOpenOnClick(context)).apply {
            putExtra(NotifyConstants.From, "notification")
            putExtra(NotifyConstants.SCREEN, model.screen)
            putExtra(NotifyConstants.ACTION, NotifyConstants.ClickedEvent) // Action when clicked
            putExtra(NotifyConstants.NOTIFICATION_ID, model.notificationId ?: 0)
            putExtra(NotifyConstants.TYPE, model.type ?: null)
            putExtra(NotifyConstants.TOKEN, model.token)
            putExtra(NotifyConstants.CLICK, true)
//            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val clickPendingIntent = PendingIntent.getActivity(
            context,
            model.notificationId?.toIntOrNull() ?: System.currentTimeMillis().toInt(),
            clickIntent,
            pendingIntentFlags
        )

// For the dismiss action (when notification is dismissed)
        val dismissIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
            putExtra(NotifyConstants.NOTIFICATION_ID, model.notificationId ?: 0)
            putExtra(NotifyConstants.ACTION, NotifyConstants.DismissedEvent) // Action when dismissed
            putExtra(NotifyConstants.SCREEN, model.screen)
            putExtra(NotifyConstants.TYPE, model.type ?: null)
            putExtra(NotifyConstants.CLICK, false)
            putExtra(NotifyConstants.TOKEN, model.token)
        }

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (model.notificationId?.toIntOrNull() ?: System.currentTimeMillis().toInt()) + 1, // Different request code
            dismissIntent,
            pendingIntentFlags
        )

        val smallIconRes = context.applicationInfo.icon // consumer app's launcher icon
        val appIcon = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = drawableToBitmap(appIcon)
        //==========================================================================================

        Log.d("Notification_SocketIO", "Notification ACTION: ${model.action}")
        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setAutoCancel(true)
            setDefaults(Notification.DEFAULT_ALL)
            setWhen(System.currentTimeMillis())
            setSmallIcon(smallIconRes)
            setLargeIcon(bitmap)
            setContentTitle(model.title)
            setContentText(model.message)
            setContentIntent(clickPendingIntent)
            setDeleteIntent(dismissPendingIntent) // Set dismiss intent
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setCategory(NotificationCompat.CATEGORY_MESSAGE)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setShowWhen(true)
        }
    }*/

    private fun buildBaseNotification(
        model: NotificationModel,
        context: Context,
        notificationId: Int
    ): NotificationCompat.Builder {

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        // For the main click action (when notification is clicked)
        val clickIntent = Intent(context, BNotifyApp.getActivityToOpenOnClick(context)).apply {
            putExtra(NotifyConstants.From, "notification")
            putExtra(NotifyConstants.SCREEN, model.screen)
            putExtra(NotifyConstants.ACTION, NotifyConstants.ClickedEvent)
            putExtra(NotifyConstants.NOTIFICATION_ID, model.notificationId ?: 0)
            putExtra(NotifyConstants.TYPE, model.type ?: null)
            putExtra(NotifyConstants.TOKEN, model.token)
            putExtra(NotifyConstants.CLICK, true)
            // Add these flags to handle the activity launch properly
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val clickPendingIntent = PendingIntent.getActivity(
            context,
            // Use a unique request code for each notification
            (model.notificationId?.toIntOrNull() ?: Random().nextInt()) + 1000,
            clickIntent,
            pendingIntentFlags
        )

        // For the dismiss action (when notification is dismissed)
        val dismissIntent = Intent(context, NotificationDismissReceiver::class.java).apply {
            putExtra(NotifyConstants.NOTIFICATION_ID, model.notificationId ?: 0)
            putExtra(EXTRA_TRAY_NOTIFICATION_ID, notificationId)
            putExtra(NotifyConstants.ACTION, NotifyConstants.DismissedEvent)
            putExtra(NotifyConstants.SCREEN, model.screen)
            putExtra(NotifyConstants.TYPE, model.type ?: null)
            putExtra(NotifyConstants.CLICK, false)
            putExtra(NotifyConstants.TOKEN, model.token)
        }

        val dismissFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2000,
            dismissIntent,
            dismissFlags
        )

        val smallIconRes = context.applicationInfo.icon
        val appIcon = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = drawableToBitmap(appIcon)

        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setAutoCancel(true)
            setDefaults(Notification.DEFAULT_ALL)
            // Frame updates call notify() again; alert only on the first post.
            setOnlyAlertOnce(true)
            setWhen(System.currentTimeMillis())
            setSmallIcon(smallIconRes)
            setLargeIcon(bitmap)
            setContentTitle(model.title)
            setContentText(model.message)
            setContentIntent(clickPendingIntent)
            setDeleteIntent(dismissPendingIntent)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setCategory(NotificationCompat.CATEGORY_MESSAGE)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setShowWhen(true)
        }
    }

    private fun loadImageForNotification(
        model: NotificationModel,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        isExpanded: Boolean,
        context: Context
    ) {
        val appCtx = context.applicationContext
        val appIconBitmap = drawableToBitmap(appCtx.packageManager.getApplicationIcon(appCtx.packageName))

        if (isExpanded) {
            loadExpandedImage(model, builder, notificationId, appIconBitmap, appCtx)
            return
        }

        // Compact notification: static large icon for any supported image (incl. GIF first frame).
        Glide.with(appCtx)
            .asBitmap()
            .load(model.imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    if (!isNotificationStillPosted(notificationId)) return
                    muteNotificationAlerts(builder)
                    builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                        .setLargeIcon(resource)
                    showNotification(builder.build(), notificationId, model, trackReceived = false)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    // Text-only alert already shown in handleNow.
                }
            })
    }

    /**
     * Expanded BigPicture:
     * - PNG / JPG / JPEG / WEBP / BMP → static image
     * - GIF → timed frame playback while the notification stays in the tray
     */
    private fun loadExpandedImage(
        model: NotificationModel,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        appIconBitmap: Bitmap,
        context: Context
    ) {
        stopGifAnimation(notificationId)

        Glide.with(context)
            .asFile()
            .load(model.imageUrl)
            .into(object : CustomTarget<File>() {
                override fun onResourceReady(
                    resource: File,
                    transition: Transition<in File>?
                ) {
                    if (!isNotificationStillPosted(notificationId)) return
                    muteNotificationAlerts(builder)

                    if (isGifUrl(model.imageUrl) || isGifFile(resource)) {
                        val frames = decodeGifFrames(resource)
                        if (frames.isNotEmpty()) {
                            applyBigPictureStyle(builder, frames[0].bitmap, model, appIconBitmap)
                            showNotification(builder.build(), notificationId, model, trackReceived = false)
                            startSmoothGifPlayback(notificationId, builder, model, appIconBitmap, frames)
                            return
                        }
                        Log.w("NotificationsManager", "GIF decode produced 0 frames; static fallback")
                    }

                    val staticBitmap = decodeStaticBitmap(resource)
                    if (staticBitmap != null) {
                        applyBigPictureStyle(builder, staticBitmap, model, appIconBitmap)
                        showNotification(builder.build(), notificationId, model, trackReceived = false)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    stopGifAnimation(notificationId)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Log.w("NotificationsManager", "Expanded image load failed")
                }
            })
    }

    private fun isGifFile(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(6)
                if (stream.read(header) < 6) return false
                val sig = String(header, Charsets.US_ASCII)
                sig.startsWith("GIF87a") || sig.startsWith("GIF89a")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeStaticBitmap(file: File): Bitmap? {
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.let { scaleBitmapIfNeeded(it) }
        } catch (e: Exception) {
            Log.e("NotificationsManager", "Failed to decode static image", e)
            null
        }
    }

    private data class GifFrame(val bitmap: Bitmap, val delayMs: Int)

    private fun decodeGifFrames(file: File): List<GifFrame> {
        return try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes)
            val header = GifHeaderParser().setData(buffer).parseHeader()

            val provider = object : GifDecoder.BitmapProvider {
                override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
                    Bitmap.createBitmap(width, height, config)

                override fun release(bitmap: Bitmap) {
                    // Keep frames for playback; do not recycle here.
                }

                override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
                override fun release(bytes: ByteArray) {}
                override fun obtainIntArray(size: Int): IntArray = IntArray(size)
                override fun release(array: IntArray) {}
            }

            val decoder = StandardGifDecoder(provider)
            decoder.setData(header, buffer)
            if (decoder.frameCount <= 0) return emptyList()

            val frames = ArrayList<GifFrame>(minOf(decoder.frameCount, GIF_MAX_FRAMES))
            val count = minOf(decoder.frameCount, GIF_MAX_FRAMES)
            repeat(count) {
                decoder.advance()
                val frame = decoder.nextFrame ?: return@repeat
                val delay = decoder.getDelay(decoder.currentFrameIndex)
                    .coerceAtLeast(GIF_MIN_FRAME_DELAY_MS)
                frames.add(GifFrame(scaleBitmapIfNeeded(frame.copy(Bitmap.Config.ARGB_8888, false)), delay))
            }
            frames
        } catch (e: Exception) {
            Log.e("NotificationsManager", "Failed to decode GIF frames", e)
            emptyList()
        }
    }

    private fun scaleBitmapIfNeeded(source: Bitmap): Bitmap {
        val maxEdge = maxOf(source.width, source.height)
        if (maxEdge <= GIF_MAX_EDGE_PX) return source
        val scale = GIF_MAX_EDGE_PX.toFloat() / maxEdge
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun startSmoothGifPlayback(
        notificationId: Int,
        builder: NotificationCompat.Builder,
        model: NotificationModel,
        appIconBitmap: Bitmap,
        frames: List<GifFrame>
    ) {
        stopGifAnimation(notificationId)
        if (frames.size <= 1) return
        if (!isNotificationStillPosted(notificationId)) return

        muteNotificationAlerts(builder)
        // First frame is already on the notification; continue from frame 1.
        // Loop until the user dismisses — so expanding again still shows a live GIF.
        var index = 1

        val tick = object : Runnable {
            override fun run() {
                if (!isNotificationStillPosted(notificationId)) {
                    stopGifAnimation(notificationId)
                    return
                }
                try {
                    val frame = frames[index]
                    muteNotificationAlerts(builder)
                    applyBigPictureStyle(builder, frame.bitmap, model, appIconBitmap)
                    // Re-check immediately before posting — dismiss can race the tick.
                    if (!isNotificationStillPosted(notificationId)) {
                        stopGifAnimation(notificationId)
                        return
                    }
                    notificationManager.notify(notificationId, builder.build())
                    if (dismissedNotificationIds.contains(notificationId)) {
                        notificationManager.cancel(notificationId)
                        stopGifAnimation(notificationId)
                        return
                    }
                    index = (index + 1) % frames.size
                    gifFrameRunnables[notificationId] = this
                    mainHandler.postDelayed(this, frame.delayMs.toLong())
                } catch (e: Exception) {
                    Log.e("NotificationsManager", "GIF playback failed", e)
                    stopGifAnimation(notificationId)
                }
            }
        }

        gifFrameRunnables[notificationId] = tick
        mainHandler.postDelayed(tick, frames[0].delayMs.toLong())
    }

    private fun stopGifAnimation(notificationId: Int) {
        gifFrameRunnables.remove(notificationId)?.let { mainHandler.removeCallbacks(it) }
    }

    private fun isNotificationStillPosted(notificationId: Int): Boolean {
        if (dismissedNotificationIds.contains(notificationId)) return false
        if (!::notificationManager.isInitialized) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return notificationManager.activeNotifications.any { it.id == notificationId }
        }
        return true
    }

    private fun applyBigPictureStyle(
        builder: NotificationCompat.Builder,
        picture: Bitmap,
        model: NotificationModel,
        appIconBitmap: Bitmap
    ) {
        builder.setStyle(
            NotificationCompat.BigPictureStyle()
                .bigPicture(picture)
                .setBigContentTitle(model.title)
                .setSummaryText(model.message)
                .bigLargeIcon(appIconBitmap)
        )
    }

    private fun showNotification(
        notification: Notification,
        notificationId: Int,
        model: NotificationModel,
        trackReceived: Boolean = true
    ) {
        try {
            notificationManager.notify(notificationId, notification)
            if (trackReceived) {
                SocketManager.handleNotificationReceived(model)
            }
            Log.d("NotificationsManager", "Notification shown with ID: $notificationId")
        } catch (e: Exception) {
            Log.e("NotificationsManager", "Error showing notification", e)
        }
    }

    private fun getBitmapFromMipmap(context: Context, mipmapResId: Int): Bitmap? {
        val drawable = AppCompatResources.getDrawable(context, mipmapResId) as? BitmapDrawable
        return drawable?.bitmap
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        return if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    private fun isGifUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return path.endsWith(".gif") || path.contains(".gif.")
    }

    fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        if (!(url.startsWith("http://", true) || url.startsWith("https://", true))) return false
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return path.endsWith(".png") ||
                path.endsWith(".jpg") ||
                path.endsWith(".jpeg") ||
                path.endsWith(".webp") ||
                path.endsWith(".gif") ||
                path.endsWith(".bmp") ||
                // CDN URLs sometimes omit a file extension; still allow https image hosts.
                !path.substringAfterLast('/').contains('.')
    }

    fun Context.getConsumerAppName(): String {
        val appInfo = applicationInfo
        val stringId = appInfo.labelRes
        return if (stringId == 0) {
            appInfo.nonLocalizedLabel?.toString() ?: packageManager.getApplicationLabel(appInfo).toString()
        } else {
            getString(stringId)
        }
    }
}
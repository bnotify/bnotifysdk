package com.convex.bnotifysdk.socket

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.content.ContextCompat
import com.convex.bnotifysdk.inappmessaging.InAppMessenger
import com.convex.bnotifysdk.BNotifyApp
import com.convex.bnotifysdk.activity.PermissionRequestActivity
import com.convex.bnotifysdk.model.BNotifyTokenModel
import com.convex.bnotifysdk.model.BnotifyConfig
import com.convex.bnotifysdk.model.NotificationModel
import com.convex.bnotifysdk.network.NetworkMonitor
import com.convex.bnotifysdk.notification.NotificationsManager
import com.convex.bnotifysdk.utils.PrefsHelper
import com.google.gson.Gson
import com.techionic.customnotifcationapp.network.IPFinder.IPFinder
import com.techionic.customnotifcationapp.network.IPFinder.IPFinderClass
import com.techionic.customnotifcationapp.network.IPFinder.IPFinderResponse
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.TimeZone
import java.util.UUID
import java.util.regex.Pattern
import kotlin.apply
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.isInitialized
import kotlin.jvm.java
import kotlin.let
import kotlin.math.sqrt
import kotlin.text.format
import kotlin.text.indexOf
import kotlin.text.isNotBlank
import kotlin.text.isNullOrBlank
import kotlin.text.isNullOrEmpty
import kotlin.text.toByteArray
import kotlin.text.toLong
import kotlin.text.uppercase
import kotlin.to
import kotlin.toString

internal object SocketManager {
    private val host = "wss://bnotify.convexinteractive.com"  // Live Server IP
    //            private val host = "wss://a3e8c524a973.ngrok-free.app"  // Server IP
    private val port = 3000
    private lateinit var socket: Socket
    internal lateinit var appContext: Context
    private lateinit var networkMonitor: NetworkMonitor
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val NOTIFICATION_CHANNEL_ID = "socket_service_channel"
    private const val NOTIFICATION_ID = 101

    // Socket events
    private const val EVENT_ON_MESSAGE_RECEIVED = "onMessageReceived"
    private const val EVENT_IN_APP_MESSAGE_SHOW = "inapp:show"
    private const val EVENT_REGISTERED = "registered"

    private val PREFS_FILE: String = "device_uuid.xml"
    private val PREFS_UUID: String = "device_uuid"
    private const val RECONNECT_DELAY = 5000L // 5 seconds
    private var reconnectAttempts = 0
    private const val MAX_RECONNECT_ATTEMPTS = 5
    private var isConnectionInProgress = false
    private const val PERMISSION_REQUEST_CODE = 1001

    internal var FCM_TOKEN: String = ""

    // Queue to hold pending events until socket connects
    private val pendingEvents: Queue<Pair<String, JSONObject>> = LinkedList()

    private var socketConnectListener:OnSocketConnectListener? = null



    internal fun setSocketConnectListener(listener: OnSocketConnectListener){
        this.socketConnectListener = listener
    }

    private fun getSocketConnectListener(): OnSocketConnectListener? {
        return this.socketConnectListener
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun connect() {
        Log.d("IP_DATA_INFO", "Socket connect method")
        IPFinder.init(appContext, IPFinderClass.UniversalIP, object : IPFinder.IPListener{
            override fun onSuccess(ipFinderResponse: IPFinderResponse) {
                var response: String = Gson().toJson(ipFinderResponse)
                Log.e("IP_DATA_INFO", "IPFinderResponse: ${response}")
                connectSocket(ipFinderResponse)
            }

            override fun onError(error: String) {
//                Log.e("SocketManager", "IPFinder error: ${error}")
                val model = IPFinderResponse()
                val dataModel = IPFinderResponse.Data()
                val ipDataModel = IPFinderResponse.Data.IPData()
                ipDataModel.ip_address = getIpAddress()
                ipDataModel.country = getDeviceCountryName(appContext)
                ipDataModel.country_code = getDeviceCountryCode(appContext)
                ipDataModel.latitude = 0.0
                ipDataModel.latitude = 0.0

                connectSocket(model)
            }
        })
    }

    fun connectSocket(ipFinderResponse: IPFinderResponse){
        Log.e("SocketManager", "connect isConnected: ${isConnected()} || isConnecting: ${isConnecting()}")
        if (isConnected() || isConnecting()) return

        isConnectionInProgress = true

        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket")
                query = buildConnectionQuery(ipFinderResponse)
                reconnection = false // We'll handle reconnection manually

                Log.i("SocketManager","BUILD_QUERY:\n ${query}")
            }

            socket = IO.socket(host, options)
            setupSocketListeners()
            socket.connect()
        } catch (e: Exception) {
            isConnectionInProgress = false
            Log.e("SocketManager", "Connection error: ${e.message}")
            reconnect()
        }
    }

    fun disconnect() {
//        try {
//            Log.i("SocketManager", "Socket disconnected isInitialized ${::socket.isInitialized} isConnected: ${socket.connected()} ReConnectin in ${RECONNECT_DELAY * (reconnectAttempts * reconnectAttempts)}")
//        }catch (e:Exception){
//            Log.e("SocketManager", "Socket disconnected Exception: ${e.printStackTrace()}")
//        }
        if (::socket.isInitialized && socket.connected()) {
            removeSocketListeners()
            socket.disconnect()
            isConnectionInProgress = false
            Log.e("SocketManager", "Socket disconnected ")
        }
    }

    fun reconnect() {
        if (::socket.isInitialized && socket.connected()) return

        mainHandler.removeCallbacksAndMessages(null)

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.i("SocketManager", "Max reconnect ${MAX_RECONNECT_ATTEMPTS}, attempts reached ${reconnectAttempts} - restarting service")
//            mainHandler.post {
//                (appContext as? ContextWrapper)?.baseContext?.let {
//                    PersistentMessagingService.restartService(it)
//                }
//            }
            reconnectAttempts = 0
            return
        }

        reconnectAttempts++
        val delay = RECONNECT_DELAY * (reconnectAttempts * reconnectAttempts) // Exponential backoff
        Log.i("SocketManager", "Attempting to reconnect in ${delay}ms (attempt $reconnectAttempts)")

        disconnect()

        mainHandler.postDelayed({
            connect()
        }, delay)
    }

    private fun flushPendingEvents() {
        while (pendingEvents.isNotEmpty()) {
            val (event, json) = pendingEvents.poll()
            socket.emit(event, json)
            Log.d("SocketManager", "Flushed queued event: $event -> $json")
        }
    }

    private fun emitOrQueue(event: String, json: JSONObject) {
        if (::socket.isInitialized && socket.connected()) {
            socket.emit(event, json)
            Log.d("SocketManager", "Emitted immediately: $event -> $json")
            Log.d("FIAM", "SocketManager Flushed queued event: $event -> $json")
        } else {
            pendingEvents.add(Pair(event, json))
            Log.w("SocketManager", "Socket not ready, queued: $event -> $json")
            Log.d("FIAM", "SocketManager not ready, queued: $event -> $json")
        }
    }

    fun handleNotificationClickedIntent(intent: Intent) {
//        if (!::socket.isInitialized || !socket.connected()) {
//            Log.w("SocketManager", "Socket not initialized or not connected — ignoring click emit")
//            return
//        }
//
//        mainHandler.post {
//            val notificationId = intent.getStringExtra("notification_id")
//            val json = JSONObject().apply { put("notificationId", notificationId) }
//            Log.d("Notification_SocketIO", "CLICK JSON: ${json}")
//            socket.emit("onClicked", json)
//        }

        mainHandler.post {
            val notificationId = intent.getStringExtra("notification_id")
            val screen = intent.getStringExtra("screen")
            val token = intent.getStringExtra("token")
            val json = JSONObject().apply {
                put("notificationId", notificationId)
                put("token", token)
                put("screen", screen)
            }
            emitOrQueue("onClicked", json)
            Log.d("Notification_SocketIO", "Notification: CLICK JSON: ${json}")
        }
    }


    fun handleNotificationDismissedIntent(intent: Intent) {
        mainHandler.post {
            val notificationId = intent.getStringExtra("notification_id")
            val token = intent.getStringExtra("token")
            val json = JSONObject().apply {
                put("notificationId", notificationId)
                put("token", token)
            }
            Log.d("Notification_SocketIO", "Notification: onDismissed JSON: ${json}")
            emitOrQueue("onDismissed", json)
//            if (::socket.isInitialized && socket.connected()) {
//                socket.emit("onDismissed", json)
//            } else {
//                Log.w("SocketManager", "Socket not initialized or not connected — skipping dismiss emit")
//            }
        }
    }

    fun handleNotificationReceived(model: NotificationModel) {
        mainHandler.post {
            val obj = Gson().toJson(model)
            val json = JSONObject().apply {
                put("notificationId", model.notificationId)
                put("token", model.token)
            }
            Log.d("Notification_SocketIO", "onReceived Params JSON: ${json}")
            Log.d("Notification_SocketIO", "onReceived Notification JSON: ${obj}")
//            socket.emit("onReceived", json)
            emitOrQueue("onReceived", json)
        }
    }

    fun handleInAppMessagesImpression(event: String, json: JSONObject){
        emitOrQueue(event, json)
    }

    fun isConnected(): Boolean {
        try {
            if (::socket.isInitialized && socket.connected()){
//                Log.d("SocketManager", "isConnected isInitialized: ${::socket.isInitialized} Connected: ${socket.connected()} isConnecting: ${isConnectionInProgress}")
                return true
            }else if (socket.connected()){
//                Log.d("SocketManager", "isConnected Connected: ${socket.connected()}")
                return true
            }else if (::socket.isInitialized && !socket.connected()){
                Log.d("SocketManager", "isConnected isInitialized: ${::socket.isInitialized} !Connected: ${socket.connected()} isConnecting: ${isConnectionInProgress}")
                return false
            }else if (!::socket.isInitialized && !socket.connected()){
//                Log.d("SocketManager", "isConnected !isInitialized: ${::socket.isInitialized} !Connected: ${socket.connected()} isConnecting: ${isConnectionInProgress}")
                return false
            }else{
//                Log.d("SocketManager", "isConnected ALL FALSE isConnecting: ${isConnectionInProgress}")
                return false
            }
//            return ::socket.isInitialized && socket.connected()
        }catch (e: Exception){
//            Log.d("SocketManager", "isConnected Exception: ${e.printStackTrace()}")
            return false
        }
    }

    fun isConnecting(): Boolean {
        return isConnectionInProgress
    }

    private fun buildConnectionQuery(ipFinderResponse: IPFinderResponse): String {
        val token = PrefsHelper.getFcmToken(appContext.applicationContext)
        if (token != null) {
            FCM_TOKEN = token
            Log.d("FCM_TOKEN", "Restored saved token: $token")
        } else {
            Log.w("FCM_TOKEN", "No token saved yet")
        }

        val (versionCode, versionName) = getAppVersionInfo(appContext)
        val config_json = PrefsHelper.getConfig(appContext)
        var configs: BnotifyConfig = BNotifyApp.readBNotifyConfig(config_json.toString())!!
        val isDebug = appContext.applicationContext.isDebugBuild()

        Log.w("APP_VARIANT", "isDebug: ${isDebug}")

        return listOf(
            "ip=${ipFinderResponse.data?.ip_data?.ip_address}",
            "os=android ${Build.VERSION.RELEASE}",
            "browser=chrome",
            "deviceType=${getDeviceType()}",
            "screenResolution=${getScreenResolution()}",
            "timezone=${TimeZone.getDefault().id}",
            "hardwareConcurrency=${Runtime.getRuntime().availableProcessors()} cores",
            "deviceMemory=${getTotalRamGB()}",
            "deviceMainMemory=${getTotalROMGB()}",
            "deviceBrand=${Build.BRAND}",
            "deviceModel=${Build.MODEL}",
            "platform=android",
            "userAgent=${System.getProperty("http.agent")}",
            "country=${ipFinderResponse.data?.ip_data?.country_code}",
            "language=${getDeviceLanguage(appContext)}",
            "version=${versionName}",
            "lat=${ipFinderResponse.data?.ip_data?.latitude}",
            "lng=${ipFinderResponse.data?.ip_data?.longitude}",
            "uuid=${getPersistentDeviceId(appContext)}",
            "projectId=${configs.projectId}",
            "appId=${configs.appId}",
            "fcmtoken=${token}",
        ).joinToString("&")
    }

    private fun setupSocketListeners() {
        socket.on(Socket.EVENT_CONNECT) {
            isConnectionInProgress = false
            Log.d("SocketManager", "Connected")
            reconnectAttempts = 0 // Reset counter on successful connection
            // Flush pending events
            getSocketConnectListener()?.onConnected()
            flushPendingEvents()
        }.on(Socket.EVENT_DISCONNECT) { args ->
            isConnectionInProgress = false
            Log.d("SocketManager", "Disconnected: ${args.joinToString()}")
            Log.d("FIAM", "SocketManager: socket disconnected: ${args.joinToString()}")
            if (args.isNotEmpty() && args[0] == "io server disconnect") {
                Log.d("SocketManager", "Server requested disconnect, not reconnecting")
            } else {
                reconnect()
            }
        }.on(Socket.EVENT_CONNECT_ERROR) { args ->
            isConnectionInProgress = false
            Log.e("SocketManager", "Connection error: ${args[0]}")
            reconnect()
        }.on(EVENT_ON_MESSAGE_RECEIVED) { args ->
//            handleMessage(args[0] as? JSONObject)
            askNotificationPermission(args[0] as? JSONObject)
        }.on(EVENT_IN_APP_MESSAGE_SHOW) { args ->
            Log.d("FIAM", "BNotify InAppMessenger: ${args[0].toString()}")
            val app: Application = BNotifyApp.getApplication() ?: return@on
            val response: String = args[0].toString()
            Log.d("InAppMessenger", "BNotify InAppMessenger: ${response}")
            val campaign = Gson().fromJson(response, com.convex.bnotifysdk.inappmessaging.model.CampaignModel::class.java)
            InAppMessenger.init(app, response)
            InAppMessenger.logEvent(campaign.trigger.event)
        }.on(EVENT_REGISTERED) { args ->
            val token_model = Gson().fromJson(args[0].toString(), BNotifyTokenModel::class.java)
            BNotifyApp.getTokenListener()?.onNewToken(token_model.token)
            Log.d("SocketManager", "${args.size} BNotifyToken: ${token_model.token}")
        }
    }

    private fun removeSocketListeners() {
        socket.off(Socket.EVENT_CONNECT)
        socket.off(Socket.EVENT_DISCONNECT)
        socket.off(Socket.EVENT_CONNECT_ERROR)
        socket.off(EVENT_ON_MESSAGE_RECEIVED)
        socket.off(EVENT_REGISTERED)
    }

    private fun handleMessage(data: JSONObject?) {
        Log.d("SocketManager", "MESSAGE: ${data}")
        Log.d("NOTIFY_MESSAGE_DATA", "SOCKET MESSAGE: ${data}")
        data?.let {
            mainHandler.post {
                // Handle your message here
                val model: NotificationModel = Gson().fromJson(data.toString(), NotificationModel::class.java)
                NotificationsManager.init(appContext)
                if(BNotifyApp.getIsAutoGeneratedNotification(appContext)){
                    NotificationsManager.handleNow(model, appContext)
                }else{
                    BNotifyApp.getNotificationListener()?.onMessageReceive(model)
                }
            }
        }
    }


    private var pendingNotificationData: JSONObject? = null
    private fun askNotificationPermission(data: JSONObject?) {
        pendingNotificationData = data

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            handleMessage(data)
            return
        }

        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            handleMessage(data)
        } else {
            // Start permission activity
            val intent = Intent(appContext, PermissionRequestActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("request_code", PERMISSION_REQUEST_CODE)
            }
            appContext.startActivity(intent)

            handleMessage(data)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            handleMessage(pendingNotificationData)
        } else {
            Log.w("SocketManager", "Notification permission denied")
        }
        pendingNotificationData = null
    }

    private fun getScreenResolution():String{
        val displayMetrics = appContext.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenResolution = "${screenWidth}x${screenHeight}"
        return screenResolution
    }

    @SuppressLint("HardwareIds")
    fun getPersistentDeviceId(context: Context): String {
        // 1️⃣ Try ANDROID_ID
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        if (!androidId.isNullOrBlank() && androidId != "unknown" && androidId != "9774d56d682e549c") {
            return androidId
        }

        // 2️⃣ Try hardware-based hash
        val hardwareId = buildHardwareId()
        if (hardwareId.isNotBlank()) {
            return hardwareId
        }

        // 3️⃣ Fallback to saved UUID in SharedPreferences
        val prefs = context.getSharedPreferences("device_id_prefs", Context.MODE_PRIVATE)
        var savedId = prefs.getString("device_id", null)
        if (savedId.isNullOrBlank()) {
            savedId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", savedId).apply()
        }
        return savedId
    }

    private fun buildHardwareId(): String {
        return try {
            val base = Build.BOARD + Build.BRAND + Build.DEVICE +
                    Build.HARDWARE + Build.MANUFACTURER + Build.MODEL + Build.PRODUCT
            md5(base)
        } catch (e: Exception) {
            ""
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getTotalRAM(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } else {
            // Fallback for older devices
            try {
                val reader = RandomAccessFile("/proc/meminfo", "r")
                val load = reader.readLine()

                // Get the total RAM value (first line)
                val p = Pattern.compile("(\\d+)")
                val m = p.matcher(load)
                var totalRam = 0L
                if (m.find()) {
                    totalRam = m.group(1)?.toLong() ?: 0L
                }
                reader.close()
                totalRam * 1024 // Convert from KB to bytes
            } catch (e: Exception) {
                0L
            }
        }
    }

    private fun getInternalStorageInfo(): Pair<Long, Long> {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)

        val blockSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.blockSizeLong
        } else {
            stat.blockSize.toLong()
        }

        val totalBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.blockCountLong
        } else {
            stat.blockCount.toLong()
        }

        val availableBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.availableBlocksLong
        } else {
            stat.availableBlocks.toLong()
        }

        val totalSize = totalBlocks * blockSize
        val availableSize = availableBlocks * blockSize

        return Pair(totalSize, availableSize)
    }

    private fun getTotalRamGB(): String{
        // Get RAM information
        val totalRam = getTotalRAM(appContext)
        val ramInGB = totalRam / (1024 * 1024 * 1024) // Convert to GB
        return  "$ramInGB GB"
    }

    private fun getTotalROMGB(): String{
        // Get ROM information
        val (totalStorage, availableStorage) = getInternalStorageInfo()
        val totalStorageGB = totalStorage / (1024 * 1024 * 1024)
        val availableStorageGB = availableStorage / (1024 * 1024 * 1024)

        return  "$totalStorageGB GB"
    }

    private fun getAppVersionInfo(context: Context): Pair<Int, String> {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            val versionName = packageInfo.versionName ?: "N/A"
            versionCode to versionName
        } catch (e: Exception) {
            e.printStackTrace()
            -1 to "Unknown"
        }
    }

    private fun isTabletMoreAccurate(context: Context): Boolean {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi
        val diagonalInches = sqrt((widthInches * widthInches) + (heightInches * heightInches))
        return diagonalInches >= 7.0 // tablets are usually 7 inches or more
    }

    private fun getDeviceType(): String{
        val deviceType = if (isTabletMoreAccurate(appContext)) "tablet" else "mobile"
        return deviceType
    }

    private fun getDeviceLanguageWithLocale(context: Context): String {
        val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return locale.toString() // returns something like "en_US"
    }

    fun getDeviceLanguage(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0).language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }
    }

    fun getIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.indexOf(':') < 0) return ip // IPv4 only
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    // ✅ Returns 2-letter country code (e.g., "PK")
    fun getDeviceCountryCode(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val simCountry = tm.simCountryIso?.uppercase(Locale.ROOT)
        if (!simCountry.isNullOrEmpty()) return simCountry

        val networkCountry = tm.networkCountryIso?.uppercase(Locale.ROOT)
        if (!networkCountry.isNullOrEmpty()) return networkCountry

        return Locale.getDefault().country.uppercase(Locale.ROOT) // fallback
    }

    // ✅ Returns full country name (e.g., "Pakistan")
    fun getDeviceCountryName(context: Context): String {
        val countryCode = getDeviceCountryCode(context)
        return Locale("", countryCode).displayCountry
    }

    fun Context.isDebugBuild(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }



    interface OnSocketConnectListener{
        fun onConnected()
    }
}
package org.openhue.streamtv.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openhue.streamtv.MainActivity
import org.openhue.streamtv.R
import org.openhue.streamtv.hue.model.HueCredentials
import org.openhue.streamtv.hue.repository.HueRepository
import org.openhue.streamtv.hue.stream.HueStreamManager
import org.openhue.streamtv.hue.stream.StreamState
import org.openhue.streamtv.whitelist.WhitelistManager
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCaptureService"
    private val NOTIFICATION_ID = 4201
    private val CHANNEL_ID = "hue_stream_capture_channel"

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var repository: HueRepository
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var streamManager: HueStreamManager
    private val colorExtractor = ColorExtractor()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var lightTargets: List<LightTarget> = emptyList()
    private var reusableBitmap: Bitmap? = null

    // Downscale resolution: 160x90 maintains 16:9 aspect ratio with negligible CPU usage on TV
    private val CAPTURE_WIDTH = 160
    private val CAPTURE_HEIGHT = 90
    private val compactBuffer = ByteBuffer.allocateDirect(CAPTURE_WIDTH * CAPTURE_HEIGHT * 4)
    private val rowByteArray = ByteArray(CAPTURE_WIDTH * 4)
    private var frameCounter = 0L

    companion object {
        const val ACTION_START = "org.openhue.streamtv.capture.ACTION_START"
        const val ACTION_STOP = "org.openhue.streamtv.capture.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        @Volatile
        var activeInstance: ScreenCaptureService? = null

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isPausedByWhitelist = MutableStateFlow(false)
        val isPausedByWhitelist: StateFlow<Boolean> = _isPausedByWhitelist.asStateFlow()

        var currentDynamics = SyncDynamics.BALANCED
        var currentBrightnessMultiplier = 1.0f

        fun notifyForegroundPackage(packageName: String, isWhitelisted: Boolean) {
            val service = activeInstance ?: return
            service.handleForegroundPackageChange(packageName, isWhitelisted)
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        repository = HueRepository(this)
        whitelistManager = WhitelistManager(this)
        streamManager = HueStreamManager(repository)

        val dynName = repository.getDynamicsName()
        currentDynamics = try { SyncDynamics.valueOf(dynName) } catch (_: Exception) { SyncDynamics.BALANCED }
        currentBrightnessMultiplier = repository.getBrightnessPercent() / 100f

        handlerThread = HandlerThread("HueFrameCapture").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (resultCode != 0 && data != null) {
                    startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_syncing)))
                    _isRunning.value = true
                    startStreaming(resultCode, data)
                }
            }
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startStreaming(resultCode: Int, data: Intent) {
        serviceScope.launch {
            val creds = repository.getSavedCredentials()
            val groupId = repository.getSelectedGroupId()

            if (creds == null || groupId == null) {
                Log.e(TAG, "Missing credentials or selected group ID")
                stopStreaming()
                return@launch
            }

            // Fetch group locations to map 3D bulb positions to screen coordinates
            val groups = repository.getEntertainmentGroups(creds)
            val activeGroup = groups.firstOrNull { it.id == groupId }
            if (activeGroup != null) {
                val targets = ColorExtractor.mapLocationsToTargets(activeGroup.locations).toMutableList()
                val targetIds = targets.map { it.lightId.toString() }.toSet()
                val missingLights = activeGroup.lightIds.filter { !targetIds.contains(it) }
                if (missingLights.isNotEmpty()) {
                    val total = activeGroup.lightIds.size
                    for (missing in missingLights) {
                        val id = missing.toIntOrNull() ?: continue
                        val idx = activeGroup.lightIds.indexOf(missing)
                        val normX = if (total <= 1) 0.5f else (0.1f + idx * (0.8f / (total - 1))).coerceIn(0.05f, 0.95f)
                        targets.add(LightTarget(lightId = id, normX = normX, normY = 0.5f))
                    }
                }
                lightTargets = targets
                Log.i(TAG, "Configured ${lightTargets.size} light target coordinates for group ${activeGroup.name}: ${lightTargets.map { "${it.lightId}@(${it.normX},${it.normY})" }}")
            }

            // Check if we should start in paused standby state (if auto-release enabled and current app not whitelisted)
            val currentPkg = packageName
            val isWhitelisted = whitelistManager.isPackageWhitelisted(currentPkg)
            val hasWhitelistFilter = whitelistManager.getWhitelistedPackages().isNotEmpty()
            val shouldStartPaused = repository.isAutoReleaseEnabled() && hasWhitelistFilter && !isWhitelisted

            if (shouldStartPaused) {
                Log.i(TAG, "Starting capture service in Standby mode (waiting for whitelisted app, current=$currentPkg)")
                _isPausedByWhitelist.value = true
                updateNotification(getString(R.string.notification_paused))
            } else {
                _isPausedByWhitelist.value = false
                // Connect DTLS Hue Stream
                val connected = streamManager.startStream(creds, groupId)
                if (!connected) {
                    Log.e(TAG, "Failed to connect DTLS stream")
                    stopStreaming()
                    return@launch
                }
            }

            // Initialize MediaProjection & VirtualDisplay
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpManager.getMediaProjection(resultCode, data) ?: run {
                Log.e(TAG, "Failed to obtain MediaProjection")
                stopStreaming()
                return@launch
            }
            mediaProjection = projection

            setupVirtualDisplay(projection)
            startForegroundAppMonitor()
        }
    }

    private fun setupVirtualDisplay(projection: MediaProjection) {
        imageReader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, PixelFormat.RGBA_8888, 2)
        reusableBitmap = Bitmap.createBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888)

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (_isPausedByWhitelist.value) {
                    return@setOnImageAvailableListener
                }
                if (frameCounter < 3L) {
                    Log.i(TAG, "Frame acquired: ${image.width}x${image.height}, rowStride=${image.planes[0].rowStride}")
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride

                val bitmap = reusableBitmap ?: return@setOnImageAvailableListener
                
                // Copy frame pixels into bitmap
                if (rowStride == CAPTURE_WIDTH * pixelStride) {
                    bitmap.copyPixelsFromBuffer(buffer)
                } else {
                    compactBuffer.clear()
                    buffer.clear()
                    val rowBytes = CAPTURE_WIDTH * pixelStride
                    for (row in 0 until CAPTURE_HEIGHT) {
                        buffer.position(row * rowStride)
                        buffer.get(rowByteArray, 0, rowBytes)
                        compactBuffer.put(rowByteArray, 0, rowBytes)
                    }
                    compactBuffer.flip()
                    bitmap.copyPixelsFromBuffer(compactBuffer)
                }

                // Sample colors and send to Hue Bridge
                val colors = colorExtractor.extractColors(
                    bitmap = bitmap,
                    targets = lightTargets,
                    dynamics = currentDynamics,
                    brightnessMultiplier = currentBrightnessMultiplier
                )

                if (colors.isNotEmpty()) {
                    streamManager.sendFrame(colors)
                    frameCounter++
                    if (frameCounter <= 5L || frameCounter % 60 == 0L) {
                        Log.i(TAG, "Streaming active: dispatched frame #$frameCounter (${colors.size} lights: ${colors.map { "${it.lightId}=rgb(${it.red},${it.green},${it.blue})" }})")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Frame processing error: ${e.message}")
            } finally {
                image.close()
            }
        }, backgroundHandler)

        val dpi = resources.displayMetrics.densityDpi.coerceAtLeast(160)
        virtualDisplay = projection.createVirtualDisplay(
            "HueStreamCapture",
            CAPTURE_WIDTH,
            CAPTURE_HEIGHT,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
    }

    fun handleForegroundPackageChange(packageName: String, isWhitelisted: Boolean) {
        if (!_isRunning.value) return
        if (!repository.isAutoReleaseEnabled()) return

        if (!isWhitelisted) {
            if (!_isPausedByWhitelist.value) {
                Log.i(TAG, "Auto-releasing Hue lights: app [$packageName] is not whitelisted")
                _isPausedByWhitelist.value = true
                serviceScope.launch {
                    streamManager.pauseStream(releaseLights = true)
                    updateNotification(getString(R.string.notification_paused))
                }
            }
        } else {
            if (_isPausedByWhitelist.value) {
                Log.i(TAG, "Auto-resuming Hue lights: app [$packageName] is whitelisted")
                _isPausedByWhitelist.value = false
                serviceScope.launch {
                    streamManager.resumeStream()
                    updateNotification(getString(R.string.notification_syncing))
                }
            }
        }
    }

    private var appMonitorJob: Job? = null

    private fun startForegroundAppMonitor() {
        appMonitorJob?.cancel()
        appMonitorJob = serviceScope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            var lastPackage = ""
            while (isActive) {
                delay(800)
                if (!repository.isAutoReleaseEnabled()) {
                    if (_isPausedByWhitelist.value) {
                        _isPausedByWhitelist.value = false
                        serviceScope.launch {
                            streamManager.resumeStream()
                            updateNotification(getString(R.string.notification_syncing))
                        }
                    }
                    continue
                }

                try {
                    val endTime = System.currentTimeMillis()
                    val beginTime = endTime - 2500
                    val events = usageStatsManager?.queryEvents(beginTime, endTime)
                    var currentApp: String? = null
                    if (events != null) {
                        val event = UsageEvents.Event()
                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                                currentApp = event.packageName
                            }
                        }
                    }
                    if (currentApp != null && currentApp.isNotBlank() && currentApp != lastPackage) {
                        lastPackage = currentApp
                        val isWhitelisted = whitelistManager.isPackageWhitelisted(currentApp)
                        handleForegroundPackageChange(currentApp, isWhitelisted)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Foreground app monitor error: ${e.message}")
                }
            }
        }
    }

    private fun stopStreaming() {
        appMonitorJob?.cancel()
        appMonitorJob = null
        _isRunning.value = false
        _isPausedByWhitelist.value = false

        serviceScope.launch {
            streamManager.stopStream()
        }

        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up projection: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        activeInstance = null
        serviceScope.cancel()
        handlerThread?.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

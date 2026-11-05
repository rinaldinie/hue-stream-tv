package org.openhue.streamtv.whitelist

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.openhue.streamtv.capture.ScreenCaptureService

class HueAccessibilityService : AccessibilityService() {
    private val TAG = "HueAccessibilityService"
    private lateinit var whitelistManager: WhitelistManager

    companion object {
        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${HueAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun setAccessibilityServiceEnabled(context: Context, enabled: Boolean): Boolean {
            val expectedServiceName = "${context.packageName}/${HueAccessibilityService::class.java.name}"
            val cr = context.contentResolver
            return try {
                val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val services = current.split(":").filter { it.isNotBlank() }.toMutableSet()
                if (enabled) {
                    services.add(expectedServiceName)
                } else {
                    services.remove(expectedServiceName)
                }
                val newSetting = services.joinToString(":")
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newSetting)
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                true
            } catch (e: Exception) {
                Log.e("HueAccessibilityService", "Failed to update enabled_accessibility_services", e)
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        whitelistManager = WhitelistManager(this)
        Log.i(TAG, "HueAccessibilityService created and ready to monitor foreground apps")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        
        // Skip system popups / keyboards without a valid package
        if (packageName.isBlank()) return

        val isWhitelisted = whitelistManager.isPackageWhitelisted(packageName)
        Log.d(TAG, "Foreground window change: package=$packageName, isWhitelisted=$isWhitelisted")

        // Notify ScreenCaptureService to auto-pause or auto-resume
        ScreenCaptureService.notifyForegroundPackage(packageName, isWhitelisted)
    }

    override fun onInterrupt() {
        Log.w(TAG, "HueAccessibilityService interrupted")
    }
}

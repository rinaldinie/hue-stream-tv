package org.openhue.streamtv.whitelist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val isWhitelisted: Boolean = false
)

class WhitelistManager(private val context: Context) {
    private val TAG = "WhitelistManager"
    private val PREFS_NAME = "hue_stream_tv_whitelist_prefs"
    private val KEY_WHITELISTED_PACKAGES = "whitelisted_packages"
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        // System and launcher packages that should never trigger Hue entertainment sync
        val SYSTEM_IGNORED_PACKAGES = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.apps.tv.launcherx",
            "com.nvidia.shield.remote.server",
            "com.android.tv.settings",
            "com.google.android.katniss",
            "com.android.systemui",
            "org.openhue.streamtv"
        )
    }

    fun getWhitelistedPackages(): Set<String> {
        return prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setPackageWhitelisted(packageName: String, whitelisted: Boolean) {
        val current = getWhitelistedPackages().toMutableSet()
        if (whitelisted) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_WHITELISTED_PACKAGES, current).apply()
    }

    fun isPackageWhitelisted(packageName: String): Boolean {
        if (SYSTEM_IGNORED_PACKAGES.contains(packageName)) {
            return false
        }
        val list = getWhitelistedPackages()
        // If user hasn't configured any whitelist yet, by default allow all non-system apps
        if (list.isEmpty()) {
            return packageName != context.packageName
        }
        return list.contains(packageName)
    }

    /**
     * Retrieves all installed launchable TV and standard applications on the device.
     */
    fun getInstalledLaunchableApps(): List<AppItem> {
        val pm = context.packageManager
        val installedApps = mutableMapOf<String, AppItem>()
        val whitelistedSet = getWhitelistedPackages()

        // Leanback launcher apps
        val tvIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val tvResolveInfos = pm.queryIntentActivities(tvIntent, PackageManager.MATCH_ALL)
        for (info in tvResolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && !SYSTEM_IGNORED_PACKAGES.contains(pkg)) {
                val label = info.loadLabel(pm)?.toString() ?: pkg
                val icon = info.loadIcon(pm)
                installedApps[pkg] = AppItem(
                    packageName = pkg,
                    label = label,
                    icon = icon,
                    isWhitelisted = whitelistedSet.contains(pkg)
                )
            }
        }

        // Sideloaded / standard launcher apps
        val stdIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val stdResolveInfos = pm.queryIntentActivities(stdIntent, PackageManager.MATCH_ALL)
        for (info in stdResolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && !SYSTEM_IGNORED_PACKAGES.contains(pkg) && !installedApps.containsKey(pkg)) {
                val label = info.loadLabel(pm)?.toString() ?: pkg
                val icon = info.loadIcon(pm)
                installedApps[pkg] = AppItem(
                    packageName = pkg,
                    label = label,
                    icon = icon,
                    isWhitelisted = whitelistedSet.contains(pkg)
                )
            }
        }

        return installedApps.values.sortedBy { it.label.lowercase() }
    }
}

package com.g7r.ailauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony

/** Loads launchable apps via PackageManager and launches / inspects them. */
class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    @Suppress("DEPRECATION")
    fun loadApps(overrides: Map<String, String>): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val myPkg = appContext.packageName
        val apps = ArrayList<AppInfo>(resolveInfos.size)
        for (ri in resolveInfos) {
            val ai = ri.activityInfo ?: continue
            val appInfo: ApplicationInfo = ai.applicationInfo ?: continue
            val pkg = ai.packageName
            if (pkg == myPkg) continue // don't list ourselves
            val label = ri.loadLabel(pm).toString().ifBlank { pkg }
            val icon = ri.loadIcon(pm) ?: pm.defaultActivityIcon
            val firstInstall = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val category = Categorizer.categorize(appInfo, pkg, overrides[pkg])
            apps.add(
                AppInfo(
                    packageName = pkg,
                    activityName = ai.name,
                    label = label,
                    icon = icon,
                    category = category,
                    firstInstallTime = firstInstall,
                    isSystem = isSystem
                )
            )
        }
        return apps.sortedBy { it.label.lowercase() }
    }

    fun launch(app: AppInfo) {
        val intent = (pm.getLaunchIntentForPackage(app.packageName) ?: Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
        }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    /** Resolve the default packages for the dock quick-launch slots (phone / sms / camera / browser). */
    @Suppress("DEPRECATION")
    fun defaultDockPackages(): List<String> {
        val phone = resolve(Intent(Intent.ACTION_DIAL))
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(appContext) }.getOrNull()
            ?: resolve(Intent(Intent.ACTION_VIEW, Uri.parse("sms:")))
        val camera = resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ?: resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        val browser = resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        return listOfNotNull(phone, sms, camera, browser).distinct()
    }

    @Suppress("DEPRECATION")
    private fun resolve(intent: Intent): String? {
        val ri = pm.resolveActivity(intent, 0) ?: return null
        val pkg = ri.activityInfo?.packageName ?: return null
        return if (pkg == "android" || pkg.contains("resolver")) null else pkg
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }
}

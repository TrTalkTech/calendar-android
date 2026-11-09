package com.example.calendar

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun appVersionName(context: Context): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName?.takeIf { it.isNotBlank() } ?: "1.04"
}

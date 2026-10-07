package com.vita.healthtracker.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * 取「大致位置」用的极简封装: 只读系统已有的最后已知位置 (不主动定位、不引入 Google Play 服务),
 * 用于天气自动获取。没有权限或拿不到位置时返回 null。
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 返回 (纬度, 经度); 取各 provider 里时间最新的一条最后已知位置。 */
    fun lastKnown(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val best = providers
            .mapNotNull { provider ->
                try {
                    lm.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    // 权限可能在检查后被撤回。
                    null
                } catch (_: IllegalArgumentException) {
                    // 某些设备没有 GPS 或网络位置提供者。
                    null
                }
            }
            .maxByOrNull { it.time }
            ?: return null
        return best.latitude to best.longitude
    }
}

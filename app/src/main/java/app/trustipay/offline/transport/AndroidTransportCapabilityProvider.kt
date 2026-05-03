package app.trustipay.offline.transport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.trustipay.offline.OfflineFeatureFlags
import app.trustipay.offline.domain.TransportType

data class TransportCapability(
    val type: TransportType,
    val enabledByFlag: Boolean,
    val availableOnDevice: Boolean,
    val requiredPermissions: List<String>,
) {
    val available: Boolean = enabledByFlag && availableOnDevice
}

class AndroidTransportCapabilityProvider(
    private val context: Context,
    private val flags: OfflineFeatureFlags,
) {
    fun capabilities(): List<TransportCapability> = TransportType.entries.map { type ->
        TransportCapability(
            type = type,
            enabledByFlag = flags.isTransportEnabled(type),
            availableOnDevice = type.isAvailableOn(context),
            requiredPermissions = type.requiredPermissions(),
        )
    }

    private fun TransportType.isAvailableOn(context: Context): Boolean {
        val pm = context.packageManager
        return when (this) {
            TransportType.QR -> pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            TransportType.BLE -> pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            TransportType.WIFI_DIRECT -> pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
            TransportType.NFC -> pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        }
    }

    private fun TransportType.requiredPermissions(): List<String> = when (this) {
        TransportType.QR -> listOf(Manifest.permission.CAMERA)
        TransportType.BLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        TransportType.WIFI_DIRECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        TransportType.NFC -> listOf(Manifest.permission.NFC)
    }
}

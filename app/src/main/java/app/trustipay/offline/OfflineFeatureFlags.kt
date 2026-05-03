package app.trustipay.offline

import app.trustipay.BuildConfig
import app.trustipay.offline.domain.TransportType

data class OfflineFeatureFlags(
    val offlinePaymentsEnabled: Boolean = BuildConfig.OFFLINE_PAYMENTS_ENABLED,
    val offlineTokenWalletEnabled: Boolean = BuildConfig.OFFLINE_TOKEN_WALLET_ENABLED,
    val offlineSyncEnabled: Boolean = BuildConfig.OFFLINE_SYNC_ENABLED,
    val transportQrEnabled: Boolean = BuildConfig.TRANSPORT_QR_ENABLED,
    val transportBleEnabled: Boolean = BuildConfig.TRANSPORT_BLE_ENABLED,
    val transportWifiDirectEnabled: Boolean = BuildConfig.TRANSPORT_WIFI_DIRECT_ENABLED,
    val transportNfcEnabled: Boolean = BuildConfig.TRANSPORT_NFC_ENABLED,
    val offlineSettlementShadowMode: Boolean = BuildConfig.OFFLINE_SETTLEMENT_SHADOW_MODE,
    val offlineSettlementLiveMode: Boolean = BuildConfig.OFFLINE_SETTLEMENT_LIVE_MODE,
) {
    fun isTransportEnabled(type: TransportType): Boolean = when (type) {
        TransportType.QR -> transportQrEnabled
        TransportType.BLE -> transportBleEnabled
        TransportType.WIFI_DIRECT -> transportWifiDirectEnabled
        TransportType.NFC -> transportNfcEnabled
    }
}

object OfflineFeatureFlagProvider {
    val current: OfflineFeatureFlags = OfflineFeatureFlags()
}

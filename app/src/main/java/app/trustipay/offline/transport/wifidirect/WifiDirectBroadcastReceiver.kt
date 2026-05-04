package app.trustipay.offline.transport.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class WifiDirectEvent {
    data class StateChanged(val isEnabled: Boolean) : WifiDirectEvent()
    object PeersChanged : WifiDirectEvent()
    data class ConnectionChanged(val isConnected: Boolean, val groupOwnerAddress: String?) : WifiDirectEvent()
}

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
) : BroadcastReceiver() {
    private val _events = MutableStateFlow<WifiDirectEvent?>(null)
    val events: StateFlow<WifiDirectEvent?> = _events.asStateFlow()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                _events.value = WifiDirectEvent.StateChanged(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                _events.value = WifiDirectEvent.PeersChanged
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                val isConnected = networkInfo?.isConnected == true
                manager.requestConnectionInfo(channel) { info ->
                    val address = if (isConnected) info?.groupOwnerAddress?.hostAddress else null
                    _events.value = WifiDirectEvent.ConnectionChanged(isConnected, address)
                }
            }
        }
    }
}

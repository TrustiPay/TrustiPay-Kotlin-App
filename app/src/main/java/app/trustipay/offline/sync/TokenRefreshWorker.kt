package app.trustipay.offline.sync

import app.trustipay.offline.data.OfflinePaymentStore
import app.trustipay.offline.domain.OfflineToken

class TokenRefreshWorker(
    private val store: OfflinePaymentStore,
) {
    fun storeRefreshedTokens(tokens: List<OfflineToken>): Int {
        tokens.forEach(store::upsertToken)
        return tokens.size
    }
}

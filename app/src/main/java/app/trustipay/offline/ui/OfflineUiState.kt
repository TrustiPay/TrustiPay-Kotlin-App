package app.trustipay.offline.ui

import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransportType

enum class QrFlowMode {
    IDLE,
    DISPLAYING,
    SCANNING,
    PROCESSING
}

enum class OtpFeedback {
    NONE,
    SUCCESS,
    ERROR
}

data class OfflineUiSnapshot(
    val balanceMinor: Long,
    val tokenCount: Int,
    val pendingSyncCount: Int,
    val lastMessage: String?,
    val tokens: List<OfflineTokenUiRow>,
    val transactions: List<OfflineTransactionUiRow>,
    val qrFlowMode: QrFlowMode = QrFlowMode.IDLE,
    val qrAmount: String? = null,
    val otpCode: String? = null,
    val processingMessage: String? = null,
    val showOtpInput: Boolean = false,
    val senderOtpCode: String? = null,
    val otpFeedback: OtpFeedback = OtpFeedback.NONE,
)

data class OfflineTokenUiRow(
    val tokenId: String,
    val amountMinor: Long,
    val expiresAt: String,
    val status: String,
)

data class OfflineTransactionUiRow(
    val transactionId: String,
    val counterparty: String,
    val amountMinor: Long,
    val currency: String,
    val state: TransactionState,
    val direction: TransactionDirection,
    val transportType: TransportType?,
    val updatedAt: String,
)

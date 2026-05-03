package app.trustipay.offline.domain

enum class TransactionDirection {
    SENT,
    RECEIVED,
}

enum class TransactionState {
    DRAFT,
    REQUEST_CREATED,
    REQUEST_SHARED,
    OFFER_CREATED,
    OFFER_SHARED,
    RECEIVER_VALIDATED,
    RECEIPT_CREATED,
    RECEIPT_SHARED,
    LOCAL_ACCEPTED_PENDING_SYNC,
    SYNC_QUEUED,
    SYNC_UPLOADED,
    SERVER_VALIDATING,
    SETTLED,
    FAILED_INVALID_SIGNATURE,
    FAILED_TOKEN_EXPIRED,
    FAILED_TOKEN_ALREADY_USED_LOCALLY,
    FAILED_AMOUNT_MISMATCH,
    FAILED_RECEIVER_MISMATCH,
    FAILED_UNSUPPORTED_CURRENCY,
    FAILED_TRANSPORT_INTERRUPTED,
    REJECTED_DOUBLE_SPEND,
    REJECTED_REVOKED_DEVICE,
    REJECTED_REVOKED_TOKEN,
    REJECTED_EXPIRED_TOKEN,
    DISPUTED,
}

val TransactionState.isTerminalFailure: Boolean
    get() = name.startsWith("FAILED_") || name.startsWith("REJECTED_") || this == TransactionState.DISPUTED

val TransactionState.canRetrySync: Boolean
    get() = this == TransactionState.SYNC_QUEUED || this == TransactionState.SYNC_UPLOADED

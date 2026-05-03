package app.trustipay.offline.domain

class TransactionStateMachine {
    fun canTransition(from: TransactionState, to: TransactionState): Boolean {
        if (from.isTerminalFailure) return false
        return to in AllowedTransitions[from].orEmpty()
    }

    fun requireTransition(from: TransactionState, to: TransactionState) {
        require(canTransition(from, to)) { "Invalid transaction state transition: $from -> $to" }
    }

    companion object {
        private val CommonFailures = setOf(
            TransactionState.FAILED_INVALID_SIGNATURE,
            TransactionState.FAILED_TOKEN_EXPIRED,
            TransactionState.FAILED_TOKEN_ALREADY_USED_LOCALLY,
            TransactionState.FAILED_AMOUNT_MISMATCH,
            TransactionState.FAILED_RECEIVER_MISMATCH,
            TransactionState.FAILED_UNSUPPORTED_CURRENCY,
            TransactionState.FAILED_TRANSPORT_INTERRUPTED,
            TransactionState.REJECTED_DOUBLE_SPEND,
            TransactionState.REJECTED_REVOKED_DEVICE,
            TransactionState.REJECTED_REVOKED_TOKEN,
            TransactionState.REJECTED_EXPIRED_TOKEN,
            TransactionState.DISPUTED,
        )

        private val AllowedTransitions: Map<TransactionState, Set<TransactionState>> = mapOf(
            TransactionState.DRAFT to setOf(TransactionState.REQUEST_CREATED) + CommonFailures,
            TransactionState.REQUEST_CREATED to setOf(TransactionState.REQUEST_SHARED, TransactionState.OFFER_CREATED) + CommonFailures,
            TransactionState.REQUEST_SHARED to setOf(TransactionState.OFFER_CREATED, TransactionState.FAILED_TRANSPORT_INTERRUPTED) + CommonFailures,
            TransactionState.OFFER_CREATED to setOf(TransactionState.OFFER_SHARED, TransactionState.RECEIVER_VALIDATED) + CommonFailures,
            TransactionState.OFFER_SHARED to setOf(TransactionState.RECEIVER_VALIDATED, TransactionState.FAILED_TRANSPORT_INTERRUPTED) + CommonFailures,
            TransactionState.RECEIVER_VALIDATED to setOf(TransactionState.RECEIPT_CREATED) + CommonFailures,
            TransactionState.RECEIPT_CREATED to setOf(TransactionState.RECEIPT_SHARED, TransactionState.LOCAL_ACCEPTED_PENDING_SYNC) + CommonFailures,
            TransactionState.RECEIPT_SHARED to setOf(TransactionState.LOCAL_ACCEPTED_PENDING_SYNC, TransactionState.FAILED_TRANSPORT_INTERRUPTED) + CommonFailures,
            TransactionState.LOCAL_ACCEPTED_PENDING_SYNC to setOf(TransactionState.SYNC_QUEUED) + CommonFailures,
            TransactionState.SYNC_QUEUED to setOf(TransactionState.SYNC_UPLOADED, TransactionState.SERVER_VALIDATING) + CommonFailures,
            TransactionState.SYNC_UPLOADED to setOf(TransactionState.SERVER_VALIDATING) + CommonFailures,
            TransactionState.SERVER_VALIDATING to setOf(TransactionState.SETTLED) + CommonFailures,
            TransactionState.SETTLED to emptySet(),
        )
    }
}

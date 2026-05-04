package app.trustipay.offline.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.trustipay.offline.domain.KnownSpentToken
import app.trustipay.offline.domain.LocalHashChainEntry
import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.OfflineTokenStatus
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SyncOperationType
import app.trustipay.offline.domain.SyncQueueItem
import app.trustipay.offline.domain.SyncQueueStatus
import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransportType
import java.time.Instant

class SQLiteOfflinePaymentStore(
    private val database: SQLiteDatabase,
) : OfflinePaymentStore {
    override fun upsertToken(token: OfflineToken) {
        database.insertWithOnConflict("offline_tokens", null, token.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun listTokens(): List<OfflineToken> =
        database.query("offline_tokens", null, null, null, null, null, "expires_at_server ASC")
            .useCursor { cursor -> generateSequence { if (cursor.moveToNext()) cursor.toOfflineToken() else null }.toList() }

    override fun updateToken(token: OfflineToken) {
        database.update("offline_tokens", token.toValues(), "token_id = ?", arrayOf(token.tokenId))
    }

    override fun upsertTransaction(transaction: OfflineTransaction) {
        database.insertWithOnConflict("offline_transactions", null, transaction.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun listTransactions(): List<OfflineTransaction> =
        database.query("offline_transactions", null, null, null, null, null, "updated_local_at DESC")
            .useCursor { cursor -> generateSequence { if (cursor.moveToNext()) cursor.toOfflineTransaction() else null }.toList() }

    override fun updateTransactionState(transactionId: String, state: TransactionState, rejectionReason: String?) {
        val values = ContentValues().apply {
            put("state", state.name)
            put("updated_local_at", Instant.now().toString())
            put("rejection_reason", rejectionReason)
        }
        database.update("offline_transactions", values, "transaction_id = ?", arrayOf(transactionId))
    }

    override fun enqueue(item: SyncQueueItem) {
        database.insertWithOnConflict("sync_queue", null, item.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun listQueue(status: SyncQueueStatus?): List<SyncQueueItem> {
        val selection = status?.let { "status = ?" }
        val args = status?.let { arrayOf(it.name) }
        return database.query("sync_queue", null, selection, args, null, null, "created_local_at ASC")
            .useCursor { cursor -> generateSequence { if (cursor.moveToNext()) cursor.toSyncQueueItem() else null }.toList() }
    }

    override fun updateQueueItem(item: SyncQueueItem) {
        database.update("sync_queue", item.toValues(), "queue_id = ?", arrayOf(item.queueId))
    }

    override fun recordKnownSpentToken(token: KnownSpentToken) {
        database.insertWithOnConflict("known_spent_tokens", null, token.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun knownSpentTokenIds(): Set<String> =
        database.query("known_spent_tokens", arrayOf("token_id"), null, null, null, null, null)
            .useCursor { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getString("token_id") else null }.toSet() }

    override fun latestLocalChainHash(deviceId: String): String? =
        database.query(
            "local_hash_chain_entries",
            arrayOf("chain_hash"),
            "device_id = ?",
            arrayOf(deviceId),
            null,
            null,
            "entry_id DESC",
            "1",
        ).useCursor { cursor -> if (cursor.moveToFirst()) cursor.getString("chain_hash") else null }

    override fun appendLocalChainEntry(entry: LocalHashChainEntry) {
        database.insertWithOnConflict(
            "local_hash_chain_entries",
            null,
            entry.toValues(),
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun OfflineToken.toValues(): ContentValues = ContentValues().apply {
        val now = Instant.now().toString()
        put("token_id", tokenId)
        put("owner_user_id", ownerUserId)
        put("owner_device_id", ownerDeviceId)
        put("amount_minor", amountMinor)
        put("currency", currency)
        put("issued_at_server", issuedAtServer.toString())
        put("expires_at_server", expiresAtServer.toString())
        put("server_key_id", issuerKeyId)
        put("server_signature", serverSignature)
        put("token_payload_canonical", canonicalPayload)
        put("status", status.name)
        put("created_local_at", now)
        put("updated_local_at", now)
    }

    private fun OfflineTransaction.toValues(): ContentValues = ContentValues().apply {
        put("transaction_id", transactionId)
        put("request_id", requestId)
        put("direction", direction.name)
        put("counterparty_alias", counterpartyAlias)
        put("amount_minor", amountMinor)
        put("currency", currency)
        put("state", state.name)
        put("transport_type", transportType?.name)
        put("request_payload", requestPayload)
        put("offer_payload", offerPayload)
        put("receipt_payload", receiptPayload)
        put("request_hash", requestHash)
        put("offer_hash", offerHash)
        put("receipt_hash", receiptHash)
        put("sender_previous_hash", senderPreviousHash)
        put("sender_chain_hash", senderChainHash)
        put("receiver_previous_hash", receiverPreviousHash)
        put("receiver_chain_hash", receiverChainHash)
        put("created_local_at", createdLocalAt.toString())
        put("updated_local_at", updatedLocalAt.toString())
        put("last_sync_attempt_at", lastSyncAttemptAt?.toString())
        put("settled_at_server", settledAtServer?.toString())
        put("rejection_reason", rejectionReason)
    }

    private fun SyncQueueItem.toValues(): ContentValues = ContentValues().apply {
        put("queue_id", queueId)
        put("transaction_id", transactionId)
        put("operation_type", operationType.name)
        put("payload", payload)
        put("attempt_count", attemptCount)
        put("next_attempt_at", nextAttemptAt?.toString())
        put("status", status.name)
        put("created_local_at", createdLocalAt.toString())
        put("updated_local_at", updatedLocalAt.toString())
    }

    private fun KnownSpentToken.toValues(): ContentValues = ContentValues().apply {
        put("token_id", tokenId)
        put("transaction_id", transactionId)
        put("seen_at_local", seenAtLocal.toString())
        put("source", source)
    }

    private fun LocalHashChainEntry.toValues(): ContentValues = ContentValues().apply {
        put("device_id", deviceId)
        put("transaction_id", transactionId)
        put("previous_hash", previousHash)
        put("chain_hash", chainHash)
        put("created_local_at", createdLocalAt.toString())
    }

    private fun Cursor.toOfflineToken(): OfflineToken = OfflineToken(
        tokenId = getString("token_id"),
        ownerUserId = getString("owner_user_id"),
        ownerDeviceId = getString("owner_device_id"),
        amountMinor = getLong("amount_minor"),
        currency = getString("currency"),
        issuedAtServer = Instant.parse(getString("issued_at_server")),
        expiresAtServer = Instant.parse(getString("expires_at_server")),
        issuerKeyId = getString("server_key_id"),
        nonce = "stored",
        serverSignature = getString("server_signature"),
        canonicalPayload = getBlob(getColumnIndexOrThrow("token_payload_canonical")),
        status = OfflineTokenStatus.valueOf(getString("status")),
    )

    private fun Cursor.toOfflineTransaction(): OfflineTransaction = OfflineTransaction(
        transactionId = getString("transaction_id"),
        requestId = getNullableString("request_id"),
        direction = TransactionDirection.valueOf(getString("direction")),
        counterpartyAlias = getNullableString("counterparty_alias"),
        amountMinor = getLong("amount_minor"),
        currency = getString("currency"),
        state = TransactionState.valueOf(getString("state")),
        transportType = getNullableString("transport_type")?.let(TransportType::valueOf),
        requestPayload = getNullableBlob("request_payload"),
        offerPayload = getNullableBlob("offer_payload"),
        receiptPayload = getNullableBlob("receipt_payload"),
        requestHash = getNullableString("request_hash"),
        offerHash = getNullableString("offer_hash"),
        receiptHash = getNullableString("receipt_hash"),
        senderPreviousHash = getNullableString("sender_previous_hash"),
        senderChainHash = getNullableString("sender_chain_hash"),
        receiverPreviousHash = getNullableString("receiver_previous_hash"),
        receiverChainHash = getNullableString("receiver_chain_hash"),
        createdLocalAt = Instant.parse(getString("created_local_at")),
        updatedLocalAt = Instant.parse(getString("updated_local_at")),
        lastSyncAttemptAt = getNullableString("last_sync_attempt_at")?.let(Instant::parse),
        settledAtServer = getNullableString("settled_at_server")?.let(Instant::parse),
        rejectionReason = getNullableString("rejection_reason"),
    )

    private fun Cursor.toSyncQueueItem(): SyncQueueItem = SyncQueueItem(
        queueId = getString("queue_id"),
        transactionId = getString("transaction_id"),
        operationType = SyncOperationType.valueOf(getString("operation_type")),
        payload = getBlob(getColumnIndexOrThrow("payload")),
        attemptCount = getInt(getColumnIndexOrThrow("attempt_count")),
        nextAttemptAt = getNullableString("next_attempt_at")?.let(Instant::parse),
        status = SyncQueueStatus.valueOf(getString("status")),
        createdLocalAt = Instant.parse(getString("created_local_at")),
        updatedLocalAt = Instant.parse(getString("updated_local_at")),
    )

    private fun Cursor.getString(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.getLong(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.getNullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getNullableBlob(column: String): ByteArray? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getBlob(index)
    }
}

private inline fun <T> Cursor.useCursor(block: (Cursor) -> T): T = use(block)

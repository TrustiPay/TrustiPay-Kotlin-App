package app.trustipay.offline.data

object OfflinePaymentSchema {
    const val DatabaseName = "trustipay_offline_payments.db"
    const val Version = 1

    val CreateStatements = listOf(
        """
        CREATE TABLE IF NOT EXISTS offline_tokens (
          token_id TEXT PRIMARY KEY,
          owner_user_id TEXT NOT NULL,
          owner_device_id TEXT NOT NULL,
          amount_minor INTEGER NOT NULL,
          currency TEXT NOT NULL,
          issued_at_server TEXT NOT NULL,
          expires_at_server TEXT NOT NULL,
          server_key_id TEXT NOT NULL,
          server_signature TEXT NOT NULL,
          token_payload_canonical BLOB NOT NULL,
          status TEXT NOT NULL,
          created_local_at TEXT NOT NULL,
          updated_local_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_offline_tokens_status ON offline_tokens(status)",
        "CREATE INDEX IF NOT EXISTS idx_offline_tokens_expires_at ON offline_tokens(expires_at_server)",
        """
        CREATE TABLE IF NOT EXISTS offline_transactions (
          transaction_id TEXT PRIMARY KEY,
          request_id TEXT,
          direction TEXT NOT NULL,
          counterparty_alias TEXT,
          amount_minor INTEGER NOT NULL,
          currency TEXT NOT NULL,
          state TEXT NOT NULL,
          transport_type TEXT,
          request_payload BLOB,
          offer_payload BLOB,
          receipt_payload BLOB,
          request_hash TEXT,
          offer_hash TEXT,
          receipt_hash TEXT,
          created_local_at TEXT NOT NULL,
          updated_local_at TEXT NOT NULL,
          last_sync_attempt_at TEXT,
          settled_at_server TEXT,
          rejection_reason TEXT
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_offline_transactions_state ON offline_transactions(state)",
        "CREATE INDEX IF NOT EXISTS idx_offline_transactions_updated ON offline_transactions(updated_local_at)",
        """
        CREATE TABLE IF NOT EXISTS sync_queue (
          queue_id TEXT PRIMARY KEY,
          transaction_id TEXT NOT NULL,
          operation_type TEXT NOT NULL,
          payload BLOB NOT NULL,
          attempt_count INTEGER NOT NULL DEFAULT 0,
          next_attempt_at TEXT,
          status TEXT NOT NULL,
          created_local_at TEXT NOT NULL,
          updated_local_at TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS idx_sync_queue_status_next_attempt ON sync_queue(status, next_attempt_at)",
        """
        CREATE TABLE IF NOT EXISTS known_spent_tokens (
          token_id TEXT PRIMARY KEY,
          transaction_id TEXT NOT NULL,
          seen_at_local TEXT NOT NULL,
          source TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS sync_state (
          key TEXT PRIMARY KEY,
          value TEXT NOT NULL,
          updated_local_at TEXT NOT NULL
        )
        """.trimIndent(),
    )
}

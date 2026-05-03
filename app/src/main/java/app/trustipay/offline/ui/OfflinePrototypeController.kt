package app.trustipay.offline.ui

import app.trustipay.offline.OfflineFeatureFlags
import app.trustipay.offline.data.InMemoryOfflinePaymentStore
import app.trustipay.offline.domain.KnownSpentToken
import app.trustipay.offline.domain.Money
import app.trustipay.offline.domain.OfflineIdGenerator
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.protocol.ChunkingService
import app.trustipay.offline.protocol.JavaSigningKeyFactory
import app.trustipay.offline.protocol.MessageHasher
import app.trustipay.offline.protocol.OfflineTokenFactory
import app.trustipay.offline.protocol.OfflineTokenWallet
import app.trustipay.offline.protocol.PaymentProtocolEngine
import app.trustipay.offline.protocol.PublicKeySignatureVerifier
import app.trustipay.offline.protocol.PublicKeyTokenIssuerVerifier
import app.trustipay.offline.protocol.TokenSelectionResult
import app.trustipay.offline.protocol.TokenValidator
import app.trustipay.offline.protocol.canonicalBytes
import app.trustipay.offline.sync.SyncRepository
import java.time.Clock
import java.time.Duration

class OfflinePrototypeController private constructor(
    private val flags: OfflineFeatureFlags,
    private val store: InMemoryOfflinePaymentStore,
    private val wallet: OfflineTokenWallet,
    private val engine: PaymentProtocolEngine,
    private val verifier: PublicKeySignatureVerifier,
    private val tokenValidator: TokenValidator,
    private val syncRepository: SyncRepository,
    private val keys: DemoKeys,
    private val clock: Clock,
    private val idGenerator: OfflineIdGenerator,
) {
    fun snapshot(lastMessage: String? = null): OfflineUiSnapshot {
        val now = clock.instant()
        val pending = store.listTransactions().count {
            it.state in PendingStates
        }
        return OfflineUiSnapshot(
            balanceMinor = wallet.spendableBalance("LKR", now),
            tokenCount = wallet.allTokens().count { it.isSpendableAt(now) },
            pendingSyncCount = pending,
            lastMessage = lastMessage,
            tokens = wallet.allTokens().map {
                OfflineTokenUiRow(
                    tokenId = it.tokenId.takeLast(8),
                    amountMinor = it.amountMinor,
                    expiresAt = it.expiresAtServer.toString().take(10),
                    status = it.status.name,
                )
            },
            transactions = store.listTransactions().map {
                OfflineTransactionUiRow(
                    transactionId = it.transactionId.takeLast(8),
                    counterparty = it.counterpartyAlias.orEmpty(),
                    amountMinor = it.amountMinor,
                    currency = it.currency,
                    state = it.state,
                    direction = it.direction,
                    transportType = it.transportType,
                    updatedAt = it.updatedLocalAt.toString().take(19),
                )
            },
        )
    }

    fun runQrPayment(amountText: String, receiverAlias: String, description: String): OfflineUiSnapshot {
        if (!flags.offlinePaymentsEnabled || !flags.transportQrEnabled) {
            return snapshot("Offline QR payments are disabled by feature flag.")
        }
        val money = Money.fromDecimalText(amountText) ?: return snapshot("Enter a valid LKR amount.")
        val now = clock.instant()
        wallet.expireOldTokens(now)

        val request = engine.createPaymentRequest(
            receiverUserAlias = receiverAlias.ifBlank { "Offline receiver" },
            receiverDeviceId = ReceiverDeviceId,
            receiverPublicKeyId = keys.receiver.publicKeyId,
            money = money,
            description = description,
            supportedTransports = listOf(TransportType.QR, TransportType.BLE, TransportType.WIFI_DIRECT, TransportType.NFC),
            signer = keys.receiver.signer(),
            validFor = Duration.ofMinutes(5),
        )
        engine.validatePaymentRequest(request, verifier).also {
            if (!it.accepted) return snapshot(it.reason)
        }

        val selected = wallet.selectExactTokens(money, now)
        if (selected !is TokenSelectionResult.Selected) {
            return snapshot("No exact offline token match for LKR ${money.displayAmount()}. Sync online for fresh denominations.")
        }

        return runCatching {
            wallet.reserveTokens(selected.tokens.map { it.tokenId })
            val offer = engine.createPaymentOffer(
                request = request,
                senderUserAlias = "Saman",
                senderDeviceId = SenderDeviceId,
                senderPublicKeyId = keys.sender.publicKeyId,
                selectedTokens = selected.tokens,
                signer = keys.sender.signer(),
            )
            val offerValidation = engine.validatePaymentOffer(
                offer = offer,
                request = request,
                verifier = verifier,
                tokenValidator = tokenValidator,
                locallySpentTokenIds = store.knownSpentTokenIds(),
            )
            if (!offerValidation.accepted) {
                wallet.releaseReservations(selected.tokens.map { it.tokenId })
                return snapshot(offerValidation.reason)
            }

            val receipt = engine.createPaymentReceipt(
                request = request,
                offer = offer,
                receiverDeviceId = ReceiverDeviceId,
                signer = keys.receiver.signer(),
            )
            val receiptValidation = engine.validatePaymentReceipt(receipt, request, offer, verifier)
            if (!receiptValidation.accepted) {
                wallet.releaseReservations(selected.tokens.map { it.tokenId })
                return snapshot(receiptValidation.reason)
            }

            wallet.markSpentPendingSync(selected.tokens.map { it.tokenId })
            wallet.allTokens().forEach(store::upsertToken)
            selected.tokens.forEach { token ->
                store.recordKnownSpentToken(
                    KnownSpentToken(
                        tokenId = token.tokenId,
                        transactionId = offer.transactionId,
                        seenAtLocal = now,
                        source = "QR",
                    )
                )
            }

            val requestPayload = request.canonicalBytes(includeSignature = true)
            val offerPayload = offer.canonicalBytes(includeSignature = true)
            val receiptPayload = receipt.canonicalBytes(includeSignature = true)
            val transaction = OfflineTransaction(
                transactionId = offer.transactionId,
                requestId = request.requestId,
                direction = TransactionDirection.SENT,
                counterpartyAlias = request.receiverUserAlias,
                amountMinor = offer.amountMinor,
                currency = offer.currency,
                state = TransactionState.LOCAL_ACCEPTED_PENDING_SYNC,
                transportType = TransportType.QR,
                requestPayload = requestPayload,
                offerPayload = offerPayload,
                receiptPayload = receiptPayload,
                requestHash = MessageHasher.sha256Base64Url(requestPayload),
                offerHash = MessageHasher.sha256Base64Url(offerPayload),
                receiptHash = MessageHasher.sha256Base64Url(receiptPayload),
                createdLocalAt = now,
                updatedLocalAt = now,
            )
            syncRepository.queueAcceptedTransaction(transaction)

            val receiptChunks = ChunkingService(maxPayloadBytes = 180).chunk(
                transportSessionId = idGenerator.newId("session"),
                messageId = idGenerator.newId("msg"),
                messageType = receipt.messageType,
                payload = receiptPayload,
            )
            snapshot("Accepted offline - pending sync. QR receipt payload: ${receiptChunks.size} chunk(s).")
        }.getOrElse { error ->
            wallet.releaseReservations(selected.tokens.map { it.tokenId })
            snapshot(error.message ?: "Offline payment failed.")
        }
    }

    fun createReceiveRequest(amountText: String, description: String): OfflineUiSnapshot {
        val money = Money.fromDecimalText(amountText) ?: return snapshot("Enter a valid LKR amount.")
        val request = engine.createPaymentRequest(
            receiverUserAlias = "TrustiPay receiver",
            receiverDeviceId = ReceiverDeviceId,
            receiverPublicKeyId = keys.receiver.publicKeyId,
            money = money,
            description = description,
            supportedTransports = listOf(TransportType.QR, TransportType.BLE, TransportType.WIFI_DIRECT, TransportType.NFC),
            signer = keys.receiver.signer(),
            validFor = Duration.ofMinutes(5),
        )
        val requestPayload = request.canonicalBytes(includeSignature = true)
        val chunks = ChunkingService(maxPayloadBytes = 180).chunk(
            transportSessionId = idGenerator.newId("session"),
            messageId = idGenerator.newId("msg"),
            messageType = request.messageType,
            payload = requestPayload,
        )
        return snapshot("Payment request ready. QR payload: ${chunks.size} chunk(s), hash ${MessageHasher.sha256Base64Url(requestPayload).take(12)}.")
    }

    fun syncNow(): OfflineUiSnapshot {
        val summary = syncRepository.processShadowSync()
        return snapshot(summary.message)
    }

    companion object {
        private const val SenderDeviceId = "device_sender_demo"
        private const val ReceiverDeviceId = "device_receiver_demo"

        private val PendingStates = setOf(
            TransactionState.LOCAL_ACCEPTED_PENDING_SYNC,
            TransactionState.SYNC_QUEUED,
            TransactionState.SYNC_UPLOADED,
            TransactionState.SERVER_VALIDATING,
        )

        fun create(flags: OfflineFeatureFlags): OfflinePrototypeController {
            val clock = Clock.systemUTC()
            val idGenerator = SecureOfflineIdGenerator()
            val store = InMemoryOfflinePaymentStore()
            val issuer = JavaSigningKeyFactory.generate("issuer-demo-001")
            val sender = JavaSigningKeyFactory.generate("sender-demo-001")
            val receiver = JavaSigningKeyFactory.generate("receiver-demo-001")
            val publicKeys = mapOf(
                issuer.publicKeyId to issuer.keyPair.public,
                sender.publicKeyId to sender.keyPair.public,
                receiver.publicKeyId to receiver.keyPair.public,
            )
            val verifier = PublicKeySignatureVerifier(publicKeys)
            val tokenFactory = OfflineTokenFactory(idGenerator)
            val now = clock.instant()
            val tokens = listOf(100000L, 50000L, 20000L, 10000L, 10000L, 5000L, 2000L, 1000L).map { amount ->
                tokenFactory.issueToken(
                    ownerUserId = "user_saman_demo",
                    ownerDeviceId = SenderDeviceId,
                    amountMinor = amount,
                    currency = "LKR",
                    issuedAtServer = now.minus(Duration.ofHours(1)),
                    expiresAtServer = now.plus(Duration.ofDays(5)),
                    issuerKeyId = issuer.publicKeyId,
                    issuerSigner = issuer.signer(),
                )
            }
            tokens.forEach(store::upsertToken)

            val wallet = OfflineTokenWallet(tokens)
            val syncRepository = SyncRepository(store, flags, idGenerator, clock)
            return OfflinePrototypeController(
                flags = flags,
                store = store,
                wallet = wallet,
                engine = PaymentProtocolEngine(clock, idGenerator),
                verifier = verifier,
                tokenValidator = TokenValidator(PublicKeyTokenIssuerVerifier(verifier), clock),
                syncRepository = syncRepository,
                keys = DemoKeys(issuer, sender, receiver),
                clock = clock,
                idGenerator = idGenerator,
            )
        }
    }
}

private data class DemoKeys(
    val issuer: app.trustipay.offline.protocol.GeneratedSigningKey,
    val sender: app.trustipay.offline.protocol.GeneratedSigningKey,
    val receiver: app.trustipay.offline.protocol.GeneratedSigningKey,
)

data class OfflineUiSnapshot(
    val balanceMinor: Long,
    val tokenCount: Int,
    val pendingSyncCount: Int,
    val lastMessage: String?,
    val tokens: List<OfflineTokenUiRow>,
    val transactions: List<OfflineTransactionUiRow>,
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

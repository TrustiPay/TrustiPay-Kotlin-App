package app.trustipay.offline.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trustipay.AppContainer
import app.trustipay.BuildConfig
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.data.OfflinePaymentOpenHelper
import app.trustipay.offline.data.SQLiteOfflinePaymentStore
import app.trustipay.offline.domain.KnownSpentToken
import app.trustipay.offline.domain.Money
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.protocol.CacheBackedSignatureVerifier
import app.trustipay.offline.protocol.JavaSigningKeyFactory
import app.trustipay.offline.protocol.LocalHashChain
import app.trustipay.offline.protocol.MessageHasher
import app.trustipay.offline.protocol.OfflineTokenFactory
import app.trustipay.offline.protocol.OfflineTokenWallet
import app.trustipay.offline.protocol.PaymentProtocolEngine
import app.trustipay.offline.protocol.PublicKeyTokenIssuerVerifier
import app.trustipay.offline.protocol.TokenSelectionResult
import app.trustipay.offline.protocol.TokenValidator
import app.trustipay.offline.protocol.canonicalBytes
import app.trustipay.offline.security.AndroidKeystoreSigner
import app.trustipay.offline.security.DeviceKeyManager
import app.trustipay.offline.security.LocalEncryptionService
import app.trustipay.offline.security.PublicKeyCache
import app.trustipay.offline.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Duration

class OfflineViewModel(application: Application) : AndroidViewModel(application) {
    private val flags = OfflineFeatureFlagProvider.current
    private val dbHelper = OfflinePaymentOpenHelper(application)
    private val store = SQLiteOfflinePaymentStore(dbHelper.writableDatabase)
    private val clock = Clock.systemUTC()
    private val idGenerator = SecureOfflineIdGenerator()
    private val syncRepository = SyncRepository(
        store = store,
        flags = flags,
        idGenerator = idGenerator,
        clock = clock,
        localEncryptionService = LocalEncryptionService(),
    )
    private val engine = PaymentProtocolEngine(clock, idGenerator)

    // Real device key (hardware-backed on API 29+)
    private val keystoreSigner = AndroidKeystoreSigner()
    private val deviceKeyManager = DeviceKeyManager(keystoreSigner)

    // Shared public key cache — populated from API token issuance and peer exchanges
    private val publicKeyCache: PublicKeyCache
        get() = AppContainer.tokenIssuanceRepository.sharedPublicKeyCache

    private val tokenValidator: TokenValidator by lazy {
        TokenValidator(
            PublicKeyTokenIssuerVerifier(CacheBackedSignatureVerifier(publicKeyCache)),
            clock,
        )
    }

    // Demo receiver key — represents the other device in same-device demo flow
    private val demoReceiver = JavaSigningKeyFactory.generate("receiver-demo-001")
    // Demo issuer key — used only when seeding debug data
    private val demoIssuer = if (BuildConfig.DEBUG) JavaSigningKeyFactory.generate("issuer-demo-001") else null

    private val _uiState = MutableStateFlow(OfflineUiSnapshot(0, 0, 0, null, emptyList(), emptyList()))
    val uiState: StateFlow<OfflineUiSnapshot> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            keystoreSigner.ensureKeyPair()
            if (BuildConfig.DEBUG) seedDemoDataIfNeeded()
            refreshSnapshot()
        }
    }

    private fun seedDemoDataIfNeeded() {
        val existingTokens = store.listTokens()
        if (existingTokens.isNotEmpty()) return
        val issuer = demoIssuer ?: return
        val tokenFactory = OfflineTokenFactory(idGenerator)
        val now = clock.instant()
        val tokens = listOf(100000L, 50000L, 20000L, 10000L, 10000L, 5000L, 2000L, 1000L).map { amount ->
            tokenFactory.issueToken(
                ownerUserId = "user_saman_demo",
                ownerDeviceId = deviceKeyManager.getPublicKeyId(),
                amountMinor = amount,
                currency = "LKR",
                issuedAtServer = now - Duration.ofHours(1),
                expiresAtServer = now + Duration.ofDays(5),
                issuerKeyId = issuer.publicKeyId,
                issuerSigner = issuer.signer(),
            )
        }
        // Cache demo issuer public key so validator can verify demo tokens
        publicKeyCache.put(issuer.publicKeyId, issuer.keyPair.public)
        tokens.forEach(store::upsertToken)
    }

    fun refreshSnapshot(lastMessage: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = clock.instant()
            val tokens = store.listTokens()
            val transactions = store.listTransactions()
            val wallet = OfflineTokenWallet(tokens)
            val pendingSyncCount = transactions.count { it.state in PendingStates }

            _uiState.value = OfflineUiSnapshot(
                balanceMinor = wallet.spendableBalance("LKR", now),
                tokenCount = tokens.count { it.isSpendableAt(now) },
                pendingSyncCount = pendingSyncCount,
                lastMessage = lastMessage,
                tokens = tokens.map {
                    OfflineTokenUiRow(
                        tokenId = it.tokenId.takeLast(8),
                        amountMinor = it.amountMinor,
                        expiresAt = it.expiresAtServer.toString().take(10),
                        status = it.status.name,
                    )
                },
                transactions = transactions.map {
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
                }
            )
        }
    }

    fun requestFreshTokens() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshSnapshot("Requesting tokens from server…")
            val result = AppContainer.tokenIssuanceRepository.requestAndStoreTokens(
                requestedAmounts = listOf(100000L, 50000L, 20000L, 10000L, 5000L, 2000L, 1000L),
                currency = "LKR",
            )
            val message = when (result) {
                is app.trustipay.api.ApiResult.Success -> "Received ${result.data} tokens from server."
                is app.trustipay.api.ApiResult.NetworkError -> "Network error requesting tokens."
                is app.trustipay.api.ApiResult.HttpError -> "Server error: ${result.message}"
                app.trustipay.api.ApiResult.AuthError -> "Not authenticated."
            }
            refreshSnapshot(message)
        }
    }

    fun runQrPayment(amountText: String, receiverAlias: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!flags.offlinePaymentsEnabled) {
                refreshSnapshot("Offline payments are disabled.")
                return@launch
            }
            val money = Money.fromDecimalText(amountText) ?: run {
                refreshSnapshot("Enter a valid LKR amount.")
                return@launch
            }

            val now = clock.instant()
            val tokens = store.listTokens()
            val wallet = OfflineTokenWallet(tokens)
            wallet.expireOldTokens(now)

            val selected = wallet.selectExactTokens(money, now)
            if (selected !is TokenSelectionResult.Selected) {
                refreshSnapshot("No exact offline token match for LKR ${money.displayAmount()}.")
                return@launch
            }

            try {
                wallet.reserveTokens(selected.tokens.map { it.tokenId })

                // Cache the demo receiver's public key so verification works
                publicKeyCache.put(demoReceiver.publicKeyId, demoReceiver.keyPair.public)
                // Cache our own signing key's public key
                val senderKeyBytes = deviceKeyManager.getPublicKeyBytes()
                val senderPublicKey = KeyFactory.getInstance("EC")
                    .generatePublic(X509EncodedKeySpec(senderKeyBytes))
                publicKeyCache.put(deviceKeyManager.getPublicKeyId(), senderPublicKey)

                val request = engine.createPaymentRequest(
                    receiverUserAlias = receiverAlias.ifBlank { "Offline Receiver" },
                    receiverDeviceId = demoReceiver.publicKeyId,
                    receiverPublicKeyId = demoReceiver.publicKeyId,
                    money = money,
                    description = description,
                    supportedTransports = listOf(TransportType.QR),
                    signer = demoReceiver.signer(),
                    validFor = Duration.ofMinutes(5),
                )

                val senderDeviceId = deviceKeyManager.getPublicKeyId()
                val senderPreviousHash = store.latestLocalChainHash(senderDeviceId) ?: LocalHashChain.GENESIS_HASH
                val offer = engine.createPaymentOffer(
                    request = request,
                    senderUserAlias = "Me",
                    senderDeviceId = senderDeviceId,
                    senderPublicKeyId = senderDeviceId,
                    selectedTokens = selected.tokens,
                    signer = keystoreSigner,
                    senderPreviousHash = senderPreviousHash,
                )

                val receipt = engine.createPaymentReceipt(
                    request = request,
                    offer = offer,
                    receiverDeviceId = demoReceiver.publicKeyId,
                    signer = demoReceiver.signer(),
                    receiverPreviousHash = LocalHashChain.GENESIS_HASH,
                )

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
                    senderPreviousHash = senderPreviousHash,
                    receiverPreviousHash = receipt.receiverPreviousHash,
                    createdLocalAt = now,
                    updatedLocalAt = now,
                )

                syncRepository.queueAcceptedTransaction(transaction)
                refreshSnapshot("Payment accepted offline.")

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                wallet.releaseReservations(selected.tokens.map { it.tokenId })
                refreshSnapshot("Error: ${e.message}")
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val summary = syncRepository.processSync()
            refreshSnapshot(summary.message)
        }
    }

    companion object {
        private val PendingStates = setOf(
            TransactionState.LOCAL_ACCEPTED_PENDING_SYNC,
            TransactionState.SYNC_QUEUED,
            TransactionState.SYNC_UPLOADED,
            TransactionState.SERVER_VALIDATING,
        )
    }
}

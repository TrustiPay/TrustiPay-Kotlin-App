package app.trustipay.offline.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trustipay.AppContainer
import app.trustipay.BuildConfig
import app.trustipay.offline.OfflineFeatureFlagProvider
import app.trustipay.offline.data.FirestorePaymentStore
import app.trustipay.offline.data.OfflinePaymentOpenHelper
import app.trustipay.offline.data.SQLiteOfflinePaymentStore
import app.trustipay.offline.domain.Money
import app.trustipay.offline.domain.OfflineIOU
import app.trustipay.offline.domain.OfflineTransaction
import app.trustipay.offline.domain.PaymentSession
import app.trustipay.offline.domain.SecureOfflineIdGenerator
import app.trustipay.offline.domain.TransactionDirection
import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransportRole
import app.trustipay.offline.domain.TransportType
import app.trustipay.offline.protocol.OfflineTokenFactory
import app.trustipay.offline.protocol.OfflineTokenWallet
import app.trustipay.offline.protocol.TokenSelectionResult
import app.trustipay.offline.security.AndroidKeystoreSigner
import app.trustipay.offline.security.DeviceKeyManager
import app.trustipay.offline.security.IOUCryptography
import app.trustipay.offline.security.LocalEncryptionService
import app.trustipay.offline.security.PublicKeyCache
import app.trustipay.offline.sync.SyncRepository
import app.trustipay.offline.transport.qr.QrCodeGenerator
import app.trustipay.offline.transport.qr.QrPaymentTransport
import app.trustipay.offline.transport.nfc.NfcPaymentTransport
import app.trustipay.voice.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import kotlinx.coroutines.withTimeoutOrNull
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
    private val firestoreStore = FirestorePaymentStore()

    // Real device key (hardware-backed on API 29+)
    private val keystoreSigner = AndroidKeystoreSigner()
    private val deviceKeyManager = DeviceKeyManager(keystoreSigner)

    // Shared public key cache — populated from API token issuance and peer exchanges
    private val publicKeyCache: PublicKeyCache
        get() = AppContainer.tokenIssuanceRepository.sharedPublicKeyCache

    // Demo issuer key — used only when seeding debug data
    private val demoIssuer = if (BuildConfig.DEBUG) app.trustipay.offline.protocol.JavaSigningKeyFactory.generate("issuer-demo-001") else null

    private val _uiState = MutableStateFlow(OfflineUiSnapshot(0, 0, 0, null, emptyList(), emptyList(), QrFlowMode.IDLE))
    val uiState: StateFlow<OfflineUiSnapshot> = _uiState.asStateFlow()

    val qrTransport = QrPaymentTransport(QrCodeGenerator())
    val nfcTransport = NfcPaymentTransport(application)
    private val qrGenerator = QrCodeGenerator()

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

            _uiState.value = _uiState.value.copy(
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

    fun startPayFlow(amountText: String, receiverId: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                qrFlowMode = QrFlowMode.PROCESSING,
                processingMessage = "Preparing secure IOU..."
            )
            
            val money = Money.fromDecimalText(amountText) ?: run {
                refreshSnapshot("Invalid amount.")
                _uiState.value = _uiState.value.copy(qrFlowMode = QrFlowMode.IDLE)
                return@launch
            }
            
            // Artificial delay for encryption animation
            kotlinx.coroutines.delay(2000)
            
            val senderId = deviceKeyManager.getPublicKeyId()
            val prevHash = firestoreStore.getLastTransactionHash(senderId)
            
            val iou = OfflineIOU(
                tx_id = idGenerator.newId("tx"),
                sender_id = senderId,
                receiver_id = receiverId,
                timestamp = clock.instant().toString(),
                amount = money.amountMinor / 100.0,
                category = note.ifBlank { "General" },
                location = "Colombo, LK",
                device_id = senderId,
                nonce = (1..1000000).random(),
                prev_hash = prevHash
            )
            
            val signedIou = IOUCryptography.sign(iou)
            val now = clock.instant()

            val isOnline = NetworkUtils.isOnline(getApplication())
            
            if (isOnline) {
                _uiState.value = _uiState.value.copy(processingMessage = "Sending via Cloud Queue...")
                try {
                    val result = withTimeoutOrNull(5000) {
                        firestoreStore.saveIOU(signedIou)
                    }
                    
                    if (result != null) {
                        store.upsertTransaction(
                            OfflineTransaction(
                                transactionId = signedIou.tx_id,
                                requestId = null,
                                direction = TransactionDirection.SENT,
                                counterpartyAlias = receiverId,
                                amountMinor = money.amountMinor,
                                currency = "LKR",
                                state = TransactionState.SETTLED,
                                transportType = null, // Cloud
                                requestPayload = null,
                                offerPayload = signedIou.toJson().toByteArray(),
                                receiptPayload = null,
                                requestHash = null,
                                offerHash = IOUCryptography.hash(signedIou.toJson()),
                                receiptHash = null,
                                senderPreviousHash = signedIou.prev_hash,
                                createdLocalAt = now,
                                updatedLocalAt = now
                            )
                        )
                        
                        refreshSnapshot("Online Payment Sent!")
                        _uiState.value = _uiState.value.copy(qrFlowMode = QrFlowMode.IDLE)
                        return@launch
                    } else {
                        Log.w("OfflineViewModel", "Online payment timed out, falling back to offline")
                    }
                } catch (e: Exception) {
                    Log.e("OfflineViewModel", "Online payment failed, falling back to offline", e)
                }
            }

            // Fallback or explicit offline flow
            _uiState.value = _uiState.value.copy(processingMessage = "Network unavailable. Generating QR/NFC...")
            
            store.upsertTransaction(
                OfflineTransaction(
                    transactionId = signedIou.tx_id,
                    requestId = null,
                    direction = TransactionDirection.SENT,
                    counterpartyAlias = receiverId,
                    amountMinor = money.amountMinor,
                    currency = "LKR",
                    state = TransactionState.LOCAL_ACCEPTED_PENDING_SYNC,
                    transportType = TransportType.QR,
                    requestPayload = null,
                    offerPayload = signedIou.toJson().toByteArray(),
                    receiptPayload = null,
                    requestHash = null,
                    offerHash = IOUCryptography.hash(signedIou.toJson()),
                    receiptHash = null,
                    senderPreviousHash = signedIou.prev_hash,
                    createdLocalAt = now,
                    updatedLocalAt = now
                )
            )
            
            qrTransport.startSession(
                TransportRole.SENDER,
                PaymentSession(
                    sessionId = idGenerator.newId("sess"),
                    role = TransportRole.SENDER,
                    transportType = TransportType.QR,
                    createdAtDevice = now,
                    expiresAtDevice = now.plusSeconds(300)
                )
            )
            val qrResult = qrTransport.sendIOU(signedIou)
            nfcTransport.sendIOU(signedIou)
            
            if (qrResult.isFailure) {
                Log.e("OfflineViewModel", "QR Generation Failed", qrResult.exceptionOrNull())
            }

            val otpForSender = IOUCryptography.generateOTP(signedIou.signature)

            _uiState.value = _uiState.value.copy(
                qrFlowMode = QrFlowMode.DISPLAYING,
                qrAmount = "Paying Rs. ${money.displayAmount()}",
                otpCode = null,
                showOtpInput = true,
                senderOtpCode = otpForSender
            )
            refreshSnapshot()
        }
    }

    fun verifySenderOtp(inputOtp: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val expected = _uiState.value.senderOtpCode
            if (inputOtp == expected) {
                _uiState.value = _uiState.value.copy(otpFeedback = OtpFeedback.SUCCESS)
                kotlinx.coroutines.delay(1500)
                _uiState.value = _uiState.value.copy(
                    qrFlowMode = QrFlowMode.IDLE,
                    showOtpInput = false,
                    senderOtpCode = null,
                    otpFeedback = OtpFeedback.NONE,
                    lastMessage = "Payment Confirmed!"
                )
                refreshSnapshot()
            } else {
                _uiState.value = _uiState.value.copy(otpFeedback = OtpFeedback.ERROR)
                kotlinx.coroutines.delay(1000)
                _uiState.value = _uiState.value.copy(otpFeedback = OtpFeedback.NONE)
                refreshSnapshot("Incorrect OTP. Please try again.")
            }
        }
    }

    fun startReceiveFlow() {
        _uiState.value = _uiState.value.copy(qrFlowMode = QrFlowMode.SCANNING, nfcMode = false)
    }

    fun startNfcReceiveFlow() {
        _uiState.value = _uiState.value.copy(qrFlowMode = QrFlowMode.SCANNING, nfcMode = true)
    }

    fun onQrScanned(raw: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val iou = qrGenerator.decodeIOU(raw)
            if (iou != null) {
                handleScannedIOU(iou)
            } else {
                refreshSnapshot("Invalid QR code format.")
            }
        }
    }

    private suspend fun handleScannedIOU(iou: OfflineIOU) {
        try {
            _uiState.value = _uiState.value.copy(
                qrFlowMode = QrFlowMode.PROCESSING,
                processingMessage = "Verifying IOU..."
            )
            
            // Artificial delay for decryption animation
            kotlinx.coroutines.delay(2000)
            
            if (!IOUCryptography.verify(iou)) {
                refreshSnapshot("IOU Signature Verification Failed!")
                _uiState.value = _uiState.value.copy(qrFlowMode = QrFlowMode.IDLE)
                return
            }
            
            if (iou.prev_hash.isEmpty()) {
                refreshSnapshot("IOU Hash Chain Broken!")
                return
            }

            val isOnline = NetworkUtils.isOnline(getApplication())
            var settledOnline = false
            
            if (isOnline) {
                _uiState.value = _uiState.value.copy(processingMessage = "Settling online...")
                try {
                    // Try to save to Firestore (acting as a cloud queue)
                    // If this fails or times out, we mark it as locally accepted
                    val syncResult = withTimeoutOrNull(3000) {
                        firestoreStore.saveIOU(iou)
                    }
                    if (syncResult != null) {
                        settledOnline = true
                    } else {
                        Log.w("OfflineViewModel", "Receiver sync timed out, queuing locally")
                    }
                } catch (e: Exception) {
                    Log.e("OfflineViewModel", "Receiver sync failed, queuing locally", e)
                }
            }

            val state = if (settledOnline) TransactionState.SETTLED else TransactionState.LOCAL_ACCEPTED_PENDING_SYNC
            
            // If we are offline or sync timed out, fire-and-forget the Firestore save.
            // Firestore's local persistence will handle the upload once back online.
            if (!settledOnline) {
                viewModelScope.launch(Dispatchers.IO) {
                    firestoreStore.saveIOU(iou)
                }
            }
            
            // Record in SQLite for UI visibility
            val now = clock.instant()
            val transaction = OfflineTransaction(
                transactionId = iou.tx_id,
                requestId = null,
                direction = TransactionDirection.RECEIVED,
                counterpartyAlias = iou.sender_id,
                amountMinor = (iou.amount * 100).toLong(),
                currency = "LKR",
                state = state,
                transportType = TransportType.QR,
                requestPayload = null,
                offerPayload = iou.toJson().toByteArray(),
                receiptPayload = null,
                requestHash = null,
                offerHash = IOUCryptography.hash(iou.toJson()),
                receiptHash = null,
                senderPreviousHash = iou.prev_hash,
                receiverPreviousHash = store.latestLocalChainHash(deviceKeyManager.getPublicKeyId()),
                createdLocalAt = now,
                updatedLocalAt = now
            )
            store.upsertTransaction(transaction)

            // If not settled, queue for background sync
            if (!settledOnline) {
                syncRepository.queueAcceptedTransaction(transaction)
            }
            
            val otp = IOUCryptography.generateOTP(iou.signature)
            
            _uiState.value = _uiState.value.copy(
                qrFlowMode = QrFlowMode.DISPLAYING,
                qrAmount = "Received Rs. ${String.format(java.util.Locale.US, "%.2f", iou.amount)}",
                otpCode = otp
            )
            
            val msg = if (isOnline) "Payment received & settled!" else "Payment received locally! Syncing..."
            refreshSnapshot(msg)
            
        } catch (e: Exception) {
            refreshSnapshot("Error processing IOU: ${e.message}")
        }
    }

    fun cancelQrFlow() {
        viewModelScope.launch {
            qrTransport.close()
            nfcTransport.close()
            _uiState.value = _uiState.value.copy(
                qrFlowMode = QrFlowMode.IDLE, 
                qrAmount = null, 
                otpCode = null,
                showOtpInput = false,
                senderOtpCode = null
            )
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

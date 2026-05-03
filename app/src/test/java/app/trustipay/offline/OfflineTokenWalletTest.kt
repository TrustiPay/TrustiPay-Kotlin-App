package app.trustipay.offline

import app.trustipay.offline.domain.Money
import app.trustipay.offline.domain.OfflineTokenStatus
import app.trustipay.offline.protocol.JavaSigningKeyFactory
import app.trustipay.offline.protocol.OfflineTokenFactory
import app.trustipay.offline.protocol.OfflineTokenWallet
import app.trustipay.offline.protocol.TokenSelectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.Duration

class OfflineTokenWalletTest {
    private val now = Instant.parse("2026-05-03T10:15:00Z")
    private val issuer = JavaSigningKeyFactory.generate("issuer-wallet-test")
    private val factory = OfflineTokenFactory()

    @Test
    fun selectExactTokens_combinesDenominationsWithoutChange() {
        val wallet = OfflineTokenWallet(
            listOf(100000L, 50000L, 20000L).map(::token)
        )

        val result = wallet.selectExactTokens(Money.lkr(150000), now)

        assertTrue(result is TokenSelectionResult.Selected)
        assertEquals(listOf(100000L, 50000L), (result as TokenSelectionResult.Selected).tokens.map { it.amountMinor })
    }

    @Test
    fun markSpentPendingSync_requiresReservation() {
        val token = token(100000)
        val wallet = OfflineTokenWallet(listOf(token))

        wallet.reserveTokens(listOf(token.tokenId))
        wallet.markSpentPendingSync(listOf(token.tokenId))

        assertEquals(OfflineTokenStatus.SPENT_PENDING_SYNC, wallet.allTokens().single().status)
    }

    private fun token(amountMinor: Long) = factory.issueToken(
        ownerUserId = "user",
        ownerDeviceId = "device",
        amountMinor = amountMinor,
        currency = "LKR",
        issuedAtServer = now.minus(Duration.ofHours(1)),
        expiresAtServer = now.plus(Duration.ofDays(3)),
        issuerKeyId = issuer.publicKeyId,
        issuerSigner = issuer.signer(),
    )
}

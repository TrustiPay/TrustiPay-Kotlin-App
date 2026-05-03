package app.trustipay.offline.protocol

import app.trustipay.offline.domain.Money
import app.trustipay.offline.domain.OfflineToken
import app.trustipay.offline.domain.OfflineTokenStatus
import java.time.Instant

class OfflineTokenWallet(
    initialTokens: List<OfflineToken> = emptyList(),
) {
    private val tokensById = initialTokens.associateBy { it.tokenId }.toMutableMap()

    fun allTokens(): List<OfflineToken> = tokensById.values.sortedWith(TokenSort)

    fun spendableBalance(currency: String, now: Instant): Long =
        allTokens()
            .filter { it.currency == currency && it.isSpendableAt(now) }
            .sumOf { it.amountMinor }

    fun upsertTokens(tokens: List<OfflineToken>) {
        tokens.forEach { token -> tokensById[token.tokenId] = token }
    }

    fun expireOldTokens(now: Instant): Int {
        var expired = 0
        tokensById.replaceAll { _, token ->
            if (token.status == OfflineTokenStatus.AVAILABLE && !token.expiresAtServer.isAfter(now)) {
                expired += 1
                token.copy(status = OfflineTokenStatus.EXPIRED)
            } else {
                token
            }
        }
        return expired
    }

    fun selectExactTokens(money: Money, now: Instant): TokenSelectionResult {
        val candidates = allTokens()
            .filter { it.currency == money.currency && it.isSpendableAt(now) }
        candidates.firstOrNull { it.amountMinor == money.amountMinor }?.let {
            return TokenSelectionResult.Selected(listOf(it))
        }

        val selectionsBySum = linkedMapOf(0L to emptyList<OfflineToken>())
        candidates.forEach { token ->
            val additions = selectionsBySum
                .filterKeys { sum -> sum + token.amountMinor <= money.amountMinor }
                .mapValues { (_, selected) -> selected + token }
            additions.forEach { (sum, selected) ->
                val newSum = sum + token.amountMinor
                val existing = selectionsBySum[newSum]
                if (existing == null || selected.betterThan(existing)) {
                    selectionsBySum[newSum] = selected
                }
            }
        }

        val selected = selectionsBySum[money.amountMinor]
        return if (selected == null) {
            TokenSelectionResult.NoExactMatch(
                spendableMinor = spendableBalance(money.currency, now),
                requestedMinor = money.amountMinor,
            )
        } else {
            TokenSelectionResult.Selected(selected)
        }
    }

    fun reserveTokens(tokenIds: Collection<String>) {
        updateStatuses(tokenIds, expected = OfflineTokenStatus.AVAILABLE, next = OfflineTokenStatus.RESERVED_FOR_LOCAL_TXN)
    }

    fun releaseReservations(tokenIds: Collection<String>) {
        updateStatuses(tokenIds, expected = OfflineTokenStatus.RESERVED_FOR_LOCAL_TXN, next = OfflineTokenStatus.AVAILABLE)
    }

    fun markSpentPendingSync(tokenIds: Collection<String>) {
        updateStatuses(tokenIds, expected = OfflineTokenStatus.RESERVED_FOR_LOCAL_TXN, next = OfflineTokenStatus.SPENT_PENDING_SYNC)
    }

    fun markSpentSynced(tokenIds: Collection<String>) {
        tokenIds.forEach { tokenId ->
            val token = tokensById[tokenId] ?: error("Unknown token: $tokenId")
            require(token.status == OfflineTokenStatus.SPENT_PENDING_SYNC || token.status == OfflineTokenStatus.RESERVED_FOR_LOCAL_TXN) {
                "Token $tokenId cannot be marked synced from ${token.status}."
            }
            tokensById[tokenId] = token.copy(status = OfflineTokenStatus.SPENT_SYNCED)
        }
    }

    private fun updateStatuses(
        tokenIds: Collection<String>,
        expected: OfflineTokenStatus,
        next: OfflineTokenStatus,
    ) {
        tokenIds.forEach { tokenId ->
            val token = tokensById[tokenId] ?: error("Unknown token: $tokenId")
            require(token.status == expected) { "Token $tokenId must be $expected before $next; was ${token.status}." }
        }
        tokenIds.forEach { tokenId ->
            tokensById[tokenId] = tokensById.getValue(tokenId).copy(status = next)
        }
    }

    private fun List<OfflineToken>.betterThan(other: List<OfflineToken>): Boolean =
        size < other.size || (size == other.size && maxOf { it.expiresAtServer }.isBefore(other.maxOf { it.expiresAtServer }))

    companion object {
        private val TokenSort = compareBy<OfflineToken> { it.expiresAtServer }
            .thenByDescending { it.amountMinor }
            .thenBy { it.tokenId }
    }
}

sealed class TokenSelectionResult {
    data class Selected(val tokens: List<OfflineToken>) : TokenSelectionResult()
    data class NoExactMatch(
        val spendableMinor: Long,
        val requestedMinor: Long,
    ) : TokenSelectionResult()
}

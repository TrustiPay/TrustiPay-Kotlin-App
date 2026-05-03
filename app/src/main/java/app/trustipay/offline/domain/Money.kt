package app.trustipay.offline.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(
    val amountMinor: Long,
    val currency: String,
) {
    init {
        require(amountMinor > 0) { "amountMinor must be positive." }
        require(currency.matches(CurrencyCodeRegex)) { "currency must be a three-letter ISO-style code." }
    }

    fun displayAmount(): String =
        BigDecimal(amountMinor)
            .movePointLeft(2)
            .setScale(2, RoundingMode.UNNECESSARY)
            .toPlainString()

    companion object {
        private val CurrencyCodeRegex = Regex("[A-Z]{3}")

        fun lkr(amountMinor: Long): Money = Money(amountMinor = amountMinor, currency = "LKR")

        fun fromDecimalText(text: String, currency: String = "LKR"): Money? {
            val normalized = text.replace(",", "").trim()
            if (normalized.isBlank()) return null
            val decimal = normalized.toBigDecimalOrNull() ?: return null
            if (decimal <= BigDecimal.ZERO) return null
            val minor = runCatching {
                decimal.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
            }.getOrNull() ?: return null
            return Money(minor.longValueExact(), currency.uppercase())
        }
    }
}

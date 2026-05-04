package app.trustipay.voice

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import org.json.JSONObject

enum class BankingIntentType(
    val wireValue: String,
) {
    SendMoney("send_money"),
    CheckBalance("check_balance"),
    PayBill("pay_bill"),
    Unknown("unknown"),
}

data class BankingIntent(
    val request: BankingIntentType,
    val rawTranscript: String,
    val to: String? = null,
    val amount: Double? = null,
    val reason: String? = null,
) {
    fun toJsonObject(): JSONObject =
        JSONObject()
            .put("request", request.wireValue)
            .put("raw", rawTranscript)
            .putOptional("to", to)
            .putOptional("amount", amount)
            .putOptional("reason", reason)

    fun toJsonString(): String = toJsonObject().toString()
}

enum class LlmAnalysisState {
    Missing,
    Initializing,
    Ready,
    Failed,
}

sealed interface LlmAnalysisResult {
    val rawTranscript: String

    data class Success(
        val intent: BankingIntent,
    ) : LlmAnalysisResult {
        override val rawTranscript: String = intent.rawTranscript
    }

    data class Unavailable(
        override val rawTranscript: String,
        val message: String,
    ) : LlmAnalysisResult

    data class Failure(
        override val rawTranscript: String,
        val message: String,
    ) : LlmAnalysisResult
}

fun LlmAnalysisResult.toJsonString(): String =
    when (this) {
        is LlmAnalysisResult.Success -> intent.toJsonString()
        is LlmAnalysisResult.Unavailable -> analysisErrorJson("unavailable", message, rawTranscript)
        is LlmAnalysisResult.Failure -> analysisErrorJson("failed", message, rawTranscript)
    }

internal object BankingIntentToolCallParser {
    fun parse(
        toolCalls: List<ToolCall>,
        rawTranscript: String,
    ): LlmAnalysisResult {
        if (toolCalls.isEmpty()) {
            return LlmAnalysisResult.Failure(
                rawTranscript = rawTranscript,
                message = "No intent tool call returned.",
            )
        }

        if (toolCalls.size != 1) {
            return LlmAnalysisResult.Failure(
                rawTranscript = rawTranscript,
                message = "Expected exactly one intent tool call, got ${toolCalls.size}.",
            )
        }

        return runCatching {
            LlmAnalysisResult.Success(parseSingleToolCall(toolCalls.single(), rawTranscript))
        }.getOrElse { throwable ->
            LlmAnalysisResult.Failure(
                rawTranscript = rawTranscript,
                message = throwable.message ?: "Intent tool call could not be parsed.",
            )
        }
    }

    private fun parseSingleToolCall(
        toolCall: ToolCall,
        rawTranscript: String,
    ): BankingIntent {
        val arguments = toolCall.arguments
        return when (normalizeToolName(toolCall.name)) {
            "sendmoney" -> BankingIntent(
                request = BankingIntentType.SendMoney,
                rawTranscript = rawTranscript,
                to = requiredText(arguments, "to", "recipient"),
                amount = requiredPositiveAmount(arguments),
                reason = optionalText(arguments, "reason"),
            )
            "checkbalance" -> BankingIntent(
                request = BankingIntentType.CheckBalance,
                rawTranscript = rawTranscript,
            )
            "paybill" -> BankingIntent(
                request = BankingIntentType.PayBill,
                rawTranscript = rawTranscript,
                to = requiredText(arguments, "to", "biller", "bill_provider"),
                amount = requiredPositiveAmount(arguments),
                reason = optionalText(arguments, "reason"),
            )
            "unknownrequest" -> BankingIntent(
                request = BankingIntentType.Unknown,
                rawTranscript = rawTranscript,
                reason = optionalText(arguments, "reason"),
            )
            else -> error("Unsupported intent tool '${toolCall.name}'.")
        }
    }

    private fun normalizeToolName(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    private fun requiredText(
        arguments: Map<String, Any?>,
        vararg names: String,
    ): String {
        val value = names.asSequence()
            .mapNotNull { arguments[it] }
            .map { it.toString().trim() }
            .firstOrNull { it.isNotBlank() }
        return value ?: error("Missing required text argument '${names.first()}'.")
    }

    private fun optionalText(
        arguments: Map<String, Any?>,
        vararg names: String,
    ): String? =
        names.asSequence()
            .mapNotNull { arguments[it] }
            .map { it.toString().trim() }
            .firstOrNull { it.isNotBlank() }

    private fun requiredPositiveAmount(arguments: Map<String, Any?>): Double {
        val amount = arguments["amount"]?.toAmount()
            ?: error("Missing required amount.")
        check(amount > 0.0) { "Amount must be greater than zero." }
        return amount
    }

    private fun Any.toAmount(): Double? =
        when (this) {
            is Number -> toDouble()
            is String -> trim().toDoubleOrNull()
            else -> null
        }
}

class TrustiPayIntentToolSet : ToolSet {
    @Tool(description = "Create a draft send-money intent (මුදල් යැවීමේ කෙටුම්පතක් සාදන්න). This does not transfer money.")
    fun sendMoney(
        @ToolParam(description = "Recipient name, phone number, account alias, or contact (ලබන්නාගේ නම හෝ අංකය).") to: String,
        @ToolParam(description = "Amount to send in Sri Lankan rupees (රුපියල් ප්‍රමාණය). Must be greater than zero.") amount: Double,
        @ToolParam(description = "Optional transfer reason, note, or memo (සටහන).") reason: String? = null,
    ): Map<String, Any?> =
        mapOf("request" to BankingIntentType.SendMoney.wireValue, "to" to to, "amount" to amount, "reason" to reason)

    @Tool(description = "Create a draft balance-check intent (ගිණුම් ශේෂය පරීක්ෂා කිරීම).")
    fun checkBalance(): Map<String, Any?> =
        mapOf("request" to BankingIntentType.CheckBalance.wireValue)

    @Tool(description = "Create a draft bill-payment intent (බිල්පත් ගෙවීමේ කෙටුම්පතක් සාදන්න). This does not pay the bill.")
    fun payBill(
        @ToolParam(description = "Biller, merchant, utility provider, or organization to pay (බිල්පත ගෙවිය යුතු ආයතනය).") to: String,
        @ToolParam(description = "Bill amount in Sri Lankan rupees (රුපියල් ප්‍රමාණය). Must be greater than zero.") amount: Double,
        @ToolParam(description = "Optional bill reference, category, or note (විමර්ශන අංකය).") reason: String? = null,
    ): Map<String, Any?> =
        mapOf("request" to BankingIntentType.PayBill.wireValue, "to" to to, "amount" to amount, "reason" to reason)

    @Tool(description = "Use when the voice request is unclear or not one of TrustiPay's supported banking intents (අපැහැදිලි ඉල්ලීමකි).")
    fun unknownRequest(
        @ToolParam(description = "Short reason the request is unclear or unsupported (හේතුව).") reason: String? = null,
    ): Map<String, Any?> =
        mapOf("request" to BankingIntentType.Unknown.wireValue, "reason" to reason)
}

private fun JSONObject.putOptional(
    name: String,
    value: Any?,
): JSONObject {
    if (value != null) put(name, value)
    return this
}

private fun analysisErrorJson(
    status: String,
    message: String,
    rawTranscript: String,
): String =
    JSONObject()
        .put("analysis", status)
        .put("error", message)
        .put("raw", rawTranscript)
        .toString()

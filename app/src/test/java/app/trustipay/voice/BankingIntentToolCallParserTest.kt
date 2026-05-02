package app.trustipay.voice

import com.google.ai.edge.litertlm.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingIntentToolCallParserTest {
    @Test
    fun parse_validSendMoneyToolCall_returnsIntent() {
        val result = parse(
            toolCall(
                "send_money",
                mapOf("to" to "Saman", "amount" to 500.0, "reason" to "food"),
            )
        )

        assertTrue(result is LlmAnalysisResult.Success)
        val intent = (result as LlmAnalysisResult.Success).intent
        assertEquals(BankingIntentType.SendMoney, intent.request)
        assertEquals("Saman", intent.to)
        assertEquals(500.0, intent.amount!!, 0.0)
        assertEquals("food", intent.reason)
        assertEquals(RawTranscript, intent.rawTranscript)
    }

    @Test
    fun parse_validCheckBalanceToolCall_returnsIntent() {
        val result = parse(toolCall("check_balance"))

        assertTrue(result is LlmAnalysisResult.Success)
        val intent = (result as LlmAnalysisResult.Success).intent
        assertEquals(BankingIntentType.CheckBalance, intent.request)
        assertEquals(RawTranscript, intent.rawTranscript)
    }

    @Test
    fun parse_validPayBillToolCall_returnsIntent() {
        val result = parse(
            toolCall(
                "pay_bill",
                mapOf("biller" to "Dialog", "amount" to "1500"),
            )
        )

        assertTrue(result is LlmAnalysisResult.Success)
        val intent = (result as LlmAnalysisResult.Success).intent
        assertEquals(BankingIntentType.PayBill, intent.request)
        assertEquals("Dialog", intent.to)
        assertEquals(1500.0, intent.amount!!, 0.0)
    }

    @Test
    fun parse_unknownRequestToolCall_preservesSinhalaRawTranscript() {
        val rawTranscript = "මට මේක පැහැදිලි නැහැ"
        val result = BankingIntentToolCallParser.parse(
            toolCalls = listOf(toolCall("unknown_request", mapOf("reason" to "unclear"))),
            rawTranscript = rawTranscript,
        )

        assertTrue(result is LlmAnalysisResult.Success)
        val intent = (result as LlmAnalysisResult.Success).intent
        assertEquals(BankingIntentType.Unknown, intent.request)
        assertEquals("unclear", intent.reason)
        assertEquals(rawTranscript, intent.rawTranscript)
    }

    @Test
    fun parse_missingRecipient_returnsFailure() {
        val result = parse(toolCall("send_money", mapOf("amount" to 500.0)))

        assertTrue(result is LlmAnalysisResult.Failure)
        assertEquals(RawTranscript, result.rawTranscript)
    }

    @Test
    fun parse_zeroAmount_returnsFailure() {
        val result = parse(toolCall("pay_bill", mapOf("to" to "Dialog", "amount" to 0.0)))

        assertTrue(result is LlmAnalysisResult.Failure)
    }

    @Test
    fun parse_malformedAmount_returnsFailure() {
        val result = parse(toolCall("send_money", mapOf("to" to "Saman", "amount" to "not-a-number")))

        assertTrue(result is LlmAnalysisResult.Failure)
    }

    @Test
    fun parse_multipleToolCalls_returnsFailure() {
        val result = parse(toolCall("check_balance"), toolCall("unknown_request"))

        assertTrue(result is LlmAnalysisResult.Failure)
    }

    @Test
    fun parse_noToolCall_returnsFailure() {
        val result = BankingIntentToolCallParser.parse(
            toolCalls = emptyList(),
            rawTranscript = RawTranscript,
        )

        assertTrue(result is LlmAnalysisResult.Failure)
    }

    private fun parse(vararg toolCalls: ToolCall): LlmAnalysisResult =
        BankingIntentToolCallParser.parse(
            toolCalls = toolCalls.toList(),
            rawTranscript = RawTranscript,
        )

    private fun toolCall(
        name: String,
        arguments: Map<String, Any> = emptyMap(),
    ): ToolCall = ToolCall(name, arguments)

    private companion object {
        const val RawTranscript = "Send 500 to Saman for food"
    }
}

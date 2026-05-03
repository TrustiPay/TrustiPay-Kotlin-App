package app.trustipay.offline

import app.trustipay.offline.domain.TransactionState
import app.trustipay.offline.domain.TransactionStateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionStateMachineTest {
    @Test
    fun offlineAccepted_cannotSettleWithoutServerValidation() {
        val machine = TransactionStateMachine()

        assertFalse(machine.canTransition(TransactionState.LOCAL_ACCEPTED_PENDING_SYNC, TransactionState.SETTLED))
        assertTrue(machine.canTransition(TransactionState.SERVER_VALIDATING, TransactionState.SETTLED))
    }
}

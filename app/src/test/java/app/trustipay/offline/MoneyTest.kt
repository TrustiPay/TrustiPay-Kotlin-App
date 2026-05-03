package app.trustipay.offline

import app.trustipay.offline.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun fromDecimalText_convertsToMinorUnits() {
        assertEquals(Money.lkr(150075), Money.fromDecimalText("1,500.75"))
    }

    @Test
    fun fromDecimalText_rejectsFractionalMinorUnits() {
        assertNull(Money.fromDecimalText("12.345"))
    }
}

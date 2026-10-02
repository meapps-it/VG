package it.meapps.gestionale

import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class DashboardPresentationTest {
    @Test
    fun sixMonthsIncludeEmptyMonthsAndYearBoundary() {
        val months =
            salesMonths(
                listOf(
                    OrderSummary(date = "2025-12-10", total = 100.0),
                    OrderSummary(date = "2026-01-03", total = 120.0, totalPaid = 30.0),
                    OrderSummary(date = "invalid", total = 999.0),
                ),
                YearMonth.of(2026, 3),
            )
        assertEquals(6, months.size)
        assertEquals(YearMonth.of(2025, 10), months.first().month)
        assertEquals(100.0, months[2].amount, .001)
        assertEquals(30.0, months[3].amount, .001)
        assertEquals(0.0, months.last().amount, .001)
    }

    @Test
    fun noOrdersProducesRealZeroValues() {
        assertTrue(salesMonths(emptyList(), YearMonth.of(2026, 1)).all { it.amount == 0.0 })
    }

    @Test
    fun unsupportedStatusDoesNotBecomeInProgress() {
        assertEquals("da verificare", orderStatusLabel("da_verificare"))
        assertEquals("Stato non indicato", orderStatusLabel(""))
    }

    @Test
    fun paymentsKeepExistingStatus() {
        assertEquals("Pagamento parziale", paymentLabel(OrderSummary(totalPaid = 10.0)))
        assertEquals("Pagato", paymentLabel(OrderSummary(paid = true)))
        assertEquals("Da pagare", paymentLabel(OrderSummary()))
        assertEquals("Rimborsato", paymentLabel(OrderSummary(paymentStatus = "rimborsato")))
    }
}

package it.meapps.gestionale

import org.junit.Assert.*
import org.junit.Test

class CoreLogicTest {
    @Test fun marginIsCalculatedFromTotalCost() {
        val p = Product(purchasePrice = 60.0, extraCosts = 10.0, salePrice = 100.0)
        assertEquals(70.0, p.totalCost, 0.001)
        assertEquals(30.0, p.marginEuro, 0.001)
        assertEquals(30.0, p.marginPercent, 0.001)
    }

    @Test fun zeroSalePriceDoesNotProduceInvalidPercentage() {
        assertEquals(0.0, Product(purchasePrice = 10.0).marginPercent, 0.001)
    }

    @Test fun navigationReturnsToPreviousTab() {
        val nav = NavigationHistory()
        nav.select(MainTab.ARCHIVES)
        nav.select(MainTab.SETTINGS)
        assertTrue(nav.back())
        assertEquals(MainTab.ARCHIVES, nav.current)
        assertTrue(nav.back())
        assertEquals(MainTab.HOME, nav.current)
        assertFalse(nav.back())
    }

    @Test fun draftRejectsBadQuantityAndAcceptsOptionalRelations() {
        assertNotNull(ProductDraft(name = "Test", quantity = "x").validate())
        assertNull(ProductDraft(name = "Test", quantity = "0").validate())
    }
}

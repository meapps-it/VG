package it.meapps.gestionale

import org.junit.Assert.assertEquals
import org.junit.Test

class VgTextScaleTest {
    @Test fun boundsAndLegacyValuesAreNormalized() {
        assertEquals(75, VgTextScale.normalize(-1))
        assertEquals(150, VgTextScale.normalize(200))
        assertEquals(100, VgTextScale.normalize(101))
        assertEquals(105, VgTextScale.normalize(103))
        for (value in 75..150 step 5) assertEquals(value, VgTextScale.normalize(value))
    }
    @Test fun systemAccessibilityScaleIsAppliedExactlyOnce() {
        assertEquals(100, VgTextScale.zoom(100, 1f))
        assertEquals(130, VgTextScale.zoom(100, 1.3f))
        assertEquals(195, VgTextScale.zoom(150, 1.3f))
        assertEquals(98, VgTextScale.zoom(75, 1.3f))
        assertEquals(300, VgTextScale.zoom(150, 2f))
    }
}

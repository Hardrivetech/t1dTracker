package com.hardrivetech.t1dtracker.insulin

import org.junit.Assert.assertEquals
import org.junit.Test

class InsulinCalculatorTest {
    @Test
    fun carbDose_basic() {
        assertEquals(2.0, InsulinCalculator.carbDose(30.0, 15.0), 1e-6)
    }

    @Test
    fun carbDose_zeroICR() {
        assertEquals(0.0, InsulinCalculator.carbDose(30.0, 0.0), 1e-6)
    }

    @Test
    fun correctionDose_positive() {
        assertEquals(1.0, InsulinCalculator.correctionDose(10.0, 5.0, 5.0), 1e-6)
    }

    @Test
    fun correctionDose_noCorrectionIfBelowTarget() {
        assertEquals(0.0, InsulinCalculator.correctionDose(4.0, 5.0, 2.0), 1e-6)
    }

    @Test
    fun roundDose_rounding() {
        assertEquals(1.5, InsulinCalculator.roundDose(1.47, 0.5), 1e-6)
    }

    @Test
    fun totalDose_combined() {
        val total = InsulinCalculator.totalDose(carbs = 30.0, icr = 15.0, current = 10.0, target = 5.0, isf = 5.0, rounding = 0.5)
        // carbDose = 2.0, correction = 1.0 => total 3.0 -> rounded to 3.0
        assertEquals(3.0, total, 1e-6)
    }
}

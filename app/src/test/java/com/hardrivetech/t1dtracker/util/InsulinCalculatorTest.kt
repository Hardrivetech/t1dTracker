package com.hardrivetech.t1dtracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class InsulinCalculatorTest {
    @Test
    fun carbDose_basic() {
        val carbs = 60.0
        val icr = 10.0
        val expected = 6.0
        assertEquals(expected, InsulinCalculator.carbDose(carbs, icr), 1e-6)
    }

    @Test
    fun correctionDose_basic() {
        val current = 180.0
        val target = 120.0
        val isf = 60.0
        val expected = 1.0
        assertEquals(expected, InsulinCalculator.correctionDose(current, target, isf), 1e-6)
    }

    @Test
    fun roundDose_roundsToStep() {
        val total = 2.34
        val step = 0.5
        val expected = 2.5
        assertEquals(expected, InsulinCalculator.roundDose(total, step), 1e-6)
    }
}

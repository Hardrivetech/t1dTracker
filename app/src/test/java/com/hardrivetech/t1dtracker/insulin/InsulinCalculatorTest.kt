package com.hardrivetech.t1dtracker.insulin

import org.junit.Assert.assertEquals
import org.junit.Test

class InsulinCalculatorTest {

    @Test
    fun testCalculateDose_basic() {
        val input = DoseInput(carbs = 30.0, icr = 10.0, currentGlucose = 150.0, targetGlucose = 100.0, isf = 50.0, rounding = 0.5)
        val result = InsulinCalculator.calculateDose(input)
        
        // 30/10 = 3.0 (carb)
        // (150-100)/50 = 1.0 (corr)
        // 3.0 + 1.0 = 4.0
        assertEquals(3.0, result.carbDose, 0.01)
        assertEquals(1.0, result.correctionDose, 0.01)
        assertEquals(4.0, result.totalDoseRounded, 0.01)
        assertEquals(0, result.warnings.size)
    }

    @Test
    fun testCalculateDose_rounding() {
        val input = DoseInput(carbs = 35.0, icr = 10.0, currentGlucose = 100.0, targetGlucose = 100.0, isf = 50.0, rounding = 0.5)
        val result = InsulinCalculator.calculateDose(input)
        
        // 35/10 = 3.5
        // (100-100)/50 = 0.0
        // 3.5 rounded by 0.5 = 3.5
        assertEquals(3.5, result.totalDoseRounded, 0.01)
        
        val input2 = DoseInput(carbs = 36.0, icr = 10.0, currentGlucose = 100.0, targetGlucose = 100.0, isf = 50.0, rounding = 1.0)
        val result2 = InsulinCalculator.calculateDose(input2)
        // 3.6 rounded by 1.0 = 4.0
        assertEquals(4.0, result2.totalDoseRounded, 0.01)
    }

    @Test
    fun testCalculateDose_warnings() {
        val input = DoseInput(carbs = 300.0, icr = 10.0, currentGlucose = 150.0, targetGlucose = 100.0, isf = 50.0, rounding = 0.5)
        val result = InsulinCalculator.calculateDose(input)
        // Total dose = 30 + 1 = 31 (>= 20 threshold)
        // Carbs = 300 (> 250 threshold)
        assertEquals(2, result.warnings.size)
    }
}

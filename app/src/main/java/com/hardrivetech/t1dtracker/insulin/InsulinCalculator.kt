package com.hardrivetech.t1dtracker.insulin

object InsulinCalculator {
    fun carbDose(carbs: Double, icr: Double): Double {
        if (icr <= 0.0) return 0.0
        return carbs / icr
    }

    fun correctionDose(current: Double, target: Double, isf: Double): Double {
        if (isf <= 0.0) return 0.0
        val diff = current - target
        return if (diff > 0) diff / isf else 0.0
    }

    fun roundDose(dose: Double, step: Double): Double {
        if (step <= 0.0) return dose
        return kotlin.math.round(dose / step) * step
    }

    fun totalDose(carbs: Double, icr: Double, current: Double, target: Double, isf: Double, rounding: Double): Double {
        val cDose = carbDose(carbs, icr)
        val corr = correctionDose(current, target, isf)
        val total = cDose + corr
        return roundDose(total, rounding)
    }
}

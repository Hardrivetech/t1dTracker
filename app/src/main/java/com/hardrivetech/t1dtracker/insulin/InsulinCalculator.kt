package com.hardrivetech.t1dtracker.insulin

data class DoseInput(
    val carbs: Double,
    val icr: Double,
    val current: Double,
    val target: Double,
    val isf: Double,
    val rounding: Double
)

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

    fun totalDose(input: DoseInput): Double {
        val cDose = carbDose(input.carbs, input.icr)
        val corr = correctionDose(input.current, input.target, input.isf)
        val total = cDose + corr
        return roundDose(total, input.rounding)
    }
}

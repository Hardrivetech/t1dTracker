package com.hardrivetech.t1dtracker.util

object InsulinCalculator {
    fun carbDose(carbs: Double, icr: Double): Double {
        return if (icr > 0.0) carbs / icr else 0.0
    }

    fun correctionDose(currentGlucose: Double, targetGlucose: Double, isf: Double): Double {
        return if (isf > 0.0 && currentGlucose > targetGlucose) (currentGlucose - targetGlucose) / isf else 0.0
    }

    fun totalDose(carbDose: Double, correctionDose: Double): Double {
        return carbDose + correctionDose
    }

    fun roundDose(total: Double, step: Double): Double {
        return if (step <= 0.0) total else (kotlin.math.round(total / step) * step)
    }
}

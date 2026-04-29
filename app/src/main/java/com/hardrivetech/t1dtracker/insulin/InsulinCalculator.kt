package com.hardrivetech.t1dtracker.insulin

import kotlin.math.round

data class DoseInput(
    val carbs: Double,
    val icr: Double,
    val currentGlucose: Double,
    val targetGlucose: Double,
    val isf: Double,
    val rounding: Double
)

data class DoseResult(
    val carbDose: Double,
    val correctionDose: Double,
    val totalDoseRaw: Double,
    val totalDoseRounded: Double,
    val warnings: List<String> = emptyList()
)

object InsulinCalculator {
    const val HIGH_DOSE_THRESHOLD = 20.0
    const val MAX_CARBS_THRESHOLD = 250.0
    const val MAX_BG_THRESHOLD = 600.0
    const val MIN_BG_THRESHOLD = 40.0

    fun calculateDose(input: DoseInput): DoseResult {
        val carbDose = if (input.icr > 0) input.carbs / input.icr else 0.0
        val correctionDose = if (input.isf > 0 && input.currentGlucose > input.targetGlucose) {
            (input.currentGlucose - input.targetGlucose) / input.isf
        } else {
            0.0
        }
        
        val totalRaw = carbDose + correctionDose
        val totalRounded = if (input.rounding > 0) {
            round(totalRaw / input.rounding) * input.rounding
        } else {
            totalRaw
        }

        val warnings = mutableListOf<String>()
        if (totalRounded >= HIGH_DOSE_THRESHOLD) warnings.add("High dose warning: >= $HIGH_DOSE_THRESHOLD U")
        if (input.carbs > MAX_CARBS_THRESHOLD) warnings.add("Extreme carbs warning: > $MAX_CARBS_THRESHOLD g")
        if (input.currentGlucose > MAX_BG_THRESHOLD) warnings.add("Extreme high BG warning: > $MAX_BG_THRESHOLD mg/dL")
        if (input.currentGlucose < MIN_BG_THRESHOLD && input.currentGlucose > 0) warnings.add("Extreme low BG warning: < $MIN_BG_THRESHOLD mg/dL")

        return DoseResult(
            carbDose = carbDose,
            correctionDose = correctionDose,
            totalDoseRaw = totalRaw,
            totalDoseRounded = totalRounded,
            warnings = warnings
        )
    }
}

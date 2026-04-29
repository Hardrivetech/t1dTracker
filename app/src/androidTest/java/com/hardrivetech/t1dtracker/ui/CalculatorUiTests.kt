package com.hardrivetech.t1dtracker.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardrivetech.t1dtracker.MainActivity
import com.hardrivetech.t1dtracker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorUiTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testCalculationAndSave() {
        // Find input fields and enter values
        // Note: Using hardcoded strings if resource labels are complex to match
        composeTestRule.onNodeWithText("Carbohydrates (g)").performTextInput("50")
        composeTestRule.onNodeWithText("ICR (g per unit)").performTextInput("10")
        composeTestRule.onNodeWithText("Current glucose (mg/dL)").performTextInput("150")
        composeTestRule.onNodeWithText("Target glucose (mg/dL)").performTextInput("100")
        composeTestRule.onNodeWithText("ISF (mg/dL per unit)").performTextInput("50")

        // Verify total dose calculation
        // Carb dose = 50/10 = 5U
        // Correction dose = (150-100)/50 = 1U
        // Total = 6U
        composeTestRule.onNodeWithText("Total dose (rounded 0.5U): 6 U").assertExists()

        // Click save
        composeTestRule.onNodeWithText("Save entry").performClick()

        // Verify confirmation dialog appears
        composeTestRule.onNodeWithText("Confirm Dose").assertExists()
        
        // Confirm save
        composeTestRule.onNodeWithText("Confirm & Save").performClick()

        // Verify entry saved message or UI reset
        composeTestRule.onNodeWithText("Entry saved").assertExists()
    }
}

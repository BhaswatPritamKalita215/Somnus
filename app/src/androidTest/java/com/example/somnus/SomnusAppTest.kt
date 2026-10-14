package com.example.somnus

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.somnus.theme.SomnusTheme
import com.example.somnus.ui.components.ScientificHonestyBadge
import org.junit.Rule
import org.junit.Test

class SomnusAppTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scientificHonestyBadge_displaysManualTimeInBed() {
        composeTestRule.setContent {
            SomnusTheme {
                ScientificHonestyBadge(isCorrected = false)
            }
        }
        composeTestRule.onNodeWithText("Time in bed (Manual)").assertExists()
    }

    @Test
    fun scientificHonestyBadge_displaysCorrectedState() {
        composeTestRule.setContent {
            SomnusTheme {
                ScientificHonestyBadge(isCorrected = true)
            }
        }
        composeTestRule.onNodeWithText("Time in bed (Manual • Corrected)").assertExists()
    }
}

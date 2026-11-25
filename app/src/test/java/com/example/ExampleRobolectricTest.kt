package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.ProductFormScreen
import com.example.ui.state.ProductFormController
import com.example.ui.theme.WarehouseLedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Ignore("Robolectric android-all SDK jar is not pre-cached in this offline container")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("حسابات المخزن", appName)
    }

    @Test
    fun `product form live calculation updates base cost from carton input`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()

        composeRule.setContent {
            val stateState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(controller.state)
            }
            WarehouseLedgerTheme {
                ProductFormScreen(
                    state = stateState.value,
                    onDismiss = {},
                    onNameChange = { controller.onNameChanged(it); stateState.value = controller.state },
                    onBarcodeChange = { controller.onBarcodeChanged(it); stateState.value = controller.state },
                    onBaseUnitNameChange = { controller.onBaseUnitNameChanged(it); stateState.value = controller.state },
                    onDefaultUnitChange = { controller.onDefaultUnitChanged(it); stateState.value = controller.state },
                    onAddTier = { controller.addUnitTier(); stateState.value = controller.state },
                    onRemoveTier = { controller.removeUnitTier(it); stateState.value = controller.state },
                    onTierNameChange = { idx, n -> controller.onUnitTierNameChanged(idx, n); stateState.value = controller.state },
                    onTierFactorChange = { idx, f -> controller.onUnitTierFactorChanged(idx, f); stateState.value = controller.state },
                    onLargeUnitCostChange = { controller.onLargeUnitCostChanged(it); stateState.value = controller.state },
                    onBaseCostManualChange = { controller.onBaseCostManuallyChanged(it); stateState.value = controller.state },
                    onBaseSellPriceChange = { controller.onBaseSellPriceChanged(it); stateState.value = controller.state },
                    onStockBaseQtyChange = { controller.onStockBaseQtyChanged(it); stateState.value = controller.state },
                    onMinStockAlertChange = { controller.onMinStockAlertChanged(it); stateState.value = controller.state },
                    onTrackExpiryChange = { controller.onTrackExpiryChanged(it); stateState.value = controller.state },
                    onExpiryDateChange = { controller.onExpiryDateChanged(it); stateState.value = controller.state },
                    onSubmit = {}
                )
            }
        }

        composeRule.onNodeWithTag("tier_factor_input_0").performTextInput("24")
        composeRule.onNodeWithTag("large_unit_cost_input").performTextInput("2400")
        composeRule.waitForIdle()

        assertEquals(100.0, controller.state.baseCost, 0.0001)
        assertEquals("100", controller.state.baseCostText)
        composeRule.onNodeWithTag("save_product_button").assertIsDisplayed()
    }
}

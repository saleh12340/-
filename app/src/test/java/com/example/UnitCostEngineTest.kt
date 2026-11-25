package com.example

import com.example.domain.NumberFormatUtils
import com.example.domain.Product
import com.example.domain.UnitCostEngine
import com.example.domain.UnitTier
import com.example.ui.state.ProductFormController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitCostEngineTest {

    @Test
    fun `case 01 basic calculation 2400 divided by 24 equals 100`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("2400")

        assertEquals(100.0, controller.state.baseCost, 0.0001)
        assertEquals("100", controller.state.baseCostText)
        assertNull(controller.state.costWarning)
    }

    @Test
    fun `case 02 changing carton cost from 2400 to 3000 updates piece cost to 125`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("2400")
        assertEquals(100.0, controller.state.baseCost, 0.0001)

        controller.onLargeUnitCostChanged("3000")
        assertEquals(125.0, controller.state.baseCost, 0.0001)
        assertEquals("125", controller.state.baseCostText)
    }

    @Test
    fun `case 03 changing conversion factor from 24 to 30 updates piece cost from 3000 to 100`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("3000")
        assertEquals(125.0, controller.state.baseCost, 0.0001)

        controller.onUnitTierFactorChanged(0, "30")
        assertEquals(100.0, controller.state.baseCost, 0.0001)
        assertEquals("100", controller.state.baseCostText)
    }

    @Test
    fun `case 04 intermediate unit tier 12000 divided by (5 x 24) equals 100`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24") // كرتون = 24 حبة
        controller.addUnitTier() // قرطاس
        controller.onUnitTierNameChanged(1, "قرطاس")
        controller.onUnitTierFactorChanged(1, "5") // قرطاس = 5 كرتون = 120 حبة

        assertEquals(120.0, controller.state.cumulativeFactor ?: 0.0, 0.0001)

        controller.onLargeUnitCostChanged("12000")
        assertEquals(100.0, controller.state.baseCost, 0.0001)
        assertEquals("100", controller.state.baseCostText)
    }

    @Test
    fun `case 05 manual override 100 to 105 is preserved and saved without clobbering`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onNameChanged("عصير")
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("2400")
        assertEquals(100.0, controller.state.baseCost, 0.0001)

        controller.onBaseCostManuallyChanged("105")
        assertEquals(105.0, controller.state.baseCost, 0.0001)
        assertTrue(controller.state.hasManualBaseCostOverride)

        // Changing unrelated product name must NOT revert 105 back to 100
        controller.onNameChanged("عصير مانجو")
        assertEquals(105.0, controller.state.baseCost, 0.0001)
        assertEquals("105", controller.state.baseCostText)

        val submitted = controller.validateForSubmit().getOrThrow()
        assertEquals(105.0, submitted.baseCost, 0.0001)
    }

    @Test
    fun `case 06 opening saved product calculates large unit cost from saved baseCost without mutating data`() {
        val controller = ProductFormController()
        val savedProduct100 = Product(
            id = "p1",
            name = "عصير",
            baseUnitName = "حبة",
            defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 24.0)),
            baseCost = 100.0,
            baseSellPrice = 150.0,
            stockBaseQty = 48.0
        )
        controller.loadExistingProduct(savedProduct100)
        assertEquals("2400", controller.state.largeUnitCostText)
        assertEquals("100", controller.state.baseCostText)
        assertEquals(100.0, controller.state.baseCost, 0.0001)

        val savedProduct105 = savedProduct100.copy(baseCost = 105.0)
        controller.loadExistingProduct(savedProduct105)
        assertEquals("2520", controller.state.largeUnitCostText)
        assertEquals("105", controller.state.baseCostText)
        assertEquals(105.0, controller.state.baseCost, 0.0001)
    }

    @Test
    fun `case 07 zero conversion factor rejects division and preserves last valid baseCost`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("2400")
        assertEquals(100.0, controller.state.baseCost, 0.0001)

        controller.onUnitTierFactorChanged(0, "0")
        assertEquals(100.0, controller.state.baseCost, 0.0001)
        assertNotNull(controller.state.costWarning)
    }

    @Test
    fun `case 08 blank conversion factor does not divide and preserves last valid baseCost`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("2400")
        assertEquals(100.0, controller.state.baseCost, 0.0001)

        controller.onUnitTierFactorChanged(0, "")
        assertEquals(100.0, controller.state.baseCost, 0.0001)
        assertNotNull(controller.state.costWarning)
    }

    @Test
    fun `case 09 negative carton cost is rejected and preserves last valid baseCost`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("2400")
        assertEquals(100.0, controller.state.baseCost, 0.0001)

        controller.onLargeUnitCostChanged("-2400")
        assertEquals(100.0, controller.state.baseCost, 0.0001)
        assertNotNull(controller.state.costWarning)
    }

    @Test
    fun `case 10 repeating decimal stores full precision while displaying 2 decimals`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onNameChanged("شاي")
        controller.onUnitTierFactorChanged(0, "3")
        controller.onLargeUnitCostChanged("1000")

        // Stored full precision: 1000 / 3 = 333.3333333333333
        assertEquals(1000.0 / 3.0, controller.state.baseCost, 1e-9)
        // Displayed 2 decimals
        assertEquals("333.33", controller.state.baseCostText)

        // When saved and reopened, 333.3333333333333 * 3 reconstructs exact 1000
        val saved = Product(
            id = "p_tea",
            name = "شاي",
            baseUnitName = "حبة",
            defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 3.0)),
            baseCost = controller.state.baseCost
        )
        controller.loadExistingProduct(saved)
        assertEquals("1000", controller.state.largeUnitCostText)
        assertEquals("333.33", controller.state.baseCostText)
        assertEquals(1000.0 / 3.0, controller.state.baseCost, 1e-9)
    }

    @Test
    fun `case 11 purchase invoice 2 cartons x 2400 adds 48 base units worth 4800`() {
        val product = Product(
            id = "p1",
            name = "عصير",
            baseUnitName = "حبة",
            defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 24.0), UnitTier("قرطاس", 5.0)),
            baseCost = 0.0,
            baseSellPrice = 150.0,
            stockBaseQty = 0.0
        )

        val res = UnitCostEngine.computePurchaseLine(
            product = product,
            unitName = "كرتون",
            unitQty = 2.0,
            unitPrice = 2400.0
        ).getOrThrow()

        assertEquals(48.0, res.line.baseQty, 0.0001)
        assertEquals(4800.0, res.line.lineTotal, 0.0001)
        assertEquals(48.0, res.newStockBaseQty, 0.0001)
        assertEquals(100.0, res.updatedBaseCost, 0.0001)

        // Multi-tier purchase: 3 qirtas (3 * 5 * 24 = 360 base units)
        val qirtasRes = UnitCostEngine.computePurchaseLine(
            product = product,
            unitName = "قرطاس",
            unitQty = 3.0,
            unitPrice = 12000.0
        ).getOrThrow()

        assertEquals(360.0, qirtasRes.line.baseQty, 0.0001)
        assertEquals(36000.0, qirtasRes.line.lineTotal, 0.0001)
        assertEquals(100.0, qirtasRes.updatedBaseCost, 0.0001)
    }

    @Test
    fun `case 12 sales invoice 10 pieces x 150 equals 1500 and carton sell price equals 3600`() {
        val product = Product(
            id = "p1",
            name = "عصير",
            baseUnitName = "حبة",
            defaultUnitName = "حبة",
            unitTiers = listOf(UnitTier("كرتون", 24.0)),
            baseCost = 100.0,
            baseSellPrice = 150.0,
            stockBaseQty = 48.0
        )

        val pieceSale = UnitCostEngine.computeSaleLine(
            product = product,
            unitName = "حبة",
            unitQty = 10.0
        ).getOrThrow()

        assertEquals(1500.0, pieceSale.line.lineTotal, 0.0001)
        assertEquals(38.0, pieceSale.newStockBaseQty, 0.0001)

        val cartonSellPrice = UnitCostEngine.calculateUnitSellPrice(product.baseSellPrice, 24.0)
        assertEquals(3600.0, cartonSellPrice, 0.0001)
    }

    @Test
    fun `changing unit name keeps linked conversion factor and calculation active`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onUnitTierFactorChanged(0, "24")
        controller.onLargeUnitCostChanged("2400")
        assertEquals(100.0, controller.state.baseCost, 0.0001)

        controller.onUnitTierNameChanged(0, "قرطاس")
        assertEquals("قرطاس", controller.state.largestUnitName)
        assertEquals(24.0, controller.state.cumulativeFactor ?: 0.0, 0.0001)

        controller.onLargeUnitCostChanged("4800")
        assertEquals(200.0, controller.state.baseCost, 0.0001)
    }

    @Test
    fun `duplicate or circular unit names are rejected`() {
        val res = UnitCostEngine.resolveUnitLevels(
            baseUnitName = "حبة",
            tiers = listOf(UnitTier("كرتون", 24.0), UnitTier("حبة", 5.0))
        )
        assertTrue(res.isFailure)

        val resDup = UnitCostEngine.resolveUnitLevels(
            baseUnitName = "حبة",
            tiers = listOf(UnitTier("كرتون", 24.0), UnitTier("كرتون", 5.0))
        )
        assertTrue(resDup.isFailure)
    }

    @Test
    fun `stock quantity and total inventory value are protected when conversion factor changes`() {
        val recon = UnitCostEngine.reconcileStockOnUnitChange(
            stockBaseQty = 48.0,
            baseCost = 100.0,
            oldLargestFactor = 24.0,
            newLargestFactor = 30.0
        )
        assertEquals(48.0, recon.preservedStockBaseQty, 0.0001)
        assertEquals(4800.0, recon.preservedTotalValue, 0.0001)
        assertEquals(100.0, recon.preservedBaseCost, 0.0001)
        assertEquals(2.0, recon.previousLargestUnitCount, 0.0001)
        assertEquals(1.6, recon.newLargestUnitCount, 0.0001)
        assertEquals(3000.0, recon.newLargestUnitCost, 0.0001)
    }

    @Test
    fun `arabic and persian numerals normalize cleanly to english digits`() {
        assertEquals("2400.5", NumberFormatUtils.toEnglishDigits("٢٤٠٠٫٥"))
        assertEquals("30", NumberFormatUtils.toEnglishDigits("۳۰"))
        assertFalse(NumberFormatUtils.formatNumber(2400.0).contains('٢'))
    }
}

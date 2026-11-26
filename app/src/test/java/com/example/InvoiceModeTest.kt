package com.example

import com.example.data.InvoiceDraftLine
import com.example.data.LocalWarehouseDataSource
import com.example.domain.InvoiceMode
import com.example.domain.InvoiceType
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.UnitTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceModeTest {

    @Test
    fun `free sale invoice is stored as free and leaves inventory unchanged`() = runBlocking {
        val dataSource = LocalWarehouseDataSource(storageFile = null)
        val inventoryId = dataSource.saveProduct(
            userId = "u1", existingId = null, name = "ماء", barcode = "",
            baseUnitName = "حبة", defaultUnitName = "حبة", unitTiers = emptyList(),
            baseCost = 10.0, baseSellPrice = 15.0, stockBaseQty = 30.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val freeLine = Product(
            id = "draft_free_sale", userId = "u1", name = "خدمة توصيل",
            baseUnitName = "خدمة", defaultUnitName = "خدمة"
        )

        val invoice = dataSource.postInvoice(
            "u1", "FREE-SALE-1", InvoiceType.SALE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "خدمة", 2.0, 500.0, freeLine, InvoiceMode.FREE)), ""
        ).getOrThrow()

        assertEquals(InvoiceMode.FREE, invoice.resolvedMode)
        assertEquals(30.0, dataSource.observeProducts("u1").first().single { it.id == inventoryId }.stockBaseQty, 1e-9)
    }

    @Test
    fun `free purchase creates inventory only on commit and persists mode in backup`() = runBlocking {
        val dataSource = LocalWarehouseDataSource(storageFile = null)
        val draft = Product(
            id = "draft_free_purchase", userId = "u1", name = "مناديل",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 24.0)),
            baseCost = 2500.0 / 24.0, baseSellPrice = 0.0, stockBaseQty = 0.0
        )

        assertTrue(dataSource.observeProducts("u1").first().isEmpty())
        val invoice = dataSource.postInvoice(
            "u1", "FREE-PURCHASE-1", InvoiceType.PURCHASE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "كرتون", 2.0, 2500.0, draft, InvoiceMode.FREE)), ""
        ).getOrThrow()

        assertEquals(InvoiceMode.FREE, invoice.resolvedMode)
        val savedProduct = dataSource.observeProducts("u1").first().single()
        assertEquals(48.0, savedProduct.stockBaseQty, 1e-9)
        val backup = dataSource.exportBackup("u1").getOrThrow()
        assertEquals(InvoiceMode.FREE, backup.invoices.single().resolvedMode)
    }

    @Test
    fun `mixed stock and free invoice lines are rejected before inventory changes`() = runBlocking {
        val dataSource = LocalWarehouseDataSource(storageFile = null)
        val stockId = dataSource.saveProduct(
            userId = "u1", existingId = null, name = "سكر", barcode = "",
            baseUnitName = "كجم", defaultUnitName = "كجم", unitTiers = emptyList(),
            baseCost = 100.0, baseSellPrice = 120.0, stockBaseQty = 20.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val freeProduct = Product(
            id = "draft_mixed", userId = "u1", name = "خدمة",
            baseUnitName = "خدمة", defaultUnitName = "خدمة"
        )

        val result = dataSource.postInvoice(
            "u1", "MIXED-1", InvoiceType.SALE, PaymentMethod.CASH, "", "",
            listOf(
                InvoiceDraftLine(stockId, "كجم", 1.0, 120.0, invoiceMode = InvoiceMode.STOCK),
                InvoiceDraftLine("", "خدمة", 1.0, 50.0, freeProduct, InvoiceMode.FREE)
            ), ""
        )

        assertTrue(result.isFailure)
        assertEquals(20.0, dataSource.observeProducts("u1").first().single().stockBaseQty, 1e-9)
    }
    @Test
    fun `free purchase refuses same-name item with incompatible unit conversion`() = runBlocking {
        val dataSource = LocalWarehouseDataSource(storageFile = null)
        val existingId = dataSource.saveProduct(
            userId = "u1", existingId = null, name = "مناديل", barcode = "",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 24.0)),
            baseCost = 100.0, baseSellPrice = 150.0, stockBaseQty = 48.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val incompatibleDraft = Product(
            id = "draft_bad_units", userId = "u1", name = "مناديل",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 20.0)),
            baseCost = 120.0, baseSellPrice = 150.0
        )

        val result = dataSource.postInvoice(
            "u1", "FREE-PURCHASE-BAD-UNITS", InvoiceType.PURCHASE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "كرتون", 1.0, 2400.0, incompatibleDraft, InvoiceMode.FREE)), ""
        )

        assertTrue(result.isFailure)
        assertEquals(1, dataSource.observeProducts("u1").first().size)
        assertEquals(48.0, dataSource.observeProducts("u1").first().single { it.id == existingId }.stockBaseQty, 1e-9)
    }

}

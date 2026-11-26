package com.example

import com.example.data.InvoiceDraftLine
import com.example.data.LocalWarehouseDataSource
import com.example.domain.Contact
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceLine
import com.example.domain.InvoiceType
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.SmartSuggestionEngine
import com.example.domain.UnitTier
import com.example.export.ShareHelper
import com.example.print.EscPosPrinter
import com.example.ui.state.ProductFormController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAndDuplicateTest {

    @Test
    fun `validateForSubmit preserves full precision calculated baseCost for repeating decimals`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onNameChanged("شاي أخضر")
        controller.onUnitTierFactorChanged(0, "3")
        controller.onLargeUnitCostChanged("1000")

        val validated = controller.validateForSubmit().getOrThrow()
        assertEquals(1000.0 / 3.0, validated.baseCost, 1e-9)
        assertEquals("333.33", controller.state.baseCostText)
    }

    @Test
    fun `duplicate unit names in ProductFormController fail validation`() {
        val controller = ProductFormController()
        controller.resetForNewProduct()
        controller.onNameChanged("حليب")
        controller.onUnitTierFactorChanged(0, "12")
        controller.addUnitTier()
        controller.onUnitTierNameChanged(1, "كرتون") // duplicates tier 0 name
        controller.onUnitTierFactorChanged(1, "4")

        val result = controller.validateForSubmit()
        assertTrue(result.isFailure)
    }

    @Test
    fun `esc pos raster encoder emits valid header, GS v 0 band, pixel bits, and paper cut`() {
        // 8px wide x 2px tall: row 0 = 10100000 (0xA0), row 1 = 00000001 (0x01)
        val bytes = EscPosPrinter.packMonochromeRowsToEscPosRaster(width = 8, height = 2) { x, y ->
            (y == 0 && (x == 0 || x == 2)) || (y == 1 && x == 7)
        }
        val expected = byteArrayOf(
            0x1B, 0x40, // ESC @
            0x1D, 0x76, 0x30, 0x00, 0x01, 0x00, 0x02, 0x00, // GS v 0, 1 byte/row, 2 rows
            0xA0.toByte(), // row 0
            0x01.toByte(), // row 1
            0x1B, 0x64, 0x04, // ESC d 4
            0x1D, 0x56, 0x42, 0x00 // GS V 66 0
        )
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `whatsapp invoice text formatting includes english digits, units, and grand total`() {
        val invoice = Invoice(
            id = "inv_1",
            invoiceNumber = "SAL-1001",
            invoiceType = InvoiceType.SALE.name,
            paymentMethod = PaymentMethod.CREDIT.name,
            contactName = "بقالة النور",
            totalAmount = 4800.0,
            lines = listOf(
                InvoiceLine(
                    productId = "p1",
                    productName = "عصير",
                    unitName = "كرتون",
                    cumulativeFactor = 24.0,
                    unitQty = 2.0,
                    unitPrice = 2400.0,
                    baseQty = 48.0,
                    lineTotal = 4800.0
                )
            )
        )

        val formatted = ShareHelper.formatInvoiceWhatsAppText(invoice, "متجر البركة")
        assertTrue(formatted.contains("SAL-1001"))
        assertTrue(formatted.contains("بقالة النور"))
        assertTrue(formatted.contains("4,800"))
        assertTrue(formatted.contains("48"))
        assertTrue(formatted.contains("كرتون"))
    }

    @Test
    fun `smart product suggestions match Arabic variants, prioritize barcode and prefix matches`() {
        val juice = Product(id = "p1", name = "عصير برتقال", barcode = "6281001", stockBaseQty = 20.0)
        val tea = Product(id = "p2", name = "شاي أخضر", barcode = "6282002", stockBaseQty = 5.0)
        val dates = Product(id = "p3", name = "أرز بسمتي", barcode = "6283003", stockBaseQty = 0.0)
        val list = listOf(tea, dates, juice)

        // Arabic alef normalization: "ارز" matches "أرز بسمتي"
        val arabicMatch = SmartSuggestionEngine.suggestProducts("ارز", list, InvoiceType.SALE)
        assertEquals("p3", arabicMatch.first().id)

        // Barcode match (even with Arabic-Indic digits)
        val barcodeMatch = SmartSuggestionEngine.suggestProducts("٦٢٨١٠٠١", list, InvoiceType.SALE)
        assertEquals("p1", barcodeMatch.first().id)

        // Blank query in SALE prioritizes highest stock first
        val defaultSale = SmartSuggestionEngine.suggestProducts("", list, InvoiceType.SALE)
        assertEquals(listOf("p1", "p2", "p3"), defaultSale.map { it.id })
    }

    @Test
    fun `smart contact suggestions combine registered contacts and recent invoice names without duplicates`() {
        val contacts = listOf(
            Contact(id = "c1", name = "أحمد صالح", contactType = ContactType.CUSTOMER.name, balance = 1200.0),
            Contact(id = "c2", name = "مؤسسة التوريد", contactType = ContactType.SUPPLIER.name, balance = 5000.0)
        )
        val invoices = listOf(
            Invoice(id = "i1", invoiceType = InvoiceType.SALE.name, contactName = "احمد صالح"),
            Invoice(id = "i2", invoiceType = InvoiceType.SALE.name, contactName = "خالد النقدي")
        )

        val saleSuggestions = SmartSuggestionEngine.suggestContacts("", contacts, invoices, InvoiceType.SALE)
        assertEquals(listOf("أحمد صالح", "خالد النقدي"), saleSuggestions.map { it.displayName })
        assertEquals(false, saleSuggestions[0].fromHistoryOnly)
        assertEquals(true, saleSuggestions[1].fromHistoryOnly)

        val filtered = SmartSuggestionEngine.suggestContacts("خالد", contacts, invoices, InvoiceType.SALE)
        assertEquals(1, filtered.size)
        assertEquals("خالد النقدي", filtered.first().displayName)
    }

    @Test
    fun `inferred unit price divides total by quantity accurately`() {
        assertEquals(250.0, SmartSuggestionEngine.computeInferredUnitPrice(1000.0, 4.0, 200.0), 1e-9)
        assertEquals(200.0, SmartSuggestionEngine.computeInferredUnitPrice(1000.0, 0.0, 200.0), 1e-9)
    }

    @Test
    fun `contact edit updates balance and delete removes contact`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val userId = "u1"
        val cid = ds.saveContact(userId, null, "أحمد", ContactType.CUSTOMER, "777", 500.0, "").getOrThrow()

        ds.saveContact(userId, cid, "أحمد المعدل", ContactType.CUSTOMER, "778", 1250.0, "ملاحظة").getOrThrow()
        val updated = ds.observeContacts(userId).first().single()
        assertEquals("أحمد المعدل", updated.name)
        assertEquals("778", updated.phone)
        assertEquals(1250.0, updated.balance, 1e-9)

        ds.deleteContact(userId, cid).getOrThrow()
        assertTrue(ds.observeContacts(userId).first().isEmpty())
    }

    @Test
    fun `invoice update and delete atomically reverse and reapply stock and credit balance`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val userId = "u1"
        val pid = ds.saveProduct(
            userId = userId,
            existingId = null,
            name = "ماء",
            barcode = "",
            baseUnitName = "حبة",
            defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 12.0)),
            baseCost = 100.0,
            baseSellPrice = 150.0,
            stockBaseQty = 120.0,
            trackExpiry = false,
            expiryDate = "",
            minStockAlert = 0.0
        ).getOrThrow()
        val cid = ds.saveContact(userId, null, "سعيد", ContactType.CUSTOMER, "", 0.0, "").getOrThrow()

        val created = ds.postInvoice(
            userId = userId,
            invoiceNumber = "0001",
            invoiceType = InvoiceType.SALE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = cid,
            contactName = "سعيد",
            draftLines = listOf(InvoiceDraftLine(pid, "كرتون", 2.0, 1800.0)), // 24 base units, 3600 total
            notes = ""
        ).getOrThrow()

        assertEquals(96.0, ds.observeProducts(userId).first().single().stockBaseQty, 1e-9)
        assertEquals(3600.0, ds.observeContacts(userId).first().single().balance, 1e-9)

        ds.updateInvoice(
            userId = userId,
            invoiceId = created.id,
            invoiceNumber = "0001",
            invoiceType = InvoiceType.SALE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = cid,
            contactName = "سعيد",
            draftLines = listOf(InvoiceDraftLine(pid, "كرتون", 3.0, 1800.0)), // 36 base units, 5400 total
            notes = "معدلة"
        ).getOrThrow()

        assertEquals(84.0, ds.observeProducts(userId).first().single().stockBaseQty, 1e-9)
        assertEquals(5400.0, ds.observeContacts(userId).first().single().balance, 1e-9)

        ds.deleteInvoice(userId, created.id).getOrThrow()
        assertEquals(120.0, ds.observeProducts(userId).first().single().stockBaseQty, 1e-9)
        assertEquals(0.0, ds.observeContacts(userId).first().single().balance, 1e-9)
        assertTrue(ds.observeInvoices(userId).first().isEmpty())
    }

    @Test
    fun `payment update and delete accurately adjust and restore contact balance`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val userId = "u1"
        val cid = ds.saveContact(userId, null, "عميل 1", ContactType.CUSTOMER, "", 1000.0, "").getOrThrow()

        ds.recordPayment(userId, cid, 400.0, "دفعة").getOrThrow()
        val payId = ds.observePayments(userId).first().single().id
        assertEquals(600.0, ds.observeContacts(userId).first().single().balance, 1e-9)

        ds.updatePayment(userId, payId, 250.0, "دفعة معدلة").getOrThrow()
        assertEquals(750.0, ds.observeContacts(userId).first().single().balance, 1e-9)

        ds.deletePayment(userId, payId).getOrThrow()
        assertEquals(1000.0, ds.observeContacts(userId).first().single().balance, 1e-9)
        assertTrue(ds.observePayments(userId).first().isEmpty())
    }


    @Test
    fun `new purchase draft product is created only when invoice is committed`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val userId = "u1"
        val transient = Product(
            id = "draft_purchase_1", userId = userId, name = "مناديل",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 24.0)),
            baseCost = 100.0, baseSellPrice = 150.0, stockBaseQty = 0.0
        )
        assertTrue(ds.observeProducts(userId).first().isEmpty())
        val invoice = ds.postInvoice(
            userId, "PUR-DRAFT-1", InvoiceType.PURCHASE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "كرتون", 2.0, 2400.0, transient)), ""
        ).getOrThrow()
        val savedProducts = ds.observeProducts(userId).first()
        assertEquals(1, savedProducts.size)
        assertEquals(48.0, savedProducts.single().stockBaseQty, 1e-9)
        assertEquals(savedProducts.single().id, invoice.lines.single().productId)
        assertEquals("مناديل", invoice.lines.single().productName)
    }

    @Test
    fun `free sale draft product stays in invoice and never changes inventory`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val userId = "u1"
        val existingId = ds.saveProduct(
            userId = userId, existingId = null, name = "ماء", barcode = "",
            baseUnitName = "حبة", defaultUnitName = "حبة", unitTiers = emptyList(),
            baseCost = 10.0, baseSellPrice = 15.0, stockBaseQty = 30.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val freeItem = Product(
            id = "draft_free_1", userId = userId, name = "خدمة توصيل",
            baseUnitName = "خدمة", defaultUnitName = "خدمة",
            baseCost = 0.0, baseSellPrice = 0.0
        )
        val invoice = ds.postInvoice(
            userId, "SAL-FREE-1", InvoiceType.SALE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "خدمة", 2.0, 500.0, freeItem)), ""
        ).getOrThrow()
        assertEquals(1, ds.observeProducts(userId).first().size)
        assertEquals(30.0, ds.observeProducts(userId).first().single { it.id == existingId }.stockBaseQty, 1e-9)
        assertEquals("", invoice.lines.single().productId)
        assertEquals("خدمة توصيل", invoice.lines.single().productName)
        assertEquals(1000.0, invoice.totalAmount, 1e-9)
        ds.deleteInvoice(userId, invoice.id).getOrThrow()
        assertEquals(30.0, ds.observeProducts(userId).first().single { it.id == existingId }.stockBaseQty, 1e-9)
    }

    @Test
    fun `free sale invoice can be edited without creating products or affecting stock`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val userId = "u1"
        val freeItem = Product(
            id = "draft_free_edit", userId = userId, name = "أجرة نقل",
            baseUnitName = "خدمة", defaultUnitName = "خدمة"
        )
        val created = ds.postInvoice(
            userId, "SAL-FREE-EDIT", InvoiceType.SALE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "خدمة", 1.0, 200.0, freeItem)), ""
        ).getOrThrow()
        val updatedItem = freeItem.copy(id = "draft_free_edit_2", name = "أجرة تحميل")
        val updated = ds.updateInvoice(
            userId, created.id, "SAL-FREE-EDIT", InvoiceType.SALE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "خدمة", 3.0, 150.0, updatedItem)), "تعديل"
        ).getOrThrow()
        assertEquals("أجرة تحميل", updated.lines.single().productName)
        assertEquals(450.0, updated.totalAmount, 1e-9)
        assertTrue(ds.observeProducts(userId).first().isEmpty())
    }
}

package com.example

import com.example.data.BackupJsonCodec
import com.example.data.InvoiceDraftLine
import com.example.data.LocalWarehouseDataSource
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceLine
import com.example.domain.InvoiceMode
import com.example.domain.InvoiceType
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.UnitCostEngine
import com.example.domain.UnitTier
import com.example.export.ShareHelper
import com.example.print.EscPosPrinter
import com.example.ui.state.WarehouseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceModeTest {

    // Scenario 1: إنشاء فاتورة بيع من المخزن والتأكد من استمرار عملها
    @Test
    fun `1_stock_sale_invoice_deducts_inventory_and_updates_customer_balance`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val pid = ds.saveProduct(
            userId = "u1", existingId = null, name = "ماء صافي", barcode = "1001",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 12.0)),
            baseCost = 80.0, baseSellPrice = 120.0, stockBaseQty = 60.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val cid = ds.saveContact("u1", null, "بقالة الوفاء", ContactType.CUSTOMER, "777", 0.0, "").getOrThrow()

        val invoice = ds.postInvoice(
            userId = "u1",
            invoiceNumber = "STK-SALE-1",
            invoiceType = InvoiceType.SALE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = cid,
            contactName = "بقالة الوفاء",
            draftLines = listOf(
                InvoiceDraftLine(pid, "كرتون", 2.0, 1440.0, invoiceMode = InvoiceMode.STOCK)
            ),
            notes = ""
        ).getOrThrow()

        assertEquals(InvoiceMode.STOCK, invoice.resolvedMode)
        assertEquals(36.0, ds.observeProducts("u1").first().single().stockBaseQty, 1e-9)
        assertEquals(2880.0, ds.observeContacts("u1").first().single().balance, 1e-9)
    }

    // Scenario 2: إنشاء فاتورة شراء من المخزن والتأكد من استمرار عملها
    @Test
    fun `2_stock_purchase_invoice_adds_inventory_and_updates_weighted_cost`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val pid = ds.saveProduct(
            userId = "u1", existingId = null, name = "عصير", barcode = "2002",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 24.0)),
            baseCost = 100.0, baseSellPrice = 150.0, stockBaseQty = 24.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val sid = ds.saveContact("u1", null, "مؤسسة الأمل", ContactType.SUPPLIER, "771", 0.0, "").getOrThrow()

        val invoice = ds.postInvoice(
            userId = "u1",
            invoiceNumber = "STK-PUR-1",
            invoiceType = InvoiceType.PURCHASE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = sid,
            contactName = "مؤسسة الأمل",
            draftLines = listOf(
                InvoiceDraftLine(pid, "كرتون", 1.0, 3600.0, invoiceMode = InvoiceMode.STOCK)
            ),
            notes = ""
        ).getOrThrow()

        val updatedProd = ds.observeProducts("u1").first().single()
        assertEquals(InvoiceMode.STOCK, invoice.resolvedMode)
        assertEquals(48.0, updatedProd.stockBaseQty, 1e-9)
        // Weighted avg: (24 * 100 + 24 * 150) / 48 = 125.0
        assertEquals(125.0, updatedProd.baseCost, 1e-9)
        assertEquals(3600.0, ds.observeContacts("u1").first().single().balance, 1e-9)
    }

    // Scenario 3: إنشاء فاتورة بيع حرة مع الوصف والخصم والمدفوع والمتبقي والتأكد من عدم تغيير المخزون
    @Test
    fun `3_free_sale_with_description_discount_and_partial_pay_never_touches_inventory`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val stockId = ds.saveProduct(
            userId = "u1", existingId = null, name = "ماء", barcode = "",
            baseUnitName = "حبة", defaultUnitName = "حبة", unitTiers = emptyList(),
            baseCost = 10.0, baseSellPrice = 15.0, stockBaseQty = 30.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val cid = ds.saveContact("u1", null, "عميل حر", ContactType.CUSTOMER, "770", 100.0, "").getOrThrow()

        val invoice = ds.postInvoice(
            userId = "u1",
            invoiceNumber = "FREE-SALE-1",
            invoiceType = InvoiceType.SALE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = cid,
            contactName = "عميل حر",
            draftLines = listOf(
                InvoiceDraftLine(
                    productId = "",
                    unitName = "صندوق",
                    unitQty = 2.0,
                    unitPrice = 600.0,
                    draftProduct = Product(
                        id = "free_1", userId = "u1", name = "خضار طازجة",
                        baseUnitName = "صندوق", defaultUnitName = "صندوق"
                    ),
                    invoiceMode = InvoiceMode.FREE,
                    productName = "خضار طازجة",
                    description = "طماطم وخيار درجة أولى"
                )
            ),
            notes = "توصيل صباحي",
            discount = 150.0,
            paidAmount = 450.0
        ).getOrThrow()

        assertEquals(InvoiceMode.FREE, invoice.resolvedMode)
        assertEquals("", invoice.lines.single().productId)
        assertEquals("خضار طازجة", invoice.lines.single().productName)
        assertEquals("طماطم وخيار درجة أولى", invoice.lines.single().description)
        assertEquals(1200.0, invoice.subtotalAmount, 1e-9)
        assertEquals(150.0, invoice.discount, 1e-9)
        assertEquals(1050.0, invoice.totalAmount, 1e-9)
        assertEquals(450.0, invoice.resolvedPaidAmount, 1e-9)
        assertEquals(600.0, invoice.resolvedRemainingAmount, 1e-9)

        // Stock completely untouched & no new product created
        val products = ds.observeProducts("u1").first()
        assertEquals(1, products.size)
        assertEquals(30.0, products.single { it.id == stockId }.stockBaseQty, 1e-9)

        // Customer balance increased by remaining only (100 + 600 = 700)
        assertEquals(700.0, ds.observeContacts("u1").first().single { it.id == cid }.balance, 1e-9)
    }

    // Scenario 4: إنشاء مسودة فاتورة شراء حرة قبل الحفظ والتأكد من عدم إضافة الأصناف قبل الحفظ
    @Test
    fun `4_free_purchase_draft_before_save_adds_zero_products_to_inventory`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val vm = WarehouseViewModel(ds, "u1")

        val draftProduct = vm.createProductFromPurchase(
            name = "مناديل فاين",
            barcode = "",
            baseUnitName = "حبة",
            wholesaleUnitName = "كرتون",
            factor = 24.0,
            wholesaleUnitCost = 2500.0,
            baseSellPrice = 130.0
        ).getOrThrow()

        assertTrue(draftProduct.id.startsWith("draft_"))
        assertTrue(ds.observeProducts("u1").first().isEmpty())
    }

    // Scenario 5 & 6: حفظ فاتورة الشراء الحرة، تحويل الوحدات الكبيرة للصغيرة (2 × 2500 = 5000، 24 حبة -> 48، تكلفة = 2500/24)، ومنع التكرار
    @Test
    fun `5_and_6_free_purchase_commit_converts_units_adds_stock_once_and_prevents_duplicates`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val draft = Product(
            id = "draft_free_purchase", userId = "u1", name = "مناديل",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 24.0)),
            baseCost = 2500.0 / 24.0, baseSellPrice = 0.0, stockBaseQty = 0.0
        )

        val invoice = ds.postInvoice(
            userId = "u1",
            invoiceNumber = "FREE-PURCHASE-1",
            invoiceType = InvoiceType.PURCHASE,
            paymentMethod = PaymentMethod.CASH,
            contactId = "",
            contactName = "مورد نقدي",
            draftLines = listOf(
                InvoiceDraftLine("", "كرتون", 2.0, 2500.0, draft, InvoiceMode.FREE, productName = "مناديل", description = "عرض خاص")
            ),
            notes = ""
        ).getOrThrow()

        assertEquals(InvoiceMode.FREE, invoice.resolvedMode)
        assertEquals(5000.0, invoice.totalAmount, 1e-9)
        assertEquals("عرض خاص", invoice.lines.single().description)

        val savedProducts = ds.observeProducts("u1").first()
        assertEquals(1, savedProducts.size)
        assertEquals(48.0, savedProducts.single().stockBaseQty, 1e-9)
        assertEquals(2500.0 / 24.0, savedProducts.single().baseCost, 1e-9)

        // Second free purchase matching the same item name & units safely reuses the existing product (no duplicate product created)
        val secondDraft = draft.copy(id = "draft_free_purchase_2", baseCost = 2600.0 / 24.0)
        ds.postInvoice(
            userId = "u1",
            invoiceNumber = "FREE-PURCHASE-2",
            invoiceType = InvoiceType.PURCHASE,
            paymentMethod = PaymentMethod.CASH,
            contactId = "",
            contactName = "مورد نقدي",
            draftLines = listOf(
                InvoiceDraftLine("", "كرتون", 1.0, 2600.0, secondDraft, InvoiceMode.FREE, productName = "مناديل")
            ),
            notes = ""
        ).getOrThrow()

        val afterSecond = ds.observeProducts("u1").first()
        assertEquals(1, afterSecond.size)
        assertEquals(72.0, afterSecond.single().stockBaseQty, 1e-9)
    }

    // Also ensure incompatible same-name units are refused safely without corrupting inventory
    @Test
    fun `free_purchase_refuses_same_name_item_with_incompatible_unit_conversion`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val existingId = ds.saveProduct(
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

        val result = ds.postInvoice(
            "u1", "FREE-PURCHASE-BAD-UNITS", InvoiceType.PURCHASE, PaymentMethod.CASH, "", "",
            listOf(InvoiceDraftLine("", "كرتون", 1.0, 2400.0, incompatibleDraft, InvoiceMode.FREE)), ""
        )

        assertTrue(result.isFailure)
        assertEquals(1, ds.observeProducts("u1").first().size)
        assertEquals(48.0, ds.observeProducts("u1").first().single { it.id == existingId }.stockBaseQty, 1e-9)
    }

    // Scenario 7 & 8: تعديل وحذف الفواتير الحرة للبيع والشراء + اتساق حسابات العملاء والموردين
    @Test
    fun `7_and_8_edit_and_delete_free_sale_and_free_purchase_preserve_stock_and_balances`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val stockId = ds.saveProduct(
            userId = "u1", existingId = null, name = "أرز", barcode = "",
            baseUnitName = "كيس", defaultUnitName = "كيس", unitTiers = emptyList(),
            baseCost = 500.0, baseSellPrice = 600.0, stockBaseQty = 10.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val customerId = ds.saveContact("u1", null, "عميل 1", ContactType.CUSTOMER, "", 0.0, "").getOrThrow()
        val supplierId = ds.saveContact("u1", null, "مورد 1", ContactType.SUPPLIER, "", 0.0, "").getOrThrow()

        // A. Free Sale post -> edit -> delete
        val freeSale = ds.postInvoice(
            userId = "u1",
            invoiceNumber = "FS-1",
            invoiceType = InvoiceType.SALE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = customerId,
            contactName = "عميل 1",
            draftLines = listOf(
                InvoiceDraftLine(
                    "", "خدمة", 1.0, 1000.0,
                    Product(id = "free_s1", userId = "u1", name = "صيانة", baseUnitName = "خدمة", defaultUnitName = "خدمة"),
                    InvoiceMode.FREE, productName = "صيانة", description = "ثلاجة العرض"
                )
            ),
            notes = "",
            discount = 100.0,
            paidAmount = 300.0
        ).getOrThrow()
        assertEquals(600.0, ds.observeContacts("u1").first().single { it.id == customerId }.balance, 1e-9)
        assertEquals(10.0, ds.observeProducts("u1").first().single { it.id == stockId }.stockBaseQty, 1e-9)

        ds.updateInvoice(
            userId = "u1",
            invoiceId = freeSale.id,
            invoiceNumber = "FS-1",
            invoiceType = InvoiceType.SALE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = customerId,
            contactName = "عميل 1",
            draftLines = listOf(
                InvoiceDraftLine(
                    "", "خدمة", 2.0, 1000.0,
                    Product(id = "free_s2", userId = "u1", name = "صيانة شاملة", baseUnitName = "خدمة", defaultUnitName = "خدمة"),
                    InvoiceMode.FREE, productName = "صيانة شاملة", description = "تبديل كمبروسور"
                )
            ),
            notes = "محدثة",
            discount = 200.0,
            paidAmount = 500.0
        ).getOrThrow()
        assertEquals(1300.0, ds.observeContacts("u1").first().single { it.id == customerId }.balance, 1e-9)
        assertEquals(10.0, ds.observeProducts("u1").first().single { it.id == stockId }.stockBaseQty, 1e-9)

        ds.deleteInvoice("u1", freeSale.id).getOrThrow()
        assertEquals(0.0, ds.observeContacts("u1").first().single { it.id == customerId }.balance, 1e-9)
        assertEquals(10.0, ds.observeProducts("u1").first().single { it.id == stockId }.stockBaseQty, 1e-9)

        // B. Free Purchase post -> edit -> payment -> delete
        val biscuitDraft = Product(
            id = "draft_biscuit", userId = "u1", name = "بسكويت",
            baseUnitName = "حبة", defaultUnitName = "قرطاس",
            unitTiers = listOf(UnitTier("قرطاس", 12.0)),
            baseCost = 100.0, baseSellPrice = 150.0
        )
        val freePurchase = ds.postInvoice(
            userId = "u1",
            invoiceNumber = "FP-1",
            invoiceType = InvoiceType.PURCHASE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = supplierId,
            contactName = "مورد 1",
            draftLines = listOf(InvoiceDraftLine("", "قرطاس", 3.0, 1200.0, biscuitDraft, InvoiceMode.FREE)),
            notes = "",
            discount = 200.0,
            paidAmount = 400.0
        ).getOrThrow()

        val prodId = freePurchase.lines.single().productId
        assertEquals(36.0, ds.observeProducts("u1").first().single { it.id == prodId }.stockBaseQty, 1e-9)
        assertEquals(3000.0, ds.observeContacts("u1").first().single { it.id == supplierId }.balance, 1e-9) // (3600 - 200) - 400 = 3000

        // Edit quantity from 3 to 5 cartons -> reverses 36 & 3000, applies 60 & new remaining
        ds.updateInvoice(
            userId = "u1",
            invoiceId = freePurchase.id,
            invoiceNumber = "FP-1",
            invoiceType = InvoiceType.PURCHASE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = supplierId,
            contactName = "مورد 1",
            draftLines = listOf(InvoiceDraftLine(prodId, "قرطاس", 5.0, 1200.0, invoiceMode = InvoiceMode.FREE)),
            notes = "",
            discount = 0.0,
            paidAmount = 1000.0
        ).getOrThrow()

        assertEquals(60.0, ds.observeProducts("u1").first().single { it.id == prodId }.stockBaseQty, 1e-9)
        assertEquals(5000.0, ds.observeContacts("u1").first().single { it.id == supplierId }.balance, 1e-9)

        // Record supplier payment and verify net balance
        ds.recordPayment("u1", supplierId, 1500.0, "دفعة").getOrThrow()
        assertEquals(3500.0, ds.observeContacts("u1").first().single { it.id == supplierId }.balance, 1e-9)

        // Delete free purchase -> reverses 60 base units and 5000 invoice remaining cleanly
        ds.deleteInvoice("u1", freePurchase.id).getOrThrow()
        assertEquals(0.0, ds.observeProducts("u1").first().single { it.id == prodId }.stockBaseQty, 1e-9)
        assertEquals(-1500.0, ds.observeContacts("u1").first().single { it.id == supplierId }.balance, 1e-9)
    }

    // Scenario 9: الطباعة والمشاركة للفواتير الحرة والمخزنية
    @Test
    fun `9_printing_and_whatsapp_sharing_include_mode_description_and_accounting_details`() {
        val freeInvoice = Invoice(
            id = "inv_free_share",
            invoiceNumber = "F-2026",
            invoiceType = InvoiceType.SALE.name,
            invoiceMode = InvoiceMode.FREE.name,
            paymentMethod = PaymentMethod.CASH.name,
            contactName = "مكتبة النجاح",
            totalAmount = 1800.0,
            discount = 200.0,
            paidAmount = 1500.0,
            lines = listOf(
                InvoiceLine(
                    productId = "",
                    productName = "مستلزمات تغليف",
                    description = "أكياس مطبوعة مقاس وسط",
                    unitName = "ربطة",
                    cumulativeFactor = 1.0,
                    unitQty = 4.0,
                    unitPrice = 500.0,
                    baseQty = 4.0,
                    lineTotal = 2000.0
                )
            )
        )

        val whatsappText = ShareHelper.formatInvoiceWhatsAppText(freeInvoice, "بقالة العزي")
        assertTrue(whatsappText.contains("حرة"))
        assertTrue(whatsappText.contains("مستلزمات تغليف"))
        assertTrue(whatsappText.contains("أكياس مطبوعة مقاس وسط"))
        assertTrue(whatsappText.contains("2,000"))
        assertTrue(whatsappText.contains("200"))
        assertTrue(whatsappText.contains("1,800"))
        assertTrue(whatsappText.contains("1,500"))
        assertTrue(whatsappText.contains("300"))

        val escPos = EscPosPrinter.packMonochromeRowsToEscPosRaster(width = 8, height = 1) { x, _ -> x == 0 }
        assertTrue(escPos.isNotEmpty())
    }

    // Scenario 10: التوافق الكامل مع الفواتير القديمة والبيانات الموجودة قبل التطوير
    @Test
    fun `10_legacy_invoices_and_legacy_backup_bundle_import_and_delete_accurately`() = runBlocking {
        // Legacy BackupBundle missing invoiceMode, description, discount, paidAmount
        val legacyProduct = Product(
            id = "p_old",
            userId = "u_old",
            name = "شاي قديم",
            barcode = "",
            baseUnitName = "حبة",
            defaultUnitName = "حبة",
            unitTiers = emptyList(),
            baseCost = 50.0,
            baseSellPrice = 80.0,
            stockBaseQty = 10.0
        )
        val legacyContact = com.example.domain.Contact(
            id = "c_old",
            userId = "u_old",
            name = "عميل قديم",
            contactType = ContactType.CUSTOMER.name,
            balance = 800.0
        )
        val oldInv = Invoice(
            id = "inv_old",
            userId = "u_old",
            invoiceNumber = "0099",
            invoiceType = InvoiceType.SALE.name,
            invoiceMode = "",
            paymentMethod = PaymentMethod.CREDIT.name,
            contactId = "c_old",
            contactName = "عميل قديم",
            totalAmount = 800.0,
            discount = 0.0,
            paidAmount = null,
            lines = listOf(
                InvoiceLine(
                    productId = "p_old",
                    productName = "شاي قديم",
                    description = "",
                    unitName = "حبة",
                    cumulativeFactor = 1.0,
                    unitQty = 10.0,
                    unitPrice = 80.0,
                    baseQty = 10.0,
                    lineTotal = 800.0
                )
            )
        )

        assertEquals(InvoiceMode.STOCK, oldInv.resolvedMode)
        assertEquals(0.0, oldInv.discount, 1e-9)
        assertEquals(0.0, oldInv.resolvedPaidAmount, 1e-9)
        assertEquals(800.0, oldInv.resolvedRemainingAmount, 1e-9)

        val decodedBundle = com.example.data.BackupBundle(
            schemaVersion = 1,
            exportedAtMillis = 1700000000000L,
            products = listOf(legacyProduct),
            contacts = listOf(legacyContact),
            invoices = listOf(oldInv),
            payments = emptyList()
        )

        val ds = LocalWarehouseDataSource(storageFile = null)
        ds.importBackup("u1", decodedBundle).getOrThrow()

        // Reversing legacy credit invoice restores stock and zeroes legacy customer balance
        ds.deleteInvoice("u1", ds.observeInvoices("u1").first().single().id).getOrThrow()
        assertEquals(20.0, ds.observeProducts("u1").first().single().stockBaseQty, 1e-9)
        assertEquals(0.0, ds.observeContacts("u1").first().single().balance, 1e-9)
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
    fun `11_multi_row_purchase_adds_all_items_to_inventory_in_one_batch_only_on_save`() = runBlocking {
        val ds = LocalWarehouseDataSource(storageFile = null)
        val vm = WarehouseViewModel(ds, "u1")
        val existingId = ds.saveProduct(
            userId = "u1", existingId = null, name = "زيت طبخ", barcode = "",
            baseUnitName = "حبة", defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTier("كرتون", 6.0)),
            baseCost = 500.0, baseSellPrice = 650.0, stockBaseQty = 6.0,
            trackExpiry = false, expiryDate = "", minStockAlert = 0.0
        ).getOrThrow()
        val supplierId = ds.saveContact("u1", null, "مورد الجملة", ContactType.SUPPLIER, "", 0.0, "").getOrThrow()

        // Step 1: Drafting 2 new items + 1 existing item adds NOTHING to inventory before Save
        val draftA = vm.createProductFromPurchase(
            name = "مكرونة", barcode = "", baseUnitName = "حبة",
            wholesaleUnitName = "كرتون", factor = 20.0,
            wholesaleUnitCost = 4000.0, baseSellPrice = 250.0
        ).getOrThrow()
        val draftB = vm.createProductFromPurchase(
            name = "شاي فاخر", barcode = "", baseUnitName = "حبة",
            wholesaleUnitName = "حبة", factor = 1.0,
            wholesaleUnitCost = 350.0, baseSellPrice = 0.0
        ).getOrThrow()

        // Inventory still contains ONLY the original product untouched before Save
        val beforeSave = ds.observeProducts("u1").first()
        assertEquals(1, beforeSave.size)
        assertEquals(6.0, beforeSave.single { it.id == existingId }.stockBaseQty, 1e-9)

        // Step 2: Saving the invoice commits ALL rows together in one batch into inventory
        val savedInvoice = ds.postInvoice(
            userId = "u1",
            invoiceNumber = "BATCH-PUR-1",
            invoiceType = InvoiceType.PURCHASE,
            paymentMethod = PaymentMethod.CREDIT,
            contactId = supplierId,
            contactName = "مورد الجملة",
            draftLines = listOf(
                InvoiceDraftLine(existingId, "كرتون", 2.0, 3300.0, invoiceMode = InvoiceMode.FREE),
                InvoiceDraftLine("", "كرتون", 3.0, 4000.0, draftA, InvoiceMode.FREE, productName = "مكرونة"),
                InvoiceDraftLine("", "حبة", 10.0, 350.0, draftB, InvoiceMode.FREE, productName = "شاي فاخر")
            ),
            notes = "دفعة واحدة",
            discount = 100.0,
            paidAmount = 2000.0
        ).getOrThrow()

        assertEquals(3, savedInvoice.lines.size)
        assertEquals(22100.0, savedInvoice.subtotalAmount, 1e-9) // 6600 + 12000 + 3500
        assertEquals(22000.0, savedInvoice.totalAmount, 1e-9)
        assertEquals(20000.0, ds.observeContacts("u1").first().single { it.id == supplierId }.balance, 1e-9)

        val afterSave = ds.observeProducts("u1").first()
        assertEquals(3, afterSave.size)
        assertEquals(18.0, afterSave.first { it.id == existingId }.stockBaseQty, 1e-9) // 6 + (2*6)
        assertEquals(60.0, afterSave.first { it.name == "مكرونة" }.stockBaseQty, 1e-9) // 3*20
        assertEquals(10.0, afterSave.first { it.name == "شاي فاخر" }.stockBaseQty, 1e-9)
    }
}


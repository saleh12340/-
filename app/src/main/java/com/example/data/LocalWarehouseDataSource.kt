package com.example.data

import android.content.Context
import com.example.domain.Contact
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceLine
import com.example.domain.InvoiceType
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.UnitCostEngine
import com.example.domain.UnitTier
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID

class LocalWarehouseDataSource internal constructor(
    private val storageFile: File?
) : WarehouseDataSource {

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "warehouse_local_db.json")
    )

    override val isCloudBacked: Boolean = false

    private val mutex = Mutex()

    private val productsFlow = MutableStateFlow<List<Product>>(emptyList())
    private val contactsFlow = MutableStateFlow<List<Contact>>(emptyList())
    private val invoicesFlow = MutableStateFlow<List<Invoice>>(emptyList())
    private val paymentsFlow = MutableStateFlow<List<Payment>>(emptyList())

    init {
        loadFromDisk()
    }

    override fun observeProducts(userId: String): Flow<List<Product>> =
        productsFlow.map { list -> list.sortedBy { it.name } }

    override fun observeContacts(userId: String): Flow<List<Contact>> =
        contactsFlow.map { list -> list.sortedBy { it.name } }

    override fun observeInvoices(userId: String): Flow<List<Invoice>> =
        invoicesFlow.map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    override fun observePayments(userId: String): Flow<List<Payment>> =
        paymentsFlow.map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    override suspend fun saveProduct(
        userId: String,
        existingId: String?,
        name: String,
        barcode: String,
        baseUnitName: String,
        defaultUnitName: String,
        unitTiers: List<UnitTier>,
        baseCost: Double,
        baseSellPrice: Double,
        stockBaseQty: Double,
        trackExpiry: Boolean,
        expiryDate: String,
        minStockAlert: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val cleanName = name.trim()
                val cleanBarcode = barcode.trim()
                require(cleanName.isNotEmpty() && cleanName.length <= 120) { "اسم الصنف مطلوب (بحد أقصى 120 حرفًا)" }
                require(cleanBarcode.length <= 64) { "الباركود طويل جدًا" }
                require(baseCost.isFinite() && baseCost >= 0.0) { "تكلفة الوحدة الأصغر لا يمكن أن تكون سالبة" }
                require(baseSellPrice.isFinite() && baseSellPrice >= 0.0) { "سعر البيع لا يمكن أن يكون سالبًا" }
                require(stockBaseQty.isFinite() && stockBaseQty >= 0.0) { "كمية المخزون لا يمكن أن تكون سالبة" }
                require(minStockAlert.isFinite() && minStockAlert >= 0.0) { "حد التنبيه لا يمكن أن يكون سالبًا" }

                UnitCostEngine.resolveUnitLevels(baseUnitName, unitTiers).getOrThrow()

                val current = productsFlow.value
                val dupName = current.any {
                    it.id != existingId && it.name.trim().equals(cleanName, ignoreCase = true)
                }
                require(!dupName) { "يوجد منتج مسجل بنفس الاسم مسبقًا، لا يُسمح بالتكرار" }

                if (cleanBarcode.isNotEmpty()) {
                    val dupBarcode = current.any {
                        it.id != existingId && it.barcode.trim().equals(cleanBarcode, ignoreCase = true)
                    }
                    require(!dupBarcode) { "يوجد منتج مسجل بنفس الباركود مسبقًا، لا يُسمح بالتكرار" }
                }

                val now = Timestamp.now()
                val cleanBaseUnit = baseUnitName.trim()
                val cleanDefaultUnit = defaultUnitName.trim().ifEmpty { cleanBaseUnit }
                val targetId = existingId?.takeIf { it.isNotBlank() } ?: "loc_prod_${UUID.randomUUID().toString().replace("-", "").take(16)}"

                val existing = current.firstOrNull { it.id == targetId }
                val newProduct = Product(
                    id = targetId,
                    userId = userId,
                    name = cleanName,
                    barcode = cleanBarcode,
                    baseUnitName = cleanBaseUnit,
                    defaultUnitName = cleanDefaultUnit,
                    unitTiers = unitTiers.map { UnitTier(it.name.trim(), it.factorToPrevious) },
                    baseCost = baseCost,
                    baseSellPrice = baseSellPrice,
                    stockBaseQty = stockBaseQty,
                    trackExpiry = trackExpiry,
                    expiryDate = if (trackExpiry) expiryDate.trim() else "",
                    minStockAlert = minStockAlert,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )

                val updated = if (existing == null) {
                    current + newProduct
                } else {
                    current.map { if (it.id == targetId) newProduct else it }
                }
                productsFlow.value = updated
                persistToDiskLocked()
                targetId
            }
        }
    }

    override suspend fun deleteProduct(userId: String, productId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    require(productId.isNotBlank()) { "معرف الصنف غير صالح" }
                    productsFlow.value = productsFlow.value.filterNot { it.id == productId }
                    persistToDiskLocked()
                }
            }
        }

    override suspend fun saveContact(
        userId: String,
        existingId: String?,
        name: String,
        contactType: ContactType,
        phone: String,
        openingBalance: Double,
        notes: String
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val cleanName = name.trim()
                require(cleanName.isNotEmpty() && cleanName.length <= 120) { "الاسم مطلوب" }
                require(openingBalance.isFinite()) { "الرصيد غير صالح" }

                val current = contactsFlow.value
                val dup = current.any {
                    it.id != existingId &&
                        it.resolvedType == contactType &&
                        it.name.trim().equals(cleanName, ignoreCase = true)
                }
                require(!dup) { "يوجد حساب مسجل بنفس الاسم مسبقًا، لا يُسمح بالتكرار" }

                val now = Timestamp.now()
                val targetId = existingId?.takeIf { it.isNotBlank() } ?: "loc_cnt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val existing = current.firstOrNull { it.id == targetId }
                val contact = Contact(
                    id = targetId,
                    userId = userId,
                    name = cleanName,
                    contactType = contactType.name,
                    phone = phone.trim().take(30),
                    balance = openingBalance,
                    notes = notes.trim().take(300),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                contactsFlow.value = if (existing == null) current + contact else current.map {
                    if (it.id == targetId) contact else it
                }
                persistToDiskLocked()
                targetId
            }
        }
    }

    override suspend fun deleteContact(userId: String, contactId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    require(contactId.isNotBlank()) { "معرف الحساب غير صالح" }
                    contactsFlow.value = contactsFlow.value.filterNot { it.id == contactId }
                    persistToDiskLocked()
                }
            }
        }

    override suspend fun postInvoice(
        userId: String,
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        draftLines: List<InvoiceDraftLine>,
        notes: String
    ): Result<Invoice> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(draftLines.isNotEmpty() && draftLines.size <= 50) { "يجب إضافة صنف واحد على الأقل للفاتورة" }
                if (paymentMethod == PaymentMethod.CREDIT) {
                    require(contactId.isNotBlank()) { "يرجى اختيار العميل أو المورد للفاتورة الآجلة" }
                }

                val productsMap = productsFlow.value.associateBy { it.id }.toMutableMap()
                val finalLines = mutableListOf<InvoiceLine>()
                var grandTotal = 0.0

                for (draft in draftLines) {
                    require(draft.unitQty.isFinite() && draft.unitQty > 0.0) { "كمية الصنف غير صالحة" }
                    require(draft.unitPrice.isFinite() && draft.unitPrice >= 0.0) { "سعر الصنف غير صالح" }

                    // Free sale rows are invoice-only: no inventory lookup, creation or stock deduction.
                    if (invoiceType == InvoiceType.SALE && draft.draftProduct != null) {
                        val product = draft.draftProduct
                        val factor = UnitCostEngine.getAvailableUnits(product)
                            .firstOrNull { it.name.equals(draft.unitName, ignoreCase = true) }
                            ?.cumulativeFactor ?: 1.0
                        val line = InvoiceLine(
                            productId = "",
                            productName = product.name,
                            unitName = draft.unitName,
                            cumulativeFactor = factor,
                            unitQty = draft.unitQty,
                            unitPrice = draft.unitPrice,
                            baseQty = draft.unitQty * factor,
                            lineTotal = draft.unitQty * draft.unitPrice
                        )
                        finalLines.add(line)
                        grandTotal += line.lineTotal
                        continue
                    }

                    var currentProd = productsMap[draft.productId]
                    if (currentProd == null && invoiceType == InvoiceType.PURCHASE && draft.draftProduct != null) {
                        currentProd = materializePurchaseDraftProduct(userId, draft.draftProduct, productsMap)
                    }
                    currentProd = currentProd ?: throw IllegalStateException("الصنف غير موجود في المخزون")
                    val productId = currentProd.id

                    if (invoiceType == InvoiceType.PURCHASE) {
                        val computed = UnitCostEngine.computePurchaseLine(
                            product = currentProd,
                            unitName = draft.unitName,
                            unitQty = draft.unitQty,
                            unitPrice = draft.unitPrice
                        ).getOrThrow()
                        finalLines.add(computed.line.copy(productId = productId))
                        grandTotal += computed.line.lineTotal
                        productsMap[productId] = currentProd.copy(
                            stockBaseQty = computed.newStockBaseQty,
                            baseCost = computed.updatedBaseCost,
                            updatedAt = Timestamp.now()
                        )
                    } else {
                        val computed = UnitCostEngine.computeSaleLine(
                            product = currentProd,
                            unitName = draft.unitName,
                            unitQty = draft.unitQty,
                            customUnitPrice = draft.unitPrice
                        ).getOrThrow()
                        finalLines.add(computed.line)
                        grandTotal += computed.line.lineTotal
                        productsMap[productId] = currentProd.copy(
                            stockBaseQty = computed.newStockBaseQty,
                            updatedAt = Timestamp.now()
                        )
                    }
                }

                val now = Timestamp.now()
                val manualNumber = invoiceNumber.trim()
                if (manualNumber.isNotEmpty()) {
                    val dupInvoice = invoicesFlow.value.any {
                        it.invoiceNumber.trim().equals(manualNumber, ignoreCase = true)
                    }
                    require(!dupInvoice) { "رقم الفاتورة ($manualNumber) مسجل مسبقًا، لا يُسمح بالتكرار" }
                }
                val cleanNumber = manualNumber.ifEmpty {
                    val prefix = if (invoiceType == InvoiceType.PURCHASE) "PUR" else "SAL"
                    "$prefix-${System.currentTimeMillis().toString().takeLast(6)}"
                }
                val invoice = Invoice(
                    id = "loc_inv_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    userId = userId,
                    invoiceNumber = cleanNumber.take(40),
                    invoiceType = invoiceType.name,
                    paymentMethod = paymentMethod.name,
                    contactId = contactId.trim(),
                    contactName = contactName.trim().ifEmpty {
                        if (invoiceType == InvoiceType.PURCHASE) "مورد نقدي" else "عميل نقدي"
                    }.take(120),
                    totalAmount = grandTotal,
                    lines = finalLines,
                    notes = notes.trim().take(300),
                    createdAt = now,
                    updatedAt = now
                )

                productsFlow.value = productsMap.values.toList()
                if (paymentMethod == PaymentMethod.CREDIT && contactId.isNotBlank()) {
                    contactsFlow.value = contactsFlow.value.map { c ->
                        if (c.id == contactId) c.copy(balance = c.balance + grandTotal, updatedAt = now) else c
                    }
                }
                invoicesFlow.value = listOf(invoice) + invoicesFlow.value
                persistToDiskLocked()
                invoice
            }
        }
    }

    override suspend fun updateInvoice(
        userId: String,
        invoiceId: String,
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        draftLines: List<InvoiceDraftLine>,
        notes: String
    ): Result<Invoice> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(invoiceId.isNotBlank()) { "معرف الفاتورة غير صالح" }
                require(draftLines.isNotEmpty() && draftLines.size <= 50) { "يجب إضافة صنف واحد على الأقل للفاتورة" }
                if (paymentMethod == PaymentMethod.CREDIT) {
                    require(contactId.isNotBlank()) { "يرجى اختيار العميل أو المورد للفاتورة الآجلة" }
                }
                val oldInvoice = invoicesFlow.value.firstOrNull { it.id == invoiceId }
                    ?: throw IllegalStateException("الفاتورة غير موجودة")

                val manualNumber = invoiceNumber.trim()
                if (manualNumber.isNotEmpty()) {
                    val dupInvoice = invoicesFlow.value.any {
                        it.id != invoiceId && it.invoiceNumber.trim().equals(manualNumber, ignoreCase = true)
                    }
                    require(!dupInvoice) { "رقم الفاتورة ($manualNumber) مسجل مسبقًا، لا يُسمح بالتكرار" }
                }

                val now = Timestamp.now()
                val productsMap = productsFlow.value.associateBy { it.id }.toMutableMap()

                // 1. Reverse stock effect of the old invoice
                for (oldLine in oldInvoice.lines) {
                    val existingProd = productsMap[oldLine.productId] ?: continue
                    val restoredQty = if (oldInvoice.resolvedType == InvoiceType.PURCHASE) {
                        (existingProd.stockBaseQty - oldLine.baseQty).coerceAtLeast(0.0)
                    } else {
                        existingProd.stockBaseQty + oldLine.baseQty
                    }
                    productsMap[oldLine.productId] = existingProd.copy(stockBaseQty = restoredQty, updatedAt = now)
                }

                // 2. Apply updated lines
                val finalLines = mutableListOf<InvoiceLine>()
                var grandTotal = 0.0
                for (draft in draftLines) {
                    require(draft.unitQty.isFinite() && draft.unitQty > 0.0) { "كمية الصنف غير صالحة" }
                    require(draft.unitPrice.isFinite() && draft.unitPrice >= 0.0) { "سعر الصنف غير صالح" }
                    if (invoiceType == InvoiceType.SALE && draft.draftProduct != null) {
                        val product = draft.draftProduct
                        val factor = UnitCostEngine.getAvailableUnits(product)
                            .firstOrNull { it.name.equals(draft.unitName, ignoreCase = true) }
                            ?.cumulativeFactor ?: 1.0
                        val line = InvoiceLine(
                            productId = "", productName = product.name, unitName = draft.unitName,
                            cumulativeFactor = factor, unitQty = draft.unitQty, unitPrice = draft.unitPrice,
                            baseQty = draft.unitQty * factor, lineTotal = draft.unitQty * draft.unitPrice
                        )
                        finalLines.add(line)
                        grandTotal += line.lineTotal
                        continue
                    }
                    var currentProd = productsMap[draft.productId]
                    if (currentProd == null && invoiceType == InvoiceType.PURCHASE && draft.draftProduct != null) {
                        currentProd = materializePurchaseDraftProduct(userId, draft.draftProduct, productsMap)
                    }
                    currentProd = currentProd ?: throw IllegalStateException("الصنف غير موجود في المخزون")
                    val productId = currentProd.id
                    if (invoiceType == InvoiceType.PURCHASE) {
                        val computed = UnitCostEngine.computePurchaseLine(
                            product = currentProd, unitName = draft.unitName,
                            unitQty = draft.unitQty, unitPrice = draft.unitPrice
                        ).getOrThrow()
                        finalLines.add(computed.line.copy(productId = productId))
                        grandTotal += computed.line.lineTotal
                        productsMap[productId] = currentProd.copy(
                            stockBaseQty = computed.newStockBaseQty,
                            baseCost = computed.updatedBaseCost, updatedAt = now
                        )
                    } else {
                        val computed = UnitCostEngine.computeSaleLine(
                            product = currentProd, unitName = draft.unitName,
                            unitQty = draft.unitQty, customUnitPrice = draft.unitPrice
                        ).getOrThrow()
                        finalLines.add(computed.line)
                        grandTotal += computed.line.lineTotal
                        productsMap[productId] = currentProd.copy(
                            stockBaseQty = computed.newStockBaseQty, updatedAt = now
                        )
                    }
                }

                // 3. Reverse old contact credit balance and apply new contact credit balance
                val contactsMap = contactsFlow.value.associateBy { it.id }.toMutableMap()
                if (oldInvoice.resolvedPaymentMethod == PaymentMethod.CREDIT && oldInvoice.contactId.isNotBlank()) {
                    contactsMap[oldInvoice.contactId]?.let { c ->
                        contactsMap[c.id] = c.copy(balance = c.balance - oldInvoice.totalAmount, updatedAt = now)
                    }
                }
                if (paymentMethod == PaymentMethod.CREDIT && contactId.isNotBlank()) {
                    val c = contactsMap[contactId] ?: throw IllegalStateException("الحساب المحدد غير موجود")
                    contactsMap[c.id] = c.copy(balance = c.balance + grandTotal, updatedAt = now)
                }

                val cleanNumber = manualNumber.ifEmpty { oldInvoice.invoiceNumber }
                val updatedInvoice = oldInvoice.copy(
                    invoiceNumber = cleanNumber.take(40),
                    invoiceType = invoiceType.name,
                    paymentMethod = paymentMethod.name,
                    contactId = contactId.trim(),
                    contactName = contactName.trim().ifEmpty {
                        if (invoiceType == InvoiceType.PURCHASE) "مورد نقدي" else "عميل نقدي"
                    }.take(120),
                    totalAmount = grandTotal,
                    lines = finalLines,
                    notes = notes.trim().take(300),
                    updatedAt = now
                )

                productsFlow.value = productsMap.values.toList()
                contactsFlow.value = contactsFlow.value.map { contactsMap[it.id] ?: it }
                invoicesFlow.value = invoicesFlow.value.map { if (it.id == invoiceId) updatedInvoice else it }
                persistToDiskLocked()
                updatedInvoice
            }
        }
    }

    override suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    require(invoiceId.isNotBlank()) { "معرف الفاتورة غير صالح" }
                    val oldInvoice = invoicesFlow.value.firstOrNull { it.id == invoiceId }
                        ?: throw IllegalStateException("الفاتورة غير موجودة")
                    val now = Timestamp.now()

                    val productsMap = productsFlow.value.associateBy { it.id }.toMutableMap()
                    for (oldLine in oldInvoice.lines) {
                        val existingProd = productsMap[oldLine.productId] ?: continue
                        val restoredQty = if (oldInvoice.resolvedType == InvoiceType.PURCHASE) {
                            (existingProd.stockBaseQty - oldLine.baseQty).coerceAtLeast(0.0)
                        } else {
                            existingProd.stockBaseQty + oldLine.baseQty
                        }
                        productsMap[oldLine.productId] = existingProd.copy(stockBaseQty = restoredQty, updatedAt = now)
                    }
                    productsFlow.value = productsMap.values.toList()

                    if (oldInvoice.resolvedPaymentMethod == PaymentMethod.CREDIT && oldInvoice.contactId.isNotBlank()) {
                        contactsFlow.value = contactsFlow.value.map { c ->
                            if (c.id == oldInvoice.contactId) {
                                c.copy(balance = c.balance - oldInvoice.totalAmount, updatedAt = now)
                            } else c
                        }
                    }

                    invoicesFlow.value = invoicesFlow.value.filterNot { it.id == invoiceId }
                    persistToDiskLocked()
                }
            }
        }

    override suspend fun recordPayment(
        userId: String,
        contactId: String,
        amount: Double,
        notes: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(contactId.isNotBlank()) { "يرجى اختيار الحساب" }
                require(amount.isFinite() && amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
                val contact = contactsFlow.value.firstOrNull { it.id == contactId }
                    ?: throw IllegalStateException("الحساب غير موجود")
                val now = Timestamp.now()
                val payment = Payment(
                    id = "loc_pay_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                    userId = userId,
                    contactId = contactId,
                    contactName = contact.name,
                    contactType = contact.contactType,
                    amount = amount,
                    notes = notes.trim().take(300),
                    createdAt = now,
                    updatedAt = now
                )
                contactsFlow.value = contactsFlow.value.map {
                    if (it.id == contactId) it.copy(balance = it.balance - amount, updatedAt = now) else it
                }
                paymentsFlow.value = listOf(payment) + paymentsFlow.value
                persistToDiskLocked()
            }
        }
    }

    override suspend fun updatePayment(
        userId: String,
        paymentId: String,
        amount: Double,
        notes: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                require(paymentId.isNotBlank()) { "معرف السند غير صالح" }
                require(amount.isFinite() && amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
                val existing = paymentsFlow.value.firstOrNull { it.id == paymentId }
                    ?: throw IllegalStateException("السند غير موجود")
                val now = Timestamp.now()
                val delta = existing.amount - amount
                contactsFlow.value = contactsFlow.value.map {
                    if (it.id == existing.contactId) it.copy(balance = it.balance + delta, updatedAt = now) else it
                }
                paymentsFlow.value = paymentsFlow.value.map {
                    if (it.id == paymentId) it.copy(amount = amount, notes = notes.trim().take(300), updatedAt = now) else it
                }
                persistToDiskLocked()
            }
        }
    }

    override suspend fun deletePayment(userId: String, paymentId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    require(paymentId.isNotBlank()) { "معرف السند غير صالح" }
                    val existing = paymentsFlow.value.firstOrNull { it.id == paymentId }
                        ?: throw IllegalStateException("السند غير موجود")
                    val now = Timestamp.now()
                    contactsFlow.value = contactsFlow.value.map {
                        if (it.id == existing.contactId) it.copy(balance = it.balance + existing.amount, updatedAt = now) else it
                    }
                    paymentsFlow.value = paymentsFlow.value.filterNot { it.id == paymentId }
                    persistToDiskLocked()
                }
            }
        }

    override suspend fun exportBackup(userId: String): Result<BackupBundle> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                BackupBundle(
                    schemaVersion = 1,
                    exportedAtMillis = System.currentTimeMillis(),
                    products = productsFlow.value,
                    contacts = contactsFlow.value,
                    invoices = invoicesFlow.value,
                    payments = paymentsFlow.value
                )
            }
        }
    }

    override suspend fun importBackup(userId: String, bundle: BackupBundle): Result<RestoreSummary> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val now = Timestamp.now()
                    val existingProducts = productsFlow.value.toMutableList()
                    var importedProducts = 0
                    var skippedDupProducts = 0

                    for (p in bundle.products) {
                        val dup = existingProducts.any {
                            it.id == p.id ||
                                it.name.trim().equals(p.name.trim(), ignoreCase = true) ||
                                (p.barcode.isNotBlank() && it.barcode.trim().equals(p.barcode.trim(), ignoreCase = true))
                        }
                        if (dup) {
                            skippedDupProducts++
                        } else {
                            val pid = p.id.ifBlank { "loc_prod_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
                            existingProducts.add(p.copy(id = pid, userId = userId, createdAt = p.createdAt ?: now, updatedAt = now))
                            importedProducts++
                        }
                    }

                    val existingContacts = contactsFlow.value.toMutableList()
                    var importedContacts = 0
                    for (c in bundle.contacts) {
                        val dup = existingContacts.any {
                            it.id == c.id || (it.resolvedType == c.resolvedType && it.name.trim().equals(c.name.trim(), ignoreCase = true))
                        }
                        if (!dup) {
                            val cid = c.id.ifBlank { "loc_cnt_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
                            existingContacts.add(c.copy(id = cid, userId = userId, createdAt = c.createdAt ?: now, updatedAt = now))
                            importedContacts++
                        }
                    }

                    val existingInvoices = invoicesFlow.value.toMutableList()
                    var importedInvoices = 0
                    for (inv in bundle.invoices) {
                        val dup = existingInvoices.any { it.id == inv.id }
                        if (!dup) {
                            val iid = inv.id.ifBlank { "loc_inv_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
                            existingInvoices.add(inv.copy(id = iid, userId = userId, createdAt = inv.createdAt ?: now, updatedAt = now))
                            importedInvoices++
                        }
                    }

                    val existingPayments = paymentsFlow.value.toMutableList()
                    var importedPayments = 0
                    for (pay in bundle.payments) {
                        val dup = existingPayments.any { it.id == pay.id }
                        if (!dup) {
                            val payId = pay.id.ifBlank { "loc_pay_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
                            existingPayments.add(pay.copy(id = payId, userId = userId, createdAt = pay.createdAt ?: now, updatedAt = now))
                            importedPayments++
                        }
                    }

                    productsFlow.value = existingProducts
                    contactsFlow.value = existingContacts
                    invoicesFlow.value = existingInvoices
                    paymentsFlow.value = existingPayments
                    persistToDiskLocked()

                    RestoreSummary(
                        importedProducts = importedProducts,
                        skippedDuplicateProducts = skippedDupProducts,
                        importedContacts = importedContacts,
                        importedInvoices = importedInvoices,
                        importedPayments = importedPayments
                    )
                }
            }
        }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.exists()) return
        try {
            val raw = file.readText(Charsets.UTF_8)
            if (raw.isBlank()) return
            val bundle = BackupJsonCodec.decode(raw)
            productsFlow.value = bundle.products
            contactsFlow.value = bundle.contacts
            invoicesFlow.value = bundle.invoices
            paymentsFlow.value = bundle.payments
        } catch (_: Exception) {
            // Keep empty safe defaults if corrupted
        }
    }

    private fun persistToDiskLocked() {
        val file = storageFile ?: return
        val parent = file.parentFile ?: return
        val bundle = BackupBundle(
            schemaVersion = 1,
            exportedAtMillis = System.currentTimeMillis(),
            products = productsFlow.value,
            contacts = contactsFlow.value,
            invoices = invoicesFlow.value,
            payments = paymentsFlow.value
        )
        val tmp = File(parent, "warehouse_local_db.json.tmp")
        tmp.writeText(BackupJsonCodec.encode(bundle), Charsets.UTF_8)
        tmp.renameTo(file)
    }
}

object BackupJsonCodec {
    fun encode(bundle: BackupBundle): String {
        val root = JSONObject()
        root.put("schemaVersion", bundle.schemaVersion)
        root.put("exportedAtMillis", bundle.exportedAtMillis)

        root.put("products", JSONArray().apply {
            bundle.products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("userId", p.userId)
                    put("name", p.name)
                    put("barcode", p.barcode)
                    put("baseUnitName", p.baseUnitName)
                    put("defaultUnitName", p.defaultUnitName)
                    put("unitTiers", JSONArray().apply {
                        p.unitTiers.forEach { t ->
                            put(JSONObject().apply {
                                put("name", t.name)
                                put("factorToPrevious", t.factorToPrevious)
                            })
                        }
                    })
                    put("baseCost", p.baseCost)
                    put("baseSellPrice", p.baseSellPrice)
                    put("stockBaseQty", p.stockBaseQty)
                    put("trackExpiry", p.trackExpiry)
                    put("expiryDate", p.expiryDate)
                    put("minStockAlert", p.minStockAlert)
                    put("createdAtMillis", p.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        root.put("contacts", JSONArray().apply {
            bundle.contacts.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("userId", c.userId)
                    put("name", c.name)
                    put("contactType", c.contactType)
                    put("phone", c.phone)
                    put("balance", c.balance)
                    put("notes", c.notes)
                    put("createdAtMillis", c.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        root.put("invoices", JSONArray().apply {
            bundle.invoices.forEach { inv ->
                put(JSONObject().apply {
                    put("id", inv.id)
                    put("userId", inv.userId)
                    put("invoiceNumber", inv.invoiceNumber)
                    put("invoiceType", inv.invoiceType)
                    put("paymentMethod", inv.paymentMethod)
                    put("contactId", inv.contactId)
                    put("contactName", inv.contactName)
                    put("totalAmount", inv.totalAmount)
                    put("notes", inv.notes)
                    put("createdAtMillis", inv.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                    put("lines", JSONArray().apply {
                        inv.lines.forEach { line ->
                            put(JSONObject().apply {
                                put("productId", line.productId)
                                put("productName", line.productName)
                                put("unitName", line.unitName)
                                put("cumulativeFactor", line.cumulativeFactor)
                                put("unitQty", line.unitQty)
                                put("unitPrice", line.unitPrice)
                                put("baseQty", line.baseQty)
                                put("lineTotal", line.lineTotal)
                            })
                        }
                    })
                })
            }
        })

        root.put("payments", JSONArray().apply {
            bundle.payments.forEach { pay ->
                put(JSONObject().apply {
                    put("id", pay.id)
                    put("userId", pay.userId)
                    put("contactId", pay.contactId)
                    put("contactName", pay.contactName)
                    put("contactType", pay.contactType)
                    put("amount", pay.amount)
                    put("notes", pay.notes)
                    put("createdAtMillis", pay.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        return root.toString(2)
    }

    fun decode(jsonText: String): BackupBundle {
        val root = JSONObject(jsonText)
        val schemaVersion = root.optInt("schemaVersion", 1)
        val exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis())

        val products = mutableListOf<Product>()
        val pArr = root.optJSONArray("products") ?: JSONArray()
        for (i in 0 until pArr.length()) {
            val obj = pArr.getJSONObject(i)
            val tiersArr = obj.optJSONArray("unitTiers") ?: JSONArray()
            val tiers = mutableListOf<UnitTier>()
            for (j in 0 until tiersArr.length()) {
                val tObj = tiersArr.getJSONObject(j)
                tiers.add(
                    UnitTier(
                        name = tObj.optString("name", ""),
                        factorToPrevious = tObj.optDouble("factorToPrevious", 1.0)
                    )
                )
            }
            val createdMs = obj.optLong("createdAtMillis", System.currentTimeMillis())
            products.add(
                Product(
                    id = obj.optString("id", ""),
                    userId = obj.optString("userId", ""),
                    name = obj.optString("name", ""),
                    barcode = obj.optString("barcode", ""),
                    baseUnitName = obj.optString("baseUnitName", "حبة"),
                    defaultUnitName = obj.optString("defaultUnitName", "حبة"),
                    unitTiers = tiers,
                    baseCost = obj.optDouble("baseCost", 0.0),
                    baseSellPrice = obj.optDouble("baseSellPrice", 0.0),
                    stockBaseQty = obj.optDouble("stockBaseQty", 0.0),
                    trackExpiry = obj.optBoolean("trackExpiry", false),
                    expiryDate = obj.optString("expiryDate", ""),
                    minStockAlert = obj.optDouble("minStockAlert", 0.0),
                    createdAt = Timestamp(Date(createdMs)),
                    updatedAt = Timestamp(Date(createdMs))
                )
            )
        }

        val contacts = mutableListOf<Contact>()
        val cArr = root.optJSONArray("contacts") ?: JSONArray()
        for (i in 0 until cArr.length()) {
            val obj = cArr.getJSONObject(i)
            val createdMs = obj.optLong("createdAtMillis", System.currentTimeMillis())
            contacts.add(
                Contact(
                    id = obj.optString("id", ""),
                    userId = obj.optString("userId", ""),
                    name = obj.optString("name", ""),
                    contactType = obj.optString("contactType", ContactType.CUSTOMER.name),
                    phone = obj.optString("phone", ""),
                    balance = obj.optDouble("balance", 0.0),
                    notes = obj.optString("notes", ""),
                    createdAt = Timestamp(Date(createdMs)),
                    updatedAt = Timestamp(Date(createdMs))
                )
            )
        }

        val invoices = mutableListOf<Invoice>()
        val invArr = root.optJSONArray("invoices") ?: JSONArray()
        for (i in 0 until invArr.length()) {
            val obj = invArr.getJSONObject(i)
            val linesArr = obj.optJSONArray("lines") ?: JSONArray()
            val lines = mutableListOf<InvoiceLine>()
            for (j in 0 until linesArr.length()) {
                val lObj = linesArr.getJSONObject(j)
                lines.add(
                    InvoiceLine(
                        productId = lObj.optString("productId", ""),
                        productName = lObj.optString("productName", ""),
                        unitName = lObj.optString("unitName", ""),
                        cumulativeFactor = lObj.optDouble("cumulativeFactor", 1.0),
                        unitQty = lObj.optDouble("unitQty", 0.0),
                        unitPrice = lObj.optDouble("unitPrice", 0.0),
                        baseQty = lObj.optDouble("baseQty", 0.0),
                        lineTotal = lObj.optDouble("lineTotal", 0.0)
                    )
                )
            }
            val createdMs = obj.optLong("createdAtMillis", System.currentTimeMillis())
            invoices.add(
                Invoice(
                    id = obj.optString("id", ""),
                    userId = obj.optString("userId", ""),
                    invoiceNumber = obj.optString("invoiceNumber", ""),
                    invoiceType = obj.optString("invoiceType", InvoiceType.SALE.name),
                    paymentMethod = obj.optString("paymentMethod", PaymentMethod.CASH.name),
                    contactId = obj.optString("contactId", ""),
                    contactName = obj.optString("contactName", ""),
                    totalAmount = obj.optDouble("totalAmount", 0.0),
                    lines = lines,
                    notes = obj.optString("notes", ""),
                    createdAt = Timestamp(Date(createdMs)),
                    updatedAt = Timestamp(Date(createdMs))
                )
            )
        }

        val payments = mutableListOf<Payment>()
        val payArr = root.optJSONArray("payments") ?: JSONArray()
        for (i in 0 until payArr.length()) {
            val obj = payArr.getJSONObject(i)
            val createdMs = obj.optLong("createdAtMillis", System.currentTimeMillis())
            payments.add(
                Payment(
                    id = obj.optString("id", ""),
                    userId = obj.optString("userId", ""),
                    contactId = obj.optString("contactId", ""),
                    contactName = obj.optString("contactName", ""),
                    contactType = obj.optString("contactType", ContactType.CUSTOMER.name),
                    amount = obj.optDouble("amount", 0.0),
                    notes = obj.optString("notes", ""),
                    createdAt = Timestamp(Date(createdMs)),
                    updatedAt = Timestamp(Date(createdMs))
                )
            )
        }

        return BackupBundle(
            schemaVersion = schemaVersion,
            exportedAtMillis = exportedAtMillis,
            products = products,
            contacts = contacts,
            invoices = invoices,
            payments = payments
        )
    }
}


private fun materializePurchaseDraftProduct(
    userId: String,
    source: Product,
    products: MutableMap<String, Product>
): Product {
    val name = source.name.trim()
    require(name.isNotEmpty()) { "اسم الصنف مطلوب" }
    require(source.baseCost.isFinite() && source.baseCost >= 0.0) { "تكلفة الصنف غير صالحة" }
    require(source.baseSellPrice.isFinite() && source.baseSellPrice >= 0.0) { "سعر البيع غير صالح" }
    UnitCostEngine.resolveUnitLevels(source.baseUnitName, source.unitTiers).getOrThrow()
    val duplicate = products.values.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
    if (duplicate != null) {
        require(duplicate.baseUnitName.equals(source.baseUnitName, ignoreCase = true)) {
            "يوجد صنف بالاسم نفسه ولكن بوحدة أساسية مختلفة"
        }
        return duplicate
    }
    if (source.barcode.isNotBlank()) {
        require(products.values.none { it.barcode.trim().equals(source.barcode.trim(), ignoreCase = true) }) {
            "الباركود مسجل لصنف آخر"
        }
    }
    val id = "loc_prod_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val created = source.copy(
        id = id, userId = userId, name = name, stockBaseQty = 0.0,
        createdAt = Timestamp.now(), updatedAt = Timestamp.now()
    )
    products[id] = created
    return created
}

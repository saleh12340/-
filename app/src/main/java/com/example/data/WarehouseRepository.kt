package com.example.data

import android.content.Context
import com.example.R
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WarehouseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : WarehouseDataSource {

    override val isCloudBacked: Boolean = true

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    override fun observeProducts(userId: String): Flow<List<Product>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "تعارض هوية المستخدم الحالي" }
        val path = "users/$uid/products"
        emitAll(
            userDoc(uid).collection("products")
                .orderBy("name", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeContacts(userId: String): Flow<List<Contact>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "تعارض هوية المستخدم الحالي" }
        val path = "users/$uid/contacts"
        emitAll(
            userDoc(uid).collection("contacts")
                .orderBy("name", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Contact::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeInvoices(userId: String): Flow<List<Invoice>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "تعارض هوية المستخدم الحالي" }
        val path = "users/$uid/invoices"
        emitAll(
            userDoc(uid).collection("invoices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Invoice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observePayments(userId: String): Flow<List<Payment>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "تعارض هوية المستخدم الحالي" }
        val path = "users/$uid/payments"
        emitAll(
            userDoc(uid).collection("payments")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Payment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Saves or updates a product in Firestore while:
     * 1. Ensuring calculated/manual smallest-unit [baseCost] is persisted accurately.
     * 2. Preserving the exact existing Firestore schema so historical records stay intact.
     * 3. Rejecting duplicate product names or duplicate non-empty barcodes.
     */
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
    ): Result<String> {
        var docPath = "users/$userId/products"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            val cleanName = name.trim()
            val cleanBarcode = barcode.trim()
            require(cleanName.isNotEmpty() && cleanName.length <= 120) { "اسم الصنف مطلوب (بحد أقصى 120 حرفًا)" }
            require(cleanBarcode.length <= 64) { "الباركود طويل جدًا" }
            require(baseCost.isFinite() && baseCost >= 0.0) { "تكلفة الوحدة الأصغر لا يمكن أن تكون سالبة" }
            require(baseSellPrice.isFinite() && baseSellPrice >= 0.0) { "سعر البيع لا يمكن أن يكون سالبًا" }
            require(stockBaseQty.isFinite() && stockBaseQty >= 0.0) { "كمية المخزون لا يمكن أن تكون سالبة" }
            require(minStockAlert.isFinite() && minStockAlert >= 0.0) { "حد التنبيه لا يمكن أن يكون سالبًا" }

            UnitCostEngine.resolveUnitLevels(baseUnitName, unitTiers).getOrThrow()

            val colRef = userDoc(uid).collection("products")
            // Prevent duplicate product names and barcodes across the user's inventory
            val existingDocs = colRef.get().await().toObjects(
                Product::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val duplicateName = existingDocs.any {
                it.id != existingId && it.name.trim().equals(cleanName, ignoreCase = true)
            }
            require(!duplicateName) { "يوجد منتج مسجل بنفس الاسم مسبقًا، لا يُسمح بالتكرار" }

            if (cleanBarcode.isNotEmpty()) {
                val duplicateBarcode = existingDocs.any {
                    it.id != existingId && it.barcode.trim().equals(cleanBarcode, ignoreCase = true)
                }
                require(!duplicateBarcode) { "يوجد منتج مسجل بنفس الباركود مسبقًا، لا يُسمح بالتكرار" }
            }

            val serializedTiers = unitTiers.map {
                mapOf(
                    "name" to it.name.trim(),
                    "factorToPrevious" to it.factorToPrevious
                )
            }
            val cleanBaseUnit = baseUnitName.trim()
            val cleanDefaultUnit = defaultUnitName.trim().ifEmpty { cleanBaseUnit }

            if (existingId.isNullOrBlank()) {
                val docRef = colRef.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "barcode" to cleanBarcode,
                    "baseUnitName" to cleanBaseUnit,
                    "defaultUnitName" to cleanDefaultUnit,
                    "unitTiers" to serializedTiers,
                    "baseCost" to baseCost,
                    "baseSellPrice" to baseSellPrice,
                    "stockBaseQty" to stockBaseQty,
                    "trackExpiry" to trackExpiry,
                    "expiryDate" to if (trackExpiry) expiryDate.trim() else "",
                    "minStockAlert" to minStockAlert,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = colRef.document(existingId)
                docPath = docRef.path
                // Partial update preserves immutable createdAt, userId, and historical invoices
                val updatePayload = mapOf(
                    "name" to cleanName,
                    "barcode" to cleanBarcode,
                    "baseUnitName" to cleanBaseUnit,
                    "defaultUnitName" to cleanDefaultUnit,
                    "unitTiers" to serializedTiers,
                    "baseCost" to baseCost,
                    "baseSellPrice" to baseSellPrice,
                    "stockBaseQty" to stockBaseQty,
                    "trackExpiry" to trackExpiry,
                    "expiryDate" to if (trackExpiry) expiryDate.trim() else "",
                    "minStockAlert" to minStockAlert,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    override suspend fun deleteProduct(userId: String, productId: String): Result<Unit> {
        val docPath = "users/$userId/products/$productId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(productId.isNotBlank()) { "معرف الصنف غير صالح" }
            userDoc(uid).collection("products").document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
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
    ): Result<String> {
        var docPath = "users/$userId/contacts"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && cleanName.length <= 120) { "الاسم مطلوب" }
            require(openingBalance.isFinite()) { "الرصيد غير صالح" }

            val colRef = userDoc(uid).collection("contacts")
            val existingContacts = colRef.get().await().toObjects(
                Contact::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val dupContact = existingContacts.any {
                it.id != existingId &&
                    it.resolvedType == contactType &&
                    it.name.trim().equals(cleanName, ignoreCase = true)
            }
            require(!dupContact) { "يوجد حساب مسجل بنفس الاسم مسبقًا، لا يُسمح بالتكرار" }

            if (existingId.isNullOrBlank()) {
                val docRef = colRef.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "contactType" to contactType.name,
                    "phone" to phone.trim().take(30),
                    "balance" to openingBalance,
                    "notes" to notes.trim().take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = colRef.document(existingId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "contactType" to contactType.name,
                        "phone" to phone.trim().take(30),
                        "balance" to openingBalance,
                        "notes" to notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    override suspend fun deleteContact(userId: String, contactId: String): Result<Unit> {
        val docPath = "users/$userId/contacts/$contactId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(contactId.isNotBlank()) { "معرف الحساب غير صالح" }
            userDoc(uid).collection("contacts").document(contactId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
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
    ): Result<Invoice> {
        val basePath = "users/$userId/invoices"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(draftLines.isNotEmpty() && draftLines.size <= 50) { "يجب إضافة صنف واحد على الأقل للفاتورة" }
            if (paymentMethod == PaymentMethod.CREDIT) {
                require(contactId.isNotBlank()) { "يرجى اختيار العميل أو المورد للفاتورة الآجلة" }
            }

            val manualNumber = invoiceNumber.trim()
            if (manualNumber.isNotEmpty()) {
                val existingInv = userDoc(uid).collection("invoices")
                    .whereEqualTo("invoiceNumber", manualNumber)
                    .limit(1)
                    .get()
                    .await()
                require(existingInv.isEmpty) { "رقم الفاتورة ($manualNumber) مسجل مسبقًا، لا يُسمح بالتكرار" }
            }

            val invoiceDocRef = userDoc(uid).collection("invoices").document()
            val cleanNumber = manualNumber.ifEmpty {
                val prefix = if (invoiceType == InvoiceType.PURCHASE) "PUR" else "SAL"
                "$prefix-${System.currentTimeMillis().toString().takeLast(6)}"
            }
            val resolvedContactName = contactName.trim().ifEmpty {
                if (invoiceType == InvoiceType.PURCHASE) "مورد نقدي" else "عميل نقدي"
            }.take(120)

            val newPurchaseRefs = draftLines
                .filter { invoiceType == InvoiceType.PURCHASE && it.draftProduct != null }
                .associate { it.draftProduct!!.id to userDoc(uid).collection("products").document() }

            val createdInvoice = db.runTransaction { tx ->
                // Read every existing product and contact before performing any write.
                val existingIds = draftLines
                    .filter { it.draftProduct == null && it.productId.isNotBlank() }
                    .map { it.productId }.distinct()
                val productSnapshots = existingIds.associateWith { pid ->
                    val ref = userDoc(uid).collection("products").document(pid)
                    val prod = tx.get(ref).toObject(Product::class.java)
                        ?: throw IllegalStateException("الصنف غير موجود في المخزون")
                    ref to prod
                }
                val contactPair = if (paymentMethod == PaymentMethod.CREDIT && contactId.isNotBlank()) {
                    val cRef = userDoc(uid).collection("contacts").document(contactId)
                    val c = tx.get(cRef).toObject(Contact::class.java)
                        ?: throw IllegalStateException("الحساب المحدد غير موجود")
                    cRef to c
                } else null

                val finalLines = mutableListOf<InvoiceLine>()
                val productUpdates = mutableMapOf<String, Pair<Double, Double>>()
                val productRefs = mutableMapOf<String, com.google.firebase.firestore.DocumentReference>()
                val originalProducts = mutableMapOf<String, Product>()
                var grandTotal = 0.0

                for (draft in draftLines) {
                    require(draft.unitQty.isFinite() && draft.unitQty > 0.0) { "كمية الصنف غير صالحة" }
                    require(draft.unitPrice.isFinite() && draft.unitPrice >= 0.0) { "سعر الصنف غير صالح" }

                    // A free sale is a snapshot line stored only in the invoice.
                    if (invoiceType == InvoiceType.SALE && draft.draftProduct != null) {
                        val source = draft.draftProduct
                        val factor = UnitCostEngine.getAvailableUnits(source)
                            .firstOrNull { it.name.equals(draft.unitName, ignoreCase = true) }
                            ?.cumulativeFactor ?: 1.0
                        val line = InvoiceLine(
                            productId = "", productName = source.name, unitName = draft.unitName,
                            cumulativeFactor = factor, unitQty = draft.unitQty, unitPrice = draft.unitPrice,
                            baseQty = draft.unitQty * factor, lineTotal = draft.unitQty * draft.unitPrice
                        )
                        finalLines.add(line)
                        grandTotal += line.lineTotal
                        continue
                    }

                    val currentProd: Product
                    val productId: String
                    if (invoiceType == InvoiceType.PURCHASE && draft.draftProduct != null) {
                        val source = draft.draftProduct
                        val ref = newPurchaseRefs[source.id]
                            ?: throw IllegalStateException("تعذر تجهيز الصنف الجديد للشراء")
                        productId = ref.id
                        productRefs[productId] = ref
                        val base = originalProducts.getOrPut(productId) {
                            source.copy(id = productId, userId = uid, stockBaseQty = 0.0)
                        }
                        currentProd = base
                    } else {
                        productId = draft.productId
                        val pair = productSnapshots[productId]
                            ?: throw IllegalStateException("الصنف غير موجود في المخزون")
                        productRefs[productId] = pair.first
                        originalProducts[productId] = pair.second
                        currentProd = pair.second
                    }

                    val running = productUpdates[productId]
                    val effectiveProd = if (running != null) {
                        currentProd.copy(stockBaseQty = running.first, baseCost = running.second)
                    } else currentProd
                    if (invoiceType == InvoiceType.PURCHASE) {
                        val computed = UnitCostEngine.computePurchaseLine(
                            product = effectiveProd, unitName = draft.unitName,
                            unitQty = draft.unitQty, unitPrice = draft.unitPrice
                        ).getOrThrow()
                        finalLines.add(computed.line.copy(productId = productId))
                        grandTotal += computed.line.lineTotal
                        productUpdates[productId] = computed.newStockBaseQty to computed.updatedBaseCost
                    } else {
                        val computed = UnitCostEngine.computeSaleLine(
                            product = effectiveProd, unitName = draft.unitName,
                            unitQty = draft.unitQty, customUnitPrice = draft.unitPrice
                        ).getOrThrow()
                        finalLines.add(computed.line)
                        grandTotal += computed.line.lineTotal
                        productUpdates[productId] = computed.newStockBaseQty to effectiveProd.baseCost
                    }
                }

                val serializedLines = finalLines.map { line ->
                    mapOf(
                        "productId" to line.productId, "productName" to line.productName,
                        "unitName" to line.unitName, "cumulativeFactor" to line.cumulativeFactor,
                        "unitQty" to line.unitQty, "unitPrice" to line.unitPrice,
                        "baseQty" to line.baseQty, "lineTotal" to line.lineTotal
                    )
                }
                tx.set(invoiceDocRef, mapOf(
                    "userId" to uid, "invoiceNumber" to cleanNumber.take(40),
                    "invoiceType" to invoiceType.name, "paymentMethod" to paymentMethod.name,
                    "contactId" to contactId.trim(), "contactName" to resolvedContactName,
                    "totalAmount" to grandTotal, "lines" to serializedLines,
                    "notes" to notes.trim().take(300),
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                ))

                for ((pid, tuple) in productUpdates) {
                    val ref = productRefs.getValue(pid)
                    val original = originalProducts.getValue(pid)
                    if (pid in newPurchaseRefs.values.map { it.id }) {
                        tx.set(ref, mapOf(
                            "userId" to uid, "name" to original.name.trim(), "barcode" to original.barcode.trim(),
                            "baseUnitName" to original.baseUnitName, "defaultUnitName" to original.defaultUnitName,
                            "unitTiers" to original.unitTiers, "baseCost" to tuple.second,
                            "baseSellPrice" to original.baseSellPrice, "stockBaseQty" to tuple.first,
                            "trackExpiry" to original.trackExpiry, "expiryDate" to original.expiryDate,
                            "minStockAlert" to original.minStockAlert,
                            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    } else {
                        val updates = mutableMapOf<String, Any>(
                            "stockBaseQty" to tuple.first, "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (invoiceType == InvoiceType.PURCHASE && tuple.second != original.baseCost) {
                            updates["baseCost"] = tuple.second
                        }
                        tx.update(ref, updates)
                    }
                }
                if (contactPair != null) {
                    val (cRef, contact) = contactPair
                    tx.update(cRef, mapOf(
                        "balance" to (contact.balance + grandTotal),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
                Invoice(
                    id = invoiceDocRef.id, userId = uid, invoiceNumber = cleanNumber.take(40),
                    invoiceType = invoiceType.name, paymentMethod = paymentMethod.name,
                    contactId = contactId.trim(), contactName = resolvedContactName,
                    totalAmount = grandTotal, lines = finalLines, notes = notes.trim().take(300)
                )
            }.await())

            Result.success(createdInvoice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
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
    ): Result<Invoice> {
        val docPath = "users/$userId/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(invoiceId.isNotBlank()) { "معرف الفاتورة غير صالح" }
            require(draftLines.isNotEmpty() && draftLines.size <= 50) { "يجب إضافة صنف واحد على الأقل للفاتورة" }
            if (paymentMethod == PaymentMethod.CREDIT) {
                require(contactId.isNotBlank()) { "يرجى اختيار العميل أو المورد للفاتورة الآجلة" }
            }

            val manualNumber = invoiceNumber.trim()
            if (manualNumber.isNotEmpty()) {
                val existingInv = userDoc(uid).collection("invoices")
                    .whereEqualTo("invoiceNumber", manualNumber)
                    .get()
                    .await()
                val dupOther = existingInv.documents.any { it.id != invoiceId }
                require(!dupOther) { "رقم الفاتورة ($manualNumber) مسجل مسبقًا، لا يُسمح بالتكرار" }
            }

            val invoiceDocRef = userDoc(uid).collection("invoices").document(invoiceId)
            val resolvedContactName = contactName.trim().ifEmpty {
                if (invoiceType == InvoiceType.PURCHASE) "مورد نقدي" else "عميل نقدي"
            }.take(120)

            val updatedInvoice = db.runTransaction { tx ->
                // 1. Read old invoice first
                val oldSnap = tx.get(invoiceDocRef)
                val oldInvoice = oldSnap.toObject(Invoice::class.java)
                    ?: throw IllegalStateException("الفاتورة غير موجودة")

                // 2. Read all distinct referenced products & contacts BEFORE any writes
                val draftProductIds = draftLines.map { it.productId }.toSet()
                val allProductIds = (oldInvoice.lines.map { it.productId } + draftLines.map { it.productId })
                    .filter { it.isNotBlank() }
                    .distinct()

                val productRefs = mutableMapOf<String, com.google.firebase.firestore.DocumentReference>()
                val origProducts = mutableMapOf<String, Product>()
                val workingProducts = mutableMapOf<String, Product>()

                for (pid in allProductIds) {
                    val pRef = userDoc(uid).collection("products").document(pid)
                    val pSnap = tx.get(pRef)
                    val prod = pSnap.toObject(Product::class.java)
                    if (prod == null) {
                        if (pid in draftProductIds) {
                            throw IllegalStateException("الصنف غير موجود في المخزون")
                        }
                    } else {
                        productRefs[pid] = pRef
                        origProducts[pid] = prod
                        workingProducts[pid] = prod
                    }
                }

                val contactIdsToRead = listOfNotNull(
                    oldInvoice.contactId.takeIf { oldInvoice.resolvedPaymentMethod == PaymentMethod.CREDIT && it.isNotBlank() },
                    contactId.takeIf { paymentMethod == PaymentMethod.CREDIT && it.isNotBlank() }
                ).distinct()

                val contactRefs = mutableMapOf<String, com.google.firebase.firestore.DocumentReference>()
                val workingBalances = mutableMapOf<String, Double>()
                for (cid in contactIdsToRead) {
                    val cRef = userDoc(uid).collection("contacts").document(cid)
                    val cSnap = tx.get(cRef)
                    val c = cSnap.toObject(Contact::class.java)
                    if (c == null) {
                        if (paymentMethod == PaymentMethod.CREDIT && cid == contactId) {
                            throw IllegalStateException("الحساب المحدد غير موجود")
                        }
                    } else {
                        contactRefs[cid] = cRef
                        workingBalances[cid] = c.balance
                    }
                }

                // 3. Reverse old invoice stock effects in memory
                for (oldLine in oldInvoice.lines) {
                    val cur = workingProducts[oldLine.productId] ?: continue
                    val restoredQty = if (oldInvoice.resolvedType == InvoiceType.PURCHASE) {
                        (cur.stockBaseQty - oldLine.baseQty).coerceAtLeast(0.0)
                    } else {
                        cur.stockBaseQty + oldLine.baseQty
                    }
                    workingProducts[oldLine.productId] = cur.copy(stockBaseQty = restoredQty)
                }

                // 4. Apply new draft lines in memory
                val finalLines = mutableListOf<InvoiceLine>()
                var grandTotal = 0.0
                for (draft in draftLines) {
                    val currentProd = workingProducts[draft.productId]
                        ?: throw IllegalStateException("الصنف غير موجود في المخزون")
                    if (invoiceType == InvoiceType.PURCHASE) {
                        val computed = UnitCostEngine.computePurchaseLine(
                            product = currentProd,
                            unitName = draft.unitName,
                            unitQty = draft.unitQty,
                            unitPrice = draft.unitPrice
                        ).getOrThrow()
                        finalLines.add(computed.line)
                        grandTotal += computed.line.lineTotal
                        workingProducts[draft.productId] = currentProd.copy(
                            stockBaseQty = computed.newStockBaseQty,
                            baseCost = computed.updatedBaseCost
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
                        workingProducts[draft.productId] = currentProd.copy(
                            stockBaseQty = computed.newStockBaseQty
                        )
                    }
                }

                // 5. Compute updated contact balances
                if (oldInvoice.resolvedPaymentMethod == PaymentMethod.CREDIT && oldInvoice.contactId.isNotBlank()) {
                    workingBalances[oldInvoice.contactId]?.let { prev ->
                        workingBalances[oldInvoice.contactId] = prev - oldInvoice.totalAmount
                    }
                }
                if (paymentMethod == PaymentMethod.CREDIT && contactId.isNotBlank()) {
                    val curBal = workingBalances[contactId]
                        ?: throw IllegalStateException("الحساب المحدد غير موجود")
                    workingBalances[contactId] = curBal + grandTotal
                }

                val cleanNumber = manualNumber.ifEmpty { oldInvoice.invoiceNumber }.take(40)
                val serializedLines = finalLines.map { line ->
                    mapOf(
                        "productId" to line.productId,
                        "productName" to line.productName,
                        "unitName" to line.unitName,
                        "cumulativeFactor" to line.cumulativeFactor,
                        "unitQty" to line.unitQty,
                        "unitPrice" to line.unitPrice,
                        "baseQty" to line.baseQty,
                        "lineTotal" to line.lineTotal
                    )
                }

                // 6. Execute writes
                tx.update(
                    invoiceDocRef,
                    mapOf(
                        "invoiceNumber" to cleanNumber,
                        "invoiceType" to invoiceType.name,
                        "paymentMethod" to paymentMethod.name,
                        "contactId" to contactId.trim(),
                        "contactName" to resolvedContactName,
                        "totalAmount" to grandTotal,
                        "lines" to serializedLines,
                        "notes" to notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                for ((pid, updatedProd) in workingProducts) {
                    val orig = origProducts.getValue(pid)
                    if (updatedProd.stockBaseQty != orig.stockBaseQty || updatedProd.baseCost != orig.baseCost) {
                        val pUpdates = mutableMapOf<String, Any>(
                            "stockBaseQty" to updatedProd.stockBaseQty,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (updatedProd.baseCost != orig.baseCost) {
                            pUpdates["baseCost"] = updatedProd.baseCost
                        }
                        tx.update(productRefs.getValue(pid), pUpdates)
                    }
                }

                for ((cid, newBalance) in workingBalances) {
                    tx.update(
                        contactRefs.getValue(cid),
                        mapOf(
                            "balance" to newBalance,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                oldInvoice.copy(
                    invoiceNumber = cleanNumber,
                    invoiceType = invoiceType.name,
                    paymentMethod = paymentMethod.name,
                    contactId = contactId.trim(),
                    contactName = resolvedContactName,
                    totalAmount = grandTotal,
                    lines = finalLines,
                    notes = notes.trim().take(300)
                )
            }.await()

            Result.success(updatedInvoice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit> {
        val docPath = "users/$userId/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(invoiceId.isNotBlank()) { "معرف الفاتورة غير صالح" }

            val invoiceDocRef = userDoc(uid).collection("invoices").document(invoiceId)

            db.runTransaction { tx ->
                val oldSnap = tx.get(invoiceDocRef)
                val oldInvoice = oldSnap.toObject(Invoice::class.java)
                    ?: throw IllegalStateException("الفاتورة غير موجودة")

                val productRefs = mutableMapOf<String, com.google.firebase.firestore.DocumentReference>()
                val workingStocks = mutableMapOf<String, Double>()
                for (pid in oldInvoice.lines.map { it.productId }.filter { it.isNotBlank() }.distinct()) {
                    val pRef = userDoc(uid).collection("products").document(pid)
                    val prod = tx.get(pRef).toObject(Product::class.java)
                    if (prod != null) {
                        productRefs[pid] = pRef
                        workingStocks[pid] = prod.stockBaseQty
                    }
                }

                val contactPair = if (oldInvoice.resolvedPaymentMethod == PaymentMethod.CREDIT && oldInvoice.contactId.isNotBlank()) {
                    val cRef = userDoc(uid).collection("contacts").document(oldInvoice.contactId)
                    val contact = tx.get(cRef).toObject(Contact::class.java)
                    contact?.let { cRef to it }
                } else null

                for (line in oldInvoice.lines) {
                    val curStock = workingStocks[line.productId] ?: continue
                    workingStocks[line.productId] = if (oldInvoice.resolvedType == InvoiceType.PURCHASE) {
                        (curStock - line.baseQty).coerceAtLeast(0.0)
                    } else {
                        curStock + line.baseQty
                    }
                }

                for ((pid, newStock) in workingStocks) {
                    tx.update(
                        productRefs.getValue(pid),
                        mapOf(
                            "stockBaseQty" to newStock,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (contactPair != null) {
                    val (cRef, contact) = contactPair
                    tx.update(
                        cRef,
                        mapOf(
                            "balance" to (contact.balance - oldInvoice.totalAmount),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.delete(invoiceDocRef)
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun recordPayment(
        userId: String,
        contactId: String,
        amount: Double,
        notes: String
    ): Result<Unit> {
        val basePath = "users/$userId/payments"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(contactId.isNotBlank()) { "يرجى اختيار الحساب" }
            require(amount.isFinite() && amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }

            val paymentRef = userDoc(uid).collection("payments").document()
            val contactRef = userDoc(uid).collection("contacts").document(contactId)

            db.runTransaction { tx ->
                val contactSnap = tx.get(contactRef)
                val contact = contactSnap.toObject(Contact::class.java)
                    ?: throw IllegalStateException("الحساب غير موجود")

                tx.set(
                    paymentRef,
                    mapOf(
                        "userId" to uid,
                        "contactId" to contactId,
                        "contactName" to contact.name.take(120),
                        "contactType" to contact.contactType,
                        "amount" to amount,
                        "notes" to notes.trim().take(300),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    contactRef,
                    mapOf(
                        "balance" to (contact.balance - amount),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    override suspend fun updatePayment(
        userId: String,
        paymentId: String,
        amount: Double,
        notes: String
    ): Result<Unit> {
        val docPath = "users/$userId/payments/$paymentId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(paymentId.isNotBlank()) { "معرف السند غير صالح" }
            require(amount.isFinite() && amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }

            val paymentRef = userDoc(uid).collection("payments").document(paymentId)

            db.runTransaction { tx ->
                val paySnap = tx.get(paymentRef)
                val oldPayment = paySnap.toObject(Payment::class.java)
                    ?: throw IllegalStateException("السند غير موجود")

                val contactPair = if (oldPayment.contactId.isNotBlank()) {
                    val cRef = userDoc(uid).collection("contacts").document(oldPayment.contactId)
                    val c = tx.get(cRef).toObject(Contact::class.java)
                    c?.let { cRef to it }
                } else null

                val delta = oldPayment.amount - amount
                if (contactPair != null) {
                    val (cRef, contact) = contactPair
                    tx.update(
                        cRef,
                        mapOf(
                            "balance" to (contact.balance + delta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.update(
                    paymentRef,
                    mapOf(
                        "amount" to amount,
                        "notes" to notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deletePayment(userId: String, paymentId: String): Result<Unit> {
        val docPath = "users/$userId/payments/$paymentId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            require(paymentId.isNotBlank()) { "معرف السند غير صالح" }

            val paymentRef = userDoc(uid).collection("payments").document(paymentId)

            db.runTransaction { tx ->
                val paySnap = tx.get(paymentRef)
                val oldPayment = paySnap.toObject(Payment::class.java)
                    ?: throw IllegalStateException("السند غير موجود")

                val contactPair = if (oldPayment.contactId.isNotBlank()) {
                    val cRef = userDoc(uid).collection("contacts").document(oldPayment.contactId)
                    val c = tx.get(cRef).toObject(Contact::class.java)
                    c?.let { cRef to it }
                } else null

                if (contactPair != null) {
                    val (cRef, contact) = contactPair
                    tx.update(
                        cRef,
                        mapOf(
                            "balance" to (contact.balance + oldPayment.amount),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.delete(paymentRef)
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun exportBackup(userId: String): Result<BackupBundle> {
        val basePath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }
            val products = userDoc(uid).collection("products").get().await()
                .toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val contacts = userDoc(uid).collection("contacts").get().await()
                .toObjects(Contact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val invoices = userDoc(uid).collection("invoices").get().await()
                .toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val payments = userDoc(uid).collection("payments").get().await()
                .toObjects(Payment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            Result.success(
                BackupBundle(
                    schemaVersion = 1,
                    exportedAtMillis = System.currentTimeMillis(),
                    products = products,
                    contacts = contacts,
                    invoices = invoices,
                    payments = payments
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, basePath)
            Result.failure(e)
        }
    }

    override suspend fun importBackup(userId: String, bundle: BackupBundle): Result<RestoreSummary> {
        val basePath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "تعارض هوية المستخدم" }

            val existingProducts = userDoc(uid).collection("products").get().await()
                .toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val existingContacts = userDoc(uid).collection("contacts").get().await()
                .toObjects(Contact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val existingInvoices = userDoc(uid).collection("invoices").get().await()
                .toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val existingPayments = userDoc(uid).collection("payments").get().await()
                .toObjects(Payment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            var importedProducts = 0
            var skippedDupProducts = 0
            val seenNames = existingProducts.map { it.name.trim().lowercase() }.toMutableSet()
            val seenBarcodes = existingProducts.mapNotNull {
                it.barcode.trim().takeIf { b -> b.isNotEmpty() }?.lowercase()
            }.toMutableSet()

            for (p in bundle.products) {
                val nameKey = p.name.trim().lowercase()
                val barcodeKey = p.barcode.trim().lowercase()
                val dup = nameKey in seenNames || (barcodeKey.isNotEmpty() && barcodeKey in seenBarcodes)
                if (dup || nameKey.isEmpty()) {
                    skippedDupProducts++
                    continue
                }
                val docRef = userDoc(uid).collection("products").document()
                val tiers = p.unitTiers.map { mapOf("name" to it.name.trim(), "factorToPrevious" to it.factorToPrevious) }
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to p.name.trim().take(120),
                        "barcode" to p.barcode.trim().take(64),
                        "baseUnitName" to p.baseUnitName.trim().ifEmpty { "حبة" }.take(40),
                        "defaultUnitName" to p.defaultUnitName.trim().ifEmpty { "حبة" }.take(40),
                        "unitTiers" to tiers,
                        "baseCost" to p.baseCost.coerceAtLeast(0.0),
                        "baseSellPrice" to p.baseSellPrice.coerceAtLeast(0.0),
                        "stockBaseQty" to p.stockBaseQty.coerceAtLeast(0.0),
                        "trackExpiry" to p.trackExpiry,
                        "expiryDate" to p.expiryDate.take(20),
                        "minStockAlert" to p.minStockAlert.coerceAtLeast(0.0),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                seenNames.add(nameKey)
                if (barcodeKey.isNotEmpty()) seenBarcodes.add(barcodeKey)
                importedProducts++
            }

            var importedContacts = 0
            val seenContacts = existingContacts.map { "${it.contactType}:${it.name.trim().lowercase()}" }.toMutableSet()
            for (c in bundle.contacts) {
                val key = "${c.contactType}:${c.name.trim().lowercase()}"
                if (c.name.isBlank() || key in seenContacts) continue
                userDoc(uid).collection("contacts").document().set(
                    mapOf(
                        "userId" to uid,
                        "name" to c.name.trim().take(120),
                        "contactType" to c.resolvedType.name,
                        "phone" to c.phone.trim().take(30),
                        "balance" to c.balance,
                        "notes" to c.notes.trim().take(300),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                seenContacts.add(key)
                importedContacts++
            }

            var importedInvoices = 0
            val seenInvoiceNumbers = existingInvoices.map { it.invoiceNumber.trim() }.toMutableSet()
            for (inv in bundle.invoices) {
                if (inv.invoiceNumber.isBlank() || inv.lines.isEmpty() || inv.invoiceNumber.trim() in seenInvoiceNumbers) continue
                userDoc(uid).collection("invoices").document().set(
                    mapOf(
                        "userId" to uid,
                        "invoiceNumber" to inv.invoiceNumber.trim().take(40),
                        "invoiceType" to inv.resolvedType.name,
                        "paymentMethod" to inv.resolvedPaymentMethod.name,
                        "contactId" to inv.contactId.take(128),
                        "contactName" to inv.contactName.take(120),
                        "totalAmount" to inv.totalAmount.coerceAtLeast(0.0),
                        "lines" to inv.lines.map { line ->
                            mapOf(
                                "productId" to line.productId.ifBlank { "restored_item" },
                                "productName" to line.productName.ifBlank { "صنف" }.take(120),
                                "unitName" to line.unitName.ifBlank { "حبة" }.take(40),
                                "cumulativeFactor" to line.cumulativeFactor.coerceAtLeast(0.0001),
                                "unitQty" to line.unitQty.coerceAtLeast(0.0001),
                                "unitPrice" to line.unitPrice.coerceAtLeast(0.0),
                                "baseQty" to line.baseQty.coerceAtLeast(0.0001),
                                "lineTotal" to line.lineTotal.coerceAtLeast(0.0)
                            )
                        },
                        "notes" to inv.notes.take(300),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                seenInvoiceNumbers.add(inv.invoiceNumber.trim())
                importedInvoices++
            }

            var importedPayments = 0
            val existingPayCount = existingPayments.size
            if (bundle.payments.isNotEmpty() && existingPayCount == 0) {
                for (pay in bundle.payments) {
                    if (pay.amount <= 0.0 || pay.contactName.isBlank()) continue
                    userDoc(uid).collection("payments").document().set(
                        mapOf(
                            "userId" to uid,
                            "contactId" to pay.contactId.ifBlank { "restored_contact" },
                            "contactName" to pay.contactName.take(120),
                            "contactType" to pay.resolvedContactType.name,
                            "amount" to pay.amount,
                            "notes" to pay.notes.take(300),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    importedPayments++
                }
            }

            Result.success(
                RestoreSummary(
                    importedProducts = importedProducts,
                    skippedDuplicateProducts = skippedDupProducts,
                    importedContacts = importedContacts,
                    importedInvoices = importedInvoices,
                    importedPayments = importedPayments
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }
}

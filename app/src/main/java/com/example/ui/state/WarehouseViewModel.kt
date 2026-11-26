package com.example.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackupBundle
import com.example.data.BackupJsonCodec
import com.example.data.InvoiceDraftLine
import com.example.data.RestoreSummary
import com.example.data.WarehouseDataSource
import com.example.domain.Contact
import com.example.domain.ContactType
import com.example.domain.CostCalculationResult
import com.example.domain.Invoice
import com.example.domain.InvoiceType
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.UiState
import com.example.domain.UnitCostEngine
import com.example.domain.UnitTier
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WarehouseViewModel(
    private val dataSource: WarehouseDataSource,
    private val currentUserId: String
) : ViewModel() {

    val isCloudBacked: Boolean get() = dataSource.isCloudBacked

    private val productFormController = ProductFormController()

    private val _productFormState = MutableStateFlow(productFormController.state)
    val productFormState: StateFlow<ProductFormState> = _productFormState.asStateFlow()

    private val _isProductFormOpen = MutableStateFlow(false)
    val isProductFormOpen: StateFlow<Boolean> = _isProductFormOpen.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _activeReceiptInvoice = MutableStateFlow<Invoice?>(null)
    val activeReceiptInvoice: StateFlow<Invoice?> = _activeReceiptInvoice.asStateFlow()

    val productsState: StateFlow<UiState<List<Product>>> = dataSource.observeProducts(currentUserId)
        .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing products", root)
            emit(UiState.Error(root.localizedMessage ?: "تعذر تحميل المنتجات"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val contactsState: StateFlow<UiState<List<Contact>>> = dataSource.observeContacts(currentUserId)
        .map<List<Contact>, UiState<List<Contact>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing contacts", root)
            emit(UiState.Error(root.localizedMessage ?: "تعذر تحميل الحسابات"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val invoicesState: StateFlow<UiState<List<Invoice>>> = dataSource.observeInvoices(currentUserId)
        .map<List<Invoice>, UiState<List<Invoice>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing invoices", root)
            emit(UiState.Error(root.localizedMessage ?: "تعذر تحميل الفواتير"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val paymentsState: StateFlow<UiState<List<Payment>>> = dataSource.observePayments(currentUserId)
        .map<List<Payment>, UiState<List<Payment>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing payments", root)
            emit(UiState.Error(root.localizedMessage ?: "تعذر تحميل سندات السداد"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun showMessage(message: String) {
        _actionMessage.value = message
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun openInvoiceReceipt(invoice: Invoice) {
        _activeReceiptInvoice.value = invoice
    }

    fun dismissInvoiceReceipt() {
        _activeReceiptInvoice.value = null
    }

    fun openNewProductForm() {
        productFormController.resetForNewProduct()
        _productFormState.value = productFormController.state
        _isProductFormOpen.value = true
    }

    fun openEditProductForm(product: Product) {
        productFormController.loadExistingProduct(product)
        _productFormState.value = productFormController.state
        _isProductFormOpen.value = true
    }

    fun closeProductForm() {
        _isProductFormOpen.value = false
    }

    fun updateProductName(value: String) {
        productFormController.onNameChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateProductBarcode(value: String) {
        productFormController.onBarcodeChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateDefaultUnit(value: String) {
        productFormController.onDefaultUnitChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateTrackExpiry(enabled: Boolean) {
        productFormController.onTrackExpiryChanged(enabled)
        _productFormState.value = productFormController.state
    }

    fun updateExpiryDate(value: String) {
        productFormController.onExpiryDateChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateMinStockAlert(value: String) {
        productFormController.onMinStockAlertChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateStockBaseQty(value: String) {
        productFormController.onStockBaseQtyChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateBaseUnitName(value: String) {
        productFormController.onBaseUnitNameChanged(value)
        _productFormState.value = productFormController.state
    }

    fun addUnitTier() {
        productFormController.addUnitTier()
        _productFormState.value = productFormController.state
    }

    fun removeUnitTier(index: Int) {
        productFormController.removeUnitTier(index)
        _productFormState.value = productFormController.state
    }

    fun updateUnitTierName(index: Int, name: String) {
        productFormController.onUnitTierNameChanged(index, name)
        _productFormState.value = productFormController.state
    }

    fun updateUnitTierFactor(index: Int, factor: String) {
        productFormController.onUnitTierFactorChanged(index, factor)
        _productFormState.value = productFormController.state
    }

    fun updateLargeUnitCost(value: String) {
        productFormController.onLargeUnitCostChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateBaseCostManually(value: String) {
        productFormController.onBaseCostManuallyChanged(value)
        _productFormState.value = productFormController.state
    }

    fun updateBaseSellPrice(value: String) {
        productFormController.onBaseSellPriceChanged(value)
        _productFormState.value = productFormController.state
    }

    fun submitProductForm() {
        if (_productFormState.value.isSaving) return
        val validated = productFormController.validateForSubmit()
            .getOrElse { err ->
                productFormController.setFormError(err.message)
                _productFormState.value = productFormController.state
                return
            }

        productFormController.setSaving(true)
        _productFormState.value = productFormController.state

        viewModelScope.launch {
            val result = dataSource.saveProduct(
                userId = currentUserId,
                existingId = validated.existingId,
                name = validated.name,
                barcode = validated.barcode,
                baseUnitName = validated.baseUnitName,
                defaultUnitName = validated.defaultUnitName,
                unitTiers = validated.unitTiers,
                baseCost = validated.baseCost,
                baseSellPrice = validated.baseSellPrice,
                stockBaseQty = validated.stockBaseQty,
                trackExpiry = validated.trackExpiry,
                expiryDate = validated.expiryDate,
                minStockAlert = validated.minStockAlert
            )
            result.onSuccess {
                productFormController.setSaving(false)
                _productFormState.value = productFormController.state
                _isProductFormOpen.value = false
                _actionMessage.value = if (validated.existingId == null) {
                    "تمت إضافة الصنف وحفظ تكلفة الوحدة الأصغر بنجاح"
                } else {
                    "تم تحديث بيانات الصنف بنجاح"
                }
            }.onFailure { err ->
                productFormController.setFormError(err.localizedMessage ?: "تعذر حفظ المنتج")
                _productFormState.value = productFormController.state
            }
        }
    }

    suspend fun createProductFromPurchase(
        name: String,
        barcode: String,
        baseUnitName: String,
        wholesaleUnitName: String,
        factor: Double,
        wholesaleUnitCost: Double,
        baseSellPrice: Double
    ): Result<Product> {
        val cleanName = name.trim()
        val cleanBase = baseUnitName.trim().ifEmpty { "حبة" }
        val cleanWholesale = wholesaleUnitName.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال اسم الصنف"))
        }
        val tiers = if (cleanWholesale.isNotEmpty() && !cleanWholesale.equals(cleanBase, ignoreCase = true)) {
            if (!factor.isFinite() || factor <= 0.0) {
                return Result.failure(IllegalArgumentException("معامل التحويل يجب أن يكون أكبر من 0"))
            }
            listOf(UnitTier(name = cleanWholesale, factorToPrevious = factor))
        } else {
            emptyList()
        }
        val effectiveFactor = if (tiers.isEmpty()) 1.0 else factor
        val baseCostResult = UnitCostEngine.calculateBaseCostFromLargeUnit(
            largeUnitCostRaw = wholesaleUnitCost.toString(),
            cumulativeFactor = effectiveFactor
        )
        val computedBaseCost = when (baseCostResult) {
            is CostCalculationResult.Success -> baseCostResult.baseCost
            is CostCalculationResult.Error -> return Result.failure(IllegalArgumentException(baseCostResult.message))
            CostCalculationResult.BlankInput -> 0.0
        }
        val defaultUnit = tiers.lastOrNull()?.name ?: cleanBase

        // Keep the product transient until the purchase invoice is committed.
        // A sale invoice may also use this object as a free line, but must never persist it.
        Result.success(
            Product(
                id = "draft_${java.util.UUID.randomUUID().toString().replace("-", "")}",
                userId = currentUserId,
                name = cleanName,
                barcode = barcode.trim(),
                baseUnitName = cleanBase,
                defaultUnitName = defaultUnit,
                unitTiers = tiers,
                baseCost = computedBaseCost,
                baseSellPrice = baseSellPrice.coerceAtLeast(0.0),
                stockBaseQty = 0.0
            )
        )
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.deleteProduct(currentUserId, productId)
                .onSuccess { _actionMessage.value = "تم حذف الصنف" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "تعذر حذف الصنف" }
            _isBusy.value = false
        }
    }

    fun saveContact(
        existingId: String?,
        name: String,
        contactType: ContactType,
        phone: String,
        openingBalance: Double,
        notes: String,
        onSuccess: () -> Unit
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.saveContact(
                userId = currentUserId,
                existingId = existingId,
                name = name,
                contactType = contactType,
                phone = phone,
                openingBalance = openingBalance,
                notes = notes
            ).onSuccess {
                _actionMessage.value = if (existingId == null) "تمت إضافة الحساب بنجاح" else "تم تحديث الحساب"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "تعذر حفظ الحساب"
            }
            _isBusy.value = false
        }
    }

    fun deleteContact(contactId: String, onSuccess: () -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.deleteContact(currentUserId, contactId)
                .onSuccess {
                    _actionMessage.value = "تم حذف الحساب"
                    onSuccess()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "تعذر حذف الحساب" }
            _isBusy.value = false
        }
    }

    fun submitInvoice(
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        lines: List<InvoiceDraftLine>,
        notes: String,
        onSuccess: () -> Unit
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.postInvoice(
                userId = currentUserId,
                invoiceNumber = invoiceNumber,
                invoiceType = invoiceType,
                paymentMethod = paymentMethod,
                contactId = contactId,
                contactName = contactName,
                draftLines = lines,
                notes = notes
            ).onSuccess { createdInvoice ->
                _actionMessage.value = if (invoiceType == InvoiceType.PURCHASE) {
                    "تم اعتماد فاتورة الشراء وتحديث المخزون والتكلفة"
                } else {
                    "تم اعتماد فاتورة البيع وخصم المخزون بنجاح"
                }
                _activeReceiptInvoice.value = createdInvoice
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "تعذر اعتماد الفاتورة"
            }
            _isBusy.value = false
        }
    }

    fun updateInvoice(
        invoiceId: String,
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        lines: List<InvoiceDraftLine>,
        notes: String,
        onSuccess: () -> Unit
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.updateInvoice(
                userId = currentUserId,
                invoiceId = invoiceId,
                invoiceNumber = invoiceNumber,
                invoiceType = invoiceType,
                paymentMethod = paymentMethod,
                contactId = contactId,
                contactName = contactName,
                draftLines = lines,
                notes = notes
            ).onSuccess { updatedInvoice ->
                _actionMessage.value = "تم تحديث الفاتورة #${updatedInvoice.invoiceNumber} وضبط المخزون والأرصدة"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "تعذر تحديث الفاتورة"
            }
            _isBusy.value = false
        }
    }

    fun deleteInvoice(invoiceId: String, onSuccess: () -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.deleteInvoice(currentUserId, invoiceId)
                .onSuccess {
                    _actionMessage.value = "تم حذف الفاتورة وإرجاع المخزون والرصيد"
                    onSuccess()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "تعذر حذف الفاتورة" }
            _isBusy.value = false
        }
    }

    fun submitPayment(
        contactId: String,
        amount: Double,
        notes: String,
        onSuccess: () -> Unit
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.recordPayment(
                userId = currentUserId,
                contactId = contactId,
                amount = amount,
                notes = notes
            ).onSuccess {
                _actionMessage.value = "تم تسجيل السند وتحديث الرصيد بنجاح"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "تعذر تسجيل السند"
            }
            _isBusy.value = false
        }
    }

    fun updatePayment(
        paymentId: String,
        amount: Double,
        notes: String,
        onSuccess: () -> Unit
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.updatePayment(
                userId = currentUserId,
                paymentId = paymentId,
                amount = amount,
                notes = notes
            ).onSuccess {
                _actionMessage.value = "تم تعديل السند وتحديث الرصيد بنجاح"
                onSuccess()
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "تعذر تعديل السند"
            }
            _isBusy.value = false
        }
    }

    fun deletePayment(paymentId: String, onSuccess: () -> Unit = {}) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            dataSource.deletePayment(currentUserId, paymentId)
                .onSuccess {
                    _actionMessage.value = "تم حذف السند وإرجاع المبلغ للرصيد"
                    onSuccess()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "تعذر حذف السند" }
            _isBusy.value = false
        }
    }

    suspend fun exportBackupJson(): Result<String> {
        return dataSource.exportBackup(currentUserId).map { bundle ->
            BackupJsonCodec.encode(bundle)
        }
    }

    suspend fun importBackupBundle(bundle: BackupBundle): Result<RestoreSummary> {
        return dataSource.importBackup(currentUserId, bundle)
    }

    private companion object {
        const val TAG = "WarehouseVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InvoiceDraftLine
import com.example.domain.Contact
import com.example.domain.ContactSuggestion
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceLine
import com.example.domain.InvoiceType
import com.example.domain.InvoiceMode
import com.example.domain.NumberFormatUtils
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.SmartSuggestionEngine
import com.example.domain.UiState
import com.example.domain.UnitCostEngine
import com.example.print.PrinterSettingsStore
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.LedgerCanvas
import com.example.ui.theme.LedgerCrimson
import com.example.ui.theme.LedgerGold
import com.example.ui.theme.LedgerGreen
import com.example.ui.theme.LedgerNavy
import com.example.ui.theme.OnEmeraldContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PendingUiLine(
    val key: String,
    val product: Product,
    val unitName: String,
    val cumulativeFactor: Double,
    val unitQty: Double,
    val unitPrice: Double,
    val description: String = "",
    val qtyText: String = NumberFormatUtils.formatInputNumber(unitQty, 2),
    val totalText: String = NumberFormatUtils.formatInputNumber(unitQty * unitPrice, 2),
    val explicitTotal: Double? = null
) {
    val baseQty: Double get() = unitQty * cumulativeFactor
    val lineTotal: Double get() = explicitTotal ?: (unitQty * unitPrice)
    val inferredUnitPrice: Double
        get() = SmartSuggestionEngine.computeInferredUnitPrice(lineTotal, unitQty, unitPrice)
}

@Composable
fun InvoicesScreen(
    productsState: UiState<List<Product>>,
    contactsState: UiState<List<Contact>>,
    invoicesState: UiState<List<Invoice>>,
    isBusy: Boolean,
    onPostInvoice: (
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        lines: List<InvoiceDraftLine>,
        notes: String,
        discount: Double,
        paidAmount: Double?,
        onSuccess: () -> Unit
    ) -> Unit,
    onUpdateInvoice: (
        invoiceId: String,
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        lines: List<InvoiceDraftLine>,
        notes: String,
        discount: Double,
        paidAmount: Double?,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, onSuccess -> onSuccess() },
    onDeleteInvoice: (invoice: Invoice, onSuccess: () -> Unit) -> Unit = { _, onDone -> onDone() },
    onQuickCreateProduct: (suspend (
        name: String,
        barcode: String,
        baseUnitName: String,
        wholesaleUnitName: String,
        factor: Double,
        wholesaleUnitCost: Double,
        baseSellPrice: Double
    ) -> Result<Product>)? = null,
    onOpenReceipt: (Invoice) -> Unit = {},
    initialEditInvoice: Invoice? = null,
    onInitialEditHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val settings = remember { PrinterSettingsStore.load(context) }

    val products = (productsState as? UiState.Success)?.data ?: emptyList()
    val contacts = (contactsState as? UiState.Success)?.data ?: emptyList()
    val savedInvoices = (invoicesState as? UiState.Success)?.data ?: emptyList()

    var showHistory by remember { mutableStateOf(false) }
    var editingInvoice by remember { mutableStateOf<Invoice?>(null) }
    var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }

    BackHandler(enabled = showHistory) {
        showHistory = false
    }

    var invoiceType by remember { mutableStateOf(InvoiceType.SALE) }
    var invoiceMode by remember { mutableStateOf(InvoiceMode.STOCK) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var manualCustomerName by remember { mutableStateOf("") }
    var isCustomerFieldFocused by remember { mutableStateOf(false) }
    var customInvoiceNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var discountInput by remember { mutableStateOf("") }
    var paidInput by remember { mutableStateOf("") }
    var isPaidManuallyEdited by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val draftLines = remember { mutableStateListOf<PendingUiLine>() }
    var lineKeyCounter by remember { mutableIntStateOf(0) }

    // Inline active row inputs & smart product suggestion state
    var draftSearchQuery by remember { mutableStateOf("") }
    var draftQtyInput by remember { mutableStateOf("") }
    var draftTotalInput by remember { mutableStateOf("") }
    var isProductInputFocused by remember { mutableStateOf(false) }
    var unitPickerLineKey by remember { mutableStateOf<String?>(null) }

    val activeTotalFocusRequester = remember { FocusRequester() }
    val activeQtyFocusRequester = remember { FocusRequester() }
    val activeNameFocusRequester = remember { FocusRequester() }
    val hasActiveRowInput = draftSearchQuery.isNotBlank() ||
        draftQtyInput.isNotBlank() ||
        draftTotalInput.isNotBlank()

    var showCustomerDialog by remember { mutableStateOf(false) }
    var showInvoiceNumDialog by remember { mutableStateOf(false) }
    var showAddLineDialog by remember { mutableStateOf(false) }
    var showFreeSaleDialog by remember { mutableStateOf(false) }
    var showQuickWholesaleDialog by remember { mutableStateOf(false) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }

    val displayDate = remember(editingInvoice) {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = editingInvoice?.createdAt?.toDate() ?: Date()
        formatter.format(date)
    }
    val autoInvoiceNumber = remember(savedInvoices.size) {
        String.format(Locale.US, "%04d", savedInvoices.size + 1)
    }
    val displayInvoiceNumber = customInvoiceNumber.ifBlank { autoInvoiceNumber }
    val subtotalAmount = draftLines.sumOf { it.lineTotal }
    val discountAmount = NumberFormatUtils.parseDecimal(discountInput)?.coerceAtLeast(0.0) ?: 0.0
    val netTotalAmount = (subtotalAmount - discountAmount).coerceAtLeast(0.0)
    val effectivePaidAmount = if (isPaidManuallyEdited) {
        NumberFormatUtils.parseDecimal(paidInput)?.coerceAtLeast(0.0) ?: 0.0
    } else if (paymentMethod == PaymentMethod.CASH) {
        netTotalAmount
    } else {
        0.0
    }
    val remainingAmount = (netTotalAmount - effectivePaidAmount).coerceAtLeast(0.0)
    val grandTotal = netTotalAmount

    val customerSuggestions = remember(manualCustomerName, contacts, savedInvoices, invoiceType) {
        SmartSuggestionEngine.suggestContacts(
            query = manualCustomerName,
            contacts = contacts,
            savedInvoices = savedInvoices,
            invoiceType = invoiceType,
            limit = 5
        )
    }

    val productSuggestions = remember(draftSearchQuery, products, invoiceType, invoiceMode) {
        if (invoiceMode == InvoiceMode.FREE) emptyList() else SmartSuggestionEngine.suggestProducts(
            query = draftSearchQuery,
            products = products,
            invoiceType = invoiceType,
            limit = 6
        )
    }

    fun effectiveAvailableStock(product: Product): Double {
        val editing = editingInvoice ?: return product.stockBaseQty
        val oldQty = editing.lines
            .filter { it.productId == product.id }
            .sumOf { it.baseQty }
        return when (editing.resolvedType) {
            InvoiceType.SALE -> product.stockBaseQty + oldQty
            InvoiceType.PURCHASE -> (product.stockBaseQty - oldQty).coerceAtLeast(0.0)
        }
    }

    fun resetDraft(keepMode: Boolean = false) {
        editingInvoice = null
        draftLines.clear()
        selectedContact = null
        manualCustomerName = ""
        isCustomerFieldFocused = false
        customInvoiceNumber = ""
        notes = ""
        discountInput = ""
        paidInput = ""
        isPaidManuallyEdited = false
        draftSearchQuery = ""
        draftQtyInput = ""
        draftTotalInput = ""
        isProductInputFocused = false
        unitPickerLineKey = null
        localError = null
        paymentMethod = PaymentMethod.CASH
        if (!keepMode) {
            invoiceMode = InvoiceMode.STOCK
        }
    }

    fun loadInvoiceForEdit(invoice: Invoice) {
        resetDraft()
        editingInvoice = invoice
        invoiceType = invoice.resolvedType
        invoiceMode = invoice.resolvedMode
        paymentMethod = invoice.resolvedPaymentMethod
        customInvoiceNumber = invoice.invoiceNumber
        notes = invoice.notes
        discountInput = if (invoice.discount > 0.0) {
            NumberFormatUtils.formatInputNumber(invoice.discount, 2)
        } else {
            ""
        }
        isPaidManuallyEdited = invoice.paidAmount != null
        paidInput = if (invoice.paidAmount != null) {
            NumberFormatUtils.formatInputNumber(invoice.resolvedPaidAmount, 2)
        } else if (invoice.resolvedPaymentMethod == PaymentMethod.CASH) {
            ""
        } else {
            "0"
        }
        val matchedContact = if (invoice.contactId.isNotBlank()) {
            contacts.firstOrNull { it.id == invoice.contactId }
        } else {
            null
        }
        selectedContact = matchedContact
        manualCustomerName = matchedContact?.name ?: invoice.contactName

        val missingProducts = mutableListOf<String>()
        invoice.lines.forEach { line ->
            val prod = products.firstOrNull { it.id == line.productId }
            if (prod != null || line.productId.isBlank()) {
                val lineProduct = prod ?: Product(
                    id = "free_${line.productName.hashCode()}_${line.unitName.hashCode()}",
                    name = line.productName,
                    baseUnitName = if (line.cumulativeFactor == 1.0) line.unitName else "حبة",
                    defaultUnitName = line.unitName,
                    unitTiers = if (line.cumulativeFactor == 1.0) emptyList() else listOf(
                        com.example.domain.UnitTier(line.unitName, line.cumulativeFactor)
                    ),
                    baseSellPrice = line.unitPrice
                )
                lineKeyCounter++
                draftLines.add(
                    PendingUiLine(
                        key = "line_$lineKeyCounter",
                        product = lineProduct,
                        unitName = line.unitName,
                        cumulativeFactor = line.cumulativeFactor,
                        unitQty = line.unitQty,
                        unitPrice = line.unitPrice,
                        description = line.description,
                        qtyText = NumberFormatUtils.formatInputNumber(line.unitQty, 2),
                        totalText = NumberFormatUtils.formatInputNumber(line.lineTotal, 2),
                        explicitTotal = line.lineTotal
                    )
                )
            } else {
                missingProducts.add(line.productName)
            }
        }
        if (missingProducts.isNotEmpty()) {
            localError = "تعذر تحميل بعض أصناف الفاتورة: ${missingProducts.joinToString("، ")}"
        }
        showHistory = false
    }

    LaunchedEffect(initialEditInvoice) {
        if (initialEditInvoice != null) {
            loadInvoiceForEdit(initialEditInvoice)
            onInitialEditHandled()
        }
    }

    fun addProductFromSuggestion(
        prod: Product,
        unitName: String,
        factor: Double,
        targetFocusAfterAdd: FocusRequester? = null
    ): Boolean {
        if (invoiceMode == InvoiceMode.FREE) {
            localError = "الفاتورة الحرة تقبل الأصناف اليدوية فقط؛ استخدم خيار إضافة صنف حر"
            return false
        }
        val dupLine = draftLines.any {
            it.product.id == prod.id && it.unitName.equals(unitName, ignoreCase = true)
        }
        if (dupLine) {
            localError = "تمت إضافة (${prod.name}) بوحدة ($unitName) مسبقًا، لا يُسمح بالتكرار"
            return false
        }
        val parsedQty = NumberFormatUtils.parseDecimal(draftQtyInput)
        if (draftQtyInput.isNotBlank() && (parsedQty == null || !parsedQty.isFinite() || parsedQty <= 0.0)) {
            localError = "يرجى إدخال كمية أكبر من صفر في السطر الحالي"
            return false
        }
        val q = parsedQty?.takeIf { it > 0.0 } ?: 1.0
        val defaultPrice = if (invoiceType == InvoiceType.PURCHASE) {
            UnitCostEngine.calculateLargeUnitCostFromBase(prod.baseCost, factor)
        } else {
            UnitCostEngine.calculateUnitSellPrice(prod.baseSellPrice, factor)
        }
        val preTotal = NumberFormatUtils.parseDecimal(draftTotalInput)?.takeIf { it >= 0.0 }
        val effectiveUnitPrice = if (preTotal != null) preTotal / q else defaultPrice

        val available = effectiveAvailableStock(prod)
        val alreadyInDraft = draftLines.filter { it.product.id == prod.id }.sumOf { it.baseQty }
        if (invoiceType == InvoiceType.SALE && (alreadyInDraft + q * factor) - available > 0.0001) {
            localError = "الكمية المطلوبة من (${prod.name}) تتجاوز المتاح بالمخزون (${NumberFormatUtils.formatNumber(available)} ${prod.baseUnitName})"
            return false
        }

        lineKeyCounter++
        val initialTotal = preTotal ?: (q * effectiveUnitPrice)
        draftLines.add(
            PendingUiLine(
                key = "line_$lineKeyCounter",
                product = prod,
                unitName = unitName,
                cumulativeFactor = factor,
                unitQty = q,
                unitPrice = effectiveUnitPrice,
                qtyText = NumberFormatUtils.formatInputNumber(q, 2),
                totalText = NumberFormatUtils.formatInputNumber(initialTotal, 2),
                explicitTotal = preTotal
            )
        )
        draftSearchQuery = ""
        draftQtyInput = ""
        draftTotalInput = ""
        isProductInputFocused = targetFocusAfterAdd == activeNameFocusRequester
        localError = null
        if (targetFocusAfterAdd != null) {
            targetFocusAfterAdd.requestFocus()
        } else {
            focusManager.clearFocus()
        }
        return true
    }

    /**
     * Commits the current active ledger row into [draftLines] (without modifying inventory)
     * so a fresh empty row is immediately ready for the next item on both SALE and PURCHASE.
     * Purchase additions to inventory happen atomically in one batch when saving the invoice.
     */
    fun commitActiveRowIfReady(
        targetFocusAfterCommit: FocusRequester? = null,
        showIncompleteError: Boolean = true
    ): Boolean {
        val rowHasInput = draftSearchQuery.isNotBlank() ||
            draftQtyInput.isNotBlank() ||
            draftTotalInput.isNotBlank()
        if (!rowHasInput) return true

        val cleanName = draftSearchQuery.trim()
        if (cleanName.isEmpty()) {
            if (showIncompleteError) {
                localError = "يرجى إدخال اسم الصنف في السطر الحالي"
            }
            return false
        }

        val parsedQty = NumberFormatUtils.parseDecimal(draftQtyInput)
        if (draftQtyInput.isNotBlank() && (parsedQty == null || !parsedQty.isFinite() || parsedQty <= 0.0)) {
            if (showIncompleteError) {
                localError = "يرجى إدخال كمية أكبر من صفر في السطر الحالي"
            }
            return false
        }
        val effectiveQty = parsedQty?.takeIf { it > 0.0 } ?: 1.0

        if (invoiceMode == InvoiceMode.STOCK) {
            val matchedProduct = products.firstOrNull {
                it.name.trim().equals(cleanName, ignoreCase = true) ||
                    (it.barcode.isNotBlank() && it.barcode.trim().equals(cleanName, ignoreCase = true))
            } ?: productSuggestions.singleOrNull()

            if (matchedProduct == null) {
                if (showIncompleteError) {
                    localError = if (invoiceType == InvoiceType.PURCHASE) {
                        "اختر الصنف من المخزون أو انتقل إلى (فاتورة حرة) لإدخال أصناف جديدة"
                    } else {
                        "اختر الصنف من قائمة الاقتراحات بالأسفل"
                    }
                    isProductInputFocused = true
                }
                return false
            }

            val units = UnitCostEngine.getAvailableUnits(matchedProduct)
            val preferredUnit = if (invoiceType == InvoiceType.PURCHASE) {
                units.last()
            } else {
                units.firstOrNull { it.name.equals(matchedProduct.defaultUnitName, ignoreCase = true) }
                    ?: units.first()
            }
            return addProductFromSuggestion(
                prod = matchedProduct,
                unitName = preferredUnit.name,
                factor = preferredUnit.cumulativeFactor,
                targetFocusAfterAdd = targetFocusAfterCommit
            )
        }

        val parsedTotal = NumberFormatUtils.parseDecimal(draftTotalInput)
        if (parsedTotal == null || !parsedTotal.isFinite() || parsedTotal < 0.0) {
            if (showIncompleteError) {
                localError = "يرجى إدخال القيمة الإجمالية للسطر الحالي"
            }
            return false
        }

        val unitPrice = parsedTotal / effectiveQty
        if (!unitPrice.isFinite()) {
            if (showIncompleteError) {
                localError = "قيمة الصنف غير صالحة"
            }
            return false
        }

        if (invoiceType == InvoiceType.PURCHASE) {
            val existingProd = products.firstOrNull {
                it.name.trim().equals(cleanName, ignoreCase = true)
            }
            if (existingProd != null) {
                val preferredUnit = UnitCostEngine.getAvailableUnits(existingProd).last()
                val dupLine = draftLines.any {
                    it.product.id == existingProd.id &&
                        it.unitName.equals(preferredUnit.name, ignoreCase = true)
                }
                if (dupLine) {
                    if (showIncompleteError) {
                        localError = "الصنف (${existingProd.name}) موجود مسبقًا في الفاتورة؛ عدّل الكمية في سطره"
                    }
                    return false
                }
                lineKeyCounter++
                draftLines.add(
                    PendingUiLine(
                        key = "line_$lineKeyCounter",
                        product = existingProd,
                        unitName = preferredUnit.name,
                        cumulativeFactor = preferredUnit.cumulativeFactor,
                        unitQty = effectiveQty,
                        unitPrice = unitPrice,
                        qtyText = NumberFormatUtils.formatInputNumber(effectiveQty, 2),
                        totalText = NumberFormatUtils.formatInputNumber(parsedTotal, 2),
                        explicitTotal = parsedTotal
                    )
                )
            } else {
                if (draftLines.any { it.product.name.trim().equals(cleanName, ignoreCase = true) }) {
                    if (showIncompleteError) {
                        localError = "الصنف ($cleanName) موجود مسبقًا في الفاتورة؛ عدّل الكمية في سطره"
                    }
                    return false
                }
                lineKeyCounter++
                val draftPurchaseProduct = Product(
                    id = "draft_${java.util.UUID.randomUUID().toString().replace("-", "")}",
                    name = cleanName,
                    baseUnitName = "حبة",
                    defaultUnitName = "حبة",
                    unitTiers = emptyList(),
                    baseCost = unitPrice,
                    baseSellPrice = 0.0,
                    stockBaseQty = 0.0
                )
                draftLines.add(
                    PendingUiLine(
                        key = "line_$lineKeyCounter",
                        product = draftPurchaseProduct,
                        unitName = "حبة",
                        cumulativeFactor = 1.0,
                        unitQty = effectiveQty,
                        unitPrice = unitPrice,
                        qtyText = NumberFormatUtils.formatInputNumber(effectiveQty, 2),
                        totalText = NumberFormatUtils.formatInputNumber(parsedTotal, 2),
                        explicitTotal = parsedTotal
                    )
                )
            }
        } else {
            lineKeyCounter++
            val transientProduct = Product(
                id = "free_${java.util.UUID.randomUUID().toString().replace("-", "")}",
                name = cleanName,
                baseUnitName = "حبة",
                defaultUnitName = "حبة",
                baseSellPrice = unitPrice,
                stockBaseQty = 0.0
            )
            draftLines.add(
                PendingUiLine(
                    key = "line_$lineKeyCounter",
                    product = transientProduct,
                    unitName = "حبة",
                    cumulativeFactor = 1.0,
                    unitQty = effectiveQty,
                    unitPrice = unitPrice,
                    qtyText = NumberFormatUtils.formatInputNumber(effectiveQty, 2),
                    totalText = NumberFormatUtils.formatInputNumber(parsedTotal, 2),
                    explicitTotal = parsedTotal
                )
            )
        }

        // Clear active inputs so the next empty row becomes ready immediately.
        draftSearchQuery = ""
        draftQtyInput = ""
        draftTotalInput = ""
        isProductInputFocused = targetFocusAfterCommit == activeNameFocusRequester
        localError = null
        if (targetFocusAfterCommit != null) {
            targetFocusAfterCommit.requestFocus()
        } else {
            focusManager.clearFocus()
        }
        return true
    }

    fun buildPreviewOrLatestInvoice(): Invoice? {
        if (draftLines.isNotEmpty()) {
            val resolvedName = selectedContact?.name
                ?: manualCustomerName.trim().ifEmpty {
                    if (invoiceType == InvoiceType.PURCHASE) "مورد نقدي" else "عميل نقدي"
                }
            return Invoice(
                id = editingInvoice?.id ?: "draft_preview",
                invoiceNumber = displayInvoiceNumber,
                invoiceType = invoiceType.name,
                invoiceMode = invoiceMode.name,
                paymentMethod = paymentMethod.name,
                contactId = selectedContact?.id ?: "",
                contactName = resolvedName,
                totalAmount = netTotalAmount,
                discount = discountAmount,
                paidAmount = effectivePaidAmount,
                createdAt = editingInvoice?.createdAt,
                lines = draftLines.map {
                    InvoiceLine(
                        productId = it.product.id.takeUnless { id -> id.startsWith("draft_") || id.startsWith("free_") } ?: "",
                        productName = it.product.name,
                        description = it.description,
                        unitName = it.unitName,
                        cumulativeFactor = it.cumulativeFactor,
                        unitQty = it.unitQty,
                        unitPrice = it.unitPrice,
                        baseQty = it.baseQty,
                        lineTotal = it.lineTotal
                    )
                },
                notes = notes
            )
        }
        return savedInvoices.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LedgerCanvas)
    ) {
        // Compact Top Navy Header Bar ([AZ] StoreName  <--->  "طباعة الشاشة" gold pill)
        Surface(
            color = LedgerNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(21.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = settings.storeBadgeText.ifBlank { "AZ" },
                                color = LedgerNavy,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = settings.storeName,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    onClick = {
                        if (hasActiveRowInput) {
                            commitActiveRowIfReady(targetFocusAfterCommit = null, showIncompleteError = false)
                        }
                        val target = buildPreviewOrLatestInvoice()
                        if (target != null) {
                            onOpenReceipt(target)
                        } else {
                            localError = "أضف صنفًا واحدًا على الأقل لمعاينة وطباعة الفاتورة"
                        }
                    },
                    color = LedgerGold,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("screen_print_4k_button")
                ) {
                    Text(
                        text = "طباعة الشاشة",
                        color = LedgerNavy,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (showHistory) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "السجل المحفوظ (${savedInvoices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LedgerNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showHistory = false },
                        colors = ButtonDefaults.buttonColors(containerColor = LedgerNavy),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("العودة", maxLines = 1)
                    }
                }
                InvoiceHistoryContent(
                    invoicesState = invoicesState,
                    onEditInvoice = { inv -> loadInvoiceForEdit(inv) },
                    onDeleteInvoice = { inv -> invoiceToDelete = inv },
                    onOpenReceipt = onOpenReceipt
                )
            }
        } else {
            // Centered Paper Invoice Sheet
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, LedgerNavy),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .widthIn(max = 700.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        editingInvoice?.let { editing ->
                            Surface(
                                color = LedgerGold.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, LedgerGold),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("editing_invoice_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "وضع تعديل الفاتورة #${editing.invoiceNumber}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerNavy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { resetDraft() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("cancel_invoice_edit_button")
                                    ) {
                                        Text(
                                            text = "إلغاء التعديل",
                                            color = LedgerCrimson,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        // 1. Compact Paper Invoice Header: Store Name + Phone | Small Circle Badge | Invoice Number & Date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${settings.storeName}${if (settings.storePhone.isNotBlank()) "  ت:${settings.storePhone}" else ""}",
                                fontFamily = AmiriFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LedgerNavy,
                                maxLines = 2,
                                lineHeight = 15.sp,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(LedgerNavy),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = settings.storeBadgeText.ifBlank { "AZ" },
                                    fontFamily = AmiriFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .clickable { showInvoiceNumDialog = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.EditNote,
                                            contentDescription = "ملاحظات الفاتورة",
                                            tint = if (notes.isNotBlank()) LedgerGreen else LedgerNavy.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "الرقم:",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerNavy
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, LedgerNavy)
                                    ) {
                                        BasicTextField(
                                            value = customInvoiceNumber,
                                            onValueChange = { raw ->
                                                customInvoiceNumber = NumberFormatUtils.toEnglishDigits(raw)
                                                    .filter { it.isDigit() }
                                                    .take(12)
                                            },
                                            singleLine = true,
                                            cursorBrush = SolidColor(LedgerCrimson),
                                            textStyle = TextStyle(
                                                fontFamily = AmiriFontFamily,
                                                color = LedgerCrimson,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                textDirection = TextDirection.Ltr
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            modifier = Modifier
                                                .widthIn(min = 52.dp, max = 86.dp)
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                                .testTag("invoice_number_inline_input"),
                                            decorationBox = { innerTextField ->
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (customInvoiceNumber.isEmpty()) {
                                                        Text(
                                                            text = autoInvoiceNumber,
                                                            fontFamily = AmiriFontFamily,
                                                            color = LedgerCrimson.copy(alpha = 0.75f),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }
                                }
                                Text(
                                    text = "التاريخ: $displayDate",
                                    fontFamily = AmiriFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(color = LedgerNavy, thickness = 1.dp)

                        // 2. Customer Row (Inline BasicTextField + Smart Suggestions) + Invoice Type Pill + Cash/Credit Radios
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (invoiceType == InvoiceType.SALE) "المطلوب من/" else "المورد/",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LedgerNavy,
                                    maxLines = 1
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        BasicTextField(
                                            value = manualCustomerName,
                                            onValueChange = { typed ->
                                                manualCustomerName = typed
                                                if (selectedContact != null &&
                                                    !selectedContact!!.name.equals(typed.trim(), ignoreCase = true)
                                                ) {
                                                    selectedContact = null
                                                }
                                            },
                                            singleLine = true,
                                            cursorBrush = SolidColor(LedgerNavy),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color(0xFF0F172A),
                                                fontWeight = FontWeight.Bold
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isCustomerFieldFocused = it.isFocused }
                                                .padding(vertical = 4.dp)
                                                .testTag("customer_inline_input"),
                                            decorationBox = { innerTextField ->
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    if (manualCustomerName.isEmpty()) {
                                                        Text(
                                                            text = if (invoiceType == InvoiceType.PURCHASE) "اسم المورد..." else "اسم العميل...",
                                                            color = Color(0xFF94A3B8),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                        DottedLine(modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { showCustomerDialog = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PersonSearch,
                                            contentDescription = "عرض كل الحسابات",
                                            tint = LedgerNavy,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Clickable Invoice Type Pill (فاتورة بيع / فاتورة شراء جملة)
                                Surface(
                                    onClick = {
                                        val nextType = if (invoiceType == InvoiceType.SALE) {
                                            InvoiceType.PURCHASE
                                        } else {
                                            InvoiceType.SALE
                                        }
                                        invoiceType = nextType
                                        selectedContact = null
                                        // Switch draft lines to wholesale unit on PURCHASE or default unit on SALE
                                        for (i in draftLines.indices) {
                                            val cur = draftLines[i]
                                            val units = UnitCostEngine.getAvailableUnits(cur.product)
                                            val targetUnit = if (nextType == InvoiceType.PURCHASE) {
                                                units.last()
                                            } else {
                                                units.firstOrNull {
                                                    it.name.equals(cur.product.defaultUnitName, ignoreCase = true)
                                                } ?: units.first()
                                            }
                                            val targetPrice = if (nextType == InvoiceType.PURCHASE) {
                                                UnitCostEngine.calculateLargeUnitCostFromBase(cur.product.baseCost, targetUnit.cumulativeFactor)
                                            } else {
                                                UnitCostEngine.calculateUnitSellPrice(cur.product.baseSellPrice, targetUnit.cumulativeFactor)
                                            }
                                            draftLines[i] = cur.copy(
                                                unitName = targetUnit.name,
                                                cumulativeFactor = targetUnit.cumulativeFactor,
                                                unitPrice = targetPrice,
                                                totalText = NumberFormatUtils.formatInputNumber(cur.unitQty * targetPrice, 2),
                                                explicitTotal = null
                                            )
                                        }
                                    },
                                    color = if (invoiceType == InvoiceType.PURCHASE) LedgerGreen else LedgerNavy,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("toggle_invoice_type_pill")
                                ) {
                                    Text(
                                        text = if (invoiceType == InvoiceType.SALE) "فاتورة بيع" else "شراء جملة",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }

                                RadioOption(
                                    label = "نقداً",
                                    selected = paymentMethod == PaymentMethod.CASH,
                                    onClick = {
                                        paymentMethod = PaymentMethod.CASH
                                        isPaidManuallyEdited = false
                                        paidInput = ""
                                    }
                                )
                                RadioOption(
                                    label = "آجل",
                                    selected = paymentMethod == PaymentMethod.CREDIT,
                                    onClick = {
                                        paymentMethod = PaymentMethod.CREDIT
                                        isPaidManuallyEdited = false
                                        paidInput = "0"
                                    }
                                )
                            }

                            // Clear Invoice Mode Selector Row: من المخزن | فاتورة حرة
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val switchMode = { targetMode: InvoiceMode ->
                                    if (invoiceMode != targetMode) {
                                        if (draftLines.isEmpty() && editingInvoice == null) {
                                            invoiceMode = targetMode
                                            localError = null
                                        } else {
                                            localError = "ابدأ فاتورة جديدة قبل تبديل النوع بين مخزنية وحرة"
                                        }
                                    }
                                }

                                Surface(
                                    onClick = { switchMode(InvoiceMode.STOCK) },
                                    color = if (invoiceMode == InvoiceMode.STOCK) LedgerNavy else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (invoiceMode == InvoiceMode.STOCK) LedgerNavy else LedgerNavy.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mode_stock_chip")
                                ) {
                                    Text(
                                        text = "فاتورة من المخزن",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (invoiceMode == InvoiceMode.STOCK) Color.White else LedgerNavy,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp)
                                    )
                                }

                                Surface(
                                    onClick = { switchMode(InvoiceMode.FREE) },
                                    color = if (invoiceMode == InvoiceMode.FREE) LedgerGold else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (invoiceMode == InvoiceMode.FREE) LedgerNavy else LedgerNavy.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mode_free_chip")
                                ) {
                                    Text(
                                        text = "فاتورة حرة (يدوية)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerNavy,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp)
                                    )
                                }
                            }

                            if (invoiceMode == InvoiceMode.FREE) {
                                Surface(
                                    color = LedgerGold.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (invoiceType == InvoiceType.SALE) {
                                                "بيع حر: تُحفظ الأصناف بالفاتورة فقط دون خصم المخزون"
                                            } else {
                                                "شراء حر: تُضاف جميع الأصناف للمخزون دفعة واحدة عند الحفظ"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = LedgerNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = {
                                                if (invoiceType == InvoiceType.SALE) {
                                                    showFreeSaleDialog = true
                                                } else {
                                                    showQuickWholesaleDialog = true
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier
                                                .height(26.dp)
                                                .testTag("free_line_add_button")
                                        ) {
                                            Text(
                                                text = if (invoiceType == InvoiceType.SALE) "+ صنف حر" else "+ شراء حر",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = LedgerGreen
                                            )
                                        }
                                    }
                                }
                            }

                            // Smart Customer / Supplier Suggestions Card
                            AnimatedVisibility(visible = isCustomerFieldFocused && customerSuggestions.isNotEmpty()) {
                                SmartCustomerSuggestionsPanel(
                                    invoiceType = invoiceType,
                                    suggestions = customerSuggestions,
                                    onSelect = { suggestion ->
                                        selectedContact = suggestion.contact
                                        manualCustomerName = suggestion.displayName
                                        isCustomerFieldFocused = false
                                        localError = null
                                        focusManager.clearFocus()
                                    },
                                    onDismiss = {
                                        isCustomerFieldFocused = false
                                        focusManager.clearFocus()
                                    }
                                )
                            }
                        }

                        DottedLine(modifier = Modifier.fillMaxWidth())

                        // 4. Structured 4-Column Ledger Table (RTL: القيمة الإجمالية | الكمية | التفاصيل | سعر الواحدة)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Navy Header
                            Surface(
                                color = LedgerNavy,
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TableHeaderCell("القيمة الإجمالية", fontSizeSp = 12.5f, modifier = Modifier.weight(0.92f))
                                    VerticalDivider(color = Color(0xFF64748B), modifier = Modifier.fillMaxHeight().padding(vertical = 3.dp))
                                    TableHeaderCell("الكمية", fontSizeSp = 12.5f, modifier = Modifier.weight(0.66f))
                                    VerticalDivider(color = Color(0xFF64748B), modifier = Modifier.fillMaxHeight().padding(vertical = 3.dp))
                                    TableHeaderCell("التفاصيــــــل", fontSizeSp = 13f, modifier = Modifier.weight(1.82f))
                                    VerticalDivider(color = Color(0xFF64748B), modifier = Modifier.fillMaxHeight().padding(vertical = 3.dp))
                                    TableHeaderCell(
                                        text = "سعر الواحدة",
                                        textColor = Color(0xFFCBD5E1).copy(alpha = 0.8f),
                                        fontSizeSp = 10f,
                                        modifier = Modifier.weight(0.60f)
                                    )
                                }
                            }

                            // Unified bordered table body so rows never double-stack thick borders
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = LedgerNavy,
                                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                    )
                                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            ) {
                                // Filled Draft Rows (Compact, Zebra-Striped, Inline Editable)
                                draftLines.forEachIndexed { idx, line ->
                                    val availableUnits = remember(line.product) {
                                        UnitCostEngine.getAvailableUnits(line.product)
                                    }
                                    val availableBaseStock = effectiveAvailableStock(line.product)
                                    val totalBaseForProduct = draftLines
                                        .filter { it.product.id == line.product.id }
                                        .sumOf { it.baseQty }
                                    val isFreeLine = invoiceMode == InvoiceMode.FREE ||
                                        line.product.id.startsWith("free_") ||
                                        line.product.id.startsWith("draft_")
                                    val exceedsStock = invoiceType == InvoiceType.SALE &&
                                        !isFreeLine &&
                                        (totalBaseForProduct - availableBaseStock > 0.0001)
                                    val rowBg = if (exceedsStock) {
                                        Color(0xFFFEF2F2)
                                    } else if (idx % 2 == 0) {
                                        Color.White
                                    } else {
                                        Color(0xFFF8FAFC)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .heightIn(min = 34.dp)
                                            .background(rowBg),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Col 1: القيمة الإجمالية (Inline Decimal Keyboard)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(0.92f)
                                                .fillMaxHeight()
                                                .padding(horizontal = 3.dp, vertical = 3.dp)
                                        ) {
                                            BasicTextField(
                                                value = line.totalText,
                                                onValueChange = { raw ->
                                                    val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                                                    val parsedTotal = NumberFormatUtils.parseDecimal(clean)
                                                    val updatedPrice = if (parsedTotal != null && parsedTotal >= 0.0 && line.unitQty > 0.0) {
                                                        parsedTotal / line.unitQty
                                                    } else {
                                                        line.unitPrice
                                                    }
                                                    draftLines[idx] = line.copy(
                                                        totalText = clean,
                                                        unitPrice = updatedPrice,
                                                        explicitTotal = parsedTotal ?: 0.0
                                                    )
                                                    localError = null
                                                },
                                                singleLine = true,
                                                cursorBrush = SolidColor(LedgerNavy),
                                                textStyle = TextStyle(
                                                    fontFamily = AmiriFontFamily,
                                                    color = LedgerNavy,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    textAlign = TextAlign.Center,
                                                    textDirection = TextDirection.Ltr
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Decimal,
                                                    imeAction = ImeAction.Next
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("row_total_input_$idx"),
                                                decorationBox = { innerTextField ->
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (line.totalText.isEmpty()) {
                                                            Text("0", fontFamily = AmiriFontFamily, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            )
                                        }

                                        VerticalDivider(color = LedgerNavy.copy(alpha = 0.7f), modifier = Modifier.fillMaxHeight())

                                        // Col 2: الكمية (Inline Decimal Keyboard)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(0.66f)
                                                .fillMaxHeight()
                                                .padding(horizontal = 3.dp, vertical = 3.dp)
                                        ) {
                                            BasicTextField(
                                                value = line.qtyText,
                                                onValueChange = { raw ->
                                                    val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                                                    val parsedQty = NumberFormatUtils.parseDecimal(clean) ?: 0.0
                                                    val newTotal = if (parsedQty > 0.0) parsedQty * line.unitPrice else 0.0
                                                    draftLines[idx] = line.copy(
                                                        qtyText = clean,
                                                        unitQty = parsedQty,
                                                        totalText = if (clean.isEmpty()) "" else NumberFormatUtils.formatInputNumber(newTotal, 2),
                                                        explicitTotal = null
                                                    )
                                                    localError = null
                                                },
                                                singleLine = true,
                                                cursorBrush = SolidColor(LedgerNavy),
                                                textStyle = TextStyle(
                                                    fontFamily = AmiriFontFamily,
                                                    color = if (exceedsStock) LedgerCrimson else Color(0xFF0F172A),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    textAlign = TextAlign.Center,
                                                    textDirection = TextDirection.Ltr
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Decimal,
                                                    imeAction = ImeAction.Next
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("row_qty_input_$idx"),
                                                decorationBox = { innerTextField ->
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (line.qtyText.isEmpty()) {
                                                            Text("0", fontFamily = AmiriFontFamily, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        }
                                                        innerTextField()
                                                    }
                                                }
                                            )
                                        }

                                        VerticalDivider(color = LedgerNavy.copy(alpha = 0.7f), modifier = Modifier.fillMaxHeight())

                                        // Col 3: التفاصيل (Compact Single-Line: Index + Name + Unit Pill + Delete)
                                        Column(
                                            modifier = Modifier
                                                .weight(1.82f)
                                                .padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "${idx + 1}. ${line.product.name}",
                                                    fontFamily = AmiriFontFamily,
                                                    fontSize = 13.sp,
                                                    lineHeight = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { editingLineIndex = idx }
                                                )

                                                Box {
                                                    Surface(
                                                        onClick = { unitPickerLineKey = line.key },
                                                        color = LedgerNavy.copy(alpha = 0.08f),
                                                        shape = RoundedCornerShape(5.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        ) {
                                                            Text(
                                                                text = line.unitName,
                                                                fontFamily = AmiriFontFamily,
                                                                fontSize = 10.5.sp,
                                                                color = LedgerNavy,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1
                                                            )
                                                            if (availableUnits.size > 1) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                                                    contentDescription = "تبديل الوحدة",
                                                                    tint = LedgerNavy,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    DropdownMenu(
                                                        expanded = unitPickerLineKey == line.key,
                                                        onDismissRequest = { unitPickerLineKey = null }
                                                    ) {
                                                        availableUnits.forEach { level ->
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Text(
                                                                        "${level.name} (${NumberFormatUtils.formatNumber(level.cumulativeFactor)})",
                                                                        fontFamily = AmiriFontFamily
                                                                    )
                                                                },
                                                                onClick = {
                                                                    unitPickerLineKey = null
                                                                    val dupUnit = draftLines.indices.any { otherIdx ->
                                                                        otherIdx != idx &&
                                                                            draftLines[otherIdx].product.id == line.product.id &&
                                                                            draftLines[otherIdx].unitName.equals(level.name, ignoreCase = true)
                                                                    }
                                                                    if (dupUnit) {
                                                                        localError = "توجد نفس الوحدة (${level.name}) في سطر آخر، لا يُسمح بالتكرار"
                                                                        return@DropdownMenuItem
                                                                    }
                                                                    val newUnitPrice = if (invoiceType == InvoiceType.PURCHASE) {
                                                                        UnitCostEngine.calculateLargeUnitCostFromBase(line.product.baseCost, level.cumulativeFactor)
                                                                    } else {
                                                                        UnitCostEngine.calculateUnitSellPrice(line.product.baseSellPrice, level.cumulativeFactor)
                                                                    }
                                                                    val newTotal = line.unitQty * newUnitPrice
                                                                    draftLines[idx] = line.copy(
                                                                        unitName = level.name,
                                                                        cumulativeFactor = level.cumulativeFactor,
                                                                        unitPrice = newUnitPrice,
                                                                        totalText = NumberFormatUtils.formatInputNumber(newTotal, 2),
                                                                        explicitTotal = null
                                                                    )
                                                                    localError = null
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .clickable { draftLines.removeAt(idx) }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = "حذف الصنف",
                                                        tint = LedgerCrimson.copy(alpha = 0.75f),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }

                                            if (line.description.isNotBlank()) {
                                                Text(
                                                    text = line.description,
                                                    fontFamily = AmiriFontFamily,
                                                    color = Color(0xFF64748B),
                                                    fontSize = 10.sp,
                                                    lineHeight = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (exceedsStock) {
                                                Text(
                                                    text = "المتاح: ${NumberFormatUtils.formatNumber(availableBaseStock)} ${line.product.baseUnitName}",
                                                    fontFamily = AmiriFontFamily,
                                                    color = LedgerCrimson,
                                                    fontSize = 9.5.sp,
                                                    lineHeight = 12.sp
                                                )
                                            }
                                        }

                                        VerticalDivider(color = LedgerNavy.copy(alpha = 0.35f), modifier = Modifier.fillMaxHeight())

                                        // Col 4: سعر الواحدة المستنتج (مُصغر وباهت يتحدث فورياً)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(0.60f)
                                                .padding(horizontal = 2.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = NumberFormatUtils.formatNumber(line.inferredUnitPrice),
                                                fontFamily = AmiriFontFamily,
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    HorizontalDivider(thickness = 0.8.dp, color = LedgerNavy.copy(alpha = 0.65f))
                                }

                                // Active Input Row (Direct Inline Typing in Total | Qty | Details + Live Inferred Unit Price)
                                val activeQtyParsed = NumberFormatUtils.parseDecimal(draftQtyInput)?.takeIf { it > 0.0 }
                                val activeTotalParsed = NumberFormatUtils.parseDecimal(draftTotalInput)?.takeIf { it >= 0.0 }
                                val effectiveActiveQtyForUnit = activeQtyParsed ?: if (activeTotalParsed != null) 1.0 else null
                                val activeInferredText = if (activeTotalParsed != null && effectiveActiveQtyForUnit != null && effectiveActiveQtyForUnit > 0.0) {
                                    NumberFormatUtils.formatNumber(activeTotalParsed / effectiveActiveQtyForUnit)
                                } else {
                                    "-"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .heightIn(min = 34.dp)
                                        .background(Color(0xFFFAFBFD)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Col 1: القيمة الإجمالية (Direct Decimal Input)
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(0.92f)
                                            .fillMaxHeight()
                                            .padding(horizontal = 3.dp, vertical = 4.dp)
                                    ) {
                                        BasicTextField(
                                            value = draftTotalInput,
                                            onValueChange = { raw ->
                                                draftTotalInput = NumberFormatUtils.sanitizeDecimalInput(raw)
                                                localError = null
                                            },
                                            singleLine = true,
                                            cursorBrush = SolidColor(LedgerNavy),
                                            textStyle = TextStyle(
                                                fontFamily = AmiriFontFamily,
                                                color = LedgerNavy,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                textAlign = TextAlign.Center,
                                                textDirection = TextDirection.Ltr
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Next
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onNext = { activeQtyFocusRequester.requestFocus() }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(activeTotalFocusRequester)
                                                .testTag("draft_total_inline_input"),
                                            decorationBox = { innerTextField ->
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (draftTotalInput.isEmpty()) {
                                                        Text("0", fontFamily = AmiriFontFamily, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }

                                    VerticalDivider(color = LedgerNavy.copy(alpha = 0.7f), modifier = Modifier.fillMaxHeight())

                                    // Col 2: الكمية (Direct Decimal Input)
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(0.66f)
                                            .fillMaxHeight()
                                            .padding(horizontal = 3.dp, vertical = 4.dp)
                                    ) {
                                        BasicTextField(
                                            value = draftQtyInput,
                                            onValueChange = { raw ->
                                                draftQtyInput = NumberFormatUtils.sanitizeDecimalInput(raw)
                                                localError = null
                                            },
                                            singleLine = true,
                                            cursorBrush = SolidColor(LedgerNavy),
                                            textStyle = TextStyle(
                                                fontFamily = AmiriFontFamily,
                                                color = Color(0xFF0F172A),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                textAlign = TextAlign.Center,
                                                textDirection = TextDirection.Ltr
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Next
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onNext = { activeNameFocusRequester.requestFocus() }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(activeQtyFocusRequester)
                                                .testTag("draft_qty_inline_input"),
                                            decorationBox = { innerTextField ->
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (draftQtyInput.isEmpty()) {
                                                        Text("-", fontFamily = AmiriFontFamily, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }

                                    VerticalDivider(color = LedgerNavy.copy(alpha = 0.7f), modifier = Modifier.fillMaxHeight())

                                    // Col 3: التفاصيل (Direct Product Search / Manual Entry + Commit/Picker Icons)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1.82f)
                                            .fillMaxHeight()
                                            .padding(horizontal = 5.dp, vertical = 3.dp)
                                    ) {
                                        BasicTextField(
                                            value = draftSearchQuery,
                                            onValueChange = {
                                                draftSearchQuery = it
                                                isProductInputFocused = true
                                                localError = null
                                            },
                                            singleLine = true,
                                            cursorBrush = SolidColor(LedgerNavy),
                                            textStyle = TextStyle(
                                                fontFamily = AmiriFontFamily,
                                                color = Color(0xFF0F172A),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Search
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    commitActiveRowIfReady(
                                                        targetFocusAfterCommit = activeTotalFocusRequester,
                                                        showIncompleteError = true
                                                    )
                                                },
                                                onDone = {
                                                    commitActiveRowIfReady(
                                                        targetFocusAfterCommit = activeTotalFocusRequester,
                                                        showIncompleteError = true
                                                    )
                                                }
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(activeNameFocusRequester)
                                                .onFocusChanged { state ->
                                                    if (state.isFocused) {
                                                        isProductInputFocused = true
                                                    }
                                                }
                                                .testTag("invoice_product_selector"),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    contentAlignment = Alignment.CenterStart,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    if (draftSearchQuery.isEmpty()) {
                                                        val rowNum = draftLines.size + 1
                                                        Text(
                                                            text = when {
                                                                invoiceType == InvoiceType.PURCHASE -> "$rowNum. اسم الصنف للشراء..."
                                                                invoiceMode == InvoiceMode.FREE -> "$rowNum. اكتب الصنف الحر..."
                                                                products.isEmpty() -> "$rowNum. أضف أصناف أولاً..."
                                                                else -> "$rowNum. اكتب اسم الصنف..."
                                                            },
                                                            fontFamily = AmiriFontFamily,
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 12.5.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                        if (hasActiveRowInput) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        commitActiveRowIfReady(
                                                            targetFocusAfterCommit = activeNameFocusRequester,
                                                            showIncompleteError = true
                                                        )
                                                    }
                                                    .testTag("commit_active_row_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AddCircleOutline,
                                                    contentDescription = "إضافة السطر وفتح صف جديد",
                                                    tint = LedgerGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    if (invoiceMode == InvoiceMode.FREE && invoiceType == InvoiceType.SALE) {
                                                        if (!commitActiveRowIfReady(
                                                                targetFocusAfterCommit = activeNameFocusRequester,
                                                                showIncompleteError = false
                                                            )
                                                        ) {
                                                            showFreeSaleDialog = true
                                                        }
                                                    } else if (invoiceMode == InvoiceMode.FREE) {
                                                        showQuickWholesaleDialog = true
                                                    } else {
                                                        showAddLineDialog = true
                                                    }
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = "فتح نافذة اختيار الصنف",
                                                tint = LedgerNavy.copy(alpha = 0.75f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    VerticalDivider(color = LedgerNavy.copy(alpha = 0.35f), modifier = Modifier.fillMaxHeight())

                                    // Col 4: سعر الواحدة المستنتج (بالمقاس المصغر الباهت)
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(0.60f)
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = activeInferredText,
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 11.sp,
                                            color = if (activeInferredText == "-") Color(0xFFCBD5E1) else Color(0xFF94A3B8),
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Auto-Created Next Empty Row whenever the active row starts being filled (for both SALE and PURCHASE)
                                AnimatedVisibility(visible = hasActiveRowInput) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(thickness = 0.8.dp, color = LedgerNavy.copy(alpha = 0.45f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .heightIn(min = 34.dp)
                                                .background(Color(0xFFF1F5F9).copy(alpha = 0.7f))
                                                .testTag("auto_next_row"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(0.92f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        commitActiveRowIfReady(
                                                            targetFocusAfterCommit = activeTotalFocusRequester,
                                                            showIncompleteError = true
                                                        )
                                                    }
                                                    .padding(horizontal = 3.dp, vertical = 4.dp)
                                                    .testTag("auto_next_total_cell")
                                            ) {
                                                Text(
                                                    text = "0",
                                                    fontFamily = AmiriFontFamily,
                                                    color = Color(0xFFCBD5E1),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            VerticalDivider(color = LedgerNavy.copy(alpha = 0.35f), modifier = Modifier.fillMaxHeight())
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(0.66f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        commitActiveRowIfReady(
                                                            targetFocusAfterCommit = activeQtyFocusRequester,
                                                            showIncompleteError = true
                                                        )
                                                    }
                                                    .padding(horizontal = 3.dp, vertical = 4.dp)
                                                    .testTag("auto_next_qty_cell")
                                            ) {
                                                Text(
                                                    text = "-",
                                                    fontFamily = AmiriFontFamily,
                                                    color = Color(0xFFCBD5E1),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            VerticalDivider(color = LedgerNavy.copy(alpha = 0.35f), modifier = Modifier.fillMaxHeight())
                                            Box(
                                                contentAlignment = Alignment.CenterStart,
                                                modifier = Modifier
                                                    .weight(1.82f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        commitActiveRowIfReady(
                                                            targetFocusAfterCommit = activeNameFocusRequester,
                                                            showIncompleteError = true
                                                        )
                                                    }
                                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                                                    .testTag("auto_next_name_cell")
                                            ) {
                                                Text(
                                                    text = "${draftLines.size + 2}. صف جديد تلقائي (اضغط للإضافة والانتقال...)",
                                                    fontFamily = AmiriFontFamily,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            VerticalDivider(color = LedgerNavy.copy(alpha = 0.25f), modifier = Modifier.fillMaxHeight())
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(0.60f)
                                                    .padding(vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = "-",
                                                    fontFamily = AmiriFontFamily,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFCBD5E1)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Smart Product Autocomplete Suggestions directly below active row (never steals focus)
                            AnimatedVisibility(
                                visible = (isProductInputFocused || draftSearchQuery.isNotBlank()) &&
                                    ((invoiceMode == InvoiceMode.STOCK && products.isNotEmpty()) ||
                                        invoiceMode == InvoiceMode.FREE)
                            ) {
                                SmartProductSuggestionsPanel(
                                    invoiceType = invoiceType,
                                    query = draftSearchQuery,
                                    suggestions = productSuggestions,
                                    canQuickCreate = invoiceMode == InvoiceMode.FREE,
                                    onPickUnit = { prod, unitName, factor ->
                                        addProductFromSuggestion(prod, unitName, factor)
                                    },
                                    onQuickAddNewProduct = {
                                        isProductInputFocused = false
                                        if (invoiceType == InvoiceType.SALE) {
                                            showFreeSaleDialog = true
                                        } else {
                                            showQuickWholesaleDialog = true
                                        }
                                    },
                                    onOpenFullPicker = {
                                        isProductInputFocused = false
                                        if (invoiceMode == InvoiceMode.FREE) {
                                            if (invoiceType == InvoiceType.SALE) {
                                                showFreeSaleDialog = true
                                            } else {
                                                showQuickWholesaleDialog = true
                                            }
                                        } else {
                                            showAddLineDialog = true
                                        }
                                    },
                                    onDismiss = {
                                        isProductInputFocused = false
                                        focusManager.clearFocus()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            // 5. Totals Double Box (الإجمالي / الصافي | عدد الأصناف)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .border(1.1.dp, LedgerNavy, RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (discountAmount > 0.0) "الصافي:" else "الإجمالي الكلي:",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LedgerNavy
                                        )
                                        if (discountAmount > 0.0) {
                                            Text(
                                                text = "قبل الخصم: ${NumberFormatUtils.formatNumber(subtotalAmount)}",
                                                fontFamily = AmiriFontFamily,
                                                fontSize = 9.5.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                    Text(
                                        text = NumberFormatUtils.formatNumber(netTotalAmount),
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LedgerCrimson
                                    )
                                }
                                VerticalDivider(color = LedgerNavy, modifier = Modifier.fillMaxHeight())
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "عدد الأصناف:",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerNavy
                                    )
                                    Text(
                                        text = "${draftLines.size}",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Compact 3-cell Discount | Paid | Remaining Accounting Strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .border(0.9.dp, LedgerNavy.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Discount
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الخصم:",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerNavy
                                    )
                                    BasicTextField(
                                        value = discountInput,
                                        onValueChange = { raw ->
                                            discountInput = NumberFormatUtils.sanitizeDecimalInput(raw)
                                            localError = null
                                        },
                                        singleLine = true,
                                        cursorBrush = SolidColor(LedgerNavy),
                                        textStyle = TextStyle(
                                            fontFamily = AmiriFontFamily,
                                            color = LedgerNavy,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.End,
                                            textDirection = TextDirection.Ltr
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Next
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("invoice_discount_input"),
                                        decorationBox = { inner ->
                                            Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                                                if (discountInput.isEmpty()) {
                                                    Text("0", fontFamily = AmiriFontFamily, fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                }

                                VerticalDivider(color = LedgerNavy.copy(alpha = 0.4f), modifier = Modifier.fillMaxHeight())

                                // Paid Amount
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المدفوع:",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerGreen
                                    )
                                    BasicTextField(
                                        value = paidInput,
                                        onValueChange = { raw ->
                                            val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                                            isPaidManuallyEdited = clean.isNotEmpty()
                                            paidInput = clean
                                            localError = null
                                        },
                                        singleLine = true,
                                        cursorBrush = SolidColor(LedgerGreen),
                                        textStyle = TextStyle(
                                            fontFamily = AmiriFontFamily,
                                            color = LedgerGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.End,
                                            textDirection = TextDirection.Ltr
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Done
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("invoice_paid_input"),
                                        decorationBox = { inner ->
                                            Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                                                if (paidInput.isEmpty()) {
                                                    val autoHint = NumberFormatUtils.formatInputNumber(
                                                        if (paymentMethod == PaymentMethod.CASH) netTotalAmount else 0.0,
                                                        2
                                                    )
                                                    Text(autoHint, fontFamily = AmiriFontFamily, fontSize = 11.5.sp, color = LedgerGreen.copy(alpha = 0.65f))
                                                }
                                                inner()
                                            }
                                        }
                                    )
                                }

                                VerticalDivider(color = LedgerNavy.copy(alpha = 0.4f), modifier = Modifier.fillMaxHeight())

                                // Remaining
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المتبقي:",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (remainingAmount > 0.0001) LedgerCrimson else Color(0xFF64748B)
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatNumber(remainingAmount),
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (remainingAmount > 0.0001) LedgerCrimson else Color(0xFF0F172A),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (localError != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = localError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        DottedLine(modifier = Modifier.fillMaxWidth())

                        // 6. Footer Policy & Signatures
                        Text(
                            text = settings.footerPolicy,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "توقيع البائع: ..................",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = LedgerNavy
                            )
                            Text(
                                text = "توقيع المشتري: ..................",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = LedgerNavy
                            )
                        }
                    }
                }
            }

            // Bottom 4 Tactile Action Buttons Bar: جديد | حفظ/تحديث | السجل | طباعة
            Surface(
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { resetDraft() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LedgerNavy),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("new_invoice_button")
                    ) {
                        Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(15.dp), tint = LedgerNavy)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("جديد", color = LedgerNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        enabled = !isBusy,
                        onClick = {
                            if (hasActiveRowInput) {
                                if (!commitActiveRowIfReady(
                                        targetFocusAfterCommit = null,
                                        showIncompleteError = true
                                    )
                                ) {
                                    return@Button
                                }
                            }
                            if (draftLines.isEmpty()) {
                                localError = "أدخل اسم الصنف والقيمة في السطر الحالي أو أضف صنفاً واحداً على الأقل"
                                return@Button
                            }
                            if (draftLines.any { it.unitQty <= 0.0 }) {
                                localError = "يرجى إدخال كمية أكبر من صفر لجميع الأصناف"
                                return@Button
                            }
                            if (invoiceType == InvoiceType.SALE) {
                                val overStockLine = draftLines.firstOrNull { line ->
                                    if (line.product.id.startsWith("draft_") || line.product.id.startsWith("free_")) return@firstOrNull false
                                    val available = effectiveAvailableStock(line.product)
                                    val required = draftLines
                                        .filter { it.product.id == line.product.id }
                                        .sumOf { it.baseQty }
                                    required - available > 0.0001
                                }
                                if (overStockLine != null) {
                                    val available = effectiveAvailableStock(overStockLine.product)
                                    localError = "الكمية المطلوبة من (${overStockLine.product.name}) تتجاوز المتاح (${NumberFormatUtils.formatNumber(available)} ${overStockLine.product.baseUnitName})"
                                    return@Button
                                }
                            }
                            if (discountAmount - subtotalAmount > 0.0001) {
                                localError = "الخصم لا يمكن أن يتجاوز إجمالي الأصناف"
                                return@Button
                            }
                            if (effectivePaidAmount - netTotalAmount > 0.0001) {
                                localError = "المبلغ المدفوع لا يمكن أن يتجاوز صافي الفاتورة"
                                return@Button
                            }
                            if ((paymentMethod == PaymentMethod.CREDIT || remainingAmount > 0.0001) && selectedContact == null) {
                                localError = "يرجى اختيار العميل أو المورد للمبلغ المتبقي (الآجل)"
                                return@Button
                            }
                            localError = null
                            val payloadLines = draftLines.map {
                                InvoiceDraftLine(
                                    productId = it.product.id.takeUnless { id -> id.startsWith("draft_") || id.startsWith("free_") } ?: "",
                                    unitName = it.unitName,
                                    unitQty = it.unitQty,
                                    unitPrice = it.unitPrice,
                                    draftProduct = it.product.takeIf { product -> product.id.startsWith("draft_") || product.id.startsWith("free_") },
                                    invoiceMode = invoiceMode,
                                    productName = it.product.name,
                                    description = it.description
                                )
                            }
                            val resolvedContactId = selectedContact?.id ?: ""
                            val resolvedContactName = selectedContact?.name ?: manualCustomerName.trim()
                            val currentEdit = editingInvoice
                            if (currentEdit != null) {
                                onUpdateInvoice(
                                    currentEdit.id,
                                    displayInvoiceNumber,
                                    invoiceType,
                                    paymentMethod,
                                    resolvedContactId,
                                    resolvedContactName,
                                    payloadLines,
                                    notes,
                                    discountAmount,
                                    effectivePaidAmount
                                ) {
                                    resetDraft()
                                }
                            } else {
                                onPostInvoice(
                                    displayInvoiceNumber,
                                    invoiceType,
                                    paymentMethod,
                                    resolvedContactId,
                                    resolvedContactName,
                                    payloadLines,
                                    notes,
                                    discountAmount,
                                    effectivePaidAmount
                                ) {
                                    resetDraft()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LedgerGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("post_invoice_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (editingInvoice != null) "تحديث" else "حفظ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = { showHistory = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LedgerGold,
                            contentColor = LedgerNavy
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("tab_invoice_history")
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("السجل", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            if (hasActiveRowInput) {
                                commitActiveRowIfReady(targetFocusAfterCommit = null, showIncompleteError = false)
                            }
                            val target = buildPreviewOrLatestInvoice()
                            if (target != null) {
                                onOpenReceipt(target)
                            } else {
                                localError = "أضف صنفًا أو اختر فاتورة من السجل للطباعة"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LedgerNavy),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("print_invoice_button")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("طباعة", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // Dialog: Pick Customer / Supplier or type manual name
    if (showCustomerDialog) {
        val relevantType = if (invoiceType == InvoiceType.PURCHASE) ContactType.SUPPLIER else ContactType.CUSTOMER
        val filteredContacts = contacts.filter { it.resolvedType == relevantType }
        var tempManual by remember(manualCustomerName) { mutableStateOf(manualCustomerName) }

        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = { Text(if (invoiceType == InvoiceType.PURCHASE) "اختيار المورد" else "اختيار العميل") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tempManual,
                        onValueChange = { tempManual = it },
                        label = { Text("اسم نقدي مباشر (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (filteredContacts.isNotEmpty()) {
                        HorizontalDivider()
                        Text("أو اختر من الحسابات المسجلة:", style = MaterialTheme.typography.labelLarge)
                        filteredContacts.forEach { contact ->
                            Surface(
                                onClick = {
                                    selectedContact = contact
                                    manualCustomerName = contact.name
                                    showCustomerDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedContact?.id == contact.id) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(contact.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${NumberFormatUtils.formatNumber(contact.balance)} ر",
                                        color = LedgerNavy
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempManual.trim().isNotEmpty() && selectedContact?.name != tempManual.trim()) {
                            selectedContact = null
                            manualCustomerName = tempManual.trim()
                        }
                        showCustomerDialog = false
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomerDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Dialog: Edit Invoice Number & Notes
    if (showInvoiceNumDialog) {
        var tempNum by remember(customInvoiceNumber) { mutableStateOf(displayInvoiceNumber) }
        var tempNotes by remember(notes) { mutableStateOf(notes) }

        AlertDialog(
            onDismissRequest = { showInvoiceNumDialog = false },
            title = { Text("رقم الفاتورة والملاحظات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tempNum,
                        onValueChange = { tempNum = NumberFormatUtils.toEnglishDigits(it).filter { ch -> ch.isDigit() } },
                        label = { Text("رقم الفاتورة") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempNotes,
                        onValueChange = { tempNotes = it },
                        label = { Text("ملاحظات إضافية (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        customInvoiceNumber = tempNum.trim()
                        notes = tempNotes.trim()
                        showInvoiceNumDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInvoiceNumDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Dialog: Add Item Line to Table (Stock-Backed)
    if (showAddLineDialog) {
        InvoiceLinePickerDialog(
            invoiceType = invoiceType,
            products = products,
            existingLine = null,
            availableStockForProduct = { effectiveAvailableStock(it) },
            onDismiss = { showAddLineDialog = false },
            onDelete = null,
            onConfirm = { prod, unitName, factor, qty, price ->
                val dupLine = draftLines.any {
                    it.product.id == prod.id && it.unitName.equals(unitName, ignoreCase = true)
                }
                if (dupLine) {
                    return@InvoiceLinePickerDialog "تمت إضافة (${prod.name}) بوحدة ($unitName) مسبقًا، لا يُسمح بالتكرار"
                }
                lineKeyCounter++
                draftLines.add(
                    PendingUiLine(
                        key = "line_$lineKeyCounter",
                        product = prod,
                        unitName = unitName,
                        cumulativeFactor = factor,
                        unitQty = qty,
                        unitPrice = price
                    )
                )
                localError = null
                showAddLineDialog = false
                null
            }
        )
    }

    // Dialog: Add Manual Free Sale Line (with description, unit, qty, price)
    if (showFreeSaleDialog) {
        val preTotal = NumberFormatUtils.parseDecimal(draftTotalInput)?.takeIf { it > 0.0 }
        val preQty = NumberFormatUtils.parseDecimal(draftQtyInput)?.takeIf { it > 0.0 } ?: 1.0
        val preUnitPrice = if (preTotal != null) preTotal / preQty else 0.0
        FreeSaleLineDialog(
            initialName = draftSearchQuery.trim(),
            initialDescription = "",
            initialUnitName = "حبة",
            initialQty = preQty,
            initialUnitPrice = preUnitPrice,
            isEditing = false,
            onDismiss = { showFreeSaleDialog = false },
            onDelete = null,
            onConfirm = { cleanName, cleanDesc, cleanUnit, qty, unitPrice ->
                lineKeyCounter++
                val transientProduct = Product(
                    id = "free_${java.util.UUID.randomUUID().toString().replace("-", "")}",
                    name = cleanName,
                    baseUnitName = cleanUnit,
                    defaultUnitName = cleanUnit,
                    baseSellPrice = unitPrice,
                    stockBaseQty = 0.0
                )
                val lineTotal = qty * unitPrice
                draftLines.add(
                    PendingUiLine(
                        key = "line_$lineKeyCounter",
                        product = transientProduct,
                        unitName = cleanUnit,
                        cumulativeFactor = 1.0,
                        unitQty = qty,
                        unitPrice = unitPrice,
                        description = cleanDesc,
                        qtyText = NumberFormatUtils.formatInputNumber(qty, 2),
                        totalText = NumberFormatUtils.formatInputNumber(lineTotal, 2),
                        explicitTotal = lineTotal
                    )
                )
                draftSearchQuery = ""
                draftQtyInput = ""
                draftTotalInput = ""
                localError = null
                showFreeSaleDialog = false
                focusManager.clearFocus()
            }
        )
    }

    // Dialog: Edit or Delete Existing Table Line
    val editIdx = editingLineIndex
    if (editIdx != null && editIdx in draftLines.indices) {
        val current = draftLines[editIdx]
        val isFreeSaleEdit = invoiceMode == InvoiceMode.FREE && invoiceType == InvoiceType.SALE
        val isFreePurchaseDraftEdit = invoiceMode == InvoiceMode.FREE &&
            invoiceType == InvoiceType.PURCHASE &&
            (current.product.id.startsWith("draft_") || current.product.id.startsWith("free_")) &&
            onQuickCreateProduct != null

        if (isFreeSaleEdit) {
            FreeSaleLineDialog(
                initialName = current.product.name,
                initialDescription = current.description,
                initialUnitName = current.unitName,
                initialQty = current.unitQty,
                initialUnitPrice = current.inferredUnitPrice,
                isEditing = true,
                onDismiss = { editingLineIndex = null },
                onDelete = {
                    draftLines.removeAt(editIdx)
                    editingLineIndex = null
                },
                onConfirm = { cleanName, cleanDesc, cleanUnit, qty, unitPrice ->
                    val updatedProd = current.product.copy(
                        name = cleanName,
                        baseUnitName = cleanUnit,
                        defaultUnitName = cleanUnit,
                        baseSellPrice = unitPrice
                    )
                    val lineTotal = qty * unitPrice
                    draftLines[editIdx] = current.copy(
                        product = updatedProd,
                        unitName = cleanUnit,
                        cumulativeFactor = 1.0,
                        unitQty = qty,
                        unitPrice = unitPrice,
                        description = cleanDesc,
                        qtyText = NumberFormatUtils.formatInputNumber(qty, 2),
                        totalText = NumberFormatUtils.formatInputNumber(lineTotal, 2),
                        explicitTotal = lineTotal
                    )
                    localError = null
                    editingLineIndex = null
                }
            )
        } else if (isFreePurchaseDraftEdit) {
            QuickWholesaleProductDialog(
                invoiceType = invoiceType,
                initialName = current.product.name,
                initialDescription = current.description,
                initialWholesaleUnit = current.unitName,
                initialBaseUnit = current.product.baseUnitName,
                initialFactorText = NumberFormatUtils.formatInputNumber(current.cumulativeFactor, 2),
                initialQtyText = NumberFormatUtils.formatInputNumber(current.unitQty, 2),
                initialWholesaleCostText = NumberFormatUtils.formatInputNumber(current.inferredUnitPrice, 2),
                initialBaseSellText = if (current.product.baseSellPrice > 0.0) {
                    NumberFormatUtils.formatInputNumber(current.product.baseSellPrice, 2)
                } else {
                    ""
                },
                initialTotalText = "",
                isEditing = true,
                onDismiss = { editingLineIndex = null },
                onDelete = {
                    draftLines.removeAt(editIdx)
                    editingLineIndex = null
                },
                onConfirmCreate = { name, barcode, baseUnit, wholesaleUnit, factor, purchaseQty, wholesaleUnitCost, baseSellPrice, description ->
                    if (draftLines.indices.any { other ->
                        other != editIdx && draftLines[other].product.name.trim().equals(name.trim(), ignoreCase = true)
                    }) {
                        localError = "الصنف موجود مسبقًا في سطر آخر من الفاتورة"
                        editingLineIndex = null
                    } else scope.launch {
                        val updatedResult = onQuickCreateProduct!!(
                            name,
                            barcode,
                            baseUnit,
                            wholesaleUnit,
                            factor,
                            wholesaleUnitCost,
                            baseSellPrice
                        )
                        updatedResult.onSuccess { updatedProd ->
                            val unitLevel = UnitCostEngine.getAvailableUnits(updatedProd).last()
                            val total = purchaseQty * wholesaleUnitCost
                            draftLines[editIdx] = current.copy(
                                product = updatedProd,
                                unitName = unitLevel.name,
                                cumulativeFactor = unitLevel.cumulativeFactor,
                                unitQty = purchaseQty,
                                unitPrice = wholesaleUnitCost,
                                description = description,
                                qtyText = NumberFormatUtils.formatInputNumber(purchaseQty, 2),
                                totalText = NumberFormatUtils.formatInputNumber(total, 2),
                                explicitTotal = total
                            )
                            localError = null
                            editingLineIndex = null
                        }.onFailure { err ->
                            localError = err.localizedMessage ?: "تعذر تعديل الصنف"
                        }
                    }
                }
            )
        } else {
            InvoiceLinePickerDialog(
                invoiceType = invoiceType,
                products = products,
                existingLine = current,
                availableStockForProduct = { effectiveAvailableStock(it) },
                onDismiss = { editingLineIndex = null },
                onDelete = {
                    draftLines.removeAt(editIdx)
                    editingLineIndex = null
                },
                onConfirm = { prod, unitName, factor, qty, price ->
                    val dupLine = draftLines.indices.any { idx ->
                        idx != editIdx &&
                            draftLines[idx].product.id == prod.id &&
                            draftLines[idx].unitName.equals(unitName, ignoreCase = true)
                    }
                    if (dupLine) {
                        return@InvoiceLinePickerDialog "تمت إضافة (${prod.name}) بوحدة ($unitName) في سطر آخر، لا يُسمح بالتكرار"
                    }
                    draftLines[editIdx] = current.copy(
                        product = prod,
                        unitName = unitName,
                        cumulativeFactor = factor,
                        unitQty = qty,
                        unitPrice = price,
                        qtyText = NumberFormatUtils.formatInputNumber(qty, 2),
                        totalText = NumberFormatUtils.formatInputNumber(qty * price, 2),
                        explicitTotal = null
                    )
                    editingLineIndex = null
                    null
                }
            )
        }
    }

    // Dialog: Quick-create a brand new wholesale product into draft Purchase Invoice (added to stock only on invoice commit)
    if (showQuickWholesaleDialog && onQuickCreateProduct != null) {
        QuickWholesaleProductDialog(
            invoiceType = invoiceType,
            initialName = draftSearchQuery.trim(),
            initialQtyText = draftQtyInput.ifBlank { "1" },
            initialTotalText = draftTotalInput,
            onDismiss = { showQuickWholesaleDialog = false },
            onConfirmCreate = { name, barcode, baseUnit, wholesaleUnit, factor, purchaseQty, wholesaleUnitCost, baseSellPrice, description ->
                if (invoiceType == InvoiceType.PURCHASE && draftLines.any {
                    it.product.name.trim().equals(name.trim(), ignoreCase = true)
                }) {
                    localError = "الصنف موجود مسبقًا في هذه الفاتورة؛ أضف الكمية إلى السطر الموجود"
                    showQuickWholesaleDialog = false
                } else scope.launch {
                    val created = onQuickCreateProduct(
                        name,
                        barcode,
                        baseUnit,
                        wholesaleUnit,
                        factor,
                        wholesaleUnitCost,
                        baseSellPrice
                    )
                    created.onSuccess { newProd ->
                        val unitLevel = UnitCostEngine.getAvailableUnits(newProd).last()
                        lineKeyCounter++
                        val total = purchaseQty * wholesaleUnitCost
                        draftLines.add(
                            PendingUiLine(
                                key = "line_$lineKeyCounter",
                                product = newProd,
                                unitName = unitLevel.name,
                                cumulativeFactor = unitLevel.cumulativeFactor,
                                unitQty = purchaseQty,
                                unitPrice = wholesaleUnitCost,
                                description = description,
                                qtyText = NumberFormatUtils.formatInputNumber(purchaseQty, 2),
                                totalText = NumberFormatUtils.formatInputNumber(total, 2),
                                explicitTotal = total
                            )
                        )
                        draftSearchQuery = ""
                        draftQtyInput = ""
                        draftTotalInput = ""
                        localError = null
                        showQuickWholesaleDialog = false
                        focusManager.clearFocus()
                    }.onFailure { err ->
                        localError = err.localizedMessage ?: "تعذر إضافة الصنف"
                    }
                }
            }
        )
    }

    // Dialog: Delete Saved Invoice Confirmation
    invoiceToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = { Text("تأكيد حذف الفاتورة") },
            text = {
                Text(
                    "هل تريد حذف الفاتورة #${target.invoiceNumber} (${target.contactName})؟ سيتم عكس أثرها من المخزون ورصيد الحساب الآجل تلقائياً.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        val removing = target
                        onDeleteInvoice(removing) {
                            if (editingInvoice?.id == removing.id) {
                                resetDraft()
                            }
                            invoiceToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LedgerCrimson),
                    modifier = Modifier.testTag("confirm_delete_invoice_button")
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun SmartCustomerSuggestionsPanel(
    invoiceType: InvoiceType,
    suggestions: List<ContactSuggestion>,
    onSelect: (ContactSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, LedgerNavy.copy(alpha = 0.35f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("smart_customer_suggestions")
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = LedgerGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (invoiceType == InvoiceType.PURCHASE) "اقتراحات الموردين" else "اقتراحات العملاء",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LedgerNavy
                    )
                }
                Text(
                    text = "إغلاق",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerNavy,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            suggestions.forEach { item ->
                Surface(
                    onClick = { onSelect(item) },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(0.8.dp, LedgerNavy.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            if (item.phone.isNotBlank()) {
                                Text(
                                    text = item.phone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Text(
                            text = if (item.fromHistoryOnly) {
                                "فاتورة سابقة"
                            } else {
                                "الرصيد: ${NumberFormatUtils.formatNumber(item.balance)} ر"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.fromHistoryOnly) Color(0xFF64748B) else LedgerNavy
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartProductSuggestionsPanel(
    invoiceType: InvoiceType,
    query: String,
    suggestions: List<Product>,
    canQuickCreate: Boolean,
    onPickUnit: (Product, String, Double) -> Unit,
    onQuickAddNewProduct: () -> Unit,
    onOpenFullPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, LedgerNavy.copy(alpha = 0.4f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("smart_product_suggestions")
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = LedgerGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (invoiceType == InvoiceType.PURCHASE) {
                            "شراء جملة (تُضاف الأصناف للمخزون دفعة واحدة عند الحفظ)"
                        } else if (query.isBlank()) {
                            "أصناف مقترحة (اضغط على الوحدة للإضافة)"
                        } else {
                            "نتائج البحث الذكي"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (invoiceType == InvoiceType.PURCHASE) LedgerGreen else LedgerNavy
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "بحث موسع",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenFullPicker)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "إغلاق",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerNavy,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (canQuickCreate) {
                Surface(
                    onClick = onQuickAddNewProduct,
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldContainer,
                    border = BorderStroke(1.dp, LedgerGreen.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_wholesale_product_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddShoppingCart,
                            contentDescription = null,
                            tint = OnEmeraldContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (invoiceType == InvoiceType.PURCHASE) {
                                if (query.isNotBlank()) "+ إضافة صنف بوحدة جملة للفاتورة (يُحفظ بالمخزون عند الحفظ): \"${query.trim()}\"" else "+ إضافة صنف بوحدة جملة للفاتورة (يُحفظ بالمخزون عند الحفظ)"
                            } else {
                                if (query.isNotBlank()) "+ إضافة صنف حر لهذه الفاتورة: \"${query.trim()}\"" else "+ إضافة صنف حر للفاتورة"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnEmeraldContainer
                        )
                    }
                }
            }

            if (suggestions.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "لا توجد أصناف مسجلة في المخزون بعد." else "لا توجد أصناف مطابقة لـ \"$query\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(4.dp)
                )
            } else {
                suggestions.forEach { prod ->
                    val units = UnitCostEngine.getAvailableUnits(prod)
                    val orderedUnits = if (invoiceType == InvoiceType.PURCHASE) units.reversed() else units
                    val wholesaleUnitName = units.lastOrNull()?.name
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(0.8.dp, LedgerNavy.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prod.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "المتاح: ${NumberFormatUtils.formatNumber(prod.stockBaseQty)} ${prod.baseUnitName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (prod.stockBaseQty > 0.0) LedgerGreen else LedgerCrimson
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                orderedUnits.forEach { level ->
                                    val isWholesalePrimary = invoiceType == InvoiceType.PURCHASE && level.name == wholesaleUnitName
                                    val autoPrice = if (invoiceType == InvoiceType.PURCHASE) {
                                        UnitCostEngine.calculateLargeUnitCostFromBase(prod.baseCost, level.cumulativeFactor)
                                    } else {
                                        UnitCostEngine.calculateUnitSellPrice(prod.baseSellPrice, level.cumulativeFactor)
                                    }
                                    Surface(
                                        onClick = { onPickUnit(prod, level.name, level.cumulativeFactor) },
                                        color = if (isWholesalePrimary) LedgerNavy else EmeraldContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        val prefix = if (isWholesalePrimary) "+ جملة: " else "+ "
                                        val factorNote = if (level.cumulativeFactor > 1.0) " (${NumberFormatUtils.formatNumber(level.cumulativeFactor)})" else ""
                                        Text(
                                            text = "$prefix${level.name}$factorNote • ${NumberFormatUtils.formatNumber(autoPrice)} ر",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isWholesalePrimary) Color.White else OnEmeraldContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeSaleLineDialog(
    initialName: String,
    initialDescription: String,
    initialUnitName: String,
    initialQty: Double,
    initialUnitPrice: Double,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: (
        name: String,
        description: String,
        unitName: String,
        qty: Double,
        unitPrice: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var unitName by remember { mutableStateOf(initialUnitName.ifBlank { "حبة" }) }
    var qtyText by remember { mutableStateOf(NumberFormatUtils.formatInputNumber(initialQty.coerceAtLeast(1.0), 2)) }
    var unitPriceText by remember {
        mutableStateOf(if (initialUnitPrice > 0.0) NumberFormatUtils.formatInputNumber(initialUnitPrice, 2) else "")
    }
    var lineTotalText by remember {
        val t = initialQty * initialUnitPrice
        mutableStateOf(if (t > 0.0) NumberFormatUtils.formatInputNumber(t, 2) else "")
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedQty = NumberFormatUtils.parseDecimal(qtyText) ?: 0.0
    val parsedUnitPrice = NumberFormatUtils.parseDecimal(unitPriceText) ?: 0.0
    val computedTotal = parsedQty * parsedUnitPrice

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isEditing) "تعديل صنف بيع حر" else "إضافة صنف بيع حر",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LedgerNavy
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("اسم الصنف *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("free_sale_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف الصنف (اختياري)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("free_sale_description_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = unitName,
                        onValueChange = { unitName = it },
                        label = { Text("الوحدة") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_sale_unit_input")
                    )
                    LtrDecimalField(
                        value = qtyText,
                        onValueChange = { raw ->
                            val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                            qtyText = clean
                            val q = NumberFormatUtils.parseDecimal(clean)
                            val p = NumberFormatUtils.parseDecimal(unitPriceText)
                            if (q != null && p != null) {
                                lineTotalText = NumberFormatUtils.formatInputNumber(q * p, 2)
                            }
                            errorMsg = null
                        },
                        label = "الكمية *",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_sale_qty_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtrDecimalField(
                        value = unitPriceText,
                        onValueChange = { raw ->
                            val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                            unitPriceText = clean
                            val p = NumberFormatUtils.parseDecimal(clean)
                            val q = NumberFormatUtils.parseDecimal(qtyText)
                            if (p != null && q != null) {
                                lineTotalText = NumberFormatUtils.formatInputNumber(q * p, 2)
                            }
                            errorMsg = null
                        },
                        label = "السعر *",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_sale_price_input")
                    )
                    LtrDecimalField(
                        value = lineTotalText,
                        onValueChange = { raw ->
                            val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                            lineTotalText = clean
                            val t = NumberFormatUtils.parseDecimal(clean)
                            val q = NumberFormatUtils.parseDecimal(qtyText)
                            if (t != null && q != null && q > 0.0) {
                                unitPriceText = NumberFormatUtils.formatInputNumber(t / q, 2)
                            }
                            errorMsg = null
                        },
                        label = "إجمالي السطر",
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = LedgerGold.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "صنف حر (لا يعدل المخزون)",
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${NumberFormatUtils.formatNumber(computedTotal)} ريال",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = LedgerCrimson
                        )
                    }
                }

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = LedgerCrimson)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("إلغاء") }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val cleanName = name.trim()
                                val cleanUnit = unitName.trim().ifEmpty { "حبة" }
                                if (cleanName.isEmpty()) {
                                    errorMsg = "يرجى إدخال اسم الصنف"
                                    return@Button
                                }
                                if (parsedQty <= 0.0) {
                                    errorMsg = "الكمية يجب أن تكون أكبر من صفر"
                                    return@Button
                                }
                                val p = NumberFormatUtils.parseDecimal(unitPriceText)
                                if (p == null || p < 0.0) {
                                    errorMsg = "يرجى إدخال السعر"
                                    return@Button
                                }
                                onConfirm(cleanName, description.trim(), cleanUnit, parsedQty, p)
                            },
                            modifier = Modifier.testTag("confirm_free_sale_line_button")
                        ) {
                            Text(if (isEditing) "تحديث" else "إضافة للفاتورة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickWholesaleProductDialog(
    invoiceType: InvoiceType,
    initialName: String,
    initialQtyText: String,
    initialTotalText: String,
    initialDescription: String = "",
    initialWholesaleUnit: String = "كرتون",
    initialBaseUnit: String = "حبة",
    initialFactorText: String = "24",
    initialWholesaleCostText: String = "",
    initialBaseSellText: String = "",
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirmCreate: (
        name: String,
        barcode: String,
        baseUnit: String,
        wholesaleUnit: String,
        factor: Double,
        purchaseQty: Double,
        wholesaleUnitCost: Double,
        baseSellPrice: Double,
        description: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var barcode by remember { mutableStateOf("") }
    var wholesaleUnit by remember { mutableStateOf(initialWholesaleUnit.ifBlank { "كرتون" }) }
    var baseUnit by remember { mutableStateOf(initialBaseUnit.ifBlank { "حبة" }) }
    var factorText by remember { mutableStateOf(initialFactorText.ifBlank { "24" }) }
    var qtyText by remember { mutableStateOf(NumberFormatUtils.sanitizeDecimalInput(initialQtyText).ifBlank { "1" }) }
    val initialQty = NumberFormatUtils.parseDecimal(initialQtyText)?.takeIf { it > 0.0 } ?: 1.0
    val initialTotal = NumberFormatUtils.parseDecimal(initialTotalText)?.takeIf { it > 0.0 }
    var wholesaleCostText by remember {
        mutableStateOf(
            when {
                initialWholesaleCostText.isNotBlank() -> NumberFormatUtils.sanitizeDecimalInput(initialWholesaleCostText)
                initialTotal != null -> NumberFormatUtils.formatInputNumber(initialTotal / initialQty, 2)
                else -> ""
            }
        )
    }
    var baseSellText by remember { mutableStateOf(initialBaseSellText) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val factor = NumberFormatUtils.parseDecimal(factorText) ?: 0.0
    val qty = NumberFormatUtils.parseDecimal(qtyText) ?: 0.0
    val wholesaleCost = NumberFormatUtils.parseDecimal(wholesaleCostText) ?: 0.0
    val effectiveFactor = if (wholesaleUnit.trim().isNotEmpty() && !wholesaleUnit.trim().equals(baseUnit.trim(), ignoreCase = true)) factor else 1.0
    val computedBaseCost = if (effectiveFactor > 0.0) wholesaleCost / effectiveFactor else 0.0
    val addedBaseUnits = if (qty > 0.0 && effectiveFactor > 0.0) qty * effectiveFactor else 0.0
    val purchaseLineTotal = if (qty > 0.0 && wholesaleCost >= 0.0) qty * wholesaleCost else 0.0

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        isEditing -> "تعديل صنف الشراء الحر"
                        invoiceType == InvoiceType.PURCHASE -> "إضافة صنف جديد (شراء حر)"
                        else -> "إضافة صنف حر للفاتورة"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الصنف *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("free_purchase_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف / ملاحظة الصنف (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = wholesaleUnit,
                        onValueChange = { wholesaleUnit = it },
                        label = { Text("الوحدة الكبيرة (كرتون/كيس)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_purchase_large_unit_input")
                    )
                    OutlinedTextField(
                        value = baseUnit,
                        onValueChange = { baseUnit = it },
                        label = { Text("الوحدة الصغيرة (حبة/كجم)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_purchase_small_unit_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtrDecimalField(
                        value = factorText,
                        onValueChange = { factorText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = "عدد الصغرى بالكبيرة",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_purchase_factor_input")
                    )
                    LtrDecimalField(
                        value = qtyText,
                        onValueChange = { qtyText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = "الكمية المشتراة",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_purchase_qty_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtrDecimalField(
                        value = wholesaleCostText,
                        onValueChange = { wholesaleCostText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = if (invoiceType == InvoiceType.PURCHASE) "سعر الوحدة الكبيرة" else "سعر الوحدة",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("free_purchase_cost_input")
                    )
                    if (invoiceType == InvoiceType.PURCHASE) {
                        LtrDecimalField(
                            value = baseSellText,
                            onValueChange = { baseSellText = NumberFormatUtils.sanitizeDecimalInput(it) },
                            label = "سعر بيع الصغيرة (اختياري)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (addedBaseUnits > 0.0 && invoiceType == InvoiceType.PURCHASE) {
                    Surface(
                        color = EmeraldContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "إجمالي الشراء: ${NumberFormatUtils.formatNumber(purchaseLineTotal)} ريال",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = OnEmeraldContainer
                            )
                            Text(
                                text = "المخزون عند الحفظ: +${NumberFormatUtils.formatNumber(addedBaseUnits)} $baseUnit",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = OnEmeraldContainer
                            )
                            Text(
                                text = "تكلفة $baseUnit: ${NumberFormatUtils.formatNumber(computedBaseCost)} ريال (لا يُضاف للمخزن إلا عند حفظ الفاتورة)",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnEmeraldContainer
                            )
                        }
                    }
                }

                if (invoiceType == InvoiceType.SALE) {
                    Text("سيُحفظ الصنف داخل فاتورة البيع فقط، ولن يُضاف إلى المخزن أو يُخصم منه.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = LedgerCrimson)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("إلغاء") }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val cleanName = name.trim()
                                val cleanBase = baseUnit.trim().ifEmpty { "حبة" }
                                val cleanWholesale = wholesaleUnit.trim()
                                if (cleanName.isEmpty()) {
                                    errorMsg = "يرجى إدخال اسم الصنف"
                                    return@Button
                                }
                                if (cleanWholesale.isNotEmpty() && !cleanWholesale.equals(cleanBase, ignoreCase = true) && factor <= 0.0) {
                                    errorMsg = "يرجى إدخال عدد الوحدات"
                                    return@Button
                                }
                                if (qty <= 0.0) {
                                    errorMsg = "الكمية يجب أن تكون أكبر من صفر"
                                    return@Button
                                }
                                val parsedCost = NumberFormatUtils.parseDecimal(wholesaleCostText)
                                if (parsedCost == null || parsedCost < 0.0) {
                                    errorMsg = "يرجى إدخال سعر الشراء"
                                    return@Button
                                }
                                val parsedSell = NumberFormatUtils.parseDecimal(baseSellText) ?: 0.0
                                onConfirmCreate(
                                    cleanName,
                                    barcode.trim(),
                                    cleanBase,
                                    cleanWholesale,
                                    effectiveFactor,
                                    qty,
                                    parsedCost,
                                    parsedSell,
                                    description.trim()
                                )
                            },
                            modifier = Modifier.testTag("confirm_free_purchase_line_button")
                        ) {
                            Text(if (isEditing) "تحديث السطر" else "إضافة للفاتورة")
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvoiceLinePickerDialog(
    invoiceType: InvoiceType,
    products: List<Product>,
    existingLine: PendingUiLine?,
    availableStockForProduct: (Product) -> Double = { it.stockBaseQty },
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onConfirm: (Product, String, Double, Double, Double) -> String?
) {
    var search by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf(existingLine?.product) }
    var selectedUnitName by remember { mutableStateOf(existingLine?.unitName ?: "") }
    var qtyText by remember {
        mutableStateOf(existingLine?.let { NumberFormatUtils.formatInputNumber(it.unitQty, 2) } ?: "1")
    }
    var totalText by remember {
        mutableStateOf(existingLine?.let { NumberFormatUtils.formatInputNumber(it.lineTotal, 2) } ?: "")
    }
    var priceText by remember {
        mutableStateOf(existingLine?.let { NumberFormatUtils.formatInputNumber(it.unitPrice, 2) } ?: "")
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val filteredProducts = remember(products, search) {
        if (search.isBlank()) products
        else products.filter {
            it.name.contains(search, ignoreCase = true) || it.barcode.contains(search, ignoreCase = true)
        }
    }

    val availableUnits = remember(selectedProduct) {
        selectedProduct?.let { UnitCostEngine.getAvailableUnits(it) } ?: emptyList()
    }
    val selectedUnitLevel = remember(availableUnits, selectedUnitName) {
        availableUnits.firstOrNull { it.name.equals(selectedUnitName, ignoreCase = true) }
            ?: availableUnits.lastOrNull()
    }

    val parsedQty = NumberFormatUtils.parseDecimal(qtyText) ?: 0.0
    val parsedPrice = NumberFormatUtils.parseDecimal(priceText) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingLine == null) "إدراج صنف في الفاتورة" else "تعديل الصنف")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("ابحث بالاسم أو الباركود") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (filteredProducts.isEmpty()) {
                    Text(
                        text = "لا توجد أصناف مطابقة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredProducts.take(6).forEach { prod ->
                            val isSelected = selectedProduct?.id == prod.id
                            Surface(
                                onClick = {
                                    selectedProduct = prod
                                    val units = UnitCostEngine.getAvailableUnits(prod)
                                    val preferred = if (invoiceType == InvoiceType.PURCHASE) {
                                        units.last()
                                    } else {
                                        units.firstOrNull { it.name.equals(prod.defaultUnitName, ignoreCase = true) }
                                            ?: units.first()
                                    }
                                    selectedUnitName = preferred.name
                                    val autoPrice = if (invoiceType == InvoiceType.PURCHASE) {
                                        UnitCostEngine.calculateLargeUnitCostFromBase(prod.baseCost, preferred.cumulativeFactor)
                                    } else {
                                        UnitCostEngine.calculateUnitSellPrice(prod.baseSellPrice, preferred.cumulativeFactor)
                                    }
                                    priceText = NumberFormatUtils.formatInputNumber(autoPrice, 2)
                                    val qty = NumberFormatUtils.parseDecimal(qtyText) ?: 1.0
                                    totalText = NumberFormatUtils.formatInputNumber(qty * autoPrice, 2)
                                    errorMsg = null
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = prod.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) OnEmeraldContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "المتاح: ${NumberFormatUtils.formatNumber(availableStockForProduct(prod))} ${prod.baseUnitName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) OnEmeraldContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (availableUnits.isNotEmpty() && selectedProduct != null) {
                    Text("الوحدة:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableUnits.forEach { level ->
                            FilterChip(
                                selected = selectedUnitLevel?.name == level.name,
                                onClick = {
                                    selectedUnitName = level.name
                                    val prod = selectedProduct!!
                                    val autoPrice = if (invoiceType == InvoiceType.PURCHASE) {
                                        UnitCostEngine.calculateLargeUnitCostFromBase(prod.baseCost, level.cumulativeFactor)
                                    } else {
                                        UnitCostEngine.calculateUnitSellPrice(prod.baseSellPrice, level.cumulativeFactor)
                                    }
                                    priceText = NumberFormatUtils.formatInputNumber(autoPrice, 2)
                                    val qty = NumberFormatUtils.parseDecimal(qtyText) ?: 1.0
                                    totalText = NumberFormatUtils.formatInputNumber(qty * autoPrice, 2)
                                },
                                label = {
                                    Text("${level.name} (${NumberFormatUtils.formatNumber(level.cumulativeFactor)})")
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LtrDecimalField(
                        value = totalText,
                        onValueChange = { raw ->
                            val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                            totalText = clean
                            val t = NumberFormatUtils.parseDecimal(clean)
                            val q = NumberFormatUtils.parseDecimal(qtyText)
                            if (t != null && q != null && q > 0.0) {
                                priceText = NumberFormatUtils.formatInputNumber(t / q, 4)
                            }
                        },
                        label = "القيمة الإجمالية",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("invoice_total_input")
                    )
                    LtrDecimalField(
                        value = qtyText,
                        onValueChange = { raw ->
                            val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                            qtyText = clean
                            val q = NumberFormatUtils.parseDecimal(clean)
                            val p = NumberFormatUtils.parseDecimal(priceText)
                            if (q != null && p != null) {
                                totalText = NumberFormatUtils.formatInputNumber(q * p, 2)
                            }
                        },
                        label = "الكمية",
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("invoice_qty_input")
                    )
                }

                LtrDecimalField(
                    value = priceText,
                    onValueChange = { raw ->
                        val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
                        priceText = clean
                        val p = NumberFormatUtils.parseDecimal(clean)
                        val q = NumberFormatUtils.parseDecimal(qtyText)
                        if (p != null && q != null) {
                            totalText = NumberFormatUtils.formatInputNumber(q * p, 2)
                        }
                    },
                    label = "سعر الواحدة (المستنتج)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invoice_price_input")
                )

                if (selectedProduct != null && selectedUnitLevel != null && parsedQty > 0) {
                    val inferred = if (parsedQty > 0.0) (parsedQty * parsedPrice) / parsedQty else 0.0
                    Surface(
                        color = EmeraldContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "القيمة الإجمالية: ${NumberFormatUtils.formatNumber(parsedQty * parsedPrice)} ريال",
                                fontWeight = FontWeight.Bold,
                                color = OnEmeraldContainer
                            )
                            Text(
                                text = "سعر الواحدة المستنتج: ${NumberFormatUtils.formatNumber(inferred)} ريال | الأساسية: ${NumberFormatUtils.formatNumber(parsedQty * selectedUnitLevel.cumulativeFactor)} ${selectedProduct!!.baseUnitName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnEmeraldContainer
                            )
                        }
                    }
                }

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = LedgerCrimson),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حذف الصنف من الفاتورة")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prod = selectedProduct
                    val unit = selectedUnitLevel
                    if (prod == null || unit == null) {
                        errorMsg = "يرجى اختيار الصنف والوحدة"
                        return@Button
                    }
                    if (parsedQty <= 0.0) {
                        errorMsg = "الكمية يجب أن تكون أكبر من صفر"
                        return@Button
                    }
                    if (parsedPrice < 0.0) {
                        errorMsg = "السعر لا يمكن أن يكون سالبًا"
                        return@Button
                    }
                    if (invoiceType == InvoiceType.SALE &&
                        !prod.id.startsWith("draft_") && !prod.id.startsWith("free_")) {
                        val effectiveProd = prod.copy(stockBaseQty = availableStockForProduct(prod))
                        val check = UnitCostEngine.computeSaleLine(effectiveProd, unit.name, parsedQty, parsedPrice)
                        if (check.isFailure) {
                            errorMsg = check.exceptionOrNull()?.message
                            return@Button
                        }
                    }
                    val err = onConfirm(prod, unit.name, unit.cumulativeFactor, parsedQty, parsedPrice)
                    if (err != null) {
                        errorMsg = err
                    }
                },
                modifier = Modifier.testTag("add_invoice_line_button")
            ) {
                Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (existingLine == null) "إضافة" else "تحديث")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    fontSizeSp: Float? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(horizontal = 3.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontFamily = AmiriFontFamily,
            color = textColor,
            fontSize = (fontSizeSp ?: 13f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .border(1.5.dp, LedgerNavy, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LedgerNavy)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = LedgerNavy
        )
    }
}

@Composable
private fun DottedLine(
    modifier: Modifier = Modifier,
    color: Color = LedgerNavy
) {
    Canvas(modifier = modifier.height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )
    }
}

@Composable
private fun InvoiceHistoryContent(
    invoicesState: UiState<List<Invoice>>,
    onEditInvoice: (Invoice) -> Unit,
    onDeleteInvoice: (Invoice) -> Unit,
    onOpenReceipt: (Invoice) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    var modeFilter by remember { mutableStateOf<InvoiceMode?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (invoicesState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(invoicesState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val allInvoices = invoicesState.data
                val invoices = remember(allInvoices, modeFilter) {
                    if (modeFilter == null) allInvoices
                    else allInvoices.filter { it.resolvedMode == modeFilter }
                }
                if (allInvoices.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = LedgerNavy,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لا توجد فواتير مسجلة بعد", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "history_mode_filter") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = modeFilter == null,
                                    onClick = { modeFilter = null },
                                    label = { Text("الكل (${allInvoices.size})", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = modeFilter == InvoiceMode.STOCK,
                                    onClick = { modeFilter = InvoiceMode.STOCK },
                                    label = { Text("من المخزن (${allInvoices.count { it.resolvedMode == InvoiceMode.STOCK }})", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = modeFilter == InvoiceMode.FREE,
                                    onClick = { modeFilter = InvoiceMode.FREE },
                                    label = { Text("حرة (${allInvoices.count { it.resolvedMode == InvoiceMode.FREE }})", fontSize = 11.sp) }
                                )
                            }
                        }

                        items(invoices, key = { it.id }) { invoice ->
                            val isPurchase = invoice.resolvedType == InvoiceType.PURCHASE
                            val dateStr = invoice.createdAt?.toDate()?.let { dateFormat.format(it) } ?: ""

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, LedgerNavy.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_invoice_card_${invoice.invoiceNumber}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = if (isPurchase) LedgerNavy else LedgerGreen,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isPurchase) "شراء جملة" else "بيع",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                color = if (invoice.resolvedMode == InvoiceMode.FREE) LedgerGold else LedgerNavy.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (invoice.resolvedMode == InvoiceMode.FREE) "حرة" else "من المخزن",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = LedgerNavy,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "#${invoice.invoiceNumber}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = LedgerCrimson,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "${NumberFormatUtils.formatNumber(invoice.totalAmount)} ر",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = LedgerNavy,
                                            maxLines = 1
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${invoice.contactName} • ${if (invoice.resolvedPaymentMethod == PaymentMethod.CASH) "نقداً" else "آجل"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (dateStr.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (invoice.discount > 0.0 || invoice.resolvedRemainingAmount > 0.0) {
                                        val parts = buildList {
                                            if (invoice.discount > 0.0) add("خصم: ${NumberFormatUtils.formatNumber(invoice.discount)}")
                                            add("مدفوع: ${NumberFormatUtils.formatNumber(invoice.resolvedPaidAmount)}")
                                            add("متبقي: ${NumberFormatUtils.formatNumber(invoice.resolvedRemainingAmount)}")
                                        }.joinToString(" • ")
                                        Text(
                                            text = parts,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (invoice.resolvedRemainingAmount > 0.0001) LedgerCrimson else LedgerGreen
                                        )
                                    }

                                    HorizontalDivider(color = LedgerNavy.copy(alpha = 0.12f))

                                    invoice.lines.forEach { line ->
                                        val descSuffix = if (line.description.isNotBlank()) " (${line.description})" else ""
                                        Text(
                                            text = "• ${line.productName}$descSuffix: ${NumberFormatUtils.formatNumber(line.unitQty)} ${line.unitName} × ${NumberFormatUtils.formatNumber(line.unitPrice)} = ${NumberFormatUtils.formatNumber(line.lineTotal)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { onEditInvoice(invoice) },
                                            shape = RoundedCornerShape(9.dp),
                                            border = BorderStroke(1.dp, LedgerNavy),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("edit_invoice_button_${invoice.invoiceNumber}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "تعديل الفاتورة",
                                                tint = LedgerNavy,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تعديل", color = LedgerNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        OutlinedButton(
                                            onClick = { onDeleteInvoice(invoice) },
                                            shape = RoundedCornerShape(9.dp),
                                            border = BorderStroke(1.dp, LedgerCrimson.copy(alpha = 0.7f)),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("delete_invoice_button_${invoice.invoiceNumber}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "حذف الفاتورة",
                                                tint = LedgerCrimson,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("حذف", color = LedgerCrimson, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        Button(
                                            onClick = { onOpenReceipt(invoice) },
                                            shape = RoundedCornerShape(9.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = LedgerNavy),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier
                                                .weight(1.25f)
                                                .height(48.dp)
                                                .testTag("open_receipt_button_${invoice.invoiceNumber}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Print,
                                                contentDescription = "طباعة وإيصال",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("إيصال / طباعة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

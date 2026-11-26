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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _, onSuccess -> onSuccess() },
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
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var manualCustomerName by remember { mutableStateOf("") }
    var isCustomerFieldFocused by remember { mutableStateOf(false) }
    var customInvoiceNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val draftLines = remember { mutableStateListOf<PendingUiLine>() }
    var lineKeyCounter by remember { mutableIntStateOf(0) }

    // Inline active row inputs & smart product suggestion state
    var draftSearchQuery by remember { mutableStateOf("") }
    var draftQtyInput by remember { mutableStateOf("") }
    var draftTotalInput by remember { mutableStateOf("") }
    var isProductInputFocused by remember { mutableStateOf(false) }
    var unitPickerLineKey by remember { mutableStateOf<String?>(null) }

    var showCustomerDialog by remember { mutableStateOf(false) }
    var showInvoiceNumDialog by remember { mutableStateOf(false) }
    var showAddLineDialog by remember { mutableStateOf(false) }
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
    val grandTotal = draftLines.sumOf { it.lineTotal }

    val customerSuggestions = remember(manualCustomerName, contacts, savedInvoices, invoiceType) {
        SmartSuggestionEngine.suggestContacts(
            query = manualCustomerName,
            contacts = contacts,
            savedInvoices = savedInvoices,
            invoiceType = invoiceType,
            limit = 5
        )
    }

    val productSuggestions = remember(draftSearchQuery, products, invoiceType) {
        SmartSuggestionEngine.suggestProducts(
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

    fun resetDraft() {
        editingInvoice = null
        draftLines.clear()
        selectedContact = null
        manualCustomerName = ""
        isCustomerFieldFocused = false
        customInvoiceNumber = ""
        notes = ""
        draftSearchQuery = ""
        draftQtyInput = ""
        draftTotalInput = ""
        isProductInputFocused = false
        unitPickerLineKey = null
        localError = null
        paymentMethod = PaymentMethod.CASH
    }

    fun loadInvoiceForEdit(invoice: Invoice) {
        resetDraft()
        editingInvoice = invoice
        invoiceType = invoice.resolvedType
        paymentMethod = invoice.resolvedPaymentMethod
        customInvoiceNumber = invoice.invoiceNumber
        notes = invoice.notes
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

    fun addProductFromSuggestion(prod: Product, unitName: String, factor: Double) {
        val dupLine = draftLines.any {
            it.product.id == prod.id && it.unitName.equals(unitName, ignoreCase = true)
        }
        if (dupLine) {
            localError = "تمت إضافة (${prod.name}) بوحدة ($unitName) مسبقًا، لا يُسمح بالتكرار"
            return
        }
        val q = NumberFormatUtils.parseDecimal(draftQtyInput)?.takeIf { it > 0.0 } ?: 1.0
        val defaultPrice = if (invoiceType == InvoiceType.PURCHASE) {
            UnitCostEngine.calculateLargeUnitCostFromBase(prod.baseCost, factor)
        } else {
            UnitCostEngine.calculateUnitSellPrice(prod.baseSellPrice, factor)
        }
        val preTotal = NumberFormatUtils.parseDecimal(draftTotalInput)?.takeIf { it > 0.0 }
        val effectiveUnitPrice = if (preTotal != null) preTotal / q else defaultPrice

        val available = effectiveAvailableStock(prod)
        val alreadyInDraft = draftLines.filter { it.product.id == prod.id }.sumOf { it.baseQty }
        if (invoiceType == InvoiceType.SALE && (alreadyInDraft + q * factor) - available > 0.0001) {
            localError = "الكمية المطلوبة من (${prod.name}) تتجاوز المتاح بالمخزون (${NumberFormatUtils.formatNumber(available)} ${prod.baseUnitName})"
            return
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
        isProductInputFocused = false
        localError = null
        focusManager.clearFocus()
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
                paymentMethod = paymentMethod.name,
                contactId = selectedContact?.id ?: "",
                contactName = resolvedName,
                totalAmount = grandTotal,
                createdAt = editingInvoice?.createdAt,
                lines = draftLines.map {
                    InvoiceLine(
                        productId = it.product.id.takeUnless { id -> id.startsWith("draft_") || id.startsWith("free_") } ?: "",
                        productName = it.product.name,
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
                                    onClick = { paymentMethod = PaymentMethod.CASH }
                                )
                                RadioOption(
                                    label = "آجل",
                                    selected = paymentMethod == PaymentMethod.CREDIT,
                                    onClick = { paymentMethod = PaymentMethod.CREDIT }
                                )
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
                                    val exceedsStock = invoiceType == InvoiceType.SALE &&
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
                                val activeTotalParsed = NumberFormatUtils.parseDecimal(draftTotalInput)?.takeIf { it > 0.0 }
                                val activeInferredText = if (activeTotalParsed != null && activeQtyParsed != null) {
                                    NumberFormatUtils.formatNumber(activeTotalParsed / activeQtyParsed)
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
                                            modifier = Modifier
                                                .fillMaxWidth()
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

                                    // Col 3: التفاصيل (Direct Product Search Input + Full Picker Icon)
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
                                            modifier = Modifier
                                                .weight(1f)
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
                                                        Text(
                                                            text = when {
                                                                invoiceType == InvoiceType.PURCHASE -> "اسم الصنف للشراء..."
                                                                products.isEmpty() -> "أضف أصناف أولاً..."
                                                                else -> "اكتب اسم الصنف..."
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
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable { showAddLineDialog = true }
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
                            }

                            // Smart Product Autocomplete Suggestions directly below active row (never steals focus)
                            AnimatedVisibility(
                                visible = (isProductInputFocused || draftSearchQuery.isNotBlank()) &&
                                    (products.isNotEmpty() || onQuickCreateProduct != null)
                            ) {
                                SmartProductSuggestionsPanel(
                                    invoiceType = invoiceType,
                                    query = draftSearchQuery,
                                    suggestions = productSuggestions,
                                    canQuickCreate = onQuickCreateProduct != null,
                                    onPickUnit = { prod, unitName, factor ->
                                        addProductFromSuggestion(prod, unitName, factor)
                                    },
                                    onQuickAddNewProduct = {
                                        isProductInputFocused = false
                                        showQuickWholesaleDialog = true
                                    },
                                    onOpenFullPicker = {
                                        isProductInputFocused = false
                                        showAddLineDialog = true
                                    },
                                    onDismiss = {
                                        isProductInputFocused = false
                                        focusManager.clearFocus()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            // 5. Totals Double Box (الإجمالي الكلي: 0 | عدد الأصناف: 0)
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
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الإجمالي الكلي:",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LedgerNavy
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatNumber(grandTotal),
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
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                            if (draftLines.isEmpty()) {
                                localError = "يرجى إضافة صنف واحد على الأقل للفاتورة"
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
                            if (paymentMethod == PaymentMethod.CREDIT && selectedContact == null) {
                                localError = "يرجى اختيار العميل أو المورد للفاتورة الآجلة"
                                return@Button
                            }
                            localError = null
                            val payloadLines = draftLines.map {
                                InvoiceDraftLine(
                                    productId = it.product.id.takeUnless { id -> id.startsWith("draft_") || id.startsWith("free_") } ?: "",
                                    unitName = it.unitName,
                                    unitQty = it.unitQty,
                                    unitPrice = it.unitPrice,
                                    draftProduct = it.product.takeIf { product -> product.id.startsWith("draft_") || product.id.startsWith("free_") }
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
                                    notes
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
                                    notes
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

    // Dialog: Add Item Line to Table
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

    // Dialog: Edit or Delete Existing Table Line
    val editIdx = editingLineIndex
    if (editIdx != null && editIdx in draftLines.indices) {
        val current = draftLines[editIdx]
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

    // Dialog: Quick-create a brand new wholesale product into inventory directly from Purchase Invoice
    if (showQuickWholesaleDialog && onQuickCreateProduct != null) {
        QuickWholesaleProductDialog(
            invoiceType = invoiceType,
            initialName = draftSearchQuery.trim(),
            initialQtyText = draftQtyInput.ifBlank { "1" },
            initialTotalText = draftTotalInput,
            onDismiss = { showQuickWholesaleDialog = false },
            onConfirmCreate = { name, barcode, baseUnit, wholesaleUnit, factor, purchaseQty, wholesaleUnitCost, baseSellPrice ->
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
                            "شراء جملة (تضاف الكميات للمخزون)"
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
                                if (query.isNotBlank()) "+ إضافة صنف جديد للمخزون وشرائه جملة: \"${query.trim()}\"" else "+ تعريف صنف جديد في المخزون والشراء جملة"
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
private fun QuickWholesaleProductDialog(
    invoiceType: InvoiceType,
    initialName: String,
    initialQtyText: String,
    initialTotalText: String,
    onDismiss: () -> Unit,
    onConfirmCreate: (
        name: String,
        barcode: String,
        baseUnit: String,
        wholesaleUnit: String,
        factor: Double,
        purchaseQty: Double,
        wholesaleUnitCost: Double,
        baseSellPrice: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var barcode by remember { mutableStateOf("") }
    var wholesaleUnit by remember { mutableStateOf("كرتون") }
    var baseUnit by remember { mutableStateOf("حبة") }
    var factorText by remember { mutableStateOf("24") }
    var qtyText by remember { mutableStateOf(NumberFormatUtils.sanitizeDecimalInput(initialQtyText).ifBlank { "1" }) }
    val initialQty = NumberFormatUtils.parseDecimal(initialQtyText)?.takeIf { it > 0.0 } ?: 1.0
    val initialTotal = NumberFormatUtils.parseDecimal(initialTotalText)?.takeIf { it > 0.0 }
    var wholesaleCostText by remember {
        mutableStateOf(
            if (initialTotal != null) NumberFormatUtils.formatInputNumber(initialTotal / initialQty, 2) else ""
        )
    }
    var baseSellText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val factor = NumberFormatUtils.parseDecimal(factorText) ?: 0.0
    val qty = NumberFormatUtils.parseDecimal(qtyText) ?: 0.0
    val wholesaleCost = NumberFormatUtils.parseDecimal(wholesaleCostText) ?: 0.0
    val effectiveFactor = if (wholesaleUnit.trim().isNotEmpty() && !wholesaleUnit.trim().equals(baseUnit.trim(), ignoreCase = true)) factor else 1.0
    val computedBaseCost = if (effectiveFactor > 0.0) wholesaleCost / effectiveFactor else 0.0
    val addedBaseUnits = if (qty > 0.0 && effectiveFactor > 0.0) qty * effectiveFactor else 0.0

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
                    text = if (invoiceType == InvoiceType.PURCHASE) "إضافة صنف جديد (شراء جملة)" else "إضافة صنف حر للفاتورة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الصنف *") },
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
                        label = { Text("وحدة الجملة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = baseUnit,
                        onValueChange = { baseUnit = it },
                        label = { Text("الوحدة الصغرى") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtrDecimalField(
                        value = factorText,
                        onValueChange = { factorText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = "العدد في الجملة",
                        modifier = Modifier.weight(1f)
                    )
                    LtrDecimalField(
                        value = qtyText,
                        onValueChange = { qtyText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = "الكمية",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtrDecimalField(
                        value = wholesaleCostText,
                        onValueChange = { wholesaleCostText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = if (invoiceType == InvoiceType.PURCHASE) "سعر شراء الجملة" else "سعر الوحدة في الفاتورة",
                        modifier = Modifier.weight(1f)
                    )
                    if (invoiceType == InvoiceType.PURCHASE) {
                        LtrDecimalField(
                            value = baseSellText,
                            onValueChange = { baseSellText = NumberFormatUtils.sanitizeDecimalInput(it) },
                            label = "سعر بيع الحبة",
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
                                text = "إضافة للمخزون: +${NumberFormatUtils.formatNumber(addedBaseUnits)} $baseUnit",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = OnEmeraldContainer
                            )
                            Text(
                                text = "تكلفة الحبة المحسوبة: ${NumberFormatUtils.formatNumber(computedBaseCost)} ريال",
                                style = MaterialTheme.typography.bodySmall,
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
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
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
                                parsedSell
                            )
                        }
                    ) {
                        Text("حفظ وإضافة")
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (invoicesState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(invoicesState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val invoices = invoicesState.data
                if (invoices.isEmpty()) {
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

                                    HorizontalDivider(color = LedgerNavy.copy(alpha = 0.12f))

                                    invoice.lines.forEach { line ->
                                        Text(
                                            text = "• ${line.productName}: ${NumberFormatUtils.formatNumber(line.unitQty)} ${line.unitName} × ${NumberFormatUtils.formatNumber(line.unitPrice)} = ${NumberFormatUtils.formatNumber(line.lineTotal)}",
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

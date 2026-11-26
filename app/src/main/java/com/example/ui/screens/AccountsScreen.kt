package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.Contact
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceMode
import com.example.domain.InvoiceType
import com.example.domain.NumberFormatUtils
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.UiState
import com.example.export.ReceiptRenderer4K
import com.example.export.ShareHelper
import com.example.print.PrinterSettingsStore
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.LedgerNavy
import com.example.ui.theme.LedgerCrimson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class LedgerOpItem {
    abstract val id: String
    abstract val timestamp: Long
    abstract val dateStr: String
    abstract val title: String
    abstract val subtitle: String
    abstract val amount: Double
    abstract val notes: String
    abstract val isInvoice: Boolean

    data class InvoiceOp(val invoice: Invoice) : LedgerOpItem() {
        override val id: String get() = invoice.id
        override val timestamp: Long get() = invoice.createdAt?.toDate()?.time ?: 0L
        override val dateStr: String get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(invoice.createdAt?.toDate() ?: Date())
        override val title: String
            get() {
                val typeLabel = if (invoice.resolvedType == InvoiceType.PURCHASE) "شراء جملة" else "بيع"
                val modeLabel = if (invoice.resolvedMode == InvoiceMode.FREE) "حرة" else "من المخزن"
                return "فاتورة #${invoice.invoiceNumber} ($typeLabel • $modeLabel)"
            }
        override val subtitle: String
            get() {
                val payLabel = if (invoice.resolvedPaymentMethod == PaymentMethod.CASH) "نقداً" else "آجل"
                val extra = buildList {
                    if (invoice.discount > 0.0) add("خصم ${NumberFormatUtils.formatNumber(invoice.discount)}")
                    if (invoice.resolvedRemainingAmount > 0.0001 && invoice.resolvedPaidAmount > 0.0001) {
                        add("مدفوع ${NumberFormatUtils.formatNumber(invoice.resolvedPaidAmount)}")
                        add("متبقي ${NumberFormatUtils.formatNumber(invoice.resolvedRemainingAmount)}")
                    }
                }
                val base = "$payLabel • ${invoice.lines.size} أصناف"
                return if (extra.isEmpty()) base else "$base • ${extra.joinToString(" • ")}"
            }
        override val amount: Double get() = invoice.totalAmount
        override val notes: String get() = invoice.notes
        override val isInvoice: Boolean get() = true
    }

    data class PaymentOp(val payment: Payment) : LedgerOpItem() {
        override val id: String get() = payment.id
        override val timestamp: Long get() = payment.createdAt?.toDate()?.time ?: 0L
        override val dateStr: String get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(payment.createdAt?.toDate() ?: Date())
        override val title: String
            get() = when {
                payment.notes.contains("توصيل", ignoreCase = true) -> "سند توصيل"
                payment.notes.contains("طرح", ignoreCase = true) -> "سند طرح"
                else -> "سند سداد"
            }
        override val subtitle: String get() = payment.notes.ifBlank { "سداد رصيد" }
        override val amount: Double get() = payment.amount
        override val notes: String get() = payment.notes
        override val isInvoice: Boolean get() = false
    }
}

@Composable
fun AccountsScreen(
    contactsState: UiState<List<Contact>>,
    invoicesState: UiState<List<Invoice>>,
    paymentsState: UiState<List<Payment>>,
    isBusy: Boolean,
    onSaveContact: (
        existingId: String?,
        name: String,
        contactType: ContactType,
        phone: String,
        openingBalance: Double,
        notes: String,
        onSuccess: () -> Unit
    ) -> Unit,
    onDeleteContact: (contact: Contact, onSuccess: () -> Unit) -> Unit,
    onRecordPayment: (
        contactId: String,
        amount: Double,
        notes: String,
        onSuccess: () -> Unit
    ) -> Unit,
    onUpdatePayment: (
        paymentId: String,
        amount: Double,
        notes: String,
        onSuccess: () -> Unit
    ) -> Unit,
    onDeletePayment: (payment: Payment, onSuccess: () -> Unit) -> Unit,
    onEditInvoice: (Invoice) -> Unit = {},
    onOpenReceipt: (Invoice) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf(ContactType.CUSTOMER) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var contactToDelete by remember { mutableStateOf<Contact?>(null) }
    var paymentTarget by remember { mutableStateOf<Contact?>(null) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }
    var ledgerOperationsContact by remember { mutableStateOf<Contact?>(null) }

    BackHandler(enabled = ledgerOperationsContact != null) {
        ledgerOperationsContact = null
    }

    val allInvoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val allPayments = (paymentsState as? UiState.Success)?.data ?: emptyList()

    val targetContact = ledgerOperationsContact
    if (targetContact != null) {
        CustomerLedgerOperationsScreen(
            contact = targetContact,
            allInvoices = allInvoices,
            allPayments = allPayments,
            onBack = { ledgerOperationsContact = null },
            onEditInvoice = { inv ->
                ledgerOperationsContact = null
                onEditInvoice(inv)
            },
            onOpenReceipt = onOpenReceipt,
            onEditPayment = { pay -> editingPayment = pay },
            onDeletePayment = { pay -> paymentToDelete = pay }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (contactsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(contactsState.message, color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> {
                val contacts = contactsState.data
                val customerTotal = remember(contacts) {
                    contacts.filter { it.resolvedType == ContactType.CUSTOMER }.sumOf { it.balance }
                }
                val supplierTotal = remember(contacts) {
                    contacts.filter { it.resolvedType == ContactType.SUPPLIER }.sumOf { it.balance }
                }
                val filtered = remember(contacts, selectedType) {
                    contacts.filter { it.resolvedType == selectedType }
                }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 78.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BalanceSummaryCard(
                                title = "لنا عند العملاء",
                                amount = customerTotal,
                                accentColor = EmeraldAccent,
                                modifier = Modifier.weight(1f)
                            )
                            BalanceSummaryCard(
                                title = "علينا للموردين",
                                amount = supplierTotal,
                                accentColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedType == ContactType.CUSTOMER,
                                onClick = { selectedType = ContactType.CUSTOMER },
                                label = { Text("العملاء (${contacts.count { it.resolvedType == ContactType.CUSTOMER }})") }
                            )
                            FilterChip(
                                selected = selectedType == ContactType.SUPPLIER,
                                onClick = { selectedType = ContactType.SUPPLIER },
                                label = { Text("الموردين (${contacts.count { it.resolvedType == ContactType.SUPPLIER }})") }
                            )
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = if (selectedType == ContactType.CUSTOMER) "لا يوجد عملاء مسجلون بعد" else "لا يوجد موردون مسجلون بعد",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Button(onClick = { showAddDialog = true }) {
                                        Text(if (selectedType == ContactType.CUSTOMER) "إضافة عميل" else "إضافة مورد")
                                    }
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { contact ->
                            ContactLedgerCard(
                                contact = contact,
                                onEditClick = { editingContact = contact },
                                onDeleteClick = { contactToDelete = contact },
                                onRecordPaymentClick = { paymentTarget = contact },
                                onOpenLedgerOperations = { ledgerOperationsContact = contact },
                                onShareWhatsApp4K = {
                                    scope.launch {
                                        val storeName = PrinterSettingsStore.load(context).storeName
                                        val bmp = withContext(Dispatchers.Default) {
                                            ReceiptRenderer4K.renderContactStatement4K(
                                                context = context,
                                                contact = contact,
                                                invoices = allInvoices,
                                                payments = allPayments,
                                                storeName = storeName
                                            )
                                        }
                                        val uri = withContext(Dispatchers.IO) {
                                            ReceiptRenderer4K.saveBitmapToShareCachePng(
                                                context = context,
                                                bitmap = bmp,
                                                prefix = "statement_4k_${contact.id}"
                                            )
                                        }
                                        val caption = ShareHelper.formatContactStatementText(
                                            contact = contact,
                                            invoices = allInvoices,
                                            payments = allPayments,
                                            storeName = storeName
                                        )
                                        ShareHelper.shareImage4KViaWhatsApp(context, uri, caption)
                                    }
                                }
                            )
                        }
                    }

                    if (allPayments.isNotEmpty()) {
                        item {
                            Text(
                                text = "سندات السداد (${allPayments.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(allPayments, key = { it.id }) { payment ->
                            val payDate = payment.createdAt?.toDate()?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) } ?: ""
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("payment_card_${payment.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = payment.contactName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (payment.notes.isNotBlank() || payDate.isNotBlank()) {
                                            Text(
                                                text = listOf(payDate, payment.notes).filter { it.isNotBlank() }.joinToString(" • "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${NumberFormatUtils.formatNumber(payment.amount)} ر",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldAccent,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    IconButton(
                                        onClick = { editingPayment = payment },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_payment_${payment.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "تعديل السند",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { paymentToDelete = payment },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_payment_${payment.id}")
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.DeleteOutline,
                                            contentDescription = "حذف السند",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .testTag("add_contact_fab")
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "إضافة حساب")
        }
    }

    if (showAddDialog) {
        ContactFormDialog(
            existingContact = null,
            initialType = selectedType,
            isBusy = isBusy,
            onDismiss = { showAddDialog = false },
            onSave = { name, type, phone, balance, notes ->
                onSaveContact(null, name, type, phone, balance, notes) {
                    showAddDialog = false
                }
            }
        )
    }

    editingContact?.let { target ->
        ContactFormDialog(
            existingContact = target,
            initialType = target.resolvedType,
            isBusy = isBusy,
            onDismiss = { editingContact = null },
            onSave = { name, type, phone, balance, notes ->
                onSaveContact(target.id, name, type, phone, balance, notes) {
                    editingContact = null
                }
            }
        )
    }

    contactToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("تأكيد حذف الحساب") },
            text = { Text("هل تريد حذف حساب \"${target.name}\"؟ لن تُحذف الفواتير المحفوظة مسبقًا.") },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        onDeleteContact(target) {
                            contactToDelete = null
                        }
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) { Text("إلغاء") }
            }
        )
    }

    paymentTarget?.let { target ->
        RecordPaymentDialog(
            contact = target,
            isBusy = isBusy,
            onDismiss = { paymentTarget = null },
            onConfirm = { amount, notes ->
                onRecordPayment(target.id, amount, notes) {
                    paymentTarget = null
                }
            }
        )
    }

    editingPayment?.let { target ->
        EditPaymentDialog(
            payment = target,
            isBusy = isBusy,
            onDismiss = { editingPayment = null },
            onConfirm = { amount, notes ->
                onUpdatePayment(target.id, amount, notes) {
                    editingPayment = null
                }
            }
        )
    }

    paymentToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("تأكيد حذف السند") },
            text = {
                Text(
                    "هل تريد حذف سند السداد لـ \"${target.contactName}\" بمبلغ ${NumberFormatUtils.formatNumber(target.amount)} ريال؟ سيتم إرجاع المبلغ لرصيد الحساب."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        onDeletePayment(target) {
                            paymentToDelete = null
                        }
                    }
                ) {
                    Text("حذف وإرجاع", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun CustomerLedgerOperationsScreen(
    contact: Contact,
    allInvoices: List<Invoice>,
    allPayments: List<Payment>,
    onBack: () -> Unit,
    onEditInvoice: (Invoice) -> Unit,
    onOpenReceipt: (Invoice) -> Unit,
    onEditPayment: (Payment) -> Unit,
    onDeletePayment: (Payment) -> Unit
) {
    val context = LocalContext.current
    val contactInvoices = remember(allInvoices, contact) {
        allInvoices.filter { it.contactId == contact.id || it.contactName.equals(contact.name, ignoreCase = true) }
            .map { LedgerOpItem.InvoiceOp(it) }
    }
    val contactPayments = remember(allPayments, contact) {
        allPayments.filter { it.contactId == contact.id || it.contactName.equals(contact.name, ignoreCase = true) }
            .map { LedgerOpItem.PaymentOp(it) }
    }

    val allOps = remember(contactInvoices, contactPayments) {
        (contactInvoices + contactPayments).sortedByDescending { it.timestamp }
    }

    // Ledger accounting: invoices are debit; payments/vouchers are credit.
    // Paid portions of cash invoices are included in credit so the summary reconciles
    // with the account balance without creating duplicate ledger records.
    val totalDebit = remember(contactInvoices) {
        contactInvoices.sumOf { it.invoice.totalAmount.coerceAtLeast(0.0) }
    }
    val totalCredit = remember(contactInvoices, contactPayments) {
        contactPayments.sumOf { it.payment.amount.coerceAtLeast(0.0) } +
            contactInvoices.sumOf { it.invoice.resolvedPaidAmount.coerceAtLeast(0.0) }
    }

    var filterMode by remember { mutableIntStateOf(0) } // 0: الكل, 1: فواتير, 2: سندات
    val operations = remember(allOps, filterMode) {
        when (filterMode) {
            1 -> allOps.filter { it.isInvoice }
            2 -> allOps.filter { !it.isInvoice }
            else -> allOps
        }
    }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top Header with Contact Info & Summary
        Surface(
            color = LedgerNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${if (contact.resolvedType == ContactType.CUSTOMER) "عميل" else "مورد"}: ${contact.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "الرصيد: ${NumberFormatUtils.formatNumber(contact.balance)} ريال • (${allOps.size} عملية مسجلة)",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                        Text(
                            text = "مدين: ${NumberFormatUtils.formatNumber(totalDebit)} ر  •  دائن: ${NumberFormatUtils.formatNumber(totalCredit)} ر",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f),
                            maxLines = 1
                        )
                        Text(
                            text = if (contact.balance >= 0.0) {
                                "الصافي المستحق: ${NumberFormatUtils.formatNumber(contact.balance)} ريال (مدين)"
                            } else {
                                "الصافي المستحق: ${NumberFormatUtils.formatNumber(kotlin.math.abs(contact.balance))} ريال (دائن)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2. Filter chips & Selection Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filterMode == 0,
                    onClick = { filterMode = 0 },
                    label = { Text("الكل (${allOps.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = filterMode == 1,
                    onClick = { filterMode = 1 },
                    label = { Text("فواتير (${contactInvoices.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = filterMode == 2,
                    onClick = { filterMode = 2 },
                    label = { Text("سندات (${contactPayments.size})", fontSize = 11.sp) }
                )
            }

            if (operations.isNotEmpty()) {
                TextButton(
                    onClick = {
                        selectedIds = if (selectedIds.size == operations.size) emptySet() else operations.map { it.id }.toSet()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        if (selectedIds.size == operations.size) "إلغاء التحديد" else "تحديد الكل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Tabular Ledger Rows like Invoice Lines
        if (operations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = AppIcons.ReceiptLong,
                        contentDescription = null,
                        tint = LedgerNavy,
                        modifier = Modifier.size(44.dp)
                    )
                    Text("لا توجد عمليات مسجلة في هذا التصنيف", style = MaterialTheme.typography.titleSmall)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // Table Container
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.1.dp, LedgerNavy.copy(alpha = 0.65f)),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Header Row (Dark Ledger Theme)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LedgerNavy)
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تحديد",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.width(36.dp)
                                )
                                androidx.compose.material3.VerticalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                Text(
                                    text = "التاريخ والعملية",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1.3f)
                                )
                                androidx.compose.material3.VerticalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                Text(
                                    text = "البيان / التفاصيل",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1.4f)
                                )
                                androidx.compose.material3.VerticalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                Text(
                                    text = "المبلغ",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1.1f)
                                )
                                androidx.compose.material3.VerticalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                Text(
                                    text = "إجراءات",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.width(86.dp)
                                )
                            }
                            HorizontalDivider(thickness = 1.dp, color = LedgerNavy)
                        }

                        // Data Rows
                        items(operations, key = { it.id }) { op ->
                            val isSelected = selectedIds.contains(op.id)
                            val rowBg = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                operations.indexOf(op) % 2 == 1 -> androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                                else -> androidx.compose.ui.graphics.Color.White
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .clickable {
                                        selectedIds = if (isSelected) selectedIds - op.id else selectedIds + op.id
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Checkbox Col
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.width(36.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + op.id else selectedIds - op.id
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                androidx.compose.material3.VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(28.dp))

                                // Date & Title Col
                                Column(
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = op.title,
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (op.isInvoice) LedgerNavy else EmeraldAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = op.dateStr,
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }

                                androidx.compose.material3.VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(28.dp))

                                // Subtitle / Details Col
                                Box(
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = op.subtitle,
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 11.sp,
                                        color = androidx.compose.ui.graphics.Color(0xFF334155),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                androidx.compose.material3.VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(28.dp))

                                // Amount Col
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = if (op.isInvoice) "مدين" else "دائن",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (op.isInvoice) LedgerCrimson else EmeraldAccent,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${NumberFormatUtils.formatNumber(op.amount)} ر",
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (op.isInvoice) LedgerCrimson else EmeraldAccent,
                                        maxLines = 1
                                    )
                                }

                                androidx.compose.material3.VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(28.dp))

                                // Action Icons Col (Edit, WhatsApp, Print)
                                Row(
                                    modifier = Modifier.width(86.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Edit
                                    IconButton(
                                        onClick = {
                                            if (op is LedgerOpItem.InvoiceOp) {
                                                onEditInvoice(op.invoice)
                                            } else if (op is LedgerOpItem.PaymentOp) {
                                                onEditPayment(op.payment)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "تعديل",
                                            tint = LedgerNavy,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    // 2. WhatsApp
                                    IconButton(
                                        onClick = {
                                            val text = if (op is LedgerOpItem.InvoiceOp) {
                                                ShareHelper.formatInvoiceWhatsAppText(op.invoice)
                                            } else {
                                                "سند سداد لـ ${contact.name}\nالمبلغ: ${NumberFormatUtils.formatNumber(op.amount)} ريال\nالتاريخ: ${op.dateStr}\n${op.notes}"
                                            }
                                            ShareHelper.shareTextViaWhatsApp(context, text, contact.phone)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = "واتساب",
                                            tint = EmeraldAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    // 3. Print (if invoice) or Statement
                                    if (op is LedgerOpItem.InvoiceOp) {
                                        IconButton(
                                            onClick = { onOpenReceipt(op.invoice) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Print,
                                                contentDescription = "طباعة",
                                                tint = LedgerNavy,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.6.dp, color = LedgerNavy.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }

        // 4. Multi-Select Bottom Actions Bar
        if (selectedIds.isNotEmpty()) {
            val selectedOps = allOps.filter { selectedIds.contains(it.id) }
            val selectedTotal = selectedOps.sumOf { it.amount }

            androidx.compose.material3.Surface(
                color = LedgerNavy,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "المحدد: ${selectedIds.size} عملية",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Text(
                            text = "إجمالي المحدد: ${NumberFormatUtils.formatNumber(selectedTotal)} ريال",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val summaryText = buildString {
                                    appendLine("*كشف عمليات ${contact.name} (${selectedOps.size} عملية محددة)*")
                                    appendLine("إجمالي العمليات المحددة: ${NumberFormatUtils.formatNumber(selectedTotal)} ريال")
                                    appendLine("الرصيد الصافي للحساب: ${NumberFormatUtils.formatNumber(contact.balance)} ريال")
                                    appendLine("──────────────────")
                                    selectedOps.forEach { op ->
                                        appendLine("• ${op.title} : ${NumberFormatUtils.formatNumber(op.amount)} ر (${op.dateStr})")
                                    }
                                }
                                ShareHelper.shareTextViaWhatsApp(context, summaryText, contact.phone)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة المحدد", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { selectedIds = emptySet() },
                            shape = RoundedCornerShape(8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                                contentColor = androidx.compose.ui.graphics.Color.White
                            )
                        ) {
                            Text("إلغاء", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun BalanceSummaryCard(
    title: String,
    amount: Double,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${NumberFormatUtils.formatNumber(amount)} ر",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContactLedgerCard(
    contact: Contact,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    onOpenLedgerOperations: () -> Unit,
    onShareWhatsApp4K: () -> Unit
) {
    Card(
        onClick = onEditClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_card_${contact.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val infoParts = listOfNotNull(
                            contact.phone.takeIf { it.isNotBlank() },
                            contact.notes.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (infoParts.isNotBlank()) {
                            Text(
                                text = infoParts,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "الرصيد: ${NumberFormatUtils.formatNumber(contact.balance)} ر",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (contact.balance > 0) MaterialTheme.colorScheme.primary else EmeraldAccent
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_contact_${contact.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "تعديل ${contact.name}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_contact_${contact.id}")
                    ) {
                        Icon(
                            imageVector = AppIcons.DeleteOutline,
                            contentDescription = "حذف ${contact.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onRecordPaymentClick,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(imageVector = AppIcons.Payments, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("سداد", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onOpenLedgerOperations,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .height(48.dp)
                        .testTag("contact_operations_button_${contact.id}")
                ) {
                    Icon(imageVector = AppIcons.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("العمليات", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onShareWhatsApp4K,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("كشف 4K", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ContactFormDialog(
    existingContact: Contact?,
    initialType: ContactType,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, ContactType, String, Double, String) -> Unit
) {
    var name by remember(existingContact?.id) { mutableStateOf(existingContact?.name ?: "") }
    var type by remember(existingContact?.id) { mutableStateOf(existingContact?.resolvedType ?: initialType) }
    var phone by remember(existingContact?.id) { mutableStateOf(existingContact?.phone ?: "") }
    var balanceText by remember(existingContact?.id) {
        mutableStateOf(
            if (existingContact != null) NumberFormatUtils.formatInputNumber(existingContact.balance, 2) else "0"
        )
    }
    var notes by remember(existingContact?.id) { mutableStateOf(existingContact?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

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
                    text = if (existingContact == null) "إضافة حساب جديد" else "تعديل بيانات الحساب",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = type == ContactType.CUSTOMER,
                        onClick = { type = ContactType.CUSTOMER },
                        label = { Text("عميل") }
                    )
                    FilterChip(
                        selected = type == ContactType.SUPPLIER,
                        onClick = { type = ContactType.SUPPLIER },
                        label = { Text("مورد") }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = NumberFormatUtils.toEnglishDigits(it) },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    LtrDecimalField(
                        value = balanceText,
                        onValueChange = { balanceText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = if (existingContact == null) "الرصيد الافتتاحي" else "الرصيد الحالي",
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            if (name.trim().isEmpty()) {
                                error = "يرجى إدخال الاسم"
                                return@Button
                            }
                            val balance = NumberFormatUtils.parseDecimal(balanceText) ?: 0.0
                            onSave(name.trim(), type, phone.trim(), balance, notes.trim())
                        }
                    ) {
                        Text(if (existingContact == null) "حفظ" else "حفظ التعديلات")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordPaymentDialog(
    contact: Contact,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "سند سداد - ${contact.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "الرصيد الحالي: ${NumberFormatUtils.formatNumber(contact.balance)} ريال",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtrDecimalField(
                        value = amountText,
                        onValueChange = { amountText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = "المبلغ *",
                        suffixText = "ر",
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("البيان") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            val amount = NumberFormatUtils.parseDecimal(amountText)
                            if (amount == null || amount <= 0.0) {
                                error = "يرجى إدخال مبلغ أكبر من صفر"
                                return@Button
                            }
                            onConfirm(amount, notes.trim())
                        }
                    ) {
                        Text("اعتماد السند")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPaymentDialog(
    payment: Payment,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amountText by remember(payment.id) {
        mutableStateOf(NumberFormatUtils.formatInputNumber(payment.amount, 2))
    }
    var notes by remember(payment.id) { mutableStateOf(payment.notes) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "تعديل سند - ${payment.contactName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LtrDecimalField(
                        value = amountText,
                        onValueChange = { amountText = NumberFormatUtils.sanitizeDecimalInput(it) },
                        label = "المبلغ *",
                        suffixText = "ر",
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("البيان") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            val amount = NumberFormatUtils.parseDecimal(amountText)
                            if (amount == null || amount <= 0.0) {
                                error = "يرجى إدخال مبلغ أكبر من صفر"
                                return@Button
                            }
                            onConfirm(amount, notes.trim())
                        }
                    ) {
                        Text("حفظ التعديل")
                    }
                }
            }
        }
    }
}


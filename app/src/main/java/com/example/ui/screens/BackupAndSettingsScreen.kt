package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.credentials.CredentialManager
import com.example.auth.onGoogleSignInClicked
import com.example.data.BackupBundle
import com.example.data.BackupJsonCodec
import com.example.data.RestoreSummary
import com.example.domain.Contact
import com.example.domain.Invoice
import com.example.domain.InvoiceLine
import com.example.domain.InvoiceType
import com.example.domain.NumberFormatUtils
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.UiState
import com.example.export.ReceiptRenderer4K
import com.example.export.ShareHelper
import com.example.print.EscPosPrinter
import com.example.print.PairedPrinterDevice
import com.example.print.PrinterSettingsStore
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.LedgerNavy
import com.example.ui.theme.OnEmeraldContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupAndSettingsScreen(
    isCloudMode: Boolean,
    userDisplayName: String,
    productsState: UiState<List<Product>>,
    contactsState: UiState<List<Contact>> = UiState.Success(emptyList()),
    invoicesState: UiState<List<Invoice>> = UiState.Success(emptyList()),
    paymentsState: UiState<List<Payment>> = UiState.Success(emptyList()),
    onExportBackupJson: suspend () -> Result<String>,
    onImportBackupBundle: suspend (BackupBundle) -> Result<RestoreSummary>,
    onGoogleLinkedSuccess: () -> Unit,
    onSignOutToLocal: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val initialSettings = remember { PrinterSettingsStore.load(context) }
    var storeName by remember { mutableStateOf(initialSettings.storeName) }
    var storeSubtitle by remember { mutableStateOf(initialSettings.storeSubtitle) }
    var storePhone by remember { mutableStateOf(initialSettings.storePhone) }
    var storeBadgeText by remember { mutableStateOf(initialSettings.storeBadgeText) }
    var footerPolicy by remember { mutableStateOf(initialSettings.footerPolicy) }
    var paperWidthMm by remember { mutableIntStateOf(initialSettings.paperWidthMm) }
    var wifiHost by remember { mutableStateOf(initialSettings.wifiHost) }
    var wifiPortText by remember { mutableStateOf(initialSettings.wifiPort.toString()) }
    var btName by remember { mutableStateOf(initialSettings.bluetoothName) }
    var btAddress by remember { mutableStateOf(initialSettings.bluetoothAddress) }
    var pairedDevices by remember { mutableStateOf<List<PairedPrinterDevice>>(emptyList()) }
    var isLinkingGoogle by remember { mutableStateOf(false) }

    var pendingRestoreBundle by remember { mutableStateOf<BackupBundle?>(null) }

    val products = (productsState as? UiState.Success)?.data ?: emptyList()
    val contacts = (contactsState as? UiState.Success)?.data ?: emptyList()
    val invoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onExportBackupJson().onSuccess { json ->
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                            out.flush()
                        } ?: throw IllegalStateException("تعذر فتح الملف للكتابة")
                    }
                    onShowMessage("تم حفظ النسخة الاحتياطية بنجاح")
                }.onFailure { err ->
                    onShowMessage(err.localizedMessage ?: "تعذر تصدير النسخة الاحتياطية")
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                            it.readText()
                        } ?: throw IllegalStateException("تعذر قراءة ملف النسخة الاحتياطية")
                    }
                    BackupJsonCodec.decode(json)
                }.onSuccess { decoded ->
                    pendingRestoreBundle = decoded
                }.onFailure { err ->
                    onShowMessage("ملف النسخة الاحتياطية غير صالح: ${err.localizedMessage ?: ""}")
                }
            }
        }
    }

    val exportInventoryPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    ReceiptRenderer4K.writeInventoryPdfToUri(context, products, uri, storeName)
                }.onSuccess {
                    onShowMessage("تم حفظ تقرير جرد المخزون PDF بنجاح")
                }.onFailure {
                    onShowMessage(it.localizedMessage ?: "تعذر حفظ ملف PDF")
                }
            }
        }
    }

    val btPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            EscPosPrinter.listPairedBluetoothPrinters(context)
                .onSuccess { pairedDevices = it }
                .onFailure { onShowMessage(it.localizedMessage ?: "تعذر جلب طابعات البلوتوث") }
        } else {
            onShowMessage("لم يتم منح صلاحية البلوتوث")
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        var settingsSection by remember { mutableIntStateOf(0) }

        Column(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = settingsSection == 0,
                    onClick = { settingsSection = 0 },
                    label = { Text("الكل", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = settingsSection == 1,
                    onClick = { settingsSection = 1 },
                    label = { Text("المتجر والطابعة", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = settingsSection == 2,
                    onClick = { settingsSection = 2 },
                    label = { Text("النسخ والمزامنة", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = settingsSection == 3,
                    onClick = { settingsSection = 3 },
                    label = { Text("تشخيص النظام", fontSize = 11.sp) }
                )
            }

            if (settingsSection == 0 || settingsSection == 1) {
                // 1. Store Identity & Header Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "هوية المتجر والإيصال المتقدم",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = storeName,
                            onValueChange = { storeName = it },
                            label = { Text("اسم المتجر") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = storeBadgeText,
                            onValueChange = { storeBadgeText = it.take(4) },
                            label = { Text("الرمز") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = storeSubtitle,
                            onValueChange = { storeSubtitle = it },
                            label = { Text("النشاط (مثل: للتجارة العامة)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = storePhone,
                            onValueChange = { storePhone = NumberFormatUtils.toEnglishDigits(it) },
                            label = { Text("رقم الهاتف") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = footerPolicy,
                        onValueChange = { footerPolicy = it },
                        label = { Text("عبارة تذييل الفاتورة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. Thermal Printer Settings & Test Print Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "إعدادات الطابعة الحرارية واتصال البلوتوث والشبكة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text("مقاس ورق الطباعة:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = paperWidthMm == 58,
                            onClick = { paperWidthMm = 58 },
                            label = { Text("58 مم") }
                        )
                        FilterChip(
                            selected = paperWidthMm == 80,
                            onClick = { paperWidthMm = 80 },
                            label = { Text("80 مم") }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (btAddress.isNotBlank()) "الطابعة المقترنة: $btName" else "الطابعة المقترنة: غير محددة",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (btAddress.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    btAddress = ""
                                    btName = ""
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "إزالة الطابعة",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                if (EscPosPrinter.hasBluetoothPermissions(context)) {
                                    EscPosPrinter.listPairedBluetoothPrinters(context)
                                        .onSuccess { pairedDevices = it }
                                        .onFailure { onShowMessage(it.localizedMessage ?: "تعذر جلب الطابعات") }
                                } else {
                                    btPermLauncher.launch(EscPosPrinter.requiredBluetoothPermissions())
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("جلب البلوتوث")
                        }
                    }

                    if (pairedDevices.isNotEmpty()) {
                        pairedDevices.forEach { dev ->
                            FilterChip(
                                selected = btAddress == dev.address,
                                onClick = {
                                    btAddress = dev.address
                                    btName = dev.name
                                },
                                label = { Text("${dev.name} (${dev.address})") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = wifiHost,
                            onValueChange = { wifiHost = NumberFormatUtils.toEnglishDigits(it) },
                            label = { Text("IP الطابعة الشبكية") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = wifiPortText,
                            onValueChange = { wifiPortText = NumberFormatUtils.toEnglishDigits(it).filter { ch -> ch.isDigit() } },
                            label = { Text("المنفذ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val settings = PrinterSettingsStore.load(context).copy(
                                            storeName = storeName,
                                            storeSubtitle = storeSubtitle,
                                            storePhone = storePhone,
                                            storeBadgeText = storeBadgeText,
                                            footerPolicy = footerPolicy,
                                            paperWidthMm = paperWidthMm,
                                            wifiHost = wifiHost,
                                            wifiPort = wifiPortText.toIntOrNull() ?: 9100,
                                            bluetoothName = btName,
                                            bluetoothAddress = btAddress
                                        )
                                        val testBmp = ReceiptRenderer4K.renderInvoiceReceipt4K(
                                            context = context,
                                            invoice = Invoice(
                                                id = "test",
                                                invoiceNumber = "TEST-01",
                                                invoiceType = InvoiceType.SALE.name,
                                                paymentMethod = PaymentMethod.CASH.name,
                                                contactName = "اختبار الطابعة",
                                                totalAmount = 1500.0,
                                                lines = listOf(
                                                    InvoiceLine(
                                                        productId = "p1",
                                                        productName = "صنف تجريبي",
                                                        unitName = "حبة",
                                                        cumulativeFactor = 1.0,
                                                        unitQty = 2.0,
                                                        unitPrice = 750.0,
                                                        baseQty = 2.0,
                                                        lineTotal = 1500.0
                                                    )
                                                )
                                            ),
                                            storeName = storeName,
                                            settingsOverride = settings
                                        )
                                        if (btAddress.isNotBlank() && EscPosPrinter.hasBluetoothPermissions(context)) {
                                            EscPosPrinter.printViaBluetoothSpp(context, btAddress, testBmp).getOrThrow()
                                        } else if (wifiHost.isNotBlank()) {
                                            EscPosPrinter.printViaTcpIp(wifiHost, wifiPortText.toIntOrNull() ?: 9100, testBmp).getOrThrow()
                                        } else {
                                            throw IllegalStateException("يرجى ربط طابعة بلوتوث أو إدخال IP الطابعة الشبكية للطباعة")
                                        }
                                    }.onSuccess {
                                        onShowMessage("تم طباعة صفحة الاختبار بنجاح")
                                    }.onFailure { err ->
                                        onShowMessage(err.localizedMessage ?: "تعذرت طباعة صفحة الاختبار")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("طباعة اختبار", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                PrinterSettingsStore.save(
                                    context,
                                    initialSettings.copy(
                                        storeName = storeName,
                                        storeSubtitle = storeSubtitle,
                                        storePhone = storePhone,
                                        storeBadgeText = storeBadgeText,
                                        footerPolicy = footerPolicy,
                                        paperWidthMm = paperWidthMm,
                                        wifiHost = wifiHost,
                                        wifiPort = wifiPortText.toIntOrNull() ?: 9100,
                                        bluetoothName = btName,
                                        bluetoothAddress = btAddress
                                    )
                                )
                                onShowMessage("تم حفظ إعدادات الطابعة بنجاح")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LedgerNavy),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حفظ الإعدادات", maxLines = 1)
                        }
                    }
                }
            }
        }

        if (settingsSection == 0 || settingsSection == 2) {
            // 3. Cloud Sync & Google Account Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCloudMode) Icons.Filled.CloudDone else Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = if (isCloudMode) EmeraldAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "حساب Google والمزامنة السحابية",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = if (isCloudMode) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isCloudMode) "م مربوط بحساب Google ($userDisplayName)" else "الحالة: الوضع المحلي (بدون حساب)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCloudMode) OnEmeraldContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isCloudMode) "تتم مزامنة بياناتك سحابياً بأمان تام." else "تعمل البيانات محلياً على جهازك. يمكنك ربط الحساب بالمزامنة السحابية متى شئت.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCloudMode) OnEmeraldContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isCloudMode) {
                        Button(
                            enabled = !isLinkingGoogle,
                            onClick = {
                                if (context !is Activity) {
                                    onShowMessage("تعذر بدء نافذة ربط Google")
                                    return@Button
                                }
                                isLinkingGoogle = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isLinkingGoogle = false
                                        onGoogleLinkedSuccess()
                                    },
                                    onAuthError = { msg ->
                                        isLinkingGoogle = false
                                        onShowMessage(msg)
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isLinkingGoogle = false
                                        onShowMessage("تم إلغاء ربط حساب Google")
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("link_google_account_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isLinkingGoogle) "جاري الربط..." else "ربط حساب Google ومزامنة البيانات")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onSignOutToLocal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("switch_to_local_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فصل حساب Google والعودة للوضع المحلي")
                        }
                    }
                }
            }

            // 4. Backup, Restore & File Exports Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "النسخ الاحتياطي وحفظ الملفات",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val stamp = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { createBackupLauncher.launch("warehouse_backup_$stamp.json") },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("export_backup_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("تصدير نسخة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("restore_backup_button")
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("استعادة نسخة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    onExportBackupJson().onSuccess { json ->
                                        ShareHelper.shareBackupJson(context, json, "warehouse_backup_$stamp.json")
                                            .onFailure { onShowMessage(it.localizedMessage ?: "تعذرت المشاركة") }
                                    }.onFailure {
                                        onShowMessage(it.localizedMessage ?: "تعذر تجهيز النسخة")
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("مشاركة JSON", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { exportInventoryPdfLauncher.launch("inventory_report_$stamp.pdf") },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("export_inventory_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("تقرير جرد PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (settingsSection == 0 || settingsSection == 3) {
            // 5. Advanced System Diagnostics Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "الإعدادات المتقدمة وإحصائيات النظام",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("• الأصناف المسجلة: ${products.size}", style = MaterialTheme.typography.bodySmall)
                            Text("• الحسابات (عملاء وموردين): ${contacts.size}", style = MaterialTheme.typography.bodySmall)
                            Text("• الفواتير المحفوظة: ${invoices.size}", style = MaterialTheme.typography.bodySmall)
                            Text("• سندات السداد: ${payments.size}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        }
    }

    pendingRestoreBundle?.let { bundle ->
        AlertDialog(
            onDismissRequest = { pendingRestoreBundle = null },
            title = { Text("تأكيد استعادة النسخة الاحتياطية") },
            text = {
                Text(
                    "تحتوي النسخة على:\n" +
                        "• ${bundle.products.size} صنف\n" +
                        "• ${bundle.contacts.size} حساب\n" +
                        "• ${bundle.invoices.size} فاتورة\n" +
                        "• ${bundle.payments.size} سند سداد\n\n" +
                        "سيتم دمج البيانات مع تخطي أي عناصر مكررة بالاسم أو الباركود."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = bundle
                        pendingRestoreBundle = null
                        scope.launch {
                            onImportBackupBundle(target).onSuccess { summary ->
                                onShowMessage(
                                    "تمت الاستعادة: +${summary.importedProducts} صنف، +${summary.importedContacts} حساب، +${summary.importedInvoices} فاتورة (تم تخطي ${summary.skippedDuplicateProducts} مكرر)"
                                )
                            }.onFailure { err ->
                                onShowMessage(err.localizedMessage ?: "تعذرت استعادة النسخة الاحتياطية")
                            }
                        }
                    }
                ) {
                    Text("استعادة الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreBundle = null }) { Text("إلغاء") }
            }
        )
    }
}

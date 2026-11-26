package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.Invoice
import com.example.domain.NumberFormatUtils
import com.example.export.ReceiptRenderer4K
import com.example.export.ShareHelper
import com.example.print.EscPosPrinter
import com.example.print.PairedPrinterDevice
import com.example.print.PrinterSettingsStore
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.OnEmeraldContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReceiptActionDialog(
    invoice: Invoice,
    contactPhone: String? = null,
    onDismiss: () -> Unit,
    onFeedbackMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var printerSettings by remember { mutableStateOf(PrinterSettingsStore.load(context)) }
    var receiptBitmap4K by remember { mutableStateOf<Bitmap?>(null) }
    var thermalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewThermalMode by remember { mutableStateOf(false) }
    var previewPaperWidthMm by remember { mutableIntStateOf(printerSettings.paperWidthMm) }
    var showThermalSheet by remember { mutableStateOf(false) }

    LaunchedEffect(invoice.id, printerSettings.storeName) {
        receiptBitmap4K = withContext(Dispatchers.Default) {
            ReceiptRenderer4K.renderInvoiceReceipt4K(
                context = context,
                invoice = invoice,
                storeName = printerSettings.storeName
            )
        }
    }

    LaunchedEffect(invoice.id, printerSettings.storeName, previewPaperWidthMm) {
        val widthPx = if (previewPaperWidthMm == 58) {
            ReceiptRenderer4K.THERMAL_58MM_PX
        } else {
            ReceiptRenderer4K.THERMAL_80MM_PX
        }
        thermalBitmap = withContext(Dispatchers.Default) {
            ReceiptRenderer4K.renderThermalReceiptBitmap(
                context = context,
                invoice = invoice,
                widthPx = widthPx,
                storeName = printerSettings.storeName
            )
        }
    }

    val activePreviewBitmap = if (previewThermalMode) thermalBitmap else receiptBitmap4K

    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = activePreviewBitmap ?: withContext(Dispatchers.Default) {
                    if (previewThermalMode) {
                        val w = if (previewPaperWidthMm == 58) ReceiptRenderer4K.THERMAL_58MM_PX else ReceiptRenderer4K.THERMAL_80MM_PX
                        ReceiptRenderer4K.renderThermalReceiptBitmap(context, invoice, w, printerSettings.storeName)
                    } else {
                        ReceiptRenderer4K.renderInvoiceReceipt4K(context, invoice, printerSettings.storeName)
                    }
                }
                withContext(Dispatchers.IO) {
                    ReceiptRenderer4K.writeBitmapPngToUri(context, bmp, uri)
                }.onSuccess {
                    val label = if (previewThermalMode) "الإيصال الحراري (${previewPaperWidthMm} مم)" else "الإيصال بدقة 4K"
                    onFeedbackMessage("تم حفظ صورة $label بنجاح")
                }.onFailure {
                    onFeedbackMessage(it.localizedMessage ?: "تعذر حفظ صورة الإيصال")
                }
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    ReceiptRenderer4K.writeInvoicePdfToUri(context, invoice, uri, printerSettings.storeName)
                }.onSuccess {
                    onFeedbackMessage("تم حفظ ملف الفاتورة PDF بدقة عالية")
                }.onFailure {
                    onFeedbackMessage(it.localizedMessage ?: "تعذر حفظ ملف PDF")
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 760.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "إيصال ${invoice.invoiceNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (previewThermalMode) "حراري ${previewPaperWidthMm}مم" else "4K UHD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnEmeraldContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        val modeBadge = if (invoice.resolvedMode == com.example.domain.InvoiceMode.FREE) "حرة" else "من المخزن"
                        val summaryLine = buildString {
                            append("$modeBadge • الإجمالي: ${NumberFormatUtils.formatNumber(invoice.totalAmount)} ر")
                            if (invoice.resolvedRemainingAmount > 0.0001) {
                                append(" • متبقي: ${NumberFormatUtils.formatNumber(invoice.resolvedRemainingAmount)} ر")
                            }
                        }
                        Text(
                            text = summaryLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                // Mode Switcher: 4K Paper Invoice vs Thermal Receipt (58mm / 80mm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !previewThermalMode,
                        onClick = { previewThermalMode = false },
                        label = { Text("فاتورة 4K") },
                        modifier = Modifier.testTag("preview_mode_4k_chip")
                    )
                    FilterChip(
                        selected = previewThermalMode,
                        onClick = { previewThermalMode = true },
                        label = { Text("إيصال حراري") },
                        modifier = Modifier.testTag("preview_mode_thermal_chip")
                    )
                    if (previewThermalMode) {
                        Spacer(modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = previewPaperWidthMm == 58,
                            onClick = {
                                previewPaperWidthMm = 58
                                val updated = printerSettings.copy(paperWidthMm = 58)
                                printerSettings = updated
                                PrinterSettingsStore.save(context, updated)
                            },
                            label = { Text("58 مم") }
                        )
                        FilterChip(
                            selected = previewPaperWidthMm == 80,
                            onClick = {
                                previewPaperWidthMm = 80
                                val updated = printerSettings.copy(paperWidthMm = 80)
                                printerSettings = updated
                                PrinterSettingsStore.save(context, updated)
                            },
                            label = { Text("80 مم") }
                        )
                    }
                }

                // 4K / Thermal Receipt Preview Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 210.dp, max = 330.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = activePreviewBitmap
                    if (bmp == null) {
                        CircularProgressIndicator()
                    } else if (previewThermalMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Surface(
                                color = Color.White,
                                shadowElevation = 3.dp,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth(if (previewPaperWidthMm == 58) 0.68f else 0.86f)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "معاينة الإيصال الحراري",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "معاينة إيصال الفاتورة بدقة 4K",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // WhatsApp Image & Text Sharing
                Button(
                    onClick = {
                        scope.launch {
                            val bmp = activePreviewBitmap ?: withContext(Dispatchers.Default) {
                                if (previewThermalMode) {
                                    val w = if (previewPaperWidthMm == 58) ReceiptRenderer4K.THERMAL_58MM_PX else ReceiptRenderer4K.THERMAL_80MM_PX
                                    ReceiptRenderer4K.renderThermalReceiptBitmap(context, invoice, w, printerSettings.storeName)
                                } else {
                                    ReceiptRenderer4K.renderInvoiceReceipt4K(context, invoice, printerSettings.storeName)
                                }
                            }
                            val prefix = if (previewThermalMode) "thermal_${invoice.invoiceNumber}" else "receipt_4k_${invoice.invoiceNumber}"
                            val uri = withContext(Dispatchers.IO) {
                                ReceiptRenderer4K.saveBitmapToShareCachePng(
                                    context = context,
                                    bitmap = bmp,
                                    prefix = prefix
                                )
                            }
                            val caption = ShareHelper.formatInvoiceWhatsAppText(invoice, printerSettings.storeName)
                            ShareHelper.shareImage4KViaWhatsApp(context, uri, caption)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("share_whatsapp_4k_button")
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (previewThermalMode) "مشاركة واتساب (إيصال حراري)" else "مشاركة واتساب (صورة إيصال 4K)",
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val text = ShareHelper.formatInvoiceWhatsAppText(invoice, printerSettings.storeName)
                            ShareHelper.shareTextViaWhatsApp(context, text, directPhone = contactPhone)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_whatsapp_text_button")
                    ) {
                        Text("واتساب (نص)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { showThermalSheet = true },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("thermal_print_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طباعة حرارية", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val filePrefix = if (previewThermalMode) "thermal_${previewPaperWidthMm}mm" else "receipt_4k"
                            savePngLauncher.launch("${filePrefix}_${invoice.invoiceNumber}.png")
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_receipt_4k_png_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (previewThermalMode) "حفظ حراري PNG" else "حفظ 4K PNG",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { savePdfLauncher.launch("invoice_${invoice.invoiceNumber}.pdf") },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_receipt_pdf_button")
                    ) {
                        Icon(imageVector = Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val bmp = receiptBitmap4K ?: withContext(Dispatchers.Default) {
                                ReceiptRenderer4K.renderInvoiceReceipt4K(context, invoice, printerSettings.storeName)
                            }
                            try {
                                EscPosPrinter.printViaSystemPrintManager(
                                    context = context,
                                    jobName = "فاتورة_${invoice.invoiceNumber}",
                                    bitmap4K = bmp
                                )
                            } catch (e: Exception) {
                                onFeedbackMessage(e.localizedMessage ?: "تعذر فتح خدمة طباعة النظام")
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("system_print_direct_button")
                ) {
                    Icon(imageVector = Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("طباعة النظام / USB / Wi-Fi Direct (4K)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    if (showThermalSheet) {
        ThermalPrintDialog(
            invoice = invoice,
            bitmap4K = receiptBitmap4K,
            onDismiss = {
                showThermalSheet = false
                printerSettings = PrinterSettingsStore.load(context)
                previewPaperWidthMm = printerSettings.paperWidthMm
            },
            onStatus = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onFeedbackMessage(msg)
            }
        )
    }
}

@Composable
private fun ThermalPrintDialog(
    invoice: Invoice,
    bitmap4K: Bitmap?,
    onDismiss: () -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { PrinterSettingsStore.load(context) }

    var paperWidthMm by remember { mutableIntStateOf(saved.paperWidthMm) }
    var wifiHost by remember { mutableStateOf(saved.wifiHost) }
    var wifiPortText by remember { mutableStateOf(saved.wifiPort.toString()) }
    var selectedBtAddress by remember { mutableStateOf(saved.bluetoothAddress) }
    var selectedBtName by remember { mutableStateOf(saved.bluetoothName) }
    var pairedDevices by remember { mutableStateOf<List<PairedPrinterDevice>>(emptyList()) }
    var btStatusMsg by remember { mutableStateOf<String?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var thermalPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(invoice.id, paperWidthMm, saved.storeName) {
        val widthPx = if (paperWidthMm == 58) ReceiptRenderer4K.THERMAL_58MM_PX else ReceiptRenderer4K.THERMAL_80MM_PX
        thermalPreviewBitmap = withContext(Dispatchers.Default) {
            ReceiptRenderer4K.renderThermalReceiptBitmap(
                context = context,
                invoice = invoice,
                widthPx = widthPx,
                storeName = saved.storeName
            )
        }
    }

    val refreshPairedDevices = {
        EscPosPrinter.listPairedBluetoothPrinters(context)
            .onSuccess { list ->
                pairedDevices = list
                btStatusMsg = if (list.isEmpty()) "لا توجد طابعات بلوتوث مقترنة بالهاتف" else null
                if (selectedBtAddress.isBlank() && list.isNotEmpty()) {
                    selectedBtAddress = list.first().address
                    selectedBtName = list.first().name
                }
            }
            .onFailure { err ->
                btStatusMsg = err.localizedMessage
            }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            refreshPairedDevices()
        } else {
            btStatusMsg = "تم رفض صلاحية البلوتوث. يمكنك الطباعة عبر Wi-Fi أو طابعة النظام."
        }
    }

    LaunchedEffect(Unit) {
        if (EscPosPrinter.hasBluetoothPermissions(context)) {
            refreshPairedDevices()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الطباعة الحرارية والنظام") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("عرض الورق الحراري:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = paperWidthMm == 58,
                        onClick = {
                            paperWidthMm = 58
                            PrinterSettingsStore.save(context, saved.copy(paperWidthMm = 58))
                        },
                        label = { Text("58 مم (384px)") }
                    )
                    FilterChip(
                        selected = paperWidthMm == 80,
                        onClick = {
                            paperWidthMm = 80
                            PrinterSettingsStore.save(context, saved.copy(paperWidthMm = 80))
                        },
                        label = { Text("80 مم (576px)") }
                    )
                }

                // Live compact thermal roll preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = thermalPreviewBitmap
                    if (bmp == null) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "معاينة الإيصال الحراري المصغرة",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth(if (paperWidthMm == 58) 0.65f else 0.85f)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. طابعة بلوتوث ESC/POS", style = MaterialTheme.typography.titleSmall)
                    if (EscPosPrinter.hasBluetoothPermissions(context)) {
                        TextButton(onClick = { refreshPairedDevices() }) {
                            Text("تحديث")
                        }
                    }
                }
                if (!EscPosPrinter.hasBluetoothPermissions(context)) {
                    OutlinedButton(
                        onClick = {
                            btPermissionLauncher.launch(EscPosPrinter.requiredBluetoothPermissions())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("منح صلاحية البلوتوث وجلب الطابعات")
                    }
                } else {
                    if (pairedDevices.isNotEmpty()) {
                        pairedDevices.forEach { dev ->
                            FilterChip(
                                selected = selectedBtAddress == dev.address,
                                onClick = {
                                    selectedBtAddress = dev.address
                                    selectedBtName = dev.name
                                },
                                label = { Text("${dev.name} (${dev.address})") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    btStatusMsg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        enabled = !isPrinting && selectedBtAddress.isNotBlank(),
                        onClick = {
                            val updated = saved.copy(
                                paperWidthMm = paperWidthMm,
                                bluetoothAddress = selectedBtAddress,
                                bluetoothName = selectedBtName
                            )
                            PrinterSettingsStore.save(context, updated)
                            isPrinting = true
                            scope.launch {
                                val widthPx = if (paperWidthMm == 58) {
                                    ReceiptRenderer4K.THERMAL_58MM_PX
                                } else {
                                    ReceiptRenderer4K.THERMAL_80MM_PX
                                }
                                val thermalBmp = thermalPreviewBitmap ?: withContext(Dispatchers.Default) {
                                    ReceiptRenderer4K.renderThermalReceiptBitmap(
                                        context = context,
                                        invoice = invoice,
                                        widthPx = widthPx,
                                        storeName = updated.storeName
                                    )
                                }
                                EscPosPrinter.printViaBluetoothSpp(context, selectedBtAddress, thermalBmp)
                                    .onSuccess {
                                        onStatus("تم إرسال الإيصال للطابعة الحرارية بلوتوث بنجاح")
                                    }
                                    .onFailure { err ->
                                        onStatus(err.localizedMessage ?: "تعذرت الطباعة عبر بلوتوث")
                                    }
                                isPrinting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("طباعة عبر بلوتوث")
                    }
                }

                HorizontalDivider()

                Text("2. طابعة شبكة Wi-Fi / LAN (ESC/POS 9100)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wifiHost,
                        onValueChange = { wifiHost = NumberFormatUtils.toEnglishDigits(it) },
                        label = { Text("عنوان IP") },
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
                OutlinedButton(
                    enabled = !isPrinting && wifiHost.isNotBlank(),
                    onClick = {
                        val port = wifiPortText.toIntOrNull() ?: 9100
                        val updated = saved.copy(
                            paperWidthMm = paperWidthMm,
                            wifiHost = wifiHost,
                            wifiPort = port
                        )
                        PrinterSettingsStore.save(context, updated)
                        isPrinting = true
                        scope.launch {
                            val widthPx = if (paperWidthMm == 58) {
                                ReceiptRenderer4K.THERMAL_58MM_PX
                            } else {
                                ReceiptRenderer4K.THERMAL_80MM_PX
                            }
                            val thermalBmp = thermalPreviewBitmap ?: withContext(Dispatchers.Default) {
                                ReceiptRenderer4K.renderThermalReceiptBitmap(
                                    context = context,
                                    invoice = invoice,
                                    widthPx = widthPx,
                                    storeName = updated.storeName
                                )
                            }
                            EscPosPrinter.printViaTcpIp(wifiHost, port, thermalBmp)
                                .onSuccess {
                                    onStatus("تمت الطباعة الحرارية عبر الشبكة ($wifiHost:$port)")
                                }
                                .onFailure { err ->
                                    onStatus(err.localizedMessage ?: "تعذر الاتصال بالطابعة على $wifiHost:$port")
                                }
                            isPrinting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طباعة عبر الشبكة IP")
                }

                HorizontalDivider()

                OutlinedButton(
                    enabled = !isPrinting,
                    onClick = {
                        scope.launch {
                            val bmp = bitmap4K ?: withContext(Dispatchers.Default) {
                                ReceiptRenderer4K.renderInvoiceReceipt4K(context, invoice, saved.storeName)
                            }
                            try {
                                EscPosPrinter.printViaSystemPrintManager(
                                    context = context,
                                    jobName = "فاتورة_${invoice.invoiceNumber}",
                                    bitmap4K = bmp
                                )
                            } catch (e: Exception) {
                                onStatus(e.localizedMessage ?: "تعذر فتح خدمة طباعة النظام")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طباعة النظام / طابعة USB أو PDF (4K)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

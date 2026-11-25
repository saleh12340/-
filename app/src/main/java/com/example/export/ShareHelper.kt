package com.example.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.domain.Contact
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceType
import com.example.domain.NumberFormatUtils
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShareHelper {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun formatInvoiceWhatsAppText(
        invoice: Invoice,
        storeName: String = "حسابات المخزن"
    ): String = buildString {
        val isPurchase = invoice.resolvedType == InvoiceType.PURCHASE
        val typeLabel = if (isPurchase) "فاتورة شراء (جملة)" else "فاتورة بيع"
        val payLabel = if (invoice.resolvedPaymentMethod == PaymentMethod.CASH) "نقداً" else "آجل"
        val partyLabel = if (isPurchase) "المورد" else "العميل"
        val dt = dateFormatter.format(invoice.createdAt?.toDate() ?: Date())

        appendLine("*$storeName*")
        appendLine("*$typeLabel ($payLabel)*")
        appendLine("رقم الفاتورة: ${invoice.invoiceNumber}")
        appendLine("$partyLabel: ${invoice.contactName}")
        appendLine("التاريخ: $dt")
        appendLine("──────────────")
        invoice.lines.forEachIndexed { i, line ->
            appendLine("${i + 1}. *${line.productName}*")
            appendLine("   ${NumberFormatUtils.formatNumber(line.unitQty)} ${line.unitName} × ${NumberFormatUtils.formatNumber(line.unitPrice)} = *${NumberFormatUtils.formatNumber(line.lineTotal)} ريال*")
            appendLine("   (الكمية الأساسية: ${NumberFormatUtils.formatNumber(line.baseQty)})")
        }
        appendLine("──────────────")
        appendLine("*الإجمالي النهائي: ${NumberFormatUtils.formatNumber(invoice.totalAmount)} ريال*")
        if (invoice.notes.isNotBlank()) {
            appendLine("ملاحظات: ${invoice.notes}")
        }
    }

    fun formatContactStatementText(
        contact: Contact,
        invoices: List<Invoice>,
        payments: List<Payment>,
        storeName: String = "حسابات المخزن"
    ): String = buildString {
        val role = if (contact.resolvedType == ContactType.CUSTOMER) "عميل" else "مورد"
        appendLine("*$storeName - كشف حساب $role*")
        appendLine("الاسم: *${contact.name}*")
        if (contact.phone.isNotBlank()) appendLine("الهاتف: ${contact.phone}")
        appendLine("*الرصيد الحالي: ${NumberFormatUtils.formatNumber(contact.balance)} ريال*")
        val recentInv = invoices.filter { it.contactId == contact.id }.take(8)
        val recentPay = payments.filter { it.contactId == contact.id }.take(8)
        if (recentInv.isNotEmpty()) {
            appendLine("──────────────")
            appendLine("أحدث الفواتير:")
            recentInv.forEach { inv ->
                appendLine("• #${inv.invoiceNumber}: ${NumberFormatUtils.formatNumber(inv.totalAmount)} ريال")
            }
        }
        if (recentPay.isNotEmpty()) {
            appendLine("──────────────")
            appendLine("أحدث سندات السداد:")
            recentPay.forEach { pay ->
                appendLine("• سداد ${NumberFormatUtils.formatNumber(pay.amount)} ريال${if (pay.notes.isNotBlank()) " (${pay.notes})" else ""}")
            }
        }
        appendLine("──────────────")
        appendLine("التاريخ: ${dateFormatter.format(Date())}")
    }

    fun shareTextViaWhatsApp(
        context: Context,
        text: String,
        directPhone: String? = null
    ) {
        val normalizedPhone = directPhone?.filter { it.isDigit() }?.takeIf { it.length >= 7 }
        val targetPkg = findInstalledWhatsAppPackage(context)

        if (normalizedPhone != null && targetPkg != null) {
            val encoded = Uri.encode(text)
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalizedPhone?text=$encoded")).apply {
                setPackage(targetPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (waIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(waIntent)
                return
            }
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (targetPkg != null) setPackage(targetPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchWhatsAppOrChooser(context, sendIntent, "مشاركة عبر واتساب")
    }

    fun shareImage4KViaWhatsApp(
        context: Context,
        imageUri: Uri,
        captionText: String
    ) {
        val targetPkg = findInstalledWhatsAppPackage(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, captionText)
            clipData = ClipData.newRawUri("إيصال 4K", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (targetPkg != null) setPackage(targetPkg)
        }
        launchWhatsAppOrChooser(context, intent, "مشاركة إيصال 4K عبر واتساب")
    }

    fun shareBackupJson(
        context: Context,
        jsonContent: String,
        fileName: String
    ): Result<Unit> = runCatching {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(jsonContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        shareFileUri(context, uri, "application/json", "مشاركة النسخة الاحتياطية")
    }

    fun shareFileUri(
        context: Context,
        fileUri: Uri,
        mimeType: String,
        chooserTitle: String
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            clipData = ClipData.newRawUri(chooserTitle, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun launchWhatsAppOrChooser(context: Context, intent: Intent, chooserTitle: String) {
        try {
            if (intent.`package` != null) {
                val chooserSafe = Intent(intent).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(chooserSafe)
                return
            }
        } catch (_: Exception) {
            // Fall through to generic chooser without package restriction
        }
        val fallback = Intent(intent).apply {
            setPackage(null)
        }
        val chooser = Intent.createChooser(fallback, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun findInstalledWhatsAppPackage(context: Context): String? {
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        return candidates.firstOrNull { pkg ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pkg, 0)
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}

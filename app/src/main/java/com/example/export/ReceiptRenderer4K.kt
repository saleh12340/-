package com.example.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.Contact
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceType
import com.example.domain.NumberFormatUtils
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.print.PrinterSettings
import com.example.print.PrinterSettingsStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptRenderer4K {

    const val WIDTH_4K_PX = 2160
    const val THERMAL_58MM_PX = 384
    const val THERMAL_80MM_PX = 576

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private fun loadArabicTypeface(context: Context, bold: Boolean = false): Typeface {
        val base = try {
            ResourcesCompat.getFont(context, R.font.amiri)
                ?: ResourcesCompat.getFont(context, R.font.cairo)
                ?: ResourcesCompat.getFont(context, R.font.tajawal)
                ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }

    /**
     * Renders an ultra-crisp 4K (2160px wide) paper invoice receipt matching the classic
     * Arabic ledger layout (Navy header, AZ monogram, 4-column bordered table, red total & invoice pill).
     */
    fun renderInvoiceReceipt4K(
        context: Context,
        invoice: Invoice,
        storeName: String = "بقالة العزي",
        settingsOverride: PrinterSettings? = null
    ): Bitmap {
        val settings = settingsOverride ?: PrinterSettingsStore.load(context)
        val effectiveStoreName = storeName.ifBlank { settings.storeName }
        val width = WIDTH_4K_PX
        val outerPad = 56f
        val cardLeft = outerPad
        val cardRight = width - outerPad
        val cardInnerPad = 48f
        val innerLeft = cardLeft + cardInnerPad
        val innerRight = cardRight - cardInnerPad
        val innerWidth = innerRight - innerLeft

        val regularTf = loadArabicTypeface(context, bold = false)
        val boldTf = loadArabicTypeface(context, bold = true)

        val navy = Color.parseColor("#132C4A")
        val crimson = Color.parseColor("#C62828")
        val darkText = Color.parseColor("#0F172A")
        val slate = Color.parseColor("#475569")
        val canvasBg = Color.parseColor("#DCE4EC")

        val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 40f
            color = navy
            textAlign = Paint.Align.RIGHT
        }
        val headerLeftPaint = TextPaint(headerTextPaint).apply {
            textAlign = Paint.Align.LEFT
        }
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 36f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val bodyRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 36f
            color = darkText
            textAlign = Paint.Align.RIGHT
        }
        val bodyCenter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 34f
            color = darkText
            textAlign = Paint.Align.CENTER
        }
        val redBoldCenter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 40f
            color = crimson
            textAlign = Paint.Align.CENTER
        }
        val redBoldLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 44f
            color = crimson
            textAlign = Paint.Align.LEFT
        }
        val mutedCenter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 30f
            color = slate
            textAlign = Paint.Align.CENTER
        }

        val isPurchase = invoice.resolvedType == InvoiceType.PURCHASE
        val typeLabel = if (isPurchase) "فاتورة شراء (جملة)" else "فاتورة بيع"
        val isCash = invoice.resolvedPaymentMethod == PaymentMethod.CASH
        val dateText = dateFormatter.format(invoice.createdAt?.toDate() ?: Date())

        val lineCount = maxOf(1, invoice.lines.size)
        val rowHeight = 84f
        val tableHeaderH = 78f
        val summaryBoxH = 112f
        val notesH = if (invoice.notes.isNotBlank()) 76f else 0f

        val cardHeight = 406f + tableHeaderH + (lineCount * rowHeight) + summaryBoxH + notesH + 220f
        val totalHeight = (outerPad * 2 + cardHeight).toInt().coerceAtLeast(1380)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(canvasBg)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = navy
        }
        val dottedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = navy
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        // Paper Card
        val cardRect = RectF(cardLeft, outerPad, cardRight, outerPad + cardHeight)
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(cardRect, 44f, 44f, fillPaint)
        canvas.drawRoundRect(cardRect, 44f, 44f, borderPaint)

        var y = outerPad + 54f

        // 1. Header Row: Store Name + Phone (Right) | Circle Badge (Center) | Invoice Number & Date (Left)
        val rightStoreText = buildString {
            append(effectiveStoreName)
            if (settings.storePhone.isNotBlank()) {
                append("   ت: ")
                append(settings.storePhone)
            }
        }
        canvas.drawText(rightStoreText, innerRight, y + 66f, headerTextPaint)

        // Top-left corner: Invoice number pill on top, Date underneath
        val numPillRect = RectF(innerLeft, y - 4f, innerLeft + 240f, y + 58f)
        canvas.drawRoundRect(numPillRect, 18f, 18f, Paint(borderPaint).apply { strokeWidth = 3.5f })
        canvas.drawText(invoice.invoiceNumber.ifBlank { "0001" }, numPillRect.centerX(), numPillRect.centerY() + 13f, redBoldCenter)
        canvas.drawText("الرقم:", numPillRect.right + 18f, y + 40f, TextPaint(headerLeftPaint).apply { textSize = 34f })
        canvas.drawText(
            "التاريخ: $dateText",
            innerLeft,
            y + 102f,
            TextPaint(headerLeftPaint).apply {
                typeface = boldTf
                textSize = 31f
                color = darkText
            }
        )

        val badgeRadius = 46f
        fillPaint.color = navy
        canvas.drawCircle(width / 2f, y + 52f, badgeRadius, fillPaint)
        canvas.drawText(settings.storeBadgeText.ifBlank { "AZ" }, width / 2f, y + 65f, badgeTextPaint)

        y += 126f
        // Thin navy divider
        canvas.drawLine(innerLeft, y, innerRight, y, Paint(borderPaint).apply { strokeWidth = 3f })
        y += 38f

        // 2. Metadata Row 1: "المطلوب من الأخ/" + dotted customer field | "فاتورة بيع/شراء" pill + cash/credit radios
        val pillWidth = 230f
        val pillHeight = 64f
        val radioTextPaint = TextPaint(bodyRight).apply { textSize = 34f }

        // Radios on the left
        val creditCircleX = innerLeft + 24f
        val radioCenterY = y + 32f
        // Credit radio
        canvas.drawCircle(creditCircleX, radioCenterY, 16f, Paint(borderPaint).apply { strokeWidth = 3f })
        if (!isCash) {
            fillPaint.color = navy
            canvas.drawCircle(creditCircleX, radioCenterY, 9f, fillPaint)
        }
        canvas.drawText("آجل", creditCircleX + 26f, radioCenterY + 12f, TextPaint(radioTextPaint).apply { textAlign = Paint.Align.LEFT })

        val cashCircleX = innerLeft + 150f
        canvas.drawCircle(cashCircleX, radioCenterY, 16f, Paint(borderPaint).apply { strokeWidth = 3f })
        if (isCash) {
            fillPaint.color = navy
            canvas.drawCircle(cashCircleX, radioCenterY, 9f, fillPaint)
        }
        canvas.drawText("نقداً", cashCircleX + 26f, radioCenterY + 12f, TextPaint(radioTextPaint).apply { textAlign = Paint.Align.LEFT })

        // Invoice type dark navy pill
        val typePillRect = RectF(innerLeft + 285f, y, innerLeft + 285f + pillWidth, y + pillHeight)
        fillPaint.color = navy
        canvas.drawRoundRect(typePillRect, 20f, 20f, fillPaint)
        canvas.drawText(typeLabel, typePillRect.centerX(), typePillRect.centerY() + 12f, TextPaint(badgeTextPaint).apply { textSize = 32f })

        // Customer / Supplier label & dotted line on right
        val labelRightText = "المطلوب من الأخ/"
        canvas.drawText(labelRightText, innerRight, y + 44f, bodyRight)
        val partyStartRight = innerRight - bodyRight.measureText(labelRightText) - 24f
        val partyLineLeft = typePillRect.right + 28f
        canvas.drawLine(partyLineLeft, y + 54f, partyStartRight, y + 54f, dottedPaint)
        canvas.drawText(
            invoice.contactName.ifBlank { "عميل نقدي" },
            (partyLineLeft + partyStartRight) / 2f,
            y + 42f,
            TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 36f }
        )

        y += 90f
        canvas.drawLine(innerLeft, y, innerRight, y, dottedPaint)
        y += 32f

        // 4. 4-Column Bordered Table (RTL from Right: القيمة الإجمالية | الكمية | التفاصيل | سعر الواحدة faded)
        val colTotalW = 420f
        val colQtyW = 300f
        val colUnitPriceW = 240f
        val colDetailsW = innerWidth - colTotalW - colQtyW - colUnitPriceW

        val xUnitPriceLeft = innerLeft
        val xDetailsLeft = xUnitPriceLeft + colUnitPriceW
        val xQtyLeft = xDetailsLeft + colDetailsW
        val xTotalLeft = xQtyLeft + colQtyW
        // xTotalLeft + colTotalW == innerRight

        // Navy rounded-top header
        val headerRect = RectF(innerLeft, y, innerRight, y + tableHeaderH)
        fillPaint.color = navy
        canvas.drawRoundRect(headerRect, 22f, 22f, fillPaint)
        canvas.drawRect(innerLeft, y + tableHeaderH - 22f, innerRight, y + tableHeaderH, fillPaint)

        val colWhitePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 36f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val colFadedHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 27f
            color = Color.parseColor("#CBD5E1")
            alpha = 195
            textAlign = Paint.Align.CENTER
        }
        val headerDivPaint = Paint().apply { color = Color.parseColor("#64748B"); strokeWidth = 2.5f }
        canvas.drawLine(xDetailsLeft, y + 10f, xDetailsLeft, y + tableHeaderH - 10f, headerDivPaint)
        canvas.drawLine(xQtyLeft, y + 10f, xQtyLeft, y + tableHeaderH - 10f, headerDivPaint)
        canvas.drawLine(xTotalLeft, y + 10f, xTotalLeft, y + tableHeaderH - 10f, headerDivPaint)

        canvas.drawText("القيمة الإجمالية", xTotalLeft + colTotalW / 2f, y + 58f, colWhitePaint)
        canvas.drawText("الكمية", xQtyLeft + colQtyW / 2f, y + 58f, colWhitePaint)
        canvas.drawText("التفاصيــــــل", xDetailsLeft + colDetailsW / 2f, y + 58f, colWhitePaint)
        canvas.drawText("سعر الواحدة", xUnitPriceLeft + colUnitPriceW / 2f, y + 56f, colFadedHeaderPaint)

        y += tableHeaderH

        val cellBorder = Paint(borderPaint).apply { strokeWidth = 3f }
        val fadedCellBorder = Paint(borderPaint).apply {
            strokeWidth = 2f
            color = Color.parseColor("#94A3B8")
        }
        val fadedValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 28f
            color = Color.parseColor("#94A3B8")
            textAlign = Paint.Align.CENTER
        }
        val lines = invoice.lines
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F8FAFC")
        }
        for (idx in 0 until lineCount) {
            val rowBottom = y + rowHeight
            if (idx % 2 == 1) {
                canvas.drawRect(innerLeft, y, innerRight, rowBottom, zebraPaint)
            }
            canvas.drawRect(innerLeft, y, innerRight, rowBottom, cellBorder)
            canvas.drawLine(xDetailsLeft, y, xDetailsLeft, rowBottom, fadedCellBorder)
            canvas.drawLine(xQtyLeft, y, xQtyLeft, rowBottom, cellBorder)
            canvas.drawLine(xTotalLeft, y, xTotalLeft, rowBottom, cellBorder)

            val centerY = y + rowHeight / 2f + 12f
            if (idx < lines.size) {
                val line = lines[idx]
                val inferredUnitPrice = if (line.unitQty > 0.0) line.lineTotal / line.unitQty else line.unitPrice

                canvas.drawText(
                    NumberFormatUtils.formatNumber(line.lineTotal),
                    xTotalLeft + colTotalW / 2f,
                    centerY,
                    TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 36f; color = navy }
                )
                canvas.drawText(
                    "${NumberFormatUtils.formatNumber(line.unitQty)} ${line.unitName}",
                    xQtyLeft + colQtyW / 2f,
                    centerY,
                    TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 33f }
                )
                canvas.drawText(
                    "${idx + 1}. ${line.productName}",
                    xQtyLeft - 24f,
                    centerY,
                    TextPaint(bodyRight).apply { textSize = 34f }
                )
                canvas.drawText(
                    NumberFormatUtils.formatNumber(inferredUnitPrice),
                    xUnitPriceLeft + colUnitPriceW / 2f,
                    centerY,
                    fadedValuePaint
                )
            }
            y = rowBottom
        }

        // 5. Totals Double Box ("الإجمالي الكلي" | "عدد الأصناف")
        val sumBottom = y + summaryBoxH
        val midX = (innerLeft + innerRight) / 2f
        val sumRect = RectF(innerLeft, y, innerRight, sumBottom)
        canvas.drawRoundRect(sumRect, 18f, 18f, cellBorder)
        canvas.drawLine(midX, y, midX, sumBottom, cellBorder)

        val sumCenterY = y + summaryBoxH / 2f + 14f
        canvas.drawText("الإجمالي الكلي:", innerRight - 28f, sumCenterY, TextPaint(bodyRight).apply { textSize = 42f; color = navy })
        canvas.drawText(
            "${NumberFormatUtils.formatNumber(invoice.totalAmount)} ريال",
            midX + 28f,
            sumCenterY,
            redBoldLeft
        )

        canvas.drawText("عدد الأصناف:", midX - 28f, sumCenterY, TextPaint(bodyRight).apply { textSize = 40f; color = navy })
        canvas.drawText(
            "${invoice.lines.size}",
            innerLeft + 28f,
            sumCenterY,
            TextPaint(bodyCenter).apply { textAlign = Paint.Align.LEFT; typeface = boldTf; textSize = 42f }
        )

        y = sumBottom + 28f

        if (invoice.notes.isNotBlank()) {
            canvas.drawText(
                "ملاحظات: ${invoice.notes}",
                innerRight,
                y + 36f,
                TextPaint(bodyRight).apply { textSize = 31f; typeface = regularTf; color = slate }
            )
            y += notesH
        }

        canvas.drawLine(innerLeft, y, innerRight, y, dottedPaint)
        y += 56f

        // 6. Footer Policy & Signatures
        canvas.drawText(
            settings.footerPolicy,
            width / 2f,
            y,
            TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 32f; color = darkText }
        )
        y += 72f

        val sigTextPaint = TextPaint(bodyRight).apply { textSize = 34f; color = navy }
        canvas.drawText("توقيع البائع: .............................", innerRight, y, sigTextPaint)
        canvas.drawText("توقيع المشتري: .............................", innerLeft, y, TextPaint(sigTextPaint).apply { textAlign = Paint.Align.LEFT })

        return bitmap
    }

    /**
     * Renders a crisp 4K (2160px wide) Account Statement for a customer/supplier.
     */
    fun renderContactStatement4K(
        context: Context,
        contact: Contact,
        invoices: List<Invoice>,
        payments: List<Payment>,
        storeName: String = "بقالة العزي"
    ): Bitmap {
        val settings = PrinterSettingsStore.load(context)
        val effectiveStoreName = storeName.ifBlank { settings.storeName }
        val width = WIDTH_4K_PX
        val margin = 96f

        val regularTf = loadArabicTypeface(context, bold = false)
        val boldTf = loadArabicTypeface(context, bold = true)
        val navy = Color.parseColor("#132C4A")
        val emerald = Color.parseColor("#15803D")
        val crimson = Color.parseColor("#C62828")
        val slate = Color.parseColor("#475569")

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 62f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 36f
            color = Color.parseColor("#E2E8F0")
            textAlign = Paint.Align.CENTER
        }
        val bodyRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 38f
            color = Color.parseColor("#0F172A")
            textAlign = Paint.Align.RIGHT
        }
        val boldLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 38f
            color = Color.parseColor("#0F172A")
            textAlign = Paint.Align.LEFT
        }
        val mutedRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 32f
            color = slate
            textAlign = Paint.Align.RIGHT
        }

        val contactInvoices = invoices.filter { it.contactId == contact.id }.take(15)
        val contactPayments = payments.filter { it.contactId == contact.id }.take(15)
        val rowsCount = maxOf(1, contactInvoices.size + contactPayments.size)

        val totalHeight = (880 + rowsCount * 105).coerceAtLeast(1400)
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = 60f

        fillPaint.color = navy
        canvas.drawRoundRect(RectF(margin, y, width - margin, y + 260f), 32f, 32f, fillPaint)
        canvas.drawText(effectiveStoreName, width / 2f, y + 108f, titlePaint)
        val role = if (contact.resolvedType == ContactType.CUSTOMER) "كشف حساب عميل" else "كشف حساب مورد"
        canvas.drawText("$role  •  ${contact.name}", width / 2f, y + 184f, subtitlePaint)
        y += 304f

        fillPaint.color = Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(RectF(margin, y, width - margin, y + 210f), 28f, 28f, fillPaint)
        val rightX = width - margin - 44f
        val leftX = margin + 44f
        canvas.drawText("الرصيد الحالي المستحق:", rightX, y + 84f, TextPaint(bodyRight).apply { typeface = boldTf; textSize = 44f; color = navy })
        canvas.drawText("${NumberFormatUtils.formatNumber(contact.balance)} ريال", leftX, y + 90f, TextPaint(boldLeft).apply { textSize = 54f; color = crimson })
        canvas.drawText("تاريخ الكشف: ${dateTimeFormatter.format(Date())}   ${if (contact.phone.isNotBlank()) "هاتف: ${contact.phone}" else ""}", rightX, y + 162f, mutedRight)
        y += 260f

        canvas.drawText("الحركات المسجلة", rightX, y, TextPaint(bodyRight).apply { typeface = boldTf; textSize = 42f; color = navy })
        y += 30f

        if (contactInvoices.isEmpty() && contactPayments.isEmpty()) {
            canvas.drawText("لا توجد فواتير أو سندات سداد مسجلة لهذا الحساب حتى الآن.", rightX, y + 70f, mutedRight)
        } else {
            contactInvoices.forEach { inv ->
                y += 16f
                fillPaint.color = Color.parseColor("#F8FAFC")
                canvas.drawRoundRect(RectF(margin, y, width - margin, y + 86f), 16f, 16f, fillPaint)
                val dt = dateTimeFormatter.format(inv.createdAt?.toDate() ?: Date())
                val label = "${if (inv.resolvedType == InvoiceType.SALE) "فاتورة بيع" else "فاتورة شراء"} #${inv.invoiceNumber} ($dt)"
                canvas.drawText(label, rightX, y + 54f, bodyRight)
                canvas.drawText("+${NumberFormatUtils.formatNumber(inv.totalAmount)} ريال", leftX, y + 54f, boldLeft)
                y += 88f
            }
            contactPayments.forEach { pay ->
                y += 16f
                fillPaint.color = Color.parseColor("#F0FDF4")
                canvas.drawRoundRect(RectF(margin, y, width - margin, y + 86f), 16f, 16f, fillPaint)
                val dt = dateTimeFormatter.format(pay.createdAt?.toDate() ?: Date())
                canvas.drawText("سند سداد ($dt) ${pay.notes}", rightX, y + 54f, bodyRight)
                canvas.drawText("-${NumberFormatUtils.formatNumber(pay.amount)} ريال", leftX, y + 54f, TextPaint(boldLeft).apply { color = emerald })
                y += 88f
            }
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 30f
            color = slate
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${settings.footerPolicy} • كشف حساب 4K", width / 2f, (totalHeight - 50).toFloat(), footerPaint)
        return bitmap
    }

    /**
     * Renders a crisp monochrome receipt bitmap sized specifically for 58mm (384px) or 80mm (576px)
     * ESC/POS thermal printers using the Amiri Arabic font, structured table columns,
     * and dynamic StaticLayout wrapping so text never clips on narrow rolls.
     */
    fun renderThermalReceiptBitmap(
        context: Context,
        invoice: Invoice,
        widthPx: Int = THERMAL_80MM_PX,
        storeName: String = "بقالة العزي"
    ): Bitmap {
        val settings = PrinterSettingsStore.load(context)
        val effectiveStoreName = storeName.ifBlank { settings.storeName }
        val scale = widthPx / 576f
        val pad = (16f * scale).coerceAtLeast(8f)
        val contentWidth = (widthPx - pad * 2).toInt().coerceAtLeast(100)
        val regularTf = loadArabicTypeface(context, bold = false)
        val boldTf = loadArabicTypeface(context, bold = true)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (30f * scale).coerceAtLeast(21f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val subCenterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (18f * scale).coerceAtLeast(13f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val boldCenter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (20f * scale).coerceAtLeast(15f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val rightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (19f * scale).coerceAtLeast(14f)
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        val rightBoldPaint = TextPaint(rightPaint).apply {
            typeface = boldTf
            textSize = (20f * scale).coerceAtLeast(15f)
        }
        val leftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (18f * scale).coerceAtLeast(13f)
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }
        val leftBoldPaint = TextPaint(leftPaint).apply {
            typeface = boldTf
            textSize = (20f * scale).coerceAtLeast(15f)
        }
        val centerCellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (18f * scale).coerceAtLeast(13f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val totalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (26f * scale).coerceAtLeast(19f)
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }
        val rtlWrapPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (19f * scale).coerceAtLeast(14f)
            color = Color.BLACK
        }
        val smallWrapPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (16f * scale).coerceAtLeast(12f)
            color = Color.BLACK
        }

        val isPurchase = invoice.resolvedType == InvoiceType.PURCHASE
        val typeText = if (isPurchase) "فاتورة شراء (جملة)" else "فاتورة بيع"
        val payText = if (invoice.resolvedPaymentMethod == PaymentMethod.CASH) "نقداً" else "آجل"
        val dateText = if (widthPx <= THERMAL_58MM_PX) {
            dateFormatter.format(invoice.createdAt?.toDate() ?: Date())
        } else {
            dateTimeFormatter.format(invoice.createdAt?.toDate() ?: Date())
        }
        val partyPrefix = if (isPurchase) "حساب المورد/" else "المطلوب من الأخ/"
        val partyName = invoice.contactName.ifBlank { if (isPurchase) "مورد نقدي" else "عميل نقدي" }

        // Column widths inside contentWidth (RTL: Total Left | Qty Mid | Name Right)
        val colTotalW = (contentWidth * 0.25f)
        val colQtyW = (contentWidth * 0.23f)
        val colNameW = (contentWidth - colTotalW - colQtyW).toInt().coerceAtLeast(80)

        val itemLayouts = invoice.lines.mapIndexed { idx, line ->
            val inferred = if (line.unitQty > 0.0) line.lineTotal / line.unitQty else line.unitPrice
            val titleText = "${idx + 1}. ${line.productName}"
            val nameLayout = StaticLayout.Builder
                .obtain(titleText, 0, titleText.length, rtlWrapPaint, colNameW - 8)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            val subText = "الواحدة: ${NumberFormatUtils.formatNumber(inferred)} | ${NumberFormatUtils.formatNumber(line.unitQty)} ${line.unitName}"
            val subLayout = StaticLayout.Builder
                .obtain(subText, 0, subText.length, smallWrapPaint, colNameW - 8)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            val blockHeight = maxOf(
                nameLayout.height + subLayout.height + (16f * scale).toInt(),
                (48f * scale).toInt()
            )
            Triple(nameLayout, subLayout, blockHeight)
        }

        val partyLayout = StaticLayout.Builder
            .obtain("$partyPrefix $partyName", 0, "$partyPrefix $partyName".length, rtlWrapPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        val notesLayout = if (invoice.notes.isNotBlank()) {
            val txt = "ملاحظة: ${invoice.notes}"
            StaticLayout.Builder
                .obtain(txt, 0, txt.length, smallWrapPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        } else null

        val footerLayout = StaticLayout.Builder
            .obtain(settings.footerPolicy, 0, settings.footerPolicy.length, smallWrapPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()

        val headerBlockH = (if (settings.storePhone.isNotBlank()) 136f else 108f) * scale
        val metaBlockH = (50f * scale) + partyLayout.height + (22f * scale)
        val tableHeaderH = (36f * scale).coerceAtLeast(28f)
        val itemsTotalH = itemLayouts.sumOf { it.third }.toFloat().coerceAtLeast(48f * scale)
        val totalsBlockH = (92f * scale)
        val notesH = notesLayout?.let { it.height + 16f * scale } ?: 0f
        val footerH = footerLayout.height + (44f * scale)

        val totalHeight = (headerBlockH + metaBlockH + tableHeaderH + itemsTotalH + totalsBlockH + notesH + footerH)
            .toInt()
            .coerceAtLeast(380)

        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val solidLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = (2.5f * scale).coerceAtLeast(2f)
            style = Paint.Style.STROKE
        }
        val dashedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = (1.6f * scale).coerceAtLeast(1.2f)
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f * scale, 5f * scale), 0f)
        }
        val fillBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val rightX = widthPx - pad
        val leftX = pad

        // 1. Store Name + Phone + Type Badge
        var y = 34f * scale
        canvas.drawText(effectiveStoreName, widthPx / 2f, y, titlePaint)
        y += 28f * scale
        if (settings.storePhone.isNotBlank()) {
            canvas.drawText("هاتف: ${settings.storePhone}", widthPx / 2f, y, subCenterPaint)
            y += 26f * scale
        }

        val badgeW = (contentWidth * 0.78f).coerceAtLeast(180f)
        val badgeH = (34f * scale).coerceAtLeast(26f)
        val badgeRect = RectF(
            (widthPx - badgeW) / 2f,
            y - (4f * scale),
            (widthPx + badgeW) / 2f,
            y - (4f * scale) + badgeH
        )
        canvas.drawRoundRect(badgeRect, 10f * scale, 10f * scale, solidLinePaint)
        canvas.drawText("$typeText • $payText", widthPx / 2f, badgeRect.centerY() + (7f * scale), boldCenter)
        y = badgeRect.bottom + (14f * scale)

        canvas.drawLine(leftX, y, rightX, y, solidLinePaint)
        y += 24f * scale

        // 2. Invoice Number & Date + Party Name
        canvas.drawText("الرقم: ${invoice.invoiceNumber.ifBlank { "0001" }}", rightX, y, rightBoldPaint)
        canvas.drawText(dateText, leftX, y, leftPaint)
        y += 14f * scale

        canvas.save()
        canvas.translate(leftX, y)
        partyLayout.draw(canvas)
        canvas.restore()
        y += partyLayout.height + (10f * scale)

        // 3. Inverted Black POS Table Header (القيمة | الكمية | الصنف)
        val headerTop = y
        val headerBottom = y + tableHeaderH
        canvas.drawRect(leftX, headerTop, rightX, headerBottom, fillBlackPaint)

        val colHeaderWhite = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (18f * scale).coerceAtLeast(13f)
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val xTotalCenter = leftX + colTotalW / 2f
        val xQtyCenter = leftX + colTotalW + colQtyW / 2f
        val xNameCenter = leftX + colTotalW + colQtyW + colNameW / 2f
        val headerBaseY = headerTop + tableHeaderH / 2f + (6f * scale)

        canvas.drawText("القيمة", xTotalCenter, headerBaseY, colHeaderWhite)
        canvas.drawText("الكمية", xQtyCenter, headerBaseY, colHeaderWhite)
        canvas.drawText("الصنف والتفاصيل", xNameCenter, headerBaseY, colHeaderWhite)
        y = headerBottom + (6f * scale)

        // 4. Structured Items Rows
        val xColNameLeft = leftX + colTotalW + colQtyW
        invoice.lines.forEachIndexed { idx, line ->
            val (nameLayout, subLayout, blockH) = itemLayouts[idx]
            val rowTop = y

            // Product name + unit-price subline on right
            canvas.save()
            canvas.translate(xColNameLeft + 4f, rowTop + (4f * scale))
            nameLayout.draw(canvas)
            canvas.translate(0f, nameLayout.height.toFloat() + (2f * scale))
            subLayout.draw(canvas)
            canvas.restore()

            val valY = rowTop + blockH / 2f + (5f * scale)
            canvas.drawText(
                NumberFormatUtils.formatNumber(line.unitQty),
                xQtyCenter,
                valY,
                centerCellPaint
            )
            canvas.drawText(
                NumberFormatUtils.formatNumber(line.lineTotal),
                xTotalCenter,
                valY,
                TextPaint(centerCellPaint).apply { textSize = (19f * scale).coerceAtLeast(14f) }
            )

            y += blockH
            canvas.drawLine(leftX, y, rightX, y, dashedLinePaint)
            y += (4f * scale)
        }

        // 5. Totals Box
        y += (4f * scale)
        canvas.drawLine(leftX, y, rightX, y, solidLinePaint)
        y += (30f * scale)

        canvas.drawText("الإجمالي الكلي:", rightX, y, TextPaint(rightBoldPaint).apply { textSize = (23f * scale).coerceAtLeast(17f) })
        canvas.drawText("${NumberFormatUtils.formatNumber(invoice.totalAmount)} ريال", leftX, y, totalPaint)
        y += (28f * scale)
        canvas.drawText("عدد الأصناف:", rightX, y, rightPaint)
        canvas.drawText("${invoice.lines.size}", leftX, y, leftBoldPaint)
        y += (14f * scale)
        canvas.drawLine(leftX, y, rightX, y, solidLinePaint)
        y += (16f * scale)

        // 6. Notes & Policy Footer
        if (notesLayout != null) {
            canvas.save()
            canvas.translate(leftX, y)
            notesLayout.draw(canvas)
            canvas.restore()
            y += notesLayout.height + (12f * scale)
        }

        canvas.save()
        canvas.translate(leftX, y)
        footerLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    fun saveBitmapToShareCachePng(context: Context, bitmap: Bitmap, prefix: String): Uri {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun writeBitmapPngToUri(context: Context, bitmap: Bitmap, destinationUri: Uri): Result<Unit> =
        runCatching {
            val out = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalStateException("تعذر فتح ملف الوجهة للحفظ")
            out.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    throw IllegalStateException("تعذر ضغط صورة الإيصال")
                }
                it.flush()
            }
        }

    fun writeInvoicePdfToUri(
        context: Context,
        invoice: Invoice,
        destinationUri: Uri,
        storeName: String = "بقالة العزي"
    ): Result<Unit> = runCatching {
        val bitmap4K = renderInvoiceReceipt4K(context, invoice, storeName)
        val doc = PdfDocument()
        val pageWidth = 1240
        val scaledH = (bitmap4K.height * (pageWidth.toFloat() / bitmap4K.width)).toInt().coerceAtLeast(1754)
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, scaledH, 1).create()
        val page = doc.startPage(pageInfo)
        val destRect = RectF(0f, 0f, pageWidth.toFloat(), (bitmap4K.height * (pageWidth.toFloat() / bitmap4K.width)))
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(bitmap4K, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        doc.finishPage(page)

        val out = context.contentResolver.openOutputStream(destinationUri)
            ?: throw IllegalStateException("تعذر فتح ملف PDF للكتابة")
        out.use {
            doc.writeTo(it)
            it.flush()
        }
        doc.close()
        bitmap4K.recycle()
    }

    fun writeInventoryPdfToUri(
        context: Context,
        products: List<Product>,
        destinationUri: Uri,
        storeName: String = "بقالة العزي"
    ): Result<Unit> = runCatching {
        val regularTf = loadArabicTypeface(context, bold = false)
        val boldTf = loadArabicTypeface(context, bold = true)
        val doc = PdfDocument()
        val pageWidth = 1240
        val pageHeight = 1754
        val margin = 56f

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 34f
            color = Color.parseColor("#132C4A")
            textAlign = Paint.Align.RIGHT
        }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 22f
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        val leftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 22f
            color = Color.parseColor("#15803D")
            textAlign = Paint.Align.LEFT
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 18f
            color = Color.parseColor("#475569")
            textAlign = Paint.Align.RIGHT
        }
        val divider = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1.5f
        }

        val itemsPerPage = 18
        val chunks = if (products.isEmpty()) listOf(emptyList()) else products.chunked(itemsPerPage)

        chunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            var y = 78f
            val rightX = pageWidth - margin
            val leftX = margin

            canvas.drawText("$storeName - تقرير جرد المخزون وتكلفة الوحدات", rightX, y, headerPaint)
            y += 32f
            val totalValue = products.sumOf { it.stockBaseQty * it.baseCost }
            canvas.drawText(
                "التاريخ: ${dateTimeFormatter.format(Date())}  |  الأصناف: ${products.size}  |  إجمالي قيمة المخزون: ${NumberFormatUtils.formatNumber(totalValue)} ريال  |  صفحة ${pageIndex + 1}/${chunks.size}",
                rightX,
                y,
                mutedPaint
            )
            y += 20f
            canvas.drawLine(margin, y, pageWidth - margin, y, divider)
            y += 36f

            if (chunk.isEmpty()) {
                canvas.drawText("لا توجد أصناف مسجلة في المخزون.", rightX, y + 30f, rowPaint)
            } else {
                chunk.forEach { p ->
                    val largeUnit = p.unitTiers.lastOrNull()
                    val largeFactor = p.unitTiers.fold(1.0) { acc, t -> acc * t.factorToPrevious }
                    val largeDesc = if (largeUnit != null) {
                        " | تكلفة ${largeUnit.name}: ${NumberFormatUtils.formatNumber(p.baseCost * largeFactor)}"
                    } else ""

                    canvas.drawText(
                        "${p.name} (${p.baseUnitName})  -  تكلفة الحبة: ${NumberFormatUtils.formatNumber(p.baseCost)}$largeDesc",
                        rightX,
                        y,
                        rowPaint
                    )
                    canvas.drawText(
                        "المتاح: ${NumberFormatUtils.formatNumber(p.stockBaseQty)}  |  قيمة: ${NumberFormatUtils.formatNumber(p.stockBaseQty * p.baseCost)} ر",
                        leftX,
                        y,
                        leftPaint
                    )
                    y += 18f
                    canvas.drawText(
                        "سعر البيع الأساسي: ${NumberFormatUtils.formatNumber(p.baseSellPrice)} ريال${if (p.barcode.isNotBlank()) " | باركود: ${p.barcode}" else ""}",
                        rightX,
                        y + 20f,
                        mutedPaint
                    )
                    y += 34f
                    canvas.drawLine(margin, y, pageWidth - margin, y, divider)
                    y += 32f
                }
            }
            doc.finishPage(page)
        }

        val out = context.contentResolver.openOutputStream(destinationUri)
            ?: throw IllegalStateException("تعذر فتح ملف تقرير المخزون للكتابة")
        out.use {
            doc.writeTo(it)
            it.flush()
        }
        doc.close()
    }
}

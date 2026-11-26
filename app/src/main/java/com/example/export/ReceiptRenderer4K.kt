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
import com.example.domain.InvoiceMode
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
            ResourcesCompat.getFont(context, R.font.amiri) ?: Typeface.DEFAULT
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
        val outerPad = 24f
        val cardLeft = outerPad
        val cardRight = width - outerPad
        val cardInnerPad = 28f
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
            textSize = 35f
            color = darkText
            textAlign = Paint.Align.RIGHT
        }
        val bodyCenter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 33f
            color = darkText
            textAlign = Paint.Align.CENTER
        }
        val redBoldCenter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 38f
            color = crimson
            textAlign = Paint.Align.CENTER
        }
        val redBoldLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 42f
            color = crimson
            textAlign = Paint.Align.LEFT
        }

        val isPurchase = invoice.resolvedType == InvoiceType.PURCHASE
        val typeLabel = (if (isPurchase) "فاتورة شراء" else "فاتورة بيع") +
            if (invoice.resolvedMode == InvoiceMode.FREE) " • حرة" else " • من المخزن"
        val isCash = invoice.resolvedPaymentMethod == PaymentMethod.CASH
        val dateText = dateFormatter.format(invoice.createdAt?.toDate() ?: Date())

        val lineCount = maxOf(1, invoice.lines.size)
        val rowHeights = (0 until lineCount).map { idx ->
            val desc = invoice.lines.getOrNull(idx)?.description.orEmpty()
            if (desc.isNotBlank()) 88f else 60f
        }
        val rowsTotalHeight = rowHeights.sum()
        val tableHeaderH = 60f
        val showAccountingBreakdown = invoice.discount > 0.0 ||
            invoice.resolvedRemainingAmount > 0.0001 ||
            (invoice.paidAmount != null && kotlin.math.abs(invoice.resolvedPaidAmount - invoice.totalAmount) > 0.0001)
        val mainSumH = 88f
        val summaryBoxH = if (showAccountingBreakdown) 142f else mainSumH
        val notesH = if (invoice.notes.isNotBlank()) 52f else 0f

        // Exact layout offsets from outerPad:
        // topPad(30) + header(94) + divGap(22) + party(72) + divGap(20) + tableHeader(60) + rows + sumGap(18) + sumBox + notes + dottedGap(38) + policyGap(48) + bottomPad(28)
        val cardHeight = 30f + 94f + 22f + 72f + 20f + tableHeaderH + rowsTotalHeight + summaryBoxH + 18f + notesH + 38f + 48f + 28f
        val totalHeight = kotlin.math.ceil((outerPad * 2 + cardHeight).toDouble()).toInt()

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
        canvas.drawRoundRect(cardRect, 36f, 36f, fillPaint)
        canvas.drawRoundRect(cardRect, 36f, 36f, borderPaint)

        var y = outerPad + 30f

        // 1. Header Row: Store Name + Phone (Right) | Circle Badge (Center) | Invoice Number & Date (Left)
        val rightStoreText = buildString {
            append(effectiveStoreName)
            if (settings.storePhone.isNotBlank()) {
                append("   ت: ")
                append(settings.storePhone)
            }
        }
        canvas.drawText(rightStoreText, innerRight, y + 54f, headerTextPaint)

        // Top-left corner: Invoice number pill on top, Date underneath
        val numPillRect = RectF(innerLeft, y, innerLeft + 230f, y + 52f)
        canvas.drawRoundRect(numPillRect, 16f, 16f, Paint(borderPaint).apply { strokeWidth = 3.5f })
        canvas.drawText(invoice.invoiceNumber.ifBlank { "0001" }, numPillRect.centerX(), numPillRect.centerY() + 12f, redBoldCenter)
        canvas.drawText("الرقم:", numPillRect.right + 16f, y + 36f, TextPaint(headerLeftPaint).apply { textSize = 33f })
        canvas.drawText(
            "التاريخ: $dateText",
            innerLeft,
            y + 84f,
            TextPaint(headerLeftPaint).apply {
                typeface = boldTf
                textSize = 29f
                color = darkText
            }
        )

        val badgeRadius = 40f
        fillPaint.color = navy
        canvas.drawCircle(width / 2f, y + 44f, badgeRadius, fillPaint)
        canvas.drawText(settings.storeBadgeText.ifBlank { "AZ" }, width / 2f, y + 56f, badgeTextPaint)

        y += 94f
        canvas.drawLine(innerLeft, y, innerRight, y, Paint(borderPaint).apply { strokeWidth = 3f })
        y += 22f

        // 2. Metadata Row 1: "المطلوب من/" + dotted customer field | "فاتورة بيع/شراء" pill + cash/credit radios
        val pillWidth = 340f
        val pillHeight = 56f
        val radioTextPaint = TextPaint(bodyRight).apply { textSize = 31f }

        // Radios on the left
        val creditCircleX = innerLeft + 20f
        val radioCenterY = y + 28f
        canvas.drawCircle(creditCircleX, radioCenterY, 14f, Paint(borderPaint).apply { strokeWidth = 3f })
        if (!isCash) {
            fillPaint.color = navy
            canvas.drawCircle(creditCircleX, radioCenterY, 7.5f, fillPaint)
        }
        canvas.drawText("آجل", creditCircleX + 20f, radioCenterY + 10f, TextPaint(radioTextPaint).apply { textAlign = Paint.Align.LEFT })

        val cashCircleX = innerLeft + 126f
        canvas.drawCircle(cashCircleX, radioCenterY, 14f, Paint(borderPaint).apply { strokeWidth = 3f })
        if (isCash) {
            fillPaint.color = navy
            canvas.drawCircle(cashCircleX, radioCenterY, 7.5f, fillPaint)
        }
        canvas.drawText("نقداً", cashCircleX + 20f, radioCenterY + 10f, TextPaint(radioTextPaint).apply { textAlign = Paint.Align.LEFT })

        // Invoice type dark navy pill
        val typePillRect = RectF(innerLeft + 244f, y, innerLeft + 244f + pillWidth, y + pillHeight)
        fillPaint.color = navy
        canvas.drawRoundRect(typePillRect, 18f, 18f, fillPaint)
        canvas.drawText(typeLabel, typePillRect.centerX(), typePillRect.centerY() + 10f, TextPaint(badgeTextPaint).apply { textSize = 28f })

        // Customer / Supplier label & dotted line on right
        val labelRightText = if (isPurchase) "حساب المورد/" else "المطلوب من/"
        canvas.drawText(labelRightText, innerRight, y + 38f, bodyRight)
        val partyStartRight = innerRight - bodyRight.measureText(labelRightText) - 18f
        val partyLineLeft = typePillRect.right + 22f
        canvas.drawLine(partyLineLeft, y + 48f, partyStartRight, y + 48f, dottedPaint)
        canvas.drawText(
            invoice.contactName.ifBlank { if (isPurchase) "مورد نقدي" else "عميل نقدي" },
            (partyLineLeft + partyStartRight) / 2f,
            y + 38f,
            TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 34f }
        )

        y += 72f
        canvas.drawLine(innerLeft, y, innerRight, y, dottedPaint)
        y += 20f

        // 4. 4-Column Bordered Table (RTL from Right: القيمة الإجمالية | الكمية | التفاصيل | سعر الواحدة faded)
        val colTotalW = 420f
        val colQtyW = 300f
        val colUnitPriceW = 240f
        val colDetailsW = innerWidth - colTotalW - colQtyW - colUnitPriceW

        val xUnitPriceLeft = innerLeft
        val xDetailsLeft = xUnitPriceLeft + colUnitPriceW
        val xQtyLeft = xDetailsLeft + colDetailsW
        val xTotalLeft = xQtyLeft + colQtyW

        val headerRect = RectF(innerLeft, y, innerRight, y + tableHeaderH)
        fillPaint.color = navy
        canvas.drawRoundRect(headerRect, 18f, 18f, fillPaint)
        canvas.drawRect(innerLeft, y + tableHeaderH - 18f, innerRight, y + tableHeaderH, fillPaint)

        val colWhitePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 34f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val colFadedHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 26f
            color = Color.parseColor("#CBD5E1")
            alpha = 195
            textAlign = Paint.Align.CENTER
        }
        val headerDivPaint = Paint().apply { color = Color.parseColor("#64748B"); strokeWidth = 2.5f }
        canvas.drawLine(xDetailsLeft, y + 8f, xDetailsLeft, y + tableHeaderH - 8f, headerDivPaint)
        canvas.drawLine(xQtyLeft, y + 8f, xQtyLeft, y + tableHeaderH - 8f, headerDivPaint)
        canvas.drawLine(xTotalLeft, y + 8f, xTotalLeft, y + tableHeaderH - 8f, headerDivPaint)

        val headerBaseline = y + tableHeaderH / 2f + 11f
        canvas.drawText("القيمة الإجمالية", xTotalLeft + colTotalW / 2f, headerBaseline, colWhitePaint)
        canvas.drawText("الكمية", xQtyLeft + colQtyW / 2f, headerBaseline, colWhitePaint)
        canvas.drawText("التفاصيــــــل", xDetailsLeft + colDetailsW / 2f, headerBaseline, colWhitePaint)
        canvas.drawText("سعر الواحدة", xUnitPriceLeft + colUnitPriceW / 2f, headerBaseline - 1f, colFadedHeaderPaint)

        y += tableHeaderH

        val cellBorder = Paint(borderPaint).apply { strokeWidth = 2.8f }
        val fadedCellBorder = Paint(borderPaint).apply {
            strokeWidth = 2f
            color = Color.parseColor("#94A3B8")
        }
        val fadedValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 27f
            color = Color.parseColor("#94A3B8")
            textAlign = Paint.Align.CENTER
        }
        val lines = invoice.lines
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F8FAFC")
        }
        val descRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 25f
            color = slate
            textAlign = Paint.Align.RIGHT
        }
        for (idx in 0 until lineCount) {
            val rowH = rowHeights[idx]
            val rowBottom = y + rowH
            if (idx % 2 == 1) {
                canvas.drawRect(innerLeft, y, innerRight, rowBottom, zebraPaint)
            }
            canvas.drawRect(innerLeft, y, innerRight, rowBottom, cellBorder)
            canvas.drawLine(xDetailsLeft, y, xDetailsLeft, rowBottom, fadedCellBorder)
            canvas.drawLine(xQtyLeft, y, xQtyLeft, rowBottom, cellBorder)
            canvas.drawLine(xTotalLeft, y, xTotalLeft, rowBottom, cellBorder)

            val centerY = y + rowH / 2f + 10f
            if (idx < lines.size) {
                val line = lines[idx]
                val inferredUnitPrice = if (line.unitQty > 0.0) line.lineTotal / line.unitQty else line.unitPrice

                canvas.drawText(
                    NumberFormatUtils.formatNumber(line.lineTotal),
                    xTotalLeft + colTotalW / 2f,
                    centerY,
                    TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 34f; color = navy }
                )
                canvas.drawText(
                    "${NumberFormatUtils.formatNumber(line.unitQty)} ${line.unitName}",
                    xQtyLeft + colQtyW / 2f,
                    centerY,
                    TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 31f }
                )
                if (line.description.isNotBlank()) {
                    canvas.drawText(
                        "${idx + 1}. ${line.productName}",
                        xQtyLeft - 20f,
                        y + 34f,
                        TextPaint(bodyRight).apply { textSize = 32f }
                    )
                    canvas.drawText(
                        line.description.take(54),
                        xQtyLeft - 20f,
                        y + 69f,
                        descRightPaint
                    )
                } else {
                    canvas.drawText(
                        "${idx + 1}. ${line.productName}",
                        xQtyLeft - 20f,
                        centerY,
                        TextPaint(bodyRight).apply { textSize = 32f }
                    )
                }
                canvas.drawText(
                    NumberFormatUtils.formatNumber(inferredUnitPrice),
                    xUnitPriceLeft + colUnitPriceW / 2f,
                    centerY,
                    fadedValuePaint
                )
            }
            y = rowBottom
        }

        // 5. Totals Double Box ("الإجمالي الكلي" | "عدد الأصناف") + optional Discount / Paid / Remaining row
        val mainSumBottom = y + mainSumH
        val sumBottom = y + summaryBoxH
        val midX = (innerLeft + innerRight) / 2f
        val sumRect = RectF(innerLeft, y, innerRight, sumBottom)
        canvas.drawRoundRect(sumRect, 16f, 16f, cellBorder)
        canvas.drawLine(midX, y, midX, mainSumBottom, cellBorder)

        val sumCenterY = y + mainSumH / 2f + 12f
        val totalLabel = if (invoice.discount > 0.0) "الصافي النهائي:" else "الإجمالي الكلي:"
        canvas.drawText(totalLabel, innerRight - 24f, sumCenterY, TextPaint(bodyRight).apply { textSize = 39f; color = navy })
        canvas.drawText(
            "${NumberFormatUtils.formatNumber(invoice.totalAmount)} ريال",
            midX + 24f,
            sumCenterY,
            redBoldLeft
        )

        canvas.drawText("عدد الأصناف:", midX - 24f, sumCenterY, TextPaint(bodyRight).apply { textSize = 38f; color = navy })
        canvas.drawText(
            "${invoice.lines.size}",
            innerLeft + 24f,
            sumCenterY,
            TextPaint(bodyCenter).apply { textAlign = Paint.Align.LEFT; typeface = boldTf; textSize = 40f }
        )

        if (showAccountingBreakdown) {
            canvas.drawLine(innerLeft, mainSumBottom, innerRight, mainSumBottom, cellBorder)
            val detailParts = buildList {
                if (invoice.discount > 0.0) {
                    add("قبل الخصم: ${NumberFormatUtils.formatNumber(invoice.subtotalAmount)}")
                    add("الخصم: ${NumberFormatUtils.formatNumber(invoice.discount)}")
                }
                add("المدفوع: ${NumberFormatUtils.formatNumber(invoice.resolvedPaidAmount)}")
                add("المتبقي: ${NumberFormatUtils.formatNumber(invoice.resolvedRemainingAmount)}")
            }.joinToString("   |   ")
            canvas.drawText(
                detailParts,
                width / 2f,
                mainSumBottom + 36f,
                TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 28f; color = navy }
            )
        }

        y = sumBottom + 18f

        if (invoice.notes.isNotBlank()) {
            canvas.drawText(
                "ملاحظات: ${invoice.notes}",
                innerRight,
                y + 28f,
                TextPaint(bodyRight).apply { textSize = 29f; typeface = regularTf; color = slate }
            )
            y += notesH
        }

        canvas.drawLine(innerLeft, y, innerRight, y, dottedPaint)
        y += 38f

        // 6. Footer Policy & Signatures
        canvas.drawText(
            settings.footerPolicy,
            width / 2f,
            y,
            TextPaint(bodyCenter).apply { typeface = boldTf; textSize = 30f; color = darkText }
        )
        y += 48f

        val sigTextPaint = TextPaint(bodyRight).apply { textSize = 32f; color = navy }
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
        val margin = 48f

        val regularTf = loadArabicTypeface(context, bold = false)
        val boldTf = loadArabicTypeface(context, bold = true)
        val navy = Color.parseColor("#132C4A")
        val emerald = Color.parseColor("#15803D")
        val crimson = Color.parseColor("#C62828")
        val slate = Color.parseColor("#475569")

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 56f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 34f
            color = Color.parseColor("#E2E8F0")
            textAlign = Paint.Align.CENTER
        }
        val bodyRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 35f
            color = Color.parseColor("#0F172A")
            textAlign = Paint.Align.RIGHT
        }
        val boldLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 35f
            color = Color.parseColor("#0F172A")
            textAlign = Paint.Align.LEFT
        }
        val mutedRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 30f
            color = slate
            textAlign = Paint.Align.RIGHT
        }

        val contactInvoices = invoices.filter { it.contactId == contact.id }.take(15)
        val contactPayments = payments.filter { it.contactId == contact.id }.take(15)
        val totalRows = contactInvoices.size + contactPayments.size
        val rowsBlockH = if (totalRows == 0) 76f else totalRows * 70f

        val totalHeight = kotlin.math.ceil((32f + 218f + 190f + 22f + rowsBlockH + 80f).toDouble()).toInt()
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = 32f

        fillPaint.color = navy
        canvas.drawRoundRect(RectF(margin, y, width - margin, y + 196f), 26f, 26f, fillPaint)
        canvas.drawText(effectiveStoreName, width / 2f, y + 84f, titlePaint)
        val role = if (contact.resolvedType == ContactType.CUSTOMER) "كشف حساب عميل" else "كشف حساب مورد"
        canvas.drawText("$role  •  ${contact.name}", width / 2f, y + 148f, subtitlePaint)
        y += 218f

        fillPaint.color = Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(RectF(margin, y, width - margin, y + 154f), 22f, 22f, fillPaint)
        val rightX = width - margin - 32f
        val leftX = margin + 32f
        canvas.drawText("الرصيد الحالي المستحق:", rightX, y + 66f, TextPaint(bodyRight).apply { typeface = boldTf; textSize = 40f; color = navy })
        canvas.drawText("${NumberFormatUtils.formatNumber(contact.balance)} ريال", leftX, y + 70f, TextPaint(boldLeft).apply { textSize = 48f; color = crimson })
        canvas.drawText("تاريخ الكشف: ${dateTimeFormatter.format(Date())}   ${if (contact.phone.isNotBlank()) "هاتف: ${contact.phone}" else ""}", rightX, y + 124f, mutedRight)
        y += 190f

        canvas.drawText("الحركات المسجلة", rightX, y, TextPaint(bodyRight).apply { typeface = boldTf; textSize = 38f; color = navy })
        y += 22f

        if (contactInvoices.isEmpty() && contactPayments.isEmpty()) {
            canvas.drawText("لا توجد فواتير أو سندات سداد مسجلة لهذا الحساب حتى الآن.", rightX, y + 48f, mutedRight)
        } else {
            contactInvoices.forEach { inv ->
                y += 8f
                fillPaint.color = Color.parseColor("#F8FAFC")
                canvas.drawRoundRect(RectF(margin, y, width - margin, y + 62f), 14f, 14f, fillPaint)
                val dt = dateTimeFormatter.format(inv.createdAt?.toDate() ?: Date())
                val label = "${if (inv.resolvedType == InvoiceType.SALE) "فاتورة بيع" else "فاتورة شراء"} #${inv.invoiceNumber} ($dt)"
                canvas.drawText(label, rightX, y + 42f, bodyRight)
                canvas.drawText("+${NumberFormatUtils.formatNumber(inv.totalAmount)} ريال", leftX, y + 42f, boldLeft)
                y += 62f
            }
            contactPayments.forEach { pay ->
                y += 8f
                fillPaint.color = Color.parseColor("#F0FDF4")
                canvas.drawRoundRect(RectF(margin, y, width - margin, y + 62f), 14f, 14f, fillPaint)
                val dt = dateTimeFormatter.format(pay.createdAt?.toDate() ?: Date())
                canvas.drawText("سند سداد ($dt) ${pay.notes}", rightX, y + 42f, bodyRight)
                canvas.drawText("-${NumberFormatUtils.formatNumber(pay.amount)} ريال", leftX, y + 42f, TextPaint(boldLeft).apply { color = emerald })
                y += 62f
            }
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 28f
            color = slate
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${settings.footerPolicy} • كشف حساب 4K", width / 2f, (totalHeight - 28).toFloat(), footerPaint)
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
        val pad = (8f * scale).coerceAtLeast(4f)
        val contentWidth = (widthPx - pad * 2).toInt().coerceAtLeast(100)
        val regularTf = loadArabicTypeface(context, bold = false)
        val boldTf = loadArabicTypeface(context, bold = true)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (28f * scale).coerceAtLeast(19f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val subCenterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (17f * scale).coerceAtLeast(12f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val boldCenter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (18f * scale).coerceAtLeast(13f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val rightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (18f * scale).coerceAtLeast(13f)
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        val rightBoldPaint = TextPaint(rightPaint).apply {
            typeface = boldTf
            textSize = (19f * scale).coerceAtLeast(14f)
        }
        val leftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (17f * scale).coerceAtLeast(12f)
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }
        val leftBoldPaint = TextPaint(leftPaint).apply {
            typeface = boldTf
            textSize = (18f * scale).coerceAtLeast(13f)
        }
        val centerCellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (17f * scale).coerceAtLeast(12f)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val totalRightLabelPaint = TextPaint(rightBoldPaint).apply {
            textSize = (21f * scale).coerceAtLeast(15f)
        }
        val totalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (23f * scale).coerceAtLeast(17f)
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
        }
        val rtlWrapPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (18f * scale).coerceAtLeast(13f)
            color = Color.BLACK
        }
        val smallWrapPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = (15f * scale).coerceAtLeast(11f)
            color = Color.BLACK
        }

        val isPurchase = invoice.resolvedType == InvoiceType.PURCHASE
        val modeBadge = if (invoice.resolvedMode == InvoiceMode.FREE) "حرة" else "من المخزن"
        val typeText = (if (isPurchase) "فاتورة شراء" else "فاتورة بيع") + " • $modeBadge"
        val payText = if (invoice.resolvedPaymentMethod == PaymentMethod.CASH) "نقداً" else "آجل"
        val dateText = if (widthPx <= THERMAL_58MM_PX) {
            dateFormatter.format(invoice.createdAt?.toDate() ?: Date())
        } else {
            dateTimeFormatter.format(invoice.createdAt?.toDate() ?: Date())
        }
        val partyPrefix = if (isPurchase) "حساب المورد/" else "المطلوب من/"
        val partyName = invoice.contactName.ifBlank { if (isPurchase) "مورد نقدي" else "عميل نقدي" }

        // Column widths inside contentWidth (RTL: Total Left | Qty Mid | Name Right)
        val colTotalW = contentWidth * 0.25f
        val colQtyW = contentWidth * 0.21f
        val colNameW = (contentWidth - colTotalW - colQtyW).toInt().coerceAtLeast(80)

        val itemLayouts = invoice.lines.mapIndexed { idx, line ->
            val inferred = if (line.unitQty > 0.0) line.lineTotal / line.unitQty else line.unitPrice
            val titleText = "${idx + 1}. ${line.productName}"
            val nameLayout = StaticLayout.Builder
                .obtain(titleText, 0, titleText.length, rtlWrapPaint, colNameW - 6)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 0.88f)
                .setIncludePad(false)
                .build()
            val descPart = if (line.description.isNotBlank()) "${line.description} | " else ""
            val subText = "${descPart}${line.unitName} × ${NumberFormatUtils.formatNumber(inferred)}"
            val subLayout = StaticLayout.Builder
                .obtain(subText, 0, subText.length, smallWrapPaint, colNameW - 6)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 0.88f)
                .setIncludePad(false)
                .build()
            val blockHeight = maxOf(
                nameLayout.height + subLayout.height + (4f * scale).toInt(),
                (28f * scale).toInt()
            )
            Triple(nameLayout, subLayout, blockHeight)
        }

        val partyFullText = "$partyPrefix $partyName"
        val partyLayout = StaticLayout.Builder
            .obtain(partyFullText, 0, partyFullText.length, rtlWrapPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 0.88f)
            .setIncludePad(false)
            .build()

        val notesLayout = if (invoice.notes.isNotBlank()) {
            val txt = "ملاحظة: ${invoice.notes}"
            StaticLayout.Builder
                .obtain(txt, 0, txt.length, smallWrapPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 0.88f)
                .setIncludePad(false)
                .build()
        } else null

        val footerLayout = StaticLayout.Builder
            .obtain(settings.footerPolicy, 0, settings.footerPolicy.length, smallWrapPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 0.88f)
            .setIncludePad(false)
            .build()

        val showDiscountRows = invoice.discount > 0.0
        val showPaymentRows = showDiscountRows ||
            invoice.resolvedRemainingAmount > 0.0001 ||
            (invoice.paidAmount != null && kotlin.math.abs(invoice.resolvedPaidAmount - invoice.totalAmount) > 0.0001)

        // Single-pass exact Y coordinate measurement so zero empty tail paper is wasted
        var cursorY = 4f * scale

        val titleFm = titlePaint.fontMetrics
        val storeNameBaselineY = cursorY - titleFm.ascent
        cursorY += (titleFm.descent - titleFm.ascent) * 0.86f + (1f * scale)

        val phoneBaselineY: Float? = if (settings.storePhone.isNotBlank()) {
            val phoneFm = subCenterPaint.fontMetrics
            val base = cursorY - phoneFm.ascent
            cursorY += (phoneFm.descent - phoneFm.ascent) * 0.86f + (2f * scale)
            base
        } else null

        val badgeH = (26f * scale).coerceAtLeast(21f)
        val badgeTop = cursorY
        val badgeBottom = badgeTop + badgeH
        cursorY = badgeBottom + (5f * scale)

        val topDividerY = cursorY
        cursorY += (4f * scale)

        val metaFm = rightBoldPaint.fontMetrics
        val numDateBaselineY = cursorY - metaFm.ascent
        cursorY += (metaFm.descent - metaFm.ascent) * 0.88f + (2f * scale)

        val partyTopY = cursorY
        cursorY += partyLayout.height + (4f * scale)

        val tableHeaderTop = cursorY
        val tableHeaderH = (27f * scale).coerceAtLeast(21f)
        val tableHeaderBottom = tableHeaderTop + tableHeaderH
        cursorY = tableHeaderBottom + (2f * scale)

        val itemTops = ArrayList<Float>(itemLayouts.size)
        itemLayouts.forEach { (_, _, blockH) ->
            itemTops.add(cursorY)
            cursorY += blockH + (2f * scale)
        }
        if (itemLayouts.isEmpty()) {
            cursorY += 16f * scale
        }

        val totalsDividerY = cursorY
        cursorY += (4f * scale)

        val regFm = rightPaint.fontMetrics
        val regRowH = (regFm.descent - regFm.ascent) * 0.88f + (2f * scale)
        val totFm = totalPaint.fontMetrics
        val totRowH = (totFm.descent - totFm.ascent) * 0.88f + (3f * scale)

        var subtotalBaselineY = 0f
        var discountBaselineY = 0f
        if (showDiscountRows) {
            subtotalBaselineY = cursorY - regFm.ascent
            cursorY += regRowH
            discountBaselineY = cursorY - regFm.ascent
            cursorY += regRowH
        }

        val netTotalBaselineY = cursorY - totFm.ascent
        cursorY += totRowH

        var paidBaselineY = 0f
        var remainingBaselineY = 0f
        if (showPaymentRows) {
            paidBaselineY = cursorY - regFm.ascent
            cursorY += regRowH
            remainingBaselineY = cursorY - regFm.ascent
            cursorY += regRowH
        }

        val itemCountBaselineY = cursorY - regFm.ascent
        cursorY += regRowH + (2f * scale)

        val bottomTotalsDividerY = cursorY
        cursorY += (4f * scale)

        val notesTopY = cursorY
        if (notesLayout != null) {
            cursorY += notesLayout.height + (4f * scale)
        }

        val footerTopY = cursorY
        cursorY += footerLayout.height + (6f * scale)

        val totalHeight = kotlin.math.ceil(cursorY.toDouble()).toInt().coerceAtLeast(180)
        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val solidLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = (2.2f * scale).coerceAtLeast(1.6f)
            style = Paint.Style.STROKE
        }
        val dashedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = (1.4f * scale).coerceAtLeast(1f)
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(5f * scale, 4f * scale), 0f)
        }
        val fillBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val rightX = widthPx - pad
        val leftX = pad

        // 1. Store Name + Phone + Type Badge
        canvas.drawText(effectiveStoreName, widthPx / 2f, storeNameBaselineY, titlePaint)
        if (phoneBaselineY != null) {
            canvas.drawText("هاتف: ${settings.storePhone}", widthPx / 2f, phoneBaselineY, subCenterPaint)
        }

        val badgeW = (contentWidth * 0.84f).coerceAtLeast(180f)
        val badgeRect = RectF((widthPx - badgeW) / 2f, badgeTop, (widthPx + badgeW) / 2f, badgeBottom)
        canvas.drawRoundRect(badgeRect, 8f * scale, 8f * scale, solidLinePaint)
        val badgeFm = boldCenter.fontMetrics
        val badgeTextBaseline = badgeRect.centerY() - (badgeFm.ascent + badgeFm.descent) / 2f
        canvas.drawText("$typeText • $payText", widthPx / 2f, badgeTextBaseline, boldCenter)

        canvas.drawLine(leftX, topDividerY, rightX, topDividerY, solidLinePaint)

        // 2. Invoice Number & Date + Party Name
        canvas.drawText("الرقم: ${invoice.invoiceNumber.ifBlank { "0001" }}", rightX, numDateBaselineY, rightBoldPaint)
        canvas.drawText(dateText, leftX, numDateBaselineY, leftPaint)

        canvas.save()
        canvas.translate(leftX, partyTopY)
        partyLayout.draw(canvas)
        canvas.restore()

        // 3. Inverted Black POS Table Header (القيمة | الكمية | الصنف)
        canvas.drawRect(leftX, tableHeaderTop, rightX, tableHeaderBottom, fillBlackPaint)

        val colHeaderWhite = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = (17f * scale).coerceAtLeast(12f)
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val xTotalCenter = leftX + colTotalW / 2f
        val xQtyCenter = leftX + colTotalW + colQtyW / 2f
        val xNameCenter = leftX + colTotalW + colQtyW + colNameW / 2f
        val hdrFm = colHeaderWhite.fontMetrics
        val headerBaseY = (tableHeaderTop + tableHeaderBottom) / 2f - (hdrFm.ascent + hdrFm.descent) / 2f

        canvas.drawText("القيمة", xTotalCenter, headerBaseY, colHeaderWhite)
        canvas.drawText("الكمية", xQtyCenter, headerBaseY, colHeaderWhite)
        canvas.drawText("الصنف والتفاصيل", xNameCenter, headerBaseY, colHeaderWhite)

        // 4. Structured Items Rows (tightly packed)
        val xColNameLeft = leftX + colTotalW + colQtyW
        val cellFm = centerCellPaint.fontMetrics
        val totalCellPaint = TextPaint(centerCellPaint).apply { textSize = (18f * scale).coerceAtLeast(13f) }
        invoice.lines.forEachIndexed { idx, line ->
            val (nameLayout, subLayout, blockH) = itemLayouts[idx]
            val rowTop = itemTops[idx]

            canvas.save()
            canvas.translate(xColNameLeft + 3f, rowTop + (1f * scale))
            nameLayout.draw(canvas)
            canvas.translate(0f, nameLayout.height.toFloat() + (1f * scale))
            subLayout.draw(canvas)
            canvas.restore()

            val valY = rowTop + blockH / 2f - (cellFm.ascent + cellFm.descent) / 2f
            canvas.drawText(NumberFormatUtils.formatNumber(line.unitQty), xQtyCenter, valY, centerCellPaint)
            canvas.drawText(NumberFormatUtils.formatNumber(line.lineTotal), xTotalCenter, valY, totalCellPaint)

            val divY = rowTop + blockH
            canvas.drawLine(leftX, divY, rightX, divY, dashedLinePaint)
        }

        // 5. Totals Box
        canvas.drawLine(leftX, totalsDividerY, rightX, totalsDividerY, solidLinePaint)

        if (showDiscountRows) {
            canvas.drawText("قبل الخصم:", rightX, subtotalBaselineY, rightPaint)
            canvas.drawText("${NumberFormatUtils.formatNumber(invoice.subtotalAmount)} ريال", leftX, subtotalBaselineY, leftPaint)
            canvas.drawText("الخصم:", rightX, discountBaselineY, rightPaint)
            canvas.drawText("-${NumberFormatUtils.formatNumber(invoice.discount)} ريال", leftX, discountBaselineY, leftPaint)
        }

        canvas.drawText(if (showDiscountRows) "الصافي الكلي:" else "الإجمالي الكلي:", rightX, netTotalBaselineY, totalRightLabelPaint)
        canvas.drawText("${NumberFormatUtils.formatNumber(invoice.totalAmount)} ريال", leftX, netTotalBaselineY, totalPaint)

        if (showPaymentRows) {
            canvas.drawText("المدفوع:", rightX, paidBaselineY, rightPaint)
            canvas.drawText("${NumberFormatUtils.formatNumber(invoice.resolvedPaidAmount)} ريال", leftX, paidBaselineY, leftBoldPaint)
            canvas.drawText("المتبقي:", rightX, remainingBaselineY, rightBoldPaint)
            canvas.drawText("${NumberFormatUtils.formatNumber(invoice.resolvedRemainingAmount)} ريال", leftX, remainingBaselineY, leftBoldPaint)
        }

        canvas.drawText("عدد الأصناف:", rightX, itemCountBaselineY, rightPaint)
        canvas.drawText("${invoice.lines.size}", leftX, itemCountBaselineY, leftBoldPaint)
        canvas.drawLine(leftX, bottomTotalsDividerY, rightX, bottomTotalsDividerY, solidLinePaint)

        // 6. Notes & Policy Footer
        if (notesLayout != null) {
            canvas.save()
            canvas.translate(leftX, notesTopY)
            notesLayout.draw(canvas)
            canvas.restore()
        }

        canvas.save()
        canvas.translate(leftX, footerTopY)
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
        val drawHeight = bitmap4K.height * (pageWidth.toFloat() / bitmap4K.width)
        val scaledH = drawHeight.toInt().coerceAtLeast(240)
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, scaledH, 1).create()
        val page = doc.startPage(pageInfo)
        val destRect = RectF(0f, 0f, pageWidth.toFloat(), drawHeight)
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
        val maxPageHeight = 1754
        val margin = 36f

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 31f
            color = Color.parseColor("#132C4A")
            textAlign = Paint.Align.RIGHT
        }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 20f
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }
        val leftPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldTf
            textSize = 20f
            color = Color.parseColor("#15803D")
            textAlign = Paint.Align.LEFT
        }
        val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTf
            textSize = 17f
            color = Color.parseColor("#475569")
            textAlign = Paint.Align.RIGHT
        }
        val divider = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1.2f
        }

        val itemsPerPage = 28
        val chunks = if (products.isEmpty()) listOf(emptyList()) else products.chunked(itemsPerPage)

        chunks.forEachIndexed { pageIndex, chunk ->
            val estimatedPageH = if (chunks.size == 1) {
                (148 + maxOf(1, chunk.size) * 56 + 32).coerceIn(260, maxPageHeight)
            } else {
                maxPageHeight
            }
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, estimatedPageH, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            var y = 52f
            val rightX = pageWidth - margin
            val leftX = margin

            canvas.drawText("$storeName - تقرير جرد المخزون وتكلفة الوحدات", rightX, y, headerPaint)
            y += 26f
            val totalValue = products.sumOf { it.stockBaseQty * it.baseCost }
            canvas.drawText(
                "التاريخ: ${dateTimeFormatter.format(Date())}  |  الأصناف: ${products.size}  |  إجمالي قيمة المخزون: ${NumberFormatUtils.formatNumber(totalValue)} ريال  |  صفحة ${pageIndex + 1}/${chunks.size}",
                rightX,
                y,
                mutedPaint
            )
            y += 14f
            canvas.drawLine(margin, y, pageWidth - margin, y, divider)
            y += 26f

            if (chunk.isEmpty()) {
                canvas.drawText("لا توجد أصناف مسجلة في المخزون.", rightX, y + 24f, rowPaint)
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
                    y += 21f
                    canvas.drawText(
                        "سعر البيع الأساسي: ${NumberFormatUtils.formatNumber(p.baseSellPrice)} ريال${if (p.barcode.isNotBlank()) " | باركود: ${p.barcode}" else ""}",
                        rightX,
                        y,
                        mutedPaint
                    )
                    y += 11f
                    canvas.drawLine(margin, y, pageWidth - margin, y, divider)
                    y += 22f
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

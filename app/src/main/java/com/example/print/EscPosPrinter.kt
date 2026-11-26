package com.example.print

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class PairedPrinterDevice(
    val name: String,
    val address: String
)

data class PrinterSettings(
    val storeName: String = "بقالة العزي",
    val storeSubtitle: String = "للتجارة العامة",
    val storePhone: String = "776425052",
    val storeBadgeText: String = "AZ",
    val footerPolicy: String = "البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل.",
    val paperWidthMm: Int = 80,
    val wifiHost: String = "192.168.1.100",
    val wifiPort: Int = 9100,
    val bluetoothName: String = "",
    val bluetoothAddress: String = "",
    val fastAutoSaveEnabled: Boolean = true
)

object PrinterSettingsStore {
    private const val PREFS_NAME = "warehouse_printer_prefs"

    fun load(context: Context): PrinterSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PrinterSettings(
            storeName = prefs.getString("store_name", "بقالة العزي") ?: "بقالة العزي",
            storeSubtitle = prefs.getString("store_subtitle", "للتجارة العامة") ?: "للتجارة العامة",
            storePhone = prefs.getString("store_phone", "776425052") ?: "776425052",
            storeBadgeText = prefs.getString("store_badge", "AZ") ?: "AZ",
            footerPolicy = prefs.getString(
                "footer_policy",
                "البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل."
            ) ?: "البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل.",
            paperWidthMm = prefs.getInt("paper_width_mm", 80).let { if (it == 58) 58 else 80 },
            wifiHost = prefs.getString("wifi_host", "192.168.1.100") ?: "192.168.1.100",
            wifiPort = prefs.getInt("wifi_port", 9100),
            bluetoothName = prefs.getString("bt_name", "") ?: "",
            bluetoothAddress = prefs.getString("bt_address", "") ?: "",
            fastAutoSaveEnabled = prefs.getBoolean("fast_auto_save_enabled", true)
        )
    }

    fun save(context: Context, settings: PrinterSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("store_name", settings.storeName.trim().ifEmpty { "بقالة العزي" })
            .putString("store_subtitle", settings.storeSubtitle.trim().ifEmpty { "للتجارة العامة" })
            .putString("store_phone", settings.storePhone.trim())
            .putString("store_badge", settings.storeBadgeText.trim().ifEmpty { "AZ" }.take(4))
            .putString("footer_policy", settings.footerPolicy.trim().ifEmpty { "البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل." })
            .putInt("paper_width_mm", if (settings.paperWidthMm == 58) 58 else 80)
            .putString("wifi_host", settings.wifiHost.trim())
            .putInt("wifi_port", settings.wifiPort.coerceIn(1, 65535))
            .putString("bt_name", settings.bluetoothName)
            .putString("bt_address", settings.bluetoothAddress)
            .putBoolean("fast_auto_save_enabled", settings.fastAutoSaveEnabled)
            .apply()
    }
}

object EscPosPrinter {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun requiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
    }

    fun listPairedBluetoothPrinters(context: Context): Result<List<PairedPrinterDevice>> = runCatching {
        if (!hasBluetoothPermissions(context)) {
            throw SecurityException("يرجى منح صلاحية البلوتوث لعرض الطابعات المقترنة")
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
            ?: throw IllegalStateException("البلوتوث غير مدعوم على هذا الجهاز")
        if (!adapter.isEnabled) {
            throw IllegalStateException("يرجى تفعيل البلوتوث أولاً")
        }
        @Suppress("MissingPermission")
        val bonded = adapter.bondedDevices ?: emptySet()
        bonded.map { dev ->
            @Suppress("MissingPermission")
            val devName = dev.name ?: "طابعة بلوتوث"
            PairedPrinterDevice(name = devName, address = dev.address)
        }.sortedBy { it.name }
    }

    /**
     * Converts an ARGB bitmap to ESC/POS GS v 0 raster command chunks (max 256px band height
     * per chunk to prevent thermal printer buffer overflow).
     */
    fun encodeBitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return packMonochromeRowsToEscPosRaster(width, height) { x, y ->
            val color = pixels[y * width + x]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            lum < 185
        }
    }

    fun packMonochromeRowsToEscPosRaster(
        width: Int,
        height: Int,
        isBlackPixel: (x: Int, y: Int) -> Boolean
    ): ByteArray {
        require(width > 0 && height > 0) { "أبعاد الطباعة غير صالحة" }
        val widthBytes = (width + 7) / 8
        val out = ByteArrayOutputStream(widthBytes * height + 128)
        // ESC @ (Initialize printer)
        out.write(byteArrayOf(0x1B, 0x40))

        val maxBandHeight = 256
        var yStart = 0
        while (yStart < height) {
            val bandHeight = minOf(maxBandHeight, height - yStart)
            // GS v 0 m xL xH yL yH
            out.write(
                byteArrayOf(
                    0x1D,
                    0x76,
                    0x30,
                    0x00,
                    (widthBytes and 0xFF).toByte(),
                    ((widthBytes shr 8) and 0xFF).toByte(),
                    (bandHeight and 0xFF).toByte(),
                    ((bandHeight shr 8) and 0xFF).toByte()
                )
            )
            for (y in yStart until (yStart + bandHeight)) {
                for (xb in 0 until widthBytes) {
                    var byteVal = 0
                    for (bit in 0 until 8) {
                        val x = xb * 8 + bit
                        if (x < width && isBlackPixel(x, y)) {
                            byteVal = byteVal or (1 shl (7 - bit))
                        }
                    }
                    out.write(byteVal)
                }
            }
            yStart += bandHeight
        }

        // Feed 2 lines (minimal cutter clearance) & Paper Cut (GS V 66 0)
        out.write(byteArrayOf(0x1B, 0x64, 0x02))
        out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        return out.toByteArray()
    }

    suspend fun printViaBluetoothSpp(
        context: Context,
        macAddress: String,
        bitmap: Bitmap
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(macAddress.isNotBlank()) { "يرجى اختيار طابعة بلوتوث مقترنة أولاً" }
            if (!hasBluetoothPermissions(context)) {
                throw SecurityException("يرجى منح صلاحية البلوتوث للطباعة")
            }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
                ?: throw IllegalStateException("محول البلوتوث غير متوفر")
            if (!adapter.isEnabled) {
                throw IllegalStateException("يرجى تشغيل البلوتوث أولاً")
            }

            val payload = encodeBitmapToEscPosRaster(bitmap)
            val device = adapter.getRemoteDevice(macAddress)
            @Suppress("MissingPermission")
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.use { sock ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    @Suppress("MissingPermission")
                    if (adapter.isDiscovering) adapter.cancelDiscovery()
                }
                @Suppress("MissingPermission")
                sock.connect()
                val stream = sock.outputStream
                // Write in 1024-byte chunks for Bluetooth RFCOMM stability
                var offset = 0
                while (offset < payload.size) {
                    val chunk = minOf(1024, payload.size - offset)
                    stream.write(payload, offset, chunk)
                    offset += chunk
                }
                stream.flush()
                // Allow Bluetooth SPP thermal printer buffer to drain before closing RFCOMM socket
                delay(250)
            }
        }
    }

    suspend fun printViaTcpIp(
        host: String,
        port: Int = 9100,
        bitmap: Bitmap
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanHost = host.trim()
            require(cleanHost.isNotEmpty()) { "يرجى إدخال عنوان IP للطابعة الحرارية" }
            require(port in 1..65535) { "رقم المنفذ غير صالح" }
            val payload = encodeBitmapToEscPosRaster(bitmap)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanHost, port), 5000)
                socket.soTimeout = 5000
                val out = socket.getOutputStream()
                out.write(payload)
                out.flush()
            }
        }
    }

    /**
     * Prints the ultra-crisp 4K receipt via Android's native PrintManager (supports Wi-Fi Direct,
     * Mopria, USB, and Save as PDF system dialog).
     */
    fun printViaSystemPrintManager(
        context: Context,
        jobName: String,
        bitmap4K: Bitmap
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: throw IllegalStateException("خدمة الطباعة غير متوفرة على هذا الجهاز")

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val doc = PdfDocument()
                try {
                    val pageWidth = 595
                    val drawHeight = bitmap4K.height * (pageWidth.toFloat() / bitmap4K.width)
                    val pageHeight = drawHeight.toInt().coerceAtLeast(120)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    val page = doc.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(
                        bitmap4K,
                        null,
                        RectF(0f, 0f, pageWidth.toFloat(), drawHeight),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                    doc.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        doc.writeTo(out)
                        out.flush()
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "خطأ في تجهيز الطباعة")
                } finally {
                    doc.close()
                }
            }
        }

        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}

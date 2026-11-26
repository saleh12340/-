package com.example.data

import android.content.Context
import com.example.domain.InvoiceMode
import com.example.domain.InvoiceType
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.UnitTier
import org.json.JSONArray
import org.json.JSONObject

data class SavedInvoiceDraftLine(
    val productId: String,
    val productName: String,
    val baseUnitName: String,
    val defaultUnitName: String,
    val unitTiers: List<UnitTier>,
    val baseCost: Double,
    val baseSellPrice: Double,
    val stockBaseQty: Double,
    val unitName: String,
    val cumulativeFactor: Double,
    val unitQty: Double,
    val unitPrice: Double,
    val description: String = "",
    val qtyText: String = "",
    val totalText: String = "",
    val explicitTotal: Double? = null
) {
    fun toProductSnapshot(): Product = Product(
        id = productId,
        name = productName,
        baseUnitName = baseUnitName,
        defaultUnitName = defaultUnitName,
        unitTiers = unitTiers,
        baseCost = baseCost,
        baseSellPrice = baseSellPrice,
        stockBaseQty = stockBaseQty
    )
}

data class SavedInvoiceDraft(
    val editingInvoiceId: String? = null,
    val invoiceType: InvoiceType = InvoiceType.SALE,
    val invoiceMode: InvoiceMode = InvoiceMode.STOCK,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val contactId: String = "",
    val manualCustomerName: String = "",
    val customInvoiceNumber: String = "",
    val notes: String = "",
    val discountInput: String = "",
    val paidInput: String = "",
    val isPaidManuallyEdited: Boolean = false,
    val draftSearchQuery: String = "",
    val draftQtyInput: String = "",
    val draftTotalInput: String = "",
    val lines: List<SavedInvoiceDraftLine> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val isEffectivelyEmpty: Boolean
        get() = lines.isEmpty() &&
            editingInvoiceId.isNullOrBlank() &&
            contactId.isBlank() &&
            manualCustomerName.isBlank() &&
            customInvoiceNumber.isBlank() &&
            notes.isBlank() &&
            discountInput.isBlank() &&
            (!isPaidManuallyEdited || paidInput.isBlank()) &&
            draftSearchQuery.isBlank() &&
            draftQtyInput.isBlank() &&
            draftTotalInput.isBlank()
}

/**
 * Persists in-progress invoice drafts instantaneously via SharedPreferences + JSON
 * so switching tabs, rotating the screen, or closing the app never loses unsaved invoice entries,
 * without touching warehouse stock until the user explicitly taps Save.
 */
object InvoiceDraftAutoSaveStore {
    private const val PREFS_NAME = "warehouse_invoice_draft_autosave"
    private const val KEY_DRAFT_JSON = "active_invoice_draft_v1"

    fun save(context: Context, draft: SavedInvoiceDraft) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (draft.isEffectivelyEmpty) {
            prefs.edit().remove(KEY_DRAFT_JSON).apply()
        } else {
            prefs.edit().putString(KEY_DRAFT_JSON, encode(draft)).apply()
        }
    }

    fun load(context: Context): SavedInvoiceDraft? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DRAFT_JSON, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { decode(raw) }.getOrNull()?.takeUnless { it.isEffectivelyEmpty }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DRAFT_JSON)
            .apply()
    }

    fun encode(draft: SavedInvoiceDraft): String {
        val root = JSONObject()
        root.put("editingInvoiceId", draft.editingInvoiceId ?: "")
        root.put("invoiceType", draft.invoiceType.name)
        root.put("invoiceMode", draft.invoiceMode.name)
        root.put("paymentMethod", draft.paymentMethod.name)
        root.put("contactId", draft.contactId)
        root.put("manualCustomerName", draft.manualCustomerName)
        root.put("customInvoiceNumber", draft.customInvoiceNumber)
        root.put("notes", draft.notes)
        root.put("discountInput", draft.discountInput)
        root.put("paidInput", draft.paidInput)
        root.put("isPaidManuallyEdited", draft.isPaidManuallyEdited)
        root.put("draftSearchQuery", draft.draftSearchQuery)
        root.put("draftQtyInput", draft.draftQtyInput)
        root.put("draftTotalInput", draft.draftTotalInput)
        root.put("updatedAtMillis", draft.updatedAtMillis)
        root.put("lines", JSONArray().apply {
            draft.lines.forEach { line ->
                put(JSONObject().apply {
                    put("productId", line.productId)
                    put("productName", line.productName)
                    put("baseUnitName", line.baseUnitName)
                    put("defaultUnitName", line.defaultUnitName)
                    put("unitTiers", JSONArray().apply {
                        line.unitTiers.forEach { t ->
                            put(JSONObject().apply {
                                put("name", t.name)
                                put("factorToPrevious", t.factorToPrevious)
                            })
                        }
                    })
                    put("baseCost", line.baseCost)
                    put("baseSellPrice", line.baseSellPrice)
                    put("stockBaseQty", line.stockBaseQty)
                    put("unitName", line.unitName)
                    put("cumulativeFactor", line.cumulativeFactor)
                    put("unitQty", line.unitQty)
                    put("unitPrice", line.unitPrice)
                    put("description", line.description)
                    put("qtyText", line.qtyText)
                    put("totalText", line.totalText)
                    if (line.explicitTotal != null) {
                        put("explicitTotal", line.explicitTotal)
                    } else {
                        put("explicitTotal", JSONObject.NULL)
                    }
                })
            }
        })
        return root.toString()
    }

    fun decode(json: String): SavedInvoiceDraft {
        val root = JSONObject(json)
        val linesArr = root.optJSONArray("lines") ?: JSONArray()
        val lines = ArrayList<SavedInvoiceDraftLine>(linesArr.length())
        for (i in 0 until linesArr.length()) {
            val lObj = linesArr.getJSONObject(i)
            val tiersArr = lObj.optJSONArray("unitTiers") ?: JSONArray()
            val tiers = ArrayList<UnitTier>(tiersArr.length())
            for (t in 0 until tiersArr.length()) {
                val tObj = tiersArr.getJSONObject(t)
                tiers.add(
                    UnitTier(
                        name = tObj.optString("name", ""),
                        factorToPrevious = tObj.optDouble("factorToPrevious", 1.0)
                    )
                )
            }
            val expTotal = if (lObj.has("explicitTotal") && !lObj.isNull("explicitTotal")) {
                lObj.optDouble("explicitTotal").takeIf { it.isFinite() }
            } else {
                null
            }
            lines.add(
                SavedInvoiceDraftLine(
                    productId = lObj.optString("productId", ""),
                    productName = lObj.optString("productName", ""),
                    baseUnitName = lObj.optString("baseUnitName", "حبة"),
                    defaultUnitName = lObj.optString("defaultUnitName", "حبة"),
                    unitTiers = tiers,
                    baseCost = lObj.optDouble("baseCost", 0.0),
                    baseSellPrice = lObj.optDouble("baseSellPrice", 0.0),
                    stockBaseQty = lObj.optDouble("stockBaseQty", 0.0),
                    unitName = lObj.optString("unitName", "حبة"),
                    cumulativeFactor = lObj.optDouble("cumulativeFactor", 1.0),
                    unitQty = lObj.optDouble("unitQty", 0.0),
                    unitPrice = lObj.optDouble("unitPrice", 0.0),
                    description = lObj.optString("description", ""),
                    qtyText = lObj.optString("qtyText", ""),
                    totalText = lObj.optString("totalText", ""),
                    explicitTotal = expTotal
                )
            )
        }
        return SavedInvoiceDraft(
            editingInvoiceId = root.optString("editingInvoiceId", "").takeIf { it.isNotBlank() },
            invoiceType = runCatching { InvoiceType.valueOf(root.optString("invoiceType", InvoiceType.SALE.name)) }
                .getOrDefault(InvoiceType.SALE),
            invoiceMode = runCatching { InvoiceMode.valueOf(root.optString("invoiceMode", InvoiceMode.STOCK.name)) }
                .getOrDefault(InvoiceMode.STOCK),
            paymentMethod = runCatching { PaymentMethod.valueOf(root.optString("paymentMethod", PaymentMethod.CASH.name)) }
                .getOrDefault(PaymentMethod.CASH),
            contactId = root.optString("contactId", ""),
            manualCustomerName = root.optString("manualCustomerName", ""),
            customInvoiceNumber = root.optString("customInvoiceNumber", ""),
            notes = root.optString("notes", ""),
            discountInput = root.optString("discountInput", ""),
            paidInput = root.optString("paidInput", ""),
            isPaidManuallyEdited = root.optBoolean("isPaidManuallyEdited", false),
            draftSearchQuery = root.optString("draftSearchQuery", ""),
            draftQtyInput = root.optString("draftQtyInput", ""),
            draftTotalInput = root.optString("draftTotalInput", ""),
            lines = lines,
            updatedAtMillis = root.optLong("updatedAtMillis", System.currentTimeMillis())
        )
    }
}

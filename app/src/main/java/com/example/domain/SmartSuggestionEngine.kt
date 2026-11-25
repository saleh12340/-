package com.example.domain

data class ContactSuggestion(
    val displayName: String,
    val contact: Contact?,
    val phone: String = "",
    val balance: Double = 0.0,
    val fromHistoryOnly: Boolean = false
)

object SmartSuggestionEngine {

    fun normalizeSearchText(raw: String): String {
        val digitsNormalized = NumberFormatUtils.toEnglishDigits(raw).trim().lowercase()
        val sb = StringBuilder(digitsNormalized.length)
        for (ch in digitsNormalized) {
            when (ch) {
                'أ', 'إ', 'آ' -> sb.append('ا')
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                'ـ' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun suggestProducts(
        query: String,
        products: List<Product>,
        invoiceType: InvoiceType,
        limit: Int = 6
    ): List<Product> {
        if (products.isEmpty()) return emptyList()
        val normQuery = normalizeSearchText(query)

        if (normQuery.isEmpty()) {
            return if (invoiceType == InvoiceType.SALE) {
                products.sortedWith(
                    compareByDescending<Product> { it.stockBaseQty > 0.0 }
                        .thenByDescending { it.stockBaseQty }
                        .thenBy { it.name.lowercase() }
                ).take(limit)
            } else {
                products.sortedWith(
                    compareBy<Product> { it.stockBaseQty > it.minStockAlert }
                        .thenBy { it.stockBaseQty }
                        .thenBy { it.name.lowercase() }
                ).take(limit)
            }
        }

        val scored = products.mapNotNull { product ->
            val normName = normalizeSearchText(product.name)
            val normBarcode = normalizeSearchText(product.barcode)

            var score = when {
                normBarcode.isNotEmpty() && normBarcode == normQuery -> 100
                normName == normQuery -> 95
                normName.startsWith(normQuery) -> 80
                normName.split(" ").any { it.startsWith(normQuery) } -> 65
                normBarcode.isNotEmpty() && normBarcode.contains(normQuery) -> 55
                normName.contains(normQuery) -> 40
                else -> 0
            }

            if (score <= 0) return@mapNotNull null

            if (invoiceType == InvoiceType.SALE && product.stockBaseQty > 0.0) {
                score += 10
            }
            product to score
        }

        return scored
            .sortedWith(
                compareByDescending<Pair<Product, Int>> { it.second }
                    .thenBy { it.first.name.lowercase() }
            )
            .map { it.first }
            .take(limit)
    }

    fun suggestContacts(
        query: String,
        contacts: List<Contact>,
        savedInvoices: List<Invoice>,
        invoiceType: InvoiceType,
        limit: Int = 6
    ): List<ContactSuggestion> {
        val targetType = if (invoiceType == InvoiceType.PURCHASE) {
            ContactType.SUPPLIER
        } else {
            ContactType.CUSTOMER
        }
        val normQuery = normalizeSearchText(query)

        val registered = contacts
            .filter { it.resolvedType == targetType }
            .map {
                ContactSuggestion(
                    displayName = it.name,
                    contact = it,
                    phone = it.phone,
                    balance = it.balance,
                    fromHistoryOnly = false
                )
            }

        val knownNames = registered.map { normalizeSearchText(it.displayName) }.toMutableSet()
        val historyOnly = mutableListOf<ContactSuggestion>()
        for (inv in savedInvoices) {
            if (inv.resolvedType != invoiceType) continue
            val clean = inv.contactName.trim()
            if (clean.isEmpty() || clean == "عميل نقدي" || clean == "مورد نقدي") continue
            val key = normalizeSearchText(clean)
            if (knownNames.add(key)) {
                historyOnly.add(
                    ContactSuggestion(
                        displayName = clean,
                        contact = null,
                        fromHistoryOnly = true
                    )
                )
            }
        }

        val combined = registered + historyOnly
        if (normQuery.isEmpty()) {
            return combined.take(limit)
        }

        return combined.mapNotNull { item ->
            val normName = normalizeSearchText(item.displayName)
            val normPhone = normalizeSearchText(item.phone)
            val score = when {
                normName == normQuery -> 100
                normName.startsWith(normQuery) -> 80
                normName.split(" ").any { it.startsWith(normQuery) } -> 65
                normPhone.isNotEmpty() && normPhone.contains(normQuery) -> 55
                normName.contains(normQuery) -> 40
                else -> 0
            }
            if (score <= 0) null else item to score
        }
            .sortedWith(
                compareByDescending<Pair<ContactSuggestion, Int>> { it.second }
                    .thenBy { it.first.fromHistoryOnly }
                    .thenBy { it.first.displayName.lowercase() }
            )
            .map { it.first }
            .take(limit)
    }

    fun computeInferredUnitPrice(
        lineTotal: Double,
        unitQty: Double,
        fallbackUnitPrice: Double
    ): Double {
        return if (unitQty > 0.0 && lineTotal.isFinite()) {
            lineTotal / unitQty
        } else {
            fallbackUnitPrice
        }
    }
}

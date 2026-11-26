package com.example.data

import com.example.domain.Contact
import com.example.domain.ContactType
import com.example.domain.Invoice
import com.example.domain.InvoiceType
import com.example.domain.InvoiceMode
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.Product
import com.example.domain.UnitTier
import kotlinx.coroutines.flow.Flow

data class InvoiceDraftLine(
    val productId: String,
    val unitName: String,
    val unitQty: Double,
    val unitPrice: Double,
    // Present only for a not-yet-committed purchase item or a free-sale line.
    // It is transient UI payload and is never serialized into the invoice document.
    val draftProduct: Product? = null,
    val invoiceMode: InvoiceMode = InvoiceMode.STOCK
)

data class BackupBundle(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val products: List<Product> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val payments: List<Payment> = emptyList()
)

data class RestoreSummary(
    val importedProducts: Int,
    val skippedDuplicateProducts: Int,
    val importedContacts: Int,
    val importedInvoices: Int,
    val importedPayments: Int
)

interface WarehouseDataSource {
    val isCloudBacked: Boolean

    fun observeProducts(userId: String): Flow<List<Product>>
    fun observeContacts(userId: String): Flow<List<Contact>>
    fun observeInvoices(userId: String): Flow<List<Invoice>>
    fun observePayments(userId: String): Flow<List<Payment>>

    suspend fun saveProduct(
        userId: String,
        existingId: String?,
        name: String,
        barcode: String,
        baseUnitName: String,
        defaultUnitName: String,
        unitTiers: List<UnitTier>,
        baseCost: Double,
        baseSellPrice: Double,
        stockBaseQty: Double,
        trackExpiry: Boolean,
        expiryDate: String,
        minStockAlert: Double
    ): Result<String>

    suspend fun deleteProduct(userId: String, productId: String): Result<Unit>

    suspend fun saveContact(
        userId: String,
        existingId: String?,
        name: String,
        contactType: ContactType,
        phone: String,
        openingBalance: Double,
        notes: String
    ): Result<String>

    suspend fun deleteContact(userId: String, contactId: String): Result<Unit>

    suspend fun postInvoice(
        userId: String,
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        draftLines: List<InvoiceDraftLine>,
        notes: String
    ): Result<Invoice>

    suspend fun updateInvoice(
        userId: String,
        invoiceId: String,
        invoiceNumber: String,
        invoiceType: InvoiceType,
        paymentMethod: PaymentMethod,
        contactId: String,
        contactName: String,
        draftLines: List<InvoiceDraftLine>,
        notes: String
    ): Result<Invoice>

    suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit>

    suspend fun recordPayment(
        userId: String,
        contactId: String,
        amount: Double,
        notes: String
    ): Result<Unit>

    suspend fun updatePayment(
        userId: String,
        paymentId: String,
        amount: Double,
        notes: String
    ): Result<Unit>

    suspend fun deletePayment(userId: String, paymentId: String): Result<Unit>

    suspend fun exportBackup(userId: String): Result<BackupBundle>

    suspend fun importBackup(userId: String, bundle: BackupBundle): Result<RestoreSummary>
}

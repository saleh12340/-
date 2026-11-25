package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UnitTier(
    val name: String = "",
    val factorToPrevious: Double = 1.0
)

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val barcode: String = "",
    val baseUnitName: String = "حبة",
    val defaultUnitName: String = "حبة",
    val unitTiers: List<UnitTier> = emptyList(),
    val baseCost: Double = 0.0,
    val baseSellPrice: Double = 0.0,
    val stockBaseQty: Double = 0.0,
    val trackExpiry: Boolean = false,
    val expiryDate: String = "",
    val minStockAlert: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ContactType {
    CUSTOMER,
    SUPPLIER;

    companion object {
        fun fromRaw(raw: String?): ContactType =
            entries.firstOrNull { it.name == raw } ?: CUSTOMER
    }
}

@Keep
@IgnoreExtraProperties
data class Contact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val contactType: String = ContactType.CUSTOMER.name,
    val phone: String = "",
    val balance: Double = 0.0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedType: ContactType get() = ContactType.fromRaw(contactType)
}

enum class InvoiceType {
    PURCHASE,
    SALE;

    companion object {
        fun fromRaw(raw: String?): InvoiceType =
            entries.firstOrNull { it.name == raw } ?: SALE
    }
}

enum class PaymentMethod {
    CASH,
    CREDIT;

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

@Keep
@IgnoreExtraProperties
data class InvoiceLine(
    val productId: String = "",
    val productName: String = "",
    val unitName: String = "",
    val cumulativeFactor: Double = 1.0,
    val unitQty: Double = 0.0,
    val unitPrice: Double = 0.0,
    val baseQty: Double = 0.0,
    val lineTotal: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class Invoice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceNumber: String = "",
    val invoiceType: String = InvoiceType.SALE.name,
    val paymentMethod: String = PaymentMethod.CASH.name,
    val contactId: String = "",
    val contactName: String = "",
    val totalAmount: Double = 0.0,
    val lines: List<InvoiceLine> = emptyList(),
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedType: InvoiceType get() = InvoiceType.fromRaw(invoiceType)
    val resolvedPaymentMethod: PaymentMethod get() = PaymentMethod.fromRaw(paymentMethod)
}

@Keep
@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val contactType: String = ContactType.CUSTOMER.name,
    val amount: Double = 0.0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedContactType: ContactType get() = ContactType.fromRaw(contactType)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

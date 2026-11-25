package com.example.domain

import kotlin.math.abs

data class ResolvedUnitLevel(
    val index: Int,
    val name: String,
    val factorToPrevious: Double,
    val cumulativeFactor: Double
)

sealed interface CostCalculationResult {
    data class Success(
        val baseCost: Double,
        val largeUnitCost: Double,
        val cumulativeFactor: Double
    ) : CostCalculationResult

    data object BlankInput : CostCalculationResult
    data class Error(val message: String) : CostCalculationResult
}

data class StockReconciliation(
    val preservedStockBaseQty: Double,
    val preservedTotalValue: Double,
    val preservedBaseCost: Double,
    val previousLargestUnitCount: Double,
    val newLargestUnitCount: Double,
    val newLargestUnitCost: Double
)

data class ComputedPurchaseLine(
    val line: InvoiceLine,
    val newStockBaseQty: Double,
    val updatedBaseCost: Double,
    val addedValue: Double
)

data class ComputedSaleLine(
    val line: InvoiceLine,
    val newStockBaseQty: Double
)

object UnitCostEngine {

    /**
     * Resolves all configured unit levels from smallest base unit up to the largest unit.
     * Prevents duplicate unit names, blank names, circular hierarchies, and non-positive factors.
     */
    fun resolveUnitLevels(
        baseUnitName: String,
        tiers: List<UnitTier>
    ): Result<List<ResolvedUnitLevel>> {
        val cleanBase = baseUnitName.trim()
        if (cleanBase.isEmpty()) {
            return Result.failure(IllegalArgumentException("يجب إدخال اسم الوحدة الأصغر"))
        }

        val seenNames = mutableSetOf(cleanBase.lowercase())
        val resolved = mutableListOf(
            ResolvedUnitLevel(
                index = 0,
                name = cleanBase,
                factorToPrevious = 1.0,
                cumulativeFactor = 1.0
            )
        )

        var runningFactor = 1.0
        for ((idx, tier) in tiers.withIndex()) {
            val cleanTierName = tier.name.trim()
            if (cleanTierName.isEmpty()) {
                return Result.failure(IllegalArgumentException("يرجى إدخال اسم الوحدة للمستوى رقم ${idx + 1}"))
            }
            val key = cleanTierName.lowercase()
            if (!seenNames.add(key)) {
                return Result.failure(IllegalArgumentException("لا يمكن تكرار اسم الوحدة '$cleanTierName' أو إنشاء تحويل دائري"))
            }
            if (!tier.factorToPrevious.isFinite() || tier.factorToPrevious <= 0.0) {
                return Result.failure(IllegalArgumentException("معامل التحويل لوحدة '$cleanTierName' يجب أن يكون أكبر من 0"))
            }
            runningFactor *= tier.factorToPrevious
            if (!runningFactor.isFinite() || runningFactor <= 0.0) {
                return Result.failure(IllegalArgumentException("معامل التحويل التراكمي غير صالح"))
            }
            resolved.add(
                ResolvedUnitLevel(
                    index = idx + 1,
                    name = cleanTierName,
                    factorToPrevious = tier.factorToPrevious,
                    cumulativeFactor = runningFactor
                )
            )
        }
        return Result.success(resolved)
    }

    /**
     * Returns the cumulative conversion factor of the largest configured unit (or 1.0 if only base unit exists).
     * Returns null if any tier has an invalid (<= 0, blank, or non-finite) conversion factor.
     */
    fun computeLargestCumulativeFactor(
        baseUnitName: String,
        tiers: List<UnitTier>
    ): Result<Double> {
        val levelsResult = resolveUnitLevels(baseUnitName, tiers)
        return levelsResult.map { levels -> levels.last().cumulativeFactor }
    }

    /**
     * Calculates full-precision base unit cost from the entered large-unit purchase price
     * and the cumulative conversion factor. Rejects negative prices, zero/empty/invalid factors,
     * and avoids division when input is blank.
     */
    fun calculateBaseCostFromLargeUnit(
        largeUnitCostRaw: String,
        cumulativeFactor: Double?
    ): CostCalculationResult {
        val normalized = NumberFormatUtils.toEnglishDigits(largeUnitCostRaw).trim()
        if (normalized.isEmpty()) {
            return CostCalculationResult.BlankInput
        }
        val parsed = NumberFormatUtils.parseDecimal(normalized)
            ?: return CostCalculationResult.Error("يرجى إدخال سعر تكلفة صالح")
        if (parsed < 0.0) {
            return CostCalculationResult.Error("لا يمكن إدخال قيمة سالبة لتكلفة الشراء")
        }
        if (cumulativeFactor == null || !cumulativeFactor.isFinite() || cumulativeFactor <= 0.0) {
            return CostCalculationResult.Error("يرجى استكمال معامل التحويل (أكبر من 0) لحساب تكلفة الحبة")
        }
        val baseCost = parsed / cumulativeFactor
        if (!baseCost.isFinite() || baseCost < 0.0) {
            return CostCalculationResult.Error("تعذر حساب تكلفة الوحدة الأصغر")
        }
        return CostCalculationResult.Success(
            baseCost = baseCost,
            largeUnitCost = parsed,
            cumulativeFactor = cumulativeFactor
        )
    }

    fun calculateLargeUnitCostFromBase(baseCost: Double, cumulativeFactor: Double): Double {
        if (!baseCost.isFinite() || baseCost < 0.0 || !cumulativeFactor.isFinite() || cumulativeFactor <= 0.0) {
            return 0.0
        }
        return baseCost * cumulativeFactor
    }

    /**
     * Selling price for any unit tier is derived strictly from the smallest unit's selling price.
     */
    fun calculateUnitSellPrice(baseSellPrice: Double, cumulativeFactor: Double): Double {
        if (!baseSellPrice.isFinite() || baseSellPrice < 0.0 || !cumulativeFactor.isFinite() || cumulativeFactor <= 0.0) {
            return 0.0
        }
        return baseSellPrice * cumulativeFactor
    }

    /**
     * Protects physical stock quantity (stored in base units) and total inventory value
     * when unit conversion factors are modified on an existing product.
     */
    fun reconcileStockOnUnitChange(
        stockBaseQty: Double,
        baseCost: Double,
        oldLargestFactor: Double,
        newLargestFactor: Double
    ): StockReconciliation {
        val safeStock = stockBaseQty.coerceAtLeast(0.0)
        val safeCost = baseCost.coerceAtLeast(0.0)
        val safeOldFactor = if (oldLargestFactor > 0.0) oldLargestFactor else 1.0
        val safeNewFactor = if (newLargestFactor > 0.0) newLargestFactor else 1.0
        val totalValue = safeStock * safeCost
        return StockReconciliation(
            preservedStockBaseQty = safeStock,
            preservedTotalValue = totalValue,
            preservedBaseCost = safeCost,
            previousLargestUnitCount = safeStock / safeOldFactor,
            newLargestUnitCount = safeStock / safeNewFactor,
            newLargestUnitCost = safeCost * safeNewFactor
        )
    }

    fun findUnitFactor(product: Product, unitName: String): Double {
        val levels = resolveUnitLevels(product.baseUnitName, product.unitTiers).getOrNull()
            ?: return 1.0
        return levels.firstOrNull { it.name.equals(unitName.trim(), ignoreCase = true) }?.cumulativeFactor ?: 1.0
    }

    fun getAvailableUnits(product: Product): List<ResolvedUnitLevel> {
        return resolveUnitLevels(product.baseUnitName, product.unitTiers).getOrElse {
            listOf(ResolvedUnitLevel(0, product.baseUnitName.ifBlank { "حبة" }, 1.0, 1.0))
        }
    }

    /**
     * Computes stock addition and weighted-average base unit cost for a purchase invoice line.
     * Example: 2 cartons x 2400 (24 pcs/carton) -> adds 48 base units, line total 4800, base cost 100.
     */
    fun computePurchaseLine(
        product: Product,
        unitName: String,
        unitQty: Double,
        unitPrice: Double
    ): Result<ComputedPurchaseLine> {
        if (!unitQty.isFinite() || unitQty <= 0.0) {
            return Result.failure(IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر"))
        }
        if (!unitPrice.isFinite() || unitPrice < 0.0) {
            return Result.failure(IllegalArgumentException("سعر الشراء لا يمكن أن يكون سالبًا"))
        }
        val levels = resolveUnitLevels(product.baseUnitName, product.unitTiers)
            .getOrElse { return Result.failure(it) }
        val selectedLevel = levels.firstOrNull { it.name.equals(unitName.trim(), ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("الوحدة المختارة غير معرفة للصنف"))

        val addedBaseQty = unitQty * selectedLevel.cumulativeFactor
        val lineTotal = unitQty * unitPrice
        val oldQty = product.stockBaseQty.coerceAtLeast(0.0)
        val newTotalQty = oldQty + addedBaseQty

        val updatedBaseCost = if (oldQty <= 0.000001 || abs(product.baseCost) <= 0.000001) {
            unitPrice / selectedLevel.cumulativeFactor
        } else {
            val oldValue = oldQty * product.baseCost
            (oldValue + lineTotal) / newTotalQty
        }

        val line = InvoiceLine(
            productId = product.id,
            productName = product.name,
            unitName = selectedLevel.name,
            cumulativeFactor = selectedLevel.cumulativeFactor,
            unitQty = unitQty,
            unitPrice = unitPrice,
            baseQty = addedBaseQty,
            lineTotal = lineTotal
        )
        return Result.success(
            ComputedPurchaseLine(
                line = line,
                newStockBaseQty = newTotalQty,
                updatedBaseCost = updatedBaseCost,
                addedValue = lineTotal
            )
        )
    }

    /**
     * Computes stock deduction and total for a sales invoice line using base unit selling price.
     * Example: 10 pieces x 150 = 1500, or 1 carton (24 pcs) x (150 * 24) = 3600.
     */
    fun computeSaleLine(
        product: Product,
        unitName: String,
        unitQty: Double,
        customUnitPrice: Double? = null
    ): Result<ComputedSaleLine> {
        if (!unitQty.isFinite() || unitQty <= 0.0) {
            return Result.failure(IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر"))
        }
        val levels = resolveUnitLevels(product.baseUnitName, product.unitTiers)
            .getOrElse { return Result.failure(it) }
        val selectedLevel = levels.firstOrNull { it.name.equals(unitName.trim(), ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("الوحدة المختارة غير معرفة للصنف"))

        val deductedBaseQty = unitQty * selectedLevel.cumulativeFactor
        if (deductedBaseQty - product.stockBaseQty > 0.0001) {
            return Result.failure(
                IllegalArgumentException(
                    "الكمية المطلوبة (${NumberFormatUtils.formatNumber(deductedBaseQty)} ${product.baseUnitName}) تتجاوز المتاح بالمخزون (${NumberFormatUtils.formatNumber(product.stockBaseQty)})"
                )
            )
        }
        val unitPrice = customUnitPrice ?: calculateUnitSellPrice(product.baseSellPrice, selectedLevel.cumulativeFactor)
        if (!unitPrice.isFinite() || unitPrice < 0.0) {
            return Result.failure(IllegalArgumentException("سعر البيع غير صالح"))
        }
        val lineTotal = unitQty * unitPrice
        val line = InvoiceLine(
            productId = product.id,
            productName = product.name,
            unitName = selectedLevel.name,
            cumulativeFactor = selectedLevel.cumulativeFactor,
            unitQty = unitQty,
            unitPrice = unitPrice,
            baseQty = deductedBaseQty,
            lineTotal = lineTotal
        )
        return Result.success(
            ComputedSaleLine(
                line = line,
                newStockBaseQty = (product.stockBaseQty - deductedBaseQty).coerceAtLeast(0.0)
            )
        )
    }
}

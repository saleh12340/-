package com.example.ui.state

import com.example.domain.CostCalculationResult
import com.example.domain.NumberFormatUtils
import com.example.domain.Product
import com.example.domain.StockReconciliation
import com.example.domain.UnitCostEngine
import com.example.domain.UnitTier

data class UnitTierDraft(
    val id: String,
    val name: String,
    val factorText: String
)

data class ProductFormState(
    val editingProductId: String? = null,
    val name: String = "",
    val barcode: String = "",
    val baseUnitName: String = "حبة",
    val defaultUnitName: String = "كرتون",
    val unitTiers: List<UnitTierDraft> = listOf(
        UnitTierDraft(id = "tier_1", name = "كرتون", factorText = "")
    ),
    val largeUnitCostText: String = "",
    val baseCost: Double = 0.0,
    val baseCostText: String = "",
    val hasManualBaseCostOverride: Boolean = false,
    val baseSellPrice: Double = 0.0,
    val baseSellPriceText: String = "",
    val stockBaseQty: Double = 0.0,
    val stockBaseQtyText: String = "0",
    val trackExpiry: Boolean = false,
    val expiryDate: String = "",
    val minStockAlertText: String = "0",
    val originalStockBaseQty: Double = 0.0,
    val originalBaseCost: Double = 0.0,
    val originalLargestFactor: Double = 1.0,
    val costWarning: String? = null,
    val unitError: String? = null,
    val formError: String? = null,
    val isSaving: Boolean = false
) {
    val largestUnitName: String
        get() = unitTiers.lastOrNull()?.name?.trim()?.ifEmpty { "الوحدة الكبيرة" }
            ?: baseUnitName.trim().ifEmpty { "الوحدة الأصغر" }

    val parsedTiers: List<UnitTier>?
        get() {
            if (unitTiers.isEmpty()) return emptyList()
            val list = mutableListOf<UnitTier>()
            for (draft in unitTiers) {
                val factor = NumberFormatUtils.parseDecimal(draft.factorText) ?: return null
                if (!factor.isFinite() || factor <= 0.0) return null
                list.add(UnitTier(name = draft.name.trim(), factorToPrevious = factor))
            }
            return list
        }

    val cumulativeFactor: Double?
        get() {
            if (unitTiers.isEmpty()) return 1.0
            val tiers = parsedTiers ?: return null
            return UnitCostEngine.computeLargestCumulativeFactor(baseUnitName, tiers).getOrNull()
        }

    val computedLargeUnitSellPrice: Double
        get() {
            val factor = cumulativeFactor ?: 1.0
            return UnitCostEngine.calculateUnitSellPrice(baseSellPrice, factor)
        }

    val unitProfit: Double
        get() = baseSellPrice - baseCost

    val marginPercent: Double
        get() = if (baseSellPrice > 0.0) ((baseSellPrice - baseCost) / baseSellPrice) * 100.0 else 0.0

    val stockReconciliation: StockReconciliation?
        get() {
            if (editingProductId == null || originalStockBaseQty <= 0.0) return null
            val newFactor = cumulativeFactor ?: return null
            return if (newFactor != originalLargestFactor) {
                UnitCostEngine.reconcileStockOnUnitChange(
                    stockBaseQty = stockBaseQty,
                    baseCost = baseCost,
                    oldLargestFactor = originalLargestFactor,
                    newLargestFactor = newFactor
                )
            } else null
        }
}

class ProductFormController(
    initialState: ProductFormState = ProductFormState()
) {
    var state: ProductFormState = initialState
        private set

    private var nextTierCounter = 100

    fun resetForNewProduct() {
        state = ProductFormState(
            editingProductId = null,
            name = "",
            barcode = "",
            baseUnitName = "حبة",
            defaultUnitName = "كرتون",
            unitTiers = listOf(UnitTierDraft(id = "tier_1", name = "كرتون", factorText = "")),
            largeUnitCostText = "",
            baseCost = 0.0,
            baseCostText = "",
            hasManualBaseCostOverride = false,
            baseSellPrice = 0.0,
            baseSellPriceText = "",
            stockBaseQty = 0.0,
            stockBaseQtyText = "0",
            trackExpiry = false,
            expiryDate = "",
            minStockAlertText = "0"
        )
    }

    fun loadExistingProduct(product: Product) {
        val tiers = product.unitTiers.mapIndexed { idx, tier ->
            UnitTierDraft(
                id = "tier_${idx + 1}",
                name = tier.name,
                factorText = NumberFormatUtils.formatInputNumber(tier.factorToPrevious, 4)
            )
        }
        val factor = UnitCostEngine.computeLargestCumulativeFactor(product.baseUnitName, product.unitTiers)
            .getOrElse { 1.0 }

        val largeUnitCostDisplay = if (product.unitTiers.isNotEmpty() && factor > 0.0 && product.baseCost > 0.0) {
            NumberFormatUtils.formatInputNumber(
                UnitCostEngine.calculateLargeUnitCostFromBase(product.baseCost, factor),
                2
            )
        } else if (product.unitTiers.isNotEmpty() && product.baseCost == 0.0) {
            "0"
        } else {
            ""
        }

        val baseCostDisplay = if (product.baseCost > 0.0) {
            NumberFormatUtils.formatInputNumber(product.baseCost, 2)
        } else {
            "0"
        }

        val sellPriceDisplay = if (product.baseSellPrice > 0.0) {
            NumberFormatUtils.formatInputNumber(product.baseSellPrice, 2)
        } else {
            "0"
        }

        state = ProductFormState(
            editingProductId = product.id,
            name = product.name,
            barcode = product.barcode,
            baseUnitName = product.baseUnitName,
            defaultUnitName = product.defaultUnitName,
            unitTiers = tiers,
            largeUnitCostText = largeUnitCostDisplay,
            baseCost = product.baseCost,
            baseCostText = baseCostDisplay,
            // Preserve saved baseCost cleanly without auto-clobbering on screen open
            hasManualBaseCostOverride = true,
            baseSellPrice = product.baseSellPrice,
            baseSellPriceText = sellPriceDisplay,
            stockBaseQty = product.stockBaseQty,
            stockBaseQtyText = NumberFormatUtils.formatInputNumber(product.stockBaseQty, 2),
            trackExpiry = product.trackExpiry,
            expiryDate = product.expiryDate,
            minStockAlertText = NumberFormatUtils.formatInputNumber(product.minStockAlert, 2),
            originalStockBaseQty = product.stockBaseQty,
            originalBaseCost = product.baseCost,
            originalLargestFactor = factor
        )
    }

    fun onNameChanged(value: String) {
        state = state.copy(name = value, formError = null)
    }

    fun onBarcodeChanged(value: String) {
        state = state.copy(barcode = NumberFormatUtils.toEnglishDigits(value), formError = null)
    }

    fun onDefaultUnitChanged(value: String) {
        state = state.copy(defaultUnitName = value, formError = null)
    }

    fun onTrackExpiryChanged(enabled: Boolean) {
        state = state.copy(trackExpiry = enabled)
    }

    fun onExpiryDateChanged(value: String) {
        state = state.copy(expiryDate = NumberFormatUtils.toEnglishDigits(value))
    }

    fun onMinStockAlertChanged(raw: String) {
        val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
        if (clean.startsWith("-")) return
        state = state.copy(minStockAlertText = clean)
    }

    fun onStockBaseQtyChanged(raw: String) {
        val clean = NumberFormatUtils.sanitizeDecimalInput(raw)
        if (clean.startsWith("-")) {
            state = state.copy(formError = "كمية المخزون لا يمكن أن تكون سالبة")
            return
        }
        val parsed = NumberFormatUtils.parseDecimal(clean)
        state = state.copy(
            stockBaseQtyText = clean,
            stockBaseQty = parsed ?: if (clean.isEmpty()) 0.0 else state.stockBaseQty,
            formError = null
        )
    }

    fun onBaseUnitNameChanged(name: String) {
        val updated = state.copy(baseUnitName = name, formError = null)
        state = validateUnitHierarchy(updated)
    }

    fun addUnitTier() {
        if (state.unitTiers.size >= 5) {
            state = state.copy(unitError = "الحد الأقصى هو 5 مستويات إضافية للوحدات")
            return
        }
        nextTierCounter++
        val suggestedName = when (state.unitTiers.size) {
            0 -> "كرتون"
            1 -> "قرطاس"
            else -> "وحدة ${state.unitTiers.size + 1}"
        }
        val updatedTiers = state.unitTiers + UnitTierDraft(
            id = "tier_$nextTierCounter",
            name = suggestedName,
            factorText = ""
        )
        val updated = validateUnitHierarchy(state.copy(unitTiers = updatedTiers))
        state = recalculateFromConversionChange(updated)
    }

    fun removeUnitTier(index: Int) {
        if (index !in state.unitTiers.indices) return
        val updatedTiers = state.unitTiers.toMutableList().apply { removeAt(index) }
        val newDefault = if (state.defaultUnitName == state.unitTiers[index].name) {
            updatedTiers.lastOrNull()?.name ?: state.baseUnitName
        } else {
            state.defaultUnitName
        }
        val updated = validateUnitHierarchy(
            state.copy(unitTiers = updatedTiers, defaultUnitName = newDefault)
        )
        state = recalculateFromConversionChange(updated)
    }

    fun onUnitTierNameChanged(index: Int, newName: String) {
        if (index !in state.unitTiers.indices) return
        val oldName = state.unitTiers[index].name
        val updatedTiers = state.unitTiers.toMutableList().apply {
            this[index] = this[index].copy(name = newName)
        }
        val updatedDefault = if (state.defaultUnitName == oldName) newName else state.defaultUnitName
        val updated = validateUnitHierarchy(
            state.copy(unitTiers = updatedTiers, defaultUnitName = updatedDefault)
        )
        // Changing unit name preserves conversion factor & continues automatic calculation
        state = updated
    }

    fun onUnitTierFactorChanged(index: Int, rawInput: String) {
        if (index !in state.unitTiers.indices) return
        val sanitized = NumberFormatUtils.sanitizeDecimalInput(rawInput)
        val updatedTiers = state.unitTiers.toMutableList().apply {
            this[index] = this[index].copy(factorText = sanitized)
        }
        val updated = validateUnitHierarchy(state.copy(unitTiers = updatedTiers, formError = null))
        state = recalculateFromConversionChange(updated)
    }

    /**
     * Triggered when user enters or modifies the Large Unit Cost field.
     * Clears manual baseCost override and recalculates baseCost automatically.
     */
    fun onLargeUnitCostChanged(rawInput: String) {
        val sanitized = NumberFormatUtils.sanitizeDecimalInput(rawInput)
        val factor = computeCurrentFactor(state)

        when (val result = UnitCostEngine.calculateBaseCostFromLargeUnit(sanitized, factor)) {
            is CostCalculationResult.BlankInput -> {
                state = state.copy(
                    largeUnitCostText = sanitized,
                    costWarning = null,
                    formError = null
                )
            }
            is CostCalculationResult.Error -> {
                // Reject negative or invalid division; keep last valid baseCost intact!
                state = state.copy(
                    largeUnitCostText = sanitized,
                    costWarning = result.message,
                    formError = null
                )
            }
            is CostCalculationResult.Success -> {
                state = state.copy(
                    largeUnitCostText = sanitized,
                    baseCost = result.baseCost,
                    baseCostText = NumberFormatUtils.formatInputNumber(result.baseCost, 2),
                    hasManualBaseCostOverride = false,
                    costWarning = null,
                    formError = null
                )
            }
        }
    }

    /**
     * Triggered when user manually edits the Smallest Unit Cost (baseCost) field.
     * Preserves manual input and prevents automatic overwrite from largeUnitCostText.
     */
    fun onBaseCostManuallyChanged(rawInput: String) {
        val sanitized = NumberFormatUtils.sanitizeDecimalInput(rawInput)
        if (sanitized.isEmpty()) {
            state = state.copy(
                baseCostText = "",
                hasManualBaseCostOverride = true,
                costWarning = null,
                formError = null
            )
            return
        }
        val parsed = NumberFormatUtils.parseDecimal(sanitized)
        if (parsed == null) {
            state = state.copy(
                baseCostText = sanitized,
                hasManualBaseCostOverride = true,
                costWarning = "يرجى إدخال تكلفة صالحة للوحدة الأصغر"
            )
            return
        }
        if (parsed < 0.0) {
            state = state.copy(
                baseCostText = sanitized,
                hasManualBaseCostOverride = true,
                costWarning = "لا يمكن إدخال قيمة سالبة لتكلفة الوحدة الأصغر"
            )
            return
        }
        state = state.copy(
            baseCostText = sanitized,
            baseCost = parsed,
            hasManualBaseCostOverride = true,
            costWarning = null,
            formError = null
        )
    }

    /**
     * Selling price is completely isolated from purchase cost.
     */
    fun onBaseSellPriceChanged(rawInput: String) {
        val sanitized = NumberFormatUtils.sanitizeDecimalInput(rawInput)
        if (sanitized.isEmpty()) {
            state = state.copy(baseSellPriceText = "", baseSellPrice = 0.0, formError = null)
            return
        }
        val parsed = NumberFormatUtils.parseDecimal(sanitized)
        if (parsed != null && parsed < 0.0) {
            state = state.copy(baseSellPriceText = sanitized, formError = "سعر البيع لا يمكن أن يكون سالبًا")
            return
        }
        state = state.copy(
            baseSellPriceText = sanitized,
            baseSellPrice = parsed ?: state.baseSellPrice,
            formError = null
        )
    }

    fun setSaving(saving: Boolean) {
        state = state.copy(isSaving = saving)
    }

    fun setFormError(message: String?) {
        state = state.copy(formError = message, isSaving = false)
    }

    fun validateForSubmit(): Result<ValidatedProductSubmission> {
        val cleanName = state.name.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال اسم المنتج"))
        }
        val cleanBaseUnit = state.baseUnitName.trim()
        if (cleanBaseUnit.isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال اسم الوحدة الأصغر"))
        }
        val tiers = state.parsedTiers
        if (tiers == null) {
            return Result.failure(IllegalArgumentException("يرجى استكمال معاملات التحويل بقيم أكبر من 0"))
        }
        val levels = UnitCostEngine.resolveUnitLevels(cleanBaseUnit, tiers)
            .getOrElse { return Result.failure(it) }

        val largeParsed = NumberFormatUtils.parseDecimal(state.largeUnitCostText)
        if (largeParsed != null && largeParsed < 0.0) {
            return Result.failure(IllegalArgumentException("لا يمكن حفظ قيمة سالبة لتكلفة الشراء"))
        }

        val manualBaseParsed = NumberFormatUtils.parseDecimal(state.baseCostText)
        if (manualBaseParsed != null && manualBaseParsed < 0.0) {
            return Result.failure(IllegalArgumentException("تكلفة الوحدة الأصغر لا يمكن أن تكون سالبة"))
        }
        if (state.baseCost < 0.0) {
            return Result.failure(IllegalArgumentException("تكلفة الوحدة الأصغر غير صالحة"))
        }

        if (state.baseSellPrice < 0.0) {
            return Result.failure(IllegalArgumentException("سعر البيع لا يمكن أن يكون سالبًا"))
        }
        if (state.stockBaseQty < 0.0) {
            return Result.failure(IllegalArgumentException("كمية المخزون لا يمكن أن تكون سالبة"))
        }
        val minAlert = NumberFormatUtils.parseDecimal(state.minStockAlertText) ?: 0.0
        if (minAlert < 0.0) {
            return Result.failure(IllegalArgumentException("حد التنبيه لا يمكن أن يكون سالبًا"))
        }

        val validUnitNames = levels.map { it.name }
        val resolvedDefaultUnit = if (validUnitNames.any { it.equals(state.defaultUnitName.trim(), ignoreCase = true) }) {
            validUnitNames.first { it.equals(state.defaultUnitName.trim(), ignoreCase = true) }
        } else {
            levels.last().name
        }

        val finalBaseCost = if (!state.hasManualBaseCostOverride && state.largeUnitCostText.isNotBlank()) {
            val cumulative = levels.last().cumulativeFactor
            when (val calc = UnitCostEngine.calculateBaseCostFromLargeUnit(state.largeUnitCostText, cumulative)) {
                is CostCalculationResult.Success -> calc.baseCost
                else -> state.baseCost
            }
        } else {
            state.baseCost
        }

        return Result.success(
            ValidatedProductSubmission(
                existingId = state.editingProductId,
                name = cleanName,
                barcode = state.barcode.trim(),
                baseUnitName = cleanBaseUnit,
                defaultUnitName = resolvedDefaultUnit,
                unitTiers = tiers,
                baseCost = finalBaseCost,
                baseSellPrice = state.baseSellPrice,
                stockBaseQty = state.stockBaseQty,
                trackExpiry = state.trackExpiry,
                expiryDate = if (state.trackExpiry) state.expiryDate.trim() else "",
                minStockAlert = minAlert
            )
        )
    }

    private fun computeCurrentFactor(target: ProductFormState): Double? {
        if (target.unitTiers.isEmpty()) return 1.0
        val tiers = target.parsedTiers ?: return null
        return UnitCostEngine.computeLargestCumulativeFactor(target.baseUnitName, tiers).getOrNull()
    }

    private fun validateUnitHierarchy(target: ProductFormState): ProductFormState {
        if (target.baseUnitName.trim().isEmpty()) {
            return target.copy(unitError = "يرجى إدخال اسم الوحدة الأصغر")
        }
        val seen = mutableSetOf(target.baseUnitName.trim().lowercase())
        for (draft in target.unitTiers) {
            val cleanName = draft.name.trim()
            if (cleanName.isEmpty()) {
                return target.copy(unitError = "يرجى إدخال اسم كل وحدة")
            }
            if (!seen.add(cleanName.lowercase())) {
                return target.copy(unitError = "لا يمكن تكرار اسم الوحدة '$cleanName' أو إنشاء تحويل دائري")
            }
            val parsedFactor = NumberFormatUtils.parseDecimal(draft.factorText)
            if (draft.factorText.isNotBlank() && (parsedFactor == null || parsedFactor <= 0.0)) {
                return target.copy(unitError = "معامل التحويل لوحدة '$cleanName' يجب أن يكون أكبر من 0")
            }
        }
        return target.copy(unitError = null)
    }

    private fun recalculateFromConversionChange(target: ProductFormState): ProductFormState {
        val factor = computeCurrentFactor(target)
        val largeRaw = target.largeUnitCostText.trim()

        if (target.unitTiers.isNotEmpty() && factor == null) {
            // Incomplete / zero / invalid factor: do NOT divide and do NOT overwrite baseCost with 0!
            val showWarning = largeRaw.isNotEmpty() || target.unitTiers.any { it.factorText.trim() == "0" || it.factorText.startsWith("-") }
            return target.copy(
                costWarning = if (showWarning) "يرجى استكمال معامل التحويل (أكبر من 0)" else null
            )
        }

        if (largeRaw.isEmpty()) {
            return target.copy(costWarning = null)
        }

        return when (val result = UnitCostEngine.calculateBaseCostFromLargeUnit(largeRaw, factor)) {
            is CostCalculationResult.Success -> target.copy(
                baseCost = result.baseCost,
                baseCostText = NumberFormatUtils.formatInputNumber(result.baseCost, 2),
                hasManualBaseCostOverride = false,
                costWarning = null
            )
            is CostCalculationResult.Error -> target.copy(costWarning = result.message)
            CostCalculationResult.BlankInput -> target.copy(costWarning = null)
        }
    }
}

data class ValidatedProductSubmission(
    val existingId: String?,
    val name: String,
    val barcode: String,
    val baseUnitName: String,
    val defaultUnitName: String,
    val unitTiers: List<UnitTier>,
    val baseCost: Double,
    val baseSellPrice: Double,
    val stockBaseQty: Double,
    val trackExpiry: Boolean,
    val expiryDate: String,
    val minStockAlert: Double
)

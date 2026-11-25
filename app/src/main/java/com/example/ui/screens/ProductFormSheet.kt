package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.domain.NumberFormatUtils
import com.example.domain.UnitCostEngine
import com.example.ui.state.ProductFormState
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnEmeraldContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductFormScreen(
    state: ProductFormState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onBaseUnitNameChange: (String) -> Unit,
    onDefaultUnitChange: (String) -> Unit,
    onAddTier: () -> Unit,
    onRemoveTier: (Int) -> Unit,
    onTierNameChange: (Int, String) -> Unit,
    onTierFactorChange: (Int, String) -> Unit,
    onLargeUnitCostChange: (String) -> Unit,
    onBaseCostManualChange: (String) -> Unit,
    onBaseSellPriceChange: (String) -> Unit,
    onStockBaseQtyChange: (String) -> Unit,
    onMinStockAlertChange: (String) -> Unit,
    onTrackExpiryChange: (Boolean) -> Unit,
    onExpiryDateChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val levels = state.parsedTiers?.let {
        UnitCostEngine.resolveUnitLevels(state.baseUnitName, it).getOrNull()
    }
    val largestUnitLabel = state.largestUnitName
    val baseUnitLabel = state.baseUnitName.trim().ifEmpty { "الحبة" }
    val cumulativeFactor = state.cumulativeFactor

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                expandedHeight = 48.dp,
                title = {
                    Text(
                        text = if (state.editingProductId == null) "إضافة منتج جديد" else "تعديل بيانات المنتج",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Basic Product Data (Name, Barcode, Stock, Expiry)
                SectionCard(
                    title = "البيانات الأساسية للمنتج",
                    icon = Icons.Filled.Inventory2
                ) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = { Text("اسم المنتج *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_name_input")
                    )

                    OutlinedTextField(
                        value = state.barcode,
                        onValueChange = onBarcodeChange,
                        label = { Text("الباركود (اختياري)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_barcode_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LtrDecimalField(
                            value = state.stockBaseQtyText,
                            onValueChange = onStockBaseQtyChange,
                            label = "الرصيد الحالي ($baseUnitLabel)",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_stock_input")
                        )
                        LtrDecimalField(
                            value = state.minStockAlertText,
                            onValueChange = onMinStockAlertChange,
                            label = "حد التنبيه ($baseUnitLabel)",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("product_min_alert_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "تفعيل تاريخ انتهاء الصلاحية",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = state.trackExpiry,
                            onCheckedChange = onTrackExpiryChange,
                            modifier = Modifier.testTag("product_expiry_switch")
                        )
                    }

                    AnimatedVisibility(visible = state.trackExpiry) {
                        LtrDecimalField(
                            value = state.expiryDate,
                            onValueChange = onExpiryDateChange,
                            label = "تاريخ الانتهاء (مثال: 2027-06-30)",
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_expiry_date_input")
                        )
                    }
                }

                // 2. Multi-Level Unit Conversion Configuration
                SectionCard(
                    title = "إعدادات الوحدات ومعامل التحويل",
                    icon = Icons.Filled.Calculate
                ) {
                    OutlinedTextField(
                        value = state.baseUnitName,
                        onValueChange = onBaseUnitNameChange,
                        label = { Text("اسم الوحدة الأصغر (المرجع المحاسبي) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("base_unit_name_input")
                    )

                    state.unitTiers.forEachIndexed { index, tier ->
                        val previousUnitName = if (index == 0) baseUnitLabel else {
                            state.unitTiers[index - 1].name.trim().ifEmpty { "الوحدة السابقة" }
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "مستوى الوحدة ${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { onRemoveTier(index) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("remove_tier_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "حذف الوحدة",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = tier.name,
                                        onValueChange = { onTierNameChange(index, it) },
                                        label = { Text("اسم الوحدة") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tier_name_input_$index")
                                    )
                                    LtrDecimalField(
                                        value = tier.factorText,
                                        onValueChange = { onTierFactorChange(index, it) },
                                        label = "عدد ($previousUnitName) بداخلها",
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("tier_factor_input_$index")
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onAddTier,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_unit_tier_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة مستوى وحدة أكبر / وسيطة")
                    }

                    if (levels != null && levels.size > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "معامل التحويل التراكمي من إعدادات المنتج:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                levels.drop(1).forEach { level ->
                                    Text(
                                        text = "1 ${level.name} = ${NumberFormatUtils.formatNumber(level.cumulativeFactor)} $baseUnitLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "الوحدة الافتراضية:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            levels.forEach { level ->
                                FilterChip(
                                    selected = state.defaultUnitName.equals(level.name, ignoreCase = true),
                                    onClick = { onDefaultUnitChange(level.name) },
                                    label = { Text(level.name) }
                                )
                            }
                        }
                    }

                    if (state.unitError != null) {
                        WarningBanner(message = state.unitError, isError = true)
                    }
                }

                // 3. Purchase Cost Section (Large Unit -> Base Unit Automatic + Manual Override)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Calculate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "إعداد تكلفة الشراء",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                color = if (state.hasManualBaseCostOverride) AmberContainer else EmeraldContainer,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = if (state.hasManualBaseCostOverride) "تعديل يدوي للحبة" else "حساب تلقائي",
                                    color = if (state.hasManualBaseCostOverride) OnAmberContainer else OnEmeraldContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Field 1: Large Unit Purchase Cost
                        if (state.unitTiers.isNotEmpty()) {
                            LtrDecimalField(
                                value = state.largeUnitCostText,
                                onValueChange = onLargeUnitCostChange,
                                label = if (largestUnitLabel == "كرتون") {
                                    "سعر تكلفة الكرتون"
                                } else {
                                    "سعر تكلفة الكرتون / $largestUnitLabel"
                                },
                                suffixText = "ريال",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("large_unit_cost_input")
                            )

                            // Field 2: Conversion factor badge pulled dynamically from product unit settings
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "معامل التحويل ($largestUnitLabel):",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (cumulativeFactor != null) {
                                            "${NumberFormatUtils.formatNumber(cumulativeFactor)} $baseUnitLabel"
                                        } else {
                                            "غير مكتمل"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (cumulativeFactor != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.testTag("conversion_factor_display")
                                    )
                                }
                            }
                        }

                        // Field 3: Smallest Unit Cost (auto-updated AND directly editable)
                        LtrDecimalField(
                            value = state.baseCostText,
                            onValueChange = onBaseCostManualChange,
                            label = "تكلفة $baseUnitLabel (الوحدة الأصغر) *",
                            suffixText = "ريال",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("base_cost_input")
                        )

                        // Single concise formula explanation (never repeated elsewhere)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "تكلفة الحبة = تكلفة الكرتون ÷ عدد الحبات في الكرتون (مثال: 2400 ÷ 24 = 100)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.costWarning != null) {
                            WarningBanner(message = state.costWarning, isError = true)
                        }
                    }
                }

                // 4. Stock Value Protection Banner when modifying unit conversion factors on existing product
                state.stockReconciliation?.let { recon ->
                    Surface(
                        color = EmeraldContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = OnEmeraldContainer
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "حماية قيمة المخزون عند تعديل المعامل",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OnEmeraldContainer
                                )
                                Text(
                                    text = "الكمية الفعلية محفوظة: ${NumberFormatUtils.formatNumber(recon.preservedStockBaseQty)} $baseUnitLabel (تعادل ${NumberFormatUtils.formatNumber(recon.newLargestUnitCount)} $largestUnitLabel) بقيمة إجمالية ثابتة ${NumberFormatUtils.formatNumber(recon.preservedTotalValue)} ريال، دون التأثير على الفواتير السابقة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnEmeraldContainer
                                )
                            }
                        }
                    }
                }

                // 5. Selling Price & Margin Preview (Strictly Isolated from Purchase Cost)
                SectionCard(
                    title = "سعر البيع ومعاينة الربح",
                    icon = Icons.Filled.Sell
                ) {
                    LtrDecimalField(
                        value = state.baseSellPriceText,
                        onValueChange = onBaseSellPriceChange,
                        label = "سعر بيع $baseUnitLabel (الوحدة الأصغر)",
                        suffixText = "ريال",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("base_sell_price_input")
                    )

                    if (state.unitTiers.isNotEmpty() && cumulativeFactor != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "سعر بيع $largestUnitLabel المحسوب:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${NumberFormatUtils.formatNumber(state.computedLargeUnitSellPrice)} ريال",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldAccent,
                                modifier = Modifier.testTag("large_sell_price_preview")
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "صافي ربح $baseUnitLabel:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${NumberFormatUtils.formatNumber(state.unitProfit)} ريال (${NumberFormatUtils.formatNumber(state.marginPercent)}%)",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.unitProfit >= 0) EmeraldAccent else MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (state.formError != null) {
                    WarningBanner(message = state.formError, isError = true)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .weight(2f)
                            .height(52.dp)
                            .testTag("save_product_button")
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (state.editingProductId == null) "حفظ المنتج" else "حفظ التعديلات",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
fun LtrDecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffixText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        suffix = suffixText?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = modifier
    )
}

@Composable
private fun WarningBanner(message: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else AmberContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else AmberWarning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else OnAmberContainer
            )
        }
    }
}

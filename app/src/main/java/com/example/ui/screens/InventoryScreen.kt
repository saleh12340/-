package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.NumberFormatUtils
import com.example.domain.Product
import com.example.domain.UiState
import com.example.domain.UnitCostEngine
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.LedgerNavy
import com.example.ui.theme.LedgerCrimson
import com.example.ui.theme.OnAmberContainer

@Composable
fun InventoryScreen(
    productsState: UiState<List<Product>>,
    onAddProductClick: () -> Unit,
    onEditProductClick: (Product) -> Unit,
    onDeleteProductClick: (Product) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var lowStockOnly by remember { mutableStateOf(false) }
    var isTableView by remember { mutableStateOf(true) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (productsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = productsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is UiState.Success -> {
                val products = productsState.data
                val totalInventoryValue = remember(products) {
                    products.sumOf { it.stockBaseQty * it.baseCost }
                }
                val lowStockCount = remember(products) {
                    products.count { it.minStockAlert > 0.0 && it.stockBaseQty <= it.minStockAlert }
                }
                val filteredProducts = remember(products, searchQuery, lowStockOnly) {
                    products.filter { prod ->
                        val matchesQuery = searchQuery.isBlank() ||
                            prod.name.contains(searchQuery.trim(), ignoreCase = true) ||
                            prod.barcode.contains(NumberFormatUtils.toEnglishDigits(searchQuery.trim()), ignoreCase = true)
                        val matchesLow = !lowStockOnly || (prod.minStockAlert > 0.0 && prod.stockBaseQty <= prod.minStockAlert)
                        matchesQuery && matchesLow
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 78.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        InventoryHeroCard(
                            productCount = products.size,
                            totalValue = totalInventoryValue,
                            lowStockCount = lowStockCount
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث باسم الصنف أو الباركود...") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inventory_search_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = !lowStockOnly,
                                        onClick = { lowStockOnly = false },
                                        label = { Text("كل الأصناف (${products.size})", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = lowStockOnly,
                                        onClick = { lowStockOnly = true },
                                        label = { Text("نقص المخزون ($lowStockCount)", fontSize = 11.sp) }
                                    )
                                }

                                FilterChip(
                                    selected = isTableView,
                                    onClick = { isTableView = !isTableView },
                                    label = { Text(if (isTableView) "أسطر كالفاتورة ☰" else "بطاقات ▤", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    if (filteredProducts.isEmpty()) {
                        item {
                            EmptyInventoryState(onAddClick = onAddProductClick)
                        }
                    } else if (isTableView) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.1.dp, LedgerNavy.copy(alpha = 0.65f)),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Table Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(LedgerNavy)
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("#", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(30.dp))
                                        VerticalDivider(color = Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                        Text("الصنف / الباركود", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1.5f))
                                        VerticalDivider(color = Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                        Text("الرصيد", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f))
                                        VerticalDivider(color = Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                        Text("التكلفة", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.85f))
                                        VerticalDivider(color = Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                        Text("البيع", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.85f))
                                        VerticalDivider(color = Color.White.copy(alpha = 0.4f), modifier = Modifier.height(18.dp))
                                        Text("إجراءات", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(68.dp))
                                    }
                                    HorizontalDivider(thickness = 1.dp, color = LedgerNavy)

                                    filteredProducts.forEachIndexed { idx, product ->
                                        ProductTableRow(
                                            index = idx,
                                            product = product,
                                            onEdit = { onEditProductClick(product) },
                                            onDelete = { productToDelete = product }
                                        )
                                        if (idx < filteredProducts.lastIndex) {
                                            HorizontalDivider(thickness = 0.6.dp, color = LedgerNavy.copy(alpha = 0.25f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                onEdit = { onEditProductClick(product) },
                                onDelete = { productToDelete = product }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddProductClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_product_fab")
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "إضافة منتج")
        }
    }

    productToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("تأكيد حذف الصنف") },
            text = { Text("هل تريد حذف الصنف \"${target.name}\"؟ لن تتأثر الفواتير المحفوظة مسبقًا.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProductClick(target)
                        productToDelete = null
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun InventoryHeroCard(
    productCount: Int,
    totalValue: Double,
    lowStockCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "ملخص المخزون والتقييم المحاسبي",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    label = "قيمة المخزون",
                    value = "${NumberFormatUtils.formatNumber(totalValue)} ر",
                    modifier = Modifier.weight(1.2f)
                )
                MetricItem(
                    label = "الأصناف",
                    value = "$productCount",
                    modifier = Modifier.weight(0.8f)
                )
                MetricItem(
                    label = "تنبيهات",
                    value = "$lowStockCount",
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProductTableRow(
    index: Int,
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val levels = UnitCostEngine.getAvailableUnits(product)
    val largest = levels.last()
    val isLow = product.minStockAlert > 0.0 && product.stockBaseQty <= product.minStockAlert
    val rowBg = if (index % 2 == 1) Color(0xFFF8FAFC) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1: # Index & Low stock warning
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(30.dp)
        ) {
            if (isLow) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "مخزون منخفض",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Text(
                    text = "${index + 1}",
                    fontFamily = AmiriFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LedgerNavy
                )
            }
        }

        VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(26.dp))

        // Col 2: Name & Barcode
        Column(
            modifier = Modifier
                .weight(1.5f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = product.name,
                fontFamily = AmiriFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (product.barcode.isNotBlank()) {
                Text(
                    text = product.barcode,
                    fontFamily = AmiriFontFamily,
                    fontSize = 9.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
            }
        }

        VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(26.dp))

        // Col 3: Stock Available
        Column(
            modifier = Modifier
                .weight(1.1f)
                .padding(horizontal = 4.dp)
        ) {
            val stockText = if (levels.size > 1 && largest.cumulativeFactor > 1.0) {
                val count = product.stockBaseQty / largest.cumulativeFactor
                "${NumberFormatUtils.formatNumber(product.stockBaseQty)} ${product.baseUnitName}\n(${NumberFormatUtils.formatNumber(count)} ${largest.name})"
            } else {
                "${NumberFormatUtils.formatNumber(product.stockBaseQty)} ${product.baseUnitName}"
            }
            Text(
                text = stockText,
                fontFamily = AmiriFontFamily,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isLow) MaterialTheme.colorScheme.error else Color(0xFF1E293B),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(26.dp))

        // Col 4: Cost
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .weight(0.85f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = "${NumberFormatUtils.formatNumber(product.baseCost)} ر",
                fontFamily = AmiriFontFamily,
                fontSize = 11.sp,
                color = Color(0xFF475569),
                maxLines = 1
            )
        }

        VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(26.dp))

        // Col 5: Sell Price
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .weight(0.85f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = "${NumberFormatUtils.formatNumber(product.baseSellPrice)} ر",
                fontFamily = AmiriFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldAccent,
                maxLines = 1
            )
        }

        VerticalDivider(color = LedgerNavy.copy(alpha = 0.2f), modifier = Modifier.height(26.dp))

        // Col 6: Actions (Edit & Delete)
        Row(
            modifier = Modifier.width(68.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("edit_product_${product.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "تعديل الصنف",
                    tint = LedgerNavy,
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("delete_product_${product.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "حذف الصنف",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val levels = UnitCostEngine.getAvailableUnits(product)
    val largest = levels.last()
    val largestCost = UnitCostEngine.calculateLargeUnitCostFromBase(product.baseCost, largest.cumulativeFactor)
    val isLow = product.minStockAlert > 0.0 && product.stockBaseQty <= product.minStockAlert

    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (product.barcode.isNotBlank()) {
                        Text(
                            text = "باركود: ${product.barcode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_product_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "تعديل ${product.name}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_product_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "حذف ${product.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoPill(
                    title = "تكلفة ${product.baseUnitName}",
                    value = "${NumberFormatUtils.formatNumber(product.baseCost)} ر",
                    modifier = Modifier.weight(1f)
                )
                if (levels.size > 1) {
                    InfoPill(
                        title = "تكلفة ${largest.name} (${NumberFormatUtils.formatNumber(largest.cumulativeFactor)})",
                        value = "${NumberFormatUtils.formatNumber(largestCost)} ر",
                        modifier = Modifier.weight(1f)
                    )
                }
                InfoPill(
                    title = "بيع ${product.baseUnitName}",
                    value = "${NumberFormatUtils.formatNumber(product.baseSellPrice)} ر",
                    highlight = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stockText = if (levels.size > 1 && largest.cumulativeFactor > 0.0) {
                    val largestCount = product.stockBaseQty / largest.cumulativeFactor
                    "الرصيد: ${NumberFormatUtils.formatNumber(product.stockBaseQty)} ${product.baseUnitName} (${NumberFormatUtils.formatNumber(largestCount)} ${largest.name})"
                } else {
                    "الرصيد: ${NumberFormatUtils.formatNumber(product.stockBaseQty)} ${product.baseUnitName}"
                }
                Text(
                    text = stockText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isLow) {
                    Surface(
                        color = AmberContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = OnAmberContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "منخفض",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnAmberContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyInventoryState(onAddClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "لا توجد أصناف مسجلة بعد",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "أضف منتجك الأول وحدد وحدات الكرتون والحبة ليقوم التطبيق بحساب تكلفة الشراء تلقائيًا.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onAddClick) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة منتج الآن")
            }
        }
    }
}

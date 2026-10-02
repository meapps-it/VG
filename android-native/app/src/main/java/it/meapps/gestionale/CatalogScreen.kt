@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun ProductsScreen(vm: AppViewModel) {
    var filters by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeading(
            stringResource(R.string.articles),
            "${vm.filteredProducts.size} articoli nel catalogo",
        )
        AppTextField(
            vm.query,
            { vm.query = it },
            stringResource(R.string.search_product),
            leading = { Icon(Icons.Default.Search, null) },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = filters,
                onClick = { filters = !filters },
                label = { Text("Filtri") },
                leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = vm.promoOnly,
                onClick = { vm.promoOnly = !vm.promoOnly },
                label = { Text(stringResource(R.string.promo_only)) },
            )
            IconButton(onClick = { vm.updateGridView(!vm.gridView) }) {
                Icon(
                    if (vm.gridView) Icons.Default.ViewList else Icons.Default.GridView,
                    if (vm.gridView) stringResource(R.string.list_view)
                    else stringResource(R.string.grid_view),
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(filters) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectionField(
                        stringResource(R.string.categories),
                        vm.categoryFilter,
                        vm.categories.map { it.id to it.name },
                        { vm.categoryFilter = it },
                        true,
                    )
                    SelectionField(
                        stringResource(R.string.brands),
                        vm.brandFilter,
                        vm.brands.map { it.id to it.name },
                        { vm.brandFilter = it },
                        true,
                    )
                    SelectionField(
                        stringResource(R.string.suppliers),
                        vm.supplierFilter,
                        vm.suppliers.map { it.id to it.name },
                        { vm.supplierFilter = it },
                        true,
                    )
                }
                if (
                    vm.categoryFilter != null ||
                        vm.brandFilter != null ||
                        vm.supplierFilter != null ||
                        vm.promoOnly
                )
                    TextButton(
                        onClick = {
                            vm.categoryFilter = null
                            vm.brandFilter = null
                            vm.supplierFilter = null
                            vm.promoOnly = false
                        }
                    ) {
                        Text("Azzera filtri")
                    }
            }
        }
        if (!vm.loading && vm.filteredProducts.isEmpty())
            EmptyState(
                stringResource(R.string.no_products),
                stringResource(R.string.no_products_hint),
            )
        else if (vm.gridView)
            LazyVerticalGrid(
                columns =
                    GridCells.Adaptive(
                        if (LocalDensity.current.fontScale > 1.2f) 220.dp
                        else if (vm.compactMode) 158.dp else 174.dp
                    ),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(vm.filteredProducts, key = { it.id }) { ProductGridCard(it, vm) }
            }
        else
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.filteredProducts, key = { it.id }) { ProductCard(it, vm) }
            }
    }
}

@Composable
internal fun ProductGridCard(product: Product, vm: AppViewModel) {
    Card(
        Modifier.fillMaxWidth().clickable { vm.openProductDetail(product) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (product.photos.isNotEmpty())
            Box {
                ProductPhotoCarousel(product, vm, aspectRatio = 1.15f)
                if (product.inPromotion)
                    Box(Modifier.padding(10.dp)) { StatusBadge("Promo", BadgeTone.WARM) }
            }
        else ProductPhotoCarousel(product, vm)
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            vm.brands
                .firstOrNull { it.id == product.brandId }
                ?.let {
                    Text(it.name, style = MaterialTheme.typography.labelSmall, color = AppBlue)
                }
            Text(
                product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                product.code.ifBlank { product.sku.ifBlank { "Senza codice" } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (product.measureValue.isNotBlank())
                Text(product.measureValue, style = MaterialTheme.typography.bodySmall)
            Text(money(product.effectiveSalePrice), style = MaterialTheme.typography.headlineSmall)
            if (vm.showEconomicDetails) {
                Text(
                    "Costo ${money(product.totalCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Margine ${money(product.effectiveMarginEuro)} · ${"%.1f".format(LocalConfiguration.current.locales[0],product.effectiveMarginPercent)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (product.effectiveMarginEuro >= 0) Positive
                        else MaterialTheme.colorScheme.error,
                )
            }
            StatusBadge(
                if (product.available && product.quantity > 0) "${product.quantity} disponibili"
                else stringResource(R.string.unavailable)
            )
            vm.suppliers
                .firstOrNull { it.id == product.supplierId }
                ?.let {
                    Text(
                        it.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            AppTonalButton(
                onClick = { vm.openOrder(product) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.AddShoppingCart, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.add_order),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
internal fun ProductCard(product: Product, vm: AppViewModel) {
    AppPanel(Modifier.fillMaxWidth().clickable { vm.openProductDetail(product) }) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProductPhotoCarousel(product, vm, Modifier.width(88.dp), aspectRatio = 1f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    product.code.ifBlank { product.sku },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(money(product.effectiveSalePrice), style = MaterialTheme.typography.titleLarge)
                if (vm.showEconomicDetails)
                    Text(
                        "Margine ${money(product.effectiveMarginEuro)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Positive,
                    )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusBadge(
                if (product.available && product.quantity > 0) "${product.quantity} disponibili"
                else stringResource(R.string.unavailable)
            )
            if (product.inPromotion) StatusBadge("Promo", BadgeTone.WARM)
            vm.suppliers
                .firstOrNull { it.id == product.supplierId }
                ?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
        }
        AppTonalButton(onClick = { vm.openOrder(product) }) {
            Icon(Icons.Default.AddShoppingCart, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add_order))
        }
    }
}

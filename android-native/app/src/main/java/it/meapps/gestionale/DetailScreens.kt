@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun DetailInfoRow(label: String, value: String, valueColor: Color = AppNavy) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

@Composable
internal fun DetailActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.edit))
        }
        TextButton(onClick = onDelete) {
            Icon(
                Icons.Default.DeleteOutline,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ProductDetailScreen(vm: AppViewModel, productId: String) {
    val p = vm.products.firstOrNull { it.id == productId }
    DetailScaffold(vm, stringResource(R.string.product_detail), MainTab.ARTICLES) { padding ->
        if (p == null)
            Box(Modifier.padding(padding)) { Text(stringResource(R.string.product_not_found)) }
        else
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { ProductPhotoCarousel(p, vm, aspectRatio = 1.15f, showThumbnails = true) }
                item {
                    Text(p.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Cod. ${p.code.ifBlank{p.sku}}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    AppPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.sale_price),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            money(p.effectiveSalePrice),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        if (p.inPromotion)
                            StatusBadge(
                                stringResource(R.string.product_in_promotion),
                                BadgeTone.WARM,
                            )
                        if (vm.showEconomicDetails) {
                            DetailInfoRow("Costo totale", money(p.totalCost))
                            DetailInfoRow(
                                stringResource(R.string.margin),
                                money(p.effectiveMarginEuro) +
                                    " · ${"%.1f".format(LocalConfiguration.current.locales[0],p.effectiveMarginPercent)}%",
                                if (p.effectiveMarginEuro >= 0) Positive
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                item {
                    StatusBadge(
                        if (p.available && p.quantity > 0) "Disponibile · ${p.quantity} pezzi"
                        else stringResource(R.string.unavailable)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.openOrder(p) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.AddShoppingCart, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Crea un ordine")
                    }
                }
                item {
                    AppPanel(Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.product_data))
                        DetailInfoRow("SKU", p.sku)
                        DetailInfoRow(
                            stringResource(R.string.brand),
                            vm.brands.firstOrNull { it.id == p.brandId }?.name.orEmpty(),
                        )
                        DetailInfoRow(
                            stringResource(R.string.category),
                            vm.categories.firstOrNull { it.id == p.categoryId }?.name.orEmpty(),
                        )
                        DetailInfoRow(
                            stringResource(R.string.supplier),
                            vm.suppliers.firstOrNull { it.id == p.supplierId }?.name.orEmpty(),
                        )
                        if (p.measureValue.isNotBlank())
                            DetailInfoRow(
                                if (p.measureType == "peso") stringResource(R.string.weight)
                                else stringResource(R.string.measure),
                                p.measureValue,
                            )
                        if (p.description.isNotBlank())
                            DetailInfoRow(stringResource(R.string.description), p.description)
                        if (p.notes.isNotBlank())
                            DetailInfoRow(stringResource(R.string.notes), p.notes)
                        if (p.productUrl.isNotBlank())
                            DetailInfoRow(stringResource(R.string.product_link), p.productUrl)
                    }
                }
                item {
                    DetailActions(
                        { vm.openProduct(p) },
                        { vm.requestDelete(DeleteTarget.ProductTarget(p)) },
                    )
                }
                item {
                    val context = LocalContext.current
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { shareProduct(context, p, null) }) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Condividi")
                        }
                        TextButton(onClick = { shareProduct(context, p, "com.whatsapp") }) {
                            Text("WhatsApp")
                        }
                        TextButton(
                            onClick = { shareProduct(context, p, "org.telegram.messenger") }
                        ) {
                            Text("Telegram")
                        }
                    }
                }
            }
    }
}

@Composable
internal fun CustomerDetailScreen(vm: AppViewModel, customerId: String) {
    val c = vm.customers.firstOrNull { it.id == customerId }
    val context = LocalContext.current
    DetailScaffold(vm, stringResource(R.string.customer_detail), MainTab.CLIENTS) { padding ->
        if (c == null) Box(Modifier.padding(padding)) { Text("Cliente non trovato") }
        else {
            val orders = vm.orders.filter { it.customerId == c.id }
            val last = orders.maxByOrNull { it.date }
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(c.displayName, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        listOf(c.city, c.province, c.country)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Button(
                        onClick = { vm.openOrderForCustomer(c) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nuovo ordine per questo cliente")
                    }
                }
                item {
                    AppPanel(Modifier.fillMaxWidth()) {
                        SectionTitle("Contatti")
                        DetailInfoRow(stringResource(R.string.phone), c.phone)
                        DetailInfoRow(stringResource(R.string.email), c.email)
                        DetailInfoRow(
                            stringResource(R.string.address),
                            listOf(c.address, c.postalCode, c.city, c.province, c.country)
                                .filter { it.isNotBlank() }
                                .joinToString(", "),
                        )
                        if (c.notes.isNotBlank())
                            DetailInfoRow(stringResource(R.string.notes), c.notes)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (c.phone.isNotBlank())
                                TextButton(
                                    onClick = {
                                        runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_DIAL,
                                                        Uri.fromParts("tel", c.phone, null),
                                                    )
                                                )
                                            }
                                            .onFailure { vm.showContactError() }
                                    }
                                ) {
                                    Text("Chiama")
                                }
                            if (c.email.isNotBlank())
                                TextButton(
                                    onClick = {
                                        runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_SENDTO,
                                                        Uri.fromParts("mailto", c.email, null),
                                                    )
                                                )
                                            }
                                            .onFailure { vm.showContactError() }
                                    }
                                ) {
                                    Text(stringResource(R.string.email))
                                }
                        }
                    }
                }
                item {
                    AppPanel(
                        Modifier.fillMaxWidth().clickable { vm.openOrdersForCustomer(c) },
                        MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text("${orders.size} ordini", style = MaterialTheme.typography.titleMedium)
                        Text(
                            money(orders.sumOf { it.total }),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Valore totale degli ordini",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (last != null)
                    item {
                        AppPanel(Modifier.fillMaxWidth().clickable { vm.openOrderDetail(last) }) {
                            Text("Ultimo ordine", style = MaterialTheme.typography.labelLarge)
                            Text(
                                displayDate(last.date),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(money(last.total))
                        }
                    }
                item {
                    DetailActions(
                        { vm.openCustomer(c) },
                        { vm.requestDelete(DeleteTarget.CustomerTarget(c)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun OrderDetailScreen(vm: AppViewModel, orderId: String) {
    val o = vm.orders.firstOrNull { it.id == orderId }
    DetailScaffold(vm, stringResource(R.string.order_detail), MainTab.ORDERS) { padding ->
        if (o == null) Box(Modifier.padding(padding)) { Text("Ordine non trovato") }
        else
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        o.customerName.ifBlank { stringResource(R.string.customer) },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        listOf(o.number, displayDate(o.date))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusBadge(orderStatusText(o.status), statusTone(o.status))
                        StatusBadge(paymentText(o))
                    }
                }
                item {
                    AppPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            stringResource(R.string.total_order),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(money(o.total), style = MaterialTheme.typography.headlineMedium)
                        DetailInfoRow("Importo incassato", money(o.totalPaid))
                        if (vm.showEconomicDetails)
                            DetailInfoRow(
                                stringResource(R.string.profit),
                                money(o.profit),
                                if (o.profit >= 0) Positive else MaterialTheme.colorScheme.error,
                            )
                    }
                }
                item {
                    AppPanel(Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.articles))
                        Text(
                            o.itemNames.joinToString("\n").ifBlank {
                                stringResource(R.string.no_products)
                            }
                        )
                        DetailInfoRow(stringResource(R.string.payment), paymentText(o))
                        DetailInfoRow("Metodo", o.paymentMethod.replace('_', ' '))
                        DetailInfoRow(stringResource(R.string.courier), o.courier)
                        DetailInfoRow("Tracking", o.trackingCode)
                        if (o.notes.isNotBlank())
                            DetailInfoRow(stringResource(R.string.notes), o.notes)
                    }
                }
                item {
                    DetailActions(
                        { vm.editOrder(o) },
                        { vm.requestDelete(DeleteTarget.OrderTarget(o)) },
                    )
                }
            }
    }
}

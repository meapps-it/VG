@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

internal fun orderStatusLabel(status: String) =
    when (status) {
        "consegnato" -> "Consegnato"
        "spedito" -> "Spedito"
        "annullato" -> "Annullato"
        "in_lavorazione" -> "In lavorazione"
        else -> status.replace('_', ' ').ifBlank { "Stato non indicato" }
    }

internal fun paymentLabel(order: OrderSummary) =
    when {
        order.paymentStatus.isNotBlank() ->
            order.paymentStatus.replace('_', ' ').replaceFirstChar { it.uppercase() }
        order.paid -> "Pagato"
        order.totalPaid > 0 -> "Pagamento parziale"
        else -> "Da pagare"
    }

internal fun displayDate(value: String) =
    runCatching {
            LocalDate.parse(value)
                .format(
                    java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
                )
        }
        .getOrDefault(value)

@Composable
internal fun CustomersScreen(vm: AppViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    val rows =
        vm.customers.filter { c ->
            search.isBlank() ||
                listOf(c.displayName, c.phone, c.email, c.city, c.province).any {
                    it.contains(search, true)
                }
        }
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(stringResource(R.string.customers), "${rows.size} clienti") {
                AppTonalButton(onClick = { vm.openCustomer() }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.new_label))
                }
            }
        }
        item {
            AppTextField(
                search,
                { search = it },
                stringResource(R.string.search_customer),
                leading = { Icon(Icons.Default.Search, null) },
            )
        }
        if (rows.isEmpty())
            item {
                EmptyState(
                    stringResource(R.string.no_customer),
                    "Aggiungi un cliente o modifica la ricerca.",
                )
            }
        items(rows, key = { it.id }) { customer ->
            val orders = vm.orders.filter { it.customerId == customer.id }
            AppPanel(Modifier.fillMaxWidth().clickable { vm.openCustomerDetail(customer) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        Modifier.size(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                customer.displayName
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString("") { it.take(1).uppercase() },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(customer.displayName, style = MaterialTheme.typography.titleMedium)
                        val place =
                            listOf(customer.city, customer.province, customer.country)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                        if (place.isNotBlank())
                            Text(
                                place,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (customer.phone.isNotBlank())
                    Text(customer.phone, style = MaterialTheme.typography.bodyMedium)
                if (customer.email.isNotBlank())
                    Text(
                        customer.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusBadge("${orders.size} ordini")
                    if (orders.isNotEmpty())
                        StatusBadge(money(orders.sumOf { it.total }), BadgeTone.BLUE)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { vm.openOrderForCustomer(customer) }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.new_order))
                    }
                    if (customer.phone.isNotBlank())
                        TextButton(
                            onClick = {
                                runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_DIAL,
                                                Uri.fromParts("tel", customer.phone, null),
                                            )
                                        )
                                    }
                                    .onFailure { vm.showContactError() }
                            }
                        ) {
                            Icon(Icons.Default.Call, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Chiama")
                        }
                }
            }
        }
    }
}

@Composable
internal fun OrdersScreen(vm: AppViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val rows =
        vm.orders.filter { order ->
            (search.isBlank() ||
                listOf(
                        order.customerName,
                        order.number,
                        order.trackingCode,
                        order.courier,
                        order.itemNames.joinToString(),
                    )
                    .any { it.contains(search, true) }) &&
                (status == null || order.status == status) &&
                (vm.orderMonthFilter == null ||
                    runCatching {
                            YearMonth.from(LocalDate.parse(order.date)).toString() ==
                                vm.orderMonthFilter
                        }
                        .getOrDefault(false)) &&
                (!vm.orderActiveOnly ||
                    (order.status != "consegnato" && order.status != "annullato")) &&
                (vm.orderCustomerFilter == null || order.customerId == vm.orderCustomerFilter)
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeading(stringResource(R.string.orders), "${rows.size} ordini") {
                AppTonalButton(onClick = { vm.openOrder() }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.new_label))
                }
            }
        }
        item {
            AppTextField(
                search,
                { search = it },
                stringResource(R.string.search_order),
                leading = { Icon(Icons.Default.Search, null) },
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = status == null,
                    onClick = { status = null },
                    label = { Text(stringResource(R.string.all)) },
                )
                listOf("in_lavorazione", "spedito", "consegnato", "annullato").forEach { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { status = if (status == s) null else s },
                        label = { Text(orderStatusText(s)) },
                    )
                }
            }
        }
        if (vm.orderMonthFilter != null || vm.orderActiveOnly || vm.orderCustomerFilter != null)
            item {
                InputChip(
                    selected = true,
                    onClick = vm::clearOrderDrillDown,
                    label = {
                        Text(
                            vm.orderMonthFilter?.let { "Mese: $it" }
                                ?: if (vm.orderActiveOnly) stringResource(R.string.active_orders)
                                else "Ordini cliente"
                        )
                    },
                    trailingIcon = { Icon(Icons.Default.Close, null) },
                )
            }
        if (rows.isEmpty())
            item { EmptyState(stringResource(R.string.no_orders), "Gli ordini compariranno qui.") }
        items(rows, key = { it.id }) { order ->
            AppPanel(Modifier.fillMaxWidth().clickable { vm.openOrderDetail(order) }) {
                Text(
                    order.customerName.ifBlank { stringResource(R.string.customer) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    listOf(order.number, displayDate(order.date))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusBadge(orderStatusText(order.status), statusTone(order.status))
                    StatusBadge(paymentText(order))
                }
                if (order.itemNames.isNotEmpty())
                    Text(
                        order.itemNames.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)
                )
                AdaptiveMetrics {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.total_order),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(money(order.total), style = MaterialTheme.typography.headlineSmall)
                    }
                    if (vm.showEconomicDetails)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.profit),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                money(order.profit),
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                    if (order.profit >= 0) Positive
                                    else MaterialTheme.colorScheme.error,
                            )
                        }
                }
                if (order.totalPaid > 0)
                    Text(
                        "Incassato ${money(order.totalPaid)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                if (order.trackingCode.isNotBlank())
                    Text(
                        "Tracking ${order.trackingCode}",
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
        }
    }
}

@Composable
internal fun orderStatusText(status: String): String =
    when (status) {
        "in_lavorazione" -> stringResource(R.string.processing)
        "spedito" -> stringResource(R.string.shipped)
        "consegnato" -> stringResource(R.string.delivered)
        "annullato" -> stringResource(R.string.cancelled)
        else -> orderStatusLabel(status)
    }

@Composable
internal fun paymentText(order: OrderSummary): String =
    when {
        order.paymentStatus == "pagato" || (order.paymentStatus.isBlank() && order.paid) ->
            stringResource(R.string.paid)
        order.paymentStatus == "da_pagare" ||
            (order.paymentStatus.isBlank() && order.totalPaid <= 0) ->
            stringResource(R.string.to_pay)
        else -> paymentLabel(order)
    }

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

internal data class SalesMonth(val month: YearMonth, val amount: Double)

internal fun salesMonths(orders: List<OrderSummary>, current: YearMonth) =
    (5 downTo 0).map { i ->
        val m = current.minusMonths(i.toLong())
        SalesMonth(
            m,
            orders
                .filter {
                    runCatching { YearMonth.from(LocalDate.parse(it.date)) == m }
                        .getOrDefault(false)
                }
                .sumOf { if (it.totalPaid > 0) it.totalPaid else it.total },
        )
    }

@Composable
internal fun HomeScreen(vm: AppViewModel) {
    val current = YearMonth.now()
    val orders = vm.orders
    val monthly =
        orders.filter {
            runCatching { YearMonth.from(LocalDate.parse(it.date)) == current }.getOrDefault(false)
        }
    val average =
        if (orders.isEmpty()) 0.0
        else orders.sumOf { if (it.totalPaid > 0) it.totalPaid else it.total } / orders.size
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeading("Panoramica", "I numeri della tua attività") }
        item {
            AppPanel(
                Modifier.fillMaxWidth().clickable { vm.openOrdersForMonth(current) },
                BrandNavy,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Insights, null, tint = Color(0xFFA7C5F9))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.showEconomicDetails) "GUADAGNO DEL MESE" else "VENDITE DEL MESE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCAD8E6),
                    )
                }
                Text(
                    money(
                        if (vm.showEconomicDetails) monthly.sumOf { it.profit }
                        else monthly.sumOf { if (it.totalPaid > 0) it.totalPaid else it.total }
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
                Text(
                    "${monthly.size} ordini · ${current.month.getDisplayName(java.time.format.TextStyle.FULL,LocalConfiguration.current.locales[0])} ${current.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCAD8E6),
                )
            }
        }
        item {
            AdaptiveMetrics {
                MetricTile(
                    stringResource(R.string.active_orders),
                    orders
                        .count { it.status != "consegnato" && it.status != "annullato" }
                        .toString(),
                    "Da completare",
                    Modifier.weight(1f),
                    onClick = vm::openActiveOrders,
                )
                MetricTile(
                    stringResource(R.string.average_orders),
                    money(average),
                    stringResource(R.string.average_order_value),
                    Modifier.weight(1f),
                    onClick = {
                        vm.clearOrderDrillDown()
                        vm.selectTab(MainTab.ORDERS)
                    },
                )
            }
        }
        item { SalesChart(salesMonths(orders, current), vm) }
        if (vm.showEconomicDetails)
            item {
                AppPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(R.string.total_profit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        money(orders.sumOf { it.profit }),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        stringResource(R.string.total_margin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        item {
            AppPanel(Modifier.fillMaxWidth()) {
                Text("Stato ordini", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("in_lavorazione", "spedito", "consegnato", "annullato").forEach { status
                        ->
                        StatusBadge(
                            "${orderStatusText(status)} · ${orders.count{it.status==status}}",
                            statusTone(status),
                        )
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.quick_actions))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTonalButton(onClick = { vm.openOrder() }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.order))
                }
                OutlinedButton(onClick = { vm.openProduct() }) {
                    Text(stringResource(R.string.product))
                }
                OutlinedButton(onClick = { vm.openCustomer() }) {
                    Text(stringResource(R.string.customer))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SalesChart(months: List<SalesMonth>, vm: AppViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(5) }
    val target = months.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by
        androidx.compose.animation.core.animateFloatAsState(
            if (appeared) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(350),
            label = "Andamento",
        )
    val bar = AppBlue
    val muted = AppBlue.copy(alpha = .72f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    AppPanel(Modifier.fillMaxWidth()) {
        ScreenHeading(
            stringResource(R.string.sales_trend),
            stringResource(R.string.last_six_months),
        )
        Text(
            "${months[selected].month.month.getDisplayName(java.time.format.TextStyle.FULL,LocalConfiguration.current.locales[0])} · ${money(months[selected].amount)}",
            style = MaterialTheme.typography.titleSmall,
        )
        Column(Modifier.fillMaxWidth()) {
            Text(
                money(target),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.Canvas(
                Modifier.fillMaxWidth()
                    .height(148.dp)
                    .pointerInput(months) {
                        detectTapGestures { point ->
                            selected = (point.x / (size.width / 6f)).toInt().coerceIn(0, 5)
                        }
                    }
                    .semantics {
                        contentDescription =
                            "Vendite mensili: " +
                                months.joinToString { it.month.toString() + " " + money(it.amount) }
                    }
            ) {
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(
                        grid,
                        androidx.compose.ui.geometry.Offset(0f, y),
                        androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                val cell = size.width / 6
                months.forEachIndexed { i, m ->
                    val h =
                        (m.amount.coerceAtLeast(0.0) / target * size.height * progress).toFloat()
                    if (h > 0)
                        drawRoundRect(
                            if (i == selected) bar else muted,
                            topLeft =
                                androidx.compose.ui.geometry.Offset(
                                    cell * i + cell * .25f,
                                    size.height - h,
                                ),
                            size = androidx.compose.ui.geometry.Size(cell * .5f, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                        )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                months.forEachIndexed { i, m ->
                    TextButton(
                        onClick = { selected = i },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            m.month.month
                                .getDisplayName(
                                    java.time.format.TextStyle.SHORT,
                                    LocalConfiguration.current.locales[0],
                                )
                                .take(3),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        TextButton(onClick = { vm.openOrdersForMonth(months[selected].month) }) {
            Text("Visualizza ordini del mese")
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

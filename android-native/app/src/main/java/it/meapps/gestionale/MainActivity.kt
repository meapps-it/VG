package it.meapps.gestionale

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private val AppNavy = Color(0xFF0B2B52)
private val AppBlue = Color(0xFF1677FF)
private val AppAmber = Color(0xFFFF8A1F)
private val AppBackground = Color(0xFFF5F8FC)
private val WarmSurface = Color(0xFFFFFBEB)
private val Positive = Color(0xFF0AA66E)
private val Negative = Color(0xFFB42318)
private val LegacyBorder = Color(0xFFC9D0D9)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val tag = prefs.getString("app_language", "system").orEmpty()
        if (tag.isBlank() || tag == "system") {
            super.attachBaseContext(newBase)
        } else {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GestionaleRoot(viewModel) }
    }
}

@Composable
private fun GestionaleRoot(vm: AppViewModel) {
    val originalDensity = LocalDensity.current
    val scaledDensity = remember(originalDensity.density, vm.fontScale) { Density(originalDensity.density, vm.fontScale) }
    val useDark = when (vm.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = if (useDark) darkColorScheme(
        primary = Color(0xFF93C5FD), secondary = Color(0xFFFBBF24), background = Color(0xFF020617),
        surface = Color(0xFF0F172A), surfaceVariant = Color(0xFF1E293B), error = Color(0xFFFCA5A5)
    ) else lightColorScheme(
        primary = AppBlue, secondary = AppAmber, background = AppBackground, surface = Color.White,
        surfaceVariant = Color(0xFFF1F5F9), error = Negative, onPrimary = Color.White
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colors, typography = Typography()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    vm.checkingAuth -> LoadingScreen(stringResource(R.string.verify_access))
                    vm.session == null -> LoginScreen(vm)
                    else -> AuthenticatedApp(vm)
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().widthIn(max = 440.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Inventory2, null, tint = AppBlue, modifier = Modifier.size(44.dp))
                Text(if (register) stringResource(R.string.create_account) else stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tagline), color = Color(0xFF667085))
                AppTextField(email, { email = it }, stringResource(R.string.email), keyboardType = KeyboardType.Email)
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                )
                Button(
                    onClick = { if (register) vm.signUp(email, password) else vm.login(email, password) },
                    enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { if (vm.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(if (register) stringResource(R.string.register) else stringResource(R.string.login)) }
                TextButton(onClick = { register = !register }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (register) stringResource(R.string.already_account) else stringResource(R.string.create_new_account))
                }
                if (!register) TextButton(onClick = { vm.resetPassword(email) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.forgot_password))
                }
                vm.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                vm.noticeMessage?.let { Text(it, color = Positive) }
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.errorMessage, vm.noticeMessage) {
        val message = vm.errorMessage ?: vm.noticeMessage
        if (message != null) { snackbar.showSnackbar(message); vm.clearMessages() }
    }
    val activity = LocalActivity.current
    BackHandler(enabled = true) {
        if (!vm.navigateBack()) activity?.moveTaskToBack(true)
    }

    vm.deleteTarget?.let { target ->
        val label = when (target) {
            is DeleteTarget.ProductTarget -> target.product.name
            is DeleteTarget.EntityTarget -> target.name
            is DeleteTarget.CustomerTarget -> target.customer.displayName
            is DeleteTarget.OrderTarget -> target.order.number.ifBlank { target.order.customerName }
            is DeleteTarget.PhotoTarget -> "questa foto"
        }
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text("Eliminare $label? L’operazione non può essere annullata.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete, enabled = !vm.saving) { Text(stringResource(R.string.delete), color = Negative) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.cancel)) } }
        )
    }

    when (val editor = vm.editor) {
        is Editor.ProductEditor -> ProductEditorScreen(vm)
        is Editor.EntityEditor -> EntityEditorScreen(vm, editor.kind)
        is Editor.CustomerEditor -> CustomerEditorScreen(vm)
        is Editor.OrderEditor -> OrderEditorScreen(vm)
        null -> when (val detail = vm.detail) {
            is Detail.ProductDetail -> ProductDetailScreen(vm, detail.productId)
            is Detail.CustomerDetail -> CustomerDetailScreen(vm, detail.customerId)
            is Detail.OrderDetail -> OrderDetailScreen(vm, detail.orderId)
            null -> MainScaffold(vm, snackbar)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AppViewModel, snackbar: SnackbarHostState) {
    var menuOpen by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val nowText = remember(now) { DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM, Locale.getDefault()).format(Date(now)) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(AppNavy, Color(0xFF0E4C92))))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(nowText, color = Color(0xFFD6D9E2), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Box {
                        FilledTonalIconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier
                                .size(46.dp)
                                .border(1.5.dp, Color.White, RoundedCornerShape(50)),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            )
                        ) { Icon(Icons.Default.Menu, stringResource(R.string.menu)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.archives)) },
                                leadingIcon = { Icon(Icons.Default.ListAlt, null) },
                                onClick = { menuOpen = false; vm.selectTab(MainTab.ARCHIVES) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuOpen = false; vm.selectTab(MainTab.SETTINGS) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh_data)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { menuOpen = false; vm.loadAll() }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                shadowElevation = 12.dp,
                color = Color.White
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomPill(stringResource(R.string.dashboard), Icons.Default.Home, vm.selectedTab == MainTab.HOME, AppBlue, Modifier.weight(1f)) { vm.selectTab(MainTab.HOME) }
                    BottomPill(stringResource(R.string.articles), Icons.Default.Inventory2, vm.selectedTab == MainTab.ARTICLES, AppBlue, Modifier.weight(1f)) { vm.selectTab(MainTab.ARTICLES) }
                    BottomPill(stringResource(R.string.customers), Icons.Default.People, vm.selectedTab == MainTab.CLIENTS, AppAmber, Modifier.weight(1f)) { vm.selectTab(MainTab.CLIENTS) }
                    BottomPill(stringResource(R.string.orders), Icons.Default.ShoppingCart, vm.selectedTab == MainTab.ORDERS, Positive, Modifier.weight(1f)) { vm.selectTab(MainTab.ORDERS) }
                }
            }
        },
        floatingActionButton = {
            if (vm.selectedTab == MainTab.ARTICLES || vm.selectedTab == MainTab.ARCHIVES) {
                FloatingActionButton(
                    modifier = Modifier.border(1.5.dp, LegacyBorder, RoundedCornerShape(50)),
                    onClick = {
                        if (vm.selectedTab == MainTab.ARTICLES) vm.openProduct()
                        else vm.openEntity(vm.archiveKind)
                    },
                    containerColor = AppAmber,
                    contentColor = AppNavy
                ) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.selectedTab) {
                MainTab.HOME -> HomeScreen(vm)
                MainTab.ARTICLES -> ProductsScreen(vm)
                MainTab.CLIENTS -> CustomersScreen(vm)
                MainTab.ORDERS -> OrdersScreen(vm)
                MainTab.ARCHIVES -> ArchivesScreen(vm)
                MainTab.SETTINGS -> SettingsScreen(vm)
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun BottomPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(52.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = .12f) else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.2.dp, accent.copy(alpha = .40f)) else null
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) accent else Color(0xFF64748B))
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                fontSize = 10.sp,
                color = if (selected) AppNavy else Color(0xFF64748B),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel) {
    val orders = vm.orders
    val currentMonth = YearMonth.now()
    val monthProfit = orders.filter {
        runCatching { YearMonth.from(LocalDate.parse(it.date)) == currentMonth }.getOrDefault(false)
    }.sumOf { it.profit }
    val totalProfit = orders.sumOf { it.profit }
    val activeOrders = orders.count { it.status != "consegnato" && it.status != "annullato" }
    val averageOrder = if (orders.isEmpty()) 0.0 else orders.sumOf { if (it.totalPaid > 0) it.totalPaid else it.total } / orders.size

    val monthNames = listOf("Gen","Feb","Mar","Apr","Mag","Giu","Lug","Ago","Set","Ott","Nov","Dic")
    val sixMonths = (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    val monthValues = sixMonths.map { month ->
        orders.filter { runCatching { YearMonth.from(LocalDate.parse(it.date)) == month }.getOrDefault(false) }
            .sumOf { if (it.totalPaid > 0) it.totalPaid else it.total }
    }
    val maxValue = (monthValues.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF8)),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.featured), color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (orders.size < 4) "Vendite sotto media" else stringResource(R.string.sales_trend),
                        fontSize = 25.sp, fontWeight = FontWeight.Black, color = AppNavy
                    )
                    Text(
                        if (orders.size < 4) "Meno di una vendita a settimana: serve più movimento."
                        else stringResource(R.string.sales_trend_hint),
                        color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.last_six_months), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B))
                        Text(stringResource(R.string.collected), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B))
                    }
                    Row(
                        Modifier.fillMaxWidth().height(128.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        sixMonths.forEachIndexed { index, month ->
                            val h = (18 + (monthValues[index] / maxValue * 72)).dp
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { vm.openOrdersForMonth(month) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(94.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(h)
                                            .background(
                                                if (index == 5) AppAmber else AppBlue,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .border(
                                                1.5.dp,
                                                LegacyBorder,
                                                RoundedCornerShape(14.dp)
                                            )
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    monthNames[month.monthValue - 1],
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegacyStatCard(
                    stringResource(R.string.active_orders),
                    activeOrders.toString(),
                    stringResource(R.string.not_delivered_yet),
                    Modifier.weight(1f),
                    onClick = { vm.openActiveOrders() }
                )
                LegacyStatCard(
                    stringResource(R.string.month_profit),
                    money(monthProfit),
                    stringResource(R.string.month_margin),
                    Modifier.weight(1f),
                    Positive,
                    onClick = { vm.openOrdersForMonth(currentMonth) }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegacyStatCard(
                    stringResource(R.string.average_orders),
                    money(averageOrder),
                    stringResource(R.string.average_order_value),
                    Modifier.weight(1f),
                    onClick = { vm.clearOrderDrillDown(); vm.selectTab(MainTab.ORDERS) }
                )
                LegacyStatCard(
                    stringResource(R.string.total_profit),
                    money(totalProfit),
                    stringResource(R.string.total_margin),
                    Modifier.weight(1f),
                    Positive,
                    onClick = { vm.clearOrderDrillDown(); vm.selectTab(MainTab.ORDERS) }
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.quick_actions), fontSize = 22.sp, fontWeight = FontWeight.Black, color = AppNavy)
                    Button(
                        onClick = { vm.openProduct() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                    ) { Text(stringResource(R.string.new_product), fontWeight = FontWeight.Black) }
                    OutlinedButton(
                        onClick = { vm.openOrder() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                    ) { Text(stringResource(R.string.new_order), fontWeight = FontWeight.Black, color = AppNavy) }
                    OutlinedButton(
                        onClick = { vm.openCustomer() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                    ) { Text(stringResource(R.string.new_customer), fontWeight = FontWeight.Black, color = AppNavy) }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun LegacyStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppNavy,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        cardModifier,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF475569))
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1)
            Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
        }
    }
}
@Composable
private fun DashboardStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.width(34.dp).height(5.dp).background(accent, RoundedCornerShape(50)))
            Spacer(Modifier.height(10.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Black, color = color)
    }

@Composable
private fun QuickArchive(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = AppBlue); Text(count.toString(), fontWeight = FontWeight.Black, fontSize = 20.sp); Text(label, fontSize = 11.sp, maxLines = 1)
        }
    }
}


@Composable
private fun ProductsScreen(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_product), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(5.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SelectionField(
                stringResource(R.string.categories),
                vm.categoryFilter,
                vm.categories.map { it.id to it.name },
                { vm.categoryFilter = it },
                compact = true
            )
            SelectionField(
                stringResource(R.string.brands),
                vm.brandFilter,
                vm.brands.map { it.id to it.name },
                { vm.brandFilter = it },
                compact = true
            )
            SelectionField(
                stringResource(R.string.suppliers),
                vm.supplierFilter,
                vm.suppliers.map { it.id to it.name },
                { vm.supplierFilter = it },
                compact = true
            )
        }

        Spacer(Modifier.height(5.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = !vm.promoOnly,
                onClick = { vm.promoOnly = false },
                modifier = Modifier.height(34.dp),
                label = { Text(stringResource(R.string.all), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = !vm.promoOnly,
                    borderColor = LegacyBorder, selectedBorderColor = LegacyBorder,
                    borderWidth = 1.5.dp, selectedBorderWidth = 1.5.dp
                )
            )
            FilterChip(
                selected = vm.promoOnly,
                onClick = { vm.promoOnly = true },
                modifier = Modifier.height(34.dp),
                label = { Text(stringResource(R.string.promo_only), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = vm.promoOnly,
                    borderColor = LegacyBorder, selectedBorderColor = LegacyBorder,
                    borderWidth = 1.5.dp, selectedBorderWidth = 1.5.dp
                )
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.articles_count, vm.filteredProducts.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { vm.updateGridView(!vm.gridView) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    if (vm.gridView) Icons.Default.ViewList else Icons.Default.GridView,
                    if (vm.gridView) stringResource(R.string.list_view) else stringResource(R.string.grid_view),
                    Modifier.size(20.dp)
                )
            }
        }

        if (!vm.loading && vm.filteredProducts.isEmpty()) {
            EmptyState(stringResource(R.string.no_products), stringResource(R.string.no_products_hint))
        } else if (vm.gridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (vm.compactMode) 150.dp else 174.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(vm.filteredProducts, key = { it.id }) { product -> ProductGridCard(product, vm) }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.filteredProducts, key = { it.id }) { product -> ProductCard(product, vm) }
            }
        }
    }
}

@Composable
private fun ArticleCounter(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
            Text(value, fontSize = 24.sp, color = AppNavy, fontWeight = FontWeight.Black)
        }
    }
}
@Composable
private fun ProductPhotoCarousel(
    product: Product,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.15f,
    rounded: RoundedCornerShape = RoundedCornerShape(18.dp),
    showThumbnails: Boolean = false
) {
    val photos = remember(product.photos) { product.photos.sortedBy { it.order } }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    photos.forEach { photo ->
        LaunchedEffect(photo.path) { vm.ensureSignedUrl(photo.path) }
    }

    if (photos.isEmpty()) {
        Surface(
            modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio),
            shape = rounded,
            border = androidx.compose.foundation.BorderStroke(1.2.dp, LegacyBorder),
            color = Color(0xFFF1F5F9)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Image, null, Modifier.size(42.dp), tint = Color(0xFF94A3B8))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.no_photo), fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        ) {
            val itemWidth = maxWidth
            LazyRow(
                state = state,
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos, key = { it.id }) { photo ->
                    Surface(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight(),
                        shape = rounded,
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, LegacyBorder),
                        color = Color(0xFFF1F5F9)
                    ) {
                        val url = vm.signedUrls[photo.path]
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = product.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            if (photos.size > 1) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xD90F172A)
                ) {
                    Text(
                        "${(state.firstVisibleItemIndex + 1).coerceAtMost(photos.size)}/${photos.size}",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        if (showThumbnails && photos.size > 1) {
            Spacer(Modifier.height(5.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(photos.size) { index ->
                    val photo = photos[index]
                    val selected = index == state.firstVisibleItemIndex
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable {
                                scope.launch { state.animateScrollToItem(index) }
                            },
                        shape = RoundedCornerShape(9.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) AppNavy else LegacyBorder
                        ),
                        color = Color(0xFFF1F5F9)
                    ) {
                        val url = vm.signedUrls[photo.path]
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Foto ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ProductGridCard(product: Product, vm: AppViewModel) {
    val brandName = vm.brands.firstOrNull { it.id == product.brandId }?.name.orEmpty()
    val code = product.code.ifBlank { product.sku.ifBlank { "Senza codice" } }

    Card(
        Modifier.fillMaxWidth().clickable { vm.openProductDetail(product) },
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
    ) {
        Column {
            Box {
                ProductPhotoCarousel(
                    product = product,
                    vm = vm,
                    aspectRatio = 1.08f,
                    rounded = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    showThumbnails = true
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xE60F172A)
                ) {
                    Text(
                        "${product.photos.size} foto",
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                if (!product.available || product.quantity <= 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xE6B42318)
                    ) {
                        Text(stringResource(R.string.unavailable_short), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (brandName.isNotBlank()) {
                    Text(brandName.uppercase(), color = AppBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                Text(product.name, fontWeight = FontWeight.Black, maxLines = 2, minLines = if (vm.compactMode) 1 else 2, color = AppNavy)
                Text(code, color = Color(0xFF64748B), fontSize = 11.sp, maxLines = 1)
                if (product.measureValue.isNotBlank()) {
                    Text(
                        (if (product.measureType == "peso") "Peso: " else if (product.measureType == "misura") "Misura: " else "") + product.measureValue,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(2.dp))
                if (product.inPromotion && product.promotionalPrice > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = Color(0xFFFFF3CF), shape = RoundedCornerShape(10.dp)) {
                            Text(stringResource(R.string.promotion), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color(0xFF92400E), fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                        Text(money(product.promotionalPrice), fontWeight = FontWeight.Black, fontSize = 19.sp, color = Negative)
                    }
                    Text(money(product.salePrice), fontSize = 11.sp, color = Color(0xFF64748B))
                } else {
                    Text(money(product.salePrice), fontWeight = FontWeight.Black, fontSize = 19.sp, color = AppNavy)
                }
                if (product.effectiveMarginEuro < 0) {
                    Surface(color = Color(0xFFFFECEA), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "Sotto margine ${money(product.effectiveMarginEuro)}",
                            Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            color = Negative,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    Text("Margine ${money(product.effectiveMarginEuro)}", color = if (product.effectiveMarginEuro >= 0) Positive else Negative, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (product.available && product.quantity > 0) "Disponibili ${product.quantity}" else "Non disponibile",
                    color = if (product.available && product.quantity > 0) Positive else Negative,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { vm.openOrder(product) },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFF3CF)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.add_order), fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

private fun shareProduct(context: android.content.Context, product: Product, targetPackage: String?) {
    val text = buildString {
        append(product.name)
        val code = product.code.ifBlank { product.sku }
        if (code.isNotBlank()) append("\nCodice: ").append(code)
        if (product.effectiveSalePrice > 0) {
            append("\nPrezzo: ").append(money(product.effectiveSalePrice))
            if (product.inPromotion && product.promotionalPrice > 0) append(" (promo)")
        }
        if (product.productUrl.isNotBlank()) append("\n").append(product.productUrl)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
    }
    val fallback = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent.createChooser(fallback, "Condividi articolo")) }
}

@Composable
private fun ProductCard(product: Product, vm: AppViewModel) {
    val imagePath = product.photos.minByOrNull { it.order }?.path
    if (imagePath != null) LaunchedEffect(imagePath) { vm.ensureSignedUrl(imagePath) }
    Card(
        Modifier.fillMaxWidth().clickable { vm.openProductDetail(product) },
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(72.dp), color = Color(0xFFEFF4FF), shape = RoundedCornerShape(14.dp)) {
                if (imagePath != null && vm.signedUrls[imagePath] != null) AsyncImage(
                    model = vm.signedUrls[imagePath], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                ) else Icon(Icons.Default.Image, null, tint = Color(0xFF98A2B3), modifier = Modifier.padding(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(listOf(product.code, product.sku).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Nessun codice" }, color = Color(0xFF667085), fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Text("Vendita ${money(product.salePrice)}  ·  Margine ${money(product.marginEuro)}", color = if (product.marginEuro >= 0) Positive else Negative, fontWeight = FontWeight.SemiBold)
                Text("Quantità ${product.quantity}${if (!product.available) " · Non disponibile" else ""}", fontSize = 12.sp, color = Color(0xFF667085))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF98A2B3))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
private fun CustomersScreen(vm: AppViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    val filteredCustomers = remember(vm.customers, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) vm.customers else vm.customers.filter {
            listOf(it.displayName, it.phone, it.email, it.city, it.province).any { value -> value.lowercase().contains(q) }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Clienti", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AppNavy)
                    Text("${filteredCustomers.size} di ${vm.customers.size} clienti", color = Color(0xFF64748B))
                }
                OutlinedButton(
                    onClick = { vm.openCustomer() },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Nuovo") }
            }
        }
        item { AppTextField(search, { search = it }, "Cerca cliente, telefono o città", leading = { Icon(Icons.Default.Search, null) }) }
        if (filteredCustomers.isEmpty()) {
            item { EmptyState("Nessun cliente", if (search.isBlank()) "I clienti compariranno qui." else "Nessun cliente corrisponde alla ricerca.") }
        } else {
            items(filteredCustomers, key = { it.id }) { customer ->
                Card(
                    Modifier.fillMaxWidth().clickable { vm.openCustomerDetail(customer) },
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(customer.displayName, fontSize = 19.sp, fontWeight = FontWeight.Black, color = AppNavy)
                            if (customer.phone.isNotBlank()) Text("Tel. ${customer.phone}", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                            val place = listOf(customer.city, customer.province).filter { it.isNotBlank() }.joinToString(" • ")
                            if (place.isNotBlank()) Text(place, color = Color(0xFF64748B))
                        }
                        if (customer.country.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder), color = Color.White) {
                                Text(customer.country, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun OrdersScreen(vm: AppViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    val filteredOrders = remember(vm.orders, search, vm.orderMonthFilter, vm.orderActiveOnly) {
        val q = search.trim().lowercase()
        vm.orders.filter { order ->
            val matchesSearch = q.isBlank() || listOf(
                order.customerName,
                order.number,
                order.status,
                order.trackingCode,
                order.courier,
                order.itemNames.joinToString(" ")
            ).any { value -> value.lowercase().contains(q) }

            val matchesMonth = vm.orderMonthFilter == null || runCatching {
                YearMonth.from(LocalDate.parse(order.date)).toString() == vm.orderMonthFilter
            }.getOrDefault(false)

            val matchesActive = !vm.orderActiveOnly || (order.status != "consegnato" && order.status != "annullato")
            matchesSearch && matchesMonth && matchesActive
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ordini", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AppNavy)
                    Text("${filteredOrders.size} di ${vm.orders.size} ordini", color = Color(0xFF64748B))
                }
                OutlinedButton(
                    onClick = { vm.openOrder() },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Nuovo") }
            }
        }
        item { AppTextField(search, { search = it }, "Cerca cliente, ordine, tracking o articolo", leading = { Icon(Icons.Default.Search, null) }) }
        if (vm.orderMonthFilter != null || vm.orderActiveOnly) {
            item {
                AssistChip(
                    onClick = { vm.clearOrderDrillDown() },
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = LegacyBorder,
                        borderWidth = 1.5.dp
                    ),
                    label = {
                        Text(
                            when {
                                vm.orderActiveOnly -> "Solo ordini in corso"
                                vm.orderMonthFilter != null -> "Mese: " + vm.orderMonthFilter
                                else -> "Filtro dashboard"
                            }
                        )
                    },
                    trailingIcon = { Icon(Icons.Default.Close, null) }
                )
            }
        }
        if (filteredOrders.isEmpty()) {
            item { EmptyState("Nessun ordine", if (search.isBlank()) "Gli ordini compariranno qui." else "Nessun ordine corrisponde alla ricerca.") }
        } else {
            items(filteredOrders, key = { it.id }) { order ->
                Card(
                    Modifier.fillMaxWidth().clickable { vm.openOrderDetail(order) },
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(order.customerName.ifBlank { "Cliente" }, fontSize = 19.sp, fontWeight = FontWeight.Black, color = AppNavy)
                                Text(order.date, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                            }
                            val delivered = order.status == "consegnato"
                            Surface(
                                color = if (delivered) Color(0xFFE9FFF3) else Color(0xFFFFF7E5),
                                shape = RoundedCornerShape(18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (delivered) Color(0xFF86EFAC) else Color(0xFFFCD34D))
                            ) {
                                Text(
                                    orderStatusLabel(order.status),
                                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (delivered) Positive else Color(0xFF92400E),
                                    fontWeight = FontWeight.Black, fontSize = 12.sp
                                )
                            }
                        }
                        if (order.itemNames.isNotEmpty()) {
                            Text(
                                "Articoli: " + order.itemNames.joinToString(" · "),
                                color = Color(0xFF64748B),
                                maxLines = 3
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val amountLabel = if (order.paid || order.totalPaid > 0) "Pagato" else "Totale"
                            val amount = if (order.totalPaid > 0) order.totalPaid else order.total
                            Text("$amountLabel: ${money(amount)}", fontWeight = FontWeight.Bold, color = if (order.paid || order.totalPaid > 0) Positive else AppNavy)
                            Text("Guadagno ${money(order.profit)}", fontWeight = FontWeight.Bold, color = if (order.profit >= 0) Positive else Negative)
                        }
                        if (order.paymentStatus.isNotBlank()) {
                            Text(
                                "Pagamento: " + order.paymentStatus.replace('_', ' '),
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (order.trackingCode.isNotBlank()) Text("Tracking: ${order.trackingCode}", color = Color(0xFF64748B))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun orderStatusLabel(status: String): String = when (status) {
    "consegnato" -> "✓ Consegnato"
    "spedito" -> "Spedito"
    "annullato" -> "Annullato"
    else -> "In lavorazione"
}

@Composable
private fun ArchivesScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(EntityKind.BRAND to "Marche", EntityKind.SUPPLIER to "Fornitori", EntityKind.CATEGORY to "Categorie").forEachIndexed { index, pair ->
                SegmentedButton(
                    selected = vm.archiveKind == pair.first, onClick = { vm.archiveKind = pair.first },
                    shape = SegmentedButtonDefaults.itemShape(index, 3)
                ) { Text(pair.second) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val rows: List<Triple<String, String, String>> = when (vm.archiveKind) {
            EntityKind.BRAND -> vm.brands.map { Triple(it.id, it.name, it.notes) }
            EntityKind.SUPPLIER -> vm.suppliers.map { Triple(it.id, it.name, listOf(it.contact, it.phone, it.email).filter(String::isNotBlank).joinToString(" · ")) }
            EntityKind.CATEGORY -> vm.categories.sortedBy { it.sortOrder }.map { Triple(it.id, it.name, "Ordine ${it.sortOrder}${it.description.takeIf(String::isNotBlank)?.let { d -> " · $d" }.orEmpty()}") }
        }
        if (rows.isEmpty()) EmptyState("Nessun dato", "Aggiungi una voce. L’app non carica marche o fornitori predefiniti.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.first }) { row ->
                Card(Modifier.fillMaxWidth().clickable { vm.openEntity(vm.archiveKind, row.first) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(row.second, fontWeight = FontWeight.Bold); if (row.third.isNotBlank()) Text(row.third, color = Color(0xFF667085), fontSize = 12.sp) }
                        if (vm.archiveKind == EntityKind.CATEGORY) {
                            IconButton(onClick = { vm.categories.firstOrNull { it.id == row.first }?.let { vm.moveCategory(it, -1) } }) { Icon(Icons.Default.KeyboardArrowUp, "Sposta su") }
                            IconButton(onClick = { vm.categories.firstOrNull { it.id == row.first }?.let { vm.moveCategory(it, 1) } }) { Icon(Icons.Default.KeyboardArrowDown, "Sposta giù") }
                        }
                        Icon(Icons.Default.Edit, null, tint = AppBlue)
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val languagePrefs = remember(context) { context.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE) }
    var appLanguage by remember { mutableStateOf(languagePrefs.getString("app_language", "system") ?: "system") }
    var dataAction by remember { mutableStateOf<String?>(null) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingBackup.orEmpty()) }
                ?: error("Impossibile aprire il file")
        }.onSuccess { exportMessage = "Backup esportato correttamente" }
            .onFailure { exportMessage = "Esportazione non riuscita: ${it.message}" }
        pendingBackup = null
    }
    fun startExport(withPhotos: Boolean) {
        pendingBackup = vm.createBackupJson(withPhotos)
        val suffix = if (withPhotos) "completo" else "leggero"
        exportLauncher.launch("gestionale-backup-$suffix-${System.currentTimeMillis()}.json")
    }
    dataAction?.let { action ->
        val message = when (action) {
            "replace" -> stringResource(R.string.replace_demo_confirm)
            "demo" -> stringResource(R.string.delete_demo_confirm)
            else -> stringResource(R.string.delete_all_confirm)
        }
        AlertDialog(
            onDismissRequest = { dataAction = null },
            title = { Text(stringResource(R.string.data_management), fontWeight = FontWeight.Black) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            "replace" -> vm.replaceWithDemoData()
                            "demo" -> vm.deleteDemoData()
                            else -> vm.deleteAllData()
                        }
                        dataAction = null
                    }
                ) { Text(if (action == "replace") stringResource(R.string.replace_demo) else stringResource(R.string.delete), color = if (action == "replace") AppBlue else Negative) }
            },
            dismissButton = { TextButton(onClick = { dataAction = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SettingsHeader(stringResource(R.string.demo_data), stringResource(R.string.demo_data_sub))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, AppBlue.copy(alpha = .25f))
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = vm::loadDemoData,
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                    ) {
                        Icon(Icons.Default.Dataset, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.load_demo), fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = { dataAction = "replace" },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.replace_demo), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { dataAction = "demo" },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, tint = Negative)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_demo), color = Negative, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.demo_notice), color = Color(0xFF64748B), fontSize = 12.sp)
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { dataAction = "all" },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Negative.copy(alpha = .55f))
                    ) {
                        Icon(Icons.Default.DeleteForever, null, tint = Negative)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_all_data), color = Negative, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            SettingsHeader(stringResource(R.string.appearance), stringResource(R.string.appearance_sub))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.theme), fontWeight = FontWeight.Black, fontSize = 17.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AppThemeMode.SYSTEM to stringResource(R.string.system),
                        AppThemeMode.LIGHT to stringResource(R.string.light),
                        AppThemeMode.DARK to stringResource(R.string.dark)
                    ).forEach { option ->
                        FilterChip(selected = vm.themeMode == option.first, onClick = { vm.updateThemeMode(option.first) }, label = { Text(option.second) })
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.language), fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(stringResource(R.string.language_sub), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                SelectionField(
                    stringResource(R.string.language),
                    appLanguage,
                    listOf(
                        "system" to stringResource(R.string.system),
                        "it" to "Italiano",
                        "en" to "English",
                        "fr" to "Français",
                        "es" to "Español",
                        "de" to "Deutsch"
                    ),
                    { selected ->
                        val tag = selected ?: "system"
                        appLanguage = tag
                        languagePrefs.edit().putString("app_language", tag).apply()
                        if (tag == "system") {
                            Locale.setDefault(Locale.getDefault())
                        }
                        (context as? android.app.Activity)?.recreate()
                    }
                )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.text_size), fontWeight = FontWeight.Bold); Text("${(vm.fontScale * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Slider(value = vm.fontScale, onValueChange = vm::updateFontScale, valueRange = .85f..1.35f, steps = 9)
                SettingSwitch(stringResource(R.string.compact_mode), stringResource(R.string.compact_mode_sub), vm.compactMode, vm::updateCompactMode)
                SettingSwitch(stringResource(R.string.grid_catalog), stringResource(R.string.grid_catalog_sub), vm.gridView, vm::updateGridView)
            } }
        }
        item {
            SettingsHeader(stringResource(R.string.data_backup), stringResource(R.string.data_backup_sub))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { startExport(true) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_full_backup)) }
                OutlinedButton(onClick = { startExport(false) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.export_without_photos)) }
                Text(stringResource(R.string.backup_note), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                exportMessage?.let { Text(it, color = if (it.startsWith("Backup")) Positive else Negative, fontWeight = FontWeight.Bold) }
            } }
        }
        item {
            SettingsHeader("Anagrafiche", "Marche, fornitori e categorie creati da te.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(10.dp)) {
                SettingsLink(Icons.Default.Label, "Marche", "${vm.brands.size} elementi") { vm.archiveKind = EntityKind.BRAND; vm.selectTab(MainTab.ARCHIVES) }
                SettingsLink(Icons.Default.LocalShipping, "Fornitori", "${vm.suppliers.size} elementi") { vm.archiveKind = EntityKind.SUPPLIER; vm.selectTab(MainTab.ARCHIVES) }
                SettingsLink(Icons.Default.Category, "Categorie", "${vm.categories.size} elementi") { vm.archiveKind = EntityKind.CATEGORY; vm.selectTab(MainTab.ARCHIVES) }
            } }
        }
        item {
            SettingsHeader(stringResource(R.string.diagnostics), "Stato reale dei dati caricati nell’app.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ValueRow(stringResource(R.string.cloud_connection), if (vm.session != null) "Attiva" else "Disconnessa", if (vm.session != null) Positive else Negative)
                ValueRow("Articoli", vm.products.size.toString())
                ValueRow(stringResource(R.string.linked_photos), vm.products.sumOf { it.photos.size }.toString())
                ValueRow(stringResource(R.string.last_update), vm.lastSyncAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "Mai")
                OutlinedButton(onClick = vm::loadAll, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.refresh_cloud)) }
            } }
        }
        item {
            SettingsHeader(stringResource(R.string.account), "Sessione protetta e dati separati dagli altri utenti.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.session?.email.orEmpty(), fontWeight = FontWeight.Bold)
                Text("ID account: ${vm.session?.userId?.take(8)}…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                OutlinedButton(onClick = vm::logout, enabled = !vm.saving, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.logout)) }
            } }
        }
        item {
            SettingsHeader(stringResource(R.string.information), "Versione tecnica e protezione dei dati.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Gestionale Android 0.5.0", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Applicazione Android nativa · base Free + Premium", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Fotocamera facoltativa · archivio immagini privato · isolamento dati tramite Supabase RLS.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable private fun SettingsHeader(title: String, subtitle: String) = Column(Modifier.padding(horizontal = 2.dp)) {
    Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    Spacer(Modifier.height(7.dp))
}

@Composable private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        Switch(checked, onChange)
    }

@Composable private fun SettingsLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(42.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) { Icon(icon, null, Modifier.padding(10.dp)) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        Icon(Icons.Default.ChevronRight, null)
    }


@Composable
private fun DetailScaffold(
    vm: AppViewModel,
    subtitle: String,
    selectedTab: MainTab,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(color = AppNavy) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.closeDetail() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text(subtitle, color = Color(0xFFD6D9E2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = { vm.closeDetail(); vm.selectTab(MainTab.SETTINGS) },
                        modifier = Modifier.border(1.5.dp, Color.White, RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = Color.White,
                shadowElevation = 10.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BottomPill(stringResource(R.string.dashboard), selectedTab == MainTab.HOME, Modifier.weight(1f)) { vm.closeDetail(); vm.selectTab(MainTab.HOME) }
                    BottomPill(stringResource(R.string.articles), selectedTab == MainTab.ARTICLES, Modifier.weight(1f)) { vm.closeDetail(); vm.selectTab(MainTab.ARTICLES) }
                    BottomPill(stringResource(R.string.customers), selectedTab == MainTab.CLIENTS, Modifier.weight(1f)) { vm.closeDetail(); vm.selectTab(MainTab.CLIENTS) }
                    BottomPill(stringResource(R.string.orders), selectedTab == MainTab.ORDERS, Modifier.weight(1f)) { vm.closeDetail(); vm.selectTab(MainTab.ORDERS) }
                }
            }
        }
    ) { padding -> content(padding) }
}

@Composable
private fun DetailInfoRow(label: String, value: String, valueColor: Color = AppNavy) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                label,
                modifier = Modifier.width(118.dp),
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Black
            )
            Text(
                value.ifBlank { "—" },
                modifier = Modifier.weight(1f),
                color = valueColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        HorizontalDivider(color = Color(0xFFCBD5E1))
    }
}

@Composable
private fun ProductDetailScreen(vm: AppViewModel, productId: String) {
    val product = vm.products.firstOrNull { it.id == productId }
    if (product == null) {
        DetailScaffold(vm, "Dettaglio articolo", MainTab.ARTICLES) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.product_not_found))
            }
        }
        return
    }
    val brand = vm.brands.firstOrNull { it.id == product.brandId }?.name.orEmpty()
    val category = vm.categories.firstOrNull { it.id == product.categoryId }?.name.orEmpty()
    val supplier = vm.suppliers.firstOrNull { it.id == product.supplierId }?.name.orEmpty()
    val code = product.code.ifBlank { product.sku.ifBlank { "Senza codice" } }

    DetailScaffold(vm, "Dettaglio articolo", MainTab.ARTICLES) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF8))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontSize = 30.sp, fontWeight = FontWeight.Black, color = AppNavy)
                                Text("Cod. $code", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (product.available && product.quantity > 0) Color(0xFFE9FFF3) else Color(0xFFFFE7E5),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, LegacyBorder)
                            ) {
                                Text(
                                    if (product.available && product.quantity > 0) "Disponibile" else "Non disponibile",
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = if (product.available && product.quantity > 0) Positive else Negative,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        ProductPhotoCarousel(
                            product = product,
                            vm = vm,
                            aspectRatio = 1.22f,
                            rounded = RoundedCornerShape(20.dp),
                            showThumbnails = true
                        )
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        DetailInfoRow("Marca", brand)
                        DetailInfoRow("Categoria", category)
                        DetailInfoRow("Fornitore", supplier)
                        if (product.measureType.isNotBlank() || product.measureValue.isNotBlank()) {
                            DetailInfoRow(
                                when (product.measureType) {
                                    "peso" -> "Peso"
                                    "misura" -> "Misura"
                                    else -> "Misura / peso"
                                },
                                product.measureValue
                            )
                        }
                        DetailInfoRow("Descrizione", product.description)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8DF))
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sale_price), color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Text(money(product.effectiveSalePrice), fontSize = 26.sp, fontWeight = FontWeight.Black, color = AppNavy)
                        }
                        VerticalDivider(Modifier.height(58.dp), color = Color(0xFFCBD5E1))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Margine", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            Text(
                                money(product.effectiveMarginEuro),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = if (product.effectiveMarginEuro >= 0) Positive else Negative
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.openProduct(product) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                    ) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.edit), fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = { vm.requestDelete(DeleteTarget.ProductTarget(product)) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Negative)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Negative)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete), color = Negative, fontWeight = FontWeight.Black)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CustomerDetailScreen(vm: AppViewModel, customerId: String) {
    val customer = vm.customers.firstOrNull { it.id == customerId }
    if (customer == null) {
        DetailScaffold(vm, "Dettaglio cliente", MainTab.CLIENTS) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cliente non trovato") }
        }
        return
    }
    val customerOrders = vm.orders.filter { it.customerId == customer.id }
    val totalPurchases = customerOrders.sumOf { if (it.totalPaid > 0) it.totalPaid else it.total }
    val lastOrder = customerOrders.maxByOrNull { it.date }

    DetailScaffold(vm, "Dettaglio cliente", MainTab.CLIENTS) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF8))
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(customer.displayName, Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Black, color = AppNavy)
                            if (customer.country.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, LegacyBorder),
                                    color = Color.White
                                ) {
                                    Text(customer.country, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        DetailInfoRow("Telefono", customer.phone)
                        DetailInfoRow("Email", customer.email)
                        DetailInfoRow("Paese", customer.country)
                        DetailInfoRow("Città", listOf(customer.city, customer.province).filter { it.isNotBlank() }.joinToString(" • "))
                        DetailInfoRow("Indirizzo", customer.address)
                        DetailInfoRow("Note", customer.notes)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.closeDetail()
                        vm.selectTab(MainTab.ORDERS)
                    },
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ORDINI", color = Color(0xFF64748B), fontWeight = FontWeight.Black)
                            Text(customerOrders.size.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = AppNavy)
                            Text("ordini totali", color = Color(0xFF64748B))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("TOTALE ACQUISTI", color = Color(0xFF64748B), fontWeight = FontWeight.Black)
                            Text(money(totalPurchases), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Positive)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            if (lastOrder != null) item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { vm.openOrderDetail(lastOrder) },
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ULTIMO ORDINE", color = Color(0xFF64748B), fontWeight = FontWeight.Black)
                            Text(lastOrder.date, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        Text(money(if (lastOrder.totalPaid > 0) lastOrder.totalPaid else lastOrder.total), fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.openCustomer(customer) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                    ) {
                        Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit), fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = { vm.requestDelete(DeleteTarget.CustomerTarget(customer)) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Negative)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Negative); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete), color = Negative, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailScreen(vm: AppViewModel, orderId: String) {
    val order = vm.orders.firstOrNull { it.id == orderId }
    if (order == null) {
        DetailScaffold(vm, "Dettaglio ordine", MainTab.ORDERS) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Ordine non trovato") }
        }
        return
    }
    DetailScaffold(vm, "Dettaglio ordine", MainTab.ORDERS) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF8))
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(order.customerName.ifBlank { "Ordine" }, fontSize = 30.sp, fontWeight = FontWeight.Black, color = AppNavy)
                                Text(order.number.ifBlank { "Ordine del ${order.date}" }, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            }
                            val delivered = order.status == "consegnato"
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (delivered) Color(0xFFE9FFF3) else Color(0xFFFFF7E5),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, LegacyBorder)
                            ) {
                                Text(
                                    orderStatusLabel(order.status),
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = if (delivered) Positive else Color(0xFF92400E),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        DetailInfoRow("Cliente", order.customerName)
                        DetailInfoRow("Data", order.date)
                        DetailInfoRow("Articoli", order.itemNames.joinToString("\n"))
                        DetailInfoRow(
                            if (order.paid || order.totalPaid > 0) "Totale pagato" else "Totale",
                            money(if (order.totalPaid > 0) order.totalPaid else order.total),
                            if (order.paid || order.totalPaid > 0) Positive else AppNavy
                        )
                        DetailInfoRow("Guadagno", money(order.profit), if (order.profit >= 0) Positive else Negative)
                        DetailInfoRow("Tracking", order.trackingCode)
                        DetailInfoRow("Stato", orderStatusLabel(order.status), if (order.status == "consegnato") Positive else AppNavy)
                        if (order.notes.isNotBlank()) DetailInfoRow("Note", order.notes)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.editOrder(order) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                    ) {
                        Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit), fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = { vm.requestDelete(DeleteTarget.OrderTarget(order)) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Negative)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Negative); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete), color = Negative, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorScreen(vm: AppViewModel) {
    val d = vm.productDraft
    val existing = vm.products.firstOrNull { it.id == d.id }
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(vm::addPendingPhoto) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraUri = uri
            takePicture.launch(uri)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { it.forEach(vm::addPendingPhoto) }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraUri = uri
            takePicture.launch(uri)
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (d.id.isBlank()) stringResource(R.string.new_product) else stringResource(R.string.edit), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { TextButton(onClick = vm::saveProduct, enabled = !vm.saving) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) } }
        )
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle(stringResource(R.string.product_data)) }
            item { AppTextField(d.name, { vm.updateProductDraft(d.copy(name = it)) }, stringResource(R.string.product_name)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(d.code, { vm.updateProductDraft(d.copy(code = it)) }, stringResource(R.string.code), Modifier.weight(1f))
                AppTextField(d.sku, { vm.updateProductDraft(d.copy(sku = it)) }, "SKU", Modifier.weight(1f))
            } }
            item { SelectionField(stringResource(R.string.optional_brand), d.brandId, vm.brands.map { it.id to it.name }, { vm.updateProductDraft(d.copy(brandId = it)) }) }
            item { SelectionField(stringResource(R.string.optional_category), d.categoryId, vm.categories.map { it.id to it.name }, { vm.updateProductDraft(d.copy(categoryId = it)) }) }
            item { SelectionField(stringResource(R.string.optional_supplier), d.supplierId, vm.suppliers.map { it.id to it.name }, { vm.updateProductDraft(d.copy(supplierId = it)) }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        SelectionField(
                            stringResource(R.string.measure_weight),
                            d.measureType.ifBlank { null },
                            listOf("misura" to stringResource(R.string.measure), "peso" to stringResource(R.string.weight)),
                            { value -> vm.updateProductDraft(d.copy(measureType = value.orEmpty())) }
                        )
                    }
                    AppTextField(
                        d.measureValue,
                        { vm.updateProductDraft(d.copy(measureValue = it)) },
                        when (d.measureType) {
                            "peso" -> stringResource(R.string.weight)
                            "misura" -> stringResource(R.string.measure)
                            else -> stringResource(R.string.value)
                        },
                        Modifier.weight(1f)
                    )
                }
            }
            item { AppTextField(d.description, { vm.updateProductDraft(d.copy(description = it)) }, stringResource(R.string.description), minLines = 3) }
            item { SectionTitle(stringResource(R.string.prices_availability)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(d.purchasePrice, { vm.updateProductDraft(d.copy(purchasePrice = it)) }, stringResource(R.string.purchase_euro), Modifier.weight(1f))
                NumberField(d.extraCosts, { vm.updateProductDraft(d.copy(extraCosts = it)) }, stringResource(R.string.extra_costs_euro), Modifier.weight(1f))
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(d.salePrice, { vm.updateProductDraft(d.copy(salePrice = it)) }, stringResource(R.string.sale_euro), Modifier.weight(1f))
                AppTextField(d.quantity, { vm.updateProductDraft(d.copy(quantity = it.filter(Char::isDigit))) }, stringResource(R.string.quantity), Modifier.weight(1f), keyboardType = KeyboardType.Number)
            } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(d.inPromotion, { vm.updateProductDraft(d.copy(inPromotion = it)) })
                    Spacer(Modifier.width(10.dp))
                    Text(if (d.inPromotion) stringResource(R.string.product_in_promotion) else stringResource(R.string.no_promotion))
                }
            }
            if (d.inPromotion) item {
                NumberField(
                    d.promotionalPrice,
                    { vm.updateProductDraft(d.copy(promotionalPrice = it)) },
                    stringResource(R.string.promo_price_euro),
                    Modifier.fillMaxWidth()
                )
            }
            item {
                val preview = d.toProduct(existing)
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF))) { Column(Modifier.padding(14.dp)) {
                    Text("Costo totale ${money(preview.totalCost)}")
                    Text("Margine ${money(preview.effectiveMarginEuro)} · ${"%.1f".format(Locale.ITALY, preview.effectiveMarginPercent)}%", fontWeight = FontWeight.Bold, color = if (preview.effectiveMarginEuro >= 0) Positive else Negative)
                } }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(d.available, { vm.updateProductDraft(d.copy(available = it)) }); Spacer(Modifier.width(10.dp)); Text(if (d.available) stringResource(R.string.available) else stringResource(R.string.unavailable)) } }
            item { AppTextField(d.productUrl, { vm.updateProductDraft(d.copy(productUrl = it)) }, stringResource(R.string.product_link), keyboardType = KeyboardType.Uri) }
            item { AppTextField(d.notes, { vm.updateProductDraft(d.copy(notes = it)) }, stringResource(R.string.notes), minLines = 3) }
            item { SectionTitle(stringResource(R.string.images)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.gallery)) }
                OutlinedButton(
                    onClick = ::launchCamera,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.camera)) }
            } }
            if (existing != null && existing.photos.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(existing.photos, key = { it.id }) { photo ->
                        LaunchedEffect(photo.path) { vm.ensureSignedUrl(photo.path) }
                        PhotoTile(vm.signedUrls[photo.path], { vm.requestDelete(DeleteTarget.PhotoTarget(photo)) })
                    }
                }
            }
            if (vm.pendingPhotos.isNotEmpty()) item {
                Text(stringResource(R.string.pending_upload), fontSize = 12.sp, color = Color(0xFF667085))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.pendingPhotos, key = { it.toString() }) { uri -> PhotoTile(uri, { vm.removePendingPhoto(uri) }) }
                }
            }
            item {
                Button(
                    onClick = vm::saveProduct,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) { Text(if (vm.saving) stringResource(R.string.saving) else stringResource(R.string.save_product)) }
            }
            if (existing != null) item { OutlinedButton(onClick = { vm.requestDelete(DeleteTarget.ProductTarget(existing)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_product), color = Negative) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PhotoTile(model: Any?, onDelete: () -> Unit) {
    Box(Modifier.size(116.dp)) {
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(14.dp), color = Color(0xFFEFF4FF)) {
            if (model != null) AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else CircularProgressIndicator(Modifier.padding(42.dp), strokeWidth = 2.dp)
        }
        IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).background(Color.White.copy(alpha = .88f), RoundedCornerShape(50))) {
            Icon(Icons.Default.Close, "Rimuovi", tint = Negative)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerEditorScreen(vm: AppViewModel) {
    val d = vm.customerDraft
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (d.id.isBlank()) "Nuovo cliente" else "Modifica cliente", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { TextButton(onClick = vm::saveCustomer, enabled = !vm.saving) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle(stringResource(R.string.customer_data)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(d.firstName, { vm.updateCustomerDraft(d.copy(firstName = it)) }, stringResource(R.string.name), Modifier.weight(1f))
                    AppTextField(d.lastName, { vm.updateCustomerDraft(d.copy(lastName = it)) }, stringResource(R.string.surname), Modifier.weight(1f))
                }
            }
            item { AppTextField(d.phone, { vm.updateCustomerDraft(d.copy(phone = it)) }, stringResource(R.string.phone), keyboardType = KeyboardType.Phone) }
            item { AppTextField(d.email, { vm.updateCustomerDraft(d.copy(email = it)) }, stringResource(R.string.email), keyboardType = KeyboardType.Email) }
            item { AppTextField(d.address, { vm.updateCustomerDraft(d.copy(address = it)) }, stringResource(R.string.address)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(d.city, { vm.updateCustomerDraft(d.copy(city = it)) }, stringResource(R.string.city), Modifier.weight(1.3f))
                    AppTextField(d.province, { vm.updateCustomerDraft(d.copy(province = it)) }, stringResource(R.string.province), Modifier.weight(.7f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(d.postalCode, { vm.updateCustomerDraft(d.copy(postalCode = it.filter(Char::isDigit))) }, stringResource(R.string.zip_code), Modifier.weight(.7f), keyboardType = KeyboardType.Number)
                    AppTextField(d.country, { vm.updateCustomerDraft(d.copy(country = it)) }, stringResource(R.string.country), Modifier.weight(1.3f))
                }
            }
            item { AppTextField(d.notes, { vm.updateCustomerDraft(d.copy(notes = it)) }, stringResource(R.string.notes), minLines = 3) }
            item {
                Button(
                    onClick = vm::saveCustomer,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) { Text(if (vm.saving) "Salvataggio…" else "Salva cliente", fontWeight = FontWeight.Black) }
            }
            if (d.id.isNotBlank()) item {
                val customer = vm.customers.firstOrNull { it.id == d.id }
                if (customer != null) {
                    OutlinedButton(
                        onClick = { vm.requestDelete(DeleteTarget.CustomerTarget(customer)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.delete_customer), color = Negative) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderEditorScreen(vm: AppViewModel) {
    val d = vm.orderDraft
    val existingOrder = vm.orders.firstOrNull { it.id == d.id }
    val selectedProduct = vm.products.firstOrNull { it.id == d.productId }
    val qty = d.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val total = existingOrder?.total ?: ((selectedProduct?.effectiveSalePrice ?: 0.0) * qty)
    val profit = existingOrder?.profit ?: ((selectedProduct?.effectiveMarginEuro ?: 0.0) * qty)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (d.id.isBlank()) "Nuovo ordine" else "Modifica ordine", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { TextButton(onClick = vm::saveOrder, enabled = !vm.saving) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle(stringResource(R.string.customer_and_product)) }
            item {
                SelectionField(
                    "Cliente *",
                    d.customerId,
                    vm.customers.map { it.id to it.displayName },
                    { vm.updateOrderDraft(d.copy(customerId = it)) }
                )
            }
            if (d.id.isBlank()) item {
                SelectionField(
                    "Articolo *",
                    d.productId,
                    vm.products.map { it.id to (it.name + if (it.code.isNotBlank()) " · " + it.code else "") },
                    { vm.updateOrderDraft(d.copy(productId = it)) }
                )
            }
            if (d.id.isBlank()) item {
                AppTextField(d.quantity, { vm.updateOrderDraft(d.copy(quantity = it.filter(Char::isDigit))) }, stringResource(R.string.quantity), keyboardType = KeyboardType.Number)
            }
            if (d.id.isNotBlank() && existingOrder != null) item {
                Card(
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.order_products), fontWeight = FontWeight.Black, color = AppNavy)
                        Text(existingOrder.itemNames.joinToString("\n").ifBlank { "Nessun articolo" }, color = Color(0xFF64748B))
                    }
                }
            }
            item { AppTextField(d.date, { vm.updateOrderDraft(d.copy(date = it)) }, stringResource(R.string.order_date)) }
            item {
                SelectionField(
                    "Stato",
                    d.status,
                    listOf(
                        "in_lavorazione" to "In lavorazione",
                        "spedito" to "Spedito",
                        "consegnato" to "Consegnato",
                        "annullato" to "Annullato"
                    ),
                    { value -> if (value != null) vm.updateOrderDraft(d.copy(status = value)) }
                )
            }
            item { SectionTitle(stringResource(R.string.payment)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(d.paid, { vm.updateOrderDraft(d.copy(paid = it)) })
                    Spacer(Modifier.width(10.dp))
                    Text(if (d.paid) "Ordine pagato" else "Da pagare", fontWeight = FontWeight.Bold)
                }
            }
            if (d.paid) item {
                NumberField(
                    d.amountPaid,
                    { vm.updateOrderDraft(d.copy(amountPaid = it)) },
                    "Importo pagato € (vuoto = totale)",
                    Modifier.fillMaxWidth()
                )
            }
            if (d.paid) item {
                SelectionField(
                    "Metodo pagamento",
                    d.paymentMethod,
                    listOf(
                        "contanti" to "Contanti",
                        "carta" to "Carta",
                        "bonifico" to "Bonifico",
                        "paypal" to "PayPal",
                        "altro" to "Altro"
                    ),
                    { value -> if (value != null) vm.updateOrderDraft(d.copy(paymentMethod = value)) }
                )
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF)),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.summary), fontWeight = FontWeight.Black)
                        ValueRow("Totale ordine", money(total))
                        ValueRow("Guadagno previsto", money(profit), if (profit >= 0) Positive else Negative)
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.shipping)) }
            item { AppTextField(d.courier, { vm.updateOrderDraft(d.copy(courier = it)) }, stringResource(R.string.courier)) }
            item { AppTextField(d.trackingCode, { vm.updateOrderDraft(d.copy(trackingCode = it)) }, "Tracking") }
            item { AppTextField(d.notes, { vm.updateOrderDraft(d.copy(notes = it)) }, stringResource(R.string.notes), minLines = 3) }
            item {
                Button(
                    onClick = vm::saveOrder,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder)
                ) { Text(if (vm.saving) "Salvataggio…" else if (d.id.isBlank()) "Crea ordine" else "Salva modifiche", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityEditorScreen(vm: AppViewModel, kind: EntityKind) {
    val d = vm.entityDraft
    val title = when (kind) { EntityKind.BRAND -> "marca"; EntityKind.SUPPLIER -> "fornitore"; EntityKind.CATEGORY -> "categoria" }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (d.id.isBlank()) "Nuova $title" else "Modifica $title", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { TextButton(onClick = vm::saveEntity, enabled = !vm.saving) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AppTextField(d.name, { vm.updateEntityDraft(d.copy(name = it)) }, "Nome *") }
            when (kind) {
                EntityKind.BRAND -> item { AppTextField(d.notes, { vm.updateEntityDraft(d.copy(notes = it)) }, stringResource(R.string.notes), minLines = 3) }
                EntityKind.CATEGORY -> {
                    item { AppTextField(d.description, { vm.updateEntityDraft(d.copy(description = it)) }, stringResource(R.string.description), minLines = 2) }
                    item { AppTextField(d.sortOrder, { vm.updateEntityDraft(d.copy(sortOrder = it.filter { c -> c.isDigit() || c == '-' })) }, "Ordine", keyboardType = KeyboardType.Number) }
                }
                EntityKind.SUPPLIER -> {
                    item { AppTextField(d.contact, { vm.updateEntityDraft(d.copy(contact = it)) }, "Referente") }
                    item { AppTextField(d.phone, { vm.updateEntityDraft(d.copy(phone = it)) }, stringResource(R.string.phone), keyboardType = KeyboardType.Phone) }
                    item { AppTextField(d.email, { vm.updateEntityDraft(d.copy(email = it)) }, stringResource(R.string.email), keyboardType = KeyboardType.Email) }
                    item { AppTextField(d.website, { vm.updateEntityDraft(d.copy(website = it)) }, "Sito web", keyboardType = KeyboardType.Uri) }
                    item { AppTextField(d.catalogUrl, { vm.updateEntityDraft(d.copy(catalogUrl = it)) }, "Link catalogo", keyboardType = KeyboardType.Uri) }
                    item { AppTextField(d.address, { vm.updateEntityDraft(d.copy(address = it)) }, stringResource(R.string.address)) }
                    item { AppTextField(d.notes, { vm.updateEntityDraft(d.copy(notes = it)) }, stringResource(R.string.notes), minLines = 3) }
                }
            }
            item { Button(onClick = vm::saveEntity, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (vm.saving) "Salvataggio…" else stringResource(R.string.save)) } }
            if (d.id.isNotBlank()) item {
                OutlinedButton(onClick = { vm.requestDelete(DeleteTarget.EntityTarget(kind, d.id, d.name)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete), color = Negative) }
            }
        }
    }
}

@Composable
private fun SelectionField(label: String, selected: String?, options: List<Pair<String, String>>, onSelect: (String?) -> Unit, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = if (compact) Modifier.height(38.dp).widthIn(min = 112.dp) else Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, LegacyBorder),
            contentPadding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 0.dp) else ButtonDefaults.ContentPadding
        ) {
            Text(
                options.firstOrNull { it.first == selected }?.second ?: if (compact) label else "$label: ${stringResource(R.string.none)}",
                maxLines = 1,
                fontSize = if (compact) 11.sp else 14.sp
            )
            Spacer(Modifier.width(if (compact) 3.dp else 6.dp))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(if (compact) 16.dp else 24.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.none)) }, onClick = { onSelect(null); open = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option.second) }, onClick = { onSelect(option.first); open = false }) }
        }
    }
}

@Composable
private fun AppTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text, minLines: Int = 1, leading: (@Composable (() -> Unit))? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (minLines > 1) ImeAction.Default else ImeAction.Next),
        singleLine = minLines == 1, minLines = minLines, leadingIcon = leading
    )
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) = AppTextField(
    value, { next -> if (next.count { it == ',' || it == '.' } <= 1 && next.all { it.isDigit() || it == ',' || it == '.' }) onValueChange(next.replace(',', '.')) },
    label, modifier, KeyboardType.Decimal
)

@Composable private fun SectionTitle(text: String) = Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold)

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inventory2, null, Modifier.size(48.dp), tint = Color(0xFF98A2B3))
            Spacer(Modifier.height(10.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(subtitle, color = Color(0xFF667085))
        }
    }
}

@Composable
private fun LoadingScreen(label: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(label) }
}

private fun money(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    format.currency = java.util.Currency.getInstance("EUR")
    return format.format(value)
}

private fun tabTitle(tab: MainTab): String = when (tab) {
    MainTab.HOME -> "Dashboard"
    MainTab.ARTICLES -> "Articoli"
    MainTab.CLIENTS -> "Clienti"
    MainTab.ORDERS -> "Ordini"
    MainTab.ARCHIVES -> "Anagrafiche"
    MainTab.SETTINGS -> "Impostazioni"
}

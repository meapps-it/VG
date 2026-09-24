package it.meapps.gestionale

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private val AppNavy = Color(0xFF0F172A)
private val AppBlue = Color(0xFF2563EB)
private val AppAmber = Color(0xFFF59E0B)
private val AppBackground = Color(0xFFF8FAFC)
private val WarmSurface = Color(0xFFFFFBEB)
private val Positive = Color(0xFF087443)
private val Negative = Color(0xFFB42318)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

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
                    vm.checkingAuth -> LoadingScreen("Verifica accesso…")
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
                Text(if (register) "Crea account" else "Gestionale", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Articoli, anagrafiche e margini. Senza cataloghi imposti.", color = Color(0xFF667085))
                AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                )
                Button(
                    onClick = { if (register) vm.signUp(email, password) else vm.login(email, password) },
                    enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { if (vm.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(if (register) "Registrati" else "Accedi") }
                TextButton(onClick = { register = !register }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (register) "Ho già un account" else "Crea un nuovo account")
                }
                if (!register) TextButton(onClick = { vm.resetPassword(email) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Password dimenticata")
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
    val activity = LocalContext.current as? Activity
    BackHandler(enabled = true) {
        if (!vm.navigateBack()) activity?.moveTaskToBack(true)
    }

    vm.deleteTarget?.let { target ->
        val label = when (target) {
            is DeleteTarget.ProductTarget -> target.product.name
            is DeleteTarget.EntityTarget -> target.name
            is DeleteTarget.PhotoTarget -> "questa foto"
        }
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Conferma eliminazione") },
            text = { Text("Eliminare $label? L’operazione non può essere annullata.") },
            confirmButton = { TextButton(onClick = vm::confirmDelete, enabled = !vm.saving) { Text("Elimina", color = Negative) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Annulla") } }
        )
    }

    when (val editor = vm.editor) {
        is Editor.ProductEditor -> ProductEditorScreen(vm)
        is Editor.EntityEditor -> EntityEditorScreen(vm, editor.kind)
        is Editor.CustomerEditor -> CustomerEditorScreen(vm)
        Editor.OrderEditor -> OrderEditorScreen(vm)
        null -> MainScaffold(vm, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AppViewModel, snackbar: SnackbarHostState) {
    var menuOpen by remember { mutableStateOf(false) }
    val nowText = remember { DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.ITALIAN).format(Date()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = AppNavy, shadowElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Gestionale", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text(nowText, color = Color(0xFFD6D9E2), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box {
                        FilledTonalIconButton(
                            onClick = { menuOpen = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            )
                        ) { Icon(Icons.Default.Menu, "Menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Anagrafiche") },
                                leadingIcon = { Icon(Icons.Default.ListAlt, null) },
                                onClick = { menuOpen = false; vm.selectTab(MainTab.ARCHIVES) }
                            )
                            DropdownMenuItem(
                                text = { Text("Impostazioni") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuOpen = false; vm.selectTab(MainTab.SETTINGS) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Aggiorna dati") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { menuOpen = false; vm.loadAll() }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 12.dp, color = Color.White) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BottomPill("Dashboard", vm.selectedTab == MainTab.HOME, Modifier.weight(1f)) { vm.selectTab(MainTab.HOME) }
                    BottomPill("Articoli", vm.selectedTab == MainTab.ARTICLES, Modifier.weight(1f)) { vm.selectTab(MainTab.ARTICLES) }
                    BottomPill("Clienti", vm.selectedTab == MainTab.CLIENTS, Modifier.weight(1f)) { vm.selectTab(MainTab.CLIENTS) }
                    BottomPill("Ordini", vm.selectedTab == MainTab.ORDERS, Modifier.weight(1f)) { vm.selectTab(MainTab.ORDERS) }
                }
            }
        },
        floatingActionButton = {
            if (vm.selectedTab == MainTab.ARTICLES || vm.selectedTab == MainTab.ARCHIVES) {
                FloatingActionButton(
                    onClick = {
                        if (vm.selectedTab == MainTab.ARTICLES) vm.openProduct()
                        else vm.openEntity(vm.archiveKind)
                    },
                    containerColor = AppAmber,
                    contentColor = AppNavy
                ) { Icon(Icons.Default.Add, "Aggiungi") }
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
private fun BottomPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(52.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) Color(0xFFFFF3CF) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) AppAmber else Color(0xFFCBD5E1)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = AppNavy, maxLines = 1)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NOVITÀ IN PRIMO PIANO", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (orders.size < 4) "Vendite sotto media" else "Andamento vendite",
                        fontSize = 25.sp, fontWeight = FontWeight.Black, color = AppNavy
                    )
                    Text(
                        if (orders.size < 4) "Meno di una vendita a settimana: serve più movimento."
                        else "Qui vedi l'andamento reale degli ultimi sei mesi.",
                        color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ANDAMENTO ULTIMI 6 MESI", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B))
                        Text("INCASSATO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF64748B))
                    }
                    Row(
                        Modifier.fillMaxWidth().height(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        sixMonths.forEachIndexed { index, month ->
                            val h = (18 + (monthValues[index] / maxValue * 72)).dp
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.fillMaxWidth().height(h)
                                        .background(if (index == 5) AppAmber else AppBlue, RoundedCornerShape(14.dp))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(monthNames[month.monthValue - 1], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegacyStatCard("ORDINI IN CORSO", activeOrders.toString(), "non ancora consegnati", Modifier.weight(1f))
                LegacyStatCard("GUADAGNO MESE", money(monthProfit), "margine del mese corrente", Modifier.weight(1f), Positive)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegacyStatCard("MEDIA ORDINI", money(averageOrder), "valore medio per ordine", Modifier.weight(1f))
                LegacyStatCard("GUADAGNO TOTALE", money(totalProfit), "margine complessivo", Modifier.weight(1f), Positive)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Azioni rapide", fontSize = 22.sp, fontWeight = FontWeight.Black, color = AppNavy)
                    Button(
                        onClick = { vm.openProduct() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                    ) { Text("Nuovo articolo", fontWeight = FontWeight.Black) }
                    OutlinedButton(
                        onClick = { vm.openOrder() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("Nuovo ordine", fontWeight = FontWeight.Black, color = AppNavy) }
                    OutlinedButton(
                        onClick = { vm.openCustomer() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("Nuovo cliente", fontWeight = FontWeight.Black, color = AppNavy) }
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
    valueColor: Color = AppNavy
) {
    Card(modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF475569))
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1)
            Text(subtitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
        }
    }
}
@Composable
private fun DashboardStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        AppTextField(vm.query, { vm.query = it }, "Cerca nome, codice o SKU", leading = { Icon(Icons.Default.Search, null) })
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectionField("Categoria", vm.categoryFilter, vm.categories.map { it.id to it.name }, { vm.categoryFilter = it }, compact = true)
            SelectionField("Marca", vm.brandFilter, vm.brands.map { it.id to it.name }, { vm.brandFilter = it }, compact = true)
            SelectionField("Fornitore", vm.supplierFilter, vm.suppliers.map { it.id to it.name }, { vm.supplierFilter = it }, compact = true)
            SelectionField(
                "Qualità",
                vm.qualityFilter,
                vm.products.map { it.quality.trim() }.filter { it.isNotBlank() }.distinct().sorted().map { it to it },
                { vm.qualityFilter = it },
                compact = true
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !vm.promoOnly,
                onClick = { vm.promoOnly = false },
                label = { Text("Tutto") }
            )
            FilterChip(
                selected = vm.promoOnly,
                onClick = { vm.promoOnly = true },
                label = { Text("Solo promo") }
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ArticleCounter("Totale articoli", vm.products.size.toString(), Modifier.weight(1f))
            ArticleCounter("Senza foto", vm.products.count { it.photos.isEmpty() }.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${vm.filteredProducts.size} visualizzati",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.updateGridView(!vm.gridView) }) {
                Icon(
                    if (vm.gridView) Icons.Default.ViewList else Icons.Default.GridView,
                    if (vm.gridView) "Vista elenco" else "Vista griglia"
                )
            }
        }

        if (!vm.loading && vm.filteredProducts.isEmpty()) {
            EmptyState("Nessun articolo", "Crea il primo articolo oppure modifica i filtri.")
        } else if (vm.gridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (vm.compactMode) 150.dp else 174.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(vm.filteredProducts, key = { it.id }) { product -> ProductGridCard(product, vm) }
                item { Spacer(Modifier.height(88.dp)) }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.filteredProducts, key = { it.id }) { product -> ProductCard(product, vm) }
                item { Spacer(Modifier.height(88.dp)) }
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
private fun ProductGridCard(product: Product, vm: AppViewModel) {
    val context = LocalContext.current
    val imagePath = product.photos.minByOrNull { it.order }?.path
    if (imagePath != null) LaunchedEffect(imagePath) { vm.ensureSignedUrl(imagePath) }
    val borderColor = if (!product.available || product.quantity <= 0) Negative.copy(alpha = .65f) else Color(0xFFD9E1EC)
    val brandName = vm.brands.firstOrNull { it.id == product.brandId }?.name.orEmpty()
    val code = product.code.ifBlank { product.sku.ifBlank { "Senza codice" } }

    Card(
        Modifier.fillMaxWidth().clickable { vm.openProduct(product) },
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column {
            Box {
                Surface(
                    Modifier.fillMaxWidth().aspectRatio(1.15f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (imagePath != null && vm.signedUrls[imagePath] != null) {
                        AsyncImage(vm.signedUrls[imagePath], null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Image, null, Modifier.padding(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xE60F172A)
                ) {
                    Text(
                        "${product.photos.size} foto",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                if (!product.available || product.quantity <= 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xE6B42318)
                    ) {
                        Text("NON DISP.", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (brandName.isNotBlank()) {
                    Text(brandName.uppercase(), color = AppBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
                Text(product.name, fontWeight = FontWeight.Black, maxLines = 2, minLines = if (vm.compactMode) 1 else 2, color = AppNavy)
                Text(code, color = Color(0xFF64748B), fontSize = 11.sp, maxLines = 1)
                if (product.quality.isNotBlank()) {
                    Text(product.quality, color = Color(0xFF475569), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                if (product.inPromotion && product.promotionalPrice > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = Color(0xFFFFF3CF), shape = RoundedCornerShape(10.dp)) {
                            Text("PROMO", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color(0xFF92400E), fontSize = 9.sp, fontWeight = FontWeight.Black)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { shareProduct(context, product, "com.facebook.katana") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) { Text("Facebook", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                    FilledTonalButton(
                        onClick = { shareProduct(context, product, "org.telegram.messenger") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) { Text("Telegram", fontSize = 10.sp, fontWeight = FontWeight.Black) }
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
    Card(Modifier.fillMaxWidth().clickable { vm.openProduct(product) }, shape = RoundedCornerShape(18.dp)) {
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
                FilledTonalButton(onClick = { vm.openCustomer() }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Nuovo") }
            }
        }
        item { AppTextField(search, { search = it }, "Cerca cliente, telefono o città", leading = { Icon(Icons.Default.Search, null) }) }
        if (filteredCustomers.isEmpty()) {
            item { EmptyState("Nessun cliente", if (search.isBlank()) "I clienti compariranno qui." else "Nessun cliente corrisponde alla ricerca.") }
        } else {
            items(filteredCustomers, key = { it.id }) { customer ->
                Card(Modifier.fillMaxWidth().clickable { vm.openCustomer(customer) }, shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(customer.displayName, fontSize = 19.sp, fontWeight = FontWeight.Black, color = AppNavy)
                            if (customer.phone.isNotBlank()) Text("Tel. ${customer.phone}", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                            val place = listOf(customer.city, customer.province).filter { it.isNotBlank() }.joinToString(" • ")
                            if (place.isNotBlank()) Text(place, color = Color(0xFF64748B))
                        }
                        if (customer.country.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)), color = Color.White) {
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
    val filteredOrders = remember(vm.orders, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) vm.orders else vm.orders.filter {
            listOf(it.customerName, it.number, it.status, it.trackingCode, it.courier, it.itemNames.joinToString(" ")).any { value -> value.lowercase().contains(q) }
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
                FilledTonalButton(onClick = { }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Nuovo") }
            }
        }
        item { AppTextField(search, { search = it }, "Cerca cliente, ordine, tracking o articolo", leading = { Icon(Icons.Default.Search, null) }) }
        if (filteredOrders.isEmpty()) {
            item { EmptyState("Nessun ordine", if (search.isBlank()) "Gli ordini compariranno qui." else "Nessun ordine corrisponde alla ricerca.") }
        } else {
            items(filteredOrders, key = { it.id }) { order ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SettingsHeader("Aspetto", "Personalizza l’interfaccia senza impazzire dentro menu inutili.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tema", fontWeight = FontWeight.Black, fontSize = 17.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AppThemeMode.SYSTEM to "Sistema", AppThemeMode.LIGHT to "Chiaro", AppThemeMode.DARK to "Scuro").forEach { option ->
                        FilterChip(selected = vm.themeMode == option.first, onClick = { vm.updateThemeMode(option.first) }, label = { Text(option.second) })
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Dimensione testo", fontWeight = FontWeight.Bold); Text("${(vm.fontScale * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Slider(value = vm.fontScale, onValueChange = vm::updateFontScale, valueRange = .85f..1.35f, steps = 9)
                SettingSwitch("Modalità compatta", "Riduce spazi e dimensioni delle schede.", vm.compactMode, vm::updateCompactMode)
                SettingSwitch("Catalogo a griglia", "Mostra gli articoli con foto grandi come nella vecchia app.", vm.gridView, vm::updateGridView)
            } }
        }
        item {
            SettingsHeader("Dati e backup", "Esporta una copia leggibile dei dati del tuo account.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { startExport(true) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Esporta backup completo") }
                OutlinedButton(onClick = { startExport(false) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Esporta senza riferimenti foto") }
                Text("Le immagini restano protette su Supabase; il backup completo include i riferimenti necessari per ritrovarle.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
            SettingsHeader("Diagnostica", "Stato reale dei dati caricati nell’app.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ValueRow("Connessione cloud", if (vm.session != null) "Attiva" else "Disconnessa", if (vm.session != null) Positive else Negative)
                ValueRow("Articoli", vm.products.size.toString())
                ValueRow("Foto collegate", vm.products.sumOf { it.photos.size }.toString())
                ValueRow("Ultimo aggiornamento", vm.lastSyncAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "Mai")
                OutlinedButton(onClick = vm::loadAll, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Aggiorna dal cloud") }
            } }
        }
        item {
            SettingsHeader("Account", "Sessione protetta e dati separati dagli altri utenti.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.session?.email.orEmpty(), fontWeight = FontWeight.Bold)
                Text("ID account: ${vm.session?.userId?.take(8)}…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                OutlinedButton(onClick = vm::logout, enabled = !vm.saving, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Disconnetti account") }
            } }
        }
        item {
            SettingsHeader("Informazioni", "Versione tecnica e protezione dei dati.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Gestionale Android 0.2.0", fontWeight = FontWeight.Black, fontSize = 18.sp)
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
            title = { Text(if (d.id.isBlank()) "Nuovo articolo" else "Modifica articolo", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = { TextButton(onClick = vm::saveProduct, enabled = !vm.saving) { Text("Salva", fontWeight = FontWeight.Bold) } }
        )
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Dati articolo") }
            item { AppTextField(d.name, { vm.updateProductDraft(d.copy(name = it)) }, "Nome articolo *") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(d.code, { vm.updateProductDraft(d.copy(code = it)) }, "Codice", Modifier.weight(1f))
                AppTextField(d.sku, { vm.updateProductDraft(d.copy(sku = it)) }, "SKU", Modifier.weight(1f))
            } }
            item { SelectionField("Marca (facoltativa)", d.brandId, vm.brands.map { it.id to it.name }, { vm.updateProductDraft(d.copy(brandId = it)) }) }
            item { SelectionField("Categoria (facoltativa)", d.categoryId, vm.categories.map { it.id to it.name }, { vm.updateProductDraft(d.copy(categoryId = it)) }) }
            item { SelectionField("Fornitore (facoltativo)", d.supplierId, vm.suppliers.map { it.id to it.name }, { vm.updateProductDraft(d.copy(supplierId = it)) }) }
            item { AppTextField(d.quality, { vm.updateProductDraft(d.copy(quality = it)) }, "Qualità (facoltativa)") }
            item { AppTextField(d.description, { vm.updateProductDraft(d.copy(description = it)) }, "Descrizione", minLines = 3) }
            item { SectionTitle("Prezzi e disponibilità") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(d.purchasePrice, { vm.updateProductDraft(d.copy(purchasePrice = it)) }, "Acquisto €", Modifier.weight(1f))
                NumberField(d.extraCosts, { vm.updateProductDraft(d.copy(extraCosts = it)) }, "Costi extra €", Modifier.weight(1f))
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(d.salePrice, { vm.updateProductDraft(d.copy(salePrice = it)) }, "Vendita €", Modifier.weight(1f))
                AppTextField(d.quantity, { vm.updateProductDraft(d.copy(quantity = it.filter(Char::isDigit))) }, "Quantità", Modifier.weight(1f), keyboardType = KeyboardType.Number)
            } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(d.inPromotion, { vm.updateProductDraft(d.copy(inPromotion = it)) })
                    Spacer(Modifier.width(10.dp))
                    Text(if (d.inPromotion) "Articolo in promozione" else "Nessuna promozione")
                }
            }
            if (d.inPromotion) item {
                NumberField(
                    d.promotionalPrice,
                    { vm.updateProductDraft(d.copy(promotionalPrice = it)) },
                    "Prezzo promozionale €",
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
            item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(d.available, { vm.updateProductDraft(d.copy(available = it)) }); Spacer(Modifier.width(10.dp)); Text(if (d.available) "Disponibile" else "Non disponibile") } }
            item { AppTextField(d.productUrl, { vm.updateProductDraft(d.copy(productUrl = it)) }, "Link prodotto", keyboardType = KeyboardType.Uri) }
            item { AppTextField(d.notes, { vm.updateProductDraft(d.copy(notes = it)) }, "Note", minLines = 3) }
            item { SectionTitle("Immagini") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galleria") }
                OutlinedButton(onClick = ::launchCamera) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Fotocamera") }
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
                Text("Da caricare al salvataggio", fontSize = 12.sp, color = Color(0xFF667085))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.pendingPhotos, key = { it.toString() }) { uri -> PhotoTile(uri, { vm.removePendingPhoto(uri) }) }
                }
            }
            item { Button(onClick = vm::saveProduct, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (vm.saving) "Salvataggio…" else "Salva articolo") } }
            if (existing != null) item { OutlinedButton(onClick = { vm.requestDelete(DeleteTarget.ProductTarget(existing)) }, modifier = Modifier.fillMaxWidth()) { Text("Elimina articolo", color = Negative) } }
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
                navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = { TextButton(onClick = vm::saveCustomer, enabled = !vm.saving) { Text("Salva", fontWeight = FontWeight.Bold) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Anagrafica cliente") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(d.firstName, { vm.updateCustomerDraft(d.copy(firstName = it)) }, "Nome", Modifier.weight(1f))
                    AppTextField(d.lastName, { vm.updateCustomerDraft(d.copy(lastName = it)) }, "Cognome", Modifier.weight(1f))
                }
            }
            item { AppTextField(d.phone, { vm.updateCustomerDraft(d.copy(phone = it)) }, "Telefono", keyboardType = KeyboardType.Phone) }
            item { AppTextField(d.email, { vm.updateCustomerDraft(d.copy(email = it)) }, "Email", keyboardType = KeyboardType.Email) }
            item { AppTextField(d.address, { vm.updateCustomerDraft(d.copy(address = it)) }, "Indirizzo") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(d.city, { vm.updateCustomerDraft(d.copy(city = it)) }, "Città", Modifier.weight(1.3f))
                    AppTextField(d.province, { vm.updateCustomerDraft(d.copy(province = it)) }, "Provincia", Modifier.weight(.7f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(d.postalCode, { vm.updateCustomerDraft(d.copy(postalCode = it.filter(Char::isDigit))) }, "CAP", Modifier.weight(.7f), keyboardType = KeyboardType.Number)
                    AppTextField(d.country, { vm.updateCustomerDraft(d.copy(country = it)) }, "Paese", Modifier.weight(1.3f))
                }
            }
            item { AppTextField(d.notes, { vm.updateCustomerDraft(d.copy(notes = it)) }, "Note", minLines = 3) }
            item {
                Button(
                    onClick = vm::saveCustomer,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (vm.saving) "Salvataggio…" else "Salva cliente", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderEditorScreen(vm: AppViewModel) {
    val d = vm.orderDraft
    val selectedProduct = vm.products.firstOrNull { it.id == d.productId }
    val qty = d.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val total = (selectedProduct?.effectiveSalePrice ?: 0.0) * qty
    val profit = (selectedProduct?.effectiveMarginEuro ?: 0.0) * qty

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuovo ordine", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = { TextButton(onClick = vm::saveOrder, enabled = !vm.saving) { Text("Salva", fontWeight = FontWeight.Bold) } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Cliente e articolo") }
            item {
                SelectionField(
                    "Cliente *",
                    d.customerId,
                    vm.customers.map { it.id to it.displayName },
                    { vm.updateOrderDraft(d.copy(customerId = it)) }
                )
            }
            item {
                SelectionField(
                    "Articolo *",
                    d.productId,
                    vm.products.map { it.id to (it.name + if (it.code.isNotBlank()) " · " + it.code else "") },
                    { vm.updateOrderDraft(d.copy(productId = it)) }
                )
            }
            item { AppTextField(d.quantity, { vm.updateOrderDraft(d.copy(quantity = it.filter(Char::isDigit))) }, "Quantità", keyboardType = KeyboardType.Number) }
            item { AppTextField(d.date, { vm.updateOrderDraft(d.copy(date = it)) }, "Data ordine (AAAA-MM-GG)") }
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
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Riepilogo", fontWeight = FontWeight.Black)
                        ValueRow("Totale ordine", money(total))
                        ValueRow("Guadagno previsto", money(profit), if (profit >= 0) Positive else Negative)
                    }
                }
            }
            item { SectionTitle("Spedizione") }
            item { AppTextField(d.courier, { vm.updateOrderDraft(d.copy(courier = it)) }, "Corriere") }
            item { AppTextField(d.trackingCode, { vm.updateOrderDraft(d.copy(trackingCode = it)) }, "Tracking") }
            item { AppTextField(d.notes, { vm.updateOrderDraft(d.copy(notes = it)) }, "Note", minLines = 3) }
            item {
                Button(
                    onClick = vm::saveOrder,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (vm.saving) "Salvataggio…" else "Crea ordine", fontWeight = FontWeight.Black) }
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
            navigationIcon = { IconButton(onClick = { vm.navigateBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
            actions = { TextButton(onClick = vm::saveEntity, enabled = !vm.saving) { Text("Salva", fontWeight = FontWeight.Bold) } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AppTextField(d.name, { vm.updateEntityDraft(d.copy(name = it)) }, "Nome *") }
            when (kind) {
                EntityKind.BRAND -> item { AppTextField(d.notes, { vm.updateEntityDraft(d.copy(notes = it)) }, "Note", minLines = 3) }
                EntityKind.CATEGORY -> {
                    item { AppTextField(d.description, { vm.updateEntityDraft(d.copy(description = it)) }, "Descrizione", minLines = 2) }
                    item { AppTextField(d.sortOrder, { vm.updateEntityDraft(d.copy(sortOrder = it.filter { c -> c.isDigit() || c == '-' })) }, "Ordine", keyboardType = KeyboardType.Number) }
                }
                EntityKind.SUPPLIER -> {
                    item { AppTextField(d.contact, { vm.updateEntityDraft(d.copy(contact = it)) }, "Referente") }
                    item { AppTextField(d.phone, { vm.updateEntityDraft(d.copy(phone = it)) }, "Telefono", keyboardType = KeyboardType.Phone) }
                    item { AppTextField(d.email, { vm.updateEntityDraft(d.copy(email = it)) }, "Email", keyboardType = KeyboardType.Email) }
                    item { AppTextField(d.website, { vm.updateEntityDraft(d.copy(website = it)) }, "Sito web", keyboardType = KeyboardType.Uri) }
                    item { AppTextField(d.catalogUrl, { vm.updateEntityDraft(d.copy(catalogUrl = it)) }, "Link catalogo", keyboardType = KeyboardType.Uri) }
                    item { AppTextField(d.address, { vm.updateEntityDraft(d.copy(address = it)) }, "Indirizzo") }
                    item { AppTextField(d.notes, { vm.updateEntityDraft(d.copy(notes = it)) }, "Note", minLines = 3) }
                }
            }
            item { Button(onClick = vm::saveEntity, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (vm.saving) "Salvataggio…" else "Salva") } }
            if (d.id.isNotBlank()) item {
                OutlinedButton(onClick = { vm.requestDelete(DeleteTarget.EntityTarget(kind, d.id, d.name)) }, modifier = Modifier.fillMaxWidth()) { Text("Elimina", color = Negative) }
            }
        }
    }
}

@Composable
private fun SelectionField(label: String, selected: String?, options: List<Pair<String, String>>, onSelect: (String?) -> Unit, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = if (compact) Modifier.widthIn(min = 150.dp) else Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: if (compact) label else "$label: nessuna", maxLines = 1)
            Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Nessuna") }, onClick = { onSelect(null); open = false })
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

private fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale.ITALY).format(value)

private fun tabTitle(tab: MainTab): String = when (tab) {
    MainTab.HOME -> "Dashboard"
    MainTab.ARTICLES -> "Articoli"
    MainTab.CLIENTS -> "Clienti"
    MainTab.ORDERS -> "Ordini"
    MainTab.ARCHIVES -> "Anagrafiche"
    MainTab.SETTINGS -> "Impostazioni"
}

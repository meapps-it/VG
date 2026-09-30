package it.meapps.spesascan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AppNavy = Color(0xFF0B2B52)
private val AppBlue = Color(0xFF1677FF)
private val AppAmber = Color(0xFFFF8A1F)
private val AppBackground = Color(0xFFF5F8FC)
private val Positive = Color(0xFF0AA66E)
private val Negative = Color(0xFFB42318)
private val LegacyBorder = Color(0xFFC9D0D9)

data class SavedProduct(
    val code: String,
    val name: String,
    val brand: String,
    val quantity: String,
    val description: String,
    val salePrice: Double,
    val savedAt: String,
    val imagePath: String = "",
    val remoteImageUrl: String = ""
)

class MainActivity : ComponentActivity() {
    private val repository by lazy { ProductRepository(this) }
    private val authRepository by lazy { AuthRepository(this) }
    private var authSession by mutableStateOf<UserSession?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authSession = authRepository.session
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = AppBlue,
                    secondary = AppAmber,
                    background = AppBackground,
                    surface = Color.White,
                    surfaceVariant = Color(0xFFF1F5F9),
                    onPrimary = Color.White,
                    error = Negative
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = AppBackground) {
                    val session = authSession
                    if (session == null) {
                        LoginScreen(
                            authRepository = authRepository,
                            onAuthenticated = {
                                authSession = it
                                repository.setOwner(it.userId)
                            }
                        )
                    } else {
                        LaunchedEffect(session.userId) { repository.setOwner(session.userId) }
                        SpesaScanApp(
                            repository = repository,
                            session = session,
                            onLogout = {
                                lifecycleScope.launch {
                                    authRepository.signOut()
                                    authSession = null
                                }
                            }
                        )
                    }
                }
            }
        }
        handleAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "spesascan" && data.host == "login-callback") {
            lifecycleScope.launch {
                runCatching { authRepository.completeGoogleOAuth(data) }
                    .onSuccess {
                        repository.setOwner(it.userId)
                        authSession = it
                    }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    authRepository: AuthRepository,
    onAuthenticated: (UserSession) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            Modifier.fillMaxWidth().widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, LegacyBorder)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.QrCodeScanner, null, tint = AppBlue, modifier = Modifier.size(48.dp))
                Text(if (register) "Crea account" else "SpesaScan", fontSize = 28.sp, fontWeight = FontWeight.Black, color = AppNavy)
                Text("Scansiona, salva e confronta i tuoi prodotti.", color = Color(0xFF667085))

                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authRepository.googleAuthUrl())))
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF202124)),
                    border = BorderStroke(1.dp, Color(0xFFDADCE0))
                ) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("Continua con Google", fontWeight = FontWeight.Bold)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDADCE0))
                    Text("  oppure  ", color = Color(0xFF64748B), fontSize = 12.sp)
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDADCE0))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                )

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            error = "Inserisci email e password"
                        } else {
                            scope.launch {
                                loading = true
                                error = null
                                notice = null
                                runCatching {
                                    if (register) {
                                        val created = authRepository.signUp(email, password)
                                        authRepository.session ?: if (created) null else null
                                    } else authRepository.signIn(email, password)
                                }.onSuccess { session ->
                                    if (session != null) onAuthenticated(session)
                                    else notice = "Account creato. Controlla l’email per confermarlo."
                                }.onFailure { error = it.message ?: "Accesso non riuscito" }
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(if (register) "Registrati" else "Accedi", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { register = !register }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (register) "Hai già un account? Accedi" else "Crea un nuovo account")
                }

                error?.let { Text(it, color = Negative) }
                notice?.let { Text(it, color = Positive) }
            }
        }
    }
}

@Composable
private fun SpesaScanApp(
    repository: ProductRepository,
    session: UserSession,
    onLogout: () -> Unit
) {
    var products by remember(session.userId) { mutableStateOf(repository.loadAll()) }
    var editing by remember { mutableStateOf<SavedProduct?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    if (creating || editing != null) {
        ProductEditorScreen(
            repository = repository,
            existing = editing,
            onBack = {
                creating = false
                editing = null
            },
            onSaved = {
                products = repository.loadAll()
                creating = false
                editing = null
            }
        )
    } else {
        ProductsScreen(
            products = products,
            session = session,
            onNew = { creating = true },
            onOpen = { editing = it },
            onLogout = onLogout
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductsScreen(
    products: List<SavedProduct>,
    session: UserSession,
    onNew: () -> Unit,
    onOpen: (SavedProduct) -> Unit,
    onLogout: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedBrand by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val brands = remember(products) {
        products.map { it.brand.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(products, query, selectedBrand) {
        val q = query.trim().lowercase(Locale.ITALY)
        products.filter {
            (q.isBlank() || listOf(it.name, it.brand, it.code, it.description).any { value ->
                value.lowercase(Locale.ITALY).contains(q)
            }) && (selectedBrand == null || it.brand == selectedBrand)
        }
    }
    val nowText = remember(now) {
        DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM, Locale.ITALY).format(Date(now))
    }

    Scaffold(
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
                        Text("SpesaScan", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
                        ) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Articoli") },
                                leadingIcon = { Icon(Icons.Default.Inventory2, null) },
                                onClick = { menuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Scansiona articolo") },
                                leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                                onClick = { menuOpen = false; onNew() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Account")
                                        Text(session.email, fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                                onClick = { menuOpen = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Esci") },
                                leadingIcon = { Icon(Icons.Default.Logout, null, tint = Negative) },
                                onClick = {
                                    menuOpen = false
                                    onLogout()
                                }
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
                    BottomPill("Articoli", Icons.Default.Inventory2, true, AppBlue, Modifier.weight(1f)) {}
                    BottomPill("Scansiona", Icons.Default.QrCodeScanner, false, AppAmber, Modifier.weight(1f), onNew)
                    BottomPill("Account", Icons.Default.Person, false, Positive, Modifier.weight(1f)) { menuOpen = true }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.border(1.5.dp, LegacyBorder, RoundedCornerShape(50)),
                onClick = onNew,
                containerColor = AppAmber,
                contentColor = AppNavy
            ) {
                Icon(Icons.Default.Add, "Aggiungi")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                singleLine = true,
                placeholder = { Text("Cerca nome, codice o marca", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(22.dp)) },
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedBrand == null,
                    onClick = { selectedBrand = null },
                    label = { Text("Tutte le marche", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Category, null, Modifier.size(18.dp)) }
                )
                brands.forEach { brand ->
                    FilterChip(
                        selected = selectedBrand == brand,
                        onClick = { selectedBrand = if (selectedBrand == brand) null else brand },
                        label = { Text(brand, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("Tutto", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${filtered.size} articoli",
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.GridView, null)
            }

            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.5.dp, LegacyBorder)
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Inventory2, null, tint = AppBlue, modifier = Modifier.size(46.dp))
                            Text("Nessun articolo", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text("Scansiona un codice a barre per creare il primo articolo.", color = Color(0xFF64748B))
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(filtered, key = { it.code }) { product ->
                        ProductCard(product, onClick = { onOpen(product) })
                    }
                }
            }
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
        border = if (selected) BorderStroke(1.2.dp, accent.copy(alpha = .40f)) else null
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
private fun ProductCard(product: SavedProduct, onClick: () -> Unit) {
    val imageModel: Any? = product.imagePath.takeIf { it.isNotBlank() }?.let(::File)
        ?: product.remoteImageUrl.takeIf { it.isNotBlank() }

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0EDF3)),
        border = BorderStroke(1.5.dp, LegacyBorder)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .background(Color(0xFFF1F5FB)),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF182237)
                    ) {
                        Text("1 foto", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(46.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Nessuna foto", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF182237)
                    ) {
                        Text("0 foto", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }

            Column(
                Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    product.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = AppNavy,
                    maxLines = 2
                )
                Text(product.code, color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (product.brand.isNotBlank()) {
                    Text(product.brand, color = Color(0xFF64748B), fontSize = 11.sp)
                }
                if (product.quantity.isNotBlank()) {
                    Text(product.quantity, color = Color(0xFF64748B), fontSize = 11.sp)
                }
                Text(
                    money(product.salePrice),
                    fontWeight = FontWeight.Black,
                    fontSize = 21.sp,
                    color = AppNavy
                )
                Text(
                    displayDate(product.savedAt),
                    color = Positive,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, LegacyBorder)
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Apri", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorScreen(
    repository: ProductRepository,
    existing: SavedProduct?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var code by rememberSaveable(existing?.code) { mutableStateOf(existing?.code.orEmpty()) }
    var name by rememberSaveable(existing?.code) { mutableStateOf(existing?.name.orEmpty()) }
    var brand by rememberSaveable(existing?.code) { mutableStateOf(existing?.brand.orEmpty()) }
    var quantity by rememberSaveable(existing?.code) { mutableStateOf(existing?.quantity.orEmpty()) }
    var description by rememberSaveable(existing?.code) { mutableStateOf(existing?.description.orEmpty()) }
    var price by rememberSaveable(existing?.code) {
        mutableStateOf(existing?.salePrice?.takeIf { it > 0 }?.let { "%.2f".format(Locale.ITALY, it) }.orEmpty())
    }
    var remoteImageUrl by rememberSaveable(existing?.code) { mutableStateOf(existing?.remoteImageUrl.orEmpty()) }
    var localImagePath by rememberSaveable(existing?.code) { mutableStateOf(existing?.imagePath.orEmpty()) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { scanned ->
            code = scanned
            val local = repository.find(scanned)
            if (local != null) {
                name = local.name
                brand = local.brand
                quantity = local.quantity
                description = local.description
                price = if (local.salePrice > 0) "%.2f".format(Locale.ITALY, local.salePrice) else ""
                remoteImageUrl = local.remoteImageUrl
                localImagePath = local.imagePath
                status = "Articolo già presente nel tuo archivio"
            } else {
                scope.launch {
                    loading = true
                    status = "Ricerca su Open Food Facts…"
                    val found = repository.lookupOpenFoodFacts(scanned)
                    if (found != null) {
                        name = found.name
                        brand = found.brand
                        quantity = found.quantity
                        description = found.description
                        remoteImageUrl = found.remoteImageUrl
                        status = "Prodotto trovato su Open Food Facts"
                    } else {
                        status = "Prodotto non trovato. Compila i dati manualmente."
                    }
                    loading = false
                }
            }
        }
    }

    fun startScan() {
        scanner.launch(
            ScanOptions()
                .setPrompt("Inquadra il codice a barre")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) "Nuovo articolo" else "Modifica articolo",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionTitle("Codice e ricerca")
            }

            item {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Codice EAN / UPC") },
                    trailingIcon = {
                        IconButton(onClick = ::startScan) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner")
                        }
                    }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = ::startScan,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Scansiona")
                    }

                    OutlinedButton(
                        onClick = {
                            if (code.isNotBlank()) {
                                scope.launch {
                                    loading = true
                                    status = "Ricerca su Open Food Facts…"
                                    val found = repository.lookupOpenFoodFacts(code)
                                    if (found != null) {
                                        name = found.name
                                        brand = found.brand
                                        quantity = found.quantity
                                        description = found.description
                                        remoteImageUrl = found.remoteImageUrl
                                        status = "Prodotto trovato su Open Food Facts"
                                    } else {
                                        status = "Prodotto non trovato"
                                    }
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading && code.isNotBlank(),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cerca")
                    }
                }
            }

            if (loading) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (status.isNotBlank()) item {
                Text(
                    status,
                    color = if (status.contains("trovato", ignoreCase = true) || status.contains("presente", ignoreCase = true)) Positive else Color(0xFF64748B),
                    fontWeight = FontWeight.SemiBold
                )
            }

            val imageModel: Any? = localImagePath.takeIf { it.isNotBlank() }?.let(::File)
                ?: remoteImageUrl.takeIf { it.isNotBlank() }

            if (imageModel != null) item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.5.dp, LegacyBorder)
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Foto prodotto",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                }
            }

            item { SectionTitle("Dati articolo") }

            item {
                AppField(name, { name = it }, "Nome prodotto *")
            }

            item {
                AppField(brand, { brand = it }, "Marca")
            }

            item {
                AppField(quantity, { quantity = it }, "Formato / quantità")
            }

            item {
                AppField(description, { description = it }, "Descrizione", minLines = 3)
            }

            item { SectionTitle("Prezzo") }

            item {
                OutlinedTextField(
                    value = price,
                    onValueChange = { value ->
                        price = value.filter { it.isDigit() || it == ',' || it == '.' }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Prezzo di vendita € *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Obbligatorio prima del salvataggio") }
                )
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF))
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = AppBlue)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Data inserimento", fontWeight = FontWeight.Bold)
                            Text(
                                if (existing != null) displayDate(existing.savedAt) else "Verrà salvata automaticamente adesso",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val parsedPrice = price.replace(',', '.').toDoubleOrNull()
                        when {
                            code.isBlank() -> status = "Inserisci o scansiona il codice"
                            name.isBlank() -> status = "Inserisci il nome del prodotto"
                            parsedPrice == null || parsedPrice <= 0 -> status = "Inserisci un prezzo valido"
                            else -> {
                                scope.launch {
                                    saving = true
                                    val imagePath = repository.persistImage(
                                        code = code,
                                        currentLocalPath = localImagePath,
                                        remoteUrl = remoteImageUrl
                                    )
                                    repository.save(
                                        SavedProduct(
                                            code = code,
                                            name = name.trim(),
                                            brand = brand.trim(),
                                            quantity = quantity.trim(),
                                            description = description.trim(),
                                            salePrice = parsedPrice,
                                            savedAt = existing?.savedAt ?: LocalDateTime.now().toString(),
                                            imagePath = imagePath,
                                            remoteImageUrl = remoteImageUrl
                                        )
                                    )
                                    saving = false
                                    onSaved()
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppNavy),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Salva articolo", fontWeight = FontWeight.Black)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 19.sp, fontWeight = FontWeight.Black, color = AppNavy)
}

@Composable
private fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        singleLine = minLines == 1,
        shape = RoundedCornerShape(16.dp)
    )
}

class ProductRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("spesascan", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private var ownerId: String = "guest"

    fun setOwner(userId: String) {
        ownerId = userId.ifBlank { "guest" }
        val userKey = "archive_v2_" + ownerId
        if (!prefs.contains(userKey) && prefs.contains("archive_v2")) {
            prefs.edit().putString(userKey, prefs.getString("archive_v2", "[]")).apply()
        }
    }

    private fun archiveKey(): String = "archive_v2_" + ownerId

    fun loadAll(): List<SavedProduct> {
        val raw = prefs.getString(archiveKey(), "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.toSavedProduct() }
            .sortedByDescending { it.savedAt }
    }

    fun find(code: String): SavedProduct? = loadAll().firstOrNull { it.code == code }

    fun save(product: SavedProduct) {
        val current = loadAll().filterNot { it.code == product.code }.toMutableList()
        current.add(product)
        val array = JSONArray()
        current.forEach { array.put(it.toJson()) }
        prefs.edit().putString(archiveKey(), array.toString()).apply()
    }

    suspend fun lookupOpenFoodFacts(code: String): SavedProduct? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://world.openfoodfacts.org/api/v2/product/${code}.json?fields=code,product_name,product_name_it,brands,quantity,generic_name,generic_name_it,categories,image_front_url"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SpesaScan/0.2.0 (Android)")
                .get()
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string().orEmpty()
            }

            val json = JSONObject(body)
            if (json.optInt("status") != 1) return@withContext null
            val p = json.optJSONObject("product") ?: return@withContext null

            SavedProduct(
                code = code,
                name = p.optString("product_name_it").ifBlank { p.optString("product_name") },
                brand = p.optString("brands"),
                quantity = p.optString("quantity"),
                description = p.optString("generic_name_it").ifBlank {
                    p.optString("generic_name").ifBlank { p.optString("categories") }
                },
                salePrice = 0.0,
                savedAt = "",
                imagePath = "",
                remoteImageUrl = p.optString("image_front_url")
            )
        }.getOrNull()
    }

    suspend fun persistImage(
        code: String,
        currentLocalPath: String,
        remoteUrl: String
    ): String = withContext(Dispatchers.IO) {
        if (currentLocalPath.isNotBlank() && File(currentLocalPath).exists()) {
            return@withContext currentLocalPath
        }
        if (remoteUrl.isBlank()) return@withContext ""

        runCatching {
            val request = Request.Builder()
                .url(remoteUrl)
                .header("User-Agent", "SpesaScan/0.2.0 (Android)")
                .get()
                .build()

            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()
            } ?: return@runCatching ""

            val dir = File(context.filesDir, "product_images").apply { mkdirs() }
            val safeCode = code.filter { it.isLetterOrDigit() }.ifBlank { System.currentTimeMillis().toString() }
            val file = File(dir, "$safeCode.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrDefault("")
    }
}

private fun SavedProduct.toJson(): JSONObject = JSONObject().apply {
    put("code", code)
    put("name", name)
    put("brand", brand)
    put("quantity", quantity)
    put("description", description)
    put("salePrice", salePrice)
    put("savedAt", savedAt)
    put("imagePath", imagePath)
    put("remoteImageUrl", remoteImageUrl)
}

private fun JSONObject.toSavedProduct(): SavedProduct = SavedProduct(
    code = optString("code"),
    name = optString("name"),
    brand = optString("brand"),
    quantity = optString("quantity"),
    description = optString("description"),
    salePrice = optDouble("salePrice", 0.0),
    savedAt = optString("savedAt"),
    imagePath = optString("imagePath"),
    remoteImageUrl = optString("remoteImageUrl")
)

private fun money(value: Double): String = "€ " + String.format(Locale.ITALY, "%.2f", value)

private fun displayDate(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val value = LocalDateTime.parse(raw)
        value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALY))
    }.getOrDefault(raw)
}

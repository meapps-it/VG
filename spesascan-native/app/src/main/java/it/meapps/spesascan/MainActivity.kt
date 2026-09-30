package it.meapps.spesascan

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val AppNavy = Color(0xFF123B69)
private val AppBlue = Color(0xFF2A7DE1)
private val AppGreen = Color(0xFF08A96B)
private val AppGreenDark = Color(0xFF068E5C)
private val AppMint = Color(0xFFEAFBF4)
private val AppSky = Color(0xFFEAF5FF)
private val AppAmber = Color(0xFFFFB020)
private val AppBackground = Color(0xFFF5FAFE)
private val Positive = Color(0xFF079A63)
private val Negative = Color(0xFFE5484D)
private val LegacyBorder = Color(0xFFD7E3EF)
private val CardSoft = Color(0xFFFFFFFF)

private val SupportedSupermarkets = listOf(
    "Mercatò", "Conad", "Lidl", "Carrefour", "Esselunga", "Coop", "Eurospin"
)

private enum class MainTab { DASHBOARD, ARTICLES, SCANNER, STATS, ACCOUNT }

data class SavedProduct(
    val code: String,
    val name: String,
    val brand: String,
    val quantity: String,
    val description: String,
    val salePrice: Double,
    val savedAt: String,
    val imagePath: String = "",
    val remoteImageUrl: String = "",
    val updatedAt: String = "",
    val category: String = "",
    val supermarket: String = "",
    val notes: String = ""
)

class MainActivity : ComponentActivity() {
    private val auth by lazy { AuthController(this) }
    private val repository by lazy { ProductRepository(this, auth) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    SpesaScanRoot(repository, auth)
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
            lifecycleScope.launch { auth.completeGoogleLogin(data) }
        }
    }
}

@Composable
private fun SpesaScanRoot(repository: ProductRepository, auth: AuthController) {
    when (auth.session) {
        null -> LoginScreen(auth)
        else -> AuthenticatedApp(repository, auth)
    }
}

@Composable
private fun LoginScreen(auth: AuthController) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            Modifier.fillMaxWidth().widthIn(max = 460.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardSoft)
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, null, tint = AppBlue, modifier = Modifier.size(46.dp))
                Text(
                    if (register) "Crea account SpesaScan" else "SpesaScan",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF111827)
                )
                Text(
                    "Scansiona, riconosci e salva i prodotti che compri.",
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(auth.googleAuthUrl())))
                    },
                    enabled = !auth.busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF202124)
                    ),
                    border = BorderStroke(1.2.dp, Color(0xFFDADCE0))
                ) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("Continua con Google", fontWeight = FontWeight.Black)
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFD8DEE8))
                    Text("  oppure  ", color = Color(0xFF64748B), fontSize = 12.sp)
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFD8DEE8))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Button(
                    onClick = {
                        scope.launch {
                            if (register) auth.signUp(email, password)
                            else auth.signIn(email, password)
                        }
                    },
                    enabled = !auth.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (auth.busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(if (register) "Crea account" else "Accedi", fontWeight = FontWeight.Black)
                    }
                }

                TextButton(
                    onClick = { register = !register; auth.clearMessage() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (register) "Hai già un account? Accedi" else "Crea un nuovo account")
                }

                if (!register) {
                    TextButton(
                        onClick = { scope.launch { auth.resetPassword(email) } },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Password dimenticata")
                    }
                }

                auth.message?.let {
                    Text(
                        it,
                        color = if (it.contains("effettuato", true) || it.contains("inviata", true)) Positive else Negative,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(repository: ProductRepository, auth: AuthController) {
    var products by remember { mutableStateOf(repository.loadAll()) }
    var editing by remember { mutableStateOf<SavedProduct?>(null) }
    var viewing by remember { mutableStateOf<SavedProduct?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.ARTICLES) }

    LaunchedEffect(auth.session?.userId) {
        if (auth.session != null) products = repository.syncWithCloud()
    }

    viewing?.let { product ->
        if (!creating && editing == null) {
            ProductDetailScreen(
                product = product,
                onBack = { viewing = null },
                onEdit = { editing = product; viewing = null }
            )
            return
        }
    }

    if (creating || editing != null) {
        ProductEditorScreen(
            repository = repository,
            existing = editing,
            onBack = { creating = false; editing = null },
            onSaved = {
                products = repository.loadAll()
                creating = false
                editing = null
                selectedTab = MainTab.ARTICLES
            }
        )
        return
    }

    MainScaffold(
        products = products,
        auth = auth,
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        onNew = { creating = true },
        onOpen = { viewing = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    products: List<SavedProduct>,
    auth: AuthController,
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onNew: () -> Unit,
    onOpen: (SavedProduct) -> Unit
) {
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler {
        if (selectedTab != MainTab.ARTICLES) onSelectTab(MainTab.ARTICLES)
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            if (selectedTab != MainTab.ARTICLES) {
                Surface(color = Color.White, shadowElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text("Spesa", color = AppNavy, fontSize = 29.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif)
                            Text("Scan", color = AppGreen, fontSize = 29.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif)
                        }
                        Box {
                            FilledTonalIconButton(
                                onClick = { menuOpen = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = AppSky,
                                    contentColor = AppNavy
                                )
                            ) { Icon(Icons.Default.Menu, "Menu") }

                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Archivio", fontFamily = FontFamily.SansSerif) },
                                    leadingIcon = { Icon(Icons.Default.Inventory2, null) },
                                    onClick = { menuOpen = false; onSelectTab(MainTab.ARTICLES) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scanner", fontFamily = FontFamily.SansSerif) },
                                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                                    onClick = { menuOpen = false; onSelectTab(MainTab.SCANNER) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Statistiche", fontFamily = FontFamily.SansSerif) },
                                    leadingIcon = { Icon(Icons.Default.BarChart, null) },
                                    onClick = { menuOpen = false; onSelectTab(MainTab.STATS) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Account", fontFamily = FontFamily.SansSerif) },
                                    leadingIcon = { Icon(Icons.Default.Person, null) },
                                    onClick = { menuOpen = false; onSelectTab(MainTab.ACCOUNT) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Logout", fontFamily = FontFamily.SansSerif) },
                                    leadingIcon = { Icon(Icons.Default.Logout, null) },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch { auth.signOut() }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                shadowElevation = 14.dp,
                color = Color.White
            ) {
                Row(
                    Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 6.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomPill("Home", Icons.Default.Home, selectedTab == MainTab.DASHBOARD, Modifier.weight(1f)) {
                        onSelectTab(MainTab.DASHBOARD)
                    }
                    BottomPill("Archivio", Icons.Default.Inventory2, selectedTab == MainTab.ARTICLES, Modifier.weight(1f)) {
                        onSelectTab(MainTab.ARTICLES)
                    }
                    BottomPill("Scanner", Icons.Default.QrCodeScanner, selectedTab == MainTab.SCANNER, Modifier.weight(1f)) {
                        onSelectTab(MainTab.SCANNER)
                    }
                    BottomPill("Statistiche", Icons.Default.BarChart, selectedTab == MainTab.STATS, Modifier.weight(1f)) {
                        onSelectTab(MainTab.STATS)
                    }
                    BottomPill("Account", Icons.Default.Person, selectedTab == MainTab.ACCOUNT, Modifier.weight(1f)) {
                        onSelectTab(MainTab.ACCOUNT)
                    }
                }
            }
        },
        floatingActionButton = { }

    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                MainTab.DASHBOARD -> DashboardScreen(products, onNew)
                MainTab.ARTICLES -> ProductsScreen(products, onNew, onOpen)
                MainTab.SCANNER -> ScannerLanding(products, onNew, onOpen)
                MainTab.STATS -> StatsScreen(products)
                MainTab.ACCOUNT -> AccountScreen(auth)
            }
        }
    }
}

@Composable
private fun BottomPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (label == "Scanner") {
        Box(
            modifier.fillMaxHeight().clickable(onClick = onClick),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier.offset(y = (-15).dp).size(56.dp),
                shape = RoundedCornerShape(50),
                color = AppGreen,
                shadowElevation = 8.dp
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    "Scanner",
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Text(
                "Scanner",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                fontSize = 9.sp,
                color = if (selected) AppGreenDark else Color(0xFF667892),
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )
        }
    } else {
        Column(
            modifier.fillMaxHeight().clickable(onClick = onClick).padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) AppGreen else Color(0xFF667892),
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                color = if (selected) AppGreenDark else Color(0xFF667892),
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
            if (selected) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(34.dp)
                        .height(3.dp)
                        .background(AppGreen, RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(products: List<SavedProduct>, onNew: () -> Unit) {
    val total = products.sumOf { it.salePrice }
    val latest = products.maxByOrNull { productTimestamp(it) }
    val stores = products.map { it.supermarket }.filter { it.isNotBlank() }.distinct().size

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = AppSky)
            ) {
                Row(
                    Modifier.padding(start = 18.dp, top = 16.dp, bottom = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("SpesaScan", color = AppNavy, fontSize = 31.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Scansiona, confronta, risparmia.",
                            color = AppGreenDark,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Tieni memoria dei prezzi e scopri dove conviene comprare.",
                            color = Color(0xFF60748C),
                            fontSize = 12.sp
                        )
                    }
                    GroceryMascot(Modifier.size(width = 138.dp, height = 122.dp), showScanner = false)
                }
            }
        }

        item {
            Button(
                onClick = onNew,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Scansiona un prodotto", fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailMetric("Prodotti", products.size.toString(), Icons.Default.Inventory2, AppMint, Modifier.weight(1f))
                DetailMetric("Supermercati", stores.toString(), Icons.Default.Storefront, AppSky, Modifier.weight(1f))
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LegacyBorder)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Ultimo prodotto", color = AppNavy, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    if (latest == null) {
                        Text("Nessun prodotto salvato", color = Color(0xFF71839A))
                    } else {
                        Text(latest.name, color = AppNavy, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(money(latest.salePrice), color = AppGreenDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            if (latest.supermarket.isNotBlank()) {
                                Spacer(Modifier.width(12.dp))
                                SupermarketBadge(latest.supermarket, compact = true)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppMint)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Savings, null, tint = AppGreen, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Valore ultimi prezzi", color = AppNavy, fontWeight = FontWeight.Black)
                        Text(money(total), color = AppGreenDark, fontSize = 27.sp, fontWeight = FontWeight.Black)
                        Text(
                            "La spesa mensile arriverà con lo storico acquisti.",
                            color = Color(0xFF61758D),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, LegacyBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = AppNavy, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ProductDetailScreen(
    product: SavedProduct,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val imageModel: Any? = product.imagePath.takeIf { it.isNotBlank() }?.let(::File)
        ?: product.remoteImageUrl.takeIf { it.isNotBlank() }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            Surface(color = Color.White, shadowElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = AppNavy)
                    }
                    Text("Dettaglio prodotto", color = AppNavy, fontSize = 23.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Modifica", tint = AppGreen)
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LegacyBorder)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(235.dp).background(AppSky, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageModel != null) {
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = product.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(12.dp)
                                )
                            } else {
                                Icon(Icons.Default.Image, null, tint = Color(0xFFA5B5C7), modifier = Modifier.size(64.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(product.name, color = AppNavy, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        if (product.brand.isNotBlank()) Text(product.brand, color = Color(0xFF6D7F95), fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailChip(Icons.Default.QrCode2, product.code)
                            if (product.quantity.isNotBlank()) DetailChip(Icons.Default.Scale, product.quantity)
                            if (product.category.isNotBlank()) DetailChip(Icons.Default.LocalOffer, product.category)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailMetric("Ultimo prezzo", money(product.salePrice), Icons.Default.Euro, AppMint, Modifier.weight(1f))
                    DetailMetric(
                        "Formato",
                        product.quantity.ifBlank { "Non indicato" },
                        Icons.Default.Scale,
                        AppSky,
                        Modifier.weight(1f)
                    )
                }
            }

            if (product.supermarket.isNotBlank()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LegacyBorder)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ultimo supermercato", color = AppNavy, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            SupermarketBadge(product.supermarket, compact = false)
                        }
                    }
                }
            }

            if (product.notes.isNotBlank()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Note", color = AppNavy, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(5.dp))
                            Text(product.notes, color = Color(0xFF60748C))
                        }
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AppMint)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = AppGreen)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Ultimo aggiornamento", color = AppNavy, fontWeight = FontWeight.Black)
                            Text(displayDate(product.updatedAt.ifBlank { product.savedAt }), color = Color(0xFF60748C))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Modifica prodotto", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun DetailChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = AppSky) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AppBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = AppNavy, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DetailMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    modifier: Modifier
) {
    Card(
        modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = AppGreen, modifier = Modifier.size(25.dp))
            Text(label, color = Color(0xFF62768E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value, color = AppNavy, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2)
        }
    }
}

@Composable
private fun StatsScreen(products: List<SavedProduct>) {
    val total = products.sumOf { it.salePrice }
    val average = if (products.isEmpty()) 0.0 else total / products.size
    val storeCounts = products
        .filter { it.supermarket.isNotBlank() }
        .groupingBy { it.supermarket }
        .eachCount()
        .entries
        .sortedByDescending { it.value }

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = AppSky)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Le tue statistiche", color = AppNavy, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("Numeri reali basati sui prodotti che hai salvato.", color = Color(0xFF61758D))
                    }
                    GroceryMascot(Modifier.size(width = 120.dp, height = 105.dp), showScanner = false)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailMetric("Prodotti", products.size.toString(), Icons.Default.Inventory2, AppMint, Modifier.weight(1f))
                DetailMetric("Prezzo medio", money(average), Icons.Default.Calculate, AppSky, Modifier.weight(1f))
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, LegacyBorder)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Somma degli ultimi prezzi", color = AppNavy, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(money(total), color = AppGreenDark, fontSize = 31.sp, fontWeight = FontWeight.Black)
                    Text(
                        "È la somma dell’ultimo prezzo salvato per ogni prodotto, non ancora la spesa mensile.",
                        color = Color(0xFF6D7F95),
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Text("Supermercati registrati", color = AppNavy, fontSize = 19.sp, fontWeight = FontWeight.Black)
        }

        if (storeCounts.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                    Text(
                        "Aggiungi il supermercato ai prodotti per vedere qui i confronti.",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF6D7F95)
                    )
                }
            }
        } else {
            items(storeCounts.size) { index ->
                val entry = storeCounts[index]
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = AppMint) {
                            Icon(Icons.Default.Storefront, null, tint = AppGreen, modifier = Modifier.padding(10.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            SupermarketBadge(entry.key, compact = false)
                        }
                        Text(entry.value.toString() + " prodotti", color = AppGreenDark, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerLanding(
    products: List<SavedProduct>,
    onNew: () -> Unit,
    onOpen: (SavedProduct) -> Unit
) {
    val latest = products.maxByOrNull { productTimestamp(it) }

    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = AppMint)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Scansiona, confronta, risparmia", color = AppNavy, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Inquadra il codice a barre. Recuperiamo i dati del prodotto e tu aggiungi il prezzo.",
                        color = Color(0xFF5F738B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        GroceryMascot(
                            modifier = Modifier.size(width = 158.dp, height = 118.dp),
                            showScanner = true
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().height(220.dp)
                            .background(Brush.linearGradient(listOf(Color.White, AppSky)), RoundedCornerShape(26.dp))
                            .border(2.dp, AppGreen, RoundedCornerShape(26.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = AppGreen, modifier = Modifier.size(88.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Punta il codice a barre", color = AppNavy, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text("La fotocamera si apre al tocco", color = Color(0xFF7A8CA4), fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onNew,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scansiona il codice a barre", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScannerQuickAction("Flash", Icons.Default.FlashOn, Modifier.weight(1f))
                ScannerQuickAction("Manuale", Icons.Default.Keyboard, Modifier.weight(1f), onClick = onNew)
                ScannerQuickAction("Cronologia", Icons.Default.History, Modifier.weight(1f))
            }
        }

        latest?.let { product ->
            item {
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(product) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LegacyBorder)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val imageModel: Any? = product.imagePath.takeIf { it.isNotBlank() }?.let(::File)
                            ?: product.remoteImageUrl.takeIf { it.isNotBlank() }
                        Surface(shape = RoundedCornerShape(18.dp), color = AppSky) {
                            if (imageModel != null) {
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = product.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(92.dp).padding(6.dp)
                                )
                            } else {
                                Icon(Icons.Default.Inventory2, null, tint = AppBlue, modifier = Modifier.padding(25.dp).size(42.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ultima scansione", color = AppBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(product.name, color = AppNavy, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2)
                            Text(money(product.salePrice), color = AppNavy, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            if (product.supermarket.isNotBlank()) {
                                Text(product.supermarket, color = AppGreenDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = AppGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, LegacyBorder)
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = AppNavy, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = AppNavy, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AccountScreen(auth: AuthController) {
    val scope = rememberCoroutineScope()
    val session = auth.session

    Column(
        Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Account", fontSize = 26.sp, fontWeight = FontWeight.Black, color = AppNavy)

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, LegacyBorder)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AccountCircle, null, tint = AppBlue, modifier = Modifier.size(48.dp))
                Text(session?.email.orEmpty(), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Account collegato a Supabase", color = Color(0xFF64748B))
            }
        }

        OutlinedButton(
            onClick = { scope.launch { auth.signOut() } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.5.dp, Negative),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Negative)
        ) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Logout", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ProductsScreen(
    products: List<SavedProduct>,
    onNew: () -> Unit,
    onOpen: (SavedProduct) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedStore by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var storeMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    val categories = remember(products) {
        products.map { it.category.trim() }.filter { it.isNotBlank() }.distinct().sorted().take(20)
    }

    val filtered = remember(products, query, selectedStore, selectedCategory) {
        val q = query.trim().lowercase(Locale.ITALY)
        products.filter { product ->
            val textHit = q.isBlank() || listOf(
                product.name, product.brand, product.code, product.category,
                product.supermarket, product.description
            ).any { it.lowercase(Locale.ITALY).contains(q) }
            val storeHit = selectedStore.isBlank() || product.supermarket == selectedStore
            val categoryHit = selectedCategory.isBlank() || product.category == selectedCategory
            textHit && storeHit && categoryHit
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F9FD))
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Hero costruito con le stesse proporzioni del mockup.
        Box(
            Modifier
                .fillMaxWidth()
                .height(218.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFEAF8FF), Color(0xFFF4FCFF))
                    ),
                    RoundedCornerShape(0.dp)
                )
        ) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 8.dp)
                    .width(225.dp)
            ) {
                Row {
                    Text(
                        "Archivio ",
                        color = AppNavy,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        "prodotti",
                        color = AppGreen,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                }
                Text(
                    "Cerca e confronta i prodotti salvati.",
                    color = AppNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.93f)
                    .height(122.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFE7FAF2)
            ) {
                Column(
                    Modifier.padding(start = 16.dp, top = 14.dp, end = 150.dp)
                ) {
                    Text(
                        "I tuoi prodotti\nsempre con te!",
                        color = AppNavy,
                        fontSize = 20.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Consulta i prezzi, confrontali\ne scopri come risparmiare.",
                        color = AppNavy,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }

            GroceryMascot(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(width = 184.dp, height = 178.dp),
                showScanner = false
            )
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, Color(0xFFE0E9F2))
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = AppNavy, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = AppNavy,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "Cerca un prodotto nell’archivio…",
                                color = Color(0xFF8A9AAF),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        inner()
                    }
                )
                Icon(Icons.Default.FilterList, null, tint = AppNavy, modifier = Modifier.size(23.dp))
            }
        }

        Spacer(Modifier.height(9.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ArchiveFilterButton(
                text = "Tutti",
                icon = Icons.Default.GridView,
                selected = selectedStore.isBlank() && selectedCategory.isBlank(),
                modifier = Modifier.weight(0.80f)
            ) {
                selectedStore = ""
                selectedCategory = ""
            }

            Box(Modifier.weight(1.25f)) {
                ArchiveFilterButton(
                    text = if (selectedStore.isBlank()) "Supermercati" else selectedStore,
                    icon = Icons.Default.ShoppingCart,
                    selected = selectedStore.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { storeMenu = true }

                DropdownMenu(expanded = storeMenu, onDismissRequest = { storeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Tutti i supermercati", fontFamily = FontFamily.SansSerif) },
                        onClick = { selectedStore = ""; storeMenu = false }
                    )
                    SupportedSupermarkets.forEach { store ->
                        DropdownMenuItem(
                            text = { Text(store, fontFamily = FontFamily.SansSerif) },
                            onClick = { selectedStore = store; storeMenu = false }
                        )
                    }
                }
            }

            Box(Modifier.weight(1.06f)) {
                ArchiveFilterButton(
                    text = if (selectedCategory.isBlank()) "Categorie" else selectedCategory,
                    icon = Icons.Default.LocalOffer,
                    selected = selectedCategory.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { categoryMenu = true }

                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Tutte le categorie", fontFamily = FontFamily.SansSerif) },
                        onClick = { selectedCategory = ""; categoryMenu = false }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category, fontFamily = FontFamily.SansSerif) },
                            onClick = { selectedCategory = category; categoryMenu = false }
                        )
                    }
                }
            }

            ArchiveFilterButton(
                text = "Aumentati",
                icon = Icons.Default.TrendingUp,
                selected = false,
                modifier = Modifier.weight(0.95f),
                accent = Negative
            ) { }
        }

        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, null, tint = Color(0xFFA5B4C7), modifier = Modifier.size(50.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nessun prodotto",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = AppNavy,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        "Usa lo scanner per aggiungere il primo.",
                        color = Color(0xFF6B7C93),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.code }) { product ->
                    ProductGridCard(product, onClick = { onOpen(product) })
                }
            }
        }
    }
}

@Composable
private fun ArchiveFilterButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    accent: Color = AppBlue,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(43.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AppGreen else Color.White,
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE1E8F0)),
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) Color.White else accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                color = if (selected) Color.White else AppNavy,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProductGridCard(product: SavedProduct, onClick: () -> Unit) {
    val imageModel: Any? = product.imagePath.takeIf { it.isNotBlank() }?.let(::File)
        ?: product.remoteImageUrl.takeIf { it.isNotBlank() }

    Card(
        Modifier
            .fillMaxWidth()
            .height(202.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE7EDF3)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(10.dp)
        ) {
            Box(
                Modifier
                    .width(86.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(112.dp)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFF2F7FB)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            null,
                            tint = Color(0xFFB2C1D2),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(7.dp))

            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Top
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            product.name,
                            color = AppNavy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 2,
                            lineHeight = 17.sp
                        )
                        if (product.quantity.isNotBlank()) {
                            Text(
                                product.quantity,
                                color = Color(0xFF7C8CA1),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        Icons.Default.MoreVert,
                        null,
                        tint = AppNavy,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    money(product.salePrice),
                    color = AppNavy,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(Modifier.weight(1f))

                if (product.supermarket.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SupermarketMark(product.supermarket, Modifier.size(22.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            product.supermarket,
                            color = AppNavy,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        null,
                        tint = Color(0xFF7D8DA3),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Ultimo acquisto: " + displayDate(product.savedAt).substringBefore(" "),
                        color = Color(0xFF7D8DA3),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GroceryMascot(
    modifier: Modifier = Modifier,
    showScanner: Boolean
) {
    EmbeddedBase64Image(
        base64 = EmbeddedMascot.WEBP_BASE64,
        modifier = modifier,
        contentDescription = "Mascotte SpesaScan",
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun SupermarketBadge(
    name: String,
    compact: Boolean
) {
    val iconSize = if (compact) 24.dp else 34.dp
    val fontSize = if (compact) 10.sp else 13.sp

    Surface(
        shape = RoundedCornerShape(if (compact) 12.dp else 16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, LegacyBorder)
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 7.dp else 10.dp,
                vertical = if (compact) 4.dp else 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SupermarketMark(name, Modifier.size(iconSize))
            Spacer(Modifier.width(if (compact) 5.dp else 7.dp))
            Text(
                name,
                color = AppNavy,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SupermarketMark(
    name: String,
    modifier: Modifier = Modifier
) {
    val asset = EmbeddedSupermarketLogos.forName(name)
    if (asset != null) {
        EmbeddedBase64Image(
            base64 = asset,
            modifier = modifier,
            contentDescription = "Logo " + name,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier.background(AppMint, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Storefront,
                null,
                tint = AppGreen,
                modifier = Modifier.fillMaxSize(0.65f)
            )
        }
    }
}

@Composable
private fun EmbeddedBase64Image(
    base64: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale
) {
    val image = remember(base64) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier.background(AppMint, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, null, tint = AppGreen)
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
    var category by rememberSaveable(existing?.code) { mutableStateOf(existing?.category.orEmpty()) }
    var supermarket by rememberSaveable(existing?.code) { mutableStateOf(existing?.supermarket.orEmpty()) }
    var notes by rememberSaveable(existing?.code) { mutableStateOf(existing?.notes.orEmpty()) }
    var supermarketMenu by remember { mutableStateOf(false) }
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
                category = local.category
                supermarket = local.supermarket
                notes = local.notes
                status = "Prodotto già presente nel tuo archivio"
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
                        category = found.category
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

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = repository.persistManualImage(
                    code = code,
                    sourceUri = uri
                )
                if (saved.isNotBlank()) {
                    localImagePath = saved
                    remoteImageUrl = ""
                    status = "Foto aggiunta dalla galleria"
                } else {
                    status = "Impossibile salvare la foto"
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val saved = repository.persistManualBitmap(
                    code = code,
                    bitmap = bitmap
                )
                if (saved.isNotBlank()) {
                    localImagePath = saved
                    remoteImageUrl = ""
                    status = "Foto scattata e salvata"
                } else {
                    status = "Impossibile salvare la foto"
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

    val imageModel: Any? = localImagePath.takeIf { it.isNotBlank() }?.let(::File)
        ?: remoteImageUrl.takeIf { it.isNotBlank() }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = AppNavy)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (existing == null) "Aggiungi prodotto" else "Modifica prodotto",
                            color = AppNavy,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Foto, dati e prezzo in un solo posto",
                            color = Color(0xFF6B7C93),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = AppMint
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            null,
                            tint = AppGreen,
                            modifier = Modifier.padding(12.dp).size(26.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LegacyBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = AppMint) {
                                Icon(
                                    Icons.Default.Image,
                                    null,
                                    tint = AppGreen,
                                    modifier = Modifier.padding(10.dp).size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Foto del prodotto", color = AppNavy, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                Text(
                                    "Scattala oppure sceglila dalla galleria",
                                    color = Color(0xFF6B7C93),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .background(
                                    Brush.linearGradient(listOf(AppSky, Color.White, AppMint)),
                                    RoundedCornerShape(22.dp)
                                )
                                .border(1.dp, LegacyBorder, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageModel != null) {
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = "Foto prodotto",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        localImagePath = ""
                                        remoteImageUrl = ""
                                        status = "Foto rimossa"
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.92f),
                                        contentColor = Negative
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Rimuovi foto")
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddAPhoto, null, tint = AppGreen, modifier = Modifier.size(54.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Aggiungi una foto", color = AppNavy, fontWeight = FontWeight.Black)
                                    Text("Aiuta a riconoscere subito il prodotto", color = Color(0xFF6B7C93), fontSize = 12.sp)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { cameraLauncher.launch(null) },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                            ) {
                                Icon(Icons.Default.PhotoCamera, null)
                                Spacer(Modifier.width(7.dp))
                                Text("Scatta foto", fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f).height(54.dp),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.4.dp, Color(0xFF9BCBFF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppBlue)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null)
                                Spacer(Modifier.width(7.dp))
                                Text("Galleria", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LegacyBorder)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = AppSky) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    null,
                                    tint = AppBlue,
                                    modifier = Modifier.padding(10.dp).size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Dettagli prodotto", color = AppNavy, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }

                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Codice a barre") },
                            leadingIcon = { Icon(Icons.Default.QrCode2, null) },
                            trailingIcon = {
                                IconButton(onClick = ::startScan) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner", tint = AppGreen)
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppGreen,
                                focusedLabelColor = AppGreen
                            )
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = ::startScan,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Scansiona", fontWeight = FontWeight.Black)
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
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.2.dp, LegacyBorder)
                            ) {
                                Icon(Icons.Default.Search, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Cerca dati", fontWeight = FontWeight.Black)
                            }
                        }

                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppGreen)

                        AppStyledField(name, { name = it }, "Nome prodotto *", Icons.Default.Edit)
                        AppStyledField(brand, { brand = it }, "Marca", Icons.Default.LocalOffer)
                        AppStyledField(quantity, { quantity = it }, "Quantità / formato", Icons.Default.Scale)
                        AppStyledField(category, { category = it }, "Categoria", Icons.Default.Category)
                        AppStyledField(description, { description = it }, "Descrizione", Icons.Default.Subject, minLines = 2)

                        Box {
                            OutlinedTextField(
                                value = supermarket,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Supermercato") },
                                leadingIcon = { Icon(Icons.Default.Storefront, null) },
                                trailingIcon = {
                                    IconButton(onClick = { supermarketMenu = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppGreen,
                                    focusedLabelColor = AppGreen
                                )
                            )
                            DropdownMenu(
                                expanded = supermarketMenu,
                                onDismissRequest = { supermarketMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Nessuno") },
                                    onClick = {
                                        supermarket = ""
                                        supermarketMenu = false
                                    }
                                )
                                SupportedSupermarkets.forEach { store ->
                                    DropdownMenuItem(
                                        text = { Text(store) },
                                        onClick = {
                                            supermarket = store
                                            supermarketMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        AppStyledField(notes, { notes = it }, "Note (opzionale)", Icons.Default.Notes, minLines = 2)

                        OutlinedTextField(
                            value = price,
                            onValueChange = { value ->
                                price = value.filter { it.isDigit() || it == ',' || it == '.' }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Prezzo pagato € *") },
                            leadingIcon = { Icon(Icons.Default.Euro, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppGreen,
                                focusedLabelColor = AppGreen
                            )
                        )
                    }
                }
            }

            if (status.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = if (
                            status.contains("trovato", true) ||
                            status.contains("aggiunta", true) ||
                            status.contains("salvata", true)
                        ) AppMint else AppSky
                    ) {
                        Text(
                            status,
                            modifier = Modifier.padding(14.dp),
                            color = AppNavy,
                            fontWeight = FontWeight.SemiBold
                        )
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
                                            savedAt = existing?.savedAt ?: java.time.Instant.now().toString(),
                                            imagePath = imagePath,
                                            remoteImageUrl = remoteImageUrl,
                                            updatedAt = java.time.Instant.now().toString(),
                                            category = category.trim(),
                                            supermarket = supermarket.trim(),
                                            notes = notes.trim()
                                        )
                                    )
                                    saving = false
                                    onSaved()
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Salva prodotto", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AppStyledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        minLines = minLines,
        singleLine = minLines == 1,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppGreen,
            focusedLabelColor = AppGreen
        )
    )
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

class ProductRepository(
    private val context: Context,
    private val auth: AuthController
) {
    private val prefs = context.getSharedPreferences("spesascan", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun loadAll(): List<SavedProduct> {
        val raw = prefs.getString("archive_v2", "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.toSavedProduct() }
            .sortedByDescending { productTimestamp(it) }
    }

    fun find(code: String): SavedProduct? = loadAll().firstOrNull { it.code == code }

    suspend fun syncWithCloud(): List<SavedProduct> {
        val session = auth.validSession() ?: return loadAll()
        return withContext(Dispatchers.IO) {
            val local = loadAll()
            val remote = runCatching { fetchRemote(session) }.getOrElse { return@withContext local }

            val localByCode = local.associateBy { it.code }.toMutableMap()
            val remoteByCode = remote.associateBy { it.code }
            val allCodes = (localByCode.keys + remoteByCode.keys).distinct()
            val merged = mutableListOf<SavedProduct>()

            for (code in allCodes) {
                val localProduct = localByCode[code]
                val remoteProduct = remoteByCode[code]
                val winner = when {
                    localProduct == null -> remoteProduct!!
                    remoteProduct == null -> {
                        runCatching { upsertRemote(localProduct, session) }
                        localProduct
                    }
                    productTimestamp(localProduct) >= productTimestamp(remoteProduct) -> {
                        runCatching { upsertRemote(localProduct, session) }
                        localProduct
                    }
                    else -> remoteProduct.copy(
                        imagePath = localProduct.imagePath.ifBlank { remoteProduct.imagePath },
                        remoteImageUrl = remoteProduct.remoteImageUrl.ifBlank { localProduct.remoteImageUrl }
                    )
                }
                merged += winner
            }

            saveAllLocal(merged)
            merged.sortedByDescending { productTimestamp(it) }
        }
    }

    suspend fun save(product: SavedProduct) {
        saveLocal(product)
        val session = auth.validSession() ?: return
        withContext(Dispatchers.IO) {
            runCatching { upsertRemote(product, session) }
        }
    }

    private fun saveLocal(product: SavedProduct) {
        val current = loadAll().filterNot { it.code == product.code }.toMutableList()
        current.add(product)
        saveAllLocal(current)
    }

    private fun saveAllLocal(products: List<SavedProduct>) {
        val array = JSONArray()
        products.forEach { array.put(it.toJson()) }
        prefs.edit().putString("archive_v2", array.toString()).apply()
    }

    private fun fetchRemote(session: AuthSession): List<SavedProduct> {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/spesascan_articoli?select=code,name,brand,quantity,description,sale_price,saved_at,image_path,remote_image_url,updated_at,category,supermarket,notes&user_id=eq.${session.userId}&order=updated_at.desc")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
            .get()
            .build()

        val body = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Sincronizzazione non riuscita (${response.code})")
            text
        }

        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { json ->
                SavedProduct(
                    code = json.optString("code"),
                    name = json.optString("name"),
                    brand = json.optString("brand"),
                    quantity = json.optString("quantity"),
                    description = json.optString("description"),
                    salePrice = json.optDouble("sale_price", 0.0),
                    savedAt = json.optString("saved_at"),
                    imagePath = "",
                    remoteImageUrl = json.optString("remote_image_url"),
                    updatedAt = json.optString("updated_at"),
                    category = json.optString("category"),
                    supermarket = json.optString("supermarket"),
                    notes = json.optString("notes")
                )
            }
        }
    }

    private fun upsertRemote(product: SavedProduct, session: AuthSession) {
        val payload = JSONArray().put(
            JSONObject()
                .put("user_id", session.userId)
                .put("code", product.code)
                .put("name", product.name)
                .put("brand", product.brand)
                .put("quantity", product.quantity)
                .put("description", product.description)
                .put("sale_price", product.salePrice)
                .put("saved_at", product.savedAt.ifBlank { java.time.Instant.now().toString() })
                .put("image_path", "")
                .put("remote_image_url", product.remoteImageUrl)
                .put("updated_at", product.updatedAt.ifBlank { java.time.Instant.now().toString() })
                .put("category", product.category)
                .put("supermarket", product.supermarket)
                .put("notes", product.notes)
        )

        val body = payload.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/spesascan_articoli?on_conflict=user_id,code")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                error("Salvataggio cloud non riuscito (${response.code}): $errorBody")
            }
        }
    }

    suspend fun lookupOpenFoodFacts(code: String): SavedProduct? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://world.openfoodfacts.org/api/v2/product/${code}.json?fields=code,product_name,product_name_it,brands,quantity,generic_name,generic_name_it,categories,image_front_url"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SpesaScan/0.3.0 (Android)")
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
                remoteImageUrl = p.optString("image_front_url"),
                updatedAt = "",
                category = p.optString("categories")
            )
        }.getOrNull()
    }

    suspend fun persistManualImage(code: String, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "product_images").apply { mkdirs() }
            val safeCode = code.filter { it.isLetterOrDigit() }
                .ifBlank { System.currentTimeMillis().toString() }
            val file = File(dir, "${safeCode}_manual_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching ""
            file.absolutePath
        }.getOrDefault("")
    }

    suspend fun persistManualBitmap(code: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "product_images").apply { mkdirs() }
            val safeCode = code.filter { it.isLetterOrDigit() }
                .ifBlank { System.currentTimeMillis().toString() }
            val file = File(dir, "${safeCode}_camera_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            file.absolutePath
        }.getOrDefault("")
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
                .header("User-Agent", "SpesaScan/0.3.0 (Android)")
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

private fun productTimestamp(product: SavedProduct): Long {
    val raw = product.updatedAt.ifBlank { product.savedAt }
    if (raw.isBlank()) return 0L
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }
        .recoverCatching { java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        .recoverCatching {
            java.time.LocalDateTime.parse(raw)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        .getOrDefault(0L)
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
    put("updatedAt", updatedAt)
    put("category", category)
    put("supermarket", supermarket)
    put("notes", notes)
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
    remoteImageUrl = optString("remoteImageUrl"),
    updatedAt = optString("updatedAt").ifBlank { optString("savedAt") },
    category = optString("category"),
    supermarket = optString("supermarket"),
    notes = optString("notes")
)

private fun money(value: Double): String = String.format(Locale.ITALY, "%.2f €", value)

private fun displayDate(raw: String): String {
    if (raw.isBlank()) return ""
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALY)
    return runCatching {
        java.time.Instant.parse(raw)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter)
    }.recoverCatching {
        java.time.OffsetDateTime.parse(raw)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .format(formatter)
    }.recoverCatching {
        LocalDateTime.parse(raw).format(formatter)
    }.getOrDefault(raw)
}

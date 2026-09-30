package it.meapps.spesascan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

enum class AppSection { ARTICLES, SCANNER, ACCOUNT, INFO }

class MainActivity : ComponentActivity() {
    private val repository by lazy { ProductRepository(this) }
    private val auth by lazy { AuthClient(this) }
    private var sessionState by mutableStateOf<AuthSession?>(null)
    private var authBusy by mutableStateOf(false)
    private var authMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionState = auth.session
        handleOAuthIntent(intent)

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
                    if (sessionState == null) {
                        LoginScreen(
                            busy = authBusy,
                            message = authMessage,
                            googleUrl = auth.googleAuthUrl(),
                            onLogin = ::login,
                            onSignUp = ::signUp,
                            onReset = ::resetPassword
                        )
                    } else {
                        SpesaScanApp(
                            repository = repository,
                            session = sessionState!!,
                            onLogout = ::logout
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "spesascan" && data.host == "login-callback") {
            authBusy = true
            lifecycleScope.launch {
                runCatching { auth.completeGoogleOAuth(data) }
                    .onSuccess {
                        sessionState = it
                        authMessage = null
                    }
                    .onFailure { authMessage = it.message ?: "Accesso Google non riuscito" }
                authBusy = false
            }
        }
    }

    private fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            authMessage = "Inserisci email e password"
            return
        }
        authBusy = true
        lifecycleScope.launch {
            runCatching { auth.signIn(email, password) }
                .onSuccess {
                    sessionState = it
                    authMessage = null
                }
                .onFailure { authMessage = it.message ?: "Accesso non riuscito" }
            authBusy = false
        }
    }

    private fun signUp(email: String, password: String) {
        if (email.isBlank() || password.length < 8) {
            authMessage = "Usa un'email valida e una password di almeno 8 caratteri"
            return
        }
        authBusy = true
        lifecycleScope.launch {
            runCatching { auth.signUp(email, password) }
                .onSuccess {
                    sessionState = auth.session
                    authMessage = if (auth.session != null) "Account creato" else "Controlla l'email per confermare l'account"
                }
                .onFailure { authMessage = it.message ?: "Registrazione non riuscita" }
            authBusy = false
        }
    }

    private fun resetPassword(email: String) {
        if (email.isBlank()) {
            authMessage = "Inserisci prima la tua email"
            return
        }
        authBusy = true
        lifecycleScope.launch {
            runCatching { auth.resetPassword(email) }
                .onSuccess { authMessage = "Email di recupero inviata" }
                .onFailure { authMessage = it.message ?: "Recupero password non riuscito" }
            authBusy = false
        }
    }

    private fun logout() {
        authBusy = true
        lifecycleScope.launch {
            auth.signOut()
            sessionState = null
            authMessage = null
            authBusy = false
        }
    }
}

@Composable
private fun LoginScreen(
    busy: Boolean,
    message: String?,
    googleUrl: String,
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onReset: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            Modifier.fillMaxWidth().widthIn(max = 440.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1EDF2))
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, null, tint = AppBlue, modifier = Modifier.size(46.dp))
                Text(if (register) "Crea account" else "SpesaScan", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(
                    "Scansiona, riconosci e salva i prodotti che compri.",
                    color = Color(0xFF667085)
                )

                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(googleUrl)))
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF202124)
                    ),
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
                    singleLine = true,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Button(
                    onClick = {
                        if (register) onSignUp(email, password) else onLogin(email, password)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(if (register) "Registrati" else "Accedi", fontWeight = FontWeight.Black)
                    }
                }

                TextButton(
                    onClick = { register = !register },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (register) "Hai già un account? Accedi" else "Crea un nuovo account")
                }

                if (!register) {
                    TextButton(
                        onClick = { onReset(email) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Password dimenticata")
                    }
                }

                message?.let {
                    Text(
                        it,
                        color = if (it.contains("inviata") || it.contains("creato")) Positive else Negative,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpesaScanApp(
    repository: ProductRepository,
    session: AuthSession,
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(AppSection.ARTICLES) }
    var products by remember { mutableStateOf(repository.loadAll()) }
    var editing by remember { mutableStateOf<SavedProduct?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var autoScan by rememberSaveable { mutableStateOf(false) }

    if (creating || editing != null) {
        ProductEditorScreen(
            repository = repository,
            existing = editing,
            autoStartScan = autoScan,
            onBack = {
                creating = false
                editing = null
                autoScan = false
            },
            onSaved = {
                products = repository.loadAll()
                creating = false
                editing = null
                autoScan = false
                section = AppSection.ARTICLES
            }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = Color.White
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(AppNavy, Color(0xFF0E4C92))))
                        .padding(22.dp)
                ) {
                    Column {
                        Text("SpesaScan", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp))
                        Text(session.email, color = Color(0xFFD7E3F4), fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                DrawerItem(Icons.Default.Inventory2, "Articoli", section == AppSection.ARTICLES) {
                    section = AppSection.ARTICLES
                    scope.launch { drawerState.close() }
                }
                DrawerItem(Icons.Default.QrCodeScanner, "Scanner", section == AppSection.SCANNER) {
                    section = AppSection.SCANNER
                    scope.launch { drawerState.close() }
                }
                DrawerItem(Icons.Default.Person, "Account", section == AppSection.ACCOUNT) {
                    section = AppSection.ACCOUNT
                    scope.launch { drawerState.close() }
                }
                DrawerItem(Icons.Default.Info, "Informazioni", section == AppSection.INFO) {
                    section = AppSection.INFO
                    scope.launch { drawerState.close() }
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                NavigationDrawerItem(
                    label = { Text("Esci", fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = onLogout,
                    icon = { Icon(Icons.Default.Logout, null, tint = Negative) },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                GestionaleStyleHeader(
                    onMenu = { scope.launch { drawerState.open() } }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = section == AppSection.ARTICLES,
                        onClick = { section = AppSection.ARTICLES },
                        icon = { Icon(Icons.Default.Inventory2, null) },
                        label = { Text("Articoli") }
                    )
                    NavigationBarItem(
                        selected = section == AppSection.SCANNER,
                        onClick = { section = AppSection.SCANNER },
                        icon = { Icon(Icons.Default.QrCodeScanner, null) },
                        label = { Text("Scanner") }
                    )
                    NavigationBarItem(
                        selected = section == AppSection.ACCOUNT,
                        onClick = { section = AppSection.ACCOUNT },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text("Account") }
                    )
                }
            },
            floatingActionButton = {
                if (section == AppSection.ARTICLES) {
                    FloatingActionButton(
                        onClick = {
                            creating = true
                            autoScan = false
                        },
                        containerColor = AppAmber,
                        contentColor = AppNavy
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nuovo articolo")
                    }
                }
            }
        ) { padding ->
            when (section) {
                AppSection.ARTICLES -> ArticlesGrid(
                    modifier = Modifier.padding(padding),
                    products = products,
                    onOpen = { editing = it },
                    onScan = {
                        creating = true
                        autoScan = true
                    }
                )
                AppSection.SCANNER -> ScannerLanding(
                    modifier = Modifier.padding(padding),
                    onScan = {
                        creating = true
                        autoScan = true
                    }
                )
                AppSection.ACCOUNT -> AccountScreen(
                    modifier = Modifier.padding(padding),
                    session = session,
                    count = products.size,
                    onLogout = onLogout
                )
                AppSection.INFO -> InfoScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        modifier = Modifier.padding(horizontal = 10.dp)
    )
}

@Composable
private fun GestionaleStyleHeader(onMenu: () -> Unit) {
    val now = remember { LocalDateTime.now() }
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy HH:mm", Locale.ITALY))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString() }

    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(AppNavy, Color(0xFF0D63B6))))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("SpesaScan", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(date, color = Color(0xFFE0E7F2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedIconButton(
                onClick = onMenu,
                border = BorderStroke(2.dp, Color.White),
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color.White),
                modifier = Modifier.size(58.dp)
            ) {
                Icon(Icons.Default.Menu, null, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun ArticlesGrid(
    modifier: Modifier,
    products: List<SavedProduct>,
    onOpen: (SavedProduct) -> Unit,
    onScan: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var recentOnly by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(products, query, recentOnly) {
        val q = query.trim().lowercase(Locale.ITALY)
        products.filter { product ->
            val matches = q.isBlank() || listOf(product.name, product.brand, product.code, product.description)
                .any { it.lowercase(Locale.ITALY).contains(q) }
            val recent = !recentOnly || runCatching {
                LocalDateTime.parse(product.savedAt).isAfter(LocalDateTime.now().minusDays(30))
            }.getOrDefault(false)
            matches && recent
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            label = { Text("Cerca nome, codice o marca") },
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !recentOnly,
                onClick = { recentOnly = false },
                label = { Text("Tutto") }
            )
            FilterChip(
                selected = recentOnly,
                onClick = { recentOnly = true },
                label = { Text("Ultimi 30 gg") }
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${filtered.size} articoli",
                modifier = Modifier.align(Alignment.CenterVertically),
                fontWeight = FontWeight.Black,
                color = AppNavy
            )
        }

        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, LegacyBorder)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inventory2, null, tint = AppBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nessun articolo", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onScan) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scansiona il primo prodotto")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filtered, key = { it.code }) { product ->
                    ProductGridCard(product, onClick = { onOpen(product) })
                }
            }
        }
    }
}

@Composable
private fun ProductGridCard(product: SavedProduct, onClick: () -> Unit) {
    val imageModel: Any? = product.imagePath.takeIf { it.isNotBlank() }?.let(::File)
        ?: product.remoteImageUrl.takeIf { it.isNotBlank() }

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1EDF2)),
        border = BorderStroke(1.5.dp, LegacyBorder)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFFF1F5FB)),
                contentAlignment = Alignment.Center
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color(0xE61E293B),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(
                            "1 foto",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(46.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Nessuna foto", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                    Surface(
                        color = Color(0xE61E293B),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    ) {
                        Text(
                            "0 foto",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(product.name, fontWeight = FontWeight.Black, fontSize = 17.sp, color = AppNavy)
                if (product.brand.isNotBlank()) {
                    Text(product.brand, color = Color(0xFF64748B), fontSize = 11.sp)
                }
                Text(product.code, color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (product.quantity.isNotBlank()) {
                    Text(product.quantity, color = Color(0xFF64748B), fontSize = 11.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(money(product.salePrice), fontWeight = FontWeight.Black, fontSize = 22.sp, color = AppNavy)
                Text(displayDate(product.savedAt), color = Positive, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ScannerLanding(modifier: Modifier, onScan: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, null, tint = AppBlue, modifier = Modifier.size(72.dp))
                Text("Scanner prodotti", fontSize = 25.sp, fontWeight = FontWeight.Black, color = AppNavy)
                Text(
                    "Inquadra il codice a barre. SpesaScan cercherà nome, marca, formato, descrizione e foto.",
                    color = Color(0xFF64748B)
                )
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apri scanner", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun AccountScreen(
    modifier: Modifier,
    session: AuthSession,
    count: Int,
    onLogout: () -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Account", fontSize = 26.sp, fontWeight = FontWeight.Black, color = AppNavy)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.email, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("ID: ${session.userId.take(10)}…", color = Color(0xFF64748B), fontSize = 12.sp)
                Text("Articoli salvati: $count", color = Color(0xFF64748B))
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Logout")
                }
            }
        }
    }
}

@Composable
private fun InfoScreen(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Informazioni", fontSize = 26.sp, fontWeight = FontWeight.Black, color = AppNavy)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SpesaScan 0.3.0", fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("Scanner barcode + Open Food Facts", color = Color(0xFF64748B))
                Text("Foto, prezzo e data vengono salvati insieme all'articolo.", color = Color(0xFF64748B))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorScreen(
    repository: ProductRepository,
    existing: SavedProduct?,
    autoStartScan: Boolean,
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
    var autoScanHandled by rememberSaveable { mutableStateOf(false) }

    suspend fun lookup(scanned: String) {
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
            return
        }

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

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { scanned ->
            code = scanned
            scope.launch { lookup(scanned) }
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

    LaunchedEffect(autoStartScan) {
        if (autoStartScan && !autoScanHandled) {
            autoScanHandled = true
            startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nuovo articolo" else "Modifica articolo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Codice e ricerca") }

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
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Scansiona")
                    }

                    OutlinedButton(
                        onClick = {
                            if (code.isNotBlank()) scope.launch { lookup(code) }
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

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            if (status.isNotBlank()) item {
                Text(
                    status,
                    color = if (status.contains("trovato", true) || status.contains("presente", true)) Positive else Color(0xFF64748B),
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
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
            }

            item { SectionTitle("Dati articolo") }
            item { AppField(name, { name = it }, "Nome prodotto *") }
            item { AppField(brand, { brand = it }, "Marca") }
            item { AppField(quantity, { quantity = it }, "Formato / quantità") }
            item { AppField(description, { description = it }, "Descrizione", minLines = 3) }

            item { SectionTitle("Prezzo") }
            item {
                OutlinedTextField(
                    value = price,
                    onValueChange = { value -> price = value.filter { it.isDigit() || it == ',' || it == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Prezzo € *") },
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
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = AppBlue)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Data inserimento", fontWeight = FontWeight.Bold)
                            Text(
                                if (existing != null) displayDate(existing.savedAt) else "Salvata automaticamente",
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
                                    val imagePath = repository.persistImage(code, localImagePath, remoteImageUrl)
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
private fun AppField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1) {
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

    fun loadAll(): List<SavedProduct> {
        val raw = prefs.getString("archive_v2", "[]").orEmpty()
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
        prefs.edit().putString("archive_v2", array.toString()).apply()
    }

    suspend fun lookupOpenFoodFacts(code: String): SavedProduct? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=code,product_name,product_name_it,brands,quantity,generic_name,generic_name_it,categories,image_front_url"
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
                remoteImageUrl = p.optString("image_front_url")
            )
        }.getOrNull()
    }

    suspend fun persistImage(code: String, currentLocalPath: String, remoteUrl: String): String =
        withContext(Dispatchers.IO) {
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
                val safeCode = code.filter { it.isLetterOrDigit() }
                    .ifBlank { System.currentTimeMillis().toString() }
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
        LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALY))
    }.getOrDefault(raw)
}

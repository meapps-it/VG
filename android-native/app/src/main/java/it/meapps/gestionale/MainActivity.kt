package it.meapps.gestionale

import android.Manifest
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import java.text.NumberFormat
import java.util.Locale

private val AppBlue = Color(0xFF155EEF)
private val AppBackground = Color(0xFFF7F8FC)
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
    val colors = lightColorScheme(
        primary = AppBlue, background = AppBackground, surface = Color.White,
        error = Negative, onPrimary = Color.White
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colors, typography = Typography()) {
            Surface(Modifier.fillMaxSize(), color = AppBackground) {
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
    BackHandler(enabled = vm.canNavigateBack()) { vm.navigateBack() }

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
        null -> MainScaffold(vm, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AppViewModel, snackbar: SnackbarHostState) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text("Gestionale", fontWeight = FontWeight.Bold); Text(vm.session?.email.orEmpty(), fontSize = 11.sp, color = Color(0xFF667085)) } },
                actions = { IconButton(onClick = vm::loadAll) { Icon(Icons.Default.Refresh, "Aggiorna") } }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = vm.selectedTab == MainTab.ARTICLES, onClick = { vm.selectTab(MainTab.ARTICLES) }, icon = { Icon(Icons.Default.Inventory2, null) }, label = { Text("Articoli") })
                NavigationBarItem(selected = vm.selectedTab == MainTab.ARCHIVES, onClick = { vm.selectTab(MainTab.ARCHIVES) }, icon = { Icon(Icons.Default.ListAlt, null) }, label = { Text("Anagrafiche") })
                NavigationBarItem(selected = vm.selectedTab == MainTab.SETTINGS, onClick = { vm.selectTab(MainTab.SETTINGS) }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Impostazioni") })
            }
        },
        floatingActionButton = {
            if (vm.selectedTab != MainTab.SETTINGS) FloatingActionButton(onClick = {
                if (vm.selectedTab == MainTab.ARTICLES) vm.openProduct() else vm.openEntity(vm.archiveKind)
            }) { Icon(Icons.Default.Add, "Aggiungi") }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.selectedTab) {
                MainTab.ARTICLES -> ProductsScreen(vm)
                MainTab.ARCHIVES -> ArchivesScreen(vm)
                MainTab.SETTINGS -> SettingsScreen(vm)
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ProductsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        AppTextField(vm.query, { vm.query = it }, "Cerca nome, codice o SKU", leading = { Icon(Icons.Default.Search, null) })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionField("Marca", vm.brandFilter, vm.brands.map { it.id to it.name }, { vm.brandFilter = it }, compact = true)
            SelectionField("Categoria", vm.categoryFilter, vm.categories.map { it.id to it.name }, { vm.categoryFilter = it }, compact = true)
            SelectionField("Fornitore", vm.supplierFilter, vm.suppliers.map { it.id to it.name }, { vm.supplierFilter = it }, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        Text("${vm.filteredProducts.size} articoli", color = Color(0xFF667085), fontSize = 13.sp)
        if (!vm.loading && vm.filteredProducts.isEmpty()) EmptyState("Nessun articolo", "Crea il primo articolo oppure modifica i filtri.")
        else LazyColumn(contentPadding = PaddingValues(vertical = 10.dp, horizontal = 0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.filteredProducts, key = { it.id }) { product -> ProductCard(product, vm) }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
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
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
                Text("Dimensione testo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${(vm.fontScale * 100).toInt()}%", color = Color(0xFF667085))
                Slider(value = vm.fontScale, onValueChange = vm::updateFontScale, valueRange = .85f..1.35f, steps = 9)
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(vm.session?.email.orEmpty())
                OutlinedButton(onClick = vm::logout, enabled = !vm.saving) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Esci") }
            } }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Informazioni app", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Gestionale Android 0.1.0")
                Text("Versione tecnica provvisoria · base Free + Premium", color = Color(0xFF667085), fontSize = 13.sp)
                Text("Backup e ripristino saranno aggiunti in una fase successiva.", color = Color(0xFF667085), fontSize = 13.sp)
            } }
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
                val preview = d.toProduct(existing)
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF))) { Column(Modifier.padding(14.dp)) {
                    Text("Costo totale ${money(preview.totalCost)}")
                    Text("Margine ${money(preview.marginEuro)} · ${"%.1f".format(Locale.ITALY, preview.marginPercent)}%", fontWeight = FontWeight.Bold, color = if (preview.marginEuro >= 0) Positive else Negative)
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

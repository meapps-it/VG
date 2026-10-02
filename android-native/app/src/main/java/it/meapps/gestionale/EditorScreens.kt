@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

internal data class ItalianMunicipality(
    val name: String,
    val province: String,
    val provinceCode: String,
    val caps: List<String>,
)

internal object ItalianMunicipalityDirectory {
    private const val DATA_URL =
        "https://cdn.jsdelivr.net/gh/RP92/comuni-italiani@main/data/comuni.json"
    private val client = OkHttpClient()
    @Volatile private var cache: List<ItalianMunicipality>? = null

    suspend fun search(query: String): List<ItalianMunicipality> =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.length < 2) return@withContext emptyList()
            val rows = load()
            val needle = clean.lowercase(Locale.ITALIAN)
            rows
                .asSequence()
                .filter { it.name.lowercase(Locale.ITALIAN).contains(needle) }
                .sortedWith(
                    compareBy<ItalianMunicipality> {
                            if (it.name.lowercase(Locale.ITALIAN).startsWith(needle)) 0 else 1
                        }
                        .thenBy { it.name }
                )
                .take(8)
                .toList()
        }

    internal fun load(): List<ItalianMunicipality> {
        cache?.let {
            return it
        }
        val request = Request.Builder().url(DATA_URL).get().build()
        val body =
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Archivio comuni non disponibile")
                response.body?.string().orEmpty()
            }
        val json = JSONArray(body)
        val rows = ArrayList<ItalianMunicipality>(json.length())
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val provinceObj = item.optJSONObject("provincia")
            val capArray = item.optJSONArray("cap")
            val caps = buildList {
                if (capArray != null)
                    for (j in 0 until capArray.length()) {
                        capArray.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                    }
            }
            rows +=
                ItalianMunicipality(
                    name = item.optString("nome"),
                    province = provinceObj?.optString("nome").orEmpty(),
                    provinceCode = provinceObj?.optString("sigla").orEmpty(),
                    caps = caps,
                )
        }
        return rows.filter { it.name.isNotBlank() }.also { cache = it }
    }
}

@Composable
internal fun OrderDatePickerField(isoDate: String, onDateSelected: (String) -> Unit) {
    val context = LocalContext.current
    val parsed =
        remember(isoDate) { runCatching { LocalDate.parse(isoDate) }.getOrDefault(LocalDate.now()) }
    val display =
        remember(parsed) {
            "%02d/%02d/%04d".format(parsed.dayOfMonth, parsed.monthValue, parsed.year)
        }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = true,
            label = { Text("Data ordine") },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier.matchParentSize().clickable {
                DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            onDateSelected(LocalDate.of(year, month + 1, day).toString())
                        },
                        parsed.year,
                        parsed.monthValue - 1,
                        parsed.dayOfMonth,
                    )
                    .show()
            }
        )
    }
}

@Composable
internal fun ProductEditorScreen(vm: AppViewModel) {
    val d = vm.productDraft
    val existing = vm.products.firstOrNull { it.id == d.id }
    val context = LocalContext.current
    val barcodeLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents
                ?.takeIf { it.isNotBlank() }
                ?.let { code -> vm.updateProductDraft(vm.productDraft.copy(code = code)) }
        }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) cameraUri?.let(vm::addPendingPhoto)
        }
    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                cameraUri = uri
                takePicture.launch(uri)
            }
        }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) {
            it.forEach(vm::addPendingPhoto)
        }
    fun launchCamera() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraUri = uri
            takePicture.launch(uri)
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    EditorScaffold(
        vm,
        topBar = {
            EditorTopBar(
                if (d.id.isBlank()) stringResource(R.string.new_product)
                else stringResource(R.string.edit),
                vm,
                vm::saveProduct,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.product_data)) }
            item {
                AppTextField(
                    d.name,
                    { vm.updateProductDraft(d.copy(name = it)) },
                    stringResource(R.string.product_name),
                )
            }
            item {
                ResponsiveFormRow {
                    AppTextField(
                        d.code,
                        { vm.updateProductDraft(d.copy(code = it)) },
                        stringResource(R.string.code),
                        Modifier.weight(1f),
                    )
                    AppTextField(
                        d.sku,
                        { vm.updateProductDraft(d.copy(sku = it)) },
                        "SKU",
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        barcodeLauncher.launch(
                            ScanOptions()
                                .setPrompt("Inquadra il codice a barre")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scanner codice a barre", fontWeight = FontWeight.Bold)
                }
            }
            item {
                SelectionField(
                    stringResource(R.string.optional_brand),
                    d.brandId,
                    vm.brands.map { it.id to it.name },
                    { vm.updateProductDraft(d.copy(brandId = it)) },
                )
            }
            item {
                SelectionField(
                    stringResource(R.string.optional_category),
                    d.categoryId,
                    vm.categories.map { it.id to it.name },
                    { vm.updateProductDraft(d.copy(categoryId = it)) },
                )
            }
            item {
                SelectionField(
                    stringResource(R.string.optional_supplier),
                    d.supplierId,
                    vm.suppliers.map { it.id to it.name },
                    { vm.updateProductDraft(d.copy(supplierId = it)) },
                )
            }
            item {
                ResponsiveFormRow {
                    Box(Modifier.weight(1f)) {
                        SelectionField(
                            stringResource(R.string.measure_weight),
                            d.measureType.ifBlank { null },
                            listOf(
                                "misura" to stringResource(R.string.measure),
                                "peso" to stringResource(R.string.weight),
                            ),
                            { value ->
                                vm.updateProductDraft(d.copy(measureType = value.orEmpty()))
                            },
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
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                AppTextField(
                    d.description,
                    { vm.updateProductDraft(d.copy(description = it)) },
                    stringResource(R.string.description),
                    minLines = 3,
                )
            }
            item { SectionTitle(stringResource(R.string.prices_availability)) }
            if (vm.showEconomicDetails)
                item {
                    ResponsiveFormRow {
                        NumberField(
                            d.purchasePrice,
                            { vm.updateProductDraft(d.copy(purchasePrice = it)) },
                            stringResource(R.string.purchase_euro),
                            Modifier.weight(1f),
                        )
                        NumberField(
                            d.extraCosts,
                            { vm.updateProductDraft(d.copy(extraCosts = it)) },
                            stringResource(R.string.extra_costs_euro),
                            Modifier.weight(1f),
                        )
                    }
                }
            item {
                ResponsiveFormRow {
                    NumberField(
                        d.salePrice,
                        { vm.updateProductDraft(d.copy(salePrice = it)) },
                        stringResource(R.string.sale_euro),
                        Modifier.weight(1f),
                    )
                    AppTextField(
                        d.quantity,
                        { vm.updateProductDraft(d.copy(quantity = it.filter(Char::isDigit))) },
                        stringResource(R.string.quantity),
                        Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(d.inPromotion, { vm.updateProductDraft(d.copy(inPromotion = it)) })
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (d.inPromotion) stringResource(R.string.product_in_promotion)
                        else stringResource(R.string.no_promotion)
                    )
                }
            }
            if (d.inPromotion)
                item {
                    NumberField(
                        d.promotionalPrice,
                        { vm.updateProductDraft(d.copy(promotionalPrice = it)) },
                        stringResource(R.string.promo_price_euro),
                        Modifier.fillMaxWidth(),
                    )
                }
            if (vm.showEconomicDetails)
                item {
                    val preview = d.toProduct(existing)
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Costo totale ${money(preview.totalCost)}")
                            Text(
                                "Margine ${money(preview.effectiveMarginEuro)} · ${"%.1f".format(Locale.ITALY, preview.effectiveMarginPercent)}%",
                                fontWeight = FontWeight.Bold,
                                color = if (preview.effectiveMarginEuro >= 0) Positive else Negative,
                            )
                        }
                    }
                }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(d.available, { vm.updateProductDraft(d.copy(available = it)) })
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (d.available) stringResource(R.string.available)
                        else stringResource(R.string.unavailable)
                    )
                }
            }
            item {
                AppTextField(
                    d.productUrl,
                    { vm.updateProductDraft(d.copy(productUrl = it)) },
                    stringResource(R.string.product_link),
                    keyboardType = KeyboardType.Uri,
                )
            }
            item {
                AppTextField(
                    d.notes,
                    { vm.updateProductDraft(d.copy(notes = it)) },
                    stringResource(R.string.notes),
                    minLines = 3,
                )
            }
            item { SectionTitle(stringResource(R.string.images)) }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            gallery.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.gallery))
                    }
                    OutlinedButton(onClick = ::launchCamera) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.camera))
                    }
                }
            }
            if (existing != null && existing.photos.isNotEmpty())
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(existing.photos, key = { it.id }) { photo ->
                            LaunchedEffect(photo.path) { vm.ensureSignedUrl(photo.path) }
                            PhotoTile(
                                vm.signedUrls[photo.path],
                                { vm.requestDelete(DeleteTarget.PhotoTarget(photo)) },
                            )
                        }
                    }
                }
            if (vm.pendingPhotos.isNotEmpty())
                item {
                    Text(
                        stringResource(R.string.pending_upload),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(vm.pendingPhotos, key = { it.toString() }) { uri ->
                            PhotoTile(uri, { vm.removePendingPhoto(uri) })
                        }
                    }
                }
            item {
                Button(
                    onClick = vm::saveProduct,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    Text(
                        if (vm.saving) stringResource(R.string.saving)
                        else stringResource(R.string.save_product)
                    )
                }
            }
            if (existing != null)
                item {
                    OutlinedButton(
                        onClick = { vm.requestDelete(DeleteTarget.ProductTarget(existing)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete_product), color = Negative)
                    }
                }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun PhotoTile(model: Any?, onDelete: () -> Unit) {
    Box(Modifier.size(116.dp)) {
        Surface(
            Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            if (model != null)
                AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else CircularProgressIndicator(Modifier.padding(42.dp), strokeWidth = 2.dp)
        }
        IconButton(
            onClick = onDelete,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                        RoundedCornerShape(50),
                    ),
        ) {
            Icon(Icons.Default.Close, "Rimuovi", tint = Negative)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomerEditorScreen(vm: AppViewModel) {
    val d = vm.customerDraft
    EditorScaffold(
        vm,
        topBar = {
            EditorTopBar(
                if (d.id.isBlank()) "Nuovo cliente" else "Modifica cliente",
                vm,
                vm::saveCustomer,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.customer_data)) }
            item {
                ResponsiveFormRow {
                    AppTextField(
                        d.firstName,
                        { vm.updateCustomerDraft(d.copy(firstName = it)) },
                        stringResource(R.string.name),
                        Modifier.weight(1f),
                    )
                    AppTextField(
                        d.lastName,
                        { vm.updateCustomerDraft(d.copy(lastName = it)) },
                        stringResource(R.string.surname),
                        Modifier.weight(1f),
                    )
                }
            }
            item { SectionTitle("Contatti") }
            item {
                AppTextField(
                    d.phone,
                    { vm.updateCustomerDraft(d.copy(phone = it)) },
                    stringResource(R.string.phone),
                    keyboardType = KeyboardType.Phone,
                )
            }
            item {
                AppTextField(
                    d.email,
                    { vm.updateCustomerDraft(d.copy(email = it)) },
                    stringResource(R.string.email),
                    keyboardType = KeyboardType.Email,
                )
            }
            item { SectionTitle("Indirizzo") }
            item {
                AppTextField(
                    d.address,
                    { vm.updateCustomerDraft(d.copy(address = it)) },
                    stringResource(R.string.address),
                )
            }
            item {
                var municipalitySuggestions by
                    remember(d.id) { mutableStateOf<List<ItalianMunicipality>>(emptyList()) }
                var lookupFailed by remember(d.id) { mutableStateOf(false) }
                var suppressSuggestions by remember(d.id) { mutableStateOf(false) }

                LaunchedEffect(d.city, suppressSuggestions) {
                    if (suppressSuggestions || d.city.trim().length < 2) {
                        municipalitySuggestions = emptyList()
                        return@LaunchedEffect
                    }
                    delay(300)
                    runCatching { ItalianMunicipalityDirectory.search(d.city) }
                        .onSuccess {
                            municipalitySuggestions = it
                            lookupFailed = false
                        }
                        .onFailure {
                            municipalitySuggestions = emptyList()
                            lookupFailed = true
                        }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        AppTextField(
                            d.city,
                            {
                                suppressSuggestions = false
                                vm.updateCustomerDraft(d.copy(city = it, country = "Italia"))
                            },
                            "Comune",
                            Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = municipalitySuggestions.isNotEmpty(),
                            onDismissRequest = { municipalitySuggestions = emptyList() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            municipalitySuggestions.forEach { municipality ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(municipality.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                listOf(
                                                        municipality.province,
                                                        municipality.provinceCode
                                                            .takeIf { it.isNotBlank() }
                                                            ?.let { "($it)" }
                                                            .orEmpty(),
                                                        municipality.caps
                                                            .firstOrNull()
                                                            ?.let { "CAP $it" }
                                                            .orEmpty(),
                                                    )
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" "),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        suppressSuggestions = true
                                        municipalitySuggestions = emptyList()
                                        vm.updateCustomerDraft(
                                            d.copy(
                                                city = municipality.name,
                                                province =
                                                    municipality.province.ifBlank {
                                                        municipality.provinceCode
                                                    },
                                                postalCode =
                                                    municipality.caps.firstOrNull().orEmpty(),
                                                country = "Italia",
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (lookupFailed) {
                        Text(
                            "Ricerca comuni non disponibile: puoi compilare i campi manualmente.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (d.city.isNotBlank()) {
                        Text(
                            "Seleziona il comune dall’elenco per compilare automaticamente Provincia e CAP.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                ResponsiveFormRow {
                    AppTextField(
                        d.province,
                        { vm.updateCustomerDraft(d.copy(province = it, country = "Italia")) },
                        "Provincia",
                        Modifier.weight(1f),
                    )
                    AppTextField(
                        d.postalCode,
                        {
                            vm.updateCustomerDraft(
                                d.copy(postalCode = it.filter(Char::isDigit), country = "Italia")
                            )
                        },
                        "CAP",
                        Modifier.weight(.8f),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = "Italia",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Nazione") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (d.city.isNotBlank() && d.postalCode.isNotBlank()) {
                    Text(
                        "Il CAP resta modificabile manualmente, utile per i comuni con più CAP.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionTitle("Note") }
            item {
                AppTextField(
                    d.notes,
                    { vm.updateCustomerDraft(d.copy(notes = it)) },
                    stringResource(R.string.notes),
                    minLines = 3,
                )
            }
            item {
                Button(
                    onClick = vm::saveCustomer,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        if (vm.saving) "Salvataggio…" else "Salva cliente",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (d.id.isNotBlank())
                item {
                    val customer = vm.customers.firstOrNull { it.id == d.id }
                    if (customer != null) {
                        OutlinedButton(
                            onClick = { vm.requestDelete(DeleteTarget.CustomerTarget(customer)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.delete_customer), color = Negative)
                        }
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderEditorScreen(vm: AppViewModel) {
    val d = vm.orderDraft
    val existingOrder = vm.orders.firstOrNull { it.id == d.id }
    val selectedProduct = vm.products.firstOrNull { it.id == d.productId }
    val qty = d.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val total = existingOrder?.total ?: ((selectedProduct?.effectiveSalePrice ?: 0.0) * qty)
    val profit = existingOrder?.profit ?: ((selectedProduct?.effectiveMarginEuro ?: 0.0) * qty)

    EditorScaffold(
        vm,
        topBar = {
            EditorTopBar(
                if (d.id.isBlank()) "Nuovo ordine" else "Modifica ordine",
                vm,
                vm::saveOrder,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.customer_and_product)) }
            item {
                SelectionField(
                    "Cliente *",
                    d.customerId,
                    vm.customers.map { it.id to it.displayName },
                    { vm.updateOrderDraft(d.copy(customerId = it)) },
                )
            }
            if (d.id.isBlank())
                item {
                    SelectionField(
                        "Articolo *",
                        d.productId,
                        vm.products.map {
                            it.id to (it.name + if (it.code.isNotBlank()) " · " + it.code else "")
                        },
                        { vm.updateOrderDraft(d.copy(productId = it)) },
                    )
                }
            if (d.id.isBlank())
                item {
                    AppTextField(
                        d.quantity,
                        { vm.updateOrderDraft(d.copy(quantity = it.filter(Char::isDigit))) },
                        stringResource(R.string.quantity),
                        keyboardType = KeyboardType.Number,
                    )
                }
            if (d.id.isNotBlank() && existingOrder != null)
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.order_products),
                                fontWeight = FontWeight.SemiBold,
                                color = AppNavy,
                            )
                            Text(
                                existingOrder.itemNames.joinToString("\n").ifBlank {
                                    "Nessun articolo"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            item {
                OrderDatePickerField(
                    isoDate = d.date,
                    onDateSelected = { vm.updateOrderDraft(d.copy(date = it)) },
                )
            }
            item {
                SelectionField(
                    "Stato",
                    d.status,
                    listOf(
                        "in_lavorazione" to "In lavorazione",
                        "spedito" to "Spedito",
                        "consegnato" to "Consegnato",
                        "annullato" to "Annullato",
                    ),
                    { value -> if (value != null) vm.updateOrderDraft(d.copy(status = value)) },
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
            if (d.paid)
                item {
                    NumberField(
                        d.amountPaid,
                        { vm.updateOrderDraft(d.copy(amountPaid = it)) },
                        "Importo pagato € (vuoto = totale)",
                        Modifier.fillMaxWidth(),
                    )
                }
            if (d.paid)
                item {
                    SelectionField(
                        "Metodo pagamento",
                        d.paymentMethod,
                        listOf(
                            "contanti" to "Contanti",
                            "carta" to "Carta",
                            "bonifico" to "Bonifico",
                            "paypal" to "PayPal",
                            "altro" to "Altro",
                        ),
                        { value ->
                            if (value != null) vm.updateOrderDraft(d.copy(paymentMethod = value))
                        },
                    )
                }
            item {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.summary), fontWeight = FontWeight.SemiBold)
                        ValueRow("Totale ordine", money(total))
                        if (vm.showEconomicDetails) {
                            ValueRow(
                                "Guadagno previsto",
                                money(profit),
                                if (profit >= 0) Positive else Negative,
                            )
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.shipping)) }
            item {
                AppTextField(
                    d.courier,
                    { vm.updateOrderDraft(d.copy(courier = it)) },
                    stringResource(R.string.courier),
                )
            }
            item {
                AppTextField(
                    d.trackingCode,
                    { vm.updateOrderDraft(d.copy(trackingCode = it)) },
                    "Tracking",
                )
            }
            item {
                AppTextField(
                    d.notes,
                    { vm.updateOrderDraft(d.copy(notes = it)) },
                    stringResource(R.string.notes),
                    minLines = 3,
                )
            }
            item {
                Button(
                    onClick = vm::saveOrder,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        if (vm.saving) "Salvataggio…"
                        else if (d.id.isBlank()) "Crea ordine" else "Salva modifiche",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntityEditorScreen(vm: AppViewModel, kind: EntityKind) {
    val d = vm.entityDraft
    val title =
        when (kind) {
            EntityKind.BRAND -> "marca"
            EntityKind.SUPPLIER -> "fornitore"
            EntityKind.CATEGORY -> "categoria"
        }
    EditorScaffold(
        vm,
        topBar = {
            EditorTopBar(
                if (d.id.isBlank()) "Nuova $title" else "Modifica $title",
                vm,
                vm::saveEntity,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AppTextField(d.name, { vm.updateEntityDraft(d.copy(name = it)) }, "Nome *") }
            when (kind) {
                EntityKind.BRAND ->
                    item {
                        AppTextField(
                            d.notes,
                            { vm.updateEntityDraft(d.copy(notes = it)) },
                            stringResource(R.string.notes),
                            minLines = 3,
                        )
                    }
                EntityKind.CATEGORY -> {
                    item {
                        AppTextField(
                            d.description,
                            { vm.updateEntityDraft(d.copy(description = it)) },
                            stringResource(R.string.description),
                            minLines = 2,
                        )
                    }
                    item {
                        AppTextField(
                            d.sortOrder,
                            {
                                vm.updateEntityDraft(
                                    d.copy(sortOrder = it.filter { c -> c.isDigit() || c == '-' })
                                )
                            },
                            "Ordine",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }
                EntityKind.SUPPLIER -> {
                    item {
                        AppTextField(
                            d.contact,
                            { vm.updateEntityDraft(d.copy(contact = it)) },
                            "Referente",
                        )
                    }
                    item {
                        AppTextField(
                            d.phone,
                            { vm.updateEntityDraft(d.copy(phone = it)) },
                            stringResource(R.string.phone),
                            keyboardType = KeyboardType.Phone,
                        )
                    }
                    item {
                        AppTextField(
                            d.email,
                            { vm.updateEntityDraft(d.copy(email = it)) },
                            stringResource(R.string.email),
                            keyboardType = KeyboardType.Email,
                        )
                    }
                    item {
                        AppTextField(
                            d.website,
                            { vm.updateEntityDraft(d.copy(website = it)) },
                            "Sito web",
                            keyboardType = KeyboardType.Uri,
                        )
                    }
                    item {
                        AppTextField(
                            d.catalogUrl,
                            { vm.updateEntityDraft(d.copy(catalogUrl = it)) },
                            "Link catalogo",
                            keyboardType = KeyboardType.Uri,
                        )
                    }
                    item {
                        AppTextField(
                            d.address,
                            { vm.updateEntityDraft(d.copy(address = it)) },
                            stringResource(R.string.address),
                        )
                    }
                    item {
                        AppTextField(
                            d.notes,
                            { vm.updateEntityDraft(d.copy(notes = it)) },
                            stringResource(R.string.notes),
                            minLines = 3,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = vm::saveEntity,
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    Text(if (vm.saving) "Salvataggio…" else stringResource(R.string.save))
                }
            }
            if (d.id.isNotBlank())
                item {
                    OutlinedButton(
                        onClick = {
                            vm.requestDelete(DeleteTarget.EntityTarget(kind, d.id, d.name))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete), color = Negative)
                    }
                }
        }
    }
}

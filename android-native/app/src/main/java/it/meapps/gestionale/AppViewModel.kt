package it.meapps.gestionale

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class EntityDraft(
    val id: String = "",
    val kind: EntityKind,
    val name: String = "",
    val notes: String = "",
    val contact: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val catalogUrl: String = "",
    val address: String = "",
    val description: String = "",
    val sortOrder: String = "0"
)

sealed interface DeleteTarget {
    data class ProductTarget(val product: Product) : DeleteTarget
    data class EntityTarget(val kind: EntityKind, val id: String, val name: String) : DeleteTarget
    data class CustomerTarget(val customer: Customer) : DeleteTarget
    data class PhotoTarget(val photo: ProductPhoto) : DeleteTarget
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SupabaseApi(application)
    private val prefs = application.getSharedPreferences("preferences", Application.MODE_PRIVATE)
    private val tabHistory = ArrayDeque<MainTab>()

    var checkingAuth by mutableStateOf(true)
        private set
    var session by mutableStateOf<Session?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var noticeMessage by mutableStateOf<String?>(null)
        private set

    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var brands by mutableStateOf<List<Brand>>(emptyList())
        private set
    var suppliers by mutableStateOf<List<Supplier>>(emptyList())
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var customers by mutableStateOf<List<Customer>>(emptyList())
        private set
    var orders by mutableStateOf<List<OrderSummary>>(emptyList())
        private set

    var selectedTab by mutableStateOf(MainTab.HOME)
        private set
    var archiveKind by mutableStateOf(EntityKind.BRAND)
    var editor by mutableStateOf<Editor?>(null)
        private set
    var productDraft by mutableStateOf(ProductDraft())
        private set
    var entityDraft by mutableStateOf(EntityDraft(kind = EntityKind.BRAND))
        private set
    var customerDraft by mutableStateOf(CustomerDraft())
        private set
    var orderDraft by mutableStateOf(OrderDraft())
        private set
    var deleteTarget by mutableStateOf<DeleteTarget?>(null)
        private set

    var query by mutableStateOf("")
    var brandFilter by mutableStateOf<String?>(null)
    var supplierFilter by mutableStateOf<String?>(null)
    var categoryFilter by mutableStateOf<String?>(null)
    var qualityFilter by mutableStateOf<String?>(null)
    var promoOnly by mutableStateOf(false)
    var orderMonthFilter by mutableStateOf<String?>(null)
        private set
    var orderActiveOnly by mutableStateOf(false)
        private set
    var fontScale by mutableStateOf(prefs.getFloat("font_scale", 1f).coerceIn(.85f, 1.35f))
        private set
    var themeMode by mutableStateOf(
        runCatching { AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name).orEmpty()) }
            .getOrDefault(AppThemeMode.SYSTEM)
    )
        private set
    var compactMode by mutableStateOf(prefs.getBoolean("compact_mode", false))
        private set
    var gridView by mutableStateOf(prefs.getBoolean("grid_view", true))
        private set
    var lastSyncAt by mutableStateOf<Long?>(null)
        private set

    val pendingPhotos = mutableStateListOf<Uri>()
    val signedUrls = mutableStateMapOf<String, String>()

    val filteredProducts: List<Product>
        get() {
            val needle = query.trim().lowercase()
            return products.filter { p ->
                (needle.isBlank() || listOf(p.name, p.code, p.sku, p.description).any { it.lowercase().contains(needle) }) &&
                    (brandFilter == null || p.brandId == brandFilter) &&
                    (supplierFilter == null || p.supplierId == supplierFilter) &&
                    (categoryFilter == null || p.categoryId == categoryFilter) &&
                    (qualityFilter == null || p.quality == qualityFilter) &&
                    (!promoOnly || p.inPromotion)
            }
        }

    init {
        viewModelScope.launch {
            session = api.restoreSession()
            checkingAuth = false
            if (session != null) loadAll()
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return showError("Inserisci email e password")
        runSaving {
            session = api.signIn(email, password)
            noticeMessage = "Accesso effettuato"
            loadAllInternal()
        }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.length < 8) return showError("Usa un’email valida e una password di almeno 8 caratteri")
        runSaving {
            val created = api.signUp(email, password)
            session = api.session
            noticeMessage = if (session != null) "Account creato" else if (created) "Controlla l’email per confermare l’account" else "Registrazione inviata"
            if (session != null) loadAllInternal()
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) return showError("Inserisci prima la tua email")
        runSaving {
            api.resetPassword(email)
            noticeMessage = "Email di recupero inviata"
        }
    }

    fun logout() = runSaving {
        api.signOut()
        session = null
        products = emptyList(); brands = emptyList(); suppliers = emptyList(); categories = emptyList(); customers = emptyList(); orders = emptyList()
        editor = null
    }

    fun loadAll() = viewModelScope.launch {
        loading = true
        errorMessage = null
        try { loadAllInternal() } catch (t: Throwable) { handleError(t) } finally { loading = false }
    }

    private suspend fun loadAllInternal() {
        val results = listOf(
            viewModelScope.async { api.fetchBrands() },
            viewModelScope.async { api.fetchSuppliers() },
            viewModelScope.async { api.fetchCategories() },
            viewModelScope.async { api.fetchProducts() },
            viewModelScope.async { api.fetchCustomers() },
            viewModelScope.async { api.fetchOrders() }
        ).awaitAll()
        @Suppress("UNCHECKED_CAST")
        brands = results[0] as List<Brand>
        @Suppress("UNCHECKED_CAST")
        suppliers = results[1] as List<Supplier>
        @Suppress("UNCHECKED_CAST")
        categories = results[2] as List<Category>
        @Suppress("UNCHECKED_CAST")
        products = results[3] as List<Product>
        @Suppress("UNCHECKED_CAST")
        customers = results[4] as List<Customer>
        @Suppress("UNCHECKED_CAST")
        orders = results[5] as List<OrderSummary>
        lastSyncAt = System.currentTimeMillis()
    }

    fun openOrdersForMonth(month: java.time.YearMonth) {
        orderMonthFilter = month.toString()
        orderActiveOnly = false
        selectTab(MainTab.ORDERS)
    }

    fun openActiveOrders() {
        orderMonthFilter = null
        orderActiveOnly = true
        selectTab(MainTab.ORDERS)
    }

    fun clearOrderDrillDown() {
        orderMonthFilter = null
        orderActiveOnly = false
    }

    fun selectTab(tab: MainTab) {
        if (selectedTab == tab) return
        tabHistory.addLast(selectedTab)
        selectedTab = tab
    }

    fun canNavigateBack() = editor != null || deleteTarget != null || tabHistory.isNotEmpty()
    fun navigateBack(): Boolean {
        if (deleteTarget != null) { deleteTarget = null; return true }
        if (editor != null) { closeEditor(); return true }
        if (tabHistory.isNotEmpty()) { selectedTab = tabHistory.removeLast(); return true }
        return false
    }

    fun openCustomer(customer: Customer? = null) {
        customerDraft = CustomerDraft.from(customer)
        editor = Editor.CustomerEditor(customer?.id)
    }

    fun updateCustomerDraft(value: CustomerDraft) { customerDraft = value }

    fun saveCustomer() {
        customerDraft.validate()?.let { return showError(it) }
        runSaving {
            api.saveCustomer(customerDraft.toCustomer())
            loadAllInternal()
            closeEditor()
            noticeMessage = "Cliente salvato"
        }
    }

    fun openOrder() {
        orderDraft = OrderDraft()
        editor = Editor.OrderEditor
    }

    fun updateOrderDraft(value: OrderDraft) { orderDraft = value }

    fun saveOrder() {
        orderDraft.validate()?.let { return showError(it) }
        val product = products.firstOrNull { it.id == orderDraft.productId }
            ?: return showError("Articolo non disponibile")
        runSaving {
            api.createOrder(orderDraft, product)
            loadAllInternal()
            closeEditor()
            noticeMessage = "Ordine creato"
        }
    }

    fun openProduct(product: Product? = null) {
        productDraft = ProductDraft.from(product)
        pendingPhotos.clear()
        editor = Editor.ProductEditor(product?.id)
    }

    fun updateProductDraft(value: ProductDraft) { productDraft = value }
    fun addPendingPhoto(uri: Uri) { if (!pendingPhotos.contains(uri)) pendingPhotos.add(uri) }
    fun removePendingPhoto(uri: Uri) { pendingPhotos.remove(uri) }

    fun saveProduct() {
        productDraft.validate()?.let { return showError(it) }
        val existing = products.firstOrNull { it.id == productDraft.id }
        runSaving {
            val saved = api.saveProduct(productDraft.toProduct(existing))
            val failures = mutableListOf<String>()
            pendingPhotos.toList().forEach { uri ->
                runCatching { api.uploadPhoto(saved.id, uri) }.onFailure { failures += it.message ?: "Foto non caricata" }
            }
            loadAllInternal()
            closeEditor()
            noticeMessage = if (failures.isEmpty()) "Articolo salvato" else "Articolo salvato, ma ${failures.size} foto non sono state caricate"
        }
    }

    fun openEntity(kind: EntityKind, id: String? = null) {
        entityDraft = when (kind) {
            EntityKind.BRAND -> brands.firstOrNull { it.id == id }?.let { EntityDraft(it.id, kind, it.name, it.notes) }
            EntityKind.SUPPLIER -> suppliers.firstOrNull { it.id == id }?.let {
                EntityDraft(it.id, kind, it.name, it.notes, it.contact, it.phone, it.email, it.website, it.catalogUrl, it.address)
            }
            EntityKind.CATEGORY -> categories.firstOrNull { it.id == id }?.let {
                EntityDraft(it.id, kind, it.name, description = it.description, sortOrder = it.sortOrder.toString())
            }
        } ?: EntityDraft(kind = kind)
        editor = Editor.EntityEditor(kind, id)
    }

    fun updateEntityDraft(value: EntityDraft) { entityDraft = value }

    fun saveEntity() {
        if (entityDraft.name.isBlank()) return showError("Il nome è obbligatorio")
        if (entityDraft.kind == EntityKind.CATEGORY && entityDraft.sortOrder.toIntOrNull() == null) return showError("L’ordine deve essere un numero")
        runSaving {
            when (entityDraft.kind) {
                EntityKind.BRAND -> api.saveBrand(Brand(entityDraft.id, entityDraft.name, entityDraft.notes))
                EntityKind.SUPPLIER -> api.saveSupplier(Supplier(
                    entityDraft.id, entityDraft.name, entityDraft.contact, entityDraft.phone, entityDraft.email,
                    entityDraft.website, entityDraft.catalogUrl, entityDraft.address, entityDraft.notes
                ))
                EntityKind.CATEGORY -> api.saveCategory(Category(entityDraft.id, entityDraft.name, entityDraft.description, entityDraft.sortOrder.toInt()))
            }
            loadAllInternal()
            closeEditor()
            noticeMessage = "Dati salvati"
        }
    }

    fun requestDelete(target: DeleteTarget) { deleteTarget = target }
    fun cancelDelete() { deleteTarget = null }
    fun confirmDelete() {
        val target = deleteTarget ?: return
        runSaving {
            when (target) {
                is DeleteTarget.ProductTarget -> api.deleteProduct(target.product)
                is DeleteTarget.EntityTarget -> api.deleteEntity(target.kind, target.id)
                is DeleteTarget.CustomerTarget -> api.deleteCustomer(target.customer.id)
                is DeleteTarget.PhotoTarget -> api.deletePhoto(target.photo)
            }
            deleteTarget = null
            loadAllInternal()
            if (target is DeleteTarget.ProductTarget || target is DeleteTarget.CustomerTarget) closeEditor()
            noticeMessage = "Eliminazione completata"
        }
    }

    fun moveCategory(category: Category, delta: Int) {
        val sorted = categories.sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name })
        val index = sorted.indexOfFirst { it.id == category.id }
        val other = sorted.getOrNull(index + delta) ?: return
        runSaving {
            if (sorted.map { it.sortOrder }.distinct().size == 1) {
                val reordered = sorted.toMutableList().apply { this[index] = other; this[index + delta] = category }
                reordered.forEachIndexed { order, item -> api.saveCategory(item.copy(sortOrder = order)) }
            } else {
                api.saveCategory(category.copy(sortOrder = other.sortOrder))
                api.saveCategory(other.copy(sortOrder = category.sortOrder))
            }
            loadAllInternal()
        }
    }

    fun ensureSignedUrl(path: String) {
        if (path.isBlank() || signedUrls.containsKey(path)) return
        viewModelScope.launch {
            runCatching { api.signedPhotoUrl(path) }.onSuccess { signedUrls[path] = it }
        }
    }

    fun updateFontScale(value: Float) {
        fontScale = value.coerceIn(.85f, 1.35f)
        prefs.edit().putFloat("font_scale", fontScale).apply()
    }

    fun updateThemeMode(value: AppThemeMode) {
        themeMode = value
        prefs.edit().putString("theme_mode", value.name).apply()
    }

    fun updateCompactMode(value: Boolean) {
        compactMode = value
        prefs.edit().putBoolean("compact_mode", value).apply()
    }

    fun updateGridView(value: Boolean) {
        gridView = value
        prefs.edit().putBoolean("grid_view", value).apply()
    }

    fun createBackupJson(includePhotoMetadata: Boolean): String {
        fun brandJson(value: Brand) = JSONObject().apply {
            put("id", value.id); put("name", value.name); put("notes", value.notes)
        }
        fun supplierJson(value: Supplier) = JSONObject().apply {
            put("id", value.id); put("name", value.name); put("contact", value.contact)
            put("phone", value.phone); put("email", value.email); put("website", value.website)
            put("catalog_url", value.catalogUrl); put("address", value.address); put("notes", value.notes)
        }
        fun categoryJson(value: Category) = JSONObject().apply {
            put("id", value.id); put("name", value.name); put("description", value.description); put("sort_order", value.sortOrder)
        }
        fun customerJson(value: Customer) = JSONObject().apply {
            put("id", value.id)
            put("first_name", value.firstName)
            put("last_name", value.lastName)
            put("phone", value.phone)
            put("email", value.email)
            put("address", value.address)
            put("city", value.city)
            put("postal_code", value.postalCode)
            put("province", value.province)
            put("country", value.country)
            put("notes", value.notes)
        }
        fun orderJson(value: OrderSummary) = JSONObject().apply {
            put("id", value.id)
            put("number", value.number)
            put("customer_id", value.customerId)
            put("customer_name", value.customerName)
            put("date", value.date)
            put("status", value.status)
            put("total", value.total)
            put("total_paid", value.totalPaid)
            put("paid", value.paid)
            put("payment_status", value.paymentStatus)
            put("profit", value.profit)
            put("tracking_code", value.trackingCode)
            put("courier", value.courier)
            put("items", JSONArray(value.itemNames))
        }
        fun productJson(value: Product) = JSONObject().apply {
            put("id", value.id); put("name", value.name); put("code", value.code); put("sku", value.sku)
            put("brand_id", value.brandId); put("category_id", value.categoryId); put("supplier_id", value.supplierId)
            put("description", value.description); put("purchase_price", value.purchasePrice)
            put("sale_price", value.salePrice); put("extra_costs", value.extraCosts); put("quantity", value.quantity)
            put("available", value.available); put("notes", value.notes); put("product_url", value.productUrl)
            put("created_at", value.createdAt); put("updated_at", value.updatedAt)
            if (includePhotoMetadata) put("photos", JSONArray(value.photos.map { photo ->
                JSONObject().apply { put("id", photo.id); put("path", photo.path); put("order", photo.order) }
            }))
        }
        return JSONObject().apply {
            put("format", "gestionale-android-backup")
            put("version", 1)
            put("exported_at", Instant.now().toString())
            put("owner_id", session?.userId)
            put("includes_photo_metadata", includePhotoMetadata)
            put("brands", JSONArray(brands.map(::brandJson)))
            put("suppliers", JSONArray(suppliers.map(::supplierJson)))
            put("categories", JSONArray(categories.map(::categoryJson)))
            put("customers", JSONArray(customers.map(::customerJson)))
            put("orders", JSONArray(orders.map(::orderJson)))
            put("products", JSONArray(products.map(::productJson)))
        }.toString(2)
    }

    fun clearMessages() { errorMessage = null; noticeMessage = null }
    private fun closeEditor() { editor = null; pendingPhotos.clear() }
    private fun showError(message: String) { errorMessage = message }

    private fun runSaving(block: suspend () -> Unit) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            errorMessage = null
            try { block() } catch (t: Throwable) { handleError(t) } finally { saving = false }
        }
    }

    private fun handleError(t: Throwable) {
        if (t is AuthenticationExpiredException || (t is ApiException && t.statusCode == 401)) {
            api.clearSession(); session = null
            errorMessage = "Sessione scaduta. Accedi di nuovo."
        } else {
            errorMessage = when {
                t.message?.contains("duplicate", true) == true -> "Esiste già un elemento con questo nome o codice"
                t.message?.contains("foreign key", true) == true || t.message?.contains("violates", true) == true ->
                    "Questo elemento è collegato ad altri dati e non può essere eliminato finché è in uso."
                t is java.net.UnknownHostException -> "Connessione assente. Controlla Internet e riprova."
                else -> t.message ?: "Operazione non riuscita"
            }
        }
    }
}

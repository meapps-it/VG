package it.meapps.gestionale

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String
)

data class Brand(
    val id: String = "",
    val name: String = "",
    val notes: String = ""
)

data class Supplier(
    val id: String = "",
    val name: String = "",
    val contact: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val catalogUrl: String = "",
    val address: String = "",
    val notes: String = ""
)

data class Category(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val sortOrder: Int = 0
)

data class Customer(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val province: String = "",
    val country: String = "Italia",
    val notes: String = ""
) {
    val displayName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Cliente" }
}

data class OrderSummary(
    val id: String = "",
    val number: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val date: String = "",
    val status: String = "",
    val total: Double = 0.0,
    val totalPaid: Double = 0.0,
    val paid: Boolean = false,
    val paymentStatus: String = "",
    val paymentMethod: String = "",
    val profit: Double = 0.0,
    val trackingCode: String = "",
    val courier: String = "",
    val notes: String = "",
    val itemNames: List<String> = emptyList()
)

data class CustomerDraft(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val province: String = "",
    val country: String = "Italia",
    val notes: String = ""
) {
    fun validate(): String? = if (firstName.isBlank() && lastName.isBlank()) "Inserisci almeno il nome del cliente" else null

    fun toCustomer() = Customer(
        id = id, firstName = firstName.trim(), lastName = lastName.trim(), phone = phone.trim(),
        email = email.trim(), address = address.trim(), city = city.trim(), postalCode = postalCode.trim(),
        province = province.trim(), country = country.trim().ifBlank { "Italia" }, notes = notes.trim()
    )

    companion object {
        fun from(value: Customer?) = value?.let {
            CustomerDraft(it.id, it.firstName, it.lastName, it.phone, it.email, it.address, it.city, it.postalCode, it.province, it.country, it.notes)
        } ?: CustomerDraft()
    }
}

data class OrderDraft(
    val id: String = "",
    val customerId: String? = null,
    val productId: String? = null,
    val quantity: String = "1",
    val date: String = java.time.LocalDate.now().toString(),
    val status: String = "in_lavorazione",
    val paid: Boolean = false,
    val amountPaid: String = "",
    val paymentMethod: String = "altro",
    val trackingCode: String = "",
    val courier: String = "",
    val notes: String = ""
) {
    fun validate(): String? = when {
        customerId.isNullOrBlank() -> "Seleziona un cliente"
        id.isBlank() && productId.isNullOrBlank() -> "Seleziona un articolo"
        id.isBlank() && (quantity.toIntOrNull() == null || quantity.toInt() <= 0) -> "La quantità deve essere maggiore di zero"
        paid && amountPaid.isNotBlank() && (amountPaid.toDoubleOrNull() ?: -1.0) < 0.0 -> "L'importo pagato non è valido"
        else -> null
    }

    companion object {
        fun from(order: OrderSummary) = OrderDraft(
            id = order.id,
            customerId = order.customerId,
            date = order.date,
            status = order.status,
            paid = order.paid,
            amountPaid = order.totalPaid.takeIf { it > 0 }?.toString().orEmpty(),
            paymentMethod = order.paymentMethod.ifBlank { "altro" },
            trackingCode = order.trackingCode,
            courier = order.courier,
            notes = order.notes
        )
    }
}

data class ProductPhoto(
    val id: String,
    val productId: String,
    val path: String,
    val order: Int
)

data class Product(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val sku: String = "",
    val brandId: String? = null,
    val categoryId: String? = null,
    val supplierId: String? = null,
    val measureType: String = "",
    val measureValue: String = "",
    val description: String = "",
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val extraCosts: Double = 0.0,
    val quantity: Int = 0,
    val available: Boolean = true,
    val inPromotion: Boolean = false,
    val promotionalPrice: Double = 0.0,
    val notes: String = "",
    val productUrl: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val isDemo: Boolean = false,
    val photos: List<ProductPhoto> = emptyList()
) {
    val totalCost: Double get() = purchasePrice + extraCosts
    val effectiveSalePrice: Double get() = if (inPromotion && promotionalPrice > 0.0) promotionalPrice else salePrice
    val marginEuro: Double get() = salePrice - totalCost
    val effectiveMarginEuro: Double get() = effectiveSalePrice - totalCost
    val marginPercent: Double get() = if (salePrice == 0.0) 0.0 else marginEuro / salePrice * 100.0
    val effectiveMarginPercent: Double get() = if (effectiveSalePrice == 0.0) 0.0 else effectiveMarginEuro / effectiveSalePrice * 100.0
}

data class ProductDraft(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val sku: String = "",
    val brandId: String? = null,
    val categoryId: String? = null,
    val supplierId: String? = null,
    val measureType: String = "",
    val measureValue: String = "",
    val description: String = "",
    val purchasePrice: String = "",
    val salePrice: String = "",
    val extraCosts: String = "",
    val quantity: String = "0",
    val available: Boolean = true,
    val inPromotion: Boolean = false,
    val promotionalPrice: String = "",
    val notes: String = "",
    val productUrl: String = ""
) {
    fun validate(): String? = when {
        name.isBlank() -> "Il nome dell’articolo è obbligatorio"
        purchasePrice.toDoubleOrNull()?.let { it < 0 } == true -> "Il prezzo di acquisto non può essere negativo"
        salePrice.toDoubleOrNull()?.let { it < 0 } == true -> "Il prezzo di vendita non può essere negativo"
        extraCosts.toDoubleOrNull()?.let { it < 0 } == true -> "I costi aggiuntivi non possono essere negativi"
        inPromotion && (promotionalPrice.toDoubleOrNull() ?: 0.0) <= 0.0 -> "Inserisci un prezzo promozionale valido"
        quantity.toIntOrNull() == null || quantity.toInt() < 0 -> "La quantità deve essere un numero non negativo"
        else -> null
    }

    fun toProduct(existing: Product? = null) = Product(
        id = id,
        name = name.trim(),
        code = code.trim(),
        sku = sku.trim(),
        brandId = brandId,
        categoryId = categoryId,
        supplierId = supplierId,
        measureType = measureType.trim(),
        measureValue = measureValue.trim(),
        description = description.trim(),
        purchasePrice = purchasePrice.toDoubleOrNull() ?: 0.0,
        salePrice = salePrice.toDoubleOrNull() ?: 0.0,
        extraCosts = extraCosts.toDoubleOrNull() ?: 0.0,
        quantity = quantity.toIntOrNull() ?: 0,
        available = available,
        inPromotion = inPromotion,
        promotionalPrice = promotionalPrice.toDoubleOrNull() ?: 0.0,
        notes = notes.trim(),
        productUrl = productUrl.trim(),
        createdAt = existing?.createdAt.orEmpty(),
        updatedAt = existing?.updatedAt.orEmpty(),
        isDemo = existing?.isDemo ?: false,
        photos = existing?.photos.orEmpty()
    )

    companion object {
        fun from(product: Product?) = product?.let {
            ProductDraft(
                id = it.id, name = it.name, code = it.code, sku = it.sku,
                brandId = it.brandId, categoryId = it.categoryId, supplierId = it.supplierId,
                measureType = it.measureType, measureValue = it.measureValue,
                description = it.description,
                purchasePrice = it.purchasePrice.takeIf { value -> value != 0.0 }?.toString().orEmpty(),
                salePrice = it.salePrice.takeIf { value -> value != 0.0 }?.toString().orEmpty(),
                extraCosts = it.extraCosts.takeIf { value -> value != 0.0 }?.toString().orEmpty(),
                quantity = it.quantity.toString(), available = it.available,
                inPromotion = it.inPromotion,
                promotionalPrice = it.promotionalPrice.takeIf { value -> value != 0.0 }?.toString().orEmpty(),
                notes = it.notes, productUrl = it.productUrl
            )
        } ?: ProductDraft()
    }
}

enum class MainTab { HOME, ARTICLES, CLIENTS, ORDERS, ARCHIVES, SETTINGS }
enum class EntityKind { BRAND, SUPPLIER, CATEGORY }
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

sealed interface Editor {
    data class ProductEditor(val productId: String?) : Editor
    data class EntityEditor(val kind: EntityKind, val entityId: String?) : Editor
    data class CustomerEditor(val customerId: String?) : Editor
    data class OrderEditor(val orderId: String?) : Editor
}

sealed interface Detail {
    data class ProductDetail(val productId: String) : Detail
    data class CustomerDetail(val customerId: String) : Detail
    data class OrderDetail(val orderId: String) : Detail
}

class NavigationHistory(initial: MainTab = MainTab.HOME) {
    var current: MainTab = initial
        private set
    private val history = ArrayDeque<MainTab>()

    fun select(tab: MainTab) {
        if (tab == current) return
        history.addLast(current)
        current = tab
    }

    fun canGoBack(): Boolean = history.isNotEmpty()
    fun back(): Boolean {
        if (history.isEmpty()) return false
        current = history.removeLast()
        return true
    }
}

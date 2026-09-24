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
    val description: String = "",
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val extraCosts: Double = 0.0,
    val quantity: Int = 0,
    val available: Boolean = true,
    val notes: String = "",
    val productUrl: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val photos: List<ProductPhoto> = emptyList()
) {
    val totalCost: Double get() = purchasePrice + extraCosts
    val marginEuro: Double get() = salePrice - totalCost
    val marginPercent: Double get() = if (salePrice == 0.0) 0.0 else marginEuro / salePrice * 100.0
}

data class ProductDraft(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val sku: String = "",
    val brandId: String? = null,
    val categoryId: String? = null,
    val supplierId: String? = null,
    val description: String = "",
    val purchasePrice: String = "",
    val salePrice: String = "",
    val extraCosts: String = "",
    val quantity: String = "0",
    val available: Boolean = true,
    val notes: String = "",
    val productUrl: String = ""
) {
    fun validate(): String? = when {
        name.isBlank() -> "Il nome dell’articolo è obbligatorio"
        purchasePrice.toDoubleOrNull()?.let { it < 0 } == true -> "Il prezzo di acquisto non può essere negativo"
        salePrice.toDoubleOrNull()?.let { it < 0 } == true -> "Il prezzo di vendita non può essere negativo"
        extraCosts.toDoubleOrNull()?.let { it < 0 } == true -> "I costi aggiuntivi non possono essere negativi"
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
        description = description.trim(),
        purchasePrice = purchasePrice.toDoubleOrNull() ?: 0.0,
        salePrice = salePrice.toDoubleOrNull() ?: 0.0,
        extraCosts = extraCosts.toDoubleOrNull() ?: 0.0,
        quantity = quantity.toIntOrNull() ?: 0,
        available = available,
        notes = notes.trim(),
        productUrl = productUrl.trim(),
        createdAt = existing?.createdAt.orEmpty(),
        updatedAt = existing?.updatedAt.orEmpty(),
        photos = existing?.photos.orEmpty()
    )

    companion object {
        fun from(product: Product?) = product?.let {
            ProductDraft(
                id = it.id, name = it.name, code = it.code, sku = it.sku,
                brandId = it.brandId, categoryId = it.categoryId, supplierId = it.supplierId,
                description = it.description,
                purchasePrice = it.purchasePrice.takeIf { value -> value != 0.0 }?.toString().orEmpty(),
                salePrice = it.salePrice.takeIf { value -> value != 0.0 }?.toString().orEmpty(),
                extraCosts = it.extraCosts.takeIf { value -> value != 0.0 }?.toString().orEmpty(),
                quantity = it.quantity.toString(), available = it.available,
                notes = it.notes, productUrl = it.productUrl
            )
        } ?: ProductDraft()
    }
}

enum class MainTab { ARTICLES, ARCHIVES, SETTINGS }
enum class EntityKind { BRAND, SUPPLIER, CATEGORY }

sealed interface Editor {
    data class ProductEditor(val productId: String?) : Editor
    data class EntityEditor(val kind: EntityKind, val entityId: String?) : Editor
}

class NavigationHistory(initial: MainTab = MainTab.ARTICLES) {
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

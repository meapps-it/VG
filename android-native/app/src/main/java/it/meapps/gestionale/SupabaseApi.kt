package it.meapps.gestionale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

class ApiException(message: String, val statusCode: Int = 0) : IOException(message)
class AuthenticationExpiredException(message: String) : IOException(message)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(): Session? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = prefs.getLong("expires_at", 0),
            userId = userId,
            email = prefs.getString("email", "").orEmpty()
        )
    }

    fun save(session: Session) {
        prefs.edit()
            .putString("access", session.accessToken)
            .putString("refresh", session.refreshToken)
            .putLong("expires_at", session.expiresAtEpochSeconds)
            .putString("user_id", session.userId)
            .putString("email", session.email)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

class SupabaseApi(private val context: Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
    private val store = SessionStore(context)
    private val refreshMutex = Mutex()

    var session: Session? = store.load()
        private set

    suspend fun restoreSession(): Session? {
        val saved = session ?: return null
        return if (saved.expiresAtEpochSeconds > nowSeconds() + 60) saved else runCatching { refreshSession() }.getOrNull()
    }

    suspend fun signIn(email: String, password: String): Session = withContext(Dispatchers.IO) {
        val response = rawRequest(
            method = "POST",
            url = "$baseUrl/auth/v1/token?grant_type=password",
            body = JSONObject().put("email", email.trim()).put("password", password).toString().toRequestBody(jsonType),
            token = null
        )
        parseAndSaveSession(JSONObject(response), email.trim())
    }

    suspend fun signUp(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val response = rawRequest(
            method = "POST",
            url = "$baseUrl/auth/v1/signup",
            body = JSONObject().put("email", email.trim()).put("password", password).toString().toRequestBody(jsonType),
            token = null
        )
        val json = JSONObject(response)
        if (json.optString("access_token").isNotBlank()) parseAndSaveSession(json, email.trim())
        json.has("user") || json.has("id")
    }

    suspend fun resetPassword(email: String) = withContext(Dispatchers.IO) {
        rawRequest(
            method = "POST",
            url = "$baseUrl/auth/v1/recover",
            body = JSONObject().put("email", email.trim()).toString().toRequestBody(jsonType),
            token = null
        )
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = session?.accessToken
        if (token != null) runCatching { rawRequest("POST", "$baseUrl/auth/v1/logout", "{}".toRequestBody(jsonType), token) }
        clearSession()
    }

    fun clearSession() {
        session = null
        store.clear()
    }

    private suspend fun refreshSession(): Session = refreshMutex.withLock {
        val current = session ?: throw AuthenticationExpiredException("Sessione non disponibile")
        if (current.expiresAtEpochSeconds > nowSeconds() + 60) return@withLock current
        val response = withContext(Dispatchers.IO) {
            rawRequest(
                method = "POST",
                url = "$baseUrl/auth/v1/token?grant_type=refresh_token",
                body = JSONObject().put("refresh_token", current.refreshToken).toString().toRequestBody(jsonType),
                token = null
            )
        }
        parseAndSaveSession(JSONObject(response), current.email)
    }

    private fun parseAndSaveSession(json: JSONObject, fallbackEmail: String): Session {
        val user = json.optJSONObject("user")
        val parsed = Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtEpochSeconds = json.optLong("expires_at").takeIf { it > 0 }
                ?: nowSeconds() + json.optLong("expires_in", 3600),
            userId = user?.optString("id").orEmpty().ifBlank { session?.userId.orEmpty() },
            email = user?.optString("email").orEmpty().ifBlank { fallbackEmail }
        )
        if (parsed.userId.isBlank()) throw ApiException("Risposta di autenticazione incompleta")
        session = parsed
        store.save(parsed)
        return parsed
    }

    suspend fun fetchBrands(): List<Brand> = getArray("marche?select=*&order=nome.asc").objects().map {
        Brand(it.string("id"), it.string("nome"), it.string("note"))
    }

    suspend fun fetchSuppliers(): List<Supplier> = getArray("fornitori?select=*&order=nome.asc").objects().map {
        Supplier(
            id = it.string("id"), name = it.string("nome"), contact = it.string("referente"),
            phone = it.string("telefono"), email = it.string("email"), website = it.string("sito_web"),
            catalogUrl = it.string("link_catalogo"), address = it.string("indirizzo"), notes = it.string("note")
        )
    }

    suspend fun fetchCategories(): List<Category> = getArray("categorie?select=*&order=sort_order.asc,nome.asc").objects().map {
        Category(it.string("id"), it.string("nome"), it.string("descrizione"), it.optInt("sort_order"))
    }

    suspend fun fetchCustomers(): List<Customer> =
        getArray("clienti?select=*&order=nome.asc,cognome.asc").objects().map {
            Customer(
                id = it.string("id"),
                firstName = it.string("nome"),
                lastName = it.string("cognome"),
                phone = it.string("telefono"),
                email = it.string("email"),
                address = it.string("indirizzo"),
                city = it.string("citta"),
                postalCode = it.string("cap"),
                province = it.string("provincia"),
                country = it.string("paese").ifBlank { "Italia" },
                notes = it.string("note")
            )
        }

    suspend fun fetchOrders(): List<OrderSummary> {
        val customers = fetchCustomers().associateBy { it.id }
        val products = getArray("prodotti?select=id,nome").objects().associate { it.string("id") to it.string("nome") }
        val rowsByOrder = getArray("righe_ordine?select=ordine_id,prodotto_id,quantita").objects()
            .groupBy { it.string("ordine_id") }
        return getArray("ordini?select=*&order=data_ordine.desc,created_at.desc").objects().map { o ->
            val names = rowsByOrder[o.string("id")].orEmpty().mapNotNull { row ->
                products[row.string("prodotto_id")]?.takeIf { it.isNotBlank() }
            }
            OrderSummary(
                id = o.string("id"),
                number = o.string("numero_ordine"),
                customerId = o.string("cliente_id"),
                customerName = customers[o.string("cliente_id")]?.displayName.orEmpty(),
                date = o.string("data_ordine"),
                status = o.string("stato"),
                total = o.number("totale"),
                totalPaid = o.number("totale_pagato"),
                profit = o.number("guadagno"),
                trackingCode = o.string("tracking_code"),
                courier = o.string("corriere"),
                itemNames = names
            )
        }
    }

    suspend fun fetchProducts(): List<Product> {
        val photos = getArray("prodotti_foto?select=*&order=ordine.asc").objects().map {
            ProductPhoto(it.string("id"), it.string("prodotto_id"), it.string("path"), it.optInt("ordine"))
        }.groupBy { it.productId }
        return getArray("prodotti?select=*&order=updated_at.desc").objects().map {
            Product(
                id = it.string("id"), name = it.string("nome"),
                code = it.string("codice").ifBlank { it.string("sku") }, sku = it.string("sku"),
                brandId = it.nullableString("marca_id"), categoryId = it.nullableString("categoria_id"),
                supplierId = it.nullableString("fornitore_id"),
                description = it.string("descrizione_app").ifBlank { it.string("descrizione") },
                purchasePrice = it.number("prezzo_acquisto"), salePrice = it.number("prezzo_vendita"),
                extraCosts = it.number("costi_aggiuntivi"), quantity = it.optInt("giacenza"),
                available = it.optBoolean("disponibile", it.optBoolean("attivo", true)),
                notes = it.string("note").ifBlank { it.string("note_interne") },
                productUrl = it.string("link_prodotto"), createdAt = it.string("created_at"),
                updatedAt = it.string("updated_at"), photos = photos[it.string("id")].orEmpty()
            )
        }
    }

    suspend fun saveCustomer(value: Customer): Customer {
        val body = JSONObject()
            .put("nome", value.firstName.trim())
            .putNullable("cognome", value.lastName)
            .putNullable("telefono", value.phone)
            .putNullable("email", value.email)
            .putNullable("indirizzo", value.address)
            .putNullable("citta", value.city)
            .putNullable("cap", value.postalCode)
            .putNullable("provincia", value.province)
            .putNullable("paese", value.country.ifBlank { "Italia" })
            .putNullable("note", value.notes)
        val result = if (value.id.isBlank()) {
            body.put("user_id", requireSession().userId)
            postObject("clienti", body)
        } else {
            patchObject("clienti?id=eq.${value.id}", body)
        }
        return Customer(
            id = result.string("id"),
            firstName = result.string("nome"),
            lastName = result.string("cognome"),
            phone = result.string("telefono"),
            email = result.string("email"),
            address = result.string("indirizzo"),
            city = result.string("citta"),
            postalCode = result.string("cap"),
            province = result.string("provincia"),
            country = result.string("paese").ifBlank { "Italia" },
            notes = result.string("note")
        )
    }

    suspend fun createOrder(draft: OrderDraft, product: Product): String {
        val current = requireSession()
        val qty = draft.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val total = product.salePrice * qty
        val profit = product.marginEuro * qty
        val orderBody = JSONObject()
            .put("user_id", current.userId)
            .put("numero_ordine", "ORD-" + System.currentTimeMillis())
            .put("cliente_id", draft.customerId)
            .put("data_ordine", draft.date)
            .put("stato", draft.status)
            .put("totale", total)
            .put("totale_pagato", 0)
            .put("guadagno", profit)
            .put("stato_pagamento", "da_pagare")
            .put("pagato", false)
            .putNullable("tracking_code", draft.trackingCode)
            .putNullable("corriere", draft.courier)
            .putNullable("note", draft.notes)
        val order = postObject("ordini", orderBody)
        val orderId = order.string("id")
        val rowBody = JSONObject()
            .put("user_id", current.userId)
            .put("ordine_id", orderId)
            .put("prodotto_id", product.id)
            .put("quantita", qty)
            .put("prezzo_unitario", product.salePrice)
            .put("costo_unitario", product.totalCost)
            .put("guadagno_riga", profit)
            .put("sconto", 0)
        postObject("righe_ordine", rowBody)
        return orderId
    }

    suspend fun saveBrand(value: Brand): Brand {
        val body = JSONObject().put("nome", value.name.trim()).putNullable("note", value.notes)
        return if (value.id.isBlank()) {
            body.put("user_id", requireSession().userId)
            postObject("marche", body).let { Brand(it.string("id"), it.string("nome"), it.string("note")) }
        } else {
            patchObject("marche?id=eq.${value.id}", body).let { Brand(it.string("id"), it.string("nome"), it.string("note")) }
        }
    }

    suspend fun saveCategory(value: Category): Category {
        val body = JSONObject().put("nome", value.name.trim()).putNullable("descrizione", value.description).put("sort_order", value.sortOrder)
        return if (value.id.isBlank()) {
            body.put("user_id", requireSession().userId)
            postObject("categorie", body).toCategory()
        } else patchObject("categorie?id=eq.${value.id}", body).toCategory()
    }

    suspend fun saveSupplier(value: Supplier): Supplier {
        val body = JSONObject()
            .put("nome", value.name.trim()).putNullable("referente", value.contact)
            .putNullable("telefono", value.phone).putNullable("email", value.email)
            .putNullable("sito_web", value.website).putNullable("link_catalogo", value.catalogUrl)
            .putNullable("indirizzo", value.address).putNullable("note", value.notes)
        val result = if (value.id.isBlank()) {
            body.put("user_id", requireSession().userId)
            postObject("fornitori", body)
        } else patchObject("fornitori?id=eq.${value.id}", body)
        return result.toSupplier()
    }

    suspend fun saveProduct(value: Product): Product {
        val body = JSONObject()
            .put("nome", value.name.trim()).putNullable("codice", value.code)
            .putNullable("sku", value.sku.ifBlank { value.code })
            .putNullable("marca_id", value.brandId).putNullable("categoria_id", value.categoryId)
            .putNullable("fornitore_id", value.supplierId)
            .putNullable("descrizione_app", value.description).putNullable("descrizione", value.description)
            .put("prezzo_acquisto", value.purchasePrice).put("prezzo_vendita", value.salePrice)
            .put("costi_aggiuntivi", value.extraCosts).put("giacenza", value.quantity)
            .put("disponibile", value.available).put("attivo", value.available)
            .putNullable("note", value.notes).putNullable("link_prodotto", value.productUrl)
        val result = if (value.id.isBlank()) {
            body.put("user_id", requireSession().userId)
            postObject("prodotti", body)
        } else patchObject("prodotti?id=eq.${value.id}", body)
        return fetchSingleProduct(result.string("id"))
    }

    private suspend fun fetchSingleProduct(id: String): Product = fetchProducts().first { it.id == id }

    suspend fun deleteEntity(kind: EntityKind, id: String) {
        val table = when (kind) { EntityKind.BRAND -> "marche"; EntityKind.SUPPLIER -> "fornitori"; EntityKind.CATEGORY -> "categorie" }
        delete("$table?id=eq.$id")
    }

    suspend fun deleteProduct(product: Product) {
        product.photos.forEach { runCatching { deletePhoto(it) } }
        delete("prodotti?id=eq.${product.id}")
    }

    suspend fun uploadPhoto(productId: String, uri: Uri): ProductPhoto = withContext(Dispatchers.IO) {
        val bytes = optimizedJpeg(uri)
        if (bytes.size > 12 * 1024 * 1024) throw ApiException("Immagine troppo grande (massimo 12 MB)")
        val current = requireSession()
        val path = "${current.userId}/$productId/${UUID.randomUUID()}.jpg"
        val requestBody = bytes.toRequestBody("image/jpeg".toMediaType())
        authorizedRawRequest("POST", "$baseUrl/storage/v1/object/articoli/$path", requestBody, mapOf("x-upsert" to "false"))
        val body = JSONObject().put("user_id", current.userId).put("prodotto_id", productId).put("path", path).put("ordine", 0)
        postObject("prodotti_foto", body).let {
            ProductPhoto(it.string("id"), it.string("prodotto_id"), it.string("path"), it.optInt("ordine"))
        }
    }

    suspend fun signedPhotoUrl(path: String): String = withContext(Dispatchers.IO) {
        val response = authorizedRawRequest(
            "POST", "$baseUrl/storage/v1/object/sign/articoli/$path",
            JSONObject().put("expiresIn", 3600).toString().toRequestBody(jsonType)
        )
        val relative = JSONObject(response).optString("signedURL").ifBlank { JSONObject(response).optString("signedUrl") }
        if (relative.startsWith("http")) relative else "$baseUrl/storage/v1$relative"
    }

    suspend fun deletePhoto(photo: ProductPhoto) = withContext(Dispatchers.IO) {
        authorizedRawRequest(
            "DELETE", "$baseUrl/storage/v1/object/articoli",
            JSONObject().put("prefixes", JSONArray().put(photo.path)).toString().toRequestBody(jsonType)
        )
        delete("prodotti_foto?id=eq.${photo.id}")
    }

    private fun optimizedJpeg(uri: Uri): ByteArray {
        val source = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val maxSide = max(info.size.width, info.size.height)
                if (maxSide > 2048) {
                    val ratio = 2048f / maxSide
                    decoder.setTargetSize((info.size.width * ratio).roundToInt(), (info.size.height * ratio).roundToInt())
                }
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input) ?: throw ApiException("Immagine non leggibile")
            }
        }
        return ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.JPEG, 86, output)
            output.toByteArray()
        }
    }

    private suspend fun getArray(path: String): JSONArray = JSONArray(authorizedRawRequest("GET", restUrl(path)))
    private suspend fun postObject(path: String, body: JSONObject): JSONObject = firstObject(
        authorizedRawRequest("POST", restUrl(path), body.toString().toRequestBody(jsonType), mapOf("Prefer" to "return=representation"))
    )
    private suspend fun patchObject(path: String, body: JSONObject): JSONObject = firstObject(
        authorizedRawRequest("PATCH", restUrl(path), body.toString().toRequestBody(jsonType), mapOf("Prefer" to "return=representation"))
    )
    private suspend fun delete(path: String) { authorizedRawRequest("DELETE", restUrl(path), headers = mapOf("Prefer" to "return=minimal")) }
    private fun restUrl(path: String) = "$baseUrl/rest/v1/$path"

    private fun firstObject(response: String): JSONObject {
        val array = JSONArray(response)
        if (array.length() == 0) throw ApiException("Il server non ha restituito il record salvato")
        return array.getJSONObject(0)
    }

    private suspend fun authorizedRawRequest(method: String, url: String, body: RequestBody? = null, headers: Map<String, String> = emptyMap()): String {
        var current = validSession()
        return try {
            withContext(Dispatchers.IO) { rawRequest(method, url, body, current.accessToken, headers) }
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
            current = refreshSession()
            withContext(Dispatchers.IO) { rawRequest(method, url, body, current.accessToken, headers) }
        }
    }

    private suspend fun validSession(): Session {
        val current = requireSession()
        return if (current.expiresAtEpochSeconds <= nowSeconds() + 60) refreshSession() else current
    }

    private fun requireSession() = session ?: throw AuthenticationExpiredException("Accedi di nuovo")

    private fun rawRequest(method: String, url: String, body: RequestBody?, token: String?, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(url)
            .header("apikey", apiKey)
            .header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        headers.forEach { (name, value) -> builder.header(name, value) }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> if (body == null) builder.delete() else builder.delete(body)
            else -> error("Metodo HTTP non supportato")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error_description") }
                }.getOrNull().orEmpty().ifBlank { "Errore server ${response.code}" }
                throw ApiException(message, response.code)
            }
            return text
        }
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.string(key: String): String = if (isNull(key)) "" else optString(key, "")
private fun JSONObject.number(key: String): Double = if (isNull(key)) 0.0 else optDouble(key, 0.0).takeIf { it.isFinite() } ?: 0.0
private fun JSONObject.nullableString(key: String): String? = string(key).takeIf { it.isNotBlank() }
private fun JSONObject.putNullable(key: String, value: String?): JSONObject = put(key, value?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
private fun JSONObject.toCategory() = Category(string("id"), string("nome"), string("descrizione"), optInt("sort_order"))
private fun JSONObject.toSupplier() = Supplier(
    string("id"), string("nome"), string("referente"), string("telefono"), string("email"),
    string("sito_web"), string("link_catalogo"), string("indirizzo"), string("note")
)

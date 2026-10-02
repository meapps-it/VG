package it.meapps.gestionale

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: AppViewModel
    private lateinit var backend: TestBackend

    @Suppress("UNCHECKED_CAST")
    private fun state(name: String, value: Any?) {
        val field = vm.javaClass.getDeclaredField(name + "\$delegate").apply { isAccessible = true }
        (field.get(vm) as MutableState<Any?>).value = value
    }

    @Before
    fun prepare() {
        compose.waitForIdle()
        compose.runOnIdle {
            val lazy =
                MainActivity::class
                    .java
                    .getDeclaredField("viewModel\$delegate")
                    .apply { isAccessible = true }
                    .get(compose.activity) as Lazy<*>
            vm = lazy.value as AppViewModel
            backend = TestBackend()
            val api = vm.javaClass.getDeclaredField("api").apply { isAccessible = true }.get(vm)
            val session =
                Session("test", "test", Long.MAX_VALUE, "isolated-test-user", "qa@example.invalid")
            api.javaClass
                .getDeclaredField("client")
                .apply { isAccessible = true }
                .set(api, OkHttpClient.Builder().addInterceptor(backend).build())
            api.javaClass
                .getDeclaredField("session")
                .apply { isAccessible = true }
                .set(api, session)
            vm.signedUrls["fixture.jpg"] =
                File("src/test/assets/catalog-photo.jpg").absoluteFile.toURI().toString()
            vm.updateThemeMode(AppThemeMode.LIGHT)
            vm.updateFontScale(1f)
            vm.updateGridView(true)
            state("checkingAuth", false)
            state("session", session)
            state("showTutorial", false)
            vm.loadAll()
        }
        loaded()
    }

    private fun loaded() {
        compose.waitUntil(20000) {
            compose.waitForIdle()
            !vm.loading && !vm.saving && vm.products.isNotEmpty()
        }
        compose.waitForIdle()
        assertNull(vm.errorMessage)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.waitUntil(10000) {
            compose.waitForIdle()
            compose
                .onAllNodesWithContentDescription("Caricamento foto")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            val dir =
                File("build/reports/ui-screenshots/${BuildConfig.VERSION_NAME}").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
    }

    @Test
    fun mainNavigationAndDetails() {
        compose.onAllNodesWithText("Gestionale").onFirst().assertIsDisplayed()
        screenshot("dashboard")
        for ((tab, name) in
            listOf(
                MainTab.ARTICLES to "articoli",
                MainTab.CLIENTS to "clienti",
                MainTab.ORDERS to "ordini",
            )) {
            compose.onNodeWithTag("navigation-${tab.name}").performClick()
            compose.waitForIdle()
            assertEquals(tab, vm.selectedTab)
            screenshot(name)
        }
        compose.runOnIdle {
            vm.selectTab(MainTab.ARTICLES)
            vm.updateGridView(false)
        }
        compose.waitForIdle()
        screenshot("articoli-elenco")
        compose.runOnIdle { vm.openProductDetail(vm.products.first()) }
        compose.waitForIdle()
        screenshot("dettaglio-articolo")
        compose.onNodeWithText("Scarpa professionale").assertExists()
        compose.runOnIdle {
            vm.closeDetail()
            vm.openCustomerDetail(vm.customers.first())
        }
        compose.waitForIdle()
        screenshot("dettaglio-cliente")
        compose.runOnIdle {
            vm.closeDetail()
            vm.openOrderDetail(vm.orders.first())
        }
        compose.waitForIdle()
        screenshot("dettaglio-ordine")
    }

    @Test
    fun createEditReloadPreservesRecordsAndPhotos() {
        compose.runOnIdle {
            vm.openCustomer()
            vm.saveCustomer()
        }
        compose.waitForIdle()
        assertNotNull(vm.errorMessage)
        compose.runOnIdle {
            vm.clearMessages()
            vm.updateCustomerDraft(
                CustomerDraft(firstName = "Cliente test", phone = "061234567", city = "Roma")
            )
            vm.saveCustomer()
        }
        loaded()
        assertEquals(2, vm.customers.size)
        val customer = vm.customers.first { it.firstName == "Cliente test" }
        compose.runOnIdle {
            vm.openCustomer(customer)
            vm.updateCustomerDraft(vm.customerDraft.copy(city = "Milano"))
            vm.saveCustomer()
        }
        loaded()
        assertEquals("Milano", vm.customers.first { it.id == customer.id }.city)
        compose.runOnIdle {
            vm.openProduct(vm.products.first())
            vm.updateProductDraft(vm.productDraft.copy(name = "Scarpa aggiornata"))
            vm.saveProduct()
        }
        loaded()
        assertEquals(1, vm.products.first().photos.size)
        compose.runOnIdle {
            vm.openOrder(vm.products.first())
            vm.updateOrderDraft(vm.orderDraft.copy(customerId = customer.id, quantity = "3"))
            vm.saveOrder()
        }
        loaded()
        assertEquals(7, vm.orders.size)
        val order = vm.orders.first { it.customerId == customer.id }
        assertEquals(294.0, order.total, .001)
        compose.runOnIdle {
            vm.editOrder(order)
            vm.updateOrderDraft(
                vm.orderDraft.copy(status = "spedito", paid = true, amountPaid = "294")
            )
            vm.saveOrder()
        }
        loaded()
        compose.runOnIdle { vm.loadAll() }
        loaded()
        assertEquals(2, vm.products.size)
        assertEquals(2, vm.customers.size)
        assertEquals(7, vm.orders.size)
        assertEquals("spedito", vm.orders.first { it.id == order.id }.status)
        assertTrue(vm.orders.first { it.id == order.id }.paid)
        assertEquals(1, vm.products.first().photos.size)
        assertTrue(
            backend.requests.all {
                it.startsWith("GET ") || it.startsWith("POST ") || it.startsWith("PATCH ")
            }
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-w320dp-h640dp-mdpi")
    fun smallScreenWithLargeText() {
        compose.runOnIdle { vm.updateFontScale(1.3f) }
        compose.waitForIdle()
        screenshot("320-dashboard")
        compose.runOnIdle { vm.selectTab(MainTab.ARTICLES) }
        compose.waitForIdle()
        screenshot("320-articoli")
        compose.runOnIdle { vm.openCustomer() }
        compose.waitForIdle()
        screenshot("320-form-cliente")
        compose.onNodeWithText(compose.activity.getString(R.string.name)).assertExists()
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-w360dp-h740dp-mdpi")
    fun mediumScreenDarkTheme() {
        compose.runOnIdle { vm.updateThemeMode(AppThemeMode.DARK) }
        compose.waitForIdle()
        screenshot("360-dashboard-dark")
        compose.runOnIdle { vm.selectTab(MainTab.ORDERS) }
        compose.waitForIdle()
        screenshot("360-ordini-dark")
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-w892dp-h412dp-land-mdpi")
    fun landscapeAndEditors() {
        compose.runOnIdle { vm.selectTab(MainTab.ARTICLES) }
        compose.waitForIdle()
        screenshot("landscape-articoli")
        compose.runOnIdle { vm.openProduct(vm.products.first()) }
        compose.waitForIdle()
        screenshot("form-articolo")
        compose.runOnIdle {
            vm.navigateBack()
            vm.openOrder(vm.products.first())
        }
        compose.waitForIdle()
        screenshot("form-ordine")
    }
}

/** Isolated memory backend: every request stops here, never reaches Supabase. */
private class TestBackend : Interceptor {
    val requests = mutableListOf<String>()
    private val tables = mutableMapOf<String, MutableList<JSONObject>>()

    init {
        fun row(s: String) = JSONObject(s)
        tables["clienti"] =
            mutableListOf(
                row(
                    """{"id":"c1","nome":"Cliente esistente","telefono":"061234567","email":"test@example.invalid","citta":"Roma","provincia":"RM","paese":"Italia"}"""
                )
            )
        tables["prodotti"] =
            mutableListOf(
                row(
                    """{"id":"p1","nome":"Scarpa professionale","codice":"SKU-120","prezzo_acquisto":60,"costi_aggiuntivi":5,"prezzo_vendita":120,"giacenza":12,"disponibile":true,"in_promozione":true,"prezzo_promozionale":98,"fornitore_id":"s1"}"""
                ),
                row(
                    """{"id":"p2","nome":"Articolo senza immagine","codice":"SKU-121","prezzo_vendita":45,"giacenza":0,"disponibile":false}"""
                ),
            )
        tables["fornitori"] = mutableListOf(row("""{"id":"s1","nome":"Fornitore esistente"}"""))
        tables["marche"] = mutableListOf()
        tables["categorie"] = mutableListOf()
        tables["prodotti_foto"] =
            mutableListOf(
                row("""{"id":"photo1","prodotto_id":"p1","path":"fixture.jpg","ordine":0}""")
            )
        tables["ordini"] =
            (0..5)
                .map { i ->
                    JSONObject()
                        .put("id", "o$i")
                        .put("numero_ordine", "ORD-${120+i}")
                        .put("cliente_id", "c1")
                        .put(
                            "data_ordine",
                            java.time.YearMonth.now().minusMonths(i.toLong()).atDay(12).toString(),
                        )
                        .put("stato", listOf("in_lavorazione", "spedito", "consegnato")[i % 3])
                        .put("totale", 120.0 + i * 25)
                        .put("guadagno", 35.0 + i * 8)
                        .put("pagato", i % 2 == 0)
                        .put("totale_pagato", if (i % 2 == 0) 120.0 + i * 25 else 0.0)
                        .put("stato_pagamento", if (i % 2 == 0) "pagato" else "da_pagare")
                }
                .toMutableList()
        tables["righe_ordine"] =
            (0..5)
                .map { i ->
                    row("""{"id":"r$i","ordine_id":"o$i","prodotto_id":"p1","quantita":1}""")
                }
                .toMutableList()
    }

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        requests += req.method + " " + req.url.encodedPath
        val table = req.url.pathSegments.last()
        val list = tables.getOrPut(table) { mutableListOf() }
        val id = req.url.queryParameter("id")?.removePrefix("eq.")
        val result =
            when (req.method) {
                "POST" -> {
                    val buffer = Buffer()
                    req.body?.writeTo(buffer)
                    val value = JSONObject(buffer.readUtf8()).put("id", "test-${list.size+1}")
                    list += value
                    JSONArray().put(value)
                }
                "PATCH" -> {
                    val buffer = Buffer()
                    req.body?.writeTo(buffer)
                    val update = JSONObject(buffer.readUtf8())
                    val value = list.first { it.optString("id") == id }
                    update.keys().forEach { value.put(it, update.get(it)) }
                    JSONArray().put(value)
                }
                else -> JSONArray(list.filter { id == null || it.optString("id") == id })
            }
        val body =
            if (req.url.encodedPath.contains("/storage/"))
                JSONObject()
                    .put(
                        "signedURL",
                        File("src/test/assets/catalog-photo.jpg").absoluteFile.toURI().toString(),
                    )
                    .toString()
            else result.toString()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

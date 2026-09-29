package it.meapps.spesascan

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val client = OkHttpClient()
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("spesascan", MODE_PRIVATE) }

    private lateinit var codeField: EditText
    private lateinit var nameField: EditText
    private lateinit var brandField: EditText
    private lateinit var quantityField: EditText
    private lateinit var descriptionField: EditText
    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var archiveText: TextView

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents.orEmpty()
        if (code.isNotBlank()) {
            codeField.setText(code)
            lookup(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "SpesaScan"
        setContentView(buildUi())
        refreshArchive()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "SpesaScan"
            textSize = 30f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Scansiona un prodotto e compila automaticamente i dati con Open Food Facts."
            textSize = 15f
            setPadding(0, dp(6), 0, dp(18))
        })

        val scanButton = Button(this).apply {
            text = "Scansiona codice a barre"
            setOnClickListener {
                scanner.launch(
                    ScanOptions()
                        .setPrompt("Inquadra il codice a barre")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            }
        }
        root.addView(scanButton, full())

        codeField = field("Codice EAN / UPC")
        root.addView(codeField, full())

        root.addView(Button(this).apply {
            text = "Cerca prodotto"
            setOnClickListener { lookup(codeField.text.toString().trim()) }
        }, full())

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(statusText, full())

        imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            minimumHeight = dp(180)
            visibility = ImageView.GONE
        }
        root.addView(imageView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply {
            bottomMargin = dp(12)
        })

        nameField = field("Nome prodotto")
        brandField = field("Marca")
        quantityField = field("Formato / quantità")
        descriptionField = field("Descrizione").apply {
            minLines = 3
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        root.addView(nameField, full())
        root.addView(brandField, full())
        root.addView(quantityField, full())
        root.addView(descriptionField, full())

        root.addView(Button(this).apply {
            text = "Salva nel mio archivio"
            setOnClickListener { saveCurrent() }
        }, full())

        root.addView(Button(this).apply {
            text = "Pulisci"
            setOnClickListener { clearFields() }
        }, full())

        root.addView(TextView(this).apply {
            text = "Archivio recente"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(8))
        })

        archiveText = TextView(this).apply {
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFF3F6FA.toInt())
        }
        root.addView(archiveText, full())

        return scroll
    }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun full() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(10) }

    private fun lookup(code: String) {
        if (code.isBlank()) {
            toast("Scansiona o inserisci un codice")
            return
        }

        findLocal(code)?.let {
            fill(it, "Trovato nel tuo archivio")
            return
        }

        statusText.text = "Ricerca in Open Food Facts…"
        imageView.visibility = ImageView.GONE

        io.execute {
            try {
                val url = "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=code,product_name,product_name_it,brands,quantity,generic_name,generic_name_it,categories,image_front_url"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SpesaScan/0.1.0 (Android)")
                    .get()
                    .build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Errore Open Food Facts ${response.code}")
                    response.body?.string().orEmpty()
                }
                val json = JSONObject(body)
                if (json.optInt("status") != 1) {
                    runOnUiThread { statusText.text = "Prodotto non trovato. Puoi compilarlo manualmente e salvarlo." }
                    return@execute
                }

                val p = json.optJSONObject("product") ?: JSONObject()
                val result = JSONObject().apply {
                    put("code", code)
                    put("name", p.optString("product_name_it").ifBlank { p.optString("product_name") })
                    put("brand", p.optString("brands"))
                    put("quantity", p.optString("quantity"))
                    put("description", p.optString("generic_name_it").ifBlank {
                        p.optString("generic_name").ifBlank { p.optString("categories") }
                    })
                    put("image", p.optString("image_front_url"))
                }
                runOnUiThread { fill(result, "Trovato su Open Food Facts") }
            } catch (t: Throwable) {
                runOnUiThread { statusText.text = "Ricerca non riuscita: ${t.message ?: "errore sconosciuto"}" }
            }
        }
    }

    private fun fill(product: JSONObject, message: String) {
        codeField.setText(product.optString("code"))
        nameField.setText(product.optString("name"))
        brandField.setText(product.optString("brand"))
        quantityField.setText(product.optString("quantity"))
        descriptionField.setText(product.optString("description"))
        statusText.text = message

        val imageUrl = product.optString("image")
        if (imageUrl.isBlank()) {
            imageView.visibility = ImageView.GONE
        } else {
            imageView.visibility = ImageView.VISIBLE
            io.execute {
                runCatching {
                    val req = Request.Builder().url(imageUrl).header("User-Agent", "SpesaScan/0.1.0 (Android)").build()
                    client.newCall(req).execute().use { response ->
                        val bytes = response.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }.getOrNull()?.let { bitmap ->
                    runOnUiThread { imageView.setImageBitmap(bitmap) }
                }
            }
        }
    }

    private fun saveCurrent() {
        val code = codeField.text.toString().trim()
        val name = nameField.text.toString().trim()
        if (code.isBlank() || name.isBlank()) {
            toast("Codice e nome sono obbligatori")
            return
        }

        val item = JSONObject().apply {
            put("code", code)
            put("name", name)
            put("brand", brandField.text.toString().trim())
            put("quantity", quantityField.text.toString().trim())
            put("description", descriptionField.text.toString().trim())
            put("saved_at", System.currentTimeMillis())
        }

        val current = loadArchive().filterNot { it.optString("code") == code }.toMutableList()
        current.add(0, item)
        val array = JSONArray()
        current.take(100).forEach(array::put)
        prefs.edit().putString("archive", array.toString()).apply()

        statusText.text = "Salvato nel tuo archivio"
        refreshArchive()
    }

    private fun findLocal(code: String): JSONObject? =
        loadArchive().firstOrNull { it.optString("code") == code }

    private fun loadArchive(): List<JSONObject> {
        val raw = prefs.getString("archive", "[]").orEmpty()
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun refreshArchive() {
        val rows = loadArchive().take(12)
        archiveText.text = if (rows.isEmpty()) {
            "Nessun prodotto salvato."
        } else {
            rows.joinToString("\n\n") {
                buildString {
                    append(it.optString("name").ifBlank { "Prodotto" })
                    val brand = it.optString("brand")
                    if (brand.isNotBlank()) append(" · ").append(brand)
                    append("\n").append(it.optString("code"))
                    val qty = it.optString("quantity")
                    if (qty.isNotBlank()) append(" · ").append(qty)
                }
            }
        }
    }

    private fun clearFields() {
        codeField.text.clear()
        nameField.text.clear()
        brandField.text.clear()
        quantityField.text.clear()
        descriptionField.text.clear()
        imageView.setImageDrawable(null)
        imageView.visibility = ImageView.GONE
        statusText.text = ""
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}

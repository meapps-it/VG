package it.meapps.gestionale

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.Build
import android.util.Base64
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** VG-only preferences. WebView textZoom is the sole web text scaler. */
internal object VgTextScale {
    fun normalize(percent: Int): Int = ((percent.coerceIn(75, 150) - 75 + 2) / 5) * 5 + 75
    fun zoom(percent: Int, systemScale: Float): Int =
        (normalize(percent) * systemScale).roundToInt().coerceAtLeast(1)
}

internal class VgAppearance(private val context: Context) {
    private val preferences = context.getSharedPreferences("vg_android_settings", Context.MODE_PRIVATE)
    var percent: Int
        get() = VgTextScale.normalize(preferences.getInt("font_percent", 100))
        set(value) { preferences.edit().putInt("font_percent", VgTextScale.normalize(value)).apply() }

    fun zoom(): Int = VgTextScale.zoom(percent, context.resources.configuration.fontScale)

    fun dialogContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.fontScale *= percent / 100f
        return android.view.ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_Gestionale)
    }

    // Public shaping APIs return the actual font selected by the native text renderer.
    // No hidden Typeface reflection, Samsung-private filesystem paths or font downloads.
    fun fontState(): JSONObject {
        val result = JSONObject().put("faces", JSONArray()).put("sources", JSONArray())
        if (Build.VERSION.SDK_INT < 31) return result.put("fontStatus",
            "Android precedente alla versione 12: il font personalizzato del telefono non può essere trasferito automaticamente alla WebView.")
        return runCatching {
            val nativeTypeface = TextView(context).typeface
            val faces = JSONArray()
            val sources = JSONArray()
            val sourceIds = linkedMapOf<String,Int>()
            var totalBytes = 0
            for (style in listOf(Typeface.NORMAL, Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD_ITALIC)) {
                val paint = android.text.TextPaint().apply {
                    typeface = Typeface.create(nativeTypeface, style)
                    textSize = 16f
                }
                val glyphs = TextRunShaper.shapeTextRun("Ag", 0, 2, 0, 2, 0f, 0f, false, paint)
                check(glyphs.glyphCount() > 0) { "Nessun carattere nativo disponibile" }
                val font = glyphs.getFont(0)
                // Chromium support for nonzero faces in TTC collections is inconsistent.
                check(font.ttcIndex == 0) { "Il font Android usa una raccolta non supportata dalla WebView" }
                val buffer = font.buffer.duplicate().apply { clear() }
                check(buffer.remaining() in 1..8_000_000) { "Dimensione font non supportata: ${buffer.remaining()} byte" }
                val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                val hash = Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes), Base64.NO_WRAP)
                val sourceId = sourceIds.getOrPut(hash) {
                    totalBytes += bytes.size
                    check(totalBytes <= 16_000_000) { "Dimensione totale font non supportata" }
                    sources.put(Base64.encodeToString(bytes, Base64.NO_WRAP))
                    sources.length() - 1
                }
                val axes = font.axes?.joinToString(",") { "'${it.tag}' ${it.styleValue}" }.orEmpty()
                faces.put(JSONObject().put("source", sourceId)
                    .put("weight", if (style and Typeface.BOLD != 0) "700" else "400")
                    .put("style", if (style and Typeface.ITALIC != 0) "italic" else "normal")
                    .put("variationSettings", axes))
            }
            result.put("sources", sources).put("faces", faces).put("fontStatus", "Carattere del testo Android disponibile; caricamento nella WebView…")
        }.getOrElse {
            android.util.Log.w("VgAppearance", "Native font transfer unavailable", it)
            result.put("faces", JSONArray()).put("fontStatus",
                "Il dispositivo non espone un font trasferibile: la WebView usa il proprio carattere di sistema. Il font Samsung personalizzato non è garantito.")
        }
    }

    fun state(): JSONObject = JSONObject().put("percent", percent)
        .put("systemPercent", (context.resources.configuration.fontScale * 100).roundToInt())
}

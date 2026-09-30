package it.meapps.spesascan

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAt: Long
)

class AuthController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("spesascan_auth", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    var session by mutableStateOf(loadSession())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun googleAuthUrl(): String {
        val redirect = Uri.encode("spesascan://login-callback")
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$redirect"
    }

    suspend fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            message = "Inserisci email e password"
            return
        }
        runBusy {
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
                .toRequestBody(jsonType)
            val response = request("POST", "$baseUrl/auth/v1/token?grant_type=password", body)
            saveFromJson(JSONObject(response), email.trim())
        }
    }

    suspend fun signUp(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            message = "Inserisci una email valida e una password di almeno 6 caratteri"
            return
        }
        runBusy {
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
                .toRequestBody(jsonType)
            val response = request("POST", "$baseUrl/auth/v1/signup", body)
            val json = JSONObject(response)
            if (json.optString("access_token").isNotBlank()) {
                saveFromJson(json, email.trim())
            } else {
                message = "Account creato. Controlla la tua email se è richiesta la conferma."
            }
        }
    }

    suspend fun resetPassword(email: String) {
        if (email.isBlank()) {
            message = "Inserisci prima la tua email"
            return
        }
        runBusy {
            val body = JSONObject().put("email", email.trim()).toString().toRequestBody(jsonType)
            request("POST", "$baseUrl/auth/v1/recover", body)
            message = "Email di recupero inviata"
        }
    }

    suspend fun completeGoogleLogin(callback: Uri) {
        runBusy {
            val params = mutableMapOf<String, String>()
            fun collect(raw: String?) {
                raw.orEmpty().split('&').forEach { part ->
                    if (part.isBlank()) return@forEach
                    val pieces = part.split('=', limit = 2)
                    val key = Uri.decode(pieces.getOrElse(0) { "" })
                    val value = Uri.decode(pieces.getOrElse(1) { "" })
                    if (key.isNotBlank()) params[key] = value
                }
            }
            collect(callback.fragment)
            collect(callback.query)

            params["error_description"]?.takeIf { it.isNotBlank() }?.let { error(it) }
            params["error"]?.takeIf { it.isNotBlank() }?.let { error(it) }

            val access = params["access_token"].orEmpty()
            val refresh = params["refresh_token"].orEmpty()
            if (access.isBlank() || refresh.isBlank()) error("Accesso Google non completato")

            val userJson = JSONObject(request("GET", "$baseUrl/auth/v1/user", token = access))
            val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
            val newSession = AuthSession(
                accessToken = access,
                refreshToken = refresh,
                userId = userJson.optString("id"),
                email = userJson.optString("email"),
                expiresAt = System.currentTimeMillis() / 1000 + expiresIn
            )
            persist(newSession)
            session = newSession
            message = "Accesso effettuato"
        }
    }

    suspend fun signOut() {
        val current = session
        busy = true
        withContext(Dispatchers.IO) {
            runCatching {
                request("POST", "$baseUrl/auth/v1/logout", "{}".toRequestBody(jsonType), current?.accessToken)
            }
        }
        prefs.edit().clear().apply()
        session = null
        busy = false
        message = null
    }

    fun clearMessage() {
        message = null
    }

    private suspend fun runBusy(block: suspend () -> Unit) {
        busy = true
        message = null
        try {
            block()
        } catch (t: Throwable) {
            message = t.message ?: "Operazione non riuscita"
        } finally {
            busy = false
        }
    }

    private fun saveFromJson(json: JSONObject, fallbackEmail: String) {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val user = json.optJSONObject("user")
        if (access.isBlank() || refresh.isBlank() || user == null) {
            error("Risposta di autenticazione incompleta")
        }
        val newSession = AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = user.optString("id"),
            email = user.optString("email").ifBlank { fallbackEmail },
            expiresAt = json.optLong("expires_at").takeIf { it > 0 }
                ?: System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)
        )
        persist(newSession)
        session = newSession
        message = "Accesso effettuato"
    }

    private fun persist(value: AuthSession) {
        prefs.edit()
            .putString("access", value.accessToken)
            .putString("refresh", value.refreshToken)
            .putString("user_id", value.userId)
            .putString("email", value.email)
            .putLong("expires_at", value.expiresAt)
            .apply()
    }

    private fun loadSession(): AuthSession? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = prefs.getString("email", "").orEmpty(),
            expiresAt = prefs.getLong("expires_at", 0)
        )
    }

    private suspend fun request(
        method: String,
        url: String,
        body: okhttp3.RequestBody? = null,
        token: String? = null
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("apikey", apiKey)
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            else -> error("Metodo non supportato")
        }
        client.newCall(builder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val readable = runCatching {
                    val json = JSONObject(responseText)
                    json.optString("msg")
                        .ifBlank { json.optString("message") }
                        .ifBlank { json.optString("error_description") }
                }.getOrNull().orEmpty()
                error(readable.ifBlank { "Errore server " + response.code })
            }
            responseText
        }
    }
}

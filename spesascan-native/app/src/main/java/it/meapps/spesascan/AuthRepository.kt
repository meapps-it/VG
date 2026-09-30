package it.meapps.spesascan

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAt: Long
)

class AuthRepository(private val context: Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val prefs = context.getSharedPreferences("spesascan_auth", Context.MODE_PRIVATE)

    var session: UserSession? = load()
        private set

    fun googleAuthUrl(): String {
        val redirect = Uri.encode("spesascan://login-callback")
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$redirect"
    }

    suspend fun signIn(email: String, password: String): UserSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val text = request("POST", "$baseUrl/auth/v1/token?grant_type=password", body.toString(), null)
        parseSession(JSONObject(text), email.trim())
    }

    suspend fun signUp(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val text = request("POST", "$baseUrl/auth/v1/signup", body.toString(), null)
        val obj = JSONObject(text)
        if (obj.optString("access_token").isNotBlank()) parseSession(obj, email.trim())
        obj.has("user") || obj.has("id")
    }

    suspend fun completeGoogleOAuth(uri: Uri): UserSession = withContext(Dispatchers.IO) {
        val params = linkedMapOf<String, String>()
        fun collect(raw: String?) {
            raw.orEmpty().split('&').forEach { part ->
                if (part.isBlank()) return@forEach
                val p = part.split('=', limit = 2)
                val key = Uri.decode(p.getOrElse(0) { "" })
                val value = Uri.decode(p.getOrElse(1) { "" })
                if (key.isNotBlank()) params[key] = value
            }
        }
        collect(uri.fragment)
        collect(uri.query)

        params["error_description"]?.takeIf { it.isNotBlank() }?.let { error(it) }
        params["error"]?.takeIf { it.isNotBlank() }?.let { error(it) }

        val access = params["access_token"].orEmpty()
        val refresh = params["refresh_token"].orEmpty()
        if (access.isBlank() || refresh.isBlank()) error("Accesso Google non completato")

        val user = JSONObject(request("GET", "$baseUrl/auth/v1/user", null, access))
        val payload = JSONObject()
            .put("access_token", access)
            .put("refresh_token", refresh)
            .put("expires_in", params["expires_in"]?.toLongOrNull() ?: 3600L)
            .put("user", user)

        parseSession(payload, user.optString("email"))
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        session?.accessToken?.let { token ->
            runCatching { request("POST", "$baseUrl/auth/v1/logout", "{}", token) }
        }
        session = null
        prefs.edit().clear().apply()
    }

    private fun parseSession(obj: JSONObject, fallbackEmail: String): UserSession {
        val user = obj.optJSONObject("user")
        val parsed = UserSession(
            accessToken = obj.getString("access_token"),
            refreshToken = obj.getString("refresh_token"),
            userId = user?.optString("id").orEmpty(),
            email = user?.optString("email").orEmpty().ifBlank { fallbackEmail },
            expiresAt = System.currentTimeMillis() / 1000 + obj.optLong("expires_in", 3600)
        )
        if (parsed.userId.isBlank()) error("Sessione incompleta")
        session = parsed
        prefs.edit()
            .putString("access", parsed.accessToken)
            .putString("refresh", parsed.refreshToken)
            .putString("user_id", parsed.userId)
            .putString("email", parsed.email)
            .putLong("expires_at", parsed.expiresAt)
            .apply()
        return parsed
    }

    private fun load(): UserSession? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        return UserSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = prefs.getString("email", "").orEmpty(),
            expiresAt = prefs.getLong("expires_at", 0)
        )
    }

    private fun request(method: String, url: String, body: String?, token: String?): String {
        val builder = Request.Builder().url(url)
            .header("apikey", apiKey)
            .header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: "{}").toRequestBody(json))
        }

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("msg")
                        .ifBlank { JSONObject(text).optString("message") }
                        .ifBlank { JSONObject(text).optString("error_description") }
                }.getOrNull().orEmpty().ifBlank { "Errore autenticazione ${response.code}" }
                throw IOException(message)
            }
            return text
        }
    }
}

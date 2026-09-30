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

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String
)

class AuthClient(context: Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val prefs = context.getSharedPreferences("spesascan_auth", Context.MODE_PRIVATE)

    var session: AuthSession? = load()
        private set

    fun googleAuthUrl(): String {
        val redirect = Uri.encode("spesascan://login-callback")
        return "$baseUrl/auth/v1/authorize?provider=google&redirect_to=$redirect"
    }

    suspend fun signIn(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
            .toRequestBody(json)
        val body = request("POST", "$baseUrl/auth/v1/token?grant_type=password", payload)
        parseSession(JSONObject(body), email.trim())
    }

    suspend fun signUp(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
            .toRequestBody(json)
        val body = request("POST", "$baseUrl/auth/v1/signup", payload)
        val obj = JSONObject(body)
        if (obj.optString("access_token").isNotBlank()) parseSession(obj, email.trim())
        obj.has("user") || obj.has("id")
    }

    suspend fun resetPassword(email: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .toString()
            .toRequestBody(json)
        request("POST", "$baseUrl/auth/v1/recover", payload)
    }

    suspend fun completeGoogleOAuth(callback: Uri): AuthSession = withContext(Dispatchers.IO) {
        val params = linkedMapOf<String, String>()
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

        val user = JSONObject(request("GET", "$baseUrl/auth/v1/user", token = access))
        val result = AuthSession(
            accessToken = access,
            refreshToken = refresh,
            userId = user.optString("id"),
            email = user.optString("email")
        )
        save(result)
        result
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        session?.accessToken?.let { token ->
            runCatching { request("POST", "$baseUrl/auth/v1/logout", "{}".toRequestBody(json), token) }
        }
        session = null
        prefs.edit().clear().apply()
    }

    private fun parseSession(obj: JSONObject, fallbackEmail: String): AuthSession {
        val user = obj.optJSONObject("user")
        val result = AuthSession(
            accessToken = obj.optString("access_token"),
            refreshToken = obj.optString("refresh_token"),
            userId = user?.optString("id").orEmpty(),
            email = user?.optString("email").orEmpty().ifBlank { fallbackEmail }
        )
        if (result.accessToken.isBlank() || result.refreshToken.isBlank() || result.userId.isBlank()) {
            error("Risposta di autenticazione incompleta")
        }
        save(result)
        return result
    }

    private fun save(value: AuthSession) {
        session = value
        prefs.edit()
            .putString("access", value.accessToken)
            .putString("refresh", value.refreshToken)
            .putString("user_id", value.userId)
            .putString("email", value.email)
            .apply()
    }

    private fun load(): AuthSession? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        return AuthSession(access, refresh, userId, prefs.getString("email", "").orEmpty())
    }

    private fun request(
        method: String,
        url: String,
        body: okhttp3.RequestBody? = null,
        token: String? = null
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("apikey", apiKey)
            .header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(null))
            else -> error("Metodo non supportato")
        }

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("message").ifBlank {
                        JSONObject(text).optString("error_description")
                    }
                }.getOrNull().orEmpty().ifBlank { "Errore server ${response.code}" }
                error(message)
            }
            return text
        }
    }
}

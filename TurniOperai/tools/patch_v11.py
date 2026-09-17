from pathlib import Path

root = Path('TurniOperai')
main = root / 'app/src/main/java/com/meapps/turnioperai/MainActivityV6.kt'
build = root / 'app/build.gradle.kts'
manifest = root / 'app/src/main/AndroidManifest.xml'
auth = root / 'app/src/main/java/com/meapps/turnioperai/AuthSupportV11.kt'

text = main.read_text()

if 'import android.content.Intent' not in text:
    text = text.replace('import android.content.Context\n', 'import android.content.Context\nimport android.content.Intent\n')
if 'import java.time.LocalDateTime' not in text:
    text = text.replace('import java.time.LocalDate\n', 'import java.time.LocalDate\nimport java.time.LocalDateTime\n')
if 'import kotlinx.coroutines.delay' not in text:
    text = text.replace('import org.json.JSONObject\n', 'import org.json.JSONObject\nimport kotlinx.coroutines.delay\n')

old_activity = '''class MainActivityV6 : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContent { TurniOperaiV6(this) }\n    }\n}\n'''
new_activity = '''class MainActivityV6 : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContent { AuthGateV11(this) { TurniOperaiV6(this) } }\n    }\n\n    override fun onNewIntent(intent: Intent) {\n        super.onNewIntent(intent)\n        setIntent(intent)\n        recreate()\n    }\n}\n'''
if old_activity in text:
    text = text.replace(old_activity, new_activity)

clock_anchor = '''    val rolProfile = RolProfileV9(rolMonthlyMinutes, rolOpeningMinutes, rolStartMonth)\n\n'''
clock_code = '''    val rolProfile = RolProfileV9(rolMonthlyMinutes, rolOpeningMinutes, rolStartMonth)\n\n    var liveNow by remember { mutableStateOf(LocalDateTime.now()) }\n    LaunchedEffect(Unit) {\n        while (true) {\n            liveNow = LocalDateTime.now()\n            delay(1000)\n        }\n    }\n    val liveDateTime = remember(liveNow) {\n        liveNow.format(DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy • HH:mm:ss", Locale.ITALIAN))\n    }\n\n'''
if 'val liveDateTime = remember(liveNow)' not in text and clock_anchor in text:
    text = text.replace(clock_anchor, clock_code)

text = text.replace(
    'Text("Calendario e cicli di turnazione", color = Color.White.copy(alpha = .80f), style = MaterialTheme.typography.bodySmall)',
    'Text(liveDateTime, color = Color.White.copy(alpha = .80f), style = MaterialTheme.typography.bodySmall)'
)

settings_line = 'MenuItemV6("Impostazioni", Icons.Default.Settings) { navigateTo(3); menuOpen = false }'
account_line = 'MenuItemV6("Account", Icons.Default.AccountCircle) { openAccountV11(context); menuOpen = false }'
if account_line not in text and settings_line in text:
    text = text.replace(settings_line, settings_line + '\n                                    ' + account_line)

main.write_text(text)

b = build.read_text()
b = b.replace('versionCode = 10', 'versionCode = 11')
b = b.replace('versionName = "10.0"', 'versionName = "11.0"')
if 'kotlinx-coroutines-android' not in b:
    b = b.replace('implementation("androidx.compose.material:material-icons-extended")', 'implementation("androidx.compose.material:material-icons-extended")\n    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")')
build.write_text(b)

m = manifest.read_text()
if 'android.permission.INTERNET' not in m:
    m = m.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">', '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.INTERNET" />')
m = m.replace('<activity android:name=".MainActivityV6" android:exported="true">', '<activity android:name=".MainActivityV6" android:exported="true" android:launchMode="singleTask">')
if 'turnioperai' not in m:
    marker = '''            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>'''
    callback = marker + '''\n            <intent-filter>\n                <action android:name="android.intent.action.VIEW" />\n                <category android:name="android.intent.category.DEFAULT" />\n                <category android:name="android.intent.category.BROWSABLE" />\n                <data android:scheme="turnioperai" android:host="auth-callback" />\n            </intent-filter>'''
    m = m.replace(marker, callback)
manifest.write_text(m)

auth.write_text(r'''package com.meapps.turnioperai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val SUPABASE_URL_V11 = "https://qfjwtawsqfwmsmrrgqfi.supabase.co"
private const val SUPABASE_KEY_V11 = "sb_publishable_-EUVjwsk3txKk2Opqrc7Kw_xFNa9Hw1"
private const val AUTH_PREFS_V11 = "turni_operai_auth"

data class AuthSessionV11(
    val email: String,
    val accessToken: String,
    val refreshToken: String
)

data class AuthResultV11(
    val session: AuthSessionV11?,
    val message: String
)

private object SupabaseAuthV11 {
    private suspend fun post(path: String, body: JSONObject): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = URL("$SUPABASE_URL_V11$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("apikey", SUPABASE_KEY_V11)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(raw: String): String {
        return runCatching {
            val j = JSONObject(raw)
            j.optString("msg").ifBlank { j.optString("message") }.ifBlank { j.optString("error_description") }
        }.getOrNull().orEmpty().ifBlank { "Operazione non riuscita" }
    }

    suspend fun signIn(email: String, password: String): Result<AuthSessionV11> = runCatching {
        val (code, raw) = post("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password))
        if (code !in 200..299) error(errorMessage(raw))
        val j = JSONObject(raw)
        AuthSessionV11(
            j.optJSONObject("user")?.optString("email").orEmpty().ifBlank { email },
            j.getString("access_token"),
            j.getString("refresh_token")
        )
    }

    suspend fun signUp(email: String, password: String): Result<AuthResultV11> = runCatching {
        val (code, raw) = post("/auth/v1/signup", JSONObject().put("email", email).put("password", password))
        if (code !in 200..299) error(errorMessage(raw))
        val j = JSONObject(raw)
        val access = j.optString("access_token")
        if (access.isNotBlank()) {
            AuthResultV11(
                AuthSessionV11(
                    j.optJSONObject("user")?.optString("email").orEmpty().ifBlank { email },
                    access,
                    j.optString("refresh_token")
                ),
                "Account creato"
            )
        } else {
            AuthResultV11(null, "Registrazione completata. Controlla la tua email per confermare l'account.")
        }
    }

    suspend fun recover(email: String): Result<String> = runCatching {
        val (code, raw) = post("/auth/v1/recover", JSONObject().put("email", email))
        if (code !in 200..299) error(errorMessage(raw))
        "Email di recupero inviata"
    }
}

private fun saveSessionV11(context: Context, session: AuthSessionV11) {
    context.getSharedPreferences(AUTH_PREFS_V11, Context.MODE_PRIVATE).edit()
        .putString("email", session.email)
        .putString("access_token", session.accessToken)
        .putString("refresh_token", session.refreshToken)
        .putBoolean("skip_auth", false)
        .putBoolean("force_login", false)
        .apply()
}

private fun emailFromJwtV11(token: String): String {
    return runCatching {
        val payload = token.split('.')[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
        JSONObject(json).optString("email")
    }.getOrDefault("")
}

fun openAccountV11(context: Context) {
    context.getSharedPreferences(AUTH_PREFS_V11, Context.MODE_PRIVATE).edit()
        .putBoolean("force_login", true)
        .apply()
    (context as? Activity)?.recreate()
}

@Composable
fun AuthGateV11(activity: Activity, content: @Composable () -> Unit) {
    val prefs = remember { activity.getSharedPreferences(AUTH_PREFS_V11, Context.MODE_PRIVATE) }
    var forceLogin by remember { mutableStateOf(prefs.getBoolean("force_login", false)) }
    var skipped by remember { mutableStateOf(prefs.getBoolean("skip_auth", false)) }
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var accessToken by remember { mutableStateOf(prefs.getString("access_token", "") ?: "") }

    LaunchedEffect(activity.intent?.dataString) {
        val data = activity.intent?.data
        if (data?.scheme == "turnioperai" && data.host == "auth-callback") {
            val parts = data.fragment.orEmpty().split('&').mapNotNull {
                val idx = it.indexOf('=')
                if (idx > 0) it.substring(0, idx) to Uri.decode(it.substring(idx + 1)) else null
            }.toMap()
            val token = parts["access_token"].orEmpty()
            if (token.isNotBlank()) {
                val session = AuthSessionV11(
                    emailFromJwtV11(token),
                    token,
                    parts["refresh_token"].orEmpty()
                )
                saveSessionV11(activity, session)
                email = session.email
                accessToken = session.accessToken
                forceLogin = false
                skipped = false
                activity.intent?.data = null
            }
        }
    }

    if ((!forceLogin && skipped) || (!forceLogin && accessToken.isNotBlank())) {
        content()
    } else {
        AuthScreenV11(
            currentEmail = email,
            isSignedIn = accessToken.isNotBlank(),
            onSignedIn = { session ->
                saveSessionV11(activity, session)
                email = session.email
                accessToken = session.accessToken
                forceLogin = false
                skipped = false
            },
            onContinue = {
                prefs.edit().putBoolean("force_login", false).apply()
                forceLogin = false
            },
            onGuest = {
                prefs.edit().putBoolean("skip_auth", true).putBoolean("force_login", false).apply()
                skipped = true
                forceLogin = false
            },
            onLogout = {
                prefs.edit().clear().putBoolean("force_login", true).apply()
                email = ""
                accessToken = ""
                skipped = false
                forceLogin = true
            },
            onGoogle = {
                val redirect = URLEncoder.encode("turnioperai://auth-callback", "UTF-8")
                val url = "$SUPABASE_URL_V11/auth/v1/authorize?provider=google&redirect_to=$redirect"
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        )
    }
}

@Composable
private fun AuthScreenV11(
    currentEmail: String,
    isSignedIn: Boolean,
    onSignedIn: (AuthSessionV11) -> Unit,
    onContinue: () -> Unit,
    onGuest: () -> Unit,
    onLogout: () -> Unit,
    onGoogle: () -> Unit
) {
    var mode by remember { mutableStateOf("login") }
    var email by remember(currentEmail) { mutableStateOf(currentEmail) }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("Turni Operai", style = MaterialTheme.typography.headlineSmall)
                    Text("Account ME Apps", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))

                    if (isSignedIn) {
                        Text("Connesso come", style = MaterialTheme.typography.labelLarge)
                        Text(currentEmail.ifBlank { "Account Google" }, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continua nell'app") }
                        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Esci dall'account") }
                    } else {
                        OutlinedButton(onClick = onGoogle, modifier = Modifier.fillMaxWidth()) { Text("Continua con Google") }
                        Spacer(Modifier.height(14.dp))
                        Text("oppure", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trim() },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (message.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            enabled = !loading && email.isNotBlank() && password.length >= 6,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                loading = true
                                message = ""
                                scope.launch {
                                    if (mode == "login") {
                                        SupabaseAuthV11.signIn(email, password)
                                            .onSuccess(onSignedIn)
                                            .onFailure { message = it.message ?: "Accesso non riuscito" }
                                    } else {
                                        SupabaseAuthV11.signUp(email, password)
                                            .onSuccess { result ->
                                                result.session?.let(onSignedIn) ?: run { message = result.message }
                                            }
                                            .onFailure { message = it.message ?: "Registrazione non riuscita" }
                                    }
                                    loading = false
                                }
                            }
                        ) { Text(if (loading) "Attendi..." else if (mode == "login") "Accedi" else "Registrati") }

                        TextButton(onClick = { mode = if (mode == "login") "register" else "login" }) {
                            Text(if (mode == "login") "Non hai un account? Registrati" else "Hai già un account? Accedi")
                        }
                        TextButton(
                            enabled = email.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    loading = true
                                    SupabaseAuthV11.recover(email)
                                        .onSuccess { message = it }
                                        .onFailure { message = it.message ?: "Invio non riuscito" }
                                    loading = false
                                }
                            }
                        ) { Text("Password dimenticata") }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        TextButton(onClick = onGuest) { Text("Continua senza account") }
                    }
                }
            }
        }
    }
}
''')

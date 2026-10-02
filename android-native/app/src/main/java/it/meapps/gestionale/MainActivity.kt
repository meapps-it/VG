@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val tag = prefs.getString("app_language", "system").orEmpty()
        if (tag.isBlank() || tag == "system") {
            super.attachBaseContext(newBase)
        } else {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config =
                Configuration(newBase.resources.configuration).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GestionaleRoot(viewModel) }
        handleAuthIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    internal fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "gestionale" && data.host == "login-callback") {
            viewModel.completeGoogleLogin(data)
        }
    }
}

@Composable
internal fun GestionaleRoot(vm: AppViewModel) {
    val originalDensity = LocalDensity.current
    val scaledDensity =
        remember(originalDensity, vm.fontScale) {
            Density(originalDensity.density, originalDensity.fontScale * vm.fontScale)
        }
    val useDark =
        when (vm.themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val colors = if (useDark) GestionaleDark else GestionaleLight
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars =
                !useDark && (vm.session == null || vm.editor != null)
            controller.isAppearanceLightNavigationBars = !useDark
        }
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colors, typography = GestionaleTypography) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    vm.checkingAuth -> LoadingScreen(stringResource(R.string.verify_access))
                    vm.session == null -> LoginScreen(vm)
                    else -> AuthenticatedApp(vm)
                }
            }
        }
    }
}

@Composable
internal fun LoginScreen(vm: AppViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var register by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        Modifier.fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(Modifier.widthIn(max = 440.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.ShowChart, null, tint = AppBlue, modifier = Modifier.size(48.dp))
                Text(
                    if (register) stringResource(R.string.create_account)
                    else stringResource(R.string.app_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.tagline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(vm.googleAuthUrl()))
                        context.startActivity(intent)
                    },
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = Color(0xFF202124),
                        ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "G",
                            color = Color(0xFF4285F4),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Continua con Google", fontWeight = FontWeight.Bold)
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDADCE0))
                    Text(
                        "  oppure  ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFDADCE0))
                }

                AppTextField(
                    email,
                    { email = it },
                    stringResource(R.string.email),
                    keyboardType = KeyboardType.Email,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                )
                Button(
                    onClick = {
                        if (register) vm.signUp(email, password) else vm.login(email, password)
                    },
                    enabled = !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (vm.saving)
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else
                        Text(
                            if (register) stringResource(R.string.register)
                            else stringResource(R.string.login)
                        )
                }
                TextButton(
                    onClick = { register = !register },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        if (register) stringResource(R.string.already_account)
                        else stringResource(R.string.create_new_account)
                    )
                }
                if (!register)
                    TextButton(
                        onClick = { vm.resetPassword(email) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.forgot_password))
                    }
                vm.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                vm.noticeMessage?.let { Text(it, color = Positive) }
            }
        }
    }
}

@Composable
internal fun AuthenticatedApp(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.errorMessage, vm.noticeMessage) {
        val message = vm.errorMessage ?: vm.noticeMessage
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearMessages()
        }
    }
    val activity = LocalActivity.current
    BackHandler(enabled = true) { if (!vm.navigateBack()) activity?.moveTaskToBack(true) }

    if (vm.showTutorial) {
        var tutorialStep by rememberSaveable { mutableIntStateOf(0) }
        val titles = listOf("Articoli", "Clienti e ordini", "Scanner", "Privacy economica")
        val texts =
            listOf(
                "Inserisci e modifica gli articoli, gestisci disponibilità, prezzi e fotografie.",
                "Salva i clienti, crea ordini dagli articoli e controlla stato, pagamenti e spedizioni.",
                "Nel nuovo articolo puoi leggere il codice a barre con la fotocamera e inserirlo automaticamente.",
                "Nelle Impostazioni puoi scegliere se mostrare anche costi e guadagni oppure soltanto i prezzi di vendita.",
            )
        AlertDialog(
            onDismissRequest = vm::dismissTutorial,
            icon = { Icon(Icons.Default.TipsAndUpdates, null, tint = AppAmber) },
            title = {
                Text("Guida rapida · ${tutorialStep + 1}/4", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(titles[tutorialStep], fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(texts[tutorialStep])
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (tutorialStep < 3) tutorialStep++ else vm.dismissTutorial() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppNavy),
                ) {
                    Text(if (tutorialStep < 3) "Avanti" else "Inizia")
                }
            },
            dismissButton = {
                if (tutorialStep > 0) TextButton(onClick = { tutorialStep-- }) { Text("Indietro") }
                else TextButton(onClick = vm::dismissTutorial) { Text("Salta") }
            },
        )
    }

    vm.deleteTarget?.let { target ->
        val label =
            when (target) {
                is DeleteTarget.ProductTarget -> target.product.name
                is DeleteTarget.EntityTarget -> target.name
                is DeleteTarget.CustomerTarget -> target.customer.displayName
                is DeleteTarget.OrderTarget ->
                    target.order.number.ifBlank { target.order.customerName }
                is DeleteTarget.PhotoTarget -> "questa foto"
            }
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text("Eliminare $label? L’operazione non può essere annullata.") },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete, enabled = !vm.saving) {
                    Text(stringResource(R.string.delete), color = Negative)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (vm.premiumRequired) {
        AlertDialog(
            onDismissRequest = vm::dismissPremiumPrompt,
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = AppAmber) },
            title = {
                Text(stringResource(R.string.premium_limit_title), fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.premium_limit_text))
                    Text(
                        stringResource(R.string.premium_play_pending),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentActivity = activity
                        if (currentActivity != null) vm.purchasePremium(currentActivity)
                        else vm.dismissPremiumPrompt()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppNavy),
                ) {
                    Text(stringResource(R.string.upgrade_pro))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPremiumPrompt) {
                    Text(stringResource(R.string.later))
                }
            },
        )
    }

    when (val editor = vm.editor) {
        is Editor.ProductEditor -> ProductEditorScreen(vm)
        is Editor.EntityEditor -> EntityEditorScreen(vm, editor.kind)
        is Editor.CustomerEditor -> CustomerEditorScreen(vm)
        is Editor.OrderEditor -> OrderEditorScreen(vm)
        null ->
            when (val detail = vm.detail) {
                is Detail.ProductDetail -> ProductDetailScreen(vm, detail.productId)
                is Detail.CustomerDetail -> CustomerDetailScreen(vm, detail.customerId)
                is Detail.OrderDetail -> OrderDetailScreen(vm, detail.orderId)
                null -> MainScaffold(vm, snackbar)
            }
    }
}

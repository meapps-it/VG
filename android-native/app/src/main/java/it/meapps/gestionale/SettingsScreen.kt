@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun ArchivesScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                    EntityKind.BRAND to "Marche",
                    EntityKind.SUPPLIER to "Fornitori",
                    EntityKind.CATEGORY to "Categorie",
                )
                .forEachIndexed { index, pair ->
                    SegmentedButton(
                        selected = vm.archiveKind == pair.first,
                        onClick = { vm.archiveKind = pair.first },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) {
                        Text(pair.second)
                    }
                }
        }
        Spacer(Modifier.height(12.dp))
        val rows: List<Triple<String, String, String>> =
            when (vm.archiveKind) {
                EntityKind.BRAND -> vm.brands.map { Triple(it.id, it.name, it.notes) }
                EntityKind.SUPPLIER ->
                    vm.suppliers.map {
                        Triple(
                            it.id,
                            it.name,
                            listOf(it.contact, it.phone, it.email)
                                .filter(String::isNotBlank)
                                .joinToString(" · "),
                        )
                    }
                EntityKind.CATEGORY ->
                    vm.categories
                        .sortedBy { it.sortOrder }
                        .map {
                            Triple(
                                it.id,
                                it.name,
                                "Ordine ${it.sortOrder}${it.description.takeIf(String::isNotBlank)?.let { d -> " · $d" }.orEmpty()}",
                            )
                        }
            }
        if (rows.isEmpty())
            EmptyState(
                "Nessun dato",
                "Aggiungi una voce. L’app non carica marche o fornitori predefiniti.",
            )
        else
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.first }) { row ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            vm.openEntity(vm.archiveKind, row.first)
                        }
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.second, fontWeight = FontWeight.Bold)
                                if (row.third.isNotBlank())
                                    Text(
                                        row.third,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                            }
                            if (vm.archiveKind == EntityKind.CATEGORY) {
                                IconButton(
                                    onClick = {
                                        vm.categories
                                            .firstOrNull { it.id == row.first }
                                            ?.let { vm.moveCategory(it, -1) }
                                    }
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Sposta su")
                                }
                                IconButton(
                                    onClick = {
                                        vm.categories
                                            .firstOrNull { it.id == row.first }
                                            ?.let { vm.moveCategory(it, 1) }
                                    }
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Sposta giù")
                                }
                            }
                            Icon(Icons.Default.Edit, null, tint = AppBlue)
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
    }
}

@Composable
internal fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val languagePrefs =
        remember(context) {
            context.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE)
        }
    var appLanguage by remember {
        mutableStateOf(languagePrefs.getString("app_language", "system") ?: "system")
    }
    var dataAction by remember { mutableStateOf<String?>(null) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                runCatching {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(pendingBackup.orEmpty())
                        } ?: error("Impossibile aprire il file")
                    }
                    .onSuccess { exportMessage = "Backup esportato correttamente" }
                    .onFailure { exportMessage = "Esportazione non riuscita: ${it.message}" }
            pendingBackup = null
        }
    fun startExport(withPhotos: Boolean) {
        pendingBackup = vm.createBackupJson(withPhotos)
        val suffix = if (withPhotos) "completo" else "leggero"
        exportLauncher.launch("gestionale-backup-$suffix-${System.currentTimeMillis()}.json")
    }
    dataAction?.let { action ->
        val message =
            when (action) {
                "replace" -> stringResource(R.string.replace_demo_confirm)
                "demo" -> stringResource(R.string.delete_demo_confirm)
                else -> stringResource(R.string.delete_all_confirm)
            }
        AlertDialog(
            onDismissRequest = { dataAction = null },
            title = {
                Text(stringResource(R.string.data_management), fontWeight = FontWeight.SemiBold)
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            "replace" -> vm.replaceWithDemoData()
                            "demo" -> vm.deleteDemoData()
                            else -> vm.deleteAllData()
                        }
                        dataAction = null
                    }
                ) {
                    Text(
                        if (action == "replace") stringResource(R.string.replace_demo)
                        else stringResource(R.string.delete),
                        color = if (action == "replace") AppBlue else Negative,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dataAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsHeader(
                if (vm.isPremium) stringResource(R.string.premium_plan)
                else stringResource(R.string.free_plan),
                if (vm.isPremium) stringResource(R.string.premium_unlimited)
                else
                    stringResource(
                        R.string.free_limit_status,
                        vm.freeProductCount,
                        AppViewModel.FREE_PRODUCT_LIMIT,
                    ),
            )
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (vm.isPremium) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (vm.isPremium) AppAmber.copy(alpha = .45f) else LegacyBorder,
                    ),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color =
                                if (vm.isPremium) AppAmber.copy(alpha = .16f)
                                else AppBlue.copy(alpha = .12f),
                        ) {
                            Icon(
                                if (vm.isPremium) Icons.Default.WorkspacePremium
                                else Icons.Default.Inventory2,
                                null,
                                Modifier.padding(12.dp).size(26.dp),
                                tint = if (vm.isPremium) AppAmber else AppBlue,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (vm.isPremium) stringResource(R.string.premium_plan)
                                else stringResource(R.string.free_plan),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            )
                            Text(
                                if (vm.isPremium) stringResource(R.string.premium_unlimited)
                                else
                                    stringResource(
                                        R.string.free_limit_status,
                                        vm.freeProductCount,
                                        AppViewModel.FREE_PRODUCT_LIMIT,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            if (!vm.isPremium && vm.premiumPrice != null) {
                                Text(
                                    "Pro annuale · ${vm.premiumPrice}",
                                    color = AppBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    if (!vm.isPremium) {
                        LinearProgressIndicator(
                            progress = {
                                (vm.freeProductCount.toFloat() / AppViewModel.FREE_PRODUCT_LIMIT)
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = AppBlue,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Button(
                            onClick = {
                                if (activity != null) vm.purchasePremium(activity)
                                else vm.showPremiumPrompt()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppNavy),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = AppAmber)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upgrade_pro),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsHeader("Prova gratuita", "7 giorni di accesso completo prima dell’abbonamento")
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (vm.trialActive) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            vm.isPremium -> "Versione Pro attiva"
                            vm.trialActive -> "Prova gratuita attiva"
                            else -> "Prova gratuita terminata"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        when {
                            vm.isPremium -> "Il tuo abbonamento è attivo."
                            vm.trialActive ->
                                "Restano ${vm.trialDaysRemaining} giorni di prova completa."
                            else -> "Per continuare con le funzioni Pro è necessario l’abbonamento."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SettingsHeader(
                stringResource(R.string.demo_data),
                stringResource(R.string.demo_data_sub),
            )
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                border =
                    androidx.compose.foundation.BorderStroke(1.2.dp, AppBlue.copy(alpha = .25f)),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = vm::loadDemoData,
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
                    ) {
                        Icon(Icons.Default.Dataset, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.load_demo), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { dataAction = "replace" },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Default.RestartAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.replace_demo), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { dataAction = "demo" },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, tint = Negative)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.delete_demo),
                            color = Negative,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        stringResource(R.string.demo_notice),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { dataAction = "all" },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(15.dp),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                Negative.copy(alpha = .55f),
                            ),
                    ) {
                        Icon(Icons.Default.DeleteForever, null, tint = Negative)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.delete_all_data),
                            color = Negative,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        item {
            SettingsHeader(
                stringResource(R.string.appearance),
                stringResource(R.string.appearance_sub),
            )
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.theme),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                                AppThemeMode.SYSTEM to stringResource(R.string.system),
                                AppThemeMode.LIGHT to stringResource(R.string.light),
                                AppThemeMode.DARK to stringResource(R.string.dark),
                            )
                            .forEach { option ->
                                FilterChip(
                                    selected = vm.themeMode == option.first,
                                    onClick = { vm.updateThemeMode(option.first) },
                                    label = { Text(option.second) },
                                )
                            }
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.language),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        stringResource(R.string.language_sub),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    SelectionField(
                        stringResource(R.string.language),
                        appLanguage,
                        listOf(
                            "system" to stringResource(R.string.system),
                            "it" to "Italiano",
                            "en" to "English",
                            "fr" to "Français",
                            "es" to "Español",
                            "de" to "Deutsch",
                        ),
                        { selected ->
                            val tag = selected ?: "system"
                            appLanguage = tag
                            languagePrefs.edit().putString("app_language", tag).apply()
                            if (tag == "system") {
                                Locale.setDefault(Locale.getDefault())
                            }
                            (context as? android.app.Activity)?.recreate()
                        },
                    )
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.text_size), fontWeight = FontWeight.Bold)
                            Text(
                                "${(vm.fontScale * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(
                        value = vm.fontScale,
                        onValueChange = vm::updateFontScale,
                        valueRange = .85f..1.35f,
                        steps = 9,
                    )
                    SettingSwitch(
                        stringResource(R.string.compact_mode),
                        stringResource(R.string.compact_mode_sub),
                        vm.compactMode,
                        vm::updateCompactMode,
                    )
                    SettingSwitch(
                        stringResource(R.string.grid_catalog),
                        stringResource(R.string.grid_catalog_sub),
                        vm.gridView,
                        vm::updateGridView,
                    )
                    HorizontalDivider()
                    SettingSwitch(
                        "Mostra costi e guadagni",
                        "Disattiva per mostrare soltanto i prezzi di vendita",
                        vm.showEconomicDetails,
                        vm::updateEconomicVisibility,
                    )
                    OutlinedButton(
                        onClick = vm::reopenTutorial,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.TipsAndUpdates, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Rivedi tutorial")
                    }
                }
            }
        }
        item {
            SettingsHeader(
                stringResource(R.string.data_backup),
                stringResource(R.string.data_backup_sub),
            )
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { startExport(true) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_full_backup))
                    }
                    OutlinedButton(
                        onClick = { startExport(false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.export_without_photos))
                    }
                    Text(
                        stringResource(R.string.backup_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    exportMessage?.let {
                        Text(
                            it,
                            color = if (it.startsWith("Backup")) Positive else Negative,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            SettingsHeader("Anagrafiche", "Marche, fornitori e categorie creati da te.")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(10.dp)) {
                    SettingsLink(Icons.Default.Label, "Marche", "${vm.brands.size} elementi") {
                        vm.archiveKind = EntityKind.BRAND
                        vm.selectTab(MainTab.ARCHIVES)
                    }
                    SettingsLink(
                        Icons.Default.LocalShipping,
                        "Fornitori",
                        "${vm.suppliers.size} elementi",
                    ) {
                        vm.archiveKind = EntityKind.SUPPLIER
                        vm.selectTab(MainTab.ARCHIVES)
                    }
                    SettingsLink(
                        Icons.Default.Category,
                        "Categorie",
                        "${vm.categories.size} elementi",
                    ) {
                        vm.archiveKind = EntityKind.CATEGORY
                        vm.selectTab(MainTab.ARCHIVES)
                    }
                }
            }
        }
        item {
            SettingsHeader(
                stringResource(R.string.diagnostics),
                "Stato reale dei dati caricati nell’app.",
            )
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ValueRow(
                        stringResource(R.string.cloud_connection),
                        if (vm.session != null) "Attiva" else "Disconnessa",
                        if (vm.session != null) Positive else Negative,
                    )
                    ValueRow("Articoli", vm.products.size.toString())
                    ValueRow(
                        stringResource(R.string.linked_photos),
                        vm.products.sumOf { it.photos.size }.toString(),
                    )
                    ValueRow(
                        stringResource(R.string.last_update),
                        vm.lastSyncAt?.let {
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
                        } ?: "Mai",
                    )
                    OutlinedButton(
                        onClick = vm::loadAll,
                        enabled = !vm.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Sync, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh_cloud))
                    }
                }
            }
        }
        item {
            SettingsHeader(
                stringResource(R.string.account),
                "Sessione protetta e dati separati dagli altri utenti.",
            )
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vm.session?.email.orEmpty(), fontWeight = FontWeight.Bold)
                    Text(
                        "ID account: ${vm.session?.userId?.take(8)}…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    OutlinedButton(
                        onClick = vm::logout,
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.logout))
                    }
                }
            }
        }
        item {
            SettingsHeader(
                stringResource(R.string.information),
                "Versione tecnica e protezione dei dati.",
            )
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Gestionale Android 0.7.3",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        "Applicazione Android nativa · base Free + Premium",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Text(
                        "Fotocamera facoltativa · archivio immagini privato · isolamento dati tramite Supabase RLS.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
internal fun SettingsHeader(title: String, subtitle: String) =
    Column(Modifier.padding(horizontal = 2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
    }

@Composable
internal fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked, onChange)
    }

@Composable
internal fun SettingsLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) =
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            Modifier.size(42.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(icon, null, Modifier.padding(10.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null)
    }

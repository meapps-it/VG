package com.meapps.turnioperai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private val V6Blue = Color(0xFF4069E5)
private val V6Navy = Color(0xFF17325C)
private val V6Green = Color(0xFF22A66A)
private val V6Orange = Color(0xFFF0A11D)
private val V6Purple = Color(0xFF8056E8)
private val V6Red = Color(0xFFDE5058)
private val V6Teal = Color(0xFF0D9B95)

data class ShiftVisualV6(val bg: Color, val fg: Color, val icon: ImageVector)

data class CyclePresetV6(
    val name: String,
    val description: String,
    val cycle: List<ShiftType>,
    val note: String = ""
)

data class VacationPresetV8(
    val key: String,
    val label: String,
    val detail: String,
    val monthlyMinutes: Int
)

data class VacationProfileV8(
    val contractKey: String,
    val monthlyMinutes: Int,
    val openingMinutes: Int,
    val startMonth: String
)

data class BackupDataV8(
    val shifts: List<ShiftEntry>,
    val theme: String?,
    val vacationProfile: VacationProfileV8?
)

private val vacationPresetsV8 = listOf(
    VacationPresetV8(
        "metal4",
        "Metalmeccanici Industria - 4 settimane",
        "160 ore annue con giornata da 8 ore: 13h20m al mese",
        13 * 60 + 20
    ),
    VacationPresetV8(
        "metal5",
        "Metalmeccanici Industria - 5 settimane",
        "200 ore annue con giornata da 8 ore: 16h40m al mese",
        16 * 60 + 40
    ),
    VacationPresetV8(
        "commerce26",
        "Commercio / Terziario - 26 giorni",
        "Equivalenza app a 8 ore/giorno: 208 ore annue, 17h20m al mese",
        17 * 60 + 20
    ),
    VacationPresetV8(
        "custom",
        "Personalizzato",
        "Imposta tu la maturazione mensile indicata dal cedolino o dal tuo contratto",
        13 * 60 + 20
    )
)

private fun vacationPresetV8(key: String): VacationPresetV8 =
    vacationPresetsV8.firstOrNull { it.key == key } ?: vacationPresetsV8.first()

private fun formatHoursMinutesV8(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h${m.toString().padStart(2, '0')}m"
    }
}

private fun formatVacationV8(totalMinutes: Int): String {
    val sign = if (totalMinutes < 0) "-" else ""
    var remaining = kotlin.math.abs(totalMinutes)
    val days = remaining / (8 * 60)
    remaining %= (8 * 60)
    val h = remaining / 60
    val m = remaining % 60
    val parts = buildList {
        if (days > 0) add("${days}g")
        if (h > 0) add("${h}h")
        if (m > 0) add("${m}m")
        if (isEmpty()) add("0h")
    }
    return sign + parts.joinToString(" ")
}

private fun vacationStartYearMonthV8(profile: VacationProfileV8): YearMonth =
    runCatching { YearMonth.parse(profile.startMonth.take(7)) }.getOrElse { YearMonth.now() }

private fun accruedMonthsV8(profile: VacationProfileV8, today: LocalDate = LocalDate.now()): Int {
    val start = vacationStartYearMonthV8(profile)
    val now = YearMonth.from(today)
    if (start.isAfter(now)) return 0
    val whole = ChronoUnit.MONTHS.between(start.atDay(1), now.atDay(1)).toInt()
    return whole + if (today.dayOfMonth >= 15) 1 else 0
}

private fun vacationUsedMinutesV8(shifts: List<ShiftEntry>, profile: VacationProfileV8): Int {
    val startDate = vacationStartYearMonthV8(profile).atDay(1)
    return shifts.asSequence()
        .filter { it.type == ShiftType.VACATION && !it.date.isBefore(startDate) }
        .sumOf { if (it.vacationMinutes > 0) it.vacationMinutes else 8 * 60 }
}

private fun vacationBalanceMinutesV8(shifts: List<ShiftEntry>, profile: VacationProfileV8): Int =
    profile.openingMinutes + accruedMonthsV8(profile) * profile.monthlyMinutes - vacationUsedMinutesV8(shifts, profile)

enum class ScheduleModeV6(val title: String, val subtitle: String, val icon: ImageVector) {
    FIXED("Turno fisso", "Stesso turno nei giorni lavorativi scelti", Icons.Default.Work),
    TWO_SHIFTS("2 turni", "Alternanza Mattino / Pomeriggio", Icons.Default.SwapHoriz),
    THREE_SHIFTS("3 turni", "Alternanza Mattino / Pomeriggio / Notte", Icons.Default.Autorenew),
    WEEKLY("Rotazione a settimane", "Una settimana intera per ogni turno", Icons.Default.ViewWeek),
    CONTINUOUS_8H("Ciclo continuo 8 ore", "H24 con Mattino / Pomeriggio / Notte / Riposo", Icons.Default.Loop),
    CONTINUOUS_12H("Ciclo continuo 12 ore", "Giorno / Notte / Riposo su cicli H24", Icons.Default.Schedule),
    CUSTOM("Ciclo personalizzato", "Crea una sequenza qualsiasi e ripetila", Icons.Default.Tune)
}

private fun visualV6(type: ShiftType): ShiftVisualV6 = when (type) {
    ShiftType.MORNING -> ShiftVisualV6(Color(0xFFE6F7EE), V6Green, Icons.Default.WbSunny)
    ShiftType.AFTERNOON -> ShiftVisualV6(Color(0xFFFFF2D8), V6Orange, Icons.Default.LightMode)
    ShiftType.NIGHT -> ShiftVisualV6(Color(0xFFE8EEFF), V6Blue, Icons.Default.DarkMode)
    ShiftType.DAY -> ShiftVisualV6(Color(0xFFE3F7F5), V6Teal, Icons.Default.Work)
    ShiftType.REST -> ShiftVisualV6(Color(0xFFF0F2F5), Color(0xFF687386), Icons.Default.Hotel)
    ShiftType.VACATION -> ShiftVisualV6(Color(0xFFF1EAFF), V6Purple, Icons.Default.BeachAccess)
    ShiftType.SICK -> ShiftVisualV6(Color(0xFFFFE8EA), V6Red, Icons.Default.MedicalServices)
    ShiftType.PERMIT -> ShiftVisualV6(Color(0xFFFFF0E2), Color(0xFFD97706), Icons.Default.EventAvailable)
    ShiftType.ON_CALL -> ShiftVisualV6(Color(0xFFE6F5FF), Color(0xFF0284C7), Icons.Default.PhoneInTalk)
    ShiftType.SPLIT -> ShiftVisualV6(Color(0xFFFFEDF6), Color(0xFFD72D7A), Icons.Default.CallSplit)
    ShiftType.HOLIDAY -> ShiftVisualV6(Color(0xFFFFE8E8), Color(0xFFC92A2A), Icons.Default.Celebration)
    ShiftType.DOUBLE -> ShiftVisualV6(Color(0xFFEDEBFF), Color(0xFF6756D9), Icons.Default.DoubleArrow)
}

private fun repeatShiftV6(type: ShiftType, count: Int): List<ShiftType> = List(count) { type }

private fun continuous8PresetsV6(): List<CyclePresetV6> = listOf(
    CyclePresetV6(
        "2-2-2-2 / 6x2",
        "2 Mattine, 2 Pomeriggi, 2 Notti, 2 Riposi",
        repeatShiftV6(ShiftType.MORNING, 2) + repeatShiftV6(ShiftType.AFTERNOON, 2) + repeatShiftV6(ShiftType.NIGHT, 2) + repeatShiftV6(ShiftType.REST, 2),
        "Ciclo di 8 giorni"
    ),
    CyclePresetV6(
        "4-1-4-1-4-2",
        "4 Mattine, 1 Riposo, 4 Pomeriggi, 1 Riposo, 4 Notti, 2 Riposi",
        repeatShiftV6(ShiftType.MORNING, 4) + listOf(ShiftType.REST) + repeatShiftV6(ShiftType.AFTERNOON, 4) + listOf(ShiftType.REST) + repeatShiftV6(ShiftType.NIGHT, 4) + repeatShiftV6(ShiftType.REST, 2),
        "Ciclo continuo classico a 4 squadre"
    ),
    CyclePresetV6(
        "4x2",
        "4 giorni sul turno, 2 riposi, poi si passa al turno successivo",
        repeatShiftV6(ShiftType.MORNING, 4) + repeatShiftV6(ShiftType.REST, 2) + repeatShiftV6(ShiftType.AFTERNOON, 4) + repeatShiftV6(ShiftType.REST, 2) + repeatShiftV6(ShiftType.NIGHT, 4) + repeatShiftV6(ShiftType.REST, 2),
        "Ciclo di 18 giorni"
    ),
    CyclePresetV6(
        "3x2",
        "3 giorni sul turno, 2 riposi, poi si passa al turno successivo",
        repeatShiftV6(ShiftType.MORNING, 3) + repeatShiftV6(ShiftType.REST, 2) + repeatShiftV6(ShiftType.AFTERNOON, 3) + repeatShiftV6(ShiftType.REST, 2) + repeatShiftV6(ShiftType.NIGHT, 3) + repeatShiftV6(ShiftType.REST, 2),
        "Ciclo di 15 giorni"
    ),
    CyclePresetV6(
        "Continental 7-2-7-2-7-3",
        "7 Mattine, 2 Riposi, 7 Pomeriggi, 2 Riposi, 7 Notti, 3 Riposi",
        repeatShiftV6(ShiftType.MORNING, 7) + repeatShiftV6(ShiftType.REST, 2) + repeatShiftV6(ShiftType.AFTERNOON, 7) + repeatShiftV6(ShiftType.REST, 2) + repeatShiftV6(ShiftType.NIGHT, 7) + repeatShiftV6(ShiftType.REST, 3),
        "Ciclo di 28 giorni"
    )
)

private fun panama28V6(): List<ShiftType> {
    val onOff = listOf(true, true, false, false, true, true, true, false, false, false, true, true, false, false)
    return onOff.map { if (it) ShiftType.DAY else ShiftType.REST } +
        onOff.map { if (it) ShiftType.NIGHT else ShiftType.REST }
}

private fun continuous12PresetsV6(): List<CyclePresetV6> = listOf(
    CyclePresetV6(
        "DDNNOO",
        "2 turni di giorno, 2 di notte, 2 riposi",
        repeatShiftV6(ShiftType.DAY, 2) + repeatShiftV6(ShiftType.NIGHT, 2) + repeatShiftV6(ShiftType.REST, 2),
        "Ciclo di 6 giorni"
    ),
    CyclePresetV6(
        "4 on / 4 off",
        "4 Giorni, 4 Riposi, 4 Notti, 4 Riposi",
        repeatShiftV6(ShiftType.DAY, 4) + repeatShiftV6(ShiftType.REST, 4) + repeatShiftV6(ShiftType.NIGHT, 4) + repeatShiftV6(ShiftType.REST, 4),
        "Ciclo rotante di 16 giorni"
    ),
    CyclePresetV6(
        "2-2-3 / Panama",
        "Schema 2 on, 2 off, 3 on con passaggio Giorno / Notte",
        panama28V6(),
        "Ciclo completo di 28 giorni"
    ),
    CyclePresetV6(
        "DuPont",
        "4 Notti, 3 Riposi, 3 Giorni, 1 Riposo, 3 Notti, 3 Riposi, 4 Giorni, 7 Riposi",
        repeatShiftV6(ShiftType.NIGHT, 4) + repeatShiftV6(ShiftType.REST, 3) + repeatShiftV6(ShiftType.DAY, 3) + listOf(ShiftType.REST) + repeatShiftV6(ShiftType.NIGHT, 3) + repeatShiftV6(ShiftType.REST, 3) + repeatShiftV6(ShiftType.DAY, 4) + repeatShiftV6(ShiftType.REST, 7),
        "Ciclo di 28 giorni"
    ),
    CyclePresetV6(
        "3 on / 3 off",
        "3 Giorni, 3 Riposi, 3 Notti, 3 Riposi",
        repeatShiftV6(ShiftType.DAY, 3) + repeatShiftV6(ShiftType.REST, 3) + repeatShiftV6(ShiftType.NIGHT, 3) + repeatShiftV6(ShiftType.REST, 3),
        "Ciclo di 12 giorni"
    ),
    CyclePresetV6(
        "5-5-4",
        "5 Giorni, 5 Riposi, 5 Notti, 5 Riposi, 4 Giorni, 4 Riposi",
        repeatShiftV6(ShiftType.DAY, 5) + repeatShiftV6(ShiftType.REST, 5) + repeatShiftV6(ShiftType.NIGHT, 5) + repeatShiftV6(ShiftType.REST, 5) + repeatShiftV6(ShiftType.DAY, 4) + repeatShiftV6(ShiftType.REST, 4),
        "Variante rotante di 28 giorni"
    )
)

private fun makeBackupJsonV8(shifts: List<ShiftEntry>, theme: String, vacationProfile: VacationProfileV8): String {
    val root = JSONObject()
    root.put("format", "TurniOperaiBackup")
    root.put("version", 8)
    root.put("createdAt", LocalDate.now().toString())
    root.put("theme", theme)
    root.put("vacationContractKey", vacationProfile.contractKey)
    root.put("vacationMonthlyMinutes", vacationProfile.monthlyMinutes)
    root.put("vacationOpeningMinutes", vacationProfile.openingMinutes)
    root.put("vacationStartMonth", vacationProfile.startMonth)
    val array = JSONArray()
    shifts.sortedBy { it.date }.forEach { entry ->
        array.put(
            JSONObject()
                .put("date", entry.date.toString())
                .put("type", entry.type.name)
                .put("overtime", entry.overtime)
                .put("overtimeMinutes", entry.overtimeMinutes)
                .put("vacationMinutes", entry.vacationMinutes)
        )
    }
    root.put("shifts", array)
    return root.toString(2)
}

private fun parseBackupV8(raw: String): BackupDataV8 {
    val root = JSONObject(raw)
    require(root.optString("format") == "TurniOperaiBackup") { "File di backup non riconosciuto" }
    val array = root.getJSONArray("shifts")
    val shifts = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val type = ShiftType.valueOf(item.getString("type"))
            val overtimeMinutes = item.optInt("overtimeMinutes", 0).coerceAtLeast(0)
            val vacationMinutes = if (item.has("vacationMinutes")) {
                item.optInt("vacationMinutes", 0).coerceAtLeast(0)
            } else if (type == ShiftType.VACATION) {
                8 * 60
            } else 0
            add(
                ShiftEntry(
                    LocalDate.parse(item.getString("date")),
                    type,
                    item.optBoolean("overtime", false) || overtimeMinutes > 0,
                    overtimeMinutes,
                    vacationMinutes
                )
            )
        }
    }
    val restoredTheme = root.optString("theme").takeIf { it in setOf("light", "dark", "system") }
    val profile = if (root.has("vacationContractKey") || root.has("vacationMonthlyMinutes")) {
        val key = root.optString("vacationContractKey", "metal4")
        VacationProfileV8(
            contractKey = key,
            monthlyMinutes = root.optInt("vacationMonthlyMinutes", vacationPresetV8(key).monthlyMinutes).coerceAtLeast(0),
            openingMinutes = root.optInt("vacationOpeningMinutes", 0),
            startMonth = root.optString("vacationStartMonth", LocalDate.now().withDayOfYear(1).withDayOfMonth(1).toString())
        )
    } else null
    return BackupDataV8(shifts, restoredTheme, profile)
}

class MainActivityV6 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurniOperaiV6(this) }
    }
}

@Composable
fun TurniOperaiV6(context: Context) {
    val prefs = remember { context.getSharedPreferences("turni_operai", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "light") ?: "light") }
    var shifts by remember { mutableStateOf(loadShifts(prefs.getStringSet("shifts", emptySet()) ?: emptySet())) }
    var tab by remember { mutableIntStateOf(0) }
    var editDate by remember { mutableStateOf<LocalDate?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val dark = theme == "dark" || (theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())
    var backupMessage by remember { mutableStateOf("") }
    var vacationContractKey by remember { mutableStateOf(prefs.getString("vacation_contract", "metal4") ?: "metal4") }
    var vacationMonthlyMinutes by remember {
        mutableIntStateOf(
            prefs.getInt("vacation_monthly_minutes", vacationPresetV8(vacationContractKey).monthlyMinutes)
        )
    }
    var vacationOpeningMinutes by remember { mutableIntStateOf(prefs.getInt("vacation_opening_minutes", 0)) }
    var vacationStartMonth by remember {
        mutableStateOf(
            prefs.getString("vacation_start_month", LocalDate.now().withDayOfYear(1).withDayOfMonth(1).toString())
                ?: LocalDate.now().withDayOfYear(1).withDayOfMonth(1).toString()
        )
    }
    val vacationProfile = VacationProfileV8(
        vacationContractKey,
        vacationMonthlyMinutes,
        vacationOpeningMinutes,
        vacationStartMonth
    )

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(makeBackupJsonV8(shifts, theme, vacationProfile))
                } ?: error("Impossibile aprire il file")
            }.onSuccess {
                backupMessage = "Backup salvato correttamente"
            }.onFailure {
                backupMessage = "Errore durante il salvataggio del backup"
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Impossibile leggere il file")
                parseBackupV8(raw)
            }.onSuccess { restored ->
                val restoredShifts = restored.shifts.sortedBy { it.date }
                shifts = restoredShifts
                val editor = prefs.edit().putStringSet(
                    "shifts",
                    restoredShifts.map {
                        "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}"
                    }.toSet()
                )
                restored.theme?.let { restoredTheme ->
                    theme = restoredTheme
                    editor.putString("theme", restoredTheme)
                }
                restored.vacationProfile?.let { profile ->
                    vacationContractKey = profile.contractKey
                    vacationMonthlyMinutes = profile.monthlyMinutes
                    vacationOpeningMinutes = profile.openingMinutes
                    vacationStartMonth = profile.startMonth
                    editor.putString("vacation_contract", profile.contractKey)
                    editor.putInt("vacation_monthly_minutes", profile.monthlyMinutes)
                    editor.putInt("vacation_opening_minutes", profile.openingMinutes)
                    editor.putString("vacation_start_month", profile.startMonth)
                }
                editor.apply()
                backupMessage = "Backup ripristinato: ${restoredShifts.size} giornate"
            }.onFailure {
                backupMessage = "Backup non valido o danneggiato"
            }
        }
    }

    fun save(list: List<ShiftEntry>) {
        shifts = list.sortedBy { it.date }
        prefs.edit().putStringSet("shifts", shifts.map { "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}" }.toSet()).apply()
    }

    fun applyGenerated(generated: List<ShiftEntry>) {
        val dates = generated.map { it.date }.toSet()
        save(shifts.filterNot { it.date in dates } + generated)
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9FB5FF)) else lightColorScheme(
            primary = V6Blue,
            secondary = V6Green,
            tertiary = V6Purple,
            background = Color(0xFFF7F9FC),
            surface = Color.White
        )
    ) {
        if (editDate != null) {
            EditShiftV6(
                date = editDate!!,
                existing = shifts.find { it.date == editDate },
                onBack = { editDate = null },
                onSave = { entry -> save(shifts.filterNot { it.date == entry.date } + entry); editDate = null },
                onDelete = { save(shifts.filterNot { it.date == editDate }); editDate = null }
            )
        } else {
            Scaffold(
                topBar = {
                    Surface(color = V6Navy) {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CalendarMonth, null, tint = Color.White) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Turni Operai", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                                Text("Calendario e cicli di turnazione", color = Color.White.copy(alpha = .80f), style = MaterialTheme.typography.bodySmall)
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, "Menu", tint = Color.White) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    MenuItemV6("Home", Icons.Default.Home) { tab = 0; menuOpen = false }
                                    MenuItemV6("Statistiche", Icons.Default.BarChart) { tab = 1; menuOpen = false }
                                    MenuItemV6("Calendario", Icons.Default.CalendarMonth) { tab = 2; menuOpen = false }
                                    MenuItemV6("Impostazione turni", Icons.Default.Settings) { tab = 3; menuOpen = false }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    NavigationBar {
                        listOf(
                            Triple("Home", Icons.Default.Home, 0),
                            Triple("Statistiche", Icons.Default.BarChart, 1),
                            Triple("Calendario", Icons.Default.CalendarMonth, 2),
                            Triple("Impostazioni", Icons.Default.Settings, 3)
                        ).forEach { (label, icon, index) ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icon, label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> HomeV6(shifts, vacationProfile) { editDate = it }
                        1 -> StatsV6(shifts, vacationProfile)
                        2 -> CalendarV6(shifts) { editDate = it }
                        else -> SettingsV6(
                            theme = theme,
                            onTheme = { value -> theme = value; prefs.edit().putString("theme", value).apply() },
                            onApply = ::applyGenerated,
                            onBackup = { createBackupLauncher.launch("TurniOperai-backup-${LocalDate.now()}.json") },
                            onRestore = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                            backupMessage = backupMessage,
                            vacationProfile = vacationProfile,
                            allShifts = shifts,
                            onVacationProfileChange = { profile ->
                                vacationContractKey = profile.contractKey
                                vacationMonthlyMinutes = profile.monthlyMinutes
                                vacationOpeningMinutes = profile.openingMinutes
                                vacationStartMonth = profile.startMonth
                                prefs.edit()
                                    .putString("vacation_contract", profile.contractKey)
                                    .putInt("vacation_monthly_minutes", profile.monthlyMinutes)
                                    .putInt("vacation_opening_minutes", profile.openingMinutes)
                                    .putString("vacation_start_month", profile.startMonth)
                                    .apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemV6(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick, leadingIcon = { Icon(icon, null) })
}

@Composable
private fun HomeV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8, onDate: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val title = month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() } + " ${month.year}"
    val monthShifts = shifts.filter { YearMonth.from(it.date) == month }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
                    }
                    MonthGridV6(month, shifts, onDate)
                }
            }
        }
        item { SummaryV6(monthShifts, shifts, vacationProfile) }
        item {
            Button(onClick = { onDate(LocalDate.now()) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Aggiungi turno", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MonthGridV6(month: YearMonth, shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val labels = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
        }
        repeat((offset + days + 6) / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = row * 7 + col - offset + 1
                    if (day in 1..days) {
                        val date = month.atDay(day)
                        val shift = shifts.find { it.date == date }
                        val v = shift?.let { visualV6(it.type) }
                        Column(
                            Modifier.weight(1f).padding(2.dp).height(56.dp).background(v?.bg ?: Color(0xFFF5F6F8), RoundedCornerShape(12.dp)).clickable { onDate(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(day.toString(), fontWeight = FontWeight.Bold)
                            if (shift != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(shift.type.short, color = v!!.fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                                    if (shift.overtime || shift.overtimeMinutes > 0) {
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Default.Bolt, "Straordinario", tint = V6Orange, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryV6(monthShifts: List<ShiftEntry>, allShifts: List<ShiftEntry>, vacationProfile: VacationProfileV8) {
    val workTypes = setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT, ShiftType.HOLIDAY, ShiftType.DOUBLE)
    val saturdaysWorked = monthShifts.count { it.date.dayOfWeek == DayOfWeek.SATURDAY && it.type in workTypes }
    val nights = monthShifts.count { it.type == ShiftType.NIGHT }
    val overtimeMinutes = monthShifts.sumOf { it.overtimeMinutes.coerceAtLeast(0) }
    val weekdayRests = monthShifts.count { it.type == ShiftType.REST && it.date.dayOfWeek.value in 1..5 }
    val vacationMonthMinutes = monthShifts.filter { it.type == ShiftType.VACATION }.sumOf {
        if (it.vacationMinutes > 0) it.vacationMinutes else 8 * 60
    }
    val vacationBalance = vacationBalanceMinutesV8(allShifts, vacationProfile)
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Riepilogo mese", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatV6("Sabati lav.", "$saturdaysWorked", Icons.Default.EventAvailable, Color(0xFFE6F7EE), V6Green, Modifier.weight(1f))
                StatV6("Notti", "$nights", Icons.Default.DarkMode, Color(0xFFE8EEFF), V6Blue, Modifier.weight(1f))
                StatV6("Straord.", formatHoursMinutesV8(overtimeMinutes), Icons.Default.Bolt, Color(0xFFFFF2D8), V6Orange, Modifier.weight(1f))
                StatV6("Riposi L-V", "$weekdayRests", Icons.Default.Hotel, Color(0xFFF1EAFF), V6Purple, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatV6("Ferie mese", formatVacationV8(vacationMonthMinutes), Icons.Default.BeachAccess, Color(0xFFF1EAFF), V6Purple, Modifier.weight(1f))
                StatV6("Ferie residue", formatVacationV8(vacationBalance), Icons.Default.AccountBalanceWallet, Color(0xFFE3F7F5), V6Teal, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatV6(label: String, value: String, icon: ImageVector, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(14.dp)).padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(value, fontWeight = FontWeight.ExtraBold, color = fg)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatsV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8) {
    val month = shifts.filter { YearMonth.from(it.date) == YearMonth.now() }
    val counts = month.groupingBy { it.type }.eachCount()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        item { SummaryV6(month, shifts, vacationProfile) }
        items(ShiftType.entries) { type ->
            val v = visualV6(type)
            Card(colors = CardDefaults.cardColors(containerColor = v.bg), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(v.icon, null, tint = v.fg)
                    Spacer(Modifier.width(10.dp))
                    Text(type.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${counts[type] ?: 0}", color = v.fg, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun CalendarV6(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Calendario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        if (shifts.isEmpty()) item { Text("Nessun turno inserito.") }
        items(shifts.sortedByDescending { it.date }) { shift ->
            val v = visualV6(shift.type)
            Card(colors = CardDefaults.cardColors(containerColor = v.bg), modifier = Modifier.clickable { onDate(shift.date) }, shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(v.icon, null, tint = v.fg)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shift.type.label, fontWeight = FontWeight.Bold)
                            if (shift.overtime || shift.overtimeMinutes > 0) {
                                Spacer(Modifier.width(7.dp))
                                Surface(color = Color(0xFFFFE2AA), shape = RoundedCornerShape(20.dp)) {
                                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, null, tint = Color(0xFFB76E00), modifier = Modifier.size(14.dp))
                                        Text(if (shift.overtimeMinutes > 0) formatHoursMinutesV8(shift.overtimeMinutes) else "Straordinario", color = Color(0xFF8A5300), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text(shift.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                        if (shift.type == ShiftType.VACATION) {
                            Text("Ferie: ${formatVacationV8(if (shift.vacationMinutes > 0) shift.vacationMinutes else 8 * 60)}", style = MaterialTheme.typography.labelSmall, color = v.fg)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = v.fg)
                }
            }
        }
    }
}

@Composable
private fun EditShiftV6(date: LocalDate, existing: ShiftEntry?, onBack: () -> Unit, onSave: (ShiftEntry) -> Unit, onDelete: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ShiftType.MORNING) }
    var overtimeMinutes by remember { mutableIntStateOf(existing?.overtimeMinutes ?: 0) }
    var vacationMinutes by remember {
        mutableIntStateOf(
            existing?.vacationMinutes?.takeIf { it > 0 }
                ?: if (existing?.type == ShiftType.VACATION) 8 * 60 else 8 * 60
        )
    }
    val workTypes = setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT, ShiftType.HOLIDAY, ShiftType.DOUBLE)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Aggiungi turno" else "Modifica turno") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) }
            item {
                Text("Tipo di giornata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ShiftType.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { st ->
                            val v = visualV6(st)
                            Surface(
                                color = if (type == st) v.bg else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (type == st) v.fg else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f).clickable {
                                    type = st
                                    if (st == ShiftType.VACATION && vacationMinutes <= 0) vacationMinutes = 8 * 60
                                    if (st !in workTypes) overtimeMinutes = 0
                                }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(v.icon, null, tint = v.fg)
                                    Spacer(Modifier.width(8.dp))
                                    Text(st.label, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (type in workTypes) {
                item {
                    Surface(color = if (overtimeMinutes > 0) Color(0xFFFFF2D8) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bolt, null, tint = V6Orange)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Straordinario", fontWeight = FontWeight.Bold)
                                    Text("Conteggio a scatti di 30 minuti", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                FilledTonalIconButton(onClick = { overtimeMinutes = (overtimeMinutes - 30).coerceAtLeast(0) }) {
                                    Icon(Icons.Default.Remove, "Meno 30 minuti")
                                }
                                Text(formatHoursMinutesV8(overtimeMinutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = V6Orange)
                                FilledTonalIconButton(onClick = { overtimeMinutes = (overtimeMinutes + 30).coerceAtMost(12 * 60) }) {
                                    Icon(Icons.Default.Add, "Più 30 minuti")
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(30, 60, 120, 180).forEach { min ->
                                    FilterChip(
                                        selected = overtimeMinutes == min,
                                        onClick = { overtimeMinutes = min },
                                        label = { Text(formatHoursMinutesV8(min)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (existing?.overtime == true && existing.overtimeMinutes == 0) {
                                Text("Questo vecchio straordinario non aveva una durata salvata. Imposta qui le ore corrette.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (type == ShiftType.VACATION) {
                item {
                    Surface(color = Color(0xFFF1EAFF), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BeachAccess, null, tint = V6Purple)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Durata ferie", fontWeight = FontWeight.Bold)
                                    Text("1 giorno = 8 ore. Puoi usare anche frazioni da 30 minuti.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                FilledTonalIconButton(onClick = { vacationMinutes = (vacationMinutes - 30).coerceAtLeast(30) }) {
                                    Icon(Icons.Default.Remove, "Meno 30 minuti")
                                }
                                Text(formatVacationV8(vacationMinutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = V6Purple)
                                FilledTonalIconButton(onClick = { vacationMinutes = (vacationMinutes + 30).coerceAtMost(8 * 60) }) {
                                    Icon(Icons.Default.Add, "Più 30 minuti")
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = vacationMinutes == 4 * 60, onClick = { vacationMinutes = 4 * 60 }, label = { Text("Mezza giornata 4h") }, modifier = Modifier.weight(1f))
                                FilterChip(selected = vacationMinutes == 8 * 60, onClick = { vacationMinutes = 8 * 60 }, label = { Text("Giornata 8h") }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        onSave(
                            ShiftEntry(
                                date = date,
                                type = type,
                                overtime = overtimeMinutes > 0,
                                overtimeMinutes = if (type in workTypes) overtimeMinutes else 0,
                                vacationMinutes = if (type == ShiftType.VACATION) vacationMinutes else 0
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Salva turno", fontWeight = FontWeight.Bold)
                }
            }
            if (existing != null) {
                item {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Elimina turno")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsV6(
    theme: String,
    onTheme: (String) -> Unit,
    onApply: (List<ShiftEntry>) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    backupMessage: String,
    vacationProfile: VacationProfileV8,
    allShifts: List<ShiftEntry>,
    onVacationProfileChange: (VacationProfileV8) -> Unit
) {
    var mode by remember { mutableStateOf(ScheduleModeV6.FIXED) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Impostazione turni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Scegli come lavori. Poi imposta il ciclo senza inventarti formule da NASA.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCardV6("1. Tipo di turnazione", "Le modalità più comuni più un ciclo completamente personalizzabile.", Icons.Default.Category, V6Blue) {
                ScheduleModeV6.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            val selected = mode == item
                            Surface(
                                color = if (selected) V6Blue.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) V6Blue else MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(15.dp),
                                modifier = Modifier.weight(1f).clickable { mode = item }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Icon(item.icon, null, tint = if (selected) V6Blue else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(6.dp))
                                    Text(item.title, fontWeight = FontWeight.Bold)
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            SettingsCardV6("2. Configura ${mode.title}", mode.subtitle, mode.icon, V6Green) {
                when (mode) {
                    ScheduleModeV6.FIXED -> FixedPlannerV6(onApply)
                    ScheduleModeV6.TWO_SHIFTS -> WeeklyPlannerV6(
                        title = "Alternanza 2 turni",
                        initial = listOf(ShiftType.MORNING, ShiftType.AFTERNOON),
                        allowed = listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.REST),
                        onApply = onApply
                    )
                    ScheduleModeV6.THREE_SHIFTS -> WeeklyPlannerV6(
                        title = "Alternanza 3 turni",
                        initial = listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT),
                        allowed = listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.REST),
                        onApply = onApply
                    )
                    ScheduleModeV6.WEEKLY -> WeeklyPlannerV6(
                        title = "Rotazione settimanale personalizzata",
                        initial = listOf(ShiftType.MORNING, ShiftType.NIGHT, ShiftType.AFTERNOON),
                        allowed = listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.REST),
                        onApply = onApply
                    )
                    ScheduleModeV6.CONTINUOUS_8H -> PresetCyclePlannerV6(
                        presets = continuous8PresetsV6(),
                        onApply = onApply
                    )
                    ScheduleModeV6.CONTINUOUS_12H -> PresetCyclePlannerV6(
                        presets = continuous12PresetsV6(),
                        onApply = onApply
                    )
                    ScheduleModeV6.CUSTOM -> CustomCyclePlannerV6(onApply)
                }
            }
        }
        item {
            SettingsCardV6("3. Legenda", "Stessi colori e icone in tutta l'app.", Icons.Default.Category, V6Orange) {
                ShiftType.entries.forEach { type ->
                    val v = visualV6(type)
                    Surface(color = v.bg, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(v.icon, null, tint = v.fg)
                            Spacer(Modifier.width(9.dp))
                            Text(type.label, fontWeight = FontWeight.SemiBold, color = v.fg)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
        item {
            SettingsCardV6("4. Aspetto", "Tema chiaro, scuro o automatico.", Icons.Default.Palette, V6Purple) {
                listOf("light" to "Chiaro", "dark" to "Scuro", "system" to "Automatico").forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onTheme(key) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == key, onClick = { onTheme(key) })
                        Text(label)
                    }
                }
            }
        }
        item {
            SettingsCardV6("5. Ferie e maturazione", "Scegli il contratto, la maturazione mensile e il saldo iniziale.", Icons.Default.BeachAccess, V6Purple) {
                VacationSettingsV8(vacationProfile, allShifts, onVacationProfileChange)
            }
        }
        item {
            SettingsCardV6("6. Backup e ripristino", "Salva turni, straordinari e impostazioni ferie per poter ripristinare tutto.", Icons.Default.Save, V6Teal) {
                Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Salva backup", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Restore, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Ripristina backup", fontWeight = FontWeight.Bold)
                }
                Text("Il backup contiene calendario, quantità di straordinario, ferie usate, contratto ferie, saldo iniziale e tema. Il file resta dove scegli tu sul telefono o nel cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (backupMessage.isNotBlank()) {
                    Surface(color = V6Green.copy(alpha = .10f), shape = RoundedCornerShape(10.dp)) {
                        Text(backupMessage, modifier = Modifier.fillMaxWidth().padding(10.dp), color = V6Green, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Nota", fontWeight = FontWeight.Bold)
                    Text("Quando applichi un ciclo, i giorni generati nello stesso intervallo sostituiscono eventuali turni già presenti. Puoi poi correggere singoli giorni dal calendario.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Turni Operai 8.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun VacationSettingsV8(
    profile: VacationProfileV8,
    shifts: List<ShiftEntry>,
    onChange: (VacationProfileV8) -> Unit
) {
    val currentPreset = vacationPresetV8(profile.contractKey)
    var startText by remember(profile.startMonth) { mutableStateOf(profile.startMonth) }
    var startMessage by remember { mutableStateOf("") }

    Text("Tipo di contratto", fontWeight = FontWeight.Bold)
    vacationPresetsV8.forEach { preset ->
        val selected = profile.contractKey == preset.key
        Surface(
            color = if (selected) V6Purple.copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) V6Purple else MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable {
                onChange(profile.copy(contractKey = preset.key, monthlyMinutes = preset.monthlyMinutes))
            }
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected,
                    onClick = { onChange(profile.copy(contractKey = preset.key, monthlyMinutes = preset.monthlyMinutes)) }
                )
                Column(Modifier.weight(1f)) {
                    Text(preset.label, fontWeight = FontWeight.Bold)
                    Text(preset.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    Text("Maturazione mensile", fontWeight = FontWeight.Bold)
    DurationStepperV8(
        valueMinutes = profile.monthlyMinutes,
        stepMinutes = if (profile.contractKey == "custom") 10 else 20,
        maxMinutes = 40 * 60,
        enabled = profile.contractKey == "custom",
        onChange = { onChange(profile.copy(monthlyMinutes = it)) }
    )
    if (profile.contractKey != "custom") {
        Text("Valore impostato dal profilo ${currentPreset.label}. Per modificarlo scegli Personalizzato.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Text("Saldo ferie iniziale", fontWeight = FontWeight.Bold)
    Text("Inserisci il saldo che leggi sul cedolino alla data di partenza del conteggio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DurationStepperV8(
        valueMinutes = profile.openingMinutes,
        stepMinutes = 30,
        maxMinutes = 1000 * 60,
        enabled = true,
        onChange = { onChange(profile.copy(openingMinutes = it)) }
    )

    Text("Inizio conteggio maturazione", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = startText,
        onValueChange = { startText = it },
        label = { Text("YYYY-MM-DD") },
        supportingText = { Text("Viene considerato il mese indicato. Il mese corrente matura dal giorno 15.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(
        onClick = {
            val parsed = runCatching { LocalDate.parse(startText) }.getOrNull()
            if (parsed == null) {
                startMessage = "Data non valida"
            } else {
                val normalized = parsed.withDayOfMonth(1).toString()
                startText = normalized
                onChange(profile.copy(startMonth = normalized))
                startMessage = "Data di partenza salvata"
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Check, null)
        Spacer(Modifier.width(6.dp))
        Text("Salva data inizio")
    }
    if (startMessage.isNotBlank()) Text(startMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

    val months = accruedMonthsV8(profile)
    val accrued = months * profile.monthlyMinutes
    val used = vacationUsedMinutesV8(shifts, profile)
    val balance = vacationBalanceMinutesV8(shifts, profile)
    Surface(color = Color(0xFFF1EAFF), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Situazione ferie", fontWeight = FontWeight.ExtraBold, color = V6Purple)
            Text("Mesi maturati: $months")
            Text("Maturate dal conteggio: ${formatVacationV8(accrued)} (${formatHoursMinutesV8(accrued)})")
            Text("Utilizzate: ${formatVacationV8(used)} (${formatHoursMinutesV8(used)})")
            Text("Residuo stimato: ${formatVacationV8(balance)} (${if (balance < 0) "-" else ""}${formatHoursMinutesV8(kotlin.math.abs(balance))})", fontWeight = FontWeight.Bold)
        }
    }
    Text("Il calcolo è gestionale: il cedolino resta il riferimento, perché anzianità, assenze e regole di rateo possono cambiare in base al CCNL e alla situazione personale.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DurationStepperV8(
    valueMinutes: Int,
    stepMinutes: Int,
    maxMinutes: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        FilledTonalIconButton(enabled = enabled, onClick = { onChange((valueMinutes - stepMinutes).coerceAtLeast(0)) }) {
            Icon(Icons.Default.Remove, "Riduci")
        }
        Text(formatHoursMinutesV8(valueMinutes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        FilledTonalIconButton(enabled = enabled, onClick = { onChange((valueMinutes + stepMinutes).coerceAtMost(maxMinutes)) }) {
            Icon(Icons.Default.Add, "Aumenta")
        }
    }
}

@Composable
private fun SettingsCardV6(title: String, subtitle: String, icon: ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(accent.copy(alpha = .12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun FixedPlannerV6(onApply: (List<ShiftEntry>) -> Unit) {
    var type by remember { mutableStateOf(ShiftType.MORNING) }
    var start by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    var weeks by remember { mutableIntStateOf(12) }
    var workDays by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var message by remember { mutableStateOf("") }

    Text("Turno da ripetere", fontWeight = FontWeight.Bold)
    ShiftPickerV6(type, listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT), "Turno") { type = it }
    WorkDaysV6(workDays) { workDays = it }
    DateFieldV6(start) { start = it }
    WeeksV6(weeks) { weeks = it }
    ApplyButtonV6("Genera turno fisso", message) {
        val date = runCatching { LocalDate.parse(start) }.getOrNull()
        when {
            date == null -> message = "Data non valida"
            workDays.isEmpty() -> message = "Seleziona almeno un giorno lavorativo"
            else -> {
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val generated = (0 until weeks * 7).map { i ->
                    val d = monday.plusDays(i.toLong())
                    ShiftEntry(d, if (d.dayOfWeek in workDays) type else ShiftType.REST)
                }
                onApply(generated)
                message = "Turno fisso applicato"
            }
        }
    }
}

@Composable
private fun WeeklyPlannerV6(
    title: String,
    initial: List<ShiftType>,
    allowed: List<ShiftType>,
    onApply: (List<ShiftEntry>) -> Unit
) {
    var sequence by remember(title) { mutableStateOf(initial) }
    var start by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    var weeks by remember { mutableIntStateOf(12) }
    var workDays by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var message by remember { mutableStateOf("") }

    Text(title, fontWeight = FontWeight.Bold)
    Text("Ogni voce vale per una settimana intera.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    sequence.forEachIndexed { index, current ->
        ShiftPickerV6(current, allowed, "Settimana ${index + 1}") { selected ->
            sequence = sequence.toMutableList().also { it[index] = selected }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (sequence.size < 8) sequence = sequence + ShiftType.REST }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Add, null)
            Text(" Aggiungi")
        }
        OutlinedButton(onClick = { if (sequence.size > 1) sequence = sequence.dropLast(1) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Remove, null)
            Text(" Togli")
        }
    }
    WorkDaysV6(workDays) { workDays = it }
    DateFieldV6(start) { start = it }
    WeeksV6(weeks) { weeks = it }
    PreviewSequenceV6(sequence, "Anteprima settimane")
    ApplyButtonV6("Applica rotazione", message) {
        val date = runCatching { LocalDate.parse(start) }.getOrNull()
        when {
            date == null -> message = "Data non valida"
            workDays.isEmpty() -> message = "Seleziona almeno un giorno lavorativo"
            else -> {
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val generated = buildList {
                    repeat(weeks) { w ->
                        val type = sequence[w % sequence.size]
                        repeat(7) { d ->
                            val day = monday.plusDays(w * 7L + d)
                            add(ShiftEntry(day, if (day.dayOfWeek in workDays) type else ShiftType.REST))
                        }
                    }
                }
                onApply(generated)
                message = "Rotazione applicata"
            }
        }
    }
}

@Composable
private fun PresetCyclePlannerV6(presets: List<CyclePresetV6>, onApply: (List<ShiftEntry>) -> Unit) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var days by remember { mutableIntStateOf(180) }
    var phase by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val selected = presets[selectedIndex.coerceIn(presets.indices)]
    val cycle = selected.cycle
    if (phase !in cycle.indices) phase = 0

    Text("Schema", fontWeight = FontWeight.Bold)
    presets.forEachIndexed { index, preset ->
        val active = index == selectedIndex
        Surface(
            color = if (active) V6Blue.copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (active) V6Blue else MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable { selectedIndex = index; phase = 0 }
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = active, onClick = { selectedIndex = index; phase = 0 })
                    Column(Modifier.weight(1f)) {
                        Text(preset.name, fontWeight = FontWeight.Bold)
                        Text(preset.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (preset.note.isNotBlank()) Text(preset.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    PreviewSequenceV6(cycle, "Ciclo selezionato")
    Text("Fase iniziale", fontWeight = FontWeight.Bold)
    Text("Se il giorno scelto non è il primo del ciclo, imposta qui da quale posizione partire.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { phase = if (phase <= 0) cycle.lastIndex else phase - 1 }) { Icon(Icons.Default.ChevronLeft, null) }
        val phaseType = cycle[phase]
        val v = visualV6(phaseType)
        Surface(color = v.bg, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(v.icon, null, tint = v.fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Giorno ${phase + 1}/${cycle.size}: ${phaseType.label}", fontWeight = FontWeight.Bold, color = v.fg)
            }
        }
        IconButton(onClick = { phase = if (phase >= cycle.lastIndex) 0 else phase + 1 }) { Icon(Icons.Default.ChevronRight, null) }
    }
    DateFieldV6(start) { start = it }
    DaysV6(days) { days = it }
    ApplyButtonV6("Genera ciclo", message) {
        val date = runCatching { LocalDate.parse(start) }.getOrNull()
        if (date == null) {
            message = "Data non valida"
        } else {
            val generated = (0 until days).map { i ->
                ShiftEntry(date.plusDays(i.toLong()), cycle[(i + phase) % cycle.size])
            }
            onApply(generated)
            message = "Ciclo ${selected.name} applicato"
        }
    }
}

@Composable
private fun CustomCyclePlannerV6(onApply: (List<ShiftEntry>) -> Unit) {
    val allowed = ShiftType.entries.toList()
    var cycle by remember { mutableStateOf(listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.REST)) }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var days by remember { mutableIntStateOf(180) }
    var phase by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    if (phase !in cycle.indices) phase = 0

    Text("Componi il ciclo", fontWeight = FontWeight.Bold)
    Text("Puoi mettere qualsiasi sequenza: turni, riposi, ferie, reperibilità, spezzato e così via.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    cycle.forEachIndexed { index, current ->
        ShiftPickerV6(current, allowed, "Giorno ${index + 1}") { selected ->
            cycle = cycle.toMutableList().also { it[index] = selected }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (cycle.size < 60) cycle = cycle + ShiftType.REST }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Add, null)
            Text(" Aggiungi giorno")
        }
        OutlinedButton(onClick = { if (cycle.size > 1) cycle = cycle.dropLast(1) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Remove, null)
            Text(" Togli")
        }
    }
    PreviewSequenceV6(cycle, "Anteprima ciclo")
    Text("Fase iniziale", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { phase = if (phase <= 0) cycle.lastIndex else phase - 1 }) { Icon(Icons.Default.ChevronLeft, null) }
        Text("Giorno ${phase + 1}/${cycle.size}: ${cycle[phase].label}", fontWeight = FontWeight.Bold)
        IconButton(onClick = { phase = if (phase >= cycle.lastIndex) 0 else phase + 1 }) { Icon(Icons.Default.ChevronRight, null) }
    }
    DateFieldV6(start) { start = it }
    DaysV6(days) { days = it }
    ApplyButtonV6("Genera ciclo personalizzato", message) {
        val date = runCatching { LocalDate.parse(start) }.getOrNull()
        if (date == null) {
            message = "Data non valida"
        } else {
            val generated = (0 until days).map { i -> ShiftEntry(date.plusDays(i.toLong()), cycle[(i + phase) % cycle.size]) }
            onApply(generated)
            message = "Ciclo personalizzato applicato"
        }
    }
}

@Composable
private fun ShiftPickerV6(current: ShiftType, allowed: List<ShiftType>, label: String, onSelected: (ShiftType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val v = visualV6(current)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(v.icon, null, tint = v.fg)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current.label, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            allowed.forEach { type ->
                val vv = visualV6(type)
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = { onSelected(type); open = false },
                    leadingIcon = { Icon(vv.icon, null, tint = vv.fg) }
                )
            }
        }
    }
}

@Composable
private fun WorkDaysV6(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    Text("Giorni lavorativi", fontWeight = FontWeight.Bold)
    DayOfWeek.entries.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { day ->
                val selected = day in days
                FilterChip(
                    selected = selected,
                    onClick = { onChange(if (selected) days - day else days + day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).take(2).uppercase()) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DateFieldV6(value: String, onChange: (String) -> Unit) {
    Text("Data di partenza", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("YYYY-MM-DD") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WeeksV6(value: Int, onChange: (Int) -> Unit) {
    Text("Durata", fontWeight = FontWeight.Bold)
    listOf(listOf(4, 8, 12), listOf(26, 52)).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { n ->
                FilterChip(selected = value == n, onClick = { onChange(n) }, label = { Text(if (n == 52) "1 anno" else "$n sett") }, modifier = Modifier.weight(1f))
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DaysV6(value: Int, onChange: (Int) -> Unit) {
    Text("Durata", fontWeight = FontWeight.Bold)
    listOf(listOf(30, 90, 180), listOf(365, 730)).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { n ->
                FilterChip(selected = value == n, onClick = { onChange(n) }, label = { Text(if (n == 365) "1 anno" else if (n == 730) "2 anni" else "$n gg") }, modifier = Modifier.weight(1f))
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PreviewSequenceV6(sequence: List<ShiftType>, title: String) {
    Surface(color = Color(0xFFE6F5FF), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
            Text(sequence.joinToString("  →  ") { it.short }, style = MaterialTheme.typography.bodySmall)
            Text("${sequence.size} giorni/step prima di ripetere", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0369A1))
        }
    }
}

@Composable
private fun ApplyButtonV6(label: String, message: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp)) {
        Icon(Icons.Default.Check, null)
        Spacer(Modifier.width(7.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}

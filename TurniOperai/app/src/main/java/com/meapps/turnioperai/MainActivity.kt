package com.meapps.turnioperai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.util.Locale

private val Green = Color(0xFF28A86B)
private val GreenSoft = Color(0xFFE8F7EF)
private val Orange = Color(0xFFF2A323)
private val OrangeSoft = Color(0xFFFFF4DC)
private val Blue = Color(0xFF4A74E8)
private val BlueSoft = Color(0xFFEAF0FF)
private val Purple = Color(0xFF8B5CF6)
private val PurpleSoft = Color(0xFFF2ECFF)
private val Red = Color(0xFFE45B62)
private val RedSoft = Color(0xFFFFEAEC)
private val Teal = Color(0xFF0F9F9A)
private val TealSoft = Color(0xFFE5F7F6)
private val GraySoft = Color(0xFFF1F3F5)

@OptIn(ExperimentalMaterial3Api::class)
enum class ShiftType(val label: String, val short: String, val defaultStart: String, val defaultEnd: String) {
    MORNING("Mattina", "M", "06:00", "14:00"),
    AFTERNOON("Pomeriggio", "P", "14:00", "22:00"),
    NIGHT("Notte", "N", "22:00", "06:00"),
    DAY("Giornaliero", "G", "08:00", "17:00"),
    REST("Riposo", "R", "", ""),
    VACATION("Ferie", "F", "", ""),
    SICK("Malattia", "Mal", "", ""),
    PERMIT("Permesso", "Per", "", ""),
    ROL("R.O.L.", "ROL", "", ""),
    ON_CALL("Reperibilità", "Rep", "", ""),
    SPLIT("Spezzato", "Sp", "08:00", "18:00"),
    HOLIDAY("Festivo lavorato", "Fest", "", ""),
    DOUBLE("Doppio turno", "2T", "", "")
}

data class ShiftEntry(
    val date: LocalDate,
    val type: ShiftType,
    val overtime: Boolean = false,
    val overtimeMinutes: Int = 0,
    val vacationMinutes: Int = 0,
    val rolMinutes: Int = 0
)

data class ShiftTimes(val start: String, val end: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurniOperaiApp(this) }
    }
}

@Composable
fun TurniOperaiApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("turni_operai", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "light") ?: "light") }
    var shifts by remember { mutableStateOf(loadShifts(prefs.getStringSet("shifts", emptySet()) ?: emptySet())) }
    var shiftTimes by remember { mutableStateOf(loadShiftTimes(prefs)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var pauseMinutes by remember { mutableIntStateOf(prefs.getInt("pause", 30)) }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = theme == "dark" || (theme == "system" && systemDark)
    val lightScheme = lightColorScheme(
        primary = Blue,
        secondary = Green,
        tertiary = Purple,
        background = Color(0xFFF8FAFD),
        surface = Color.White,
        surfaceVariant = Color(0xFFF1F4F8)
    )

    fun saveAll(list: List<ShiftEntry>) {
        shifts = list.sortedBy { it.date }
        prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}|${it.rolMinutes}" }.toSet()).apply()
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF8EACFF)) else lightScheme) {
        var tab by remember { mutableStateOf("home") }
        var editing by remember { mutableStateOf<LocalDate?>(null) }

        if (editing != null) {
            AddShiftScreen(
                date = editing!!,
                existing = shifts.find { it.date == editing },
                shiftTimes = shiftTimes,
                onBack = { editing = null },
                onSave = { entry -> saveAll(shifts.filterNot { it.date == entry.date } + entry); editing = null },
                onDelete = { saveAll(shifts.filterNot { it.date == editing }); editing = null }
            )
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        listOf(
                            Triple("home", "Home", Icons.Default.Home),
                            Triple("stats", "Statistiche", Icons.Default.BarChart),
                            Triple("calendar", "Calendario", Icons.Default.CalendarMonth),
                            Triple("settings", "Impostazioni", Icons.Default.Settings)
                        ).forEach { (key, label, icon) ->
                            NavigationBarItem(
                                selected = tab == key,
                                onClick = { tab = key },
                                icon = { Icon(icon, label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = BlueSoft)
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        "home" -> HomeScreen(shifts) { editing = it }
                        "stats" -> StatsScreen(shifts)
                        "calendar" -> CalendarScreen(shifts) { editing = it }
                        else -> SettingsScreen(
                            theme = theme,
                            setTheme = { value -> theme = value; prefs.edit().putString("theme", value).apply() },
                            shiftTimes = shiftTimes,
                            onShiftTimesChange = { type, times ->
                                shiftTimes = shiftTimes + (type to times)
                                prefs.edit().putString("time_${type.name}", "${times.start}|${times.end}").apply()
                            },
                            pauseMinutes = pauseMinutes,
                            setPauseMinutes = { pauseMinutes = it; prefs.edit().putInt("pause", it).apply() },
                            notifications = notifications,
                            setNotifications = { notifications = it; prefs.edit().putBoolean("notifications", it).apply() },
                            onApplyRotation = { start, cycle, days ->
                                val generatedDates = (0 until days).map { start.plusDays(it.toLong()) }.toSet()
                                val base = shifts.filterNot { it.date in generatedDates }
                                val generated = (0 until days).map { i -> ShiftEntry(start.plusDays(i.toLong()), cycle[i % cycle.size]) }
                                saveAll(base + generated)
                            },
                            onApplyWeekly = { start, weeks, pattern ->
                                val totalDays = weeks * 7
                                val generatedDates = (0 until totalDays).map { start.plusDays(it.toLong()) }.toSet()
                                val base = shifts.filterNot { it.date in generatedDates }
                                val generated = (0 until totalDays).map { i ->
                                    val date = start.plusDays(i.toLong())
                                    ShiftEntry(date, pattern[date.dayOfWeek] ?: ShiftType.REST)
                                }
                                saveAll(base + generated)
                            }
                        )
                    }
                }
            }
        }
    }
}

fun loadShifts(raw: Set<String>): List<ShiftEntry> = raw.mapNotNull { row ->
    runCatching {
        val p = row.split("|")
        val type = ShiftType.valueOf(p[1])
        val overtimeFlag = p.getOrNull(2)?.toBoolean() ?: false
        val overtimeMinutes = p.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val vacationMinutes = p.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0)
            ?: if (type == ShiftType.VACATION) 8 * 60 else 0
        val rolMinutes = p.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(0)
            ?: if (type == ShiftType.ROL) 8 * 60 else 0
        ShiftEntry(
            LocalDate.parse(p[0]),
            type,
            overtimeFlag || overtimeMinutes > 0,
            overtimeMinutes,
            vacationMinutes,
            rolMinutes
        )
    }.getOrNull()
}.sortedBy { it.date }

fun loadShiftTimes(prefs: android.content.SharedPreferences): Map<ShiftType, ShiftTimes> = ShiftType.entries.associateWith { type ->
    val raw = prefs.getString("time_${type.name}", null)
    if (raw != null && raw.contains("|")) {
        val p = raw.split("|")
        ShiftTimes(p.getOrElse(0) { type.defaultStart }, p.getOrElse(1) { type.defaultEnd })
    } else ShiftTimes(type.defaultStart, type.defaultEnd)
}

fun shiftColors(type: ShiftType?): Pair<Color, Color> = when (type) {
    ShiftType.MORNING -> GreenSoft to Green
    ShiftType.AFTERNOON -> OrangeSoft to Orange
    ShiftType.NIGHT -> BlueSoft to Blue
    ShiftType.DAY -> TealSoft to Teal
    ShiftType.REST -> GraySoft to Color(0xFF667085)
    ShiftType.VACATION -> PurpleSoft to Purple
    ShiftType.SICK -> RedSoft to Red
    ShiftType.PERMIT -> Color(0xFFFFF0E5) to Color(0xFFD97706)
    ShiftType.ROL -> Color(0xFFE8F4FF) to Color(0xFF2563EB)
    ShiftType.ON_CALL -> Color(0xFFE9F6FF) to Color(0xFF0284C7)
    ShiftType.SPLIT -> Color(0xFFFFF0F7) to Color(0xFFDB2777)
    ShiftType.HOLIDAY -> Color(0xFFFFECEC) to Color(0xFFDC2626)
    ShiftType.DOUBLE -> Color(0xFFECEBFF) to Color(0xFF6D5CE7)
    null -> GraySoft to Color(0xFF98A2B3)
}

@Composable
fun HomeScreen(shifts: List<ShiftEntry>, onDateClick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val locale = Locale.ITALIAN
    val title = month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " ${month.year}"
    val monthShifts = shifts.filter { YearMonth.from(it.date) == month }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Turni Operai", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("I tuoi turni senza casino", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
                    }
                    MonthGrid(month, shifts, onDateClick)
                }
            }
        }
        item { MonthlySummary(monthShifts) }
        item {
            Button(
                onClick = { onDateClick(LocalDate.now()) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Aggiungi turno", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MonthGrid(month: YearMonth, shifts: List<ShiftEntry>, onDateClick: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val total = month.lengthOfMonth()
    val labels = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        repeat((offset + total + 6) / 7) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - offset + 1
                    if (day in 1..total) {
                        val date = month.atDay(day)
                        DayCell(date, shifts.find { it.date == date }, Modifier.weight(1f), onDateClick)
                    } else Spacer(Modifier.weight(1f).height(53.dp))
                }
            }
        }
    }
}

@Composable
fun DayCell(date: LocalDate, shift: ShiftEntry?, modifier: Modifier, onDateClick: (LocalDate) -> Unit) {
    val (bg, accent) = shiftColors(shift?.type)
    Column(
        modifier.padding(2.dp).height(53.dp).background(bg, RoundedCornerShape(13.dp)).clickable { onDateClick(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(date.dayOfMonth.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.ExtraBold else FontWeight.Medium)
        if (shift != null) Text(shift.type.short, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = accent)
    }
}

@Composable
fun MonthlySummary(shifts: List<ShiftEntry>) {
    val workTypes = setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT, ShiftType.HOLIDAY, ShiftType.DOUBLE)
    val work = shifts.count { it.type in workTypes }
    val nights = shifts.count { it.type == ShiftType.NIGHT }
    val overtime = shifts.count { it.overtime }
    val rests = shifts.count { it.type == ShiftType.REST }
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Riepilogo mese", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Ore", (work * 8).toString(), Icons.Default.Schedule, GreenSoft, Green, Modifier.weight(1f))
                StatCard("Notti", nights.toString(), Icons.Default.DarkMode, BlueSoft, Blue, Modifier.weight(1f))
                StatCard("Straord.", overtime.toString(), Icons.Default.Bolt, OrangeSoft, Orange, Modifier.weight(1f))
                StatCard("Riposi", rests.toString(), Icons.Default.Hotel, PurpleSoft, Purple, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, bg: Color, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(16.dp)).padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = accent)
        Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShiftScreen(date: LocalDate, existing: ShiftEntry?, shiftTimes: Map<ShiftType, ShiftTimes>, onBack: () -> Unit, onSave: (ShiftEntry) -> Unit, onDelete: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ShiftType.MORNING) }
    var overtime by remember { mutableStateOf(existing?.overtime ?: false) }
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "Aggiungi turno" else "Modifica turno") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = Blue)
                        Spacer(Modifier.width(10.dp))
                        Text(date.format(formatter).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text("Tipo di turno", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                ShiftType.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { st ->
                            val (bg, accent) = shiftColors(st)
                            FilterChip(
                                selected = type == st,
                                onClick = { type = st },
                                label = { Text(st.label, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bg, selectedLabelColor = accent)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                val times = shiftTimes[type] ?: ShiftTimes(type.defaultStart, type.defaultEnd)
                if (times.start.isNotBlank() || times.end.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Orario predefinito")
                            Text("${times.start} - ${times.end}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Switch(overtime, { overtime = it })
                        Spacer(Modifier.width(10.dp))
                        Column { Text("Straordinario", fontWeight = FontWeight.Bold); Text("Segna questo turno come straordinario", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { Button({ onSave(ShiftEntry(date, type, overtime)) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Salva turno", fontWeight = FontWeight.Bold) } }
            if (existing != null) item { OutlinedButton(onDelete, Modifier.fillMaxWidth()) { Text("Elimina turno") } }
        }
    }
}

@Composable
fun StatsScreen(shifts: List<ShiftEntry>) {
    val month = shifts.filter { YearMonth.from(it.date) == YearMonth.now() }
    val grouped = month.groupingBy { it.type }.eachCount()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { MonthlySummary(month) }
        items(ShiftType.entries) { type ->
            val (bg, accent) = shiftColors(type)
            Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(accent, RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(type.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text((grouped[type] ?: 0).toString(), fontWeight = FontWeight.ExtraBold, color = accent)
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(shifts: List<ShiftEntry>, onDateClick: (LocalDate) -> Unit) {
    val ordered = shifts.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Calendario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)) }
        if (ordered.isEmpty()) item { Text("Nessun turno inserito.") }
        items(ordered) { s ->
            val (bg, accent) = shiftColors(s.type)
            Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { onDateClick(s.date) }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.type.label, fontWeight = FontWeight.Bold)
                        Text(s.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = accent)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    theme: String,
    setTheme: (String) -> Unit,
    shiftTimes: Map<ShiftType, ShiftTimes>,
    onShiftTimesChange: (ShiftType, ShiftTimes) -> Unit,
    pauseMinutes: Int,
    setPauseMinutes: (Int) -> Unit,
    notifications: Boolean,
    setNotifications: (Boolean) -> Unit,
    onApplyRotation: (LocalDate, List<ShiftType>, Int) -> Unit,
    onApplyWeekly: (LocalDate, Int, Map<DayOfWeek, ShiftType>) -> Unit
) {
    var expandedSection by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Impostazioni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Configura l'app sul tuo ciclo reale", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard("Aspetto", "Tema chiaro, scuro o automatico", Icons.Default.Palette, BlueSoft, Blue) {
                listOf("light" to "Chiaro", "dark" to "Scuro", "system" to "Automatico").forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().clickable { setTheme(key) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(theme == key, { setTheme(key) })
                        Text(label)
                    }
                }
            }
        }
        item {
            SettingsCard("Orari dei turni", "Personalizza gli orari standard", Icons.Default.Schedule, GreenSoft, Green) {
                listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT).forEach { type ->
                    val t = shiftTimes[type] ?: ShiftTimes(type.defaultStart, type.defaultEnd)
                    ShiftTimeEditor(type, t) { onShiftTimesChange(type, it) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Pausa predefinita", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(0, 15, 30, 45, 60).forEach { min ->
                        FilterChip(selected = pauseMinutes == min, onClick = { setPauseMinutes(min) }, label = { Text("${min}m") }, modifier = Modifier.padding(end = 5.dp))
                    }
                }
            }
        }
        item {
            SettingsCard("Programmazione settimanale", "Turni fissi dal lunedì alla domenica", Icons.Default.ViewWeek, TealSoft, Teal) {
                WeeklyPlanner(onApplyWeekly)
            }
        }
        item {
            SettingsCard("Rotazione automatica", "Cicli M P N R e rotazioni personalizzate", Icons.Default.Autorenew, PurpleSoft, Purple) {
                RotationPlanner(onApplyRotation)
            }
        }
        item {
            SettingsCard("Tipi di giornata disponibili", "Lavoro, assenze e casi speciali", Icons.Default.ListAlt, OrangeSoft, Orange) {
                ShiftType.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { type ->
                            val (bg, accent) = shiftColors(type)
                            Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text(type.label, Modifier.padding(10.dp), color = accent, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        item {
            SettingsCard("Notifiche", "Promemoria prima del turno", Icons.Default.Notifications, RedSoft, Red) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Notifiche turni", fontWeight = FontWeight.Bold); Text("Attiva i promemoria", style = MaterialTheme.typography.bodySmall) }
                    Switch(notifications, setNotifications)
                }
            }
        }
        item { Text("Turni Operai 2.0", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
    }
}

@Composable
fun SettingsCard(title: String, subtitle: String, icon: ImageVector, bg: Color, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(bg, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
                Spacer(Modifier.width(12.dp))
                Column { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
fun ShiftTimeEditor(type: ShiftType, times: ShiftTimes, onChange: (ShiftTimes) -> Unit) {
    var start by remember(times.start) { mutableStateOf(times.start) }
    var end by remember(times.end) { mutableStateOf(times.end) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(type.label, Modifier.width(105.dp), fontWeight = FontWeight.SemiBold)
        OutlinedTextField(start, { value -> start = value.take(5); if (value.length == 5) onChange(ShiftTimes(start, end)) }, label = { Text("Inizio") }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(end, { value -> end = value.take(5); if (value.length == 5) onChange(ShiftTimes(start, end)) }, label = { Text("Fine") }, singleLine = true, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyPlanner(onApply: (LocalDate, Int, Map<DayOfWeek, ShiftType>) -> Unit) {
    var startText by remember { mutableStateOf(LocalDate.now().toString()) }
    var weeks by remember { mutableIntStateOf(4) }
    var pattern by remember {
        mutableStateOf(
            DayOfWeek.entries.associateWith { day -> if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) ShiftType.REST else ShiftType.MORNING }
        )
    }
    var message by remember { mutableStateOf("") }

    Text("Schema settimanale", fontWeight = FontWeight.Bold)
    DayOfWeek.entries.forEach { day ->
        var open by remember { mutableStateOf(false) }
        val current = pattern[day] ?: ShiftType.REST
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = !open }) {
            OutlinedTextField(
                value = current.label,
                onValueChange = {},
                readOnly = true,
                label = { Text(day.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ShiftType.entries.forEach { type -> DropdownMenuItem(text = { Text(type.label) }, onClick = { pattern = pattern + (day to type); open = false }) }
            }
        }
    }
    OutlinedTextField(startText, { startText = it }, label = { Text("Data inizio YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Durata")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1, 4, 8, 12, 26, 52).forEach { n -> FilterChip(selected = weeks == n, onClick = { weeks = n }, label = { Text(if (n == 1) "1 sett" else "$n sett") }) }
    }
    Button(onClick = {
        runCatching { LocalDate.parse(startText) }.onSuccess { onApply(it, weeks, pattern); message = "Programmazione applicata per $weeks settimane" }.onFailure { message = "Data non valida" }
    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Applica settimana tipo") }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun RotationPlanner(onApply: (LocalDate, List<ShiftType>, Int) -> Unit) {
    val presets = linkedMapOf(
        "M P N R" to listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.REST),
        "2M 2P 2N 2R" to listOf(ShiftType.MORNING, ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.NIGHT, ShiftType.REST, ShiftType.REST),
        "M M P P N N R R" to listOf(ShiftType.MORNING, ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.NIGHT, ShiftType.REST, ShiftType.REST),
        "4M 2R 4P 2R 4N 2R" to buildList { repeat(4){ add(ShiftType.MORNING) }; repeat(2){ add(ShiftType.REST) }; repeat(4){ add(ShiftType.AFTERNOON) }; repeat(2){ add(ShiftType.REST) }; repeat(4){ add(ShiftType.NIGHT) }; repeat(2){ add(ShiftType.REST) } }
    )
    var selectedName by remember { mutableStateOf(presets.keys.first()) }
    var custom by remember { mutableStateOf("M,P,N,R") }
    var customMode by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf(LocalDate.now().toString()) }
    var days by remember { mutableIntStateOf(90) }
    var message by remember { mutableStateOf("") }

    Text("Scegli una rotazione", fontWeight = FontWeight.Bold)
    presets.forEach { (name, _) ->
        Row(Modifier.fillMaxWidth().clickable { selectedName = name; customMode = false }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !customMode && selectedName == name, onClick = { selectedName = name; customMode = false })
            Text(name)
        }
    }
    Row(Modifier.fillMaxWidth().clickable { customMode = true }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = customMode, onClick = { customMode = true })
        Text("Rotazione personalizzata")
    }
    if (customMode) {
        OutlinedTextField(custom, { custom = it.uppercase() }, label = { Text("Codici separati da virgola") }, supportingText = { Text("M mattina, P pomeriggio, N notte, R riposo, G giornaliero") }, modifier = Modifier.fillMaxWidth())
    }
    OutlinedTextField(startText, { startText = it }, label = { Text("Data inizio YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Durata rotazione")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(30, 90, 180, 365).forEach { n -> FilterChip(selected = days == n, onClick = { days = n }, label = { Text(if (n == 365) "1 anno" else "$n gg") }) }
    }
    Button(onClick = {
        val cycle = if (customMode) parseCycle(custom) else presets[selectedName].orEmpty()
        val date = runCatching { LocalDate.parse(startText) }.getOrNull()
        if (date == null) message = "Data non valida"
        else if (cycle.isEmpty()) message = "Rotazione non valida"
        else { onApply(date, cycle, days); message = "Rotazione applicata per $days giorni" }
    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Autorenew, null); Spacer(Modifier.width(6.dp)); Text("Genera rotazione") }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
}

fun parseCycle(raw: String): List<ShiftType> {
    val map = mapOf(
        "M" to ShiftType.MORNING, "P" to ShiftType.AFTERNOON, "N" to ShiftType.NIGHT, "R" to ShiftType.REST,
        "G" to ShiftType.DAY, "F" to ShiftType.VACATION, "MAL" to ShiftType.SICK, "PER" to ShiftType.PERMIT, "ROL" to ShiftType.ROL,
        "REP" to ShiftType.ON_CALL, "SP" to ShiftType.SPLIT, "FEST" to ShiftType.HOLIDAY, "2T" to ShiftType.DOUBLE
    )
    return raw.split(",", ";", " ").mapNotNull { map[it.trim().uppercase()] }
}

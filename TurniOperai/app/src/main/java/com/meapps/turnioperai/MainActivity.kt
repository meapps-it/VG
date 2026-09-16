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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class ShiftType(val label: String, val short: String, val start: String, val end: String) {
    MORNING("Mattina", "M", "06:00", "14:00"),
    AFTERNOON("Pomeriggio", "P", "14:00", "22:00"),
    NIGHT("Notte", "N", "22:00", "06:00"),
    REST("Riposo", "R", "", ""),
    VACATION("Ferie", "F", "", ""),
    SICK("Malattia", "Mal", "", ""),
    PERMIT("Permesso", "Per", "", "")
}

data class ShiftEntry(val date: LocalDate, val type: ShiftType, val overtime: Boolean = false)

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
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = theme == "dark" || (theme == "system" && systemDark)

    fun saveAll(list: List<ShiftEntry>) {
        shifts = list
        prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}" }.toSet()).apply()
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        var tab by remember { mutableStateOf("home") }
        var editing by remember { mutableStateOf<LocalDate?>(null) }

        if (editing != null) {
            AddShiftScreen(
                date = editing!!,
                existing = shifts.find { it.date == editing },
                onBack = { editing = null },
                onSave = { entry -> saveAll(shifts.filterNot { it.date == entry.date } + entry); editing = null },
                onDelete = { saveAll(shifts.filterNot { it.date == editing }); editing = null }
            )
        } else {
            Scaffold(bottomBar = {
                NavigationBar {
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
                            label = { Text(label) }
                        )
                    }
                }
            }) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        "home" -> HomeScreen(shifts) { editing = it }
                        "stats" -> StatsScreen(shifts)
                        "calendar" -> CalendarScreen(shifts) { editing = it }
                        else -> SettingsScreen(theme) { value -> theme = value; prefs.edit().putString("theme", value).apply() }
                    }
                }
            }
        }
    }
}

fun loadShifts(raw: Set<String>): List<ShiftEntry> = raw.mapNotNull { row ->
    runCatching {
        val p = row.split("|")
        ShiftEntry(LocalDate.parse(p[0]), ShiftType.valueOf(p[1]), p.getOrNull(2)?.toBoolean() ?: false)
    }.getOrNull()
}.sortedBy { it.date }

@Composable
fun HomeScreen(shifts: List<ShiftEntry>, onDateClick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val locale = Locale.ITALIAN
    val title = month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " ${month.year}"
    val monthShifts = shifts.filter { YearMonth.from(it.date) == month }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Turni Operai", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Organizza il tuo lavoro, un giorno alla volta", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
            }
            MonthGrid(month, shifts, onDateClick)
        }
        item { MonthlySummary(monthShifts) }
        item {
            Button(onClick = { onDateClick(LocalDate.now()) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Aggiungi turno")
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
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
        }
        repeat((offset + total + 6) / 7) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - offset + 1
                    if (day in 1..total) {
                        val date = month.atDay(day)
                        DayCell(date, shifts.find { it.date == date }, Modifier.weight(1f), onDateClick)
                    } else Spacer(Modifier.weight(1f).height(50.dp))
                }
            }
        }
    }
}

@Composable
fun DayCell(date: LocalDate, shift: ShiftEntry?, modifier: Modifier, onDateClick: (LocalDate) -> Unit) {
    val bg = when (shift?.type) {
        ShiftType.MORNING -> Color(0xFFE7F8EE)
        ShiftType.AFTERNOON -> Color(0xFFFFF3D7)
        ShiftType.NIGHT -> Color(0xFFE7EFFF)
        ShiftType.REST -> Color(0xFFF0F2F4)
        ShiftType.VACATION -> Color(0xFFF3EAFE)
        ShiftType.SICK -> Color(0xFFFFE9EA)
        ShiftType.PERMIT -> Color(0xFFF8EFE7)
        null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
    }
    Column(
        modifier.padding(2.dp).height(50.dp).background(bg, RoundedCornerShape(12.dp)).clickable { onDateClick(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(date.dayOfMonth.toString(), fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
        if (shift != null) Text(shift.type.short, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MonthlySummary(shifts: List<ShiftEntry>) {
    val work = shifts.count { it.type == ShiftType.MORNING || it.type == ShiftType.AFTERNOON || it.type == ShiftType.NIGHT }
    val nights = shifts.count { it.type == ShiftType.NIGHT }
    val overtime = shifts.count { it.overtime }
    val rests = shifts.count { it.type == ShiftType.REST }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Riepilogo mese", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Ore", (work * 8).toString(), Icons.Default.Schedule)
                Stat("Notti", nights.toString(), Icons.Default.DarkMode)
                Stat("Straord.", overtime.toString(), Icons.Default.Bolt)
                Stat("Riposi", rests.toString(), Icons.Default.Hotel)
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null)
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AddShiftScreen(date: LocalDate, existing: ShiftEntry?, onBack: () -> Unit, onSave: (ShiftEntry) -> Unit, onDelete: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ShiftType.MORNING) }
    var overtime by remember { mutableStateOf(existing?.overtime ?: false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "Aggiungi turno" else "Modifica turno") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(date.toString(), style = MaterialTheme.typography.titleMedium) }
            item {
                Text("Tipo di turno", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ShiftType.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { st ->
                            FilterChip(selected = type == st, onClick = { type = st }, label = { Text(st.label) }, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(overtime, { overtime = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Segna come straordinario")
                }
            }
            item { Button({ onSave(ShiftEntry(date, type, overtime)) }, Modifier.fillMaxWidth()) { Text("Salva turno") } }
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
            ListItem(headlineContent = { Text(type.label) }, trailingContent = { Text((grouped[type] ?: 0).toString(), fontWeight = FontWeight.Bold) })
            HorizontalDivider()
        }
    }
}

@Composable
fun CalendarScreen(shifts: List<ShiftEntry>, onDateClick: (LocalDate) -> Unit) {
    val ordered = shifts.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Calendario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)) }
        if (ordered.isEmpty()) item { Text("Nessun turno inserito.") }
        items(ordered) { s ->
            ListItem(
                headlineContent = { Text(s.type.label) },
                supportingContent = { Text(s.date.toString()) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clickable { onDateClick(s.date) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun SettingsScreen(theme: String, setTheme: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Impostazioni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Aspetto", fontWeight = FontWeight.Bold)
                    listOf("light" to "Chiaro", "dark" to "Scuro", "system" to "Automatico").forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth().clickable { setTheme(key) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(theme == key, { setTheme(key) })
                            Text(label)
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Turni predefiniti", fontWeight = FontWeight.Bold)
                    Text("Mattina 06:00 - 14:00")
                    Text("Pomeriggio 14:00 - 22:00")
                    Text("Notte 22:00 - 06:00")
                }
            }
        }
        item { Text("Versione 1.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

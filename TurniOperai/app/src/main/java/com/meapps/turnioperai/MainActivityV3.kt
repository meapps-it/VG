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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val V3Blue = Color(0xFF4A74E8)
private val V3Green = Color(0xFF28A86B)
private val V3Orange = Color(0xFFF2A323)
private val V3Purple = Color(0xFF8B5CF6)
private val V3Red = Color(0xFFE45B62)
private val V3Teal = Color(0xFF0F9F9A)

class MainActivityV3 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurniOperaiAppV3(this) }
    }
}

@Composable
fun TurniOperaiAppV3(context: Context) {
    val prefs = remember { context.getSharedPreferences("turni_operai", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "light") ?: "light") }
    var shifts by remember { mutableStateOf(loadShifts(prefs.getStringSet("shifts", emptySet()) ?: emptySet())) }
    val dark = theme == "dark" || (theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())

    fun save(list: List<ShiftEntry>) {
        shifts = list.sortedBy { it.date }
        prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}" }.toSet()).apply()
    }

    val light = lightColorScheme(
        primary = V3Blue,
        secondary = V3Green,
        tertiary = V3Purple,
        background = Color(0xFFF7F9FC),
        surface = Color.White
    )

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9DB5FF)) else light) {
        var tab by remember { mutableStateOf(0) }
        var editDate by remember { mutableStateOf<LocalDate?>(null) }

        if (editDate != null) {
            EditShiftV3(
                date = editDate!!,
                existing = shifts.find { it.date == editDate },
                onBack = { editDate = null },
                onSave = { entry -> save(shifts.filterNot { it.date == entry.date } + entry); editDate = null },
                onDelete = { save(shifts.filterNot { it.date == editDate }); editDate = null }
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        val nav = listOf(
                            Triple("Home", Icons.Default.Home, 0),
                            Triple("Statistiche", Icons.Default.BarChart, 1),
                            Triple("Calendario", Icons.Default.CalendarMonth, 2),
                            Triple("Impostazioni", Icons.Default.Settings, 3)
                        )
                        nav.forEach { (label, icon, idx) ->
                            NavigationBarItem(
                                selected = tab == idx,
                                onClick = { tab = idx },
                                icon = { Icon(icon, label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> HomeV3(shifts) { editDate = it }
                        1 -> StatsV3(shifts)
                        2 -> CalendarV3(shifts) { editDate = it }
                        else -> SettingsV3(
                            theme = theme,
                            onTheme = { theme = it; prefs.edit().putString("theme", it).apply() },
                            onWholeWeekRotation = { start, sequence, totalWeeks, workDays ->
                                val startMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                val totalDays = totalWeeks * 7
                                val range = (0 until totalDays).map { startMonday.plusDays(it.toLong()) }.toSet()
                                val base = shifts.filterNot { it.date in range }
                                val generated = buildList {
                                    repeat(totalWeeks) { weekIndex ->
                                        val weekType = sequence[weekIndex % sequence.size]
                                        repeat(7) { d ->
                                            val date = startMonday.plusDays((weekIndex * 7L) + d)
                                            add(ShiftEntry(date, if (date.dayOfWeek in workDays) weekType else ShiftType.REST))
                                        }
                                    }
                                }
                                save(base + generated)
                            },
                            onDayRotation = { start, cycle, days ->
                                val range = (0 until days).map { start.plusDays(it.toLong()) }.toSet()
                                val base = shifts.filterNot { it.date in range }
                                val generated = (0 until days).map { i -> ShiftEntry(start.plusDays(i.toLong()), cycle[i % cycle.size]) }
                                save(base + generated)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeV3(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val locale = Locale.ITALIAN
    val title = month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " ${month.year}"
    val monthShifts = shifts.filter { YearMonth.from(it.date) == month }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = V3Blue, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(50.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CalendarMonth, null, tint = Color.White) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Turni Operai", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Turni chiari. Vita meno incasinata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
                    }
                    MonthGridV3(month, shifts, onDate)
                }
            }
        }
        item { SummaryV3(monthShifts) }
        item {
            Button(onClick = { onDate(LocalDate.now()) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi turno", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MonthGridV3(month: YearMonth, shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val labels = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        repeat((offset + days + 6) / 7) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - offset + 1
                    if (day in 1..days) {
                        val date = month.atDay(day)
                        val shift = shifts.find { it.date == date }
                        val (bg, fg) = colorForV3(shift?.type)
                        Column(
                            Modifier.weight(1f).padding(2.dp).height(52.dp).background(bg, RoundedCornerShape(12.dp)).clickable { onDate(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(day.toString(), fontWeight = FontWeight.Bold)
                            if (shift != null) Text(shift.type.short, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    } else Spacer(Modifier.weight(1f).height(52.dp))
                }
            }
        }
    }
}

private fun colorForV3(type: ShiftType?): Pair<Color, Color> = when (type) {
    ShiftType.MORNING -> Color(0xFFE6F7EE) to V3Green
    ShiftType.AFTERNOON -> Color(0xFFFFF2D8) to V3Orange
    ShiftType.NIGHT -> Color(0xFFE9EFFF) to V3Blue
    ShiftType.DAY -> Color(0xFFE5F7F6) to V3Teal
    ShiftType.REST -> Color(0xFFF0F2F4) to Color(0xFF687386)
    ShiftType.VACATION -> Color(0xFFF2ECFF) to V3Purple
    ShiftType.SICK -> Color(0xFFFFEAEC) to V3Red
    ShiftType.PERMIT -> Color(0xFFFFF0E5) to Color(0xFFD97706)
    ShiftType.ON_CALL -> Color(0xFFE9F6FF) to Color(0xFF0284C7)
    ShiftType.SPLIT -> Color(0xFFFFF0F7) to Color(0xFFDB2777)
    ShiftType.HOLIDAY -> Color(0xFFFFECEC) to Color(0xFFDC2626)
    ShiftType.DOUBLE -> Color(0xFFECEBFF) to Color(0xFF6D5CE7)
    null -> Color(0xFFF5F6F8) to Color(0xFF98A2B3)
}

@Composable
private fun SummaryV3(shifts: List<ShiftEntry>) {
    val work = shifts.count { it.type in setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT, ShiftType.HOLIDAY, ShiftType.DOUBLE) }
    val nights = shifts.count { it.type == ShiftType.NIGHT }
    val rests = shifts.count { it.type == ShiftType.REST }
    val overtime = shifts.count { it.overtime }
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Riepilogo mese", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatV3("Ore", "${work * 8}", Color(0xFFE6F7EE), V3Green, Modifier.weight(1f))
                MiniStatV3("Notti", "$nights", Color(0xFFE9EFFF), V3Blue, Modifier.weight(1f))
                MiniStatV3("Straord.", "$overtime", Color(0xFFFFF2D8), V3Orange, Modifier.weight(1f))
                MiniStatV3("Riposi", "$rests", Color(0xFFF2ECFF), V3Purple, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStatV3(label: String, value: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(14.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, color = fg)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatsV3(shifts: List<ShiftEntry>) {
    val month = shifts.filter { YearMonth.from(it.date) == YearMonth.now() }
    val counts = month.groupingBy { it.type }.eachCount()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        item { SummaryV3(month) }
        items(ShiftType.entries) { type ->
            val (bg, fg) = colorForV3(type)
            Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(type.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${counts[type] ?: 0}", color = fg, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun CalendarV3(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Calendario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        if (shifts.isEmpty()) item { Text("Nessun turno inserito.") }
        items(shifts.sortedByDescending { it.date }) { s ->
            val (bg, fg) = colorForV3(s.type)
            Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = Modifier.clickable { onDate(s.date) }, shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.date.dayOfMonth.toString(), fontWeight = FontWeight.ExtraBold, color = fg, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.type.label, fontWeight = FontWeight.Bold)
                        Text(s.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = fg)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditShiftV3(date: LocalDate, existing: ShiftEntry?, onBack: () -> Unit, onSave: (ShiftEntry) -> Unit, onDelete: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ShiftType.MORNING) }
    var overtime by remember { mutableStateOf(existing?.overtime ?: false) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "Aggiungi turno" else "Modifica turno") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) }
            item {
                ShiftType.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { st ->
                            val (bg, fg) = colorForV3(st)
                            FilterChip(selected = type == st, onClick = { type = st }, label = { Text(st.label) }, modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = bg, selectedLabelColor = fg))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(overtime, { overtime = it }); Spacer(Modifier.width(8.dp)); Text("Straordinario") } }
            item { Button({ onSave(ShiftEntry(date, type, overtime)) }, Modifier.fillMaxWidth()) { Text("Salva turno") } }
            if (existing != null) item { OutlinedButton(onDelete, Modifier.fillMaxWidth()) { Text("Elimina turno") } }
        }
    }
}

@Composable
private fun SettingsV3(
    theme: String,
    onTheme: (String) -> Unit,
    onWholeWeekRotation: (LocalDate, List<ShiftType>, Int, Set<DayOfWeek>) -> Unit,
    onDayRotation: (LocalDate, List<ShiftType>, Int) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Impostazioni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        item {
            V3SettingsCard("Aspetto", "Chiaro, scuro o automatico", Icons.Default.Palette, Color(0xFFE9EFFF), V3Blue) {
                listOf("light" to "Chiaro", "dark" to "Scuro", "system" to "Automatico").forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onTheme(k) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(theme == k, { onTheme(k) }); Text(label)
                    }
                }
            }
        }
        item {
            V3SettingsCard("Rotazione a settimane intere", "Una settimana Mattino, poi Notte, poi Pomeriggio", Icons.Default.ViewWeek, Color(0xFFE6F7EE), V3Green) {
                WholeWeekRotationPlannerV3(onWholeWeekRotation)
            }
        }
        item {
            V3SettingsCard("Rotazione a giorni", "Cicli M P N R e sequenze personalizzate", Icons.Default.Autorenew, Color(0xFFF2ECFF), V3Purple) {
                DayRotationPlannerV3(onDayRotation)
            }
        }
        item {
            V3SettingsCard("Turni disponibili", "Tutti i tipi selezionabili nel calendario", Icons.Default.ListAlt, Color(0xFFFFF2D8), V3Orange) {
                ShiftType.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { t ->
                            val (bg, fg) = colorForV3(t)
                            Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text(t.label, Modifier.padding(10.dp), color = fg, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        item { Text("Turni Operai 3.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun V3SettingsCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, fg: Color, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(bg, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg) }
                Spacer(Modifier.width(12.dp))
                Column { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WholeWeekRotationPlannerV3(onApply: (LocalDate, List<ShiftType>, Int, Set<DayOfWeek>) -> Unit) {
    val allowed = listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.REST)
    var sequence by remember { mutableStateOf(listOf(ShiftType.MORNING, ShiftType.NIGHT, ShiftType.AFTERNOON)) }
    var start by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    var totalWeeks by remember { mutableIntStateOf(12) }
    var workDays by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var message by remember { mutableStateOf("") }

    Text("Sequenza delle settimane", fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "M-N-P" to listOf(ShiftType.MORNING, ShiftType.NIGHT, ShiftType.AFTERNOON),
            "M-P-N" to listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT),
            "P-M-N" to listOf(ShiftType.AFTERNOON, ShiftType.MORNING, ShiftType.NIGHT)
        ).forEach { (label, seq) ->
            FilterChip(selected = sequence == seq, onClick = { sequence = seq }, label = { Text(label) })
        }
    }

    sequence.forEachIndexed { idx, current ->
        var open by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = !open }) {
            OutlinedTextField(
                value = current.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Settimana ${idx + 1}") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                allowed.forEach { t -> DropdownMenuItem(text = { Text(t.label) }, onClick = { sequence = sequence.toMutableList().also { it[idx] = t }; open = false }) }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (sequence.size < 6) sequence = sequence + ShiftType.REST }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" Settimana") }
        OutlinedButton(onClick = { if (sequence.size > 1) sequence = sequence.dropLast(1) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Remove, null); Text(" Settimana") }
    }

    Text("Giorni lavorativi", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in workDays
            FilterChip(
                selected = selected,
                onClick = { workDays = if (selected) workDays - day else workDays + day },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).take(2).uppercase()) }
            )
        }
    }

    OutlinedTextField(start, { start = it }, label = { Text("Lunedì di partenza YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Per quante settimane")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(4, 8, 12, 26, 52).forEach { n -> FilterChip(selected = totalWeeks == n, onClick = { totalWeeks = n }, label = { Text(if (n == 52) "1 anno" else "$n") }) }
    }

    Surface(color = Color(0xFFE9F6FF), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Anteprima ciclo", fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
            Text(sequence.mapIndexed { i, t -> "Sett. ${i + 1}: ${t.label}" }.joinToString("   "), style = MaterialTheme.typography.bodySmall)
        }
    }

    Button(onClick = {
        val date = runCatching { LocalDate.parse(start) }.getOrNull()
        if (date == null) message = "Data non valida"
        else if (sequence.isEmpty()) message = "Aggiungi almeno una settimana"
        else if (workDays.isEmpty()) message = "Seleziona almeno un giorno lavorativo"
        else { onApply(date, sequence, totalWeeks, workDays); message = "Rotazione settimanale applicata" }
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Applica rotazione settimanale", fontWeight = FontWeight.Bold)
    }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DayRotationPlannerV3(onApply: (LocalDate, List<ShiftType>, Int) -> Unit) {
    var raw by remember { mutableStateOf("M,P,N,R") }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var days by remember { mutableIntStateOf(90) }
    var message by remember { mutableStateOf("") }
    OutlinedTextField(raw, { raw = it.uppercase() }, label = { Text("Sequenza") }, supportingText = { Text("M mattina, P pomeriggio, N notte, R riposo, G giornaliero") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(start, { start = it }, label = { Text("Data inizio YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30, 90, 180, 365).forEach { n -> FilterChip(selected = days == n, onClick = { days = n }, label = { Text(if (n == 365) "1 anno" else "$n gg") }) } }
    Button(onClick = {
        val cycle = parseCycle(raw)
        val date = runCatching { LocalDate.parse(start) }.getOrNull()
        if (date == null) message = "Data non valida" else if (cycle.isEmpty()) message = "Sequenza non valida" else { onApply(date, cycle, days); message = "Rotazione applicata" }
    }, modifier = Modifier.fillMaxWidth()) { Text("Genera ciclo giornaliero") }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
}

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
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Blue5 = Color(0xFF4069E5)
private val Navy5 = Color(0xFF17325C)
private val Green5 = Color(0xFF22A66A)
private val Orange5 = Color(0xFFF0A11D)
private val Purple5 = Color(0xFF8056E8)
private val Red5 = Color(0xFFDE5058)
private val Teal5 = Color(0xFF0D9B95)

data class ShiftVisual5(val bg: Color, val fg: Color, val icon: ImageVector)

private fun visual5(type: ShiftType): ShiftVisual5 = when(type) {
    ShiftType.MORNING -> ShiftVisual5(Color(0xFFE6F7EE), Green5, Icons.Default.WbSunny)
    ShiftType.AFTERNOON -> ShiftVisual5(Color(0xFFFFF2D8), Orange5, Icons.Default.LightMode)
    ShiftType.NIGHT -> ShiftVisual5(Color(0xFFE8EEFF), Blue5, Icons.Default.DarkMode)
    ShiftType.DAY -> ShiftVisual5(Color(0xFFE3F7F5), Teal5, Icons.Default.Work)
    ShiftType.REST -> ShiftVisual5(Color(0xFFF0F2F5), Color(0xFF687386), Icons.Default.Hotel)
    ShiftType.VACATION -> ShiftVisual5(Color(0xFFF1EAFF), Purple5, Icons.Default.BeachAccess)
    ShiftType.SICK -> ShiftVisual5(Color(0xFFFFE8EA), Red5, Icons.Default.MedicalServices)
    ShiftType.ROL -> ShiftVisual5(Color(0xFFE8F4FF), Color(0xFF2563EB), Icons.Default.AccessTime)
    ShiftType.PERMIT -> ShiftVisual5(Color(0xFFFFF0E2), Color(0xFFD97706), Icons.Default.EventAvailable)
    ShiftType.ON_CALL -> ShiftVisual5(Color(0xFFE6F5FF), Color(0xFF0284C7), Icons.Default.PhoneInTalk)
    ShiftType.SPLIT -> ShiftVisual5(Color(0xFFFFEDF6), Color(0xFFD72D7A), Icons.Default.CallSplit)
    ShiftType.HOLIDAY -> ShiftVisual5(Color(0xFFFFE8E8), Color(0xFFC92A2A), Icons.Default.Celebration)
    ShiftType.DOUBLE -> ShiftVisual5(Color(0xFFEDEBFF), Color(0xFF6756D9), Icons.Default.DoubleArrow)
}

class MainActivityV5 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurniOperaiV5(this) }
    }
}

@Composable
fun TurniOperaiV5(context: Context) {
    val prefs = remember { context.getSharedPreferences("turni_operai", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "light") ?: "light") }
    var shifts by remember { mutableStateOf(loadShifts(prefs.getStringSet("shifts", emptySet()) ?: emptySet())) }
    var tab by remember { mutableIntStateOf(0) }
    var editDate by remember { mutableStateOf<LocalDate?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val dark = theme == "dark" || (theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())

    fun save(list: List<ShiftEntry>) {
        shifts = list.sortedBy { it.date }
        prefs.edit().putStringSet("shifts", shifts.map { "${it.date}|${it.type.name}|${it.overtime}" }.toSet()).apply()
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9FB5FF)) else lightColorScheme(primary = Blue5, secondary = Green5, tertiary = Purple5, background = Color(0xFFF7F9FC), surface = Color.White)) {
        if (editDate != null) {
            EditShift5(
                date = editDate!!,
                existing = shifts.find { it.date == editDate },
                onBack = { editDate = null },
                onSave = { entry -> save(shifts.filterNot { it.date == entry.date } + entry); editDate = null },
                onDelete = { save(shifts.filterNot { it.date == editDate }); editDate = null }
            )
        } else {
            Scaffold(
                topBar = {
                    Surface(color = Navy5) {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CalendarMonth, null, tint = Color.White) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Turni Operai", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                                Text("Il tuo calendario di lavoro", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Menu, "Menu", tint = Color.White) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    MenuItem5("Home", Icons.Default.Home) { tab = 0; menuOpen = false }
                                    MenuItem5("Statistiche", Icons.Default.BarChart) { tab = 1; menuOpen = false }
                                    MenuItem5("Calendario", Icons.Default.CalendarMonth) { tab = 2; menuOpen = false }
                                    MenuItem5("Impostazioni turni", Icons.Default.Settings) { tab = 3; menuOpen = false }
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
                        ).forEach { (label, icon, idx) ->
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
                        0 -> Home5(shifts) { editDate = it }
                        1 -> Stats5(shifts)
                        2 -> Calendar5(shifts) { editDate = it }
                        else -> Settings5(
                            theme = theme,
                            onTheme = { theme = it; prefs.edit().putString("theme", it).apply() },
                            onWholeWeek = { start, sequence, weeks, workDays ->
                                val monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                val range = (0 until weeks * 7).map { monday.plusDays(it.toLong()) }.toSet()
                                val base = shifts.filterNot { it.date in range }
                                val generated = buildList {
                                    repeat(weeks) { w ->
                                        val type = sequence[w % sequence.size]
                                        repeat(7) { d ->
                                            val date = monday.plusDays((w * 7L) + d)
                                            add(ShiftEntry(date, if (date.dayOfWeek in workDays) type else ShiftType.REST))
                                        }
                                    }
                                }
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
private fun MenuItem5(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick, leadingIcon = { Icon(icon, null) })
}

@Composable
private fun Home5(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
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
                    MonthGrid5(month, shifts, onDate)
                }
            }
        }
        item { Summary5(monthShifts) }
        item {
            Button(onClick = { onDate(LocalDate.now()) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi turno", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MonthGrid5(month: YearMonth, shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
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
                        val visual = shift?.let { visual5(it.type) }
                        Column(
                            Modifier.weight(1f).padding(2.dp).height(56.dp).background(visual?.bg ?: Color(0xFFF5F6F8), RoundedCornerShape(12.dp)).clickable { onDate(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(day.toString(), fontWeight = FontWeight.Bold)
                            if (shift != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(shift.type.short, color = visual!!.fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                                    if (shift.overtime) {
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Default.Bolt, "Straordinario", tint = Orange5, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    } else Spacer(Modifier.weight(1f).height(56.dp))
                }
            }
        }
    }
}

@Composable
private fun Summary5(shifts: List<ShiftEntry>) {
    val workTypes = setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT, ShiftType.HOLIDAY, ShiftType.DOUBLE)
    val work = shifts.count { it.type in workTypes }
    val nights = shifts.count { it.type == ShiftType.NIGHT }
    val overtime = shifts.count { it.overtime }
    val rests = shifts.count { it.type == ShiftType.REST }
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Riepilogo mese", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat5("Ore", "${work * 8}", Icons.Default.Schedule, Color(0xFFE6F7EE), Green5, Modifier.weight(1f))
                Stat5("Notti", "$nights", Icons.Default.DarkMode, Color(0xFFE8EEFF), Blue5, Modifier.weight(1f))
                Stat5("Straord.", "$overtime", Icons.Default.Bolt, Color(0xFFFFF2D8), Orange5, Modifier.weight(1f))
                Stat5("Riposi", "$rests", Icons.Default.Hotel, Color(0xFFF1EAFF), Purple5, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat5(label: String, value: String, icon: ImageVector, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(14.dp)).padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Text(value, fontWeight = FontWeight.ExtraBold, color = fg); Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Stats5(shifts: List<ShiftEntry>) {
    val month = shifts.filter { YearMonth.from(it.date) == YearMonth.now() }
    val counts = month.groupingBy { it.type }.eachCount()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        item { Summary5(month) }
        items(ShiftType.entries) { type ->
            val v = visual5(type)
            Card(colors = CardDefaults.cardColors(containerColor = v.bg), shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(v.icon, null, tint = v.fg); Spacer(Modifier.width(10.dp)); Text(type.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text("${counts[type] ?: 0}", color = v.fg, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun Calendar5(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Calendario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
        if (shifts.isEmpty()) item { Text("Nessun turno inserito.") }
        items(shifts.sortedByDescending { it.date }) { shift ->
            val v = visual5(shift.type)
            Card(colors = CardDefaults.cardColors(containerColor = v.bg), modifier = Modifier.clickable { onDate(shift.date) }, shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(v.icon, null, tint = v.fg); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shift.type.label, fontWeight = FontWeight.Bold)
                            if (shift.overtime) {
                                Spacer(Modifier.width(7.dp))
                                Surface(color = Color(0xFFFFE2AA), shape = RoundedCornerShape(20.dp)) {
                                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, null, tint = Color(0xFFB76E00), modifier = Modifier.size(14.dp)); Text("Straordinario", color = Color(0xFF8A5300), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text(shift.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = v.fg)
                }
            }
        }
    }
}

@Composable
private fun EditShift5(date: LocalDate, existing: ShiftEntry?, onBack: () -> Unit, onSave: (ShiftEntry) -> Unit, onDelete: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ShiftType.MORNING) }
    var overtime by remember { mutableStateOf(existing?.overtime ?: false) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "Aggiungi turno" else "Modifica turno") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) }
            item {
                Text("Scegli il tipo di giornata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ShiftType.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { st ->
                            val v = visual5(st)
                            Surface(
                                color = if (type == st) v.bg else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (type == st) v.fg else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f).clickable { type = st }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(v.icon, null, tint = v.fg); Spacer(Modifier.width(8.dp)); Text(st.label, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                Surface(color = if (overtime) Color(0xFFFFF2D8) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { overtime = !overtime }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = Orange5); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Straordinario", fontWeight = FontWeight.Bold); Text("Comparirà nel calendario con il fulmine", style = MaterialTheme.typography.bodySmall) }; Switch(checked = overtime, onCheckedChange = { overtime = it })
                    }
                }
            }
            item { Button(onClick = { onSave(ShiftEntry(date, type, overtime)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Salva turno", fontWeight = FontWeight.Bold) } }
            if (existing != null) item { OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Elimina turno") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Settings5(theme: String, onTheme: (String) -> Unit, onWholeWeek: (LocalDate, List<ShiftType>, Int, Set<DayOfWeek>) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Impostazione turni", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text("Più chiaro. Prima scegli il tipo di rotazione, poi il ciclo.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            SettingsCard5("1. Rotazione settimanale", "Una settimana Mattino. Poi Notte. Poi Pomeriggio.", Icons.Default.ViewWeek, Green5) {
                WholeWeek5(onWholeWeek)
            }
        }
        item {
            SettingsCard5("2. Legenda turni", "Colori e icone sempre uguali in tutta l'app.", Icons.Default.Category, Orange5) {
                ShiftType.entries.forEach { type ->
                    val v = visual5(type)
                    Surface(color = v.bg, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(v.icon, null, tint = v.fg); Spacer(Modifier.width(9.dp)); Text(type.label, fontWeight = FontWeight.SemiBold, color = v.fg) }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
        item {
            SettingsCard5("3. Aspetto", "Tema chiaro, scuro o automatico.", Icons.Default.Palette, Blue5) {
                listOf("light" to "Chiaro", "dark" to "Scuro", "system" to "Automatico").forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onTheme(key) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = theme == key, onClick = { onTheme(key) }); Text(label) }
                }
            }
        }
        item { Text("Turni Operai 5.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingsCard5(title: String, subtitle: String, icon: ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(accent.copy(alpha = .12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
                Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.ExtraBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(2.dp)); content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WholeWeek5(onApply: (LocalDate, List<ShiftType>, Int, Set<DayOfWeek>) -> Unit) {
    val allowed = listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.REST)
    var sequence by remember { mutableStateOf(listOf(ShiftType.MORNING, ShiftType.NIGHT, ShiftType.AFTERNOON)) }
    var start by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()) }
    var weeks by remember { mutableIntStateOf(12) }
    var workDays by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var message by remember { mutableStateOf("") }

    Text("A. Ordine delle settimane", fontWeight = FontWeight.Bold)
    sequence.forEachIndexed { index, current ->
        var open by remember { mutableStateOf(false) }
        val v = visual5(current)
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = !open }) {
            OutlinedTextField(value = current.label, onValueChange = {}, readOnly = true, label = { Text("Settimana ${index + 1}") }, leadingIcon = { Icon(v.icon, null, tint = v.fg) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                allowed.forEach { type ->
                    val vv = visual5(type)
                    DropdownMenuItem(text = { Text(type.label) }, onClick = { sequence = sequence.toMutableList().also { it[index] = type }; open = false }, leadingIcon = { Icon(vv.icon, null, tint = vv.fg) })
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (sequence.size < 6) sequence = sequence + ShiftType.REST }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" Aggiungi") }
        OutlinedButton(onClick = { if (sequence.size > 1) sequence = sequence.dropLast(1) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Remove, null); Text(" Togli") }
    }
    HorizontalDivider()
    Text("B. Giorni lavorativi", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in workDays
            FilterChip(selected = selected, onClick = { workDays = if (selected) workDays - day else workDays + day }, label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).take(2).uppercase()) })
        }
    }
    Text("C. Data di partenza", fontWeight = FontWeight.Bold)
    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Lunedì iniziale YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("D. Durata", fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(4, 8, 12, 26, 52).forEach { n -> FilterChip(selected = weeks == n, onClick = { weeks = n }, label = { Text(if (n == 52) "1 anno" else "$n sett") }) }
    }
    Surface(color = Color(0xFFE6F5FF), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Anteprima ciclo", fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
            sequence.forEachIndexed { i, type -> Text("Settimana ${i + 1}: ${type.label}") }
        }
    }
    Button(onClick = {
        val date = runCatching { LocalDate.parse(start) }.getOrNull()
        when {
            date == null -> message = "Data non valida"
            workDays.isEmpty() -> message = "Seleziona almeno un giorno lavorativo"
            else -> { onApply(date, sequence, weeks, workDays); message = "Rotazione settimanale applicata" }
        }
    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Applica rotazione", fontWeight = FontWeight.Bold) }
    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
}

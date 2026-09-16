package com.meapps.turnioperai

// Compatibility source retained for historical builds.
// The active application entry point is MainActivityV6.

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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val V3Blue = Color(0xFF4169E1)
private val V3Green = Color(0xFF22A66A)
private val V3Orange = Color(0xFFF0A11D)
private val V3Purple = Color(0xFF8056E8)
private val V3Red = Color(0xFFDE5058)
private val V3Teal = Color(0xFF0D9B95)

class MainActivityV3 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TurniOperaiV3(this) }
    }
}

@Composable
fun TurniOperaiV3(context: Context) {
    val prefs = remember { context.getSharedPreferences("turni_operai", Context.MODE_PRIVATE) }
    var shifts by remember { mutableStateOf(loadShifts(prefs.getStringSet("shifts", emptySet()) ?: emptySet())) }
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<LocalDate?>(null) }

    fun save(list: List<ShiftEntry>) {
        shifts = list.sortedBy { it.date }
        prefs.edit().putStringSet("shifts", shifts.map { "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}|${it.rolMinutes}" }.toSet()).apply()
    }

    MaterialTheme {
        if (editing != null) {
            EditShiftV3(
                date = editing!!,
                existing = shifts.find { it.date == editing },
                onBack = { editing = null },
                onSave = { entry -> save(shifts.filterNot { it.date == entry.date } + entry); editing = null },
                onDelete = { save(shifts.filterNot { it.date == editing }); editing = null }
            )
        } else {
            Scaffold(
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
                        0 -> HomeV3(shifts) { editing = it }
                        1 -> StatsV3(shifts)
                        2 -> CalendarV3(shifts) { editing = it }
                        else -> SettingsV3()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeV3(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
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
                    MonthGridV3(month, shifts, onDate)
                }
            }
        }
        item { SummaryV3(monthShifts) }
    }
}

@Composable
private fun MonthGridV3(month: YearMonth, shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
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
    ShiftType.ROL -> Color(0xFFE8F4FF) to Color(0xFF2563EB)
    ShiftType.ON_CALL -> Color(0xFFE9F6FF) to Color(0xFF0284C7)
    ShiftType.SPLIT -> Color(0xFFFFF0F7) to Color(0xFFDB2777)
    ShiftType.HOLIDAY -> Color(0xFFFFECEC) to Color(0xFFDC2626)
    ShiftType.DOUBLE -> Color(0xFFECEBFF) to Color(0xFF6D5CE7)
    null -> Color(0xFFF5F6F8) to Color(0xFF687386)
}

@Composable
private fun SummaryV3(shifts: List<ShiftEntry>) {
    val work = shifts.count { it.type in setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT, ShiftType.HOLIDAY, ShiftType.DOUBLE) }
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Turni", fontWeight = FontWeight.Bold)
            Text(work.toString(), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun StatsV3(shifts: List<ShiftEntry>) {
    val month = shifts.filter { YearMonth.from(it.date) == YearMonth.now() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Statistiche", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        items(ShiftType.entries) { type ->
            val (bg, fg) = colorForV3(type)
            Card(colors = CardDefaults.cardColors(containerColor = bg)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(type.label)
                    Text(month.count { it.type == type }.toString(), color = fg, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CalendarV3(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shifts.sortedByDescending { it.date }) { shift ->
            val (bg, fg) = colorForV3(shift.type)
            Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = Modifier.clickable { onDate(shift.date) }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(shift.type.label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(shift.date.format(DateTimeFormatter.ISO_DATE), color = fg)
                }
            }
        }
    }
}

@Composable
private fun EditShiftV3(date: LocalDate, existing: ShiftEntry?, onBack: () -> Unit, onSave: (ShiftEntry) -> Unit, onDelete: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ShiftType.MORNING) }
    Scaffold(topBar = { TopAppBar(title = { Text("Turno") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShiftType.entries) { st ->
                FilterChip(selected = type == st, onClick = { type = st }, label = { Text(st.label) })
            }
            item { Button(onClick = { onSave(ShiftEntry(date, type)) }, modifier = Modifier.fillMaxWidth()) { Text("Salva") } }
            if (existing != null) item { OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Elimina") } }
        }
    }
}

@Composable
private fun SettingsV3() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Impostazioni") }
}

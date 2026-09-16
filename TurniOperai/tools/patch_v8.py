from pathlib import Path

main_path = Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivity.kt")
v6_path = Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivityV6.kt")
gradle_path = Path("TurniOperai/app/build.gradle.kts")

# ---- Shared model + persistence migration ----
main = main_path.read_text(encoding="utf-8")
main = main.replace(
    'data class ShiftEntry(val date: LocalDate, val type: ShiftType, val overtime: Boolean = false)',
    'data class ShiftEntry(\n    val date: LocalDate,\n    val type: ShiftType,\n    val overtime: Boolean = false,\n    val overtimeMinutes: Int = 0,\n    val vacationMinutes: Int = 0\n)'
)
main = main.replace(
    'prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}" }.toSet()).apply()',
    'prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}" }.toSet()).apply()'
)
start = main.index('fun loadShifts(raw: Set<String>): List<ShiftEntry>')
end = main.index('fun loadShiftTimes(', start)
main = main[:start] + '''fun loadShifts(raw: Set<String>): List<ShiftEntry> = raw.mapNotNull { row ->
    runCatching {
        val p = row.split("|")
        val type = ShiftType.valueOf(p[1])
        val overtimeFlag = p.getOrNull(2)?.toBoolean() ?: false
        val overtimeMinutes = p.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val vacationMinutes = p.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0)
            ?: if (type == ShiftType.VACATION) 8 * 60 else 0
        ShiftEntry(
            LocalDate.parse(p[0]),
            type,
            overtimeFlag || overtimeMinutes > 0,
            overtimeMinutes,
            vacationMinutes
        )
    }.getOrNull()
}.sortedBy { it.date }

''' + main[end:]
main_path.write_text(main, encoding="utf-8")

# ---- Main v8 UI and logic ----
text = v6_path.read_text(encoding="utf-8")
if 'import java.time.temporal.ChronoUnit' not in text:
    text = text.replace('import java.time.temporal.TemporalAdjusters\n', 'import java.time.temporal.TemporalAdjusters\nimport java.time.temporal.ChronoUnit\n', 1)

if 'data class VacationProfileV8' not in text:
    insert_at = text.index('enum class ScheduleModeV6')
    vacation_helpers = r'''data class VacationPresetV8(
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

'''
    text = text[:insert_at] + vacation_helpers + text[insert_at:]

# Replace v7 backup helper block with v8-compatible backup and restore.
backup_start = text.index('private fun makeBackupJsonV7')
backup_end = text.index('class MainActivityV6', backup_start)
backup_block = r'''private fun makeBackupJsonV8(shifts: List<ShiftEntry>, theme: String, vacationProfile: VacationProfileV8): String {
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

'''
text = text[:backup_start] + backup_block + text[backup_end:]

# Add vacation state after backup message.
state_anchor = '    var backupMessage by remember { mutableStateOf("") }\n'
state_block = r'''    var backupMessage by remember { mutableStateOf("") }
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
'''
if state_anchor in text:
    text = text.replace(state_anchor, state_block, 1)
else:
    raise SystemExit('vacation state anchor not found')

# Replace launcher section with v8 backup / restore.
launcher_start = text.index('    val createBackupLauncher = rememberLauncherForActivityResult(')
launcher_end = text.index('    fun save(list: List<ShiftEntry>) {', launcher_start)
launcher_block = r'''    val createBackupLauncher = rememberLauncherForActivityResult(
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

'''
text = text[:launcher_start] + launcher_block + text[launcher_end:]

text = text.replace(
    'prefs.edit().putStringSet("shifts", shifts.map { "${it.date}|${it.type.name}|${it.overtime}" }.toSet()).apply()',
    'prefs.edit().putStringSet("shifts", shifts.map { "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}" }.toSet()).apply()',
    1
)

text = text.replace('0 -> HomeV6(shifts) { editDate = it }', '0 -> HomeV6(shifts, vacationProfile) { editDate = it }', 1)
text = text.replace('1 -> StatsV6(shifts)', '1 -> StatsV6(shifts, vacationProfile)', 1)

settings_old = '''                            onRestore = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                            backupMessage = backupMessage
'''
settings_new = '''                            onRestore = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
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
'''
if settings_old in text:
    text = text.replace(settings_old, settings_new, 1)
else:
    raise SystemExit('settings call anchor not found')

text = text.replace(
    'private fun HomeV6(shifts: List<ShiftEntry>, onDate: (LocalDate) -> Unit) {',
    'private fun HomeV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8, onDate: (LocalDate) -> Unit) {',
    1
)
text = text.replace('item { SummaryV6(monthShifts) }', 'item { SummaryV6(monthShifts, shifts, vacationProfile) }', 1)

# Monthly summary with real overtime duration and vacation figures.
summary_start = text.index('@Composable\nprivate fun SummaryV6(')
summary_end = text.index('@Composable\nprivate fun StatV6', summary_start)
summary_block = r'''@Composable
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

'''
text = text[:summary_start] + summary_block + text[summary_end:]

text = text.replace(
    'private fun StatsV6(shifts: List<ShiftEntry>) {',
    'private fun StatsV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8) {',
    1
)
text = text.replace('item { SummaryV6(month) }', 'item { SummaryV6(month, shifts, vacationProfile) }', 1)

# Calendar overtime badge now shows the quantity; vacation rows show duration.
text = text.replace('if (shift.overtime) {', 'if (shift.overtime || shift.overtimeMinutes > 0) {', 2)
text = text.replace(
    'Text("Straordinario", color = Color(0xFF8A5300), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)',
    'Text(if (shift.overtimeMinutes > 0) formatHoursMinutesV8(shift.overtimeMinutes) else "Straordinario", color = Color(0xFF8A5300), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)',
    1
)
calendar_date_anchor = 'Text(shift.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)'
calendar_date_new = calendar_date_anchor + '''
                        if (shift.type == ShiftType.VACATION) {
                            Text("Ferie: ${formatVacationV8(if (shift.vacationMinutes > 0) shift.vacationMinutes else 8 * 60)}", style = MaterialTheme.typography.labelSmall, color = v.fg)
                        }'''
text = text.replace(calendar_date_anchor, calendar_date_new, 1)

# Replace shift editor with half-hour overtime and vacation duration controls.
edit_start = text.index('@Composable\nprivate fun EditShiftV6(')
edit_end = text.index('@Composable\nprivate fun SettingsV6(', edit_start)
edit_block = r'''@Composable
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

'''
text = text[:edit_start] + edit_block + text[edit_end:]

# Extend settings signature.
settings_sig_old = '''private fun SettingsV6(
    theme: String,
    onTheme: (String) -> Unit,
    onApply: (List<ShiftEntry>) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    backupMessage: String
) {'''
settings_sig_new = '''private fun SettingsV6(
    theme: String,
    onTheme: (String) -> Unit,
    onApply: (List<ShiftEntry>) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    backupMessage: String,
    vacationProfile: VacationProfileV8,
    allShifts: List<ShiftEntry>,
    onVacationProfileChange: (VacationProfileV8) -> Unit
) {'''
if settings_sig_old in text:
    text = text.replace(settings_sig_old, settings_sig_new, 1)
else:
    raise SystemExit('settings signature not found')

backup_card_anchor = '            SettingsCardV6("5. Backup e ripristino", "Salva i turni in un file e ripristinali se cambi telefono o reinstalli l\'app.", Icons.Default.Save, V6Teal) {'
vacation_card = r'''            SettingsCardV6("5. Ferie e maturazione", "Scegli il contratto, la maturazione mensile e il saldo iniziale.", Icons.Default.BeachAccess, V6Purple) {
                VacationSettingsV8(vacationProfile, allShifts, onVacationProfileChange)
            }
        }
        item {
            SettingsCardV6("6. Backup e ripristino", "Salva turni, straordinari e impostazioni ferie per poter ripristinare tutto.", Icons.Default.Save, V6Teal) {'''
if backup_card_anchor in text:
    text = text.replace(backup_card_anchor, vacation_card, 1)
else:
    raise SystemExit('backup card anchor not found')

text = text.replace(
    'Text("Il backup contiene calendario dei turni, straordinari e tema dell\'app. Il file resta dove scegli tu sul telefono o nel cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
    'Text("Il backup contiene calendario, quantità di straordinario, ferie usate, contratto ferie, saldo iniziale e tema. Il file resta dove scegli tu sul telefono o nel cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
    1
)
text = text.replace('Text("Turni Operai 7.0", color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text("Turni Operai 8.0", color = MaterialTheme.colorScheme.onSurfaceVariant)', 1)

# Add vacation settings composable before SettingsCardV6.
settings_card_anchor = '@Composable\nprivate fun SettingsCardV6('
idx = text.index(settings_card_anchor)
vacation_composable = r'''@Composable
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

'''
text = text[:idx] + vacation_composable + text[idx:]

v6_path.write_text(text, encoding="utf-8")

# Keep stable signing and bump installable update version.
gradle = gradle_path.read_text(encoding="utf-8")
gradle = gradle.replace('versionCode = 7', 'versionCode = 8', 1)
gradle = gradle.replace('versionName = "7.0"', 'versionName = "8.0"', 1)
gradle_path.write_text(gradle, encoding="utf-8")

print("Turni Operai v8 patch applied")

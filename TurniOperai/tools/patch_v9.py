from pathlib import Path

main_path = Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivity.kt")
v6_path = Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivityV6.kt")
gradle_path = Path("TurniOperai/app/build.gradle.kts")

# Shared model and persistence: add a dedicated ROL type and duration.
main = main_path.read_text(encoding="utf-8")
main = main.replace(
    '    PERMIT("Permesso", "Per", "", ""),\n',
    '    PERMIT("Permesso", "Per", "", ""),\n    ROL("R.O.L.", "ROL", "", ""),\n',
    1,
)
main = main.replace(
    '    val vacationMinutes: Int = 0\n)',
    '    val vacationMinutes: Int = 0,\n    val rolMinutes: Int = 0\n)',
    1,
)
main = main.replace(
    'prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}" }.toSet()).apply()',
    'prefs.edit().putStringSet("shifts", list.map { "${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}|${it.rolMinutes}" }.toSet()).apply()',
    1,
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

''' + main[end:]
main = main.replace(
    '    ShiftType.PERMIT -> Color(0xFFFFF0E5) to Color(0xFFD97706)\n',
    '    ShiftType.PERMIT -> Color(0xFFFFF0E5) to Color(0xFFD97706)\n    ShiftType.ROL -> Color(0xFFE8F4FF) to Color(0xFF2563EB)\n',
    1,
)
main = main.replace(
    '        "G" to ShiftType.DAY, "F" to ShiftType.VACATION, "MAL" to ShiftType.SICK, "PER" to ShiftType.PERMIT,\n',
    '        "G" to ShiftType.DAY, "F" to ShiftType.VACATION, "MAL" to ShiftType.SICK, "PER" to ShiftType.PERMIT, "ROL" to ShiftType.ROL,\n',
    1,
)
main_path.write_text(main, encoding="utf-8")

text = v6_path.read_text(encoding="utf-8")
text = text.replace(
    '    ShiftType.PERMIT -> ShiftVisualV6(Color(0xFFFFF0E2), Color(0xFFD97706), Icons.Default.EventAvailable)\n',
    '    ShiftType.PERMIT -> ShiftVisualV6(Color(0xFFFFF0E2), Color(0xFFD97706), Icons.Default.EventAvailable)\n    ShiftType.ROL -> ShiftVisualV6(Color(0xFFE8F4FF), Color(0xFF2563EB), Icons.Default.AccessTime)\n',
    1,
)

# Add ROL profile to existing vacation data model.
text = text.replace(
'''data class BackupDataV8(
    val shifts: List<ShiftEntry>,
    val theme: String?,
    val vacationProfile: VacationProfileV8?
)
''',
'''data class RolProfileV9(
    val monthlyMinutes: Int,
    val openingMinutes: Int,
    val startMonth: String
)

data class BackupDataV8(
    val shifts: List<ShiftEntry>,
    val theme: String?,
    val vacationProfile: VacationProfileV8?,
    val rolProfile: RolProfileV9?
)
''',
    1,
)

helper_anchor = '''private fun vacationBalanceMinutesV8(shifts: List<ShiftEntry>, profile: VacationProfileV8): Int =
    profile.openingMinutes + accruedMonthsV8(profile) * profile.monthlyMinutes - vacationUsedMinutesV8(shifts, profile)

'''
helper_block = helper_anchor + '''private fun rolStartYearMonthV9(profile: RolProfileV9): YearMonth =
    runCatching { YearMonth.parse(profile.startMonth.take(7)) }.getOrElse { YearMonth.now() }

private fun rolAccruedMonthsV9(profile: RolProfileV9, today: LocalDate = LocalDate.now()): Int {
    val start = rolStartYearMonthV9(profile)
    val now = YearMonth.from(today)
    if (start.isAfter(now)) return 0
    val whole = ChronoUnit.MONTHS.between(start.atDay(1), now.atDay(1)).toInt()
    return whole + if (today.dayOfMonth >= 15) 1 else 0
}

private fun rolUsedMinutesV9(shifts: List<ShiftEntry>, profile: RolProfileV9): Int {
    val startDate = rolStartYearMonthV9(profile).atDay(1)
    return shifts.asSequence()
        .filter { it.type == ShiftType.ROL && !it.date.isBefore(startDate) }
        .sumOf { if (it.rolMinutes > 0) it.rolMinutes else 8 * 60 }
}

private fun rolBalanceMinutesV9(shifts: List<ShiftEntry>, profile: RolProfileV9): Int =
    profile.openingMinutes + rolAccruedMonthsV9(profile) * profile.monthlyMinutes - rolUsedMinutesV9(shifts, profile)

'''
if helper_anchor not in text:
    raise SystemExit('ROL helper anchor not found')
text = text.replace(helper_anchor, helper_block, 1)

# Replace backup helpers with v9-capable JSON while keeping v7/v8 restore compatibility.
backup_start = text.index('private fun makeBackupJsonV8')
backup_end = text.index('class MainActivityV6', backup_start)
backup_block = r'''private fun makeBackupJsonV9(
    shifts: List<ShiftEntry>,
    theme: String,
    vacationProfile: VacationProfileV8,
    rolProfile: RolProfileV9
): String {
    val root = JSONObject()
    root.put("format", "TurniOperaiBackup")
    root.put("version", 9)
    root.put("createdAt", LocalDate.now().toString())
    root.put("theme", theme)
    root.put("vacationContractKey", vacationProfile.contractKey)
    root.put("vacationMonthlyMinutes", vacationProfile.monthlyMinutes)
    root.put("vacationOpeningMinutes", vacationProfile.openingMinutes)
    root.put("vacationStartMonth", vacationProfile.startMonth)
    root.put("rolMonthlyMinutes", rolProfile.monthlyMinutes)
    root.put("rolOpeningMinutes", rolProfile.openingMinutes)
    root.put("rolStartMonth", rolProfile.startMonth)
    val array = JSONArray()
    shifts.sortedBy { it.date }.forEach { entry ->
        array.put(
            JSONObject()
                .put("date", entry.date.toString())
                .put("type", entry.type.name)
                .put("overtime", entry.overtime)
                .put("overtimeMinutes", entry.overtimeMinutes)
                .put("vacationMinutes", entry.vacationMinutes)
                .put("rolMinutes", entry.rolMinutes)
        )
    }
    root.put("shifts", array)
    return root.toString(2)
}

private fun parseBackupV9(raw: String): BackupDataV8 {
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
            } else if (type == ShiftType.VACATION) 8 * 60 else 0
            val rolMinutes = if (item.has("rolMinutes")) {
                item.optInt("rolMinutes", 0).coerceAtLeast(0)
            } else if (type == ShiftType.ROL) 8 * 60 else 0
            add(
                ShiftEntry(
                    LocalDate.parse(item.getString("date")),
                    type,
                    item.optBoolean("overtime", false) || overtimeMinutes > 0,
                    overtimeMinutes,
                    vacationMinutes,
                    rolMinutes
                )
            )
        }
    }
    val restoredTheme = root.optString("theme").takeIf { it in setOf("light", "dark", "system") }
    val vacationProfile = if (root.has("vacationContractKey") || root.has("vacationMonthlyMinutes")) {
        val key = root.optString("vacationContractKey", "metal4")
        VacationProfileV8(
            contractKey = key,
            monthlyMinutes = root.optInt("vacationMonthlyMinutes", vacationPresetV8(key).monthlyMinutes).coerceAtLeast(0),
            openingMinutes = root.optInt("vacationOpeningMinutes", 0),
            startMonth = root.optString("vacationStartMonth", LocalDate.now().withDayOfYear(1).withDayOfMonth(1).toString())
        )
    } else null
    val rolProfile = if (root.has("rolMonthlyMinutes") || root.has("rolOpeningMinutes")) {
        RolProfileV9(
            monthlyMinutes = root.optInt("rolMonthlyMinutes", 8 * 60).coerceAtLeast(0),
            openingMinutes = root.optInt("rolOpeningMinutes", 0).coerceAtLeast(0),
            startMonth = root.optString("rolStartMonth", LocalDate.now().withDayOfYear(1).withDayOfMonth(1).toString())
        )
    } else null
    return BackupDataV8(shifts, restoredTheme, vacationProfile, rolProfile)
}

'''
text = text[:backup_start] + backup_block + text[backup_end:]

# ROL profile state: for the current payslip the rate is 8 hours/month.
profile_anchor = '''    val vacationProfile = VacationProfileV8(
        vacationContractKey,
        vacationMonthlyMinutes,
        vacationOpeningMinutes,
        vacationStartMonth
    )
'''
profile_block = profile_anchor + '''    var rolMonthlyMinutes by remember { mutableIntStateOf(prefs.getInt("rol_monthly_minutes", 8 * 60)) }
    var rolOpeningMinutes by remember { mutableIntStateOf(prefs.getInt("rol_opening_minutes", 0)) }
    var rolStartMonth by remember {
        mutableStateOf(
            prefs.getString("rol_start_month", LocalDate.now().withDayOfYear(1).withDayOfMonth(1).toString())
                ?: LocalDate.now().withDayOfYear(1).withDayOfMonth(1).toString()
        )
    }
    val rolProfile = RolProfileV9(rolMonthlyMinutes, rolOpeningMinutes, rolStartMonth)
'''
if profile_anchor not in text:
    raise SystemExit('ROL state anchor not found')
text = text.replace(profile_anchor, profile_block, 1)

text = text.replace('writer.write(makeBackupJsonV8(shifts, theme, vacationProfile))', 'writer.write(makeBackupJsonV9(shifts, theme, vacationProfile, rolProfile))', 1)
text = text.replace('parseBackupV8(raw)', 'parseBackupV9(raw)', 1)
text = text.replace(
    '"${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}"',
    '"${it.date}|${it.type.name}|${it.overtime}|${it.overtimeMinutes}|${it.vacationMinutes}|${it.rolMinutes}"',
)

restore_anchor = '''                restored.vacationProfile?.let { profile ->
                    vacationContractKey = profile.contractKey
                    vacationMonthlyMinutes = profile.monthlyMinutes
                    vacationOpeningMinutes = profile.openingMinutes
                    vacationStartMonth = profile.startMonth
                    editor.putString("vacation_contract", profile.contractKey)
                    editor.putInt("vacation_monthly_minutes", profile.monthlyMinutes)
                    editor.putInt("vacation_opening_minutes", profile.openingMinutes)
                    editor.putString("vacation_start_month", profile.startMonth)
                }
'''
restore_block = restore_anchor + '''                restored.rolProfile?.let { profile ->
                    rolMonthlyMinutes = profile.monthlyMinutes
                    rolOpeningMinutes = profile.openingMinutes
                    rolStartMonth = profile.startMonth
                    editor.putInt("rol_monthly_minutes", profile.monthlyMinutes)
                    editor.putInt("rol_opening_minutes", profile.openingMinutes)
                    editor.putString("rol_start_month", profile.startMonth)
                }
'''
if restore_anchor not in text:
    raise SystemExit('ROL restore anchor not found')
text = text.replace(restore_anchor, restore_block, 1)

# Pass ROL profile through home/stats/settings.
text = text.replace('0 -> HomeV6(shifts, vacationProfile) { editDate = it }', '0 -> HomeV6(shifts, vacationProfile, rolProfile) { editDate = it }', 1)
text = text.replace('1 -> StatsV6(shifts, vacationProfile)', '1 -> StatsV6(shifts, vacationProfile, rolProfile)', 1)
settings_call_anchor = '''                            onVacationProfileChange = { profile ->
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
settings_call_block = '''                            onVacationProfileChange = { profile ->
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
                            },
                            rolProfile = rolProfile,
                            onRolProfileChange = { profile ->
                                rolMonthlyMinutes = profile.monthlyMinutes
                                rolOpeningMinutes = profile.openingMinutes
                                rolStartMonth = profile.startMonth
                                prefs.edit()
                                    .putInt("rol_monthly_minutes", profile.monthlyMinutes)
                                    .putInt("rol_opening_minutes", profile.openingMinutes)
                                    .putString("rol_start_month", profile.startMonth)
                                    .apply()
                            }
'''
if settings_call_anchor not in text:
    raise SystemExit('settings ROL call anchor not found')
text = text.replace(settings_call_anchor, settings_call_block, 1)

text = text.replace(
    'private fun HomeV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8, onDate: (LocalDate) -> Unit) {',
    'private fun HomeV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8, rolProfile: RolProfileV9, onDate: (LocalDate) -> Unit) {',
    1,
)
text = text.replace('item { SummaryV6(monthShifts, shifts, vacationProfile) }', 'item { SummaryV6(monthShifts, shifts, vacationProfile, rolProfile) }', 1)
text = text.replace(
    'private fun SummaryV6(monthShifts: List<ShiftEntry>, allShifts: List<ShiftEntry>, vacationProfile: VacationProfileV8) {',
    'private fun SummaryV6(monthShifts: List<ShiftEntry>, allShifts: List<ShiftEntry>, vacationProfile: VacationProfileV8, rolProfile: RolProfileV9) {',
    1,
)
summary_calc_anchor = '''    val vacationBalance = vacationBalanceMinutesV8(allShifts, vacationProfile)
'''
summary_calc_block = summary_calc_anchor + '''    val rolMonthMinutes = monthShifts.filter { it.type == ShiftType.ROL }.sumOf {
        if (it.rolMinutes > 0) it.rolMinutes else 8 * 60
    }
    val rolBalance = rolBalanceMinutesV9(allShifts, rolProfile)
'''
if summary_calc_anchor not in text:
    raise SystemExit('summary ROL calc anchor not found')
text = text.replace(summary_calc_anchor, summary_calc_block, 1)
summary_ui_anchor = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatV6("Ferie mese", formatVacationV8(vacationMonthMinutes), Icons.Default.BeachAccess, Color(0xFFF1EAFF), V6Purple, Modifier.weight(1f))
                StatV6("Ferie residue", formatVacationV8(vacationBalance), Icons.Default.AccountBalanceWallet, Color(0xFFE3F7F5), V6Teal, Modifier.weight(1f))
            }
'''
summary_ui_block = summary_ui_anchor + '''            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatV6("ROL mese", formatHoursMinutesV8(rolMonthMinutes), Icons.Default.AccessTime, Color(0xFFE8F4FF), Color(0xFF2563EB), Modifier.weight(1f))
                StatV6("ROL residui", formatHoursMinutesV8(rolBalance.coerceAtLeast(0)), Icons.Default.AccountBalanceWallet, Color(0xFFE8F4FF), Color(0xFF2563EB), Modifier.weight(1f))
            }
'''
if summary_ui_anchor not in text:
    raise SystemExit('summary ROL UI anchor not found')
text = text.replace(summary_ui_anchor, summary_ui_block, 1)

text = text.replace(
    'private fun StatsV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8) {',
    'private fun StatsV6(shifts: List<ShiftEntry>, vacationProfile: VacationProfileV8, rolProfile: RolProfileV9) {',
    1,
)
text = text.replace('item { SummaryV6(month, shifts, vacationProfile) }', 'item { SummaryV6(month, shifts, vacationProfile, rolProfile) }', 1)

# Calendar detail for ROL.
calendar_anchor = '''                        if (shift.type == ShiftType.VACATION) {
                            Text("Ferie: ${formatVacationV8(if (shift.vacationMinutes > 0) shift.vacationMinutes else 8 * 60)}", style = MaterialTheme.typography.labelSmall, color = v.fg)
                        }
'''
calendar_block = calendar_anchor + '''                        if (shift.type == ShiftType.ROL) {
                            Text("ROL: ${formatHoursMinutesV8(if (shift.rolMinutes > 0) shift.rolMinutes else 8 * 60)}", style = MaterialTheme.typography.labelSmall, color = v.fg)
                        }
'''
if calendar_anchor not in text:
    raise SystemExit('calendar ROL anchor not found')
text = text.replace(calendar_anchor, calendar_block, 1)

# Edit screen: ROL duration in 30-minute steps.
edit_state_anchor = '''    var vacationMinutes by remember {
        mutableIntStateOf(
            existing?.vacationMinutes?.takeIf { it > 0 }
                ?: if (existing?.type == ShiftType.VACATION) 8 * 60 else 8 * 60
        )
    }
'''
edit_state_block = edit_state_anchor + '''    var rolMinutes by remember {
        mutableIntStateOf(
            existing?.rolMinutes?.takeIf { it > 0 }
                ?: if (existing?.type == ShiftType.ROL) 8 * 60 else 8 * 60
        )
    }
'''
if edit_state_anchor not in text:
    raise SystemExit('edit ROL state anchor not found')
text = text.replace(edit_state_anchor, edit_state_block, 1)
text = text.replace(
    '                                    if (st == ShiftType.VACATION && vacationMinutes <= 0) vacationMinutes = 8 * 60\n',
    '                                    if (st == ShiftType.VACATION && vacationMinutes <= 0) vacationMinutes = 8 * 60\n                                    if (st == ShiftType.ROL && rolMinutes <= 0) rolMinutes = 8 * 60\n',
    1,
)
rol_editor_anchor = '''            item {
                Button(
                    onClick = {
'''
rol_editor = '''            if (type == ShiftType.ROL) {
                item {
                    Surface(color = Color(0xFFE8F4FF), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, null, tint = Color(0xFF2563EB))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Durata ROL", fontWeight = FontWeight.Bold)
                                    Text("Conteggio separato dalle ferie, a scatti di 30 minuti.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                FilledTonalIconButton(onClick = { rolMinutes = (rolMinutes - 30).coerceAtLeast(30) }) {
                                    Icon(Icons.Default.Remove, "Meno 30 minuti")
                                }
                                Text(formatHoursMinutesV8(rolMinutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2563EB))
                                FilledTonalIconButton(onClick = { rolMinutes = (rolMinutes + 30).coerceAtMost(8 * 60) }) {
                                    Icon(Icons.Default.Add, "Più 30 minuti")
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = rolMinutes == 4 * 60, onClick = { rolMinutes = 4 * 60 }, label = { Text("4h") }, modifier = Modifier.weight(1f))
                                FilterChip(selected = rolMinutes == 8 * 60, onClick = { rolMinutes = 8 * 60 }, label = { Text("8h") }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
'''
if rol_editor_anchor not in text:
    raise SystemExit('ROL editor insertion anchor not found')
text = text.replace(rol_editor_anchor, rol_editor, 1)
text = text.replace(
    '                                vacationMinutes = if (type == ShiftType.VACATION) vacationMinutes else 0\n',
    '                                vacationMinutes = if (type == ShiftType.VACATION) vacationMinutes else 0,\n                                rolMinutes = if (type == ShiftType.ROL) rolMinutes else 0\n',
    1,
)

# Settings signature and UI.
settings_sig_anchor = '''    vacationProfile: VacationProfileV8,
    allShifts: List<ShiftEntry>,
    onVacationProfileChange: (VacationProfileV8) -> Unit
) {
'''
settings_sig_block = '''    vacationProfile: VacationProfileV8,
    allShifts: List<ShiftEntry>,
    onVacationProfileChange: (VacationProfileV8) -> Unit,
    rolProfile: RolProfileV9,
    onRolProfileChange: (RolProfileV9) -> Unit
) {
'''
if settings_sig_anchor not in text:
    raise SystemExit('settings signature ROL anchor not found')
text = text.replace(settings_sig_anchor, settings_sig_block, 1)
settings_cards_anchor = '''        item {
            SettingsCardV6("6. Backup e ripristino", "Salva turni, straordinari e impostazioni ferie per poter ripristinare tutto.", Icons.Default.Save, V6Teal) {
'''
settings_cards_block = '''        item {
            SettingsCardV6("6. ROL", "Profilo attuale dalla tua busta paga: maturazione 8 ore al mese, separata dalle ferie.", Icons.Default.AccessTime, Color(0xFF2563EB)) {
                RolSettingsV9(rolProfile, allShifts, onRolProfileChange)
            }
        }
        item {
            SettingsCardV6("7. Backup e ripristino", "Salva turni, straordinari, ferie e ROL per poter ripristinare tutto.", Icons.Default.Save, V6Teal) {
'''
if settings_cards_anchor not in text:
    raise SystemExit('settings ROL card anchor not found')
text = text.replace(settings_cards_anchor, settings_cards_block, 1)
text = text.replace(
    'Il backup contiene calendario, quantità di straordinario, ferie usate, contratto ferie, saldo iniziale e tema.',
    'Il backup contiene calendario, quantità di straordinario, ferie, ROL, saldi iniziali e tema.',
    1,
)
text = text.replace('Turni Operai 8.0', 'Turni Operai 9.0', 1)

# ROL settings panel, intentionally fixed to the user's current 8h/month profile for now.
rol_settings_anchor = '''@Composable
private fun DurationStepperV8(
'''
rol_settings_block = r'''@Composable
private fun RolSettingsV9(
    profile: RolProfileV9,
    shifts: List<ShiftEntry>,
    onChange: (RolProfileV9) -> Unit
) {
    var startText by remember(profile.startMonth) { mutableStateOf(profile.startMonth) }
    var startMessage by remember { mutableStateOf("") }

    Surface(color = Color(0xFFE8F4FF), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Maturazione mensile", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2563EB))
            Text("8 ore al mese", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Valore ricavato dalla busta paga attuale: 64 ore maturate da gennaio ad agosto.", style = MaterialTheme.typography.bodySmall)
        }
    }

    Text("Saldo ROL iniziale", fontWeight = FontWeight.Bold)
    Text("Inserisci il residuo del cedolino alla data da cui vuoi iniziare il conteggio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DurationStepperV8(
        valueMinutes = profile.openingMinutes,
        stepMinutes = 30,
        maxMinutes = 1000 * 60,
        enabled = true,
        onChange = { onChange(profile.copy(openingMinutes = it)) }
    )

    Text("Inizio conteggio ROL", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = startText,
        onValueChange = { startText = it },
        label = { Text("YYYY-MM-DD") },
        supportingText = { Text("Il mese corrente viene conteggiato dal giorno 15.") },
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
                onChange(profile.copy(startMonth = normalized, monthlyMinutes = 8 * 60))
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

    val months = rolAccruedMonthsV9(profile)
    val accrued = months * profile.monthlyMinutes
    val used = rolUsedMinutesV9(shifts, profile)
    val balance = rolBalanceMinutesV9(shifts, profile)
    Surface(color = Color(0xFFE8F4FF), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Situazione ROL", fontWeight = FontWeight.ExtraBold, color = Color(0xFF2563EB))
            Text("Mesi maturati: $months")
            Text("Maturati dal conteggio: ${formatHoursMinutesV8(accrued)}")
            Text("Utilizzati: ${formatHoursMinutesV8(used)}")
            Text("Residuo stimato: ${if (balance < 0) "-" else ""}${formatHoursMinutesV8(kotlin.math.abs(balance))}", fontWeight = FontWeight.Bold)
        }
    }
    Text("Per ora il rateo ROL è fissato a 8h/mese perché è quello risultante dal cedolino caricato. In seguito aggiungeremo profili ROL diversi.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DurationStepperV8(
'''
if rol_settings_anchor not in text:
    raise SystemExit('ROL settings composable anchor not found')
text = text.replace(rol_settings_anchor, rol_settings_block, 1)

v6_path.write_text(text, encoding="utf-8")

g = gradle_path.read_text(encoding="utf-8")
g = g.replace('versionCode = 8', 'versionCode = 9', 1)
g = g.replace('versionName = "8.0"', 'versionName = "9.0"', 1)
gradle_path.write_text(g, encoding="utf-8")

print("Turni Operai v9 ROL patch applied")

from pathlib import Path

# Trigger v7 patch/build workflow.
src = Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivityV6.kt")
text = src.read_text(encoding="utf-8")

# Imports for Android document picker and JSON backup.
old_imports = """import androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\n"""
new_imports = """import androidx.activity.ComponentActivity\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.compose.setContent\nimport androidx.activity.result.contract.ActivityResultContracts\n"""
if old_imports in text and "rememberLauncherForActivityResult" not in text:
    text = text.replace(old_imports, new_imports, 1)

old_json_anchor = """import java.util.Locale\n\nprivate val V6Blue"""
new_json_anchor = """import java.util.Locale\nimport org.json.JSONArray\nimport org.json.JSONObject\n\nprivate val V6Blue"""
if old_json_anchor in text and "org.json.JSONObject" not in text:
    text = text.replace(old_json_anchor, new_json_anchor, 1)

# Backup helpers.
helper_anchor = """class MainActivityV6 : ComponentActivity() {"""
helpers = r'''private fun makeBackupJsonV7(shifts: List<ShiftEntry>, theme: String): String {
    val root = JSONObject()
    root.put("format", "TurniOperaiBackup")
    root.put("version", 7)
    root.put("createdAt", LocalDate.now().toString())
    root.put("theme", theme)
    val array = JSONArray()
    shifts.sortedBy { it.date }.forEach { entry ->
        array.put(
            JSONObject()
                .put("date", entry.date.toString())
                .put("type", entry.type.name)
                .put("overtime", entry.overtime)
        )
    }
    root.put("shifts", array)
    return root.toString(2)
}

private fun parseBackupV7(raw: String): Pair<List<ShiftEntry>, String?> {
    val root = JSONObject(raw)
    require(root.optString("format") == "TurniOperaiBackup") { "File di backup non riconosciuto" }
    val array = root.getJSONArray("shifts")
    val shifts = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(
                ShiftEntry(
                    LocalDate.parse(item.getString("date")),
                    ShiftType.valueOf(item.getString("type")),
                    item.optBoolean("overtime", false)
                )
            )
        }
    }
    val restoredTheme = root.optString("theme").takeIf { it in setOf("light", "dark", "system") }
    return shifts to restoredTheme
}

'''
if helper_anchor in text and "makeBackupJsonV7" not in text:
    text = text.replace(helper_anchor, helpers + helper_anchor, 1)

# Launchers and message state. Keep the same SharedPreferences file so v6 data survives update.
state_anchor = """    val dark = theme == \"dark\" || (theme == \"system\" && androidx.compose.foundation.isSystemInDarkTheme())\n\n    fun save(list: List<ShiftEntry>) {"""
state_replacement = r'''    val dark = theme == "dark" || (theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())
    var backupMessage by remember { mutableStateOf("") }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(makeBackupJsonV7(shifts, theme))
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
                parseBackupV7(raw)
            }.onSuccess { restored ->
                val restoredShifts = restored.first.sortedBy { it.date }
                shifts = restoredShifts
                val editor = prefs.edit().putStringSet(
                    "shifts",
                    restoredShifts.map { "${it.date}|${it.type.name}|${it.overtime}" }.toSet()
                )
                restored.second?.let { restoredTheme ->
                    theme = restoredTheme
                    editor.putString("theme", restoredTheme)
                }
                editor.apply()
                backupMessage = "Backup ripristinato: ${restoredShifts.size} giornate"
            }.onFailure {
                backupMessage = "Backup non valido o danneggiato"
            }
        }
    }

    fun save(list: List<ShiftEntry>) {'''
if state_anchor in text:
    text = text.replace(state_anchor, state_replacement, 1)
else:
    raise SystemExit("State anchor not found")

# Pass backup actions to Settings.
settings_call = """                        else -> SettingsV6(\n                            theme = theme,\n                            onTheme = { value -> theme = value; prefs.edit().putString(\"theme\", value).apply() },\n                            onApply = ::applyGenerated\n                        )"""
settings_call_new = """                        else -> SettingsV6(\n                            theme = theme,\n                            onTheme = { value -> theme = value; prefs.edit().putString(\"theme\", value).apply() },\n                            onApply = ::applyGenerated,\n                            onBackup = { createBackupLauncher.launch(\"TurniOperai-backup-${LocalDate.now()}.json\") },\n                            onRestore = { restoreBackupLauncher.launch(arrayOf(\"application/json\", \"text/plain\")) },\n                            backupMessage = backupMessage\n                        )"""
if settings_call in text:
    text = text.replace(settings_call, settings_call_new, 1)
else:
    raise SystemExit("Settings call anchor not found")

# Monthly summary: weekday rests only; replace total shifts with worked Saturdays.
summary_old = r'''private fun SummaryV6(shifts: List<ShiftEntry>) {
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
                StatV6("Turni", "$work", Icons.Default.Schedule, Color(0xFFE6F7EE), V6Green, Modifier.weight(1f))
                StatV6("Notti", "$nights", Icons.Default.DarkMode, Color(0xFFE8EEFF), V6Blue, Modifier.weight(1f))
                StatV6("Straord.", "$overtime", Icons.Default.Bolt, Color(0xFFFFF2D8), V6Orange, Modifier.weight(1f))
                StatV6("Riposi", "$rests", Icons.Default.Hotel, Color(0xFFF1EAFF), V6Purple, Modifier.weight(1f))
            }
        }
    }
}'''
summary_new = r'''private fun SummaryV6(shifts: List<ShiftEntry>) {
    val workTypes = setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY, ShiftType.SPLIT, ShiftType.HOLIDAY, ShiftType.DOUBLE)
    val saturdaysWorked = shifts.count { it.date.dayOfWeek == DayOfWeek.SATURDAY && it.type in workTypes }
    val nights = shifts.count { it.type == ShiftType.NIGHT }
    val overtime = shifts.count { it.overtime }
    val weekdayRests = shifts.count { it.type == ShiftType.REST && it.date.dayOfWeek.value in 1..5 }
    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Riepilogo mese", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatV6("Sabati lav.", "$saturdaysWorked", Icons.Default.EventAvailable, Color(0xFFE6F7EE), V6Green, Modifier.weight(1f))
                StatV6("Notti", "$nights", Icons.Default.DarkMode, Color(0xFFE8EEFF), V6Blue, Modifier.weight(1f))
                StatV6("Straord.", "$overtime", Icons.Default.Bolt, Color(0xFFFFF2D8), V6Orange, Modifier.weight(1f))
                StatV6("Riposi L-V", "$weekdayRests", Icons.Default.Hotel, Color(0xFFF1EAFF), V6Purple, Modifier.weight(1f))
            }
        }
    }
}'''
if summary_old in text:
    text = text.replace(summary_old, summary_new, 1)
else:
    raise SystemExit("Summary anchor not found")

# Extend Settings signature.
settings_sig = """private fun SettingsV6(theme: String, onTheme: (String) -> Unit, onApply: (List<ShiftEntry>) -> Unit) {"""
settings_sig_new = """private fun SettingsV6(\n    theme: String,\n    onTheme: (String) -> Unit,\n    onApply: (List<ShiftEntry>) -> Unit,\n    onBackup: () -> Unit,\n    onRestore: () -> Unit,\n    backupMessage: String\n) {"""
if settings_sig in text:
    text = text.replace(settings_sig, settings_sig_new, 1)
else:
    raise SystemExit("Settings signature anchor not found")

# Add Backup / Restore card before the note.
backup_anchor = r'''        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Nota", fontWeight = FontWeight.Bold)'''
backup_block = r'''        item {
            SettingsCardV6("5. Backup e ripristino", "Salva i turni in un file e ripristinali se cambi telefono o reinstalli l'app.", Icons.Default.Save, V6Teal) {
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
                Text("Il backup contiene calendario dei turni, straordinari e tema dell'app. Il file resta dove scegli tu sul telefono o nel cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Nota", fontWeight = FontWeight.Bold)'''
if backup_anchor in text:
    text = text.replace(backup_anchor, backup_block, 1)
else:
    raise SystemExit("Backup insertion anchor not found")

text = text.replace('Text("Turni Operai 6.0", color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text("Turni Operai 7.0", color = MaterialTheme.colorScheme.onSurfaceVariant)', 1)

src.write_text(text, encoding="utf-8")

# Bump APK version while keeping the same stable signing key used by v6.
gradle = Path("TurniOperai/app/build.gradle.kts")
g = gradle.read_text(encoding="utf-8")
g = g.replace('versionCode = 6', 'versionCode = 7', 1)
g = g.replace('versionName = "6.0"', 'versionName = "7.0"', 1)
gradle.write_text(g, encoding="utf-8")

print("Turni Operai v7 patch applied")

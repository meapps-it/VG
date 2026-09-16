from pathlib import Path

src = Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivityV6.kt")
text = src.read_text(encoding="utf-8")

# Imports.
if "rememberLauncherForActivityResult" not in text:
    text = text.replace(
        "import androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\n",
        "import androidx.activity.ComponentActivity\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.compose.setContent\nimport androidx.activity.result.contract.ActivityResultContracts\n",
        1,
    )
if "org.json.JSONObject" not in text:
    text = text.replace(
        "import java.util.Locale\n",
        "import java.util.Locale\nimport org.json.JSONArray\nimport org.json.JSONObject\n",
        1,
    )

# Backup helpers.
if "private fun makeBackupJsonV7" not in text:
    helpers = r'''private fun makeBackupJsonV7(shifts: List<ShiftEntry>, theme: String): String {
    val root = JSONObject()
    root.put("format", "TurniOperaiBackup")
    root.put("version", 7)
    root.put("createdAt", LocalDate.now().toString())
    root.put("theme", theme)
    val array = JSONArray()
    shifts.sortedBy { it.date }.forEach { entry ->
        array.put(JSONObject().put("date", entry.date.toString()).put("type", entry.type.name).put("overtime", entry.overtime))
    }
    root.put("shifts", array)
    return root.toString(2)
}

private fun parseBackupV7(raw: String): Pair<List<ShiftEntry>, String?> {
    val root = JSONObject(raw)
    require(root.optString("format") == "TurniOperaiBackup") { "File di backup non riconosciuto" }
    val array = root.getJSONArray("shifts")
    val restored = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(ShiftEntry(LocalDate.parse(item.getString("date")), ShiftType.valueOf(item.getString("type")), item.optBoolean("overtime", false)))
        }
    }
    val restoredTheme = root.optString("theme").takeIf { it in setOf("light", "dark", "system") }
    return restored to restoredTheme
}

'''
    anchor = "class MainActivityV6 : ComponentActivity() {"
    if anchor not in text:
        raise SystemExit("Activity anchor not found")
    text = text.replace(anchor, helpers + anchor, 1)

# Backup launchers inside the main composable.
if "val createBackupLauncher" not in text:
    anchor = "    fun save(list: List<ShiftEntry>) {"
    if anchor not in text:
        raise SystemExit("save() anchor not found")
    launchers = r'''    var backupMessage by remember { mutableStateOf("") }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(makeBackupJsonV7(shifts, theme)) }
                    ?: error("Impossibile aprire il file")
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

'''
    text = text.replace(anchor, launchers + anchor, 1)

# Wire backup actions into Settings.
if "onBackup = { createBackupLauncher.launch" not in text:
    old = '''                        else -> SettingsV6(
                            theme = theme,
                            onTheme = { value -> theme = value; prefs.edit().putString("theme", value).apply() },
                            onApply = ::applyGenerated
                        )'''
    new = '''                        else -> SettingsV6(
                            theme = theme,
                            onTheme = { value -> theme = value; prefs.edit().putString("theme", value).apply() },
                            onApply = ::applyGenerated,
                            onBackup = { createBackupLauncher.launch("TurniOperai-backup-${LocalDate.now()}.json") },
                            onRestore = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                            backupMessage = backupMessage
                        )'''
    if old not in text:
        raise SystemExit("Settings call anchor not found")
    text = text.replace(old, new, 1)

# Replace monthly summary.
summary_start = text.find("@Composable\nprivate fun SummaryV6(shifts: List<ShiftEntry>) {")
summary_end = text.find("\n@Composable\nprivate fun StatV6", summary_start)
if summary_start < 0 or summary_end < 0:
    raise SystemExit("Summary boundaries not found")
summary = r'''@Composable
private fun SummaryV6(shifts: List<ShiftEntry>) {
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
}
'''
text = text[:summary_start] + summary + text[summary_end:]

# Extend Settings signature.
if "backupMessage: String" not in text:
    old = "private fun SettingsV6(theme: String, onTheme: (String) -> Unit, onApply: (List<ShiftEntry>) -> Unit) {"
    new = '''private fun SettingsV6(
    theme: String,
    onTheme: (String) -> Unit,
    onApply: (List<ShiftEntry>) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    backupMessage: String
) {'''
    if old not in text:
        raise SystemExit("Settings signature anchor not found")
    text = text.replace(old, new, 1)

# Add Backup / Restore card immediately before the Note card.
if 'SettingsCardV6("5. Backup e ripristino"' not in text:
    note_text = '                    Text("Nota", fontWeight = FontWeight.Bold)'
    note_pos = text.find(note_text)
    if note_pos < 0:
        raise SystemExit("Note card not found")
    item_pos = text.rfind("        item {", 0, note_pos)
    if item_pos < 0:
        raise SystemExit("Note item start not found")
    backup_card = r'''        item {
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
'''
    text = text[:item_pos] + backup_card + text[item_pos:]

text = text.replace('Text("Turni Operai 6.0", color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text("Turni Operai 7.0", color = MaterialTheme.colorScheme.onSurfaceVariant)')
src.write_text(text, encoding="utf-8")

# Version bump. Stable signing configuration is deliberately left unchanged.
gradle = Path("TurniOperai/app/build.gradle.kts")
g = gradle.read_text(encoding="utf-8")
g = g.replace("versionCode = 6", "versionCode = 7", 1)
g = g.replace('versionName = "6.0"', 'versionName = "7.0"', 1)
gradle.write_text(g, encoding="utf-8")

print("Turni Operai v7 patch applied")

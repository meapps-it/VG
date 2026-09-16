from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/com/meapps/turnioperai/MainActivityV6.kt"
gradle = root / "app/build.gradle.kts"

src = main.read_text(encoding="utf-8")

def rep(old, new, label):
    global src
    if old not in src:
        raise SystemExit(f"Missing marker: {label}")
    src = src.replace(old, new, 1)

rep("import androidx.activity.ComponentActivity\n", "import androidx.activity.ComponentActivity\nimport androidx.activity.compose.BackHandler\n", "back import")
rep(
    "private val vacationPresetsV8 = listOf(\n",
    """private val vacationPresetsV8 = listOf(\n    VacationPresetV8(\n        \"textile_industry\",\n        \"Tessile / Abbigliamento Industria - 4 settimane\",\n        \"Preset 40 ore settimanali: 160 ore annue, 13h20m al mese. Verifica sempre il cedolino.\",\n        13 * 60 + 20\n    ),\n    VacationPresetV8(\n        \"textile_pmi\",\n        \"Tessile PMI / Uniontessile - 4 settimane\",\n        \"Preset 40 ore settimanali: 160 ore annue, 13h20m al mese. Verifica sempre il cedolino.\",\n        13 * 60 + 20\n    ),\n    VacationPresetV8(\n        \"textile_craft\",\n        \"Tessile / Moda Artigianato - 4 settimane\",\n        \"Preset 40 ore settimanali: 160 ore annue, 13h20m al mese. Verifica sempre il cedolino.\",\n        13 * 60 + 20\n    ),\n""",
    "textile presets",
)
rep("    var tab by remember { mutableIntStateOf(0) }\n", "    var tab by remember { mutableIntStateOf(0) }\n    val tabHistory = remember { mutableStateListOf<Int>() }\n", "tab history")
marker = """    fun applyGenerated(generated: List<ShiftEntry>) {\n        val dates = generated.map { it.date }.toSet()\n        save(shifts.filterNot { it.date in dates } + generated)\n    }\n\n"""
insert = marker + """    fun navigateTo(index: Int) {\n        if (index != tab) {\n            tabHistory.add(tab)\n            tab = index\n        }\n    }\n\n    BackHandler(enabled = menuOpen || editDate != null || tabHistory.isNotEmpty() || tab != 0) {\n        when {\n            menuOpen -> menuOpen = false\n            editDate != null -> editDate = null\n            tabHistory.isNotEmpty() -> tab = tabHistory.removeAt(tabHistory.lastIndex)\n            tab != 0 -> tab = 0\n        }\n    }\n\n"""
rep(marker, insert, "navigation handler")
rep('MenuItemV6("Home", Icons.Default.Home) { tab = 0; menuOpen = false }', 'MenuItemV6("Home", Icons.Default.Home) { navigateTo(0); menuOpen = false }', "menu home")
rep('MenuItemV6("Statistiche", Icons.Default.BarChart) { tab = 1; menuOpen = false }', 'MenuItemV6("Statistiche", Icons.Default.BarChart) { navigateTo(1); menuOpen = false }', "menu stats")
rep('MenuItemV6("Calendario", Icons.Default.CalendarMonth) { tab = 2; menuOpen = false }', 'MenuItemV6("Calendario", Icons.Default.CalendarMonth) { navigateTo(2); menuOpen = false }', "menu calendar")
rep('MenuItemV6("Impostazione turni", Icons.Default.Settings) { tab = 3; menuOpen = false }', 'MenuItemV6("Impostazione turni", Icons.Default.Settings) { navigateTo(3); menuOpen = false }', "menu settings")
rep('onClick = { tab = index },', 'onClick = { navigateTo(index) },', "bottom nav")
rep('Text("Scegli come lavori. Poi imposta il ciclo senza inventarti formule da NASA.", color = MaterialTheme.colorScheme.onSurfaceVariant)', 'Text("Scegli la turnazione e configura il ciclo di lavoro.", color = MaterialTheme.colorScheme.onSurfaceVariant)', "settings copy")
rep('item { Text("Turni Operai 9.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }', 'item { Text("Turni Operai 10.0", color = MaterialTheme.colorScheme.onSurfaceVariant) }', "footer")
main.write_text(src, encoding="utf-8")

build = gradle.read_text(encoding="utf-8")
if 'versionCode = 9' not in build or 'versionName = "9.0"' not in build:
    raise SystemExit("Version markers missing")
build = build.replace('versionCode = 9', 'versionCode = 10', 1)
build = build.replace('versionName = "9.0"', 'versionName = "10.0"', 1)
gradle.write_text(build, encoding="utf-8")
print("Turni Operai v10 patch applied")

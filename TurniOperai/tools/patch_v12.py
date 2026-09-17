from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / "app/src/main/java/com/meapps/turnioperai/MainActivityV6.kt"
gradle = root / "app/build.gradle.kts"

text = main.read_text(encoding="utf-8")
old_menu = '''                                    MenuItemV6("Home", Icons.Default.Home) { navigateTo(0); menuOpen = false }
                                    MenuItemV6("Statistiche", Icons.Default.BarChart) { navigateTo(1); menuOpen = false }
                                    MenuItemV6("Calendario", Icons.Default.CalendarMonth) { navigateTo(2); menuOpen = false }
                                    MenuItemV6("Impostazione turni", Icons.Default.Settings) { navigateTo(3); menuOpen = false }
'''
new_menu = '''                                    MenuItemV6("Home", Icons.Default.Home) { navigateTo(0); menuOpen = false }
                                    MenuItemV6("Statistiche", Icons.Default.BarChart) { navigateTo(1); menuOpen = false }
                                    MenuItemV6("Calendario", Icons.Default.CalendarMonth) { navigateTo(2); menuOpen = false }
                                    MenuItemV6("Impostazione turni", Icons.Default.Settings) { navigateTo(3); menuOpen = false }
                                    MenuItemV6("Account", Icons.Default.AccountCircle) { openAccountV11(context); menuOpen = false }
'''
if old_menu not in text:
    raise SystemExit("menu block not found")
text = text.replace(old_menu, new_menu, 1)
main.write_text(text, encoding="utf-8")

g = gradle.read_text(encoding="utf-8")
g = g.replace('versionCode = 11', 'versionCode = 12', 1)
g = g.replace('versionName = "11.0"', 'versionName = "12.0"', 1)
gradle.write_text(g, encoding="utf-8")

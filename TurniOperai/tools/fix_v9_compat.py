from pathlib import Path

files = [
    Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivityV3.kt"),
    Path("TurniOperai/app/src/main/java/com/meapps/turnioperai/MainActivityV5.kt"),
]

for path in files:
    text = path.read_text(encoding="utf-8")
    if "ShiftType.ROL ->" not in text:
        text = text.replace(
            '    ShiftType.PERMIT ->',
            '    ShiftType.ROL -> ' + ('Color(0xFFE8F4FF) to Color(0xFF2563EB)\n    ' if path.name == 'MainActivityV3.kt' else 'ShiftVisual5(Color(0xFFE8F4FF), Color(0xFF2563EB), Icons.Default.AccessTime)\n    ') + 'ShiftType.PERMIT ->',
            1,
        )
    path.write_text(text, encoding="utf-8")

print("v9 compatibility sources patched")

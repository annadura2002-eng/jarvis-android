#!/bin/bash
set -e

echo "=== 1. ДОБАВЛЯЕМ ТЕМЫ В styles.xml ==="
# Проверяем, есть ли styles.xml
if [ -f "app/src/main/res/values/styles.xml" ]; then
    if ! grep -q "Theme.Jarvis.Light" app/src/main/res/values/styles.xml; then
        cat << 'XML' >> app/src/main/res/values/styles.xml
        <style name="Theme.Jarvis.Light" parent="Theme.Material3.DayNight">
            <item name="colorPrimary">#0B0E14</item>
            <item name="colorOnPrimary">#FFFFFF</item>
            <item name="android:windowBackground">@drawable/bg_light_gradient</item>
        </style>
XML
    fi
fi

echo "=== 2. СОЗДАЁМ ФОНЫ ==="
cat << 'INNER' > app/src/main/res/drawable/bg_light_gradient.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="135"
        android:endColor="#E5E7EB"
        android:startColor="#F3F4F6"
        android:type="linear" />
</shape>
INNER

cat << 'INNER' > app/src/main/res/drawable/bg_dark_gradient.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="135"
        android:endColor="#0B0E14"
        android:startColor="#151A24"
        android:type="linear" />
</shape>
INNER

echo "=== 3. ПЕРЕПИСЫВАЕМ MainActivity (логика темы) ==="
python3 - << 'PYEOF'
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# В методе showBottomSheetMenu меняем обработчик темы
old_theme = '''view.findViewById<LinearLayout>(R.id.btnMenuTheme)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            }
        }'''

new_theme = '''view.findViewById<LinearLayout>(R.id.btnMenuTheme)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
            val isDark = prefs.getBoolean("is_dark_theme", true)
            val newMode = if (isDark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            prefs.edit().putBoolean("is_dark_theme", !isDark).apply()
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newMode)
            recreate()
        }'''

content = content.replace(old_theme, new_theme)

# В onCreate применяем тему при запуске
if 'AppCompatDelegate.setDefaultNightMode' not in content:
    content = content.replace(
        'super.onCreate(savedInstanceState)',
        '''super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("is_dark_theme", true)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (isDark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )'''
    )
else:
    # Обновляем существующую тему
    content = content.replace(
        'PrefsManager.init(this)',
        '''PrefsManager.init(this)
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("is_dark_theme", true)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (isDark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )'''
    )

with open(filepath, 'w') as f:
    f.write(content)
print("✅ Логика смены темы обновлена.")
PYEOF

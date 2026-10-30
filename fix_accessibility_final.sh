#!/bin/bash
set -e

echo "=== ПЕРЕЗАПИСЫВАЕМ ПРОВЕРКУ В PermissionsActivity.kt ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/PermissionsActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

new_check = '''    private fun isAccessibilityServiceEnabled(): Boolean {
        // Проверяем, есть ли наш пакет в списке включённых служб доступности.
        // Это надёжнее, чем искать полное имя класса, т.к. система может его форматировать по-разному.
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName)
    }'''

# Заменяем старую функцию на новую
old_check = re.search(r'private fun isAccessibilityServiceEnabled\(\): Boolean \{.*?\n    \}', content, re.DOTALL)
if old_check:
    content = content.replace(old_check.group(0), new_check)
    with open(filepath, 'w') as f:
        f.write(content)
    print("✅ Проверка Accessibility обновлена: теперь ищет пакет в списке.")
else:
    print("❌ Не найдена старая проверка, добавляем новую...")
    # Добавляем перед последней скобкой класса
    content = content.rstrip()[:-1] + new_check + '\n}\n'
    with open(filepath, 'w') as f:
        f.write(content)
    print("✅ Добавлена новая проверка.")
PYEOF

echo "=== ПРОВЕРЯЕМ МАНИФЕСТ ==="
if grep -q "JarvisAccessibilityService" app/src/main/AndroidManifest.xml; then
    echo "✅ JarvisAccessibilityService есть в манифесте."
    grep -A 3 "JarvisAccessibilityService" app/src/main/AndroidManifest.xml
else
    echo "❌ Служба НЕ найдена в манифесте!"
fi

echo "✅ Готово!"

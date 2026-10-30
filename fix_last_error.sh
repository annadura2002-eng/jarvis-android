#!/bin/bash

echo "=== ФИКСИМ PermissionsActivity.kt ==="
# Заменяем инициализацию btnPhoneSms на заглушку
sed -i 's/val btnPhoneSms: Button/val btnPhoneSms: Button? = null/g' app/src/main/java/com/jarvis/app/PermissionsActivity.kt

# Убираем поиск btnPhoneSms по ID (оставляем как null)
sed -i 's/btnPhoneSms = findViewById(R.id.btnPhoneSms)/btnPhoneSms = null/g' app/src/main/java/com/jarvis/app/PermissionsActivity.kt

# Убираем клик по btnPhoneSms
sed -i 's/btnPhoneSms.setOnClickListener { requestPerms.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)) }//g' app/src/main/java/com/jarvis/app/PermissionsActivity.kt

# Убираем обновление текста для btnPhoneSms
sed -i 's/if (hasPhone \&\& hasSms) btnPhoneSms.text = "Выдано ✓" else btnPhoneSms.text = "Выдать"//g' app/src/main/java/com/jarvis/app/PermissionsActivity.kt

echo "✅ Кнопка btnPhoneSms удалена из кода (её и так нет в вёрстке)."

# Проверяем что осталось
echo "Проверка ссылок на btnPhoneSms:"
grep -n "btnPhoneSms" app/src/main/java/com/jarvis/app/PermissionsActivity.kt || echo "✅ Ссылок больше нет"

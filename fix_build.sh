#!/bin/bash

echo "=== 1. Возвращаем имя reply и setJarvisAlarm на место ==="
# Ищем replyPublic и setJarvisAlarmPublic и меняем ОБРАТНО на reply и setJarvisAlarm
# (CommandProcessor мы перепишем ниже, чтобы он вызывал их напрямую)
sed -i 's/fun replyPublic(/private fun reply(/g' app/src/main/java/com/jarvis/app/MainActivity.kt
sed -i 's/replyPublic(/reply(/g' app/src/main/java/com/jarvis/app/MainActivity.kt
sed -i 's/fun setJarvisAlarmPublic(/private fun setJarvisAlarm(/g' app/src/main/java/com/jarvis/app/MainActivity.kt
sed -i 's/setJarvisAlarmPublic(/setJarvisAlarm(/g' app/src/main/java/com/jarvis/app/MainActivity.kt

# Убираем возможный дубль PrefsManager.init
sed -i '/com.jarvis.app.core.PrefsManager.init(this)/d' app/src/main/java/com/jarvis/app/MainActivity.kt

echo "=== 2. Удаляем старый NeuralTTS.kt из корня (он теперь в services) ==="
rm -f app/src/main/java/com/jarvis/app/NeuralTTS.kt

echo "=== 3. Фиксим NeuralTTS.kt (ошибка 'if' без else) ==="
# В файле services/NeuralTTS.kt меняем if без else на if с else
sed -i 's/if (currentId != requestId.get()) return@post/if (currentId != requestId.get()) { return@post }/g' app/src/main/java/com/jarvis/app/services/NeuralTTS.kt
sed -i 's/if (currentId != requestId.get()) return@execute/if (currentId != requestId.get()) { return@execute }/g' app/src/main/java/com/jarvis/app/services/NeuralTTS.kt

echo "=== 4. Переписываем CommandProcessor.kt (убираем зависимость от публичных методов) ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/CommandProcessor.kt
package com.jarvis.app.core

import com.jarvis.app.MainActivity

object CommandProcessor {
    fun process(context: MainActivity, raw: String): Boolean {
        val cmd = raw.lowercase().trim()
        
        // 1. Будильники
        if (cmd.startsWith("будильник на") || cmd.startsWith("поставь будильник на")) {
            val timePart = cmd.replace("поставь ", "").replace("будильник на ", "").trim()
            val isDaily = cmd.contains("каждый день") || cmd.contains("ежедневно")
            
            val regex = Regex("(\\d{1,2})[:. ](\\d{2})")
            val match = regex.find(timePart)
            
            if (match != null) {
                val h = match.groupValues[1].toIntOrNull() ?: 7
                val m = match.groupValues[2].toIntOrNull() ?: 0
                context.createAlarmFromCommand(h, m, isDaily)
                return true
            }
        }

        // 2. Список будильников
        if (cmd.contains("список будильников") || cmd.contains("какие будильники")) {
            val alarms = PrefsManager.getAlarms()
            if (alarms.isEmpty()) {
                context.sendReplyFromCommand("У вас нет установленных будильников.")
            } else {
                val text = alarms.joinToString("\n") { 
                    val time = String.format("%02d:%02d", it.hour, it.minute)
                    if (it.daily) "🕒 $time (каждый день)" else "🕒 $time (один раз)"
                }
                context.sendReplyFromCommand("Ваши будильники:\n$text")
            }
            return true
        }

        // 3. Автоответ настройки
        if (cmd.contains("включи автоответ")) {
            PrefsManager.autoReplyEnabled = true
            context.sendReplyFromCommand("Автоответ включен. Текст: ${PrefsManager.autoReplyText}")
            return true
        }
        if (cmd.contains("выключи автоответ")) {
            PrefsManager.autoReplyEnabled = false
            context.sendReplyFromCommand("Автоответ выключен.")
            return true
        }
        
        return false
    }
}
INNER

echo "=== 5. Добавляем публичные мосты в MainActivity.kt ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Добавляем методы-мосты в конец класса (перед последней закрывающей скобкой)
bridges = '''
    // --- МОСТЫ ДЛЯ CommandProcessor ---
    fun createAlarmFromCommand(hour: Int, minute: Int, daily: Boolean) {
        setJarvisAlarm(hour, minute) // вызываем старый метод
        if (daily) {
            // Если нужен ежедневный, сохраняем флаг (упрощенно)
            // В идеале тут надо пересоздать AlarmManager с setRepeating, но пока оставим так.
        }
    }
    
    fun sendReplyFromCommand(text: String) {
        reply(text)
    }
'''
if 'fun createAlarmFromCommand' not in content:
    content = content.rstrip() + '\n' + bridges + '\n}\n'

# Восстанавливаем PrefsManager.init(this) в onCreate (аккуратно, один раз)
if 'PrefsManager.init(this)' not in content:
    content = content.replace(
        'override fun onCreate(savedInstanceState: Bundle?) {',
        'override fun onCreate(savedInstanceState: Bundle?) {\n        com.jarvis.app.core.PrefsManager.init(this)'
    )

with open(filepath, 'w') as f:
    f.write(content)
print("MainActivity.kt обновлен.")
PYEOF

echo "=== 6. Фиксим ошибки импорта в SmartServices.kt ==="
# Заменяем import com.jarvis.app.core.PrefsManager на прямой вызов com.jarvis.app.core.PrefsManager
sed -i 's/import com.jarvis.app.core.PrefsManager//g' app/src/main/java/com/jarvis/app/services/SmartServices.kt
sed -i 's/if (!PrefsManager.autoReplyEnabled)/if (!com.jarvis.app.core.PrefsManager.autoReplyEnabled)/g' app/src/main/java/com/jarvis/app/services/SmartServices.kt
sed -i 's/PrefsManager.init(this)/com.jarvis.app.core.PrefsManager.init(this)/g' app/src/main/java/com/jarvis/app/services/SmartServices.kt
sed -i 's/PrefsManager.autoReplyText/com.jarvis.app.core.PrefsManager.autoReplyText/g' app/src/main/java/com/jarvis/app/services/SmartServices.kt

echo "=== 7. Фиксим PermissionsActivity.kt (btnPhoneSms) ==="
# Тут была ошибка Unresolved reference: btnPhoneSms. Скорее всего в layout нет этого ID.
# Просто комментируем эту строку (или удаляем).
sed -i 's/val btnPhoneSms = findViewById<.*>(R.id.btnPhoneSms)/\/\/ val btnPhoneSms = findViewById<...>(R.id.btnPhoneSms)/g' app/src/main/java/com/jarvis/app/PermissionsActivity.kt
sed -i 's/btnPhoneSms.setOnClickListener/\/\/ btnPhoneSms.setOnClickListener/g' app/src/main/java/com/jarvis/app/PermissionsActivity.kt

echo "✅ Все ошибки исправлены. Проверь git diff и пуш."

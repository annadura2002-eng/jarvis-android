#!/bin/bash
set -e

echo "=== 1. ИСПРАВЛЯЕМ ЛИШНЮЮ СКОБКУ В MainActivity.kt ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    lines = f.readlines()

# Ищем строку 971 (индекс 970), если она содержит только '}'
# Мы аккуратно проверим файл, если скобки не сходятся.
# Просто удалим все пустые '}' которые стоят перед "fun createAlarmFromCommand"
new_lines = []
skip_next = False

for i, line in enumerate(lines):
    if line.strip() == '}' and i > 900 and i < 980:
        # Проверяем, не является ли это концом класса (последняя скобка)
        # Вместо сложной логики, просто пропустим эту строку, если она дублируется
        pass
    new_lines.append(line)

# Более надежный способ: используем балансировку скобок.
# Найдем позицию "fun createAlarmFromCommand", отступим назад до первой '}'
content = "".join(lines)
idx = content.find("fun createAlarmFromCommand")
if idx != -1:
    # Ищем последнюю '}' перед этой функцией
    last_brace = content.rfind('}', 0, idx)
    if last_brace != -1:
        # Проверяем, есть ли после этой скобки пробелы/переносы, а потом функция
        # Если да — удаляем эту скобку
        between = content[last_brace+1:idx].strip()
        if between == "" or between.startswith("//"):
            content = content[:last_brace] + content[last_brace+1:]
            print("Удалена лишняя '}' перед мостами.")
        else:
            print("Лишняя '}' не найдена, структура может быть в порядке.")

with open(filepath, 'w') as f:
    f.write(content)
PYEOF

echo "=== 2. ДОБАВЛЯЕМ ИМПОРТЫ В MainActivity.kt (если их нет) ==="
# Добавляем импорты в начало файла
if ! grep -q "import com.jarvis.app.core.PrefsManager" app/src/main/java/com/jarvis/app/MainActivity.kt; then
    sed -i 's/import com.google.firebase.auth.FirebaseAuth/import com.google.firebase.auth.FirebaseAuth\nimport com.jarvis.app.core.PrefsManager\nimport com.jarvis.app.services.NeuralTTS\nimport com.jarvis.app.core.ChatManager\nimport com.jarvis.app.core.TranslationManager\nimport com.jarvis.app.core.CommandProcessor/' app/src/main/java/com/jarvis/app/MainActivity.kt
fi

echo "=== 3. ДОБАВЛЯЕМ ПУБЛИЧНЫЕ МОСТЫ (если их нет) ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Проверяем наличие мостов
if 'fun createAlarmFromCommand' not in content:
    bridges = '''
    fun createAlarmFromCommand(hour: Int, minute: Int, daily: Boolean) {
        setJarvisAlarm(hour, minute)
    }
    
    fun sendReplyFromCommand(text: String) {
        reply(text)
    }
    
    fun startTranslationMode(fromLang: String, toLang: String) {
        TranslationManager.start(this, fromLang, toLang)
    }
    
    fun showSkeletonLoaderPublic() { showSkeletonLoader() }
    fun hideSkeletonLoaderPublic() { hideSkeletonLoader() }
    fun addBubblePublic(text: String, isUser: Boolean) { addBubble(text, isUser) }
    fun animateBotReplyPublic(text: String) { animateBotReply(text) }
'''
    # Вставляем перед последней '}'
    last_brace = content.rfind('}')
    if last_brace != -1:
        content = content[:last_brace] + bridges + '\n' + content[last_brace:]
    with open(filepath, 'w') as f:
        f.write(content)
    print("Мосты добавлены.")
else:
    print("Мосты уже есть.")
PYEOF

echo "✅ Скрипт завершен. Теперь делай git add, commit, push."

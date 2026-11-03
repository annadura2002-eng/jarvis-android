#!/bin/bash
set -e

echo "=== 1. ИСПРАВЛЯЕМ ChatManager.kt ==="
sed -i 's/put("model", "gpt-4o-mini")/put("model", "deepseek-chat")/g' app/src/main/java/com/jarvis/app/core/ChatManager.kt
sed -i 's|https://api.proxyapi.ru/openai/v1/chat/completions|https://api.proxyapi.ru/deepseek/chat/completions|g' app/src/main/java/com/jarvis/app/core/ChatManager.kt

echo "=== 2. ИСПРАВЛЯЕМ TranslationManager.kt ==="
sed -i 's/put("model", "gpt-4o-mini")/put("model", "deepseek-chat")/g' app/src/main/java/com/jarvis/app/core/TranslationManager.kt
sed -i 's|https://api.proxyapi.ru/openai/v1/chat/completions|https://api.proxyapi.ru/deepseek/chat/completions|g' app/src/main/java/com/jarvis/app/core/TranslationManager.kt

echo "=== 3. ИСПРАВЛЯЕМ MainActivity.kt (метод askAiForButton) ==="
# В MainActivity может быть несколько вхождений, меняем все
sed -i 's/put("model", "gpt-4o-mini")/put("model", "deepseek-chat")/g' app/src/main/java/com/jarvis/app/MainActivity.kt
sed -i 's|https://api.proxyapi.ru/openai/v1/chat/completions|https://api.proxyapi.ru/deepseek/chat/completions|g' app/src/main/java/com/jarvis/app/MainActivity.kt

echo "=== 4. ПРОВЕРЯЕМ РЕЗУЛЬТАТ ==="
echo "Осталось упоминаний 'gpt-4o-mini':"
grep -r "gpt-4o-mini" app/src/main/java/com/jarvis/app/ || echo "✅ НЕТ"
echo "Осталось упоминаний 'openai/v1':"
grep -r "openai/v1" app/src/main/java/com/jarvis/app/ || echo "✅ НЕТ"
echo "Новые упоминания 'deepseek':"
grep -r "deepseek" app/src/main/java/com/jarvis/app/ || echo "❌ НЕТ (ошибка!)"

echo "=== 5. ПРОВЕРЯЕМ, ЧТО ОСТАЛОСЬ В КОРНЕ ==="
ls app/src/main/java/com/jarvis/app/*.kt

echo "=== 6. УДАЛЯЕМ СТАРЫЕ ДУБЛИКАТЫ (если есть) ==="
rm -f app/src/main/java/com/jarvis/app/AlarmService.kt
rm -f app/src/main/java/com/jarvis/app/VoiceService.kt
rm -f app/src/main/java/com/jarvis/app/SmartServices.kt
rm -f app/src/main/java/com/jarvis/app/FloatingBubbleService.kt
rm -f app/src/main/java/com/jarvis/app/JarvisAccessibilityService.kt

echo "✅ ВСЁ ГОТОВО! Теперь все запросы идут в DeepSeek."

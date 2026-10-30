#!/bin/bash
set -e

echo "=== 1. ПОЛНАЯ ПРОВЕРКА НАЛИЧИЯ ФАЙЛОВ ==="
FILES=(
  "app/src/main/java/com/jarvis/app/MainActivity.kt"
  "app/src/main/java/com/jarvis/app/PermissionsActivity.kt"
  "app/src/main/java/com/jarvis/app/LoginActivity.kt"
  "app/src/main/java/com/jarvis/app/core/PrefsManager.kt"
  "app/src/main/java/com/jarvis/app/core/CommandProcessor.kt"
  "app/src/main/java/com/jarvis/app/core/ChatManager.kt"
  "app/src/main/java/com/jarvis/app/core/TranslationManager.kt"
  "app/src/main/java/com/jarvis/app/services/NeuralTTS.kt"
  "app/src/main/java/com/jarvis/app/services/AlarmService.kt"
  "app/src/main/java/com/jarvis/app/services/FloatingBubbleService.kt"
  "app/src/main/java/com/jarvis/app/services/VoiceService.kt"
  "app/src/main/java/com/jarvis/app/services/JarvisAccessibilityService.kt"
  "app/src/main/java/com/jarvis/app/services/SmartServices.kt"
  "app/src/main/java/com/jarvis/app/Translit.kt"
  "app/src/main/java/com/jarvis/app/EqualizerView.kt"
  "app/src/main/java/com/jarvis/app/AuroraBackground.kt"
)

MISSING=0
for f in "${FILES[@]}"; do
  if [ -f "$f" ]; then
    echo "✅ $f"
  else
    echo "❌ ОТСУТСТВУЕТ: $f"
    MISSING=$((MISSING+1))
  fi
done

if [ $MISSING -eq 0 ]; then
  echo "✅ Все файлы на месте."
else
  echo "❌ Не хватает $MISSING файлов."
fi

echo ""
echo "=== 2. ПРОВЕРКА ВНУТРЕННИХ ИМПОРТОВ В КАЖДОМ ФАЙЛЕ ==="
for f in "${FILES[@]}"; do
  if [ -f "$f" ]; then
    echo "--- $f ---"
    grep -n "import com.jarvis.app" "$f" || echo "Нет внутренних импортов"
  fi
done

echo ""
echo "=== 3. ПРОВЕРКА ССЫЛОК НА R. (ресурсы) ==="
echo "Список всех ресурсов, которые используются в коде:"
grep -rho "R\.[a-z]*\.[a-zA-Z0-9_]*" app/src/main/java/com/jarvis/app/ | sort -u | head -50
echo "(показаны первые 50, если больше — просто много ресурсов)"

echo ""
echo "=== 4. ПРОВЕРКА НАЛИЧИЯ МЕТОДА destroy() В NeuralTTS ==="
grep -n "fun destroy()" app/src/main/java/com/jarvis/app/services/NeuralTTS.kt || echo "❌ НЕТ destroy()"

echo ""
echo "=== 5. ПРОВЕРКА НАЛИЧИЯ onDestroy В FloatingBubbleService ==="
grep -n "override fun onDestroy" app/src/main/java/com/jarvis/app/services/FloatingBubbleService.kt || echo "❌ НЕТ onDestroy()"

echo ""
echo "=== 6. ПРОВЕРКА НАЛИЧИЯ КЛАССА Translit ==="
grep -n "object Translit" app/src/main/java/com/jarvis/app/Translit.kt || echo "❌ НЕТ object Translit"
grep -n "fun toLatin" app/src/main/java/com/jarvis/app/Translit.kt || echo "❌ НЕТ fun toLatin"

echo ""
echo "=== 7. ПРОВЕРКА НАЛИЧИЯ version.json ==="
cat version.json

echo ""
echo "=== 8. ПРОВЕРКА НАЛИЧИЯ AndroidManifest.xml (только ключевые сервисы) ==="
grep -E "VoiceService|AlarmService|FloatingBubbleService|JarvisAccessibilityService|SmartNotificationService" app/src/main/AndroidManifest.xml || echo "❌ В манифесте нет упоминаний сервисов"

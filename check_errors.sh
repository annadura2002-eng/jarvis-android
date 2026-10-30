#!/bin/bash

echo "=== 1. ПРОВЕРКА НАЛИЧИЯ ВСЕХ ФАЙЛОВ ==="
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

for f in "${FILES[@]}"; do
  if [ -f "$f" ]; then
    echo "✅ $f"
  else
    echo "❌ ОТСУТСТВУЕТ: $f"
  fi
done

echo ""
echo "=== 2. ПРОВЕРКА ИМПОРТОВ В MainActivity.kt ==="
grep -n "import com.jarvis.app" app/src/main/java/com/jarvis/app/MainActivity.kt || echo "Нет внутренних импортов"

echo ""
echo "=== 3. ПРОВЕРКА ПАРНЫХ СКОБОК В MainActivity.kt ==="
OPEN=$(grep -o "{" app/src/main/java/com/jarvis/app/MainActivity.kt | wc -l)
CLOSE=$(grep -o "}" app/src/main/java/com/jarvis/app/MainActivity.kt | wc -l)
echo "Открывающих { : $OPEN"
echo "Закрывающих } : $CLOSE"

if [ "$OPEN" -eq "$CLOSE" ]; then
  echo "✅ Скобки сбалансированы"
else
  echo "❌ НЕСБАЛАНСИРОВАНЫ! Разница: $((OPEN - CLOSE))"
fi

echo ""
echo "=== 4. ПРОВЕРКА НАЛИЧИЯ КРИТИЧЕСКИХ МЕТОДОВ ==="
grep -n "fun sendReplyFromCommand" app/src/main/java/com/jarvis/app/MainActivity.kt || echo "❌ НЕТ sendReplyFromCommand"
grep -n "fun createAlarmFromCommand" app/src/main/java/com/jarvis/app/MainActivity.kt || echo "❌ НЕТ createAlarmFromCommand"
grep -n "fun startTranslationMode" app/src/main/java/com/jarvis/app/MainActivity.kt || echo "❌ НЕТ startTranslationMode"
grep -n "fun showSkeletonLoaderPublic" app/src/main/java/com/jarvis/app/MainActivity.kt || echo "❌ НЕТ showSkeletonLoaderPublic"

echo ""
echo "=== 5. ПРОВЕРКА ВЕРСИИ В version.json ==="
cat version.json

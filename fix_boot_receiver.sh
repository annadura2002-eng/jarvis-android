#!/bin/bash
set -e

echo "=== ФИКСИМ BootReceiver.kt ==="
# Добавляем импорт, если его нет
if ! grep -q "import com.jarvis.app.services.VoiceService" app/src/main/java/com/jarvis/app/BootReceiver.kt; then
    sed -i 's/^import android.content.BroadcastReceiver/import android.content.BroadcastReceiver\nimport com.jarvis.app.services.VoiceService/' app/src/main/java/com/jarvis/app/BootReceiver.kt
fi

# Проверяем, что импорт встал
grep -n "VoiceService" app/src/main/java/com/jarvis/app/BootReceiver.kt || echo "❌ Импорт не добавлен!"

echo "✅ BootReceiver.kt исправлен (добавлен импорт VoiceService)."

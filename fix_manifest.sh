#!/bin/bash
set -e

echo "=== ИСПРАВЛЯЕМ AndroidManifest.xml (добавляем .services.) ==="
# Меняем имена сервисов на полные пути
sed -i 's/android:name=".VoiceService"/android:name=".services.VoiceService"/g' app/src/main/AndroidManifest.xml
sed -i 's/android:name=".FloatingBubbleService"/android:name=".services.FloatingBubbleService"/g' app/src/main/AndroidManifest.xml
sed -i 's/android:name=".SmartNotificationService"/android:name=".services.SmartNotificationService"/g' app/src/main/AndroidManifest.xml
sed -i 's/android:name=".JarvisAccessibilityService"/android:name=".services.JarvisAccessibilityService"/g' app/src/main/AndroidManifest.xml
sed -i 's/android:name=".AlarmService"/android:name=".services.AlarmService"/g' app/src/main/AndroidManifest.xml
sed -i 's/android:name=".GeoService"/android:name=".services.GeoService"/g' app/src/main/AndroidManifest.xml

echo "Проверяем результат:"
grep -E "service android:name" app/src/main/AndroidManifest.xml

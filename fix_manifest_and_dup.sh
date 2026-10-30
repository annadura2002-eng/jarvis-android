#!/bin/bash
set -e

echo "=== 1. ИСПРАВЛЯЕМ МАНИФЕСТ (жёсткие пути) ==="
# Удаляем все старые записи о JarvisAccessibilityService
sed -i '/JarvisAccessibilityService/,/<\/service>/d' app/src/main/AndroidManifest.xml

# Вставляем одну правильную запись перед закрытием application
python3 - << 'PYEOF'
filepath = 'app/src/main/AndroidManifest.xml'
with open(filepath, 'r') as f:
    content = f.read()

# Убеждаемся, что путь правильный (com.jarvis.app.services.JarvisAccessibilityService)
service_block = '''
        <service
            android:name="com.jarvis.app.services.JarvisAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
'''
if 'com.jarvis.app.services.JarvisAccessibilityService' not in content:
    content = content.replace('</application>', service_block + '</application>')
    with open(filepath, 'w') as f:
        f.write(content)
    print("✅ Служба Accessibility прописана с полным путём.")
else:
    print("✅ Служба уже есть.")
PYEOF

echo "=== 2. УДАЛЯЕМ ДУБЛИКАТЫ УВЕДОМЛЕНИЙ ==="
# Ищем старые файлы служб уведомлений и удаляем их
find app/src/main/java -name "NotificationService.kt" -delete
find app/src/main/java -name "SmartNotificationService.kt" -not -path "*/services/*" -delete
# В манифесте оставляем только одну запись .services.SmartNotificationService
sed -i '/\.NotificationService/d' app/src/main/AndroidManifest.xml
sed -i '/SmartNotificationService/d' app/src/main/AndroidManifest.xml

# Вставляем правильную запись
python3 - << 'PYEOF'
filepath = 'app/src/main/AndroidManifest.xml'
with open(filepath, 'r') as f:
    content = f.read()

notif_block = '''
        <service
            android:name="com.jarvis.app.services.SmartNotificationService"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
'''
if 'com.jarvis.app.services.SmartNotificationService' not in content:
    content = content.replace('</application>', notif_block + '</application>')
    with open(filepath, 'w') as f:
        f.write(content)
    print("✅ Служба уведомлений прописана с полным путём.")
else:
    print("✅ Служба уведомлений уже есть.")
PYEOF

echo "=== 3. ПРОВЕРЯЕМ ВИТАЛЬНЫЕ НАСТРОЙКИ В XML ==="
cat << 'XML' > app/src/main/res/xml/accessibility_service_config.xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_desc"
    android:notificationTimeout="100" />
XML

echo "=== 4. ДОБАВЛЯЕМ СТРОКУ ДЛЯ ОПИСАНИЯ ==="
if ! grep -q "accessibility_desc" app/src/main/res/values/strings.xml; then
    sed -i 's|</resources>|    <string name="accessibility_desc">Jarvis использует эту службу для чтения экрана и нажатий.</string>\n</resources>|' app/src/main/res/values/strings.xml
fi

echo "✅ Готово! Теперь система должна видеть одну службу уведомлений и одну службу доступности."

#!/bin/bash
set -e

echo "=== ПЕРЕЗАПИСЫВАЕМ dialog_commands_list.xml ==="
mkdir -p app/src/main/res/layout
cat << 'INNER' > app/src/main/res/layout/dialog_commands_list.xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="#1A1F2B"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="📋 Все команды"
            android:textColor="#2DD4BF"
            android:textSize="20sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <TextView
            android:id="@+id/tvCommandsList"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#F8FAFC"
            android:textSize="14sp"
            android:lineSpacingExtra="4dp" />

    </LinearLayout>
</ScrollView>
INNER

echo "✅ Файл dialog_commands_list.xml перезаписан (с ID tvCommandsList)."

echo "=== ПРОВЕРЯЕМ НАЛИЧИЕ ID В ФАЙЛЕ ==="
grep -n "tvCommandsList" app/src/main/res/layout/dialog_commands_list.xml || echo "❌ ID всё ещё нет!"

echo "=== ПРОВЕРЯЕМ КОД В MainActivity (есть ли findViewById) ==="
grep -n "tvCommandsList" app/src/main/java/com/jarvis/app/MainActivity.kt || echo "❌ В коде нет ссылки на tvCommandsList"

echo "✅ Готово!"

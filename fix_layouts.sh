#!/bin/bash
set -e

echo "=== 1. ПЕРЕЗАПИСЫВАЕМ activity_permissions.xml ==="
cat << 'INNER' > app/src/main/res/layout/activity_permissions.xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0B0B0E"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp"
        android:gravity="center_horizontal">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Настройка Jarvis"
            android:textColor="#2DD4BF"
            android:textSize="28sp"
            android:textStyle="bold"
            android:layout_marginTop="32dp"
            android:layout_marginBottom="8dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Для работы ассистента нужны разрешения"
            android:textColor="#94A3B8"
            android:textSize="14sp"
            android:layout_marginBottom="32dp"/>

        <!-- Микрофон -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="12dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="🎤 Микрофон"
                android:textColor="#F8FAFC"
                android:textSize="16sp"/>
            <Button
                android:id="@+id/btnMic"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Выдать"
                android:backgroundTint="#5B5BFF"
                android:textColor="#FFFFFF"/>
        </LinearLayout>

        <!-- Контакты -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="12dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="📇 Контакты"
                android:textColor="#F8FAFC"
                android:textSize="16sp"/>
            <Button
                android:id="@+id/btnContacts"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Выдать"
                android:backgroundTint="#5B5BFF"
                android:textColor="#FFFFFF"/>
        </LinearLayout>

        <!-- Уведомления -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="12dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="🔔 Уведомления (доступ)"
                android:textColor="#F8FAFC"
                android:textSize="16sp"/>
            <Button
                android:id="@+id/btnNotifs"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Выдать"
                android:backgroundTint="#5B5BFF"
                android:textColor="#FFFFFF"/>
        </LinearLayout>

        <!-- Доступ к экрану -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="32dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="🖐 Доступ к экрану (Accessibility)"
                android:textColor="#F8FAFC"
                android:textSize="16sp"/>
            <Button
                android:id="@+id/btnAccess"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Выдать"
                android:backgroundTint="#5B5BFF"
                android:textColor="#FFFFFF"/>
        </LinearLayout>

        <!-- Продолжить -->
        <Button
            android:id="@+id/btnContinue"
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:text="Продолжить"
            android:textSize="18sp"
            android:textColor="#FFFFFF"
            android:backgroundTint="#2A2A35"
            android:enabled="false"
            android:layout_marginTop="16dp"/>

    </LinearLayout>
</ScrollView>
INNER

echo "=== 2. ПЕРЕЗАПИСЫВАЕМ dialog_menu.xml (с кнопкой 'Список команд') ==="
cat << 'INNER' > app/src/main/res/layout/dialog_menu.xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#1A1F2B"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Меню Jarvis"
        android:textColor="#2DD4BF"
        android:textSize="20sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp"/>

    <!-- Список команд -->
    <LinearLayout
        android:id="@+id/btnMenuCommands"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackground">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="📋 Список команд"
            android:textColor="#F8FAFC"
            android:textSize="16sp"/>
    </LinearLayout>

    <!-- Настройки -->
    <LinearLayout
        android:id="@+id/btnMenuSettings"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackground">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="⚙️ Настройки API"
            android:textColor="#F8FAFC"
            android:textSize="16sp"/>
    </LinearLayout>

    <!-- Голос -->
    <LinearLayout
        android:id="@+id/btnMenuVoice"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackground">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🗣 Голос"
            android:textColor="#F8FAFC"
            android:textSize="16sp"/>
    </LinearLayout>

    <!-- Пузырь -->
    <LinearLayout
        android:id="@+id/btnMenuBubble"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackground">
        <TextView
            android:id="@+id/tvBubbleStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🫧 Включить пузырь"
            android:textColor="#F8FAFC"
            android:textSize="16sp"/>
    </LinearLayout>

    <!-- Секреты -->
    <LinearLayout
        android:id="@+id/btnMenuSecrets"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackground">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🔐 Секретные команды"
            android:textColor="#F8FAFC"
            android:textSize="16sp"/>
    </LinearLayout>

    <!-- Тема -->
    <LinearLayout
        android:id="@+id/btnMenuTheme"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackground">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🌗 Сменить тему"
            android:textColor="#F8FAFC"
            android:textSize="16sp"/>
    </LinearLayout>

    <!-- Выход -->
    <LinearLayout
        android:id="@+id/btnMenuLogout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackground">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🚪 Выйти"
            android:textColor="#FF5252"
            android:textSize="16sp"/>
    </LinearLayout>

</LinearLayout>
INNER

echo "✅ Вёрстки перезаписаны: activity_permissions.xml и dialog_menu.xml"
echo "Кнопки btnNotifs, btnAccess, btnMenuCommands теперь существуют."

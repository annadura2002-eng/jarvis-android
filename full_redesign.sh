#!/bin/bash
set -e

echo "=== 1. ЦВЕТА ==="
cat << 'INNER' > app/src/main/res/values/colors.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="bg_deep">#0A0C10</color>
    <color name="bg_card">#141821</color>
    <color name="bg_card_glass">#CC141821</color>
    <color name="accent_teal">#2DD4BF</color>
    <color name="accent_purple">#A78BFA</color>
    <color name="accent_blue">#5B5BFF</color>
    <color name="text_primary">#F8FAFC</color>
    <color name="text_secondary">#94A3B8</color>
    <color name="danger">#FF5252</color>
    <color name="bg_light">#F3F4F6</color>
    <color name="bg_light_card">#FFFFFF</color>
    <color name="text_dark_primary">#0B0E14</color>
    <color name="text_dark_secondary">#4B5563</color>
</resources>
INNER

echo "=== 2. ФОНЫ ==="
cat << 'INNER' > app/src/main/res/drawable/bg_dark_gradient.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient android:angle="135" android:endColor="#0A0C10" android:startColor="#161B26" android:type="linear"/>
</shape>
INNER

cat << 'INNER' > app/src/main/res/drawable/bg_light_gradient.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient android:angle="135" android:endColor="#E5E7EB" android:startColor="#F9FAFB" android:type="linear"/>
</shape>
INNER

cat << 'INNER' > app/src/main/res/drawable/bg_glass_card.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#CC141821"/>
    <corners android:radius="24dp"/>
    <stroke android:width="1dp" android:color="#2DD4BF" android:alpha="0.3"/>
</shape>
INNER

cat << 'INNER' > app/src/main/res/drawable/bg_button_round.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <gradient android:angle="45" android:endColor="#2DD4BF" android:startColor="#A78BFA" android:type="linear"/>
</shape>
INNER

cat << 'INNER' > app/src/main/res/drawable/bg_user_bubble.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient android:angle="45" android:endColor="#2DD4BF" android:startColor="#5B5BFF" android:type="linear"/>
    <corners android:radius="24dp" android:topRightRadius="6dp"/>
</shape>
INNER

cat << 'INNER' > app/src/main/res/drawable/bg_bot_bubble.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#141821"/>
    <corners android:radius="24dp" android:topLeftRadius="6dp"/>
    <stroke android:width="1dp" android:color="#2A2A35"/>
</shape>
INNER

echo "=== 3. АНИМАЦИИ ==="
mkdir -p app/src/main/res/anim
cat << 'INNER' > app/src/main/res/anim/slide_up.xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <translate android:fromYDelta="10%" android:toYDelta="0" android:duration="250"/>
    <alpha android:fromAlpha="0" android:toAlpha="1" android:duration="250"/>
</set>
INNER

cat << 'INNER' > app/src/main/res/anim/pulse_scale.xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <scale android:duration="800" android:fromXScale="1.0" android:fromYScale="1.0"
        android:pivotX="50%" android:pivotY="50%" android:repeatCount="infinite"
        android:repeatMode="reverse" android:toXScale="1.2" android:toYScale="1.2"/>
</set>
INNER

cat << 'INNER' > app/src/main/res/anim/pulse_alpha.xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <alpha android:duration="800" android:fromAlpha="0.5" android:toAlpha="1.0"
        android:repeatCount="infinite" android:repeatMode="reverse"/>
</set>
INNER

echo "=== 4. СТИЛИ ==="
cat << 'INNER' > app/src/main/res/values/themes.xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Jarvis" parent="Theme.Material3.DayNight">
        <item name="colorPrimary">@color/accent_teal</item>
        <item name="colorSecondary">@color/accent_purple</item>
        <item name="android:windowBackground">@drawable/bg_dark_gradient</item>
        <item name="android:statusBarColor">@color/bg_deep</item>
        <item name="android:navigationBarColor">@color/bg_deep</item>
        <item name="android:fontFamily">@font/manrope_regular</item>
    </style>
</resources>
INNER

echo "=== 5. ГЛАВНЫЙ ЭКРАН ==="
cat << 'INNER' > app/src/main/res/layout/activity_main.xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@drawable/bg_dark_gradient">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="20dp"
        android:gravity="center_vertical">

        <TextView
            android:id="@+id/toolbarTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Jarvis"
            android:textColor="#2DD4BF"
            android:textSize="28sp"
            android:textStyle="bold"
            android:fontFamily="@font/manrope_bold"/>

        <ImageButton
            android:id="@+id/btnMenu"
            android:layout_width="52dp"
            android:layout_height="52dp"
            android:src="@android:drawable/ic_menu_sort_by_size"
            android:background="@drawable/bg_button_round"
            android:contentDescription="Меню"
            android:padding="14dp"
            android:tint="#0A0C10"/>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/apiBar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:visibility="gone"
        android:background="@drawable/bg_glass_card"
        android:layout_marginHorizontal="16dp"
        android:layout_marginBottom="12dp">

        <EditText
            android:id="@+id/apiKeyField"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="API-ключ ProxyAPI"
            android:textColor="#F8FAFC"
            android:textColorHint="#94A3B8"
            android:inputType="text"
            android:background="@null"/>

        <Button
            android:id="@+id/btnSaveKey"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Сохранить"
            android:backgroundTint="#2DD4BF"
            android:textColor="#0A0C10"/>
    </LinearLayout>

    <ScrollView
        android:id="@+id/scrollView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:paddingHorizontal="16dp">

        <LinearLayout
            android:id="@+id/logContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingVertical="16dp"/>
    </ScrollView>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center_vertical">

        <EditText
            android:id="@+id/inputField"
            android:layout_width="0dp"
            android:layout_height="56dp"
            android:layout_weight="1"
            android:hint="Спросите Джарвиса..."
            android:textColor="#F8FAFC"
            android:textColorHint="#94A3B8"
            android:background="@drawable/bg_glass_card"
            android:paddingHorizontal="20dp"
            android:textSize="16sp"
            android:fontFamily="@font/manrope_regular"/>

        <ImageButton
            android:id="@+id/voiceButton"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_marginStart="8dp"
            android:src="@android:drawable/ic_btn_speak_now"
            android:background="@drawable/bg_button_round"
            android:padding="14dp"
            android:tint="#0A0C10"/>

        <ImageButton
            android:id="@+id/sendButton"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_marginStart="8dp"
            android:src="@android:drawable/ic_menu_send"
            android:background="@drawable/bg_button_round"
            android:padding="14dp"
            android:tint="#0A0C10"/>
    </LinearLayout>
</LinearLayout>
INNER

echo "=== 6. ДИАЛОГ ГОЛОСА ==="
cat << 'INNER' > app/src/main/res/layout/dialog_voice.xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="32dp"
    android:gravity="center"
    android:background="@drawable/bg_dark_gradient">

    <TextView
        android:id="@+id/voiceStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Слушаю..."
        android:textColor="#2DD4BF"
        android:textSize="24sp"
        android:textStyle="bold"
        android:fontFamily="@font/manrope_bold"
        android:layout_marginBottom="32dp"/>

    <com.jarvis.app.EqualizerView
        android:id="@+id/equalizerView"
        android:layout_width="200dp"
        android:layout_height="100dp"/>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Скажите команду"
        android:textColor="#94A3B8"
        android:textSize="14sp"
        android:layout_marginTop="24dp"/>
</LinearLayout>
INNER

echo "=== 7. ДИАЛОГ ПУЗЫРЯ ==="
cat << 'INNER' > app/src/main/res/layout/dialog_bubble_chat.xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="@drawable/bg_glass_card"
    android:layout_margin="16dp">

    <TextView
        android:id="@+id/tvBubbleAnswer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#F8FAFC"
        android:textSize="16sp"
        android:padding="16dp"
        android:background="@drawable/bg_bot_bubble"
        android:visibility="gone"
        android:layout_marginBottom="12dp"
        android:fontFamily="@font/manrope_regular"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <EditText
            android:id="@+id/etBubbleInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Спросите..."
            android:textColor="#F8FAFC"
            android:textColorHint="#94A3B8"
            android:background="@drawable/bg_glass_card"
            android:padding="16dp"
            android:textSize="14sp"/>

        <Button
            android:id="@+id/btnBubbleSend"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginStart="8dp"
            android:text="➤"
            android:background="@drawable/bg_button_round"
            android:textColor="#0A0C10"/>
    </LinearLayout>
</LinearLayout>
INNER

echo "=== 8. ЭКРАН РАЗРЕШЕНИЙ ==="
cat << 'INNER' > app/src/main/res/layout/activity_permissions.xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/bg_dark_gradient"
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
            android:textSize="32sp"
            android:textStyle="bold"
            android:fontFamily="@font/manrope_bold"
            android:layout_marginTop="48dp"
            android:layout_marginBottom="8dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Для работы ассистента нужны разрешения"
            android:textColor="#94A3B8"
            android:textSize="14sp"
            android:layout_marginBottom="48dp"/>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:padding="16dp"
            android:background="@drawable/bg_glass_card"
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
                android:backgroundTint="#2DD4BF"
                android:textColor="#0A0C10"/>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:padding="16dp"
            android:background="@drawable/bg_glass_card"
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
                android:backgroundTint="#2DD4BF"
                android:textColor="#0A0C10"/>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:padding="16dp"
            android:background="@drawable/bg_glass_card"
            android:layout_marginBottom="12dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="🔔 Уведомления"
                android:textColor="#F8FAFC"
                android:textSize="16sp"/>
            <Button
                android:id="@+id/btnNotifs"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Выдать"
                android:backgroundTint="#2DD4BF"
                android:textColor="#0A0C10"/>
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:padding="16dp"
            android:background="@drawable/bg_glass_card"
            android:layout_marginBottom="32dp">
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="🖐 Доступ к экрану"
                android:textColor="#F8FAFC"
                android:textSize="16sp"/>
            <Button
                android:id="@+id/btnAccess"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Выдать"
                android:backgroundTint="#2DD4BF"
                android:textColor="#0A0C10"/>
        </LinearLayout>

        <Button
            android:id="@+id/btnContinue"
            android:layout_width="match_parent"
            android:layout_height="60dp"
            android:text="Продолжить"
            android:textSize="18sp"
            android:textColor="#0A0C10"
            android:background="@drawable/bg_button_round"
            android:enabled="false"/>
    </LinearLayout>
</ScrollView>
INNER

echo "=== 9. ОБНОВЛЯЕМ MainActivity (цвета и ID) ==="
python3 - << 'PYEOF'
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Заменяем цвета в строках
content = content.replace('Color.parseColor("#0B0E14")', 'Color.parseColor("#0A0C10")')
content = content.replace('Color.parseColor("#1A1F2B")', 'Color.parseColor("#141821")')
content = content.replace('Color.parseColor("#2A2A35")', 'Color.parseColor("#141821")')

# Обновляем addBubble (правильные ссылки на фоны)
old_add = content[content.find('private fun addBubble'):]
if 'R.drawable.bg_user_bubble' not in content:
    content = content.replace(
        '''        if (isUser) {
            tv.setBackgroundColor(Color.parseColor("#2A2A35"))
        } else {
            val drawable = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#2A2A35"), Color.parseColor("#1E1E28"))
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadius = 24f
            }
            tv.background = drawable
        }''',
        '''        if (isUser) {
            tv.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_user_bubble)
        } else {
            tv.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_bot_bubble)
        }'''
    )

with open(filepath, 'w') as f:
    f.write(content)
print("✅ MainActivity обновлён под новый дизайн.")
PYEOF

echo "✅ ВСЁ ГОТОВО! Дизайн полностью переделан."

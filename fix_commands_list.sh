#!/bin/bash
set -e

echo "=== 1. ПРОВЕРЯЕМ НАЛИЧИЕ КНОПКИ В МЕНЮ (dialog_menu.xml) ==="
if grep -q "btnMenuCommands" app/src/main/res/layout/dialog_menu.xml; then
    echo "✅ Кнопка btnMenuCommands есть в меню."
else
    echo "❌ Кнопки btnMenuCommands НЕТ в меню! Добавляем..."
    # Вставляем кнопку перед кнопкой секретов
    sed -i 's|<LinearLayout android:id="@+id/btnMenuSecrets"|<LinearLayout\n        android:id="@+id/btnMenuCommands"\n        android:layout_width="match_parent"\n        android:layout_height="wrap_content"\n        android:orientation="horizontal"\n        android:padding="16dp"\n        android:gravity="center_vertical">\n        <TextView\n            android:layout_width="wrap_content"\n            android:layout_height="wrap_content"\n            android:text="📋 Список команд"\n            android:textColor="#F8FAFC"\n            android:textSize="16sp" />\n    </LinearLayout>\n\n    <LinearLayout android:id="@+id/btnMenuSecrets"|' app/src/main/res/layout/dialog_menu.xml
fi

echo "=== 2. ПРОВЕРЯЕМ НАЛИЧИЕ МЕТОДА showCommandsList В MainActivity.kt ==="
if grep -q "fun showCommandsList()" app/src/main/java/com/jarvis/app/MainActivity.kt; then
    echo "✅ Метод showCommandsList есть."
else
    echo "❌ Метода нет! Добавляем..."
    # Вставляем метод перед onDestroy
    python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

method = '''
    private fun showCommandsList() {
        val dialog = Dialog(this, R.style.Theme_Jarvis)
        val view = layoutInflater.inflate(R.layout.dialog_commands_list, null)
        dialog.setContentView(view)
        dialog.show()
    }
'''
if 'fun showCommandsList()' not in content:
    content = content.replace('override fun onDestroy()', method + '\n    override fun onDestroy()')
    with open(filepath, 'w') as f:
        f.write(content)
    print("Метод showCommandsList добавлен.")
else:
    print("Метод showCommandsList уже есть.")
PYEOF
fi

echo "=== 3. ПРОВЕРЯЕМ НАЛИЧИЕ МАКЕТА dialog_commands_list.xml ==="
if [ -f "app/src/main/res/layout/dialog_commands_list.xml" ]; then
    echo "✅ Макет dialog_commands_list.xml есть."
else
    echo "❌ Макета нет! Создаём..."
    cat << 'XML' > app/src/main/res/layout/dialog_commands_list.xml
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
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="— Голосовые команды —\n\n"
            android:textColor="#94A3B8"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/tvCommandsList"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="#F8FAFC"
            android:textSize="14sp"
            android:lineSpacingExtra="4dp" />

    </LinearLayout>
</ScrollView>
XML
    echo "✅ Макет создан."
fi

echo "=== 4. ЗАПОЛНЯЕМ СПИСОК КОМАНД (если он пустой) ==="
python3 - << 'PYEOF'
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Проверяем, заполняется ли tvCommandsList
if 'tvCommandsList' not in content:
    # Добавляем заполнение в showCommandsList
    old_method = '''    private fun showCommandsList() {
        val dialog = Dialog(this, R.style.Theme_Jarvis)
        val view = layoutInflater.inflate(R.layout.dialog_commands_list, null)
        dialog.setContentView(view)
        dialog.show()
    }'''
    new_method = '''    private fun showCommandsList() {
        val dialog = Dialog(this, R.style.Theme_Jarvis)
        val view = layoutInflater.inflate(R.layout.dialog_commands_list, null)
        dialog.setContentView(view)
        
        val tvCommandsList = view.findViewById<TextView>(R.id.tvCommandsList)
        val commands = """
            • Открой [приложение] — открыть программу
            • Позвони [имя/номер] — позвонить
            • Напиши [имя] [текст] — отправить СМС
            • Будильник на 7:30 — поставить будильник
            • Будильник на 7:30 каждый день — ежедневный
            • Список будильников — показать все
            • Выключи будильник — убрать все
            • Таймер на 5 минут — поставить таймер
            • Громкость 50 — изменить громкость
            • Включи звук / Выключи звук
            • Пауза / Играй / Следующий трек
            • Погода [город] — узнать погоду
            • Маршрут до [адрес] — построить маршрут
            • Найди в ютубе [запрос]
            • Найди в тик токе [запрос]
            • Загугли [запрос]
            • Смени имя на [имя] — изменить имя ассистента
            • Переведи с [язык] на [язык] — режим переводчика
            • Нажми [кнопка] — нажать на кнопку на экране
            • Что на экране — прочитать текст с экрана
            • Запиши звонок — включить запись звонков
            • Отправь в телеграм [контакт] [текст]
            • Секрет [слово] — выполнить секретную команду
        """.trimIndent()
        tvCommandsList?.text = commands
        
        dialog.show()
    }'''
    content = content.replace(old_method, new_method)
    with open(filepath, 'w') as f:
        f.write(content)
    print("Список команд добавлен.")
else:
    print("Список команд уже заполняется.")
PYEOF

echo "✅ Готово! Теперь кнопка 'Список команд' будет работать."

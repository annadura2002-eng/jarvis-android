#!/bin/bash
set -e

echo "=== 1. ИСПРАВЛЯЕМ URL И МОДЕЛЬ (ChatManager, TranslationManager, MainActivity) ==="
# Возвращаем URL openai/v1, но оставляем модель deepseek-chat
sed -i 's|https://api.proxyapi.ru/deepseek/chat/completions|https://api.proxyapi.ru/openai/v1/chat/completions|g' app/src/main/java/com/jarvis/app/core/ChatManager.kt
sed -i 's|https://api.proxyapi.ru/deepseek/chat/completions|https://api.proxyapi.ru/openai/v1/chat/completions|g' app/src/main/java/com/jarvis/app/core/TranslationManager.kt
sed -i 's|https://api.proxyapi.ru/deepseek/chat/completions|https://api.proxyapi.ru/openai/v1/chat/completions|g' app/src/main/java/com/jarvis/app/MainActivity.kt

# Меняем модель на deepseek/deepseek-chat (как ты сказал)
sed -i 's/"deepseek-chat"/"deepseek\/deepseek-chat"/g' app/src/main/java/com/jarvis/app/core/ChatManager.kt
sed -i 's/"deepseek-chat"/"deepseek\/deepseek-chat"/g' app/src/main/java/com/jarvis/app/core/TranslationManager.kt
sed -i 's/"deepseek-chat"/"deepseek\/deepseek-chat"/g' app/src/main/java/com/jarvis/app/MainActivity.kt

echo "=== 2. ИСПРАВЛЯЕМ ПУЗЫРЬКИ (addBubble и animateBotReply) ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Правильный addBubble с GradientDrawable
new_add = '''    private fun addBubble(text: String, isUser: Boolean) {
        val tv = TextView(this)
        try { tv.typeface = ResourcesCompat.getFont(this, R.font.manrope_regular) } catch(e: Exception){}
        tv.setTextColor(if (isUser) Color.parseColor("#0A0C10") else Color.parseColor("#F8FAFC"))
        tv.text = text
        tv.textSize = 15f
        tv.setPadding(32, 24, 32, 24)
        
        val drawable = GradientDrawable()
        if (isUser) {
            drawable.colors = intArrayOf(Color.parseColor("#2DD4BF"), Color.parseColor("#5B5BFF"))
            drawable.gradientType = GradientDrawable.LINEAR_GRADIENT
            drawable.orientation = GradientDrawable.Orientation.TL_BR
            drawable.cornerRadii = floatArrayOf(24f, 24f, 24f, 24f, 6f, 6f, 24f, 24f) // Right-top small
        } else {
            drawable.setColor(Color.parseColor("#141821"))
            drawable.cornerRadii = floatArrayOf(24f, 24f, 6f, 6f, 24f, 24f, 24f, 24f) // Left-top small
            drawable.setStroke(2, Color.parseColor("#2A2A35"))
        }
        tv.background = drawable
        
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(if (isUser) 64 else 0, 8, if (isUser) 0 else 64, 8)
        tv.layoutParams = params
        logContainer.addView(tv)
        
        tv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }'''

# Правильный animateBotReply
new_anim = '''    private fun animateBotReply(fullText: String) {
        val tv = TextView(this)
        try { tv.typeface = ResourcesCompat.getFont(this, R.font.manrope_regular) } catch(e: Exception){}
        tv.textSize = 15f; tv.setPadding(32, 24, 32, 24); tv.setTextColor(Color.parseColor("#F8FAFC"))
        
        val drawable = GradientDrawable()
        drawable.setColor(Color.parseColor("#141821"))
        drawable.cornerRadii = floatArrayOf(24f, 24f, 6f, 6f, 24f, 24f, 24f, 24f)
        drawable.setStroke(2, Color.parseColor("#2A2A35"))
        tv.background = drawable

        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 8, 64, 8); tv.layoutParams = params
        logContainer.addView(tv); scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        neuralTTS.speak(fullText, selectedVoice) {
            var index = 0
            val runnable = object : Runnable {
                override fun run() {
                    if (index <= fullText.length) {
                        tv.text = fullText.substring(0, index); index++
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        uiHandler.postDelayed(this, 50)
                    }
                }
            }
            uiHandler.post(runnable)
        }
    }'''

# Заменяем функции
content = re.sub(r'private fun addBubble\(text: String, isUser: Boolean\) \{.*?\n    \}', new_add.strip(), content, flags=re.DOTALL)
content = re.sub(r'private fun animateBotReply\(fullText: String\) \{.*?\n    \}', new_anim.strip(), content, flags=re.DOTALL)

with open(filepath, 'w') as f:
    f.write(content)
print("✅ addBubble и animateBotReply переписаны (теперь градиенты в коде).")
PYEOF

echo "✅ ГОТОВО! Модель deepseek/deepseek-chat, URL openai/v1, пузырьки исправлены."

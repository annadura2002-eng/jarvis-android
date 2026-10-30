package com.jarvis.app.core

import com.jarvis.app.MainActivity

object CommandProcessor {
    fun process(context: MainActivity, raw: String): Boolean {
        val cmd = raw.lowercase().trim()

        if (cmd.startsWith("будильник на") || cmd.startsWith("поставь будильник на")) {
            val timePart = cmd.replace("поставь ", "").replace("будильник на ", "").trim()
            val isDaily = cmd.contains("каждый день") || cmd.contains("ежедневно")
            val regex = Regex("(\\d{1,2})[:. ](\\d{2})")
            val match = regex.find(timePart)
            if (match != null) {
                val h = match.groupValues[1].toIntOrNull() ?: 7
                val m = match.groupValues[2].toIntOrNull() ?: 0
                context.createAlarmFromCommand(h, m, isDaily)
                return true
            }
        }

        if (cmd.contains("список будильников") || cmd.contains("какие будильники")) {
            val alarms = PrefsManager.getAlarms()
            if (alarms.isEmpty()) {
                context.sendReplyFromCommand("У вас нет установленных будильников.")
            } else {
                val text = alarms.joinToString("\n") {
                    val time = String.format("%02d:%02d", it.hour, it.minute)
                    if (it.daily) "🕒 $time (каждый день)" else "🕒 $time (один раз)"
                }
                context.sendReplyFromCommand("Ваши будильники:\n$text")
            }
            return true
        }

        if (cmd.contains("включи автоответ")) {
            PrefsManager.autoReplyEnabled = true
            context.sendReplyFromCommand("Автоответ включен. Текст: ${PrefsManager.autoReplyText}")
            return true
        }
        if (cmd.contains("выключи автоответ")) {
            PrefsManager.autoReplyEnabled = false
            context.sendReplyFromCommand("Автоответ выключен.")
            return true
        }

        if (cmd.contains("переведи с ") && cmd.contains(" на ")) {
            val parts = cmd.split(" на ")
            if (parts.size == 2) {
                val fromLangRaw = parts[0].replace("переведи с ", "").trim()
                val toLangRaw = parts[1].trim()
                context.startTranslationMode(fromLangRaw, toLangRaw)
                return true
            }
        }

        return false
    }
}

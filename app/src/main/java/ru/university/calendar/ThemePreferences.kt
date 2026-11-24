// Адрес (пакет) моего приложения
package ru.university.calendar

// Context нужен, чтобы открыть локальный файл настроек приложения (SharedPreferences)
import android.content.Context
// AppCompatDelegate — системный инструмент AndroidX, который умеет переключать светлую и тёмную тему
// сразу для всего приложения на лету
import androidx.appcompat.app.AppCompatDelegate

// Помощник для сохранения и применения выбранной темы оформления (системная / светлая / тёмная).
//
// Частый вопрос на защите: «Почему настройки темы хранятся в SharedPreferences, а не в базе Room?»
// Ответ: Room — это полноценная SQL-база для больших списков структурированных данных (таблиц задач с поиском и сортировкой).
// А для пары простых настроек вроде «какая тема выбрана» поднимать целую таблицу SQLite избыточно —
// SharedPreferences хранит простые пары «ключ -> значение» в маленьком XML-файле, работает быстро и в две строчки кода.
// Сделан через `object` (одиночный объект), чтобы просто вызывать его методы из MainActivity.
object ThemePreferences {

    // Имя файла настроек внутри приватной папки приложения и имя ключа, под которым храним число-режим
    private const val PREFS_NAME = "task_calendar_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    // Понятные константы для трёх режимов темы — их номера (0, 1, 2) совпадают с пунктами диалога в MainActivity:
    // 0 — как в системе, 1 — всегда светлая, 2 — всегда тёмная
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    // Читает из SharedPreferences, какой режим темы пользователь выбрал в прошлый раз.
    // `Context.MODE_PRIVATE` означает, что этот файл настроек доступен только нашему приложению (другие не прочитают).
    // Если приложение запустили впервые и ключа ещё нет — вернётся значение по умолчанию `MODE_SYSTEM` (0)
    fun getSavedThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    // Сохраняет новый выбор пользователя в SharedPreferences и сразу же применяет тему к экранам.
    // Важная деталь: в конце вызываем именно `.apply()`, а не `.commit()` —
    // `apply()` сохраняет файл на диск асинхронно в фоне, не задерживая главный поток интерфейса
    fun saveAndApplyTheme(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        applyTheme(mode)
    }

    // Вызывается в самом начале `MainActivity.onCreate` при каждом запуске приложения:
    // достаёт сохранённый режим и применяет его, чтобы тема не сбрасывалась после перезапуска
    fun applySavedTheme(context: Context) {
        applyTheme(getSavedThemeMode(context))
    }

    // Внутренний метод: переводит наши числа (0, 1, 2) в системные константы AndroidX
    // и переключает глобальный режим DayNight.
    // После этого Android сам автоматически подставляет цвета либо из `res/values/colors.xml` (светлая тема),
    // либо из `res/values-night/colors.xml` (тёмная тема) на всех экранах приложения
    private fun applyTheme(mode: Int) {
        val nightMode = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO            // Принудительно светлая тема
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES            // Принудительно тёмная тема
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM       // Подстраиваться под настройку телефона
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
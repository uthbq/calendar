// Адрес (пакет) моего приложения, чтобы все классы проекта видели друг друга
package ru.university.calendar

// Системный сервис будильников Android — умеет запускать код в точное время, даже если приложение закрыто
import android.app.AlarmManager

// Специальный «отложенный билет» (PendingIntent) — передаём его системе Android, чтобы она сама
// запустила наш TaskAlarmReceiver позже, когда наступит нужное время
import android.app.PendingIntent

// Context — доступ к системным службам Android (через него достаём AlarmManager и создаём Intent)
import android.content.Context

// Intent — «послание», в котором указываем, какой класс запустить и какие данные ему передать
import android.content.Intent

// Build нужен, чтобы проверять версию Android на телефоне пользователя (например, Android 12 и выше)
import android.os.Build

// Стандартный класс для перевода даты и времени в миллисекунды
import java.util.Calendar

// Помощник для постановки и отмены локальных напоминаний о задачах.
// Сделан через `object` (а не `class`) — в Kotlin это готовый одиночный объект на всё приложение:
// не нужно каждый раз писать `AlarmScheduler()`, можно сразу вызывать `AlarmScheduler.scheduleTaskReminder(...)`.
//
// Почему для напоминаний выбран именно AlarmManager, а не WorkManager:
// WorkManager подходит для фоновых задач, которым не важна точность до минуты (система может сдвинуть их ради экономии батареи).
// А будильник о задаче должен прийти точно в указанное время, даже если экран выключен — для этого и нужен AlarmManager.
object AlarmScheduler {

    // Константы (`const val`) с ключами для передачи данных через Intent в TaskAlarmReceiver.
    // Вынесены сюда, чтобы использовать одни и те же названия в обоих файлах и случайно не опечататься в строке
    const val EXTRA_TASK_ID = "ALARM_TASK_ID"
    const val EXTRA_TASK_TITLE = "ALARM_TASK_TITLE"
    const val EXTRA_TASK_DESC = "ALARM_TASK_DESC"

    // Главная функция: ставит напоминание на дату и время конкретной задачи.
    // Вызывается из TaskViewModel при создании/обновлении задачи и из BootReceiver после перезагрузки телефона
    fun scheduleTaskReminder(context: Context, task: Task) {
        // Сначала отсеиваем задачи, которым напоминание вообще не нужно:
        // - id == 0 (задача ещё не сохранена в базу и не получила реальный номер);
        // - пустая дата или пустое время (пользователь не указал, когда напоминать);
        // - задача уже отмечена выполненной (isDone == true).
        // Если сработало хоть одно условие — сразу выходим из функции через `return`
        if (task.id == 0 || task.date.isBlank() || task.time.isBlank() || task.isDone) return

        // Переводим строковые дату и время (например, "2026-11-25" и "14:30") в точное число миллисекунд.
        // Если формат оказался кривым, parseDateTimeMillis вернёт null, и через `?: return` мы сразу выйдем
        val triggerAtMillis = parseDateTimeMillis(task.date, task.time) ?: return

        // Сравниваем время напоминания с текущим временем на телефоне.
        // Если задача запланирована в прошлом (или прямо сейчас) — будильник не ставим, иначе уведомление выскочит мгновенно
        if (triggerAtMillis <= System.currentTimeMillis()) return

        // Просим у Android системный сервис будильников и приводим его к типу AlarmManager через `as`
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Готовим Intent — указываем, что когда будильник прозвенит, нужно запустить наш класс TaskAlarmReceiver.
        // Через `.apply { ... }` сразу кладём внутрь данные задачи, чтобы ресивер знал, что написать в уведомлении
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_TASK_DESC, task.description)
        }

        // Оборачиваем Intent в PendingIntent — это как подписанный пропуск для системы Android,
        // который даёт ей право запустить наш BroadcastReceiver позже, даже если приложение выгружено из памяти.
        // Параметры:
        // 1) `context` — контекст приложения;
        // 2) `task.id` — уникальный код запроса (requestCode). Очень важно передавать именно ID задачи:
        //    благодаря этому у каждой задачи свой отдельный будильник (они не перезатирают друг друга),
        //    и по этому же `task.id` мы сможем потом точно отменить напоминание;
        // 3) `intent` — что запускать;
        // 4) флаги, объединённые через `or`:
        //    - FLAG_UPDATE_CURRENT: если будильник с таким же task.id уже был, просто обнови в нём текст и данные;
        //    - FLAG_IMMUTABLE: запрещает сторонним приложениям менять наш Intent (обязательное требование с Android 12)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Начиная с Android 12 (API 31, это константа Build.VERSION_CODES.S) система может ограничивать
        // точные будильники, поэтому заранее проверяем, разрешено ли нам их ставить.
        // На более старых версиях Android это разрешено всегда (true)
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            // Страхуемся через try/catch на случай, если система всё-таки заблокирует точный будильник
            try {
                // Ставим ТОЧНЫЙ будильник, который сработает даже в спящем режиме телефона (режим Doze).
                // AlarmManager.RTC_WAKEUP — означает: ориентируйся на обычные часы телефона (RTC)
                // и разбуди устройство (WAKEUP), если экран погашен
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (_: SecurityException) {
                // Если система выдала SecurityException (знак `_` — сам объект ошибки нам не нужен),
                // не падаем, а ставим обычный (чуть менее точный) будильник как запасной вариант
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            // Если разрешения на точные будильники нет — тоже используем запасной вариант без краша приложения
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    // Функция отмены запланированного напоминания.
    // Вызывается из TaskViewModel, когда задачу удалили, отметили выполненной или поменяли ей время
    fun cancelTaskReminder(context: Context, taskId: Int) {
        // Если ID равен 0 — отменять нечего
        if (taskId == 0) return

        // Достаём системный AlarmManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Чтобы Android понял, какой именно будильник нужно отменить, собираем точно такой же Intent
        // (на тот же TaskAlarmReceiver) и точно такой же PendingIntent с тем же самым `taskId`
        val intent = Intent(context, TaskAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Снимаем будильник с расписания в системе и аннулируем сам PendingIntent
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    // Вспомогательная функция: превращает дату ("2026-11-25") и время ("14:30") в число миллисекунд с 1970 года
    // (именно в таком формате AlarmManager принимает время запуска).
    // Возвращает `Long?` (может вернуть null, если строка даты или времени окажется битой)
    private fun parseDateTimeMillis(dateStr: String, timeStr: String): Long? {
        // Режем дату по дефису и время по двоеточию
        val dateParts = dateStr.split("-")
        val timeParts = timeStr.split(":")
        // Если получилось не 3 части даты или не 2 части времени — сразу возвращаем null
        if (dateParts.size != 3 || timeParts.size != 2) return null

        // Безопасно переводим каждый кусок в Int; если хоть один не число — выходим и возвращаем null
        val year = dateParts[0].toIntOrNull() ?: return null
        val month = dateParts[1].toIntOrNull() ?: return null
        val day = dateParts[2].toIntOrNull() ?: return null
        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null

        // Загружаем эти числа в Calendar и забираем итоговое время в миллисекундах (`.timeInMillis`).
        // Помним про особенность Calendar:
        // - месяцы считаются с нуля, поэтому пишем `month - 1`;
        // - `HOUR_OF_DAY` — это 24-часовой формат (0..23);
        // - секунды и миллисекунды обнуляем, чтобы будильник сработал ровно в начале минуты (например, 14:30:00)
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
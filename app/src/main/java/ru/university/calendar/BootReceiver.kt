// Адрес (пакет) моего приложения
package ru.university.calendar

// BroadcastReceiver — компонент Android, который умеет «подслушивать» системные события,
// даже когда само приложение закрыто (у нас он слушает завершение перезагрузки телефона)
import android.content.BroadcastReceiver

// Context и Intent приходят от системы в момент события
import android.content.Context
import android.content.Intent

// Инструменты для запуска кода в фоновом потоке (корутины Kotlin)
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Специальный приёмник, который восстанавливает все запланированные напоминания после перезагрузки телефона.
//
// Зачем он вообще нужен (важная особенность Android):
// Сами задачи никуда не пропадают, потому что они лежат в файле базы SQLite (Room).
// А вот все будильники в AlarmManager хранятся в оперативной памяти системы — как только телефон
// перезагружается, Android их полностью стирает. Поэтому этот класс ловит системное событие
// `BOOT_COMPLETED` (телефон включился), залезает в Room и заново ставит все актуальные напоминания.
// Чтобы Android про него знал, класс зарегистрирован в AndroidManifest.xml с разрешением RECEIVE_BOOT_COMPLETED.
class BootReceiver : BroadcastReceiver() {

    // Этот метод Android сам вызывает, когда происходит событие, на которое мы подписались в манифесте
    override fun onReceive(context: Context, intent: Intent) {
        // Обязательная проверка безопасности: убеждаемся, что нас дёрнули именно из-за завершения загрузки ОС,
        // а не по какому-то случайному чужому Intent
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Очень важный трюк Android для работы с базой внутри BroadcastReceiver!
        // Обычно система считает, что как только метод `onReceive` дошёл до конца, работу можно сворачивать
        // и процесс приложения можно убить. Но чтение из Room мы обязаны делать в фоновом потоке (корутине),
        // которая может не успеть закончиться до выхода из `onReceive`.
        // Вызов `goAsync()` как бы говорит Android: «подожди пару секунд, не убивай меня, я делаю фоновую работу
        // и сам скажу, когда закончу (через pendingResult.finish())»
        val pendingResult = goAsync()

        // Запускаем корутину в фоновом потоке `Dispatchers.IO` (поток специально для работы с диском и базой данных),
        // потому что Room запрещает обращаться к базе в главном потоке
        CoroutineScope(Dispatchers.IO).launch {
            // Используем блок `try ... finally`, чтобы `pendingResult.finish()` гарантированно вызвался в любом случае
            try {
                // Получаем доступ к нашей таблице задач через единственный экземпляр AppDatabase
                val dao = AppDatabase.getDatabase(context).taskDao()

                // Загружаем из базы только те задачи, которые ещё не выполнены и у которых задано время напоминания
                val tasks = dao.getPendingTasksWithTime()

                // Пробегаемся по каждой такой задаче и заново ставим для неё будильник в AlarmManager
                // (просроченные задачи AlarmScheduler отсеет сам внутри)
                tasks.forEach { task ->
                    AlarmScheduler.scheduleTaskReminder(context, task)
                }
            } finally {
                // Сообщаем системе Android, что вся фоновая работа закончена и ресурс можно освободить.
                // Если забыть вызвать finish(), система сочтёт приложение зависшим
                pendingResult.finish()
            }
        }
    }
}
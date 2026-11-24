// Адрес (пакет) моего приложения
package ru.university.calendar

// BroadcastReceiver — компонент Android, который умеет принимать сигналы от системы,
// даже если само приложение сейчас свёрнуто или вообще закрыто
import android.content.BroadcastReceiver
// Context и Intent приходят в метод onReceive от системы Android в момент срабатывания будильника
import android.content.Context
import android.content.Intent

// Приёмник, который срабатывает ровно в тот момент, когда подходит время будильника из AlarmManager.
// Чтобы Android знал про этот класс и мог его запустить, он обязательно прописан в AndroidManifest.xml
// внутри тега <receiver android:name=".TaskAlarmReceiver" />.
//
// Цепочка работы уведомления целиком:
// TaskViewModel -> AlarmScheduler (ставит будильник и кладёт данные задачи в Intent)
// -> [наступает нужное время] -> Android будит TaskAlarmReceiver
// -> NotificationHelper (рисует и показывает уведомление в шторке телефона).
class TaskAlarmReceiver : BroadcastReceiver() {

    // Метод, который система Android сама вызывает в момент срабатывания запланированного будильника
    override fun onReceive(context: Context, intent: Intent) {
        // Вытаскиваем из прилетевшего Intent все данные задачи, которые мы положили туда заранее в AlarmScheduler.
        // Ключи берём из констант AlarmScheduler.EXTRA_..., чтобы нигде не опечататься.
        // Если ID вдруг не нашёлся, берём 0 по умолчанию
        val taskId = intent.getIntExtra(AlarmScheduler.EXTRA_TASK_ID, 0)

        // Достаём название задачи; если вдруг прилетел null, через `?:` подставляем запасной заголовок
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Напоминание о задаче"

        // Достаём описание задачи; `.orEmpty()` безопасно превращает возможный null в пустую строку ""
        val description = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_DESC).orEmpty()

        // Передаём всё в наш помощник NotificationHelper — он проверит разрешение, убедится что канал создан
        // и сразу покажет уведомление с названием и описанием задачи в шторке телефона
        NotificationHelper.showTaskNotification(context, taskId, title, description)
    }
}
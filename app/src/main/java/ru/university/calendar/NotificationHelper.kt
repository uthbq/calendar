// Адрес (пакет) моего приложения
package ru.university.calendar

// Manifest — отсюда берём имя разрешения POST_NOTIFICATIONS для Android 13+
import android.Manifest
// NotificationChannel — канал уведомлений (обязательное требование начиная с Android 8.0)
import android.app.NotificationChannel
// Системная служба управления уведомлениями
import android.app.NotificationManager
// PendingIntent — «отложенное действие» при нажатии пальцем на само уведомление в шторке
import android.app.PendingIntent
// Context и Intent нужны для доступа к системным сервисам и открытия MainActivity по клику на уведомление
import android.content.Context
import android.content.Intent
// Для проверки, выдано ли разрешение на уведомления
import android.content.pm.PackageManager
// Для проверки версии Android на телефоне
import android.os.Build
// Классы-помощники из AndroidX (с приставкой Compat), которые сами аккуратно подстраиваются под разные версии Android
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// Помощник для создания канала уведомлений и показа самих локальных уведомлений в шторке телефона.
// Сделан как `object` (единственный объект на всё приложение), чтобы просто вызывать
// `NotificationHelper.createNotificationChannel(context)` и `NotificationHelper.showTaskNotification(...)`
// из MainActivity и TaskAlarmReceiver без создания экземпляров класса.
object NotificationHelper {

    // Уникальный строковый ID нашего канала уведомлений (`const val` — постоянное значение).
    // Каждое уведомление должно ссылаться ровно на тот же CHANNEL_ID, с которым был создан канал
    const val CHANNEL_ID = "task_reminders_channel"

    // Создаёт канал уведомлений в системе Android.
    // Начиная с Android 8.0 (API 26, константа Build.VERSION_CODES.O), система вообще не покажет
    // ни одного уведомления, если оно не привязано к созданному каналу. Пользователь потом может
    // в настройках телефона отдельно включать/выключать звук именно для этого канала.
    // Вызывать этот метод повторно абсолютно безопасно: если канал уже существует, Android просто ничего не сделает
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Собираем канал:
            // 1) CHANNEL_ID — внутренний идентификатор;
            // 2) "Напоминания о задачах" — название, которое человек увидит в настройках уведомлений телефона;
            // 3) IMPORTANCE_HIGH — высокая важность, чтобы напоминание вылетало сверху экрана со звуком,
            //    а не пряталось тихо в шторке
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания о задачах",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                // Пояснение для пользователя в настройках Android, зачем нужен этот канал
                description = "Уведомления о запланированных задачах из календаря"
            }

            // Получаем системный NotificationManager и регистрируем в нём наш канал
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // Собирает и сразу показывает уведомление о конкретной задаче в шторке телефона.
    // Вызывается из TaskAlarmReceiver в тот момент, когда сработал будильник AlarmManager
    fun showTaskNotification(
        context: Context,
        taskId: Int,
        title: String,
        description: String
    ) {
        // Обязательная проверка для Android 13+ (TIRAMISU / API 33):
        // если пользователь запретил уведомления, сразу выходим, чтобы не получить SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        // На всякий случай убеждаемся, что канал уже создан (например, если приложение давно не открывали
        // или телефон только что перезагрузили)
        createNotificationChannel(context)

        // Готовим Intent, который откроет главный экран приложения, если человек нажмёт на уведомление.
        // Флаги `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`:
        // - NEW_TASK нужен, потому что мы запускаем экран не из другой Activity, а из фонового BroadcastReceiver;
        // - CLEAR_TOP не плодит лишние копии MainActivity, если приложение уже открыто
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Оборачиваем Intent в PendingIntent (разрешение для шторки уведомлений открыть наш экран по клику).
        // Передаём `taskId` как код запроса, чтобы у разных уведомлений были свои отдельные PendingIntent.
        // FLAG_IMMUTABLE обязателен с Android 12 — запрещает другим приложениям менять этот Intent
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            taskId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Если у задачи есть описание — показываем его в тексте уведомления,
        // а если описание пустое или из одних пробелов (`ifBlank`) — подставляем понятную стандартную фразу
        val contentText = description.ifBlank { "Пора выполнить запланированную задачу!" }

        // Собираем само уведомление шаг за шагом через NotificationCompat.Builder
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Маленькая иконка в верхней строке состояния (берём готовую системную иконку напоминания)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            // Жирный заголовок — название нашей задачи
            .setContentTitle(title)
            // Короткий текст под заголовком
            .setContentText(contentText)
            // Стиль BigTextStyle позволяет развернуть уведомление в шторке и прочитать длинное описание целиком,
            // чтобы оно не обрезалось многоточием на первой строчке
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            // Высокий приоритет (дублирует IMPORTANCE_HIGH для совместимости)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Привязываем действие при нажатии на уведомление (открыть MainActivity)
            .setContentIntent(contentPendingIntent)
            // `setAutoCancel(true)` — уведомление само исчезнет из шторки, как только пользователь на него нажмёт
            .setAutoCancel(true)
            // Финальный шаг — создаём готовый объект уведомления
            .build()

        // Отправляем уведомление в систему.
        // В качестве ID уведомления передаём `taskId`: благодаря этому напоминания разных задач
        // показываются отдельно и не перезатирают друг друга в шторке
        NotificationManagerCompat.from(context).notify(taskId, notification)
    }
}
// Адрес (пакет) моего приложения
package ru.university.calendar

// PendingIntent — «отложенные клики» для кнопок на виджете (открыть приложение, добавить задачу, обновить виджет)
import android.app.PendingIntent
// AppWidgetManager — системная служба, которая реально перерисовывает виджеты на рабочем столе телефона.
// AppWidgetProvider — базовый класс для виджетов (под капотом это специальный BroadcastReceiver)
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
// ComponentName — точный «адрес» класса нашего виджета в системе, чтобы найти все его копии на рабочем столе
import android.content.ComponentName
// Context и Intent для общения с системой Android
import android.content.Context
import android.content.Intent
// RemoteViews — специальная «удалённая вёрстка» для виджетов (обычный findViewById в виджетах не работает)
import android.widget.RemoteViews
// Корутины Kotlin для асинхронного чтения задач из базы Room в фоновом потоке
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// Для получения сегодняшней даты
import java.util.Calendar
import java.util.Locale

// Виджет приложения на главный экран (рабочий стол) телефона.
// Показывает список задач именно на сегодняшний день, количество выполненных,
// кнопку "+" для быстрого добавления задачи и кнопку "↻" для ручного обновления.
// Подключается к системе через AndroidManifest.xml и файл настроек res/xml/task_widget_info.xml.
class TaskWidgetProvider : AppWidgetProvider() {

    // Главный метод виджета — Android вызывает его, когда виджет впервые поставили на рабочий стол,
    // по таймеру (у нас раз в 30 минут в task_widget_info.xml), по нажатию кнопки "↻"
    // или когда мы сами дёрнули `updateAllWidgets()` из TaskViewModel.
    // `appWidgetIds` — массив ID всех копий нашего виджета (пользователь может поставить на экран хоть два виджета сразу)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Тот же важный приём, что и в BootReceiver: поскольку AppWidgetProvider — это BroadcastReceiver,
        // `goAsync()` просит Android не убивать наш процесс, пока фоновая корутина не успеет прочитать базу Room
        val pendingResult = goAsync()

        // Идём в базу Room в фоновом потоке Dispatchers.IO (в главном потоке к базе обращаться нельзя)
        CoroutineScope(Dispatchers.IO).launch {
            // `try ... finally` гарантирует, что `pendingResult.finish()` вызовется в любом случае
            try {
                // Берём сегодняшнюю дату строкой "yyyy-MM-dd"
                val today = getTodayString()
                // Разово забираем из Room задачи на сегодня (они уже приходят отсортированными:
                // сначала невыполненные, потом по приоритету и времени)
                val tasks = AppDatabase.getDatabase(context)
                    .taskDao()
                    .getTasksByDateSync(today)

                // Для каждой установленной на рабочем столе копии виджета собираем свежую разметку
                // и просим AppWidgetManager её отрисовать
                for (widgetId in appWidgetIds) {
                    val views = buildRemoteViews(context, today, tasks, widgetId)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                // Говорим системе, что обновление закончено и процесс можно освобождать
                pendingResult.finish()
            }
        }
    }

    // Собирает объект `RemoteViews` — набор команд для отрисовки нашего виджета.
    // Почему в виджетах нельзя использовать обычный findViewById и setOnClickListener?
    // Потому что виджет живёт не внутри нашего приложения, а внутри системного «рабочего стола» (Launcher).
    // Поэтому мы упаковываем в RemoteViews инструкции вроде «поставь такой-то текст в такой-то ID»
    // и «при нажатии на такой-то ID отправь такой-то PendingIntent»
    private fun buildRemoteViews(
        context: Context,
        today: String,
        tasks: List<Task>,
        widgetId: Int
    ): RemoteViews {
        // Загружаем XML-шаблон виджета (res/layout/widget_tasks.xml)
        val views = RemoteViews(context.packageName, R.layout.widget_tasks)
        // Пишем сегодняшнюю дату в подзаголовок виджета
        views.setTextViewText(R.id.widgetDate, today)

        if (tasks.isEmpty()) {
            // Если на сегодня дел нет — показываем понятную подсказку вместо пустого квадрата
            views.setTextViewText(
                R.id.widgetTasksText,
                "На сегодня задач нет.\nНажмите +, чтобы добавить."
            )
            views.setTextViewText(R.id.widgetFooter, "Нажмите на виджет, чтобы открыть календарь")
        } else {
            // Ограничиваем вывод первыми 5 задачами, чтобы текст красиво помещался в стандартный размер виджета
            val maxLines = 5
            // Превращаем каждую из первых 5 задач в аккуратную текстовую строчку с галочкой, приоритетом и временем:
            // например, `☐ ❗ 14:30 Сдать лабораторную`
            val lines = tasks.take(maxLines).map { task ->
                val check = if (task.isDone) "☑" else "☐"
                val priorityMark = when (task.priority) {
                    2 -> "❗ "
                    else -> ""
                }
                val timePrefix = if (task.time.isNotBlank()) "${task.time} " else ""
                "$check $priorityMark$timePrefix${task.title}"
            }

            // `buildString` удобно склеивает итоговый многострочный текст:
            // соединяем строки через перенос `\n`, а если задач больше 5 — дописываем внизу «… и ещё N»
            val bodyText = buildString {
                append(lines.joinToString("\n"))
                if (tasks.size > maxLines) {
                    append("\n… и ещё ${tasks.size - maxLines}")
                }
            }

            // Считаем, сколько из сегодняшних задач уже отмечены выполненными, и выводим счётчик в подвал виджета
            val doneCount = tasks.count { it.isDone }
            views.setTextViewText(R.id.widgetTasksText, bodyText)
            views.setTextViewText(
                R.id.widgetFooter,
                "Выполнено: $doneCount из ${tasks.size}"
            )
        }

        // --- Настраиваем 3 кликабельные зоны на виджете через PendingIntent ---
        // Важная деталь: у всех трёх PendingIntent разные коды запроса (`widgetId`, `widgetId + 10_000`, `widgetId + 20_000`),
        // чтобы Android различал эти три кнопки и они не перезаписывали друг друга!

        // Клик №1: нажатие по самому фону виджета открывает главный экран MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

        // Клик №2: нажатие на кнопку "+" сразу открывает экран создания задачи AddTaskActivity
        // и передаёт туда сегодняшнюю дату (`EXTRA_PREFILL_DATE`), чтобы не выбирать её руками
        val addTaskIntent = Intent(context, AddTaskActivity::class.java).apply {
            putExtra("EXTRA_PREFILL_DATE", today)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addTaskPendingIntent = PendingIntent.getActivity(
            context,
            widgetId + 10_000,
            addTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetBtnAdd, addTaskPendingIntent)

        // Клик №3: нажатие на кнопку "↻" отправляет системное сообщение ACTION_APPWIDGET_UPDATE
        // обратно в наш же TaskWidgetProvider, что запускает метод onUpdate и перечитывает базу
        val refreshIntent = Intent(context, TaskWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId + 20_000,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetBtnRefresh, refreshPendingIntent)

        return views
    }

    // Блок общих методов класса (`companion object`), чтобы вызывать `TaskWidgetProvider.updateAllWidgets(context)`
    // прямо из TaskViewModel при любом добавлении, редактировании, удалении или импорте задач
    companion object {
        // Находит все установленные на рабочем столе виджеты нашего приложения и принудительно обновляет их
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            // Указываем адрес именно нашего класса виджета
            val component = ComponentName(context, TaskWidgetProvider::class.java)
            // Получаем массив ID всех виджетов этого приложения на рабочем столе
            val ids = manager.getAppWidgetIds(component)
            // Если пользователь вообще не выносил виджет на рабочий стол — выходим, ничего отправлять не нужно
            if (ids.isEmpty()) return

            // Отправляем системный broadcast самому себе — Android тут же вызовет `onUpdate` для всех этих ID
            val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        // Возвращает сегодняшнюю дату строкой формата "yyyy-MM-dd" (+1 к месяцу из-за нумерации с нуля в Calendar)
        private fun getTodayString(): String {
            val c = Calendar.getInstance()
            return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}
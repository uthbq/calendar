// Адрес (пакет) моего приложения
package ru.university.calendar

// LiveData — «наблюдаемый» контейнер данных, который сам сообщает экрану об изменениях в базе
import androidx.lifecycle.LiveData

// Слой Repository (Репозиторий) в архитектуре MVVM — это прослойка между TaskViewModel и TaskDao (базой Room).
//
// Частый вопрос преподавателя на защите: «Зачем вообще нужен Repository, если ViewModel могла бы звать TaskDao напрямую?»
// Ответ простой: чтобы разделить ответственность.
// 1) TaskViewModel вообще не знает, откуда именно берутся данные — из локальной Room, из файла или с сервера в интернете.
//    Если завтра понадобится добавить синхронизацию с облаком, менять придётся только этот класс, а ViewModel и все Activity останутся нетронутыми.
// 2) Вся логика выбора конкретного SQL-запроса под нужную сортировку (через `when (sortOrder)`) аккуратно спрятана здесь,
//    поэтому сама ViewModel не захламляется кучей однотипных вызовов DAO.
//
// В конструктор передаём `private val taskDao: TaskDao` — ссылку на наш DAO-интерфейс из AppDatabase.
class TaskRepository(private val taskDao: TaskDao) {

    // Возвращает LiveData со списком всех уникальных дат, в которых есть задачи (нужно календарю для точек).
    // Запись через `=` — короткий способ написать однострочную функцию вместо `{ return ... }`
    fun getDatesWithTasks(): LiveData<List<String>> = taskDao.getDatesWithTasks()

    // Выбирает задачи за конкретную дату (`date` в формате yyyy-MM-dd) с учётом выбранной сортировки (`sortOrder`).
    // Конструкция `when` проверяет наш enum SortOrder и подбирает нужный SQL-метод из TaskDao
    fun getTasksByDate(date: String, sortOrder: SortOrder): LiveData<List<Task>> {
        return when (sortOrder) {
            SortOrder.BY_DATE -> taskDao.getTasksByDateOrderByTime(date)
            SortOrder.BY_TITLE -> taskDao.getTasksByDateOrderByTitle(date)
            SortOrder.BY_STATUS -> taskDao.getTasksByDateOrderByStatus(date)
            SortOrder.BY_PRIORITY -> taskDao.getTasksByDateOrderByPriority(date)
        }
    }

    // Ищет задачи по тексту `query` (по названию и категории) с учётом выбранной сортировки `sortOrder`.
    // Когда `query` пустой (""), этот же метод возвращает вообще все задачи из базы для режима «Все задачи»
    fun searchTasks(query: String, sortOrder: SortOrder): LiveData<List<Task>> {
        return when (sortOrder) {
            SortOrder.BY_DATE -> taskDao.searchTasksOrderByDate(query)
            SortOrder.BY_TITLE -> taskDao.searchTasksOrderByTitle(query)
            SortOrder.BY_STATUS -> taskDao.searchTasksOrderByStatus(query)
            SortOrder.BY_PRIORITY -> taskDao.searchTasksOrderByPriority(query)
        }
    }

    // --- Методы ниже помечены `suspend`, потому что вызывают `suspend`-методы TaskDao ---
    // Это гарантирует, что работать с базой можно только из фоновой корутины (Dispatchers.IO),
    // и никто случайно не заблокирует главный поток интерфейса.

    // Разово достаёт все задачи из базы обычным списком (нужно для экспорта в JSON и проверки дубликатов при импорте)
    suspend fun getAllTasksSync(): List<Task> = taskDao.getAllTasksSync()

    // Сохраняет одну новую задачу в Room и возвращает её сгенерированный ID (`Long`) для постановки будильника
    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)

    // Сохраняет сразу список задач за один раз (при импорте из JSON) и возвращает список их новых ID
    suspend fun insertTasks(tasks: List<Task>): List<Long> = taskDao.insertTasks(tasks)

    // Обновляет существующую задачу в базе (по её ID)
    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    // Удаляет задачу из базы
    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
}
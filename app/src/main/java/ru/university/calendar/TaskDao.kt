// Адрес (пакет) моего приложения
package ru.university.calendar

// LiveData — «наблюдаемая» обёртка над данными: как только таблица tasks_table меняется,
// Room сам автоматически отправляет свежий список всем, кто подписался через observe()
import androidx.lifecycle.LiveData
// Аннотации Room для описания операций с базой данных
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

// DAO (Data Access Object) — интерфейс, в котором перечислены вообще все операции с таблицей tasks_table:
// добавление, обновление, удаление, поиск, фильтрация по дню и сортировка.
//
// Почему это именно `interface` без тела методов?
// В Room нам не нужно писать руками курсоры SQLite и разбор колонок — при сборке проекта Room сам
// сгенерирует рабочий класс с реализацией всех этих методов.
// Большой плюс @Query по сравнению с «сырым» SQL: Room проверяет правильность SQL-запросов
// и названия всех колонок ещё на этапе компиляции (если опечататься в имени поля, проект просто не соберётся,
// а не упадёт у пользователя во время работы).
@Dao
interface TaskDao {

    // Добавляет одну новую задачу в таблицу.
    // `onConflict = OnConflictStrategy.REPLACE` — если вдруг вставляется задача с уже занятым id,
    // старая строка просто перезапишется новой без ошибки.
    // Слово `suspend` означает, что функцию можно вызывать только из фоновой корутины (Dispatchers.IO),
    // чтобы не тормозить главный поток интерфейса.
    // Возвращает `Long` — это реальный новый ID, который база присвоила задаче (он сразу нужен в TaskViewModel,
    // чтобы поставить будильник в AlarmScheduler именно по этому ID)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    // Массовая вставка сразу списка задач за один заход (используется при импорте резервной копии из JSON).
    // Возвращает список новых ID (`List<Long>`) для всех вставленных задач, чтобы поставить им напоминания
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>): List<Long>

    // Обновляет существующую задачу в базе (Room сам находит нужную строку по её первичному ключу `id`
    // и перезаписывает все поля: текст, дату, галочку isDone, категорию, приоритет)
    @Update
    suspend fun updateTask(task: Task)

    // Удаляет переданную задачу из таблицы (Room находит нужную строку по её `id`)
    @Delete
    suspend fun deleteTask(task: Task)

    // Выбирает все уникальные даты, на которые есть хотя бы одна задача — нужно календарю для рисования точек.
    // Ключевое слово SQL `DISTINCT` убирает дубликаты (если на один день 5 задач, эта дата вернётся один раз).
    // Обрати внимание: здесь НЕТ слова `suspend`, потому что методы, возвращающие LiveData,
    // Room сам автоматически выполняет в фоновом потоке и постоянно следит за изменениями в таблице
    @Query("SELECT DISTINCT date FROM tasks_table")
    fun getDatesWithTasks(): LiveData<List<String>>

    // --- Разовые (синхронные внутри корутины) запросы для BootReceiver, экспорта и виджета ---
    // В отличие от LiveData выше, эти методы помечены `suspend` и возвращают обычный `List<Task>`,
    // потому что фоновым компонентам (BootReceiver, экспорту JSON и виджету) нужно просто один раз
    // получить текущий снимок данных из базы, а не подписываться на постоянные обновления экрана.

    // Выбирает все невыполненные задачи (`isDone = 0`, т.к. false в SQLite хранится как 0),
    // у которых задано время (`time != ''`). Используется в BootReceiver после перезагрузки телефона
    @Query("SELECT * FROM tasks_table WHERE isDone = 0 AND time != ''")
    suspend fun getPendingTasksWithTime(): List<Task>

    // Достаёт вообще все задачи из базы, отсортированные по дате, времени и ID.
    // Используется при экспорте в JSON и при проверке на дубликаты во время импорта
    @Query("SELECT * FROM tasks_table ORDER BY date ASC, time ASC, id ASC")
    suspend fun getAllTasksSync(): List<Task>

    // Достаёт задачи за конкретную дату (для виджета на главном экране).
    // `:selectedDate` — параметр, куда Room безопасно подставит значение аргумента `selectedDate` (защита от SQL-инъекций).
    // Порядок сортировки для виджета:
    // 1) `isDone ASC` — сначала невыполненные (0), выполненные (1) уходят вниз;
    // 2) `priority DESC` — сначала важные (2 -> 1 -> 0);
    // 3) `CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC` — хитрость SQL: если просто сделать `ORDER BY time ASC`,
    //    то пустая строка '' по алфавиту встанет РАНЬШЕ чем "09:00". А `CASE WHEN` даёт пустому времени вес 1,
    //    а указанному времени вес 0 — поэтому задачи со временем идут по порядку сверху, а задачи без времени уходят в конец!
    @Query(
        "SELECT * FROM tasks_table WHERE date = :selectedDate " +
                "ORDER BY isDone ASC, priority DESC, CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC"
    )
    suspend fun getTasksByDateSync(selectedDate: String): List<Task>

    // --- Задачи выбранного дня (LiveData для главного экрана с 4 режимами сортировки) ---

    // Режим 1 (по дате и времени): выбираем задачи за выбранный день;
    // сначала те, у которых задано время (от ранних к поздним), потом без времени; при совпадении — свежие выше (`id DESC`)
    @Query(
        "SELECT * FROM tasks_table WHERE date = :selectedDate " +
                "ORDER BY CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC, id DESC"
    )
    fun getTasksByDateOrderByTime(selectedDate: String): LiveData<List<Task>>

    // Режим 2 (по алфавиту): сортировка по названию от А до Я (`ASC`).
    // `COLLATE NOCASE` отключает чувствительность к регистру (чтобы "арбуз" и "Арбуз" сортировались рядом, а не отдельно)
    @Query(
        "SELECT * FROM tasks_table WHERE date = :selectedDate " +
                "ORDER BY title COLLATE NOCASE ASC"
    )
    fun getTasksByDateOrderByTitle(selectedDate: String): LiveData<List<Task>>

    // Режим 3 (сначала невыполненные): сначала идут задачи с `isDone = 0` (активные), ниже `isDone = 1` (выполненные),
    // а внутри каждой группы они дополнительно упорядочены по времени
    @Query(
        "SELECT * FROM tasks_table WHERE date = :selectedDate " +
                "ORDER BY isDone ASC, CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC, id DESC"
    )
    fun getTasksByDateOrderByStatus(selectedDate: String): LiveData<List<Task>>

    // Режим 4 (по приоритету): сначала Высокий (2), затем Средний (1), затем Низкий (0) за счёт `priority DESC`,
    // а среди задач с одинаковым приоритетом невыполненные и более ранние по времени стоят выше
    @Query(
        "SELECT * FROM tasks_table WHERE date = :selectedDate " +
                "ORDER BY priority DESC, isDone ASC, CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC"
    )
    fun getTasksByDateOrderByPriority(selectedDate: String): LiveData<List<Task>>

    // --- Поиск по названию или категории + режим «Все задачи» (LiveData с 4 режимами сортировки) ---
    // Как работает `WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'`:
    // - `||` в SQLite — это склейка строк;
    // - знак `%` означает «любое количество любых символов»;
    // - значит если юзер ввёл "мат", запрос найдёт и "Математика", и "Подготовить материал", и категорию "Учёба" по слову "уч".
    // Удобный бонус: когда строка поиска пустая (`query = ""`), шаблон превращается в `LIKE '%%'`,
    // которому подходят вообще ВСЕ задачи в базе — поэтому эти же запросы обслуживают и режим «Все задачи»!

    // Поиск / Все задачи с сортировкой по дате и времени (благодаря формату yyyy-MM-dd даты сортируются хронологически)
    @Query(
        "SELECT * FROM tasks_table " +
                "WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' " +
                "ORDER BY date ASC, CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC, id DESC"
    )
    fun searchTasksOrderByDate(query: String): LiveData<List<Task>>

    // Поиск / Все задачи с сортировкой по алфавиту (без учёта заглавных/строчных букв)
    @Query(
        "SELECT * FROM tasks_table " +
                "WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' " +
                "ORDER BY title COLLATE NOCASE ASC"
    )
    fun searchTasksOrderByTitle(query: String): LiveData<List<Task>>

    // Поиск / Все задачи: сначала невыполненные, потом по дате и времени
    @Query(
        "SELECT * FROM tasks_table " +
                "WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' " +
                "ORDER BY isDone ASC, date ASC, CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC, id DESC"
    )
    fun searchTasksOrderByStatus(query: String): LiveData<List<Task>>

    // Поиск / Все задачи: сначала самый высокий приоритет, потом невыполненные, потом по дате и времени
    @Query(
        "SELECT * FROM tasks_table " +
                "WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' " +
                "ORDER BY priority DESC, isDone ASC, date ASC, CASE WHEN time = '' THEN 1 ELSE 0 END, time ASC"
    )
    fun searchTasksOrderByPriority(query: String): LiveData<List<Task>>
}
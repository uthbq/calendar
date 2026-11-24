// Адрес (пакет) моего приложения — все файлы в этой папке видят друг друга без лишних импортов
package ru.university.calendar

// Application — объект всего приложения целиком (нужен, чтобы передать его в базу Room,
// в планировщик будильников и в обновление виджета без привязки к какому-то конкретному экрану)
import android.app.Application
// AndroidViewModel — специальная версия ViewModel, которая сразу умеет хранить ссылку на Application.
// LiveData, MutableLiveData, MediatorLiveData — «наблюдаемые» коробки для данных, на которые подписывается экран.
// viewModelScope — встроенная область запуска фоновых корутин, привязанная к жизни самой ViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
// Инструменты для работы с потоками в корутинах:
// Dispatchers.IO — фоновый поток для базы данных и файлов;
// Dispatchers.Main — главный поток интерфейса (для показа Toast и обновления UI);
// launch — запускает фоновую задачу;
// withContext — временно переключает поток прямо внутри корутины
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// Стандартные классы для получения сегодняшней даты и форматирования цифр
import java.util.Calendar
import java.util.Locale

// Главный «мозг» между экранами и базой данных (в архитектуре MVVM это слой ViewModel).
//
// Зачем вообще нужна ViewModel:
// 1) Она хранит текущее состояние экрана (какая дата выбрана, что введено в поиск, какая сортировка включена)
//    и НЕ уничтожается при повороте телефона (в отличие от Activity).
// 2) Экраны (MainActivity, AddTaskActivity) вообще не работают с базой Room напрямую — они только
//    подписываются на LiveData отсюда и вызывают методы вроде insertTask / deleteTask.
// 3) Наследуемся именно от `AndroidViewModel`, а не от обычной `ViewModel`, потому что для работы с Room,
//    AlarmManager и виджетом нужен Context. Хранить внутри ViewModel ссылку на Activity строго нельзя
//    (будет утечка памяти при повороте), а вот контекст всего `Application` хранить абсолютно безопасно.
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    // Ссылка на наш слой репозитория (все обращения к базе идут только через него, а не напрямую в DAO)
    private val repository: TaskRepository

    // Наблюдаемый список всех дат (в формате "yyyy-MM-dd"), на которые есть задачи —
    // на него подписывается MainActivity, чтобы рисовать цветные точки в календаре
    val datesWithTasks: LiveData<List<String>>

    // --- Хранение состояния главного экрана (выбранная дата, режим «Все», поиск, сортировка) ---
    // Тут везде используется популярный приём с двумя переменными (одна с подчёркиванием `_`, вторая без):
    // - `private val _selectedDate = MutableLiveData(...)` — изменяемая коробка, доступная ТОЛЬКО внутри ViewModel;
    // - `val selectedDate: LiveData<...> = _selectedDate` — открытая наружу версия только для чтения и подписки.
    // Зачем так делать: чтобы MainActivity могла наблюдать за значением, но не могла случайно поменять его в обход методов ViewModel.

    // Текущая выбранная дата в календаре (при старте приложения сразу ставим сегодняшнее число)
    private val _selectedDate = MutableLiveData(getTodayString())
    val selectedDate: LiveData<String> = _selectedDate

    // Режим отображения: false — показываем задачи только за выбранный день; true — показываем вообще все задачи
    private val _showAllTasks = MutableLiveData(false)
    val showAllTasks: LiveData<Boolean> = _showAllTasks

    // Текущий текст, который пользователь ввёл в строку поиска (изначально пустая строка "")
    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    // Текущий выбранный режим сортировки (по умолчанию — по дате и времени)
    private val _sortOrder = MutableLiveData(SortOrder.BY_DATE)
    val sortOrder: LiveData<SortOrder> = _sortOrder

    // --- Динамическое переключение источника списка задач ---
    // Так как список задач может приходить из разных SQL-запросов (за конкретный день / поиск / разная сортировка),
    // мы не можем просто один раз привязать одну LiveData.
    // `currentSource` запоминает, на какой конкретный запрос из Room мы подписаны прямо сейчас (`null` до первого запуска)
    private var currentSource: LiveData<List<Task>>? = null

    // `MediatorLiveData` — специальная LiveData-«переходник», которая умеет переподключаться к разным источникам
    // внутри, но для MainActivity выглядит как одна постоянная переменная `displayedTasks`.
    // Благодаря этому MainActivity подписывается на `displayedTasks` ровно один раз в onCreate,
    // а мы здесь спокойно меняем SQL-запросы под капотом
    val displayedTasks = MediatorLiveData<List<Task>>()

    // Блок `init` выполняется автоматически сразу в момент создания TaskViewModel
    init {
        // Получаем единственный экземпляр базы Room и достаём из него интерфейс TaskDao
        val taskDao = AppDatabase.getDatabase(application).taskDao()
        // Создаём репозиторий поверх DAO
        repository = TaskRepository(taskDao)
        // Сразу подключаем список дат для точек в календаре
        datesWithTasks = repository.getDatesWithTasks()
        // Загружаем стартовый список задач (на сегодняшний день с сортировкой по дате/времени)
        refreshSource()
    }

    // Вызывается из MainActivity, когда пользователь ткнул по конкретному дню в календаре
    fun selectDate(date: String) {
        // Запоминаем новую дату и автоматически выходим из режима «Все задачи»,
        // чтобы пользователь сразу увидел задачи именно за выбранный день
        _selectedDate.value = date
        _showAllTasks.value = false
        // Переключаем SQL-запрос под новую дату
        refreshSource()
    }

    // Переключает режим «Все задачи» <-> «Задачи за выбранный день» (кнопка рядом с заголовком списка)
    fun toggleShowAllTasks() {
        // Инвертируем текущее булево значение через `!` (если вдруг было null, берём false)
        _showAllTasks.value = !(_showAllTasks.value ?: false)
        refreshSource()
    }

    // Обновляет строку поиска (вызывается на каждую введённую букву из SearchView)
    fun setSearchQuery(query: String) {
        // Проверяем, реально ли текст поменялся, чтобы не дёргать базу данных впустую
        if (_searchQuery.value != query) {
            _searchQuery.value = query
            refreshSource()
        }
    }

    // Меняет режим сортировки (вызывается из диалога сортировки в MainActivity)
    fun setSortOrder(order: SortOrder) {
        // Тоже переподключаем запрос только если пользователь выбрал другой режим
        if (_sortOrder.value != order) {
            _sortOrder.value = order
            refreshSource()
        }
    }

    // Сердце фильтрации и сортировки: смотрит на все 4 текущих параметра и подставляет в `displayedTasks`
    // нужную LiveData из репозитория
    private fun refreshSource() {
        val showAll = _showAllTasks.value ?: false
        val date = _selectedDate.value ?: getTodayString()
        val query = _searchQuery.value.orEmpty().trim()
        val sort = _sortOrder.value ?: SortOrder.BY_DATE

        // Если в поиск что-то введено ИЛИ включена кнопка «Все задачи» — используем поисковый SQL-запрос
        // (при пустом query `LIKE '%%'` отдаст вообще все задачи с нужной сортировкой).
        // Иначе берём задачи строго за выбранную дату `date`
        val newSource = if (query.isNotEmpty() || showAll) {
            repository.searchTasks(query, sort)
        } else {
            repository.getTasksByDate(date, sort)
        }

        // ОБЯЗАТЕЛЬНО отписываемся от предыдущего источника перед подключением нового!
        // `?.let { ... }` выполнится только если `currentSource` не null.
        // Если не отписаться от старого запроса, MediatorLiveData начнёт получать обновления сразу и от старой даты, и от новой
        currentSource?.let { displayedTasks.removeSource(it) }
        // Запоминаем новый источник как текущий
        currentSource = newSource
        // Подписываемся на новый источник: как только Room пришлёт список задач (и при любом дальнейшем изменении в БД),
        // сразу кладём этот список в `displayedTasks.value`, и MainActivity автоматически обновит экран
        displayedTasks.addSource(newSource) { tasks ->
            displayedTasks.value = tasks
        }
    }

    // --- CRUD-операции (все запускаются в `viewModelScope.launch(Dispatchers.IO)`) ---
    // Почему именно так:
    // - `Dispatchers.IO` переносит работу с базой SQLite в фоновый поток, чтобы интерфейс не подвисал;
    // - `viewModelScope` автоматически отменит корутину, если сама ViewModel уничтожится (нет утечек памяти).

    // Добавление новой задачи в базу
    fun insertTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        // Вставляем задачу в Room и сразу забираем реальный новый ID, который сгенерировала база (переводим Long в Int)
        val newId = repository.insertTask(task).toInt()
        // Делаем копию задачи уже с этим реальным `newId` (у исходного объекта id был равен 0)
        val savedTask = task.copy(id = newId)
        // Ставим напоминание в AlarmManager по реальному ID (getApplication() отдаёт контекст приложения)
        AlarmScheduler.scheduleTaskReminder(getApplication(), savedTask)
        // Сразу просим виджет на рабочем столе перерисоваться (на случай если добавили задачу на сегодня)
        TaskWidgetProvider.updateAllWidgets(getApplication())
    }

    // Обновление существующей задачи (и при редактировании в AddTaskActivity, и при нажатии на CheckBox)
    fun updateTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        // Сохраняем обновлённые поля в Room
        repository.updateTask(task)
        // Сначала всегда отменяем старый будильник этой задачи (вдруг время перенесли, стёрли или задачу выполнили)
        AlarmScheduler.cancelTaskReminder(getApplication(), task.id)
        // Если задача всё ещё не выполнена (`!task.isDone`), ставим актуальное напоминание по новому времени
        if (!task.isDone) {
            AlarmScheduler.scheduleTaskReminder(getApplication(), task)
        }
        // Обновляем виджет на рабочем столе (например, чтобы там сразу появилась галочка ☑ или новое название)
        TaskWidgetProvider.updateAllWidgets(getApplication())
    }

    // Удаление задачи (вызывается после подтверждения свайпа в MainActivity)
    fun deleteTask(task: Task) = viewModelScope.launch(Dispatchers.IO) {
        // Удаляем запись из Room
        repository.deleteTask(task)
        // Обязательно отменяем запланированное напоминание, чтобы будильник не прозвенел по уже удалённой задаче
        AlarmScheduler.cancelTaskReminder(getApplication(), task.id)
        // Обновляем виджет на рабочем столе, чтобы удалённая задача исчезла и оттуда
        TaskWidgetProvider.updateAllWidgets(getApplication())
    }

    // Экспорт всех задач в JSON-текст.
    // Принимает функцию-колбэк `onReady: (String, Int) -> Unit`, куда вернёт готовую JSON-строку и количество задач
    fun exportTasksToJson(onReady: (String, Int) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        // В фоновом потоке забираем все задачи из базы и превращаем их в красивый JSON-текст
        val tasks = repository.getAllTasksSync()
        val json = TaskJsonHelper.tasksToJson(tasks)
        // Переключаемся обратно на главный поток (`Dispatchers.Main`), потому что внутри `onReady`
        // MainActivity будет показывать Toast и работать с экраном — это можно делать только в главном потоке
        withContext(Dispatchers.Main) {
            onReady(json, tasks.size)
        }
    }

    // Импорт задач из JSON-текста с защитой от дубликатов.
    // В колбэк `onComplete` возвращаем два числа:
    // - `addedCount` — сколько новых задач реально добавили (или `null`, если JSON вообще оказался битым);
    // - `skippedCount` — сколько задач пропустили, потому что точно такие же уже есть в базе
    fun importTasksFromJson(
        json: String,
        onComplete: (addedCount: Int?, skippedCount: Int) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        // Пытаемся разобрать JSON в список задач
        val parsedTasks = TaskJsonHelper.jsonToTasks(json)
        // Если вернулся null (файл повреждён или это не JSON) — сообщаем об ошибке в главный поток и выходим из корутины
        if (parsedTasks == null) {
            withContext(Dispatchers.Main) { onComplete(null, 0) }
            return@launch
        }

        // Защита от дубликатов: достаём все задачи, которые уже лежат в базе,
        // превращаем каждую в уникальный текстовый «отпечаток» через `buildTaskKey` и складываем в изменяемое множество (`MutableSet`).
        // Поиск и вставка в Set работают мгновенно
        val existingKeys = repository.getAllTasksSync()
            .map { buildTaskKey(it) }
            .toMutableSet()

        // Сюда сложим только действительно новые задачи, а в `skippedCount` посчитаем пропущенные дубли
        val newTasks = mutableListOf<Task>()
        var skippedCount = 0

        for (task in parsedTasks) {
            val key = buildTaskKey(task)
            // Хитрый и удобный момент в Kotlin/Java: метод `Set.add(key)` возвращает `true`,
            // если такого ключа ещё НЕ было (и тут же добавляет его в Set — это заодно отсеет дубли и внутри самого JSON-файла!),
            // и возвращает `false`, если такой ключ уже есть
            if (existingKeys.add(key)) {
                newTasks.add(task)
            } else {
                skippedCount++
            }
        }

        // Если после проверки нашлись новые задачи — записываем их в базу за один раз
        if (newTasks.isNotEmpty()) {
            val insertedIds = repository.insertTasks(newTasks)
            // Для каждой добавленной невыполненной задачи ставим её напоминание по новому ID из базы
            newTasks.forEachIndexed { index, task ->
                val newId = insertedIds.getOrNull(index)?.toInt() ?: 0
                if (newId != 0 && !task.isDone) {
                    AlarmScheduler.scheduleTaskReminder(getApplication(), task.copy(id = newId))
                }
            }
            // Обновляем виджет, если среди восстановленных задач есть дела на сегодня
            TaskWidgetProvider.updateAllWidgets(getApplication())
        }

        // Возвращаемся в главный поток и отдаём итоговые цифры в MainActivity для показа Toast
        withContext(Dispatchers.Main) {
            onComplete(newTasks.size, skippedCount)
        }
    }

    // Собирает «отпечаток» задачи для сравнения на дубликаты: склеивает через `|` название (в нижнем регистре,
    // чтобы «Учёба» и «учёба» считались одной задачей), дату, время и описание без крайних пробелов
    private fun buildTaskKey(task: Task): String {
        return listOf(
            task.title.trim().lowercase(Locale.getDefault()),
            task.date.trim(),
            task.time.trim(),
            task.description.trim()
        ).joinToString("|")
    }

    // Вспомогательная функция: возвращает сегодняшнюю дату с часов телефона в едином формате "yyyy-MM-dd"
    // (к месяцу прибавляем +1, потому что в Calendar январь = 0)
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
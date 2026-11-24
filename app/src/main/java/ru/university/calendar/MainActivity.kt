// Адрес (пакет) моего приложения — все классы внутри папки ru.university.calendar видят друг друга без лишних импортов
package ru.university.calendar

// Manifest — системный класс Android, из которого берём название разрешения на уведомления (POST_NOTIFICATIONS)
import android.Manifest
// Intent — «послание» для перехода на другие экраны (AddTaskActivity и DetailActivity) и передачи туда данных
import android.content.Intent
// PackageManager нужен, чтобы сравнить результат проверки разрешения с константой PERMISSION_GRANTED («разрешено»)
import android.content.pm.PackageManager
// Build позволяет узнать версию Android на устройстве (нужно для проверки Android 13+)
import android.os.Build
// Bundle — «коробка» для временного состояния экрана при пересоздании Activity (например, при повороте)
import android.os.Bundle
// View — базовый класс любого элемента на экране (отсюда берём View.VISIBLE и View.GONE)
import android.view.View
// Стандартные элементы: кнопка, текст и всплывающее сообщение снизу экрана (Toast)
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
// Готовые системные контракты AndroidX: запрос разрешений и открытие системного проводника для создания/выбора файла
import androidx.activity.result.contract.ActivityResultContracts
// Подключает удобную запись `by viewModels()` для получения ViewModel
import androidx.activity.viewModels
// Стандартное всплывающее окно с кнопками/списком (AlertDialog) и базовый класс современного экрана (AppCompatActivity)
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
// Компонент строки поиска из AndroidX
import androidx.appcompat.widget.SearchView
// ContextCompat помогает безопасно проверять разрешения на всех версиях Android
import androidx.core.content.ContextCompat
// Инструменты для RecyclerView:
// GridLayoutManager — раскладывает элементы сеткой (у нас 7 колонок для календаря);
// ItemTouchHelper — добавляет жесты смахивания (свайпа) по карточкам списка;
// LinearLayoutManager — раскладывает карточки обычным вертикальным списком;
// RecyclerView — сам современный переиспользуемый список
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Круглая плавающая кнопка "+" из Material Design в правом нижнем углу экрана
import com.google.android.material.floatingactionbutton.FloatingActionButton
// Стандартные классы Java для работы с календарём и форматирования чисел (0-9)
import java.util.Calendar
import java.util.Locale

// Главный экран всего приложения (в архитектуре MVVM это слой View).
// Здесь собрано всё основное: строка поиска, кнопки бэкапа/сортировки/темы, сетка календаря,
// список задач выбранного дня и кнопка "+" для добавления новой задачи.
// Сама Activity в базу данных Room напрямую не лезет — она только показывает данные,
// которые ей присылает TaskViewModel, и передаёт туда действия пользователя.
class MainActivity : AppCompatActivity() {

    // Получаем нашу TaskViewModel через `by viewModels()`.
    // Android сам создаст её при первом обращении и сохранит при повороте экрана,
    // поэтому выбранная дата, текст поиска и сортировка не сбросятся, если перевернуть телефон
    private val taskViewModel: TaskViewModel by viewModels()

    // Адаптеры для двух списков на экране: списка задач и календарной сетки.
    // Слово `lateinit var` говорит Kotlin: «я точно заполню эти переменные чуть позже (в onCreate),
    // до первого использования, так что не заставляй меня писать тут null и везде ставить знаки вопроса ?»
    private lateinit var tasksAdapter: TasksAdapter
    private lateinit var calendarAdapter: CalendarAdapter

    // Ссылки на основные текстовые элементы и кнопку переключения режима,
    // к которым нужно обращаться из разных методов внутри MainActivity (поэтому они вынесены в поля класса)
    private lateinit var tvMonthYear: TextView
    private lateinit var tvTasksHeader: TextView
    private lateinit var btnToggleAllTasks: Button
    private lateinit var tvEmptyDay: TextView

    // Какой год и месяц сейчас показаны в календарной сетке (`var` — меняются при нажатии стрелок ‹ и ›).
    // Стартовые значения 2026 и 1 — просто заглушки, в onCreate они сразу перезапишутся на реальный текущий месяц
    private var displayedYear: Int = 2026
    private var displayedMonth: Int = 1 // 1..12

    // Множество (Set) всех дат формата "yyyy-MM-dd", на которые в базе есть хотя бы одна задача.
    // Использую именно Set, а не обычный List, потому что проверка `datesWithTasksSet.contains(dateStr)`
    // в множестве работает мгновенно и там автоматически нет повторов
    private var datesWithTasksSet: Set<String> = emptySet()

    // Массив русских названий месяцев для заголовка календаря (индекс 0 = Январь, индекс 11 = Декабрь)
    private val monthNames = arrayOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    // --- Системные «лаунчеры» для запросов к Android ---
    // В современном Android их обязательно регистрировать прямо на уровне класса (до запуска onStart),
    // а запускать потом в нужный момент через `.launch(...)`.

    // Лаунчер 1: запрос системного разрешения на показ уведомлений (для Android 13 и выше).
    // В фигурных скобках результат ответа пользователя (дал разрешение или нет) — нам он тут не нужен, поэтому `_`
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    // Лаунчер 2: экспорт задач в JSON через системный проводник Android (CreateDocument).
    // Плюс этого подхода: пользователю не нужно выдавать опасные разрешения на доступ ко всей памяти телефона —
    // система сама даёт нам адрес (`uri`) ровно того одного файла, который выбрал пользователь
    private val exportTasksLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            // Если пользователь нажал «Назад» и передумал сохранять файл — uri будет null, просто выходим
            if (uri == null) return@registerForActivityResult

            // Просим ViewModel в фоновом потоке достать все задачи из Room и собрать из них JSON-строку
            taskViewModel.exportTasksToJson { json, count ->
                // Оборачиваем запись файла в try/catch на случай, если кончилось место или файл недоступен
                try {
                    // Через contentResolver открываем поток записи по выбранному адресу uri.
                    // Блок `.use { ... }` в Kotlin очень удобен: он сам гарантированно закроет файл после записи,
                    // даже если внутри случится ошибка. Строку переводим в байты в кодировке UTF-8 (для нормальной кириллицы)
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "Экспортировано задач: $count", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Лаунчер 3: импорт задач из JSON-файла через системный проводник Android (OpenDocument)
    private val importTasksLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            // Если пользователь закрыл окно выбора файла, ничего не выбрав — выходим
            if (uri == null) return@registerForActivityResult
            try {
                // Открываем поток чтения файла по uri, оборачиваем в bufferedReader с UTF-8,
                // считываем весь текст через readText() (и `.use` сам закрывает файл).
                // Если вдруг поток не открылся (null), `.orEmpty()` подставит пустую строку ""
                val json = contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

                // Отдаём текст файла в ViewModel — она в фоне разберёт JSON, отсеет задачи,
                // которые уже есть в базе (защита от дублей), добавит новые и вернёт два счётчика
                taskViewModel.importTasksFromJson(json) { addedCount, skippedCount ->
                    // Выбираем понятное сообщение для пользователя в зависимости от результата
                    val message = when {
                        // Если addedCount == null, значит файл вообще не похож на правильный JSON
                        addedCount == null -> "Ошибка: некорректный JSON-файл"
                        // Все задачи из файла уже были в базе
                        addedCount == 0 && skippedCount > 0 ->
                            "Новых задач нет (пропущено дублей: $skippedCount)"
                        // Часть задач добавили, а часть уже была на месте
                        skippedCount > 0 ->
                            "Добавлено: $addedCount, пропущено дублей: $skippedCount"
                        // Все задачи из бэкапа оказались новыми
                        else -> "Импортировано задач: $addedCount"
                    }
                    // Toast.LENGTH_LONG держит сообщение на экране чуть дольше, чтобы успеть прочитать цифры
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
            }
        }

    // Главный метод жизненного цикла Activity — вызывается Android при создании экрана
    override fun onCreate(savedInstanceState: Bundle?) {
        // ВАЖНО: применяем сохранённую тему (светлую/тёмную/системную) ДО super.onCreate и setContentView,
        // чтобы экран сразу открылся в нужных цветах и не мигнул белым при старте
        ThemePreferences.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        // Подключаем XML-разметку главного экрана (res/layout/activity_main.xml)
        setContentView(R.layout.activity_main)

        // Создаём канал уведомлений (обязательно с Android 8.0; если он уже есть, система просто ничего не сделает)
        NotificationHelper.createNotificationChannel(this)
        // Если на телефоне Android 13+, проверяем и запрашиваем разрешение на показ уведомлений
        requestNotificationPermissionIfNeeded()

        // Находим основные элементы разметки по их ID
        tvMonthYear = findViewById(R.id.tvMonthYear)
        tvTasksHeader = findViewById(R.id.tvTasksHeader)
        btnToggleAllTasks = findViewById(R.id.btnToggleAllTasks)
        tvEmptyDay = findViewById(R.id.tvEmptyDay)

        // Разбили настройку экрана на понятные отдельные методы, чтобы onCreate не превратился в кашу:
        initDisplayedMonthFromSelected() // 1) выставляем нужный месяц в календаре
        setupSearchAndControls()         // 2) подключаем поиск и кнопки Бэкап / Сортировка / Тема
        setupCalendar()                  // 3) настраиваем сетку календаря и стрелки переключения месяцев
        setupTasksList()                 // 4) настраиваем список задач, клики, чекбоксы и удаление свайпом
        setupObservers()                 // 5) подписываемся на LiveData из ViewModel

        // Настраиваем круглую кнопку "+" в правом нижнем углу экрана
        val fab = findViewById<FloatingActionButton>(R.id.fabAddTask)
        fab.setOnClickListener {
            // Открываем AddTaskActivity и сразу передаём туда день, который сейчас выбран в календаре,
            // чтобы дата в форме создания подставилась сама
            val intent = Intent(this, AddTaskActivity::class.java).apply {
                putExtra("EXTRA_PREFILL_DATE", taskViewModel.selectedDate.value)
            }
            startActivity(intent)
        }

        // Кнопка переключения режима списка («Все задачи» <-> «По дню»)
        btnToggleAllTasks.setOnClickListener {
            taskViewModel.toggleShowAllTasks()
        }
    }

    // Настройка верхней панели: строка живого поиска и кнопки «Бэкап», «Сортировка», «Тема»
    private fun setupSearchAndControls() {
        val searchView = findViewById<SearchView>(R.id.searchViewTasks)

        // Вешаем слушатель ввода текста на SearchView.
        // Здесь используется анонимный объект (`object : ...`), потому что у слушателя сразу два метода
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Срабатывает, когда пользователь нажал кнопку «Поиск / Enter» на клавиатуре
            override fun onQueryTextSubmit(query: String?): Boolean {
                taskViewModel.setSearchQuery(query.orEmpty())
                // Убираем фокус со строки поиска, чтобы клавиатура спряталась и было видно найденные задачи
                searchView.clearFocus()
                return true
            }

            // Срабатывает на КАЖДУЮ введённую или стёртую букву — благодаря этому список фильтруется прямо на лету
            override fun onQueryTextChange(newText: String?): Boolean {
                taskViewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })

        // Вешаем клики на кнопки открытия диалогов
        findViewById<Button>(R.id.btnBackup).setOnClickListener {
            showBackupDialog()
        }

        findViewById<Button>(R.id.btnSort).setOnClickListener {
            showSortDialog()
        }

        findViewById<Button>(R.id.btnTheme).setOnClickListener {
            showThemeDialog()
        }
    }

    // Показывает диалог выбора действия с резервной копией (экспорт или импорт JSON)
    private fun showBackupDialog() {
        val actions = arrayOf(
            "Экспортировать задачи в JSON",
            "Импортировать задачи из JSON"
        )
        AlertDialog.Builder(this)
            .setTitle("Резервная копия")
            // setItems показывает простой список пунктов; `which` — номер нажатого пункта (0 или 1)
            .setItems(actions) { _, which ->
                when (which) {
                    // Запускаем окно сохранения с предложенным именем файла "tasks_backup.json"
                    0 -> exportTasksLauncher.launch("tasks_backup.json")
                    // Запускаем окно выбора файла (разрешаем json, txt и любые файлы, т.к. некоторые телефоны
                    // не всегда правильно определяют тип .json файлов)
                    1 -> importTasksLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
            // Кнопка «Отмена» с null вместо обработчика просто закрывает окно
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Показывает диалог с радиокнопками для выбора способа сортировки задач
    private fun showSortDialog() {
        val options = arrayOf(
            "По дате и времени",
            "По алфавиту (А–Я)",
            "Сначала невыполненные",
            "По приоритету (сначала важные)"
        )
        // Узнаём, какая сортировка выбрана сейчас в ViewModel, чтобы отметить нужную радиокнопку точкой
        val current = when (taskViewModel.sortOrder.value ?: SortOrder.BY_DATE) {
            SortOrder.BY_DATE -> 0
            SortOrder.BY_TITLE -> 1
            SortOrder.BY_STATUS -> 2
            SortOrder.BY_PRIORITY -> 3
        }

        AlertDialog.Builder(this)
            .setTitle("Сортировка задач")
            // setSingleChoiceItems рисует список с кружочками-радиокнопками; `current` — какой пункт отмечен сейчас
            .setSingleChoiceItems(options, current) { dialog, which ->
                // Переводим номер нажатого пункта обратно в значение нашего enum SortOrder
                val chosen = when (which) {
                    1 -> SortOrder.BY_TITLE
                    2 -> SortOrder.BY_STATUS
                    3 -> SortOrder.BY_PRIORITY
                    else -> SortOrder.BY_DATE
                }
                // Передаём новую сортировку в ViewModel (она сама переключит SQL-запрос в DAO)
                taskViewModel.setSortOrder(chosen)
                // Закрываем диалог сразу после выбора
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Показывает диалог переключения темы (системная / светлая / тёмная)
    private fun showThemeDialog() {
        val themes = arrayOf("Как в системе", "Светлая тема", "Тёмная тема")
        // Читаем из SharedPreferences, какой режим сохранён сейчас (0, 1 или 2)
        val currentMode = ThemePreferences.getSavedThemeMode(this)

        AlertDialog.Builder(this)
            .setTitle("Тема оформления")
            .setSingleChoiceItems(themes, currentMode) { dialog, which ->
                // Сохраняем выбор в SharedPreferences и мгновенно применяем тему ко всему приложению
                ThemePreferences.saveAndApplyTheme(this, which)
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // Запрашивает у пользователя разрешение на показ уведомлений, если на устройстве Android 13 или выше
    private fun requestNotificationPermissionIfNeeded() {
        // TIRAMISU — кодовое имя Android 13 (API 33). До этой версии уведомления разрешались автоматически
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Проверяем, не дал ли пользователь разрешение уже раньше
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            // Если разрешения пока нет — открываем системное окно с вопросом «Разрешить уведомления?»
            if (!granted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Выставляет отображаемый месяц календаря по дате, которая хранится в ViewModel.
    // Зачем это нужно: если пользователь пролистал календарь на другой месяц, выбрал там день и повернул телефон,
    // Activity пересоздастся, но благодаря этому методу календарь откроется на том же выбранном месяце, а не сбросится
    private fun initDisplayedMonthFromSelected() {
        val selected = taskViewModel.selectedDate.value.orEmpty()
        val parts = selected.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (y != null && m != null) {
                displayedYear = y
                displayedMonth = m
                return
            }
        }
        // Запасной вариант (если дата вдруг пустая): берём текущий год и месяц с часов телефона
        val cal = Calendar.getInstance()
        displayedYear = cal.get(Calendar.YEAR)
        // Прибавляем 1, потому что в Calendar январь = 0
        displayedMonth = cal.get(Calendar.MONTH) + 1
    }

    // Настройка календарной сетки и кнопок листания месяцев
    private fun setupCalendar() {
        val rvCalendar = findViewById<RecyclerView>(R.id.recyclerViewCalendar)
        // GridLayoutManager(this, 7) раскладывает ячейки сеткой ровно по 7 колонок (Пн–Вс)
        rvCalendar.layoutManager = GridLayoutManager(this, 7)
        // Отключаем собственную прокрутку у сетки, потому что весь главный экран уже обёрнут в NestedScrollView —
        // так прокрутка всей страницы целиком работает плавно и не конфликтует
        rvCalendar.isNestedScrollingEnabled = false

        // Создаём адаптер календаря и передаём ему действие при клике на день:
        // просто сообщаем ViewModel новую выбранную дату
        calendarAdapter = CalendarAdapter { clickedDate ->
            taskViewModel.selectDate(clickedDate)
        }
        rvCalendar.adapter = calendarAdapter

        // Кнопка «‹» (предыдущий месяц)
        findViewById<Button>(R.id.btnPrevMonth).setOnClickListener {
            if (displayedMonth == 1) {
                // Если был Январь — переходим на Декабрь прошлого года
                displayedMonth = 12
                displayedYear -= 1
            } else {
                displayedMonth -= 1
            }
            renderCalendar()
        }

        // Кнопка «›» (следующий месяц)
        findViewById<Button>(R.id.btnNextMonth).setOnClickListener {
            if (displayedMonth == 12) {
                // Если был Декабрь — переходим на Январь следующего года
                displayedMonth = 1
                displayedYear += 1
            } else {
                displayedMonth += 1
            }
            renderCalendar()
        }

        // Первый раз отрисовываем сетку текущего месяца
        renderCalendar()
    }

    // Собирает список ячеек для текущего `displayedMonth` и `displayedYear` и отдаёт его в CalendarAdapter
    private fun renderCalendar() {
        // Пишем заголовок вроде «Ноябрь 2026» (индекс массива = месяц - 1)
        tvMonthYear.text = "${monthNames[displayedMonth - 1]} $displayedYear"

        // Получаем сегодняшнюю дату строкой формата "yyyy-MM-dd", чтобы выделить сегодняшний день обводкой
        val todayCal = Calendar.getInstance()
        val todayStr = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            todayCal.get(Calendar.YEAR),
            todayCal.get(Calendar.MONTH) + 1,
            todayCal.get(Calendar.DAY_OF_MONTH)
        )
        // Узнаём, какой день сейчас выбран пользователем (если вдруг null — считаем выбранным сегодня)
        val selectedDateStr = taskViewModel.selectedDate.value ?: todayStr

        // Настраиваем вспомогательный Calendar на 1-е число отображаемого месяца
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth - 1) // в Calendar месяцы с 0
            set(Calendar.DAY_OF_MONTH, 1)
        }

        // Узнаём, сколько всего дней в этом месяце (28, 29, 30 или 31 — високосные годы Calendar считает сам)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Считаем, сколько пустых клеток нужно вставить перед 1-м числом, чтобы неделя начиналась с Понедельника.
        // В Java Calendar дни недели пронумерованы по-американски: Вс = 1, Пн = 2, Вт = 3, Ср = 4, Чт = 5, Пт = 6, Сб = 7.
        // Формула `(DAY_OF_WEEK + 5) % 7` переводит это в сдвиг от Понедельника:
        // Пн -> (2+5)%7 = 0 пустых клеток; Вт -> 1; Ср -> 2; ...; Сб -> 5; Вс -> (1+5)%7 = 6 пустых клеток
        val leadingEmptyCells = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        // Собираем изменяемый список всех ячеек сетки
        val days = mutableListOf<CalendarDay>()

        // Сначала кладём нужное количество пустых ячеек-заглушек (с date = null)
        repeat(leadingEmptyCells) {
            days.add(CalendarDay(date = null))
        }

        // Затем циклом от 1 до последнего числа месяца добавляем все настоящие дни
        for (day in 1..daysInMonth) {
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", displayedYear, displayedMonth, day)
            days.add(
                CalendarDay(
                    date = dateStr,
                    dayNumber = day,
                    isToday = dateStr == todayStr,
                    isSelected = dateStr == selectedDateStr,
                    // Проверяем в нашем Set, есть ли в базе задачи на эту дату (для зелёной точки под числом)
                    hasTasks = datesWithTasksSet.contains(dateStr)
                )
            )
        }

        // Передаём готовый список клеток в адаптер, чтобы сетка перерисовалась
        calendarAdapter.updateDays(days)
    }

    // Настройка списка карточек задач под календарём: клик по карточке, галочка и свайп-удаление
    private fun setupTasksList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTasks)
        // Обычный вертикальный список карточек
        recyclerView.layoutManager = LinearLayoutManager(this)
        // Тоже отключаем внутренний скролл, чтобы список плавно крутился вместе с календарём внутри NestedScrollView
        recyclerView.isNestedScrollingEnabled = false

        // Создаём адаптер ОДИН раз и передаём ему два обработчика: клик по задаче и переключение галочки
        tasksAdapter = TasksAdapter(
            onTaskClick = { selectedTask ->
                // По клику на карточку открываем экран подробностей DetailActivity и передаём туда все поля задачи
                val intent = Intent(this, DetailActivity::class.java).apply {
                    putExtra("EXTRA_ID", selectedTask.id)
                    putExtra("EXTRA_TITLE", selectedTask.title)
                    putExtra("EXTRA_DATE", selectedTask.date)
                    putExtra("EXTRA_TIME", selectedTask.time)
                    putExtra("EXTRA_DESCRIPTION", selectedTask.description)
                    putExtra("EXTRA_IS_DONE", selectedTask.isDone)
                    putExtra("EXTRA_CATEGORY", selectedTask.category)
                    putExtra("EXTRA_PRIORITY", selectedTask.priority)
                }
                startActivity(intent)
            },
            onTaskCheckedChange = { task, isChecked ->
                // Когда нажали на CheckBox — делаем копию задачи с новым isDone и отдаём в ViewModel.
                // ViewModel обновит запись в Room, отменит/вернёт будильник и обновит виджет
                taskViewModel.updateTask(task.copy(isDone = isChecked))
            }
        )
        recyclerView.adapter = tasksAdapter

        // Настраиваем жест удаления свайпом (смахивание карточки влево или вправо).
        // Первый параметр `0` — отключаем перетаскивание вверх-вниз (drag & drop);
        // второй параметр — разрешаем смахивать карточку и влево (LEFT), и вправо (RIGHT)
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            // Перетаскивание вверх-вниз мы не используем, поэтому просто возвращаем false
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            // Срабатывает в момент, когда пользователь до конца смахнул карточку в сторону
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Узнаём номер смахнутой карточки в списке
                val position = viewHolder.adapterPosition
                // Защита от редкого случая, когда карточка уже успела исчезнуть из списка
                if (position == RecyclerView.NO_POSITION) return

                // Достаём саму задачу, которую хотят удалить
                val taskToDelete = tasksAdapter.getTaskAt(position)

                // Не удаляем сразу молча — сначала спрашиваем подтверждение через AlertDialog.
                // Важно: внутри анонимного объекта слово `this` указывает на сам Callback,
                // поэтому для контекста экрана пишем `this@MainActivity`
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Удалить задачу?")
                    .setMessage("Задача «${taskToDelete.title}» будет удалена без возможности восстановления.")
                    .setPositiveButton("Удалить") { _, _ ->
                        // Подтвердили — удаляем из Room, отменяем будильник и обновляем виджет через ViewModel
                        taskViewModel.deleteTask(taskToDelete)
                    }
                    .setNegativeButton("Отмена") { _, _ ->
                        // Нажали «Отмена» — говорим адаптеру перерисовать эту позицию, чтобы смахнутая карточка уехала обратно на место
                        tasksAdapter.notifyItemChanged(position)
                    }
                    .setOnCancelListener {
                        // Если пользователь не нажал «Отмена», а просто ткнул мимо диалога или нажал «Назад» —
                        // тоже обязательно возвращаем карточку обратно, иначе в списке останется пустая дыра
                        tasksAdapter.notifyItemChanged(position)
                    }
                    .show()
            }
        }

        // Прикрепляем наш обработчик свайпов к списку задач
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    // Подписка на все «наблюдаемые» данные (LiveData) из TaskViewModel.
    // Суть observe(this): пока экран жив, как только данные в базе или в ViewModel поменялись,
    // код внутри фигурных скобок сам автоматически выполнится и обновит интерфейс
    private fun setupObservers() {
        // 1) Подписка на список дат, на которых есть задачи — как только добавили/удалили задачу,
        //    нормализуем даты, кладём в Set и перерисовываем календарь (точки обновятся сами)
        taskViewModel.datesWithTasks.observe(this) { rawDates ->
            datesWithTasksSet = rawDates.map { normalizeDate(it) }.toSet()
            renderCalendar()
        }

        // 2) Подписка на выбранную дату — перерисовываем синий кружок в календаре и заголовок «Задачи на ...»
        taskViewModel.selectedDate.observe(this) {
            renderCalendar()
            updateHeader()
        }

        // 3) Подписка на переключатель «Все задачи / По дню» — обновляем заголовок и текст кнопки
        taskViewModel.showAllTasks.observe(this) {
            updateHeader()
        }

        // 4) Подписка на строку поиска — обновляем заголовок («Поиск: «...»») и текст пустого состояния
        taskViewModel.searchQuery.observe(this) {
            updateHeader()
        }

        // 5) Главная подписка на итоговый отфильтрованный и отсортированный список задач:
        //    отдаём его в адаптер, а если список пуст — показываем понятную заглушку tvEmptyDay
        taskViewModel.displayedTasks.observe(this) { tasks ->
            tasksAdapter.updateTasks(tasks)
            tvEmptyDay.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // Обновляет заголовок над списком задач, подпись на кнопке «Все задачи / По дню» и текст пустого экрана
    // в зависимости от того, в каком режиме сейчас находится пользователь
    private fun updateHeader() {
        val query = taskViewModel.searchQuery.value.orEmpty().trim()
        val showAll = taskViewModel.showAllTasks.value ?: false
        val selectedDate = taskViewModel.selectedDate.value.orEmpty()

        when {
            // Если пользователь что-то ввёл в строку поиска — поиск главнее остальных режимов
            query.isNotEmpty() -> {
                tvTasksHeader.text = "Поиск: «$query»"
                btnToggleAllTasks.text = if (showAll) "По дню" else "Все задачи"
                tvEmptyDay.text = "По запросу «$query» ничего не найдено."
            }
            // Режим «Все задачи» (показываем задачи за все дни подряд)
            showAll -> {
                tvTasksHeader.text = "Все задачи"
                btnToggleAllTasks.text = "По дню"
                tvEmptyDay.text = "Список задач пока пуст.\nНажмите +, чтобы добавить задачу."
            }
            // Обычный режим — показываем задачи только за выбранный день в календаре
            else -> {
                tvTasksHeader.text = "Задачи на $selectedDate"
                btnToggleAllTasks.text = "Все задачи"
                tvEmptyDay.text = "На этот день задач нет.\nНажмите +, чтобы добавить задачу."
            }
        }
    }

    // Вспомогательная функция: на всякий случай приводит даты вроде "2026-5-3" к виду "2026-05-03",
    // чтобы старые или импортированные записи точно совпадали с датами ячеек календаря
    private fun normalizeDate(raw: String): String {
        val parts = raw.split("-")
        if (parts.size != 3) return raw
        val y = parts[0].toIntOrNull() ?: return raw
        val m = parts[1].toIntOrNull() ?: return raw
        val d = parts[2].toIntOrNull() ?: return raw
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }
}
// Адрес (пакет) моего проекта. По нему Android понимает, к какому приложению относится файл,
// и все классы внутри папки ru.university.calendar видят друг друга без лишних импортов
package ru.university.calendar

// Готовые системные окошки Android: календарик для выбора даты и часики для выбора времени.
// Самому их верстать не нужно — система сама откроет стандартные диалоги в стиле телефона
import android.app.DatePickerDialog
import android.app.TimePickerDialog

// Bundle — это как словарь «ключ -> значение», куда Android сохраняет временные данные экрана,
// если систему заставили пересоздать Activity (например, при повороте телефона)
import android.os.Bundle

// Обычные стандартные элементы интерфейса из Android:
// ArrayAdapter — «переводчик», который умеет передать обычный список строк в выпадающий список;
// Button — простая кнопка;
// Spinner — сам выпадающий список
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner

// Подключает удобную запись `by viewModels()` из библиотеки AndroidX
import androidx.activity.viewModels

// Современный базовый класс для экранов (Activity). В отличие от старого обычного Activity,
// он нормально дружит с Material Design и переключением светлой/тёмной темы
import androidx.appcompat.app.AppCompatActivity

// Два компонента из библиотеки Material Design:
// TextInputEditText — само поле, куда пользователь вводит текст;
// TextInputLayout — внешняя рамка вокруг него, которая красиво поднимает подсказку вверх и рисует красные ошибки
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// Стандартный класс для работы с датой и временем (знает текущий год, месяц, день, час и минуту)
import java.util.Calendar

// Класс региональных настроек — нужен для String.format, чтобы цифры в дате и времени
// всегда писались обычными 0-9 независимо от языка телефона
import java.util.Locale

// Экран для создания новой задачи И редактирования уже существующей (в MVVM это слой View).
// Я специально сделал одну Activity сразу на два режима, чтобы не плодить два почти одинаковых файла:
// 1) если экран открыли по кнопке "+" (ID не передали, значит он 0) — создаём новую задачу;
// 2) если перешли с экрана деталей по кнопке "Редактировать" (передали реальный ID) —
//    заполняем поля старыми данными, а при сохранении обновляем запись в базе.
// Двоеточие `:` означает наследование от AppCompatActivity, а скобки `()` вызывают его конструктор.
class AddTaskActivity : AppCompatActivity() {

    // Получаем нашу TaskViewModel через специальную запись `by viewModels()`.
    // Почему нельзя просто написать `= TaskViewModel(application)`?
    // 1) Если создавать руками, то при повороте телефона Activity пересоздастся и создаст новую
    //    пустую ViewModel, а старая пропадёт. А `by viewModels()` прячет её внутри Android (ViewModelProvider),
    //    поэтому она спокойно переживает повороты экрана.
    // 2) Создаётся она не сразу, а только в момент первого обращения.
    // 3) По правилам MVVM экран напрямую в базу Room не лезет — все команды идут только через ViewModel.
    // `private` — поле видно только в этом классе, `val` — ссылку нельзя переприсвоить (как final в Java).
    private val taskViewModel: TaskViewModel by viewModels()

    // Фиксированный список категорий для выпадающего списка.
    // `listOf(...)` создаёт список только для чтения — случайно добавить или удалить оттуда что-то в работе не получится
    private val categories = listOf("Личное", "Учёба", "Работа", "Здоровье", "Покупки", "Другое")

    // Варианты приоритета для второго выпадающего списка.
    // Тут важный момент: их номера в списке (0, 1, 2) один в один совпадают с числами в поле Task.priority в базе:
    // 0 — Низкий, 1 — Средний, 2 — Высокий.
    // В базе храню именно числом, а не словом, чтобы в SQL было легко сортировать:
    // `ORDER BY priority DESC` сразу ставит важные задачи (2) выше средних (1) и низких (0).
    // Если бы там были слова, сортировка по алфавиту поставила бы «Высокий», потом «Низкий», потом «Средний» — было бы криво.
    private val priorities = listOf("Низкий", "Средний", "Высокий") // индексы 0, 1, 2

    // Главный метод жизненного цикла Activity — Android запускает его самым первым, когда открывает этот экран.
    // `override` — переопределяем готовый метод из родительского AppCompatActivity.
    // `savedInstanceState: Bundle?` — знак вопроса `?` означает, что сюда может прийти null
    // (при обычном первом открытии экрана там действительно null, а после поворота — сохранённое состояние).
    override fun onCreate(savedInstanceState: Bundle?) {
        // Первой строкой обязательно вызываем родительский onCreate — там Android настраивает окно и тему.
        // Если забыть эту строчку, приложение сразу упадёт при запуске экрана.
        super.onCreate(savedInstanceState)

        // Подключаем к этому классу его XML-разметку из res/layout/activity_add_task.xml.
        // `R` — это автоматически созданный каталог всех ресурсов проекта (разметок, id, цветов, строк)
        setContentView(R.layout.activity_add_task)

        // --- 1. Находим все элементы интерфейса из XML ---
        // findViewById<Тип>(R.id.имя) находит нужный элемент в загруженной разметке по его android:id.
        // Зачем нужны и TextInputLayout, и TextInputEditText?
        // - TextInputLayout — это внешняя рамка от Material Design, только у неё есть `.error`,
        //   чтобы подсветить поле красным и написать текст ошибки прямо под ним.
        // - TextInputEditText — само текстовое поле внутри, откуда мы читаем текст пользователя и куда пишем данные.
        val layoutTitle = findViewById<TextInputLayout>(R.id.textInputLayoutTitle)
        val layoutDate = findViewById<TextInputLayout>(R.id.textInputLayoutDate)
        val editTitle = findViewById<TextInputEditText>(R.id.editTextTitle)
        val editDate = findViewById<TextInputEditText>(R.id.editTextDate)
        val editTime = findViewById<TextInputEditText>(R.id.editTextTime)

        // Выпадающие списки для категории и приоритета
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerPriority = findViewById<Spinner>(R.id.spinnerPriority)

        // Поле для необязательного описания и кнопка сохранения внизу экрана
        val editDescription = findViewById<TextInputEditText>(R.id.editTextDescription)
        val buttonSave = findViewById<Button>(R.id.buttonSave)

        // --- 2. Заполняем выпадающие списки (Spinner) ---
        // Сам Spinner не умеет напрямую работать со списком строк, ему нужен посредник — ArrayAdapter.
        // Передаём в него три вещи:
        // 1) `this` — текущий экран (контекст, чтобы адаптер мог создавать элементы интерфейса);
        // 2) `android.R.layout.simple_spinner_item` — через `android.R` берём готовый встроенный шаблон Android
        //    для свёрнутого состояния выпадающего списка;
        // 3) `categories` — сам список строк.
        // Блок `.apply { ... }` в Kotlin позволяет сразу донастроить только что созданный адаптер
        // и тут же положить его в `spinnerCategory.adapter`, не заводя лишнюю временную переменную.
        spinnerCategory.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        ).apply {
            // Задаём встроенный системный вид для строчек внутри раскрытого списка (с нормальными отступами под палец)
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Точно так же подключаем список приоритетов ко второму выпадающему списку
        spinnerPriority.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            priorities
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // --- 3. Определяем режим: создаём новую задачу или редактируем старую ---
        // `intent` — это данные, с которыми этот экран запустили (с главного экрана, с деталей или с виджета).
        // Достаём ID задачи по ключу "EXTRA_ID". Если такого ключа нет, вернётся значение по умолчанию — 0.
        val taskId = intent.getIntExtra("EXTRA_ID", 0)

        // В базе Room автогенерация ID начинается с 1, поэтому если taskId != 0 —
        // мы точно открыли уже существующую задачу на редактирование.
        val isEditMode = taskId != 0

        // Обязательно запоминаем, была ли задача уже отмечена выполненной (по умолчанию false).
        // Если этого не сохранить, то при редактировании текста или времени у выполненной задачи
        // случайно слетала бы галочка обратно в false.
        val existingIsDone = intent.getBooleanExtra("EXTRA_IS_DONE", false)

        if (isEditMode) {
            // === РЕЖИМ РЕДАКТИРОВАНИЯ: подставляем в поля старые данные, пришедшие из DetailActivity ===
            editTitle.setText(intent.getStringExtra("EXTRA_TITLE"))

            // `getStringExtra` может вернуть null, поэтому `.orEmpty()` безопасно превращает null в пустую строку "",
            // а `normalizeDate(...)` на всякий случай приводит дату к строгому виду год-месяц-день (yyyy-MM-dd)
            editDate.setText(normalizeDate(intent.getStringExtra("EXTRA_DATE").orEmpty()))
            editTime.setText(intent.getStringExtra("EXTRA_TIME"))
            editDescription.setText(intent.getStringExtra("EXTRA_DESCRIPTION"))

            // Достаём категорию. Запись `?:` работает как запасной вариант:
            // возьми то, что слева, а если там вдруг null — подставь "Личное"
            val existingCategory = intent.getStringExtra("EXTRA_CATEGORY") ?: "Личное"

            // Ищем, под каким номером эта категория лежит в нашем списке `categories`.
            // Если вдруг пришла какая-то неизвестная категория (например, из старого бэкапа), `indexOf` вернёт -1.
            // Чтобы Spinner от этого не упал, `.coerceAtLeast(0)` превратит любое число меньше 0 в 0.
            val catIndex = categories.indexOf(existingCategory).coerceAtLeast(0)
            // Программно выбираем нужный пункт в выпадающем списке
            spinnerCategory.setSelection(catIndex)

            // Достаём приоритет (по умолчанию 1 — Средний).
            // `.coerceIn(0, 2)` на всякий случай ограничивает число от 0 до 2, чтобы не выйти за границы списка
            val existingPriority = intent.getIntExtra("EXTRA_PRIORITY", 1).coerceIn(0, 2)
            spinnerPriority.setSelection(existingPriority)

            // Меняем текст на кнопке, чтобы пользователь видел, что обновляет старую задачу, а не создаёт новую
            buttonSave.text = "Обновить задачу"
        } else {
            // === РЕЖИМ СОЗДАНИЯ НОВОЙ ЗАДАЧИ ===
            // По умолчанию сразу выбираем позицию 1 ("Средний"), чтобы чаще всего не приходилось менять руками
            spinnerPriority.setSelection(1) // По умолчанию "Средний"

            // Проверяем, прислал ли календарь или виджет дату, на которую нажал пользователь
            val prefillDate = intent.getStringExtra("EXTRA_PREFILL_DATE")

            // `isNullOrBlank()` сразу проверяет три вещи: что строка не null, не пустая "" и не из одних пробелов
            if (!prefillDate.isNullOrBlank()) {
                // Если дата пришла с календаря — сразу вписываем её в поле, чтобы не выбирать заново
                editDate.setText(prefillDate)
            } else {
                // Если открыли без выбранной даты — берём сегодняшнее число с часов телефона через Calendar
                val today = Calendar.getInstance()

                // Собираем дату строго в формате ГГГГ-ММ-ДД (например, "2026-11-23"):
                // %04d — 4 цифры с нулями спереди, %02d — 2 цифры (чтобы 5-е число стало "05", а не "5").
                // Locale.US нужен, чтобы всегда были обычные цифры 0-9.
                // Важная особенность java.util.Calendar: месяцы там считаются с нуля (Январь = 0, Декабрь = 11),
                // поэтому когда переводим месяц в нормальный вид, всегда прибавляем 1!
                editDate.setText(
                    String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        today.get(Calendar.YEAR),
                        today.get(Calendar.MONTH) + 1,
                        today.get(Calendar.DAY_OF_MONTH)
                    )
                )
            }
        }

        // --- 4. Нажатие на поле даты (открываем системный календарик DatePickerDialog) ---
        // В XML у editDate стоит `focusable="false"` и `inputType="none"`, поэтому при нажатии
        // не вылезает обычная клавиатура, а сразу срабатывает этот обработчик.
        // Вместо старого джавовского `object : View.OnClickListener { ... }` в Kotlin пишем просто блок `{ ... }`.
        editDate.setOnClickListener {
            // Берём текущую дату как запасной вариант
            val calendar = Calendar.getInstance()

            // Пробуем разобрать дату, которая уже написана в поле (например, "2026-11-25"),
            // чтобы календарик открылся именно на 25 ноября, а не сбрасывался каждый раз на «сегодня».
            // `split("-")` делит строку по дефисам на список ["2026", "11", "25"].
            val parts = editDate.text.toString().split("-")
            if (parts.size == 3) {
                // `toIntOrNull()` безопасно переводит текст в число (если там не число — вернёт null без ошибки)
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null) {
                    // Когда передаём месяц обратно в Calendar, отнимаем 1 (из 1..12 обратно в 0..11)
                    calendar.set(y, m - 1, d)
                }
            }

            // Создаём и сразу показываем (`.show()`) стандартный андроидовский DatePickerDialog.
            // Что передаём:
            // 1) `this` — текущий экран;
            // 2) `{ _, year, month, day -> ... }` — код, который выполнится после нажатия «ОК»
            //    (знак `_` на первом месте значит, что сам объект календарика нам не нужен);
            // 3, 4, 5) год, месяц (0..11) и день, которые будут выбраны при открытии диалога.
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    // Снова делаем `month + 1` и сохраняем дату в едином формате yyyy-MM-dd
                    val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    editDate.setText(formatted)
                    // Если до этого под полем даты горела красная ошибка валидации — сразу убираем её
                    layoutDate.error = null
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // --- 5. Нажатие на поле времени (открываем системные часики TimePickerDialog) ---
        editTime.setOnClickListener {
            val calendar = Calendar.getInstance()

            // Если время уже было указано (например, "14:30"), делим его по двоеточию, чтобы часы открылись на 14:30
            val parts = editTime.text.toString().split(":")

            // Безопасная цепочка, которая не упадёт даже на пустом поле или кривом тексте:
            // `getOrNull(0)` — достаёт первый элемент или возвращает null (вместо ошибки выхода за границы списка);
            // `?.` — вызывает `toIntOrNull()` только если слева не null;
            // `?:` — если на любом шаге получился null, подставляет текущие часы/минуты телефона.
            // Берём именно `HOUR_OF_DAY` (0..23), а не `HOUR` (0..11), чтобы не перепутать день и ночь.
            val initHour = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY)
            val initMinute = parts.getOrNull(1)?.toIntOrNull() ?: calendar.get(Calendar.MINUTE)

            // Открываем TimePickerDialog.
            // Последний параметр `true` включает наш привычный 24-часовой формат вместо AM/PM
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    // Сохраняем время всегда по две цифры: "09:05", а не "9:5" — и красиво, и правильно сортируется в базе
                    editTime.setText(String.format(Locale.US, "%02d:%02d", hour, minute))
                },
                initHour,
                initMinute,
                true
            ).show()
        }

        // --- 6. Проверка полей и сохранение задачи по нажатию кнопки ---
        buttonSave.setOnClickListener {
            // Сначала очищаем старые ошибки, чтобы они не продолжали висеть после того, как всё исправили
            layoutTitle.error = null
            layoutDate.error = null

            // Забираем всё, что ввёл пользователь.
            // `.trim()` убирает случайные пробелы по краям (чтобы нельзя было сохранить задачу из одних пробелов)
            val title = editTitle.text.toString().trim()
            val date = normalizeDate(editDate.text.toString().trim())
            val time = editTime.text.toString().trim()
            val description = editDescription.text.toString().trim()

            // Из первого спиннера забираем текст выбранной категории ("Личное", "Учёба" и т.д.)
            val category = spinnerCategory.selectedItem.toString()
            // Из второго спиннера забираем именно номер выбранной строчки (0, 1 или 2)
            val priority = spinnerPriority.selectedItemPosition

            // Флаг наличия ошибок. Здесь `var` (изменяемая переменная), потому что ниже её значение может поменяться на true
            var hasError = false

            // Проверка 1: без названия сохранять задачу нельзя — подсвечиваем поле красным
            if (title.isEmpty()) {
                layoutTitle.error = "Название задачи обязательно!"
                hasError = true
            }

            // Проверка 2: сверяем дату по шаблону (Regex), что она точно выглядит как "4 цифры - 2 цифры - 2 цифры".
            // Как читается шаблон: `^` — начало строки, `\d{4}` — 4 цифры года, `-` — дефис,
            // `\d{2}` — 2 цифры месяца, `-` — дефис, `\d{2}` — 2 цифры дня, `$` — конец строки.
            // Два слэша `\\d` пишутся потому, что в обычных кавычках один обратный слэш — служебный символ.
            if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) {
                layoutDate.error = "Выберите корректную дату"
                hasError = true
            }

            // Если нашлась хоть одна ошибка — не сохраняем и выходим из обработчика кнопки.
            // Пишем именно `return@setOnClickListener`, а не просто `return`, чтобы выйти только из нажатия кнопки,
            // а не пытаться завершить весь метод onCreate.
            if (hasError) return@setOnClickListener

            // Собираем объект задачи Task (пишем имена полей явно, чтобы точно ничего не перепутать).
            // Главное отличие двух режимов:
            // - при редактировании передаём старый `taskId` -> Room найдёт эту строку в таблице и обновит её,
            //   и оставляем старый `existingIsDone`, чтобы не слетела галочка;
            // - при создании ставим `id = 0` -> Room поймёт, что задача новая, и сам выдаст ей новый ID.
            val task = Task(
                id = if (isEditMode) taskId else 0,
                title = title,
                description = description,
                date = date,
                time = time,
                isDone = if (isEditMode) existingIsDone else false,
                category = category,
                priority = priority
            )

            // Отправляем задачу в ViewModel. Внутри неё в фоновом потоке (Dispatchers.IO, чтобы экран не подвисал)
            // автоматически произойдут сразу три вещи:
            // 1) запись или обновление в локальной базе Room (SQLite);
            // 2) постановка или перенос напоминания в AlarmManager (если указано время);
            // 3) обновление виджета на рабочем столе телефона.
            if (isEditMode) {
                taskViewModel.updateTask(task)
            } else {
                taskViewModel.insertTask(task)
            }

            // Закрываем этот экран и возвращаемся назад на MainActivity.
            // Обновлять список руками в MainActivity не нужно — там висит подписка observe() на LiveData,
            // так что база сама уведомит интерфейс, и задача с точкой в календаре появятся автоматически.
            finish()
        }
    }

    // Вспомогательная функция, которая чинит формат даты: например, из "2026-5-3" делает "2026-05-03".
    // Зачем это вообще важно (частый вопрос про хранение даты в базе):
    // В SQLite даты хранятся текстом. Когда формат везде единый `yyyy-MM-dd` с нулями спереди:
    // 1) Календарь всегда точно находит задачи нужного дня через `WHERE date = :selectedDate`.
    // 2) Обычная текстовая сортировка `ORDER BY date ASC` автоматически работает правильно по календарю!
    //    Без нулей "2026-10-01" встало бы раньше, чем "2026-2-01" (потому что символ '1' идёт раньше '2').
    private fun normalizeDate(raw: String): String {
        // Делим строку по дефисам
        val parts = raw.split("-")
        // Если получилось не 3 части — это явно не дата, возвращаем как есть (дальше её забракует проверка Regex)
        if (parts.size != 3) return raw

        // Переводим год, месяц и день в числа; если хоть что-то оказалось не числом — сразу выходим через `?: return raw`
        val year = parts[0].toIntOrNull() ?: return raw
        val month = parts[1].toIntOrNull() ?: return raw
        val day = parts[2].toIntOrNull() ?: return raw

        // Склеиваем обратно в правильную строку с нулями впереди и возвращаем результат
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }
}
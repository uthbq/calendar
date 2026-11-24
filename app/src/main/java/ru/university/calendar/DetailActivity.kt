// Адрес (пакет) моего приложения
package ru.university.calendar

// Intent нужен, чтобы открыть экран редактирования AddTaskActivity и передать туда все поля задачи
import android.content.Intent

// Bundle — стандартный параметр метода onCreate для восстановления состояния экрана
import android.os.Bundle

// Обычные элементы интерфейса: кнопка и текстовое поле
import android.widget.Button
import android.widget.TextView

// Базовый класс экрана AndroidX с поддержкой Material Design и тёмной темы
import androidx.appcompat.app.AppCompatActivity

// Экран подробного просмотра выбранной задачи (открывается по клику на карточку в MainActivity).
// Здесь пользователь видит полное описание, дату, время, категорию, приоритет
// и может нажать кнопку «Редактировать», чтобы перейти в форму AddTaskActivity.
// Наследуемся от AppCompatActivity — базового современного экрана в Android.
class DetailActivity : AppCompatActivity() {

    // Главный метод жизненного цикла Activity — запускается системой при открытии экрана.
    // `savedInstanceState: Bundle?` может быть null (знак ?), если экран открыт первый раз
    override fun onCreate(savedInstanceState: Bundle?) {
        // Обязательный вызов родительского onCreate — без него экран упадёт при запуске
        super.onCreate(savedInstanceState)
        // Подключаем XML-вёрстку экрана деталей (res/layout/activity_detail.xml)
        setContentView(R.layout.activity_detail)

        // --- 1. Находим все элементы интерфейса из XML по их android:id ---
        val tvTitle = findViewById<TextView>(R.id.tvDetailTitle)       // Заголовок задачи
        val tvDate = findViewById<TextView>(R.id.tvDetailDate)         // Строка с датой
        val tvTime = findViewById<TextView>(R.id.tvDetailTime)         // Строка со временем
        val tvMeta = findViewById<TextView>(R.id.tvDetailMeta)         // Категория и приоритет
        val tvDescription = findViewById<TextView>(R.id.tvDetailDescription) // Подробное описание
        val buttonEdit = findViewById<Button>(R.id.buttonEditTask)     // Кнопка «Редактировать»

        // --- 2. Вытаскиваем все данные задачи из Intent, с которым нас открыла MainActivity ---
        // Для чисел и булевых значений вторым аргументом задаём значение по умолчанию (если ключ не найдётся).
        // Для строк `getStringExtra` может вернуть null, поэтому через `?:` подставляем запасной текст
        val taskId = intent.getIntExtra("EXTRA_ID", 0)
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Без названия"
        val date = intent.getStringExtra("EXTRA_DATE") ?: ""
        val time = intent.getStringExtra("EXTRA_TIME") ?: ""
        val description = intent.getStringExtra("EXTRA_DESCRIPTION") ?: ""

        // Галочку выполнения `isDone` на этом экране мы даже не показываем, но ОБЯЗАТЕЛЬНО забираем её из Intent,
        // чтобы передать дальше в AddTaskActivity при нажатии «Редактировать» — иначе статус выполнения потеряется
        val isDone = intent.getBooleanExtra("EXTRA_IS_DONE", false)

        val category = intent.getStringExtra("EXTRA_CATEGORY") ?: "Личное"
        val priority = intent.getIntExtra("EXTRA_PRIORITY", 1)

        // В базе приоритет хранится числом (0, 1 или 2), поэтому через `when` переводим его обратно
        // в понятное человеку слово для отображения на экране
        val priorityLabel = when (priority) {
            2 -> "Высокий"
            0 -> "Низкий"
            else -> "Средний"
        }

        // --- 3. Заполняем текстовые поля на экране ---
        // В Kotlin можно вставлять переменные прямо внутрь строки через знак `$` (например, `"Дата: $date"`).
        // Проверяем `isNotBlank()` (строка не пустая и не из одних пробелов): если данных нет,
        // пишем аккуратную заглушку вроде «не указано» или «Нет описания», чтобы на экране не было пустых обрывков
        tvTitle.text = title
        tvDate.text = if (date.isNotBlank()) "Дата: $date" else "Дата: не указана"
        tvTime.text = if (time.isNotBlank()) "Время: $time" else "Время: не указано"
        tvMeta.text = "Категория: $category • Приоритет: $priorityLabel"
        tvDescription.text = if (description.isNotBlank()) description else "Нет описания"

        // --- 4. Обработчик нажатия на кнопку «Редактировать» ---
        buttonEdit.setOnClickListener {
            // Создаём Intent для перехода с этого экрана (`this`) на форму AddTaskActivity.
            // Внутри блока `.apply { ... }` складываем в Intent все поля задачи через putExtra(),
            // чтобы AddTaskActivity увидела ненулевой EXTRA_ID, включила режим редактирования и заполнила все поля
            val editIntent = Intent(this, AddTaskActivity::class.java).apply {
                putExtra("EXTRA_ID", taskId)
                putExtra("EXTRA_TITLE", title)
                putExtra("EXTRA_DATE", date)
                putExtra("EXTRA_TIME", time)
                putExtra("EXTRA_DESCRIPTION", description)
                putExtra("EXTRA_IS_DONE", isDone)
                putExtra("EXTRA_CATEGORY", category)
                putExtra("EXTRA_PRIORITY", priority)
            }
            // Запускаем экран редактирования
            startActivity(editIntent)

            // Сразу закрываем DetailActivity!
            // Зачем тут finish(): на этом экране данные пришли один раз через Intent и сами не обновляются.
            // Если его не закрыть, то после сохранения правок в AddTaskActivity пользователь вернулся бы сюда
            // и увидел бы старый неисправленный текст. А с finish() он сразу возвращается на MainActivity,
            // где список обновляется автоматически через LiveData.
            finish()
        }
    }
}
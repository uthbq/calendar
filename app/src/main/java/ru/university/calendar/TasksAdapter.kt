// Адрес (пакет) моего приложения
package ru.university.calendar

// Color нужен, чтобы перевести HEX-коды цветов ("#E53935" и т.д.) в числа-цвета для полоски приоритета
import android.graphics.Color
// Paint хранит настройки отрисовки текста — отсюда берём флаг зачёркивания (STRIKE_THRU_TEXT_FLAG) для выполненных задач
import android.graphics.Paint
// LayoutInflater превращает наш XML-макет одной карточки (item_task.xml) в реальный объект View
import android.view.LayoutInflater
// Базовые классы всех элементов экрана и их контейнеров
import android.view.View
import android.view.ViewGroup
// Стандартные элементы внутри карточки: галочка выполнения и текстовые поля
import android.widget.CheckBox
import android.widget.TextView
// Современный компонент списка, который переиспользует карточки при прокрутке ради экономии памяти
import androidx.recyclerview.widget.RecyclerView

// Адаптер для списка карточек задач на главном экране (связывает список объектов Task с XML-макетом item_task.xml).
//
// В конструктор передаём две функции-обработчика из MainActivity (такой подход позволяет не смешивать
// код адаптера с ViewModel и переходом на другие экраны):
// 1) `onTaskClick: (Task) -> Unit` — сработает, когда пользователь нажмёт на всю карточку (откроет DetailActivity);
// 2) `onTaskCheckedChange: (Task, Boolean) -> Unit` — сработает при нажатии на CheckBox (передаст задачу и новое состояние галочки).
//
// Наследуемся от RecyclerView.Adapter и указываем наш внутренний класс TaskViewHolder.
class TasksAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onTaskCheckedChange: (Task, Boolean) -> Unit
) : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {

    // Текущий список задач, который сейчас отображается на экране (`var` — меняется при каждом обновлении LiveData).
    // Изначально пустой список `emptyList()`, чтобы не было null
    private var tasks: List<Task> = emptyList()

    // Обновляет список задач в адаптере без пересоздания самого адаптера.
    // Вызывается из MainActivity каждый раз, когда LiveData присылает новый список (выбрали день, ввели поиск, добавили задачу)
    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        // Сообщаем RecyclerView, что данные поменялись и нужно перерисовать видимые карточки
        notifyDataSetChanged()
    }

    // Отдаёт задачу по её номеру позиции в списке.
    // Используется в MainActivity внутри обработчика свайпа (ItemTouchHelper), чтобы узнать, какую именно задачу смахнул пользователь
    fun getTaskAt(position: Int): Task = tasks[position]

    // Внутренний класс ViewHolder — хранит ссылки на все элементы внутри ОДНОЙ карточки задачи.
    // Зачем он нужен (любимый вопрос на защите):
    // Если бы мы вызывали `findViewById` каждый раз при прокрутке на каждую карточку, список тормозил бы.
    // ViewHolder находит все 5 элементов ровно один раз при создании карточки и потом просто переиспользует готовые ссылки.
    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val priorityIndicator: View = itemView.findViewById(R.id.viewPriorityIndicator) // Цветная вертикальная полоска слева
        val titleTextView: TextView = itemView.findViewById(R.id.textViewTitle)         // Название задачи
        val dateTextView: TextView = itemView.findViewById(R.id.textViewDate)           // Дата (и время, если есть)
        val metaTextView: TextView = itemView.findViewById(R.id.textViewMeta)           // Категория и приоритет
        val statusCheckBox: CheckBox = itemView.findViewById(R.id.checkBoxDone)         // Галочка «Выполнено»
    }

    // Вызывается системой, когда RecyclerView нужно создать с нуля новую пустую карточку
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        // Загружаем XML-разметку одной карточки из res/layout/item_task.xml.
        // Третий параметр `false` значит «не прикрепляй карточку к списку прямо сейчас» — RecyclerView сделает это сам
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    // Вызывается для каждой видимой карточки по её номеру `position` и заполняет её реальными данными из объекта Task
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        // Берём нужную задачу из списка
        val task = tasks[position]

        // Ставим заголовок задачи
        holder.titleTextView.text = task.title

        // Если у задачи указано время (`isNotBlank()` — не пустое и не пробелы), показываем «дата • время»,
        // а если времени нет — показываем просто дату, чтобы не висела лишняя точка в конце
        holder.dateTextView.text = if (task.time.isNotBlank()) {
            "${task.date} • ${task.time}"
        } else {
            task.date
        }

        // Выбираем подпись и цвет полоски приоритета по числу `task.priority` (0, 1 или 2).
        // Конструкция `"Высокий" to Color.parseColor(...)` в Kotlin создаёт пару из двух значений,
        // а `val (priorityLabel, priorityColor) = ...` сразу раскладывает эту пару в две отдельные переменные:
        // 2 -> красный (#E53935), 0 -> зелёный (#43A047), 1 (и всё остальное) -> оранжевый (#FB8C00)
        val (priorityLabel, priorityColor) = when (task.priority) {
            2 -> "Высокий" to Color.parseColor("#E53935")
            0 -> "Низкий" to Color.parseColor("#43A047")
            else -> "Средний" to Color.parseColor("#FB8C00")
        }
        // Красим вертикальную полоску слева в цвет приоритета и пишем строку «Категория • Приоритет»
        holder.priorityIndicator.setBackgroundColor(priorityColor)
        holder.metaTextView.text = "${task.category} • $priorityLabel приоритет"

        // Зачёркиваем название, если задача выполнена, и ОБЯЗАТЕЛЬНО снимаем зачёркивание, если не выполнена
        // (иначе при переиспользовании карточки RecyclerView чужое зачёркивание переехало бы на активную задачу!).
        // В Android стили текста хранятся в одном числе `paintFlags` в виде битовых флажков:
        // - `or Paint.STRIKE_THRU_TEXT_FLAG` — включает бит зачёркивания (не трогая остальные настройки шрифта);
        // - `and Paint.STRIKE_THRU_TEXT_FLAG.inv()` — выключает ровно этот один бит (`inv()` переворачивает биты)
        holder.titleTextView.paintFlags = if (task.isDone) {
            holder.titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // ОЧЕНЬ ВАЖНЫЙ ФИКС ДЛЯ CHECKBOX В RECYCLERVIEW!
        // Так как карточки переиспользуются, на этом CheckBox мог остаться старый слушатель от предыдущей задачи.
        // Если просто поменять `isChecked = task.isDone`, сработал бы старый слушатель и случайно перезаписал чужую задачу в БД!
        // Поэтому сначала полностью снимаем слушатель (`null`), затем спокойно выставляем нужную галочку `task.isDone`,
        // и только ПОСЛЕ этого вешаем свежий слушатель на клики пользователя
        holder.statusCheckBox.setOnCheckedChangeListener(null)
        holder.statusCheckBox.isChecked = task.isDone
        holder.statusCheckBox.setOnCheckedChangeListener { _, isChecked ->
            // Передаём задачу и новое состояние галочки обратно в MainActivity -> TaskViewModel -> Room
            onTaskCheckedChange(task, isChecked)
        }

        // Клик по самой карточке — передаём задачу в MainActivity, чтобы открыть экран подробностей DetailActivity
        holder.itemView.setOnClickListener {
            onTaskClick(task)
        }
    }

    // Говорит RecyclerView, сколько всего карточек задач сейчас в списке
    override fun getItemCount(): Int = tasks.size
}
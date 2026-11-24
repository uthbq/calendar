// Адрес (пакет) моего приложения — все файлы в этой папке видят друг друга без лишних импортов
package ru.university.calendar

// LayoutInflater — инструмент Android, который берёт наш XML-файл (item_calendar_day.xml)
// и превращает его в живой объект интерфейса на экране
import android.view.LayoutInflater

// View — базовый класс для вообще любого элемента на экране (текста, кнопки, кружочка-точки).
// ViewGroup — контейнер, внутри которого лежат другие элементы (например, сам RecyclerView)
import android.view.View
import android.view.ViewGroup

// TextView — элемент для отображения числа дня месяца
import android.widget.TextView

// ContextCompat нужен, чтобы безопасно и правильно доставать цвета из colors.xml
// (он сам подхватит нужный цвет — светлый или тёмный, в зависимости от выбранной темы)
import androidx.core.content.ContextCompat

// RecyclerView — современный список/сетка, который умеет переиспользовать ячейки и не тратить лишнюю память
import androidx.recyclerview.widget.RecyclerView

// Адаптер для моей собственной календарной сетки месяца.
// Зачем я сделал календарь сам через RecyclerView (с GridLayoutManager на 7 колонок), а не взял готовый CalendarView?
// Потому что стандартный CalendarView почти нельзя нормально настроить — туда не добавишь свои точки под днями с задачами
// и неудобно красить ячейки под тёмную тему. А через свой адаптер я полностью контролирую вид каждой клетки.
//
// В скобках передаётся `onDayClick: (String) -> Unit`:
// это функция-обработчик, которую мне даёт MainActivity. Она принимает строку с датой (String),
// а `Unit` значит, что ничего не возвращает (как void в Java). Когда юзер ткнёт в день — я просто вызову эту функцию.
//
// Наследуемся от RecyclerView.Adapter и в треугольных скобках указываем наш внутренний класс DayViewHolder.
class CalendarAdapter(
    private val onDayClick: (String) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    // Текущий список всех ячеек выбранного месяца (пустые клетки в начале + сами дни с 1 по 28/30/31).
    // Тут `var`, потому что при переключении месяца или клике по дню список заменяется на новый.
    // Изначально кладём пустой список `emptyList()`, чтобы не было null и лишних проверок
    private var days: List<CalendarDay> = emptyList()

    // Метод для обновления сетки календаря (вызывается из MainActivity, когда переключили месяц,
    // выбрали другой день или в базе появились/исчезли задачи)
    fun updateDays(newDays: List<CalendarDay>) {
        // Сохраняем новый список ячеек
        days = newDays
        // Говорим RecyclerView: «данные обновились, перерисуй все ячейки календаря заново»
        notifyDataSetChanged()
    }

    // Внутренний класс ViewHolder — хранит готовые ссылки на элементы внутри ОДНОЙ ячейки календаря.
    // Частый вопрос на защите: зачем вообще нужен ViewHolder?
    // Поиск через findViewById — тяжёлая операция. Если бы мы искали TextView и View заново при каждой
    // перерисовке каждой из 35-42 клеток, экран мог бы тормозить. А ViewHolder ищет их ровно один раз
    // при создании ячейки и потом просто отдаёт готовые ссылки.
    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Текст с числом месяца (1, 2, 3...)
        val dayText: TextView = itemView.findViewById(R.id.tvCalendarDay)
        // Маленькая цветная точка под числом, которая показывает, что в этот день есть задачи
        val dotView: View = itemView.findViewById(R.id.viewTaskDot)
    }

    // Вызывается RecyclerView, когда ему нужно создать с нуля новую пустую ячейку календаря
    // (обычно создаётся только столько ячеек, сколько реально помещается на экране)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        // Берём наш макет одной клетки (res/layout/item_calendar_day.xml) и создаём из него реальный View.
        // Важный третий параметр `false` (attachToRoot): говорим LayoutInflater НЕ прикреплять ячейку к сетке прямо сейчас,
        // потому что RecyclerView сам прикрепит её в нужный момент и в нужное место
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        // Заворачиваем созданную клетку в наш DayViewHolder и отдаём системе
        return DayViewHolder(view)
    }

    // Вызывается для каждой видимой ячейки по её номеру `position` (от 0 до days.size - 1).
    // Здесь мы заполняем конкретную клетку реальными данными: числом, фоном, цветом текста и точкой
    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        // Берём модель дня, которая должна отображаться на этой позиции
        val day = days[position]
        // Достаём Context прямо из самой ячейки — он пригодится ниже, чтобы брать цвета из colors.xml
        val context = holder.itemView.context

        // Проверяем, не пустая ли это ячейка-заглушка в начале месяца
        // (например, если 1-е число — это среда, то понедельник и вторник будут с date == null).
        // Очень важно принудительно очищать и текст, и фон, и точку, и клик:
        // потому что RecyclerView переиспользует старые ячейки, и без очистки в пустой клетке
        // мог бы остаться старый синий кружок или чужое число с прошлого месяца!
        if (day.date == null) {
            holder.dayText.text = ""
            holder.dayText.background = null
            // Именно INVISIBLE (скрыта, но занимает своё место), а не GONE, чтобы все строчки сетки были ровными
            holder.dotView.visibility = View.INVISIBLE
            // Снимаем обработчик нажатия и отключаем кликабельность, чтобы по пустоте нельзя было нажать
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
            // Дальше для пустой клетки делать нечего — выходим из метода
            return
        }

        // Если это обычный день месяца — пишем его число (переводим Int в строку через toString())
        holder.dayText.text = day.dayNumber.toString()

        // Если на эту дату есть хотя бы одна задача в базе — показываем точку (VISIBLE),
        // если задач нет — прячем её (INVISIBLE, чтобы место под числом сохранялось и числа не «прыгали» по высоте)
        holder.dotView.visibility = if (day.hasTasks) View.VISIBLE else View.INVISIBLE

        // На всякий случай возвращаем кликабельность (если раньше эта ячейка была пустой заглушкой)
        holder.itemView.isClickable = true

        // Выбираем оформление числа через конструкцию `when` (удобная замена цепочке if / else if).
        // Порядок веток важен: `day.isSelected` стоит выше `day.isToday`, поэтому если сегодня выбрано,
        // оно закрасится сплошным кружком, а не останется просто с тонкой обводкой.
        when {
            day.isSelected -> {
                // Выбранный день: залитый кружок из res/drawable/bg_calendar_selected.xml и белый текст
                holder.dayText.setBackgroundResource(R.drawable.bg_calendar_selected)
                holder.dayText.setTextColor(ContextCompat.getColor(context, R.color.cal_selected_text))
            }
            day.isToday -> {
                // Сегодняшний день (если выбран другой): тонкое колечко bg_calendar_today.xml и акцентный цвет цифры
                holder.dayText.setBackgroundResource(R.drawable.bg_calendar_today)
                holder.dayText.setTextColor(ContextCompat.getColor(context, R.color.cal_accent))
            }
            else -> {
                // Обычный день: обязательно убираем фон (null), иначе от переиспользования останется старый кружок,
                // и ставим обычный основной цвет текста (чёрный в светлой теме / светлый в тёмной теме)
                holder.dayText.background = null
                holder.dayText.setTextColor(ContextCompat.getColor(context, R.color.cal_text_primary))
            }
        }

        // Вешаем обработчик нажатия на всю ячейку.
        // Когда пользователь тыкает по числу — передаём дату этой клетки (например, "2026-11-24")
        // обратно в MainActivity через нашу функцию `onDayClick`
        holder.itemView.setOnClickListener {
            onDayClick(day.date)
        }
    }

    // Обязательный метод адаптера: говорит RecyclerView, сколько всего ячеек нужно нарисовать в сетке.
    // Запись через `=` — короткий способ написать функцию в одну строчку вместо `{ return days.size }`
    override fun getItemCount(): Int = days.size
}
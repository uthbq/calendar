package ru.university.calendar

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val tvTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = findViewById<TextView>(R.id.tvDetailDate)
        val tvTime = findViewById<TextView>(R.id.tvDetailTime)
        val tvDescription = findViewById<TextView>(R.id.tvDetailDescription)
        val buttonEdit = findViewById<Button>(R.id.buttonEditTask)

        // Получаем все данные из Intent (по умолчанию id = 0, если не найдено)
        val taskId = intent.getIntExtra("EXTRA_ID", 0)
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Без названия"
        val date = intent.getStringExtra("EXTRA_DATE") ?: "Без даты"
        val time = intent.getStringExtra("EXTRA_TIME") ?: "Без времени"
        val description = intent.getStringExtra("EXTRA_DESCRIPTION") ?: "Нет описания"

        // Выводим данные на экран
        tvTitle.text = title
        tvDate.text = "Дата: $date"
        tvTime.text = "Время: $time"
        tvDescription.text = description

        // Настраиваем кнопку "Редактировать"
        buttonEdit.setOnClickListener {
            val editIntent = Intent(this, AddTaskActivity::class.java).apply {
                putExtra("EXTRA_ID", taskId)
                putExtra("EXTRA_TITLE", title)
                putExtra("EXTRA_DATE", date)
                putExtra("EXTRA_TIME", time)
                putExtra("EXTRA_DESCRIPTION", description)
            }
            startActivity(editIntent)
            finish()
        }
    }
}
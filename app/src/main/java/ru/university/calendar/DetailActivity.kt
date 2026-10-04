package ru.university.calendar

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val tvTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvDate = findViewById<TextView>(R.id.tvDetailDate)
        val tvDescription = findViewById<TextView>(R.id.tvDetailDescription)

        // Получаем данные, переданные через Intent
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Без названия"
        val date = intent.getStringExtra("EXTRA_DATE") ?: "Без даты"
        val description = intent.getStringExtra("EXTRA_DESCRIPTION") ?: "Нет описания"

        // Вывод сведений
        tvTitle.text = title
        tvDate.text = "Дата: $date"
        tvDescription.text = description
    }
}
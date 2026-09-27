package ru.university.calendar

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Привязываем код к XML-разметке главного экрана
        setContentView(R.layout.activity_main)

        // Создаем тестовые данные
        val dummyTasks = listOf(
            Task(id = 1, title = "Написать код бэкенда", description = "Сделать API", date = "10.10.2026"),
            Task(id = 2, title = "Сделать тренировку в зале", description = "Руки и пресс", date = "11.10.2026"),
            Task(id = 3, title = "Поработать над машиной", description = "Замена масла", date = "12.10.2026")
        )

        // Настраиваем RecyclerView
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewTasks)
        // LinearLayoutManager выстраивает элементы в обычный вертикальный список
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Создаем адаптер и передаем ему список и обработчик кликов
        val adapter = TasksAdapter(dummyTasks) { taskId ->
            // Этот код сработает при клике на задачу в списке
            // Intent — объект-намерение для перехода на экран TaskDetailsActivity
            val intent = Intent(this, TaskDetailsActivity::class.java)
            // putExtra позволяет передать данные (в нашем случае id) вместе с переходом
            intent.putExtra("TASK_ID", taskId)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Настраиваем кнопку добавления задачи (FloatingActionButton)
        val fab: FloatingActionButton = findViewById(R.id.fabAddTask)
        fab.setOnClickListener {
            // Переход на пустой экран AddTaskActivity
            val intent = Intent(this, AddTaskActivity::class.java)
            startActivity(intent)
        }
    }
}
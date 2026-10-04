package ru.university.calendar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTasks)
        recyclerView.adapter = TasksAdapter(emptyList()) {}
        // Настраиваем переход на экран добавления задачи
        val fab = findViewById<FloatingActionButton>(R.id.fabAddTask)
        fab.setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }

        testDatabase()
    }

    private fun testDatabase() {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.taskDao()

        lifecycleScope.launch(Dispatchers.IO) {
            // Создаем тестовые задачи
            val task1 = Task(title = "Купить продукты", description = "Молоко, хлеб, яйца", date = "2026-10-04")
            val task2 = Task(title = "Сделать ДЗ по Android", description = "Закончить 3 этап", date = "2026-10-05")

            // Сохраняем их
            dao.insertTask(task1)
            dao.insertTask(task2)

            // Читаем все задачи из базы
            val allTasks = dao.getAllTasks()

            for (task in allTasks) {
                Log.d("DB_TEST", "Найдена задача: ID=${task.id}, Название='${task.title}', Дата=${task.date}")
            }

            withContext(Dispatchers.Main) {
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTasks)
                recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)

                val adapter = TasksAdapter(allTasks) { taskId ->
                    // Находим задачу, на которую кликнули
                    val selectedTask = allTasks.find { it.id == taskId }
                    if (selectedTask != null) {
                        // Передаем данные на экран деталей
                        val intent = Intent(this@MainActivity, DetailActivity::class.java).apply {
                            putExtra("EXTRA_TITLE", selectedTask.title)
                            putExtra("EXTRA_DATE", selectedTask.date)
                            putExtra("EXTRA_DESCRIPTION", selectedTask.description)
                        }
                        startActivity(intent)
                    }
                }
                recyclerView.adapter = adapter
            }
        }
    }
}
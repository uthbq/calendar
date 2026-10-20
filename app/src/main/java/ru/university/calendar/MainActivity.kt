package ru.university.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val taskViewModel: TaskViewModel by viewModels()

    // Переменная для хранения текущего списка задач на экране (нужна для свайпов)
    private var currentTasks: List<Task> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTasks)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Кнопка добавления новой задачи
        val fab = findViewById<FloatingActionButton>(R.id.fabAddTask)
        fab.setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }

        // Подписка на данные из ViewModel
        taskViewModel.allTasks.observe(this) { tasks ->
            currentTasks = tasks // Сохраняем свежий список в переменную

            val adapter = TasksAdapter(tasks) { taskId ->
                val selectedTask = tasks.find { it.id == taskId }
                if (selectedTask != null) {
                    val intent = Intent(this, DetailActivity::class.java).apply {
                        putExtra("EXTRA_ID", selectedTask.id) // Передаем ID!
                        putExtra("EXTRA_TITLE", selectedTask.title)
                        putExtra("EXTRA_DATE", selectedTask.date)
                        putExtra("EXTRA_TIME", selectedTask.time) // Передаем время!
                        putExtra("EXTRA_DESCRIPTION", selectedTask.description)
                    }
                    startActivity(intent)
                }
            }
            recyclerView.adapter = adapter
        }

        //УДАЛЕНИЕ СВАЙПОМ
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            // Метод срабатывает, когда карточка смахнута до конца
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val taskToDelete = currentTasks[position]
                taskViewModel.deleteTask(taskToDelete)
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
package ru.university.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class AddTaskActivity : AppCompatActivity() {

    private val taskViewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        val layoutTitle = findViewById<TextInputLayout>(R.id.textInputLayoutTitle)
        val editTitle = findViewById<TextInputEditText>(R.id.editTextTitle)
        val editDate = findViewById<TextInputEditText>(R.id.editTextDate)
        val editTime = findViewById<TextInputEditText>(R.id.editTextTime)
        val editDescription = findViewById<TextInputEditText>(R.id.editTextDescription)
        val buttonSave = findViewById<Button>(R.id.buttonSave)

        val taskId = intent.getIntExtra("EXTRA_ID", 0)
        val isEditMode = taskId != 0

        if (isEditMode) {
            editTitle.setText(intent.getStringExtra("EXTRA_TITLE"))
            editDate.setText(intent.getStringExtra("EXTRA_DATE"))
            editTime.setText(intent.getStringExtra("EXTRA_TIME"))
            editDescription.setText(intent.getStringExtra("EXTRA_DESCRIPTION"))
            buttonSave.text = "Обновить задачу" // Меняем текст кнопки
        }
        // =================================

        editDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                editDate.setText("$year-${month + 1}-$day")
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        editTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                editTime.setText(String.format("%02d:%02d", hour, minute))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        buttonSave.setOnClickListener {
            val title = editTitle.text.toString().trim()
            val date = editDate.text.toString().trim()
            val time = editTime.text.toString().trim()
            val description = editDescription.text.toString().trim()

            if (title.isEmpty()) {
                layoutTitle.error = "Название задачи обязательно!"
                return@setOnClickListener
            }

            val task = Task(
                id = if (isEditMode) taskId else 0,
                title = title,
                description = description,
                date = date,
                time = time
            )

            if (isEditMode) {
                taskViewModel.updateTask(task)
            } else {
                taskViewModel.insertTask(task)
            }

            finish()
        }
    }
}
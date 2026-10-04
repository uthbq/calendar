package ru.university.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TasksAdapter(
    private val tasks: List<Task>,
    private val onTaskClick: (Int) -> Unit
) : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.textViewTitle)
        val dateTextView: TextView = itemView.findViewById(R.id.textViewDate)
        val statusCheckBox: CheckBox = itemView.findViewById(R.id.checkBoxDone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.titleTextView.text = task.title
        holder.dateTextView.text = task.date
        // Привязываем статус из базы данных к галочке
        holder.statusCheckBox.isChecked = task.isDone

        holder.itemView.setOnClickListener {
            onTaskClick(task.id)
        }
    }

    override fun getItemCount(): Int {
        return tasks.size
    }
}
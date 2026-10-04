package ru.university.calendar

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Указываем Room, что это интерфейс для работы с базой данных
@Dao
interface TaskDao {

    // suspend означает, что функция будет работать в фоне
    // onConflict = OnConflictStrategy.REPLACE обновит задачу, если вдруг ID совпадут.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // ORDER BY id DESC отсортирует задачи так, чтобы новые были сверху.
    @Query("SELECT * FROM tasks_table ORDER BY id DESC")
    fun getAllTasks(): List<Task>

    // Пример запроса для получения задач на конкретный день для календаря.
    // Двоеточие перед selectedDate связывает переменную из запроса с параметром функции.
    @Query("SELECT * FROM tasks_table WHERE date = :selectedDate")
    fun getTasksByDate(selectedDate: String): List<Task>
}
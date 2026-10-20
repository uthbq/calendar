package ru.university.calendar

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @androidx.room.Update
    suspend fun updateTask(task: Task)
    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM tasks_table ORDER BY id DESC")
    fun getAllTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks_table WHERE date = :selectedDate")
    fun getTasksByDate(selectedDate: String): LiveData<List<Task>>
}
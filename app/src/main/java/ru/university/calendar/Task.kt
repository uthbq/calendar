package ru.university.calendar

import androidx.room.Entity
import androidx.room.PrimaryKey

// Эта аннотация говорит Room, что нужно создать таблицу с именем "tasks_table"
@Entity(tableName = "tasks_table")
data class Task(
    // Эта пометка заставляет базу данных автоматически генерировать уникальный айди
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,
    val description: String,
    val date: String,
    val isDone: Boolean = false
)
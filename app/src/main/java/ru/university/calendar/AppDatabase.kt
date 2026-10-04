package ru.university.calendar

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Аннотация связывает базу данных с нашей таблицей (Entity)
@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Абстрактный метод, через который Room предоставит нам доступ к методам DAO
    abstract fun taskDao(): TaskDao

    companion object {
        // Volatile гарантирует, что значение INSTANCE всегда актуально для всех потоков
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Метод для получения единого экземпляра базы данных
        fun getDatabase(context: Context): AppDatabase {
            // Если экземпляр уже создан возвращаем его, если нет создаем
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_database" // Имя файла базы данных на устройстве
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
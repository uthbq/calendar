// Глобальный файл проекта
plugins {
    id("com.android.application") version "8.2.0" apply false
    // Обновляем версию Kotlin до 1.9.24 (самая стабильная версия, где исправлены баги совместимости)
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.24" apply false
}
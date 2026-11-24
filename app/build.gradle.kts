// Главный файл настройки сборки нашего модуля приложения (Gradle Kotlin DSL).
// Именно здесь мы говорим системе сборки Gradle, под какие версии Android собирать APK,
// какой у приложения ID, на какой версии Java/Kotlin компилировать код и какие библиотеки подключить.

// Подключаем плагины (инструменты сборки), версии которых объявлены в корневом build.gradle.kts:
plugins {
    // Превращает этот модуль в полноценное Android-приложение (собирает APK, обрабатывает манифест и ресурсы res/)
    id("com.android.application")
    // Включает поддержку языка Kotlin в Android-проекте
    id("org.jetbrains.kotlin.android")
    // KAPT (Kotlin Annotation Processing Tool) — обработчик аннотаций при сборке.
    // Нужен специально для Room: во время компиляции он читает наши @Entity, @Dao и @Database
    // и сам генерирует готовые классы работы с SQLite (AppDatabase_Impl и TaskDao_Impl)
    id("org.jetbrains.kotlin.kapt")
}

// Основной блок настроек самого Android-приложения
android {
    // Пространство имён проекта — по нему генерируется класс ресурсов `R` (R.layout, R.id, R.color и т.д.)
    namespace = "ru.university.calendar"

    // Версия Android SDK, с которой компилируется наш код (34 = Android 14).
    // Благодаря этому нам доступны свежие константы и API, например POST_NOTIFICATIONS и Build.VERSION_CODES.TIRAMISU
    compileSdk = 34

    // Базовые параметры, общие для всех сборок (и для отладки debug, и для финального release)
    defaultConfig {
        // Уникальный идентификатор приложения на телефоне и в Google Play (два приложения с одним ID поставить нельзя)
        applicationId = "ru.university.calendar"

        // Минимальная версия Android, на которой вообще запустится приложение (26 = Android 8.0 Oreo).
        // Начиная с 26-го API в системе появились каналы уведомлений (NotificationChannel) и современные фичи Android
        minSdk = 26

        // Целевая версия Android, под правила которой тестировалось приложение (34 = Android 14).
        // Говорит системе, что мы умеем работать по современным правилам безопасности (например, запрашивать POST_NOTIFICATIONS)
        targetSdk = 34

        // Внутренний числовой номер версии (целое число: 1, 2, 3...) — по нему Android понимает,
        // что новая сборка новее предыдущей при обновлении приложения
        versionCode = 1

        // Понятное человеку название версии, которое отображается в настройках телефона ("1.0", "1.1" и т.д.)
        versionName = "1.0"
    }

    // Настройки разных типов сборки (debug собирается для тестов в студии, release — для публикации)
    buildTypes {
        release {
            // Отключаем сжатие и запутывание кода (R8/ProGuard), чтобы упростить сборку и отладку учебного проекта
            isMinifyEnabled = false
            // Файлы правил для ProGuard (стандартные правила Android + локальный proguard-rules.pro),
            // сработают только если когда-нибудь включить isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Указываем версию Java для компилятора (17-я версия — стандарт для современных Android Studio и Gradle 8+)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Указываем ту же 17-ю версию JVM для компилятора Kotlin, чтобы Java-код и Kotlin-код совпадали по формату байткода
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Дополнительная настройка для обработчика аннотаций KAPT:
// `correctErrorTypes = true` помогает корректно распознавать сгенерированные типы при сборке
// и выдавать понятные сообщения об ошибках, если что-то пойдёт не так в аннотациях Room
kapt {
    correctErrorTypes = true
}

// Список всех внешних библиотек, от которых зависит наше приложение.
// - `implementation(...)` — обычная библиотека, которая попадает внутрь собранного APK;
// - `kapt(...)` — генератор кода, работает только на этапе компиляции на компьютере;
// - `testImplementation(...)` — нужна только для локальных тестов на компьютере (папка src/test);
// - `androidTestImplementation(...)` — нужна только для тестов на эмуляторе/телефоне (папка src/androidTest).
dependencies {
    // Базовые библиотеки прямым текстом
    // Удобные Kotlin-расширения и совместимость со старыми версиями Android (ContextCompat, NotificationCompat)
    implementation("androidx.core:core-ktx:1.13.0")
    // Современная база экранов и элементов: AppCompatActivity, AlertDialog, SearchView и переключение тёмной темы AppCompatDelegate
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Библиотека Material Design от Google: FloatingActionButton, TextInputLayout, TextInputEditText и темы DayNight
    implementation("com.google.android.material:material:1.10.0")
    // Гибкий контейнер ConstraintLayout, на котором построен главный экран activity_main.xml
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Библиотеки базы данных Room прямым текстом
    // Основное ядро библиотеки Room (RoomDatabase, @Entity, @Dao, @Query)
    implementation("androidx.room:room-runtime:2.6.1")
    // Расширение Room для Kotlin — добавляет поддержку `suspend`-функций и корутин в TaskDao
    implementation("androidx.room:room-ktx:2.6.1")
    // Компилятор Room (работает через kapt) — проверяет SQL-запросы при сборке и генерирует реальный код работы с SQLite
    kapt("androidx.room:room-compiler:2.6.1")

    // Компоненты архитектуры MVVM (Android Jetpack Lifecycle и Activity):
    // Даёт классы AndroidViewModel и готовый `viewModelScope` для безопасного запуска фоновых корутин
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    // Даёт наблюдаемые контейнеры LiveData, MutableLiveData и MediatorLiveData
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    // Даёт удобную запись `by viewModels()` в Activity и контракты `registerForActivityResult` (разрешения и выбор JSON-файла)
    implementation("androidx.activity:activity-ktx:1.8.0")

    // Стандартные библиотеки для автоматического тестирования (подключаются шаблоном Android Studio)
    // JUnit — для обычных юнит-тестов логики, которые запускаются прямо на ПК без эмулятора
    testImplementation("junit:junit:4.13.2")
    // AndroidX JUnit и Espresso — для UI-тестов (автоматического кликания по кнопкам) на эмуляторе или телефоне
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
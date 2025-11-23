package com.example.kiesocounter_v3_1_1

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat  // ← ÚJ
import java.util.Locale            // ← ÚJ
import kotlin.random.Random



// Adatstruktúra a havi diagramhoz
data class MonthlyChartData(val dayOfMonth: Int, val categoryTotals: Map<String, Int>)

open class MainViewModel(private val dao: NumberEntryDao) : ViewModel() {

    val todayEntries: StateFlow<List<NumberEntry>> = dao.getEntriesForDay(getStartOfDay(Date()), getEndOfDay(Date()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryTotalsToday: StateFlow<Map<String, Int>> = todayEntries.map { entries ->
        entries
            .groupBy { it.categoryName }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // MainViewModel.kt-be add hozzá ezt az állapotot a többi StateFlow mellé:

    // BINGÓ mód állapota
    private val _bingoModeEnabled = MutableStateFlow(false)
    val bingoModeEnabled: StateFlow<Boolean> = _bingoModeEnabled.asStateFlow()

    fun toggleBingoMode() {
        _bingoModeEnabled.value = !_bingoModeEnabled.value
    }

    // ÚJ: Utolsó munkanapos adatok (max 30 napra visszamenőleg)
    private val _lastWorkdayData = MutableStateFlow<Pair<Date?, List<NumberEntry>>>(null to emptyList())
    val lastWorkdayEntries: StateFlow<List<NumberEntry>> = _lastWorkdayData
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ÚJ: Utolsó munkanap összesítései kategóriánként
    val categoryTotalsLastWorkday: StateFlow<Map<String, Int>> = lastWorkdayEntries.map { entries ->
        entries
            .groupBy { it.categoryName }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Betöltjük az utolsó munkanap adatait az inicializáláskor
        viewModelScope.launch {
            loadLastWorkdayData()
        }
    }

    private suspend fun loadLastWorkdayData() {
        val today = getStartOfDay(Date())
        val calendar = Calendar.getInstance()

        // Max 30 napra visszamenőleg keresünk
        for (i in 1..30) {
            calendar.time = today
            calendar.add(Calendar.DAY_OF_YEAR, -i)

            val checkDate = calendar.time
            val startOfDay = getStartOfDay(checkDate)
            val endOfDay = getEndOfDay(checkDate)

            // Szinkron lekérdezés - megnézzük van-e adat ezen a napon
            val entries = dao.getEntriesForDay(startOfDay, endOfDay)

            // Az első flow értéket várjuk
            var found = false
            entries.collect { entryList ->
                if (entryList.isNotEmpty() && !found) {
                    _lastWorkdayData.value = checkDate to entryList
                    found = true
                }
            }

            if (found) {
                return // Megtaláltuk, kilépünk
            }
        }

        // Ha nem találtunk semmit 30 napon belül
        _lastWorkdayData.value = null to emptyList()
    }

    private val _selectedDate = MutableStateFlow(getStartOfDay(Date()))

    val selectedDayEntries: StateFlow<List<NumberEntry>> = _selectedDate.flatMapLatest { date ->
        val startOfDay = getStartOfDay(date)
        val endOfDay = getEndOfDay(date)
        dao.getEntriesForDay(startOfDay, endOfDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- HAVI NÉZET --- //
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())

    val monthlyChartData: StateFlow<List<MonthlyChartData>> = _selectedMonth.flatMapLatest { cal ->
        val startOfMonth = cal.clone() as Calendar
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        val endOfMonth = cal.clone() as Calendar
        endOfMonth.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))

        dao.getEntriesForMonth(getStartOfDay(startOfMonth.time), getEndOfDay(endOfMonth.time))
            .map { entries ->
                val groupedByDay = entries.groupBy {
                    val entryCal = Calendar.getInstance()
                    entryCal.time = it.timestamp
                    entryCal.get(Calendar.DAY_OF_MONTH)
                }

                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                (1..daysInMonth).map { day ->
                    val dayEntries = groupedByDay[day]
                    val categoryTotals = if (dayEntries != null) {
                        CATEGORIES.associateWith { category ->
                            dayEntries.filter { it.categoryName == category }.sumOf { it.value }
                        }
                    } else {
                        CATEGORIES.associateWith { 0 }
                    }
                    MonthlyChartData(day, categoryTotals)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadEntriesForSelectedMonth(year: Int, month: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        _selectedMonth.value = calendar
    }
    // --- HAVI NÉZET VÉGE ---

    fun loadEntriesForSelectedDate(dateInMillis: Long?) {
        _selectedDate.value = Date(dateInMillis ?: Date().time)
    }

    open fun addEntry(value: Int, categoryName: String) {
        viewModelScope.launch {
            dao.insert(NumberEntry(value = value, categoryName = categoryName, timestamp = Date()))
        }
    }

    open fun updateEntry(entry: NumberEntry) {
        viewModelScope.launch {
            dao.update(entry)
        }
    }

    open fun deleteEntry(entry: NumberEntry) {
        viewModelScope.launch {
            dao.delete(entry)
        }
    }

    open fun undoLastEntry() {
        viewModelScope.launch {
            val lastEntry = dao.getLastEntry()
            lastEntry?.let {
                dao.delete(it)
            }
        }
    }

    open fun deleteAllEntries() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }

    open fun deleteTodayEntries() {
        viewModelScope.launch {
            val startOfDay = getStartOfDay(Date())
            dao.deleteEntriesSince(startOfDay)
        }
    }

    open fun generateTestData(days: Int) {
        viewModelScope.launch {
            for (i in 0 until days) {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -i)

                CATEGORIES.forEach { categoryName ->
                    for (j in 0..Random.nextInt(1, 5)) {
                        val entryCalendar = calendar.clone() as Calendar
                        entryCalendar.set(Calendar.HOUR_OF_DAY, Random.nextInt(8, 17))
                        val date = entryCalendar.time

                        val entry = NumberEntry(
                            value = Random.nextInt(1, 100),
                            categoryName = categoryName,
                            timestamp = date
                        )
                        dao.insert(entry)
                    }
                }
            }
            // Újra betöltjük az utolsó munkanap adatait
            loadLastWorkdayData()
        }
    }

    private fun getStartOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun getEndOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }

    // Export minden adat CSV-be - JAVÍTOTT
    suspend fun exportAllDataToCSV(): String {
        val stringBuilder = StringBuilder()

        // CSV fejléc
        stringBuilder.appendLine("id,érték,kategória,dátum")

        // Adatok gyűjtése - first() használata a Flow-ból egyszer lekérdezni
        val entries = dao.getAllEntries()
        val entryList = entries.first()  // ← JAVÍTÁS: first() használata

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        entryList.forEach { entry ->
            val formattedDate = dateFormat.format(entry.timestamp)
            stringBuilder.appendLine("${entry.id},${entry.value},${entry.categoryName},$formattedDate")
        }

        return stringBuilder.toString()
    }

    // Import CSV adatok
    suspend fun importDataFromCSV(csvContent: String): Result<Int> {
        return try {
            val lines = csvContent.trim().lines()

            // Ellenőrizzük a fejlécet
            if (lines.isEmpty() || !lines[0].contains("érték")) {
                return Result.failure(Exception("Hibás CSV formátum!"))
            }

            var importedCount = 0
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            // Minden adat törlése import előtt
            dao.deleteAll()

            // Adatok beolvasása (első sor = fejléc, azt kihagyjuk)
            lines.drop(1).forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split(",")
                    if (parts.size >= 4) {
                        val value = parts[1].toIntOrNull() ?: 0
                        val categoryName = parts[2]
                        val timestamp = try {
                            dateFormat.parse(parts[3]) ?: Date()
                        } catch (e: Exception) {
                            Date()
                        }

                        val entry = NumberEntry(
                            value = value,
                            categoryName = categoryName,
                            timestamp = timestamp
                        )
                        dao.insert(entry)
                        importedCount++
                    }
                }
            }

            // Újra betöltjük az utolsó munkanap adatait
            loadLastWorkdayData()

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val dao = AppDatabase.getInstance(application).numberEntryDao()
            return MainViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


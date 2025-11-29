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
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import kotlin.compareTo
import kotlin.random.Random
import kotlin.text.toFloat
import kotlin.times

data class MonthlyChartData(val dayOfMonth: Int, val categoryTotals: Map<String, Int>)

open class MainViewModel(
    private val dao: NumberEntryDao,
    val settingsManager: SettingsManager  // ← ÚJ!
) : ViewModel() {

    // ÚJ: Settings StateFlow
    val settings: StateFlow<AppSettings> = settingsManager.settings
    val todayEntries: StateFlow<List<NumberEntry>> = dao.getEntriesForDay(getStartOfDay(Date()), getEndOfDay(Date()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ÚJ: Egyéb kategória csoportjai - KÖZVETLENÜL az entries-ekből!
    val egyebSubCategories: StateFlow<List<String>> = todayEntries
        .map { entries ->
            entries
                .filter { it.categoryName == "Egyéb" && it.subCategory != null }
                .mapNotNull { it.subCategory }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _egyebEntriesByGroup = MutableStateFlow<Map<String, List<NumberEntry>>>(emptyMap())
    val egyebEntriesByGroup: StateFlow<Map<String, List<NumberEntry>>> = _egyebEntriesByGroup.asStateFlow()

    private val _daysWithData = MutableStateFlow<Set<LocalDate>>(emptySet())
    val daysWithData: StateFlow<Set<LocalDate>> = _daysWithData.asStateFlow()

    val categoryTotalsToday: StateFlow<Map<String, Int>> = todayEntries.map { entries ->
        entries
            .groupBy { it.categoryName }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _bingoModeEnabled = MutableStateFlow(false)
    val bingoModeEnabled: StateFlow<Boolean> = _bingoModeEnabled.asStateFlow()

    fun toggleBingoMode() {
        _bingoModeEnabled.value = !_bingoModeEnabled.value
    }

    private val _debugModeEnabled = MutableStateFlow(false)
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    fun toggleDebugMode() {
        _debugModeEnabled.value = !_debugModeEnabled.value
    }

    private val _contextDate = MutableStateFlow(Date())
    val contextDate: StateFlow<Date> = _contextDate.asStateFlow()

    fun setContextDate(date: Date) {
        _contextDate.value = date
    }

    fun resetContextDate() {
        _contextDate.value = Date()
    }

    private val _lastWorkdayData = MutableStateFlow<Pair<Date?, List<NumberEntry>>>(null to emptyList())
    val lastWorkdayEntries: StateFlow<List<NumberEntry>> = _lastWorkdayData
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryTotalsLastWorkday: StateFlow<Map<String, Int>> = lastWorkdayEntries.map { entries ->
        entries
            .groupBy { it.categoryName }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())



    fun loadLastWorkdayData() {
        viewModelScope.launch {
            // ========== ÚJ: SETTINGS-BŐL OLVASSUK A MÉLYSÉGET ==========
            val maxDaysBack = settings.value.lastWorkdaySearchDepth  // ← DINAMIKUS!

            val today = Calendar.getInstance()
            var daysBack = 1

            while (daysBack <= maxDaysBack) {
                val checkDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -daysBack)
                }

                val dayOfWeek = checkDate.get(Calendar.DAY_OF_WEEK)

                if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                    val startOfDay = Calendar.getInstance().apply {
                        time = checkDate.time
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

                    val endOfDay = Calendar.getInstance().apply {
                        time = checkDate.time
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.time

                    val entries = dao.getEntriesForDay(startOfDay, endOfDay).first()

                    if (entries.isNotEmpty()) {
                        _lastWorkdayData.value = Pair(checkDate.time, entries)  // ← JAVÍTVA!
                        return@launch
                    }
                }

                daysBack++
            }

            _lastWorkdayData.value = Pair(null, emptyList())  // ← JAVÍTVA!
        }
    }

    private val _selectedDate = MutableStateFlow(getStartOfDay(Date()))

    val selectedDayEntries: StateFlow<List<NumberEntry>> = _selectedDate.flatMapLatest { date ->
        val startOfDay = getStartOfDay(date)
        val endOfDay = getEndOfDay(date)
        dao.getEntriesForDay(startOfDay, endOfDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun loadDaysWithDataForMonth(year: Int, month: Int) {
        viewModelScope.launch {
            try {
                val yearMonth = String.format("%04d-%02d", year, month + 1)
                val daysStrings = dao.getDaysWithDataInMonth(yearMonth)

                val localDates = daysStrings.mapNotNull { dateString ->
                    try {
                        LocalDate.parse(dateString)
                    } catch (e: Exception) {
                        null
                    }
                }.toSet()

                _daysWithData.value = localDates
            } catch (e: Exception) {
                _daysWithData.value = emptySet()
            }
        }
    }

    fun loadEntriesForSelectedDate(dateInMillis: Long?) {
        _selectedDate.value = Date(dateInMillis ?: Date().time)
    }

    fun reloadSelectedDayEntries() {
        viewModelScope.launch {
            val current = _selectedDate.value
            _selectedDate.value = Date(current.time - 1)
            delay(50)
            _selectedDate.value = current
        }
    }

    open fun addEntry(number: Int, categoryName: String) {
        viewModelScope.launch {
            if (number > 0) {
                if (!_debugModeEnabled.value && _contextDate.value.time < getStartOfDay(Date()).time) {
                    resetContextDate()
                }

                dao.insert(NumberEntry(
                    id = 0,
                    value = number,
                    categoryName = categoryName,
                    subCategory = null,
                    movedFrom = null,
                    timestamp = _contextDate.value
                ))
                reloadSelectedDayEntries()
            }
        }
    }

    open fun updateEntry(entry: NumberEntry) {
        viewModelScope.launch {
            dao.update(entry)
            reloadSelectedDayEntries()
        }
    }

    open fun deleteEntry(entry: NumberEntry) {
        viewModelScope.launch {
            dao.delete(entry)
            reloadSelectedDayEntries()
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

    fun deleteAllEntries() {
        viewModelScope.launch {
            dao.deleteAll()
            _egyebEntriesByGroup.value = emptyMap()
            _lastWorkdayData.value = Pair(null, emptyList())
        }
    }

    open fun deleteTodayEntries() {
        viewModelScope.launch {
            val startOfDay = getStartOfDay(Date())
            dao.deleteEntriesSince(startOfDay)
        }
    }

    open fun generateTodayData() {
        viewModelScope.launch {
            val egyebGroupNames = listOf("Zárolt", "Zajos", "Paszta hiány")
            val groupsToCreate = mutableSetOf<String>()

            CATEGORIES.forEach { categoryName ->
                for (j in 0..Random.nextInt(1, 5)) {
                    val selectedGroup = if (categoryName == "Egyéb") {
                        if (Random.nextFloat() < 0.8f) {
                            egyebGroupNames.random().also { groupsToCreate.add(it) }
                        } else null
                    } else null

                    val entry = NumberEntry(
                        id = 0,
                        value = Random.nextInt(1, 100),
                        categoryName = categoryName,
                        subCategory = selectedGroup,
                        movedFrom = null,
                        timestamp = Date()
                    )
                    dao.insert(entry)
                }
            }

            if (groupsToCreate.isNotEmpty()) {
                createEmptyGroups(groupsToCreate.toList())
            }

            loadEgyebEntriesByGroup()
        }
    }

    open fun generateTestData(daysBack: Int) {
        viewModelScope.launch {
            val egyebGroupNames = listOf("Zárolt", "Zajos", "Paszta hiány")
            val calendar = Calendar.getInstance()

            for (dayOffset in 1..daysBack) {
                calendar.time = Date()
                calendar.add(Calendar.DAY_OF_YEAR, -dayOffset)
                val targetDate = calendar.time

                val groupsToCreate = mutableSetOf<String>()

                CATEGORIES.forEach { categoryName ->
                    for (j in 0..Random.nextInt(1, 5)) {
                        val selectedGroup = if (categoryName == "Egyéb") {
                            if (Random.nextFloat() < 0.8f) {
                                egyebGroupNames.random().also { groupsToCreate.add(it) }
                            } else null
                        } else null

                        val entry = NumberEntry(
                            id = 0,
                            value = Random.nextInt(1, 100),
                            categoryName = categoryName,
                            subCategory = selectedGroup,
                            movedFrom = null,
                            timestamp = targetDate
                        )
                        dao.insert(entry)
                    }
                }

                if (groupsToCreate.isNotEmpty()) {
                    groupsToCreate.forEach { groupName ->
                        dao.insert(NumberEntry(
                            id = 0,
                            value = 0,
                            categoryName = "Egyéb",
                            subCategory = groupName,
                            movedFrom = null,
                            timestamp = targetDate
                        ))
                    }
                }
            }

            loadLastWorkdayData()
            loadEgyebEntriesByGroup()
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

    suspend fun exportAllDataToCSV(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("id,érték,kategória,csoport,dátum")  // ← ÚJ: csoport oszlop!

        val entries = dao.getAllEntries()
        val entryList = entries.first()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        entryList.forEach { entry ->
            val formattedDate = dateFormat.format(entry.timestamp)
            val subCategory = entry.subCategory ?: ""  // ← ÚJ: Ha nincs csoport, üres string
            stringBuilder.appendLine("${entry.id},${entry.value},${entry.categoryName},$subCategory,$formattedDate")
        }

        return stringBuilder.toString()
    }

    suspend fun importDataFromCSV(csvContent: String): Result<Int> {
        return try {
            val lines = csvContent.trim().lines()

            if (lines.isEmpty() || !lines[0].contains("érték")) {
                return Result.failure(Exception("Hibás CSV formátum!"))
            }

            var importedCount = 0
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            dao.deleteAll()

            lines.drop(1).forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split(",")
                    if (parts.size >= 4) {  // ← Minimum 4 mező kell (id, érték, kategória, dátum)
                        val value = parts[1].toIntOrNull() ?: 0
                        val categoryName = parts[2]

                        // ÚJ: Csoport kezelése (ha van 5. oszlop)
                        val subCategory = if (parts.size >= 5 && parts[3].isNotBlank()) {
                            parts[3]
                        } else {
                            null
                        }

                        // Dátum az utolsó oszlopban
                        val timestamp = try {
                            dateFormat.parse(parts.last()) ?: Date()
                        } catch (e: Exception) {
                            Date()
                        }

                        val entry = NumberEntry(
                            id = 0,
                            value = value,
                            categoryName = categoryName,
                            subCategory = subCategory,  // ← ÚJ!
                            movedFrom = null,
                            timestamp = timestamp
                        )
                        dao.insert(entry)
                        importedCount++
                    }
                }
            }

            loadLastWorkdayData()
            delay(100)

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createEmptyGroup(groupName: String) {
        viewModelScope.launch {
            if (!_debugModeEnabled.value && _contextDate.value.time < getStartOfDay(Date()).time) {
                resetContextDate()
            }

            val contextDateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(_contextDate.value)
            println("🔧 DEBUG: Creating group '$groupName' for date: $contextDateFormatted")

            dao.insert(NumberEntry(
                id = 0,
                value = 0,
                categoryName = "Egyéb",
                subCategory = groupName,
                movedFrom = null,
                timestamp = _contextDate.value
            ))

            reloadSelectedDayEntries()
        }
    }

    fun createEmptyGroups(groupNames: List<String>) {
        viewModelScope.launch {
            if (!_debugModeEnabled.value && _contextDate.value.time < getStartOfDay(Date()).time) {
                resetContextDate()
            }

            groupNames.forEach { groupName ->
                dao.insert(NumberEntry(
                    id = 0,
                    value = 0,
                    categoryName = "Egyéb",
                    subCategory = groupName,
                    movedFrom = null,
                    timestamp = _contextDate.value
                ))
            }

            reloadSelectedDayEntries()
        }
    }

    suspend fun loadEgyebEntriesByGroup() {
        viewModelScope.launch {
            val allEgyebEntries = todayEntries.value.filter { it.categoryName == "Egyéb" }

            val grouped = allEgyebEntries.groupBy { entry ->
                entry.subCategory ?: "Csoportosítatlan"
            }

            _egyebEntriesByGroup.value = grouped
        }
    }

    fun addEntryWithSubCategory(value: Int, categoryName: String, subCategory: String?) {
        viewModelScope.launch {
            if (value > 0) {
                if (!_debugModeEnabled.value && _contextDate.value.time < getStartOfDay(Date()).time) {
                    resetContextDate()
                }

                dao.insert(NumberEntry(
                    id = 0,
                    value = value,
                    categoryName = categoryName,
                    subCategory = subCategory,
                    movedFrom = null,
                    timestamp = _contextDate.value
                ))

                if (categoryName == "Egyéb") {
                    loadEgyebEntriesByGroup()
                }

                reloadSelectedDayEntries()
            }
        }
    }

    fun moveEntryToCategory(entry: NumberEntry, newCategoryName: String) {
        viewModelScope.launch {
            val updatedEntry = entry.copy(
                categoryName = newCategoryName,
                movedFrom = entry.categoryName,
                subCategory = null
            )
            dao.update(updatedEntry)
            loadEgyebEntriesByGroup()
        }
    }

    suspend fun getPreviousDaySubCategories(): List<String> {
        return try {
            val lastWorkday = _lastWorkdayData.value.second
            lastWorkday
                .filter { it.categoryName == "Egyéb" && it.subCategory != null }
                .mapNotNull { it.subCategory }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun renameSubCategory(oldName: String, newName: String) {
        viewModelScope.launch {
            val entriesToUpdate = if (_debugModeEnabled.value) {
                selectedDayEntries.value.filter {
                    it.categoryName == "Egyéb" && it.subCategory == oldName
                }
            } else {
                todayEntries.value.filter {
                    it.categoryName == "Egyéb" && it.subCategory == oldName
                }
            }

            entriesToUpdate.forEach { entry ->
                dao.update(entry.copy(subCategory = newName))
            }

            loadEgyebEntriesByGroup()
            reloadSelectedDayEntries()
        }
    }

    suspend fun getTopThreeNumbers(categoryName: String): List<Int> {
        // ========== ÚJ: SETTINGS-BŐL OLVASSUK AZ IDŐTARTAMOT ==========
        val daysToLookBack = settings.value.smartButtonsDays  // ← DINAMIKUS!

        val startDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysToLookBack)
        }.time

        val entries = dao.getEntriesForDay(startDate, Date()).first()

        val numbers = entries
            .filter { it.categoryName == categoryName }
            .groupBy { it.value }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        return numbers
    }

    suspend fun getLastWorkdayBeforeDate(date: Date): List<NumberEntry> {
        // ========== ÚJ: SETTINGS-BŐL OLVASSUK A MÉLYSÉGET ==========
        val maxDaysBack = settings.value.lastWorkdaySearchDepth  // ← DINAMIKUS!

        val calendar = Calendar.getInstance().apply { time = date }
        var daysBack = 1

        while (daysBack <= maxDaysBack) {
            val checkDate = Calendar.getInstance().apply {
                time = date
                add(Calendar.DAY_OF_YEAR, -daysBack)
            }

            val dayOfWeek = checkDate.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                val startOfDay = Calendar.getInstance().apply {
                    time = checkDate.time
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                val endOfDay = Calendar.getInstance().apply {
                    time = checkDate.time
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.time

                val entries = dao.getEntriesForDay(startOfDay, endOfDay).first()

                if (entries.isNotEmpty()) {
                    return entries
                }
            }

            daysBack++
        }

        return emptyList()
    }

    fun deleteGroup(groupName: String) {
        viewModelScope.launch {
            val entriesToDelete = if (_debugModeEnabled.value) {
                selectedDayEntries.value.filter {
                    it.categoryName == "Egyéb" && it.subCategory == groupName
                }
            } else {
                todayEntries.value.filter {
                    it.categoryName == "Egyéb" && it.subCategory == groupName
                }
            }

            entriesToDelete.forEach { entry ->
                dao.delete(entry)
            }

            loadEgyebEntriesByGroup()
            reloadSelectedDayEntries()
        }
    }

    fun deleteAllEgyebGroups() {
        viewModelScope.launch {
            val entriesToDelete = todayEntries.value.filter {
                it.categoryName == "Egyéb"
            }
            entriesToDelete.forEach { entry ->
                dao.delete(entry)
            }

            loadEgyebEntriesByGroup()
        }
    }

    // ========== MULTI-SELECT ÉS ÁTHELYEZÉS ==========

    /**
     * Visszaadja az összes elérhető kategóriát (CATEGORIES listából, kivéve Egyéb)
     */
    fun getAvailableCategories(): List<String> {
        return CATEGORIES.filter { it != "Egyéb" }
    }

    /**
     * Számok áthelyezése csoportból kategóriába (multi-select)
     * @param entryIds - Kijelölt entry ID-k
     * @param targetCategory - Célkategória neve
     */
    suspend fun moveEntriesToCategory(entryIds: Set<Int>, targetCategory: String) {
        // DEBUG: Mit kaptunk paraméterként?
        android.util.Log.d("MoveEntries", "========== ÁTHELYEZÉS KEZDÉS ==========")
        android.util.Log.d("MoveEntries", "entryIds paraméter: $entryIds")
        android.util.Log.d("MoveEntries", "targetCategory: $targetCategory")

        // Először lekérjük az ÖSSZES entry-t a DAO-ból
        val allCurrentEntries = dao.getAllEntries().first()
        android.util.Log.d("MoveEntries", "Összes entry a DB-ből: ${allCurrentEntries.size} db")
        android.util.Log.d("MoveEntries", "DB entry ID-k: ${allCurrentEntries.map { it.id }}")

        // Konvertáljuk az entryIds-t Long típusúra
        val entryIdsAsLong = entryIds.map { it.toLong() }.toSet()
        android.util.Log.d("MoveEntries", "entryIds Long-ként: $entryIdsAsLong")

        var movedCount = 0
        entryIdsAsLong.forEach { entryId ->
            android.util.Log.d("MoveEntries", "Keresés: ID = $entryId")

            // Keressük meg az entryt ID alapján
            val entry = allCurrentEntries.find { it.id == entryId }

            if (entry != null) {
                android.util.Log.d("MoveEntries", "TALÁLT entry: id=${entry.id}, value=${entry.value}, category=${entry.categoryName}")

                // Új entry létrehozása a célkategóriában
                val movedEntry = entry.copy(
                    id = 0,  // Új ID generálása
                    categoryName = targetCategory,
                    subCategory = null,
                    movedFromGroup = true
                )

                dao.insert(movedEntry)
                android.util.Log.d("MoveEntries", "ÚJ entry beszúrva: $targetCategory kategóriába")

                // Régi entry törlése
                dao.delete(entry)
                android.util.Log.d("MoveEntries", "RÉGI entry törölve")

                movedCount++
            } else {
                android.util.Log.e("MoveEntries", "NEM TALÁLT entry ID-val: $entryId")
            }
        }

        android.util.Log.d("MoveEntries", "Összesen áthelyezve: $movedCount db")
        android.util.Log.d("MoveEntries", "========== ÁTHELYEZÉS VÉGE ==========")

        // UI frissítése
        loadEgyebEntriesByGroup()
        reloadSelectedDayEntries()
    }

    /**
     * Egyetlen szám áthelyezése csoportból kategóriába (drag & drop - később)
     * @param entry - Az entry amit áthelyezünk
     * @param targetCategory - Célkategória neve
     */
    suspend fun moveSingleEntryToCategory(entry: NumberEntry, targetCategory: String) {
        // Új entry létrehozása a célkategóriában
        val movedEntry = entry.copy(
            id = 0,
            categoryName = targetCategory,
            subCategory = null,
            movedFromGroup = true  // ← SÁRGA JELÖLÉS!
        )
        dao.insert(movedEntry)

        // Régi entry törlése
        dao.delete(entry)

        // UI frissítése
        loadEgyebEntriesByGroup()
        reloadSelectedDayEntries()
    }
    // ========== EXCEL EXPORT/IMPORT ==========

    suspend fun exportAllDataToExcel(outputStream: java.io.OutputStream): Result<Int> {
        return try {
            val entries = dao.getAllEntries().first()

            // Workbook létrehozása
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            val sheet = workbook.createSheet("Kieső Adatok")

            // Fejléc sor
            val headerRow = sheet.createRow(0)
            val headerStyle = workbook.createCellStyle()
            val headerFont = workbook.createFont()
            headerFont.bold = true
            headerStyle.setFont(headerFont)

            headerRow.createCell(0).apply {
                setCellValue("ID")
                cellStyle = headerStyle
            }
            headerRow.createCell(1).apply {
                setCellValue("Érték")
                cellStyle = headerStyle
            }
            headerRow.createCell(2).apply {
                setCellValue("Kategória")
                cellStyle = headerStyle
            }
            headerRow.createCell(3).apply {
                setCellValue("Csoport")
                cellStyle = headerStyle
            }
            headerRow.createCell(4).apply {
                setCellValue("Dátum")
                cellStyle = headerStyle
            }

            // Dátum formátum
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            // Adatok írása
            entries.forEachIndexed { index, entry ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(entry.id.toDouble())
                row.createCell(1).setCellValue(entry.value.toDouble())
                row.createCell(2).setCellValue(entry.categoryName)
                row.createCell(3).setCellValue(entry.subCategory ?: "")
                row.createCell(4).setCellValue(dateFormat.format(entry.timestamp))
            }

            // ✅ JAVÍTVA: Oszlopszélesség MANUÁLISAN (AWT nélkül!)
            sheet.setColumnWidth(0, 10 * 256)  // ID - 10 karakter széles
            sheet.setColumnWidth(1, 10 * 256)  // Érték - 10 karakter
            sheet.setColumnWidth(2, 25 * 256)  // Kategória - 25 karakter
            sheet.setColumnWidth(3, 20 * 256)  // Csoport - 20 karakter
            sheet.setColumnWidth(4, 20 * 256)  // Dátum - 20 karakter

            // Fájl írása
            workbook.write(outputStream)
            workbook.close()

            Result.success(entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importDataFromExcel(inputStream: java.io.InputStream): Result<Int> {
        return try {
            var importedCount = 0
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            dao.deleteAll()

            // Első sor a fejléc, kihagyjuk
            for (i in 1 until sheet.physicalNumberOfRows) {
                val row = sheet.getRow(i) ?: continue

                val value = row.getCell(1)?.numericCellValue?.toInt() ?: 0
                val categoryName = row.getCell(2)?.stringCellValue ?: ""
                val subCategory = row.getCell(3)?.stringCellValue?.takeIf { it.isNotBlank() }
                val dateString = row.getCell(4)?.stringCellValue ?: ""

                val timestamp = try {
                    dateFormat.parse(dateString) ?: Date()
                } catch (e: Exception) {
                    Date()
                }

                val entry = NumberEntry(
                    id = 0,
                    value = value,
                    categoryName = categoryName,
                    subCategory = subCategory,
                    movedFrom = null,
                    timestamp = timestamp
                )
                dao.insert(entry)
                importedCount++
            }

            workbook.close()

            loadLastWorkdayData()
            loadEgyebEntriesByGroup()
            delay(100)

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // ========== NAPI MEGJEGYZÉS ==========
    private val _todayNote = MutableStateFlow<String?>(null)
    val todayNote: StateFlow<String?> = _todayNote.asStateFlow()

    init {
        viewModelScope.launch {
            loadLastWorkdayData()
            loadEgyebEntriesByGroup()
            loadTodayNote()  // ← ÚJ!
        }
    }

    private suspend fun loadTodayNote() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayString = dateFormat.format(Date())

        val note = dao.getDailyNote(todayString)
        _todayNote.value = note?.note
    }

    fun saveTodayNote(noteText: String) {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayString = dateFormat.format(Date())

            if (noteText.isBlank()) {
                // Ha üres, töröljük
                dao.deleteDailyNote(todayString)
                _todayNote.value = null
            } else {
                // Mentés
                val dailyNote = DailyNote(
                    date = todayString,
                    note = noteText.trim(),
                    timestamp = Date()
                )
                dao.insertDailyNote(dailyNote)
                _todayNote.value = noteText.trim()
            }
        }
    }

    suspend fun getNoteForDate(date: Date): String? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(date)
        return dao.getDailyNote(dateString)?.note
    }

    // ========== STATISZTIKÁK ==========

    suspend fun getStatisticsForPeriod(startDate: Date, endDate: Date): StatisticsResult {
        val entries = dao.getEntriesForDay(startDate, endDate).first()

        // 1. NAPI ÖSSZESÍTETT KÉSZLET - Dátum szerint csoportosítva
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Naponta kategóriánként ÖSSZEGZÉS (nem felülírás!)
        val dailyStockByDate = mutableMapOf<String, MutableMap<String, Int>>()

        entries.forEach { entry ->
            val dateKey = dateFormat.format(entry.timestamp)

            if (!dailyStockByDate.containsKey(dateKey)) {
                dailyStockByDate[dateKey] = mutableMapOf()
            }

            // ========== JAVÍTÁS: ÖSSZEGZÉS! ==========
            val currentTotal = dailyStockByDate[dateKey]?.get(entry.categoryName) ?: 0
            dailyStockByDate[dateKey]?.set(entry.categoryName, currentTotal + entry.value)
        }

        // ... többi kód változatlan ...


        // 2. DAILYTOTAL OBJEKTUMOK - Változás számítással
        val sortedDates = dailyStockByDate.keys.sorted()
        val dailyTotals = mutableListOf<DailyTotal>()

        // Előző nap készlete kategóriánként
        val previousDayStock = mutableMapOf<String, Int>()
        CATEGORIES.forEach { previousDayStock[it] = 0 }

        sortedDates.forEach { dateKey ->
            val categoryMap = dailyStockByDate[dateKey] ?: emptyMap()
            val date = dateFormat.parse(dateKey) ?: Date()

            // Jelenlegi nap készlete
            val currentTotal = categoryMap.values.sum()

            // Változás számítás: Mai készlet - Tegnapi készlet
            var totalChange = 0
            CATEGORIES.forEach { category ->
                val currentStock = categoryMap[category] ?: previousDayStock[category] ?: 0
                val prevStock = previousDayStock[category] ?: 0

                totalChange += (currentStock - prevStock)

                // Frissítjük az előző nap készletét
                previousDayStock[category] = currentStock
            }

            dailyTotals.add(
                DailyTotal(
                    date = date,
                    total = currentTotal,
                    change = totalChange,
                    categoryBreakdown = categoryMap
                )
            )
        }

        // 3. KATEGÓRIA STATISZTIKÁK
        val categoryStats = CATEGORIES.map { categoryName ->
            // Napi változások kategóriánként
            val categoryDailyChanges = mutableListOf<Int>()
            var previousStock = 0

            sortedDates.forEach { dateKey ->
                val currentStock = dailyStockByDate[dateKey]?.get(categoryName) ?: previousStock
                val change = currentStock - previousStock

                if (previousStock > 0 || currentStock > 0) {  // Csak ha volt aktivitás
                    categoryDailyChanges.add(change)
                }

                previousStock = currentStock
            }

            // Nettó változás (utolsó - első)
            val firstStock = sortedDates.firstOrNull()?.let {
                dailyStockByDate[it]?.get(categoryName) ?: 0
            } ?: 0
            val lastStock = sortedDates.lastOrNull()?.let {
                dailyStockByDate[it]?.get(categoryName) ?: 0
            } ?: 0
            val netChange = lastStock - firstStock

            // Százalék (abszolút értékek alapján)
            val totalAbsoluteChange = CATEGORIES.sumOf { cat ->
                val first = sortedDates.firstOrNull()?.let { dailyStockByDate[it]?.get(cat) ?: 0 } ?: 0
                val last = sortedDates.lastOrNull()?.let { dailyStockByDate[it]?.get(cat) ?: 0 } ?: 0
                kotlin.math.abs(last - first)
            }.coerceAtLeast(1)

            val percentage = (kotlin.math.abs(netChange).toFloat() / totalAbsoluteChange * 100)

            // Napi átlag
            val daysCount = categoryDailyChanges.size.coerceAtLeast(1)
            val dailyAverage = netChange.toFloat() / daysCount

            // Trend számítás (első fél vs. második fél)
            val halfwayPoint = categoryDailyChanges.size / 2
            val firstHalf = categoryDailyChanges.take(halfwayPoint)
            val secondHalf = categoryDailyChanges.drop(halfwayPoint)

            val firstHalfAvg = if (firstHalf.isNotEmpty()) firstHalf.average() else 0.0
            val secondHalfAvg = if (secondHalf.isNotEmpty()) secondHalf.average() else 0.0

            val trend = when {
                // Ha második félben jobban csökken = Javulás (↓ zöld)
                secondHalfAvg < firstHalfAvg - 5 -> TrendDirection.DOWN
                // Ha második félben kevésbé csökken vagy nő = Romlás (↑ piros)
                secondHalfAvg > firstHalfAvg + 5 -> TrendDirection.UP
                else -> TrendDirection.STABLE
            }

            // Legjobb/legrosszabb nap
            val bestDay = categoryDailyChanges.minOrNull() ?: 0  // Legnagyobb csökkenés (legnegatívabb)
            val worstDay = categoryDailyChanges.maxOrNull() ?: 0  // Legnagyobb növekedés (legpozitívabb)

            CategoryStatistics(
                categoryName = categoryName,
                netChange = netChange,
                percentage = percentage,
                dailyAverage = dailyAverage,
                trend = trend,
                bestDay = bestDay,
                worstDay = worstDay
            )
        }

        return StatisticsResult(
            categoryStats = categoryStats,
            dailyTotals = dailyTotals
        )
    }

}  // ← MainViewModel osztály vége


// ========== STATISZTIKÁK DATA CLASS-OK (OSZTÁLYON KÍVÜL!) ==========

data class StatisticsResult(
    val categoryStats: List<CategoryStatistics>,
    val dailyTotals: List<DailyTotal>
)

data class CategoryStatistics(
    val categoryName: String,
    val netChange: Int,  // Nettó változás (negatív = csökkent)
    val percentage: Float,
    val dailyAverage: Float,
    val trend: TrendDirection,
    val bestDay: Int,  // Legnagyobb csökkenés egy napon (legnegatívabb)
    val worstDay: Int  // Legnagyobb növekedés egy napon (legpozitívabb)
)

enum class TrendDirection {
    UP, DOWN, STABLE
}

data class DailyTotal(
    val date: Date,
    val total: Int,  // Jelenlegi készlet aznap
    val change: Int,  // Változás az előző naphoz képest
    val categoryBreakdown: Map<String, Int>
)


// ========== VIEWMODEL FACTORY ==========

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val dao = AppDatabase.getInstance(application).numberEntryDao()
            val settingsManager = SettingsManager(application)
            return MainViewModel(dao, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



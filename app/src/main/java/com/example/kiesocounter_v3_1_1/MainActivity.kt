package com.example.kiesocounter_v3_1_1

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border  // ← ÚJ!
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape  // ← ÚJ!
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckBox          // ← ÚJ!
import androidx.compose.material.icons.filled.Close            // ← ÚJ!
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete           // ← ÚJ!
import androidx.compose.material.icons.filled.DriveFileMove   // ← ÚJ!
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip                            // ← ÚJ!
import androidx.compose.ui.unit.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kiesocounter_v3_1_1.ui.theme.KiesoCounter_v3_1_1Theme
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight

val CATEGORIES = listOf(
    "Teszter kieső",
    "Inline kieső",
    "F.A. kieső",        // ← ÚJ KATEGÓRIA
    "Fedél szorult",
    "Mérnöki döntésre vár",
    "Egyéb"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiesoCounter_v3_1_1Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KiesoCounterApp()
                }
            }
        }
    }
}

@Composable
fun KiesoCounterApp() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as Application
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application))

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(navController = navController, viewModel = viewModel)
        }
        composable("edit/{categoryName}", arguments = listOf(navArgument("categoryName") { type = NavType.StringType })) {
            val categoryName = it.arguments?.getString("categoryName")?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) } ?: ""
            EditScreen(navController = navController, viewModel = viewModel, categoryName = categoryName)
        }
        composable("calendar") {
            CalendarScreen(navController = navController, viewModel = viewModel)
        }
        composable("chart") {
            ChartScreen(navController = navController, viewModel = viewModel)
        }
        composable("monthly-chart") {
            MonthlyChartScreen(navController = navController, viewModel = viewModel)
        }
    }
}

// --- Képernyők --- //

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, viewModel: MainViewModel) {
    val allEntries by viewModel.todayEntries.collectAsState()
    val lastWorkdayEntries by viewModel.lastWorkdayEntries.collectAsState()  // ← ÚJ
    var categoryForAddDialog by remember { mutableStateOf<String?>(null) }
    var showUndoDialog by remember { mutableStateOf(false) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<NumberEntry?>(null) }
    var showExportSuccess by remember { mutableStateOf(false) }
    var showImportSuccess by remember { mutableStateOf<Int?>(null) }
    var showError by remember { mutableStateOf<String?>(null) }


    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedGroupForAdd by remember { mutableStateOf<String?>(null) }
    var groupToEdit by remember { mutableStateOf<String?>(null) }  // ← ÚJ!
    var showDeleteAllGroupsDialog by remember { mutableStateOf(false) }  // ← ÚJ!

    val bingoModeEnabled by viewModel.bingoModeEnabled.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current



    // ========== CSV EXPORT/IMPORT ==========
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val csvContent = viewModel.exportAllDataToCSV()
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                    showExportSuccess = true
                } catch (e: Exception) {
                    showError = "CSV export hiba: ${e.message}"
                }
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val csvContent = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    } ?: ""

                    val result = viewModel.importDataFromCSV(csvContent)
                    result.onSuccess { count ->
                        showImportSuccess = count
                    }.onFailure { error ->
                        showError = "CSV import hiba: ${error.message}"
                    }
                } catch (e: Exception) {
                    showError = "CSV import hiba: ${e.message}"
                }
            }
        }
    }

// ========== EXCEL EXPORT/IMPORT ==========
    val exportExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        val result = viewModel.exportAllDataToExcel(outputStream)
                        result.onSuccess {
                            showExportSuccess = true
                        }.onFailure { error ->
                            showError = "Excel export hiba: ${error.message}"
                        }
                    }
                } catch (e: Exception) {
                    showError = "Excel export hiba: ${e.message}"
                }
            }
        }
    }

    val importExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val result = viewModel.importDataFromExcel(inputStream)
                        result.onSuccess { count ->
                            showImportSuccess = count
                        }.onFailure { error ->
                            showError = "Excel import hiba: ${error.message}"
                        }
                    }
                } catch (e: Exception) {
                    showError = "Excel import hiba: ${e.message}"
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.5f)) {
                Text("Menü", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Divider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Naptár") },
                    label = { Text("Naptár") },
                    selected = false,
                    onClick = { navController.navigate("calendar"); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Napi Grafikon") },
                    label = { Text("Napi Grafikon") },
                    selected = false,
                    onClick = { navController.navigate("chart"); scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Havi Grafikon") },
                    label = { Text("Havi Grafikon") },
                    selected = false,
                    onClick = { navController.navigate("monthly-chart"); scope.launch { drawerState.close() } }
                )

                Divider()

                // BINGÓ mód kapcsoló
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎯", style = MaterialTheme.typography.titleLarge)
                        Column {
                            Text(
                                "BINGÓ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Mód",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Switch(
                        checked = bingoModeEnabled,
                        onCheckedChange = { viewModel.toggleBingoMode() }
                    )
                }

                Divider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Admin") },
                    label = { Text("ADMIN", color = Color.Red) },
                    selected = false,
                    onClick = { showAdminDialog = true; scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kieso Counter (Mai nap)") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // ÚJ: MainScreen-ben mindig mai napra állítjuk a kontextust
               // LaunchedEffect(Unit) {
               //     viewModel.resetContextDate()
              //  }
                LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                    items(CATEGORIES) { categoryName ->
                        val categoryEntries = allEntries.filter { it.categoryName == categoryName }

                        if (categoryName == "Egyéb") {
                            // ÚJ: Speciális megjelenítés az Egyéb kategóriának
                            val groups by viewModel.egyebSubCategories.collectAsState()

                            CategoryViewEgyeb(
                                entries = categoryEntries,
                                groups = groups,
                                lastWorkdayEntries = lastWorkdayEntries,
                                bingoModeEnabled = bingoModeEnabled,
                                onCreateGroup = { showCreateGroupDialog = true },
                                onAddToGroup = { groupName ->
                                    selectedGroupForAdd = groupName
                                    categoryForAddDialog = categoryName
                                },
                                onEditClick = {
                                    val encodedCategoryName = URLEncoder.encode(categoryName, StandardCharsets.UTF_8.name())
                                    navController.navigate("edit/$encodedCategoryName")
                                },
                                onEntryLongClick = { entryToEdit = it },
                                onEditGroup = { groupName ->  // ← ÚJ!
                                    groupToEdit = groupName
                                },
                                onDeleteAllGroups = {  // ← ÚJ!
                                    showDeleteAllGroupsDialog = true
                                },
                                viewModel = viewModel  // ← ÚJ!
                            )
                        } else {
                            // Normál kategória megjelenítés
                            CategoryView(
                                categoryName = categoryName,
                                entries = categoryEntries.reversed(),
                                lastWorkdayEntries = lastWorkdayEntries,
                                bingoModeEnabled = bingoModeEnabled,
                                onAddClick = { categoryForAddDialog = categoryName },
                                onEditClick = {
                                    val encodedCategoryName = URLEncoder.encode(categoryName, StandardCharsets.UTF_8.name())
                                    navController.navigate("edit/$encodedCategoryName")
                                },
                                onEntryLongClick = { entryToEdit = it }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = { showUndoDialog = true }, enabled = allEntries.isNotEmpty()) {
                        Text("Visszavonás")
                    }
                }
            }
        }
    }

    categoryForAddDialog?.let { categoryName ->
        var smartButtons by remember { mutableStateOf<List<Int>>(emptyList()) }

        // Betöltjük az okos gombokat
        LaunchedEffect(categoryName) {
            smartButtons = viewModel.getTopThreeNumbers(categoryName)
        }

        // ÚJ: Csak az AKTUÁLIS CSOPORT számai (0-ák nélkül!)
        val currentNumbers = if (categoryName == "Egyéb" && selectedGroupForAdd != null) {
            allEntries
                .filter {
                    it.categoryName == categoryName &&
                            it.subCategory == selectedGroupForAdd &&
                            it.value > 0  // ← 0-ák kiszűrése!
                }
                .map { it.value }
        } else {
            allEntries
                .filter { it.categoryName == categoryName }
                .map { it.value }
        }

        AddNumberDialog(
            categoryName = categoryName,
            groupName = selectedGroupForAdd,  // ← ÚJ paraméter!
            currentNumbers = currentNumbers,  // ← Ez a HELYES érték!
            smartButtons = smartButtons,
            onDismissRequest = {
                categoryForAddDialog = null
                selectedGroupForAdd = null
            },
            onConfirmation = { number, shouldClose ->
                if (categoryName == "Egyéb" && selectedGroupForAdd != null) {
                    viewModel.addEntryWithSubCategory(number, categoryName, selectedGroupForAdd)
                } else {
                    viewModel.addEntry(number, categoryName)
                }

                if (shouldClose) {
                    categoryForAddDialog = null
                    selectedGroupForAdd = null
                }
            }
        )
    }

    entryToEdit?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismissRequest = { entryToEdit = null },
            onModify = { viewModel.updateEntry(it); entryToEdit = null },
            onDelete = { viewModel.deleteEntry(it); entryToEdit = null }
        )
    }

    if (showUndoDialog) {
        UndoConfirmationDialogStable(
            onDismissRequest = { showUndoDialog = false },
            onConfirmation = { viewModel.undoLastEntry(); showUndoDialog = false }
        )
    }

    val debugModeEnabled by viewModel.debugModeEnabled.collectAsState()  // ← ÚJ!

    // MainScreen-ben az AdminDialog hívásánál add hozzá:

    if (showAdminDialog) {
        AdminDialog(
            onDismissRequest = { showAdminDialog = false },
            onDeleteToday = { viewModel.deleteTodayEntries(); showAdminDialog = false },
            onGenerateYesterday = { viewModel.generateTestData(1); showAdminDialog = false },
            onGenerateWeek = { viewModel.generateTestData(7); showAdminDialog = false },
            onGenerateToday = { viewModel.generateTodayData(); showAdminDialog = false },
            onDeleteAll = { viewModel.deleteAllEntries(); showAdminDialog = false },
            onExportCSV = {  // ← ÚJ!
                exportCsvLauncher.launch("kiesocounter_backup_${System.currentTimeMillis()}.csv")
                showAdminDialog = false
            },
            onExportExcel = {  // ← ÚJ!
                exportExcelLauncher.launch("kiesocounter_backup_${System.currentTimeMillis()}.xlsx")
                showAdminDialog = false
            },
            onImportCSV = {  // ← ÚJ!
                importCsvLauncher.launch("text/csv")
                showAdminDialog = false
            },
            onImportExcel = {  // ← ÚJ!
                importExcelLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                showAdminDialog = false
            },
            onReloadData = {
                scope.launch {
                    viewModel.loadLastWorkdayData()
                }
                showAdminDialog = false
            },
            onToggleDebugMode = { viewModel.toggleDebugMode() },
            debugModeEnabled = debugModeEnabled
        )
    }

    // Export sikeres
    if (showExportSuccess) {
        AlertDialog(
            onDismissRequest = { showExportSuccess = false },
            title = { Text("Sikeres export") },
            text = { Text("Az adatok sikeresen exportálva lettek!") },
            confirmButton = { TextButton(onClick = { showExportSuccess = false }) { Text("OK") } }
        )
    }

    // Csoport létrehozása dialógus
    if (showCreateGroupDialog) {
        var previousGroups by remember { mutableStateOf<List<String>>(emptyList()) }

        // Betöltjük az előző nap csoportjait
        LaunchedEffect(Unit) {
            previousGroups = viewModel.getPreviousDaySubCategories()
        }

        CreateGroupDialog(
            previousGroups = previousGroups,
            onDismissRequest = { showCreateGroupDialog = false },
            onCreateGroup = { groupName ->
                viewModel.createEmptyGroup(groupName)  // ← ÚJ! Egyszerűbb!
                showCreateGroupDialog = false
            },
            onImportGroups = { groupNames ->
                viewModel.createEmptyGroups(groupNames)  // ← ÚJ! Egyszerűbb!
                showCreateGroupDialog = false
            }
        )
    }

    // Import sikeres
    showImportSuccess?.let { count ->
        AlertDialog(
            onDismissRequest = { showImportSuccess = null },
            title = { Text("Sikeres import") },
            text = { Text("$count bejegyzés importálva!") },
            confirmButton = { TextButton(onClick = { showImportSuccess = null }) { Text("OK") } }
        )
    }

    // Csoport szerkesztése dialógus
    groupToEdit?.let { groupName ->
        EditGroupDialog(
            groupName = groupName,
            onDismissRequest = { groupToEdit = null },
            onRename = { newName ->
                viewModel.renameSubCategory(groupName, newName)
                groupToEdit = null
            },
            onDelete = {
                viewModel.deleteGroup(groupName)
                groupToEdit = null
            }
        )
    }

// Minden csoport törlése megerősítés
    if (showDeleteAllGroupsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllGroupsDialog = false },
            title = { Text("Minden csoport törlése") },
            text = {
                Text("Biztosan törölni szeretnéd az ÖSSZES csoportot az Egyéb kategóriából?\n\nAz összes szám törlődik!")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllEgyebGroups()
                        showDeleteAllGroupsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Törlés")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllGroupsDialog = false }) {
                    Text("Mégse")
                }
            }
        )
    }

    // Hibaüzenet
    showError?.let { error ->
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("Hiba") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { showError = null }) { Text("OK") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(navController: NavController, viewModel: MainViewModel, categoryName: String) {
    val allEntries by viewModel.todayEntries.collectAsState()
    val entries = allEntries.filter { it.categoryName == categoryName }
    var entryToEdit by remember { mutableStateOf<NumberEntry?>(null) }

    entryToEdit?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismissRequest = { entryToEdit = null },
            onModify = { viewModel.updateEntry(it); entryToEdit = null },
            onDelete = { viewModel.deleteEntry(it); entryToEdit = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$categoryName szerkesztése (Mai nap)") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(entries.reversed()) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = entry.value.toString(), style = MaterialTheme.typography.bodyLarge)
                    Row {
                        Button(onClick = { entryToEdit = entry }) { Text("Módosítás") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { viewModel.deleteEntry(entry) }) { Text("Törlés") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController, viewModel: MainViewModel) {
    val selectedDateEntries by viewModel.selectedDayEntries.collectAsState()
    val daysWithData by viewModel.daysWithData.collectAsState()
    val debugModeEnabled by viewModel.debugModeEnabled.collectAsState()

    var lastWorkdayEntries by remember { mutableStateOf<List<NumberEntry>>(emptyList()) }
    var entryToEdit by remember { mutableStateOf<NumberEntry?>(null) }

    // ÚJ: Egyéb kategória állapotok
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedGroupForAdd by remember { mutableStateOf<String?>(null) }
    var categoryForAddDialog by remember { mutableStateOf<String?>(null) }
    var groupToEdit by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Naptár állapot
    val calendarState = io.github.boguszpawlowski.composecalendar.rememberSelectableCalendarState(
        initialMonth = java.time.YearMonth.now(),
        initialSelection = listOf(LocalDate.now())
    )

    // Kiválasztott dátum lekérése
    val selectedDate = calendarState.selectionState.selection.firstOrNull()

    // Betöltjük a napokat ahol van adat az aktuális hónapban
    LaunchedEffect(calendarState.monthState.currentMonth) {
        val yearMonth = calendarState.monthState.currentMonth
        viewModel.loadDaysWithDataForMonth(
            yearMonth.year,
            yearMonth.monthValue - 1
        )
    }

    // Amikor kiválasztunk egy napot, betöltjük az adatokat
    LaunchedEffect(selectedDate) {
        selectedDate?.let { date ->
            val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val javaDate = Date(millis)

            // ÚJ SORREND - ELŐSZÖR a selectedDate, UTÁNA a contextDate!
            viewModel.loadEntriesForSelectedDate(millis)  // ← ELŐSZÖR EZ!
            viewModel.setContextDate(javaDate)            // ← UTÁNA EZ!

            // Előző munkanap adatai
            scope.launch {
                lastWorkdayEntries = viewModel.getLastWorkdayBeforeDate(javaDate)
            }
        }
    }

// ÚJ: Amikor elhagyjuk a CalendarScreen-t, reseteljük a kontextust
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetContextDate()  // ← ÚJ! Reset mai napra
        }
    }

    // ÚJ: Csoportok a kiválasztott naphoz
    val groups by viewModel.egyebSubCategories.collectAsState()

    // Dialógus a szám szerkesztéséhez
    entryToEdit?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismissRequest = { entryToEdit = null },
            onModify = { viewModel.updateEntry(it); entryToEdit = null },
            onDelete = { viewModel.deleteEntry(it); entryToEdit = null }
        )
    }

    // ÚJ: Csoport létrehozása dialógus
    if (showCreateGroupDialog) {
        var previousGroups by remember { mutableStateOf<List<String>>(emptyList()) }

        LaunchedEffect(Unit) {
            previousGroups = viewModel.getPreviousDaySubCategories()
        }

        CreateGroupDialog(
            previousGroups = previousGroups,
            onDismissRequest = { showCreateGroupDialog = false },
            onCreateGroup = { groupName ->
                viewModel.createEmptyGroup(groupName)
                showCreateGroupDialog = false
            },
            onImportGroups = { groupNames ->
                viewModel.createEmptyGroups(groupNames)
                showCreateGroupDialog = false
            }
        )
    }

    // ÚJ: Szám hozzáadása dialógus
    categoryForAddDialog?.let { categoryName ->
        var smartButtons by remember { mutableStateOf<List<Int>>(emptyList()) }

        LaunchedEffect(categoryName) {
            smartButtons = viewModel.getTopThreeNumbers(categoryName)
        }

        // ÚJ: Csak az AKTUÁLIS CSOPORT számai (0-ák nélkül!)
        val currentNumbers = if (categoryName == "Egyéb" && selectedGroupForAdd != null) {
            selectedDateEntries
                .filter {
                    it.categoryName == categoryName &&
                            it.subCategory == selectedGroupForAdd &&
                            it.value > 0  // ← 0-ák kiszűrése!
                }
                .map { it.value }
        } else {
            selectedDateEntries
                .filter { it.categoryName == categoryName }
                .map { it.value }
        }

        AddNumberDialog(
            categoryName = categoryName,
            groupName = selectedGroupForAdd,  // ← ÚJ paraméter!
            currentNumbers = currentNumbers,  // ← Ez a HELYES érték!
            smartButtons = smartButtons,
            onDismissRequest = {
                categoryForAddDialog = null
                selectedGroupForAdd = null
            },
            onConfirmation = { number, shouldClose ->
                if (categoryName == "Egyéb" && selectedGroupForAdd != null) {
                    viewModel.addEntryWithSubCategory(number, categoryName, selectedGroupForAdd)
                } else {
                    viewModel.addEntry(number, categoryName)
                }

                if (shouldClose) {
                    categoryForAddDialog = null
                    selectedGroupForAdd = null
                }
            }
        )
    }

    // ÚJ: Csoport szerkesztése dialógus
    groupToEdit?.let { groupName ->
        EditGroupDialog(
            groupName = groupName,
            onDismissRequest = { groupToEdit = null },
            onRename = { newName ->
                viewModel.renameSubCategory(groupName, newName)
                groupToEdit = null
            },
            onDelete = {
                viewModel.deleteGroup(groupName)
                groupToEdit = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Előzmények")
                        if (debugModeEnabled) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "🔧 DEBUG",
                                fontSize = 12.sp,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ComposeCalendar
            io.github.boguszpawlowski.composecalendar.SelectableCalendar(
                modifier = Modifier.fillMaxWidth(),
                firstDayOfWeek = java.time.DayOfWeek.MONDAY,
                calendarState = calendarState,
                monthHeader = { monthState ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            calendarState.monthState.currentMonth =
                                calendarState.monthState.currentMonth.minusMonths(1)
                        }) {
                            Text("<", style = MaterialTheme.typography.headlineSmall)
                        }

                        Text(
                            text = "${monthState.currentMonth.month.name} ${monthState.currentMonth.year}",
                            style = MaterialTheme.typography.titleLarge
                        )

                        IconButton(onClick = {
                            calendarState.monthState.currentMonth =
                                calendarState.monthState.currentMonth.plusMonths(1)
                        }) {
                            Text(">", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                },
                dayContent = { dayState ->
                    val hasData = daysWithData.contains(dayState.date)
                    val isSelected = dayState.isFromCurrentMonth &&
                            dayState.date == selectedDate

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(2.dp)
                            .clickable(enabled = dayState.isFromCurrentMonth) {
                                calendarState.selectionState.selection = listOf(dayState.date)
                            }
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayState.date.dayOfMonth.toString(),
                            fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !dayState.isFromCurrentMonth -> Color.Gray
                                hasData -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )

            // Kiválasztott nap adatai
            selectedDate?.let { date ->
                val formattedDate = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())
                    .format(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Adatok a(z) $formattedDate napra:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(16.dp))

                    if (selectedDateEntries.isEmpty()) {
                        Text("Nincsenek adatok ezen a napon.")
                    } else {
                        CATEGORIES.forEach { categoryName ->
                            val categoryEntries = selectedDateEntries.filter {
                                it.categoryName == categoryName
                            }
                            if (categoryEntries.isNotEmpty()) {
                                if (categoryName == "Egyéb") {
                                    // Csoportnevek kinyerése a selected date entries-ből
                                    val displayGroups = categoryEntries
                                        .mapNotNull { it.subCategory }
                                        .distinct()
                                        .sorted()

                                    CategoryViewEgyeb(
                                        entries = categoryEntries,
                                        groups = displayGroups,
                                        lastWorkdayEntries = lastWorkdayEntries,
                                        bingoModeEnabled = false,
                                        onCreateGroup = if (debugModeEnabled) {
                                            { showCreateGroupDialog = true }
                                        } else {
                                            {}
                                        },
                                        onAddToGroup = if (debugModeEnabled) {
                                            { groupName ->
                                                selectedGroupForAdd = groupName
                                                categoryForAddDialog = categoryName
                                            }
                                        } else {
                                            {}
                                        },
                                        onEditClick = {},  // Szerkesztés továbbra is disabled
                                        onEntryLongClick = { entry ->
                                            if (debugModeEnabled) {
                                                entryToEdit = entry
                                            }
                                        },
                                        onEditGroup = if (debugModeEnabled) {
                                            { groupName -> groupToEdit = groupName }
                                        } else {
                                            {}
                                        },
                                        onDeleteAllGroups = {},
                                        viewModel = viewModel  // ← ÚJ!
// Továbbra is disabled
                                    )
                                } else {
                                    // Normál kategóriák
                                    CategoryView(
                                        categoryName = categoryName,
                                        entries = categoryEntries.reversed(),
                                        lastWorkdayEntries = lastWorkdayEntries,
                                        bingoModeEnabled = false,
                                        onAddClick = if (debugModeEnabled) {
                                            { categoryForAddDialog = categoryName }
                                        } else {
                                            {}
                                        },
                                        onEditClick = {},  // Disabled
                                        onEntryLongClick = { entry ->
                                            if (debugModeEnabled) {
                                                entryToEdit = entry
                                            }
                                        }
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(navController: NavController, viewModel: MainViewModel) {
    val categoryTotals by viewModel.categoryTotalsToday.collectAsState()
    val modelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(categoryTotals) {
        val chartEntries = CATEGORIES.mapIndexed { index, category ->
            entryOf(index.toFloat(), categoryTotals[category] ?: 0f)
        }
        modelProducer.setEntries(chartEntries)
    }

    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        CATEGORIES.getOrNull(value.toInt()) ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Napi Grafikon") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        if (categoryTotals.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nincsenek adatok a mai napon a grafikonhoz.")
            }
        } else {
            Chart(
                modifier = Modifier.padding(padding).padding(16.dp),
                chart = columnChart(),
                chartModelProducer = modelProducer,
                bottomAxis = com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis(valueFormatter = bottomAxisValueFormatter)
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyChartScreen(navController: NavController, viewModel: MainViewModel) {
    val monthlyData by viewModel.monthlyChartData.collectAsState()
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    if (monthlyData.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Havi Diagram") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nincsenek adatok ebben a hónapban.")
            }
        }
        return
    }

    val modelProducer = remember { ChartEntryModelProducer() }

    // Színek definiálása a kategóriákhoz
    val categoryColors = listOf(
        Color(0xFFE57373), // Piros - Teszter kieső
        Color(0xFF64B5F6), // Kék - Inline kieső
        Color(0xFFFF9800), // Narancs - F.A. kieső  ← ÚJ
        Color(0xFF81C784), // Zöld - Fedél szorult
        Color(0xFFFFD54F), // Sárga - Mérnöki döntésre vár
        Color(0xFFBA68C8)  // Lila - Egyéb
    )

    LaunchedEffect(monthlyData) {
        val sortedData = monthlyData.sortedBy { it.dayOfMonth }

        val seriesList = CATEGORIES.map { category ->
            sortedData.map { day ->
                entryOf(day.dayOfMonth.toFloat(), day.categoryTotals[category]?.toFloat() ?: 0f)
            }
        }

        modelProducer.setEntries(seriesList)
    }

    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        value.toInt().toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Havi Halmozott Diagram") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Jelmagyarázat
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text("Jelmagyarázat:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                CATEGORIES.forEachIndexed { index, category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    color = categoryColors[index],
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(category, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Kiválasztott nap adatainak megjelenítése
            selectedDay?.let { day ->
                val dayData = monthlyData.find { it.dayOfMonth == day }
                dayData?.let { data ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$day. nap részletei:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { selectedDay = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Bezár",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            CATEGORIES.forEachIndexed { index, category ->
                                val value = data.categoryTotals[category] ?: 0
                                if (value > 0) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(
                                                    color = categoryColors[index],
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "$category:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "$value db",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Divider()
                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Napi összesen:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${data.categoryTotals.values.sum()} db",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Grafikon
            Box(modifier = Modifier.weight(1f)) {
                Chart(
                    modifier = Modifier.fillMaxSize(),
                    chart = columnChart(
                        mergeMode = com.patrykandpatrick.vico.core.chart.column.ColumnChart.MergeMode.Stack,
                        columns = listOf(
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = categoryColors[0].hashCode(),
                                thicknessDp = 16f
                            ),
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = categoryColors[1].hashCode(),
                                thicknessDp = 16f
                            ),
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = categoryColors[2].hashCode(),
                                thicknessDp = 16f
                            ),
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = categoryColors[3].hashCode(),
                                thicknessDp = 16f
                            ),
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = categoryColors[4].hashCode(),
                                thicknessDp = 16f
                            ),
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = categoryColors[5].hashCode(),
                                thicknessDp = 16f
                            )
                        )
                    ),
                    chartModelProducer = modelProducer,
                    bottomAxis = com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis(
                        valueFormatter = bottomAxisValueFormatter
                    ),
                    startAxis = com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis()
                )
            }

            // Tippek a használathoz
            Text(
                text = "Tipp: Kattints egy napszámra az oszlop alatt a részletekért!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Napok kiválasztására szolgáló gombok
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(monthlyData.sortedBy { it.dayOfMonth }.size) { index ->
                    val dayData = monthlyData.sortedBy { it.dayOfMonth }[index]
                    FilterChip(
                        selected = selectedDay == dayData.dayOfMonth,
                        onClick = {
                            selectedDay = if (selectedDay == dayData.dayOfMonth) null else dayData.dayOfMonth
                        },
                        label = { Text("${dayData.dayOfMonth}.") }
                    )
                }
            }
        }
    }
}

// --- Komponensek és Dialógusok --- //

@Composable
fun AdminDialog(
    onDismissRequest: () -> Unit,
    onDeleteToday: () -> Unit,
    onGenerateYesterday: () -> Unit,
    onGenerateWeek: () -> Unit,
    onGenerateToday: () -> Unit = {},
    onDeleteAll: () -> Unit,
    onExportCSV: () -> Unit = {},      // ← ÚJ!
    onExportExcel: () -> Unit = {},    // ← ÚJ!
    onImportCSV: () -> Unit = {},      // ← ÚJ!
    onImportExcel: () -> Unit = {},    // ← ÚJ!
    onReloadData: () -> Unit = {},
    onToggleDebugMode: () -> Unit = {},
    debugModeEnabled: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Admin Funkciók") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // DEBUG MÓD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔧", style = MaterialTheme.typography.titleLarge)
                        Column {
                            Text(
                                "DEBUG MÓD",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Múltbeli napok szerkesztése",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Switch(
                        checked = debugModeEnabled,
                        onCheckedChange = { onToggleDebugMode() }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                // ========== EXPORT SZAKASZ ==========
                Text("📤 Export (Mentés)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onExportCSV,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("📄 CSV Export (Gyors biztonsági mentés)")
                }
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onExportExcel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("📊 Excel Export (Szerkeszthető)")
                }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                // ========== IMPORT SZAKASZ ==========
                Text("📥 Import (Betöltés)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onImportCSV,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("📄 CSV Import")
                }
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onImportExcel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("📊 Excel Import")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onReloadData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text("🔄 Adatok újratöltése")
                }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                // Teszt adatok szakasz
                Text("Teszt adatok", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                Button(onClick = onDeleteToday, modifier = Modifier.fillMaxWidth()) {
                    Text("Mai adatok törlése")
                }
                Spacer(Modifier.height(8.dp))

                Button(onClick = onGenerateToday, modifier = Modifier.fillMaxWidth()) {
                    Text("Mai nap feltöltése (random)")
                }
                Spacer(Modifier.height(8.dp))

                Button(onClick = onGenerateYesterday, modifier = Modifier.fillMaxWidth()) {
                    Text("Tegnapi nap feltöltése (random)")
                }
                Spacer(Modifier.height(8.dp))

                Button(onClick = onGenerateWeek, modifier = Modifier.fillMaxWidth()) {
                    Text("Elmúlt 7 nap feltöltése (random)")
                }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                // Veszélyes műveletek
                Text("⚠️ Veszélyes műveletek", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Red)
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onDeleteAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("MINDEN ADAT TÖRLÉSE")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("Bezárás") } }
    )
}

// JAVÍTOTT CategoryView - MainActivty.kt-be kerül
// Cseréld le a meglévő CategoryView függvényt erre!

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryView(
    categoryName: String,
    entries: List<NumberEntry>,
    lastWorkdayEntries: List<NumberEntry>,
    bingoModeEnabled: Boolean = false,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit,
    onEntryLongClick: (NumberEntry) -> Unit
) {
    // Mai összeg
    val todayTotal = entries.sumOf { it.value }

    // Utolsó munkanap összege erre a kategóriára
    val lastWorkdayTotal = lastWorkdayEntries
        .filter { it.categoryName == categoryName }
        .sumOf { it.value }

    // Utolsó munkanap számai erre a kategóriára
    val lastWorkdayNumbers = lastWorkdayEntries
        .filter { it.categoryName == categoryName }
        .map { it.value }

    // Mai számok
    val todayNumbers = entries.map { it.value }

    // Különbség megjelenítésének állapota kategóriánként
    var showDifference by remember { mutableStateOf(false) }

    // ÚJ: Számoljuk hányszor szerepel minden szám tegnap
    val remainingCounts = remember(lastWorkdayNumbers, todayNumbers) {
        mutableStateMapOf<Int, Int>().apply {
            // Először összeszámoljuk tegnapi számokat
            lastWorkdayNumbers.forEach { number ->
                this[number] = (this[number] ?: 0) + 1
            }
        }
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // + gomb BALRA
            Button(onClick = onAddClick) {
                Text("+")
            }
            Spacer(Modifier.width(8.dp))
            // Szerkesztés gomb is BALRA
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés")
            }
            Spacer(Modifier.width(8.dp))
            // Kategória név - elfoglalja a maradék helyet
            Text(
                text = categoryName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Text("Nincsenek bevitt számok.", style = MaterialTheme.typography.bodyLarge)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                entries.forEach { entry ->
                    Box(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onEntryLongClick(entry) }
                            )
                            .padding(horizontal = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {  // ← ÚJ: Column!
                            Text(
                                text = "${entry.value},",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (entry.movedFromGroup) {
                                    Color(0xFFFFEB3B)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )

                            // ========== ÚJ: KÉK PÖTTY ==========
                            if (entry.note != null) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .offset(y = (-2).dp)  // Közelebb a számhoz
                                        .background(
                                            color = Color(0xFF2196F3),  // Kék
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // BINGÓ mód - JAVÍTOTT LOGIKA
        if (bingoModeEnabled && lastWorkdayNumbers.isNotEmpty()) {
            Text(
                text = "Előző: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // ÚJ LOGIKA: Számoljuk hányszor került be már minden szám ma
            val usedTodayCounts = mutableMapOf<Int, Int>()
            todayNumbers.forEach { num ->
                usedTodayCounts[num] = (usedTodayCounts[num] ?: 0) + 1
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Végigmegyünk a tegnapi számokon SORRENDBEN
                lastWorkdayNumbers.forEach { yesterdayNumber ->
                    // Van-e még felhasználható match?
                    val availableToday = usedTodayCounts[yesterdayNumber] ?: 0
                    val hasMatch = availableToday > 0

                    // Ha van match, "használjuk fel" egyet
                    if (hasMatch) {
                        usedTodayCounts[yesterdayNumber] = availableToday - 1
                    }

                    Text(
                        text = "$yesterdayNumber,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasMatch) {
                            Color(0xFF4CAF50) // Zöld ha van match (BINGÓ!)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) // Halvány
                        },
                        fontWeight = if (hasMatch) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Összesen sor TREND IKONNAL
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Összesen: $todayTotal db")

            // Trend ikon logika
            when {
                // Van előző adat
                lastWorkdayEntries.isNotEmpty() && lastWorkdayTotal > 0 -> {
                    val difference = todayTotal - lastWorkdayTotal

                    Box(
                        modifier = Modifier.clickable { showDifference = !showDifference }
                    ) {
                        when {
                            difference > 0 -> {
                                // Több kieső = piros felfelé
                                Text("▲", color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                                if (showDifference) {
                                    Text(
                                        text = "+$difference",
                                        color = Color.Red,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        modifier = Modifier.offset(x = 12.dp, y = (-4).dp)
                                    )
                                }
                            }
                            difference < 0 -> {
                                // Kevesebb kieső = zöld lefelé
                                Text("▼", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyLarge)
                                if (showDifference) {
                                    Text(
                                        text = "$difference",
                                        color = Color(0xFF4CAF50),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        modifier = Modifier.offset(x = 12.dp, y = (-4).dp)
                                    )
                                }
                            }
                            else -> {
                                // Egyenlő = kék egyenlőség
                                Text("=", color = Color(0xFF2196F3), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                // Nincs előző adat
                lastWorkdayEntries.isEmpty() -> {
                    Text("⚠", color = Color(0xFFFFC107), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryViewEgyeb(
    entries: List<NumberEntry>,
    groups: List<String>,
    lastWorkdayEntries: List<NumberEntry>,
    bingoModeEnabled: Boolean = false,
    onCreateGroup: () -> Unit,
    onAddToGroup: (String) -> Unit,
    onEditClick: () -> Unit,
    onEntryLongClick: (NumberEntry) -> Unit,
    onEditGroup: (String) -> Unit,
    onDeleteAllGroups: () -> Unit,
    viewModel: MainViewModel
) {
    // ========== STATE A CATEGORYVIEWEGYEB SZINTJÉN ==========
    var activeGroupName by remember { mutableStateOf<String?>(null) }
    var selectedEntryIds by remember { mutableStateOf(setOf<Int>()) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Csoportok nélküli számok
    val ungroupedEntries = entries.filter { it.subCategory == null }

    // ... (többi kód változatlan)

    // Csoportonkénti számok
    val groupedEntries = entries
        .filter { it.subCategory != null }
        .groupBy { it.subCategory!! }

    // Utolsó munkanap számai az Egyéb kategóriában
    val lastWorkdayNumbers = lastWorkdayEntries
        .filter { it.categoryName == "Egyéb" }
        .map { it.value }

    Column {
        // FEJLÉC
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Új csoport gomb (🏷️ ikon helyett)
            Button(
                onClick = onCreateGroup,
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("🏷️", fontSize = 20.sp)
            }

            Spacer(Modifier.width(8.dp))

            // Szerkesztés gomb
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés")
            }

            Spacer(Modifier.width(8.dp))

            // Kategória név
            Text(
                text = "Egyéb",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // CSOPORTOK
        if (groups.isEmpty()) {
            Text(
                "Nincsenek csoportok.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCreateGroup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Új csoport létrehozása")
            }
        } else {
            // ========== CSOPORTOK MEGJELENÍTÉSE ==========
            groups.forEach { groupName ->
                EgyebGroupCard(
                    groupName = groupName,
                    entries = groupedEntries[groupName] ?: emptyList(),
                    onAddToGroup = { onAddToGroup(groupName) },
                    onEditGroup = { onEditGroup(groupName) },
                    onEntryLongClick = onEntryLongClick,
                    isActiveGroup = activeGroupName == groupName,  // ← ÚJ!
                    onSelectionModeChanged = { isActive, entryIds ->  // ← ÚJ!
                        if (isActive) {
                            activeGroupName = groupName
                            selectedEntryIds = entryIds
                        } else {
                            activeGroupName = null
                            selectedEntryIds = setOf()
                        }
                    },
                    onMoveRequested = {  // ← ÚJ!
                        showMoveToCategoryDialog = true
                    },
                    viewModel = viewModel  // ← ÚJ PARAMÉTER!
                )
                Spacer(Modifier.height(8.dp))
            }

            // Új csoport gomb
            Button(
                onClick = onCreateGroup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Új csoport")
            }

            Spacer(Modifier.height(4.dp))

            // Minden csoport törlése gomb
            if (groups.isNotEmpty()) {
                Button(
                    onClick = onDeleteAllGroups,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f)
                    )
                ) {
                    Text("🗑️ Minden csoport törlése")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Csoportosítatlan számok (ha vannak) - 0-ákat kiszűrjük
        val filteredUngroupedEntries = ungroupedEntries.filter { it.value > 0 }
        if (filteredUngroupedEntries.isNotEmpty()) {
            Text(
                "❓ Csoportosítatlan:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filteredUngroupedEntries.reversed().forEach { entry ->
                    Box(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onEntryLongClick(entry) }
                            )
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${entry.value},",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // BINGÓ mód (ha engedélyezve)
        if (bingoModeEnabled && lastWorkdayNumbers.isNotEmpty()) {
            val todayNumbers = entries.map { it.value }

            Text(
                text = "Előző: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            val usedTodayCounts = mutableMapOf<Int, Int>()
            todayNumbers.forEach { num ->
                usedTodayCounts[num] = (usedTodayCounts[num] ?: 0) + 1
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                lastWorkdayNumbers.forEach { yesterdayNumber ->
                    val availableToday = usedTodayCounts[yesterdayNumber] ?: 0
                    val hasMatch = availableToday > 0

                    if (hasMatch) {
                        usedTodayCounts[yesterdayNumber] = availableToday - 1
                    }

                    Text(
                        text = "$yesterdayNumber,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasMatch) {
                            Color(0xFF4CAF50)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                        fontWeight = if (hasMatch) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // Teljes összesítés
        val totalSum = entries.sumOf { it.value }
        Text(
            "📊 Teljes összesen: $totalSum db",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
    // CategoryViewEgyeb végén, KÍVÜL a Column-on:
    if (showMoveToCategoryDialog && activeGroupName != null) {
        val availableCategories = viewModel.getAvailableCategories()

        // ========== KRITIKUS: CAPTURE A VÁLTOZÓT! ==========
        val capturedSelectedIds = selectedEntryIds  // ← ÚJ SOR!

        android.util.Log.d("CategoryViewEgyeb", "Dialógus - selectedEntryIds: $selectedEntryIds")
        android.util.Log.d("CategoryViewEgyeb", "Dialógus - capturedSelectedIds: $capturedSelectedIds")  // ← ÚJ LOG!

        AlertDialog(
            onDismissRequest = { showMoveToCategoryDialog = false },
            title = { Text("Áthelyezés kategóriába") },
            text = {
                Column {
                    Text(
                        "${capturedSelectedIds.size} szám kijelölve",  // ← VÁLTOZOTT!
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Válassz kategóriát:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))

                    availableCategories.forEach { category ->
                        Button(
                            onClick = {
                                android.util.Log.d("CategoryViewEgyeb", "Gomb - capturedSelectedIds: $capturedSelectedIds")  // ← VÁLTOZOTT!
                                scope.launch {
                                    viewModel.moveEntriesToCategory(capturedSelectedIds, category)  // ← VÁLTOZOTT!
                                    android.widget.Toast.makeText(
                                        context,
                                        "${capturedSelectedIds.size} szám áthelyezve: $category",  // ← VÁLTOZOTT!
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                selectedEntryIds = setOf()
                                activeGroupName = null
                                showMoveToCategoryDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(category)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToCategoryDialog = false }) {
                    Text("Mégse")
                }
            }
        )
    }
}

// ========== ÚJ COMPOSABLE: EGYÉB GROUP CARD MULTI-SELECT GOMBOKKAL ==========
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EgyebGroupCard(
    groupName: String,
    entries: List<NumberEntry>,
    onAddToGroup: () -> Unit,
    onEditGroup: () -> Unit,
    onEntryLongClick: (NumberEntry) -> Unit,
    isActiveGroup: Boolean,
    onSelectionModeChanged: (Boolean, Set<Int>) -> Unit,
    onMoveRequested: () -> Unit,
    viewModel: MainViewModel  // ← ÚJ PARAMÉTER!
) {
    // ========== LOKÁLIS STATE ==========
    var isSelectionMode by remember { mutableStateOf(false) }
    var localSelectedIds by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // ← TÖRÖLD a `val viewModel: MainViewModel = viewModel()` sort!

    // ... (többi kód változatlan)

    // State frissítés amikor selection mode változik
    LaunchedEffect(isSelectionMode, localSelectedIds) {
        onSelectionModeChanged(isSelectionMode, localSelectedIds)
    }

    // 0-ás értékek kiszűrése
    val filteredEntries = entries.filter { it.value > 0 }
    val groupTotal = filteredEntries.sumOf { it.value }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ========== FEJLÉC ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onEditGroup() }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Bal oldal - Cím
                Text(
                    text = "🏷️ $groupName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Jobb oldal - Gombok (40dp méret!)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isSelectionMode) {
                        // ========== KIJELÖLÉS AKTÍV ==========

                        // 1. Bezárás gomb (X)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable {
                                    isSelectionMode = false
                                    localSelectedIds = setOf()  // ← JAVÍTVA!
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kijelölés vége",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. Áthelyezés gomb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (localSelectedIds.isNotEmpty())  // ← JAVÍTVA!
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = localSelectedIds.isNotEmpty()) {  // ← JAVÍTVA!
                                    onMoveRequested()  // ← JAVÍTVA!
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "Áthelyezés kategóriába",
                                tint = if (localSelectedIds.isNotEmpty())  // ← JAVÍTVA!
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 3. Törlés gomb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (localSelectedIds.isNotEmpty())  // ← JAVÍTVA!
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = localSelectedIds.isNotEmpty()) {  // ← JAVÍTVA!
                                    showDeleteConfirmDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Kijelöltek törlése",
                                tint = if (localSelectedIds.isNotEmpty())  // ← JAVÍTVA!
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                    } else {
                        // ========== NORMÁL MÓD ==========

                        // 1. Multi-select aktiváló gomb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    isSelectionMode = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBox,
                                contentDescription = "Kijelölés",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. Hozzáadás gomb
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                                .clickable { onAddToGroup() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ========== SZÁMOK GRID - TISZTA VERZIÓ ==========
            if (filteredEntries.isEmpty()) {
                Text(
                    "Nincsenek számok",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredEntries.reversed().forEach { entry ->
                        val isSelected = localSelectedIds.contains(entry.id.toInt())

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            localSelectedIds = if (isSelected) {
                                                localSelectedIds - entry.id.toInt()
                                            } else {
                                                localSelectedIds + entry.id.toInt()
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            onEntryLongClick(entry)
                                        }
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {  // ← ÚJ: Column!
                                Text(
                                    text = "${entry.value},",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (entry.movedFromGroup) {
                                        Color(0xFFFFEB3B)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                // ========== ÚJ: KÉK PÖTTY ==========
                                if (entry.note != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .offset(y = (-2).dp)
                                            .background(
                                                color = Color(0xFF2196F3),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Összesen: $groupTotal db",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    // ========== TÖRLÉS MEGERŐSÍTŐ DIALÓGUS ==========
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Számok törlése") },
            text = {
                Text("Biztosan törölni szeretnéd a kijelölt ${localSelectedIds.size} számot?")  // ← JAVÍTVA!
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            localSelectedIds.forEach { entryId ->  // ← JAVÍTVA!
                                val entry = entries.find { it.id.toInt() == entryId }
                                entry?.let { viewModel.deleteEntry(it) }
                            }
                            android.widget.Toast.makeText(
                                context,
                                "${localSelectedIds.size} szám törölve",  // ← JAVÍTVA!
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        localSelectedIds = setOf()  // ← JAVÍTVA!
                        isSelectionMode = false
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Törlés")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Mégse")
                }
            }
        )
    }
}


@Composable
fun EditEntryDialog(
    entry: NumberEntry,
    onDismissRequest: () -> Unit,
    onModify: (NumberEntry) -> Unit,
    onDelete: (NumberEntry) -> Unit
) {
    var newNumberInput by remember { mutableStateOf(entry.value.toString()) }
    var noteInput by remember { mutableStateOf(entry.note ?: "") }  // ← ÚJ!
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Törlés megerősítése") },
            text = { Text("Biztosan törölni szeretnéd a(z) '${entry.value}' értéket?") },
            confirmButton = {
                TextButton(onClick = { onDelete(entry); showDeleteConfirmation = false }) {
                    Text("Igen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Nem")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "'${entry.value}' szerkesztése") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Szám szerkesztése
                OutlinedTextField(
                    value = newNumberInput,
                    onValueChange = { newNumberInput = it },
                    label = { Text("Új érték") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // ========== ÚJ: MEGJEGYZÉS MEZŐ ==========
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Megjegyzés (opcionális)") },
                    placeholder = { Text("pl. \"Kétszer futott át\"") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                // Kis tipp
                if (noteInput.isBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "💡 A megjegyzéssel ellátott számok kék pöttyel lesznek jelölve",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { showDeleteConfirmation = true }) {
                    Text("Törlés")
                }
                Row {
                    TextButton(onClick = onDismissRequest) {
                        Text("Mégse")
                    }
                    TextButton(
                        onClick = {
                            newNumberInput.toIntOrNull()?.let { newValue ->
                                val updatedEntry = entry.copy(
                                    value = newValue,
                                    note = noteInput.ifBlank { null }  // ← ÚJ!
                                )
                                onModify(updatedEntry)
                            }
                        }
                    ) {
                        Text("Módosítás")
                    }
                }
            }
        }
    )
}

@Composable
fun AddNumberDialog(
    categoryName: String,
    groupName: String? = null,  // ← ÚJ paraméter!
    currentNumbers: List<Int>,
    smartButtons: List<Int>,
    onDismissRequest: () -> Unit,
    onConfirmation: (Int, Boolean) -> Unit
) {
    var numberInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ÚJ: Dinamikus cím
    val dialogTitle = if (groupName != null) {
        "Szám hozzáadása: $categoryName - $groupName"
    } else {
        "Szám hozzáadása: $categoryName"
    }

    AlertDialog(
        title = { Text(dialogTitle) },  // ← Dinamikus cím!
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // ÚJ: Csak akkor jelenítünk meg "Legutóbbi számok"-at, ha vannak (és nem 0-k!)
                if (currentNumbers.isNotEmpty()) {
                    Text(
                        text = "Legutóbb hozzáadott elemek: " + currentNumbers.take(8).reversed().joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { numberInput = it },
                    label = { Text("Szám") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester)
                )
                Spacer(Modifier.height(16.dp))
                Text("Gyorsgombok", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                // Felső sor - FIX gombok
                val fixButtons = listOf(1, 16, 100)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { onConfirmation(fixButtons[0], false) }) { Text("1") }
                    Button(onClick = { onConfirmation(fixButtons[1], false) }) { Text("16") }
                    Button(onClick = { onConfirmation(fixButtons[2], false) }) { Text("100") }
                }
                Spacer(Modifier.height(8.dp))

                // Alsó sor - OKOS gombok
                val defaultButtons = listOf(14, 50, 80)
                val bottomButtons = if (smartButtons.size >= 3) {
                    smartButtons.take(3)
                } else {
                    defaultButtons
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { onConfirmation(bottomButtons[0], false) }) {
                        Text(bottomButtons[0].toString())
                    }
                    Button(onClick = { onConfirmation(bottomButtons[1], false) }) {
                        Text(bottomButtons[1].toString())
                    }
                    Button(onClick = { onConfirmation(bottomButtons[2], false) }) {
                        Text(bottomButtons[2].toString())
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = { numberInput.toIntOrNull()?.let { onConfirmation(it, true) } }) { Text("Ok") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Mégse") } },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 0.dp
    )

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

@Composable
fun UndoConfirmationDialogStable(onDismissRequest: () -> Unit, onConfirmation: () -> Unit) {
    AlertDialog(
        title = { Text("Visszavonás") },
        text = { Text("Biztosan törölni szeretnéd az utoljára bevitt számot?") },
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onConfirmation) { Text("Igen") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Nem") } }
    )
}

@Composable
fun CreateGroupDialog(
    previousGroups: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onImportGroups: (List<String>) -> Unit
) {
    var newGroupName by remember { mutableStateOf("") }
    var selectedPreviousGroups by remember { mutableStateOf(setOf<String>()) }
    var showPreviousGroups by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Csoport létrehozása") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Új csoport név
                Text("Új csoport neve:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Csoport neve") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Előző csoportok import
                if (previousGroups.isNotEmpty()) {
                    Divider()
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📋 Előző nap csoportjai:",
                            style = MaterialTheme.typography.titleSmall
                        )

                        IconButton(
                            onClick = { showPreviousGroups = !showPreviousGroups },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(if (showPreviousGroups) "▼" else "▶")
                        }
                    }

                    if (showPreviousGroups) {
                        Spacer(Modifier.height(8.dp))

                        previousGroups.forEach { groupName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPreviousGroups = if (selectedPreviousGroups.contains(groupName)) {
                                            selectedPreviousGroups - groupName
                                        } else {
                                            selectedPreviousGroups + groupName
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedPreviousGroups.contains(groupName),
                                    onCheckedChange = { checked ->
                                        selectedPreviousGroups = if (checked) {
                                            selectedPreviousGroups + groupName
                                        } else {
                                            selectedPreviousGroups - groupName
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(groupName)
                            }
                        }

                        if (selectedPreviousGroups.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onImportGroups(selectedPreviousGroups.toList())
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("✓ ${selectedPreviousGroups.size} csoport létrehozása")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newGroupName.isNotBlank()) {
                        onCreateGroup(newGroupName.trim())
                    }
                },
                enabled = newGroupName.isNotBlank()
            ) {
                Text("Létrehozás")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Mégse")
            }
        }
    )
}

@Composable
fun EditGroupDialog(
    groupName: String,
    onDismissRequest: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var newName by remember { mutableStateOf(groupName) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Csoport törlése") },
            text = {
                Text("Biztosan törölni szeretnéd a(z) '$groupName' csoportot?\n\nA csoportban lévő összes szám törlődik!")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("Törlés")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Mégse")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("'$groupName' szerkesztése") },
        text = {
            Column {
                Text(
                    "Csoport átnevezése:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Új név") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { showDeleteConfirmation = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("🗑️ Törlés")
                }

                Row {
                    TextButton(onClick = onDismissRequest) {
                        Text("Mégse")
                    }
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank() && newName != groupName) {
                                onRename(newName.trim())
                            } else {
                                onDismissRequest()
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Átnevezés")
                    }
                }
            }
        }
    )
}

private class FakeMainViewModel : MainViewModel(object : NumberEntryDao {
    override suspend fun insert(entry: NumberEntry) {}
    override suspend fun update(entry: NumberEntry) {}
    override suspend fun delete(entry: NumberEntry) {}
    override fun getAllEntries(): Flow<List<NumberEntry>> = MutableStateFlow(emptyList())
    override fun getEntriesForDay(startOfDay: Date, endOfDay: Date): Flow<List<NumberEntry>> =
        MutableStateFlow(emptyList())
    override fun getEntriesForMonth(startOfMonth: Date, endOfMonth: Date): Flow<List<NumberEntry>> =
        MutableStateFlow(emptyList())
    override suspend fun getEntryById(id: Long): NumberEntry? = null
    override suspend fun getLastEntry(): NumberEntry? = null
    override suspend fun deleteEntriesSince(startOfDay: Date) {}
    override suspend fun deleteAll() {}
    override suspend fun getDaysWithDataInMonth(yearMonth: String): List<String> = emptyList()
    override suspend fun getSubCategoriesForEgyeb(): List<String> = emptyList()

    // ========== EGYÉB CSOPORTOK ==========
    //override suspend fun insertGroup(group: EgyebGroup) {}
    //override suspend fun updateGroup(group: EgyebGroup) {}
    //override suspend fun deleteGroup(group: EgyebGroup) {}
    //override fun getAllGroups(): Flow<List<EgyebGroup>> = MutableStateFlow(emptyList())
    //override suspend fun getGroupByName(groupName: String): EgyebGroup? = null
    //override suspend fun deleteAllGroups() {}
})


@Preview(showBackground = true)
@Composable
fun KiesoCounterAppPreview() {
    KiesoCounter_v3_1_1Theme {
        MainScreen(
            navController = rememberNavController(),
            viewModel = FakeMainViewModel()
        )
    }
}
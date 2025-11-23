package com.example.kiesocounter_v3_1_1

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

val CATEGORIES = listOf(
    "Teszter kieső",
    "Inline kieső",
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
    var categoryForAddDialog by remember { mutableStateOf<String?>(null) }
    var showUndoDialog by remember { mutableStateOf(false) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<NumberEntry?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
                LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                    items(CATEGORIES) { categoryName ->
                        val categoryEntries = allEntries.filter { it.categoryName == categoryName }
                        CategoryView(
                            categoryName = categoryName,
                            entries = categoryEntries.reversed(),
                            onAddClick = { categoryForAddDialog = categoryName },
                            onEditClick = {
                                val encodedCategoryName = URLEncoder.encode(categoryName, StandardCharsets.UTF_8.name())
                                navController.navigate("edit/$encodedCategoryName")
                            },
                            onEntryLongClick = { entryToEdit = it }
                        )
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
        AddNumberDialog(
            categoryName = categoryName,
            currentNumbers = allEntries.filter { it.categoryName == categoryName }.map { it.value },
            onDismissRequest = { categoryForAddDialog = null },
            onConfirmation = { number, shouldClose ->
                viewModel.addEntry(number, categoryName)
                if (shouldClose) {
                    categoryForAddDialog = null
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

    if (showAdminDialog) {
        AdminDialog(
            onDismissRequest = { showAdminDialog = false },
            onDeleteToday = { viewModel.deleteTodayEntries(); showAdminDialog = false },
            onGenerateYesterday = { viewModel.generateTestData(1); showAdminDialog = false },
            onGenerateWeek = { viewModel.generateTestData(7); showAdminDialog = false },
            onDeleteAll = { viewModel.deleteAllEntries(); showAdminDialog = false }
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
    val datePickerState = rememberDatePickerState()
    val selectedDateEntries by viewModel.selectedDayEntries.collectAsState()

    LaunchedEffect(datePickerState.selectedDateMillis) {
        viewModel.loadEntriesForSelectedDate(datePickerState.selectedDateMillis)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Előzmények") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            DatePicker(state = datePickerState)

            datePickerState.selectedDateMillis?.let { millis ->
                val formattedDate = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault()).format(Date(millis))
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Adatok a(z) $formattedDate napra:", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))

                    if (selectedDateEntries.isEmpty()) {
                        Text("Nincsenek adatok ezen a napon.")
                    } else {
                        CATEGORIES.forEach { categoryName ->
                            val categoryEntries = selectedDateEntries.filter { it.categoryName == categoryName }
                            if (categoryEntries.isNotEmpty()) {
                                CategoryView(
                                    categoryName = categoryName,
                                    entries = categoryEntries.reversed(),
                                    onAddClick = {},
                                    onEditClick = {},
                                    onEntryLongClick = { _ -> }
                                )
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

    // Ha nincs adat, jelenítse meg az üzenetet
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

    // A havi adatokból oszlopok előkészítése kategóriánként
    val modelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(monthlyData) {
        // Napok (x tengely) szerint rendezzük
        val sortedData = monthlyData.sortedBy { it.dayOfMonth }

        // Kategóriákhoz tartozó sorozatok előkészítése
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
        Chart(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            chart = columnChart(
                mergeMode = com.patrykandpatrick.vico.core.chart.column.ColumnChart.MergeMode.Stack
            ),
            chartModelProducer = modelProducer,
            bottomAxis = com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis(
                valueFormatter = bottomAxisValueFormatter
            )
        )
    }
}



// --- Komponensek és Dialógusok --- //

@Composable
fun AdminDialog(
    onDismissRequest: () -> Unit, onDeleteToday: () -> Unit, onGenerateYesterday: () -> Unit, onGenerateWeek: () -> Unit, onDeleteAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Admin Funkciók") },
        text = {
            Column {
                Button(onClick = onDeleteToday, modifier = Modifier.fillMaxWidth()) { Text("Mai adatok törlése") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGenerateYesterday, modifier = Modifier.fillMaxWidth()) { Text("Tegnapi nap feltöltése (random)") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGenerateWeek, modifier = Modifier.fillMaxWidth()) { Text("Elmúlt 7 nap feltöltése (random)") }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDeleteAll, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("MINDEN ADAT TÖRLÉSE") }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("Bezárás") } }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryView(
    categoryName: String,
    entries: List<NumberEntry>,
    onAddClick: () -> Unit,
    onEditClick: () -> Unit,
    onEntryLongClick: (NumberEntry) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = categoryName, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés")
            }
            Button(onClick = onAddClick) { Text("+") }
        }

        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Text("Nincsenek bevitt számok.", style = MaterialTheme.typography.bodyLarge)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entries.forEach { entry ->
                    Box(modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onEntryLongClick(entry) }).padding(horizontal = 4.dp)) {
                        Text(text = "${entry.value},", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Összesen: ${entries.sumOf { it.value }} db")
    }
}

@Composable
fun EditEntryDialog(entry: NumberEntry, onDismissRequest: () -> Unit, onModify: (NumberEntry) -> Unit, onDelete: (NumberEntry) -> Unit) {
    var newNumberInput by remember { mutableStateOf(entry.value.toString()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Törlés megerősítése") },
            text = { Text("Biztosan törölni szeretnéd a(z) '${entry.value}' értéket?") },
            confirmButton = { TextButton(onClick = { onDelete(entry); showDeleteConfirmation = false }) { Text("Igen") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Nem") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "'${entry.value}' szerkesztése") },
        text = { OutlinedTextField(value = newNumberInput, onValueChange = { newNumberInput = it }, label = { Text("Új érték") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showDeleteConfirmation = true }) { Text("Törlés") }
                Row {
                    TextButton(onClick = onDismissRequest) { Text("Mégse") }
                    TextButton(onClick = { newNumberInput.toIntOrNull()?.let { onModify(entry.copy(value = it)) } }) { Text("Módosítás") }
                }
            }
        }
    )
}

@Composable
fun AddNumberDialog(categoryName: String, currentNumbers: List<Int>, onDismissRequest: () -> Unit, onConfirmation: (Int, Boolean) -> Unit) {
    var numberInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        title = { Text("Szám hozzáadása: $categoryName") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (currentNumbers.isNotEmpty()) {
                    Text(text = "Legutóbb hozzáadott elemek: " + currentNumbers.take(8).reversed().joinToString(", "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Spacer(Modifier.height(16.dp))
                }
                OutlinedTextField(value = numberInput, onValueChange = { numberInput = it }, label = { Text("Szám") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.focusRequester(focusRequester))
                Spacer(Modifier.height(16.dp))
                Text("Gyorsgombok", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val quickAddNumbers = listOf(1, 16, 100, 14, 50, 80)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { onConfirmation(quickAddNumbers[0], false) }) { Text("1") }
                    Button(onClick = { onConfirmation(quickAddNumbers[1], false) }) { Text("16") }
                    Button(onClick = { onConfirmation(quickAddNumbers[2], false) }) { Text("100") }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { onConfirmation(quickAddNumbers[3], false) }) { Text("14") }
                    Button(onClick = { onConfirmation(quickAddNumbers[4], false) }) { Text("50") }
                    Button(onClick = { onConfirmation(quickAddNumbers[5], false) }) { Text("80") }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = { numberInput.toIntOrNull()?.let { onConfirmation(it, true) } }) { Text("Ok") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Mégse") } }
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

class FakeMainViewModel : MainViewModel(dao = object : NumberEntryDao {
    override suspend fun insert(entry: NumberEntry) {}
    override suspend fun update(entry: NumberEntry) {}
    override suspend fun delete(entry: NumberEntry) {}
    override fun getAllEntries(): Flow<List<NumberEntry>> = MutableStateFlow(emptyList())
    override fun getEntriesForDay(startOfDay: Date, endOfDay: Date): Flow<List<NumberEntry>> = MutableStateFlow(emptyList())
    override fun getEntriesForMonth(startOfMonth: Date, endOfMonth: Date): Flow<List<NumberEntry>> = MutableStateFlow(emptyList())
    override suspend fun getEntryById(id: Long): NumberEntry? = null
    override suspend fun deleteEntriesSince(startOfDay: Date) {}
    override suspend fun deleteAll() {}
    override suspend fun getLastEntry(): NumberEntry? = null
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
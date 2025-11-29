package com.example.kiesocounter_v3_1_1

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ========== DATA CLASS-OK ==========

data class StatisticsPeriod(
    val label: String,
    val startDate: Date,
    val endDate: Date
)



// ========== FŐ KÉPERNYŐ ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    // Időszakok definíciója
    val periods = remember {
        val now = Calendar.getInstance()

        val last7Days = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val last30Days = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
        val startOfMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }

        listOf(
            StatisticsPeriod("Utolsó 7 nap", last7Days.time, now.time),
            StatisticsPeriod("Utolsó 30 nap", last30Days.time, now.time),
            StatisticsPeriod("Aktuális hónap", startOfMonth.time, now.time)
        )
    }

    var selectedPeriodIndex by remember { mutableIntStateOf(0) }
    val selectedPeriod = periods[selectedPeriodIndex]

    // Adatok betöltése
    var statisticsData by remember { mutableStateOf<StatisticsResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedPeriodIndex) {
        isLoading = true
        try {
            statisticsData = viewModel.getStatisticsForPeriod(
                startDate = selectedPeriod.startDate,
                endDate = selectedPeriod.endDate
            )
        } catch (e: Exception) {
            // Hiba esetén null marad
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statisztikák") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Időszak választó
                PeriodSelector(
                    periods = periods,
                    selectedIndex = selectedPeriodIndex,
                    onPeriodSelected = { selectedPeriodIndex = it }
                )

                // Tartalom
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        if (statisticsData != null && statisticsData!!.dailyTotals.isNotEmpty()) {
                            SummaryCards(statisticsData!!)
                            Spacer(modifier = Modifier.height(16.dp))
                            PieChartCard(statisticsData!!)
                            Spacer(modifier = Modifier.height(16.dp))
                            CategoryStatisticsTable(statisticsData!!)
                            Spacer(modifier = Modifier.height(16.dp))
                            DailyBreakdownChart(statisticsData!!)
                            Spacer(modifier = Modifier.height(16.dp))
                            RecordsSection(statisticsData!!)
                        } else {
                            // Nincs adat
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Nincs adat erre az időszakra",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Adj hozzá készlet adatokat a főképernyőn!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========== IDŐSZAK VÁLASZTÓ ==========

@Composable
fun PeriodSelector(
    periods: List<StatisticsPeriod>,
    selectedIndex: Int,
    onPeriodSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        periods.forEachIndexed { index, period ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onPeriodSelected(index) },
                label = { Text(period.label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ========== ÖSSZESÍTŐ KÁRTYÁK ==========

@Composable
fun SummaryCards(data: StatisticsResult) {
    // OPCIÓ B: Jelenlegi készlet (utolsó nap)
    val currentStock = data.dailyTotals.lastOrNull()?.total ?: 0

    // Napi átlag változás
    val totalChange = data.dailyTotals.sumOf { it.change }
    val daysCount = data.dailyTotals.size.coerceAtLeast(1)
    val dailyAverageChange = totalChange.toFloat() / daysCount

    // Legjobb nap (legnagyobb csökkenés = legnegatívabb szám)
    val bestDay = data.dailyTotals.minByOrNull { it.change }

    // Legrosszabb nap (legnagyobb növekedés = legpozitívabb szám)
    val worstDay = data.dailyTotals.maxByOrNull { it.change }

    val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                title = "Jelenlegi készlet",
                value = currentStock.toString(),
                subtitle = "db összesen",
                color = if (currentStock > 0) Color(0xFFFF9800) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Átlag változás",
                value = if (dailyAverageChange < 0) {
                    String.format("%.1f", dailyAverageChange)
                } else {
                    String.format("+%.1f", dailyAverageChange)
                },
                subtitle = "db/nap",
                color = if (dailyAverageChange < 0) Color(0xFF4CAF50) else Color(0xFFE57373),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                title = "Legjobb nap",
                value = bestDay?.change?.toString() ?: "0",
                subtitle = bestDay?.let { dateFormat.format(it.date) } ?: "-",
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Legrosszabb nap",
                value = if ((worstDay?.change ?: 0) > 0) {
                    "+${worstDay?.change}"
                } else {
                    worstDay?.change?.toString() ?: "0"
                },
                subtitle = worstDay?.let { dateFormat.format(it.date) } ?: "-",
                color = Color(0xFFE57373),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

// ========== KÖRDIAGRAM (OPCIÓ C: Legnagyobb nettó csökkenés) ==========

@Composable
fun PieChartCard(data: StatisticsResult) {
    val categoryColors = mapOf(
        "Teszter kieső" to Color(0xFFE57373),
        "Inline kieső" to Color(0xFF64B5F6),
        "F.A. kieső" to Color(0xFFFFB74D),
        "Fedél szorult" to Color(0xFF81C784),
        "Mérnöki döntésre vár" to Color(0xFFFFD54F),
        "Egyéb" to Color(0xFFBA68C8),
        // Régi kategóriák (ha esetleg vannak még)

    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Nettó változás kategóriánként",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Negatív = csökkenés ✅, Pozitív = növekedés ❌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            SimplePieChart(data = data, colors = categoryColors)
        }
    }
}

@Composable
fun SimplePieChart(
    data: StatisticsResult,
    colors: Map<String, Color>
) {
    // OPCIÓ C: Nettó változás (legnagyobb csökkenés)
    val totalAbsoluteChange = data.categoryStats.sumOf { abs(it.netChange) }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.categoryStats
            .filter { it.netChange != 0 }
            .sortedBy { it.netChange }  // Legnegatívabb elől
            .forEach { stat ->
                val percentage = (abs(stat.netChange).toFloat() / totalAbsoluteChange * 100)
                val displayValue = if (stat.netChange < 0) {
                    stat.netChange.toString()
                } else {
                    "+${stat.netChange}"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                colors[stat.categoryName] ?: Color.Gray,
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stat.categoryName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$displayValue (${String.format("%.1f%%", percentage)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (stat.netChange < 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                }
                LinearProgressIndicator(
                    progress = { percentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = colors[stat.categoryName] ?: Color.Gray,
                )
            }
    }
}

// ========== KATEGÓRIA STATISZTIKÁK TÁBLÁZAT ==========

@Composable
fun CategoryStatisticsTable(data: StatisticsResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Részletes statisztikák",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Fejléc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Kategória", Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Változás", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Napi átlag", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Trend", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Adatok
            data.categoryStats
                .filter { it.netChange != 0 }
                .sortedBy { it.netChange }
                .forEach { stat ->
                    val displayChange = if (stat.netChange < 0) {
                        stat.netChange.toString()
                    } else {
                        "+${stat.netChange}"
                    }

                    val displayAverage = if (stat.dailyAverage < 0) {
                        String.format("%.1f", stat.dailyAverage)
                    } else {
                        String.format("+%.1f", stat.dailyAverage)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stat.categoryName, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            displayChange,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (stat.netChange < 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                        Text(
                            displayAverage,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        val (icon, color) = when (stat.trend) {
                            TrendDirection.DOWN -> Icons.Default.ArrowDownward to Color(0xFF4CAF50)  // Javulás!
                            TrendDirection.UP -> Icons.Default.ArrowUpward to Color(0xFFE57373)  // Romlás!
                            TrendDirection.STABLE -> Icons.Default.ArrowForward to Color(0xFF2196F3)
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).weight(0.5f),
                            tint = color
                        )
                    }
                }
        }
    }
}

// ========== NAPI BONTÁS GRAFIKON ==========

@Composable
fun DailyBreakdownChart(data: StatisticsResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Napi változás (utolsó 10 nap)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Negatív = csökkent ✅, Pozitív = nőtt ❌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            SimpleDailyChart(data = data.dailyTotals.takeLast(10))
        }
    }
}

@Composable
fun SimpleDailyChart(data: List<DailyTotal>) {
    val maxAbsValue = data.maxOfOrNull { abs(it.change) } ?: 1
    val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEach { daily ->
            val displayChange = if (daily.change < 0) {
                daily.change.toString()
            } else {
                "+${daily.change}"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(daily.date),
                    modifier = Modifier.width(50.dp),
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { abs(daily.change).toFloat() / maxAbsValue },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    color = if (daily.change < 0) Color(0xFF4CAF50) else Color(0xFFE57373),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayChange,
                    modifier = Modifier.width(60.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (daily.change < 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                )
            }
        }
    }
}

// ========== REKORDOK ==========

@Composable
fun RecordsSection(data: StatisticsResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Rekordok",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Legnagyobb csökkenés kategóriánként egy napon
            Text("Legnagyobb csökkenés egy napon:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            data.categoryStats
                .filter { it.bestDay < 0 }
                .sortedBy { it.bestDay }
                .forEach { stat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stat.categoryName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stat.bestDay.toString(),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

            Spacer(modifier = Modifier.height(16.dp))

            // Leghosszabb "zöld" sorozat (folyamatos csökkenés)
            val consecutiveDays = calculateConsecutiveDays(data.dailyTotals)
            Text("Leghosszabb javulási sorozat:", fontWeight = FontWeight.Bold)
            Text(
                "$consecutiveDays egymást követő nap csökkent a készlet",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun calculateConsecutiveDays(dailyTotals: List<DailyTotal>): Int {
    if (dailyTotals.isEmpty()) return 0

    val sortedDays = dailyTotals.sortedBy { it.date }
    var maxStreak = 0
    var currentStreak = 0

    for (i in sortedDays.indices) {
        if (sortedDays[i].change < 0) {  // Csökkenés = jó
            currentStreak++
            if (i > 0) {
                val prevDate = Calendar.getInstance().apply { time = sortedDays[i - 1].date }
                val currDate = Calendar.getInstance().apply { time = sortedDays[i].date }
                val daysDiff = ((currDate.timeInMillis - prevDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

                if (daysDiff > 1 || sortedDays[i - 1].change >= 0) {
                    currentStreak = 1
                }
            }
            maxStreak = maxOf(maxStreak, currentStreak)
        } else {
            currentStreak = 0
        }
    }

    return maxStreak
}
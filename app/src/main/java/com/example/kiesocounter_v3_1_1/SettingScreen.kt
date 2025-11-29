package com.example.kiesocounter_v3_1_1

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beállítások") },
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
                .padding(16.dp)
        ) {
            // ========== MEGJELENÉS SZAKASZ ==========
            Text(
                "🎨 MEGJELENÉS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            // 1. DIALÓGUS ÁTLÁTSZÓSÁG
            Text(
                "Dialógus átlátszóság",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = settings.dialogOpacity,
                        onValueChange = { viewModel.settingsManager.updateDialogOpacity(it) },
                        valueRange = 0.0f..1.0f,  // ← VÁLTOZOTT! 0-100%
                        steps = 19,  // ← ÚJ! 20 lépés (0%, 5%, 10%, ... 100%)
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(settings.dialogOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(50.dp)
                    )
                }

                // Alapértelmezett jelölés
                Text(
                    "Alapértelmezett: 80%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // 2. BETŰMÉRET
            Text(
                "Betűméret",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))

            Column {
                FontSizeOption.values().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.fontSize == option,
                            onClick = { viewModel.settingsManager.updateFontSize(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 3. SÖTÉT MÓD
            Text(
                "Sötét mód",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))

            Column {
                DarkModeOption.values().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.darkMode == option,
                            onClick = { viewModel.settingsManager.updateDarkMode(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (option) {
                                DarkModeOption.LIGHT -> "Világos"
                                DarkModeOption.DARK -> "Sötét"
                                DarkModeOption.SYSTEM -> "Rendszer szerinti"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Divider()
            Spacer(Modifier.height(32.dp))

            // ========== FUNKCIÓK SZAKASZ ==========
            Text(
                "⚙️ FUNKCIÓK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            // 4. OKOS GOMBOK IDŐTARTAM
            Text(
                "Okos gombok időtartam",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Mennyi időre vizsgálja a gyakori számokat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            var expandedSmartButtons by remember { mutableStateOf(false) }
            val smartButtonsOptions = listOf(1, 7, 14, 30)

            ExposedDropdownMenuBox(
                expanded = expandedSmartButtons,
                onExpandedChange = { expandedSmartButtons = it }
            ) {
                OutlinedTextField(
                    value = "${settings.smartButtonsDays} nap",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedSmartButtons) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedSmartButtons,
                    onDismissRequest = { expandedSmartButtons = false }
                ) {
                    smartButtonsOptions.forEach { days ->
                        DropdownMenuItem(
                            text = { Text("$days nap") },
                            onClick = {
                                viewModel.settingsManager.updateSmartButtonsDays(days)
                                expandedSmartButtons = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 5. UTOLSÓ MUNKANAP KERESÉSI MÉLYSÉG
            Text(
                "Utolsó munkanap keresése",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Hány napra visszamenőleg keresse az előző munkanapot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            var expandedWorkday by remember { mutableStateOf(false) }
            val workdayOptions = listOf(7, 14, 30, 60)

            ExposedDropdownMenuBox(
                expanded = expandedWorkday,
                onExpandedChange = { expandedWorkday = it }
            ) {
                OutlinedTextField(
                    value = "${settings.lastWorkdaySearchDepth} nap",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedWorkday) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedWorkday,
                    onDismissRequest = { expandedWorkday = false }
                ) {
                    workdayOptions.forEach { days ->
                        DropdownMenuItem(
                            text = { Text("$days nap") },
                            onClick = {
                                viewModel.settingsManager.updateLastWorkdayDepth(days)
                                expandedWorkday = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Divider()
            Spacer(Modifier.height(32.dp))

            // ========== INFORMÁCIÓ SZAKASZ ==========
            Text(
                "ℹ️ INFORMÁCIÓ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Verzió: 3.1.1",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
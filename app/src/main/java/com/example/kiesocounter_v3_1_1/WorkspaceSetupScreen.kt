package com.example.kiesocounter_v3_1_1

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

/**
 * Workspace Setup Screen
 *
 * Itt lehet:
 * - Új csapat létrehozása
 * - Csatlakozás meglévő csapathoz (invite code-dal)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSetupScreen(
    navController: NavController,
    workspaceViewModel: WorkspaceViewModel = viewModel()
) {
    // State-ek
    var workspaceName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var showQRDialog by remember { mutableStateOf(false) }

    val isLoading by workspaceViewModel.isLoading.collectAsState()
    val errorMessage by workspaceViewModel.errorMessage.collectAsState()
    val currentWorkspace by workspaceViewModel.currentWorkspace.collectAsState()




    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspace beállítás") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ═══════════════════════════════════════════════════════════
            // 1. ÚJ CSAPAT LÉTREHOZÁSA
            // ═══════════════════════════════════════════════════════════
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Új csapat létrehozása",
                        style = MaterialTheme.typography.titleLarge
                    )

                    OutlinedTextField(
                        value = workspaceName,
                        onValueChange = { workspaceName = it },
                        label = { Text("Csapat neve") },
                        placeholder = { Text("pl. DAR Csapat Nappalos") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )

                    Button(
                        onClick = {
                            if (workspaceName.isNotBlank()) {
                                workspaceViewModel.createWorkspace(workspaceName)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && workspaceName.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Csapat létrehozása")
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // 2. CSATLAKOZÁS MEGLÉVŐ CSAPATHOZ
            // ═══════════════════════════════════════════════════════════
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Csatlakozás csapathoz",
                        style = MaterialTheme.typography.titleLarge
                    )

                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase() },
                        label = { Text("Megosztási kód") },
                        placeholder = { Text("pl. ABC123") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (inviteCode.isNotBlank()) {
                                    workspaceViewModel.joinWorkspace(inviteCode)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && inviteCode.length == 6
                        ) {
                            Text("Csatlakozás")
                        }

                        // QR kód beolvasás gomb (később implementáljuk)
                        OutlinedButton(
                            onClick = { /* TODO: QR scanner */ },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.QrCode, "QR kód", modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("QR beolvasás")
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // HIBAÜZENET
            // ═══════════════════════════════════════════════════════════
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { workspaceViewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                }
            }
        } // Column vége
    } // Scaffold vége

    // ═══════════════════════════════════════════════════════════
    // QR KÓD DIALÓG - SCAFFOLD-ON KÍVÜL!
    // ═══════════════════════════════════════════════════════════
    currentWorkspace?.let { workspace ->
        if (showQRDialog) {
            QRCodeDialog(
                inviteCode = workspace.inviteCode,
                onDismiss = {
                    showQRDialog = false
                    navController.navigateUp()
                }
            )
        }
    }
    // ═══════════════════════════════════════════════════════════
    // ÚJ: AKTÍV WORKSPACE MEGJELENÍTÉSE
    // ═══════════════════════════════════════════════════════════
    currentWorkspace?.let { workspace ->
        if (!showQRDialog) {  // NE mutassuk ha a QR dialóg látszik
            AlertDialog(
                onDismissRequest = { navController.navigateUp() },
                title = { Text("Aktív Workspace") },
                text = {
                    Column {
                        Text("Csatlakozva: ${workspace.name}")
                        Spacer(Modifier.height(8.dp))
                        Text("Megosztási kód: ${workspace.inviteCode}")
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showQRDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📱 QR kód megjelenítése")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            workspaceViewModel.leaveWorkspace()
                            navController.navigateUp()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Red
                        )
                    ) {
                        Text("Kilépés a csapatból")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { navController.navigateUp() }) {
                        Text("Vissza")
                    }
                }
            )
        }
    }

} // WorkspaceSetupScreen függvény vége

/**
 * QR kód megjelenítő dialóg
 *
 * Használat:
 *   if (showQRDialog) {
 *       QRCodeDialog(inviteCode = "ABC123", onDismiss = { showQRDialog = false })
 *   }
 */
@Composable
fun QRCodeDialog(
    inviteCode: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(inviteCode) {
        QRCodeGenerator.generateQRCode("KIESO:$inviteCode", 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Megosztási QR kód") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR kód",
                        modifier = Modifier.size(250.dp)
                    )
                }

                Text(
                    text = "Kód: $inviteCode",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Másoknak olvassák be ezt a QR kódot a csatlakozáshoz!",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Bezárás")
            }
        }
    )
}
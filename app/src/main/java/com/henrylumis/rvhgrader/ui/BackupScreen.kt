package com.henrylumis.rvhgrader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.henrylumis.rvhgrader.data.BackupManager
import com.henrylumis.rvhgrader.data.BackupPayload
import com.henrylumis.rvhgrader.model.GradingScale
import com.henrylumis.rvhgrader.model.StudentRecord

/**
 * Exports the whole gradebook (every learner, every class, every term) plus the custom grading
 * scale to a single JSON file the teacher chooses the location for — and can restore from later,
 * including on a different phone. Separate from the silent autosave that runs on every change;
 * this is the "keep a copy somewhere safe" version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    records: List<StudentRecord>,
    gradingScale: GradingScale,
    onOpenMenu: () -> Unit,
    onRestore: (BackupPayload) -> Unit
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupPayload?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                BackupManager.writeTo(context, uri, records, gradingScale)
                statusMessage = "BACKUP SAVED."
            } catch (e: Exception) {
                statusMessage = "BACKUP FAILED: ${e.message}"
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                pendingRestore = BackupManager.readFrom(context, uri)
            } catch (e: Exception) {
                statusMessage = "RESTORE FAILED: could not read that file."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BACKUP & RESTORE") },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("BACK UP EVERYTHING", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Saves every learner across every class and term, plus your grading scale, " +
                            "into one file you choose the location for — keep it somewhere safe, or " +
                            "move it to a new phone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${records.size} record(s) will be included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { createBackupLauncher.launch("rvh_grader_backup.json") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("EXPORT BACKUP FILE") }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("RESTORE FROM BACKUP", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Loads a previously exported backup file. This REPLACES everything " +
                            "currently in the app — you'll be asked to confirm first.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { openBackupLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("CHOOSE BACKUP FILE") }
                }
            }

            statusMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    pendingRestore?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace current data?") },
            text = {
                Text(
                    "This backup contains ${payload.records.size} record(s). Restoring it will " +
                        "replace everything currently in the app — this can't be undone unless you " +
                        "have another backup of the current data."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRestore(payload)
                    statusMessage = "RESTORED ${payload.records.size} RECORD(S)."
                    pendingRestore = null
                }) { Text("REPLACE") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("CANCEL") }
            }
        )
    }
}

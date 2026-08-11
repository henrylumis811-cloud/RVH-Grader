package com.henrylumis.rvhgrader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.henrylumis.rvhgrader.export.ExportFormat
import com.henrylumis.rvhgrader.model.StudentRecord

/**
 * Every learner ever recorded, grouped by name. Tap a learner to expand and see their record in
 * every class/term they've been graded in — this is what makes "progress over time" visible,
 * since [StudentRecord.recordedAtMillis] orders each learner's history newest-first. Each past
 * record can also be exported individually from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<StudentRecord>,
    onOpenMenu: () -> Unit,
    onExportIndividual: (StudentRecord, ExportFormat) -> Unit
) {
    var expandedName by remember { mutableStateOf<String?>(null) }
    var exportTarget by remember { mutableStateOf<StudentRecord?>(null) }

    val grouped = remember(records) { records.groupBy { it.name }.toSortedMap() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("STUDENT HISTORY") },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        if (grouped.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "NO LEARNERS RECORDED YET.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(grouped.keys.toList()) { name ->
                    val learnerRecords = grouped[name].orEmpty().sortedByDescending { it.recordedAtMillis }
                    val expanded = expandedName == name

                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedName = if (expanded) null else name },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${learnerRecords.size} record(s)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                learnerRecords.forEach { r ->
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${r.schoolClass.displayName} — ${r.term.displayName}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            TextButton(onClick = { exportTarget = r }) { Text("EXPORT") }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Total: ${r.total}", style = MaterialTheme.typography.labelSmall)
                                            if (r.graded) {
                                                Text(
                                                    "Agg: ${r.aggSum}   Div: ${r.division}",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    exportTarget?.let { record ->
        ExportFormatDialog(
            title = "Export ${record.name}'s report",
            onDismiss = { exportTarget = null },
            onConfirm = { format ->
                onExportIndividual(record, format)
                exportTarget = null
            }
        )
    }
}

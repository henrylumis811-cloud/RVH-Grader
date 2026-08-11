package com.henrylumis.rvhgrader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.henrylumis.rvhgrader.export.ExportFormat

/**
 * "Which format?" dialog shared by the class-list export (Dashboard) and the individual report
 * export (Student History) — same three choices either way.
 */
@Composable
fun ExportFormatDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat) -> Unit
) {
    var selected by remember { mutableStateOf(ExportFormat.PDF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ExportFormat.values().forEach { format ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                    ) {
                        RadioButton(selected = selected == format, onClick = { selected = format })
                        Text(
                            when (format) {
                                ExportFormat.PDF -> "PDF — formatted, print-ready report"
                                ExportFormat.EXCEL -> "Excel (.xlsx) — spreadsheet"
                                ExportFormat.CSV -> "CSV — plain data, opens anywhere"
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("EXPORT & SHARE") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

package com.henrylumis.rvhgrader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.henrylumis.rvhgrader.model.AggregateBand
import com.henrylumis.rvhgrader.model.DivisionBand
import com.henrylumis.rvhgrader.model.GradingScale

private class EditableAggBand(minMark: String, aggregate: String) {
    var minMark by mutableStateOf(minMark)
    var aggregate by mutableStateOf(aggregate)
}

private class EditableDivBand(maxAggSum: String, label: String) {
    var maxAggSum by mutableStateOf(maxAggSum)
    var label by mutableStateOf(label)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    scale: GradingScale,
    onScaleChange: (GradingScale) -> Unit,
    onOpenMenu: () -> Unit
) {
    val aggRows = remember(scale) {
        mutableStateListOf(
            *scale.aggregateBands.sortedByDescending { it.minMark }
                .map { EditableAggBand(it.minMark.toString(), it.aggregate.toString()) }
                .toTypedArray()
        )
    }
    val divRows = remember(scale) {
        mutableStateListOf(
            *scale.divisionBands.sortedBy { it.maxAggSum }
                .map { EditableDivBand(it.maxAggSum.toString(), it.label) }
                .toTypedArray()
        )
    }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GRADING SCALE") },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Adjust the mark-to-aggregate and aggregate-to-division tables used for Upper " +
                        "Primary grading (Lower Primary, P1-P3, is never graded). Changes apply to " +
                        "every new score entered from here on — records already saved keep whichever " +
                        "scale was in effect when they were graded.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("MARK → AGGREGATE", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        aggRows.forEachIndexed { index, row ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = row.minMark,
                                    onValueChange = { row.minMark = it.filter { c -> c.isDigit() }.take(3) },
                                    label = { Text("MIN MARK") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("→")
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = row.aggregate,
                                    onValueChange = { row.aggregate = it.filter { c -> c.isDigit() }.take(1) },
                                    label = { Text("AGG") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { aggRows.removeAt(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove band")
                                }
                            }
                        }
                        TextButton(onClick = { aggRows.add(EditableAggBand("", "")) }) {
                            Text("+ ADD BAND")
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("AGGREGATE SUM → DIVISION", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        divRows.forEachIndexed { index, row ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = row.maxAggSum,
                                    onValueChange = { row.maxAggSum = it.filter { c -> c.isDigit() }.take(3) },
                                    label = { Text("UP TO") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("→")
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = row.label,
                                    onValueChange = { row.label = it.uppercase().take(6) },
                                    label = { Text("DIVISION") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { divRows.removeAt(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove band")
                                }
                            }
                        }
                        TextButton(onClick = { divRows.add(EditableDivBand("", "")) }) {
                            Text("+ ADD BAND")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val default = GradingScale.default()
                            aggRows.clear()
                            aggRows.addAll(
                                default.aggregateBands.sortedByDescending { it.minMark }
                                    .map { EditableAggBand(it.minMark.toString(), it.aggregate.toString()) }
                            )
                            divRows.clear()
                            divRows.addAll(
                                default.divisionBands.sortedBy { it.maxAggSum }
                                    .map { EditableDivBand(it.maxAggSum.toString(), it.label) }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("RESET TO DEFAULT") }

                    Button(
                        onClick = {
                            val aggBands = aggRows.mapNotNull { r ->
                                val min = r.minMark.toIntOrNull()
                                val agg = r.aggregate.toIntOrNull()
                                if (min != null && agg != null) AggregateBand(min, agg) else null
                            }
                            val divBands = divRows.mapNotNull { r ->
                                val max = r.maxAggSum.toIntOrNull()
                                if (max != null && r.label.isNotBlank()) DivisionBand(max, r.label) else null
                            }
                            savedMessage = if (aggBands.isNotEmpty() && divBands.isNotEmpty()) {
                                onScaleChange(GradingScale(aggBands, divBands))
                                "GRADING SCALE SAVED"
                            } else {
                                "FIX INCOMPLETE ROWS BEFORE SAVING"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("SAVE") }
                }
            }
            savedMessage?.let { msg ->
                item {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

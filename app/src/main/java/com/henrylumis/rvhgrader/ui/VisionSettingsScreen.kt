package com.henrylumis.rvhgrader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.henrylumis.rvhgrader.ai.VisionSettings
import com.henrylumis.rvhgrader.ai.VisionSettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionSettingsScreen(
    settings: VisionSettings,
    onSettingsChange: (VisionSettings) -> Unit,
    onOpenMenu: () -> Unit
) {
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var model by remember(settings) { mutableStateOf(settings.model) }
    var showKey by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI VISION OCR") },
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
                    Text("WHAT THIS IS", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The default scanner reads handwriting entirely on-device — free, works " +
                            "with no internet, but can struggle on messy handwriting like any OCR " +
                            "does. Turning this on sends the photo to Claude's vision API instead, " +
                            "which reads the whole table directly rather than piecing together word " +
                            "positions — far more reliable on real handwriting, at the cost of " +
                            "needing an internet connection and your own Anthropic API key (a few " +
                            "cents per scan). This is entirely optional — leave it off and the app " +
                            "keeps working exactly as before, fully offline.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("USE AI VISION FOR SCANS", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it.trim() },
                        label = { Text("ANTHROPIC API KEY") },
                        singleLine = true,
                        visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "HIDE KEY" else "SHOW KEY")
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it.trim() },
                        label = { Text("MODEL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Get a key at console.anthropic.com. If a newer/cheaper vision model is " +
                            "available later, update this field to its model name — no app update " +
                            "needed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val newSettings = VisionSettings(
                                enabled = enabled,
                                apiKey = apiKey,
                                model = model.ifBlank { VisionSettingsRepository.DEFAULT_MODEL }
                            )
                            onSettingsChange(newSettings)
                            savedMessage = if (enabled && apiKey.isBlank()) {
                                "SAVED — but AI Vision is ON with no API key set, so scans will fail until you add one."
                            } else {
                                "SETTINGS SAVED"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("SAVE") }
                }
            }

            savedMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Text(
                "Your key is stored in this app's private storage only — never sent anywhere " +
                    "except directly to Anthropic's API alongside each scan, and never included " +
                    "in backup files.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

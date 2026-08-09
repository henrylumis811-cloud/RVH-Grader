package com.henrylumis.rvhgrader.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "rvh_grader_prefs"
private const val KEY_PIN = "access_pin"

private fun getStoredPin(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PIN, null)

private fun savePin(context: Context, pin: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_PIN, pin).apply()
}

/**
 * A basic local PIN gate. NOTE: this is a simple deterrent (keeps a casual user out of the
 * app on a shared device) — it is not real security, since the PIN lives in local app storage
 * on the device itself. It replaces the original web app's fake "hardware lock" + hardcoded
 * password, which offered no real protection either.
 */
@Composable
fun PinGate(context: Context, onUnlocked: () -> Unit) {
    var storedPin by remember { mutableStateOf(getStoredPin(context)) }
    var input by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (storedPin == null) {
                    Text("SET UP ACCESS PIN", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Choose a 4+ digit PIN to protect this device's grading data.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            when {
                                input.length < 4 -> error = "PIN must be at least 4 digits."
                                input != confirmInput -> error = "PINs do not match."
                                else -> {
                                    savePin(context, input)
                                    storedPin = input
                                    onUnlocked()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("SAVE & CONTINUE") }
                } else {
                    Text("ENTER PIN", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (input == storedPin) onUnlocked() else {
                                error = "Incorrect PIN."
                                input = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("UNLOCK") }
                }
            }
        }
    }
}

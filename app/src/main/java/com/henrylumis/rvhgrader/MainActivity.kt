package com.henrylumis.rvhgrader

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SystemMode
import com.henrylumis.rvhgrader.ocr.OcrFieldParser
import com.henrylumis.rvhgrader.ocr.loadUprightBitmap
import com.henrylumis.rvhgrader.ocr.recognizeText
import com.henrylumis.rvhgrader.ui.DashboardScreen
import com.henrylumis.rvhgrader.ui.PinGate
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    RVHGraderApp()
                }
            }
        }
    }
}

@Composable
fun RVHGraderApp() {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }

    if (!unlocked) {
        PinGate(context = context, onUnlocked = { unlocked = true })
        return
    }

    // ---- Grading state (mirrors lowerDatabase / upperDatabase in the original app) ----
    var mode by remember { mutableStateOf(SystemMode.LOWER) }
    val lowerRecords = remember { mutableStateListOf<StudentRecord>() }
    val upperRecords = remember { mutableStateListOf<StudentRecord>() }

    var nameValue by remember { mutableStateOf("") }
    val fieldValues = remember { mutableStateMapOf<String, String>() }

    var scanning by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf<String?>(null) }
    var lastCapturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val scope = rememberCoroutineScope()

    fun currentRecords() = if (mode == SystemMode.LOWER) lowerRecords else upperRecords

    fun resetForm() {
        nameValue = ""
        fieldValues.clear()
    }

    fun runOcrOnUri(uri: Uri) {
        scanning = true
        scanStatus = null
        scope.launch {
            try {
                val bitmap = loadUprightBitmap(context, uri)
                lastCapturedImage = bitmap
                val text = recognizeText(bitmap)
                val result = OcrFieldParser.parse(text, mode)

                if (result.name != null && nameValue.isBlank()) {
                    nameValue = result.name
                }
                result.scores.forEach { (field, value) ->
                    if (fieldValues[field].isNullOrBlank()) {
                        fieldValues[field] = value.toString()
                    }
                }

                scanStatus = if (result.fieldsPopulated > 0) {
                    "DATA INGESTED: ${result.fieldsPopulated} FIELD(S) POPULATED - VERIFY BELOW"
                } else {
                    "NO MATCHABLE DATA FOUND - ENTER MANUALLY"
                }
            } catch (e: Exception) {
                scanStatus = "SCAN FAILED - ENTER DATA MANUALLY"
            } finally {
                scanning = false
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            runOcrOnUri(uri)
        }
    }

    fun launchCamera() {
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val photoFile = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, photoFile)
        pendingPhotoUri = uri
        takePictureLauncher.launch(uri)
    }

    DashboardScreen(
        mode = mode,
        onModeChange = { newMode ->
            mode = newMode
            resetForm()
        },
        fieldValues = fieldValues,
        nameValue = nameValue,
        onNameChange = { nameValue = it },
        onFieldChange = { field, value -> fieldValues[field] = value },
        onCapturePhoto = { launchCamera() },
        scanning = scanning,
        scanStatus = scanStatus,
        lastCapturedImage = lastCapturedImage,
        onCommit = {
            if (nameValue.isBlank()) {
                scanStatus = "PROCESSING ERROR: Student Name missing."
            } else {
                val record = GradingLogic.buildRecord(nameValue, mode, fieldValues)
                currentRecords().add(record)
                currentRecords().sortByDescending { it.total }
                resetForm()
            }
        },
        records = currentRecords()
    )
}

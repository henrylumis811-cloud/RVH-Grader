package com.henrylumis.rvhgrader

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.SystemMode
import com.henrylumis.rvhgrader.ocr.ClassListParser
import com.henrylumis.rvhgrader.ocr.PendingRow
import com.henrylumis.rvhgrader.ocr.loadUprightBitmap
import com.henrylumis.rvhgrader.ocr.recognizeText
import com.henrylumis.rvhgrader.ui.AppTheme
import com.henrylumis.rvhgrader.ui.DashboardScreen
import com.henrylumis.rvhgrader.ui.PinGate
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
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

    // ---- Review & Correct state — a class-list photo produces one row per learner here,
    //      nothing is added to the class list until the teacher checks it and hits commit. ----
    var reviewing by remember { mutableStateOf(false) }
    val pendingRows = remember { mutableStateListOf<PendingRow>() }
    var nextRowId by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    fun currentRecords() = if (mode == SystemMode.LOWER) lowerRecords else upperRecords

    fun resetForm() {
        nameValue = ""
        fieldValues.clear()
    }

    fun discardReview() {
        pendingRows.clear()
        reviewing = false
    }

    fun runOcrOnUri(uri: Uri) {
        discardReview() // a new scan replaces any unreviewed batch from a previous photo
        scanning = true
        scanStatus = null
        scope.launch {
            try {
                val bitmap = loadUprightBitmap(context, uri)
                lastCapturedImage = bitmap
                val visionText = recognizeText(bitmap)
                val extractedRows = ClassListParser.extractRows(visionText, mode)

                extractedRows.forEach { row ->
                    pendingRows.add(
                        PendingRow(
                            id = nextRowId++,
                            initialName = row.name,
                            initialScores = row.scores,
                            flaggedFields = row.flaggedFields,
                            lowConfidence = row.lowConfidence
                        )
                    )
                }

                scanStatus = if (extractedRows.isNotEmpty()) {
                    "${extractedRows.size} ROW(S) DETECTED — REVIEW BELOW BEFORE SAVING"
                } else {
                    "NO ROWS DETECTED - ENTER MANUALLY"
                }
                reviewing = extractedRows.isNotEmpty()
            } catch (e: Exception) {
                scanStatus = "SCAN FAILED - ENTER DATA MANUALLY"
            } finally {
                scanning = false
            }
        }
    }

    fun commitReviewedRows() {
        var committed = 0
        pendingRows.forEach { row ->
            if (!row.included || row.name.isBlank()) return@forEach
            currentRecords().add(GradingLogic.buildRecord(row.name, mode, row.scores))
            committed++
        }
        currentRecords().sortByDescending { it.total }
        pendingRows.clear()
        reviewing = false
        scanStatus = if (committed > 0) {
            "$committed STUDENT(S) COMMITTED TO DATABASE"
        } else {
            "NO ROWS COMMITTED: check a name for at least one checked row."
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
            discardReview() // subject columns differ between modes — avoid stale review data
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
        records = currentRecords(),
        reviewing = reviewing,
        pendingRows = pendingRows,
        onCommitReview = { commitReviewedRows() },
        onDiscardReview = { discardReview() }
    )
}

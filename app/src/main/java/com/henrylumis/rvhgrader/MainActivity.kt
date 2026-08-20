package com.henrylumis.rvhgrader

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.henrylumis.rvhgrader.ai.ClaudeVisionOcr
import com.henrylumis.rvhgrader.ai.VisionOcrException
import com.henrylumis.rvhgrader.ai.VisionSettingsRepository
import com.henrylumis.rvhgrader.data.GradebookRepository
import com.henrylumis.rvhgrader.export.ExportFormat
import com.henrylumis.rvhgrader.export.ExportManager
import com.henrylumis.rvhgrader.grading.GradingLogic
import com.henrylumis.rvhgrader.grading.GradingScaleRepository
import com.henrylumis.rvhgrader.model.SchoolClass
import com.henrylumis.rvhgrader.model.StudentRecord
import com.henrylumis.rvhgrader.model.Term
import com.henrylumis.rvhgrader.ocr.ClassListParser
import com.henrylumis.rvhgrader.ocr.PendingRow
import com.henrylumis.rvhgrader.ocr.loadUprightBitmap
import com.henrylumis.rvhgrader.ocr.recognizeText
import com.henrylumis.rvhgrader.ui.AppTheme
import com.henrylumis.rvhgrader.ui.BackupScreen
import com.henrylumis.rvhgrader.ui.DashboardScreen
import com.henrylumis.rvhgrader.ui.HistoryScreen
import com.henrylumis.rvhgrader.ui.PinGate
import com.henrylumis.rvhgrader.ui.Screen
import com.henrylumis.rvhgrader.ui.SettingsScreen
import com.henrylumis.rvhgrader.ui.VisionSettingsScreen
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RVHGraderApp() {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }

    if (!unlocked) {
        PinGate(context = context, onUnlocked = { unlocked = true })
        return
    }

    // ---- Persisted state: loaded once on unlock, auto-saved after every change ----
    val allRecords = remember { mutableStateListOf<StudentRecord>().apply { addAll(GradebookRepository.load(context)) } }
    var gradingScale by remember { mutableStateOf(GradingScaleRepository.load(context)) }
    var visionSettings by remember { mutableStateOf(VisionSettingsRepository.load(context)) }

    fun persistRecords() = GradebookRepository.save(context, allRecords)
    fun persistScale() = GradingScaleRepository.save(context, gradingScale)

    // ---- Class / term selection (replaces the old Lower/Upper toggle) ----
    var selectedClass by remember { mutableStateOf(SchoolClass.P1) }
    var selectedTerm by remember { mutableStateOf(Term.TERM1) }

    var nameValue by remember { mutableStateOf("") }
    val fieldValues = remember { mutableStateMapOf<String, String>() }

    var scanning by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf<String?>(null) }
    var lastCapturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var reviewing by remember { mutableStateOf(false) }
    val pendingRows = remember { mutableStateListOf<PendingRow>() }
    var nextRowId by remember { mutableStateOf(0) }

    // ---- Navigation ----
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val navScope = rememberCoroutineScope()
    val ocrScope = rememberCoroutineScope()

    fun currentRecords() = allRecords.filter { it.schoolClass == selectedClass && it.term == selectedTerm }

    fun resetForm() {
        nameValue = ""
        fieldValues.clear()
    }

    fun discardReview() {
        pendingRows.clear()
        reviewing = false
    }

    fun runOcrOnUri(uri: Uri) {
        discardReview()
        scanning = true
        scanStatus = null
        ocrScope.launch {
            try {
                val bitmap = loadUprightBitmap(context, uri)
                lastCapturedImage = bitmap

                val extractedRows = if (visionSettings.enabled) {
                    scanStatus = "READING WITH AI VISION..."
                    ClaudeVisionOcr.extractRows(
                        apiKey = visionSettings.apiKey,
                        model = visionSettings.model,
                        bitmap = bitmap,
                        mode = selectedClass.mode
                    )
                } else {
                    val visionText = recognizeText(bitmap)
                    ClassListParser.extractRows(visionText, selectedClass.mode)
                }

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
            } catch (e: VisionOcrException) {
                scanStatus = "AI VISION FAILED: ${e.message} — check Settings, or turn it off to use offline OCR."
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
            allRecords.add(GradingLogic.buildRecord(row.name, selectedClass, selectedTerm, row.scores, gradingScale))
            committed++
        }
        pendingRows.clear()
        reviewing = false
        if (committed > 0) persistRecords()
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

    fun openMenu() {
        navScope.launch { drawerState.open() }
    }

    fun goTo(screen: Screen) {
        currentScreen = screen
        navScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("HENRY LUMIS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "In dedication to Hellen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(Screen.DASHBOARD.label) },
                    selected = currentScreen == Screen.DASHBOARD,
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = { goTo(Screen.DASHBOARD) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(Screen.HISTORY.label) },
                    selected = currentScreen == Screen.HISTORY,
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    onClick = { goTo(Screen.HISTORY) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(Screen.SETTINGS.label) },
                    selected = currentScreen == Screen.SETTINGS,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = { goTo(Screen.SETTINGS) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(Screen.VISION.label) },
                    selected = currentScreen == Screen.VISION,
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    onClick = { goTo(Screen.VISION) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text(Screen.BACKUP.label) },
                    selected = currentScreen == Screen.BACKUP,
                    icon = { Icon(Icons.Filled.Backup, contentDescription = null) },
                    onClick = { goTo(Screen.BACKUP) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        when (currentScreen) {
            Screen.DASHBOARD -> DashboardScreen(
                selectedClass = selectedClass,
                onClassChange = { newClass ->
                    selectedClass = newClass
                    resetForm()
                    discardReview()
                },
                selectedTerm = selectedTerm,
                onTermChange = { newTerm ->
                    selectedTerm = newTerm
                    resetForm()
                    discardReview()
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
                        allRecords.add(
                            GradingLogic.buildRecord(nameValue, selectedClass, selectedTerm, fieldValues, gradingScale)
                        )
                        persistRecords()
                        resetForm()
                    }
                },
                records = currentRecords(),
                reviewing = reviewing,
                pendingRows = pendingRows,
                onCommitReview = { commitReviewedRows() },
                onDiscardReview = { discardReview() },
                onOpenMenu = { openMenu() },
                onExportClassList = { format ->
                    val file = ExportManager.exportClassList(
                        context, currentRecords(), selectedClass.mode,
                        selectedClass.displayName, selectedTerm.displayName, format
                    )
                    ExportManager.shareFile(context, file, format.mimeType)
                }
            )

            Screen.HISTORY -> HistoryScreen(
                records = allRecords,
                onOpenMenu = { openMenu() },
                onExportIndividual = { record, format ->
                    val file = ExportManager.exportIndividual(context, record, format)
                    ExportManager.shareFile(context, file, format.mimeType)
                }
            )

            Screen.SETTINGS -> SettingsScreen(
                scale = gradingScale,
                onScaleChange = { newScale ->
                    gradingScale = newScale
                    persistScale()
                },
                onOpenMenu = { openMenu() }
            )

            Screen.VISION -> VisionSettingsScreen(
                settings = visionSettings,
                onSettingsChange = { newSettings ->
                    visionSettings = newSettings
                    VisionSettingsRepository.save(context, newSettings)
                },
                onOpenMenu = { openMenu() }
            )

            Screen.BACKUP -> BackupScreen(
                records = allRecords,
                gradingScale = gradingScale,
                onOpenMenu = { openMenu() },
                onRestore = { payload ->
                    allRecords.clear()
                    allRecords.addAll(payload.records)
                    gradingScale = payload.gradingScale
                    persistRecords()
                    persistScale()
                }
            )
        }
    }
}

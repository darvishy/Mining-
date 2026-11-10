package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ColumnMapping
import com.example.data.CsvBatchImporter
import com.example.data.CsvDelimiter
import com.example.data.CsvFormatConfig
import com.example.data.CsvQueueItem
import com.example.data.DatabaseHelper
import com.example.data.HeaderMode
import com.example.data.ImportStatus
import com.example.data.QueueItemStatus
import com.example.ui.components.HudBadge
import com.example.ui.components.HudButton
import com.example.ui.components.HudCard
import com.example.ui.components.HudProgressBar
import com.example.ui.components.triggerHudHaptic
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackground
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudGreen
import com.example.ui.theme.HudRed
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudTextDim
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    importer: CsvBatchImporter,
    dbHelper: DatabaseHelper,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by importer.progress.collectAsState()
    val queue by importer.queue.collectAsState()
    val overallQueue by importer.queueOverallProgress.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }
    var totalDbCount by remember { mutableLongStateOf(dbHelper.getTotalCount()) }

    // Format Configuration States
    var selectedDelimiter by remember { mutableStateOf(CsvDelimiter.AUTO) }
    var selectedMapping by remember { mutableStateOf(ColumnMapping.AUTO_HEADER) }
    var selectedHeaderMode by remember { mutableStateOf(HeaderMode.AUTO) }

    val currentFormat = remember(selectedDelimiter, selectedMapping, selectedHeaderMode) {
        CsvFormatConfig(
            delimiter = selectedDelimiter,
            columnMapping = selectedMapping,
            headerMode = selectedHeaderMode
        )
    }

    val refreshDbCount = {
        scope.launch(Dispatchers.IO) {
            val count = dbHelper.getTotalCount()
            withContext(Dispatchers.Main) {
                totalDbCount = count
            }
        }
    }

    // Multiple Files Picker Launcher (Queue Pipeline)
    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                try {
                    val files = uris.map { uri ->
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) {}
                        uri to getFileNameFromUri(context, uri)
                    }
                    importer.enqueueFiles(files, currentFormat, autoStart = true)
                    refreshDbCount()
                } catch (e: Exception) {
                    Log.e("ImportScreen", "Error enqueueing multiple files", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Queue error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Single File Picker Launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri)
            scope.launch {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    importer.enqueueFiles(listOf(uri to fileName), currentFormat, autoStart = true)
                    refreshDbCount()
                } catch (e: Exception) {
                    Log.e("ImportScreen", "Import error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HudBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Navigation Header: Uniform Action Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    triggerHudHaptic(context, 15)
                    onBack()
                },
            color = HudSurfaceElevated,
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, HudBorderBright)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HudCyan,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RETURN TO HUD",
                    color = HudTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "DATA INGESTION TERMINAL",
                color = HudCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = "MULTI-FILE BATCH QUEUE & FORMAT-AWARE INGESTION ENGINE",
                color = HudTextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interrupted Session Recovery Banner
        AnimatedVisibility(visible = progress.hasRecoverableSession) {
            HudCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                borderColor = HudAmber,
                backgroundColor = HudSurfaceElevated,
                title = "CHECKPOINT DETECTED // CRASH RESILIENCE",
                titleColor = HudAmber,
                statusBadge = "RESUMABLE",
                statusColor = HudGreen
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = HudAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UNFINISHED INGESTION JOB RECOVERED",
                            color = HudAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Source: ${progress.sourceName.ifEmpty { "PREVIOUS_IMPORT" }} | Checkpoint: ${String.format("%,d", progress.processedRecords)} records ingested. Resume without data loss.",
                        color = HudTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HudButton(
                            text = "RESUME FROM CHECKPOINT",
                            onClick = {
                                scope.launch {
                                    importer.resumeImport()
                                    refreshDbCount()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = HudGreen,
                            icon = Icons.Default.PlayArrow
                        )

                        HudButton(
                            text = "DISCARD",
                            onClick = {
                                dbHelper.clearSession()
                                importer.checkForRecoverableSession()
                            },
                            modifier = Modifier.width(100.dp),
                            accentColor = HudRed,
                            isSecondary = true
                        )
                    }
                }
            }
        }

        // Live Telemetry & Gauge Card
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            title = "LIVE HARDWARE INGESTION METRICS",
            titleColor = HudCyan,
            statusBadge = if (overallQueue.isRunning) "QUEUE ACTIVE" else progress.status.name,
            statusColor = when {
                overallQueue.isRunning -> HudCyan
                overallQueue.isPaused || progress.status == ImportStatus.PAUSED -> HudAmber
                progress.status == ImportStatus.COMPLETED -> HudGreen
                progress.status == ImportStatus.ERROR -> HudRed
                else -> HudTextDim
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Key Value Metric Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricBox(
                        label = "TOTAL IN DATABASE",
                        value = String.format("%,d", totalDbCount),
                        accentColor = HudGreen,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MetricBox(
                        label = "INGESTION SPEED",
                        value = if (overallQueue.overallSpeed > 0) "${String.format("%,d", overallQueue.overallSpeed)} r/s" else "${String.format("%,d", progress.recordsPerSecond)} r/s",
                        accentColor = HudCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricBox(
                        label = "ELAPSED TIME",
                        value = formatSeconds(progress.elapsedSeconds),
                        accentColor = HudTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MetricBox(
                        label = "ESTIMATED REMAINING",
                        value = if (overallQueue.etaSeconds > 0) formatSeconds(overallQueue.etaSeconds) else if (progress.etaSeconds > 0) formatSeconds(progress.etaSeconds) else "--:--",
                        accentColor = HudAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Multi-File Ingestion Queue Card
        if (queue.isNotEmpty()) {
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (overallQueue.isRunning) HudCyan else HudBorderBright,
                title = "INGESTION BATCH QUEUE // ${queue.size} FILE(S)",
                titleColor = HudCyan,
                statusBadge = when {
                    overallQueue.isRunning -> "FILE ${overallQueue.currentFileIndex}/${overallQueue.totalFiles}"
                    overallQueue.isPaused -> "QUEUE PAUSED"
                    queue.all { it.status == QueueItemStatus.COMPLETED } -> "ALL COMPLETED"
                    else -> "QUEUE STANDBY"
                },
                statusColor = if (overallQueue.isRunning) HudCyan else HudGreen
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Overall Queue Progress
                    val overallPct = if (overallQueue.totalEstimatedRecords > 0) {
                        (overallQueue.totalProcessedRecords.toFloat() / overallQueue.totalEstimatedRecords).coerceIn(0f, 1f)
                    } else if (queue.any { it.status == QueueItemStatus.COMPLETED }) {
                        queue.count { it.status == QueueItemStatus.COMPLETED }.toFloat() / queue.size
                    } else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OVERALL BATCH PROGRESS",
                            color = HudTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${String.format("%,d", overallQueue.totalProcessedRecords)} / ${String.format("%,d", overallQueue.totalEstimatedRecords)} (${(overallPct * 100).toInt()}%)",
                            color = HudCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HudProgressBar(
                        progress = overallPct,
                        color = HudCyan,
                        trackColor = HudBorder
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Queue Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (overallQueue.isRunning) {
                            HudButton(
                                text = "PAUSE",
                                onClick = { importer.pauseQueue() },
                                modifier = Modifier.weight(1f),
                                accentColor = HudAmber,
                                icon = Icons.Default.Pause
                            )
                            HudButton(
                                text = "SKIP FILE",
                                onClick = { importer.skipCurrentFile() },
                                modifier = Modifier.weight(1f),
                                accentColor = HudCyan,
                                icon = Icons.Default.SkipNext,
                                isSecondary = true
                            )
                            HudButton(
                                text = "ABORT",
                                onClick = {
                                    importer.cancelQueue()
                                    refreshDbCount()
                                },
                                modifier = Modifier.weight(1f),
                                accentColor = HudRed,
                                icon = Icons.Default.Stop,
                                isSecondary = true
                            )
                        } else if (overallQueue.isPaused) {
                            HudButton(
                                text = "RESUME",
                                onClick = { importer.resumeQueue() },
                                modifier = Modifier.weight(1f),
                                accentColor = HudGreen,
                                icon = Icons.Default.PlayArrow
                            )
                            HudButton(
                                text = "ABORT",
                                onClick = {
                                    importer.cancelQueue()
                                    refreshDbCount()
                                },
                                modifier = Modifier.weight(1f),
                                accentColor = HudRed,
                                icon = Icons.Default.Stop,
                                isSecondary = true
                            )
                        } else {
                            if (queue.any { it.status == QueueItemStatus.PENDING }) {
                                HudButton(
                                    text = "START QUEUE",
                                    onClick = { importer.startQueue() },
                                    modifier = Modifier.weight(1f),
                                    accentColor = HudGreen,
                                    icon = Icons.Default.PlayArrow
                                )
                            }
                            HudButton(
                                text = "CLEAR FINISHED",
                                onClick = { importer.clearCompletedQueue() },
                                modifier = Modifier.weight(1f),
                                accentColor = HudTextSecondary,
                                icon = Icons.Default.Clear,
                                isSecondary = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Queue Items List
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        queue.forEachIndexed { idx, item ->
                            QueueItemRow(
                                item = item,
                                index = idx + 1,
                                onRemove = { importer.removeFromQueue(item.id) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // CSV Parser Format Configuration Card
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudCyan,
            title = "CSV INGESTION FORMAT CONFIGURATION",
            titleColor = HudCyan,
            statusBadge = "${selectedDelimiter.name} // ${selectedMapping.name}",
            statusColor = HudCyan
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SELECT PARSING FORMAT SCHEMA",
                    color = HudTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Configure delimiter and column order before queuing CSV files.",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Delimiter Selection
                Text(
                    text = "FIELD DELIMITER / SEPARATOR:",
                    color = HudCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(CsvDelimiter.entries) { delim ->
                        val isSelected = delim == selectedDelimiter
                        Surface(
                            modifier = Modifier.clickable {
                                triggerHudHaptic(context, 15)
                                selectedDelimiter = delim
                            },
                            color = if (isSelected) HudCyan.copy(alpha = 0.2f) else HudSurfaceElevated,
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(
                                if (isSelected) 1.dp else 0.8.dp,
                                if (isSelected) HudCyan else HudBorder
                            )
                        ) {
                            Text(
                                text = delim.label,
                                color = if (isSelected) HudCyan else HudTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Column Mapping Selection
                Text(
                    text = "COLUMN ORDER & FIELD MAPPING:",
                    color = HudCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ColumnMapping.entries) { mapping ->
                        val isSelected = mapping == selectedMapping
                        Surface(
                            modifier = Modifier.clickable {
                                triggerHudHaptic(context, 15)
                                selectedMapping = mapping
                            },
                            color = if (isSelected) HudCyan.copy(alpha = 0.2f) else HudSurfaceElevated,
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(
                                if (isSelected) 1.dp else 0.8.dp,
                                if (isSelected) HudCyan else HudBorder
                            )
                        ) {
                            Text(
                                text = mapping.label,
                                color = if (isSelected) HudCyan else HudTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Schema: ${selectedMapping.description}",
                    color = HudAmber,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Header Row Behavior
                Text(
                    text = "FIRST ROW / HEADER BEHAVIOR:",
                    color = HudCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(HeaderMode.entries) { mode ->
                        val isSelected = mode == selectedHeaderMode
                        Surface(
                            modifier = Modifier.clickable {
                                triggerHudHaptic(context, 15)
                                selectedHeaderMode = mode
                            },
                            color = if (isSelected) HudCyan.copy(alpha = 0.2f) else HudSurfaceElevated,
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(
                                if (isSelected) 1.dp else 0.8.dp,
                                if (isSelected) HudCyan else HudBorder
                            )
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) HudCyan else HudTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Select & Enqueue CSV Files Card
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            title = "ADD FILES TO INGESTION QUEUE",
            titleColor = HudCyan,
            statusBadge = "FORMAT READY",
            statusColor = HudGreen
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SELECT MULTIPLE CSV DATABASE FILES",
                    color = HudTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selected files will be queued and ingested sequentially with SQLite WAL multi-million record optimization.",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Multi-File Button
                HudButton(
                    text = if (queue.isNotEmpty()) "ADD MORE CSV FILES TO QUEUE" else "SELECT MULTIPLE CSV FILES (QUEUE)",
                    onClick = {
                        try {
                            multiFilePicker.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            Log.e("ImportScreen", "Failed to launch multi picker", e)
                            Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = HudCyan,
                    icon = Icons.Default.FolderOpen
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Single File Button
                HudButton(
                    text = "SELECT SINGLE CSV FILE",
                    onClick = {
                        try {
                            filePicker.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            Log.e("ImportScreen", "Failed to launch picker", e)
                            Toast.makeText(context, "Cannot open file picker: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = HudBorderBright,
                    icon = Icons.Default.FolderOpen,
                    isSecondary = true
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Database Maintenance Card
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudBorder,
            title = "DATABASE MAINTENANCE",
            titleColor = HudTextSecondary
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PURGE DATABASE",
                        color = HudRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Wipe all records and reset B-Tree indexes",
                        color = HudTextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                HudButton(
                    text = "CLEAR ALL",
                    onClick = { showClearConfirm = true },
                    accentColor = HudRed,
                    icon = Icons.Default.DeleteForever,
                    isSecondary = true
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Optimization Telemetry Specs
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudBorder,
            title = "HARDWARE ENGINE CONFIGURATION",
            titleColor = HudCyan
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ConfigSpecRow(name = "STORAGE ENGINE", value = "SQLite 3.x (Zero-Memory Copy)")
                ConfigSpecRow(name = "JOURNAL MODE", value = "WAL (Write-Ahead Logging)")
                ConfigSpecRow(name = "QUEUE PIPELINE", value = "Multi-File Sequential Stream")
                ConfigSpecRow(name = "BATCH CAPACITY", value = "10,000 Rows Per Transaction")
                ConfigSpecRow(name = "INDEXING", value = "4x B-Tree (Nat, Card, Mobile, Name)")
                ConfigSpecRow(name = "CRASH RESILIENCE", value = "Persistent State Checkpoints")
            }
        }
    }

    if (showClearConfirm) {
        BasicAlertDialog(onDismissRequest = { showClearConfirm = false }) {
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = HudRed,
                backgroundColor = HudBackground,
                title = "CONFIRM PURGE",
                titleColor = HudRed
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "CONFIRM DATA ERASURE",
                        color = HudTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Are you sure you want to permanently delete all $totalDbCount records from the database?",
                        color = HudTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HudButton(
                            text = "YES, PURGE",
                            onClick = {
                                dbHelper.clearAllRecords()
                                refreshDbCount()
                                showClearConfirm = false
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = HudRed
                        )
                        HudButton(
                            text = "CANCEL",
                            onClick = { showClearConfirm = false },
                            modifier = Modifier.weight(1f),
                            accentColor = HudBorderBright,
                            isSecondary = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    item: CsvQueueItem,
    index: Int,
    onRemove: () -> Unit
) {
    val statusColor = when (item.status) {
        QueueItemStatus.IMPORTING -> HudCyan
        QueueItemStatus.COMPLETED -> HudGreen
        QueueItemStatus.PENDING -> HudAmber
        QueueItemStatus.SCANNING -> HudCyan
        QueueItemStatus.PAUSED -> HudAmber
        QueueItemStatus.FAILED -> HudRed
        QueueItemStatus.CANCELLED -> HudTextDim
    }

    val progress = if (item.totalRecords > 0) {
        (item.processedRecords.toFloat() / item.totalRecords).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudSurfaceElevated,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(
            if (item.status == QueueItemStatus.IMPORTING) 1.dp else 0.8.dp,
            if (item.status == QueueItemStatus.IMPORTING) HudCyan else HudBorder
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .border(0.8.dp, statusColor, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$index",
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.fileName,
                            color = HudTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Text(
                            text = "${item.format.delimiter.label} • ${item.format.columnMapping.label}",
                            color = HudTextDim,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudBadge(
                        text = item.status.name,
                        color = statusColor
                    )

                    if (item.status != QueueItemStatus.IMPORTING) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Remove from Queue",
                                tint = HudTextDim,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (item.status == QueueItemStatus.COMPLETED) {
                        "INGESTED ${String.format("%,d", item.processedRecords)} RECORDS"
                    } else if (item.totalRecords > 0) {
                        "${String.format("%,d", item.processedRecords)} / ${String.format("%,d", item.totalRecords)} RECORDS"
                    } else {
                        "QUEUED // PENDING"
                    },
                    color = if (item.status == QueueItemStatus.COMPLETED) HudGreen else HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (item.recordsPerSecond > 0 && item.status == QueueItemStatus.IMPORTING) {
                    Text(
                        text = "${String.format("%,d", item.recordsPerSecond)} r/s",
                        color = HudCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (item.status == QueueItemStatus.IMPORTING) {
                Spacer(modifier = Modifier.height(4.dp))
                HudProgressBar(
                    progress = progress,
                    color = HudCyan,
                    trackColor = HudBorder
                )
            } else if (!item.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ERROR: ${item.errorMessage}",
                    color = HudRed,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = HudSurfaceElevated,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(0.8.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                color = HudTextDim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ConfigSpecRow(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = HudTextDim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = HudCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "IMPORT_DATA.csv"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val displayName = cursor.getString(nameIndex)
                if (!displayName.isNullOrBlank()) {
                    name = displayName
                }
            }
        }
    } catch (_: Exception) {}
    return name
}

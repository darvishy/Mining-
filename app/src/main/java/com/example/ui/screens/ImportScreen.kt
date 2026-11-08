package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
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
import com.example.data.CsvBatchImporter
import com.example.data.DatabaseHelper
import com.example.data.ImportStatus
import com.example.ui.components.DeveloperHeader
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
import kotlinx.coroutines.launch

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

    var showClearConfirm by remember { mutableStateOf(false) }
    var totalDbCount by remember { mutableLongStateOf(dbHelper.getTotalCount()) }

    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "IMPORT_DATA.csv"
            scope.launch {
                importer.importCsvFile(
                    uri = uri,
                    fileName = fileName,
                    totalEstimate = 10_000_000L
                )
                totalDbCount = dbHelper.getTotalCount()
            }
        }
    }

    // Refresh DB count periodically or on state change
    val refreshDbCount = {
        totalDbCount = dbHelper.getTotalCount()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HudBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(HudSurfaceElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, HudBorderBright, RoundedCornerShape(2.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HudCyan
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "DATA INGESTION TERMINAL",
                    color = HudCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "10M+ RECORDS HIGH-PERFORMANCE ENGINE",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
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
                                    totalDbCount = dbHelper.getTotalCount()
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
            statusBadge = progress.status.name,
            statusColor = when (progress.status) {
                ImportStatus.RUNNING -> HudCyan
                ImportStatus.PAUSED -> HudAmber
                ImportStatus.COMPLETED -> HudGreen
                ImportStatus.ERROR -> HudRed
                ImportStatus.IDLE -> HudTextDim
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
                        value = "${String.format("%,d", progress.recordsPerSecond)} rec/s",
                        accentColor = HudCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricBox(
                        label = "PROCESSED",
                        value = String.format("%,d", progress.processedRecords),
                        accentColor = HudTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MetricBox(
                        label = "ESTIMATED ETA",
                        value = if (progress.etaSeconds > 0) formatSeconds(progress.etaSeconds) else "--:--",
                        accentColor = HudAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                val progressFraction = if (progress.totalRecords > 0) {
                    (progress.processedRecords.toFloat() / progress.totalRecords.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PROGRESS: ${(progressFraction * 100).toInt()}%",
                        color = HudCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ELAPSED: ${formatSeconds(progress.elapsedSeconds)}",
                        color = HudTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                HudProgressBar(
                    progress = progressFraction,
                    color = when (progress.status) {
                        ImportStatus.PAUSED -> HudAmber
                        ImportStatus.COMPLETED -> HudGreen
                        else -> HudCyan
                    }
                )

                if (progress.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "! ERROR: ${progress.errorMessage}",
                        color = HudRed,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-Time Control Buttons
                when (progress.status) {
                    ImportStatus.RUNNING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HudButton(
                                text = "PAUSE INGESTION",
                                onClick = {
                                    importer.pause()
                                    refreshDbCount()
                                },
                                modifier = Modifier.weight(1f),
                                accentColor = HudAmber,
                                icon = Icons.Default.Pause
                            )
                            HudButton(
                                text = "ABORT",
                                onClick = {
                                    importer.cancel()
                                    refreshDbCount()
                                },
                                modifier = Modifier.weight(1f),
                                accentColor = HudRed,
                                icon = Icons.Default.Stop,
                                isSecondary = true
                            )
                        }
                    }
                    ImportStatus.PAUSED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HudButton(
                                text = "RESUME INGESTION",
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
                                text = "DISCARD JOB",
                                onClick = {
                                    importer.cancel()
                                    dbHelper.clearSession()
                                    importer.checkForRecoverableSession()
                                    refreshDbCount()
                                },
                                modifier = Modifier.weight(1f),
                                accentColor = HudRed,
                                icon = Icons.Default.Stop,
                                isSecondary = true
                            )
                        }
                    }
                    else -> {
                        // IDLE or COMPLETED: Show File Import & Benchmark Options
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ingestion Sources Card (when not currently running)
        if (progress.status != ImportStatus.RUNNING) {
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                title = "CSV DATABASE INGESTION",
                titleColor = HudCyan,
                statusBadge = "READY",
                statusColor = HudGreen
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "IMPORT REAL CSV DATABASE FILE",
                        color = HudTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Required Schema: NATIONAL_CODE, CARD_NO, FULL_NAME, BIRTH_DATE, MOBILE",
                        color = HudAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hardware Optimized: Multi-million record capacity with SQLite WAL mode, 64 MB memory cache, and automatic crash checkpoint recovery.",
                        color = HudTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    HudButton(
                        text = "SELECT & IMPORT CSV FILE",
                        onClick = {
                            filePicker.launch(arrayOf("*/*", "text/*", "text/csv", "application/csv"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = HudCyan,
                        icon = Icons.Default.FolderOpen
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
                ConfigSpecRow(name = "PRAGMA CACHE", value = "64 MB RAM Allocation")
                ConfigSpecRow(name = "BATCH PIPELINE", value = "10,000 Rows Per Transaction")
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
        border = androidx.compose.foundation.BorderStroke(0.8.dp, HudBorder)
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

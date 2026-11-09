package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseHelper
import com.example.data.ImportStatus
import com.example.data.VcfExporter
import com.example.ui.components.DeveloperHeader
import com.example.ui.components.HudBadge
import com.example.ui.components.HudButton
import com.example.ui.components.HudCard
import com.example.ui.components.HudProgressBar
import com.example.ui.components.HudTextField
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
import java.io.File

@Composable
fun VcfExportScreen(
    vcfExporter: VcfExporter,
    dbHelper: DatabaseHelper,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val progress by vcfExporter.progress.collectAsState()

    var prefixInput by remember { mutableStateOf("") }
    var matchingCount by remember { mutableLongStateOf(0L) }
    var isCheckingCount by remember { mutableStateOf(false) }

    // Quick prefix recommendations
    val samplePrefixes = listOf("001", "008", "123", "256", "387", "500")

    // Update matching count whenever prefix input changes
    LaunchedEffect(prefixInput) {
        val trimmed = prefixInput.trim()
        if (trimmed.length in 1..5) {
            isCheckingCount = true
            scope.launch(Dispatchers.IO) {
                val count = dbHelper.countVcfEligibleRecords(trimmed)
                withContext(Dispatchers.Main) {
                    matchingCount = count
                    isCheckingCount = false
                }
            }
        } else {
            matchingCount = 0L
            isCheckingCount = false
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
            border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderBright)
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
                    text = "VCF CONTACTS COMPILER",
                    color = HudCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "VCARD 3.0 GENERATOR // NATIONAL CODE FILTER",
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
                title = "VCF CHECKPOINT DETECTED // RESUMABLE",
                titleColor = HudAmber,
                statusBadge = "UNFINISHED JOB",
                statusColor = HudAmber
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
                            text = "PREVIOUS EXPORT INTERRUPTED (PREFIX: ${progress.prefix})",
                            color = HudAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Checkpoint: ${String.format("%,d", progress.processedRecords)} / ${String.format("%,d", progress.totalRecords)} contacts written to ${File(progress.filePath).name}. Resume to continue without starting over.",
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
                            text = "RESUME EXPORT",
                            onClick = {
                                scope.launch {
                                    vcfExporter.resumeExport()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = HudGreen,
                            icon = Icons.Default.PlayArrow
                        )

                        HudButton(
                            text = "DISCARD",
                            onClick = {
                                dbHelper.clearVcfSession()
                                vcfExporter.checkForRecoverableSession()
                            },
                            modifier = Modifier.width(100.dp),
                            accentColor = HudRed,
                            isSecondary = true
                        )
                    }
                }
            }
        }

        // Live Telemetry Card (When Running, Paused, or Completed)
        if (progress.status != ImportStatus.IDLE) {
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                title = "VCF EXPORT ENGINE TELEMETRY",
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
                    // Metric grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        VcfMetricBox(
                            label = "PREFIX FILTER",
                            value = progress.prefix.ifEmpty { prefixInput },
                            accentColor = HudAmber,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        VcfMetricBox(
                            label = "THROUGHPUT",
                            value = "${String.format("%,d", progress.recordsPerSecond)} rec/s",
                            accentColor = HudCyan,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        VcfMetricBox(
                            label = "WRITTEN / TOTAL",
                            value = "${String.format("%,d", progress.processedRecords)} / ${String.format("%,d", progress.totalRecords)}",
                            accentColor = HudTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        VcfMetricBox(
                            label = "ESTIMATED ETA",
                            value = if (progress.etaSeconds > 0) formatSeconds(progress.etaSeconds) else if (progress.status == ImportStatus.COMPLETED) "00:00" else "--:--",
                            accentColor = HudGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        VcfMetricBox(
                            label = "FILE SIZE",
                            value = formatFileSize(progress.fileSizeBytes),
                            accentColor = HudCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        VcfMetricBox(
                            label = "ELAPSED",
                            value = formatSeconds(progress.elapsedSeconds),
                            accentColor = HudTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress Bar
                    val fraction = if (progress.totalRecords > 0) {
                        (progress.processedRecords.toFloat() / progress.totalRecords.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "VCF PROGRESS: ${(fraction * 100).toInt()}%",
                            color = HudCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "STATUS: ${progress.status.name}",
                            color = when (progress.status) {
                                ImportStatus.RUNNING -> HudCyan
                                ImportStatus.PAUSED -> HudAmber
                                ImportStatus.COMPLETED -> HudGreen
                                else -> HudTextSecondary
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HudProgressBar(
                        progress = fraction,
                        color = when (progress.status) {
                            ImportStatus.PAUSED -> HudAmber
                            ImportStatus.COMPLETED -> HudGreen
                            else -> HudCyan
                        }
                    )

                    if (progress.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "! ${progress.errorMessage}",
                            color = HudRed,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Controls during execution
                    when (progress.status) {
                        ImportStatus.RUNNING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HudButton(
                                    text = "PAUSE EXPORT",
                                    onClick = { vcfExporter.pause() },
                                    modifier = Modifier.weight(1f),
                                    accentColor = HudAmber,
                                    icon = Icons.Default.Pause
                                )
                                HudButton(
                                    text = "ABORT",
                                    onClick = { vcfExporter.cancel() },
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
                                    text = "RESUME EXPORT",
                                    onClick = {
                                        scope.launch { vcfExporter.resumeExport() }
                                    },
                                    modifier = Modifier.weight(1f),
                                    accentColor = HudGreen,
                                    icon = Icons.Default.PlayArrow
                                )
                                HudButton(
                                    text = "DISCARD",
                                    onClick = {
                                        vcfExporter.cancel()
                                        dbHelper.clearVcfSession()
                                        vcfExporter.checkForRecoverableSession()
                                    },
                                    modifier = Modifier.weight(1f),
                                    accentColor = HudRed,
                                    icon = Icons.Default.Stop,
                                    isSecondary = true
                                )
                            }
                        }
                        ImportStatus.COMPLETED -> {
                            // Completed action buttons: Open in Contacts & Share
                            val vcfFile = File(progress.filePath)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = HudGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "VCF READY FOR CONTACTS IMPORT",
                                        color = HudGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "File saved at: ${vcfFile.name} (${formatFileSize(progress.fileSizeBytes)})",
                                    color = HudTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HudButton(
                                        text = "IMPORT TO CONTACTS",
                                        onClick = {
                                            try {
                                                val intent = vcfExporter.createOpenInContactsIntent(vcfFile)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open contacts handler: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        accentColor = HudGreen,
                                        icon = Icons.Default.PersonAdd
                                    )

                                    HudButton(
                                        text = "SHARE VCF",
                                        onClick = {
                                            try {
                                                val shareIntent = vcfExporter.createShareVcfIntent(vcfFile)
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Contacts VCF"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        accentColor = HudCyan,
                                        icon = Icons.Default.Share
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Configuration Card: Prompt for First 3 Digits of NATIONAL_CODE
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudCyan,
            title = "NATIONAL CODE PREFIX PROMPT",
            titleColor = HudCyan,
            statusBadge = if (matchingCount > 0) "$matchingCount ELIGIBLE" else "WAITING INPUT",
            statusColor = if (matchingCount > 0) HudGreen else HudAmber
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SPECIFY FIRST THREE DIGITS OF NATIONAL CODE",
                    color = HudTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "The system will query all indexed records beginning with this prefix that contain both a valid Full Name and Phone Number, compiling them into a phone contacts VCF file.",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input Field for First 3 Digits
                HudTextField(
                    value = prefixInput,
                    onValueChange = { input ->
                        if (input.length <= 3 && input.all { it.isDigit() }) {
                            prefixInput = input
                        }
                    },
                    label = "NATIONAL CODE PREFIX (3 DIGITS)",
                    placeholder = "e.g. 001",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContactPage,
                            contentDescription = null,
                            tint = HudCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                    })
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick recommendation chips
                Text(
                    text = "QUICK PRESETS:",
                    color = HudTextDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    samplePrefixes.take(5).forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    triggerHudHaptic(context, 15)
                                    prefixInput = preset
                                },
                            color = if (prefixInput == preset) HudCyan.copy(alpha = 0.2f) else HudSurfaceElevated,
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (prefixInput == preset) HudCyan else HudBorder
                            )
                        ) {
                            Text(
                                text = preset,
                                color = if (prefixInput == preset) HudCyan else HudTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Eligibility Counter Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, HudBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "QUALIFYING CONTACTS DETECTED",
                                color = HudTextDim,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isCheckingCount) "SCANNING DATABASE..." else if (prefixInput.length >= 1) "${String.format("%,d", matchingCount)} records with Name & Mobile" else "Enter 3-digit prefix above",
                                color = if (matchingCount > 0) HudGreen else HudTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (matchingCount > 0) {
                            HudBadge(
                                text = "READY",
                                color = HudGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start Export Button
                val canStart = prefixInput.length == 3 && matchingCount > 0 && progress.status != ImportStatus.RUNNING

                HudButton(
                    text = "COMPILE & EXPORT VCF FILE",
                    onClick = {
                        keyboardController?.hide()
                        scope.launch {
                            vcfExporter.startExport(prefixInput)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canStart,
                    accentColor = HudCyan,
                    icon = Icons.Default.Download
                )

                if (prefixInput.length < 3) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "! Please input all 3 digits of the national code prefix",
                        color = HudAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (matchingCount == 0L && !isCheckingCount) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "! No matching records found with valid Name and Mobile for prefix '$prefixInput'",
                        color = HudAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // VCF Technical Specifications Card
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudBorder,
            title = "VCF CONTACTS ARCHITECTURE",
            titleColor = HudTextSecondary
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                VcfSpecRow(name = "STANDARD", value = "vCard 3.0 (RFC 2426)")
                VcfSpecRow(name = "COMPATIBILITY", value = "Android Contacts, iOS, Google, Samsung")
                VcfSpecRow(name = "ENCODING", value = "UTF-8 Formatted")
                VcfSpecRow(name = "DATA MAPPING", value = "FN, N, TEL (CELL), NOTE (NAT, CARD, DOB)")
                VcfSpecRow(name = "FILTER CONSTRAINT", value = "prefix(3) + name != '' + mobile != ''")
                VcfSpecRow(name = "FAULT TOLERANCE", value = "Crash-Resilient State Checkpoint Engine")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun VcfMetricBox(
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = HudTextDim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun VcfSpecRow(name: String, value: String) {
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

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

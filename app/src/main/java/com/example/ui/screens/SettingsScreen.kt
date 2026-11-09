package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackupAction
import com.example.data.DatabaseHelper
import com.example.data.FastBackupManager
import com.example.security.BiometricHelper
import com.example.security.SecurityManager
import com.example.ui.components.HudButton
import com.example.ui.components.HudCard
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    securityManager: SecurityManager,
    backupManager: FastBackupManager,
    dbHelper: DatabaseHelper,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val isBiometricHardwareAvailable = remember { BiometricHelper.isBiometricAvailable(context) }
    var biometricEnabled by remember { mutableStateOf(securityManager.isBiometricEnabled()) }

    // Database telemetry stats
    var dbRecordCount by remember { mutableLongStateOf(0L) }
    var dbFileSizeMb by remember { mutableStateOf(0.0) }

    val refreshDbStats = {
        scope.launch(Dispatchers.IO) {
            val count = dbHelper.getTotalCount()
            val file = dbHelper.getDatabaseFile()
            val sizeMb = if (file.exists()) file.length() / (1024.0 * 1024.0) else 0.0
            withContext(Dispatchers.Main) {
                dbRecordCount = count
                dbFileSizeMb = sizeMb
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDbStats()
    }

    // Backup & Restore state
    val backupProgress by backupManager.progress.collectAsState()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    // Storage Access Framework Launchers
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri != null) {
            triggerHudHaptic(context, 25)
            scope.launch {
                backupManager.createBackup(uri)
                refreshDbStats()
            }
        }
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
            triggerHudHaptic(context, 20)
        }
    }

    // Password change state
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordSuccess by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HudBackground)
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Top Navigation Action Bar with uniform width and height
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
                    contentDescription = "Return",
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

        Spacer(modifier = Modifier.height(14.dp))

        // ULTRA-FAST LIGHTWEIGHT BACKUP & RESTORE SECTION
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudCyan,
            title = "FAST BACKUP & RESTORE // LIGHTWEIGHT ARCHIVE",
            titleColor = HudCyan,
            statusBadge = "ULTRA-FAST",
            statusColor = HudGreen
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "HARDWARE-ACCELERATED BINARY SNAPSHOT",
                    color = HudTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Instant stream backup and restore in compact .nbk.gz format. Flushes WAL journals and preserves 100% of B-Tree indexes with ~75% compression reduction and zero memory spikes.",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Current Database Metrics Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HudSurfaceElevated, RoundedCornerShape(2.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE RECORDS",
                            color = HudTextDim,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = String.format("%,d", dbRecordCount),
                            color = HudCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DATABASE FILE SIZE",
                            color = HudTextDim,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = String.format("%.2f MB", dbFileSizeMb),
                            color = HudAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Backup/Restore Progress & Telemetry Card
                if (backupProgress.isRunning || backupProgress.action == BackupAction.COMPLETED || backupProgress.action == BackupAction.ERROR) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        color = HudBackground,
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (backupProgress.action) {
                                BackupAction.COMPLETED -> HudGreen
                                BackupAction.ERROR -> HudRed
                                else -> HudCyan
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = backupProgress.title.ifEmpty { "OPERATION IN PROGRESS" },
                                    color = when (backupProgress.action) {
                                        BackupAction.COMPLETED -> HudGreen
                                        BackupAction.ERROR -> HudRed
                                        else -> HudCyan
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                if (backupProgress.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = HudCyan,
                                        strokeWidth = 1.5.dp
                                    )
                                }
                            }

                            if (backupProgress.details.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = backupProgress.details,
                                    color = HudTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }

                            if (backupProgress.error != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ERROR: ${backupProgress.error}",
                                    color = HudRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (backupProgress.throughputMBps > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = HudGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "THROUGHPUT: ${String.format("%.1f", backupProgress.throughputMBps)} MB/s",
                                            color = HudGreen,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Text(
                                        text = "TIME: ${String.format("%.2f", backupProgress.elapsedMs / 1000.0)} s",
                                        color = HudCyan,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons: Ultra-Fast Backup & Restore
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Backup Button
                    HudButton(
                        text = "CREATE BACKUP",
                        onClick = {
                            val timestamp = System.currentTimeMillis()
                            createBackupLauncher.launch("nullemperor_backup_$timestamp.nbk.gz")
                        },
                        modifier = Modifier.weight(1f),
                        accentColor = HudCyan,
                        icon = Icons.Default.CloudDownload,
                        enabled = !backupProgress.isRunning
                    )

                    // Restore Button
                    HudButton(
                        text = "RESTORE BACKUP",
                        onClick = {
                            openRestoreLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        accentColor = HudAmber,
                        icon = Icons.Default.Restore,
                        enabled = !backupProgress.isRunning,
                        isSecondary = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // BIOMETRIC SECURITY SECTION
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudCyan,
            title = "SECURITY PROTOCOL // BIOMETRICS",
            titleColor = HudCyan,
            statusBadge = if (biometricEnabled) "ACTIVE" else "OFF",
            statusColor = if (biometricEnabled) HudGreen else HudAmber
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "BIOMETRIC AUTHENTICATION",
                    color = HudTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enable or disable hardware biometric verification (fingerprint / face) for terminal unlock.",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isBiometricHardwareAvailable) {
                                val newState = !biometricEnabled
                                biometricEnabled = newState
                                securityManager.setBiometricEnabled(newState)
                                triggerHudHaptic(context, 20)
                                Toast.makeText(
                                    context,
                                    if (newState) "Biometric unlock activated" else "Biometric unlock disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (biometricEnabled) HudCyan else HudBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (biometricEnabled) HudCyan else HudTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (biometricEnabled) "BIOMETRIC UNLOCK: ENABLED" else "BIOMETRIC UNLOCK: DISABLED",
                                    color = if (biometricEnabled) HudCyan else HudTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isBiometricHardwareAvailable) "Hardware biometric sensor ready" else "No enrolled hardware biometrics",
                                    color = if (isBiometricHardwareAvailable) HudGreen else HudAmber,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { checked ->
                                if (isBiometricHardwareAvailable) {
                                    biometricEnabled = checked
                                    securityManager.setBiometricEnabled(checked)
                                    triggerHudHaptic(context, 20)
                                }
                            },
                            enabled = isBiometricHardwareAvailable,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HudCyan,
                                checkedTrackColor = HudCyan.copy(alpha = 0.3f),
                                uncheckedThumbColor = HudTextDim,
                                uncheckedTrackColor = HudBackground
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CHANGE PASSWORD SECTION
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudAmber,
            title = "CHANGE MASTER PASSWORD",
            titleColor = HudAmber,
            statusBadge = "CIPHER KEY",
            statusColor = HudAmber
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CIPHER MODIFICATION",
                    color = HudTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verify current password to encrypt vault with a new master key.",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                HudTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        passwordError = null
                        passwordSuccess = null
                    },
                    label = "CURRENT PASSWORD",
                    placeholder = "Enter current master key",
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = HudAmber, modifier = Modifier.size(16.dp))
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = HudAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                HudTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        passwordError = null
                        passwordSuccess = null
                    },
                    label = "NEW PASSWORD",
                    placeholder = "Min 4 characters",
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = HudCyan, modifier = Modifier.size(16.dp))
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.height(10.dp))

                HudTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        passwordError = null
                        passwordSuccess = null
                    },
                    label = "CONFIRM NEW PASSWORD",
                    placeholder = "Re-enter new password",
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null, tint = HudCyan, modifier = Modifier.size(16.dp))
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    isError = passwordError != null,
                    errorMessage = passwordError
                )

                if (passwordSuccess != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = passwordSuccess ?: "",
                        color = HudGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HudButton(
                    text = "UPDATE MASTER CIPHER",
                    onClick = {
                        keyboardController?.hide()
                        if (currentPassword.isEmpty()) {
                            passwordError = "Enter current password"
                            return@HudButton
                        }
                        if (newPassword.length < 4) {
                            passwordError = "New password must be at least 4 characters"
                            return@HudButton
                        }
                        if (newPassword != confirmPassword) {
                            passwordError = "New passwords do not match"
                            return@HudButton
                        }

                        val success = securityManager.updatePassword(currentPassword, newPassword)
                        if (success) {
                            triggerHudHaptic(context, 40)
                            passwordError = null
                            passwordSuccess = "MASTER CIPHER UPDATED SUCCESSFULLY"
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            Toast.makeText(context, "Master password updated", Toast.LENGTH_SHORT).show()
                        } else {
                            passwordError = "Current password invalid"
                            triggerHudHaptic(context, 70)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = HudAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // CONFIRM RESTORE DIALOG
    if (showRestoreConfirmDialog && pendingRestoreUri != null) {
        BasicAlertDialog(onDismissRequest = {
            showRestoreConfirmDialog = false
            pendingRestoreUri = null
        }) {
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = HudAmber,
                backgroundColor = HudBackground,
                title = "CONFIRM DATABASE RESTORE",
                titleColor = HudAmber
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = HudAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OVERWRITE ACTIVE DATABASE?",
                            color = HudTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Restoring this backup will replace all currently stored records (${String.format("%,d", dbRecordCount)} records) with the snapshot from the selected archive file.",
                        color = HudTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HudButton(
                            text = "PROCEED RESTORE",
                            onClick = {
                                val uriToRestore = pendingRestoreUri
                                showRestoreConfirmDialog = false
                                pendingRestoreUri = null
                                if (uriToRestore != null) {
                                    triggerHudHaptic(context, 35)
                                    scope.launch {
                                        backupManager.restoreBackup(uriToRestore)
                                        refreshDbStats()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            accentColor = HudAmber
                        )

                        HudButton(
                            text = "CANCEL",
                            onClick = {
                                showRestoreConfirmDialog = false
                                pendingRestoreUri = null
                            },
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

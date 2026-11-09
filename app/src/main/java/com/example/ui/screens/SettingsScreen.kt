package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun SettingsScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isBiometricHardwareAvailable = remember { BiometricHelper.isBiometricAvailable(context) }
    var biometricEnabled by remember { mutableStateOf(securityManager.isBiometricEnabled()) }

    // Password change fields
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

        // Top Action Bar with uniform width and height fitting other buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
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
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Security Configuration Card
        HudCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HudCyan,
            title = "SECURITY CONFIGURATION // VAULT SETTINGS",
            titleColor = HudCyan,
            statusBadge = "ACTIVE",
            statusColor = HudGreen
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Biometric Toggle Section
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

        // Change Password Card
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
}

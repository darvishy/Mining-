package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.components.DeveloperHeader
import com.example.ui.components.HudAvatar
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
fun AuthScreen(
    securityManager: SecurityManager,
    onAuthenticated: () -> Unit,
    onRequestBiometricPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isSetup = remember { mutableStateOf(securityManager.isSetupCompleted()) }
    val isBiometricAvailable = remember { BiometricHelper.isBiometricAvailable(context) }

    // Setup state
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var enableBiometricSetup by remember { mutableStateOf(isBiometricAvailable) }
    var setupError by remember { mutableStateOf<String?>(null) }
    // Unlock state
    var unlockPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HudBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isSetup.value) {
            // DEVELOPER ATTRIBUTION & TELEGRAM ON SETUP WIZARD
            DeveloperHeader()

            Spacer(modifier = Modifier.height(16.dp))

            // INITIAL SETUP SCREEN
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                title = "INITIAL SETUP // SECURITY PROTOCOL",
                titleColor = HudCyan,
                statusBadge = "FIRST LAUNCH",
                statusColor = HudAmber
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "INITIAL SECURITY CIPHER REQUIRED",
                        color = HudTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Establish a master password and configure hardware biometric authentication to encrypt and protect local database queries.",
                        color = HudTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HudTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            setupError = null
                        },
                        label = "MASTER PASSWORD",
                        placeholder = "Minimum 4 characters",
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = HudCyan, modifier = Modifier.size(18.dp))
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = HudCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HudTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            setupError = null
                        },
                        label = "CONFIRM PASSWORD",
                        placeholder = "Re-enter password",
                        leadingIcon = {
                            Icon(Icons.Default.Security, contentDescription = null, tint = HudCyan, modifier = Modifier.size(18.dp))
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        isError = setupError != null,
                        errorMessage = setupError
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Biometric Toggle Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HudSurfaceElevated, RoundedCornerShape(2.dp))
                            .clickable {
                                triggerHudHaptic(context, 15)
                                enableBiometricSetup = !enableBiometricSetup
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    if (enableBiometricSetup) HudCyan.copy(alpha = 0.2f) else HudBackground,
                                    RoundedCornerShape(2.dp)
                                )
                                .background(
                                    if (enableBiometricSetup) HudCyan else HudBorderBright
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (enableBiometricSetup) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = HudBackground, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = if (enableBiometricSetup) HudCyan else HudTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BIOMETRIC AUTHENTICATION",
                                    color = if (enableBiometricSetup) HudCyan else HudTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (isBiometricAvailable) "Hardware biometric sensor ready (Fingerprint/Face)" else "No enrolled biometrics detected on device",
                                color = if (isBiometricAvailable) HudGreen else HudAmber,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    HudButton(
                        text = "INITIALIZE SECURITY CLEARANCE",
                        onClick = {
                            keyboardController?.hide()
                            if (newPassword.length < 4) {
                                setupError = "Password must be at least 4 characters"
                                return@HudButton
                            }
                            if (newPassword != confirmPassword) {
                                setupError = "Passwords do not match"
                                return@HudButton
                            }

                            val success = securityManager.setupCredentials(newPassword, enableBiometricSetup)
                            if (success) {
                                triggerHudHaptic(context, 50)
                                isSetup.value = true
                                onAuthenticated()
                            } else {
                                setupError = "Failed to store encrypted cipher"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = HudCyan
                    )
                }
            }
        } else {
            // Animated HUD Avatar on Lockscreen
            HudAvatar(
                totalRecordsCount = 0L,
                statusText = "SYS LOCKED // CLEARANCE REQUIRED"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // UNLOCK SCREEN
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                title = "GATEWAY // ACCESS RESTRICTED",
                titleColor = HudRed,
                statusBadge = "SYS LOCKED",
                statusColor = HudRed
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(HudSurfaceElevated, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = HudCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "AUTHENTICATION REQUIRED",
                        color = HudTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )

                    Text(
                        text = "Provide biometric clearance or master password",
                        color = HudTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    HudTextField(
                        value = unlockPassword,
                        onValueChange = {
                            unlockPassword = it
                            unlockError = null
                        },
                        label = "MASTER PASSWORD",
                        placeholder = "Enter access key",
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = HudCyan, modifier = Modifier.size(18.dp))
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            if (securityManager.verifyPassword(unlockPassword)) {
                                triggerHudHaptic(context, 40)
                                onAuthenticated()
                            } else {
                                unlockError = "ACCESS DENIED: Invalid master password"
                                triggerHudHaptic(context, 80)
                            }
                        }),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = HudCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        isError = unlockError != null,
                        errorMessage = unlockError
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HudButton(
                        text = "VERIFY ACCESS KEY",
                        onClick = {
                            keyboardController?.hide()
                            if (securityManager.verifyPassword(unlockPassword)) {
                                triggerHudHaptic(context, 40)
                                onAuthenticated()
                            } else {
                                unlockError = "ACCESS DENIED: Invalid master password"
                                triggerHudHaptic(context, 80)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = HudCyan
                    )

                    if (securityManager.isBiometricEnabled() && isBiometricAvailable) {
                        Spacer(modifier = Modifier.height(12.dp))

                        HudButton(
                            text = "INITIALIZE BIOMETRIC SCAN",
                            onClick = {
                                onRequestBiometricPrompt()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = HudAmber,
                            icon = Icons.Default.Fingerprint,
                            isSecondary = true
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "[RESET CREDENTIALS]",
                        color = HudTextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable {
                                securityManager.resetSecurity()
                                isSetup.value = false
                                newPassword = ""
                                confirmPassword = ""
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

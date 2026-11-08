package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.data.CsvBatchImporter
import com.example.data.DatabaseHelper
import com.example.security.BiometricHelper
import com.example.security.SecurityManager
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ImportScreen
import com.example.ui.screens.MainHudScreen
import com.example.ui.theme.HudBackground
import com.example.ui.theme.NullHudTheme

import com.example.data.VcfExporter
import com.example.ui.screens.VcfExportScreen

enum class AppScreen {
    AUTH,
    MAIN,
    IMPORT,
    VCF_EXPORT
}

class MainActivity : FragmentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var importer: CsvBatchImporter
    private lateinit var vcfExporter: VcfExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        securityManager = SecurityManager.getInstance(this)
        dbHelper = DatabaseHelper.getInstance(this)
        importer = CsvBatchImporter(this, dbHelper)
        vcfExporter = VcfExporter(this, dbHelper)

        setContent {
            NullHudTheme {
                var currentScreen by remember {
                    mutableStateOf(AppScreen.AUTH)
                }

                val showBiometric = {
                    BiometricHelper.showBiometricPrompt(
                        activity = this@MainActivity,
                        title = "NULL-HUD ACCESS VERIFICATION",
                        subtitle = "Developer: NullEmperor // Terminal Security",
                        negativeButtonText = "USE MASTER KEY",
                        onSuccess = {
                            currentScreen = AppScreen.MAIN
                        },
                        onError = { _, errString ->
                            // Toast or silent fallback to password entry
                        },
                        onFailed = {
                            Toast.makeText(this@MainActivity, "Biometric unverified. Enter password.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HudBackground)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(HudBackground)
                    ) {
                        when (currentScreen) {
                            AppScreen.AUTH -> {
                                AuthScreen(
                                    securityManager = securityManager,
                                    onAuthenticated = {
                                        currentScreen = AppScreen.MAIN
                                    },
                                    onRequestBiometricPrompt = {
                                        showBiometric()
                                    }
                                )
                            }
                            AppScreen.MAIN -> {
                                BackHandler {
                                    // Lock terminal on back from main
                                    currentScreen = AppScreen.AUTH
                                }

                                MainHudScreen(
                                    dbHelper = dbHelper,
                                    importer = importer,
                                    vcfExporter = vcfExporter,
                                    onNavigateToImport = {
                                        currentScreen = AppScreen.IMPORT
                                    },
                                    onNavigateToVcf = {
                                        currentScreen = AppScreen.VCF_EXPORT
                                    },
                                    onLockTerminal = {
                                        currentScreen = AppScreen.AUTH
                                    }
                                )
                            }
                            AppScreen.IMPORT -> {
                                BackHandler {
                                    currentScreen = AppScreen.MAIN
                                }

                                ImportScreen(
                                    importer = importer,
                                    dbHelper = dbHelper,
                                    onBack = {
                                        currentScreen = AppScreen.MAIN
                                    }
                                )
                            }
                            AppScreen.VCF_EXPORT -> {
                                BackHandler {
                                    currentScreen = AppScreen.MAIN
                                }

                                VcfExportScreen(
                                    vcfExporter = vcfExporter,
                                    dbHelper = dbHelper,
                                    onBack = {
                                        currentScreen = AppScreen.MAIN
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

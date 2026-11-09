package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CsvBatchImporter
import com.example.data.DatabaseHelper
import com.example.data.ImportStatus
import com.example.data.PersonRecord
import com.example.data.SearchField
import com.example.data.SearchResult
import com.example.ui.components.DeveloperHeader
import com.example.ui.components.HudAvatar
import com.example.ui.components.HudBadge
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

import com.example.data.VcfExporter
import androidx.compose.material.icons.filled.ContactPage

@Composable
fun MainHudScreen(
    dbHelper: DatabaseHelper,
    importer: CsvBatchImporter,
    vcfExporter: VcfExporter,
    onNavigateToImport: () -> Unit,
    onNavigateToVcf: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLockTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val importProgress by importer.progress.collectAsState()
    val vcfProgress by vcfExporter.progress.collectAsState()
    var totalDbCount by remember { mutableLongStateOf(0L) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedField by remember { mutableStateOf(SearchField.ALL) }
    var searchResult by remember { mutableStateOf<SearchResult?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var selectedRecordForDetail by remember { mutableStateOf<PersonRecord?>(null) }

    val isSearchAtTop = isSearchFocused || searchQuery.isNotEmpty() || searchResult != null || isSearching

    // Refresh database count
    val refreshCount = {
        scope.launch(Dispatchers.IO) {
            val count = dbHelper.getTotalCount()
            withContext(Dispatchers.Main) {
                totalDbCount = count
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshCount()
    }

    // Execute indexed search
    val executeSearch = {
        if (searchQuery.trim().isNotEmpty()) {
            isSearching = true
            scope.launch(Dispatchers.IO) {
                val res = dbHelper.searchRecords(searchQuery, selectedField, limit = 100)
                withContext(Dispatchers.Main) {
                    searchResult = res
                    isSearching = false
                }
            }
        } else {
            searchResult = null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (searchQuery.isEmpty() && searchResult == null) {
                        isSearchFocused = false
                    }
                })
            }
            .padding(horizontal = 14.dp)
    ) {
        // Top Space
        item {
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Tactical Action Bar: 4 Uniform Equal-Width Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Ingest Data Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            triggerHudHaptic(context, 20)
                            onNavigateToImport()
                        },
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (importProgress.status == ImportStatus.RUNNING) HudCyan else HudBorderBright
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Data Import",
                            tint = if (importProgress.status == ImportStatus.RUNNING) HudCyan else HudTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (importProgress.status == ImportStatus.RUNNING) "INGEST" else "INGEST",
                            color = if (importProgress.status == ImportStatus.RUNNING) HudCyan else HudTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 2. VCF Contacts Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            triggerHudHaptic(context, 20)
                            onNavigateToVcf()
                        },
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (vcfProgress.status == ImportStatus.RUNNING || vcfProgress.hasRecoverableSession) HudAmber else HudBorderBright
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPage,
                            contentDescription = "VCF",
                            tint = if (vcfProgress.status == ImportStatus.RUNNING) HudAmber else HudCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "VCF",
                            color = if (vcfProgress.status == ImportStatus.RUNNING) HudAmber else HudTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 3. Settings Button (Fits width uniformly)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            triggerHudHaptic(context, 20)
                            onNavigateToSettings()
                        },
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderBright)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = HudCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "SETTINGS",
                            color = HudTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 4. Lock Terminal Button (Fits width uniformly)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            triggerHudHaptic(context, 20)
                            onLockTerminal()
                        },
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderBright)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = HudAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "LOCK",
                            color = HudAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // If search is not active, display Animated HUD Avatar above search interface
        if (!isSearchAtTop) {
            item {
                HudAvatar(
                    totalRecordsCount = totalDbCount,
                    statusText = when (importProgress.status) {
                        ImportStatus.RUNNING -> "IMPORTING DATA"
                        ImportStatus.PAUSED -> "IMPORT PAUSED"
                        else -> "RADAR: ONLINE"
                    },
                    onAvatarClick = {
                        refreshCount()
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Search Interface Section (Moves to top when search is clicked/active)
        item {
            HudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = HudCyan,
                title = "DATA RETRIEVAL TERMINAL // B-TREE INDEXED",
                titleColor = HudCyan,
                statusBadge = "SUB-MS LATENCY",
                statusColor = HudGreen
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Filter Mode Chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(SearchField.entries) { field ->
                            val isSelected = field == selectedField
                            Surface(
                                modifier = Modifier
                                    .clickable {
                                        triggerHudHaptic(context, 15)
                                        selectedField = field
                                        executeSearch()
                                    },
                                color = if (isSelected) HudCyan.copy(alpha = 0.2f) else HudSurfaceElevated,
                                shape = RoundedCornerShape(2.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.dp else 0.8.dp,
                                    if (isSelected) HudCyan else HudBorder
                                )
                            ) {
                                Text(
                                    text = field.label,
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

                    // Main Search Input Field - Right Aligned
                    HudTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.length >= 2) {
                                executeSearch()
                            } else if (it.isEmpty()) {
                                searchResult = null
                            }
                        },
                        label = "SEARCH QUERY [INDEXED: ${selectedField.name}]",
                        placeholder = when (selectedField) {
                            SearchField.ALL -> "Enter national code, card no, mobile, name..."
                            SearchField.NATIONAL_CODE -> "e.g. 0081234567"
                            SearchField.CARD_NO -> "e.g. 6037991234..."
                            SearchField.MOBILE -> "e.g. 09123456789"
                            SearchField.FULL_NAME -> "e.g. Rezaei, Hossein..."
                        },
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isSearchFocused = true
                                }
                            },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = HudCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            executeSearch()
                        })
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Search Action Buttons - Center Aligned
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "INDEX KEYS: NAT_CODE, CARD_NO, MOBILE, NAME",
                            color = HudTextDim,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        HudButton(
                            text = "FIND",
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                executeSearch()
                            },
                            accentColor = HudCyan,
                            icon = Icons.Default.Search,
                            modifier = Modifier.fillMaxWidth(0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Live Search Benchmark Telemetry Bar (When search was executed)
        if (searchResult != null) {
            item {
                HudCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = HudGreen,
                    backgroundColor = HudSurfaceElevated,
                    cornerLength = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = HudGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LATENCY: ${String.format("%.2f", searchResult?.queryDurationMs)} ms",
                                color = HudGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "INDEX: ${searchResult?.indexUsed}",
                            color = HudCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "MATCHES: ${searchResult?.records?.size ?: 0}",
                            color = HudTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Search Results List
        val records = searchResult?.records ?: emptyList()
        if (records.isNotEmpty()) {
            items(records, key = { it.id }) { record ->
                RecordItemCard(
                    record = record,
                    onClick = {
                        triggerHudHaptic(context, 15)
                        selectedRecordForDetail = record
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else if (searchResult != null && searchResult?.records?.isEmpty() == true) {
            item {
                HudCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = HudAmber,
                    title = "SEARCH RESULT",
                    titleColor = HudAmber
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "[ NO CLASSIFIED RECORDS MATCH CIPHER ]",
                            color = HudAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Query \"$searchQuery\" returned 0 matches in database of ${String.format("%,d", totalDbCount)} records.",
                            color = HudTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        } else if (totalDbCount == 0L) {
            // Initial empty database guidance card
            item {
                HudCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = HudAmber,
                    backgroundColor = HudSurfaceElevated,
                    title = "DATABASE STATUS // UNPOPULATED",
                    titleColor = HudAmber,
                    statusBadge = "STANDBY",
                    statusColor = HudAmber
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "DATABASE CONTAINS 0 RECORDS",
                            color = HudTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import a CSV database file containing records with schema (NATIONAL_CODE, CARD_NO, FULL_NAME, BIRTH_DATE, MOBILE).",
                            color = HudTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        HudButton(
                            text = "DATA INGESTION TERMINAL",
                            onClick = onNavigateToImport,
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = HudCyan,
                            icon = Icons.Default.CloudDownload
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // When search is active and moved to top, render Animated Avatar at bottom of list
        if (isSearchAtTop) {
            item {
                Spacer(modifier = Modifier.height(14.dp))
                HudAvatar(
                    totalRecordsCount = totalDbCount,
                    statusText = when (importProgress.status) {
                        ImportStatus.RUNNING -> "IMPORTING DATA"
                        ImportStatus.PAUSED -> "IMPORT PAUSED"
                        else -> "RADAR: ONLINE"
                    },
                    onAvatarClick = {
                        refreshCount()
                    }
                )
            }
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Detail Bottom Sheet / Dialog
    selectedRecordForDetail?.let { record ->
        RecordDetailDialog(
            record = record,
            onDismiss = { selectedRecordForDetail = null }
        )
    }
}

@Composable
private fun RecordItemCard(
    record: PersonRecord,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = HudSurfaceElevated,
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Center-aligned Record Name & ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.fullName.ifEmpty { "UNNAMED RECORD" },
                    color = HudTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(8.dp))
                HudBadge(
                    text = "ID #${record.id}",
                    color = HudTextDim,
                    backgroundColor = HudBackground
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Center-aligned National Code & Mobile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // National Code
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = HudAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = record.nationalCode,
                        color = HudAmber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Mobile
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = HudCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = record.mobile,
                        color = HudCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Center-aligned Card Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = HudTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = record.cardNo,
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

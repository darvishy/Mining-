package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class CsvBatchImporter(
    private val context: Context,
    private val dbHelper: DatabaseHelper
) {
    companion object {
        private const val TAG = "CsvBatchImporter"
        const val BATCH_SIZE = 10_000
        const val BENCHMARK_SOURCE_PREFIX = "SYNTHETIC_BENCHMARK_"
    }

    // Single-file progress (also mirrors active queue item for Main HUD compatibility)
    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    // Multi-File Ingestion Queue State
    private val _queue = MutableStateFlow<List<CsvQueueItem>>(emptyList())
    val queue: StateFlow<List<CsvQueueItem>> = _queue.asStateFlow()

    private val _queueOverallProgress = MutableStateFlow(QueueOverallProgress())
    val queueOverallProgress: StateFlow<QueueOverallProgress> = _queueOverallProgress.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val skipCurrentRequested = AtomicBoolean(false)
    @Volatile private var isRunning = false

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var queueJob: Job? = null

    init {
        checkForRecoverableSession()
    }

    fun checkForRecoverableSession() {
        try {
            val session = dbHelper.getActiveSession()
            if (session != null && (session.status == ImportStatus.RUNNING.name || session.status == ImportStatus.PAUSED.name)) {
                _progress.value = ImportProgress(
                    status = ImportStatus.PAUSED,
                    sourceName = session.sourceName,
                    processedRecords = session.processedRecords,
                    totalRecords = session.totalRecords,
                    elapsedSeconds = session.elapsedMs / 1000,
                    hasRecoverableSession = true
                )
            } else {
                _progress.value = _progress.value.copy(hasRecoverableSession = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session", e)
        }
    }

    fun pause() {
        pauseQueue()
    }

    fun cancel() {
        cancelQueue()
    }

    fun pauseQueue() {
        if (isRunning) {
            isPaused.set(true)
            _queueOverallProgress.value = _queueOverallProgress.value.copy(isPaused = true)
        }
    }

    fun resumeQueue() {
        if (isRunning && isPaused.get()) {
            isPaused.set(false)
            _queueOverallProgress.value = _queueOverallProgress.value.copy(isPaused = false)
        } else if (!isRunning) {
            startQueue()
        }
    }

    fun cancelQueue() {
        isCancelled.set(true)
        isPaused.set(false)
        skipCurrentRequested.set(false)
    }

    fun skipCurrentFile() {
        skipCurrentRequested.set(true)
    }

    fun removeFromQueue(itemId: String) {
        val currentList = _queue.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = currentList[index]
            if (item.status == QueueItemStatus.IMPORTING) {
                skipCurrentFile()
            }
            try {
                val cacheFile = File(getQueueDir(), "queue_${item.id}.csv")
                if (cacheFile.exists()) cacheFile.delete()
            } catch (_: Exception) {}
            currentList.removeAt(index)
            _queue.value = currentList
            updateOverallMetrics()
        }
    }

    fun clearCompletedQueue() {
        val currentList = _queue.value.filter { it.status != QueueItemStatus.COMPLETED && it.status != QueueItemStatus.FAILED && it.status != QueueItemStatus.CANCELLED }
        _queue.value = currentList
        updateOverallMetrics()
    }

    fun clearAllQueue() {
        if (isRunning) {
            cancelQueue()
        }
        val queueDir = getQueueDir()
        try {
            queueDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
        _queue.value = emptyList()
        _queueOverallProgress.value = QueueOverallProgress()
    }

    private fun getQueueDir(): File {
        val dir = File(context.cacheDir, "csv_queue")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Enqueues multiple CSV files with their specific parsing format.
     * Caches files to avoid Android ContentProvider URI stream expiration.
     */
    suspend fun enqueueFiles(
        files: List<Pair<Uri, String>>,
        format: CsvFormatConfig,
        autoStart: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val newItems = mutableListOf<CsvQueueItem>()
        val queueDir = getQueueDir()

        for ((uri, name) in files) {
            val itemId = UUID.randomUUID().toString()
            val cacheFile = File(queueDir, "queue_$itemId.csv")
            var fileSize = 0L

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                fileSize = cacheFile.length()
            } catch (e: Exception) {
                Log.e(TAG, "Error caching file for queue: $name", e)
            }

            val recordsCount = if (cacheFile.exists()) countTotalRecordsInFile(cacheFile) else 0L

            newItems.add(
                CsvQueueItem(
                    id = itemId,
                    uri = uri,
                    fileName = name,
                    fileSize = fileSize,
                    format = format,
                    status = QueueItemStatus.PENDING,
                    totalRecords = recordsCount,
                    processedRecords = 0L
                )
            )
        }

        _queue.value = _queue.value + newItems
        updateOverallMetrics()

        if (autoStart && !isRunning) {
            startQueue()
        }
    }

    fun startQueue() {
        if (isRunning) return
        queueJob = scope.launch {
            processQueue()
        }
    }

    private suspend fun processQueue() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true
        isPaused.set(false)
        isCancelled.set(false)
        skipCurrentRequested.set(false)

        val db = dbHelper.writableDatabase
        dbHelper.optimizeForBulkInsert(db)

        val queueDir = getQueueDir()

        try {
            while (true) {
                if (isCancelled.get()) {
                    break
                }

                while (isPaused.get()) {
                    if (isCancelled.get()) break
                    kotlinx.coroutines.delay(200)
                }
                if (isCancelled.get()) break

                // Find next pending item
                val currentQueue = _queue.value
                val nextItem = currentQueue.firstOrNull { it.status == QueueItemStatus.PENDING } ?: break

                // Mark item as IMPORTING
                updateItemStatus(nextItem.id, QueueItemStatus.IMPORTING)
                updateOverallMetrics()

                val cacheFile = File(queueDir, "queue_${nextItem.id}.csv")
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    // Cache file missing or unreadable
                    updateItemStatus(nextItem.id, QueueItemStatus.FAILED, errorMessage = "Cannot read cached file stream")
                    continue
                }

                // Process single file inside queue
                val success = processSingleQueueFile(db, nextItem, cacheFile)
                if (success) {
                    updateItemStatus(nextItem.id, QueueItemStatus.COMPLETED)
                    try {
                        if (cacheFile.exists()) cacheFile.delete()
                    } catch (_: Exception) {}
                } else if (skipCurrentRequested.get()) {
                    skipCurrentRequested.set(false)
                    updateItemStatus(nextItem.id, QueueItemStatus.CANCELLED)
                } else if (!isCancelled.get()) {
                    updateItemStatus(nextItem.id, QueueItemStatus.FAILED)
                }
                updateOverallMetrics()
            }

            dbHelper.clearSession()
            if (isCancelled.get()) {
                _progress.value = ImportProgress(status = ImportStatus.IDLE)
            } else {
                _progress.value = ImportProgress(
                    status = ImportStatus.COMPLETED,
                    sourceName = "QUEUE BATCH COMPLETED",
                    processedRecords = _queueOverallProgress.value.totalProcessedRecords,
                    totalRecords = _queueOverallProgress.value.totalEstimatedRecords
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Queue execution error", e)
        } finally {
            dbHelper.restoreStandardConfig(db)
            isRunning = false
            isPaused.set(false)
            _queueOverallProgress.value = _queueOverallProgress.value.copy(isRunning = false, isPaused = false)
        }
    }

    private fun updateItemStatus(
        id: String,
        status: QueueItemStatus,
        processed: Long? = null,
        total: Long? = null,
        speed: Long? = null,
        errorMessage: String? = null
    ) {
        val list = _queue.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = list[index]
            list[index] = old.copy(
                status = status,
                processedRecords = processed ?: old.processedRecords,
                totalRecords = total ?: old.totalRecords,
                recordsPerSecond = speed ?: old.recordsPerSecond,
                errorMessage = errorMessage ?: old.errorMessage
            )
            _queue.value = list
        }
    }

    private fun updateOverallMetrics() {
        val list = _queue.value
        val totalFiles = list.size
        val completedCount = list.count { it.status == QueueItemStatus.COMPLETED }
        val currentActiveIdx = list.indexOfFirst { it.status == QueueItemStatus.IMPORTING }.let { if (it >= 0) it + 1 else completedCount }

        val totalProcessed = list.sumOf { it.processedRecords }
        val totalEstimated = list.sumOf { it.totalRecords }
        val activeItem = list.firstOrNull { it.status == QueueItemStatus.IMPORTING }

        _queueOverallProgress.value = QueueOverallProgress(
            isRunning = isRunning,
            isPaused = isPaused.get(),
            currentFileIndex = currentActiveIdx,
            totalFiles = totalFiles,
            totalProcessedRecords = totalProcessed,
            totalEstimatedRecords = totalEstimated,
            currentFileName = activeItem?.fileName ?: "",
            overallSpeed = activeItem?.recordsPerSecond ?: 0L,
            etaSeconds = if ((activeItem?.recordsPerSecond ?: 0L) > 0L) {
                (totalEstimated - totalProcessed).coerceAtLeast(0) / activeItem!!.recordsPerSecond
            } else 0L
        )
    }

    private suspend fun processSingleQueueFile(
        db: android.database.sqlite.SQLiteDatabase,
        item: CsvQueueItem,
        cacheFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val startTimestamp = System.currentTimeMillis()
        var lastMetricTime = System.currentTimeMillis()
        var lastMetricRecords = 0L
        var processed = 0L
        val totalEstimate = if (item.totalRecords > 0) item.totalRecords else countTotalRecordsInFile(cacheFile)

        updateItemStatus(item.id, QueueItemStatus.IMPORTING, processed = 0L, total = totalEstimate)

        try {
            // Read first line to detect delimiter and column mapping
            var firstLine: String? = null
            BufferedReader(InputStreamReader(FileInputStream(cacheFile), Charsets.UTF_8), 8192).use { reader ->
                firstLine = reader.readLine()
            }

            val delimiter = if (item.format.delimiter == CsvDelimiter.AUTO) {
                detectDelimiter(firstLine ?: "")
            } else {
                item.format.delimiter.char
            }

            val (indices, isHeader) = resolveColumnIndices(firstLine, delimiter, item.format.columnMapping)
            val shouldSkipHeader = when (item.format.headerMode) {
                HeaderMode.ALWAYS_SKIP -> true
                HeaderMode.NO_HEADER -> false
                HeaderMode.AUTO -> isHeader
            }

            BufferedReader(InputStreamReader(FileInputStream(cacheFile), Charsets.UTF_8), 65536).use { reader ->
                val insertStmt = dbHelper.createInsertStatement(db)

                try {
                    var batchCount = 0
                    db.beginTransactionNonExclusive()

                    try {
                        var line: String? = reader.readLine()
                        if (shouldSkipHeader && line != null) {
                            line = reader.readLine()
                        }

                        while (line != null) {
                            if (isCancelled.get() || skipCurrentRequested.get()) {
                                db.setTransactionSuccessful()
                                db.endTransaction()
                                return@withContext false
                            }

                            while (isPaused.get()) {
                                if (isCancelled.get() || skipCurrentRequested.get()) {
                                    db.setTransactionSuccessful()
                                    db.endTransaction()
                                    return@withContext false
                                }
                                kotlinx.coroutines.delay(200)
                            }

                            val tokens = parseTokens(line, delimiter)
                            if (tokens.isNotEmpty()) {
                                val natCode = tokens.getOrNull(indices.natCodeIdx)?.trim() ?: ""
                                val cardNo = tokens.getOrNull(indices.cardNoIdx)?.trim() ?: ""
                                val fullName = tokens.getOrNull(indices.fullNameIdx)?.trim() ?: ""
                                val birthDate = tokens.getOrNull(indices.birthDateIdx)?.trim() ?: ""
                                val mobile = tokens.getOrNull(indices.mobileIdx)?.trim() ?: ""

                                insertStmt.bindString(1, natCode)
                                insertStmt.bindString(2, cardNo)
                                insertStmt.bindString(3, fullName)
                                insertStmt.bindString(4, birthDate)
                                insertStmt.bindString(5, mobile)
                                insertStmt.executeInsert()
                                insertStmt.clearBindings()

                                processed++
                                batchCount++

                                if (batchCount >= BATCH_SIZE) {
                                    db.setTransactionSuccessful()
                                    db.endTransaction()

                                    val now = System.currentTimeMillis()
                                    val timeDelta = (now - lastMetricTime).coerceAtLeast(1)
                                    val recordsDelta = processed - lastMetricRecords
                                    val currentSpeed = (recordsDelta * 1000L) / timeDelta
                                    val currentElapsed = (now - startTimestamp) / 1000

                                    val remaining = (totalEstimate - processed).coerceAtLeast(0)
                                    val eta = if (currentSpeed > 0 && remaining > 0) remaining / currentSpeed else 0

                                    // Update single progress (mirrors to main screen radar)
                                    _progress.value = ImportProgress(
                                        status = ImportStatus.RUNNING,
                                        sourceName = item.fileName,
                                        processedRecords = processed,
                                        totalRecords = totalEstimate,
                                        recordsPerSecond = currentSpeed,
                                        elapsedSeconds = currentElapsed,
                                        etaSeconds = eta
                                    )

                                    updateItemStatus(item.id, QueueItemStatus.IMPORTING, processed = processed, speed = currentSpeed)
                                    updateOverallMetrics()

                                    lastMetricTime = now
                                    lastMetricRecords = processed
                                    batchCount = 0
                                    db.beginTransactionNonExclusive()
                                }
                            }

                            line = reader.readLine()
                        }

                        if (batchCount > 0) {
                            db.setTransactionSuccessful()
                        }
                    } finally {
                        if (db.inTransaction()) {
                            db.endTransaction()
                        }
                    }
                } finally {
                    try {
                        insertStmt.close()
                    } catch (_: Exception) {}
                }
            }

            updateItemStatus(item.id, QueueItemStatus.COMPLETED, processed = processed, total = processed)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queue file: ${item.fileName}", e)
            updateItemStatus(item.id, QueueItemStatus.FAILED, errorMessage = e.localizedMessage)
            return@withContext false
        }
    }

    /**
     * Rapidly counts lines in a cached CSV file using high-throughput byte buffer.
     * Counts all newlines in tens of milliseconds without line-by-line string allocations.
     */
    private suspend fun countTotalRecordsInFile(file: File): Long = withContext(Dispatchers.IO) {
        try {
            var count = 0L
            var hasHeader = false
            file.inputStream().use { stream ->
                val buffer = ByteArray(128 * 1024)
                var read: Int
                var firstChunk = true
                while (stream.read(buffer).also { read = it } != -1) {
                    if (firstChunk && read > 0) {
                        firstChunk = false
                        val preview = String(buffer, 0, minOf(read, 1024), Charsets.UTF_8).uppercase()
                        if (preview.contains("NATIONAL") || preview.contains("CARD") || preview.contains("MOBILE") || preview.contains("MELI")) {
                            hasHeader = true
                        }
                    }
                    for (i in 0 until read) {
                        if (buffer[i] == '\n'.code.toByte()) {
                            count++
                        }
                    }
                }
            }
            val total = if (hasHeader && count > 0) count - 1 else count
            return@withContext if (total > 0) total else 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error counting CSV lines", e)
            return@withContext 0L
        }
    }

    /**
     * Resumes an existing paused or interrupted session (single-file or queue).
     */
    suspend fun resumeImport(): Boolean = withContext(Dispatchers.IO) {
        if (_queue.value.any { it.status == QueueItemStatus.PENDING || it.status == QueueItemStatus.PAUSED }) {
            resumeQueue()
            return@withContext true
        }
        val session = dbHelper.getActiveSession() ?: return@withContext false
        if (!session.sourceUri.isNullOrEmpty()) {
            val uri = Uri.parse(session.sourceUri)
            importCsvFile(
                uri = uri,
                fileName = session.sourceName,
                resumeFrom = session.processedRecords,
                priorElapsedMs = session.elapsedMs,
                totalEstimate = session.totalRecords
            )
            return@withContext true
        }
        return@withContext false
    }

    /**
     * Direct single-file import with format support (backward compatibility).
     */
    suspend fun importCsvFile(
        uri: Uri,
        fileName: String,
        resumeFrom: Long = 0L,
        priorElapsedMs: Long = 0L,
        totalEstimate: Long = 0L,
        format: CsvFormatConfig = CsvFormatConfig()
    ) = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true
        isPaused.set(false)
        isCancelled.set(false)

        val cacheFile = File(context.cacheDir, "incoming_import.csv")
        val db = dbHelper.writableDatabase
        dbHelper.optimizeForBulkInsert(db)

        val startTimestamp = System.currentTimeMillis() - priorElapsedMs
        var lastMetricTime = System.currentTimeMillis()
        var lastMetricRecords = resumeFrom
        var processed = resumeFrom
        var lineIndex = 0L

        try {
            if (resumeFrom == 0L || !cacheFile.exists()) {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("Cannot open stream for selected file.")
            }

            var actualTotal = totalEstimate
            if (actualTotal <= 0L) {
                _progress.value = ImportProgress(
                    status = ImportStatus.RUNNING,
                    sourceName = fileName,
                    processedRecords = processed,
                    totalRecords = 0L,
                    elapsedSeconds = priorElapsedMs / 1000,
                    hasRecoverableSession = false
                )
                actualTotal = countTotalRecordsInFile(cacheFile)
            }

            _progress.value = ImportProgress(
                status = ImportStatus.RUNNING,
                sourceName = fileName,
                processedRecords = processed,
                totalRecords = actualTotal,
                elapsedSeconds = priorElapsedMs / 1000,
                hasRecoverableSession = false
            )

            dbHelper.saveSession(
                sourceName = fileName,
                sourceUri = uri.toString(),
                sourceType = "FILE",
                processedRecords = processed,
                byteOffset = 0,
                totalRecords = actualTotal,
                status = ImportStatus.RUNNING.name,
                elapsedMs = priorElapsedMs
            )

            // Detect format
            var firstLine: String? = null
            BufferedReader(InputStreamReader(FileInputStream(cacheFile), Charsets.UTF_8), 8192).use { reader ->
                firstLine = reader.readLine()
            }
            val delimiter = if (format.delimiter == CsvDelimiter.AUTO) detectDelimiter(firstLine ?: "") else format.delimiter.char
            val (indices, isHeader) = resolveColumnIndices(firstLine, delimiter, format.columnMapping)
            val shouldSkipHeader = when (format.headerMode) {
                HeaderMode.ALWAYS_SKIP -> true
                HeaderMode.NO_HEADER -> false
                HeaderMode.AUTO -> isHeader
            }

            BufferedReader(InputStreamReader(FileInputStream(cacheFile), Charsets.UTF_8), 65536).use { reader ->
                val insertStmt = dbHelper.createInsertStatement(db)

                try {
                    var batchCount = 0
                    db.beginTransactionNonExclusive()

                    try {
                        var line: String? = reader.readLine()
                        if (resumeFrom == 0L && shouldSkipHeader && line != null) {
                            line = reader.readLine()
                        }

                        while (line != null && lineIndex < resumeFrom) {
                            lineIndex++
                            line = reader.readLine()
                        }

                        while (line != null) {
                            if (isCancelled.get()) {
                                db.setTransactionSuccessful()
                                db.endTransaction()
                                dbHelper.clearSession()
                                _progress.value = ImportProgress(status = ImportStatus.IDLE)
                                return@withContext
                            }

                            if (isPaused.get()) {
                                db.setTransactionSuccessful()
                                db.endTransaction()
                                val currentElapsed = System.currentTimeMillis() - startTimestamp
                                dbHelper.saveSession(
                                    sourceName = fileName,
                                    sourceUri = uri.toString(),
                                    sourceType = "FILE",
                                    processedRecords = processed,
                                    byteOffset = 0,
                                    totalRecords = actualTotal,
                                    status = ImportStatus.PAUSED.name,
                                    elapsedMs = currentElapsed
                                )
                                _progress.value = _progress.value.copy(
                                    status = ImportStatus.PAUSED,
                                    processedRecords = processed,
                                    totalRecords = actualTotal,
                                    elapsedSeconds = currentElapsed / 1000,
                                    hasRecoverableSession = true
                                )
                                return@withContext
                            }

                            val tokens = parseTokens(line, delimiter)
                            if (tokens.isNotEmpty()) {
                                val natCode = tokens.getOrNull(indices.natCodeIdx)?.trim() ?: ""
                                val cardNo = tokens.getOrNull(indices.cardNoIdx)?.trim() ?: ""
                                val fullName = tokens.getOrNull(indices.fullNameIdx)?.trim() ?: ""
                                val birthDate = tokens.getOrNull(indices.birthDateIdx)?.trim() ?: ""
                                val mobile = tokens.getOrNull(indices.mobileIdx)?.trim() ?: ""

                                insertStmt.bindString(1, natCode)
                                insertStmt.bindString(2, cardNo)
                                insertStmt.bindString(3, fullName)
                                insertStmt.bindString(4, birthDate)
                                insertStmt.bindString(5, mobile)
                                insertStmt.executeInsert()
                                insertStmt.clearBindings()

                                processed++
                                batchCount++

                                if (batchCount >= BATCH_SIZE) {
                                    db.setTransactionSuccessful()
                                    db.endTransaction()

                                    val now = System.currentTimeMillis()
                                    val timeDelta = (now - lastMetricTime).coerceAtLeast(1)
                                    val recordsDelta = processed - lastMetricRecords
                                    val currentSpeed = (recordsDelta * 1000L) / timeDelta
                                    val currentElapsed = (now - startTimestamp) / 1000

                                    val remaining = (actualTotal - processed).coerceAtLeast(0)
                                    val eta = if (currentSpeed > 0 && remaining > 0) remaining / currentSpeed else 0

                                    _progress.value = ImportProgress(
                                        status = ImportStatus.RUNNING,
                                        sourceName = fileName,
                                        processedRecords = processed,
                                        totalRecords = actualTotal,
                                        recordsPerSecond = currentSpeed,
                                        elapsedSeconds = currentElapsed,
                                        etaSeconds = eta
                                    )

                                    dbHelper.saveSession(
                                        sourceName = fileName,
                                        sourceUri = uri.toString(),
                                        sourceType = "FILE",
                                        processedRecords = processed,
                                        byteOffset = 0,
                                        totalRecords = actualTotal,
                                        status = ImportStatus.RUNNING.name,
                                        elapsedMs = currentElapsed * 1000
                                    )

                                    lastMetricTime = now
                                    lastMetricRecords = processed
                                    batchCount = 0
                                    db.beginTransactionNonExclusive()
                                }
                            }

                            line = reader.readLine()
                        }

                        if (batchCount > 0) {
                            db.setTransactionSuccessful()
                        }
                    } finally {
                        if (db.inTransaction()) {
                            db.endTransaction()
                        }
                    }
                } finally {
                    try {
                        insertStmt.close()
                    } catch (_: Exception) {}
                }
            }

            dbHelper.clearSession()
            val totalTimeSec = (System.currentTimeMillis() - startTimestamp) / 1000
            _progress.value = ImportProgress(
                status = ImportStatus.COMPLETED,
                sourceName = fileName,
                processedRecords = processed,
                totalRecords = processed,
                elapsedSeconds = totalTimeSec,
                recordsPerSecond = if (totalTimeSec > 0) processed / totalTimeSec else processed,
                etaSeconds = 0,
                hasRecoverableSession = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "CSV Import error", e)
            _progress.value = ImportProgress(
                status = ImportStatus.ERROR,
                sourceName = fileName,
                processedRecords = processed,
                errorMessage = e.localizedMessage ?: "Unknown I/O error during import",
                hasRecoverableSession = true
            )
        } finally {
            try {
                if (cacheFile.exists() && _progress.value.status == ImportStatus.COMPLETED) {
                    cacheFile.delete()
                }
            } catch (_: Exception) {}
            dbHelper.restoreStandardConfig(db)
            isRunning = false
        }
    }

    private data class ColumnIndices(
        val natCodeIdx: Int = 0,
        val cardNoIdx: Int = 1,
        val fullNameIdx: Int = 2,
        val birthDateIdx: Int = 3,
        val mobileIdx: Int = 4
    )

    private fun detectDelimiter(sample: String): Char {
        val commas = sample.count { it == ',' }
        val semicolons = sample.count { it == ';' }
        val tabs = sample.count { it == '\t' }
        val pipes = sample.count { it == '|' }

        val maxCount = maxOf(commas, semicolons, tabs, pipes)
        if (maxCount == 0) return ','
        return when (maxCount) {
            pipes -> '|'
            tabs -> '\t'
            semicolons -> ';'
            else -> ','
        }
    }

    private fun resolveColumnIndices(
        firstLine: String?,
        delimiter: Char,
        mapping: ColumnMapping
    ): Pair<ColumnIndices, Boolean> {
        if (firstLine == null) return Pair(ColumnIndices(), false)
        val tokens = parseTokens(firstLine, delimiter)
        val isHeader = isLikelyHeaderRow(tokens)

        return when (mapping) {
            ColumnMapping.STANDARD -> Pair(ColumnIndices(0, 1, 2, 3, 4), isHeader)
            ColumnMapping.MOBILE_FIRST -> Pair(ColumnIndices(natCodeIdx = 1, cardNoIdx = 3, fullNameIdx = 2, birthDateIdx = 4, mobileIdx = 0), isHeader)
            ColumnMapping.CARD_FIRST -> Pair(ColumnIndices(natCodeIdx = 1, cardNoIdx = 0, fullNameIdx = 2, birthDateIdx = 4, mobileIdx = 3), isHeader)
            ColumnMapping.NAME_FIRST -> Pair(ColumnIndices(natCodeIdx = 1, cardNoIdx = 3, fullNameIdx = 0, birthDateIdx = 4, mobileIdx = 2), isHeader)
            ColumnMapping.AUTO_HEADER -> {
                if (!isHeader) {
                    Pair(ColumnIndices(0, 1, 2, 3, 4), false)
                } else {
                    var natIdx = -1
                    var cardIdx = -1
                    var nameIdx = -1
                    var birthIdx = -1
                    var mobIdx = -1

                    tokens.forEachIndexed { idx, rawToken ->
                        val t = rawToken.lowercase().replace("_", "").replace("-", "").replace(" ", "")
                        if (t.contains("national") || t.contains("melli") || t.contains("meli") || t.contains("codemeli") || t.contains("ssn") || t == "code") {
                            if (natIdx == -1) natIdx = idx
                        } else if (t.contains("card") || t.contains("kart") || t.contains("pan") || t.contains("shomarekart") || t.contains("account")) {
                            if (cardIdx == -1) cardIdx = idx
                        } else if (t.contains("name") || t.contains("fname") || t.contains("family") || t.contains("lname") || t.contains("fullname") || t.contains("cust")) {
                            if (nameIdx == -1) nameIdx = idx
                        } else if (t.contains("birth") || t.contains("bdate") || t.contains("tavalod") || t.contains("tarikh") || t.contains("date")) {
                            if (birthIdx == -1) birthIdx = idx
                        } else if (t.contains("mobile") || t.contains("phone") || t.contains("tel") || t.contains("cell") || t.contains("hamrah") || t.contains("shomare")) {
                            if (mobIdx == -1) mobIdx = idx
                        }
                    }

                    Pair(
                        ColumnIndices(
                            natCodeIdx = if (natIdx != -1) natIdx else 0,
                            cardNoIdx = if (cardIdx != -1) cardIdx else 1,
                            fullNameIdx = if (nameIdx != -1) nameIdx else 2,
                            birthDateIdx = if (birthIdx != -1) birthIdx else 3,
                            mobileIdx = if (mobIdx != -1) mobIdx else 4
                        ),
                        true
                    )
                }
            }
        }
    }

    private fun isLikelyHeaderRow(tokens: List<String>): Boolean {
        var textCount = 0
        tokens.forEach { t ->
            val trimmed = t.trim()
            val letters = trimmed.count { it.isLetter() }
            val digits = trimmed.count { it.isDigit() }
            val upper = trimmed.uppercase()
            if (letters > digits || upper.contains("NAME") || upper.contains("CODE") || upper.contains("CARD") ||
                upper.contains("MOBILE") || upper.contains("PHONE") || upper.contains("DATE") || upper.contains("MELI")
            ) {
                textCount++
            }
        }
        return textCount >= 2 || (tokens.isNotEmpty() && tokens[0].any { it.isLetter() } && !tokens[0].all { it.isDigit() })
    }

    private fun parseTokens(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}

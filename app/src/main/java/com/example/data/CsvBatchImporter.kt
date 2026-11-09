package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
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

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    @Volatile private var isRunning = false

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
        if (isRunning) {
            isPaused.set(true)
        }
    }

    fun cancel() {
        isCancelled.set(true)
        isPaused.set(false)
    }

    /**
     * Resumes an existing paused or interrupted session.
     */
    suspend fun resumeImport(): Boolean = withContext(Dispatchers.IO) {
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
     * Rapidly counts lines in a CSV file using high-throughput byte buffer.
     * Counts all newlines in tens of milliseconds without line-by-line string allocations.
     */
    suspend fun countTotalRecords(uri: Uri): Long = withContext(Dispatchers.IO) {
        try {
            var count = 0L
            var hasHeader = false
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(128 * 1024)
                var read: Int
                var firstChunk = true
                while (stream.read(buffer).also { read = it } != -1) {
                    if (firstChunk && read > 0) {
                        firstChunk = false
                        val preview = String(buffer, 0, minOf(read, 1024), Charsets.UTF_8).uppercase()
                        if (preview.contains("NATIONAL") || preview.contains("CARD") || preview.contains("MOBILE")) {
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
     * Imports a real CSV file from Uri.
     */
    suspend fun importCsvFile(
        uri: Uri,
        fileName: String,
        resumeFrom: Long = 0L,
        priorElapsedMs: Long = 0L,
        totalEstimate: Long = 0L
    ) = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true
        isPaused.set(false)
        isCancelled.set(false)

        val db = dbHelper.writableDatabase
        dbHelper.optimizeForBulkInsert(db)

        val startTimestamp = System.currentTimeMillis() - priorElapsedMs
        var lastMetricTime = System.currentTimeMillis()
        var lastMetricRecords = resumeFrom
        var processed = resumeFrom
        var lineIndex = 0L

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
            actualTotal = countTotalRecords(uri)
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

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 65536).use { reader ->
                val insertStmt = dbHelper.createInsertStatement(db)

                var batchCount = 0
                db.beginTransactionNonExclusive()

                try {
                    var line: String? = reader.readLine()
                    // Check header line if starting from 0
                    if (resumeFrom == 0L && line != null) {
                        val upper = line.uppercase()
                        if (upper.contains("NATIONAL") || upper.contains("CARD") || upper.contains("MOBILE")) {
                            // Header skipped
                            line = reader.readLine()
                        }
                    }

                    // Fast forward if resuming
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

                        val tokens = parseCsvLine(line)
                        if (tokens.isNotEmpty()) {
                            val natCode = tokens.getOrNull(0)?.trim() ?: ""
                            val cardNo = tokens.getOrNull(1)?.trim() ?: ""
                            val fullName = tokens.getOrNull(2)?.trim() ?: ""
                            val birthDate = tokens.getOrNull(3)?.trim() ?: ""
                            val mobile = tokens.getOrNull(4)?.trim() ?: ""

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
                                    elapsedMs = now - startTimestamp
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
            dbHelper.restoreStandardConfig(db)
            isRunning = false
        }
    }



    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) {
                        sb.append(ch)
                    } else {
                        tokens.add(sb.toString().trim())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}

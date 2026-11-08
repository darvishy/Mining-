package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

class VcfExporter(
    private val context: Context,
    private val dbHelper: DatabaseHelper
) {
    companion object {
        private const val TAG = "VcfExporter"
        const val BATCH_SIZE = 5000
    }

    private val _progress = MutableStateFlow(VcfExportProgress())
    val progress: StateFlow<VcfExportProgress> = _progress.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    @Volatile private var isRunning = false

    init {
        checkForRecoverableSession()
    }

    fun checkForRecoverableSession() {
        try {
            val session = dbHelper.getActiveVcfSession()
            if (session != null && (session.status == ImportStatus.RUNNING.name || session.status == ImportStatus.PAUSED.name)) {
                val file = File(session.filePath)
                val fileSize = if (file.exists()) file.length() else 0L
                _progress.value = VcfExportProgress(
                    status = ImportStatus.PAUSED,
                    prefix = session.prefix,
                    filePath = session.filePath,
                    processedRecords = session.processedRecords,
                    totalRecords = session.totalRecords,
                    elapsedSeconds = session.elapsedMs / 1000,
                    fileSizeBytes = fileSize,
                    hasRecoverableSession = true
                )
            } else {
                _progress.value = _progress.value.copy(hasRecoverableSession = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VCF session", e)
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

    suspend fun resumeExport(): Boolean = withContext(Dispatchers.IO) {
        val session = dbHelper.getActiveVcfSession() ?: return@withContext false
        val file = File(session.filePath)
        if (!file.exists()) {
            dbHelper.clearVcfSession()
            _progress.value = VcfExportProgress(status = ImportStatus.IDLE)
            return@withContext false
        }
        exportVcf(
            prefix = session.prefix,
            resumeFromId = session.lastProcessedId,
            priorProcessed = session.processedRecords,
            priorElapsedMs = session.elapsedMs,
            targetTotal = session.totalRecords,
            existingFile = file
        )
        return@withContext true
    }

    suspend fun startExport(prefix: String) = withContext(Dispatchers.IO) {
        val cleanPrefix = prefix.trim()
        if (cleanPrefix.isEmpty()) return@withContext

        val totalEligible = dbHelper.countVcfEligibleRecords(cleanPrefix)
        if (totalEligible == 0L) {
            _progress.value = VcfExportProgress(
                status = ImportStatus.ERROR,
                prefix = cleanPrefix,
                errorMessage = "No records found matching prefix '$cleanPrefix' with valid Name and Mobile Number."
            )
            return@withContext
        }

        val exportDir = File(context.getExternalFilesDir(null), "vcf").apply { mkdirs() }
        val vcfFile = File(exportDir, "contacts_prefix_${cleanPrefix}.vcf")
        // If starting fresh, delete existing file
        if (vcfFile.exists()) {
            vcfFile.delete()
        }

        exportVcf(
            prefix = cleanPrefix,
            resumeFromId = 0L,
            priorProcessed = 0L,
            priorElapsedMs = 0L,
            targetTotal = totalEligible,
            existingFile = vcfFile
        )
    }

    private suspend fun exportVcf(
        prefix: String,
        resumeFromId: Long,
        priorProcessed: Long,
        priorElapsedMs: Long,
        targetTotal: Long,
        existingFile: File
    ) = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true
        isPaused.set(false)
        isCancelled.set(false)

        val startTimestamp = System.currentTimeMillis() - priorElapsedMs
        var lastMetricTime = System.currentTimeMillis()
        var lastMetricRecords = priorProcessed
        var processed = priorProcessed
        var lastId = resumeFromId

        _progress.value = VcfExportProgress(
            status = ImportStatus.RUNNING,
            prefix = prefix,
            filePath = existingFile.absolutePath,
            processedRecords = processed,
            totalRecords = targetTotal,
            elapsedSeconds = priorElapsedMs / 1000,
            fileSizeBytes = existingFile.length(),
            hasRecoverableSession = false
        )

        dbHelper.saveVcfSession(
            prefix = prefix,
            filePath = existingFile.absolutePath,
            lastProcessedId = lastId,
            processedRecords = processed,
            totalRecords = targetTotal,
            status = ImportStatus.RUNNING.name,
            elapsedMs = priorElapsedMs
        )

        try {
            // Append mode = true allows resuming without destroying previously written cards
            FileOutputStream(existingFile, true).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8), 65536).use { writer ->
                    var hasMore = true

                    while (hasMore) {
                        if (isCancelled.get()) {
                            dbHelper.clearVcfSession()
                            _progress.value = VcfExportProgress(status = ImportStatus.IDLE)
                            return@withContext
                        }

                        if (isPaused.get()) {
                            writer.flush()
                            val currentElapsed = System.currentTimeMillis() - startTimestamp
                            dbHelper.saveVcfSession(
                                prefix = prefix,
                                filePath = existingFile.absolutePath,
                                lastProcessedId = lastId,
                                processedRecords = processed,
                                totalRecords = targetTotal,
                                status = ImportStatus.PAUSED.name,
                                elapsedMs = currentElapsed
                            )
                            _progress.value = _progress.value.copy(
                                status = ImportStatus.PAUSED,
                                processedRecords = processed,
                                elapsedSeconds = currentElapsed / 1000,
                                fileSizeBytes = existingFile.length(),
                                hasRecoverableSession = true
                            )
                            return@withContext
                        }

                        val batch = dbHelper.queryVcfRecordsBatch(prefix, lastId, BATCH_SIZE)
                        if (batch.isEmpty()) {
                            hasMore = false
                            break
                        }

                        for (record in batch) {
                            writeVCardRecord(writer, record)
                            processed++
                            lastId = record.id
                        }

                        writer.flush()

                        val now = System.currentTimeMillis()
                        val timeDelta = (now - lastMetricTime).coerceAtLeast(1)
                        val recordsDelta = processed - lastMetricRecords
                        val currentSpeed = (recordsDelta * 1000L) / timeDelta
                        val currentElapsed = (now - startTimestamp) / 1000
                        val remaining = (targetTotal - processed).coerceAtLeast(0)
                        val eta = if (currentSpeed > 0 && remaining > 0) remaining / currentSpeed else 0

                        _progress.value = VcfExportProgress(
                            status = ImportStatus.RUNNING,
                            prefix = prefix,
                            filePath = existingFile.absolutePath,
                            processedRecords = processed,
                            totalRecords = targetTotal,
                            recordsPerSecond = currentSpeed,
                            elapsedSeconds = currentElapsed,
                            etaSeconds = eta,
                            fileSizeBytes = existingFile.length(),
                            hasRecoverableSession = false
                        )

                        dbHelper.saveVcfSession(
                            prefix = prefix,
                            filePath = existingFile.absolutePath,
                            lastProcessedId = lastId,
                            processedRecords = processed,
                            totalRecords = targetTotal,
                            status = ImportStatus.RUNNING.name,
                            elapsedMs = now - startTimestamp
                        )

                        lastMetricTime = now
                        lastMetricRecords = processed

                        if (batch.size < BATCH_SIZE) {
                            hasMore = false
                        }
                    }

                    writer.flush()
                }
            }

            dbHelper.clearVcfSession()
            val totalTimeSec = (System.currentTimeMillis() - startTimestamp) / 1000

            _progress.value = VcfExportProgress(
                status = ImportStatus.COMPLETED,
                prefix = prefix,
                filePath = existingFile.absolutePath,
                processedRecords = processed,
                totalRecords = processed,
                elapsedSeconds = totalTimeSec,
                recordsPerSecond = if (totalTimeSec > 0) processed / totalTimeSec else processed,
                etaSeconds = 0,
                fileSizeBytes = existingFile.length(),
                hasRecoverableSession = false
            )

        } catch (e: Exception) {
            Log.e(TAG, "VCF Export Error", e)
            _progress.value = VcfExportProgress(
                status = ImportStatus.ERROR,
                prefix = prefix,
                filePath = existingFile.absolutePath,
                processedRecords = processed,
                totalRecords = targetTotal,
                errorMessage = e.localizedMessage ?: "I/O failure while writing VCF file",
                hasRecoverableSession = true
            )
        } finally {
            isRunning = false
        }
    }

    /**
     * Writes standard vCard 3.0 entry compatible with all Android/iOS contacts apps.
     */
    private fun writeVCardRecord(writer: BufferedWriter, record: PersonRecord) {
        val nameParts = record.fullName.trim().split("\\s+".toRegex())
        val firstName = nameParts.firstOrNull() ?: ""
        val lastName = if (nameParts.size > 1) nameParts.subList(1, nameParts.size).joinToString(" ") else ""

        writer.write("BEGIN:VCARD\r\n")
        writer.write("VERSION:3.0\r\n")
        writer.write("FN:${escapeVcf(record.fullName.trim())}\r\n")
        writer.write("N:${escapeVcf(lastName)};${escapeVcf(firstName)};;;\r\n")
        writer.write("TEL;TYPE=CELL:${escapeVcf(record.mobile.trim())}\r\n")

        val noteBuilder = StringBuilder()
        if (record.nationalCode.isNotEmpty()) noteBuilder.append("NAT:${record.nationalCode} ")
        if (record.cardNo.isNotEmpty()) noteBuilder.append("CARD:${record.cardNo} ")
        if (record.birthDate.isNotEmpty()) noteBuilder.append("DOB:${record.birthDate}")
        val note = noteBuilder.toString().trim()
        if (note.isNotEmpty()) {
            writer.write("NOTE:${escapeVcf(note)}\r\n")
        }

        writer.write("END:VCARD\r\n")
    }

    private fun escapeVcf(value: String): String {
        return value.replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    fun createOpenInContactsIntent(file: File): Intent {
        val uri = getFileUri(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/x-vcard")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createShareVcfIntent(file: File): Intent {
        val uri = getFileUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Exported Contacts (${file.name})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

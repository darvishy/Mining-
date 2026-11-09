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
        const val DEFAULT_RECORDS_PER_FILE = 2000
        const val QUERY_BATCH_SIZE = 1000
    }

    private val _progress = MutableStateFlow(VcfExportProgress())
    val progress: StateFlow<VcfExportProgress> = _progress.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    @Volatile private var isRunning = false

    fun pause() {
        if (isRunning) {
            isPaused.set(true)
        }
    }

    fun cancel() {
        isCancelled.set(true)
        isPaused.set(false)
    }

    suspend fun startExport(prefix: String, recordsPerFile: Int = DEFAULT_RECORDS_PER_FILE) = withContext(Dispatchers.IO) {
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

        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val exportDir = File(baseDir, "vcf").apply { mkdirs() }

        // Clean any previous export files for this prefix
        exportDir.listFiles { file ->
            file.name.startsWith("contacts_${cleanPrefix}_part") && file.name.endsWith(".vcf")
        }?.forEach { it.delete() }

        exportVcfSplit(
            prefix = cleanPrefix,
            targetTotal = totalEligible,
            recordsPerFile = recordsPerFile.coerceIn(500, 10000),
            exportDir = exportDir
        )
    }

    private suspend fun exportVcfSplit(
        prefix: String,
        targetTotal: Long,
        recordsPerFile: Int,
        exportDir: File
    ) = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true
        isPaused.set(false)
        isCancelled.set(false)

        val totalParts = ((targetTotal + recordsPerFile - 1) / recordsPerFile).toInt().coerceAtLeast(1)
        val startTimestamp = System.currentTimeMillis()
        var lastMetricTime = System.currentTimeMillis()
        var lastMetricRecords = 0L
        var processed = 0L
        var lastId = 0L

        val exportedFiles = mutableListOf<VcfFileInfo>()

        _progress.value = VcfExportProgress(
            status = ImportStatus.RUNNING,
            prefix = prefix,
            processedRecords = 0L,
            totalRecords = targetTotal,
            currentPart = 1,
            totalParts = totalParts,
            exportedFiles = emptyList()
        )

        try {
            var currentPartNumber = 1

            while (processed < targetTotal && !isCancelled.get()) {
                val partFile = File(exportDir, "contacts_${prefix}_part${currentPartNumber}.vcf")
                if (partFile.exists()) {
                    partFile.delete()
                }

                var partRecordCount = 0L

                FileOutputStream(partFile, false).use { fos ->
                    BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8), 32768).use { writer ->
                        while (partRecordCount < recordsPerFile && processed < targetTotal) {
                            if (isCancelled.get()) {
                                _progress.value = VcfExportProgress(status = ImportStatus.IDLE)
                                return@withContext
                            }

                            val needed = (recordsPerFile - partRecordCount).coerceAtMost(QUERY_BATCH_SIZE.toLong()).toInt()
                            val batch = dbHelper.queryVcfRecordsBatch(prefix, lastId, needed)
                            if (batch.isEmpty()) {
                                break
                            }

                            for (record in batch) {
                                writeVCardRecord(writer, record)
                                processed++
                                partRecordCount++
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
                                filePath = partFile.absolutePath,
                                processedRecords = processed,
                                totalRecords = targetTotal,
                                recordsPerSecond = currentSpeed,
                                elapsedSeconds = currentElapsed,
                                etaSeconds = eta,
                                fileSizeBytes = exportedFiles.sumOf { it.sizeBytes } + partFile.length(),
                                currentPart = currentPartNumber,
                                totalParts = totalParts,
                                exportedFiles = exportedFiles.toList()
                            )

                            lastMetricTime = now
                            lastMetricRecords = processed

                            if (batch.size < needed) {
                                break
                            }
                        }

                        // Explicitly flush and sync file descriptor before closing
                        writer.flush()
                    }
                    fos.fd.sync()
                }

                if (partFile.exists() && partFile.length() > 0) {
                    exportedFiles.add(
                        VcfFileInfo(
                            file = partFile,
                            partNumber = currentPartNumber,
                            recordsCount = partRecordCount,
                            sizeBytes = partFile.length()
                        )
                    )
                }

                currentPartNumber++
            }

            val totalTimeSec = (System.currentTimeMillis() - startTimestamp) / 1000
            val totalBytes = exportedFiles.sumOf { it.sizeBytes }

            _progress.value = VcfExportProgress(
                status = ImportStatus.COMPLETED,
                prefix = prefix,
                filePath = exportedFiles.firstOrNull()?.file?.absolutePath ?: "",
                processedRecords = processed,
                totalRecords = processed,
                elapsedSeconds = totalTimeSec,
                recordsPerSecond = if (totalTimeSec > 0) processed / totalTimeSec else processed,
                etaSeconds = 0,
                fileSizeBytes = totalBytes,
                currentPart = exportedFiles.size,
                totalParts = exportedFiles.size,
                exportedFiles = exportedFiles.toList()
            )

        } catch (e: Exception) {
            Log.e(TAG, "VCF Export Error", e)
            _progress.value = VcfExportProgress(
                status = ImportStatus.ERROR,
                prefix = prefix,
                processedRecords = processed,
                totalRecords = targetTotal,
                errorMessage = e.localizedMessage ?: "I/O failure while writing split VCF files",
                exportedFiles = exportedFiles.toList(),
                fileSizeBytes = exportedFiles.sumOf { it.sizeBytes }
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

    fun createShareAllVcfIntent(files: List<File>): Intent {
        val uris = ArrayList<Uri>()
        for (file in files) {
            uris.add(getFileUri(file))
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/x-vcard"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Exported Contacts (${files.size} parts)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

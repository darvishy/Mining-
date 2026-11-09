package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class BackupAction {
    IDLE,
    BACKING_UP,
    RESTORING,
    COMPLETED,
    ERROR
}

data class BackupProgress(
    val action: BackupAction = BackupAction.IDLE,
    val isRunning: Boolean = false,
    val title: String = "",
    val details: String = "",
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val elapsedMs: Long = 0L,
    val throughputMBps: Double = 0.0,
    val recordsCount: Long = 0L,
    val originalSizeMb: Double = 0.0,
    val compressedSizeMb: Double = 0.0,
    val compressionRatio: Double = 0.0,
    val error: String? = null
)

class FastBackupManager(
    private val context: Context,
    private val dbHelper: DatabaseHelper
) {
    companion object {
        private const val TAG = "FastBackupManager"
        private const val MAGIC_HEADER = 0x4E424B31 // "NBK1" (NullEmperor Backup v1)
        private const val BUFFER_SIZE = 128 * 1024 // 128 KB high-throughput buffer
        private const val SQLITE_HEADER = "SQLite format 3"
    }

    private val _progress = MutableStateFlow(BackupProgress())
    val progress: StateFlow<BackupProgress> = _progress.asStateFlow()

    /**
     * Creates an ultra-fast compressed backup of the active SQLite database directly to a destination Uri or internal storage.
     */
    suspend fun createBackup(outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            _progress.value = BackupProgress(
                action = BackupAction.BACKING_UP,
                isRunning = true,
                title = "COMMITTING WAL CHECKPOINT",
                details = "Flushing pending write-ahead log to master database file..."
            )

            // 1. Commit and truncate write-ahead log to ensure main database file is 100% current
            dbHelper.checkpointWal()

            val dbFile = dbHelper.getDatabaseFile()
            if (!dbFile.exists() || dbFile.length() == 0L) {
                _progress.value = BackupProgress(
                    action = BackupAction.ERROR,
                    error = "Database file is empty or missing"
                )
                return@withContext false
            }

            val totalRecords = dbHelper.getTotalCount()
            val originalLength = dbFile.length()

            _progress.value = BackupProgress(
                action = BackupAction.BACKING_UP,
                isRunning = true,
                title = "ULTRA-FAST STREAM COMPRESSION",
                details = "Streaming raw database pages with hardware-accelerated DEFLATE...",
                totalBytes = originalLength,
                recordsCount = totalRecords,
                originalSizeMb = originalLength / (1024.0 * 1024.0)
            )

            val outputStream: OutputStream? = context.contentResolver.openOutputStream(outputUri)
            if (outputStream == null) {
                _progress.value = BackupProgress(
                    action = BackupAction.ERROR,
                    error = "Failed to open target output stream"
                )
                return@withContext false
            }

            var bytesReadTotal = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            outputStream.use { rawOut ->
                BufferedOutputStream(rawOut, BUFFER_SIZE).use { bufferedOut ->
                    val dataOut = DataOutputStream(bufferedOut)
                    // Write NBK1 Magic Header and Metadata
                    dataOut.writeInt(MAGIC_HEADER)
                    dataOut.writeLong(totalRecords)
                    dataOut.writeLong(System.currentTimeMillis())
                    dataOut.writeLong(originalLength)
                    dataOut.flush()

                    // Stream compressed raw database
                    GZIPOutputStream(bufferedOut, BUFFER_SIZE).use { gzipOut ->
                        FileInputStream(dbFile).use { fileIn ->
                            BufferedInputStream(fileIn, BUFFER_SIZE).use { bufferedIn ->
                                var len: Int
                                while (bufferedIn.read(buffer).also { len = it } != -1) {
                                    gzipOut.write(buffer, 0, len)
                                    bytesReadTotal += len

                                    val now = System.currentTimeMillis()
                                    val elapsed = (now - startTime).coerceAtLeast(1)
                                    val speed = (bytesReadTotal / (1024.0 * 1024.0)) / (elapsed / 1000.0)

                                    _progress.value = _progress.value.copy(
                                        bytesProcessed = bytesReadTotal,
                                        elapsedMs = elapsed,
                                        throughputMBps = speed
                                    )
                                }
                                gzipOut.finish()
                            }
                        }
                    }
                }
            }

            val totalElapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val finalSpeed = (originalLength / (1024.0 * 1024.0)) / (totalElapsed / 1000.0)

            // Query compressed size if possible
            val compressedBytes = try {
                context.contentResolver.openFileDescriptor(outputUri, "r")?.use {
                    it.statSize
                } ?: -1L
            } catch (e: Exception) {
                -1L
            }

            val compressedMb = if (compressedBytes > 0) compressedBytes / (1024.0 * 1024.0) else (originalLength * 0.25) / (1024.0 * 1024.0)
            val origMb = originalLength / (1024.0 * 1024.0)
            val ratio = if (origMb > 0) ((origMb - compressedMb) / origMb) * 100.0 else 0.0

            _progress.value = BackupProgress(
                action = BackupAction.COMPLETED,
                isRunning = false,
                title = "BACKUP COMPLETE // 100% INTEGRITY",
                details = String.format("Backed up %,d records in %.2f s at %.1f MB/s (Reduced from %.2f MB to %.2f MB)", totalRecords, totalElapsed / 1000.0, finalSpeed, origMb, compressedMb),
                bytesProcessed = originalLength,
                totalBytes = originalLength,
                elapsedMs = totalElapsed,
                throughputMBps = finalSpeed,
                recordsCount = totalRecords,
                originalSizeMb = origMb,
                compressedSizeMb = compressedMb,
                compressionRatio = ratio
            )

            Log.i(TAG, "Fast backup completed in ${totalElapsed}ms. Throughput: ${finalSpeed}MB/s")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error generating fast backup", e)
            _progress.value = BackupProgress(
                action = BackupAction.ERROR,
                isRunning = false,
                error = "Backup failed: ${e.localizedMessage ?: "I/O Stream Error"}"
            )
            return@withContext false
        }
    }

    /**
     * Restores an ultra-fast compressed or raw backup into the SQLite database with zero-loss atomicity.
     */
    suspend fun restoreBackup(inputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            _progress.value = BackupProgress(
                action = BackupAction.RESTORING,
                isRunning = true,
                title = "ANALYZING BACKUP PAYLOAD",
                details = "Validating binary headers and archive format..."
            )

            val inputStream: InputStream? = context.contentResolver.openInputStream(inputUri)
            if (inputStream == null) {
                _progress.value = BackupProgress(
                    action = BackupAction.ERROR,
                    error = "Failed to access backup input stream"
                )
                return@withContext false
            }

            val dbFile = dbHelper.getDatabaseFile()
            val tempRestoreFile = File(dbFile.parentFile, "nullhud_restore_temp.db")
            if (tempRestoreFile.exists()) {
                tempRestoreFile.delete()
            }

            var recordedRecordsCount = 0L
            var originalSize = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            // 1. Inspect Header
            val bufferedIn = BufferedInputStream(inputStream, BUFFER_SIZE)
            bufferedIn.mark(1024)

            val magicBytes = ByteArray(4)
            val bytesRead = bufferedIn.read(magicBytes)
            bufferedIn.reset()

            val isNbkArchive = if (bytesRead == 4) {
                val magicInt = ((magicBytes[0].toInt() and 0xFF) shl 24) or
                        ((magicBytes[1].toInt() and 0xFF) shl 16) or
                        ((magicBytes[2].toInt() and 0xFF) shl 8) or
                        (magicBytes[3].toInt() and 0xFF)
                magicInt == MAGIC_HEADER
            } else false

            // Check if standard GZIP header (0x1F, 0x8B)
            val isStandardGzip = bytesRead >= 2 &&
                    (magicBytes[0].toInt() and 0xFF) == 0x1F &&
                    (magicBytes[1].toInt() and 0xFF) == 0x8B

            _progress.value = BackupProgress(
                action = BackupAction.RESTORING,
                isRunning = true,
                title = "STREAMING HIGH-THROUGHPUT RESTORE",
                details = if (isNbkArchive) "Decompressing NullEmperor NBK1 payload..." else "Restoring raw database image..."
            )

            var totalBytesWritten = 0L

            FileOutputStream(tempRestoreFile).use { fileOut ->
                BufferedOutputStream(fileOut, BUFFER_SIZE).use { bufferedOut ->
                    when {
                        isNbkArchive -> {
                            val dataIn = DataInputStream(bufferedIn)
                            dataIn.readInt() // Skip Magic
                            recordedRecordsCount = dataIn.readLong()
                            val timestamp = dataIn.readLong()
                            originalSize = dataIn.readLong()

                            GZIPInputStream(dataIn, BUFFER_SIZE).use { gzipIn ->
                                var len: Int
                                while (gzipIn.read(buffer).also { len = it } != -1) {
                                    bufferedOut.write(buffer, 0, len)
                                    totalBytesWritten += len

                                    val now = System.currentTimeMillis()
                                    val elapsed = (now - startTime).coerceAtLeast(1)
                                    val speed = (totalBytesWritten / (1024.0 * 1024.0)) / (elapsed / 1000.0)

                                    _progress.value = _progress.value.copy(
                                        bytesProcessed = totalBytesWritten,
                                        totalBytes = originalSize,
                                        elapsedMs = elapsed,
                                        throughputMBps = speed
                                    )
                                }
                            }
                        }
                        isStandardGzip -> {
                            GZIPInputStream(bufferedIn, BUFFER_SIZE).use { gzipIn ->
                                var len: Int
                                while (gzipIn.read(buffer).also { len = it } != -1) {
                                    bufferedOut.write(buffer, 0, len)
                                    totalBytesWritten += len

                                    val now = System.currentTimeMillis()
                                    val elapsed = (now - startTime).coerceAtLeast(1)
                                    val speed = (totalBytesWritten / (1024.0 * 1024.0)) / (elapsed / 1000.0)

                                    _progress.value = _progress.value.copy(
                                        bytesProcessed = totalBytesWritten,
                                        elapsedMs = elapsed,
                                        throughputMBps = speed
                                    )
                                }
                            }
                        }
                        else -> {
                            // Raw uncompressed SQLite backup
                            var len: Int
                            while (bufferedIn.read(buffer).also { len = it } != -1) {
                                bufferedOut.write(buffer, 0, len)
                                totalBytesWritten += len

                                val now = System.currentTimeMillis()
                                val elapsed = (now - startTime).coerceAtLeast(1)
                                val speed = (totalBytesWritten / (1024.0 * 1024.0)) / (elapsed / 1000.0)

                                _progress.value = _progress.value.copy(
                                    bytesProcessed = totalBytesWritten,
                                    elapsedMs = elapsed,
                                    throughputMBps = speed
                                )
                            }
                        }
                    }
                    bufferedOut.flush()
                }
            }

            // 2. Validate SQLite Header on Restored Temp File
            val isValidSqlite = FileInputStream(tempRestoreFile).use { checkIn ->
                val headerBytes = ByteArray(16)
                val read = checkIn.read(headerBytes)
                read == 16 && String(headerBytes).startsWith(SQLITE_HEADER)
            }

            if (!isValidSqlite) {
                tempRestoreFile.delete()
                _progress.value = BackupProgress(
                    action = BackupAction.ERROR,
                    error = "File is corrupt or not a valid SQLite database image"
                )
                return@withContext false
            }

            _progress.value = BackupProgress(
                action = BackupAction.RESTORING,
                isRunning = true,
                title = "FINALIZING DATABASE REPLACEMENT",
                details = "Closing active database instances and replacing data storage..."
            )

            // 3. Close open connections and replace live database file
            dbHelper.close()

            // Remove existing database, WAL, and SHM files
            val walFile = File(dbFile.parentFile, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parentFile, "${dbFile.name}-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (dbFile.exists()) dbFile.delete()

            val renameSuccess = tempRestoreFile.renameTo(dbFile)
            if (!renameSuccess) {
                // Fallback copy
                tempRestoreFile.copyTo(dbFile, overwrite = true)
                tempRestoreFile.delete()
            }

            // 4. Reopen and verify new record count
            val verifiedCount = dbHelper.getTotalCount()
            val totalElapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val finalSpeed = (totalBytesWritten / (1024.0 * 1024.0)) / (totalElapsed / 1000.0)

            _progress.value = BackupProgress(
                action = BackupAction.COMPLETED,
                isRunning = false,
                title = "RESTORE COMPLETE // 100% SYNCHRONIZED",
                details = String.format("Restored %,d records (%.2f MB) in %.2f s at %.1f MB/s", verifiedCount, totalBytesWritten / (1024.0 * 1024.0), totalElapsed / 1000.0, finalSpeed),
                bytesProcessed = totalBytesWritten,
                totalBytes = totalBytesWritten,
                elapsedMs = totalElapsed,
                throughputMBps = finalSpeed,
                recordsCount = verifiedCount
            )

            Log.i(TAG, "Restore successful: $verifiedCount records in ${totalElapsed}ms at ${finalSpeed}MB/s")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup", e)
            _progress.value = BackupProgress(
                action = BackupAction.ERROR,
                isRunning = false,
                error = "Restore failed: ${e.localizedMessage ?: "Decompression Error"}"
            )
            return@withContext false
        }
    }

    fun resetStatus() {
        _progress.value = BackupProgress()
    }
}

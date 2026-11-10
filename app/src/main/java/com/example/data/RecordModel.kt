package com.example.data

import android.net.Uri

data class PersonRecord(
    val id: Long = 0,
    val nationalCode: String,
    val cardNo: String,
    val fullName: String,
    val birthDate: String,
    val mobile: String
)

enum class SearchField(val label: String, val dbColumn: String) {
    ALL("SMART AUTO", "all"),
    NATIONAL_CODE("NAT CODE", "national_code"),
    CARD_NO("CARD NO", "card_no"),
    MOBILE("MOBILE", "mobile"),
    FULL_NAME("FULL NAME", "full_name")
}

data class SearchResult(
    val records: List<PersonRecord>,
    val queryDurationMs: Double,
    val totalMatches: Int,
    val indexUsed: String
)

enum class ImportStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class ImportProgress(
    val status: ImportStatus = ImportStatus.IDLE,
    val sourceName: String = "",
    val processedRecords: Long = 0L,
    val totalRecords: Long = 0L,
    val recordsPerSecond: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val etaSeconds: Long = 0L,
    val errorMessage: String? = null,
    val hasRecoverableSession: Boolean = false
)

data class VcfFileInfo(
    val file: java.io.File,
    val partNumber: Int,
    val recordsCount: Long,
    val sizeBytes: Long
)

data class VcfExportProgress(
    val status: ImportStatus = ImportStatus.IDLE,
    val prefix: String = "",
    val filePath: String = "",
    val exportedFiles: List<VcfFileInfo> = emptyList(),
    val currentPart: Int = 1,
    val totalParts: Int = 1,
    val processedRecords: Long = 0L,
    val totalRecords: Long = 0L,
    val recordsPerSecond: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val etaSeconds: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val hasRecoverableSession: Boolean = false
)

data class VcfSessionData(
    val prefix: String,
    val filePath: String,
    val lastProcessedId: Long,
    val processedRecords: Long,
    val totalRecords: Long,
    val status: String,
    val elapsedMs: Long
)

enum class CsvDelimiter(val label: String, val char: Char) {
    AUTO("AUTO-DETECT", ','),
    COMMA("COMMA [,]", ','),
    SEMICOLON("SEMICOLON [;]", ';'),
    TAB("TAB [\\t]", '\t'),
    PIPE("PIPE [|]", '|')
}

enum class ColumnMapping(val label: String, val description: String) {
    AUTO_HEADER("AUTO (FROM HEADERS)", "Detect columns: Code, Card, Name, Birth, Mobile"),
    STANDARD("STANDARD", "Code, Card, Name, Birth, Mobile"),
    MOBILE_FIRST("MOBILE FIRST", "Mobile, Code, Name, Card, Birth"),
    CARD_FIRST("CARD FIRST", "Card, Code, Name, Mobile, Birth"),
    NAME_FIRST("NAME FIRST", "Name, Code, Mobile, Card, Birth")
}

enum class HeaderMode(val label: String) {
    AUTO("AUTO-DETECT"),
    ALWAYS_SKIP("SKIP 1ST LINE"),
    NO_HEADER("NO HEADER (ALL DATA)")
}

data class CsvFormatConfig(
    val delimiter: CsvDelimiter = CsvDelimiter.AUTO,
    val columnMapping: ColumnMapping = ColumnMapping.AUTO_HEADER,
    val headerMode: HeaderMode = HeaderMode.AUTO
)

enum class QueueItemStatus {
    PENDING,
    SCANNING,
    IMPORTING,
    COMPLETED,
    PAUSED,
    FAILED,
    CANCELLED
}

data class CsvQueueItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val fileSize: Long = 0L,
    val format: CsvFormatConfig = CsvFormatConfig(),
    val status: QueueItemStatus = QueueItemStatus.PENDING,
    val totalRecords: Long = 0L,
    val processedRecords: Long = 0L,
    val recordsPerSecond: Long = 0L,
    val errorMessage: String? = null,
    val enqueuedTimeMs: Long = System.currentTimeMillis()
)

data class QueueOverallProgress(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val totalProcessedRecords: Long = 0L,
    val totalEstimatedRecords: Long = 0L,
    val currentFileName: String = "",
    val overallSpeed: Long = 0L,
    val etaSeconds: Long = 0L
)


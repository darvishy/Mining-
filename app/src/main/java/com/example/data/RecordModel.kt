package com.example.data

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


package com.zime.app.androidtoflutter.models.conversion

import com.zime.app.androidtoflutter.models.project.SourceFile
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * 변환 결과
 */
@Serializable
data class ConversionResult(
    val success: Boolean,
    val sourceFile: SourceFile,
    val outputFiles: List<OutputFile> = emptyList(),
    val errors: List<ConversionError> = emptyList(),
    val warnings: List<ConversionWarning> = emptyList(),
    val stats: ConversionStats = ConversionStats()
)

@Serializable
data class OutputFile(
    val pathString: String,
    val content: String,
    val type: OutputFileType,
    val generatedBy: GenerationMethod
)

@Serializable
enum class OutputFileType {
    DART,
    YAML,
    JSON,
    ASSET,
    ARB, // Flutter localization
    MARKDOWN
}

@Serializable
enum class GenerationMethod {
    RULE_BASED,
    AI_ASSISTED,
    TEMPLATE,
    HYBRID
}

@Serializable
data class ConversionError(
    val code: String,
    val message: String,
    val filePath: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null,
    val severity: ErrorSeverity = ErrorSeverity.ERROR
)

@Serializable
enum class ErrorSeverity {
    ERROR,
    FATAL
}

@Serializable
data class ConversionWarning(
    val code: String,
    val message: String,
    val filePath: String? = null,
    val line: Int? = null,
    val suggestion: String? = null,
    val severity: WarningSeverity = WarningSeverity.MEDIUM
)

@Serializable
enum class WarningSeverity {
    LOW,
    MEDIUM,
    HIGH
}

@Serializable
data class ConversionStats(
    val totalLines: Int = 0,
    val convertedLines: Int = 0,
    val aiAssistedLines: Int = 0,
    val manualReviewRequired: Int = 0,
    val durationMs: Long = 0
) {
    val conversionRate: Float get() = 
        if (totalLines > 0) convertedLines.toFloat() / totalLines else 0f
    
    val aiAssistanceRate: Float get() = 
        if (convertedLines > 0) aiAssistedLines.toFloat() / convertedLines else 0f
}

/**
 * 프로젝트 전체 변환 결과
 */
@Serializable
data class ProjectConversionResult(
    val success: Boolean,
    val fileResults: List<ConversionResult> = emptyList(),
    val totalStats: ProjectConversionStats = ProjectConversionStats(),
    val createdFlutterProject: Boolean = false
)

@Serializable
data class ProjectConversionStats(
    val totalFiles: Int = 0,
    val successfulFiles: Int = 0,
    val failedFiles: Int = 0,
    val warningFiles: Int = 0,
    val totalLines: Int = 0,
    val convertedLines: Int = 0,
    val totalDurationMs: Long = 0
) {
    val successRate: Float get() = 
        if (totalFiles > 0) successfulFiles.toFloat() / totalFiles else 0f
}

package com.zime.app.androidtoflutter.models.analysis

import com.zime.app.androidtoflutter.models.project.SourceFile
import kotlinx.serialization.Serializable

/**
 * 프로젝트 전체 분석 결과
 */
@Serializable
data class AnalysisResult(
    val totalFiles: Int = 0,
    val totalComposables: Int = 0,
    val totalClasses: Int = 0,
    val composables: List<AnalyzedComposable> = emptyList(),
    val classes: List<AnalyzedClass> = emptyList(),
    val dependencies: List<FileDependency> = emptyList(),
    val conversionOrder: List<ConversionOrderItem> = emptyList(),
    val complexityScore: ComplexityScore = ComplexityScore(),
    val hasCyclicDependencies: Boolean = false,
    val cyclicDependencies: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val estimatedConversionTime: String = ""
)

/**
 * 단일 파일 분석 결과 (기존 호환성 유지)
 */
@Serializable
data class FileAnalysisResult(
    val file: SourceFile,
    val composables: List<ComposableInfo> = emptyList(),
    val classes: List<ClassInfo> = emptyList(),
    val functions: List<FunctionInfo> = emptyList(),
    val imports: List<ImportInfo> = emptyList(),
    val complexity: ComplexityScore = ComplexityScore()
)

/**
 * 분석된 Composable 정보
 */
@Serializable
data class AnalyzedComposable(
    val name: String,
    val filePath: String,
    val parameters: List<ComposableParam> = emptyList(),
    val stateVariables: List<StateInfo> = emptyList(),
    val childWidgets: List<String> = emptyList(),
    val modifiers: List<String> = emptyList(),
    val complexity: Int = 0
)

@Serializable
data class ComposableParam(
    val name: String,
    val type: String,
    val defaultValue: String? = null,
    val isRequired: Boolean = true
)

@Serializable
data class StateInfo(
    val name: String,
    val type: String,
    val stateType: String,
    val initialValue: String
)

/**
 * 분석된 클래스 정보
 */
@Serializable
data class AnalyzedClass(
    val name: String,
    val fqName: String,
    val filePath: String,
    val kind: String,
    val properties: List<PropertyInfo> = emptyList(),
    val methods: List<MethodInfo> = emptyList(),
    val superTypes: List<String> = emptyList()
)

@Serializable
data class PropertyInfo(
    val name: String,
    val type: String,
    val isMutable: Boolean = false,
    val hasDefault: Boolean = false,
    val defaultValue: String? = null
)

@Serializable
data class MethodInfo(
    val name: String,
    val returnType: String,
    val parameters: List<String> = emptyList(),
    val isComposable: Boolean = false,
    val isSuspend: Boolean = false
)

/**
 * 파일 의존성 정보
 */
@Serializable
data class FileDependency(
    val filePath: String,
    val dependsOn: List<String>
)

/**
 * 변환 순서 항목
 */
@Serializable
data class ConversionOrderItem(
    val filePath: String,
    val priority: String,
    val requiresAI: Boolean,
    val complexity: Int
)

/**
 * 복잡도 점수
 */
@Serializable
data class ComplexityScore(
    val total: Int = 0,
    val composeSpecific: Int = 0,
    val averagePerFile: Int = 0,
    val level: String = "LOW",
    val breakdown: Map<String, Int> = emptyMap(),
    // 기존 필드 (호환성)
    val linesOfCode: Int = 0,
    val cyclomaticComplexity: Int = 0,
    val composableDepth: Int = 0,
    val stateCount: Int = 0,
    val dependencyCount: Int = 0
) {
    val overallScore: Int get() =
        if (total > 0) total / 10
        else (cyclomaticComplexity * 2 + composableDepth * 3 + stateCount + dependencyCount) / 7

    val difficulty: ConversionDifficulty get() = when {
        level == "HIGH" || overallScore > 12 -> ConversionDifficulty.COMPLEX
        level == "MEDIUM" || overallScore > 7 -> ConversionDifficulty.HARD
        overallScore > 3 -> ConversionDifficulty.MEDIUM
        else -> ConversionDifficulty.EASY
    }
}

@Serializable
enum class ConversionDifficulty {
    EASY,
    MEDIUM,
    HARD,
    COMPLEX
}

// ============================================
// 기존 호환성을 위한 클래스들
// ============================================

@Serializable
data class ComposableInfo(
    val name: String,
    val parameters: List<ParameterInfo> = emptyList(),
    val hasPreview: Boolean = false,
    val modifiers: List<String> = emptyList(),
    val childComposables: List<String> = emptyList(),
    val stateUsage: List<StateUsageInfo> = emptyList(),
    val startLine: Int = 0,
    val endLine: Int = 0
)

@Serializable
data class ClassInfo(
    val name: String,
    val type: ClassType,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val properties: List<OldPropertyInfo> = emptyList(),
    val methods: List<FunctionInfo> = emptyList(),
    val annotations: List<String> = emptyList(),
    val startLine: Int = 0,
    val endLine: Int = 0
)

@Serializable
enum class ClassType {
    CLASS,
    DATA_CLASS,
    SEALED_CLASS,
    OBJECT,
    INTERFACE,
    ENUM,
    ANNOTATION
}

@Serializable
data class FunctionInfo(
    val name: String,
    val parameters: List<ParameterInfo> = emptyList(),
    val returnType: String? = null,
    val modifiers: List<String> = emptyList(),
    val annotations: List<String> = emptyList(),
    val isComposable: Boolean = false,
    val isSuspend: Boolean = false,
    val startLine: Int = 0,
    val endLine: Int = 0
)

@Serializable
data class ParameterInfo(
    val name: String,
    val type: String,
    val defaultValue: String? = null,
    val isNullable: Boolean = false,
    val annotations: List<String> = emptyList()
)

@Serializable
data class OldPropertyInfo(
    val name: String,
    val type: String,
    val isVal: Boolean = true,
    val isMutable: Boolean = false,
    val isNullable: Boolean = false,
    val defaultValue: String? = null,
    val annotations: List<String> = emptyList()
)

@Serializable
data class ImportInfo(
    val fullPath: String,
    val alias: String? = null,
    val isWildcard: Boolean = false
)

@Serializable
data class StateUsageInfo(
    val type: StateType,
    val name: String,
    val valueType: String? = null
)

@Serializable
enum class StateType {
    REMEMBER,
    REMEMBER_SAVEABLE,
    MUTABLE_STATE,
    STATE_FLOW,
    SHARED_FLOW,
    LIVE_DATA,
    DERIVED_STATE
}

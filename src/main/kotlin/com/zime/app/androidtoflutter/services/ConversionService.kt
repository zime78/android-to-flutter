package com.zime.app.androidtoflutter.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.zime.app.androidtoflutter.analyzer.ProjectAnalyzer
import com.zime.app.androidtoflutter.claude.cli.ClaudeCliExecutor
import com.zime.app.androidtoflutter.claude.hook.ConversionContext
import com.zime.app.androidtoflutter.claude.hook.HookManager
import com.zime.app.androidtoflutter.converter.ProjectConverter
import com.zime.app.androidtoflutter.mappings.TypeMappings
import com.zime.app.androidtoflutter.mappings.WidgetMappings
import com.zime.app.androidtoflutter.models.analysis.AnalysisResult
import com.zime.app.androidtoflutter.models.conversion.ConversionResult
import com.zime.app.androidtoflutter.models.conversion.ConversionStats
import com.zime.app.androidtoflutter.models.conversion.GenerationMethod
import com.zime.app.androidtoflutter.models.conversion.OutputFile
import com.zime.app.androidtoflutter.models.conversion.OutputFileType
import com.zime.app.androidtoflutter.models.project.ConversionConfig
import com.zime.app.androidtoflutter.models.project.SourceFile
import com.zime.app.androidtoflutter.verification.VerificationConfig
import com.zime.app.androidtoflutter.verification.VerificationResult
import com.zime.app.androidtoflutter.verification.VerificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * 변환 서비스 - 통합 진입점
 * - 프로젝트 분석
 * - 규칙 기반 변환
 * - AI 보조 변환
 * - UI 검증
 */
@Service(Service.Level.PROJECT)
class ConversionService(private val project: Project) {

    private val hookManager = HookManager(project)
    private val cliExecutor = ClaudeCliExecutor()
    private val projectAnalyzer by lazy { ProjectAnalyzer(project) }
    private val verificationService by lazy { VerificationService.getInstance(project) }

    /**
     * 프로젝트 분석
     */
    suspend fun analyzeProject(
        sourcePath: Path,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): AnalysisResult = withContext(Dispatchers.Default) {
        onProgress("프로젝트 분석 시작...", 0)
        projectAnalyzer.analyzeProject(sourcePath)
    }

    /**
     * 프로젝트 전체 변환
     */
    suspend fun convertProject(
        sourcePath: Path,
        targetPath: Path,
        config: ConversionConfig,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): com.zime.app.androidtoflutter.models.conversion.ConversionResult = withContext(Dispatchers.Default) {
        // Hook 설정 (필요시)
        if (config.aiSettings.hookEnabled) {
            hookManager.setupHooks()
        }

        // ProjectConverter 사용
        val converter = ProjectConverter(project, config)
        converter.convertProject(sourcePath, targetPath, onProgress)
    }

    /**
     * 단일 파일 변환 (기존 호환성 유지)
     */
    suspend fun convertFile(
        sourceFile: SourceFile,
        config: ConversionConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ConversionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        onProgress(0.1f, "파일 분석 중: ${sourceFile.relativePath}")

        // 1. 소스 코드 읽기
        val sourceCode = sourceFile.path.toFile().readText()

        // 2. 변환 컨텍스트 설정
        val context = ConversionContext(
            sourceFile = sourceFile.pathString,
            targetFile = "",
            stateManagement = config.options.stateManagement.name.lowercase(),
            navigation = config.options.navigation.name.lowercase(),
            widgetMappings = emptyMap()
        )

        if (config.aiSettings.hookEnabled) {
            hookManager.setConversionContext(context)
        }

        onProgress(0.3f, "변환 중...")

        // 3. 변환 수행
        val (dartCode, method) = performConversion(sourceCode, config, context)

        onProgress(0.8f, "결과 생성 중...")

        // 4. 출력 파일 생성
        val outputFileName = convertFileName(sourceFile.relativePath)
        val outputFile = OutputFile(
            pathString = "${config.targetPathString}/lib/$outputFileName",
            content = dartCode,
            type = OutputFileType.DART,
            generatedBy = method
        )

        val duration = System.currentTimeMillis() - startTime

        onProgress(1.0f, "완료: $outputFileName")

        ConversionResult(
            success = true,
            sourceFile = sourceFile,
            outputFiles = listOf(outputFile),
            errors = emptyList(),
            warnings = emptyList(),
            stats = ConversionStats(
                totalLines = sourceCode.lines().size,
                convertedLines = dartCode.lines().size,
                aiAssistedLines = if (method == GenerationMethod.AI_ASSISTED) dartCode.lines().size else 0,
                manualReviewRequired = 0,
                durationMs = duration
            )
        )
    }

    /**
     * UI 검증 실행
     */
    suspend fun verifyConversion(
        config: VerificationConfig,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): VerificationResult = withContext(Dispatchers.Default) {
        verificationService.runVerification(config, onProgress)
    }

    /**
     * 변환 수행 (규칙 기반 또는 AI)
     */
    private suspend fun performConversion(
        sourceCode: String,
        config: ConversionConfig,
        context: ConversionContext
    ): Pair<String, GenerationMethod> {
        val isSimple = isSimpleFile(sourceCode)

        return if (isSimple && !config.aiSettings.useClaudeCode) {
            Pair(convertWithRules(sourceCode, config), GenerationMethod.RULE_BASED)
        } else if (config.aiSettings.cliEnabled && cliExecutor.isAvailable()) {
            val aiContext = ConversionContext(
                sourceFile = "",
                targetFile = "",
                stateManagement = config.options.stateManagement.name.lowercase(),
                navigation = config.options.navigation.name.lowercase(),
                widgetMappings = emptyMap()
            )
            val result = cliExecutor.convertWithAi(sourceCode, aiContext)
            when (result) {
                is com.zime.app.androidtoflutter.claude.cli.AiConversionResult.Success -> {
                    Pair(result.dartCode, GenerationMethod.AI_ASSISTED)
                }
                is com.zime.app.androidtoflutter.claude.cli.AiConversionResult.Failure -> {
                    Pair(convertWithRules(sourceCode, config), GenerationMethod.RULE_BASED)
                }
            }
        } else {
            Pair(convertWithRules(sourceCode, config), GenerationMethod.RULE_BASED)
        }
    }

    /**
     * 규칙 기반 변환
     */
    private fun convertWithRules(sourceCode: String, config: ConversionConfig): String {
        var dartCode = sourceCode

        // 1. 패키지 선언 변환
        dartCode = convertPackageToImports(dartCode)

        // 2. 타입 변환
        dartCode = convertTypes(dartCode)

        // 3. 기본 문법 변환
        dartCode = convertBasicSyntax(dartCode)

        // 4. Composable 어노테이션 제거
        dartCode = dartCode.replace("@Composable", "")

        // 5. 위젯 변환 (기본)
        dartCode = convertBasicWidgets(dartCode)

        return formatDartCode(dartCode)
    }

    /**
     * 패키지 선언을 Dart import로 변환
     */
    private fun convertPackageToImports(code: String): String {
        val lines = code.lines().toMutableList()
        val imports = mutableListOf<String>()

        imports.add("import 'package:flutter/material.dart';")

        lines.filter { it.trim().startsWith("import ") }
            .forEach { line ->
                val kotlinImport = line.trim().removePrefix("import ")
                val dartImport = convertImport(kotlinImport)
                if (dartImport != null) {
                    imports.add(dartImport)
                }
            }

        val codeWithoutImports = lines.filter {
            !it.trim().startsWith("package ") && !it.trim().startsWith("import ")
        }.joinToString("\n")

        return imports.distinct().joinToString("\n") + "\n\n" + codeWithoutImports
    }

    /**
     * Kotlin import를 Dart import로 변환
     */
    private fun convertImport(kotlinImport: String): String? {
        return when {
            kotlinImport.contains("compose") -> null
            kotlinImport.contains("kotlinx.coroutines") -> null
            kotlinImport.contains("retrofit") -> "import 'package:dio/dio.dart';"
            kotlinImport.contains("room") -> "import 'package:drift/drift.dart';"
            kotlinImport.contains("hilt") -> null
            else -> null
        }
    }

    /**
     * 타입 변환
     */
    private fun convertTypes(code: String): String {
        var result = code

        TypeMappings.primitiveTypes.forEach { (kotlin, dart) ->
            result = result.replace(Regex("\\b$kotlin\\b"), dart)
        }

        TypeMappings.collectionTypes.forEach { (kotlin, dart) ->
            result = result.replace(Regex("\\b$kotlin\\b"), dart)
        }

        return result
    }

    /**
     * 기본 문법 변환
     */
    private fun convertBasicSyntax(code: String): String {
        var result = code

        result = result.replace(Regex("\\bval\\s+"), "final ")
        result = result.replace(Regex("\\bvar\\s+"), "var ")
        result = result.replace(Regex("\\bfun\\s+"), "")
        result = result.replace(Regex("\\bdata class\\s+"), "class ")
        result = result.replace(Regex("\\{\\s*([^}]+)\\s*->"), "($1) {")
        result = result.replace("?:", "??")
        result = result.replace(Regex("\\$\\{([^}]+)\\}"), "\${$1}")
        result = result.replace(Regex("\\$([a-zA-Z_][a-zA-Z0-9_]*)"), "\$$1")

        return result
    }

    /**
     * 기본 위젯 변환
     */
    private fun convertBasicWidgets(code: String): String {
        var result = code

        WidgetMappings.basicWidgets.forEach { (compose, flutter) ->
            if (compose != flutter) {
                result = result.replace(Regex("\\b$compose\\s*\\("), "$flutter(")
            }
        }

        return result
    }

    /**
     * 파일 이름 변환
     */
    private fun convertFileName(kotlinFileName: String): String {
        val baseName = kotlinFileName
            .replace(".kt", "")
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()
        return "$baseName.dart"
    }

    /**
     * 간단한 파일인지 확인
     */
    private fun isSimpleFile(code: String): Boolean {
        val complexPatterns = listOf(
            "ConstraintLayout",
            "LazyVerticalGrid",
            "AnimatedVisibility",
            "rememberCoroutineScope",
            "LaunchedEffect",
            "DisposableEffect"
        )
        return complexPatterns.none { code.contains(it) }
    }

    /**
     * 복잡도 계산
     */
    private fun calculateComplexity(code: String): Int {
        var complexity = 0
        complexity += code.lines().size / 10
        complexity += code.count { it == '{' }
        if (code.contains("@Composable")) complexity += 5
        if (code.contains("remember")) complexity += 3
        if (code.contains("LaunchedEffect")) complexity += 4
        return complexity
    }

    /**
     * Dart 코드 포맷팅
     */
    private fun formatDartCode(code: String): String {
        return code
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Claude Code 사용 가능 여부 확인
     */
    fun isClaudeAvailable(): Boolean = cliExecutor.isAvailable()

    /**
     * Hook 설치 상태 확인
     */
    fun isHooksInstalled(): Boolean = hookManager.isHooksInstalled()

    /**
     * Hook 설치
     */
    fun setupHooks() = hookManager.setupHooks()

    /**
     * Hook 제거
     */
    fun removeHooks() = hookManager.removeHooks()

    companion object {
        fun getInstance(project: Project): ConversionService {
            return project.getService(ConversionService::class.java)
        }
    }
}

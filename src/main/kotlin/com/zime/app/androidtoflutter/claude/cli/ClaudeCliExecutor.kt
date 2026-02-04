package com.zime.app.androidtoflutter.claude.cli

import com.zime.app.androidtoflutter.claude.hook.ConversionContext
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Claude Code CLI 실행기
 * - claude --print 모드로 AI 변환 실행
 * - 응답 파싱 및 Dart 코드 추출
 */
class ClaudeCliExecutor {
    
    /**
     * Claude CLI 사용 가능 여부 확인
     */
    fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Claude CLI 버전 확인
     */
    fun getVersion(): String? {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Claude CLI 실행 (--print 모드)
     */
    suspend fun execute(
        prompt: String,
        options: CliOptions = CliOptions()
    ): CliResponse = withContext(Dispatchers.IO) {
        val command = buildCommand(prompt, options)
        
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
        
        options.workingDirectory?.let {
            processBuilder.directory(it.toFile())
        }
        
        val process = processBuilder.start()
        
        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        
        // 타임아웃 처리
        val job = launch {
            reader.forEachLine { line ->
                output.appendLine(line)
            }
        }
        
        val completed = withTimeoutOrNull(options.timeout) {
            job.join()
            process.waitFor()
            true
        }
        
        if (completed == null) {
            process.destroyForcibly()
            return@withContext CliResponse(
                success = false,
                content = output.toString(),
                error = "Timeout after ${options.timeout}",
                exitCode = -1
            )
        }
        
        CliResponse(
            success = process.exitValue() == 0,
            content = output.toString(),
            error = if (process.exitValue() != 0) "Exit code: ${process.exitValue()}" else null,
            exitCode = process.exitValue()
        )
    }
    
    /**
     * AI 기반 코드 변환
     */
    suspend fun convertWithAi(
        kotlinCode: String,
        context: ConversionContext
    ): AiConversionResult {
        val prompt = buildConversionPrompt(kotlinCode, context)
        
        val response = execute(prompt, CliOptions(
            printMode = true,
            timeout = 120.seconds
        ))
        
        if (!response.success) {
            return AiConversionResult.Failure(
                error = response.error ?: "Unknown error",
                rawOutput = response.content
            )
        }
        
        // Dart 코드 블록 추출
        val dartCode = extractDartCode(response.content)
        
        return if (dartCode != null) {
            AiConversionResult.Success(
                dartCode = dartCode,
                explanation = extractExplanation(response.content)
            )
        } else {
            AiConversionResult.Failure(
                error = "Could not extract Dart code from response",
                rawOutput = response.content
            )
        }
    }
    
    /**
     * 복잡한 위젯 변환을 AI에 요청
     */
    suspend fun convertComplexWidget(
        composableCode: String,
        widgetName: String,
        usedFeatures: List<String>,
        context: ConversionContext
    ): AiConversionResult {
        val prompt = """
            |I need to convert a complex Kotlin Compose widget to Flutter.
            |
            |Widget name: $widgetName
            |Features used: ${usedFeatures.joinToString(", ")}
            |Target state management: ${context.stateManagement}
            |Target navigation: ${context.navigation}
            |
            |Source Kotlin/Compose code:
            |```kotlin
            |$composableCode
            |```
            |
            |Requirements:
            |1. Preserve exact visual appearance (colors, spacing, layout)
            |2. Maintain the same interaction behavior
            |3. Use Flutter best practices
            |4. Add necessary imports
            |
            |Please provide the complete Flutter/Dart code in a ```dart code block.
        """.trimMargin()
        
        val response = execute(prompt, CliOptions(
            printMode = true,
            timeout = 180.seconds
        ))
        
        if (!response.success) {
            return AiConversionResult.Failure(
                error = response.error ?: "Unknown error",
                rawOutput = response.content
            )
        }
        
        val dartCode = extractDartCode(response.content)
        
        return if (dartCode != null) {
            AiConversionResult.Success(
                dartCode = dartCode,
                explanation = extractExplanation(response.content)
            )
        } else {
            AiConversionResult.Failure(
                error = "Could not extract Dart code from response",
                rawOutput = response.content
            )
        }
    }
    
    /**
     * 변환 결과 검증 요청
     */
    suspend fun verifyConversion(
        kotlinCode: String,
        dartCode: String
    ): VerificationResult {
        val prompt = """
            |Review the following code conversion for correctness.
            |
            |Original Kotlin/Compose:
            |```kotlin
            |$kotlinCode
            |```
            |
            |Converted Flutter/Dart:
            |```dart
            |$dartCode
            |```
            |
            |Check for:
            |1. Visual parity (layout, colors, spacing)
            |2. Logic correctness
            |3. Missing functionality
            |4. Flutter best practices
            |
            |Respond in this exact JSON format:
            |```json
            |{
            |  "score": <1-10>,
            |  "issues": ["issue1", "issue2"],
            |  "suggestions": ["suggestion1", "suggestion2"],
            |  "passed": <true/false>
            |}
            |```
        """.trimMargin()
        
        val response = execute(prompt, CliOptions(
            printMode = true,
            timeout = 60.seconds
        ))
        
        if (!response.success) {
            return VerificationResult(
                score = 0,
                issues = listOf(response.error ?: "Verification failed"),
                suggestions = emptyList(),
                passed = false
            )
        }
        
        return parseVerificationResult(response.content)
    }
    
    private fun buildCommand(prompt: String, options: CliOptions): List<String> {
        return buildList {
            add("claude")
            
            if (options.printMode) {
                add("--print")
            }
            
            options.model?.let {
                add("--model")
                add(it)
            }
            
            options.maxTokens?.let {
                add("--max-tokens")
                add(it.toString())
            }
            
            if (options.dangerouslySkipPermissions) {
                add("--dangerously-skip-permissions")
            }
            
            add(prompt)
        }
    }
    
    private fun buildConversionPrompt(
        kotlinCode: String,
        context: ConversionContext
    ): String {
        val mappingsText = if (context.widgetMappings.isNotEmpty()) {
            """
            |
            |Widget mappings to use:
            |${context.widgetMappings.entries.joinToString("\n") { "- ${it.key} → ${it.value}" }}
            """.trimMargin()
        } else ""
        
        return """
            |Convert the following Kotlin/Jetpack Compose code to Flutter/Dart.
            |
            |Requirements:
            |1. Preserve exact visual appearance (colors, spacing, layout)
            |2. Use ${context.stateManagement} for state management
            |3. Use ${context.navigation} for navigation
            |4. Maintain the same business logic behavior
            |5. Follow Flutter best practices and conventions
            |6. Include necessary imports
            |$mappingsText
            |
            |Source Kotlin/Compose code:
            |```kotlin
            |$kotlinCode
            |```
            |
            |Please provide the complete converted Flutter/Dart code in a ```dart code block.
            |Also explain any significant changes or considerations.
        """.trimMargin()
    }
    
    private fun extractDartCode(response: String): String? {
        // ```dart 또는 ```Dart 코드 블록 추출
        val codeBlockRegex = Regex("```[dD]art\\s*\\n([\\s\\S]*?)\\n```")
        return codeBlockRegex.find(response)?.groupValues?.get(1)?.trim()
    }
    
    private fun extractExplanation(response: String): String {
        // 코드 블록 제거
        val withoutCodeBlocks = response.replace(Regex("```[\\s\\S]*?```"), "")
        return withoutCodeBlocks.trim()
    }
    
    private fun parseVerificationResult(response: String): VerificationResult {
        return try {
            val jsonRegex = Regex("```json\\s*\\n([\\s\\S]*?)\\n```")
            val jsonMatch = jsonRegex.find(response)?.groupValues?.get(1)
            
            if (jsonMatch != null) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                json.decodeFromString<VerificationResult>(jsonMatch)
            } else {
                // JSON 파싱 실패 시 기본값
                VerificationResult(
                    score = 5,
                    issues = listOf("Could not parse verification result"),
                    suggestions = emptyList(),
                    passed = false
                )
            }
        } catch (e: Exception) {
            VerificationResult(
                score = 5,
                issues = listOf("Verification parsing error: ${e.message}"),
                suggestions = emptyList(),
                passed = false
            )
        }
    }
}

data class CliOptions(
    val printMode: Boolean = true,
    val model: String? = null,
    val maxTokens: Int? = null,
    val timeout: Duration = 60.seconds,
    val workingDirectory: Path? = null,
    val dangerouslySkipPermissions: Boolean = false
)

data class CliResponse(
    val success: Boolean,
    val content: String,
    val error: String?,
    val exitCode: Int
)

sealed class AiConversionResult {
    data class Success(
        val dartCode: String,
        val explanation: String
    ) : AiConversionResult()
    
    data class Failure(
        val error: String,
        val rawOutput: String
    ) : AiConversionResult()
}

@kotlinx.serialization.Serializable
data class VerificationResult(
    val score: Int,
    val issues: List<String>,
    val suggestions: List<String>,
    val passed: Boolean
)

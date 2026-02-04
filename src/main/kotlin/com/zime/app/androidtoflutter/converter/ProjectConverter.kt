package com.zime.app.androidtoflutter.converter

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.zime.app.androidtoflutter.analyzer.*
import com.zime.app.androidtoflutter.claude.cli.AiConversionResult
import com.zime.app.androidtoflutter.claude.cli.ClaudeCliExecutor
import com.zime.app.androidtoflutter.models.conversion.*
import com.zime.app.androidtoflutter.models.project.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import kotlin.io.path.*

/**
 * 프로젝트 변환기 - Android 프로젝트를 Flutter 프로젝트로 변환
 */
class ProjectConverter(
    private val project: Project,
    private val config: ConversionConfig
) {
    private val kotlinAnalyzer = KotlinAnalyzer(project)
    private val composeAnalyzer = ComposeAnalyzer(project)
    private val dependencyAnalyzer = DependencyAnalyzer(project)
    private val composeConverter = ComposeToFlutterConverter()
    private val dartConverter = KotlinToDartConverter()
    private val claudeExecutor = ClaudeCliExecutor()

    private val outputFiles = mutableListOf<OutputFile>()
    private val errors = mutableListOf<ConversionError>()
    private val warnings = mutableListOf<ConversionWarning>()

    /**
     * 프로젝트 전체 변환
     */
    suspend fun convertProject(
        sourcePath: Path,
        targetPath: Path,
        progressCallback: (String, Int) -> Unit
    ): ConversionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        progressCallback("소스 파일 수집 중...", 5)

        // 1. Kotlin 파일 수집
        val kotlinFiles = collectKotlinFiles(sourcePath)
        val virtualFiles = kotlinFiles.mapNotNull { path ->
            LocalFileSystem.getInstance().findFileByPath(path.toString())
        }

        progressCallback("의존성 분석 중...", 10)

        // 2. 의존성 분석 및 변환 순서 결정
        val dependencyGraph = ReadAction.compute<DependencyGraph, Exception> {
            dependencyAnalyzer.analyzeProject(virtualFiles)
        }
        val conversionTasks = dependencyAnalyzer.determineConversionPriority(dependencyGraph)

        progressCallback("Flutter 프로젝트 구조 생성 중...", 15)

        // 3. Flutter 프로젝트 구조 생성
        createFlutterProjectStructure(targetPath)

        // 4. 파일별 변환
        val totalFiles = conversionTasks.size
        var totalLines = 0
        var convertedLines = 0
        var aiAssistedLines = 0

        conversionTasks.forEachIndexed { index, task ->
            val progress = 15 + (index * 70 / totalFiles.coerceAtLeast(1))
            val fileName = task.filePath.substringAfterLast("/")
            progressCallback("변환 중: $fileName", progress)

            try {
                val result = convertFile(task, sourcePath, targetPath)
                totalLines += result.sourceLines
                convertedLines += result.convertedLines
                if (result.usedAI) {
                    aiAssistedLines += result.convertedLines
                }
            } catch (e: Exception) {
                errors.add(ConversionError(
                    code = "CONVERSION_ERROR",
                    message = e.message ?: "Unknown error",
                    filePath = task.filePath
                ))
            }
        }

        progressCallback("pubspec.yaml 생성 중...", 90)

        // 5. pubspec.yaml 생성
        generatePubspec(targetPath)

        progressCallback("마무리 중...", 95)

        // 6. 변환 결과 생성
        val endTime = System.currentTimeMillis()

        progressCallback("완료!", 100)

        // 빈 SourceFile 생성 (프로젝트 변환은 단일 파일이 아님)
        val dummySourceFile = SourceFile(
            pathString = sourcePath.toString(),
            relativePath = "project",
            type = FileType.KOTLIN
        )

        ConversionResult(
            success = errors.isEmpty(),
            sourceFile = dummySourceFile,
            outputFiles = outputFiles,
            errors = errors,
            warnings = warnings,
            stats = ConversionStats(
                totalLines = totalLines,
                convertedLines = convertedLines,
                aiAssistedLines = aiAssistedLines,
                manualReviewRequired = errors.size,
                durationMs = endTime - startTime
            )
        )
    }

    /**
     * 단일 파일 변환
     */
    private suspend fun convertFile(
        task: ConversionTask,
        sourcePath: Path,
        targetPath: Path
    ): FileConversionResult {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(task.filePath)
            ?: return FileConversionResult(0, 0, false)

        val ktFile = ReadAction.compute<KtFile?, Exception> {
            PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
        } ?: return FileConversionResult(0, 0, false)

        val sourceLines = ReadAction.compute<Int, Exception> {
            ktFile.text.lines().size
        }

        // 파일 분석
        val analysis = ReadAction.compute<KotlinFileAnalysis?, Exception> {
            kotlinAnalyzer.analyzeKtFile(ktFile)
        } ?: return FileConversionResult(sourceLines, 0, false)

        // 변환 전략 결정
        val useAI = task.requiresAI && config.aiSettings.useClaudeCode && claudeExecutor.isAvailable()
        val convertedCode = if (useAI) {
            // AI 지원 변환
            convertWithAI(ktFile, analysis, task)
        } else {
            // 규칙 기반 변환
            convertWithRules(ktFile, analysis)
        }

        // 파일 저장
        val relativePath = task.filePath.substringAfter(sourcePath.toString()).removePrefix("/")
        val dartFileName = relativePath
            .replace(".kt", ".dart")
            .replace("kotlin/", "")
            .replace("/", "_")

        val outputPath = targetPath / "lib" / dartFileName

        outputPath.parent.createDirectories()
        outputPath.writeText(convertedCode)

        outputFiles.add(OutputFile(
            pathString = outputPath.toString(),
            content = convertedCode,
            type = OutputFileType.DART,
            generatedBy = if (useAI) GenerationMethod.AI_ASSISTED else GenerationMethod.RULE_BASED
        ))

        return FileConversionResult(
            sourceLines = sourceLines,
            convertedLines = convertedCode.lines().size,
            usedAI = useAI
        )
    }

    /**
     * 규칙 기반 변환
     */
    private fun convertWithRules(ktFile: KtFile, analysis: KotlinFileAnalysis): String {
        val imports = mutableSetOf<String>()
        imports.add("import 'package:flutter/material.dart';")

        val parts = mutableListOf<String>()

        // Composable 변환
        val composeTrees = ReadAction.compute<List<ComposeTree>, Exception> {
            composeAnalyzer.analyzeFile(ktFile)
        }

        composeTrees.forEach { tree ->
            val widgetCode = composeConverter.convert(tree)
            imports.addAll(widgetCode.imports)
            parts.add(widgetCode.code)
        }

        // 비-Composable 코드 변환
        val dartCode = dartConverter.convert(analysis)
        imports.addAll(dartCode.imports)
        parts.addAll(dartCode.classes.map { it.code })
        parts.addAll(dartCode.functions.map { it.code })
        parts.addAll(dartCode.topLevelProperties.map { it.code })

        return """
${imports.sorted().joinToString("\n")}

${parts.joinToString("\n\n")}
""".trimIndent()
    }

    /**
     * AI 지원 변환
     */
    private suspend fun convertWithAI(
        ktFile: KtFile,
        analysis: KotlinFileAnalysis,
        task: ConversionTask
    ): String {
        val kotlinCode = ReadAction.compute<String, Exception> {
            ktFile.text
        }

        // Claude CLI로 변환 요청
        val context = com.zime.app.androidtoflutter.claude.hook.ConversionContext(
            sourceFile = task.filePath,
            targetFile = "",
            stateManagement = config.options.stateManagement.name.lowercase(),
            navigation = config.options.navigation.name.lowercase(),
            widgetMappings = emptyMap()
        )

        val result = claudeExecutor.convertWithAi(kotlinCode, context)

        return when (result) {
            is AiConversionResult.Success -> result.dartCode
            is AiConversionResult.Failure -> {
                // AI 실패 시 규칙 기반으로 폴백
                warnings.add(ConversionWarning(
                    code = "AI_FALLBACK",
                    message = "AI 변환 실패, 규칙 기반 변환으로 대체: ${result.error}",
                    filePath = task.filePath
                ))
                convertWithRules(ktFile, analysis)
            }
        }
    }

    /**
     * Kotlin 파일 수집
     */
    private fun collectKotlinFiles(sourcePath: Path): List<Path> {
        if (!sourcePath.exists()) return emptyList()

        return sourcePath.walk()
            .filter { it.extension == "kt" }
            .filter { !it.name.endsWith("Test.kt") }
            .filter { !it.toString().contains("/test/") }
            .filter { !it.toString().contains("/androidTest/") }
            .filter { !it.toString().contains("/build/") }
            .toList()
    }

    /**
     * Flutter 프로젝트 구조 생성
     */
    private fun createFlutterProjectStructure(targetPath: Path) {
        // 기본 디렉토리 생성
        listOf(
            "lib",
            "lib/models",
            "lib/screens",
            "lib/widgets",
            "lib/services",
            "lib/utils",
            "test",
            "assets",
            "assets/images"
        ).forEach { dir ->
            (targetPath / dir).createDirectories()
        }

        // main.dart 생성
        val mainDart = """
import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Converted App',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const Scaffold(
        body: Center(
          child: Text('Converted from Android'),
        ),
      ),
    );
  }
}
""".trimIndent()

        (targetPath / "lib" / "main.dart").writeText(mainDart)
    }

    /**
     * pubspec.yaml 생성
     */
    private fun generatePubspec(targetPath: Path) {
        val dependencies = mutableListOf(
            "flutter:",
            "    sdk: flutter",
            "  cupertino_icons: ^1.0.6"
        )

        // 설정에 따른 의존성 추가
        when (config.options.stateManagement) {
            StateManagementType.RIVERPOD -> {
                dependencies.add("  flutter_riverpod: ^2.4.0")
            }
            StateManagementType.BLOC -> {
                dependencies.add("  flutter_bloc: ^8.1.3")
                dependencies.add("  bloc: ^8.1.2")
            }
            StateManagementType.PROVIDER -> {
                dependencies.add("  provider: ^6.1.0")
            }
            StateManagementType.GETX -> {
                dependencies.add("  get: ^4.6.6")
            }
        }

        when (config.options.navigation) {
            NavigationType.GO_ROUTER -> {
                dependencies.add("  go_router: ^12.0.0")
            }
            NavigationType.AUTO_ROUTE -> {
                dependencies.add("  auto_route: ^7.8.0")
            }
            NavigationType.NAVIGATOR -> {
                // 기본 Navigator 사용, 추가 의존성 없음
            }
        }

        when (config.options.networking) {
            NetworkingType.DIO -> {
                dependencies.add("  dio: ^5.4.0")
            }
            NetworkingType.HTTP -> {
                dependencies.add("  http: ^1.1.0")
            }
            NetworkingType.CHOPPER -> {
                dependencies.add("  chopper: ^7.0.0")
            }
        }

        val pubspec = """
name: converted_app
description: App converted from Android using Android to Flutter Porter
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: '>=3.2.0 <4.0.0'

dependencies:
${dependencies.joinToString("\n")}

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^3.0.0

flutter:
  uses-material-design: true
  assets:
    - assets/images/
""".trimIndent()

        (targetPath / "pubspec.yaml").writeText(pubspec)
    }
}

/**
 * 파일 변환 결과 (내부용)
 */
private data class FileConversionResult(
    val sourceLines: Int,
    val convertedLines: Int,
    val usedAI: Boolean
)

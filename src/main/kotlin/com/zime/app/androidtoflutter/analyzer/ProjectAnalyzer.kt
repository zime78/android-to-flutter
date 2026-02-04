package com.zime.app.androidtoflutter.analyzer

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.zime.app.androidtoflutter.models.analysis.AnalysisResult
import com.zime.app.androidtoflutter.models.analysis.AnalyzedComposable
import com.zime.app.androidtoflutter.models.analysis.AnalyzedClass
import com.zime.app.androidtoflutter.models.analysis.ComplexityScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import kotlin.io.path.*

/**
 * 프로젝트 분석기 - Android 프로젝트 전체 분석 수행
 */
class ProjectAnalyzer(private val project: Project) {

    private val kotlinAnalyzer = KotlinAnalyzer(project)
    private val composeAnalyzer = ComposeAnalyzer(project)
    private val dependencyAnalyzer = DependencyAnalyzer(project)

    /**
     * 프로젝트 전체 분석
     */
    suspend fun analyzeProject(sourcePath: Path): AnalysisResult = withContext(Dispatchers.Default) {
        val kotlinFiles = collectKotlinFiles(sourcePath)
        val virtualFiles = kotlinFiles.mapNotNull { path ->
            LocalFileSystem.getInstance().findFileByPath(path.toString())
        }

        // 파일별 분석
        val fileAnalyses = mutableListOf<KotlinFileAnalysis>()
        val composeTrees = mutableListOf<Pair<String, ComposeTree>>()

        ReadAction.compute<Unit, Exception> {
            virtualFiles.forEach { vFile ->
                kotlinAnalyzer.analyzeFile(vFile)?.let { analysis ->
                    fileAnalyses.add(analysis)
                }

                val psiFile = PsiManager.getInstance(project).findFile(vFile) as? KtFile
                psiFile?.let { ktFile ->
                    composeAnalyzer.analyzeFile(ktFile).forEach { tree ->
                        composeTrees.add(vFile.path to tree)
                    }
                }
            }
        }

        // 의존성 분석
        val dependencyGraph = ReadAction.compute<DependencyGraph, Exception> {
            dependencyAnalyzer.analyzeProject(virtualFiles)
        }

        // 변환 우선순위 결정
        val conversionTasks = dependencyAnalyzer.determineConversionPriority(dependencyGraph)

        // 결과 변환
        buildAnalysisResult(fileAnalyses, composeTrees, dependencyGraph, conversionTasks)
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
            .toList()
    }

    /**
     * 분석 결과 빌드
     */
    private fun buildAnalysisResult(
        fileAnalyses: List<KotlinFileAnalysis>,
        composeTrees: List<Pair<String, ComposeTree>>,
        dependencyGraph: DependencyGraph,
        conversionTasks: List<ConversionTask>
    ): AnalysisResult {
        // Composable 분석 결과 변환
        val composables = composeTrees.map { (filePath, tree) ->
            AnalyzedComposable(
                name = tree.name,
                filePath = filePath,
                parameters = tree.parameters.map { param ->
                    com.zime.app.androidtoflutter.models.analysis.ComposableParam(
                        name = param.name,
                        type = param.type,
                        defaultValue = param.defaultValue,
                        isRequired = param.isRequired
                    )
                },
                stateVariables = tree.stateVariables.map { state ->
                    com.zime.app.androidtoflutter.models.analysis.StateInfo(
                        name = state.name,
                        type = state.type,
                        stateType = state.stateType.name,
                        initialValue = state.initialValue
                    )
                },
                childWidgets = extractChildWidgetNames(tree.rootNodes),
                modifiers = extractModifierNames(tree.rootNodes),
                complexity = calculateTreeComplexity(tree)
            )
        }

        // 클래스 분석 결과 변환
        val classes = fileAnalyses.flatMap { file ->
            file.classes.map { cls ->
                AnalyzedClass(
                    name = cls.name,
                    fqName = cls.fqName,
                    filePath = file.filePath,
                    kind = cls.kind.name,
                    properties = cls.properties.map { prop ->
                        com.zime.app.androidtoflutter.models.analysis.PropertyInfo(
                            name = prop.name,
                            type = prop.type,
                            isMutable = prop.isMutable,
                            hasDefault = prop.hasDefaultValue,
                            defaultValue = prop.defaultValue
                        )
                    },
                    methods = cls.methods.map { method ->
                        com.zime.app.androidtoflutter.models.analysis.MethodInfo(
                            name = method.name,
                            returnType = method.returnType,
                            parameters = method.parameters.map { p -> "${p.name}: ${p.type}" },
                            isComposable = method.isComposable,
                            isSuspend = method.isSuspend
                        )
                    },
                    superTypes = cls.superTypes
                )
            }
        }

        // 복잡도 점수 계산
        val complexityScore = calculateComplexityScore(fileAnalyses, composeTrees)

        // 의존성 정보
        val dependencies = dependencyGraph.edges.map { (file, deps) ->
            com.zime.app.androidtoflutter.models.analysis.FileDependency(
                filePath = file,
                dependsOn = deps
            )
        }

        return AnalysisResult(
            totalFiles = fileAnalyses.size,
            totalComposables = composables.size,
            totalClasses = classes.size,
            composables = composables,
            classes = classes,
            dependencies = dependencies,
            conversionOrder = conversionTasks.map { task ->
                com.zime.app.androidtoflutter.models.analysis.ConversionOrderItem(
                    filePath = task.filePath,
                    priority = task.priority.name,
                    requiresAI = task.requiresAI,
                    complexity = task.estimatedComplexity
                )
            },
            complexityScore = complexityScore,
            hasCyclicDependencies = dependencyGraph.hasCycles(),
            cyclicDependencies = dependencyGraph.cycles.map { cycle -> cycle.joinToString(" -> ") },
            warnings = generateWarnings(fileAnalyses, dependencyGraph),
            estimatedConversionTime = estimateConversionTime(conversionTasks)
        )
    }

    /**
     * 자식 위젯 이름 추출
     */
    private fun extractChildWidgetNames(nodes: List<ComposeNode>): List<String> {
        val names = mutableListOf<String>()

        nodes.forEach { node ->
            when (node) {
                is WidgetNode -> {
                    names.add(node.name)
                    names.addAll(extractChildWidgetNames(node.children))
                }
                is ConditionalNode -> {
                    names.addAll(extractChildWidgetNames(node.thenBranch))
                    names.addAll(extractChildWidgetNames(node.elseBranch))
                }
                is WhenNode -> {
                    node.branches.forEach { branch ->
                        names.addAll(extractChildWidgetNames(branch.nodes))
                    }
                }
                is ForEachNode -> {
                    names.addAll(extractChildWidgetNames(node.children))
                }
            }
        }

        return names.distinct()
    }

    /**
     * Modifier 이름 추출
     */
    private fun extractModifierNames(nodes: List<ComposeNode>): List<String> {
        val modifiers = mutableListOf<String>()

        nodes.forEach { node ->
            when (node) {
                is WidgetNode -> {
                    modifiers.addAll(node.modifiers.map { it.name })
                    modifiers.addAll(extractModifierNames(node.children))
                }
                is ConditionalNode -> {
                    modifiers.addAll(extractModifierNames(node.thenBranch))
                    modifiers.addAll(extractModifierNames(node.elseBranch))
                }
                is WhenNode -> {
                    node.branches.forEach { branch ->
                        modifiers.addAll(extractModifierNames(branch.nodes))
                    }
                }
                is ForEachNode -> {
                    modifiers.addAll(extractModifierNames(node.children))
                }
            }
        }

        return modifiers.distinct()
    }

    /**
     * 트리 복잡도 계산
     */
    private fun calculateTreeComplexity(tree: ComposeTree): Int {
        var complexity = tree.parameters.size + tree.stateVariables.size

        fun calculateNodeComplexity(node: ComposeNode): Int {
            return when (node) {
                is WidgetNode -> {
                    1 + node.modifiers.size + node.arguments.size +
                            node.children.sumOf { calculateNodeComplexity(it) }
                }
                is ConditionalNode -> {
                    2 + node.thenBranch.sumOf { calculateNodeComplexity(it) } +
                            node.elseBranch.sumOf { calculateNodeComplexity(it) }
                }
                is WhenNode -> {
                    node.branches.size + node.branches.sumOf { branch ->
                        branch.nodes.sumOf { calculateNodeComplexity(it) }
                    }
                }
                is ForEachNode -> {
                    2 + node.children.sumOf { calculateNodeComplexity(it) }
                }
            }
        }

        complexity += tree.rootNodes.sumOf { calculateNodeComplexity(it) }

        return complexity
    }

    /**
     * 전체 복잡도 점수 계산
     */
    private fun calculateComplexityScore(
        fileAnalyses: List<KotlinFileAnalysis>,
        composeTrees: List<Pair<String, ComposeTree>>
    ): ComplexityScore {
        val totalComplexity = fileAnalyses.sumOf { it.complexity }
        val composeComplexity = composeTrees.sumOf { calculateTreeComplexity(it.second) }
        val avgComplexityPerFile = if (fileAnalyses.isNotEmpty()) totalComplexity / fileAnalyses.size else 0

        val level = when {
            avgComplexityPerFile > 50 -> "HIGH"
            avgComplexityPerFile > 20 -> "MEDIUM"
            else -> "LOW"
        }

        return ComplexityScore(
            total = totalComplexity,
            composeSpecific = composeComplexity,
            averagePerFile = avgComplexityPerFile,
            level = level,
            breakdown = mapOf(
                "files" to fileAnalyses.size,
                "classes" to fileAnalyses.sumOf { it.classes.size },
                "functions" to fileAnalyses.sumOf { it.functions.size },
                "composables" to composeTrees.size,
                "stateVariables" to composeTrees.sumOf { it.second.stateVariables.size }
            )
        )
    }

    /**
     * 경고 생성
     */
    private fun generateWarnings(
        fileAnalyses: List<KotlinFileAnalysis>,
        dependencyGraph: DependencyGraph
    ): List<String> {
        val warnings = mutableListOf<String>()

        // 순환 의존성 경고
        if (dependencyGraph.hasCycles()) {
            warnings.add("순환 의존성이 발견되었습니다. 변환 순서에 주의가 필요합니다.")
        }

        // 높은 복잡도 파일 경고
        fileAnalyses.filter { it.complexity > 50 }.forEach { file ->
            warnings.add("${file.filePath}의 복잡도가 높습니다 (${file.complexity}). AI 지원 변환을 권장합니다.")
        }

        // Compose 없는 파일 경고
        val nonComposeFiles = fileAnalyses.filter { !it.hasComposeImports }
        if (nonComposeFiles.size > fileAnalyses.size * 0.5) {
            warnings.add("Compose를 사용하지 않는 파일이 많습니다. 일부 파일은 Flutter 변환이 불필요할 수 있습니다.")
        }

        return warnings
    }

    /**
     * 변환 시간 추정
     */
    private fun estimateConversionTime(tasks: List<ConversionTask>): String {
        val totalComplexity = tasks.sumOf { it.estimatedComplexity }
        val aiTasks = tasks.count { it.requiresAI }

        // 복잡도 기반 시간 추정 (분 단위)
        val baseTime = totalComplexity * 0.5 // 복잡도당 30초
        val aiTime = aiTasks * 2 // AI 작업당 2분 추가

        val totalMinutes = (baseTime + aiTime).toInt()

        return when {
            totalMinutes < 5 -> "5분 미만"
            totalMinutes < 15 -> "약 10분"
            totalMinutes < 30 -> "약 20분"
            totalMinutes < 60 -> "약 30분-1시간"
            else -> "1시간 이상"
        }
    }
}

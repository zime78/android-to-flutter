package com.zime.app.androidtoflutter.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * 의존성 분석기 - 파일 간 의존성 분석 및 변환 순서 결정
 */
class DependencyAnalyzer(private val project: Project) {

    private val psiManager = PsiManager.getInstance(project)

    /**
     * 프로젝트의 모든 Kotlin 파일 의존성 분석
     */
    fun analyzeProject(sourceFiles: List<VirtualFile>): DependencyGraph {
        val fileAnalyses = mutableMapOf<String, FileNode>()
        val dependencies = mutableMapOf<String, MutableSet<String>>()

        // 1단계: 모든 파일 분석하여 정의된 심볼 수집
        val definedSymbols = mutableMapOf<String, String>() // symbol -> file path

        sourceFiles.forEach { file ->
            val ktFile = psiManager.findFile(file) as? KtFile ?: return@forEach
            val filePath = file.path

            val node = analyzeFileNode(ktFile, filePath)
            fileAnalyses[filePath] = node

            // 정의된 심볼 등록
            node.definedClasses.forEach { className ->
                definedSymbols[className] = filePath
                definedSymbols[node.packageName + "." + className] = filePath
            }
            node.definedFunctions.forEach { funcName ->
                definedSymbols[funcName] = filePath
            }
        }

        // 2단계: 의존성 그래프 구축
        fileAnalyses.forEach { (filePath, node) ->
            dependencies[filePath] = mutableSetOf()

            node.imports.forEach { import ->
                // import된 심볼이 정의된 파일 찾기
                val importedFile = findFileForImport(import, definedSymbols)
                if (importedFile != null && importedFile != filePath) {
                    dependencies[filePath]!!.add(importedFile)
                }
            }

            node.referencedTypes.forEach { type ->
                val typeFile = definedSymbols[type]
                if (typeFile != null && typeFile != filePath) {
                    dependencies[filePath]!!.add(typeFile)
                }
            }
        }

        // 3단계: 변환 순서 계산 (위상 정렬)
        val conversionOrder = topologicalSort(dependencies)

        // 4단계: 순환 의존성 감지
        val cycles = detectCycles(dependencies)

        return DependencyGraph(
            nodes = fileAnalyses,
            edges = dependencies.mapValues { it.value.toList() },
            conversionOrder = conversionOrder,
            cycles = cycles
        )
    }

    /**
     * 개별 파일 노드 분석
     */
    private fun analyzeFileNode(ktFile: KtFile, filePath: String): FileNode {
        val imports = ktFile.importDirectives.mapNotNull { it.importPath?.pathStr }

        val definedClasses = mutableListOf<String>()
        val definedFunctions = mutableListOf<String>()
        val referencedTypes = mutableSetOf<String>()

        ktFile.declarations.forEach { declaration ->
            when (declaration) {
                is KtClass -> {
                    definedClasses.add(declaration.name ?: "")
                    // 상위 타입 참조
                    declaration.superTypeListEntries.forEach { superType ->
                        val typeName = extractTypeName(superType.text)
                        if (typeName.isNotEmpty()) {
                            referencedTypes.add(typeName)
                        }
                    }
                    // 프로퍼티 타입 참조
                    declaration.getProperties().forEach { prop ->
                        prop.typeReference?.let { typeRef ->
                            extractReferencedTypes(typeRef.text, referencedTypes)
                        }
                    }
                    // 메서드 파라미터/반환 타입 참조
                    declaration.declarations.filterIsInstance<KtNamedFunction>().forEach { func ->
                        func.valueParameters.forEach { param ->
                            param.typeReference?.let { typeRef ->
                                extractReferencedTypes(typeRef.text, referencedTypes)
                            }
                        }
                        func.typeReference?.let { typeRef ->
                            extractReferencedTypes(typeRef.text, referencedTypes)
                        }
                    }
                }
                is KtNamedFunction -> {
                    definedFunctions.add(declaration.name ?: "")
                    // 파라미터/반환 타입 참조
                    declaration.valueParameters.forEach { param ->
                        param.typeReference?.let { typeRef ->
                            extractReferencedTypes(typeRef.text, referencedTypes)
                        }
                    }
                    declaration.typeReference?.let { typeRef ->
                        extractReferencedTypes(typeRef.text, referencedTypes)
                    }
                    // 함수 본문의 타입 참조 (간단한 분석)
                    declaration.bodyExpression?.text?.let { body ->
                        extractReferencedTypesFromBody(body, referencedTypes)
                    }
                }
                is KtProperty -> {
                    declaration.typeReference?.let { typeRef ->
                        extractReferencedTypes(typeRef.text, referencedTypes)
                    }
                }
            }
        }

        // 기본 타입 및 표준 라이브러리 제외
        referencedTypes.removeAll(BUILTIN_TYPES)

        return FileNode(
            path = filePath,
            packageName = ktFile.packageFqName.asString(),
            imports = imports,
            definedClasses = definedClasses,
            definedFunctions = definedFunctions,
            referencedTypes = referencedTypes.toList(),
            hasComposables = ktFile.declarations.any { decl ->
                decl is KtNamedFunction && decl.annotationEntries.any { it.text.contains("Composable") }
            },
            complexity = calculateFileComplexity(ktFile)
        )
    }

    /**
     * 타입 이름 추출
     */
    private fun extractTypeName(text: String): String {
        // "Foo<Bar>" -> "Foo"
        return text.substringBefore("<").substringBefore("(").trim()
    }

    /**
     * 타입 참조 추출
     */
    private fun extractReferencedTypes(typeText: String, types: MutableSet<String>) {
        // 제네릭 타입 파싱: "List<Foo>" -> ["List", "Foo"]
        val cleanText = typeText.replace("?", "").trim()
        val mainType = extractTypeName(cleanText)

        if (mainType.isNotEmpty() && mainType.first().isUpperCase()) {
            types.add(mainType)
        }

        // 제네릭 파라미터 추출
        val genericMatch = Regex("""<(.+)>""").find(cleanText)
        genericMatch?.groupValues?.getOrNull(1)?.split(",")?.forEach { param ->
            val paramType = extractTypeName(param.trim())
            if (paramType.isNotEmpty() && paramType.first().isUpperCase()) {
                types.add(paramType)
            }
        }
    }

    /**
     * 함수 본문에서 타입 참조 추출 (간단한 휴리스틱)
     */
    private fun extractReferencedTypesFromBody(body: String, types: MutableSet<String>) {
        // 대문자로 시작하는 식별자 패턴 (생성자 호출, 정적 호출 등)
        val pattern = Regex("""([A-Z][a-zA-Z0-9_]*)\s*[(<.]""")
        pattern.findAll(body).forEach { match ->
            val typeName = match.groupValues[1]
            if (typeName !in BUILTIN_TYPES && typeName !in COMPOSE_KEYWORDS) {
                types.add(typeName)
            }
        }
    }

    /**
     * import에 해당하는 파일 찾기
     */
    private fun findFileForImport(import: String, definedSymbols: Map<String, String>): String? {
        // 정확한 매칭
        if (definedSymbols.containsKey(import)) {
            return definedSymbols[import]
        }

        // 와일드카드 import (com.example.*) 처리
        if (import.endsWith("*")) {
            val packagePrefix = import.dropLast(1)
            definedSymbols.entries.find { (symbol, _) ->
                symbol.startsWith(packagePrefix)
            }?.let { return it.value }
        }

        // 클래스 이름만으로 매칭
        val className = import.substringAfterLast(".")
        return definedSymbols[className]
    }

    /**
     * 위상 정렬 (Kahn's Algorithm)
     */
    private fun topologicalSort(dependencies: Map<String, Set<String>>): List<String> {
        val inDegree = mutableMapOf<String, Int>()
        val allNodes = dependencies.keys + dependencies.values.flatten()

        allNodes.forEach { node ->
            inDegree[node] = 0
        }

        dependencies.forEach { (_, deps) ->
            deps.forEach { dep ->
                inDegree[dep] = (inDegree[dep] ?: 0) + 1
            }
        }

        val queue = ArrayDeque<String>()
        inDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }

        val result = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            result.add(node)

            dependencies[node]?.forEach { dep ->
                inDegree[dep] = (inDegree[dep] ?: 1) - 1
                if (inDegree[dep] == 0) {
                    queue.add(dep)
                }
            }
        }

        // 순환이 있으면 나머지 노드도 추가
        allNodes.filter { it !in result }.forEach { result.add(it) }

        return result.reversed() // 의존성 순서대로 (의존되는 파일 먼저)
    }

    /**
     * 순환 의존성 감지 (DFS)
     */
    private fun detectCycles(dependencies: Map<String, Set<String>>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            visited.add(node)
            recStack.add(node)
            path.add(node)

            dependencies[node]?.forEach { dep ->
                if (dep !in visited) {
                    dfs(dep)
                } else if (dep in recStack) {
                    // 순환 발견
                    val cycleStart = path.indexOf(dep)
                    if (cycleStart >= 0) {
                        cycles.add(path.subList(cycleStart, path.size).toList() + dep)
                    }
                }
            }

            path.removeLast()
            recStack.remove(node)
        }

        dependencies.keys.forEach { node ->
            if (node !in visited) {
                dfs(node)
            }
        }

        return cycles
    }

    /**
     * 파일 복잡도 계산
     */
    private fun calculateFileComplexity(ktFile: KtFile): Int {
        var complexity = 0

        ktFile.declarations.forEach { decl ->
            when (decl) {
                is KtClass -> {
                    complexity += 5 + decl.declarations.size
                }
                is KtNamedFunction -> {
                    complexity += 2
                    if (decl.annotationEntries.any { it.text.contains("Composable") }) {
                        complexity += 3 // Composable은 추가 복잡도
                    }
                }
                is KtProperty -> complexity += 1
            }
        }

        return complexity
    }

    /**
     * 변환 우선순위 결정
     */
    fun determineConversionPriority(graph: DependencyGraph): List<ConversionTask> {
        val tasks = mutableListOf<ConversionTask>()

        graph.conversionOrder.forEach { filePath ->
            val node = graph.nodes[filePath] ?: return@forEach
            val dependencies = graph.edges[filePath] ?: emptyList()

            val priority = when {
                dependencies.isEmpty() -> ConversionPriority.HIGH // 의존성 없음 - 먼저 변환
                node.hasComposables -> ConversionPriority.MEDIUM // UI 컴포넌트
                else -> ConversionPriority.LOW
            }

            tasks.add(ConversionTask(
                filePath = filePath,
                priority = priority,
                dependencies = dependencies,
                estimatedComplexity = node.complexity,
                requiresAI = node.complexity > 20 || node.hasComposables
            ))
        }

        return tasks.sortedWith(
            compareBy<ConversionTask> { it.priority.ordinal }
                .thenBy { it.dependencies.size }
                .thenBy { it.estimatedComplexity }
        )
    }

    companion object {
        private val BUILTIN_TYPES = setOf(
            "String", "Int", "Long", "Float", "Double", "Boolean", "Char", "Byte", "Short",
            "Unit", "Any", "Nothing", "List", "Set", "Map", "Array", "Pair", "Triple",
            "Sequence", "Iterable", "Collection", "MutableList", "MutableSet", "MutableMap",
            "Result", "Lazy", "Comparable", "Throwable", "Exception", "Error",
            // Compose 기본 타입
            "Modifier", "Color", "Dp", "Sp", "TextUnit", "IntOffset", "IntSize",
            "Offset", "Size", "Alignment", "Arrangement", "ContentScale",
            "PaddingValues", "CornerSize", "Shape", "TextStyle", "FontWeight"
        )

        private val COMPOSE_KEYWORDS = setOf(
            "Column", "Row", "Box", "Text", "Button", "Image", "Icon",
            "Scaffold", "TopAppBar", "LazyColumn", "LazyRow", "Card",
            "Surface", "Spacer", "Divider", "Checkbox", "Switch", "TextField"
        )
    }
}

/**
 * 의존성 그래프
 */
data class DependencyGraph(
    val nodes: Map<String, FileNode>,
    val edges: Map<String, List<String>>,
    val conversionOrder: List<String>,
    val cycles: List<List<String>>
) {
    fun hasCycles(): Boolean = cycles.isNotEmpty()

    fun getDependencies(filePath: String): List<String> = edges[filePath] ?: emptyList()

    fun getDependents(filePath: String): List<String> {
        return edges.filter { (_, deps) -> filePath in deps }.keys.toList()
    }
}

/**
 * 파일 노드 정보
 */
data class FileNode(
    val path: String,
    val packageName: String,
    val imports: List<String>,
    val definedClasses: List<String>,
    val definedFunctions: List<String>,
    val referencedTypes: List<String>,
    val hasComposables: Boolean,
    val complexity: Int
)

/**
 * 변환 작업
 */
data class ConversionTask(
    val filePath: String,
    val priority: ConversionPriority,
    val dependencies: List<String>,
    val estimatedComplexity: Int,
    val requiresAI: Boolean
)

enum class ConversionPriority {
    HIGH, MEDIUM, LOW
}

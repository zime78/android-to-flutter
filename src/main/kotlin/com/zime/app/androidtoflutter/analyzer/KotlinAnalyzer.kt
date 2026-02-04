package com.zime.app.androidtoflutter.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.zime.app.androidtoflutter.models.analysis.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Kotlin 코드 분석기 - IntelliJ PSI를 사용하여 Kotlin 파일 분석
 */
class KotlinAnalyzer(private val project: Project) {

    private val psiManager = PsiManager.getInstance(project)

    /**
     * Kotlin 파일 분석
     */
    fun analyzeFile(file: VirtualFile): KotlinFileAnalysis? {
        val psiFile = psiManager.findFile(file) as? KtFile ?: return null
        return analyzeKtFile(psiFile)
    }

    /**
     * KtFile PSI 분석
     */
    fun analyzeKtFile(ktFile: KtFile): KotlinFileAnalysis {
        val imports = extractImports(ktFile)
        val classes = extractClasses(ktFile)
        val functions = extractFunctions(ktFile)
        val properties = extractTopLevelProperties(ktFile)
        val composables = extractComposables(ktFile)

        return KotlinFileAnalysis(
            filePath = ktFile.virtualFile?.path ?: ktFile.name,
            packageName = ktFile.packageFqName.asString(),
            imports = imports,
            classes = classes,
            functions = functions,
            topLevelProperties = properties,
            composables = composables,
            hasComposeImports = imports.any { it.contains("androidx.compose") },
            complexity = calculateComplexity(classes, functions)
        )
    }

    /**
     * import 문 추출
     */
    private fun extractImports(ktFile: KtFile): List<String> {
        return ktFile.importDirectives.mapNotNull { import ->
            import.importPath?.pathStr
        }
    }

    /**
     * 클래스 정보 추출
     */
    private fun extractClasses(ktFile: KtFile): List<ClassAnalysis> {
        val classes = mutableListOf<ClassAnalysis>()

        ktFile.declarations.filterIsInstance<KtClass>().forEach { ktClass ->
            classes.add(analyzeClass(ktClass))
        }

        return classes
    }

    /**
     * 개별 클래스 분석
     */
    private fun analyzeClass(ktClass: KtClass): ClassAnalysis {
        val properties = ktClass.getProperties().map { property ->
            PropertyAnalysis(
                name = property.name ?: "",
                type = property.typeReference?.text ?: "Any",
                isVar = property.isVar,
                isMutable = property.isVar,
                hasDefaultValue = property.hasInitializer(),
                defaultValue = property.initializer?.text,
                annotations = property.annotationEntries.map { it.text }
            )
        }

        val methods = ktClass.declarations.filterIsInstance<KtNamedFunction>().map { function ->
            analyzeFunctionDeclaration(function)
        }

        val constructorParams = ktClass.primaryConstructor?.valueParameters?.map { param ->
            ParameterAnalysis(
                name = param.name ?: "",
                type = param.typeReference?.text ?: "Any",
                hasDefaultValue = param.hasDefaultValue(),
                defaultValue = param.defaultValue?.text,
                isVararg = param.isVarArg
            )
        } ?: emptyList()

        return ClassAnalysis(
            name = ktClass.name ?: "",
            fqName = ktClass.fqName?.asString() ?: ktClass.name ?: "",
            kind = when {
                ktClass.isData() -> ClassKind.DATA_CLASS
                ktClass.isSealed() -> ClassKind.SEALED_CLASS
                ktClass.isEnum() -> ClassKind.ENUM_CLASS
                ktClass.isInterface() -> ClassKind.INTERFACE
                else -> ClassKind.CLASS
            },
            superTypes = ktClass.superTypeListEntries.map { it.text },
            properties = properties,
            methods = methods,
            constructorParams = constructorParams,
            annotations = ktClass.annotationEntries.map { it.text },
            isAbstract = ktClass.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD),
            isOpen = ktClass.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.OPEN_KEYWORD)
        )
    }

    /**
     * 최상위 함수 추출
     */
    private fun extractFunctions(ktFile: KtFile): List<FunctionAnalysis> {
        return ktFile.declarations
            .filterIsInstance<KtNamedFunction>()
            .filter { it.containingClassOrObject == null }
            .map { analyzeFunctionDeclaration(it) }
    }

    /**
     * 함수 분석
     */
    private fun analyzeFunctionDeclaration(function: KtNamedFunction): FunctionAnalysis {
        val parameters = function.valueParameters.map { param ->
            ParameterAnalysis(
                name = param.name ?: "",
                type = param.typeReference?.text ?: "Any",
                hasDefaultValue = param.hasDefaultValue(),
                defaultValue = param.defaultValue?.text,
                isVararg = param.isVarArg
            )
        }

        val annotations = function.annotationEntries.map { it.text }
        val isComposable = annotations.any { it.contains("Composable") }

        return FunctionAnalysis(
            name = function.name ?: "",
            returnType = function.typeReference?.text ?: "Unit",
            parameters = parameters,
            annotations = annotations,
            isComposable = isComposable,
            isSuspend = function.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.SUSPEND_KEYWORD),
            isInline = function.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.INLINE_KEYWORD),
            body = function.bodyExpression?.text,
            complexity = calculateFunctionComplexity(function)
        )
    }

    /**
     * 최상위 프로퍼티 추출
     */
    private fun extractTopLevelProperties(ktFile: KtFile): List<PropertyAnalysis> {
        return ktFile.declarations
            .filterIsInstance<KtProperty>()
            .filter { it.containingClassOrObject == null }
            .map { property ->
                PropertyAnalysis(
                    name = property.name ?: "",
                    type = property.typeReference?.text ?: "Any",
                    isVar = property.isVar,
                    isMutable = property.isVar,
                    hasDefaultValue = property.hasInitializer(),
                    defaultValue = property.initializer?.text,
                    annotations = property.annotationEntries.map { it.text }
                )
            }
    }

    /**
     * Composable 함수 추출
     */
    private fun extractComposables(ktFile: KtFile): List<ComposableAnalysis> {
        val composables = mutableListOf<ComposableAnalysis>()

        ktFile.declarations
            .filterIsInstance<KtNamedFunction>()
            .filter { function ->
                function.annotationEntries.any { it.text.contains("Composable") }
            }
            .forEach { function ->
                composables.add(analyzeComposable(function))
            }

        return composables
    }

    /**
     * Composable 함수 상세 분석
     */
    private fun analyzeComposable(function: KtNamedFunction): ComposableAnalysis {
        val parameters = function.valueParameters.map { param ->
            ComposableParameter(
                name = param.name ?: "",
                type = param.typeReference?.text ?: "Any",
                hasDefaultValue = param.hasDefaultValue(),
                defaultValue = param.defaultValue?.text,
                isLambda = param.typeReference?.text?.contains("->") == true ||
                          param.typeReference?.text?.startsWith("@Composable") == true
            )
        }

        val stateVariables = extractStateVariables(function)
        val childComposables = extractChildComposableCalls(function)
        val modifiers = extractModifierCalls(function)

        return ComposableAnalysis(
            name = function.name ?: "",
            parameters = parameters,
            stateVariables = stateVariables,
            childComposables = childComposables,
            modifiers = modifiers,
            hasPreview = function.annotationEntries.any { it.text.contains("Preview") },
            complexity = calculateFunctionComplexity(function)
        )
    }

    /**
     * State 변수 추출 (remember, mutableStateOf 등)
     */
    private fun extractStateVariables(function: KtNamedFunction): List<StateVariable> {
        val stateVars = mutableListOf<StateVariable>()

        function.bodyExpression?.let { body ->
            extractStateFromExpression(body, stateVars)
        }

        return stateVars
    }

    private fun extractStateFromExpression(expression: KtExpression, stateVars: MutableList<StateVariable>) {
        when (expression) {
            is KtBlockExpression -> {
                expression.statements.forEach { stmt ->
                    if (stmt is KtProperty) {
                        val initializer = stmt.initializer?.text ?: ""
                        val stateType = when {
                            initializer.contains("remember") && initializer.contains("mutableStateOf") -> StateType.REMEMBER_MUTABLE_STATE
                            initializer.contains("rememberSaveable") -> StateType.REMEMBER_SAVEABLE
                            initializer.contains("remember") -> StateType.REMEMBER
                            initializer.contains("mutableStateOf") -> StateType.MUTABLE_STATE
                            initializer.contains("derivedStateOf") -> StateType.DERIVED_STATE
                            initializer.contains("collectAsState") -> StateType.FLOW_STATE
                            else -> null
                        }

                        stateType?.let {
                            stateVars.add(StateVariable(
                                name = stmt.name ?: "",
                                type = stmt.typeReference?.text ?: extractTypeFromInitializer(initializer),
                                stateType = it,
                                initialValue = initializer
                            ))
                        }
                    }
                    if (stmt is KtExpression) {
                        extractStateFromExpression(stmt, stateVars)
                    }
                }
            }
            is KtProperty -> {
                // Handle inline properties
            }
        }
    }

    private fun extractTypeFromInitializer(initializer: String): String {
        // 간단한 타입 추론
        return when {
            initializer.contains("mutableStateOf(true)") || initializer.contains("mutableStateOf(false)") -> "Boolean"
            initializer.contains("mutableStateOf(0)") || initializer.contains("mutableStateOf(1)") -> "Int"
            initializer.contains("mutableStateOf(\"\")") || initializer.contains("mutableStateOf(\"") -> "String"
            initializer.contains("mutableStateListOf") -> "List"
            else -> "Any"
        }
    }

    /**
     * 자식 Composable 호출 추출
     */
    private fun extractChildComposableCalls(function: KtNamedFunction): List<String> {
        val calls = mutableListOf<String>()

        function.bodyExpression?.let { body ->
            extractCallsFromExpression(body, calls)
        }

        return calls.distinct()
    }

    private fun extractCallsFromExpression(expression: KtExpression, calls: MutableList<String>) {
        when (expression) {
            is KtCallExpression -> {
                val callName = expression.calleeExpression?.text ?: ""
                // Composable 호출 패턴 (대문자로 시작하거나 알려진 Compose 함수)
                if (callName.first().isUpperCase() || isKnownComposeFunction(callName)) {
                    calls.add(callName)
                }
                // 람다 인자 내부도 검색
                expression.lambdaArguments.forEach { lambda ->
                    lambda.getLambdaExpression()?.bodyExpression?.let { body ->
                        extractCallsFromExpression(body, calls)
                    }
                }
                // 일반 인자도 검색
                expression.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let { argExpr ->
                        extractCallsFromExpression(argExpr, calls)
                    }
                }
            }
            is KtBlockExpression -> {
                expression.statements.forEach { stmt ->
                    if (stmt is KtExpression) {
                        extractCallsFromExpression(stmt, calls)
                    }
                }
            }
            is KtDotQualifiedExpression -> {
                expression.selectorExpression?.let { selector ->
                    extractCallsFromExpression(selector, calls)
                }
            }
            is KtBinaryExpression -> {
                expression.left?.let { extractCallsFromExpression(it, calls) }
                expression.right?.let { extractCallsFromExpression(it, calls) }
            }
            is KtIfExpression -> {
                expression.then?.let { extractCallsFromExpression(it, calls) }
                expression.`else`?.let { extractCallsFromExpression(it, calls) }
            }
            is KtWhenExpression -> {
                expression.entries.forEach { entry ->
                    entry.expression?.let { extractCallsFromExpression(it, calls) }
                }
            }
        }
    }

    private fun isKnownComposeFunction(name: String): Boolean {
        return name in listOf(
            "remember", "LaunchedEffect", "SideEffect", "DisposableEffect",
            "rememberCoroutineScope", "rememberSaveable", "derivedStateOf",
            "key", "CompositionLocalProvider"
        )
    }

    /**
     * Modifier 호출 추출
     */
    private fun extractModifierCalls(function: KtNamedFunction): List<String> {
        val modifiers = mutableListOf<String>()

        function.bodyExpression?.let { body ->
            extractModifiersFromExpression(body, modifiers)
        }

        return modifiers.distinct()
    }

    private fun extractModifiersFromExpression(expression: KtExpression, modifiers: MutableList<String>) {
        when (expression) {
            is KtDotQualifiedExpression -> {
                val receiverText = expression.receiverExpression.text
                if (receiverText.contains("Modifier") || receiverText.contains("modifier")) {
                    expression.selectorExpression?.let { selector ->
                        if (selector is KtCallExpression) {
                            modifiers.add(selector.calleeExpression?.text ?: "")
                        }
                    }
                }
                extractModifiersFromExpression(expression.receiverExpression, modifiers)
                expression.selectorExpression?.let {
                    extractModifiersFromExpression(it, modifiers)
                }
            }
            is KtCallExpression -> {
                expression.valueArguments.forEach { arg ->
                    arg.getArgumentExpression()?.let {
                        extractModifiersFromExpression(it, modifiers)
                    }
                }
                expression.lambdaArguments.forEach { lambda ->
                    lambda.getLambdaExpression()?.bodyExpression?.let {
                        extractModifiersFromExpression(it, modifiers)
                    }
                }
            }
            is KtBlockExpression -> {
                expression.statements.forEach { stmt ->
                    if (stmt is KtExpression) {
                        extractModifiersFromExpression(stmt, modifiers)
                    }
                }
            }
        }
    }

    /**
     * 복잡도 계산
     */
    private fun calculateComplexity(classes: List<ClassAnalysis>, functions: List<FunctionAnalysis>): Int {
        var complexity = 0
        complexity += classes.sumOf { it.methods.size + it.properties.size }
        complexity += functions.sumOf { it.complexity }
        return complexity
    }

    private fun calculateFunctionComplexity(function: KtNamedFunction): Int {
        var complexity = 1 // 기본 복잡도

        function.bodyExpression?.let { body ->
            complexity += countComplexityInExpression(body)
        }

        return complexity
    }

    private fun countComplexityInExpression(expression: KtExpression): Int {
        var count = 0

        when (expression) {
            is KtIfExpression -> {
                count += 1
                expression.then?.let { count += countComplexityInExpression(it) }
                expression.`else`?.let { count += countComplexityInExpression(it) }
            }
            is KtWhenExpression -> {
                count += expression.entries.size
                expression.entries.forEach { entry ->
                    entry.expression?.let { count += countComplexityInExpression(it) }
                }
            }
            is KtForExpression -> {
                count += 1
                expression.body?.let { count += countComplexityInExpression(it) }
            }
            is KtWhileExpression -> {
                count += 1
                expression.body?.let { count += countComplexityInExpression(it) }
            }
            is KtDoWhileExpression -> {
                count += 1
                expression.body?.let { count += countComplexityInExpression(it) }
            }
            is KtTryExpression -> {
                count += 1 + expression.catchClauses.size
            }
            is KtBlockExpression -> {
                expression.statements.forEach { stmt ->
                    if (stmt is KtExpression) {
                        count += countComplexityInExpression(stmt)
                    }
                }
            }
            is KtCallExpression -> {
                expression.lambdaArguments.forEach { lambda ->
                    lambda.getLambdaExpression()?.bodyExpression?.let {
                        count += countComplexityInExpression(it)
                    }
                }
            }
        }

        return count
    }
}

/**
 * Kotlin 파일 분석 결과
 */
data class KotlinFileAnalysis(
    val filePath: String,
    val packageName: String,
    val imports: List<String>,
    val classes: List<ClassAnalysis>,
    val functions: List<FunctionAnalysis>,
    val topLevelProperties: List<PropertyAnalysis>,
    val composables: List<ComposableAnalysis>,
    val hasComposeImports: Boolean,
    val complexity: Int
)

data class ClassAnalysis(
    val name: String,
    val fqName: String,
    val kind: ClassKind,
    val superTypes: List<String>,
    val properties: List<PropertyAnalysis>,
    val methods: List<FunctionAnalysis>,
    val constructorParams: List<ParameterAnalysis>,
    val annotations: List<String>,
    val isAbstract: Boolean,
    val isOpen: Boolean
)

enum class ClassKind {
    CLASS, DATA_CLASS, SEALED_CLASS, ENUM_CLASS, INTERFACE, OBJECT
}

data class PropertyAnalysis(
    val name: String,
    val type: String,
    val isVar: Boolean,
    val isMutable: Boolean,
    val hasDefaultValue: Boolean,
    val defaultValue: String?,
    val annotations: List<String>
)

data class FunctionAnalysis(
    val name: String,
    val returnType: String,
    val parameters: List<ParameterAnalysis>,
    val annotations: List<String>,
    val isComposable: Boolean,
    val isSuspend: Boolean,
    val isInline: Boolean,
    val body: String?,
    val complexity: Int
)

data class ParameterAnalysis(
    val name: String,
    val type: String,
    val hasDefaultValue: Boolean,
    val defaultValue: String?,
    val isVararg: Boolean
)

data class ComposableAnalysis(
    val name: String,
    val parameters: List<ComposableParameter>,
    val stateVariables: List<StateVariable>,
    val childComposables: List<String>,
    val modifiers: List<String>,
    val hasPreview: Boolean,
    val complexity: Int
)

data class ComposableParameter(
    val name: String,
    val type: String,
    val hasDefaultValue: Boolean,
    val defaultValue: String?,
    val isLambda: Boolean
)

data class StateVariable(
    val name: String,
    val type: String,
    val stateType: StateType,
    val initialValue: String
)

// StateType은 com.zime.app.androidtoflutter.models.analysis.StateType 사용

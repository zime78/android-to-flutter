package com.zime.app.androidtoflutter.analyzer

import com.intellij.openapi.project.Project
import com.zime.app.androidtoflutter.models.analysis.*
import org.jetbrains.kotlin.psi.*

/**
 * Compose UI 분석기 - Composable 함수에서 UI 트리 추출
 */
class ComposeAnalyzer(private val project: Project) {

    private val kotlinAnalyzer = KotlinAnalyzer(project)

    /**
     * Composable 함수에서 ComposeTree 추출
     */
    fun analyzeComposable(function: KtNamedFunction): ComposeTree {
        val rootNodes = mutableListOf<ComposeNode>()

        function.bodyExpression?.let { body ->
            extractNodesFromExpression(body, rootNodes)
        }

        return ComposeTree(
            name = function.name ?: "Unknown",
            rootNodes = rootNodes,
            parameters = extractParameters(function),
            stateVariables = extractStateVariables(function)
        )
    }

    /**
     * KtFile에서 모든 ComposeTree 추출
     */
    fun analyzeFile(ktFile: KtFile): List<ComposeTree> {
        val trees = mutableListOf<ComposeTree>()

        ktFile.declarations
            .filterIsInstance<KtNamedFunction>()
            .filter { it.annotationEntries.any { ann -> ann.text.contains("Composable") } }
            .forEach { function ->
                trees.add(analyzeComposable(function))
            }

        return trees
    }

    /**
     * 표현식에서 ComposeNode 추출
     */
    private fun extractNodesFromExpression(expression: KtExpression, nodes: MutableList<ComposeNode>) {
        when (expression) {
            is KtBlockExpression -> {
                expression.statements.forEach { stmt ->
                    if (stmt is KtExpression) {
                        extractNodesFromExpression(stmt, nodes)
                    }
                }
            }
            is KtCallExpression -> {
                val callName = expression.calleeExpression?.text ?: return

                // Compose 위젯 호출인지 확인 (대문자로 시작)
                if (callName.first().isUpperCase() || isComposeWidget(callName)) {
                    val node = extractComposeNode(expression)
                    nodes.add(node)
                } else if (isComposeScope(callName)) {
                    // remember, LaunchedEffect 등 스코프 함수 내부 탐색
                    expression.lambdaArguments.forEach { lambda ->
                        lambda.getLambdaExpression()?.bodyExpression?.let { body ->
                            extractNodesFromExpression(body, nodes)
                        }
                    }
                }
            }
            is KtDotQualifiedExpression -> {
                // 체인된 호출 처리
                expression.selectorExpression?.let { selector ->
                    extractNodesFromExpression(selector, nodes)
                }
            }
            is KtIfExpression -> {
                val condition = expression.condition?.text ?: ""
                val thenNodes = mutableListOf<ComposeNode>()
                val elseNodes = mutableListOf<ComposeNode>()

                expression.then?.let { extractNodesFromExpression(it, thenNodes) }
                expression.`else`?.let { extractNodesFromExpression(it, elseNodes) }

                if (thenNodes.isNotEmpty() || elseNodes.isNotEmpty()) {
                    nodes.add(ConditionalNode(
                        condition = condition,
                        thenBranch = thenNodes,
                        elseBranch = elseNodes
                    ))
                }
            }
            is KtWhenExpression -> {
                val subject = expression.subjectExpression?.text
                val branches = expression.entries.map { entry ->
                    val conditionText = entry.conditions.joinToString(", ") { it.text }
                    val branchNodes = mutableListOf<ComposeNode>()
                    entry.expression?.let { extractNodesFromExpression(it, branchNodes) }
                    WhenBranch(conditionText, branchNodes)
                }

                if (branches.any { it.nodes.isNotEmpty() }) {
                    nodes.add(WhenNode(subject, branches))
                }
            }
            is KtForExpression -> {
                val loopRange = expression.loopRange?.text ?: ""
                val loopParameter = expression.loopParameter?.text ?: "it"
                val bodyNodes = mutableListOf<ComposeNode>()

                expression.body?.let { extractNodesFromExpression(it, bodyNodes) }

                if (bodyNodes.isNotEmpty()) {
                    nodes.add(ForEachNode(loopParameter, loopRange, bodyNodes))
                }
            }
        }
    }

    /**
     * CallExpression에서 ComposeNode 추출
     */
    private fun extractComposeNode(callExpression: KtCallExpression): ComposeNode {
        val widgetName = callExpression.calleeExpression?.text ?: "Unknown"
        val arguments = extractArguments(callExpression)
        val modifiers = extractModifiers(callExpression)
        val children = mutableListOf<ComposeNode>()

        // 람다 인자에서 자식 노드 추출
        callExpression.lambdaArguments.forEach { lambda ->
            lambda.getLambdaExpression()?.bodyExpression?.let { body ->
                extractNodesFromExpression(body, children)
            }
        }

        // content 인자에서도 자식 추출
        callExpression.valueArguments.forEach { arg ->
            if (arg.getArgumentName()?.asName?.asString() == "content") {
                arg.getArgumentExpression()?.let { expr ->
                    if (expr is KtLambdaExpression) {
                        expr.bodyExpression?.let { body ->
                            extractNodesFromExpression(body, children)
                        }
                    }
                }
            }
        }

        return WidgetNode(
            name = widgetName,
            arguments = arguments,
            modifiers = modifiers,
            children = children,
            sourceLocation = SourceLocation(
                line = getLineNumber(callExpression),
                column = getColumnNumber(callExpression),
                length = callExpression.textLength
            )
        )
    }

    /**
     * 인자 추출
     */
    private fun extractArguments(callExpression: KtCallExpression): Map<String, ArgumentValue> {
        val args = mutableMapOf<String, ArgumentValue>()

        callExpression.valueArguments.forEachIndexed { index, arg ->
            val name = arg.getArgumentName()?.asName?.asString() ?: "arg$index"
            val expr = arg.getArgumentExpression()

            if (name != "modifier" && name != "content") {
                args[name] = extractArgumentValue(expr)
            }
        }

        return args
    }

    /**
     * 인자 값 추출
     */
    private fun extractArgumentValue(expression: KtExpression?): ArgumentValue {
        if (expression == null) return ArgumentValue.Null

        return when (expression) {
            is KtStringTemplateExpression -> ArgumentValue.StringLiteral(expression.text.trim('"'))
            is KtConstantExpression -> {
                val text = expression.text
                when {
                    text == "true" || text == "false" -> ArgumentValue.BooleanLiteral(text.toBoolean())
                    text.toIntOrNull() != null -> ArgumentValue.IntLiteral(text.toInt())
                    text.toDoubleOrNull() != null -> ArgumentValue.DoubleLiteral(text.toDouble())
                    else -> ArgumentValue.Raw(text)
                }
            }
            is KtDotQualifiedExpression -> {
                // Color.Red, Alignment.Center 등
                ArgumentValue.Reference(expression.text)
            }
            is KtCallExpression -> {
                // 함수 호출 (e.g., Color(0xFF000000))
                ArgumentValue.FunctionCall(
                    name = expression.calleeExpression?.text ?: "",
                    arguments = expression.valueArguments.map { it.getArgumentExpression()?.text ?: "" }
                )
            }
            is KtLambdaExpression -> ArgumentValue.Lambda(expression.text)
            is KtNameReferenceExpression -> ArgumentValue.Reference(expression.text)
            else -> ArgumentValue.Raw(expression.text)
        }
    }

    /**
     * Modifier 추출
     */
    private fun extractModifiers(callExpression: KtCallExpression): List<ModifierCall> {
        val modifiers = mutableListOf<ModifierCall>()

        callExpression.valueArguments.forEach { arg ->
            if (arg.getArgumentName()?.asName?.asString() == "modifier" ||
                (arg.getArgumentExpression()?.text?.contains("Modifier") == true)) {
                arg.getArgumentExpression()?.let { expr ->
                    extractModifiersFromChain(expr, modifiers)
                }
            }
        }

        return modifiers
    }

    /**
     * Modifier 체인에서 개별 Modifier 추출
     */
    private fun extractModifiersFromChain(expression: KtExpression, modifiers: MutableList<ModifierCall>) {
        when (expression) {
            is KtDotQualifiedExpression -> {
                // 재귀적으로 체인 탐색
                extractModifiersFromChain(expression.receiverExpression, modifiers)
                expression.selectorExpression?.let { selector ->
                    if (selector is KtCallExpression) {
                        val name = selector.calleeExpression?.text ?: ""
                        val args = selector.valueArguments.map { arg ->
                            arg.getArgumentName()?.asName?.asString() to (arg.getArgumentExpression()?.text ?: "")
                        }.toMap()
                        modifiers.add(ModifierCall(name, args))
                    }
                }
            }
            is KtCallExpression -> {
                val name = expression.calleeExpression?.text ?: ""
                if (name != "Modifier") {
                    val args = expression.valueArguments.map { arg ->
                        arg.getArgumentName()?.asName?.asString() to (arg.getArgumentExpression()?.text ?: "")
                    }.toMap()
                    modifiers.add(ModifierCall(name, args))
                }
            }
        }
    }

    /**
     * 파라미터 추출
     */
    private fun extractParameters(function: KtNamedFunction): List<ComposeParameter> {
        return function.valueParameters.map { param ->
            ComposeParameter(
                name = param.name ?: "",
                type = param.typeReference?.text ?: "Any",
                defaultValue = param.defaultValue?.text,
                isRequired = !param.hasDefaultValue()
            )
        }
    }

    /**
     * State 변수 추출
     */
    private fun extractStateVariables(function: KtNamedFunction): List<ComposeState> {
        val states = mutableListOf<ComposeState>()

        function.bodyExpression?.let { body ->
            if (body is KtBlockExpression) {
                body.statements.filterIsInstance<KtProperty>().forEach { prop ->
                    val initializer = prop.initializer?.text ?: ""
                    val stateType = detectStateType(initializer)

                    if (stateType != null) {
                        states.add(ComposeState(
                            name = prop.name ?: "",
                            type = prop.typeReference?.text ?: inferType(initializer),
                            stateType = stateType,
                            initialValue = extractInitialValue(initializer)
                        ))
                    }
                }
            }
        }

        return states
    }

    private fun detectStateType(initializer: String): ComposeStateType? {
        return when {
            initializer.contains("rememberSaveable") -> ComposeStateType.REMEMBER_SAVEABLE
            initializer.contains("remember") && initializer.contains("mutableStateOf") -> ComposeStateType.REMEMBER_MUTABLE_STATE
            initializer.contains("remember") && initializer.contains("mutableStateListOf") -> ComposeStateType.REMEMBER_MUTABLE_LIST
            initializer.contains("remember") && initializer.contains("mutableStateMapOf") -> ComposeStateType.REMEMBER_MUTABLE_MAP
            initializer.contains("remember") -> ComposeStateType.REMEMBER
            initializer.contains("mutableStateOf") -> ComposeStateType.MUTABLE_STATE
            initializer.contains("derivedStateOf") -> ComposeStateType.DERIVED_STATE
            initializer.contains("collectAsState") -> ComposeStateType.COLLECT_AS_STATE
            initializer.contains("produceState") -> ComposeStateType.PRODUCE_STATE
            else -> null
        }
    }

    private fun inferType(initializer: String): String {
        return when {
            initializer.contains("mutableStateOf(true)") || initializer.contains("mutableStateOf(false)") -> "Boolean"
            initializer.contains("mutableStateOf(0)") -> "Int"
            initializer.contains("mutableStateOf(\"\")") -> "String"
            initializer.contains("mutableStateListOf") -> "SnapshotStateList"
            initializer.contains("mutableStateMapOf") -> "SnapshotStateMap"
            else -> "Any"
        }
    }

    private fun extractInitialValue(initializer: String): String {
        // mutableStateOf(value) 에서 value 추출
        val regex = Regex("""mutableStateOf\((.*?)\)""")
        return regex.find(initializer)?.groupValues?.getOrNull(1) ?: initializer
    }

    private fun isComposeWidget(name: String): Boolean {
        return name in COMPOSE_WIDGETS
    }

    private fun isComposeScope(name: String): Boolean {
        return name in COMPOSE_SCOPES
    }

    private fun getLineNumber(element: KtElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        return document.getLineNumber(element.textOffset) + 1
    }

    private fun getColumnNumber(element: KtElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        val lineStart = document.getLineStartOffset(document.getLineNumber(element.textOffset))
        return element.textOffset - lineStart + 1
    }

    companion object {
        private val COMPOSE_WIDGETS = setOf(
            // Layout
            "Column", "Row", "Box", "ConstraintLayout", "LazyColumn", "LazyRow",
            "LazyVerticalGrid", "LazyHorizontalGrid", "FlowRow", "FlowColumn",
            // Basic
            "Text", "Image", "Icon", "Spacer", "Divider", "Surface",
            // Input
            "Button", "IconButton", "TextButton", "OutlinedButton", "FloatingActionButton",
            "TextField", "OutlinedTextField", "BasicTextField",
            "Checkbox", "RadioButton", "Switch", "Slider",
            // Container
            "Card", "Scaffold", "TopAppBar", "BottomAppBar", "NavigationBar",
            "ModalBottomSheet", "AlertDialog", "Dialog",
            // Other
            "CircularProgressIndicator", "LinearProgressIndicator",
            "DropdownMenu", "DropdownMenuItem", "ExposedDropdownMenuBox",
            "TabRow", "Tab", "Pager", "HorizontalPager", "VerticalPager"
        )

        private val COMPOSE_SCOPES = setOf(
            "remember", "rememberSaveable", "rememberCoroutineScope",
            "LaunchedEffect", "SideEffect", "DisposableEffect",
            "derivedStateOf", "produceState", "snapshotFlow",
            "key", "CompositionLocalProvider", "AnimatedVisibility",
            "AnimatedContent", "Crossfade"
        )
    }
}

/**
 * Compose UI 트리
 */
data class ComposeTree(
    val name: String,
    val rootNodes: List<ComposeNode>,
    val parameters: List<ComposeParameter>,
    val stateVariables: List<ComposeState>
)

/**
 * Compose 노드 (sealed class)
 */
sealed class ComposeNode {
    abstract val sourceLocation: SourceLocation?
}

data class WidgetNode(
    val name: String,
    val arguments: Map<String, ArgumentValue>,
    val modifiers: List<ModifierCall>,
    val children: List<ComposeNode>,
    override val sourceLocation: SourceLocation?
) : ComposeNode()

data class ConditionalNode(
    val condition: String,
    val thenBranch: List<ComposeNode>,
    val elseBranch: List<ComposeNode>,
    override val sourceLocation: SourceLocation? = null
) : ComposeNode()

data class WhenNode(
    val subject: String?,
    val branches: List<WhenBranch>,
    override val sourceLocation: SourceLocation? = null
) : ComposeNode()

data class WhenBranch(
    val condition: String,
    val nodes: List<ComposeNode>
)

data class ForEachNode(
    val variable: String,
    val iterable: String,
    val children: List<ComposeNode>,
    override val sourceLocation: SourceLocation? = null
) : ComposeNode()

/**
 * 인자 값 타입
 */
sealed class ArgumentValue {
    data class StringLiteral(val value: String) : ArgumentValue()
    data class IntLiteral(val value: Int) : ArgumentValue()
    data class DoubleLiteral(val value: Double) : ArgumentValue()
    data class BooleanLiteral(val value: Boolean) : ArgumentValue()
    data class Reference(val name: String) : ArgumentValue()
    data class FunctionCall(val name: String, val arguments: List<String>) : ArgumentValue()
    data class Lambda(val code: String) : ArgumentValue()
    data class Raw(val text: String) : ArgumentValue()
    object Null : ArgumentValue()
}

data class ModifierCall(
    val name: String,
    val arguments: Map<String?, String>
)

data class ComposeParameter(
    val name: String,
    val type: String,
    val defaultValue: String?,
    val isRequired: Boolean
)

data class ComposeState(
    val name: String,
    val type: String,
    val stateType: ComposeStateType,
    val initialValue: String
)

enum class ComposeStateType {
    MUTABLE_STATE,
    REMEMBER,
    REMEMBER_MUTABLE_STATE,
    REMEMBER_SAVEABLE,
    REMEMBER_MUTABLE_LIST,
    REMEMBER_MUTABLE_MAP,
    DERIVED_STATE,
    COLLECT_AS_STATE,
    PRODUCE_STATE
}

data class SourceLocation(
    val line: Int,
    val column: Int,
    val length: Int
)

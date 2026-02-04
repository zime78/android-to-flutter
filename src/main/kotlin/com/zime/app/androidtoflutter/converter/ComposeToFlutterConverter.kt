package com.zime.app.androidtoflutter.converter

import com.zime.app.androidtoflutter.analyzer.*
import com.zime.app.androidtoflutter.mappings.ModifierMappings
import com.zime.app.androidtoflutter.mappings.TypeMappings
import com.zime.app.androidtoflutter.mappings.WidgetMappings

/**
 * Compose -> Flutter 위젯 변환기
 * ComposeTree를 Flutter 위젯 코드로 변환
 */
class ComposeToFlutterConverter {

    /**
     * ComposeTree를 Flutter 위젯 코드로 변환
     */
    fun convert(tree: ComposeTree): FlutterWidgetCode {
        val imports = mutableSetOf<String>()
        imports.add("import 'package:flutter/material.dart';")

        val stateCode = generateStateCode(tree.stateVariables)
        val buildMethod = generateBuildMethod(tree.rootNodes, imports)

        // StatelessWidget vs StatefulWidget 결정
        val isStateful = tree.stateVariables.isNotEmpty()

        val widgetCode = if (isStateful) {
            generateStatefulWidget(tree.name, tree.parameters, stateCode, buildMethod)
        } else {
            generateStatelessWidget(tree.name, tree.parameters, buildMethod)
        }

        return FlutterWidgetCode(
            widgetName = tree.name,
            imports = imports.toList(),
            code = widgetCode,
            isStateful = isStateful
        )
    }

    /**
     * StatelessWidget 생성
     */
    private fun generateStatelessWidget(
        name: String,
        parameters: List<ComposeParameter>,
        buildMethod: String
    ): String {
        val params = parameters.filter { it.name != "modifier" }
        val constructorParams = generateConstructorParams(params)
        val fields = generateFields(params)

        return """
class $name extends StatelessWidget {
$fields
  const $name({
    super.key,
$constructorParams
  });

  @override
  Widget build(BuildContext context) {
    return $buildMethod;
  }
}
""".trimIndent()
    }

    /**
     * StatefulWidget 생성
     */
    private fun generateStatefulWidget(
        name: String,
        parameters: List<ComposeParameter>,
        stateCode: String,
        buildMethod: String
    ): String {
        val params = parameters.filter { it.name != "modifier" }
        val constructorParams = generateConstructorParams(params)
        val fields = generateFields(params)

        return """
class $name extends StatefulWidget {
$fields
  const $name({
    super.key,
$constructorParams
  });

  @override
  State<$name> createState() => _${name}State();
}

class _${name}State extends State<$name> {
$stateCode

  @override
  Widget build(BuildContext context) {
    return $buildMethod;
  }
}
""".trimIndent()
    }

    /**
     * 생성자 파라미터 생성
     */
    private fun generateConstructorParams(params: List<ComposeParameter>): String {
        if (params.isEmpty()) return ""

        return params.joinToString(",\n") { param ->
            val dartType = TypeMappings.toDartType(param.type)
            val required = if (param.isRequired && param.defaultValue == null) "required " else ""
            val defaultVal = param.defaultValue?.let { " = ${convertDefaultValue(it)}" } ?: ""
            "    ${required}this.${param.name}$defaultVal"
        }
    }

    /**
     * 필드 생성
     */
    private fun generateFields(params: List<ComposeParameter>): String {
        if (params.isEmpty()) return ""

        return params.joinToString("\n") { param ->
            val dartType = TypeMappings.toDartType(param.type)
            val nullable = if (!param.isRequired && param.defaultValue == null) "?" else ""
            "  final $dartType$nullable ${param.name};"
        }
    }

    /**
     * State 코드 생성
     */
    private fun generateStateCode(stateVariables: List<ComposeState>): String {
        if (stateVariables.isEmpty()) return ""

        return stateVariables.joinToString("\n") { state ->
            val dartType = TypeMappings.toDartType(state.type)
            val initialValue = convertInitialValue(state.initialValue, state.type)
            "  $dartType ${state.name} = $initialValue;"
        }
    }

    /**
     * build 메서드 본문 생성
     */
    private fun generateBuildMethod(nodes: List<ComposeNode>, imports: MutableSet<String>): String {
        if (nodes.isEmpty()) return "const SizedBox.shrink()"

        if (nodes.size == 1) {
            return convertNode(nodes[0], imports, 0)
        }

        // 여러 노드면 Column으로 감싸기
        val children = nodes.joinToString(",\n") { node ->
            convertNode(node, imports, 2)
        }
        return """Column(
      children: [
$children
      ],
    )"""
    }

    /**
     * ComposeNode를 Flutter 위젯으로 변환
     */
    private fun convertNode(node: ComposeNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)

        return when (node) {
            is WidgetNode -> convertWidgetNode(node, imports, indent)
            is ConditionalNode -> convertConditionalNode(node, imports, indent)
            is WhenNode -> convertWhenNode(node, imports, indent)
            is ForEachNode -> convertForEachNode(node, imports, indent)
        }
    }

    /**
     * WidgetNode 변환
     */
    private fun convertWidgetNode(node: WidgetNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val flutterWidget = WidgetMappings.toFlutterWidget(node.name)

        // 특수 위젯 처리
        return when (node.name) {
            "Text" -> convertTextWidget(node, prefix)
            "Button", "TextButton", "OutlinedButton", "IconButton" -> convertButtonWidget(node, imports, indent)
            "TextField", "OutlinedTextField" -> convertTextFieldWidget(node, prefix)
            "Image" -> convertImageWidget(node, imports, prefix)
            "Icon" -> convertIconWidget(node, prefix)
            "Column", "Row", "Box" -> convertLayoutWidget(node, imports, indent)
            "LazyColumn", "LazyRow" -> convertLazyWidget(node, imports, indent)
            "Card" -> convertCardWidget(node, imports, indent)
            "Scaffold" -> convertScaffoldWidget(node, imports, indent)
            "Spacer" -> "${prefix}const Spacer()"
            "Divider" -> "${prefix}const Divider()"
            else -> convertGenericWidget(node, imports, indent)
        }
    }

    /**
     * Text 위젯 변환
     */
    private fun convertTextWidget(node: WidgetNode, prefix: String): String {
        val textArg = node.arguments["text"] ?: node.arguments["arg0"]
        val text = when (textArg) {
            is ArgumentValue.StringLiteral -> "'${textArg.value}'"
            is ArgumentValue.Reference -> textArg.name
            is ArgumentValue.Raw -> textArg.text
            else -> "''"
        }

        val styleArgs = mutableListOf<String>()

        node.arguments["fontSize"]?.let { arg ->
            styleArgs.add("fontSize: ${convertArgumentValue(arg)}")
        }
        node.arguments["fontWeight"]?.let { arg ->
            styleArgs.add("fontWeight: ${convertFontWeight(arg)}")
        }
        node.arguments["color"]?.let { arg ->
            styleArgs.add("color: ${convertColor(arg)}")
        }

        val style = if (styleArgs.isNotEmpty()) {
            ",\n${prefix}  style: TextStyle(\n${prefix}    ${styleArgs.joinToString(",\n${prefix}    ")},\n${prefix}  )"
        } else ""

        return "${prefix}Text(\n${prefix}  $text$style,\n${prefix})"
    }

    /**
     * Button 위젯 변환
     */
    private fun convertButtonWidget(node: WidgetNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val buttonType = when (node.name) {
            "TextButton" -> "TextButton"
            "OutlinedButton" -> "OutlinedButton"
            "IconButton" -> "IconButton"
            else -> "ElevatedButton"
        }

        val onClick = node.arguments["onClick"]?.let {
            when (it) {
                is ArgumentValue.Lambda -> convertLambdaToCallback(it.code)
                is ArgumentValue.Reference -> it.name
                else -> "() {}"
            }
        } ?: "() {}"

        val childContent = if (node.children.isNotEmpty()) {
            convertNode(node.children[0], imports, indent + 1)
        } else {
            node.arguments["arg0"]?.let {
                "${prefix}  Text(${convertArgumentValue(it)})"
            } ?: "${prefix}  const Text('Button')"
        }

        return """${prefix}$buttonType(
${prefix}  onPressed: $onClick,
${prefix}  child: $childContent,
${prefix})"""
    }

    /**
     * TextField 위젯 변환
     */
    private fun convertTextFieldWidget(node: WidgetNode, prefix: String): String {
        val args = mutableListOf<String>()

        node.arguments["value"]?.let { arg ->
            // controller 사용 권장이지만 간단한 변환
            args.add("controller: TextEditingController(text: ${convertArgumentValue(arg)})")
        }

        node.arguments["onValueChange"]?.let { arg ->
            args.add("onChanged: ${convertLambdaToCallback((arg as? ArgumentValue.Lambda)?.code ?: "")}")
        }

        node.arguments["label"]?.let { arg ->
            args.add("decoration: InputDecoration(labelText: ${convertArgumentValue(arg)})")
        }

        node.arguments["placeholder"]?.let { arg ->
            args.add("decoration: InputDecoration(hintText: ${convertArgumentValue(arg)})")
        }

        val isOutlined = node.name == "OutlinedTextField"
        if (isOutlined && !args.any { it.contains("decoration") }) {
            args.add("decoration: const InputDecoration(border: OutlineInputBorder())")
        }

        return """${prefix}TextField(
${prefix}  ${args.joinToString(",\n${prefix}  ")},
${prefix})"""
    }

    /**
     * Image 위젯 변환
     */
    private fun convertImageWidget(node: WidgetNode, imports: MutableSet<String>, prefix: String): String {
        val painter = node.arguments["painter"] ?: node.arguments["arg0"]

        return when {
            painter?.toString()?.contains("painterResource") == true -> {
                val resourceId = extractResourceId(painter.toString())
                "${prefix}Image.asset('assets/images/$resourceId')"
            }
            painter?.toString()?.contains("rememberAsyncImagePainter") == true -> {
                val url = extractUrl(painter.toString())
                imports.add("import 'package:cached_network_image/cached_network_image.dart';")
                "${prefix}CachedNetworkImage(imageUrl: $url)"
            }
            else -> "${prefix}const Placeholder()"
        }
    }

    /**
     * Icon 위젯 변환
     */
    private fun convertIconWidget(node: WidgetNode, prefix: String): String {
        val icon = node.arguments["imageVector"] ?: node.arguments["arg0"]
        val iconName = convertIconName(icon)
        return "${prefix}Icon($iconName)"
    }

    /**
     * Layout 위젯 변환 (Column, Row, Box)
     */
    private fun convertLayoutWidget(node: WidgetNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val flutterWidget = when (node.name) {
            "Column" -> "Column"
            "Row" -> "Row"
            "Box" -> "Stack"
            else -> "Column"
        }

        val args = mutableListOf<String>()

        // Arrangement -> MainAxisAlignment
        node.arguments["verticalArrangement"]?.let { arg ->
            args.add("mainAxisAlignment: ${convertArrangement(arg)}")
        }
        node.arguments["horizontalArrangement"]?.let { arg ->
            if (node.name == "Row") {
                args.add("mainAxisAlignment: ${convertArrangement(arg)}")
            } else {
                args.add("crossAxisAlignment: ${convertArrangementToCross(arg)}")
            }
        }

        // Alignment -> CrossAxisAlignment
        node.arguments["horizontalAlignment"]?.let { arg ->
            if (node.name == "Column") {
                args.add("crossAxisAlignment: ${convertAlignment(arg)}")
            }
        }
        node.arguments["verticalAlignment"]?.let { arg ->
            if (node.name == "Row") {
                args.add("crossAxisAlignment: ${convertAlignment(arg)}")
            }
        }

        // Children
        val children = if (node.children.isNotEmpty()) {
            val childrenCode = node.children.joinToString(",\n") { child ->
                convertNode(child, imports, indent + 2)
            }
            args.add("children: [\n$childrenCode,\n${prefix}  ]")
        } else {
            args.add("children: const []")
        }

        // Modifier -> 감싸는 위젯들
        val modifierWrappers = convertModifiers(node.modifiers, indent)

        val widgetCode = """${prefix}$flutterWidget(
${prefix}  ${args.joinToString(",\n${prefix}  ")},
${prefix})"""

        return applyModifierWrappers(widgetCode, modifierWrappers, prefix)
    }

    /**
     * LazyColumn/LazyRow -> ListView 변환
     */
    private fun convertLazyWidget(node: WidgetNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val direction = if (node.name == "LazyRow") "scrollDirection: Axis.horizontal," else ""

        // items 패턴 찾기
        val itemsContent = node.children.firstOrNull()

        return """${prefix}ListView.builder(
${prefix}  $direction
${prefix}  itemBuilder: (context, index) {
${prefix}    return ${itemsContent?.let { convertNode(it, imports, indent + 2) } ?: "const SizedBox()"};
${prefix}  },
${prefix})"""
    }

    /**
     * Card 위젯 변환
     */
    private fun convertCardWidget(node: WidgetNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val args = mutableListOf<String>()

        node.arguments["elevation"]?.let { arg ->
            args.add("elevation: ${convertArgumentValue(arg)}")
        }

        val child = if (node.children.isNotEmpty()) {
            "child: ${convertNode(node.children[0], imports, indent + 1)}"
        } else {
            "child: const SizedBox()"
        }
        args.add(child)

        return """${prefix}Card(
${prefix}  ${args.joinToString(",\n${prefix}  ")},
${prefix})"""
    }

    /**
     * Scaffold 위젯 변환
     */
    private fun convertScaffoldWidget(node: WidgetNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val args = mutableListOf<String>()

        node.arguments["topBar"]?.let { arg ->
            args.add("appBar: AppBar(title: const Text('Title'))")
        }

        node.arguments["bottomBar"]?.let { arg ->
            args.add("bottomNavigationBar: BottomNavigationBar(items: const [])")
        }

        node.arguments["floatingActionButton"]?.let { arg ->
            args.add("floatingActionButton: FloatingActionButton(onPressed: () {}, child: const Icon(Icons.add))")
        }

        val content = if (node.children.isNotEmpty()) {
            "body: ${convertNode(node.children[0], imports, indent + 1)}"
        } else {
            "body: const Center(child: Text('Content'))"
        }
        args.add(content)

        return """${prefix}Scaffold(
${prefix}  ${args.joinToString(",\n${prefix}  ")},
${prefix})"""
    }

    /**
     * 일반 위젯 변환
     */
    private fun convertGenericWidget(node: WidgetNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val flutterWidget = WidgetMappings.toFlutterWidget(node.name)

        val args = node.arguments.entries
            .filter { it.key != "modifier" && it.key != "content" }
            .map { (key, value) ->
                "${convertParameterName(key)}: ${convertArgumentValue(value)}"
            }

        val childArg = if (node.children.size == 1) {
            "child: ${convertNode(node.children[0], imports, indent + 1)}"
        } else if (node.children.size > 1) {
            val childrenCode = node.children.joinToString(",\n") { child ->
                convertNode(child, imports, indent + 2)
            }
            "children: [\n$childrenCode,\n${prefix}  ]"
        } else null

        val allArgs = if (childArg != null) args + childArg else args

        return if (allArgs.isEmpty()) {
            "${prefix}const $flutterWidget()"
        } else {
            """${prefix}$flutterWidget(
${prefix}  ${allArgs.joinToString(",\n${prefix}  ")},
${prefix})"""
        }
    }

    /**
     * Conditional 노드 변환
     */
    private fun convertConditionalNode(node: ConditionalNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)
        val condition = convertCondition(node.condition)

        val thenCode = if (node.thenBranch.size == 1) {
            convertNode(node.thenBranch[0], imports, 0)
        } else {
            val children = node.thenBranch.joinToString(",\n") { convertNode(it, imports, 1) }
            "Column(children: [$children])"
        }

        val elseCode = if (node.elseBranch.isEmpty()) {
            "const SizedBox.shrink()"
        } else if (node.elseBranch.size == 1) {
            convertNode(node.elseBranch[0], imports, 0)
        } else {
            val children = node.elseBranch.joinToString(",\n") { convertNode(it, imports, 1) }
            "Column(children: [$children])"
        }

        return """${prefix}$condition
${prefix}  ? $thenCode
${prefix}  : $elseCode"""
    }

    /**
     * When 노드 변환
     */
    private fun convertWhenNode(node: WhenNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)

        // switch expression 또는 if-else 체인으로 변환
        val branches = node.branches.mapIndexed { index, branch ->
            val condition = if (branch.condition == "else" || branch.condition.isEmpty()) {
                "true"
            } else if (node.subject != null) {
                "${node.subject} == ${branch.condition}"
            } else {
                branch.condition
            }

            val content = if (branch.nodes.size == 1) {
                convertNode(branch.nodes[0], imports, 0)
            } else {
                val children = branch.nodes.joinToString(",\n") { convertNode(it, imports, 1) }
                "Column(children: [$children])"
            }

            if (index == 0) {
                "$condition ? $content"
            } else {
                ": $condition ? $content"
            }
        }

        return """${prefix}${branches.joinToString("\n$prefix")}
${prefix}: const SizedBox.shrink()"""
    }

    /**
     * ForEach 노드 변환
     */
    private fun convertForEachNode(node: ForEachNode, imports: MutableSet<String>, indent: Int): String {
        val prefix = "  ".repeat(indent)

        val childCode = if (node.children.size == 1) {
            convertNode(node.children[0], imports, indent + 2)
        } else {
            val children = node.children.joinToString(",\n") { convertNode(it, imports, indent + 3) }
            """${prefix}    Column(children: [$children])"""
        }

        return """${prefix}...${node.iterable}.map((${node.variable}) =>
$childCode
${prefix})"""
    }

    /**
     * Modifier를 감싸는 위젯들로 변환
     */
    private fun convertModifiers(modifiers: List<ModifierCall>, indent: Int): List<ModifierWrapper> {
        return modifiers.mapNotNull { modifier ->
            when (modifier.name) {
                "padding" -> {
                    val value = modifier.arguments.values.firstOrNull() ?: "8.0"
                    ModifierWrapper("Padding", "padding: EdgeInsets.all($value)")
                }
                "fillMaxWidth" -> ModifierWrapper("SizedBox", "width: double.infinity")
                "fillMaxHeight" -> ModifierWrapper("SizedBox", "height: double.infinity")
                "fillMaxSize" -> ModifierWrapper("SizedBox", "width: double.infinity, height: double.infinity")
                "width" -> {
                    val value = modifier.arguments.values.firstOrNull() ?: "100"
                    ModifierWrapper("SizedBox", "width: $value")
                }
                "height" -> {
                    val value = modifier.arguments.values.firstOrNull() ?: "100"
                    ModifierWrapper("SizedBox", "height: $value")
                }
                "size" -> {
                    val value = modifier.arguments.values.firstOrNull() ?: "100"
                    ModifierWrapper("SizedBox", "width: $value, height: $value")
                }
                "background" -> {
                    val color = modifier.arguments.values.firstOrNull() ?: "Colors.transparent"
                    ModifierWrapper("ColoredBox", "color: $color")
                }
                "clickable" -> {
                    val onClick = modifier.arguments["onClick"] ?: "{}"
                    ModifierWrapper("GestureDetector", "onTap: () $onClick")
                }
                "clip" -> ModifierWrapper("ClipRRect", "borderRadius: BorderRadius.circular(8)")
                else -> null
            }
        }
    }

    /**
     * Modifier 래퍼 적용
     */
    private fun applyModifierWrappers(code: String, wrappers: List<ModifierWrapper>, prefix: String): String {
        var result = code
        wrappers.reversed().forEach { wrapper ->
            result = """${prefix}${wrapper.widget}(
${prefix}  ${wrapper.params},
${prefix}  child: $result,
${prefix})"""
        }
        return result
    }

    // ===== 유틸리티 메서드들 =====

    private fun convertArgumentValue(value: ArgumentValue?): String {
        return when (value) {
            is ArgumentValue.StringLiteral -> "'${value.value}'"
            is ArgumentValue.IntLiteral -> value.value.toString()
            is ArgumentValue.DoubleLiteral -> value.value.toString()
            is ArgumentValue.BooleanLiteral -> value.value.toString()
            is ArgumentValue.Reference -> convertReference(value.name)
            is ArgumentValue.FunctionCall -> "${value.name}(${value.arguments.joinToString(", ")})"
            is ArgumentValue.Lambda -> "() ${value.code}"
            is ArgumentValue.Raw -> value.text
            ArgumentValue.Null, null -> "null"
        }
    }

    private fun convertReference(name: String): String {
        // Compose 상수를 Flutter로 변환
        return when {
            name.startsWith("Color.") -> "Colors.${name.substringAfter("Color.").lowercase()}"
            name.startsWith("Alignment.") -> "Alignment.${convertAlignmentName(name.substringAfter("Alignment."))}"
            name.contains(".dp") -> name.replace(".dp", "")
            name.contains(".sp") -> name.replace(".sp", "")
            else -> name
        }
    }

    private fun convertAlignmentName(name: String): String {
        return when (name) {
            "TopStart" -> "topLeft"
            "TopCenter" -> "topCenter"
            "TopEnd" -> "topRight"
            "CenterStart" -> "centerLeft"
            "Center" -> "center"
            "CenterEnd" -> "centerRight"
            "BottomStart" -> "bottomLeft"
            "BottomCenter" -> "bottomCenter"
            "BottomEnd" -> "bottomRight"
            else -> "center"
        }
    }

    private fun convertDefaultValue(value: String): String {
        return when {
            value == "null" -> "null"
            value.startsWith("\"") -> value.replace("\"", "'")
            value.contains(".dp") -> value.replace(".dp", "")
            else -> value
        }
    }

    private fun convertInitialValue(value: String, type: String): String {
        return when {
            value.isEmpty() -> getDefaultForType(type)
            value.contains("mutableStateOf(") -> {
                val inner = value.substringAfter("mutableStateOf(").substringBeforeLast(")")
                convertDefaultValue(inner)
            }
            else -> convertDefaultValue(value)
        }
    }

    private fun getDefaultForType(type: String): String {
        return when (type.lowercase()) {
            "boolean", "bool" -> "false"
            "int" -> "0"
            "double", "float" -> "0.0"
            "string" -> "''"
            "list" -> "[]"
            else -> "null"
        }
    }

    private fun convertParameterName(name: String): String {
        // Compose 파라미터명을 Flutter로 변환
        return when (name) {
            "onClick" -> "onPressed"
            "onValueChange" -> "onChanged"
            "contentDescription" -> "semanticsLabel"
            else -> name
        }
    }

    private fun convertCondition(condition: String): String {
        return condition
            .replace("==", "==")
            .replace("!=", "!=")
            .replace("&&", "&&")
            .replace("||", "||")
    }

    private fun convertLambdaToCallback(lambda: String): String {
        // 간단한 람다 변환
        if (lambda.isBlank()) return "() {}"
        return if (lambda.contains("->")) {
            val body = lambda.substringAfter("->").trim()
            "() { $body; }"
        } else {
            "() { $lambda }"
        }
    }

    private fun convertFontWeight(arg: ArgumentValue?): String {
        val ref = (arg as? ArgumentValue.Reference)?.name ?: return "FontWeight.normal"
        return when {
            ref.contains("Bold") -> "FontWeight.bold"
            ref.contains("Light") -> "FontWeight.w300"
            ref.contains("Medium") -> "FontWeight.w500"
            ref.contains("SemiBold") -> "FontWeight.w600"
            ref.contains("Thin") -> "FontWeight.w100"
            else -> "FontWeight.normal"
        }
    }

    private fun convertColor(arg: ArgumentValue?): String {
        return when (arg) {
            is ArgumentValue.Reference -> {
                when {
                    arg.name.contains("Color.") -> "Colors.${arg.name.substringAfter("Color.").lowercase()}"
                    arg.name.contains("MaterialTheme") -> "Theme.of(context).colorScheme.primary"
                    else -> "Colors.black"
                }
            }
            is ArgumentValue.FunctionCall -> {
                if (arg.name == "Color") {
                    val hexValue = arg.arguments.firstOrNull() ?: "0xFF000000"
                    "Color($hexValue)"
                } else "Colors.black"
            }
            else -> "Colors.black"
        }
    }

    private fun convertArrangement(arg: ArgumentValue?): String {
        val ref = (arg as? ArgumentValue.Reference)?.name ?: return "MainAxisAlignment.start"
        return when {
            ref.contains("SpaceBetween") -> "MainAxisAlignment.spaceBetween"
            ref.contains("SpaceAround") -> "MainAxisAlignment.spaceAround"
            ref.contains("SpaceEvenly") -> "MainAxisAlignment.spaceEvenly"
            ref.contains("Center") -> "MainAxisAlignment.center"
            ref.contains("End") || ref.contains("Bottom") -> "MainAxisAlignment.end"
            else -> "MainAxisAlignment.start"
        }
    }

    private fun convertArrangementToCross(arg: ArgumentValue?): String {
        val ref = (arg as? ArgumentValue.Reference)?.name ?: return "CrossAxisAlignment.start"
        return when {
            ref.contains("Center") -> "CrossAxisAlignment.center"
            ref.contains("End") -> "CrossAxisAlignment.end"
            ref.contains("Stretch") -> "CrossAxisAlignment.stretch"
            else -> "CrossAxisAlignment.start"
        }
    }

    private fun convertAlignment(arg: ArgumentValue?): String {
        val ref = (arg as? ArgumentValue.Reference)?.name ?: return "CrossAxisAlignment.start"
        return when {
            ref.contains("CenterHorizontally") || ref.contains("CenterVertically") -> "CrossAxisAlignment.center"
            ref.contains("End") -> "CrossAxisAlignment.end"
            else -> "CrossAxisAlignment.start"
        }
    }

    private fun convertIconName(arg: ArgumentValue?): String {
        val ref = (arg as? ArgumentValue.Reference)?.name ?: return "Icons.help"
        return when {
            ref.contains("Icons.Default.") -> "Icons.${ref.substringAfter("Icons.Default.").lowercase()}"
            ref.contains("Icons.Filled.") -> "Icons.${ref.substringAfter("Icons.Filled.").lowercase()}"
            ref.contains("Icons.Outlined.") -> "Icons.${ref.substringAfter("Icons.Outlined.").lowercase()}_outlined"
            else -> "Icons.help"
        }
    }

    private fun extractResourceId(painter: String): String {
        val regex = Regex("""R\.drawable\.(\w+)""")
        return regex.find(painter)?.groupValues?.getOrNull(1) ?: "placeholder"
    }

    private fun extractUrl(painter: String): String {
        val regex = Regex(""""([^"]+)"""")
        return regex.find(painter)?.groupValues?.getOrNull(1)?.let { "'$it'" } ?: "''"
    }
}

/**
 * Flutter 위젯 코드 결과
 */
data class FlutterWidgetCode(
    val widgetName: String,
    val imports: List<String>,
    val code: String,
    val isStateful: Boolean
)

data class ModifierWrapper(
    val widget: String,
    val params: String
)

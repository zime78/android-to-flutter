package com.zime.app.androidtoflutter.converter

import com.zime.app.androidtoflutter.analyzer.*
import com.zime.app.androidtoflutter.mappings.TypeMappings

/**
 * Kotlin -> Dart 코드 변환기
 * 비-Composable Kotlin 코드를 Dart로 변환
 */
class KotlinToDartConverter {

    /**
     * Kotlin 파일 분석 결과를 Dart 코드로 변환
     */
    fun convert(analysis: KotlinFileAnalysis): DartFileCode {
        val imports = mutableSetOf<String>()
        val classes = analysis.classes.map { convertClass(it, imports) }
        val functions = analysis.functions
            .filter { !it.isComposable }
            .map { convertFunction(it, imports) }
        val properties = analysis.topLevelProperties.map { convertProperty(it) }

        return DartFileCode(
            fileName = analysis.filePath.substringAfterLast("/").replace(".kt", ".dart"),
            imports = imports.toList(),
            classes = classes,
            functions = functions,
            topLevelProperties = properties
        )
    }

    /**
     * 클래스 변환
     */
    private fun convertClass(cls: ClassAnalysis, imports: MutableSet<String>): DartClass {
        return when (cls.kind) {
            ClassKind.DATA_CLASS -> convertDataClass(cls)
            ClassKind.SEALED_CLASS -> convertSealedClass(cls, imports)
            ClassKind.ENUM_CLASS -> convertEnumClass(cls)
            ClassKind.INTERFACE -> convertInterface(cls)
            else -> convertRegularClass(cls, imports)
        }
    }

    /**
     * Data Class -> Dart Class with copyWith
     */
    private fun convertDataClass(cls: ClassAnalysis): DartClass {
        val fields = cls.constructorParams.map { param ->
            DartField(
                name = param.name,
                type = TypeMappings.toDartType(param.type),
                isFinal = true,
                isNullable = param.type.endsWith("?"),
                defaultValue = param.defaultValue?.let { convertDefaultValue(it) }
            )
        }

        val constructorParams = fields.map { field ->
            val required = if (!field.isNullable && field.defaultValue == null) "required " else ""
            "${required}this.${field.name}"
        }

        val copyWithParams = fields.map { field ->
            "${field.type}${if (field.isNullable) "" else "?"} ${field.name}"
        }

        val copyWithBody = fields.map { field ->
            "${field.name}: ${field.name} ?? this.${field.name}"
        }

        val toStringBody = fields.joinToString(", ") { "${it.name}: \$${it.name}" }

        val equalsBody = fields.joinToString(" && ") { "other.${it.name} == ${it.name}" }

        val hashCodeBody = fields.joinToString(" ^ ") { "${it.name}.hashCode" }

        val code = """
class ${cls.name} {
${fields.joinToString("\n") { "  final ${it.type}${if (it.isNullable) "?" else ""} ${it.name};" }}

  const ${cls.name}({
    ${constructorParams.joinToString(",\n    ")},
  });

  ${cls.name} copyWith({
    ${copyWithParams.joinToString(",\n    ")},
  }) {
    return ${cls.name}(
      ${copyWithBody.joinToString(",\n      ")},
    );
  }

  @override
  String toString() => '${cls.name}($toStringBody)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ${cls.name} &&
          runtimeType == other.runtimeType &&
          $equalsBody;

  @override
  int get hashCode => $hashCodeBody;
}
""".trimIndent()

        return DartClass(
            name = cls.name,
            kind = DartClassKind.DATA,
            code = code
        )
    }

    /**
     * Sealed Class -> Dart sealed class (Dart 3.0+) 또는 abstract class
     */
    private fun convertSealedClass(cls: ClassAnalysis, imports: MutableSet<String>): DartClass {
        // Dart 3.0 sealed class 사용
        val subclasses = cls.superTypes.filter { it.startsWith(cls.name) }

        val code = """
sealed class ${cls.name} {
  const ${cls.name}();
}

${subclasses.joinToString("\n\n") { subclass ->
    val subclassName = subclass.substringBefore("(").trim()
    """
class $subclassName extends ${cls.name} {
  const $subclassName();
}
""".trim()
}}
""".trimIndent()

        return DartClass(
            name = cls.name,
            kind = DartClassKind.SEALED,
            code = code
        )
    }

    /**
     * Enum Class 변환
     */
    private fun convertEnumClass(cls: ClassAnalysis): DartClass {
        // enum entries 추출 (간단한 변환)
        val entries = cls.properties.map { it.name.lowercase() }

        val code = """
enum ${cls.name} {
  ${entries.joinToString(",\n  ")};
}
""".trimIndent()

        return DartClass(
            name = cls.name,
            kind = DartClassKind.ENUM,
            code = code
        )
    }

    /**
     * Interface -> Abstract class
     */
    private fun convertInterface(cls: ClassAnalysis): DartClass {
        val methods = cls.methods.map { method ->
            val params = method.parameters.joinToString(", ") { param ->
                "${TypeMappings.toDartType(param.type)} ${param.name}"
            }
            val returnType = TypeMappings.toDartType(method.returnType)
            "  $returnType ${method.name}($params);"
        }

        val code = """
abstract class ${cls.name} {
${methods.joinToString("\n")}
}
""".trimIndent()

        return DartClass(
            name = cls.name,
            kind = DartClassKind.INTERFACE,
            code = code
        )
    }

    /**
     * 일반 클래스 변환
     */
    private fun convertRegularClass(cls: ClassAnalysis, imports: MutableSet<String>): DartClass {
        val fields = cls.properties.map { prop ->
            val dartType = TypeMappings.toDartType(prop.type)
            val modifier = if (prop.isMutable) "" else "final "
            val nullable = if (prop.type.endsWith("?")) "?" else ""
            DartField(
                name = prop.name,
                type = dartType,
                isFinal = !prop.isMutable,
                isNullable = prop.type.endsWith("?"),
                defaultValue = prop.defaultValue?.let { convertDefaultValue(it) }
            )
        }

        val constructorFields = cls.constructorParams.map { param ->
            DartField(
                name = param.name,
                type = TypeMappings.toDartType(param.type),
                isFinal = true,
                isNullable = param.type.endsWith("?"),
                defaultValue = param.defaultValue?.let { convertDefaultValue(it) }
            )
        }

        val allFields = (constructorFields + fields).distinctBy { it.name }

        val methods = cls.methods.filter { !it.isComposable }.map { method ->
            convertMethod(method, imports)
        }

        val superClass = cls.superTypes.firstOrNull { !it.contains("Interface") }?.let {
            " extends ${TypeMappings.toDartType(it)}"
        } ?: ""

        val interfaces = cls.superTypes.filter { it.contains("Interface") }.map {
            TypeMappings.toDartType(it)
        }
        val implementsClause = if (interfaces.isNotEmpty()) {
            " implements ${interfaces.joinToString(", ")}"
        } else ""

        val fieldsCode = allFields.joinToString("\n") { field ->
            val modifier = if (field.isFinal) "final " else ""
            val nullable = if (field.isNullable) "?" else ""
            "  $modifier${field.type}$nullable ${field.name};"
        }

        val constructorParams = allFields.map { field ->
            val required = if (!field.isNullable && field.defaultValue == null) "required " else ""
            "${required}this.${field.name}"
        }

        val code = """
class ${cls.name}$superClass$implementsClause {
$fieldsCode

  ${cls.name}({
    ${constructorParams.joinToString(",\n    ")},
  });

${methods.joinToString("\n\n")}
}
""".trimIndent()

        return DartClass(
            name = cls.name,
            kind = DartClassKind.CLASS,
            code = code
        )
    }

    /**
     * 함수 변환
     */
    private fun convertFunction(func: FunctionAnalysis, imports: MutableSet<String>): DartFunction {
        val params = func.parameters.map { param ->
            val dartType = TypeMappings.toDartType(param.type)
            val nullable = if (param.type.endsWith("?")) "?" else ""
            val defaultVal = param.defaultValue?.let { " = ${convertDefaultValue(it)}" } ?: ""
            "$dartType$nullable ${param.name}$defaultVal"
        }

        val returnType = TypeMappings.toDartType(func.returnType)
        val asyncModifier = if (func.isSuspend) "async " else ""
        val returnTypeModifier = if (func.isSuspend && !returnType.startsWith("Future")) {
            "Future<$returnType>"
        } else returnType

        val body = func.body?.let { convertFunctionBody(it, imports) } ?: "// TODO: implement"

        val code = """
$returnTypeModifier ${func.name}(${params.joinToString(", ")}) $asyncModifier{
  $body
}
""".trimIndent()

        return DartFunction(
            name = func.name,
            code = code,
            isAsync = func.isSuspend
        )
    }

    /**
     * 메서드 변환
     */
    private fun convertMethod(method: FunctionAnalysis, imports: MutableSet<String>): String {
        val params = method.parameters.map { param ->
            val dartType = TypeMappings.toDartType(param.type)
            val nullable = if (param.type.endsWith("?")) "?" else ""
            val defaultVal = param.defaultValue?.let { " = ${convertDefaultValue(it)}" } ?: ""
            "$dartType$nullable ${param.name}$defaultVal"
        }

        val returnType = TypeMappings.toDartType(method.returnType)
        val asyncModifier = if (method.isSuspend) "async " else ""
        val returnTypeModifier = if (method.isSuspend && !returnType.startsWith("Future")) {
            "Future<$returnType>"
        } else returnType

        val body = method.body?.let { convertFunctionBody(it, imports) } ?: "// TODO: implement"

        return """
  $returnTypeModifier ${method.name}(${params.joinToString(", ")}) $asyncModifier{
    $body
  }
""".trimIndent()
    }

    /**
     * 프로퍼티 변환
     */
    private fun convertProperty(prop: PropertyAnalysis): DartProperty {
        val dartType = TypeMappings.toDartType(prop.type)
        val modifier = if (prop.isMutable) "" else "final "
        val nullable = if (prop.type.endsWith("?")) "?" else ""
        val value = prop.defaultValue?.let { " = ${convertDefaultValue(it)}" } ?: ""

        val code = "$modifier$dartType$nullable ${prop.name}$value;"

        return DartProperty(
            name = prop.name,
            code = code
        )
    }

    /**
     * 함수 본문 변환 (간단한 변환)
     */
    private fun convertFunctionBody(body: String, imports: MutableSet<String>): String {
        var result = body
            // Kotlin 문법 -> Dart 문법
            .replace("val ", "final ")
            .replace("var ", "var ")
            .replace("fun ", "")
            .replace("println(", "print(")
            .replace("listOf(", "[")
            .replace("mutableListOf(", "[")
            .replace("mapOf(", "{")
            .replace("mutableMapOf(", "{")
            .replace("setOf(", "{")
            .replace(".forEach {", ".forEach((")
            .replace("it ->", ") {")
            .replace(" it ", " element ")
            .replace("(it)", "(element)")
            .replace(".let {", "?.let((value) {")
            .replace(".also {", "..also((value) {")
            .replace("?.let", "")
            .replace("when ", "switch ")
            .replace(" is ", " is ")
            .replace("!!", "!")
            .replace("?:", "??")

        // suspend -> async/await
        if (body.contains("suspend")) {
            result = result.replace("suspend ", "")
        }

        // coroutine 관련
        if (body.contains("launch") || body.contains("async")) {
            result = result
                .replace("launch {", "Future(() async {")
                .replace("async {", "Future(() async {")
                .replace("withContext(Dispatchers.IO)", "")
                .replace("withContext(Dispatchers.Main)", "")
        }

        // 타입 캐스팅
        result = result.replace(Regex("""as\s+(\w+)""")) { match ->
            "as ${TypeMappings.toDartType(match.groupValues[1])}"
        }

        // null 체크
        result = result.replace(Regex("""(\w+)\s*!=\s*null""")) { match ->
            "${match.groupValues[1]} != null"
        }

        // range 변환
        result = result.replace(Regex("""(\d+)\.\.(\d+)""")) { match ->
            "List.generate(${match.groupValues[2].toInt() - match.groupValues[1].toInt() + 1}, (i) => i + ${match.groupValues[1]})"
        }

        // string template
        result = result.replace(Regex("""\$\{([^}]+)\}""")) { match ->
            "\${${match.groupValues[1]}}"
        }
        result = result.replace(Regex("""\$(\w+)""")) { match ->
            "\$${match.groupValues[1]}"
        }

        return result
    }

    /**
     * 기본값 변환
     */
    private fun convertDefaultValue(value: String): String {
        return when {
            value == "null" -> "null"
            value == "true" || value == "false" -> value
            value.startsWith("\"") -> value.replace("\"", "'")
            value.startsWith("'") -> value
            value.matches(Regex("""-?\d+""")) -> value
            value.matches(Regex("""-?\d+\.\d+""")) -> value
            value.contains("listOf(") -> value.replace("listOf(", "[").replace(")", "]")
            value.contains("emptyList()") -> "[]"
            value.contains("emptyMap()") -> "{}"
            value.contains(".dp") -> value.replace(".dp", "")
            value.contains(".sp") -> value.replace(".sp", "")
            else -> value
        }
    }
}

/**
 * Dart 파일 코드 결과
 */
data class DartFileCode(
    val fileName: String,
    val imports: List<String>,
    val classes: List<DartClass>,
    val functions: List<DartFunction>,
    val topLevelProperties: List<DartProperty>
) {
    fun toCode(): String {
        val importsCode = imports.joinToString("\n")
        val classesCode = classes.joinToString("\n\n") { it.code }
        val functionsCode = functions.joinToString("\n\n") { it.code }
        val propertiesCode = topLevelProperties.joinToString("\n") { it.code }

        return """
$importsCode

$propertiesCode

$classesCode

$functionsCode
""".trimIndent()
    }
}

data class DartClass(
    val name: String,
    val kind: DartClassKind,
    val code: String
)

enum class DartClassKind {
    CLASS, DATA, SEALED, ENUM, INTERFACE
}

data class DartFunction(
    val name: String,
    val code: String,
    val isAsync: Boolean
)

data class DartProperty(
    val name: String,
    val code: String
)

data class DartField(
    val name: String,
    val type: String,
    val isFinal: Boolean,
    val isNullable: Boolean,
    val defaultValue: String?
)

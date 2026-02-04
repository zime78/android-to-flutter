package com.zime.app.androidtoflutter.mappings

/**
 * Kotlin → Dart 타입 매핑
 */
object TypeMappings {
    
    /**
     * 기본 타입 매핑
     */
    val primitiveTypes = mapOf(
        // 숫자
        "Int" to "int",
        "Long" to "int",
        "Short" to "int",
        "Byte" to "int",
        "Float" to "double",
        "Double" to "double",
        
        // 문자
        "Char" to "String",
        "String" to "String",
        
        // 불리언
        "Boolean" to "bool",
        
        // Void
        "Unit" to "void",
        "Nothing" to "Never"
    )
    
    /**
     * 컬렉션 타입 매핑
     */
    val collectionTypes = mapOf(
        "List" to "List",
        "MutableList" to "List",
        "ArrayList" to "List",
        "Set" to "Set",
        "MutableSet" to "Set",
        "HashSet" to "Set",
        "Map" to "Map",
        "MutableMap" to "Map",
        "HashMap" to "Map",
        "Array" to "List",
        "IntArray" to "List<int>",
        "DoubleArray" to "List<double>",
        "BooleanArray" to "List<bool>"
    )
    
    /**
     * Nullable 타입 처리
     */
    fun convertNullable(kotlinType: String): String {
        return if (kotlinType.endsWith("?")) {
            val baseType = kotlinType.dropLast(1)
            "${convertType(baseType)}?"
        } else {
            convertType(kotlinType)
        }
    }
    
    /**
     * 제네릭 타입 변환
     */
    fun convertGeneric(kotlinType: String): String {
        // List<String> → List<String>
        // Map<String, Int> → Map<String, int>
        val genericRegex = Regex("(\\w+)<(.+)>")
        val match = genericRegex.find(kotlinType)
        
        return if (match != null) {
            val containerType = match.groupValues[1]
            val typeParams = match.groupValues[2]
            
            val dartContainer = collectionTypes[containerType] ?: containerType
            val dartTypeParams = typeParams.split(",").joinToString(", ") { 
                convertType(it.trim())
            }
            
            "$dartContainer<$dartTypeParams>"
        } else {
            convertType(kotlinType)
        }
    }
    
    /**
     * 기본 타입 변환
     */
    fun convertType(kotlinType: String): String {
        // Nullable 처리
        if (kotlinType.endsWith("?")) {
            return "${convertType(kotlinType.dropLast(1))}?"
        }
        
        // 제네릭 처리
        if (kotlinType.contains("<")) {
            return convertGeneric(kotlinType)
        }
        
        // 기본 타입 매핑
        return primitiveTypes[kotlinType] 
            ?: collectionTypes[kotlinType] 
            ?: kotlinType
    }
    
    /**
     * Android/Compose 특수 타입 매핑
     */
    val specialTypes = mapOf(
        // Compose 타입
        "Color" to "Color",
        "Dp" to "double",
        "Sp" to "double",
        "TextUnit" to "double",
        "Offset" to "Offset",
        "Size" to "Size",
        "Rect" to "Rect",
        
        // Modifier 관련
        "Modifier" to "Widget", // Modifier는 Widget으로 래핑
        "PaddingValues" to "EdgeInsets",
        
        // State 타입
        "State" to "ValueNotifier",
        "MutableState" to "ValueNotifier",
        "StateFlow" to "Stream",
        "MutableStateFlow" to "StreamController",
        "SharedFlow" to "Stream",
        "LiveData" to "ValueNotifier",
        
        // Context
        "Context" to "BuildContext",
        
        // 기타
        "Uri" to "Uri",
        "Date" to "DateTime",
        "LocalDate" to "DateTime",
        "LocalDateTime" to "DateTime",
        "Instant" to "DateTime",
        "Duration" to "Duration"
    )
    
    /**
     * 전체 타입 변환
     */
    fun convert(kotlinType: String): String {
        // Nullable 처리
        val isNullable = kotlinType.endsWith("?")
        val baseType = if (isNullable) kotlinType.dropLast(1) else kotlinType

        // 제네릭 처리
        if (baseType.contains("<")) {
            val result = convertGeneric(baseType)
            return if (isNullable) "$result?" else result
        }

        // 특수 타입 확인
        val dartType = specialTypes[baseType]
            ?: primitiveTypes[baseType]
            ?: collectionTypes[baseType]
            ?: baseType

        return if (isNullable) "$dartType?" else dartType
    }

    /**
     * toDartType - convert()의 별칭
     */
    fun toDartType(kotlinType: String): String = convert(kotlinType)
}

/**
 * Kotlin → Dart 키워드 매핑
 */
object KeywordMappings {
    
    val keywords = mapOf(
        // 선언
        "val" to "final",
        "var" to "var",
        "const" to "const",
        "fun" to "", // 함수 선언에서 fun 제거
        
        // 클래스
        "class" to "class",
        "data class" to "class", // freezed 사용
        "sealed class" to "sealed class", // Dart 3.0+
        "object" to "class", // 싱글톤
        "interface" to "abstract class",
        "enum class" to "enum",
        
        // 제어문
        "when" to "switch",
        "if" to "if",
        "else" to "else",
        "for" to "for",
        "while" to "while",
        "do" to "do",
        
        // 예외
        "try" to "try",
        "catch" to "catch",
        "finally" to "finally",
        "throw" to "throw",
        
        // 기타
        "return" to "return",
        "break" to "break",
        "continue" to "continue",
        "this" to "this",
        "super" to "super",
        "null" to "null",
        "true" to "true",
        "false" to "false",
        
        // 접근 제어자
        "private" to "_", // 언더스코어 접두사
        "protected" to "@protected",
        "public" to "", // 기본값
        "internal" to "", // Dart에 없음
        
        // 코루틴
        "suspend" to "async",
        "await" to "await",
        "launch" to "", // Future로 변환
        "async" to "", // Future로 변환
        "withContext" to "" // 직접 변환
    )
    
    /**
     * 키워드 변환
     */
    fun convert(kotlinKeyword: String): String {
        return keywords[kotlinKeyword] ?: kotlinKeyword
    }
}

package com.zime.app.androidtoflutter.mappings

/**
 * Jetpack Compose Modifier → Flutter 매핑
 */
object ModifierMappings {
    
    /**
     * Modifier → Flutter Widget/Property 매핑
     * 
     * Compose Modifier는 Flutter에서 여러 방식으로 표현됨:
     * 1. Container/DecoratedBox의 속성
     * 2. 래핑 위젯 (Padding, SizedBox 등)
     * 3. GestureDetector 등의 핸들러
     */
    data class ModifierMapping(
        val flutterWidget: String?,      // 래핑 위젯 (예: Padding)
        val containerProperty: String?,  // Container 속성 (예: padding)
        val parameterMapping: Map<String, String> = emptyMap()
    )
    
    val modifierMappings = mapOf(
        // 크기
        "size" to ModifierMapping(
            flutterWidget = "SizedBox",
            containerProperty = null,
            parameterMapping = mapOf("width" to "width", "height" to "height")
        ),
        "width" to ModifierMapping(
            flutterWidget = "SizedBox",
            containerProperty = "width",
            parameterMapping = mapOf("width" to "width")
        ),
        "height" to ModifierMapping(
            flutterWidget = "SizedBox",
            containerProperty = "height",
            parameterMapping = mapOf("height" to "height")
        ),
        "fillMaxWidth" to ModifierMapping(
            flutterWidget = "SizedBox",
            containerProperty = "width",
            parameterMapping = mapOf("" to "double.infinity")
        ),
        "fillMaxHeight" to ModifierMapping(
            flutterWidget = "SizedBox",
            containerProperty = "height",
            parameterMapping = mapOf("" to "double.infinity")
        ),
        "fillMaxSize" to ModifierMapping(
            flutterWidget = "SizedBox.expand",
            containerProperty = null
        ),
        
        // 패딩
        "padding" to ModifierMapping(
            flutterWidget = "Padding",
            containerProperty = "padding",
            parameterMapping = mapOf(
                "all" to "EdgeInsets.all",
                "horizontal" to "EdgeInsets.symmetric(horizontal: )",
                "vertical" to "EdgeInsets.symmetric(vertical: )",
                "start" to "EdgeInsetsDirectional.only(start: )",
                "end" to "EdgeInsetsDirectional.only(end: )",
                "top" to "EdgeInsets.only(top: )",
                "bottom" to "EdgeInsets.only(bottom: )"
            )
        ),
        
        // 배경
        "background" to ModifierMapping(
            flutterWidget = "Container",
            containerProperty = "color"
        ),
        
        // 테두리
        "border" to ModifierMapping(
            flutterWidget = "Container",
            containerProperty = "decoration",
            parameterMapping = mapOf("" to "BoxDecoration(border: Border.all())")
        ),
        
        // 클리핑
        "clip" to ModifierMapping(
            flutterWidget = "ClipRRect",
            containerProperty = null,
            parameterMapping = mapOf(
                "RoundedCornerShape" to "borderRadius: BorderRadius.circular()"
            )
        ),
        "clipToBounds" to ModifierMapping(
            flutterWidget = "ClipRect",
            containerProperty = null
        ),
        
        // 클릭
        "clickable" to ModifierMapping(
            flutterWidget = "GestureDetector",
            containerProperty = null,
            parameterMapping = mapOf("onClick" to "onTap")
        ),
        "selectable" to ModifierMapping(
            flutterWidget = "InkWell",
            containerProperty = null
        ),
        
        // 스크롤
        "verticalScroll" to ModifierMapping(
            flutterWidget = "SingleChildScrollView",
            containerProperty = null
        ),
        "horizontalScroll" to ModifierMapping(
            flutterWidget = "SingleChildScrollView",
            containerProperty = null,
            parameterMapping = mapOf("" to "scrollDirection: Axis.horizontal")
        ),
        
        // 변형
        "alpha" to ModifierMapping(
            flutterWidget = "Opacity",
            containerProperty = null,
            parameterMapping = mapOf("alpha" to "opacity")
        ),
        "rotate" to ModifierMapping(
            flutterWidget = "Transform.rotate",
            containerProperty = null,
            parameterMapping = mapOf("degrees" to "angle")
        ),
        "scale" to ModifierMapping(
            flutterWidget = "Transform.scale",
            containerProperty = null,
            parameterMapping = mapOf("scale" to "scale")
        ),
        "offset" to ModifierMapping(
            flutterWidget = "Transform.translate",
            containerProperty = null,
            parameterMapping = mapOf("x" to "offset.dx", "y" to "offset.dy")
        ),
        
        // Row/Column 관련
        "weight" to ModifierMapping(
            flutterWidget = "Expanded",
            containerProperty = null,
            parameterMapping = mapOf("weight" to "flex")
        ),
        "align" to ModifierMapping(
            flutterWidget = "Align",
            containerProperty = null,
            parameterMapping = mapOf("alignment" to "alignment")
        ),
        
        // 그림자
        "shadow" to ModifierMapping(
            flutterWidget = "Container",
            containerProperty = "decoration",
            parameterMapping = mapOf("" to "BoxDecoration(boxShadow: [])")
        ),
        
        // Z-Index
        "zIndex" to ModifierMapping(
            flutterWidget = "IndexedStack",
            containerProperty = null
        )
    )
    
    /**
     * Modifier 변환 정보 조회
     */
    fun getMapping(modifierName: String): ModifierMapping? {
        return modifierMappings[modifierName]
    }
    
    /**
     * Modifier를 Flutter 코드로 변환
     * 예: .padding(16.dp) → Padding(padding: EdgeInsets.all(16), child: ...)
     */
    fun convertToFlutter(modifierName: String, params: Map<String, String>): String? {
        val mapping = modifierMappings[modifierName] ?: return null
        
        return when (modifierName) {
            "padding" -> convertPadding(params)
            "size" -> convertSize(params)
            "fillMaxWidth" -> "width: double.infinity"
            "fillMaxHeight" -> "height: double.infinity"
            "clickable" -> "GestureDetector(onTap: ${params["onClick"] ?: "() {}"}, child: "
            "alpha" -> "Opacity(opacity: ${params["alpha"] ?: "1.0"}, child: "
            "weight" -> "Expanded(flex: ${params["weight"]?.toIntOrNull() ?: 1}, child: "
            else -> mapping.flutterWidget
        }
    }
    
    private fun convertPadding(params: Map<String, String>): String {
        return when {
            params.containsKey("all") -> 
                "EdgeInsets.all(${params["all"]})"
            params.containsKey("horizontal") && params.containsKey("vertical") -> 
                "EdgeInsets.symmetric(horizontal: ${params["horizontal"]}, vertical: ${params["vertical"]})"
            params.containsKey("horizontal") -> 
                "EdgeInsets.symmetric(horizontal: ${params["horizontal"]})"
            params.containsKey("vertical") -> 
                "EdgeInsets.symmetric(vertical: ${params["vertical"]})"
            else -> {
                val top = params["top"] ?: "0"
                val bottom = params["bottom"] ?: "0"
                val start = params["start"] ?: params["left"] ?: "0"
                val end = params["end"] ?: params["right"] ?: "0"
                "EdgeInsets.only(left: $start, top: $top, right: $end, bottom: $bottom)"
            }
        }
    }
    
    private fun convertSize(params: Map<String, String>): String {
        val width = params["width"]
        val height = params["height"]
        
        return when {
            width != null && height != null -> "SizedBox(width: $width, height: $height, child: "
            width != null -> "SizedBox(width: $width, child: "
            height != null -> "SizedBox(height: $height, child: "
            else -> ""
        }
    }
}

package com.zime.app.androidtoflutter.mappings

/**
 * Jetpack Compose → Flutter Widget 매핑
 */
object WidgetMappings {
    
    /**
     * 기본 위젯 매핑
     */
    val basicWidgets = mapOf(
        // 텍스트
        "Text" to "Text",
        
        // 버튼
        "Button" to "ElevatedButton",
        "TextButton" to "TextButton",
        "OutlinedButton" to "OutlinedButton",
        "IconButton" to "IconButton",
        "FloatingActionButton" to "FloatingActionButton",
        "ExtendedFloatingActionButton" to "FloatingActionButton.extended",
        
        // 입력
        "TextField" to "TextField",
        "OutlinedTextField" to "TextField", // decoration으로 처리
        "BasicTextField" to "TextField",
        
        // 선택
        "Checkbox" to "Checkbox",
        "Switch" to "Switch",
        "RadioButton" to "Radio",
        "Slider" to "Slider",
        "RangeSlider" to "RangeSlider",
        
        // 이미지/아이콘
        "Image" to "Image",
        "Icon" to "Icon",
        "AsyncImage" to "Image.network",
        
        // 기타
        "Divider" to "Divider",
        "Spacer" to "SizedBox",
        "CircularProgressIndicator" to "CircularProgressIndicator",
        "LinearProgressIndicator" to "LinearProgressIndicator"
    )
    
    /**
     * 레이아웃 위젯 매핑
     */
    val layoutWidgets = mapOf(
        // 기본 레이아웃
        "Column" to "Column",
        "Row" to "Row",
        "Box" to "Stack",
        
        // 스크롤 레이아웃
        "LazyColumn" to "ListView.builder",
        "LazyRow" to "ListView.builder", // scrollDirection: Axis.horizontal
        "LazyVerticalGrid" to "GridView.builder",
        "LazyHorizontalGrid" to "GridView.builder",
        
        // Material 레이아웃
        "Scaffold" to "Scaffold",
        "Surface" to "Material",
        "Card" to "Card",
        
        // 기타 레이아웃
        "ConstraintLayout" to "LayoutBuilder + Stack",
        "FlowRow" to "Wrap",
        "FlowColumn" to "Wrap"
    )
    
    /**
     * Material 컴포넌트 매핑
     */
    val materialComponents = mapOf(
        // App Bar
        "TopAppBar" to "AppBar",
        "CenterAlignedTopAppBar" to "AppBar",
        "SmallTopAppBar" to "AppBar",
        "MediumTopAppBar" to "AppBar",
        "LargeTopAppBar" to "AppBar",
        
        // Navigation
        "NavigationBar" to "NavigationBar",
        "NavigationRail" to "NavigationRail",
        "NavigationDrawer" to "Drawer",
        "BottomAppBar" to "BottomAppBar",
        
        // Tabs
        "TabRow" to "TabBar",
        "Tab" to "Tab",
        "ScrollableTabRow" to "TabBar",
        
        // 다이얼로그/바텀시트
        "AlertDialog" to "AlertDialog",
        "ModalBottomSheet" to "showModalBottomSheet",
        "BottomSheetScaffold" to "Scaffold + DraggableScrollableSheet",
        
        // 기타
        "Snackbar" to "SnackBar",
        "Badge" to "Badge",
        "Chip" to "Chip",
        "FilterChip" to "FilterChip",
        "InputChip" to "InputChip"
    )
    
    /**
     * 애니메이션 위젯 매핑
     */
    val animationWidgets = mapOf(
        "AnimatedVisibility" to "AnimatedOpacity + AnimatedSize",
        "Crossfade" to "AnimatedSwitcher",
        "AnimatedContent" to "AnimatedSwitcher",
        "animateContentSize" to "AnimatedContainer"
    )
    
    /**
     * 모든 매핑 통합
     */
    val allMappings: Map<String, String> by lazy {
        basicWidgets + layoutWidgets + materialComponents + animationWidgets
    }
    
    /**
     * 위젯 매핑 조회
     */
    fun getFlutterWidget(composeWidget: String): String? {
        return allMappings[composeWidget]
    }
    
    /**
     * 복잡한 변환이 필요한 위젯인지 확인
     */
    fun requiresComplexConversion(composeWidget: String): Boolean {
        return composeWidget in setOf(
            "ConstraintLayout",
            "LazyVerticalGrid",
            "LazyHorizontalGrid",
            "AnimatedVisibility",
            "BottomSheetScaffold",
            "PullRefresh",
            "Pager"
        )
    }
    
    /**
     * AI 변환이 권장되는 위젯인지 확인
     */
    fun recommendsAiConversion(composeWidget: String): Boolean {
        return requiresComplexConversion(composeWidget) ||
               composeWidget.startsWith("Custom") ||
               composeWidget !in allMappings
    }

    /**
     * toFlutterWidget - Compose 위젯을 Flutter 위젯으로 변환
     */
    fun toFlutterWidget(composeWidget: String): String {
        return allMappings[composeWidget] ?: composeWidget
    }
}

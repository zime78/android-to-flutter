# Android to Flutter Porter - Data Models

## 1. Project Models

### 1.1 SourceProject

```kotlin
/**
 * Android 소스 프로젝트 정보
 */
data class SourceProject(
    val path: Path,
    val name: String,
    val packageName: String,
    val modules: List<SourceModule>,
    val buildConfig: AndroidBuildConfig,
    val dependencies: List<Dependency>
)

data class SourceModule(
    val name: String,
    val path: Path,
    val type: ModuleType, // APP, LIBRARY, FEATURE
    val sourceFiles: List<SourceFile>,
    val resources: ResourceSet
)

data class SourceFile(
    val path: Path,
    val type: FileType, // KOTLIN, XML, RESOURCE
    val content: String,
    val analysisResult: AnalysisResult? = null
)

data class AndroidBuildConfig(
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int,
    val kotlinVersion: String,
    val composeVersion: String
)
```

### 1.2 TargetProject

```kotlin
/**
 * Flutter 타겟 프로젝트 정보
 */
data class TargetProject(
    val path: Path,
    val name: String,
    val packageName: String,
    val flutterVersion: String,
    val dartVersion: String,
    val structure: FlutterStructure,
    val pubspec: PubspecConfig
)

data class FlutterStructure(
    val libPath: Path,
    val assetsPath: Path,
    val testPath: Path,
    val l10nPath: Path
)

data class PubspecConfig(
    val name: String,
    val description: String,
    val dependencies: Map<String, String>,
    val devDependencies: Map<String, String>,
    val assets: List<String>,
    val fonts: List<FontConfig>
)
```

### 1.3 ConversionConfig

```kotlin
/**
 * 변환 설정
 */
data class ConversionConfig(
    val source: SourceProject,
    val target: TargetProject,
    val options: ConversionOptions,
    val mappings: CustomMappings,
    val aiSettings: AiSettings
)

data class ConversionOptions(
    val stateManagement: StateManagementType, // RIVERPOD, PROVIDER, BLOC
    val navigation: NavigationType, // GO_ROUTER, AUTO_ROUTE
    val networking: NetworkingType, // DIO, HTTP
    val localStorage: LocalStorageType, // DRIFT, HIVE, SHARED_PREFS
    val imageLoading: ImageLoadingType, // CACHED_NETWORK_IMAGE, FLUTTER_CACHE
    val nullSafety: Boolean = true,
    val generateTests: Boolean = true
)

data class CustomMappings(
    val widgetMappings: Map<String, String>,
    val typeMappings: Map<String, String>,
    val libraryMappings: Map<String, String>
)

data class AiSettings(
    val useClaudeCode: Boolean = true,
    val hookEnabled: Boolean = true,
    val cliEnabled: Boolean = true,
    val maxRetries: Int = 3,
    val timeout: Duration = 60.seconds
)
```

---

## 2. Analysis Models

### 2.1 AnalysisResult

```kotlin
/**
 * 코드 분석 결과
 */
data class AnalysisResult(
    val file: SourceFile,
    val ast: KotlinAst,
    val composables: List<ComposableInfo>,
    val classes: List<ClassInfo>,
    val functions: List<FunctionInfo>,
    val imports: List<ImportInfo>,
    val complexity: ComplexityScore
)

data class KotlinAst(
    val root: AstNode,
    val typeInfo: Map<AstNode, TypeInfo>
)

sealed class AstNode {
    abstract val children: List<AstNode>
    abstract val sourceRange: SourceRange
    
    data class FileNode(
        val packageName: String,
        val imports: List<ImportNode>,
        val declarations: List<DeclarationNode>,
        override val children: List<AstNode>,
        override val sourceRange: SourceRange
    ) : AstNode()
    
    data class ClassNode(
        val name: String,
        val modifiers: List<Modifier>,
        val superTypes: List<TypeReference>,
        val members: List<MemberNode>,
        override val children: List<AstNode>,
        override val sourceRange: SourceRange
    ) : AstNode()
    
    data class FunctionNode(
        val name: String,
        val modifiers: List<Modifier>,
        val parameters: List<ParameterNode>,
        val returnType: TypeReference?,
        val body: BodyNode?,
        override val children: List<AstNode>,
        override val sourceRange: SourceRange
    ) : AstNode()
    
    // ... 추가 노드 타입들
}
```

### 2.2 ComposeTree

```kotlin
/**
 * Compose UI 트리 구조
 */
data class ComposeTree(
    val root: ComposeNode,
    val stateBindings: List<StateBinding>,
    val eventHandlers: List<EventHandler>
)

sealed class ComposeNode {
    abstract val children: List<ComposeNode>
    abstract val modifiers: List<ModifierInfo>
    abstract val sourceLocation: SourceLocation
    
    data class ContainerNode(
        val type: ContainerType, // Column, Row, Box, LazyColumn, etc.
        val arrangement: ArrangementInfo?,
        val alignment: AlignmentInfo?,
        override val modifiers: List<ModifierInfo>,
        override val children: List<ComposeNode>,
        override val sourceLocation: SourceLocation
    ) : ComposeNode()
    
    data class WidgetNode(
        val type: WidgetType, // Text, Button, Image, TextField, etc.
        val properties: Map<String, PropertyValue>,
        override val modifiers: List<ModifierInfo>,
        override val children: List<ComposeNode>,
        override val sourceLocation: SourceLocation
    ) : ComposeNode()
    
    data class CustomNode(
        val composableName: String,
        val parameters: List<ParameterValue>,
        override val modifiers: List<ModifierInfo>,
        override val children: List<ComposeNode>,
        override val sourceLocation: SourceLocation
    ) : ComposeNode()
}

data class ModifierInfo(
    val type: ModifierType,
    val parameters: Map<String, Any?>
)

enum class ModifierType {
    PADDING, MARGIN, SIZE, BACKGROUND, BORDER, CLIP,
    CLICK, SCROLL, ALPHA, ROTATE, SCALE, OFFSET
}
```

### 2.3 DependencyGraph

```kotlin
/**
 * 파일 의존성 그래프
 */
data class DependencyGraph(
    val nodes: Set<DependencyNode>,
    val edges: Set<DependencyEdge>
) {
    fun getConversionOrder(): List<DependencyNode> {
        // 토폴로지 정렬로 변환 순서 결정
        return topologicalSort(nodes, edges)
    }
}

data class DependencyNode(
    val file: SourceFile,
    val exports: Set<String>, // 이 파일이 제공하는 심볼
    val imports: Set<String>  // 이 파일이 필요로 하는 심볼
)

data class DependencyEdge(
    val from: DependencyNode,
    val to: DependencyNode,
    val type: DependencyType // IMPORT, INHERITANCE, COMPOSITION
)
```

---

## 3. Conversion Models

### 3.1 ConversionResult

```kotlin
/**
 * 변환 결과
 */
data class ConversionResult(
    val success: Boolean,
    val sourceFile: SourceFile,
    val outputFiles: List<OutputFile>,
    val errors: List<ConversionError>,
    val warnings: List<ConversionWarning>,
    val stats: ConversionStats
)

data class OutputFile(
    val path: Path,
    val content: String,
    val type: OutputFileType, // DART, YAML, JSON, ASSET
    val generatedBy: GenerationMethod // RULE_BASED, AI_ASSISTED, TEMPLATE
)

data class ConversionError(
    val code: String,
    val message: String,
    val sourceLocation: SourceLocation?,
    val suggestion: String?
)

data class ConversionWarning(
    val code: String,
    val message: String,
    val sourceLocation: SourceLocation?,
    val severity: WarningSeverity // LOW, MEDIUM, HIGH
)

data class ConversionStats(
    val totalLines: Int,
    val convertedLines: Int,
    val aiAssistedLines: Int,
    val manualReviewRequired: Int,
    val duration: Duration
)
```

### 3.2 MappingRule

```kotlin
/**
 * 변환 매핑 규칙
 */
sealed class MappingRule {
    abstract val priority: Int
    abstract val description: String
    
    data class DirectMapping(
        val sourcePattern: String,
        val targetTemplate: String,
        override val priority: Int,
        override val description: String
    ) : MappingRule()
    
    data class ConditionalMapping(
        val condition: MappingCondition,
        val trueMapping: MappingRule,
        val falseMapping: MappingRule?,
        override val priority: Int,
        override val description: String
    ) : MappingRule()
    
    data class AiMapping(
        val promptTemplate: String,
        val validationRule: ValidationRule?,
        override val priority: Int,
        override val description: String
    ) : MappingRule()
}

data class MappingCondition(
    val type: ConditionType,
    val parameters: Map<String, Any>
)

enum class ConditionType {
    HAS_MODIFIER, HAS_PARAMETER, TYPE_MATCHES, 
    CONTAINS_LAMBDA, IS_STATEFUL
}
```

---

## 4. Verification Models

### 4.1 Screenshot

```kotlin
/**
 * 스크린샷 데이터
 */
data class Screenshot(
    val id: String,
    val source: ScreenshotSource, // ANDROID, FLUTTER
    val image: BufferedImage,
    val metadata: ScreenshotMetadata,
    val capturedAt: Instant
)

data class ScreenshotMetadata(
    val screenName: String,
    val deviceConfig: DeviceConfig,
    val state: Map<String, Any>, // 캡처 시점의 앱 상태
    val resolution: Resolution
)

data class DeviceConfig(
    val name: String,
    val width: Int,
    val height: Int,
    val density: Float,
    val platform: Platform
)
```

### 4.2 ComparisonResult

```kotlin
/**
 * 이미지 비교 결과
 */
data class ComparisonResult(
    val sourceScreenshot: Screenshot,
    val targetScreenshot: Screenshot,
    val similarity: Float, // 0.0 ~ 1.0
    val diffRegions: List<DiffRegion>,
    val diffImage: BufferedImage?, // 차이점 하이라이트 이미지
    val passed: Boolean // similarity >= threshold
)

data class DiffRegion(
    val bounds: Rectangle,
    val diffScore: Float,
    val category: DiffCategory
)

enum class DiffCategory {
    COLOR_DIFFERENCE,    // 색상 차이
    POSITION_SHIFT,      // 위치 이동
    SIZE_DIFFERENCE,     // 크기 차이
    MISSING_ELEMENT,     // 요소 누락
    EXTRA_ELEMENT,       // 추가 요소
    TEXT_DIFFERENCE      // 텍스트 차이
}
```

---

## 5. Claude Integration Models

### 5.1 Hook Models

```kotlin
/**
 * Claude Code Hook 설정
 */
data class HookConfig(
    val projectPath: Path,
    val hooks: List<HookDefinition>
)

data class HookDefinition(
    val type: HookType,
    val matcher: HookMatcher,
    val command: String,
    val timeout: Int = 30000
)

enum class HookType {
    PRE_TOOL_USE,    // 도구 사용 전
    POST_TOOL_USE,   // 도구 사용 후
    NOTIFICATION     // 알림
}

data class HookMatcher(
    val tool: String?,        // 특정 도구만 매칭
    val event: String?,       // 특정 이벤트만 매칭
    val filePattern: String?  // 특정 파일 패턴만 매칭
)
```

### 5.2 CLI Models

```kotlin
/**
 * Claude CLI 명령 및 응답
 */
data class ClaudeCommand(
    val type: CommandType,
    val prompt: String,
    val options: CommandOptions
)

enum class CommandType {
    PRINT,     // --print 모드 (단일 응답)
    CONTINUE,  // --continue 모드 (세션 이어가기)
    DANGEROUSLY_SKIP_PERMISSIONS // 권한 스킵
}

data class CommandOptions(
    val model: String? = null,
    val maxTokens: Int? = null,
    val timeout: Duration = 60.seconds,
    val workingDirectory: Path? = null
)

data class ClaudeResponse(
    val success: Boolean,
    val content: String,
    val error: String?,
    val usage: TokenUsage?
)

data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int
)
```

---

## 6. Widget Mapping Table

### 6.1 Layout Widgets

| Compose | Flutter | Notes |
|---------|---------|-------|
| `Column` | `Column` | arrangement → mainAxisAlignment |
| `Row` | `Row` | arrangement → mainAxisAlignment |
| `Box` | `Stack` | alignment → alignment |
| `LazyColumn` | `ListView.builder` | 가상화 리스트 |
| `LazyRow` | `ListView.builder` (horizontal) | scrollDirection: Axis.horizontal |
| `LazyVerticalGrid` | `GridView.builder` | 그리드 레이아웃 |
| `Scaffold` | `Scaffold` | 거의 동일 |
| `Surface` | `Material` | elevation 매핑 |
| `Card` | `Card` | 동일 |
| `ConstraintLayout` | `LayoutBuilder` + `Stack` | 복잡한 매핑 |

### 6.2 Basic Widgets

| Compose | Flutter | Notes |
|---------|---------|-------|
| `Text` | `Text` | style 매핑 필요 |
| `Button` | `ElevatedButton` | onClick → onPressed |
| `TextButton` | `TextButton` | 동일 |
| `OutlinedButton` | `OutlinedButton` | 동일 |
| `IconButton` | `IconButton` | 동일 |
| `FloatingActionButton` | `FloatingActionButton` | 동일 |
| `TextField` | `TextField` | 입력 필드 |
| `OutlinedTextField` | `TextField` (decoration) | OutlineInputBorder |
| `Checkbox` | `Checkbox` | 동일 |
| `Switch` | `Switch` | 동일 |
| `Slider` | `Slider` | 동일 |
| `RadioButton` | `Radio` | 동일 |
| `Image` | `Image.network/asset` | 소스에 따라 |
| `Icon` | `Icon` | 동일 |

### 6.3 Modifier Mapping

| Compose Modifier | Flutter | Notes |
|------------------|---------|-------|
| `.padding()` | `Padding` widget 또는 `EdgeInsets` | Container padding |
| `.fillMaxWidth()` | `double.infinity` | width: double.infinity |
| `.fillMaxHeight()` | `double.infinity` | height: double.infinity |
| `.size()` | `SizedBox` | 고정 크기 |
| `.background()` | `Container` + `color` | 배경색 |
| `.clip()` | `ClipRRect`, `ClipOval` | 클리핑 |
| `.border()` | `Container` + `BoxDecoration` | 테두리 |
| `.clickable()` | `GestureDetector` 또는 `InkWell` | 클릭 핸들러 |
| `.verticalScroll()` | `SingleChildScrollView` | 스크롤 |
| `.horizontalScroll()` | `SingleChildScrollView` | scrollDirection |
| `.alpha()` | `Opacity` | 투명도 |

### 6.4 State Management Mapping

| Android (ViewModel) | Flutter (Riverpod) |
|--------------------|--------------------|
| `ViewModel` | `@riverpod class` |
| `StateFlow<T>` | `AsyncValue<T>` 또는 `State` |
| `MutableStateFlow<T>` | `StateNotifier<T>` |
| `viewModel.state.collectAsState()` | `ref.watch(provider)` |
| `viewModel.action()` | `ref.read(provider.notifier).action()` |
| `@HiltViewModel` | `@riverpod` annotation |

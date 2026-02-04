# Android to Flutter Porter - Architecture Design

## 1. System Architecture

### 1.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        IntelliJ IDEA Platform                             │
├──────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                    Android to Flutter Porter Plugin                 │  │
│  ├────────────────────────────────────────────────────────────────────┤  │
│  │                                                                     │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │  │
│  │  │  UI Layer   │  │  Analysis   │  │ Conversion  │  │Verification│ │  │
│  │  │  (Compose)  │  │   Layer     │  │   Layer     │  │   Layer   │ │  │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────┬─────┘ │  │
│  │         │                │                │               │        │  │
│  │         └────────────────┼────────────────┼───────────────┘        │  │
│  │                          │                │                        │  │
│  │                    ┌─────┴────────────────┴─────┐                  │  │
│  │                    │      Core Services         │                  │  │
│  │                    │  ┌─────────┐ ┌──────────┐  │                  │  │
│  │                    │  │ Project │ │ Settings │  │                  │  │
│  │                    │  │ Manager │ │ Manager  │  │                  │  │
│  │                    │  └─────────┘ └──────────┘  │                  │  │
│  │                    └─────────────┬──────────────┘                  │  │
│  │                                  │                                 │  │
│  │                    ┌─────────────┴──────────────┐                  │  │
│  │                    │   Claude Code Integration   │                  │  │
│  │                    │  ┌─────────┐ ┌──────────┐  │                  │  │
│  │                    │  │  Hook   │ │   CLI    │  │                  │  │
│  │                    │  │ Manager │ │ Executor │  │                  │  │
│  │                    │  └─────────┘ └──────────┘  │                  │  │
│  │                    └────────────────────────────┘                  │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           External Systems                                │
├──────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │  Claude     │  │   Flutter   │  │   Android   │  │  File       │     │
│  │  Code CLI   │  │   SDK       │  │   SDK       │  │  System     │     │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │
└──────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Layer Responsibilities

| Layer | 책임 | 주요 컴포넌트 |
|-------|------|--------------|
| **UI Layer** | 사용자 인터페이스, 이벤트 처리 | ToolWindow, Dialogs, Panels |
| **Analysis Layer** | 소스 코드 분석, AST 처리 | KotlinAnalyzer, ComposeParser |
| **Conversion Layer** | 코드 변환, 매핑 적용 | DartGenerator, WidgetMapper |
| **Verification Layer** | 변환 결과 검증 | ScreenshotCapture, ImageComparator |
| **Core Services** | 프로젝트 관리, 설정 관리 | ProjectManager, SettingsManager |
| **Claude Integration** | AI 통합, CLI 실행 | HookManager, CliExecutor |

---

## 2. Component Design

### 2.1 UI Layer Components

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │  MainToolWindow  │  │ ConversionPanel  │  │ VerifyPanel   │  │
│  │  ───────────────│  │  ───────────────│  │ ─────────────│  │
│  │  - Project Setup │  │  - File Tree     │  │ - Results     │  │
│  │  - Quick Actions │  │  - Progress      │  │ - Screenshots │  │
│  │  - Status Bar    │  │  - Logs          │  │ - Diff View   │  │
│  └──────────────────┘  └──────────────────┘  └───────────────┘  │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │ SettingsDialog   │  │ MappingEditor    │                     │
│  │  ───────────────│  │  ───────────────│                     │
│  │  - Library Maps  │  │  - Custom Rules  │                     │
│  │  - AI Settings   │  │  - Templates     │                     │
│  └──────────────────┘  └──────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Analysis Layer Components

```
┌─────────────────────────────────────────────────────────────────┐
│                      Analysis Layer                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    KotlinAnalyzer                         │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ PSI Parser  │→│ AST Builder │→│ Type Resolver│       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    ComposeAnalyzer                        │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ Composable  │→│ UI Tree     │→│ State       │       │   │
│  │  │ Detector    │  │ Builder     │  │ Analyzer    │       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  DependencyAnalyzer                       │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ Import      │→│ Dependency  │→│ Conversion  │       │   │
│  │  │ Scanner     │  │ Graph       │  │ Order       │       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 Conversion Layer Components

```
┌─────────────────────────────────────────────────────────────────┐
│                     Conversion Layer                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    CodeConverter                          │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ Kotlin→Dart │  │ Type        │  │ Syntax      │       │   │
│  │  │ Transformer │  │ Mapper      │  │ Adjuster    │       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    WidgetConverter                        │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ Compose→    │  │ Layout      │  │ Style       │       │   │
│  │  │ Flutter Map │  │ Converter   │  │ Extractor   │       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   ResourceConverter                       │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ Drawable    │  │ String      │  │ Dimen/Color │       │   │
│  │  │ Converter   │  │ Converter   │  │ Converter   │       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.4 Claude Code Integration

```
┌─────────────────────────────────────────────────────────────────┐
│                  Claude Code Integration                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                     HookManager                           │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ Settings    │  │ Hook        │  │ Event       │       │   │
│  │  │ Generator   │  │ Registry    │  │ Handler     │       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  │                                                           │   │
│  │  Hook Types:                                              │   │
│  │  - PreToolUse: 변환 전 코드 분석 요청                      │   │
│  │  - PostToolUse: 변환 후 결과 검증                         │   │
│  │  - Notification: 진행 상황 알림                           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                     CliExecutor                           │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │   │
│  │  │ Command     │  │ Process     │  │ Response    │       │   │
│  │  │ Builder     │  │ Manager     │  │ Parser      │       │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │   │
│  │                                                           │   │
│  │  CLI Commands:                                            │   │
│  │  - claude --print "Convert this Kotlin to Dart: ..."     │   │
│  │  - claude --continue (세션 이어가기)                      │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Flow

### 3.1 Conversion Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Source    │     │  Analysis   │     │ Conversion  │
│   Files     │────▶│   Engine    │────▶│   Engine    │
│  (.kt)      │     │             │     │             │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                   │
                           ▼                   ▼
                    ┌─────────────┐     ┌─────────────┐
                    │  Analysis   │     │  Converted  │
                    │   Result    │     │   Code      │
                    │  (IR Tree)  │     │  (.dart)    │
                    └─────────────┘     └─────────────┘
                           │                   │
                           └─────────┬─────────┘
                                     ▼
                           ┌─────────────────┐
                           │  Claude Code    │
                           │  (복잡한 변환)   │
                           └─────────────────┘
                                     │
                                     ▼
                           ┌─────────────────┐
                           │   Verification  │
                           │     Engine      │
                           └─────────────────┘
                                     │
                                     ▼
                           ┌─────────────────┐
                           │  Final Output   │
                           │ Flutter Project │
                           └─────────────────┘
```

### 3.2 AI-Assisted Conversion Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                    복잡한 코드 변환 시                             │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  1. 규칙 기반 변환 시도                                           │
│     │                                                             │
│     ├── 성공 → 결과 반환                                          │
│     │                                                             │
│     └── 실패/불확실 → Claude Code 호출                            │
│                          │                                        │
│                          ▼                                        │
│  2. Claude Code CLI 실행                                          │
│     ┌─────────────────────────────────────────────────┐          │
│     │ claude --print "                                │          │
│     │   Convert this Kotlin Compose code to Flutter:  │          │
│     │   [원본 코드]                                    │          │
│     │   Requirements:                                 │          │
│     │   - Preserve exact visual appearance            │          │
│     │   - Maintain state management pattern           │          │
│     │ "                                               │          │
│     └─────────────────────────────────────────────────┘          │
│                          │                                        │
│                          ▼                                        │
│  3. 응답 파싱 및 검증                                             │
│     │                                                             │
│     ├── 유효한 Dart 코드 → 적용                                   │
│     │                                                             │
│     └── 에러/불완전 → 재시도 또는 수동 처리 플래그                  │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. Package Structure

```
com.zime.app.androidtoflutter/
├── ui/                          # UI Layer
│   ├── toolwindow/
│   │   ├── MainToolWindow.kt
│   │   ├── MainToolWindowFactory.kt
│   │   └── components/
│   │       ├── ProjectSetupPanel.kt
│   │       ├── ConversionPanel.kt
│   │       ├── VerificationPanel.kt
│   │       └── LogPanel.kt
│   ├── dialogs/
│   │   ├── SettingsDialog.kt
│   │   └── MappingEditorDialog.kt
│   └── theme/
│       └── PluginTheme.kt
│
├── analyzer/                    # Analysis Layer
│   ├── kotlin/
│   │   ├── KotlinAnalyzer.kt
│   │   ├── KotlinAstBuilder.kt
│   │   └── TypeResolver.kt
│   ├── compose/
│   │   ├── ComposeAnalyzer.kt
│   │   ├── ComposableDetector.kt
│   │   ├── UiTreeBuilder.kt
│   │   └── StateAnalyzer.kt
│   └── dependency/
│       ├── DependencyAnalyzer.kt
│       ├── ImportScanner.kt
│       └── ConversionOrder.kt
│
├── converter/                   # Conversion Layer
│   ├── code/
│   │   ├── KotlinToDartConverter.kt
│   │   ├── TypeMapper.kt
│   │   └── SyntaxAdjuster.kt
│   ├── widget/
│   │   ├── ComposeToFlutterMapper.kt
│   │   ├── LayoutConverter.kt
│   │   └── StyleExtractor.kt
│   ├── resource/
│   │   ├── DrawableConverter.kt
│   │   ├── StringResourceConverter.kt
│   │   └── ThemeConverter.kt
│   └── templates/
│       ├── FlutterProjectTemplate.kt
│       └── WidgetTemplates.kt
│
├── verification/                # Verification Layer
│   ├── screenshot/
│   │   ├── AndroidScreenshotCapture.kt
│   │   ├── FlutterScreenshotCapture.kt
│   │   └── ScreenshotManager.kt
│   ├── compare/
│   │   ├── ImageComparator.kt
│   │   ├── PixelDiffAnalyzer.kt
│   │   └── SimilarityCalculator.kt
│   └── report/
│       ├── VerificationReport.kt
│       └── DiffReportGenerator.kt
│
├── claude/                      # Claude Code Integration
│   ├── hook/
│   │   ├── HookManager.kt
│   │   ├── HookSettingsGenerator.kt
│   │   ├── HookRegistry.kt
│   │   └── HookEventHandler.kt
│   ├── cli/
│   │   ├── ClaudeCliExecutor.kt
│   │   ├── CommandBuilder.kt
│   │   ├── ProcessManager.kt
│   │   └── ResponseParser.kt
│   └── prompt/
│       ├── PromptTemplates.kt
│       └── ConversionPromptBuilder.kt
│
├── models/                      # Data Models
│   ├── project/
│   │   ├── SourceProject.kt
│   │   ├── TargetProject.kt
│   │   └── ConversionConfig.kt
│   ├── analysis/
│   │   ├── AnalysisResult.kt
│   │   ├── ComposeTree.kt
│   │   └── DependencyGraph.kt
│   ├── conversion/
│   │   ├── ConversionResult.kt
│   │   ├── MappingRule.kt
│   │   └── ConversionError.kt
│   └── verification/
│       ├── Screenshot.kt
│       ├── ComparisonResult.kt
│       └── DiffRegion.kt
│
├── services/                    # Core Services
│   ├── ProjectManagerService.kt
│   ├── SettingsService.kt
│   ├── ConversionService.kt
│   └── NotificationService.kt
│
├── mappings/                    # Mapping Rules
│   ├── WidgetMappings.kt       # Compose → Flutter Widget
│   ├── TypeMappings.kt         # Kotlin → Dart Types
│   ├── LibraryMappings.kt      # Library mappings
│   └── PatternMappings.kt      # Code pattern mappings
│
└── util/                        # Utilities
    ├── FileUtils.kt
    ├── ProcessUtils.kt
    └── ImageUtils.kt
```

---

## 5. Key Interfaces

### 5.1 Analyzer Interfaces

```kotlin
interface IKotlinAnalyzer {
    fun analyze(file: PsiFile): AnalysisResult
    fun buildAst(file: PsiFile): KotlinAst
    fun resolveTypes(ast: KotlinAst): TypedAst
}

interface IComposeAnalyzer {
    fun detectComposables(file: PsiFile): List<ComposableFunction>
    fun buildUiTree(composable: ComposableFunction): UiTree
    fun analyzeState(composable: ComposableFunction): StateInfo
}

interface IDependencyAnalyzer {
    fun scanImports(project: Project): List<Import>
    fun buildDependencyGraph(files: List<PsiFile>): DependencyGraph
    fun getConversionOrder(graph: DependencyGraph): List<PsiFile>
}
```

### 5.2 Converter Interfaces

```kotlin
interface ICodeConverter {
    fun convert(kotlinCode: String): DartCode
    fun convertFile(file: PsiFile): ConversionResult
    fun convertProject(project: SourceProject): ProjectConversionResult
}

interface IWidgetConverter {
    fun mapWidget(compose: ComposeWidget): FlutterWidget
    fun convertLayout(layout: ComposeLayout): FlutterLayout
    fun extractStyles(modifier: Modifier): FlutterStyles
}

interface IResourceConverter {
    fun convertDrawables(drawables: List<Drawable>): FlutterAssets
    fun convertStrings(strings: StringResources): L10nFiles
    fun convertTheme(theme: ComposeTheme): FlutterTheme
}
```

### 5.3 Claude Integration Interfaces

```kotlin
interface IHookManager {
    fun setupHooks(projectPath: Path): HookSetupResult
    fun registerHook(hookType: HookType, handler: HookHandler)
    fun removeHooks(projectPath: Path)
}

interface IClaudeCliExecutor {
    suspend fun execute(command: ClaudeCommand): ClaudeResponse
    suspend fun convertWithAi(code: String, context: ConversionContext): String
    fun isAvailable(): Boolean
}
```

### 5.4 Verification Interfaces

```kotlin
interface IScreenshotCapture {
    suspend fun captureAndroid(config: CaptureConfig): Screenshot
    suspend fun captureFlutter(config: CaptureConfig): Screenshot
}

interface IImageComparator {
    fun compare(source: Screenshot, target: Screenshot): ComparisonResult
    fun calculateSimilarity(source: Image, target: Image): Float
    fun findDiffRegions(source: Image, target: Image): List<DiffRegion>
}
```

---

## 6. Technology Decisions

### 6.1 선택된 기술 및 근거

| 영역 | 기술 | 근거 |
|------|------|------|
| **Plugin UI** | Compose for Desktop | IntelliJ 공식 지원, 모던 선언형 UI |
| **Kotlin Analysis** | IntelliJ PSI | 정확한 Kotlin 파싱, IDE 통합 |
| **Image Compare** | OpenCV (via JavaCV) | 고성능 이미지 처리, 픽셀 비교 |
| **Process Exec** | Kotlin Coroutines | 비동기 CLI 실행, UI 블로킹 방지 |
| **State Management** | MutableState + Flow | Compose UI 상태 관리 표준 |
| **Serialization** | Kotlinx Serialization | Kotlin 네이티브, 타입 안전 |

### 6.2 Flutter 타겟 라이브러리 (기본값)

| Android | Flutter | 비고 |
|---------|---------|------|
| ViewModel | Riverpod | Provider도 선택 가능 |
| Navigation Compose | GoRouter | 선언적 라우팅 |
| Retrofit | Dio | 인터셉터 지원 |
| Room | Drift | 타입 안전 ORM |
| Hilt | GetIt + Injectable | DI 컨테이너 |
| Coil | cached_network_image | 이미지 로딩 |

---

## 7. Error Handling Strategy

### 7.1 에러 분류

| 레벨 | 유형 | 처리 |
|------|------|------|
| **Fatal** | PSI 파싱 실패, 프로젝트 구조 오류 | 중단 + 상세 에러 표시 |
| **Error** | 변환 실패, AI 응답 오류 | 해당 파일 스킵 + 로그 |
| **Warning** | 불완전 변환, 추측 변환 | 계속 + 경고 표시 |
| **Info** | 수동 검토 권장 | 로그만 기록 |

### 7.2 Fallback Strategy

```
1. 규칙 기반 변환 시도
   │
   ├── 성공 → 완료
   │
   └── 실패 → 2. Claude Code AI 변환
                │
                ├── 성공 → 완료
                │
                └── 실패 → 3. 템플릿 기반 스켈레톤 생성
                             │
                             └── TODO 주석 삽입하여 수동 처리 유도
```

---

## 8. Security Considerations

| 항목 | 대응 |
|------|------|
| **API Key 노출** | 환경 변수 또는 IDE 보안 저장소 사용 |
| **코드 전송** | Claude CLI는 로컬 실행, 필요시에만 API 호출 |
| **파일 접근** | 사용자 지정 경로만 접근, 시스템 파일 보호 |
| **프로세스 실행** | Claude CLI만 화이트리스트 실행 |

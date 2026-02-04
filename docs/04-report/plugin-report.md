# Android to Flutter Porter Plugin - 개발 결과 보고서

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | Android to Flutter Porter |
| **버전** | 1.0.0 |
| **플랫폼** | IntelliJ IDEA 2024.2+ |
| **목적** | Android Kotlin/Jetpack Compose 앱을 Flutter/Dart로 변환 |
| **GitHub** | https://github.com/zime78/android-to-flutter |

---

## 2. 구현 완료 기능

### 2.1 Analysis Layer (분석 계층)
| 컴포넌트 | 파일 | 기능 |
|----------|------|------|
| KotlinAnalyzer | `analyzer/KotlinAnalyzer.kt` | Kotlin 파일 PSI 분석 |
| ComposeAnalyzer | `analyzer/ComposeAnalyzer.kt` | Composable 함수 트리 분석 |
| DependencyAnalyzer | `analyzer/DependencyAnalyzer.kt` | 파일 간 의존성 그래프 생성 |
| ProjectAnalyzer | `analyzer/ProjectAnalyzer.kt` | 프로젝트 전체 분석 |

### 2.2 Conversion Layer (변환 계층)
| 컴포넌트 | 파일 | 기능 |
|----------|------|------|
| KotlinToDartConverter | `converter/KotlinToDartConverter.kt` | Kotlin → Dart 문법 변환 |
| ComposeToFlutterConverter | `converter/ComposeToFlutterConverter.kt` | Compose → Flutter 위젯 변환 |
| ProjectConverter | `converter/ProjectConverter.kt` | 프로젝트 전체 변환 |

### 2.3 Mappings (매핑 테이블)
| 컴포넌트 | 파일 | 매핑 수 |
|----------|------|---------|
| TypeMappings | `mappings/TypeMappings.kt` | 기본 타입 + 컬렉션 타입 |
| WidgetMappings | `mappings/WidgetMappings.kt` | 50+ 위젯 매핑 |
| ModifierMappings | `mappings/ModifierMappings.kt` | 30+ Modifier 매핑 |

### 2.4 Claude Code Integration (AI 통합)
| 컴포넌트 | 파일 | 기능 |
|----------|------|------|
| ClaudeCliExecutor | `claude/cli/ClaudeCliExecutor.kt` | Claude CLI 실행 |
| HookManager | `claude/hook/HookManager.kt` | Hook 시스템 관리 |

### 2.5 Verification Layer (검증 계층)
| 컴포넌트 | 파일 | 기능 |
|----------|------|------|
| ScreenshotCapture | `verification/ScreenshotCapture.kt` | 스크린샷 캡처 |
| ScreenshotComparator | `verification/ScreenshotComparator.kt` | 이미지 비교 |
| VerificationService | `verification/VerificationService.kt` | 검증 서비스 |

### 2.6 UI Layer
| 컴포넌트 | 파일 | 기능 |
|----------|------|------|
| PorterToolWindow | `ui/toolwindow/PorterToolWindow.kt` | 메인 Tool Window |
| PorterToolWindowFactory | `ui/toolwindow/PorterToolWindowFactory.kt` | Tool Window 팩토리 |

---

## 3. 지원 옵션

### State Management
- Riverpod
- Bloc
- Provider
- GetX

### Navigation
- GoRouter
- AutoRoute
- Navigator (기본)

### Networking
- Dio
- HTTP
- Chopper

---

## 4. 파일 구조

```
src/main/kotlin/com/zime/app/androidtoflutter/
├── actions/                    # IntelliJ Actions
│   ├── ConvertFileAction.kt
│   ├── ConvertProjectAction.kt
│   ├── ConvertSelectionAction.kt
│   ├── SetupHooksAction.kt
│   └── VerifyAction.kt
├── analyzer/                   # 분석 계층
│   ├── ComposeAnalyzer.kt
│   ├── DependencyAnalyzer.kt
│   ├── KotlinAnalyzer.kt
│   └── ProjectAnalyzer.kt
├── claude/                     # Claude Code 통합
│   ├── cli/
│   │   └── ClaudeCliExecutor.kt
│   └── hook/
│       └── HookManager.kt
├── converter/                  # 변환 계층
│   ├── ComposeToFlutterConverter.kt
│   ├── KotlinToDartConverter.kt
│   └── ProjectConverter.kt
├── mappings/                   # 매핑 테이블
│   ├── ModifierMappings.kt
│   ├── TypeMappings.kt
│   └── WidgetMappings.kt
├── models/                     # 데이터 모델
│   ├── analysis/
│   ├── conversion/
│   └── project/
├── services/                   # 서비스
│   └── ConversionService.kt
├── ui/                         # UI 계층
│   ├── state/
│   └── toolwindow/
└── verification/               # 검증 계층
    ├── ScreenshotCapture.kt
    ├── ScreenshotComparator.kt
    └── VerificationService.kt
```

---

## 5. 빌드 결과

```
BUILD SUCCESSFUL
Plugin: build/distributions/android-to-flutter-1.0.0.zip (4.4 MB)
```

---

## 6. 미완료 항목 (TODO)

| 항목 | 위치 | 설명 |
|------|------|------|
| File Chooser | PorterToolWindow.kt:34 | 파일 선택 다이얼로그 |
| Hook 설치 로직 | PorterToolWindow.kt:69 | HookManager 연결 |
| 변환 로직 연결 | PorterToolWindow.kt:91 | ConversionService 호출 |
| 스크린샷 검증 연결 | PorterToolWindow.kt:96 | VerificationService 호출 |
| 선택 영역 변환 | ConvertSelectionAction.kt:28 | 선택 영역 변환 구현 |

---

## 7. CI/CD

- **Qodana 코드 품질 검사**: PR 및 main push 시 자동 실행
- **설정 파일**: `.github/workflows/qodana_code_quality.yml`

---

## 8. 테스트 방법

### 방법 1: IDE에서 직접 실행 (권장)

1. IntelliJ IDEA에서 프로젝트 열기
2. Gradle 탭 → Tasks → intellij → `runIde` 실행
3. 새 IntelliJ 인스턴스가 열림 (플러그인 포함)

### 방법 2: 빌드된 플러그인 설치

1. `./gradlew buildPlugin` 실행
2. `build/distributions/android-to-flutter-1.0.0.zip` 생성됨
3. IntelliJ IDEA → Settings → Plugins → ⚙️ → Install Plugin from Disk
4. zip 파일 선택 → 재시작

### 방법 3: Run Configuration 사용

1. `.run/Run IDE with Plugin.run.xml` 설정 사용
2. Run → Run 'Run IDE with Plugin'

---

## 9. 사용 방법

1. **Tool Window 열기**: View → Tool Windows → Android to Flutter Porter
2. **소스 경로 설정**: Android 프로젝트 경로 선택
3. **대상 경로 설정**: Flutter 프로젝트 생성 경로 선택
4. **옵션 설정**: State Management, Navigation 선택
5. **변환 실행**: "전체 변환" 또는 "선택 변환" 버튼 클릭

---

## 10. 다음 단계

1. UI 기능 연결 완료
2. 실제 Android 프로젝트로 변환 테스트
3. 변환 품질 개선
4. 사용자 피드백 수집

---

*생성일: 2026-02-05*
*버전: 1.0.0*

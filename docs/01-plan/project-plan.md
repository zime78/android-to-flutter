# Android to Flutter Porter Plugin - Project Plan

## 1. Project Overview

### 1.1 프로젝트명
**Android to Flutter Porter** - IntelliJ IDEA Plugin

### 1.2 목적
Android Kotlin/Jetpack Compose 앱을 Flutter로 자동 포팅하여 iOS/Android 크로스플랫폼 앱을 생성하는 IntelliJ IDEA 플러그인 개발

### 1.3 핵심 가치
- **100% UI 동일성**: Android 화면과 Flutter 화면이 픽셀 단위로 일치
- **100% 동작 동일성**: 비즈니스 로직 완벽 보존
- **AI 기반 변환**: Claude Code를 활용한 지능형 코드 변환
- **개발자 생산성**: 수동 포팅 대비 90% 이상 시간 절감

---

## 2. Scope Definition

### 2.1 In Scope (포함)

| 영역 | 상세 |
|------|------|
| **소스 분석** | Kotlin/Compose 코드 파싱 및 AST 분석 |
| **UI 변환** | Jetpack Compose → Flutter Widget 변환 |
| **로직 변환** | Kotlin → Dart 비즈니스 로직 변환 |
| **상태관리** | ViewModel/StateFlow → Provider/Riverpod 매핑 |
| **네비게이션** | Navigation Compose → GoRouter 변환 |
| **네트워크** | Retrofit/OkHttp → Dio/http 변환 |
| **로컬저장소** | Room/DataStore → Drift/SharedPreferences 변환 |
| **리소스** | drawable/strings → assets/l10n 변환 |
| **검증** | 스크린샷 자동 비교 시스템 |

### 2.2 Out of Scope (제외)

| 영역 | 사유 |
|------|------|
| Java 코드 | Kotlin만 지원 (Java는 향후 버전) |
| XML Layout | Compose만 지원 (XML은 제외) |
| NDK/JNI | 네이티브 코드는 수동 처리 필요 |
| 플랫폼 특화 API | 카메라, 센서 등은 매핑 가이드 제공 |

### 2.3 필수 조건 (Prerequisites)

| 조건 | 설명 |
|------|------|
| Claude Code Max+ | Hook 기능 사용을 위한 구독 필수 |
| Flutter SDK | 3.19+ 설치 필수 |
| IntelliJ IDEA | 2025.2+ (Ultimate 또는 Community) |
| Android Studio | 소스 프로젝트 빌드용 |

---

## 3. Feature Specifications

### 3.1 Core Features

#### F1: 프로젝트 설정
```
- 소스 Android 프로젝트 경로 선택
- 대상 Flutter 프로젝트 경로 지정
- 변환 옵션 설정 (상태관리, 네트워크 라이브러리 등)
```

#### F2: 코드 분석 엔진
```
- Kotlin PSI를 활용한 소스 코드 파싱
- Compose UI 컴포넌트 트리 구조 분석
- 의존성 그래프 생성
- 변환 복잡도 리포트 생성
```

#### F3: 변환 엔진
```
- Kotlin → Dart 문법 변환
- Compose → Flutter Widget 매핑
- 상태관리 패턴 변환
- 리소스 파일 변환
```

#### F4: Claude Code 통합
```
- Hook 자동 설정 (.claude/settings.json 생성)
- CLI 직접 호출 (복잡한 변환에 AI 활용)
- 변환 결과 검증 및 수정 제안
```

#### F5: 변환 모드
```
- 전체 프로젝트 자동 변환
- 파일/모듈 단위 선택적 변환
- 증분 변환 (변경된 파일만)
```

#### F6: 검증 시스템
```
- Android 앱 자동 빌드 및 스크린샷 캡처
- Flutter 앱 자동 빌드 및 스크린샷 캡처
- 이미지 비교 알고리즘으로 일치율 측정
- 불일치 영역 하이라이트 및 리포트
```

### 3.2 UI Components

#### Tool Window
```
┌─────────────────────────────────────────────┐
│ Android to Flutter Porter                    │
├─────────────────────────────────────────────┤
│ Source: [____________________] [Browse]     │
│ Target: [____________________] [Browse]     │
├─────────────────────────────────────────────┤
│ [▼] Conversion Options                      │
│   ☑ State Management: [Riverpod    ▼]      │
│   ☑ Network: [Dio              ▼]          │
│   ☑ Navigation: [GoRouter      ▼]          │
├─────────────────────────────────────────────┤
│ [Convert All] [Convert Selected] [Verify]   │
├─────────────────────────────────────────────┤
│ Progress: ████████████░░░░░░░░ 60%         │
│ Converting: UserViewModel.kt → user_vm.dart │
└─────────────────────────────────────────────┘
```

#### File Tree Panel
```
┌─────────────────────────────────────────────┐
│ Source Files          │ Target Files        │
├───────────────────────┼─────────────────────┤
│ ▼ app/                │ ▼ lib/              │
│   ▼ ui/               │   ▼ ui/             │
│     ☑ HomeScreen.kt   │     home_screen.dart│
│     ☑ LoginScreen.kt  │     login_screen.dart│
│   ▼ viewmodel/        │   ▼ providers/      │
│     ☑ HomeVM.kt       │     home_provider.dart│
└───────────────────────┴─────────────────────┘
```

---

## 4. Success Criteria

### 4.1 기능적 성공 기준

| ID | 기준 | 측정 방법 | 목표값 |
|----|------|----------|--------|
| SC1 | UI 일치율 | 스크린샷 픽셀 비교 | ≥ 95% |
| SC2 | 로직 일치율 | 단위 테스트 통과율 | ≥ 98% |
| SC3 | 빌드 성공률 | Flutter 빌드 성공 | 100% |
| SC4 | 변환 커버리지 | 지원 패턴 변환율 | ≥ 90% |

### 4.2 비기능적 성공 기준

| ID | 기준 | 측정 방법 | 목표값 |
|----|------|----------|--------|
| NF1 | 변환 속도 | 1000 LOC 변환 시간 | < 60초 |
| NF2 | 메모리 사용 | 대규모 프로젝트 변환 시 | < 2GB |
| NF3 | 안정성 | 크래시 없이 완료 | 100% |

### 4.3 사용성 성공 기준

| ID | 기준 | 측정 방법 | 목표값 |
|----|------|----------|--------|
| UX1 | 초기 설정 시간 | 처음 사용자 기준 | < 5분 |
| UX2 | 변환 작업 클릭 수 | 전체 변환 기준 | < 5회 |
| UX3 | 에러 메시지 명확성 | 사용자 이해도 | 자명함 |

---

## 5. Risk Assessment

### 5.1 기술적 리스크

| 리스크 | 확률 | 영향 | 대응 전략 |
|--------|------|------|----------|
| Compose-Flutter 매핑 불완전 | 중 | 상 | 매핑 테이블 지속 확장 + AI 보완 |
| 복잡한 커스텀 컴포넌트 | 상 | 중 | AI 기반 분석 + 수동 가이드 제공 |
| Claude API 의존성 | 중 | 상 | 오프라인 폴백 모드 구현 |
| 스크린샷 비교 정확도 | 중 | 중 | 허용 오차 설정 + 영역별 비교 |

### 5.2 일정 리스크

| 리스크 | 확률 | 영향 | 대응 전략 |
|--------|------|------|----------|
| IntelliJ SDK 학습 곡선 | 중 | 중 | 공식 문서 + 예제 플러그인 참조 |
| 테스트 케이스 부족 | 중 | 상 | 실제 오픈소스 앱으로 테스트 |

---

## 6. Milestones

### Phase 1: Foundation
- [ ] 프로젝트 구조 설계
- [ ] IntelliJ Plugin 기본 프레임워크 구축
- [ ] Compose UI Tool Window 구현

### Phase 2: Analysis Engine
- [ ] Kotlin PSI 기반 코드 분석기
- [ ] Compose 컴포넌트 트리 파서
- [ ] 의존성 그래프 생성기

### Phase 3: Conversion Engine
- [ ] Kotlin → Dart 기본 변환기
- [ ] Compose → Flutter 위젯 매퍼
- [ ] 상태관리 패턴 변환기

### Phase 4: AI Integration
- [ ] Claude Code Hook 자동 설정
- [ ] CLI 통합 서비스
- [ ] AI 기반 복잡 변환 처리

### Phase 5: Verification
- [ ] 스크린샷 캡처 자동화
- [ ] 이미지 비교 엔진
- [ ] 검증 리포트 생성기

### Phase 6: Polish
- [ ] 에러 핸들링 강화
- [ ] 성능 최적화
- [ ] 문서화 및 튜토리얼

---

## 7. Stakeholders

| 역할 | 담당 | 책임 |
|------|------|------|
| Product Owner | 사용자 | 요구사항 정의, 우선순위 결정 |
| Developer | Claude AI | 설계, 구현, 테스트 |
| QA | 사용자 + AI | 검증, 피드백 |

---

## 8. Approval

- [ ] 프로젝트 범위 승인
- [ ] 성공 기준 승인
- [ ] 일정 승인

**작성일**: 2026-02-05
**버전**: 1.0

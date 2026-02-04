# Android to Flutter Porter - IntelliJ IDEA Plugin

## Project Overview

**Level**: Enterprise
**Type**: IntelliJ IDEA Plugin (Kotlin + Compose for Desktop)
**Purpose**: Android Kotlin/Compose 앱을 Flutter로 자동 포팅하는 IDE 플러그인

## Core Requirements

### 필수 조건
- Claude Code Max 이상 구독 (Hook 기능 사용)
- Flutter SDK 설치
- IntelliJ IDEA 2025.2+

### 핵심 기능
1. **소스 위치 지정**: 포팅할 Android 앱 소스 경로 선택
2. **출력 위치 지정**: Flutter 프로젝트 생성 경로 선택
3. **100% 동일 화면**: Android UI를 Flutter로 완벽 재현
4. **100% 동일 동작**: 비즈니스 로직 완벽 변환
5. **iOS 동시 지원**: Flutter의 크로스플랫폼 특성 활용
6. **Claude Code Hook 통합**: AI 기반 코드 분석 및 변환

## Tech Stack

```
Plugin Development:
- Kotlin 2.1.x
- IntelliJ Platform SDK 2025.2
- Compose for Desktop (Plugin UI)
- Gradle Kotlin DSL

AI Integration:
- Claude Code Hooks (PreToolUse, PostToolUse)
- Claude Code CLI API

Target Conversion:
- Source: Android Kotlin + Jetpack Compose
- Target: Flutter (Dart) + iOS/Android
```

## Architecture Components

```
┌─────────────────────────────────────────────────────────┐
│                 IntelliJ IDEA Plugin                     │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │  UI Layer   │  │  Analyzer   │  │  Generator  │     │
│  │  (Compose)  │  │   Engine    │  │   Engine    │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
│         │                │                │             │
│         └────────────────┼────────────────┘             │
│                          │                              │
│              ┌───────────┴───────────┐                  │
│              │   Claude Code Hook    │                  │
│              │      Integration      │                  │
│              └───────────────────────┘                  │
└─────────────────────────────────────────────────────────┘
```

## Directory Structure

```
android-to-flutter/
├── src/main/kotlin/com/zime/app/androidtoflutter/
│   ├── ui/                    # Compose UI components
│   ├── analyzer/              # Kotlin/Compose code analyzer
│   ├── converter/             # Code conversion engine
│   ├── generator/             # Flutter code generator
│   ├── hooks/                 # Claude Code hook handlers
│   ├── models/                # Data models
│   └── services/              # Plugin services
├── src/main/resources/
│   ├── META-INF/plugin.xml
│   ├── templates/             # Flutter code templates
│   └── mappings/              # Kotlin-to-Dart mappings
└── docs/
    ├── 01-plan/               # PDCA Plan documents
    ├── 02-design/             # Architecture & API design
    ├── 03-analysis/           # Gap analysis reports
    └── 04-report/             # Completion reports
```

## Coding Conventions

### Kotlin Style
- 4 spaces indentation
- Trailing commas in multiline
- Explicit return types for public APIs

### Naming
- Classes: PascalCase
- Functions/Properties: camelCase
- Constants: SCREAMING_SNAKE_CASE
- Packages: lowercase

## PDCA Status

Current Phase: **Plan**
See `docs/.pdca-status.json` for detailed status.

## Commands

```bash
# Build plugin
./gradlew build

# Run plugin in sandbox IDE
./gradlew runIde

# Run tests
./gradlew test
```

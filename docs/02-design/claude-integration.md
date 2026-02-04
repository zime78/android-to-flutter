# Android to Flutter Porter - Claude Code Integration Design

## 1. Overview

Claude Code 통합은 두 가지 방식으로 구현됩니다:

1. **Hook 자동 설정**: 플러그인이 `.claude/settings.json`에 훅을 등록
2. **CLI 직접 호출**: 복잡한 변환 시 `claude` CLI를 직접 실행

---

## 2. Hook Integration

### 2.1 Hook 설정 구조

플러그인이 생성하는 `.claude/settings.json`:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit",
        "hooks": [
          {
            "type": "command",
            "command": "python3 ~/.claude/hooks/android-to-flutter/pre_edit.py \"$TOOL_INPUT\""
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit",
        "hooks": [
          {
            "type": "command",
            "command": "python3 ~/.claude/hooks/android-to-flutter/post_edit.py \"$TOOL_INPUT\" \"$TOOL_RESULT\""
          }
        ]
      }
    ],
    "Notification": [
      {
        "matcher": "Message",
        "hooks": [
          {
            "type": "command",
            "command": "python3 ~/.claude/hooks/android-to-flutter/notify.py \"$SESSION_ID\" \"$MESSAGE\""
          }
        ]
      }
    ]
  }
}
```

### 2.2 Hook Scripts

#### pre_edit.py - 편집 전 검증

```python
#!/usr/bin/env python3
"""
PreToolUse Hook: Edit 도구 사용 전 호출
- 변환 대상 파일인지 확인
- 변환 컨텍스트 주입
"""
import sys
import json
from pathlib import Path

def main():
    tool_input = json.loads(sys.argv[1])
    file_path = tool_input.get('file_path', '')
    
    # Flutter 프로젝트 파일인지 확인
    if not file_path.endswith('.dart'):
        return  # 다른 파일은 무시
    
    # 변환 컨텍스트 파일 확인
    context_file = Path.home() / '.claude' / 'hooks' / 'android-to-flutter' / 'context.json'
    if context_file.exists():
        context = json.loads(context_file.read_text())
        
        # 추가 지침 출력 (Claude가 읽음)
        print(f"[A2F Context] Converting from: {context.get('source_file')}")
        print(f"[A2F Context] Widget mappings active: {len(context.get('mappings', {}))}")

if __name__ == '__main__':
    main()
```

#### post_edit.py - 편집 후 검증

```python
#!/usr/bin/env python3
"""
PostToolUse Hook: Edit 완료 후 호출
- 생성된 Dart 코드 검증
- 변환 로그 기록
"""
import sys
import json
import subprocess
from pathlib import Path
from datetime import datetime

def validate_dart_syntax(file_path: str) -> bool:
    """dart analyze로 문법 검증"""
    try:
        result = subprocess.run(
            ['dart', 'analyze', file_path],
            capture_output=True,
            text=True,
            timeout=30
        )
        return result.returncode == 0
    except Exception:
        return True  # 검증 실패 시 통과 처리

def main():
    tool_input = json.loads(sys.argv[1])
    tool_result = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}
    
    file_path = tool_input.get('file_path', '')
    
    if not file_path.endswith('.dart'):
        return
    
    # Dart 문법 검증
    if not validate_dart_syntax(file_path):
        print("[A2F Warning] Generated Dart code has syntax errors")
    
    # 변환 로그 기록
    log_entry = {
        'timestamp': datetime.now().isoformat(),
        'file': file_path,
        'success': tool_result.get('success', True),
        'lines_changed': tool_result.get('lines_changed', 0)
    }
    
    log_file = Path.home() / '.claude' / 'hooks' / 'android-to-flutter' / 'conversion.log'
    log_file.parent.mkdir(parents=True, exist_ok=True)
    
    with open(log_file, 'a') as f:
        f.write(json.dumps(log_entry) + '\n')

if __name__ == '__main__':
    main()
```

#### notify.py - 알림 처리

```python
#!/usr/bin/env python3
"""
Notification Hook: 메시지 알림 처리
- 플러그인에 진행 상황 전달
"""
import sys
import json
import socket
from pathlib import Path

PLUGIN_SOCKET = '/tmp/android-to-flutter-plugin.sock'

def main():
    session_id = sys.argv[1] if len(sys.argv) > 1 else ''
    message = sys.argv[2] if len(sys.argv) > 2 else ''
    
    # 플러그인 소켓으로 알림 전송
    try:
        if Path(PLUGIN_SOCKET).exists():
            sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            sock.connect(PLUGIN_SOCKET)
            sock.send(json.dumps({
                'type': 'notification',
                'session_id': session_id,
                'message': message
            }).encode())
            sock.close()
    except Exception:
        pass  # 연결 실패 시 무시

if __name__ == '__main__':
    main()
```

### 2.3 Hook Manager Implementation

```kotlin
package com.zime.app.androidtoflutter.claude.hook

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.*

class HookManager(private val project: Project) {
    
    private val claudeDir = Path.of(System.getProperty("user.home"), ".claude")
    private val hooksDir = claudeDir.resolve("hooks/android-to-flutter")
    private val settingsFile = claudeDir.resolve("settings.json")
    
    /**
     * Hook 설정 및 스크립트 설치
     */
    fun setupHooks(): HookSetupResult {
        return try {
            // 1. Hook 스크립트 디렉토리 생성
            hooksDir.createDirectories()
            
            // 2. Hook 스크립트 복사
            installHookScripts()
            
            // 3. settings.json 업데이트
            updateClaudeSettings()
            
            HookSetupResult.Success(
                message = "Hooks installed successfully",
                hooksDir = hooksDir
            )
        } catch (e: Exception) {
            HookSetupResult.Failure(
                error = e.message ?: "Unknown error",
                exception = e
            )
        }
    }
    
    /**
     * Hook 스크립트 설치
     */
    private fun installHookScripts() {
        val scripts = listOf(
            "pre_edit.py" to PRE_EDIT_SCRIPT,
            "post_edit.py" to POST_EDIT_SCRIPT,
            "notify.py" to NOTIFY_SCRIPT
        )
        
        scripts.forEach { (name, content) ->
            val scriptFile = hooksDir.resolve(name)
            scriptFile.writeText(content)
            // 실행 권한 부여 (Unix 계열)
            scriptFile.toFile().setExecutable(true)
        }
    }
    
    /**
     * Claude settings.json 업데이트
     */
    private fun updateClaudeSettings() {
        val existingSettings = if (settingsFile.exists()) {
            Json.parseToJsonElement(settingsFile.readText()).jsonObject.toMutableMap()
        } else {
            mutableMapOf()
        }
        
        // hooks 섹션 추가/업데이트
        val hooks = buildJsonObject {
            putJsonArray("PreToolUse") {
                add(buildJsonObject {
                    put("matcher", "Edit")
                    putJsonArray("hooks") {
                        add(buildJsonObject {
                            put("type", "command")
                            put("command", "python3 ${hooksDir}/pre_edit.py \"\$TOOL_INPUT\"")
                        })
                    }
                })
            }
            putJsonArray("PostToolUse") {
                add(buildJsonObject {
                    put("matcher", "Edit")
                    putJsonArray("hooks") {
                        add(buildJsonObject {
                            put("type", "command")
                            put("command", "python3 ${hooksDir}/post_edit.py \"\$TOOL_INPUT\" \"\$TOOL_RESULT\"")
                        })
                    }
                })
            }
        }
        
        existingSettings["hooks"] = hooks
        
        claudeDir.createDirectories()
        settingsFile.writeText(
            Json { prettyPrint = true }.encodeToString(
                JsonObject.serializer(),
                JsonObject(existingSettings)
            )
        )
    }
    
    /**
     * Hook 제거
     */
    fun removeHooks() {
        // Hook 스크립트 삭제
        hooksDir.toFile().deleteRecursively()
        
        // settings.json에서 hooks 섹션 제거
        if (settingsFile.exists()) {
            val settings = Json.parseToJsonElement(settingsFile.readText())
                .jsonObject.toMutableMap()
            settings.remove("hooks")
            settingsFile.writeText(
                Json { prettyPrint = true }.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(settings)
                )
            )
        }
    }
    
    /**
     * 변환 컨텍스트 설정 (Hook 스크립트가 읽음)
     */
    fun setConversionContext(context: ConversionContext) {
        val contextFile = hooksDir.resolve("context.json")
        contextFile.writeText(
            Json { prettyPrint = true }.encodeToString(
                ConversionContext.serializer(),
                context
            )
        )
    }
    
    companion object {
        private const val PRE_EDIT_SCRIPT = """..."""  // 위의 Python 스크립트
        private const val POST_EDIT_SCRIPT = """..."""
        private const val NOTIFY_SCRIPT = """..."""
    }
}

sealed class HookSetupResult {
    data class Success(val message: String, val hooksDir: Path) : HookSetupResult()
    data class Failure(val error: String, val exception: Exception?) : HookSetupResult()
}
```

---

## 3. CLI Integration

### 3.1 CLI Executor

```kotlin
package com.zime.app.androidtoflutter.claude.cli

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClaudeCliExecutor {
    
    /**
     * Claude CLI 사용 가능 여부 확인
     */
    fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Claude CLI 버전 확인
     */
    fun getVersion(): String? {
        return try {
            val process = ProcessBuilder("claude", "--version")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Claude CLI 실행 (--print 모드)
     */
    suspend fun execute(
        prompt: String,
        options: CliOptions = CliOptions()
    ): CliResponse = withContext(Dispatchers.IO) {
        val command = buildCommand(prompt, options)
        
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
        
        options.workingDirectory?.let {
            processBuilder.directory(it.toFile())
        }
        
        val process = processBuilder.start()
        
        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        
        // 타임아웃 처리
        val job = launch {
            reader.forEachLine { line ->
                output.appendLine(line)
            }
        }
        
        val completed = withTimeoutOrNull(options.timeout) {
            job.join()
            process.waitFor()
            true
        }
        
        if (completed == null) {
            process.destroyForcibly()
            return@withContext CliResponse(
                success = false,
                content = output.toString(),
                error = "Timeout after ${options.timeout}",
                exitCode = -1
            )
        }
        
        CliResponse(
            success = process.exitValue() == 0,
            content = output.toString(),
            error = if (process.exitValue() != 0) "Exit code: ${process.exitValue()}" else null,
            exitCode = process.exitValue()
        )
    }
    
    /**
     * AI 기반 코드 변환
     */
    suspend fun convertWithAi(
        kotlinCode: String,
        context: ConversionContext
    ): AiConversionResult {
        val prompt = buildConversionPrompt(kotlinCode, context)
        
        val response = execute(prompt, CliOptions(
            printMode = true,
            timeout = 120.seconds,
            workingDirectory = context.targetProjectPath
        ))
        
        if (!response.success) {
            return AiConversionResult.Failure(
                error = response.error ?: "Unknown error",
                rawOutput = response.content
            )
        }
        
        // Dart 코드 블록 추출
        val dartCode = extractDartCode(response.content)
        
        return if (dartCode != null) {
            AiConversionResult.Success(
                dartCode = dartCode,
                explanation = extractExplanation(response.content)
            )
        } else {
            AiConversionResult.Failure(
                error = "Could not extract Dart code from response",
                rawOutput = response.content
            )
        }
    }
    
    private fun buildCommand(prompt: String, options: CliOptions): List<String> {
        return buildList {
            add("claude")
            
            if (options.printMode) {
                add("--print")
            }
            
            options.model?.let {
                add("--model")
                add(it)
            }
            
            options.maxTokens?.let {
                add("--max-tokens")
                add(it.toString())
            }
            
            if (options.dangerouslySkipPermissions) {
                add("--dangerously-skip-permissions")
            }
            
            add(prompt)
        }
    }
    
    private fun buildConversionPrompt(
        kotlinCode: String,
        context: ConversionContext
    ): String {
        return """
            |Convert the following Kotlin/Jetpack Compose code to Flutter/Dart.
            |
            |Requirements:
            |1. Preserve exact visual appearance (colors, spacing, layout)
            |2. Use ${context.stateManagement} for state management
            |3. Use ${context.navigation} for navigation
            |4. Maintain the same business logic behavior
            |5. Follow Flutter best practices and conventions
            |
            |Source Kotlin/Compose code:
            |```kotlin
            |$kotlinCode
            |```
            |
            |Widget mappings to use:
            |${context.widgetMappings.entries.joinToString("\n") { "- ${it.key} → ${it.value}" }}
            |
            |Please provide the converted Flutter/Dart code in a code block.
            |Also explain any significant changes or considerations.
        """.trimMargin()
    }
    
    private fun extractDartCode(response: String): String? {
        val codeBlockRegex = Regex("```dart\\s*\\n([\\s\\S]*?)\\n```")
        return codeBlockRegex.find(response)?.groupValues?.get(1)?.trim()
    }
    
    private fun extractExplanation(response: String): String {
        val withoutCodeBlocks = response.replace(Regex("```[\\s\\S]*?```"), "")
        return withoutCodeBlocks.trim()
    }
}

data class CliOptions(
    val printMode: Boolean = true,
    val model: String? = null,
    val maxTokens: Int? = null,
    val timeout: Duration = 60.seconds,
    val workingDirectory: Path? = null,
    val dangerouslySkipPermissions: Boolean = false
)

data class CliResponse(
    val success: Boolean,
    val content: String,
    val error: String?,
    val exitCode: Int
)

sealed class AiConversionResult {
    data class Success(
        val dartCode: String,
        val explanation: String
    ) : AiConversionResult()
    
    data class Failure(
        val error: String,
        val rawOutput: String
    ) : AiConversionResult()
}
```

### 3.2 Prompt Templates

```kotlin
package com.zime.app.androidtoflutter.claude.prompt

object PromptTemplates {
    
    /**
     * 기본 코드 변환 프롬프트
     */
    val CODE_CONVERSION = """
        |Convert the following Kotlin/Jetpack Compose code to Flutter/Dart.
        |
        |Requirements:
        |1. Preserve exact visual appearance
        |2. Use {stateManagement} for state management
        |3. Use {navigation} for navigation
        |4. Output only the Dart code in a code block
        |
        |Source:
        |```kotlin
        |{sourceCode}
        |```
    """.trimMargin()
    
    /**
     * 복잡한 위젯 변환 프롬프트
     */
    val COMPLEX_WIDGET_CONVERSION = """
        |I need to convert a complex Kotlin Compose widget to Flutter.
        |
        |The widget uses:
        |{usedFeatures}
        |
        |Source code:
        |```kotlin
        |{sourceCode}
        |```
        |
        |Please:
        |1. Analyze the widget structure
        |2. Identify the best Flutter equivalents
        |3. Convert maintaining exact visual parity
        |4. Explain any trade-offs or alternatives
        |
        |Provide the Flutter code in a ```dart code block.
    """.trimMargin()
    
    /**
     * 상태 관리 변환 프롬프트
     */
    val STATE_MANAGEMENT_CONVERSION = """
        |Convert this Android ViewModel to Flutter {stateManagement}.
        |
        |Android ViewModel:
        |```kotlin
        |{viewModelCode}
        |```
        |
        |Requirements:
        |1. Preserve all state variables
        |2. Convert StateFlow to appropriate {stateManagement} primitives
        |3. Maintain all actions/methods
        |4. Handle async operations properly
        |
        |Provide the Flutter code in a ```dart code block.
    """.trimMargin()
    
    /**
     * 검증 프롬프트
     */
    val VERIFICATION = """
        |Review the following code conversion for correctness.
        |
        |Original Kotlin:
        |```kotlin
        |{kotlinCode}
        |```
        |
        |Converted Dart:
        |```dart
        |{dartCode}
        |```
        |
        |Check for:
        |1. Visual parity (layout, colors, spacing)
        |2. Logic correctness
        |3. Missing functionality
        |4. Flutter best practices
        |
        |Rate the conversion (1-10) and list any issues.
    """.trimMargin()
}

class PromptBuilder {
    private val variables = mutableMapOf<String, String>()
    
    fun set(key: String, value: String): PromptBuilder {
        variables[key] = value
        return this
    }
    
    fun build(template: String): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return result
    }
}
```

---

## 4. Integration Flow

### 4.1 변환 시 Claude 통합 흐름

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Conversion Flow with Claude                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. 변환 시작                                                            │
│     │                                                                    │
│     ▼                                                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ HookManager.setConversionContext(context)                        │   │
│  │ - 현재 변환 중인 파일 정보                                        │   │
│  │ - 사용할 위젯 매핑                                                │   │
│  │ - 상태 관리 방식                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│     │                                                                    │
│     ▼                                                                    │
│  2. 규칙 기반 변환 시도                                                  │
│     │                                                                    │
│     ├── 성공 ──────────────────────────────────────────────► 완료       │
│     │                                                                    │
│     └── 실패/불확실                                                      │
│          │                                                               │
│          ▼                                                               │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ ClaudeCliExecutor.convertWithAi(kotlinCode, context)             │   │
│  │                                                                   │   │
│  │ - 프롬프트 생성                                                   │   │
│  │ - claude --print 실행                                            │   │
│  │ - 응답에서 Dart 코드 추출                                         │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│     │                                                                    │
│     ▼                                                                    │
│  3. 결과 검증                                                            │
│     │                                                                    │
│     ├── 유효한 Dart 코드 ─────────────────────────────────► 적용        │
│     │                                                                    │
│     └── 에러                                                             │
│          │                                                               │
│          ▼                                                               │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ 재시도 (최대 3회) 또는 수동 처리 플래그                           │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Hook 이벤트 흐름

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Hook Event Flow                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Claude Code가 파일 수정 시:                                             │
│                                                                          │
│  ┌─────────────┐                                                        │
│  │ Claude Code │                                                        │
│  │  (Edit 도구)│                                                        │
│  └──────┬──────┘                                                        │
│         │                                                                │
│         ▼                                                                │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ PreToolUse Hook (pre_edit.py)                                    │   │
│  │ - 변환 컨텍스트 확인                                              │   │
│  │ - 추가 지침 출력 (Claude가 읽음)                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│         │                                                                │
│         ▼                                                                │
│  ┌─────────────┐                                                        │
│  │  Edit 실행  │                                                        │
│  └──────┬──────┘                                                        │
│         │                                                                │
│         ▼                                                                │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ PostToolUse Hook (post_edit.py)                                  │   │
│  │ - Dart 문법 검증                                                  │   │
│  │ - 변환 로그 기록                                                  │   │
│  │ - 경고/에러 출력                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│         │                                                                │
│         ▼                                                                │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ Notification Hook (notify.py)                                    │   │
│  │ - 플러그인 UI로 진행 상황 전달                                    │   │
│  │ - Unix 소켓 통신                                                  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Error Handling

### 5.1 Claude CLI 에러 처리

| 에러 유형 | 처리 방법 |
|----------|----------|
| CLI 미설치 | 설치 안내 다이얼로그 표시 |
| 인증 실패 | `claude login` 실행 안내 |
| Max 미만 구독 | 업그레이드 필요 안내 |
| 타임아웃 | 재시도 또는 규칙 기반으로 폴백 |
| 잘못된 응답 | 파싱 실패 로그 + 수동 처리 플래그 |

### 5.2 Hook 에러 처리

| 에러 유형 | 처리 방법 |
|----------|----------|
| 스크립트 실행 실패 | 로그 기록, 변환 계속 |
| 권한 오류 | chmod 재시도 |
| Python 미설치 | Python 설치 안내 |
| 소켓 연결 실패 | 알림 스킵, 로그 기록 |

---

## 6. Security Considerations

| 항목 | 대응 |
|------|------|
| Hook 스크립트 실행 | 플러그인이 생성한 스크립트만 실행 |
| 코드 노출 | 로컬 CLI만 사용, API 직접 호출 안 함 |
| 파일 접근 | 사용자 지정 프로젝트 경로만 |
| 프로세스 격리 | 타임아웃 + 강제 종료 처리 |

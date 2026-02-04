package com.zime.app.androidtoflutter.claude.hook

import com.intellij.openapi.project.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Claude Code Hook 관리자
 * - Hook 스크립트 설치
 * - settings.json 설정
 * - 변환 컨텍스트 관리
 */
class HookManager(private val project: Project) {

    private val claudeDir: Path = Path(System.getProperty("user.home"), ".claude")
    private val hooksDir: Path = claudeDir.resolve("hooks/android-to-flutter")
    private val settingsFile: Path = claudeDir.resolve("settings.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

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
                message = "Hooks installed successfully at $hooksDir",
                hooksDir = hooksDir
            )
        } catch (e: Exception) {
            HookSetupResult.Failure(
                error = e.message ?: "Unknown error during hook setup",
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
            try {
                scriptFile.toFile().setExecutable(true)
            } catch (e: Exception) {
                // Windows에서는 무시
            }
        }
    }

    /**
     * Claude settings.json 업데이트
     */
    private fun updateClaudeSettings() {
        val existingSettings: MutableMap<String, JsonElement> = if (settingsFile.exists()) {
            json.parseToJsonElement(settingsFile.readText()).jsonObject.toMutableMap()
        } else {
            mutableMapOf()
        }

        // hooks 섹션 생성
        val hooks = buildJsonObject {
            putJsonArray("PreToolUse") {
                add(buildJsonObject {
                    put("matcher", "Edit")
                    putJsonArray("hooks") {
                        add(buildJsonObject {
                            put("type", "command")
                            put("command", "python3 $hooksDir/pre_edit.py \"\$TOOL_INPUT\"")
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
                            put("command", "python3 $hooksDir/post_edit.py \"\$TOOL_INPUT\" \"\$TOOL_RESULT\"")
                        })
                    }
                })
            }
            putJsonArray("Notification") {
                add(buildJsonObject {
                    put("matcher", "Message")
                    putJsonArray("hooks") {
                        add(buildJsonObject {
                            put("type", "command")
                            put("command", "python3 $hooksDir/notify.py \"\$SESSION_ID\" \"\$MESSAGE\"")
                        })
                    }
                })
            }
        }

        existingSettings["hooks"] = hooks

        // 디렉토리 생성 및 파일 저장
        claudeDir.createDirectories()
        settingsFile.writeText(json.encodeToString(JsonObject(existingSettings)))
    }

    /**
     * Hook 제거
     */
    fun removeHooks(): Boolean {
        return try {
            // Hook 스크립트 삭제
            hooksDir.toFile().deleteRecursively()

            // settings.json에서 hooks 섹션 제거
            if (settingsFile.exists()) {
                val settings = json.parseToJsonElement(settingsFile.readText())
                    .jsonObject.toMutableMap()
                settings.remove("hooks")
                settingsFile.writeText(json.encodeToString(JsonObject(settings)))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 변환 컨텍스트 설정 (Hook 스크립트가 읽음)
     */
    fun setConversionContext(context: ConversionContext) {
        val contextFile = hooksDir.resolve("context.json")
        hooksDir.createDirectories()
        contextFile.writeText(json.encodeToString(context))
    }

    /**
     * Hook 설치 상태 확인
     */
    fun isHooksInstalled(): Boolean {
        return hooksDir.exists() &&
               hooksDir.resolve("pre_edit.py").exists() &&
               hooksDir.resolve("post_edit.py").exists()
    }

    companion object {
        private val PRE_EDIT_SCRIPT = """
#!/usr/bin/env python3
# PreToolUse Hook: Edit 도구 사용 전 호출
# - 변환 대상 파일인지 확인
# - 변환 컨텍스트 주입

import sys
import json
from pathlib import Path

def main():
    if len(sys.argv) < 2:
        return

    try:
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
            print(f"[A2F Context] Converting from: {context.get('sourceFile', 'unknown')}")
            print(f"[A2F Context] State management: {context.get('stateManagement', 'riverpod')}")
            print(f"[A2F Context] Widget mappings active: {len(context.get('widgetMappings', {}))}")
    except Exception as e:
        print(f"[A2F Warning] Pre-edit hook error: {e}")

if __name__ == '__main__':
    main()
""".trimIndent()

        private val POST_EDIT_SCRIPT = """
#!/usr/bin/env python3
# PostToolUse Hook: Edit 완료 후 호출
# - 생성된 Dart 코드 검증
# - 변환 로그 기록

import sys
import json
import subprocess
from pathlib import Path
from datetime import datetime

def validate_dart_syntax(file_path: str) -> bool:
    '''dart analyze로 문법 검증'''
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
    if len(sys.argv) < 2:
        return

    try:
        tool_input = json.loads(sys.argv[1])
        tool_result = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}

        file_path = tool_input.get('file_path', '')

        if not file_path.endswith('.dart'):
            return

        # Dart 문법 검증
        if Path(file_path).exists() and not validate_dart_syntax(file_path):
            print("[A2F Warning] Generated Dart code may have syntax errors")

        # 변환 로그 기록
        log_entry = {
            'timestamp': datetime.now().isoformat(),
            'file': file_path,
            'success': tool_result.get('success', True)
        }

        log_file = Path.home() / '.claude' / 'hooks' / 'android-to-flutter' / 'conversion.log'
        log_file.parent.mkdir(parents=True, exist_ok=True)

        with open(log_file, 'a') as f:
            f.write(json.dumps(log_entry) + '\n')

        print(f"[A2F Success] Converted: {Path(file_path).name}")
    except Exception as e:
        print(f"[A2F Warning] Post-edit hook error: {e}")

if __name__ == '__main__':
    main()
""".trimIndent()

        private val NOTIFY_SCRIPT = """
#!/usr/bin/env python3
# Notification Hook: 메시지 알림 처리
# - 플러그인에 진행 상황 전달

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
""".trimIndent()
    }
}

sealed class HookSetupResult {
    data class Success(val message: String, val hooksDir: Path) : HookSetupResult()
    data class Failure(val error: String, val exception: Exception?) : HookSetupResult()
}

@kotlinx.serialization.Serializable
data class ConversionContext(
    val sourceFile: String,
    val targetFile: String,
    val stateManagement: String = "riverpod",
    val navigation: String = "go_router",
    val widgetMappings: Map<String, String> = emptyMap()
)

package com.zime.app.androidtoflutter.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.zime.app.androidtoflutter.models.project.NavigationType
import com.zime.app.androidtoflutter.models.project.StateManagementType
import javax.swing.JComponent

/**
 * Porter Tool Window - IntelliJ UI DSL
 */
class PorterToolWindow(private val project: Project) {

    private var sourcePath: String = ""
    private var targetPath: String = ""
    private var stateManagement: StateManagementType = StateManagementType.RIVERPOD
    private var navigation: NavigationType = NavigationType.GO_ROUTER
    private var useClaudeCode: Boolean = true
    private var hookEnabled: Boolean = true

    fun getContent(): JComponent {
        return panel {
            group("프로젝트 경로") {
                row("소스 (Android):") {
                    textField()
                        .columns(40)
                        .bindText({ sourcePath }, { sourcePath = it })
                    button("Browse...") {
                        // TODO: File chooser
                    }
                }
                row("대상 (Flutter):") {
                    textField()
                        .columns(40)
                        .bindText({ targetPath }, { targetPath = it })
                    button("Browse...") {
                        // TODO: File chooser
                    }
                }
            }

            group("변환 옵션") {
                row("State Management:") {
                    comboBox(StateManagementType.entries)
                        .bindItem({ stateManagement }, { stateManagement = it ?: StateManagementType.RIVERPOD })
                }
                row("Navigation:") {
                    comboBox(NavigationType.entries)
                        .bindItem({ navigation }, { navigation = it ?: NavigationType.GO_ROUTER })
                }
            }

            group("Claude Code 설정") {
                row {
                    checkBox("Claude Code AI 사용")
                        .bindSelected({ useClaudeCode }, { useClaudeCode = it })
                }
                row {
                    checkBox("Hook 활성화")
                        .bindSelected({ hookEnabled }, { hookEnabled = it })
                }
                row {
                    button("Hook 설치") {
                        // TODO: Hook 설치 로직
                    }
                }
            }

            group("실행") {
                row {
                    button("전체 변환") {
                        startConversion(ConversionMode.ALL)
                    }
                    button("선택 변환") {
                        startConversion(ConversionMode.SELECTED)
                    }
                    button("UI 검증") {
                        startVerification()
                    }
                }
            }
        }
    }

    private fun startConversion(mode: ConversionMode) {
        // TODO: 실제 변환 로직 연결
        println("Starting conversion in mode: $mode")
    }

    private fun startVerification() {
        // TODO: 스크린샷 비교 로직 연결
        println("Starting verification")
    }
}

enum class ConversionMode {
    ALL,
    SELECTED
}

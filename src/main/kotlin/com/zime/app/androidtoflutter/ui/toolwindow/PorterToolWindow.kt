package com.zime.app.androidtoflutter.ui.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import com.zime.app.androidtoflutter.models.project.AiSettings
import com.zime.app.androidtoflutter.models.project.ConversionConfig
import com.zime.app.androidtoflutter.models.project.ConversionOptions
import com.zime.app.androidtoflutter.models.project.NavigationType
import com.zime.app.androidtoflutter.models.project.StateManagementType
import com.zime.app.androidtoflutter.services.ConversionService
import com.zime.app.androidtoflutter.verification.ScreenConfig
import com.zime.app.androidtoflutter.verification.VerificationConfig
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

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

    private val conversionService: ConversionService by lazy {
        project.service<ConversionService>()
    }

    fun getContent(): JComponent {
        return panel {
            group("프로젝트 경로") {
                row("소스 (Android):") {
                    textField()
                        .columns(40)
                        .bindText({ sourcePath }, { sourcePath = it })
                    button("Browse...") {
                        chooseDirectory("Android 프로젝트 선택") { path ->
                            sourcePath = path
                        }
                    }
                }
                row("대상 (Flutter):") {
                    textField()
                        .columns(40)
                        .bindText({ targetPath }, { targetPath = it })
                    button("Browse...") {
                        chooseDirectory("Flutter 프로젝트 위치 선택") { path ->
                            targetPath = path
                        }
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
                        installHooks()
                    }
                    button("Hook 제거") {
                        removeHooks()
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

            group("상태") {
                row {
                    label("Claude Code: ${if (conversionService.isClaudeAvailable()) "사용 가능" else "미감지"}")
                }
                row {
                    label("Hook 상태: ${if (conversionService.isHooksInstalled()) "설치됨" else "미설치"}")
                }
            }
        }
    }

    /**
     * 디렉토리 선택 다이얼로그
     */
    private fun chooseDirectory(title: String, onSelected: (String) -> Unit) {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(title)
            .withDescription("변환할 프로젝트 디렉토리를 선택하세요")

        FileChooser.chooseFile(descriptor, project, null) { virtualFile ->
            onSelected(virtualFile.path)
        }
    }

    /**
     * Hook 설치
     */
    private fun installHooks() {
        try {
            conversionService.setupHooks()
            Messages.showInfoMessage(
                project,
                "Hook이 성공적으로 설치되었습니다.",
                "Hook 설치 완료"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Hook 설치 실패: ${e.message}",
                "오류"
            )
        }
    }

    /**
     * Hook 제거
     */
    private fun removeHooks() {
        try {
            conversionService.removeHooks()
            Messages.showInfoMessage(
                project,
                "Hook이 제거되었습니다.",
                "Hook 제거 완료"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Hook 제거 실패: ${e.message}",
                "오류"
            )
        }
    }

    /**
     * 변환 시작
     */
    private fun startConversion(mode: ConversionMode) {
        // 입력 검증
        if (sourcePath.isBlank()) {
            Messages.showWarningDialog(
                project,
                "소스 프로젝트 경로를 선택해주세요.",
                "경로 미설정"
            )
            return
        }

        if (targetPath.isBlank()) {
            Messages.showWarningDialog(
                project,
                "대상 프로젝트 경로를 선택해주세요.",
                "경로 미설정"
            )
            return
        }

        val sourcePathObj = Path(sourcePath)
        if (!sourcePathObj.exists()) {
            Messages.showErrorDialog(
                project,
                "소스 경로가 존재하지 않습니다: $sourcePath",
                "경로 오류"
            )
            return
        }

        // 변환 설정 생성
        val config = ConversionConfig(
            sourcePathString = sourcePath,
            targetPathString = targetPath,
            options = ConversionOptions(
                stateManagement = stateManagement,
                navigation = navigation
            ),
            aiSettings = AiSettings(
                useClaudeCode = useClaudeCode,
                hookEnabled = hookEnabled
            )
        )

        // 백그라운드 작업으로 변환 실행
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Android → Flutter 변환 중...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                runBlocking {
                    try {
                        val result = conversionService.convertProject(
                            sourcePath = sourcePathObj,
                            targetPath = Path(targetPath),
                            config = config
                        ) { message, progress ->
                            indicator.text = message
                            indicator.fraction = progress / 100.0
                        }

                        // UI 스레드에서 결과 표시
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            if (result.success) {
                                Messages.showInfoMessage(
                                    project,
                                    """
                                    |변환이 완료되었습니다!
                                    |
                                    |변환된 파일: ${result.outputFiles.size}개
                                    |총 라인 수: ${result.stats.totalLines}줄
                                    |AI 보조 라인: ${result.stats.aiAssistedLines}줄
                                    |소요 시간: ${result.stats.durationMs / 1000}초
                                    |
                                    |출력 경로: $targetPath
                                    """.trimMargin(),
                                    "변환 완료"
                                )
                            } else {
                                Messages.showErrorDialog(
                                    project,
                                    "변환 중 오류 발생:\n${result.errors.joinToString("\n")}",
                                    "변환 실패"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "변환 중 예외 발생: ${e.message}",
                                "오류"
                            )
                        }
                    }
                }
            }
        })
    }

    /**
     * UI 검증 시작
     */
    private fun startVerification() {
        if (targetPath.isBlank()) {
            Messages.showWarningDialog(
                project,
                "Flutter 프로젝트 경로를 선택해주세요.",
                "경로 미설정"
            )
            return
        }

        val targetPathObj = Path(targetPath)
        if (!targetPathObj.exists()) {
            Messages.showErrorDialog(
                project,
                "대상 경로가 존재하지 않습니다: $targetPath",
                "경로 오류"
            )
            return
        }

        // 화면 목록 자동 감지 (lib 디렉토리의 screen/page 파일들)
        val screens = detectScreens(targetPathObj)

        if (screens.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "검증할 화면을 찾을 수 없습니다.\nFlutter 프로젝트의 lib 디렉토리에 screen 또는 page 파일이 있는지 확인해주세요.",
                "화면 미발견"
            )
            return
        }

        val verificationConfig = VerificationConfig(
            screens = screens,
            flutterProjectPath = targetPathObj,
            captureAndroid = sourcePath.isNotBlank(),
            captureFlutter = true,
            generateReport = true
        )

        // 백그라운드 작업으로 검증 실행
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "UI 검증 중...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                runBlocking {
                    try {
                        val result = conversionService.verifyConversion(
                            config = verificationConfig
                        ) { message, progress ->
                            indicator.text = message
                            indicator.fraction = progress / 100.0
                        }

                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            if (result.success) {
                                Messages.showInfoMessage(
                                    project,
                                    """
                                    |UI 검증이 완료되었습니다!
                                    |
                                    |전체 점수: ${String.format("%.1f", result.overallScore)}%
                                    |검증 화면: ${result.totalScreens}개
                                    |통과: ${result.passedScreens}개
                                    |소요 시간: ${result.verificationTimeMs / 1000}초
                                    |
                                    |${result.reportPath?.let { "보고서: $it" } ?: ""}
                                    """.trimMargin(),
                                    "검증 완료"
                                )
                            } else {
                                Messages.showErrorDialog(
                                    project,
                                    "검증 실패: ${result.error}",
                                    "검증 오류"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "검증 중 예외 발생: ${e.message}",
                                "오류"
                            )
                        }
                    }
                }
            }
        })
    }

    /**
     * Flutter 프로젝트에서 화면 파일 자동 감지
     */
    private fun detectScreens(projectPath: Path): List<ScreenConfig> {
        val libPath = projectPath.resolve("lib")
        if (!libPath.exists()) return emptyList()

        val screens = mutableListOf<ScreenConfig>()

        // screen, page, view 패턴의 파일 찾기
        val screenPatterns = listOf("screen", "page", "view")

        fun searchDirectory(dir: Path) {
            try {
                dir.listDirectoryEntries().forEach { entry ->
                    if (entry.toFile().isDirectory) {
                        searchDirectory(entry)
                    } else if (entry.name.endsWith(".dart")) {
                        val fileName = entry.name.lowercase()
                        if (screenPatterns.any { fileName.contains(it) }) {
                            val screenName = entry.name
                                .removeSuffix(".dart")
                                .replace("_screen", "")
                                .replace("_page", "")
                                .replace("_view", "")

                            screens.add(ScreenConfig(
                                name = screenName,
                                flutterRoute = "/$screenName"
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                // 디렉토리 접근 실패 시 무시
            }
        }

        searchDirectory(libPath)
        return screens
    }
}

enum class ConversionMode {
    ALL,
    SELECTED
}

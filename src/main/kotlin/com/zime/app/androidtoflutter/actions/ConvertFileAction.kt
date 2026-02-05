package com.zime.app.androidtoflutter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.zime.app.androidtoflutter.models.project.AiSettings
import com.zime.app.androidtoflutter.models.project.ConversionConfig
import com.zime.app.androidtoflutter.models.project.FileType
import com.zime.app.androidtoflutter.models.project.SourceFile
import com.zime.app.androidtoflutter.services.ConversionService
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 현재 파일 변환 액션
 */
class ConvertFileAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // Kotlin 파일인지 확인
        if (!virtualFile.name.endsWith(".kt")) {
            Messages.showWarningDialog(
                project,
                "Kotlin 파일만 변환할 수 있습니다.",
                "Android to Flutter Porter"
            )
            return
        }

        // 저장되지 않은 변경사항 저장
        FileDocumentManager.getInstance().saveAllDocuments()

        val conversionService = project.service<ConversionService>()

        // Claude 사용 가능 여부 확인
        val useAi = if (!conversionService.isClaudeAvailable()) {
            val proceed = Messages.showYesNoDialog(
                project,
                "Claude Code CLI가 감지되지 않았습니다.\n" +
                "규칙 기반 변환만 수행됩니다. 계속하시겠습니까?",
                "Claude Code 미감지",
                Messages.getWarningIcon()
            )
            if (proceed != Messages.YES) return
            false
        } else {
            true
        }

        // 출력 디렉토리 선택
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Flutter 출력 디렉토리 선택")
            .withDescription("변환된 Dart 파일을 저장할 디렉토리를 선택하세요")

        FileChooser.chooseFile(descriptor, project, null) { selectedDir ->
            val outputDir = selectedDir.path

            // 소스 파일 생성
            val sourceFile = SourceFile(
                pathString = virtualFile.path,
                type = FileType.KOTLIN,
                relativePath = virtualFile.name
            )

            // 변환 설정 생성
            val config = ConversionConfig(
                sourcePathString = virtualFile.parent?.path ?: virtualFile.path,
                targetPathString = outputDir,
                aiSettings = AiSettings(
                    useClaudeCode = useAi,
                    cliEnabled = useAi
                )
            )

            // 백그라운드 작업으로 변환 실행
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "파일 변환 중: ${virtualFile.name}",
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false

                    runBlocking {
                        try {
                            val result = conversionService.convertFile(
                                sourceFile = sourceFile,
                                config = config
                            ) { progress, message ->
                                indicator.fraction = progress.toDouble()
                                indicator.text = message
                            }

                            ApplicationManager.getApplication().invokeLater {
                                if (result.success) {
                                    val outputFile = result.outputFiles.firstOrNull()
                                    val outputPath = outputFile?.pathString ?: "알 수 없음"

                                    // 출력 파일 저장
                                    outputFile?.let {
                                        try {
                                            File(it.pathString).apply {
                                                parentFile?.mkdirs()
                                                writeText(it.content)
                                            }
                                        } catch (e: Exception) {
                                            Messages.showErrorDialog(
                                                project,
                                                "파일 저장 실패: ${e.message}",
                                                "오류"
                                            )
                                            return@invokeLater
                                        }
                                    }

                                    Messages.showInfoMessage(
                                        project,
                                        """
                                        |파일 변환이 완료되었습니다!
                                        |
                                        |원본: ${virtualFile.name}
                                        |출력: $outputPath
                                        |
                                        |변환 방식: ${outputFile?.generatedBy?.name ?: "RULE_BASED"}
                                        |원본 라인: ${result.stats.totalLines}줄
                                        |변환 라인: ${result.stats.convertedLines}줄
                                        |소요 시간: ${result.stats.durationMs}ms
                                        """.trimMargin(),
                                        "변환 완료"
                                    )
                                } else {
                                    Messages.showErrorDialog(
                                        project,
                                        "변환 실패:\n${result.errors.joinToString("\n")}",
                                        "오류"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            ApplicationManager.getApplication().invokeLater {
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
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.name?.endsWith(".kt") == true
    }
}

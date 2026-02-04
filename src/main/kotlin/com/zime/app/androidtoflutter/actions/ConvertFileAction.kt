package com.zime.app.androidtoflutter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.zime.app.androidtoflutter.models.project.FileType
import com.zime.app.androidtoflutter.models.project.SourceFile
import com.zime.app.androidtoflutter.services.ConversionService
import kotlinx.coroutines.runBlocking

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
        if (!conversionService.isClaudeAvailable()) {
            val proceed = Messages.showYesNoDialog(
                project,
                "Claude Code CLI가 감지되지 않았습니다.\n" +
                "규칙 기반 변환만 수행됩니다. 계속하시겠습니까?",
                "Claude Code 미감지",
                Messages.getWarningIcon()
            )
            if (proceed != Messages.YES) return
        }
        
        // 소스 파일 생성
        val sourceFile = SourceFile(
            pathString = virtualFile.path,
            type = FileType.KOTLIN,
            relativePath = virtualFile.name
        )
        
        // TODO: 변환 실행 (백그라운드 작업으로 변경 필요)
        Messages.showInfoMessage(
            project,
            "변환 기능이 준비 중입니다.\n" +
            "Tool Window에서 전체 프로젝트 변환을 사용해주세요.",
            "Android to Flutter Porter"
        )
    }
    
    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.name?.endsWith(".kt") == true
    }
}

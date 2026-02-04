package com.zime.app.androidtoflutter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.zime.app.androidtoflutter.claude.hook.HookSetupResult
import com.zime.app.androidtoflutter.services.ConversionService

/**
 * Claude Code Hook 설치 액션
 */
class SetupHooksAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val conversionService = project.service<ConversionService>()
        
        // 이미 설치되어 있는지 확인
        if (conversionService.isHooksInstalled()) {
            val reinstall = Messages.showYesNoDialog(
                project,
                "Claude Code Hook이 이미 설치되어 있습니다.\n재설치하시겠습니까?",
                "Hook 재설치",
                Messages.getQuestionIcon()
            )
            if (reinstall != Messages.YES) return
        }
        
        // Hook 설치
        when (val result = conversionService.setupHooks()) {
            is HookSetupResult.Success -> {
                Messages.showInfoMessage(
                    project,
                    "Claude Code Hook이 성공적으로 설치되었습니다.\n\n" +
                    "위치: ${result.hooksDir}\n\n" +
                    "이제 Claude Code에서 변환 작업 시 자동으로 컨텍스트가 제공됩니다.",
                    "설치 완료"
                )
            }
            is HookSetupResult.Failure -> {
                Messages.showErrorDialog(
                    project,
                    "Hook 설치 중 오류가 발생했습니다.\n\n" +
                    "오류: ${result.error}",
                    "설치 실패"
                )
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

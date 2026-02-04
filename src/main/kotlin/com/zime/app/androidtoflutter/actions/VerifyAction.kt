package com.zime.app.androidtoflutter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 변환 검증 액션
 */
class VerifyAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        Messages.showInfoMessage(
            project,
            "검증 기능이 준비 중입니다.\n\n" +
            "스크린샷 비교를 통한 변환 검증 기능이 곧 추가됩니다.",
            "Android to Flutter Porter"
        )
        
        // Tool Window 열기
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Android to Flutter Porter")
        
        toolWindow?.show()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

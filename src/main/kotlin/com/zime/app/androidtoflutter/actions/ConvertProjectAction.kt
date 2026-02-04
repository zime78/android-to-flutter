package com.zime.app.androidtoflutter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 전체 프로젝트 변환 액션
 */
class ConvertProjectAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Tool Window 열기
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Android to Flutter Porter")
        
        toolWindow?.show()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

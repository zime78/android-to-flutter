package com.zime.app.androidtoflutter.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Android to Flutter Porter Tool Window Factory
 */
class PorterToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val porterToolWindow = PorterToolWindow(project)
        val content = ContentFactory.getInstance().createContent(
            porterToolWindow.getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean {
        // 항상 사용 가능 (Android 프로젝트 체크는 내부에서)
        return true
    }
}

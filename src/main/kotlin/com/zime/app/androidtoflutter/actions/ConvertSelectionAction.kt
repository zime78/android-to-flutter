package com.zime.app.androidtoflutter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

/**
 * 선택 영역 변환 액션 (에디터 컨텍스트 메뉴)
 */
class ConvertSelectionAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        val selectedText = editor.selectionModel.selectedText
        
        if (selectedText.isNullOrBlank()) {
            Messages.showWarningDialog(
                project,
                "변환할 코드를 선택해주세요.",
                "Android to Flutter Porter"
            )
            return
        }
        
        // TODO: 선택 영역 변환 구현
        Messages.showInfoMessage(
            project,
            "선택 영역 변환 기능이 준비 중입니다.\n\n" +
            "선택된 ${selectedText.lines().size}줄의 코드가 변환됩니다.",
            "Android to Flutter Porter"
        )
    }
    
    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        
        e.presentation.isEnabledAndVisible = 
            virtualFile?.name?.endsWith(".kt") == true && 
            editor?.selectionModel?.hasSelection() == true
    }
}

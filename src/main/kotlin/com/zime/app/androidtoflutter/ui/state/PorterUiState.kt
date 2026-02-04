package com.zime.app.androidtoflutter.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zime.app.androidtoflutter.models.project.ConversionOptions

/**
 * Porter UI 상태
 */
class PorterUiState {
    // 프로젝트 경로
    var sourcePath by mutableStateOf("")
    var targetPath by mutableStateOf("")
    
    // 변환 옵션
    var options by mutableStateOf(ConversionOptions())
    var optionsExpanded by mutableStateOf(false)
    
    // Claude 설정
    var hookEnabled by mutableStateOf(true)
    var cliEnabled by mutableStateOf(true)
    
    // 변환 상태
    var isConverting by mutableStateOf(false)
    var isVerifying by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var currentTask by mutableStateOf("")
    
    // 로그
    val logs = mutableStateListOf<LogEntry>()
    
    // 선택된 파일
    val selectedFiles = mutableStateListOf<String>()
    
    // 변환 가능 여부
    val canConvert: Boolean
        get() = sourcePath.isNotBlank() && targetPath.isNotBlank() && !isConverting
    
    fun addLog(level: LogLevel, message: String) {
        logs.add(LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message
        ))
    }
    
    fun clearLogs() {
        logs.clear()
    }
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO,
    WARNING,
    ERROR,
    SUCCESS
}

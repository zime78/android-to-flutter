package com.zime.app.androidtoflutter.models.project

import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Flutter 타겟 프로젝트 정보
 */
@Serializable
data class TargetProject(
    val pathString: String,
    val name: String,
    val packageName: String,
    val flutterVersion: String = "3.19.0",
    val dartVersion: String = "3.3.0"
) {
    val path: Path get() = Path(pathString)
    
    val libPath: Path get() = path.resolve("lib")
    val assetsPath: Path get() = path.resolve("assets")
    val testPath: Path get() = path.resolve("test")
    val l10nPath: Path get() = path.resolve("lib/l10n")
}

@Serializable
data class PubspecConfig(
    val name: String,
    val description: String = "",
    val version: String = "1.0.0+1",
    val dependencies: Map<String, String> = defaultDependencies(),
    val devDependencies: Map<String, String> = defaultDevDependencies(),
    val assets: List<String> = emptyList(),
    val fonts: List<FontConfig> = emptyList()
) {
    companion object {
        fun defaultDependencies() = mapOf(
            "flutter" to "sdk: flutter",
            "flutter_riverpod" to "^2.4.0",
            "go_router" to "^13.0.0",
            "dio" to "^5.4.0",
            "freezed_annotation" to "^2.4.1",
            "json_annotation" to "^4.8.1"
        )
        
        fun defaultDevDependencies() = mapOf(
            "flutter_test" to "sdk: flutter",
            "flutter_lints" to "^3.0.0",
            "build_runner" to "^2.4.8",
            "freezed" to "^2.4.6",
            "json_serializable" to "^6.7.1"
        )
    }
}

@Serializable
data class FontConfig(
    val family: String,
    val fonts: List<FontWeight>
)

@Serializable
data class FontWeight(
    val asset: String,
    val weight: Int? = null,
    val style: String? = null
)

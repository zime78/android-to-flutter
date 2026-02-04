package com.zime.app.androidtoflutter.models.project

import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Android 소스 프로젝트 정보
 */
@Serializable
data class SourceProject(
    val pathString: String,
    val name: String,
    val packageName: String,
    val modules: List<SourceModule> = emptyList(),
    val buildConfig: AndroidBuildConfig? = null,
    val dependencies: List<Dependency> = emptyList()
) {
    val path: Path get() = Path(pathString)
}

@Serializable
data class SourceModule(
    val name: String,
    val pathString: String,
    val type: ModuleType,
    val sourceFiles: List<SourceFile> = emptyList()
) {
    val path: Path get() = Path(pathString)
}

@Serializable
enum class ModuleType {
    APP,
    LIBRARY,
    FEATURE
}

@Serializable
data class SourceFile(
    val pathString: String,
    val type: FileType,
    val relativePath: String
) {
    val path: Path get() = Path(pathString)
}

@Serializable
enum class FileType {
    KOTLIN,
    XML,
    RESOURCE,
    GRADLE,
    OTHER
}

@Serializable
data class AndroidBuildConfig(
    val minSdk: Int = 21,
    val targetSdk: Int = 34,
    val compileSdk: Int = 34,
    val kotlinVersion: String = "2.0.0",
    val composeVersion: String = "1.6.0"
)

@Serializable
data class Dependency(
    val group: String,
    val name: String,
    val version: String,
    val type: DependencyType = DependencyType.IMPLEMENTATION
)

@Serializable
enum class DependencyType {
    IMPLEMENTATION,
    API,
    COMPILE_ONLY,
    RUNTIME_ONLY,
    TEST_IMPLEMENTATION,
    ANDROID_TEST_IMPLEMENTATION
}

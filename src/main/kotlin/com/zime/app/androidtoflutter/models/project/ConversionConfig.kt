package com.zime.app.androidtoflutter.models.project

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 변환 설정
 */
@Serializable
data class ConversionConfig(
    val sourcePathString: String,
    val targetPathString: String,
    val options: ConversionOptions = ConversionOptions(),
    val customMappings: CustomMappings = CustomMappings(),
    val aiSettings: AiSettings = AiSettings()
)

@Serializable
data class ConversionOptions(
    val stateManagement: StateManagementType = StateManagementType.RIVERPOD,
    val navigation: NavigationType = NavigationType.GO_ROUTER,
    val networking: NetworkingType = NetworkingType.DIO,
    val localStorage: LocalStorageType = LocalStorageType.DRIFT,
    val imageLoading: ImageLoadingType = ImageLoadingType.CACHED_NETWORK_IMAGE,
    val nullSafety: Boolean = true,
    val generateTests: Boolean = true,
    val preserveComments: Boolean = true
)

@Serializable
enum class StateManagementType(val packageName: String) {
    RIVERPOD("flutter_riverpod"),
    PROVIDER("provider"),
    BLOC("flutter_bloc"),
    GETX("get")
}

@Serializable
enum class NavigationType(val packageName: String) {
    GO_ROUTER("go_router"),
    AUTO_ROUTE("auto_route"),
    NAVIGATOR("flutter/material")
}

@Serializable
enum class NetworkingType(val packageName: String) {
    DIO("dio"),
    HTTP("http"),
    CHOPPER("chopper")
}

@Serializable
enum class LocalStorageType(val packageName: String) {
    DRIFT("drift"),
    HIVE("hive"),
    SHARED_PREFS("shared_preferences"),
    ISAR("isar")
}

@Serializable
enum class ImageLoadingType(val packageName: String) {
    CACHED_NETWORK_IMAGE("cached_network_image"),
    FLUTTER_CACHE("flutter_cache_manager"),
    EXTENDED_IMAGE("extended_image")
}

@Serializable
data class CustomMappings(
    val widgetMappings: Map<String, String> = emptyMap(),
    val typeMappings: Map<String, String> = emptyMap(),
    val libraryMappings: Map<String, String> = emptyMap()
)

@Serializable
data class AiSettings(
    val useClaudeCode: Boolean = true,
    val hookEnabled: Boolean = true,
    val cliEnabled: Boolean = true,
    val maxRetries: Int = 3,
    val timeoutSeconds: Long = 60
) {
    val timeout: Duration get() = timeoutSeconds.seconds
}

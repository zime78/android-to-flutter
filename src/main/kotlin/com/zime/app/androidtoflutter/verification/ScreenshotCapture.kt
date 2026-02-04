package com.zime.app.androidtoflutter.verification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.*

/**
 * 스크린샷 캡처 서비스
 * Android Emulator와 Flutter 앱의 스크린샷을 캡처
 */
class ScreenshotCapture {

    /**
     * Android Emulator 스크린샷 캡처
     */
    suspend fun captureAndroid(
        deviceId: String? = null,
        outputPath: Path
    ): CaptureResult = withContext(Dispatchers.IO) {
        try {
            outputPath.parent.createDirectories()

            // ADB를 통한 스크린샷 캡처
            val device = deviceId?.let { "-s $it" } ?: ""
            val tempPath = "/sdcard/screenshot_temp.png"

            val captureProcess = ProcessBuilder(
                "adb", *device.split(" ").filter { it.isNotEmpty() }.toTypedArray(),
                "shell", "screencap", "-p", tempPath
            ).start()
            captureProcess.waitFor()

            if (captureProcess.exitValue() != 0) {
                return@withContext CaptureResult(
                    success = false,
                    error = "ADB screencap failed"
                )
            }

            // 로컬로 파일 가져오기
            val pullProcess = ProcessBuilder(
                "adb", *device.split(" ").filter { it.isNotEmpty() }.toTypedArray(),
                "pull", tempPath, outputPath.toString()
            ).start()
            pullProcess.waitFor()

            if (pullProcess.exitValue() != 0) {
                return@withContext CaptureResult(
                    success = false,
                    error = "ADB pull failed"
                )
            }

            // 임시 파일 삭제
            ProcessBuilder(
                "adb", *device.split(" ").filter { it.isNotEmpty() }.toTypedArray(),
                "shell", "rm", tempPath
            ).start().waitFor()

            CaptureResult(
                success = true,
                imagePath = outputPath.toString()
            )
        } catch (e: Exception) {
            CaptureResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Flutter 앱 스크린샷 캡처 (Flutter Driver 또는 Integration Test)
     */
    suspend fun captureFlutter(
        projectPath: Path,
        outputPath: Path,
        deviceId: String? = null
    ): CaptureResult = withContext(Dispatchers.IO) {
        try {
            outputPath.parent.createDirectories()

            // flutter screenshot 명령 사용
            val device = deviceId?.let { listOf("-d", it) } ?: emptyList()

            val process = ProcessBuilder(
                listOf("flutter", "screenshot", "-o", outputPath.toString()) + device
            )
                .directory(projectPath.toFile())
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() != 0) {
                return@withContext CaptureResult(
                    success = false,
                    error = "Flutter screenshot failed: $output"
                )
            }

            CaptureResult(
                success = true,
                imagePath = outputPath.toString()
            )
        } catch (e: Exception) {
            CaptureResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * iOS Simulator 스크린샷 캡처
     */
    suspend fun captureIOS(
        deviceId: String? = null,
        outputPath: Path
    ): CaptureResult = withContext(Dispatchers.IO) {
        try {
            outputPath.parent.createDirectories()

            val device = deviceId ?: "booted"

            val process = ProcessBuilder(
                "xcrun", "simctl", "io", device, "screenshot", outputPath.toString()
            ).start()
            process.waitFor()

            if (process.exitValue() != 0) {
                return@withContext CaptureResult(
                    success = false,
                    error = "iOS screenshot failed"
                )
            }

            CaptureResult(
                success = true,
                imagePath = outputPath.toString()
            )
        } catch (e: Exception) {
            CaptureResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 여러 화면 연속 캡처
     */
    suspend fun captureMultipleScreens(
        platform: Platform,
        projectPath: Path,
        screens: List<ScreenInfo>,
        outputDir: Path,
        deviceId: String? = null
    ): List<CaptureResult> = withContext(Dispatchers.IO) {
        outputDir.createDirectories()

        screens.map { screen ->
            // 화면 이동 명령 (딥링크 또는 intent)
            navigateToScreen(platform, screen, deviceId)

            // 잠시 대기 (화면 렌더링)
            kotlinx.coroutines.delay(1000)

            val outputPath = outputDir / "${screen.name}.png"

            when (platform) {
                Platform.ANDROID -> captureAndroid(deviceId, outputPath)
                Platform.FLUTTER -> captureFlutter(projectPath, outputPath, deviceId)
                Platform.IOS -> captureIOS(deviceId, outputPath)
            }
        }
    }

    /**
     * 화면 이동
     */
    private suspend fun navigateToScreen(
        platform: Platform,
        screen: ScreenInfo,
        deviceId: String?
    ) = withContext(Dispatchers.IO) {
        when (platform) {
            Platform.ANDROID -> {
                // ADB를 통한 딥링크 또는 Activity 실행
                screen.deepLink?.let { link ->
                    val device = deviceId?.let { "-s $it" } ?: ""
                    ProcessBuilder(
                        "adb", *device.split(" ").filter { it.isNotEmpty() }.toTypedArray(),
                        "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", link
                    ).start().waitFor()
                }
            }
            Platform.FLUTTER, Platform.IOS -> {
                // Flutter에서는 테스트 드라이버를 통한 네비게이션 필요
                // 현재는 수동 네비게이션 가정
            }
        }
    }

    /**
     * 연결된 디바이스 목록 조회
     */
    suspend fun getConnectedDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DeviceInfo>()

        // Android 디바이스
        try {
            val adbProcess = ProcessBuilder("adb", "devices", "-l")
                .redirectErrorStream(true)
                .start()
            val output = adbProcess.inputStream.bufferedReader().readText()
            adbProcess.waitFor()

            output.lines()
                .filter { it.contains("device") && !it.startsWith("List") }
                .forEach { line ->
                    val parts = line.split(Regex("\\s+"))
                    if (parts.isNotEmpty()) {
                        val id = parts[0]
                        val model = line.substringAfter("model:", "Unknown").substringBefore(" ")
                        devices.add(DeviceInfo(
                            id = id,
                            name = model,
                            platform = Platform.ANDROID,
                            isEmulator = id.startsWith("emulator")
                        ))
                    }
                }
        } catch (e: Exception) {
            // ADB not available
        }

        // iOS Simulator
        try {
            val simctlProcess = ProcessBuilder(
                "xcrun", "simctl", "list", "devices", "booted", "--json"
            ).redirectErrorStream(true).start()
            val output = simctlProcess.inputStream.bufferedReader().readText()
            simctlProcess.waitFor()

            // 간단한 JSON 파싱 (실제로는 kotlinx.serialization 사용 권장)
            if (output.contains("udid")) {
                val regex = Regex(""""udid"\s*:\s*"([^"]+)".*?"name"\s*:\s*"([^"]+)"""")
                regex.findAll(output).forEach { match ->
                    devices.add(DeviceInfo(
                        id = match.groupValues[1],
                        name = match.groupValues[2],
                        platform = Platform.IOS,
                        isEmulator = true
                    ))
                }
            }
        } catch (e: Exception) {
            // xcrun not available (not on macOS)
        }

        // Flutter 디바이스
        try {
            val flutterProcess = ProcessBuilder("flutter", "devices", "--machine")
                .redirectErrorStream(true)
                .start()
            val output = flutterProcess.inputStream.bufferedReader().readText()
            flutterProcess.waitFor()

            // Flutter devices JSON 파싱
            if (output.contains("id")) {
                val idRegex = Regex(""""id"\s*:\s*"([^"]+)"""")
                val nameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
                val platformRegex = Regex(""""targetPlatform"\s*:\s*"([^"]+)"""")

                // 이미 추가된 디바이스와 중복 체크
                val existingIds = devices.map { it.id }.toSet()

                // 간단한 파싱 (실제로는 JSON 라이브러리 사용)
                val lines = output.split("},")
                lines.forEach { line ->
                    val id = idRegex.find(line)?.groupValues?.get(1)
                    val name = nameRegex.find(line)?.groupValues?.get(1)
                    val platform = platformRegex.find(line)?.groupValues?.get(1)

                    if (id != null && id !in existingIds) {
                        devices.add(DeviceInfo(
                            id = id,
                            name = name ?: "Unknown",
                            platform = when {
                                platform?.contains("android") == true -> Platform.ANDROID
                                platform?.contains("ios") == true -> Platform.IOS
                                else -> Platform.FLUTTER
                            },
                            isEmulator = id.contains("emulator") || id.contains("simulator")
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            // Flutter not available
        }

        devices
    }
}

data class CaptureResult(
    val success: Boolean,
    val imagePath: String? = null,
    val error: String? = null
)

data class ScreenInfo(
    val name: String,
    val deepLink: String? = null,
    val route: String? = null
)

data class DeviceInfo(
    val id: String,
    val name: String,
    val platform: Platform,
    val isEmulator: Boolean
)

enum class Platform {
    ANDROID, FLUTTER, IOS
}

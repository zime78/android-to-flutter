package com.zime.app.androidtoflutter.verification

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.*

/**
 * 검증 서비스 - 변환된 Flutter 앱의 UI 동일성 검증
 */
@Service(Service.Level.PROJECT)
class VerificationService(private val project: Project) {

    private val screenshotCapture = ScreenshotCapture()
    private val screenshotComparator = ScreenshotComparator()

    /**
     * 전체 검증 실행
     */
    suspend fun runVerification(
        config: VerificationConfig,
        progressCallback: (String, Int) -> Unit
    ): VerificationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val screenResults = mutableListOf<ScreenVerificationResult>()

        progressCallback("디바이스 확인 중...", 5)

        // 1. 디바이스 확인
        val devices = screenshotCapture.getConnectedDevices()
        val androidDevice = devices.find { it.platform == Platform.ANDROID }
        val flutterDevice = devices.find { it.platform == Platform.FLUTTER || it.platform == Platform.IOS }

        if (androidDevice == null && config.requireAndroid) {
            return@withContext VerificationResult(
                success = false,
                error = "Android 디바이스를 찾을 수 없습니다. 에뮬레이터를 실행해주세요."
            )
        }

        if (flutterDevice == null && config.requireFlutter) {
            return@withContext VerificationResult(
                success = false,
                error = "Flutter 디바이스를 찾을 수 없습니다."
            )
        }

        progressCallback("스크린샷 디렉토리 준비 중...", 10)

        // 2. 스크린샷 디렉토리 준비
        val screenshotDir = config.outputDir ?: Path(project.basePath!!, ".verification")
        val androidDir = screenshotDir / "android"
        val flutterDir = screenshotDir / "flutter"
        val diffDir = screenshotDir / "diff"

        listOf(androidDir, flutterDir, diffDir).forEach { it.createDirectories() }

        // 3. 각 화면별 검증
        val totalScreens = config.screens.size
        config.screens.forEachIndexed { index, screen ->
            val progress = 10 + (index * 80 / totalScreens)
            progressCallback("검증 중: ${screen.name}", progress)

            val result = verifyScreen(
                screen = screen,
                androidDevice = androidDevice,
                flutterDevice = flutterDevice,
                androidDir = androidDir,
                flutterDir = flutterDir,
                diffDir = diffDir,
                config = config
            )

            screenResults.add(result)
        }

        progressCallback("결과 분석 중...", 95)

        // 4. 종합 결과 계산
        val passedScreens = screenResults.count { it.passed }
        val overallScore = if (screenResults.isNotEmpty()) {
            screenResults.map { it.score }.average()
        } else 0.0

        // 5. 보고서 생성
        val reportPath = if (config.generateReport) {
            generateReport(screenResults, screenshotDir, overallScore)
        } else null

        val endTime = System.currentTimeMillis()

        progressCallback("완료!", 100)

        VerificationResult(
            success = true,
            overallScore = overallScore,
            screenResults = screenResults,
            totalScreens = totalScreens,
            passedScreens = passedScreens,
            reportPath = reportPath,
            verificationTimeMs = endTime - startTime
        )
    }

    /**
     * 단일 화면 검증
     */
    private suspend fun verifyScreen(
        screen: ScreenConfig,
        androidDevice: DeviceInfo?,
        flutterDevice: DeviceInfo?,
        androidDir: Path,
        flutterDir: Path,
        diffDir: Path,
        config: VerificationConfig
    ): ScreenVerificationResult {
        val androidScreenshotPath = androidDir / "${screen.name}.png"
        val flutterScreenshotPath = flutterDir / "${screen.name}.png"

        // Android 스크린샷 캡처
        val androidCapture = if (androidDevice != null && config.captureAndroid) {
            // 화면 이동
            screen.androidDeepLink?.let { link ->
                navigateAndroid(androidDevice.id, link)
                kotlinx.coroutines.delay(config.navigationDelay)
            }
            screenshotCapture.captureAndroid(androidDevice.id, androidScreenshotPath)
        } else if (screen.existingAndroidScreenshot != null) {
            // 기존 스크린샷 사용
            CaptureResult(success = true, imagePath = screen.existingAndroidScreenshot)
        } else {
            CaptureResult(success = false, error = "Android 스크린샷을 캡처할 수 없습니다")
        }

        // Flutter 스크린샷 캡처
        val flutterCapture = if (flutterDevice != null && config.captureFlutter) {
            // 화면 이동
            screen.flutterRoute?.let { route ->
                navigateFlutter(config.flutterProjectPath, route)
                kotlinx.coroutines.delay(config.navigationDelay)
            }
            screenshotCapture.captureFlutter(
                config.flutterProjectPath,
                flutterScreenshotPath,
                flutterDevice.id
            )
        } else if (screen.existingFlutterScreenshot != null) {
            CaptureResult(success = true, imagePath = screen.existingFlutterScreenshot)
        } else {
            CaptureResult(success = false, error = "Flutter 스크린샷을 캡처할 수 없습니다")
        }

        // 캡처 실패 처리
        if (!androidCapture.success || !flutterCapture.success) {
            return ScreenVerificationResult(
                screenName = screen.name,
                passed = false,
                score = 0.0,
                error = listOfNotNull(androidCapture.error, flutterCapture.error).joinToString("; ")
            )
        }

        // 스크린샷 비교
        val compareResult = screenshotComparator.compare(
            sourcePath = Path(androidCapture.imagePath!!),
            targetPath = Path(flutterCapture.imagePath!!),
            options = CompareOptions(
                colorTolerance = config.colorTolerance,
                generateDiffImage = true,
                outputDir = diffDir,
                passThreshold = config.passThreshold
            )
        )

        return ScreenVerificationResult(
            screenName = screen.name,
            passed = compareResult.passed,
            score = compareResult.overallScore,
            pixelMatchRate = compareResult.pixelMatchRate,
            structuralSimilarity = compareResult.structuralSimilarity,
            androidScreenshotPath = androidCapture.imagePath,
            flutterScreenshotPath = flutterCapture.imagePath,
            diffImagePath = compareResult.diffImagePath,
            details = compareResult.details
        )
    }

    /**
     * Android 화면 이동
     */
    private suspend fun navigateAndroid(deviceId: String, deepLink: String) = withContext(Dispatchers.IO) {
        ProcessBuilder(
            "adb", "-s", deviceId,
            "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", deepLink
        ).start().waitFor()
    }

    /**
     * Flutter 화면 이동 (Flutter Driver 또는 수동)
     */
    private suspend fun navigateFlutter(projectPath: Path, route: String) {
        // Flutter에서 프로그래매틱 네비게이션은 테스트 드라이버 필요
        // 현재는 수동 네비게이션 가정
    }

    /**
     * 검증 보고서 생성
     */
    private fun generateReport(
        results: List<ScreenVerificationResult>,
        outputDir: Path,
        overallScore: Double
    ): String {
        val reportPath = outputDir / "verification_report.html"

        val passedCount = results.count { it.passed }
        val failedCount = results.size - passedCount

        val screenRows = results.joinToString("\n") { result ->
            val statusColor = if (result.passed) "#4CAF50" else "#f44336"
            val statusText = if (result.passed) "PASS" else "FAIL"

            """
            <tr>
                <td>${result.screenName}</td>
                <td style="color: $statusColor; font-weight: bold;">$statusText</td>
                <td>${String.format("%.1f", result.score)}%</td>
                <td>${String.format("%.1f", (result.pixelMatchRate ?: 0.0) * 100)}%</td>
                <td>${String.format("%.3f", result.structuralSimilarity ?: 0.0)}</td>
                <td>
                    ${result.diffImagePath?.let { "<a href='$it'>차이 보기</a>" } ?: "-"}
                </td>
            </tr>
            """.trimIndent()
        }

        val html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>UI 검증 보고서</title>
    <style>
        body { font-family: 'Segoe UI', Arial, sans-serif; margin: 40px; background: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        h1 { color: #333; border-bottom: 2px solid #2196F3; padding-bottom: 10px; }
        .summary { display: flex; gap: 20px; margin: 20px 0; }
        .summary-card { flex: 1; padding: 20px; border-radius: 8px; text-align: center; }
        .summary-card.score { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .summary-card.passed { background: #E8F5E9; }
        .summary-card.failed { background: #FFEBEE; }
        .summary-card h2 { margin: 0 0 10px 0; font-size: 14px; text-transform: uppercase; opacity: 0.8; }
        .summary-card .value { font-size: 36px; font-weight: bold; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background: #f8f8f8; font-weight: 600; }
        tr:hover { background: #f5f5f5; }
        .timestamp { color: #666; font-size: 14px; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Android to Flutter UI 검증 보고서</h1>

        <div class="summary">
            <div class="summary-card score">
                <h2>Overall Score</h2>
                <div class="value">${String.format("%.1f", overallScore)}%</div>
            </div>
            <div class="summary-card passed">
                <h2>Passed</h2>
                <div class="value" style="color: #4CAF50;">$passedCount</div>
            </div>
            <div class="summary-card failed">
                <h2>Failed</h2>
                <div class="value" style="color: #f44336;">$failedCount</div>
            </div>
        </div>

        <h2>화면별 결과</h2>
        <table>
            <thead>
                <tr>
                    <th>화면</th>
                    <th>상태</th>
                    <th>점수</th>
                    <th>픽셀 일치율</th>
                    <th>구조 유사도</th>
                    <th>차이 이미지</th>
                </tr>
            </thead>
            <tbody>
                $screenRows
            </tbody>
        </table>

        <p class="timestamp">생성 시간: ${java.time.LocalDateTime.now()}</p>
    </div>
</body>
</html>
        """.trimIndent()

        reportPath.writeText(html)
        return reportPath.toString()
    }

    companion object {
        fun getInstance(project: Project): VerificationService {
            return project.getService(VerificationService::class.java)
        }
    }
}

/**
 * 검증 설정
 */
data class VerificationConfig(
    val screens: List<ScreenConfig>,
    val flutterProjectPath: Path,
    val outputDir: Path? = null,
    val captureAndroid: Boolean = true,
    val captureFlutter: Boolean = true,
    val requireAndroid: Boolean = true,
    val requireFlutter: Boolean = true,
    val colorTolerance: Double = 0.05,
    val passThreshold: Double = 90.0,
    val navigationDelay: Long = 1500,
    val generateReport: Boolean = true
)

/**
 * 화면 설정
 */
data class ScreenConfig(
    val name: String,
    val androidDeepLink: String? = null,
    val flutterRoute: String? = null,
    val existingAndroidScreenshot: String? = null,
    val existingFlutterScreenshot: String? = null
)

/**
 * 검증 결과
 */
data class VerificationResult(
    val success: Boolean,
    val overallScore: Double = 0.0,
    val screenResults: List<ScreenVerificationResult> = emptyList(),
    val totalScreens: Int = 0,
    val passedScreens: Int = 0,
    val reportPath: String? = null,
    val verificationTimeMs: Long = 0,
    val error: String? = null
) {
    val passRate: Double get() = if (totalScreens > 0) passedScreens.toDouble() / totalScreens else 0.0
}

/**
 * 화면별 검증 결과
 */
data class ScreenVerificationResult(
    val screenName: String,
    val passed: Boolean,
    val score: Double,
    val pixelMatchRate: Double? = null,
    val structuralSimilarity: Double? = null,
    val androidScreenshotPath: String? = null,
    val flutterScreenshotPath: String? = null,
    val diffImagePath: String? = null,
    val details: CompareDetails? = null,
    val error: String? = null
)

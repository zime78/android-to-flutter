package com.zime.app.androidtoflutter.verification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 스크린샷 비교 서비스
 * 두 이미지의 시각적 차이를 분석
 */
class ScreenshotComparator {

    /**
     * 두 스크린샷 비교
     */
    suspend fun compare(
        sourcePath: Path,
        targetPath: Path,
        options: CompareOptions = CompareOptions()
    ): CompareResult = withContext(Dispatchers.IO) {
        try {
            val sourceImage = ImageIO.read(sourcePath.toFile())
            val targetImage = ImageIO.read(targetPath.toFile())

            if (sourceImage == null || targetImage == null) {
                return@withContext CompareResult(
                    success = false,
                    error = "Failed to load images"
                )
            }

            // 크기 조정 (필요시)
            val (normalizedSource, normalizedTarget) = if (options.normalizeSize) {
                normalizeImages(sourceImage, targetImage)
            } else {
                sourceImage to targetImage
            }

            // 픽셀별 비교
            val pixelComparison = comparePixels(normalizedSource, normalizedTarget, options)

            // 구조적 유사도 (SSIM 간략 버전)
            val structuralSimilarity = calculateStructuralSimilarity(normalizedSource, normalizedTarget)

            // 차이 이미지 생성
            val diffImagePath = if (options.generateDiffImage) {
                generateDiffImage(normalizedSource, normalizedTarget, pixelComparison, options.outputDir)
            } else null

            // 종합 점수 계산
            val overallScore = calculateOverallScore(pixelComparison, structuralSimilarity, options)

            CompareResult(
                success = true,
                overallScore = overallScore,
                pixelMatchRate = pixelComparison.matchRate,
                structuralSimilarity = structuralSimilarity,
                diffImagePath = diffImagePath,
                details = CompareDetails(
                    sourceSize = "${normalizedSource.width}x${normalizedSource.height}",
                    targetSize = "${normalizedTarget.width}x${normalizedTarget.height}",
                    totalPixels = pixelComparison.totalPixels,
                    matchingPixels = pixelComparison.matchingPixels,
                    differentPixels = pixelComparison.differentPixels,
                    colorDifference = pixelComparison.averageColorDifference,
                    regions = pixelComparison.differentRegions
                )
            )
        } catch (e: Exception) {
            CompareResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 다중 스크린 비교
     */
    suspend fun compareMultiple(
        pairs: List<ScreenPair>,
        options: CompareOptions = CompareOptions()
    ): MultiCompareResult = withContext(Dispatchers.IO) {
        val results = pairs.map { pair ->
            pair.name to compare(pair.sourcePath, pair.targetPath, options)
        }

        val successfulResults = results.filter { it.second.success }
        val averageScore = if (successfulResults.isNotEmpty()) {
            successfulResults.map { it.second.overallScore }.average()
        } else 0.0

        MultiCompareResult(
            overallScore = averageScore,
            screenResults = results.toMap(),
            totalScreens = pairs.size,
            passedScreens = successfulResults.count { it.second.overallScore >= options.passThreshold }
        )
    }

    /**
     * 이미지 크기 정규화
     */
    private fun normalizeImages(
        source: BufferedImage,
        target: BufferedImage
    ): Pair<BufferedImage, BufferedImage> {
        // 더 큰 이미지를 작은 이미지 크기로 조정
        val targetWidth = minOf(source.width, target.width)
        val targetHeight = minOf(source.height, target.height)

        val normalizedSource = resizeImage(source, targetWidth, targetHeight)
        val normalizedTarget = resizeImage(target, targetWidth, targetHeight)

        return normalizedSource to normalizedTarget
    }

    /**
     * 이미지 리사이즈
     */
    private fun resizeImage(image: BufferedImage, width: Int, height: Int): BufferedImage {
        if (image.width == width && image.height == height) {
            return image
        }

        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = resized.createGraphics()
        g.drawImage(image, 0, 0, width, height, null)
        g.dispose()
        return resized
    }

    /**
     * 픽셀별 비교
     */
    private fun comparePixels(
        source: BufferedImage,
        target: BufferedImage,
        options: CompareOptions
    ): PixelComparison {
        val width = source.width
        val height = source.height
        val totalPixels = width * height

        var matchingPixels = 0
        var totalColorDiff = 0.0
        val differentRegions = mutableListOf<DifferentRegion>()

        // 차이가 있는 영역 추적용
        val diffMap = Array(height) { BooleanArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourcePixel = Color(source.getRGB(x, y), true)
                val targetPixel = Color(target.getRGB(x, y), true)

                val colorDiff = calculateColorDifference(sourcePixel, targetPixel)
                totalColorDiff += colorDiff

                if (colorDiff <= options.colorTolerance) {
                    matchingPixels++
                } else {
                    diffMap[y][x] = true
                }
            }
        }

        // 차이 영역 군집화
        val regions = findDifferentRegions(diffMap, width, height)

        return PixelComparison(
            totalPixels = totalPixels,
            matchingPixels = matchingPixels,
            differentPixels = totalPixels - matchingPixels,
            matchRate = matchingPixels.toDouble() / totalPixels,
            averageColorDifference = totalColorDiff / totalPixels,
            differentRegions = regions
        )
    }

    /**
     * 색상 차이 계산 (유클리드 거리)
     */
    private fun calculateColorDifference(c1: Color, c2: Color): Double {
        val dr = c1.red - c2.red
        val dg = c1.green - c2.green
        val db = c1.blue - c2.blue
        val da = c1.alpha - c2.alpha

        return sqrt((dr * dr + dg * dg + db * db + da * da).toDouble()) / 510.0 // 0~1로 정규화
    }

    /**
     * 구조적 유사도 계산 (간략 SSIM)
     */
    private fun calculateStructuralSimilarity(
        source: BufferedImage,
        target: BufferedImage
    ): Double {
        val width = source.width
        val height = source.height

        // 휘도 계산
        var sumSource = 0.0
        var sumTarget = 0.0
        var sumSourceSq = 0.0
        var sumTargetSq = 0.0
        var sumSourceTarget = 0.0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourcePixel = Color(source.getRGB(x, y))
                val targetPixel = Color(target.getRGB(x, y))

                val sourceLum = getLuminance(sourcePixel)
                val targetLum = getLuminance(targetPixel)

                sumSource += sourceLum
                sumTarget += targetLum
                sumSourceSq += sourceLum * sourceLum
                sumTargetSq += targetLum * targetLum
                sumSourceTarget += sourceLum * targetLum
            }
        }

        val n = (width * height).toDouble()

        val meanSource = sumSource / n
        val meanTarget = sumTarget / n

        val varSource = (sumSourceSq / n) - (meanSource * meanSource)
        val varTarget = (sumTargetSq / n) - (meanTarget * meanTarget)

        val covariance = (sumSourceTarget / n) - (meanSource * meanTarget)

        val c1 = 0.01 * 0.01
        val c2 = 0.03 * 0.03

        val ssim = ((2 * meanSource * meanTarget + c1) * (2 * covariance + c2)) /
                ((meanSource * meanSource + meanTarget * meanTarget + c1) * (varSource + varTarget + c2))

        return ssim.coerceIn(0.0, 1.0)
    }

    /**
     * 휘도 계산
     */
    private fun getLuminance(color: Color): Double {
        return (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue) / 255.0
    }

    /**
     * 차이 영역 찾기 (간단한 연결 요소 분석)
     */
    private fun findDifferentRegions(
        diffMap: Array<BooleanArray>,
        width: Int,
        height: Int
    ): List<DifferentRegion> {
        val visited = Array(height) { BooleanArray(width) }
        val regions = mutableListOf<DifferentRegion>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (diffMap[y][x] && !visited[y][x]) {
                    val region = floodFill(diffMap, visited, x, y, width, height)
                    if (region.pixelCount >= 10) { // 최소 10픽셀 이상인 영역만
                        regions.add(region)
                    }
                }
            }
        }

        return regions.sortedByDescending { it.pixelCount }.take(10) // 상위 10개 영역
    }

    /**
     * Flood Fill로 연결된 영역 찾기
     */
    private fun floodFill(
        diffMap: Array<BooleanArray>,
        visited: Array<BooleanArray>,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int
    ): DifferentRegion {
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited[startY][startX] = true

        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        var pixelCount = 0

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            pixelCount++

            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)

            // 4방향 탐색
            listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until width && ny in 0 until height &&
                    diffMap[ny][nx] && !visited[ny][nx]) {
                    visited[ny][nx] = true
                    queue.add(nx to ny)
                }
            }
        }

        return DifferentRegion(
            x = minX,
            y = minY,
            width = maxX - minX + 1,
            height = maxY - minY + 1,
            pixelCount = pixelCount
        )
    }

    /**
     * 차이 이미지 생성
     */
    private fun generateDiffImage(
        source: BufferedImage,
        target: BufferedImage,
        comparison: PixelComparison,
        outputDir: Path?
    ): String? {
        val width = source.width
        val height = source.height

        val diffImage = BufferedImage(width * 3, height, BufferedImage.TYPE_INT_ARGB)
        val g = diffImage.createGraphics()

        // 왼쪽: 원본
        g.drawImage(source, 0, 0, null)

        // 가운데: 차이 강조
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourcePixel = Color(source.getRGB(x, y), true)
                val targetPixel = Color(target.getRGB(x, y), true)

                val colorDiff = calculateColorDifference(sourcePixel, targetPixel)

                val highlightColor = if (colorDiff > 0.05) {
                    // 차이가 있는 부분은 빨간색으로 표시
                    val intensity = (colorDiff * 255).toInt().coerceIn(0, 255)
                    Color(255, 0, 0, intensity).rgb
                } else {
                    // 동일한 부분은 회색조로
                    val gray = ((sourcePixel.red + sourcePixel.green + sourcePixel.blue) / 3)
                    Color(gray, gray, gray, 128).rgb
                }

                diffImage.setRGB(width + x, y, highlightColor)
            }
        }

        // 오른쪽: 대상
        g.drawImage(target, width * 2, 0, null)

        g.dispose()

        // 저장
        val outputPath = outputDir?.resolve("diff_${System.currentTimeMillis()}.png")
            ?: Path(System.getProperty("java.io.tmpdir"), "diff_${System.currentTimeMillis()}.png")

        outputPath.parent.createDirectories()
        ImageIO.write(diffImage, "PNG", outputPath.toFile())

        return outputPath.toString()
    }

    /**
     * 종합 점수 계산
     */
    private fun calculateOverallScore(
        pixelComparison: PixelComparison,
        structuralSimilarity: Double,
        options: CompareOptions
    ): Double {
        // 가중치 기반 점수 계산
        val pixelScore = pixelComparison.matchRate * options.pixelWeight
        val structuralScore = structuralSimilarity * options.structuralWeight

        return ((pixelScore + structuralScore) * 100).coerceIn(0.0, 100.0)
    }
}

data class CompareOptions(
    val colorTolerance: Double = 0.05, // 색상 허용 오차 (0~1)
    val normalizeSize: Boolean = true,
    val generateDiffImage: Boolean = true,
    val outputDir: Path? = null,
    val passThreshold: Double = 90.0, // 통과 기준 점수
    val pixelWeight: Double = 0.6, // 픽셀 매칭 가중치
    val structuralWeight: Double = 0.4 // 구조적 유사도 가중치
)

data class CompareResult(
    val success: Boolean,
    val overallScore: Double = 0.0,
    val pixelMatchRate: Double = 0.0,
    val structuralSimilarity: Double = 0.0,
    val diffImagePath: String? = null,
    val details: CompareDetails? = null,
    val error: String? = null
) {
    val passed: Boolean get() = success && overallScore >= 90.0
}

data class CompareDetails(
    val sourceSize: String,
    val targetSize: String,
    val totalPixels: Int,
    val matchingPixels: Int,
    val differentPixels: Int,
    val colorDifference: Double,
    val regions: List<DifferentRegion>
)

data class PixelComparison(
    val totalPixels: Int,
    val matchingPixels: Int,
    val differentPixels: Int,
    val matchRate: Double,
    val averageColorDifference: Double,
    val differentRegions: List<DifferentRegion>
)

data class DifferentRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pixelCount: Int
)

data class ScreenPair(
    val name: String,
    val sourcePath: Path,
    val targetPath: Path
)

data class MultiCompareResult(
    val overallScore: Double,
    val screenResults: Map<String, CompareResult>,
    val totalScreens: Int,
    val passedScreens: Int
) {
    val passRate: Double get() = passedScreens.toDouble() / totalScreens
}

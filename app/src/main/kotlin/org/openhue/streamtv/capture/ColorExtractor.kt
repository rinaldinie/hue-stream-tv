package org.openhue.streamtv.capture

import android.graphics.Bitmap
import android.graphics.Color
import org.openhue.streamtv.hue.model.LightLocation
import org.openhue.streamtv.hue.stream.LightRgbColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class SyncDynamics(val alpha: Float, val label: String) {
    SUBTLE(0.20f, "Subtle"),
    BALANCED(0.45f, "Balanced"),
    INTENSE(0.85f, "Intense")
}

data class LightTarget(
    val lightId: Int,
    val normX: Float, // 0.0 (left) .. 1.0 (right)
    val normY: Float  // 0.0 (top) .. 1.0 (bottom)
)

class ColorExtractor(
    private val dynamics: SyncDynamics = SyncDynamics.BALANCED,
    private val brightnessFactor: Float = 1.0f
) {
    // Cache previous smoothed colors for temporal low-pass filter
    private val smoothedColors = mutableMapOf<Int, Triple<Float, Float, Float>>()

    companion object {
        /**
         * Converts Hue 3D coordinates (-1.0 .. 1.0) to 2D normalized screen coordinates (0.0 .. 1.0).
         */
        fun mapLocationsToTargets(locations: Map<String, LightLocation>): List<LightTarget> {
            return locations.mapNotNull { (idStr, loc) ->
                val id = idStr.toIntOrNull() ?: return@mapNotNull null
                val normX = ((loc.x + 1.0f) / 2.0f).toFloat().coerceIn(0.15f, 0.85f)
                
                // Map Z (-1..1) to screen Y (0.75 .. 0.25) so sampling stays inside picture and avoids letterbox bars
                val normY = if (loc.z != 0.0) {
                    (0.5f - (loc.z.toFloat() * 0.25f)).coerceIn(0.20f, 0.80f)
                } else {
                    0.5f
                }
                LightTarget(lightId = id, normX = normX, normY = normY)
            }
        }
    }

    /**
     * Extracts and calculates RGB colors for each configured light target from a downscaled screen bitmap.
     */
    fun extractColors(bitmap: Bitmap, targets: List<LightTarget>, dynamics: SyncDynamics, brightnessMultiplier: Float): List<LightRgbColor> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return emptyList()

        val results = ArrayList<LightRgbColor>(targets.size)

        // Sample window size: ~36% width, ~40% height of the downscaled buffer for rich ambient coverage
        val sampleHalfW = max(2, (width * 0.18f).roundToInt())
        val sampleHalfH = max(2, (height * 0.20f).roundToInt())

        for (target in targets) {
            val centerX = (target.normX * width).roundToInt().coerceIn(0, width - 1)
            val centerY = (target.normY * height).roundToInt().coerceIn(0, height - 1)

            val startX = max(0, centerX - sampleHalfW)
            val endX = min(width - 1, centerX + sampleHalfW)
            val startY = max(0, centerY - sampleHalfH)
            val endY = min(height - 1, centerY + sampleHalfH)

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var pixelCount = 0

            // Sample pixels with stride 2 for performance and cache efficiency
            var y = startY
            while (y <= endY) {
                var x = startX
                while (x <= endX) {
                    val pixel = bitmap.getPixel(x, y)
                    sumR += Color.red(pixel)
                    sumG += Color.green(pixel)
                    sumB += Color.blue(pixel)
                    pixelCount++
                    x += 2
                }
                y += 2
            }

            if (pixelCount == 0) {
                pixelCount = 1
            }

            val avgR = (sumR.toFloat() / pixelCount)
            val avgG = (sumG.toFloat() / pixelCount)
            val avgB = (sumB.toFloat() / pixelCount)

            // Temporal Smoothing (IIR Low-pass Filter)
            val prev = smoothedColors[target.lightId] ?: Triple(avgR, avgG, avgB)
            val alpha = dynamics.alpha
            val smoothedR = prev.first * (1f - alpha) + avgR * alpha
            val smoothedG = prev.second * (1f - alpha) + avgG * alpha
            val smoothedB = prev.third * (1f - alpha) + avgB * alpha
            smoothedColors[target.lightId] = Triple(smoothedR, smoothedG, smoothedB)

            // Brightness multiplier
            val factor = brightnessMultiplier.coerceIn(0.1f, 2.0f)
            val rCorrected = (smoothedR * factor).roundToInt().coerceIn(0, 255)
            val gCorrected = (smoothedG * factor).roundToInt().coerceIn(0, 255)
            val bCorrected = (smoothedB * factor).roundToInt().coerceIn(0, 255)

            results.add(
                LightRgbColor(
                    lightId = target.lightId,
                    red = rCorrected,
                    green = gCorrected,
                    blue = bCorrected
                )
            )
        }

        return results
    }

    fun reset() {
        smoothedColors.clear()
    }
}

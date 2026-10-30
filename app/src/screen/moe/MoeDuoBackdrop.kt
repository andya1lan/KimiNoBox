/*
 * This file is part of KimiNoBox, a modified version of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 KimiNoBox contributors
 *
 */

package com.github.yumeyucca.yumebox.screen.moe

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay

/**
 * The Duo strip picks black or white ink from the wallpaper behind it: the home background records
 * its drawing into a layer, and a downscaled copy of that layer is averaged over the strip's status
 * block and its capsule.
 */
internal fun Modifier.moeDuoBackdrop(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * Where the strip's status block and capsule sit: [stripWidth] in from the screen's right edge, the
 * status block [statusTop] down from the top, the capsule centered vertically.
 */
internal data class MoeDuoBackdropRegions(
    val stripWidth: Dp,
    val statusTop: Dp,
    val statusHeight: Dp,
    val capsuleHeight: Dp,
)

/** Average wallpaper colors behind the status block and the capsule. */
internal data class MoeDuoBackdropColors(val status: Color, val capsule: Color)

/**
 * Samples [layer] a few times after each change of [keys], because the wallpaper loads
 * asynchronously. Null until the wallpaper has drawn.
 */
@Composable
internal fun rememberMoeDuoBackdropColors(
    layer: GraphicsLayer,
    regions: MoeDuoBackdropRegions,
    vararg keys: Any?,
): MoeDuoBackdropColors? {
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    var colors by remember { mutableStateOf<MoeDuoBackdropColors?>(null) }
    LaunchedEffect(layer, regions, density, *keys) {
        for (wait in SAMPLE_DELAYS_MS) {
            delay(wait)
            sampleBackdrop(graphicsContext, density, layer, regions)?.let { colors = it }
        }
    }
    return colors
}

/**
 * Whether the strip needs dark ink over [wallpaper]: the color goes through the strip's own tint
 * (YumeHaze.glassColorEffects, then about the outer two thirds of MoeSidebarDecoration's
 * gradient) before its luminance is checked.
 */
internal fun moeDuoNeedsDarkInk(wallpaper: Color, surface: Color, isDarkSurface: Boolean): Boolean {
    val tinted =
        if (isDarkSurface) {
            wallpaper.over(Color.Black, 0.24f).over(Color.Black, 0.10f).over(Color.Black, 0.30f)
        } else {
            wallpaper.over(surface, 0.13f).over(Color.Black, 0.10f).over(surface, 0.21f)
        }
    return tinted.luminance() > DARK_INK_LUMINANCE
}

private fun Color.over(tint: Color, alpha: Float): Color =
    Color(
        red = red + (tint.red - red) * alpha,
        green = green + (tint.green - green) * alpha,
        blue = blue + (tint.blue - blue) * alpha,
    )

private suspend fun sampleBackdrop(
    graphicsContext: GraphicsContext,
    density: Density,
    layer: GraphicsLayer,
    regions: MoeDuoBackdropRegions,
): MoeDuoBackdropColors? {
    val size = layer.size
    if (size.width <= 0 || size.height <= 0) return null
    val smallSize =
        IntSize(
            (size.width / SAMPLE_SCALE).coerceAtLeast(1),
            (size.height / SAMPLE_SCALE).coerceAtLeast(1),
        )
    val small = graphicsContext.createGraphicsLayer()
    val bitmap =
        try {
            small.record(density, LayoutDirection.Ltr, smallSize) {
                scale(1f / SAMPLE_SCALE, pivot = Offset.Zero) { drawLayer(layer) }
            }
            // A hardware bitmap on API 28+; its pixels are only readable from a software copy.
            small.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            graphicsContext.releaseGraphicsLayer(small)
        }
    return try {
        with(density) {
            val left = (size.width - regions.stripWidth.toPx()) / SAMPLE_SCALE
            val right = size.width.toFloat() / SAMPLE_SCALE
            val statusTop = regions.statusTop.toPx() / SAMPLE_SCALE
            val statusBottom = (regions.statusTop + regions.statusHeight).toPx() / SAMPLE_SCALE
            val capsuleTop = (size.height - regions.capsuleHeight.toPx()) / 2f / SAMPLE_SCALE
            val capsuleBottom = (size.height + regions.capsuleHeight.toPx()) / 2f / SAMPLE_SCALE
            val status = bitmap.averageColor(left, statusTop, right, statusBottom)
            val capsule = bitmap.averageColor(left, capsuleTop, right, capsuleBottom)
            if (status != null && capsule != null) MoeDuoBackdropColors(status, capsule) else null
        }
    } finally {
        bitmap.recycle()
    }
}

/** Average color of the area, or null while it is still mostly transparent (not drawn yet). */
private fun Bitmap.averageColor(left: Float, top: Float, right: Float, bottom: Float): Color? {
    val l = left.toInt().coerceIn(0, width - 1)
    val t = top.toInt().coerceIn(0, height - 1)
    val r = right.toInt().coerceIn(l + 1, width)
    val b = bottom.toInt().coerceIn(t + 1, height)
    val w = r - l
    val h = b - t
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, l, t, w, h)
    var alpha = 0L
    var red = 0L
    var green = 0L
    var blue = 0L
    for (pixel in pixels) {
        alpha += pixel ushr 24
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size
    if (alpha / count < MIN_DRAWN_ALPHA) return null
    return Color(
        red = red / count / 255f,
        green = green / count / 255f,
        blue = blue / count / 255f,
    )
}

/** The wallpaper is blurred behind the strip anyway, so an 8x smaller copy is plenty. */
private const val SAMPLE_SCALE = 8

/** Covers a slow load, such as copying the system wallpaper first. */
private val SAMPLE_DELAYS_MS = longArrayOf(300, 900, 2500, 6000)

private const val MIN_DRAWN_ALPHA = 230

/** Dark ink from about #ABABAB up, white below; white reads better on mid tones. */
private const val DARK_INK_LUMINANCE = 0.4f

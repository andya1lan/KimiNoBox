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

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.screen.moe

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.yumeyucca.yumebox.core.locale.YumeTxt
import com.github.yumeyucca.yumebox.presentation.theme.AppTheme
import com.github.yumeyucca.yumebox.screen.home.HomeProxyControlState
import com.kyant.shapes.Capsule
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The Moe home strip in the style of the iPhone Duo outer screen: the strip sits on the right,
 * the content panel's strip-side corners are large so the panel reads as a D, and the strip
 * holds the proxy's status where the Duo shows its status circle (uptime above a delay ring) and
 * the shortcuts in one vertical glass capsule. Its ink turns black or white with the wallpaper
 * behind it (MoeDuoBackdrop.kt).
 */
internal object MoeDuoUi {
    /** Strip-side corners of the content panel, close to an iPhone screen corner. */
    val panelCorner = 56.dp

    /** The hero sits this far below the panel's top edge, as far as from its sides. */
    val heroTopGap = MoeUi.Hero.containerHorizontalInset
    val digitSize = 28.sp
    val digitLineHeight = 30.sp
    val digitDividerWidth = 14.dp
    val digitDividerHeight = 2.dp
    val digitGap = 6.dp
    val ringTopGap = 12.dp
    val ringSize = 36.dp
    val ringStroke = 3.dp
    val ringTextSize = 10.sp

    /** Both uptime lines, the divider and the ring: the area whose wallpaper picks the status ink. */
    val statusHeight = 122.dp
    val capsuleWidth = 52.dp
    val capsuleItemHeight = 52.dp
    val capsulePadding = 4.dp
    val capsuleIconSize = 26.dp
}

/**
 * Fills the strip's part of [MoeSidebarDecoration]: the status block starts at [statusTop], level
 * with the hero's top edge, and the capsule sits at the strip's middle. [stripWidth] is the
 * strip's width when expanded, [visibleWidth] its animated width.
 */
@Composable
internal fun BoxScope.MoeDuoSidebarContent(
    state: MoeHomeLayoutState,
    stripWidth: Dp,
    visibleWidth: Dp,
    statusTop: Dp,
    hazeState: HazeState,
    backdropLayer: GraphicsLayer,
    glassEnabled: Boolean,
    onTestDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // MoeSidebarDecoration pads its content by space24 at the top and bottom, which keeps the
    // middle of this box at the middle of the screen.
    val decorationPadding = AppTheme.spacing.space24
    val surface = MiuixTheme.colorScheme.surface
    val isDarkSurface = surface.luminance() < 0.5f
    val iconCount = state.sidebarIcons.size
    val regions =
        remember(stripWidth, statusTop, iconCount) {
            MoeDuoBackdropRegions(
                stripWidth = stripWidth,
                statusTop = statusTop,
                statusHeight = MoeDuoUi.statusHeight,
                capsuleHeight = MoeDuoUi.capsuleItemHeight * iconCount + MoeDuoUi.capsulePadding * 2,
            )
        }
    val backdrop =
        rememberMoeDuoBackdropColors(
            backdropLayer,
            regions,
            state.wallpaperUri,
            state.wallpaperZoom,
            state.wallpaperBiasX,
            state.wallpaperBiasY,
            LocalUseSystemWallpaper.current,
            LocalWallpaperRefreshKey.current,
        )
    val statusDarkInk = backdrop?.let { moeDuoNeedsDarkInk(it.status, surface, isDarkSurface) } == true
    val capsuleDarkInk = backdrop?.let { moeDuoNeedsDarkInk(it.capsule, surface, isDarkSurface) } == true
    val statusDarkness by
        animateFloatAsState(if (statusDarkInk) 1f else 0f, label = "moe_duo_status_ink")
    val capsuleDarkness by
        animateFloatAsState(if (capsuleDarkInk) 1f else 0f, label = "moe_duo_capsule_ink")
    val phase =
        when (state.controlState) {
            HomeProxyControlState.Running -> MoeDuoPhase.Running
            HomeProxyControlState.Connecting,
            HomeProxyControlState.Disconnecting -> MoeDuoPhase.Busy
            HomeProxyControlState.Idle,
            HomeProxyControlState.Lost -> MoeDuoPhase.Idle
        }
    val uptime = state.duration.takeIf { phase == MoeDuoPhase.Running && !state.isRemoteController }

    Box(
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(visibleWidth.coerceAtLeast(0.dp)),
    ) {
        MoeDuoStatusBlock(
            uptime = uptime,
            phase = phase,
            delay = state.selectedServerPing,
            testing = state.delayTesting,
            darkness = statusDarkness,
            onTestDelay = onTestDelay,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (statusTop - decorationPadding).coerceAtLeast(0.dp)),
        )
        MoeDuoShortcutCapsule(
            icons = state.sidebarIcons,
            hazeState = hazeState,
            glassEnabled = glassEnabled,
            darkInk = capsuleDarkInk,
            darkness = capsuleDarkness,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private enum class MoeDuoPhase {
    Idle,
    Busy,
    Running,
}

/** White ink over dark wallpaper (0) and black ink over light wallpaper (1). */
private fun moeDuoInk(darkness: Float): Color =
    lerp(Color.White.copy(alpha = 0.96f), Color.Black.copy(alpha = 0.84f), darkness)

private fun moeDuoTrack(darkness: Float): Color =
    lerp(Color.White.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.16f), darkness)

/**
 * The Duo's status circle turned into the proxy's: the uptime, stacked like the original Moe
 * rail's clock, replaces the clock, and the ring shows the selected node's delay like a signal. A
 * tap on the ring tests the node again. [uptime] is null while the proxy is not running.
 */
@Composable
private fun MoeDuoStatusBlock(
    uptime: MoeDurationPair?,
    phase: MoeDuoPhase,
    delay: Int?,
    testing: Boolean,
    darkness: Float,
    onTestDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = moeDuoInk(darkness)
    val digitInk = if (uptime != null) ink else ink.copy(alpha = ink.alpha * 0.6f)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MoeDuoDigits(uptime?.top ?: "--", digitInk)
        Box(
            modifier =
                Modifier
                    .padding(vertical = MoeDuoUi.digitGap)
                    .size(MoeDuoUi.digitDividerWidth, MoeDuoUi.digitDividerHeight)
                    .background(digitInk.copy(alpha = digitInk.alpha * 0.5f), CircleShape)
        )
        MoeDuoDigits(uptime?.bottom ?: "--", digitInk)
        MoeDuoDelayRing(
            phase = phase,
            delay = delay,
            testing = testing,
            darkness = darkness,
            onTestDelay = onTestDelay,
            modifier = Modifier.padding(top = MoeDuoUi.ringTopGap),
        )
    }
}

@Composable
private fun MoeDuoDigits(value: String, color: Color) {
    Text(
        text = value,
        color = color,
        // Fixed-width digits keep the ticking uptime from shifting sideways.
        style = MiuixTheme.textStyles.title1.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
        fontSize = MoeDuoUi.digitSize,
        lineHeight = MoeDuoUi.digitLineHeight,
        softWrap = false,
    )
}

@Composable
private fun MoeDuoDelayRing(
    phase: MoeDuoPhase,
    delay: Int?,
    testing: Boolean,
    darkness: Float,
    onTestDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = phase == MoeDuoPhase.Running
    val spinning = phase == MoeDuoPhase.Busy || (running && testing)
    val ink = moeDuoInk(darkness)
    val track = moeDuoTrack(darkness)
    val delayColor = moeDuoDelayColor(delay, darkness)
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing)),
            )
        }
    }
    Box(
        modifier =
            modifier
                .size(MoeDuoUi.ringSize)
                .clip(CircleShape)
                .clickable(
                    enabled = running && !testing,
                    onClickLabel = YumeTxt.Proxy.Action.Test,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTestDelay,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(MoeDuoUi.ringSize)) {
            val stroke = MoeDuoUi.ringStroke.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (spinning) {
                drawArc(
                    color = delayColor ?: ink,
                    startAngle = rotation.value - 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            } else if (running && delayColor != null) {
                drawArc(
                    color = delayColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            }
            if (!running) drawPowerGlyph(ink.copy(alpha = ink.alpha * 0.6f))
        }
        if (running) {
            Text(
                text = moeDuoDelayLabel(delay),
                color = ink,
                style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                fontSize = MoeDuoUi.ringTextSize,
                softWrap = false,
            )
        }
    }
}

/** Same delay convention as the node list: null or 0 untested, below 0 timed out. */
private fun moeDuoDelayLabel(delay: Int?): String =
    when {
        delay == null || delay == 0 -> "--"
        delay < 0 -> "×"
        else -> delay.coerceAtMost(9999).toString()
    }

/**
 * The node list's thresholds (300 and 1000 ms) in iOS's system colors: the dark-mode set over
 * dark wallpaper, the increased-contrast light set over light wallpaper.
 */
private fun moeDuoDelayColor(delay: Int?, darkness: Float): Color? {
    val (overDark, overLight) =
        when {
            delay == null || delay == 0 -> return null
            delay < 0 || delay > 1000 -> Color(0xFFFF453A) to Color(0xFFD70015)
            delay <= 300 -> Color(0xFF30D158) to Color(0xFF248A3D)
            else -> Color(0xFFFFD60A) to Color(0xFFB88400)
        }
    return lerp(overDark, overLight, darkness)
}

private fun DrawScope.drawPowerGlyph(color: Color) {
    val stroke = 1.8.dp.toPx()
    val radius = 6.dp.toPx()
    drawArc(
        color = color,
        startAngle = -50f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - radius - 1.dp.toPx()),
        end = Offset(center.x, center.y - 1.dp.toPx()),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/** The Duo's vertical tab bar: icon-only items in one liquid-glass capsule. */
@Composable
private fun MoeDuoShortcutCapsule(
    icons: List<MoeSidebarIconItem>,
    hazeState: HazeState,
    glassEnabled: Boolean,
    darkInk: Boolean,
    darkness: Float,
    modifier: Modifier = Modifier,
) {
    val surface = MiuixTheme.colorScheme.surface
    val glassStyle = remember(surface, darkInk) { moeDuoGlassStyle(surface, darkInk) }
    // Glass needs RenderEffect (API 31); older devices get a plain translucent capsule.
    val useGlass = glassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val iconTint = lerp(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.78f), darkness)
    Column(
        modifier =
            modifier
                .width(MoeDuoUi.capsuleWidth)
                .clip(Capsule())
                .then(
                    if (useGlass) {
                        Modifier.moeDuoGlass(hazeState, glassStyle)
                    } else {
                        Modifier.background(
                            if (darkInk) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.18f),
                            Capsule(),
                        )
                    }
                )
                .padding(vertical = MoeDuoUi.capsulePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icons.forEach { item -> MoeDuoCapsuleItem(item, iconTint) }
    }
}

@Composable
private fun MoeDuoCapsuleItem(item: MoeSidebarIconItem, tint: Color) {
    Box(
        modifier =
            Modifier
                .size(width = MoeDuoUi.capsuleWidth, height = MoeDuoUi.capsuleItemHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = item.onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(MoeDuoUi.capsuleIconSize),
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
private fun Modifier.moeDuoGlass(state: HazeState, style: GlassStyle): Modifier =
    hazeGlass(input = HazeInput.Sources(state), style = style)

/**
 * haze's Regular glass, fully diffused and tinted to match the ink: darkened under white icons so
 * they stay readable over a bright wallpaper, lightened under black icons. haze's dark response
 * follows the system appearance, so a dark app theme writes it out itself.
 */
@OptIn(ExperimentalHazeApi::class)
private fun moeDuoGlassStyle(surface: Color, darkInk: Boolean): GlassStyle {
    val darkTheme = surface.luminance() < 0.5f
    return GlassStyle.regular.then {
        shape(RoundedCornerShape(percent = 50))
        optics(
            refractionHeightFraction = 0.15f,
            refractionDisplacement = 48.dp,
            depth = 1f,
            blurRadius = 24.dp,
            refractionDetailIntensity = 0f,
            refractionProfile = RefractionProfile.Edge(20.dp),
        )
        tint(
            when {
                darkInk -> Color.White.copy(alpha = 0.34f)
                darkTheme -> surface.copy(alpha = 0.5f)
                else -> Color.Black.copy(alpha = 0.16f)
            }
        )
        if (darkTheme && !darkInk) {
            specularIntensity(0.38f)
            edgeShadow(Color.Black.copy(alpha = 0.32f))
            ambientResponse(0.08f)
            contrast(0.08f)
            whitePoint(-0.22f)
            chromaMultiplier(1.1f)
        }
    }
}

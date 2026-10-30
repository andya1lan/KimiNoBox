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
 * Adapted from IosLiquidGlassNavigationBar in the compose-miuix-ui v0.9.4 example
 * (example/shared/.../component/liquid/LiquidGlassNavigationBar.kt),
 * Copyright 2026 compose-miuix-ui contributors, Apache License 2.0, itself adapted from
 * Kyant0/AndroidLiquidGlass (Apache 2.0). Changes: KimiNoBox's tabs, theme and insets; glass
 * only (without blur the bar stays LegacyBottomNavigationBar); one tilt sensor listener, and only
 * while the bar is shown; the hidden accent copy of the tabs takes no focus or semantics.
 *
 */

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.github.yumeyucca.yumebox.presentation.component.liquid.DampedDragAnimation
import com.github.yumeyucca.yumebox.presentation.component.liquid.InnerShadow
import com.github.yumeyucca.yumebox.presentation.component.liquid.InteractiveHighlight
import com.github.yumeyucca.yumebox.presentation.component.liquid.innerShadow
import com.github.yumeyucca.yumebox.presentation.component.liquid.lens
import com.github.yumeyucca.yumebox.presentation.component.liquid.rememberCombinedBackdrop
import com.github.yumeyucca.yumebox.presentation.component.liquid.vibrancy
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * The main pager recorded for the liquid glass tab bar. Non-null only while the glass is on (blur
 * on and runtime shaders available); the pager host records into it, the tab bar samples it.
 */
val LocalBottomBarBackdrop = compositionLocalOf<LayerBackdrop?> { null }

object LiquidGlassTabBarDefaults {
    val HorizontalPadding = UiDp.dp24
    val Height = UiDp.dp64
    val ReservedHeight: Dp = Height + MainBottomBarDefaults.FloatingBottomPadding
}

private val LocalTabScale = staticCompositionLocalOf { { 1f } }

private val TabBarSpecular: Highlight =
    Highlight(
        width = UiDp.dp1,
        alpha = 1f,
        style =
            BloomStroke(
                color = Color.White.copy(alpha = 0.12f),
                innerBlurRadius = UiDp.dp2,
                primaryLight =
                    LightSource(
                        position = LightPosition(0.5f, -0.3f, -0.05f),
                        color = Color.White,
                        intensity = 1f,
                    ),
                secondaryLight =
                    LightSource(
                        position = LightPosition(0.5f, 0.8f, -0.5f),
                        color = Color.White,
                        intensity = 0.4f,
                    ),
                dualPeak = true,
            ),
    )

// Mirrors miuix-blur HighlightStyle.kt's LIGHT_REF — keep in sync.
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f // |g_xy| > 0.1, ≈ 6° tilt

// 3° quantization step for the gravity direction: finer changes are imperceptible.
private const val GRAVITY_ANGLE_STEP_RAD = (3.0 * PI / 180.0).toFloat()

private const val FLAT_GRAVITY_ANGLE_RAD = (-PI / 2).toFloat()

/**
 * In-screen-plane gravity direction angle (radians, quantized to 3° steps), or the flat angle
 * without [tracking] so the rotation sensor is off while the bar is hidden.
 *
 * Returned as [State] so the read can be deferred to the draw phase: the sensor writes tilt state
 * unthrottled (~50Hz), and a composition-time read would recompose the whole caller scope on every
 * tick. The derivedStateOf equality check then drops draw invalidations to quantization-step
 * crossings.
 */
@Composable
private fun rememberQuantizedGravityAngle(tracking: Boolean): State<Float> {
    if (!tracking) return remember { mutableFloatStateOf(FLAT_GRAVITY_ANGLE_RAD) }
    val tiltState = rememberDeviceTilt()
    return remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            val gx = tilt.gravityX
            val gy = tilt.gravityY
            if (gx * gx + gy * gy > GRAVITY_DIR_THRESHOLD_SQ) {
                (atan2(gy, gx) / GRAVITY_ANGLE_STEP_RAD).roundToInt() * GRAVITY_ANGLE_STEP_RAD
            } else {
                // Near-flat: the in-plane gravity direction is unstable, pin to (0, -1).
                FLAT_GRAVITY_ANGLE_RAD
            }
        }
    }
}

/**
 * [base] with its `dualPeak` primary light rotated to [gravityAngle] plus [extraDegrees]. Read
 * `.value` only at draw time (see [rememberQuantizedGravityAngle]).
 */
@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float,
    gravityAngle: State<Float>,
): State<Highlight> {
    return remember(gravityAngle, base, extraDegrees) {
        derivedStateOf {
            val baseStyle = base.style as BloomStroke
            val basePrimary = baseStyle.primaryLight
            val rad = gravityAngle.value + (extraDegrees * PI / 180.0).toFloat()
            base.copy(
                style =
                    baseStyle.copy(
                        primaryLight =
                            basePrimary.copy(
                                position =
                                    LightPosition(
                                        x = LIGHT_REF_X + cos(rad),
                                        y = LIGHT_REF_Y + sin(rad),
                                        z = basePrimary.position.z,
                                    ),
                            ),
                    ),
            )
        }
    }
}

/**
 * iOS 26-style floating tab bar: a lightly blurred, edge-refracting glass capsule over [backdrop],
 * and a pill that swells into a refracting droplet while pressed and can be dragged across tabs.
 * Inside the pill the tabs take the accent colour: an accent copy of the tab row is recorded into a
 * second backdrop that only the pill samples.
 */
@Composable
internal fun LiquidGlassTabBar(
    destinations: List<BottomBarDestination>,
    selectedIndex: Int,
    enabled: Boolean,
    backdrop: LayerBackdrop,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = remember { CircleShape } // the lens needs a corner-based shape
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = destinations.size
    val contentInsetPx = with(density) { UiDp.dp4.toPx() }

    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    // Derived, not set in onSizeChanged: a new tab (Proxy) changes the count but not the bar's size.
    val tabWidthPx by
        remember(tabsCount, contentInsetPx) {
            derivedStateOf { ((totalWidthPx - contentInsetPx * 2) / tabsCount).coerceAtLeast(0f) }
        }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { UiDp.dp4.toPx() }
    val panelOffset by
        remember(rubberBandPx) {
            derivedStateOf {
                if (totalWidthPx == 0f) {
                    0f
                } else {
                    val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                    rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onTabClickUpdated by rememberUpdatedState(onTabClick)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - contentInsetPx) / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
    }

    val dampedDrag =
        remember(animationScope, tabsCount, density, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                canDrag = { position -> position.x in 0f..totalWidthPx },
                onDragStarted = { position -> updateValue(indexAt(position.x).toFloat()) },
                onDragStopped = {
                    val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                    if (currentIndex != targetIndex) {
                        currentIndex = targetIndex
                        onTabClickUpdated(targetIndex)
                    }
                    updateValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDragCancelled = {
                    updateValue(currentIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    if (tabWidthPx > 0f && dragAmount.x != 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                                .coerceIn(0f, (tabsCount - 1).toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            )
        }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dampedDrag.animateToValue(selectedIndex.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onTabClickUpdated(index)
        }
        dampedDrag.animateToValue(index.toFloat())
    }

    // Keyed on dampedDrag: the position lambda captures it; a stale capture would freeze the spot.
    val interactiveHighlight =
        remember(animationScope, isLtr, dampedDrag) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { layerSize, _ ->
                    Offset(
                        x =
                            if (isLtr) {
                                (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                            } else {
                                layerSize.width - (dampedDrag.value + 0.5f) * tabWidthPx +
                                    panelOffset
                            },
                        y = layerSize.height / 2f,
                    )
                },
            )
        }

    // One sensor listener for both highlights. Read .value only inside highlight lambdas (draw
    // phase), never in composition.
    val gravityAngle = rememberQuantizedGravityAngle(tracking = enabled)
    val baseHighlight = rememberGravityRotatedHighlight(TabBarSpecular, -45f, gravityAngle)
    val pillHighlight = rememberGravityRotatedHighlight(TabBarSpecular, 90f, gravityAngle)

    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    val tabs: @Composable RowScope.(interactive: Boolean) -> Unit = { interactive ->
        val tabScale = LocalTabScale.current
        destinations.forEachIndexed { index, destination ->
            Column(
                modifier =
                    Modifier
                        .then(
                            if (interactive) {
                                Modifier
                                    .semantics(mergeDescendants = true) {
                                        selected = index == currentIndex
                                        role = Role.Tab
                                        if (enabled) {
                                            onClick {
                                                activateTab(index)
                                                true
                                            }
                                        } else {
                                            disabled()
                                        }
                                    }
                                    .onKeyEvent { event ->
                                        val isActivationKey =
                                            event.key == Key.Enter ||
                                                event.key == Key.NumPadEnter ||
                                                event.key == Key.Spacebar
                                        if (enabled && isActivationKey) {
                                            if (event.type == KeyEventType.KeyUp) activateTab(index)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .focusable(enabled = enabled)
                            } else {
                                Modifier
                            }
                        )
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val s = tabScale()
                            scaleX = s
                            scaleY = s
                        },
                verticalArrangement = Arrangement.spacedBy(UiDp.dp1, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(UiDp.dp22),
                    imageVector = destination.icon,
                    // Decorative: the adjacent label names the tab; avoids TalkBack double-read.
                    contentDescription = null,
                )
                Text(
                    text = destination.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalContentColor provides tabContentColor) {
            Row(
                modifier =
                    Modifier
                        .selectableGroup()
                        .onSizeChanged { totalWidthPx = it.width.toFloat() }
                        .graphicsLayer { translationX = panelOffset }
                        .dropShadow(
                            shape = pillShape,
                            shadow =
                                Shadow(
                                    radius = UiDp.dp10,
                                    color = Color.Black,
                                    // Lighter in light theme to avoid a visible gray fringe.
                                    alpha = if (isDark) 0.2f else 0.1f,
                                ),
                        )
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                // 24dp lens refraction + 16dp press-scale reach, raised before
                                // blur() reads it.
                                padding = maxOf(padding, UiDp.dp40.toPx())
                                vibrancy()
                                blur(UiDp.dp4.toPx(), UiDp.dp4.toPx())
                                lens(
                                    refractionHeight = UiDp.dp24.toPx(),
                                    refractionAmount = UiDp.dp24.toPx(),
                                )
                            },
                            highlight = { baseHighlight.value.copy(alpha = 0.75f) },
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val s =
                                    lerp(
                                        1f,
                                        1f + UiDp.dp16.toPx() / width,
                                        dampedDrag.pressProgress,
                                    )
                                scaleX = s
                                scaleY = s
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .then(
                            if (enabled) {
                                interactiveHighlight.gestureModifier.then(dampedDrag.modifier)
                            } else {
                                Modifier
                            }
                        )
                        .height(LiquidGlassTabBarDefaults.Height)
                        .padding(UiDp.dp4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs(true)
            }
        }

        // The accent copy: invisible on screen, recorded into tabsBackdrop for the pill to sample.
        CompositionLocalProvider(
            LocalTabScale provides { lerp(1f, 1.2f, dampedDrag.pressProgress) },
            LocalContentColor provides accentColor,
        ) {
            Row(
                modifier =
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(UiDp.dp4.toPx(), UiDp.dp4.toPx())
                                lens(
                                    refractionHeight = UiDp.dp24.toPx(),
                                    refractionAmount = UiDp.dp24.toPx(),
                                )
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(UiDp.dp56)
                        .padding(horizontal = UiDp.dp4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs(false)
            }
        }

        if (tabWidthPx > 0f) {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = UiDp.dp4)
                        .graphicsLayer {
                            val progressOffset = dampedDrag.value * tabWidthPx
                            translationX =
                                if (isLtr) progressOffset + panelOffset
                                else -progressOffset + panelOffset
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDrag.pressProgress
                                lens(
                                    refractionHeight = UiDp.dp10.toPx() * progress,
                                    refractionAmount = UiDp.dp14.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = {
                                pillHighlight.value.copy(alpha = dampedDrag.pressProgress)
                            },
                            layerBlock = {
                                scaleX = dampedDrag.scaleX
                                scaleY = dampedDrag.scaleY
                                val v = dampedDrag.velocity / 10f
                                scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDrag.pressProgress
                                drawRect(
                                    color =
                                        if (isDark) Color.White.copy(alpha = 0.1f)
                                        else Color.Black.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = UiDp.dp8 * dampedDrag.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDrag.pressProgress,
                            )
                        }
                        .height(UiDp.dp56)
                        .width(with(density) { tabWidthPx.toDp() }),
            )
        }
    }
}

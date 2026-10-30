/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Copyright (c) YumeYucca 2025 - Present
 */

@file:Suppress("DuplicatedCode", "FunctionName")

package com.github.yumeyucca.yumebox.screen.moe


import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer // KimiNoBox
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.yumeyucca.yumebox.data.network.IpInfo
import com.github.yumeyucca.yumebox.domain.model.TrafficData
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import com.github.yumeyucca.yumebox.screen.home.HomeProxyControlState
import com.kyant.shapes.UnevenRoundedRectangle // KimiNoBox
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class MoeHomeLayoutState(
    val wallpaperUri: String,
    val wallpaperZoom: Float,
    val wallpaperBiasX: Float,
    val wallpaperBiasY: Float,
    val statusBarTop: Dp,
    val pageProgress: Float,
    val sidebarProgress: Float,
    val sidebarToggleProgress: Float,
    val duration: MoeDurationPair,
    val batteryPercent: Int?,
    val sidebarIcons: List<MoeSidebarIconItem>,
    val contentSurface: Color,
    val isRunning: Boolean,
    val traffic: TrafficData,
    val selectedServerName: String?,
    val selectedServerPing: Int?,
    val now: Long,
    val quote: String,
    val controlState: HomeProxyControlState,
    val canLaunch: Boolean,
    val isRemoteController: Boolean,
    val usesTabletLayout: Boolean = false,
    val externalIp: IpInfo? = null, // KimiNoBox: egress IP row (E5)
    val delayTesting: Boolean = false, // KimiNoBox
)

internal class MoeHomeActions(
    val toggleSidebar: () -> Unit,
    val pickWallpaper: () -> Unit,
    val openSettings: () -> Unit,
    val toggleProxy: () -> Unit,
    val testDelay: () -> Unit = {}, // KimiNoBox
)

@Composable
context(actions: MoeHomeActions)
internal fun MoeHomeLayout(state: MoeHomeLayoutState) {
    val density = LocalDensity.current
    // Dedicated haze state so the sidebar blurs only the wallpaper layer, not the content panel.
    val sidebarHazeState = rememberHazeState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (state.usesTabletLayout) {
            MoeTabletHomeLayout(state = state, maxWidth = maxWidth, maxHeight = maxHeight)
            return@BoxWithConstraints
        }
        val sidebarWidth = maxWidth * MoeUi.Sidebar.fraction
        val contentStart = (sidebarWidth - MoeUi.Sidebar.contentOverlap).coerceAtLeast(UiDp.dp0)
        // KimiNoBox: a fixed large corner instead of the screen corner makes the panel a D
        val screenCorner = MoeDuoUi.panelCorner
        // KimiNoBox: the quote keeps the expanded panel's width, so swipes never rewrap it
        val quoteWidth =
            maxWidth - contentStart -
                (MoeUi.Hero.containerHorizontalInset + MoeUi.Hero.contentHorizontalInset) * 2
        val page = state.pageProgress.coerceIn(0f, 1f)
        val sidebar = state.sidebarProgress.coerceIn(0f, 1f) * state.sidebarToggleProgress
        val sidebarWidthVisible = lerpDp(MoeUi.Sidebar.collapsedVisibleWidth, contentStart, sidebar)
        val heroHeight =
            (maxHeight - state.statusBarTop).coerceAtLeast(UiDp.dp0) * MoeUi.Hero.heightFraction
        // Haze blur works from API 31; skip the effect while the rail is fully collapsed.
        val blurReady by
        remember(sidebar) {
            derivedStateOf { sidebar > 0.03f }
        }
        val backdropLayer = rememberGraphicsLayer() // KimiNoBox: the Duo strip reads its ink from it
        MoeWallpaperBackground(
            wallpaperUri = state.wallpaperUri,
            wallpaperZoom = state.wallpaperZoom,
            wallpaperBiasX = state.wallpaperBiasX,
            wallpaperBiasY = state.wallpaperBiasY,
            qualityMode = MoeWallpaperQualityMode.BackgroundBlur,
            modifier = Modifier
                .matchParentSize()
                .hazeSource(state = sidebarHazeState)
                .moeDuoBackdrop(backdropLayer), // KimiNoBox
        )
        // KimiNoBox: frosted glass under the whole panel, showing as the status bar on top and the
        // strip on the right like the iPhone Duo outer screen's (MoeDuoSidebar.kt); only the
        // strip's content slides away
        MoeSidebarDecoration(
            hazeState = sidebarHazeState,
            blurEnabled = blurReady,
            blurProgress = sidebar,
            outerEdgeAtEnd = true,
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = lerpFloat(0.78f, 1f, sidebar) * page },
        ) {
            MoeDuoSidebarContent(
                state = state,
                stripWidth = contentStart,
                visibleWidth = sidebarWidthVisible,
                statusTop = state.statusBarTop + MoeDuoUi.heroTopGap,
                hazeState = sidebarHazeState,
                backdropLayer = backdropLayer,
                glassEnabled = blurReady,
                onTestDelay = actions.testDelay,
                onStart = actions.toggleProxy,
                modifier =
                    Modifier.graphicsLayer {
                        translationX = with(density) { lerpDp(56.dp, UiDp.dp0, sidebar).toPx() }
                    },
            )
        }
        MoeHomePanel(state, contentStart, heroHeight, sidebar, screenCorner, quoteWidth)
    }
}

@Composable
context(actions: MoeHomeActions)
private fun MoeHomePanel(
    state: MoeHomeLayoutState,
    contentStart: Dp,
    heroHeight: Dp,
    sidebar: Float,
    screenCorner: Dp,
    quoteWidth: Dp, // KimiNoBox
) {
    val corner = lerpDp(UiDp.dp0, screenCorner, sidebar)
    // KimiNoBox: the panel starts below the frosted status bar, while the hero and the copy keep
    // their place on screen
    val panelTop = lerpDp(UiDp.dp0, state.statusBarTop, sidebar)
    val heroTop = state.statusBarTop + MoeDuoUi.heroTopGap
    val heroScale =
        if (state.pageProgress >= 0.999f) 1f
        else
            lerpFloat(
                1f,
                0.965f,
                FastOutSlowInEasing.transform(1f - state.pageProgress.coerceIn(0f, 1f)),
            )
    Box(
        Modifier
            .fillMaxSize()
            // KimiNoBox: strip on the right, frosted status bar on top
            .padding(top = panelTop, end = lerpDp(UiDp.dp0, contentStart, sidebar))
            .background(
                color = state.contentSurface,
                // KimiNoBox: continuous corners on the strip side only, so the panel reads as a D
                shape = UnevenRoundedRectangle(topEnd = corner, bottomEnd = corner),
            )
    ) {
        // KimiNoBox: positions inside the panel, which moved down by panelTop
        MoeHero(
            state,
            heroScale,
            top = heroTop - panelTop,
            height = heroHeight - MoeDuoUi.heroTopGap,
            // concentric with the D corner, so the gap around it stays even
            topEndRadius = maxOf(MoeUi.Shape.heroRadius, corner - MoeDuoUi.heroTopGap),
        )
        MoeHomeCopy(
            state,
            top = state.statusBarTop + heroHeight + MoeUi.Hero.belowHeroTopGap - panelTop,
            quoteWidth = quoteWidth,
        )
    }
}

@Composable
context(actions: MoeHomeActions)
private fun BoxScope.MoeHero(
    state: MoeHomeLayoutState,
    scale: Float,
    top: Dp, // KimiNoBox
    height: Dp, // KimiNoBox
    topEndRadius: Dp, // KimiNoBox
) {
    val toggleSidebar by rememberUpdatedState(actions.toggleSidebar)
    val pickWallpaper by rememberUpdatedState(actions.pickWallpaper)
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(
                start = MoeUi.Hero.containerHorizontalInset,
                end = MoeUi.Hero.containerHorizontalInset,
                top = top, // KimiNoBox
            )
            .height(height) // KimiNoBox: fixed, so the panel's moving top edge never resizes it
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { toggleSidebar() },
                    onLongPress = { pickWallpaper() },
                )
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = scale
                scaleY = scale
            }
            // KimiNoBox: the top-end corner follows the D corner
            .squircleClip(
                topStart = MoeUi.Shape.heroRadius,
                topEnd = topEndRadius,
                bottomEnd = MoeUi.Shape.heroRadius,
                bottomStart = MoeUi.Shape.heroRadius,
            )
    ) {
        MoeWallpaperBackground(
            wallpaperUri = state.wallpaperUri,
            wallpaperZoom = state.wallpaperZoom,
            wallpaperBiasX = state.wallpaperBiasX,
            wallpaperBiasY = state.wallpaperBiasY,
            qualityMode = MoeWallpaperQualityMode.Foreground,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        // Fully opaque from 0.90 so the rounded corner band never lets the
                        // wallpaper bleed through as a colored line (issue #151 item 6); the
                        // longer fade avoids the hard mid-way cut (item 5).
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.78f to state.contentSurface.copy(alpha = 0.88f),
                        0.90f to state.contentSurface,
                        1f to state.contentSurface,
                    )
                )
        )
        AnimatedVisibility(
            visible = state.isRunning,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = MoeUi.Hero.contentHorizontalInset,
                        end = MoeUi.Hero.contentHorizontalInset,
                        bottom = MoeUi.Hero.trafficBottomInset,
                    ),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MoeUi.Hero.runtimeInfoTopGap)) {
                MoeCardUptime(state.duration, 1f - state.sidebarToggleProgress) // KimiNoBox: E2
                MoeTrafficStrip(state.traffic.download, state.traffic.upload)
                MoeHomeInfoPanel(
                    serverName = state.selectedServerName.takeIf { state.isRunning },
                    serverPing = state.selectedServerPing.takeIf { state.isRunning },
                    modifier = Modifier.fillMaxWidth(),
                    delayTesting = state.delayTesting, // KimiNoBox
                    onTestDelay = actions.testDelay, // KimiNoBox
                )
                MoeCardExternalIp(state.externalIp) // KimiNoBox: E5
            }
        }
    }
}

@Composable
context(actions: MoeHomeActions)
private fun BoxScope.MoeHomeCopy(
    state: MoeHomeLayoutState,
    top: Dp, // KimiNoBox
    quoteWidth: Dp, // KimiNoBox
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(
                    start = MoeUi.Hero.containerHorizontalInset + MoeUi.Hero.contentHorizontalInset,
                    end = MoeUi.Hero.containerHorizontalInset + MoeUi.Hero.contentHorizontalInset,
                    top = top, // KimiNoBox
                ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MoeUi.Hero.belowHeroContentGap),
    ) {
        MoeHomeCopyBlock(
            nowMillis = state.now,
            quoteText = state.quote,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            quoteWidth = quoteWidth, // KimiNoBox
        ) {
            MoeLaunchControls(
                controlState = state.controlState,
                enabled = state.canLaunch && state.controlState.canInteract,
                isRemoteController = state.isRemoteController,
                surfaceColor = state.contentSurface,
                modifier = Modifier.fillMaxWidth(),
                onSettingsClick = actions.openSettings,
                onLaunchClick = actions.toggleProxy,
            )
        }
    }
}

@Composable
context(actions: MoeHomeActions)
private fun MoeTabletHomeLayout(
    state: MoeHomeLayoutState,
    maxWidth: Dp,
    maxHeight: Dp,
) {
    val pickWallpaper by rememberUpdatedState(actions.pickWallpaper)
    val shortHeight = maxHeight < UiDp.dp560
    // Keep the Moe panel readable on ultra-wide screens without a right-side config pane.
    val contentMaxWidth = minOf(maxWidth, UiDp.dp560)
    // Phone-landscape panes are barely wider than the content itself; a floor keeps the launch
    // controls clear of the divider and screen edges instead of pressing against them.
    val horizontalGutter = ((maxWidth - contentMaxWidth) / 2f).coerceIn(UiDp.dp16, maxWidth / 2f)
    val heroHeightFraction = if (shortHeight) 0.50f else MoeUi.Hero.heightFraction
    val heroHeight = (maxHeight - state.statusBarTop).coerceAtLeast(UiDp.dp0) * heroHeightFraction

    Box(Modifier
        .fillMaxSize()
        .background(state.contentSurface)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (shortHeight) Modifier else Modifier.fillMaxHeight())
                    .padding(horizontal = horizontalGutter)
                    .then(
                        if (shortHeight) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    )
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MoeUi.Hero.containerHorizontalInset,
                        end = MoeUi.Hero.containerHorizontalInset,
                        top = state.statusBarTop,
                    )
                    .height(heroHeight)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { pickWallpaper() })
                    }
                    .squircleClip(MoeUi.Shape.heroRadius)
            ) {
                MoeWallpaperBackground(
                    wallpaperUri = state.wallpaperUri,
                    wallpaperZoom = state.wallpaperZoom,
                    wallpaperBiasX = state.wallpaperBiasX,
                    wallpaperBiasY = state.wallpaperBiasY,
                    qualityMode = MoeWallpaperQualityMode.Foreground,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.78f to state.contentSurface.copy(alpha = 0.88f),
                                0.90f to state.contentSurface,
                                1f to state.contentSurface,
                            )
                        )
                )
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(start = MoeUi.Hero.contentHorizontalInset, end = MoeUi.Hero.contentHorizontalInset, top = UiDp.dp14),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.duration.top}:${state.duration.bottom}",
                        color = Color.White.copy(alpha = 0.94f),
                        style = MiuixTheme.textStyles.title3,
                    )
                    state.batteryPercent?.let { percent ->
                        Text(
                            text = "$percent%",
                            color = Color.White.copy(alpha = 0.86f),
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(
                                start = MoeUi.Hero.contentHorizontalInset,
                                end = MoeUi.Hero.contentHorizontalInset,
                                bottom = MoeUi.Hero.trafficBottomInset,
                            )
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.isRunning,
                        enter = fadeIn() + slideInVertically { it / 3 },
                        exit = fadeOut() + slideOutVertically { it / 3 },
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MoeUi.Hero.runtimeInfoTopGap)
                        ) {
                            MoeTrafficStrip(state.traffic.download, state.traffic.upload)
                            MoeHomeInfoPanel(
                                serverName = state.selectedServerName.takeIf { state.isRunning },
                                serverPing = state.selectedServerPing.takeIf { state.isRunning },
                                modifier = Modifier.fillMaxWidth(),
                                delayTesting = state.delayTesting, // KimiNoBox
                                onTestDelay = actions.testDelay, // KimiNoBox
                            )
                            MoeCardExternalIp(state.externalIp) // KimiNoBox: E5
                        }
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (shortHeight) Modifier else Modifier.weight(1f, fill = true))
                        .padding(
                            start =
                                MoeUi.Hero.containerHorizontalInset +
                                        MoeUi.Hero.contentHorizontalInset,
                            end =
                                MoeUi.Hero.containerHorizontalInset +
                                        MoeUi.Hero.contentHorizontalInset,
                            top = MoeUi.Hero.belowHeroTopGap,
                            bottom = UiDp.dp12,
                        ),
                verticalArrangement = Arrangement.spacedBy(MoeUi.Hero.belowHeroContentGap),
            ) {
                MoeHomeCopyBlock(
                    nowMillis = state.now,
                    quoteText = state.quote,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MoeLaunchControls(
                        controlState = state.controlState,
                        enabled = state.canLaunch && state.controlState.canInteract,
                        isRemoteController = state.isRemoteController,
                        surfaceColor = state.contentSurface,
                        modifier = Modifier.fillMaxWidth(),
                        onSettingsClick = actions.openSettings,
                        onLaunchClick = actions.toggleProxy,
                    )
                }
            }
        }
    }
}

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

@file:Suppress("FunctionName")

package com.github.yumeyucca.yumebox.screen.moe

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getRoundedCorner

internal data class MoeHomeLayoutState(
    val wallpaperUri: String,
    val wallpaperZoom: Float,
    val wallpaperBiasX: Float,
    val wallpaperBiasY: Float,
    val statusBarTop: Dp,
    // Pager progress is read where it is drawn, not here: it changes on every scroll frame, and a
    // plain Float in this state would recompose the whole home layout as the pager moves.
    val pageProgress: State<Float>,
    val sidebarProgress: State<Float>,
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
    val quoteAuthor: String,
    val controlState: HomeProxyControlState,
    val canLaunch: Boolean,
    val isRemoteController: Boolean,
    val hasUpdate: Boolean,
    val externalIp: IpInfo? = null, // KimiNoBox: egress IP row (E5)
    val delayTesting: Boolean = false, // KimiNoBox
)

internal class MoeHomeActions(
    val toggleSidebar: () -> Unit,
    val pickWallpaper: () -> Unit,
    val openSettings: () -> Unit,
    val toggleProxy: () -> Unit,
    val openUpdate: () -> Unit,
    val testDelay: () -> Unit = {}, // KimiNoBox
)

/** Which home layout to compose; the window layout mode decides once, at the call site. */
internal enum class MoeHomeLayoutMode {
    Phone,
    Tablet,
}

/** Where the hero card sits and how big it is; both layouts share the one derived value. */
internal data class MoeHeroGeometry(
    val top: Dp,
    val height: Dp,
    val radius: Dp,
)

private fun moeHeroGeometry(
    statusBarTop: Dp,
    maxHeight: Dp,
    deviceCorner: Dp,
): MoeHeroGeometry =
    MoeHeroGeometry(
        top = statusBarTop,
        height = (maxHeight - statusBarTop).coerceAtLeast(UiDp.dp0) * MoeUi.Hero.heightFraction,
        radius = MoeUi.Shape.heroCornerRadius(deviceCorner),
    )

@Composable
context(actions: MoeHomeActions)
internal fun MoeHomeLayout(
    state: MoeHomeLayoutState,
    layout: MoeHomeLayoutMode,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val greetingColor =
            rememberMoeWallpaperAccentColor(
                wallpaperUri = state.wallpaperUri,
                surface = state.contentSurface,
                fallback = MiuixTheme.colorScheme.primary,
            )
        // Read the device corner once and floor it once: the panel, its decoration and the hero
        // card all inherit the same radius, so a device that reports a flat corner cannot leave
        // any of them square.
        val deviceCorner = MoeUi.Shape.panelCorner(getRoundedCorner())
        val hero =
            moeHeroGeometry(
                statusBarTop = state.statusBarTop,
                maxHeight = maxHeight,
                deviceCorner = deviceCorner,
            )
        when (layout) {
            MoeHomeLayoutMode.Phone -> {
                MoePhoneHomeLayout(state, greetingColor, maxWidth, maxHeight, hero, deviceCorner)
            }

            MoeHomeLayoutMode.Tablet -> {
                MoeTabletHomeLayout(state, greetingColor, maxWidth, maxHeight, hero)
            }
        }
    }
}

@Composable
context(actions: MoeHomeActions)
private fun BoxScope.MoePhoneHomeLayout(
    state: MoeHomeLayoutState,
    greetingColor: Color,
    maxWidth: Dp,
    maxHeight: Dp,
    hero: MoeHeroGeometry,
    screenCorner: Dp,
) {
    val density = LocalDensity.current
    // Dedicated layer backdrop so the sidebar glass blurs only the wallpaper layer, not the
    // content panel.
    val sidebarBackdrop = rememberLayerBackdrop { drawContent() }
    val sidebarWidth = maxWidth * MoeUi.Sidebar.fraction
    val contentStart = (sidebarWidth - MoeUi.Sidebar.contentOverlap).coerceAtLeast(UiDp.dp0)
    // KimiNoBox: the quote keeps the expanded panel's width, so swipes never rewrap it
    val quoteWidth =
        maxWidth - contentStart - (MoeUi.Hero.containerHorizontalInset + MoeUi.Hero.contentHorizontalInset) * 2
    val sidebar = state.sidebarProgress.value.coerceIn(0f, 1f) * state.sidebarToggleProgress
    val sidebarWidthVisible = lerpDp(MoeUi.Sidebar.collapsedVisibleWidth, contentStart, sidebar)
    // Glass blur needs a RuntimeShader (API 33+); miuix falls back to a solid surface without
    // one. Skip the effect entirely while the rail is fully collapsed.
    val blurEnabled = sidebar > 0.03f
    val backdropLayer = rememberGraphicsLayer() // KimiNoBox: the Duo strip reads its ink from it
    MoeWallpaperBackground(
        wallpaperUri = state.wallpaperUri,
        wallpaperZoom = state.wallpaperZoom,
        wallpaperBiasX = state.wallpaperBiasX,
        wallpaperBiasY = state.wallpaperBiasY,
        qualityMode = MoeWallpaperQualityMode.BackgroundBlur,
        modifier =
            Modifier.matchParentSize()
                .layerBackdrop(sidebarBackdrop)
                .moeDuoBackdrop(backdropLayer), // KimiNoBox
    )
    // KimiNoBox: frosted glass under the whole panel, showing as the status bar on top and the
    // strip on the right like the iPhone Duo outer screen's (MoeDuoSidebar.kt); only the strip's
    // content slides away
    MoeSidebarDecoration(
        backdrop = sidebarBackdrop,
        blurEnabled = blurEnabled,
        blurProgress = sidebar,
        outerEdgeAtEnd = true,
        modifier =
            Modifier.matchParentSize().graphicsLayer {
                alpha = lerpFloat(0.78f, 1f, sidebar) * state.pageProgress.value.coerceIn(0f, 1f)
            },
    ) {
        MoeDuoSidebarContent(
            state = state,
            stripWidth = contentStart,
            visibleWidth = sidebarWidthVisible,
            statusTop = state.statusBarTop + MoeDuoUi.heroTopGap,
            glassBackdrop = sidebarBackdrop,
            backdropLayer = backdropLayer,
            glassEnabled = blurEnabled,
            onTestDelay = actions.testDelay,
            onStart = actions.toggleProxy,
            modifier =
                Modifier.graphicsLayer {
                    translationX = with(density) { lerpDp(56.dp, UiDp.dp0, sidebar).toPx() }
                },
        )
    }
    MoeHomePanel(
        state = state,
        greetingColor = greetingColor,
        contentStart = contentStart,
        hero = hero,
        sidebar = sidebar,
        // KimiNoBox: a fixed large corner instead of the screen corner makes the panel a D
        screenCorner = MoeDuoUi.panelCorner,
        quoteWidth = quoteWidth,
    )
}

@Composable
context(actions: MoeHomeActions)
private fun MoeHomePanel(
    state: MoeHomeLayoutState,
    greetingColor: Color,
    contentStart: Dp,
    hero: MoeHeroGeometry,
    sidebar: Float,
    screenCorner: Dp,
    quoteWidth: Dp, // KimiNoBox
) {
    val corner = lerpDp(UiDp.dp0, screenCorner, sidebar)
    // KimiNoBox: the panel starts below the frosted status bar, while the hero and the copy keep
    // their place on screen
    val panelTop = lerpDp(UiDp.dp0, state.statusBarTop, sidebar)
    val heroTop = state.statusBarTop + MoeDuoUi.heroTopGap
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // KimiNoBox: strip on the right, frosted status bar on top
                .padding(top = panelTop, end = lerpDp(UiDp.dp0, contentStart, sidebar))
                .background(
                    color = state.contentSurface,
                    // KimiNoBox: continuous corners on the strip side only, so the panel reads as a D
                    shape = UnevenRoundedRectangle(topEnd = corner, bottomEnd = corner),
                ),
    ) {
        // KimiNoBox: positions inside the panel, which moved down by panelTop
        MoeHeroCard(
            state = state,
            geometry =
                hero.copy(
                    top = heroTop - panelTop,
                    height = hero.height - MoeDuoUi.heroTopGap,
                ),
            modifier = Modifier.align(Alignment.TopStart),
            onDoubleTap = actions.toggleSidebar,
            // concentric with the D corner, so the gap around it stays even
            topEndRadius = maxOf(hero.radius, corner - MoeDuoUi.heroTopGap),
        )
        MoeHomeCopySection(
            state = state,
            greetingColor = greetingColor,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            topPadding = hero.top + hero.height + MoeUi.Hero.belowHeroTopGap - panelTop,
            quoteWidth = quoteWidth,
        )
    }
}

/**
 * Hero card shell shared by both layouts: [MoeHeroOverlay] owns wallpaper, scrim and runtime info; this only owns the
 * card frame, its gestures and the page-turn transform.
 */
@Composable
context(actions: MoeHomeActions)
private fun MoeHeroCard(
    state: MoeHomeLayoutState,
    geometry: MoeHeroGeometry,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
    headerContent: @Composable BoxScope.() -> Unit = {},
    topEndRadius: Dp = geometry.radius, // KimiNoBox: the Duo panel's D corner
) {
    val pickWallpaper by rememberUpdatedState(actions.pickWallpaper)
    val doubleTap = rememberUpdatedState(onDoubleTap)
    Box(
        modifier
            .fillMaxWidth()
            .padding(
                start = MoeUi.Hero.containerHorizontalInset,
                end = MoeUi.Hero.containerHorizontalInset,
                top = geometry.top,
            ).height(geometry.height)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { doubleTap.value?.invoke() },
                    onLongPress = { pickWallpaper() },
                )
            }.graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                // At rest the eased term is exactly 0, so the hero sits at scale 1 without a
                // special case. Derived here rather than in the caller so a page turn only
                // re-runs this layer block instead of recomposing the card.
                val pageTurn = FastOutSlowInEasing.transform(1f - state.pageProgress.value.coerceIn(0f, 1f))
                val scale = lerpFloat(1f, MoeUi.Hero.pageTurnScale, pageTurn)
                scaleX = scale
                scaleY = scale
            }.squircleClip(
                topStart = geometry.radius,
                topEnd = topEndRadius,
                bottomEnd = geometry.radius,
                bottomStart = geometry.radius,
            ),
    ) {
        MoeHeroOverlay(state, headerContent)
    }
}

@Composable
context(actions: MoeHomeActions)
private fun BoxScope.MoeHeroOverlay(
    state: MoeHomeLayoutState,
    headerContent: @Composable BoxScope.() -> Unit = {},
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
                    // Fully opaque from 0.92 so the rounded corner band never lets the
                    // wallpaper bleed through as a colored line (issue #151 item 6); the
                    // longer fade avoids the hard mid-way cut (item 5).
                    0f to Color.Transparent,
                    0.52f to Color.Transparent,
                    0.82f to state.contentSurface.copy(alpha = 0.88f),
                    0.92f to state.contentSurface,
                    1f to state.contentSurface,
                ),
            ),
    )
    headerContent()
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

@Composable
context(actions: MoeHomeActions)
private fun MoeHomeCopySection(
    state: MoeHomeLayoutState,
    greetingColor: Color,
    modifier: Modifier,
    topPadding: Dp,
    bottomPadding: Dp = UiDp.dp0,
    quoteWidth: Dp = Dp.Unspecified, // KimiNoBox
) {
    val horizontalPadding = MoeUi.Hero.containerHorizontalInset + MoeUi.Hero.contentHorizontalInset
    Column(
        modifier =
            modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = topPadding,
                bottom = bottomPadding,
            ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MoeUi.Hero.belowHeroContentGap),
    ) {
        MoeHomeCopyBlock(
            nowMillis = state.now,
            quoteText = state.quote,
            quoteAuthor = state.quoteAuthor,
            greetingColor = greetingColor,
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
                hasUpdate = state.hasUpdate,
                onUpdateClick = actions.openUpdate,
            )
        }
    }
}

/** A pane too short for the hero and the copy block to share the height scrolls as one column. */
private enum class MoeTabletFlow {
    Scrolling,
    Filling,
}

// Keep the Moe panel readable on ultra-wide screens without a right-side config pane.
private val TabletPanelMaxWidth = UiDp.dp560

// Below this height the pane can no longer pin the hero above a weighted copy block.
private val TabletShortWindow = UiDp.dp560

// The split shell only ever hands the home a narrow pane, so the tablet layout pins the rail open
// as a fixed control column instead of fading it in on a toggle the pane cannot reach.
private val TabletSidebarWidth = 76.dp

private fun moeTabletPanelModifier(
    flow: MoeTabletFlow,
    horizontalGutter: Dp,
    scrollState: ScrollState,
): Modifier =
    when (flow) {
        MoeTabletFlow.Scrolling -> {
            Modifier.fillMaxWidth().padding(horizontal = horizontalGutter).verticalScroll(scrollState)
        }

        MoeTabletFlow.Filling -> {
            Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = horizontalGutter)
        }
    }

private fun ColumnScope.moeTabletCopyModifier(flow: MoeTabletFlow): Modifier =
    when (flow) {
        MoeTabletFlow.Scrolling -> Modifier.fillMaxWidth()
        MoeTabletFlow.Filling -> Modifier.fillMaxWidth().weight(1f, fill = true)
    }

@Composable
context(actions: MoeHomeActions)
private fun MoeTabletHomeLayout(
    state: MoeHomeLayoutState,
    greetingColor: Color,
    maxWidth: Dp,
    maxHeight: Dp,
    hero: MoeHeroGeometry,
) {
    val flow = if (maxHeight < TabletShortWindow) MoeTabletFlow.Scrolling else MoeTabletFlow.Filling
    val scrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize().background(state.contentSurface)) {
        MoeSidebarDecoration(
            backdrop = null,
            blurEnabled = false,
            blurProgress = 0f,
            modifier = Modifier.width(TabletSidebarWidth).fillMaxHeight(),
        ) {
            MoeSidebarContent(
                topValue = state.duration.top,
                bottomValue = state.duration.bottom,
                batteryPercent = state.batteryPercent,
                icons = state.sidebarIcons,
                visibleWidth = TabletSidebarWidth,
            )
        }
        // No wallpaper sits behind this pane, so the glass has nothing to separate the rail from
        // the panel with; a hairline draws the boundary instead.
        VerticalDivider()
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
            MoeTabletHomePanel(
                state = state,
                greetingColor = greetingColor,
                maxWidth = maxWidth,
                hero = hero,
                flow = flow,
                scrollState = scrollState,
            )
        }
    }
}

@Composable
context(actions: MoeHomeActions)
private fun MoeTabletHomePanel(
    state: MoeHomeLayoutState,
    greetingColor: Color,
    maxWidth: Dp,
    hero: MoeHeroGeometry,
    flow: MoeTabletFlow,
    scrollState: ScrollState,
) {
    val contentMaxWidth = minOf(maxWidth, TabletPanelMaxWidth)
    // Phone-landscape panes are barely wider than the content itself; a floor keeps the launch
    // controls clear of the divider and screen edges instead of pressing against them.
    val horizontalGutter = ((maxWidth - contentMaxWidth) / 2f).coerceIn(UiDp.dp16, maxWidth / 2f)

    Column(moeTabletPanelModifier(flow, horizontalGutter, scrollState)) {
        // The rail already carries the clock and battery, so the hero stays a pure wallpaper card.
        MoeHeroCard(state = state, geometry = hero)

        MoeHomeCopySection(
            state = state,
            greetingColor = greetingColor,
            modifier = moeTabletCopyModifier(flow),
            topPadding = MoeUi.Hero.belowHeroTopGap,
            bottomPadding = UiDp.dp12,
        )
    }
}

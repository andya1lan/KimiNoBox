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

package com.github.yumeyucca.yumebox.feature.editor.codemirror

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.github.yumeyucca.yumebox.feature.editor.R
import com.github.yumeyucca.yumebox.feature.editor.editor.CodeEditorState
import com.github.yumeyucca.yumebox.feature.editor.editor.editorWordWrap
import com.github.yumeyucca.yumebox.presentation.theme.UiDp
import io.github.yumeyucca.lucide.Lucide
import io.github.yumeyucca.lucide.lucide.Search
import java.io.ByteArrayInputStream
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KimiNoBox: the code editor behind every editor screen, CodeMirror 6 in a WebView (feature/editor/codemirror). It
 * shows spaces, tabs and line ends, and a symbol bar above the keyboard while the text is being typed into.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodeMirrorEditor(
    state: CodeEditorState,
    modifier: Modifier,
    fontSizeSp: Float,
    onScroll: ((Int) -> Unit)?,
    onTextChange: ((String) -> Unit)?,
) {
    val handle = remember(state) { CodeMirrorHandle(state.content, state.language, state.readOnly) }
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnScroll by rememberUpdatedState(onScroll)

    DisposableEffect(handle) {
        state.editor = handle
        handle.onChange = {
            state.syncContentFromEditor()
            currentOnTextChange?.invoke(state.content)
        }
        handle.onScroll = { y -> currentOnScroll?.invoke(y) }
        onDispose {
            if (state.editor === handle) state.editor = null
            handle.onChange = null
            handle.onScroll = null
        }
    }

    // Text set while no editor was attached
    LaunchedEffect(handle, state.content) { if (handle.text != state.content) handle.setText(state.content) }

    // Read here, not in SideEffect, so that a change recomposes this
    val wordWrap = editorWordWrap().state.collectAsState().value
    val palette = rememberCodeMirrorPalette()
    val fontPx = fontSizeSp * LocalDensity.current.fontScale
    SideEffect {
        handle.setWrap(wordWrap)
        handle.setFontPx(fontPx)
        handle.setColors(palette.css)
    }

    val imeVisible = WindowInsets.isImeVisible
    val typing = !state.readOnly && handle.hasFocus && imeVisible

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context -> createCodeMirrorView(context, handle, palette.background.toArgb()) },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    handle.detach()
                    view.destroy()
                },
            )
            if (!imeVisible) {
                SearchButton(
                    onClick = handle::toggleSearch,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(UiDp.dp16),
                )
            }
        }
        if (typing) CodeMirrorSymbolBar(handle)
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(UiDp.dp44)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh.copy(alpha = 0.92f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Search, contentDescription = "查找", tint = colors.onSurface, modifier = Modifier.size(UiDp.dp20))
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createCodeMirrorView(context: Context, handle: CodeMirrorHandle, background: Int): WebView =
    WebView(context).apply {
        // With AndroidView's default WRAP_CONTENT, WebView sizes the page to its content, and height: 100% is 0
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(background)
        // The glass top bar and popups draw the page a second time, blurred. WebView content drawn twice per frame
        // flickers (parts of the page drop out every other frame), so it renders once into its own layer.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            safeBrowsingEnabled = false
        }
        webViewClient = PageClient(context.applicationContext, handle)
        webChromeClient = ConsoleClient
        // Debug builds can be inspected from chrome://inspect
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        handle.attach(this)
        loadUrl("https://$PAGE_HOST/index.html")
    }

/** Serves the page from the APK; nothing it asks for goes to the network. */
private class PageClient(private val context: Context, private val handle: CodeMirrorHandle) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        if (url.scheme != "https" || url.host != PAGE_HOST) return notFound()
        return when (url.path) {
            "/index.html" -> asset("codemirror/index.html", "text/html")
            "/editor.js" -> asset("codemirror/editor.js", "text/javascript")
            "/font.ttf" ->
                WebResourceResponse("font/ttf", null, context.resources.openRawResource(R.font.jetbrains_mono_regular))
            else -> notFound()
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    override fun onPageFinished(view: WebView, url: String) {
        handle.open()
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Timber.w("Editor renderer gone, crashed=${detail.didCrash()}")
        handle.lost()
        return true
    }

    private fun asset(path: String, mimeType: String) =
        WebResourceResponse(mimeType, "utf-8", context.assets.open(path))

    private fun notFound() =
        WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

/** The page's warnings and errors, in logcat under [LOG_TAG]. */
private object ConsoleClient : WebChromeClient() {
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val level = message.messageLevel()
        if (level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING) {
            Log.w(LOG_TAG, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
        }
        return true
    }
}

private const val PAGE_HOST = "editor.kiminobox.local"
private const val LOG_TAG = "KimiEditor"

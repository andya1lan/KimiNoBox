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

package com.github.yumeyucca.yumebox.feature.editor.codemirror

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.yumeyucca.yumebox.core.model.LanguageScope
import org.json.JSONObject

/** Where a symbol bar arrow moves the caret. */
enum class CaretMove(internal val js: String) {
    Left("left"),
    Right("right"),
    Up("up"),
    Down("down"),
}

/**
 * KimiNoBox: the editor a `CodeEditorState` drives, CodeMirror 6 in a WebView (feature/editor/codemirror). Its methods
 * keep the names Sora's CodeEditor had, so upstream code written against `state.editor` keeps compiling.
 *
 * Until the page is ready, calls only update what the page opens with.
 */
class CodeMirrorHandle internal constructor(initialText: String, language: LanguageScope, readOnly: Boolean) {
    /** The editor's text as of its last change. */
    var text: String = initialText
        private set

    /** Whether the text itself has the input focus; the search fields don't count. */
    internal var hasFocus: Boolean by mutableStateOf(false)
        private set

    private var undoable: Boolean by mutableStateOf(false)
    private var redoable: Boolean by mutableStateOf(false)

    internal var onChange: (() -> Unit)? = null
    internal var onScroll: ((Int) -> Unit)? = null

    private var webView: WebView? = null
    private var ready = false

    private val language = language.jsName
    private val readOnly = readOnly
    private var wrap = false
    private var fontPx = 0f
    private var colors = JSONObject()

    /** What the page was opened with, to send what changed while it was loading. */
    private var opened: Opened? = null

    fun canUndo(): Boolean = undoable

    fun canRedo(): Boolean = redoable

    fun undo() = call("kimi.undo()")

    fun redo() = call("kimi.redo()")

    /** Replaces the whole text and forgets the undo history. */
    fun setText(newText: String) {
        text = newText
        undoable = false
        redoable = false
        call("kimi.setText(${JSONObject.quote(newText)})")
    }

    /** Inserts [insertion] over the selection and puts the caret [selectionOffset] characters into it. */
    fun insertText(insertion: String, selectionOffset: Int) =
        call("kimi.insert(${JSONObject.quote(insertion)}, $selectionOffset)")

    /** Indents the selected lines, or inserts one indent unit at the caret. */
    fun indentOrCommitTab() = call("kimi.indent()")

    fun outdent() = call("kimi.outdent()")

    /** Types [symbol] like a key would: brackets and quotes pair up the same way they do from the keyboard. */
    fun type(symbol: String) = call("kimi.type(${JSONObject.quote(symbol)})")

    fun moveCaret(move: CaretMove) = call("kimi.move('${move.js}')")

    /** Opens the search panel at the top, or closes it. */
    fun toggleSearch() = call("kimi.toggleSearch()")

    internal fun setWrap(on: Boolean) {
        if (wrap == on) return
        wrap = on
        call("kimi.setWrap($on)")
    }

    internal fun setFontPx(px: Float) {
        if (fontPx == px) return
        fontPx = px
        call("kimi.setFontSize($px)")
    }

    internal fun setColors(palette: JSONObject) {
        if (colors.toString() == palette.toString()) return
        colors = palette
        call("kimi.setColors($palette)")
    }

    internal fun attach(view: WebView) {
        webView = view
        view.addJavascriptInterface(Bridge(), "KimiBridge")
    }

    internal fun detach() {
        ready = false
        webView = null
        hasFocus = false
    }

    /** The page has loaded: open the editor with everything known so far. */
    internal fun open() {
        val options =
            JSONObject()
                .put("text", text)
                .put("language", language)
                .put("readOnly", readOnly)
                .put("wrap", wrap)
                .put("fontSize", fontPx.toDouble())
                .put("colors", colors)
        opened = Opened(text, wrap, fontPx, colors.toString())
        webView?.evaluateJavascript("kimi.setup($options)", null)
    }

    /** The renderer is gone, so the page is gone too, until the screen is opened again. */
    internal fun lost() {
        ready = false
        hasFocus = false
    }

    private fun onReady() {
        ready = true
        val opened = opened ?: return
        if (text != opened.text) call("kimi.setText(${JSONObject.quote(text)})")
        if (wrap != opened.wrap) call("kimi.setWrap($wrap)")
        if (fontPx != opened.fontPx) call("kimi.setFontSize($fontPx)")
        if (colors.toString() != opened.colors) call("kimi.setColors($colors)")
    }

    private fun onChanged(newText: String, canUndo: Boolean, canRedo: Boolean) {
        text = newText
        undoable = canUndo
        redoable = canRedo
        onChange?.invoke()
    }

    private fun call(script: String) {
        if (ready) webView?.evaluateJavascript(script, null)
    }

    private data class Opened(val text: String, val wrap: Boolean, val fontPx: Float, val colors: String)

    /** What the page calls; WebView calls it on its own thread, so everything hops to the main thread. */
    private inner class Bridge {
        private val main = Handler(Looper.getMainLooper())

        @JavascriptInterface fun ready() = post { onReady() }

        @JavascriptInterface
        fun changed(text: String, canUndo: Boolean, canRedo: Boolean) = post { onChanged(text, canUndo, canRedo) }

        @JavascriptInterface fun focused(focused: Boolean) = post { hasFocus = focused }

        @JavascriptInterface fun scrolled(y: Int) = post { onScroll?.invoke(y) }

        private fun post(block: () -> Unit) {
            main.post { if (webView != null) block() }
        }
    }
}

private val LanguageScope.jsName: String
    get() =
        when (this) {
            LanguageScope.Yaml -> "yaml"
            LanguageScope.Json -> "json"
            LanguageScope.JavaScript -> "javascript"
            LanguageScope.Text -> "text"
        }

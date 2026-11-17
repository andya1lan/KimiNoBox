// KimiNoBox: the code editor every editor screen shows, CodeMirror 6 in a WebView.
// The Kotlin side (CodeMirrorEditor.kt) drives it through window.kimi and hears back through window.KimiBridge.

import { EditorState, Compartment, RangeSet, RangeSetBuilder, StateField } from "@codemirror/state";
import {
  EditorView,
  Decoration,
  GutterMarker,
  ViewPlugin,
  gutterLineClass,
  keymap,
  lineNumbers,
  highlightSpecialChars,
  highlightWhitespace,
} from "@codemirror/view";
import {
  defaultKeymap,
  history,
  historyKeymap,
  indentWithTab,
  undo,
  redo,
  undoDepth,
  redoDepth,
  indentMore,
  indentLess,
  cursorCharLeft,
  cursorCharRight,
  cursorLineUp,
  cursorLineDown,
} from "@codemirror/commands";
import {
  HighlightStyle,
  bracketMatching,
  codeFolding,
  foldGutter,
  foldKeymap,
  foldedRanges,
  indentOnInput,
  indentUnit,
  syntaxHighlighting,
  syntaxTree,
} from "@codemirror/language";
import {
  closeSearchPanel,
  openSearchPanel,
  search,
  searchKeymap,
  searchPanelOpen,
} from "@codemirror/search";
import { closeBrackets, closeBracketsKeymap, insertBracket } from "@codemirror/autocomplete";
import { yaml } from "@codemirror/lang-yaml";
import { json } from "@codemirror/lang-json";
import { javascript } from "@codemirror/lang-javascript";
import { tags as t } from "@lezer/highlight";

const bridge = window.KimiBridge;

// The colours come from the app as CSS variables (see CodeMirrorTheme.kt), so one theme serves light and dark.
const highlightStyle = HighlightStyle.define([
  { tag: [t.definition(t.propertyName), t.propertyName], color: "var(--tok-key)" },
  { tag: [t.string, t.special(t.string)], color: "var(--tok-string)" },
  { tag: t.content, color: "var(--tok-plain)" },
  { tag: [t.number, t.integer, t.float], color: "var(--tok-number)" },
  { tag: [t.bool, t.null, t.atom], color: "var(--tok-constant)" },
  // Not italic: slanted CJK reads badly
  { tag: [t.lineComment, t.blockComment, t.comment], color: "var(--tok-comment)" },
  { tag: [t.labelName, t.typeName], color: "var(--tok-anchor)" },
  { tag: [t.keyword, t.controlKeyword, t.moduleKeyword, t.operatorKeyword, t.definitionKeyword], color: "var(--tok-keyword)" },
  { tag: [t.function(t.variableName), t.function(t.propertyName)], color: "var(--tok-function)" },
  { tag: [t.separator, t.punctuation, t.squareBracket, t.brace, t.paren, t.meta], color: "var(--tok-punctuation)" },
  { tag: t.invalid, color: "var(--tok-invalid)" },
]);

const baseTheme = EditorView.theme({
  "&": { height: "100%", color: "var(--fg)", backgroundColor: "var(--bg)" },
  "&.cm-focused": { outline: "none" },
  // No ligatures: === and != should look like what was typed
  ".cm-scroller": {
    fontFamily: "'JetBrains Mono', monospace",
    fontVariantLigatures: "none",
    lineHeight: "1.5",
    overscrollBehavior: "contain",
  },
  ".cm-content": { caretColor: "var(--cursor)", padding: "4px 0 30vh" },
  ".cm-content ::selection, .cm-content::selection": { backgroundColor: "var(--selection)" },
  ".cm-gutters": { backgroundColor: "var(--bg)", color: "var(--gutter)", border: "none" },
  ".cm-lineNumbers .cm-gutterElement": { padding: "0 4px 0 12px", minWidth: "2ch" },
  ".cm-activeLineGutter": { backgroundColor: "transparent", color: "var(--gutter-active)" },
  ".cm-activeLine": { backgroundColor: "var(--active-line)" },
  ".cm-foldGutter .cm-gutterElement": { width: "22px", textAlign: "center", fontSize: "1.1em" },
  ".cm-foldPlaceholder": { backgroundColor: "var(--active-line)", border: "none", color: "var(--gutter-active)", padding: "0 6px" },
  ".cm-matchingBracket, &.cm-focused .cm-matchingBracket": { backgroundColor: "var(--bracket)", outline: "none" },
  ".cm-nonmatchingBracket, &.cm-focused .cm-nonmatchingBracket": { backgroundColor: "transparent", color: "var(--tok-invalid)" },
  ".cm-searchMatch": { backgroundColor: "var(--match)", outline: "none" },
  ".cm-searchMatch.cm-searchMatch-selected": { backgroundColor: "var(--match-current)" },
  // Whitespace made visible: a dot per space, an arrow per tab, a mark at each line end.
  ".cm-highlightSpace": {
    backgroundImage: "radial-gradient(circle at 50% 55%, var(--whitespace) 16%, transparent 22%)",
    backgroundPosition: "center",
  },
  ".cm-highlightTab": { color: "var(--tok-invalid)" },
  ".cm-line.cm-eol::after": { content: '"↵"', color: "var(--whitespace)", pointerEvents: "none" },
  // After an empty line's <br> the mark would start a second row, so it is laid over the line's start instead
  ".cm-line.cm-eol-empty": { position: "relative" },
  ".cm-line.cm-eol-empty::after": { position: "absolute", left: "6px", top: "0" },
  // Over the grammar's colour, whichever of the two spans ends up inside
  ".cm-yaml-number, .cm-yaml-number *": { color: "var(--tok-number) !important" },
  ".cm-yaml-constant, .cm-yaml-constant *": { color: "var(--tok-constant) !important" },
  // The search panel, sized for fingers
  ".cm-panels": { backgroundColor: "var(--panel)", color: "var(--fg)", borderColor: "var(--gutter)" },
  ".cm-panels.cm-panels-top": { borderBottom: "1px solid var(--active-line)" },
  ".cm-search": { padding: "8px 40px 8px 10px", fontSize: "15px" },
  ".cm-search label": { fontSize: "13px", marginRight: "6px" },
  ".cm-textfield": {
    fontSize: "15px",
    padding: "6px 8px",
    margin: "3px 0",
    borderRadius: "8px",
    border: "1px solid var(--gutter)",
    backgroundColor: "var(--bg)",
    color: "var(--fg)",
    width: "100%",
    boxSizing: "border-box",
  },
  ".cm-textfield:focus": { outline: "none", borderColor: "var(--accent)" },
  ".cm-search input[type=checkbox]": { accentColor: "var(--accent)" },
  ".cm-button": {
    fontSize: "13px",
    padding: "6px 12px",
    margin: "3px 6px 3px 0",
    borderRadius: "8px",
    border: "none",
    backgroundImage: "none",
    backgroundColor: "var(--active-line)",
    color: "var(--fg)",
  },
  ".cm-panel.cm-search [name=close]": { fontSize: "26px", padding: "2px 10px", color: "var(--fg)" },
});

// Chinese labels for the search panel and folding
const phrases = EditorState.phrases.of({
  Find: "查找",
  Replace: "替换",
  next: "下一个",
  previous: "上一个",
  all: "全选",
  "match case": "区分大小写",
  regexp: "正则",
  "by word": "全词",
  replace: "替换",
  "replace all": "全部替换",
  close: "关闭",
  "current match": "当前匹配",
  "on line": "行",
  "replaced match on line $": "已替换第 $ 行的匹配",
  "replaced $ matches": "已替换 $ 处",
  "Folded lines": "已折叠",
  "Unfold line": "展开",
  "Fold line": "折叠",
  "Control character": "控制字符",
});

// The line break mark is drawn by CSS after the line's text (::after), not as a widget: a widget at the end of a line
// is where Chrome on Android drops a selection handle being dragged across it.
const lineEndMark = Decoration.line({ class: "cm-eol" });
const emptyLineEndMark = Decoration.line({ class: "cm-eol cm-eol-empty" });

const lineEnds = ViewPlugin.fromClass(
  class {
    constructor(view) {
      this.decorations = this.build(view);
    }
    update(update) {
      if (update.docChanged || update.viewportChanged || foldedRanges(update.startState) != foldedRanges(update.state)) {
        this.decorations = this.build(update.view);
      }
    }
    build(view) {
      const builder = new RangeSetBuilder();
      const doc = view.state.doc;
      const folded = foldedRanges(view.state);
      for (const { from, to } of view.visibleRanges) {
        for (let pos = from; pos <= to; ) {
          const line = doc.lineAt(pos);
          // A line that starts inside a folded block is drawn as part of the line the fold starts on
          let hidden = false;
          folded.between(line.from, line.from, (a, b) => {
            if (a < line.from && line.from <= b) hidden = true;
          });
          if (line.number < doc.lines && !hidden) {
            builder.add(line.from, line.from, line.length ? lineEndMark : emptyLineEndMark);
          }
          pos = line.to + 1;
        }
      }
      return builder.finish();
    }
  },
  { decorations: (plugin) => plugin.decorations },
);

// The YAML grammar leaves plain scalars untyped; colour the numbers and the true/false/null among them.
const yamlNumber = /^[-+]?(\d[\d_]*(\.\d*)?([eE][-+]?\d+)?|0x[0-9a-fA-F_]+|0o[0-7_]+|\.\d+|\.(inf|Inf|INF)|\.(nan|NaN|NAN))$/;
const yamlConstant = /^(true|false|True|False|TRUE|FALSE|null|Null|NULL|~|yes|no|on|off|Yes|No|On|Off|YES|NO|ON|OFF)$/;
const numberMark = Decoration.mark({ class: "cm-yaml-number" });
const constantMark = Decoration.mark({ class: "cm-yaml-constant" });

const yamlScalars = ViewPlugin.fromClass(
  class {
    constructor(view) {
      this.decorations = this.build(view);
    }
    update(update) {
      if (update.docChanged || update.viewportChanged || syntaxTree(update.startState) != syntaxTree(update.state)) {
        this.decorations = this.build(update.view);
      }
    }
    build(view) {
      const builder = new RangeSetBuilder();
      for (const { from, to } of view.visibleRanges) {
        syntaxTree(view.state).iterate({
          from,
          to,
          enter: (node) => {
            if (node.name != "Literal" || node.node.parent?.name == "Key") return;
            const text = view.state.doc.sliceString(node.from, node.to);
            if (yamlConstant.test(text)) builder.add(node.from, node.to, constantMark);
            else if (yamlNumber.test(text)) builder.add(node.from, node.to, numberMark);
          },
        });
      }
      return builder.finish();
    }
  },
  { decorations: (plugin) => plugin.decorations },
);

// Chrome on Android drops the selection handles when the DOM around a selection changes while one is dragged
// (codemirror/dev#1675). So the current line's highlight follows the caret only, and stays put while text is
// selected, and there is no highlightSelectionMatches.
const activeLineStart = StateField.define({
  create: (state) => state.doc.lineAt(state.selection.main.head).from,
  update(start, tr) {
    const head = tr.state.selection.main;
    if (head.empty) return tr.state.doc.lineAt(head.head).from;
    return tr.docChanged ? tr.state.doc.lineAt(tr.changes.mapPos(start)).from : start;
  },
});
const activeLineMark = Decoration.line({ class: "cm-activeLine" });
const activeGutterMark = new (class extends GutterMarker {
  elementClass = "cm-activeLineGutter";
})();
const activeLine = [
  activeLineStart,
  EditorView.decorations.compute([activeLineStart], (state) =>
    Decoration.set([activeLineMark.range(state.field(activeLineStart))]),
  ),
  gutterLineClass.compute([activeLineStart], (state) =>
    RangeSet.of([activeGutterMark.range(state.field(activeLineStart))]),
  ),
];

function languageOf(name) {
  switch (name) {
    case "yaml":
      return [yaml(), yamlScalars];
    case "json":
      return json();
    case "javascript":
      return javascript();
    default:
      return [];
  }
}

const languageSlot = new Compartment();
const indentSlot = new Compartment();
const editableSlot = new Compartment();
const wrapSlot = new Compartment();
const fontSlot = new Compartment();

let view = null;
let options = null;

function fontTheme(px) {
  return EditorView.theme({ "&": { fontSize: `${px}px` } });
}

function indentOf(name) {
  const unit = name == "text" ? "    " : "  ";
  return [indentUnit.of(unit), EditorState.tabSize.of(unit.length)];
}

function editableOf(readOnly) {
  return [
    EditorState.readOnly.of(readOnly),
    EditorView.editable.of(!readOnly),
    // Without a caret, the line it is on means nothing
    readOnly ? [] : activeLine,
  ];
}

// The keyboard should not capitalise or correct what is searched for
function quietSearchFields(update) {
  if (!searchPanelOpen(update.state) || searchPanelOpen(update.startState)) return;
  for (const field of update.view.dom.querySelectorAll(".cm-search input.cm-textfield")) {
    field.setAttribute("autocapitalize", "off");
    field.setAttribute("autocorrect", "off");
    field.setAttribute("autocomplete", "off");
    field.setAttribute("spellcheck", "false");
  }
}

function report(update) {
  quietSearchFields(update);
  if (!update.docChanged) return;
  bridge.changed(update.state.doc.toString(), undoDepth(update.state) > 0, redoDepth(update.state) > 0);
}

function createState(text) {
  const lineBreak = text.includes("\r\n") && !/(^|[^\r])\n/.test(text) ? "\r\n" : null;
  return EditorState.create({
    doc: text,
    extensions: [
      lineBreak ? EditorState.lineSeparator.of(lineBreak) : [],
      phrases,
      baseTheme,
      lineNumbers(),
      foldGutter({ openText: "▾", closedText: "▸" }),
      codeFolding({ placeholderText: "…" }),
      highlightSpecialChars(),
      highlightWhitespace(),
      lineEnds,
      history(),
      indentOnInput(),
      bracketMatching(),
      closeBrackets(),
      search({ top: true }),
      syntaxHighlighting(highlightStyle),
      EditorView.contentAttributes.of({ autocomplete: "off" }),
      keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, ...foldKeymap, indentWithTab]),
      languageSlot.of(languageOf(options.language)),
      indentSlot.of(indentOf(options.language)),
      editableSlot.of(editableOf(options.readOnly)),
      wrapSlot.of(options.wrap ? EditorView.lineWrapping : []),
      fontSlot.of(fontTheme(options.fontSize)),
      EditorView.updateListener.of(report),
      EditorView.domEventHandlers({
        focus: () => bridge.focused(true),
        blur: () => bridge.focused(false),
      }),
    ],
  });
}

function applyColors(colors) {
  const root = document.documentElement.style;
  for (const [name, value] of Object.entries(colors)) root.setProperty(`--${name}`, value);
}

function reportScroll() {
  let pending = false;
  view.scrollDOM.addEventListener(
    "scroll",
    () => {
      if (pending) return;
      pending = true;
      requestAnimationFrame(() => {
        pending = false;
        bridge.scrolled(Math.round(view.scrollDOM.scrollTop * window.devicePixelRatio));
      });
    },
    { passive: true },
  );
}

/** Keeps the caret in sight when the keyboard shrinks the WebView. */
function followResize() {
  window.addEventListener("resize", () => {
    if (!view || !view.hasFocus) return;
    view.dispatch({ effects: EditorView.scrollIntoView(view.state.selection.main.head, { y: "nearest", yMargin: 24 }) });
  });
}

/** Typed one character from the symbol bar, with the same bracket and quote pairing as the keyboard. */
function typeText(text) {
  if (text.length == 1) {
    const paired = insertBracket(view.state, text);
    if (paired) {
      view.dispatch(paired);
      return;
    }
  }
  view.dispatch(view.state.replaceSelection(text), { scrollIntoView: true, userEvent: "input.type" });
}

function indentOrInsertUnit() {
  const state = view.state;
  if (state.selection.ranges.some((range) => !range.empty)) {
    indentMore(view);
    return;
  }
  view.dispatch(state.replaceSelection(state.facet(indentUnit)), { scrollIntoView: true, userEvent: "input.type" });
}

const moves = { left: cursorCharLeft, right: cursorCharRight, up: cursorLineUp, down: cursorLineDown };

window.kimi = {
  setup(opts) {
    options = opts;
    applyColors(opts.colors);
    const parent = document.getElementById("editor");
    view = new EditorView({ state: createState(opts.text), parent });
    reportScroll();
    followResize();
    // Font metrics settle once JetBrains Mono has loaded
    document.fonts?.ready.then(() => view.requestMeasure());
    bridge.ready();
  },
  setText(text) {
    view.setState(createState(text));
    bridge.changed(text, false, false);
  },
  setLanguage(language) {
    options.language = language;
    view.dispatch({
      effects: [languageSlot.reconfigure(languageOf(language)), indentSlot.reconfigure(indentOf(language))],
    });
  },
  setReadOnly(readOnly) {
    options.readOnly = readOnly;
    view.dispatch({ effects: editableSlot.reconfigure(editableOf(readOnly)) });
  },
  setWrap(wrap) {
    options.wrap = wrap;
    const head = view.state.selection.main.head;
    const top = view.lineBlockAtHeight(view.scrollDOM.getBoundingClientRect().top - view.documentTop).from;
    view.dispatch({ effects: wrapSlot.reconfigure(wrap ? EditorView.lineWrapping : []) });
    // The line that was at the top stays at the top
    view.dispatch({ effects: EditorView.scrollIntoView(view.hasFocus ? head : top, { y: view.hasFocus ? "center" : "start" }) });
    if (!wrap) view.scrollDOM.scrollLeft = 0;
  },
  setFontSize(px) {
    options.fontSize = px;
    view.dispatch({ effects: fontSlot.reconfigure(fontTheme(px)) });
  },
  setColors(colors) {
    applyColors(colors);
  },
  type: typeText,
  insert(text, caret) {
    const { from, to } = view.state.selection.main;
    view.dispatch({
      changes: { from, to, insert: text },
      selection: { anchor: from + Math.min(caret, text.length) },
      scrollIntoView: true,
      userEvent: "input.type",
    });
  },
  indent: indentOrInsertUnit,
  outdent() {
    indentLess(view);
  },
  move(direction) {
    moves[direction]?.(view);
  },
  undo() {
    undo(view);
  },
  redo() {
    redo(view);
  },
  toggleSearch() {
    if (searchPanelOpen(view.state)) {
      closeSearchPanel(view);
      if (!options.readOnly) view.focus();
    } else {
      openSearchPanel(view);
    }
  },
  focus() {
    if (!options.readOnly) view.focus();
  },
};

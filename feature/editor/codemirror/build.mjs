// KimiNoBox: bundles src/main.js into the editor.js the app ships (feature/editor/assets/codemirror).
// Run `npm ci && npm run build` here after changing the source or the CodeMirror versions, and commit the output.

import { build } from "esbuild";

await build({
  entryPoints: ["src/main.js"],
  outfile: "../assets/codemirror/editor.js",
  bundle: true,
  format: "iife",
  minify: true,
  legalComments: "none",
  target: ["chrome100"],
  logLevel: "info",
});
